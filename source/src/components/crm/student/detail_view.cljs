(ns components.crm.student.detail-view
  "Student detail view with section-nav layout.
   Left section nav (1/4) + content area (3/4). One section visible at a time."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["/gen/shadcn/components/ui/alert-dialog" :as alert-dialog]
            ["/gen/shadcn/components/ui/collapsible" :as collapsible]
            ["lucide-react" :refer [AlertCircle Archive ChevronDown ChevronRight Calendar FileText Sparkles MessageSquare Loader2 User House Mail]]
            [components.crm.profile-photo :refer [profile-photo]]
            [components.crm.student.tabs.core :as sections]
            [components.crm.student.status :as status]
            [components.shared.formatting :refer [format-keyword]]
            [components.context.interface :as context]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [tick.core :as t]
            [tick.locale-en-us]))

;; =============================================================================
;; Formatting helpers
;; =============================================================================

(defn format-datetime [iso-string]
  (when iso-string
    (try
      (let [zdt (t/zoned-date-time iso-string)
            formatted (t/format (t/formatter "MMM d, yyyy 'at' h:mm a") zdt)]
        formatted)
      (catch js/Error _e iso-string))))

;; =============================================================================
;; Recommendation Alert
;; =============================================================================

(defui recommendation-alert [{:keys [student api-client rec-status regenerating?]}]
  (let [sub-rec-status (use-subscribe [::student-subs/recommendation-status])
        sub-regenerating? (use-subscribe [::student-subs/regenerating-recommendations?])
        rec-status (or rec-status sub-rec-status)
        regenerating? (or regenerating? sub-regenerating?)
        [show-changes set-show-changes] (use-state false)]

    (cond
      regenerating?
      ($ :div {:class "mb-6"}
         ($ alert/Alert {:class "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950"}
            ($ Loader2 {:class "h-4 w-4 animate-spin text-amber-600 dark:text-amber-400"})
            ($ alert/AlertTitle {:class "text-amber-900 dark:text-amber-100"}
               "Generating Recommendations...")
            ($ alert/AlertDescription
               ($ :p {:class "text-sm text-amber-800 dark:text-amber-200"}
                  "AI is generating personalized recommendations. This typically takes about 10 minutes. "
                  "You will be notified when generation is complete."))))

      (:needs-regeneration? rec-status)
      (let [changes (:changes rec-status)
            has-changes? (and changes (seq changes))]
        ($ :div {:class "mb-6"}
           ($ alert/Alert {:class "border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950"}
              ($ AlertCircle {:class "text-blue-600 dark:text-blue-400"})
              ($ alert/AlertTitle {:class "text-blue-900 dark:text-blue-100"}
                 "Recommendations Need Update")
              ($ alert/AlertDescription
                 ($ :div {:class "space-y-3"}
                    ($ :div
                       (when-let [last-gen (:last-generated-at rec-status)]
                         ($ :p {:class "text-sm text-blue-800 dark:text-blue-200 mb-3"}
                            "Last generated: "
                            ($ :span {:class "tabular-nums"} (format-datetime last-gen))))

                       (when has-changes?
                         ($ collapsible/Collapsible {:open show-changes :on-open-change set-show-changes}
                            ($ collapsible/CollapsibleTrigger {:as-child true}
                               ($ :button {:class "flex items-center gap-1 text-sm font-medium text-blue-600 hover:text-blue-800"}
                                  (if show-changes
                                    ($ ChevronDown {:class "h-4 w-4"})
                                    ($ ChevronRight {:class "h-4 w-4"}))
                                  (str (count changes) " change" (when (> (count changes) 1) "s") " detected")))
                            ($ collapsible/CollapsibleContent
                               ($ :div {:class "mt-3 space-y-3 pl-4 border-l-2 border-blue-300"}
                                  (for [[idx change] (map-indexed vector changes)]
                                    ($ :div {:key idx :class "text-sm"}
                                       ($ :p {:class "font-medium text-blue-700"} (:description change))
                                       ($ :p {:class "text-blue-600/70 tabular-nums text-xs mt-0.5"}
                                          (format-datetime (:timestamp change))))))))))

                    ($ :div {:class "flex gap-2"}
                       ($ button/Button
                          {:on-click #(rf/dispatch [::student-events/regenerate-recommendations
                                                    (:student/id student) api-client])
                           :variant "outline"
                           :size "sm"
                           :class "border-blue-300 bg-white hover:bg-blue-100 text-blue-700"}
                          "Generate Now")))))))

      :else nil)))

