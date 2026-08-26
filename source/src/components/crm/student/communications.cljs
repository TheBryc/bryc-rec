(ns components.crm.student.communications
  "Communication logging section for student detail view.
   Wide timeline layout with type filtering."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/label" :refer [Label]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/radio-group" :as radio]
            ["/gen/shadcn/components/ui/date-time-picker" :refer [DateTimePicker]]
            ["lucide-react" :refer [Mail MessageSquare Phone Video ArrowUpRight ArrowDownLeft
                                    Pencil Trash2 ExternalLink Plus]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [clojure.string]
            [tick.core :as t]
            [tick.locale-en-us]))

;; =============================================================================
;; Formatting helpers
;; =============================================================================

(defn- parse-to-instant
  "Parse an ISO timestamp string to a tick instant."
  [s]
  (let [s (str s)
        without-zone (clojure.string/replace s #"\[.+\]$" "")
        normalized (clojure.string/replace without-zone #"\.\d+Z$" "Z")]
    (if (clojure.string/ends-with? normalized "Z")
      (t/instant normalized)
      (t/instant (t/offset-date-time normalized)))))

(defn format-datetime [iso-string]
  (when iso-string
    (try
      (let [instant (parse-to-instant iso-string)
            local-tz (t/zone (.. js/Intl DateTimeFormat (resolvedOptions) -timeZone))
            zdt (t/in instant local-tz)
            formatted (t/format (t/formatter "MMM d, yyyy 'at' h:mm a") zdt)]
        formatted)
      (catch js/Error _e iso-string))))

(defn- type-label [communication-type]
  (case communication-type
    :email "Email"
    :sms "SMS"
    :voice-call "Voice Call"
    :video-call "Video Call"
    "Unknown"))

(defn- call-type? [communication-type]
  (#{:voice-call :video-call} communication-type))

;; Timeline dot colors by type
(def ^:private dot-colors
  {:email      "bg-blue-500"
   :sms        "bg-green-500"
   :voice-call "bg-purple-500"
   :video-call "bg-orange-500"})

;; =============================================================================
;; Type Filter Toolbar
;; =============================================================================

(defui comm-toolbar
  "Toolbar with Log Communication button and type filter.
   Props:
   - active-filter: :all, :email, :sms, or :calls
   - on-filter-change: callback (filter-keyword)"
  [{:keys [active-filter on-filter-change]}]
  (let [filter-btn (fn [filter-key label]
                     ($ :button
                        {:class (str "px-3 py-1 text-xs font-medium rounded-md transition-colors "
                                     (if (= active-filter filter-key)
                                       "bg-foreground text-background"
                                       "text-muted-foreground hover:text-foreground hover:bg-accent"))
                         :on-click #(on-filter-change filter-key)}
                        label))]
    ($ :div {:class "flex items-center justify-end mb-6"}
       ;; Type filters
       ($ :div {:class "flex items-center gap-1 border rounded-lg p-1 bg-muted/50"}
          (filter-btn :all "All")
          (filter-btn :email "Email")
          (filter-btn :sms "SMS")
          (filter-btn :calls "Calls")))))

;; =============================================================================
;; Timeline Communication Card
;; =============================================================================

(defui timeline-entry
  "Single communication rendered as a timeline entry.
   Inbound entries are indented to create a conversational feel.
   Calls get larger dots (bigger events). SMS entries are compact."
  [{:keys [communication api-client last?]}]
  (let [{:keys [communication-type direction subject content occurred-at
                duration-minutes outcome next-steps call-link source]} communication
        is-outbound? (= direction :outbound)
        is-call? (call-type? communication-type)
        is-sms? (= communication-type :sms)
        ;; Provider-backed events are immutable —
        ;; you can't unsend an SMS — so the edit/delete affordances and
        ;; the "Sent" / "Received" labels only apply to manually-logged
        ;; offline communications.
        source (or source (get-in communication [:metadata :source]))
        provider-backed? (contains? #{:communications :provider :broadcast} source)
        dot-color (get dot-colors communication-type "bg-gray-400")
        dot-size "w-[14px] h-[14px] -left-[7px]"
        ring-color (get {:email      "border-blue-500"
                         :sms        "border-green-500"
                         :voice-call "border-purple-500"
                         :video-call "border-orange-500"}
                        communication-type "border-gray-400")]

    ($ :div {:class (str "relative pl-8 "
                           (if is-sms? "pb-5 " "pb-7 ")
                           (when-not last? "border-l-2 border-border")
                           " ml-2")}
         ;; Timeline dot — filled for outbound, hollow ring for inbound
         ($ :div {:class (str "absolute top-1 rounded-full " dot-size " "
                              (if is-outbound?
                                (str "border-[3px] border-background " dot-color)
                                (str "border-[3px] bg-background " ring-color)))})

         ;; Card
         ($ :div {:class "group rounded-lg border bg-card p-4"}

            ;; Row 1: Type + direction + timestamp ····· actions
            ($ :div {:class "flex items-center justify-between mb-2"}
               ($ :div {:class "flex items-center gap-2 text-xs text-muted-foreground"}
                  ($ :span {:class "font-semibold uppercase tracking-wide"}
                     (type-label communication-type))
                  ($ :span "·")
                  ($ :span (if is-outbound? "Sent" "Received"))
                  ($ :span "·")
                  ($ :span (format-datetime occurred-at)))
               (when-not provider-backed?
                 ($ :div {:class "flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"}
                    ($ button/Button
                       {:variant "ghost" :size "sm" :class "h-6 w-6 p-0"
                        :on-click #(rf/dispatch [::student-events/open-log-communication-modal
                                                 {:communication communication}])}
                       ($ Pencil {:class "h-3 w-3"}))
                    ($ button/Button
                       {:variant "ghost" :size "sm"
                        :class "h-6 w-6 p-0 text-destructive hover:text-destructive"
                        :on-click #(when (js/confirm "Delete this communication log?")
                                     (rf/dispatch [::student-events/delete-communication
                                                   (:id communication) api-client]))}
                       ($ Trash2 {:class "h-3 w-3"})))))

            ;; Row 2: Subject / Outcome (the headline — what is this about?)
            (when (and (= communication-type :email) subject (seq subject))
              ($ :p {:class "font-semibold text-[15px] leading-snug mb-2"} subject))
            (when (and is-call? outcome (seq outcome))
              ($ :p {:class "font-semibold text-[15px] leading-snug mb-2"} outcome))

            ;; Row 3: Body content
            (when (and content (seq content))
              ($ :p {:class "text-sm text-muted-foreground whitespace-pre-wrap mb-2"}
                 content))

            ;; Row 4: Call metadata (compact footer)
            (when is-call?
              ($ :div {:class "flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground pt-2 border-t mt-2"}
                 (when duration-minutes
                   ($ :span (str duration-minutes " min")))
                 (when (and next-steps (seq next-steps))
                   ($ :span (str "Next: " next-steps)))
                 (when (and call-link (seq call-link))
                   ($ :a {:href call-link :target "_blank" :rel "noopener noreferrer"
                           :class "text-primary hover:underline inline-flex items-center gap-1"}
                      "Meeting link" ($ ExternalLink {:class "h-3 w-3"})))))))))


;; =============================================================================
;; Log Communication Modal (unchanged)
;; =============================================================================

(defui log-communication-modal
  "Modal dialog for logging or editing a communication."
  [{:keys [api-client]}]
  (let [open? (use-subscribe [::student-subs/communication-modal-open?])
        form (use-subscribe [::student-subs/communication-modal-form])
        saving? (use-subscribe [::student-subs/communication-modal-saving?])
        error (use-subscribe [::student-subs/communication-modal-error])
        mode (use-subscribe [::student-subs/communication-modal-mode])

        editing? (= mode :edit)
        communication-type (or (:communication-type form) :email)
        direction (or (:direction form) :outbound)
        subject (or (:subject form) "")
        content (or (:content form) "")
        duration-minutes (:duration-minutes form)
        outcome (or (:outcome form) "")
        next-steps (or (:next-steps form) "")
        call-link (or (:call-link form) "")
        occurred-at (or (:occurred-at form) "")

        is-email? (= communication-type :email)
        is-sms? (= communication-type :sms)
        is-call? (call-type? communication-type)
        is-video? (= communication-type :video-call)

        valid? (cond
                 is-email? (and (seq content) (seq subject))
                 is-sms? (seq content)
                 is-call? true
                 :else true)]

    ($ dialog/Dialog
       {:open (boolean open?)
        :onOpenChange #(when-not saving?
                         (rf/dispatch [::student-events/close-log-communication-modal]))}

       ($ dialog/DialogContent {:class "sm:max-w-lg"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle (if editing? "Edit Communication" "Log Communication"))
             ($ dialog/DialogDescription
                (cond
                  is-email? "Log an email exchange."
                  is-sms? "Log an SMS/text message exchange."
                  (= communication-type :voice-call) "Log a phone call."
                  (= communication-type :video-call) "Log a video call."
                  :else "Record a communication.")))

          ($ :div {:class "space-y-4 py-4"}
             ;; Communication Type & Direction
             ($ :div {:class "space-y-3 pl-3 border-l-2 border-muted"}
                ($ :p {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"}
                   "Type & Direction")
                ($ :div {:class "space-y-1"}
                   ($ Label {:class "text-sm"} "Type")
                   ($ radio/RadioGroup
                      {:value (name communication-type)
                       :onValueChange #(rf/dispatch [::student-events/set-communication-form-field
                                                     :communication-type (keyword %)])
                       :class "grid grid-cols-2 gap-2"
                       :disabled saving?}
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "email" :id "type-email"})
                         ($ Label {:htmlFor "type-email" :class "font-normal cursor-pointer"}
                            ($ :<> ($ Mail {:class "h-4 w-4 inline mr-1"}) "Email")))
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "sms" :id "type-sms"})
                         ($ Label {:htmlFor "type-sms" :class "font-normal cursor-pointer"}
                            ($ :<> ($ MessageSquare {:class "h-4 w-4 inline mr-1"}) "SMS")))
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "voice-call" :id "type-voice"})
                         ($ Label {:htmlFor "type-voice" :class "font-normal cursor-pointer"}
                            ($ :<> ($ Phone {:class "h-4 w-4 inline mr-1"}) "Voice Call")))
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "video-call" :id "type-video"})
                         ($ Label {:htmlFor "type-video" :class "font-normal cursor-pointer"}
                            ($ :<> ($ Video {:class "h-4 w-4 inline mr-1"}) "Video Call")))))
                ($ :div {:class "space-y-1"}
                   ($ Label {:class "text-sm"} "Direction")
                   ($ radio/RadioGroup
                      {:value (name direction)
                       :onValueChange #(rf/dispatch [::student-events/set-communication-form-field
                                                     :direction (keyword %)])
                       :class "flex gap-4"
                       :disabled saving?}
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "outbound" :id "dir-out"})
                         ($ Label {:htmlFor "dir-out" :class "font-normal cursor-pointer"}
                            ($ :<> ($ ArrowUpRight {:class "h-4 w-4 inline mr-1"}) "Outbound (from Staff)")))
                      ($ :div {:class "flex items-center space-x-2"}
                         ($ radio/RadioGroupItem {:value "inbound" :id "dir-in"})
                         ($ Label {:htmlFor "dir-in" :class "font-normal cursor-pointer"}
                            ($ :<> ($ ArrowDownLeft {:class "h-4 w-4 inline mr-1"}) "Inbound (to Staff)"))))))

             ;; Date & Time
             ($ :div {:class "space-y-1"}
                ($ Label {:class "text-sm"} "Date & Time")
                ($ DateTimePicker
                   {:value occurred-at
                    :disabled saving?
                    :placeholder "When did this happen?"
                    :onChange #(rf/dispatch [::student-events/set-communication-form-field
                                             :occurred-at %])}))

             ;; === Email fields ===
             (when is-email?
               ($ :div {:class "space-y-3 pl-3 border-l-2 border-muted"}
                  ($ :p {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"}
                     "Email Details"
                     ($ :span {:class "font-normal normal-case tracking-normal ml-1"} "- all fields required"))
                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "subject" :class "text-sm"}
                        "Subject" ($ :span {:class "text-red-500 ml-1"} "*"))
                     ($ Input
                        {:id "subject"
                         :value subject
                         :placeholder "Email subject..."
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :subject (.. % -target -value)])}))
                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "content" :class "text-sm"}
                        "Email Body" ($ :span {:class "text-red-500 ml-1"} "*"))
                     ($ Textarea
                        {:id "content"
                         :value content
                         :placeholder "Enter the email content..."
                         :rows 5
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :content (.. % -target -value)])}))))

             ;; === SMS fields ===
             (when is-sms?
               ($ :div {:class "space-y-3 pl-3 border-l-2 border-muted"}
                  ($ :p {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"}
                     "Message Details"
                     ($ :span {:class "font-normal normal-case tracking-normal ml-1"} "- required"))
                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "content" :class "text-sm"}
                        "Message" ($ :span {:class "text-red-500 ml-1"} "*"))
                     ($ Textarea
                        {:id "content"
                         :value content
                         :placeholder "Enter the SMS message..."
                         :rows 5
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :content (.. % -target -value)])}))))

             ;; === Call fields (voice & video) ===
             (when is-call?
               ($ :div {:class "space-y-3 pl-3 border-l-2 border-muted"}
                  ($ :p {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"}
                     "Call Details"
                     ($ :span {:class "font-normal normal-case tracking-normal ml-1"} "- all fields optional"))

                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "outcome" :class "text-sm"} "Outcome")
                     ($ Input
                        {:id "outcome"
                         :value outcome
                         :placeholder "e.g. Discussed enrollment options, reviewed transcript"
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :outcome (.. % -target -value)])}))

                  ($ :div {:class (if is-video? "grid grid-cols-2 gap-3" "")}
                     ($ :div {:class "space-y-1"}
                        ($ Label {:htmlFor "duration" :class "text-sm"} "Duration (min)")
                        ($ Input
                           {:id "duration"
                            :type "number"
                            :min 0
                            :value (or duration-minutes "")
                            :placeholder "e.g. 30"
                            :disabled saving?
                            :on-change #(let [v (.. % -target -value)]
                                          (rf/dispatch [::student-events/set-communication-form-field
                                                        :duration-minutes (when (seq v) (js/parseInt v 10))]))}))
                     (when is-video?
                       ($ :div {:class "space-y-1"}
                          ($ Label {:htmlFor "call-link" :class "text-sm"} "Meeting Link")
                          ($ Input
                             {:id "call-link"
                              :value call-link
                              :placeholder "https://zoom.us/j/..."
                              :disabled saving?
                              :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                        :call-link (.. % -target -value)])}))))

                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "next-steps" :class "text-sm"} "Next Steps")
                     ($ Textarea
                        {:id "next-steps"
                         :value next-steps
                         :placeholder "Action items or follow-ups..."
                         :rows 2
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :next-steps (.. % -target -value)])}))

                  ($ :div {:class "space-y-1"}
                     ($ Label {:htmlFor "content" :class "text-sm"} "Notes")
                     ($ Textarea
                        {:id "content"
                         :value content
                         :placeholder "Additional notes about the call..."
                         :rows 2
                         :disabled saving?
                         :on-change #(rf/dispatch [::student-events/set-communication-form-field
                                                   :content (.. % -target -value)])}))))

             ;; Error message
             (when error
               ($ :p {:class "text-sm text-destructive"} error)))

          ($ dialog/DialogFooter
             ($ button/Button
                {:variant "outline"
                 :on-click #(rf/dispatch [::student-events/close-log-communication-modal])
                 :disabled saving?}
                "Cancel")
             ($ button/Button
                {:on-click #(rf/dispatch [::student-events/log-communication api-client])
                 :disabled (or (not valid?) saving?)}
                (cond
                  saving? (if editing? "Saving..." "Logging...")
                  editing? "Save Changes"
                  :else "Log Communication")))))))

