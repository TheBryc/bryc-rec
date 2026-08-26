(ns components.advisor.meetings-page
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            [components.context.interface :as context]
            [tick.core :as t]
            [tick.locale-en-us]
            [store.advisor.meetings.events :as meetings-events]
            [store.advisor.meetings.subs :as meetings-subs]))

(defn format-datetime [iso-string]
  (when iso-string
    (try
      ;; Parse as zoned-date-time which handles the offset
      (let [zdt (t/zoned-date-time iso-string)
            formatted (t/format (t/formatter "MMM d, yyyy 'at' h:mm a") zdt)]
        formatted)
      (catch js/Error e
        (js/console.error "Error formatting timestamp:" e iso-string)
        iso-string))))

(defui meetings-page [{:keys [current-match]}]
  (let [student-id (get-in current-match [:query-params :student-id])
        ;; Subscribe to si-frame state
        meetings (use-subscribe [::meetings-subs/meetings])
        loading? (use-subscribe [::meetings-subs/loading?])
        starting-meeting? (use-subscribe [::meetings-subs/starting-meeting?])
        new-meeting-id (use-subscribe [::meetings-subs/new-meeting-id])

        ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        api-client (:api/client ctx)

        ;; Check if there's an existing intake interview meeting
        has-intake-meeting? (some #(= (:meeting/type %) :intake-interview) meetings)

        start-meeting (fn [type]
                        (rf/dispatch [::meetings-events/start-meeting
                                      (parse-uuid student-id)
                                      type
                                      api-client]))

        open-meeting (fn [meeting]
                       (navigate! :hub-meeting {:student-id student-id
                                                :meeting-id (str (:meeting/id meeting))}))]

    ;; Load screen data on mount or when student-id changes (fat query pattern)
    (use-effect
     (fn []
       (when student-id
         (rf/dispatch [::meetings-events/load-screen (parse-uuid student-id) api-client]))
       js/undefined)
     [student-id api-client])

    ;; Handle navigation when a new meeting is created
    (use-effect
     (fn []
       (when new-meeting-id
         (navigate! :hub-meeting {:student-id student-id
                                  :meeting-id (str new-meeting-id)})
         (rf/dispatch [::meetings-events/clear-new-meeting-id]))
       js/undefined)
     [navigate! new-meeting-id student-id])

    ($ :div {:class "flex flex-col h-screen"}
       ;; Content - Two column layout
       ($ :div {:class "flex-1 overflow-y-auto py-6 pl-6 pr-6"}
          ($ :div {:class "max-w-7xl ml-0"}
             ($ :div {:class "grid grid-cols-3 gap-8"}
                ;; Left column - Meeting templates
                ($ :div {:class "pr-8 border-r"}
                   ($ :h3 {:class "text-xl font-semibold mb-4"} "New Meeting")
                   ($ :div {:class "space-y-4"}
                      ;; Only show intake interview option if no intake meeting exists
                      (when-not has-intake-meeting?
                        ($ :div {:class (str "bg-card rounded-lg border p-6 transition-colors "
                                            (if starting-meeting?
                                              "opacity-50 cursor-not-allowed"
                                              "cursor-pointer hover:bg-muted/50"))
                                 :on-click #(when-not starting-meeting?
                                             (start-meeting :intake-interview))}
                           ($ :div
                              ($ :h4 {:class "text-lg font-medium mb-2"} "Start Intake Interview")
                              ($ :p {:class "text-sm text-muted-foreground"}
                                 "Comprehensive onboarding meeting to review student profile, introduce Overgrad, and establish goals."))))
                      ($ :div {:class (str "bg-card rounded-lg border p-6 transition-colors "
                                          (if starting-meeting?
                                            "opacity-50 cursor-not-allowed"
                                            "cursor-pointer hover:bg-muted/50"))
                               :on-click #(when-not starting-meeting?
                                           (start-meeting :freeform))}
                         ($ :div
                            ($ :h4 {:class "text-lg font-medium mb-2"} "Start Freeform Meeting")
                            ($ :p {:class "text-sm text-muted-foreground"}
                               "Open-ended meeting without a structured agenda. Take notes freely during your conversation.")))))

                ;; Right column - Meeting history (spans 2 columns)
                ($ :div {:class "col-span-2 pl-8"}
                   ($ :h3 {:class "text-xl font-semibold mb-4"} "Meeting History")
                   (if (seq meetings)
                     ($ :div {:class "space-y-3"}
                        (for [[idx meeting] (map-indexed vector meetings)]
                          ($ :div {:key idx
                                   :class "bg-card rounded-lg border p-4 cursor-pointer hover:bg-muted/50 transition-colors"
                                   :on-click #(open-meeting meeting)}
                             ($ :div {:class "space-y-2"}
                                ($ :div {:class "flex items-center gap-3"}
                                   ($ :h4 {:class "text-base font-medium"}
                                      (case (:meeting/type meeting)
                                        :intake-interview "Intake Interview"
                                        :freeform "Freeform Meeting"
                                        "Meeting"))
                                   ($ :span {:class (str "text-xs px-2 py-1 rounded-full "
                                                         (if (= (:meeting/status meeting) :completed)
                                                           "bg-green-100 text-green-800"
                                                           "bg-yellow-100 text-yellow-800"))}
                                      (if (= (:meeting/status meeting) :completed)
                                        "Completed"
                                        "In Progress")))
                                ($ :p {:class "text-sm text-muted-foreground"}
                                   (str "Started: " (format-datetime (:meeting/started-at meeting))))
                                (when (:meeting/completed-at meeting)
                                   ($ :p {:class "text-sm text-muted-foreground"}
                                      (str "Completed: " (format-datetime (:meeting/completed-at meeting)))))))))
                     ($ :p {:class "text-sm text-muted-foreground"}
                        "No meetings yet. Start a new meeting to get started.")))))))))