;; =============================================================================
;; Section Nav
;; =============================================================================

(defui section-nav [{:keys [active-section on-select bryc-status]}]
  ($ :nav {:class "flex overflow-x-auto gap-1 border-b pb-2 mb-4 lg:flex-col lg:border-b-0 lg:pb-0 lg:mb-0 lg:space-y-0.5"}
     (for [{:keys [id label danger?]} (sections/visible-sections bryc-status)]
       ($ :button
          {:key id
           :class (str "whitespace-nowrap px-3 py-2 rounded-lg text-sm transition-colors duration-150 flex items-center lg:w-full lg:text-left "
                       (cond
                         (= active-section id) "bg-secondary font-medium text-foreground"
                         danger? "text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                         :else "text-muted-foreground hover:bg-accent/50 hover:text-foreground"))
           :on-click #(on-select id)}
          label))))

;; =============================================================================
;; Header with Actions Dropdown
;; =============================================================================

(def header-status-labels
  {:needs-initial-text "New"
   :no-response-to-initial-communication "No Response"
   :needs-to-schedule-intake-interview "Needs Scheduling"
   :needs-to-conduct-intake-interview "Needs Interview"
   :intake-interview-completed "Interview Done"
   :needs-recommendation-document "Needs Recs"
   :recommendations-sent "Recs Sent"
   :not-eligible "Ineligible"
   :duplicate "Duplicate"})

(defn- known-value [value]
  (when (and value (not= value "") (not= value "—"))
    value))

(defn- display-keyword [value]
  (cond
    (keyword? value) (format-keyword value)
    (string? value) value
    :else nil))

(defn- fellow-profile? [field-values]
  (status/status-in? sections/fellow-profile-statuses (get field-values :bryc-status)))

(defn- detail-url [student-id view]
  (str "/hub/students/view?student-id=" (js/encodeURIComponent student-id)
       (when view (str "&view=" view))))

(defui header-fact [{:keys [icon value href placeholder]}]
  (let [content (or (known-value value) placeholder)]
    ($ :div {:class "flex min-w-0 items-center gap-2 text-sm text-muted-foreground"}
       (when icon ($ icon {:class "h-4 w-4 shrink-0"}))
       (if href
         ($ :a {:href href :class "truncate text-primary underline-offset-2 hover:underline"} content)
         ($ :span {:class "truncate"} content)))))

(defui archive-student-dialog [{:keys [open? on-open-change student-name profile-label on-confirm]}]
  ($ alert-dialog/AlertDialog {:open open? :onOpenChange on-open-change}
     ($ alert-dialog/AlertDialogContent
        ($ alert-dialog/AlertDialogHeader
           ($ alert-dialog/AlertDialogTitle (str "Archive " profile-label "?"))
           ($ alert-dialog/AlertDialogDescription
              (str "Archive " student-name
                   " and remove them from "
                   (if (= profile-label "fellow") "fellows" "advising")
                   " and student dashboard screens. The detail page will remain available by direct link.")))
        ($ alert-dialog/AlertDialogFooter
           ($ alert-dialog/AlertDialogCancel "Cancel")
           ($ alert-dialog/AlertDialogAction
              {:class "bg-destructive text-white hover:bg-destructive/90"
               :on-click on-confirm}
              "Archive")))))

(defui student-header [{:keys [student field-values active-tab]}]
  (let [ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        api-client (:api/client ctx)
        student-id (str (:student/id student))
        archived? (= :archived (:student/status student))
        school (or (:student/high-school student) (:student/school student))
        grad-year (or (:student/graduation-year student) (get field-values :graduation-year))
        fellow? (fellow-profile? field-values)
        advisee? (status/advisee? (get field-values :bryc-status))
        archivable? (or advisee? fellow?)
        archive-label (if fellow? "fellow" "advisee")
        [archive-open? set-archive-open!] (use-state false)
        bryc-status (display-keyword (get field-values :bryc-status))
        meeting-day (or (display-keyword (get field-values :meeting-day))
                        (display-keyword (get field-values :fellow-meeting-day))
                        (display-keyword (get field-values :attendance-day))
                        (display-keyword (get field-values :program-day)))
        program-manager (or (known-value (get field-values :program-manager))
                            (known-value (:student/program-manager student)))
        email (:student/email student)
        tab-class (fn [tab-key]
                    (str "px-3 py-1.5 rounded-md text-sm font-medium transition-colors "
                         (if (= active-tab tab-key)
                           "bg-background text-foreground shadow-sm"
                           "text-muted-foreground hover:text-foreground")))
        tab-btn (fn [tab-key label & [route-name]]
                  ($ :button
                     {:type "button"
                      :class (tab-class tab-key)
                      :on-click (case tab-key
                                  :profile #(navigate! :hub-student-detail {:student-id student-id})
                                  :communications #(navigate! :hub-student-detail {:student-id student-id
                                                                                    :view "communications"})
                                  #(navigate! route-name {:student-id student-id}))}
                     label))]
    ($ :div
       ;; Top row: avatar + name + Log Communication
       ($ :div {:class "flex flex-col md:flex-row items-start gap-4 md:gap-6 md:items-center mb-4"}
          ($ :div {:class "flex items-center gap-4 flex-1 min-w-0"}
             ($ profile-photo {:api-client api-client
                               :contact-id (:student/id student)
                               :file-id (get field-values :profile-photo)
                               :name (:student/name student)
                               :editable? true})
             ($ :div {:class "min-w-0 flex-1"}
                ($ :h1 {:class "text-2xl font-semibold truncate"}
                   (if fellow?
                     (str (:student/name student) ", " (if grad-year
                                                          (str "Class of " grad-year)
                                                          "class year missing"))
                     (:student/name student)))
                (if fellow?
                  ($ :div {:class "mt-2 flex flex-wrap items-center gap-x-5 gap-y-2"}
                     ($ header-fact {:value bryc-status :placeholder "BRYC status missing"})
                     ($ header-fact {:icon Calendar :value meeting-day :placeholder "Meeting day missing"})
                     ($ header-fact {:icon User :value program-manager :placeholder "Program manager missing"})
                     ($ header-fact {:icon House :value school :placeholder "School missing"})
                     ($ header-fact {:icon Mail
                                     :value email
                                     :href (when email (str "mailto:" email))
                                     :placeholder "Email missing"}))
                  ($ :div {:class "flex items-center gap-2 text-sm text-muted-foreground"}
                     (when school ($ :span school))
                     (when (and school grad-year) ($ :span "·"))
                     (when grad-year ($ :span (str "Class of " grad-year)))))))
          (when-not archived?
            ($ :div {:class "flex items-center gap-2"}
               (when archivable?
                 ($ button/Button
                    {:variant "outline" :size "sm"
                     :on-click #(set-archive-open! true)}
                    ($ Archive {:class "h-4 w-4 mr-2"})
                    "Archive"))
               ($ button/Button
                  {:variant "outline" :size "sm"
                   :on-click #(rf/dispatch [::student-events/open-log-communication-modal])}
                  ($ MessageSquare {:class "h-4 w-4 mr-2"})
                  "Log Communication"))))

       ($ archive-student-dialog
          {:open? archive-open?
           :on-open-change set-archive-open!
           :student-name (:student/name student)
           :profile-label archive-label
           :on-confirm (fn []
                         (set-archive-open! false)
                         (rf/dispatch [::student-events/archive-student api-client]))})

       ;; Tab bar
       (if fellow?
         ($ :div {:class "flex w-full items-center gap-1 overflow-x-auto rounded-lg bg-muted/60 p-1"}
            (for [[tab-key label view] [[:profile "Profile" nil]
                                        [:attendance "Attendance" "attendance"]
                                        [:communications "Communications" "communications"]
                                        [:case-management "Case Management" "case-management"]
                                        [:involvement-awards "Involvement/Awards" "involvement-awards"]]]
              ($ :a {:key (name tab-key)
                     :href (detail-url student-id view)
                     :class (str "whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors "
                                 (if (= active-tab tab-key)
                                   "bg-background text-foreground shadow-sm"
                                   "text-muted-foreground hover:text-foreground"))}
                 label))
            ($ :button
               {:class (str "whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors "
                            (if (= active-tab :recommendations)
                              "bg-background text-foreground shadow-sm"
                              "text-muted-foreground hover:text-foreground"))
                :on-click #(navigate! :hub-recommendations {:student-id student-id})}
               "Recommendations"))
         ($ :div {:class "flex items-center gap-1 border rounded-lg p-1 bg-muted/50 w-fit"}
            ($ :a {:href (detail-url student-id nil)
                   :class (tab-class :profile)}
               "Profile")
            ($ :a {:href (detail-url student-id "communications")
                   :class (tab-class :communications)}
               "Communications")
            (tab-btn :meetings "Meetings" :hub-meetings)
            (tab-btn :transcript "Transcript" :hub-transcript)
            (tab-btn :recommendations "Recommendations" :hub-recommendations))))))

;; =============================================================================
;; Main Detail View
;; =============================================================================

(defui student-detail-view
  "Full student detail view with section-nav layout.
   Left nav shows sections, right area shows one section at a time.
   Props:
   - student: student data map"
  [{:keys [student initial-section]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        field-values (or (use-subscribe [::student-subs/field-values]) {})
        archived? (= :archived (:student/status student))
        [active-section set-section-raw!] (uix/use-state (or initial-section sections/default-section-id))
        sync-url! (fn [section]
                    (let [url (js/URL. js/window.location.href)]
                      (.set (.-searchParams url) "section" section)
                      (js/window.history.replaceState nil "" (str url))))
        set-section! (fn [section-id]
                       (set-section-raw! section-id)
                       (sync-url! section-id))]

    ($ :div {:class "flex flex-col h-full min-h-0"}
       ;; Header
       ($ :div {:class "border-b bg-card p-6"}
          ($ student-header {:student student :field-values field-values}))

       (when archived?
         ($ :div {:class "px-6 pt-4"}
            ($ alert/Alert {:class "border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950"}
               ($ Archive {:class "h-4 w-4 text-amber-600 dark:text-amber-400"})
               ($ alert/AlertTitle {:class "text-amber-900 dark:text-amber-100"}
                  "Archived Student")
               ($ alert/AlertDescription
                  ($ :p {:class "text-sm text-amber-800 dark:text-amber-200"}
                     "This student is hidden from advising and student dashboard screens. Direct links remain available for review.")))))

       ;; Recommendation alert
       (when-not archived?
         ($ :div {:class "px-6 pt-4"}
            ($ recommendation-alert {:student student :api-client api-client})))

       ;; Section nav + content
       ($ :div {:class "flex-1 overflow-hidden px-6 pb-6"}
          ($ :div {:class "h-full grid grid-cols-1 lg:grid-cols-4 gap-6 pt-2"}
             ;; Left nav (1/4)
             ($ :div {:class "lg:overflow-y-auto"}
                ($ section-nav {:active-section active-section
                               :on-select set-section!
                               :bryc-status (get field-values :bryc-status)}))
             ;; Content (3/4)
             ($ :div {:class "lg:col-span-3 overflow-y-auto"}
                ($ sections/section-content
                   {:active-section active-section
                    :student student
                    :field-values field-values
                    :api-client (when-not archived? api-client)})))))))