;; =============================================================================
;; Communications Section
;; =============================================================================

(defn- matches-filter?
  "Check if a communication matches the active type filter."
  [comm active-filter]
  (case active-filter
    :all true
    :email (= (:communication-type comm) :email)
    :sms (= (:communication-type comm) :sms)
    :calls (call-type? (:communication-type comm))
    true))

(defui communications-section
  "Full-width timeline communications view with type filtering.
   Props:
   - student-id: UUID of the student
   - api-client: API client for making requests"
  [{:keys [student-id api-client]}]
  (let [communications (use-subscribe [::student-subs/communications])
        loading? (use-subscribe [::student-subs/communications-loading?])
        error (use-subscribe [::student-subs/communications-error])
        [active-filter set-active-filter!] (uix/use-state :all)
        filtered (filterv #(matches-filter? % active-filter) (or communications []))]

    ;; Fetch communications on mount
    (uix/use-effect
      (fn []
        (when student-id
          (rf/dispatch [::student-events/fetch-communications student-id api-client]))
        js/undefined)
      [student-id api-client])

    ($ :div
       ;; Toolbar
       ($ comm-toolbar {:active-filter active-filter
                        :on-filter-change set-active-filter!})

       ;; Timeline
       (cond
         loading?
         ($ :p {:class "text-sm text-muted-foreground italic py-8"} "Loading communications...")

         error
         ($ :p {:class "text-sm text-destructive py-8"} error)

         (empty? communications)
         ($ :div {:class "text-center py-12"}
            ($ :p {:class "text-muted-foreground mb-2"} "No communications logged yet.")
            ($ :p {:class "text-sm text-muted-foreground"} "Use the button above to log your first communication."))

         (empty? filtered)
         ($ :p {:class "text-sm text-muted-foreground italic py-8"}
            "No communications match this filter.")

         :else
         ($ :div {:class "mt-2"}
            (for [[idx comm] (map-indexed vector filtered)]
              ($ timeline-entry {:key (str (or (:id comm) idx))
                                 :communication comm
                                 :api-client api-client
                                 :last? (= idx (dec (count filtered)))}))))

       ;; Modal
       ($ log-communication-modal {:api-client api-client}))))
