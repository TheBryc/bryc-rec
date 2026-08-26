(ns components.advisor.core
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-memo use-callback]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["/gen/shadcn/components/ui/data-table" :refer [DataTable]]
            ["/gen/shadcn/components/ui/data-table-column-header" :refer [DataTableColumnHeader]]
            ["/gen/shadcn/components/ui/dropdown-menu" :as dropdown]
            ["/gen/shadcn/components/ui/alert-dialog" :as alert-dialog]
            ["/gen/shadcn/components/ui/popover" :as popover]
            ["/gen/shadcn/components/ui/badge" :refer [Badge]]
            ["/gen/shadcn/components/ui/select" :as select]
            ["/gen/shadcn/components/ui/sonner" :refer [toast]]
            [components.context.interface :as context]
            [components.advisor.add-student-modal :refer [add-student-modal]]
            [store.advisor.dashboard.events :as dashboard-events]
            [store.advisor.dashboard.subs :as dashboard-subs]
            [utils.csv :as csv]
            [clojure.string :as str]
            [components.shared.formatting :refer [format-phone format-relative-time days-since]]
            ["/gen/shadcn/components/ui/collapsible" :as collapsible]
            [tick.core :as t]
            [tick.locale-en-us]))

;; =============================================================================
;; Formatters
;; =============================================================================

(defn format-date-str
  "Format a YYYY-MM-DD date string (from Survey Submitted) to MM/dd/yyyy."
  [value]
  (when (and value (not= value ""))
    (try
      (t/format (t/formatter "MM/dd/yyyy") (t/date value))
      (catch js/Error _e (str value)))))

(defn format-js-date
  "Format a JS Date object or date string to MM/dd/yyyy hh:mm a in the browser timezone."
  [value]
  (when value
    (try
      (let [date (cond
                   (instance? js/Date value) value
                   (string? value) (js/Date. value)
                   :else nil)
            opts #js {:month "2-digit" :day "2-digit" :year "numeric"
                      :hour "2-digit" :minute "2-digit" :hour12 true}]
        (if (and date (not (js/isNaN (.getTime date))))
          (str/replace (.toLocaleString date "en-US" opts) ", " " ")
          (str value)))
      (catch js/Error _e (str value)))))

;; =============================================================================
;; Status Labels (for dropdown options)
;; =============================================================================

(def status-labels
  "Human-readable labels for advisor status values"
  {:needs-initial-text "Needs Initial Text"
   :no-response-to-initial-communication "No Response"
   :needs-to-schedule-intake-interview "Needs to Schedule"
   :needs-to-conduct-intake-interview "Needs Interview"
   :intake-interview-completed "Interview Done"
   :needs-recommendation-document "Needs Recommendations"
   :recommendations-sent "Recommendations Sent"
   :not-eligible "Not Eligible"
   :duplicate "Duplicate"
   :unassigned "Unassigned"})

(def status-weights
  {:needs-initial-text                    90
   :no-response-to-initial-communication  80
   :needs-to-schedule-intake-interview    70
   :needs-to-conduct-intake-interview     60
   :needs-recommendation-document         50
   :intake-interview-completed            30
   :recommendations-sent                  20
   :not-eligible                          0
   :duplicate                             0})

(defn priority-score
  "Compute priority score for a student JS object."
  [student-js]
  (let [status (keyword (or (aget student-js "student/advisor-status") ""))
        last-activity (aget student-js "student/last-activity")
        base-weight (get status-weights status 40)
        days (or (days-since last-activity) 0)
        multiplier (cond
                     (<= days 2)  1.0
                     (<= days 5)  1.5
                     (<= days 10) 2.0
                     :else        3.0)]
    (* base-weight multiplier)))

(defn priority-label [score]
  (cond
    (> score 150) "Urgent"
    (> score 80)  "Attention"
    (> score 0)   "On Track"
    :else         "Inactive"))

(defn priority-class [score]
  (cond
    (> score 150) "text-destructive"
    (> score 80)  "text-amber-600"
    (> score 0)   "text-green-600"
    :else         "text-muted-foreground"))

(def status-options
  "Statuses in display order for the dropdown"
  [:needs-initial-text
   :needs-to-conduct-intake-interview
   :needs-recommendation-document
   :no-response-to-initial-communication
   :needs-to-schedule-intake-interview
   :intake-interview-completed
   :recommendations-sent
   :not-eligible
   :duplicate
   :unassigned])

;; =============================================================================
;; Helpers
;; =============================================================================

(defn student-href
  "Build a URL path for the student detail page."
  [student-id]
  (str "/hub/students/view?student-id=" (js/encodeURIComponent (str student-id))))

;; Convert ClojureScript data to JS objects for TanStack
(defn students->js [students]
  (clj->js students :keyword-fn #(if (namespace %)
                                   (str (namespace %) "/" (name %))
                                   (name %))))

;; =============================================================================
;; Row Actions
;; =============================================================================

(defui archive-student-dialog [{:keys [open? on-open-change student-name on-confirm]}]
  ($ alert-dialog/AlertDialog {:open open? :onOpenChange on-open-change}
     ($ alert-dialog/AlertDialogContent {:on-click (fn [e] (.stopPropagation e))}
        ($ alert-dialog/AlertDialogHeader
           ($ alert-dialog/AlertDialogTitle "Archive advisee?")
           ($ alert-dialog/AlertDialogDescription
              (str "Archive " student-name
                   " and remove them from advising and student dashboard screens. Their detail page remains available by direct link.")))
        ($ alert-dialog/AlertDialogFooter
           ($ alert-dialog/AlertDialogCancel "Cancel")
           ($ alert-dialog/AlertDialogAction
              {:class "bg-destructive text-white hover:bg-destructive/90"
               :on-click on-confirm}
              "Archive")))))

(defui row-actions-cell [{:keys [student advisors on-assign on-archive]}]
  (let [email (aget student "student/email")
        phone (aget student "student/phone")
        student-id (aget student "student/id")
        student-name (aget student "student/name")
        current-advisor-id (aget student "student/assigned-advisor-id")
        current-advisor-name (aget student "student/assigned-advisor-name")
        [archive-open? set-archive-open!] (use-state false)
        copy-to-clipboard (fn [text label]
                            (-> (js/navigator.clipboard.writeText text)
                                (.then #(toast (str label " copied to clipboard")))
                                (.catch #(toast.error "Failed to copy to clipboard"))))
        copy-icon (fn []
                    ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                             :width "16"
                             :height "16"
                             :viewBox "0 0 24 24"
                             :fill "none"
                             :stroke "currentColor"
                             :strokeWidth "2"
                             :strokeLinecap "round"
                             :strokeLinejoin "round"
                             :class "mr-2"}
                       ($ :rect {:width "14" :height "14" :x "8" :y "8" :rx "2" :ry "2"})
                       ($ :path {:d "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"})))
        people-icon (fn []
                      ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                               :width "16"
                               :height "16"
                               :viewBox "0 0 24 24"
                               :fill "none"
                               :stroke "currentColor"
                               :strokeWidth "2"
                               :strokeLinecap "round"
                               :strokeLinejoin "round"
                               :class "mr-1"}
                         ($ :path {:d "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"})
                         ($ :circle {:cx "9" :cy "7" :r "4"})
                         ($ :path {:d "M22 21v-2a4 4 0 0 0-3-3.87"})
                         ($ :path {:d "M16 3.13a4 4 0 0 1 0 7.75"})))]
    ($ :<>
       ($ dropdown/DropdownMenu
          ($ dropdown/DropdownMenuTrigger {:asChild true}
             ($ button/Button {:variant "ghost"
                               :size "sm"
                               :class "h-8 w-8 p-0"
                               :on-click (fn [e] (.stopPropagation e))}
                ($ :span {:class "sr-only"} "Open menu")
                ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                         :width "16"
                         :height "16"
                         :viewBox "0 0 24 24"
                         :fill "none"
                         :stroke "currentColor"
                         :strokeWidth "2"
                         :strokeLinecap "round"
                         :strokeLinejoin "round"
                         :class "lucide lucide-more-horizontal"}
                   ($ :circle {:cx "12" :cy "12" :r "1"})
                   ($ :circle {:cx "19" :cy "12" :r "1"})
                   ($ :circle {:cx "5" :cy "12" :r "1"}))))
          ($ dropdown/DropdownMenuContent {:align "end"
                                           :on-click (fn [e] (.stopPropagation e))}
             ($ dropdown/DropdownMenuLabel "Contact Info")
             ($ dropdown/DropdownMenuSeparator)
             (when email
               ($ dropdown/DropdownMenuItem
                  {:on-click #(copy-to-clipboard email "Email")}
                  (copy-icon)
                  "Copy email"))
             (when phone
               ($ dropdown/DropdownMenuItem
                  {:on-click #(copy-to-clipboard phone "Phone")}
                  (copy-icon)
                  "Copy phone"))
             (when (seq advisors)
               ($ :<>
                  ($ dropdown/DropdownMenuSeparator)
                  ($ dropdown/DropdownMenuSub
                     ($ dropdown/DropdownMenuSubTrigger
                        ($ :span {:class "flex items-center gap-2"}
                           (people-icon)
                           (or current-advisor-name "Assign Advisor")))
                     ($ dropdown/DropdownMenuSubContent
                        (for [advisor advisors
                              :let [advisor-id (:advisor-id advisor)
                                    advisor-name (:name advisor)
                                    is-current (= (str advisor-id) (str current-advisor-id))]]
                          ($ dropdown/DropdownMenuItem
                             {:key (str advisor-id)
                              :on-click (fn [_]
                                          (when-not is-current
                                            (on-assign student-id advisor-id)))
                              :disabled is-current
                              :class (when is-current "font-semibold")}
                             advisor-name
                             (when is-current
                               ($ :span {:class "ml-2 text-xs text-muted-foreground"} "(current)")))))))
             (when on-archive
               ($ :<>
                  ($ dropdown/DropdownMenuSeparator)
                  ($ dropdown/DropdownMenuItem
                     {:class "text-destructive focus:text-destructive"
                      :on-click (fn [e]
                                  (.stopPropagation e)
                                  (set-archive-open! true))}
                     "Archive advisee")))))
       ($ archive-student-dialog
          {:open? archive-open?
           :on-open-change set-archive-open!
           :student-name (or student-name "this advisee")
           :on-confirm (fn []
                         (set-archive-open! false)
                         (on-archive student-id))})))))

;; =============================================================================
;; Table Columns
;; =============================================================================

(defn make-student-columns [{:keys [advisors on-assign on-archive selected-ids on-toggle-select on-select-all students get-student-href]}]
  #js [;; Checkbox column for bulk selection
       #js {:id "select"
            :size 40
            :header (fn [_props]
                      (let [all-ids (set (map #(aget % "student/id") students))
                            all-selected? (and (seq all-ids) (every? #(contains? selected-ids %) all-ids))
                            some-selected? (and (seq selected-ids) (not all-selected?))]
                        ($ Checkbox {:checked (if some-selected? "indeterminate" all-selected?)
                                     :onCheckedChange (fn [checked]
                                                       (if checked
                                                         (on-select-all (vec all-ids))
                                                         (on-select-all [])))
                                     :aria-label "Select all"
                                     :onClick (fn [e] (.stopPropagation e))})))
            :cell (fn [props]
                    (let [student-id (aget (.. props -row -original) "student/id")
                          is-selected (contains? selected-ids student-id)]
                      ($ Checkbox {:checked is-selected
                                   :onCheckedChange #(on-toggle-select student-id)
                                   :aria-label "Select row"
                                   :onClick (fn [e] (.stopPropagation e))})))
            :enableSorting false}

       ;; Name — clickable, navigates to detail
       #js {:accessorKey "student/name"
            :size 180
            :minSize 130
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Name"}))
            :cell (fn [props]
                    (let [student (.. props -row -original)
                          href (when get-student-href (get-student-href student))
                          name-val (.. props -row (getValue "student/name"))
                          status (aget student "student/advisor-status")
                          status-text (when status
                                        (get status-labels (keyword status)
                                             (-> status (str/replace #"-" " "))))]
                      ($ :div
                         (if href
                           ($ :a {:href href
                                  :class "font-medium text-foreground hover:underline"
                                  :on-click (fn [e]
                                              (if (or (.-metaKey e) (.-ctrlKey e))
                                                (.stopPropagation e)
                                                (.preventDefault e)))}
                              name-val)
                           ($ :div {:class "font-medium"} name-val))
                         (when status-text
                           ($ :p {:class "text-xs text-muted-foreground mt-0.5"} status-text)))))}

       ;; School
       #js {:accessorKey "student/school"
            :size 180
            :minSize 100
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "School"}))
            :cell (fn [props]
                    ($ :span {:class "text-sm"} (.. props -row (getValue "student/school"))))}

       ;; Survey Submitted
       #js {:accessorKey "student/submitted-survey"
            :size 140
            :minSize 110
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Survey Submitted"}))
            :cell (fn [props]
                    (let [value (.. props -row (getValue "student/submitted-survey"))]
                      (when (and value (not= value ""))
                        ($ :span {:class "text-sm text-muted-foreground"}
                           (format-relative-time value)))))}

       ;; Advisor
       #js {:accessorKey "student/assigned-advisor-name"
            :size 140
            :minSize 100
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Advisor"}))
            :cell (fn [props]
                    (let [value (.. props -row (getValue "student/assigned-advisor-name"))]
                      (if (and value (not= value ""))
                        ($ :span {:class "text-sm"} value)
                        ($ :span {:class "text-sm text-muted-foreground"} "Unassigned"))))}

       ;; Row actions (copy contact info, assign advisor)
       #js {:id "actions"
            :size 40
            :header ""
            :cell (fn [props]
                    ($ row-actions-cell {:student (.. props -row -original)
                                         :advisors advisors
                                         :on-assign on-assign
                                         :on-archive on-archive}))
            :enableSorting false}])

;; =============================================================================
;; Bulk Action Bar
;; =============================================================================

(defui bulk-action-bar [{:keys [selected-count advisors on-assign on-clear assigning?]}]
  ($ :div {:class "flex items-center gap-4 p-3 bg-muted/50 border rounded-lg"}
     ($ :span {:class "text-sm font-medium"}
        (str selected-count " student" (when (> selected-count 1) "s") " selected"))
     ($ dropdown/DropdownMenu
        ($ dropdown/DropdownMenuTrigger {:asChild true}
           ($ button/Button {:variant "default"
                             :size "sm"
                             :disabled assigning?}
              (if assigning?
                "Assigning..."
                "Assign to...")))
        ($ dropdown/DropdownMenuContent {:align "start"}
           ($ dropdown/DropdownMenuLabel "Select Advisor")
           ($ dropdown/DropdownMenuSeparator)
           (for [advisor advisors
                 :let [advisor-id (:advisor-id advisor)
                       advisor-name (:name advisor)]]
             ($ dropdown/DropdownMenuItem
                {:key (str advisor-id)
                 :on-click #(on-assign advisor-id)}
                advisor-name))))
     ($ button/Button {:variant "ghost"
                       :size "sm"
                       :on-click on-clear}
        "Clear selection")))

;; =============================================================================
;; Filter Popover
;; =============================================================================

(defn- count-active-filters [selected-status selected-school selected-advisor selected-graduation-year]
  (count (remove nil? [selected-status selected-school selected-advisor selected-graduation-year])))

(defui filter-popover
  [{:keys [advisors schools graduation-years
           selected-status selected-school selected-advisor selected-graduation-year
           showing-all?]}]
  (let [active-count (count-active-filters selected-status selected-school selected-advisor selected-graduation-year)]
    ($ popover/Popover
       ($ popover/PopoverTrigger {:asChild true}
          ($ button/Button {:variant "outline" :size "sm"}
             ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                      :width "16" :height "16" :viewBox "0 0 24 24"
                      :fill "none" :stroke "currentColor" :strokeWidth "2"
                      :strokeLinecap "round" :strokeLinejoin "round"
                      :class "mr-2"}
                ($ :polygon {:points "22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"}))
             "Filter"
             (when (pos? active-count)
               ($ Badge {:variant "secondary" :class "ml-2 h-5 px-1.5"}
                  (str active-count)))))
       ($ popover/PopoverContent {:class "w-72 p-4" :align "end"}
          ($ :div {:class "space-y-4"}
             ($ :div {:class "flex items-center justify-between"}
                ($ :h4 {:class "font-medium text-sm"} "Filters")
                (when (pos? active-count)
                  ($ button/Button {:variant "ghost" :size "sm"
                                    :class "h-7 text-xs -mr-2"
                                    :on-click #(rf/dispatch [::dashboard-events/clear-all-filters])}
                     "Clear all")))
             ($ :div {:class "space-y-1.5"}
                ($ :label {:class "text-xs font-medium text-muted-foreground"} "Status")
                ($ select/Select
                   {:value (or (some-> selected-status name) "all")
                    :onValueChange (fn [v]
                                     (if (= v "all")
                                       (rf/dispatch [::dashboard-events/clear-status-filter])
                                       (rf/dispatch [::dashboard-events/set-status-filter (keyword v)])))}
                   ($ select/SelectTrigger {:class "w-full h-9"}
                      ($ select/SelectValue {:placeholder "All Statuses"}))
                   ($ select/SelectContent
                      ($ select/SelectItem {:value "all"} "All Statuses")
                      (for [status status-options]
                        ($ select/SelectItem {:key (name status) :value (name status)}
                           (get status-labels status (name status)))))))
             (when (seq schools)
               ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-xs font-medium text-muted-foreground"} "School")
                  ($ select/Select
                     {:value (or selected-school "all")
                      :onValueChange (fn [v]
                                       (if (= v "all")
                                         (rf/dispatch [::dashboard-events/clear-school-filter])
                                         (rf/dispatch [::dashboard-events/set-school-filter v])))}
                     ($ select/SelectTrigger {:class "w-full h-9"}
                        ($ select/SelectValue {:placeholder "All Schools"}))
                     ($ select/SelectContent
                        ($ select/SelectItem {:value "all"} "All Schools")
                        (for [school schools]
                          ($ select/SelectItem {:key school :value school} school))))))
             (when (seq graduation-years)
               ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-xs font-medium text-muted-foreground"} "Graduation Year")
                  ($ select/Select
                     {:value (or selected-graduation-year "all")
                      :onValueChange (fn [v]
                                       (if (= v "all")
                                         (rf/dispatch [::dashboard-events/clear-graduation-year-filter])
                                         (rf/dispatch [::dashboard-events/set-graduation-year-filter v])))}
                     ($ select/SelectTrigger {:class "w-full h-9"}
                        ($ select/SelectValue {:placeholder "All Graduation Years"}))
                     ($ select/SelectContent
                        ($ select/SelectItem {:value "all"} "All Graduation Years")
                        (for [year graduation-years]
                          ($ select/SelectItem {:key year :value year} year))))))
             (when (and showing-all? (> (count advisors) 1))
               ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-xs font-medium text-muted-foreground"} "Advisor")
                  ($ select/Select
                     {:value (or (some-> selected-advisor str) "all")
                      :onValueChange (fn [v]
                                       (if (= v "all")
                                         (rf/dispatch [::dashboard-events/clear-advisor-filter])
                                         (rf/dispatch [::dashboard-events/set-advisor-filter v])))}
                     ($ select/SelectTrigger {:class "w-full h-9"}
                        ($ select/SelectValue {:placeholder "All Advisors"}))
                     ($ select/SelectContent
                        ($ select/SelectItem {:value "all"} "All Advisors")
                        (for [advisor advisors
                              :let [id (str (:advisor-id advisor))]]
                          ($ select/SelectItem {:key id :value id} (:name advisor))))))))))))

;; =============================================================================
;; Filter Pills
;; =============================================================================

(defui filter-pills [{:keys [selected-status selected-school selected-advisor selected-graduation-year advisors]}]
  (let [has-any? (or selected-status selected-school selected-advisor selected-graduation-year)
        advisor-name (when selected-advisor
                       (->> advisors
                            (some #(when (= (str (:advisor-id %)) (str selected-advisor))
                                     (:name %)))))]
    (when has-any?
      ($ :div {:class "flex items-center gap-2 flex-wrap"}
         (when selected-status
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "Status: " (get status-labels selected-status (name selected-status)))
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::dashboard-events/clear-status-filter])}
                 "\u00d7")))
         (when selected-school
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "School: " selected-school)
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::dashboard-events/clear-school-filter])}
                 "\u00d7")))
         (when selected-advisor
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "Advisor: " (or advisor-name "..."))
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::dashboard-events/clear-advisor-filter])}
                 "\u00d7")))
         (when selected-graduation-year
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "Graduation Year: " selected-graduation-year)
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::dashboard-events/clear-graduation-year-filter])}
                 "\u00d7")))
         (when (> (count (remove nil? [selected-status selected-school selected-advisor selected-graduation-year])) 1)
           ($ button/Button {:variant "ghost"
                             :size "sm"
                             :class "text-xs h-7"
                             :on-click #(rf/dispatch [::dashboard-events/clear-all-filters])}
              "Clear all"))))))

;; =============================================================================
;; Filter Presets
;; =============================================================================

(defui filter-presets [{:keys [active on-change]}]
  ($ :div {:class "flex items-center gap-2"}
     (for [{:keys [id label]} [{:id "attention" :label "Needs Attention"}
                                {:id "overdue" :label "Overdue"}
                                {:id "new" :label "New Students"}
                                {:id "all" :label "All"}]]
       ($ button/Button
          {:key id
           :variant (if (= active id) "secondary" "ghost")
           :size "sm"
           :on-click #(on-change id)}
          label))))

;; =============================================================================
;; Main Dashboard Content
;; =============================================================================

(defui advisor-content
  "Content-only dashboard for use within hub-layout. No header/layout wrapper."
  [{:keys [student-route-name current-match]
    :or {student-route-name :hub-student-detail}}]
  (let [students (use-subscribe [::dashboard-subs/students])
        loading? (use-subscribe [::dashboard-subs/loading?])
        showing-all? (use-subscribe [::dashboard-subs/showing-all?])
        advisors (use-subscribe [::dashboard-subs/advisors])
        schools (use-subscribe [::dashboard-subs/schools])
        graduation-years (use-subscribe [::dashboard-subs/graduation-years])
        selected-status (use-subscribe [::dashboard-subs/selected-status-filter])
        selected-school (use-subscribe [::dashboard-subs/selected-school-filter])
        selected-advisor (use-subscribe [::dashboard-subs/selected-advisor-filter])
        selected-graduation-year (use-subscribe [::dashboard-subs/selected-graduation-year-filter])
        ;; Selection state for bulk operations
        selected-ids (use-subscribe [::dashboard-subs/selected-student-ids])
        selected-count (use-subscribe [::dashboard-subs/selected-count])
        bulk-assigning? (use-subscribe [::dashboard-subs/bulk-assigning?])

        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        toggle-show-all (fn []
                          (rf/dispatch [::dashboard-events/toggle-show-all api-client]))

        handle-student-click (fn [student]
                              (let [student-id (aget student "student/id")]
                                (navigate! student-route-name {:student-id (str student-id)})))

        get-student-href (use-callback
                          (fn [student]
                            (let [student-id (aget student "student/id")]
                              (student-href (str student-id))))
                          [])

        handle-assign (fn [student-id advisor-id]
                        (rf/dispatch [::dashboard-events/assign-student api-client student-id advisor-id]))

        handle-archive (fn [student-id]
                         (rf/dispatch [::dashboard-events/archive-student api-client student-id]))

        ;; Selection handlers for bulk operations
        handle-toggle-select (use-callback
                              (fn [student-id]
                                (rf/dispatch [::dashboard-events/toggle-student-selection student-id]))
                              [])

        handle-select-all (use-callback
                           (fn [student-ids]
                             (if (seq student-ids)
                               (rf/dispatch [::dashboard-events/select-all-students student-ids])
                               (rf/dispatch [::dashboard-events/clear-selection])))
                           [])

        handle-clear-selection (use-callback
                                #(rf/dispatch [::dashboard-events/clear-selection])
                                [])

        handle-bulk-assign (use-callback
                            (fn [advisor-id]
                              (rf/dispatch [::dashboard-events/assign-students api-client (vec selected-ids) advisor-id]))
                            [api-client selected-ids])

        students-js (use-memo #(students->js (or students [])) [students])

        ;; Toolbar state
        [search-text set-search-text!] (use-state "")

        search-filtered-js (use-memo
                             (fn []
                               (if (seq search-text)
                                 (let [q (.toLowerCase search-text)]
                                   (.filter students-js
                                            (fn [s]
                                              (let [name (or (aget s "student/name") "")]
                                                (.includes (.toLowerCase name) q)))))
                                 students-js))
                             [students-js search-text])

        columns (use-memo
                 #(make-student-columns {:advisors advisors
                                         :on-assign handle-assign
                                         :on-archive handle-archive
                                         :selected-ids selected-ids
                                         :on-toggle-select handle-toggle-select
                                         :on-select-all handle-select-all
                                         :students search-filtered-js
                                         :get-student-href get-student-href})
                 [advisors handle-assign handle-archive selected-ids handle-toggle-select handle-select-all search-filtered-js get-student-href])]

    ;; Initialize filters from URL on mount, then fetch
    (use-effect
      (fn []
        (let [qp (:query-params current-match)]
          (if (seq qp)
            (rf/dispatch [::dashboard-events/initialize-filters-from-url qp api-client])
            (rf/dispatch [::dashboard-events/fetch api-client])))
        js/undefined)
      [current-match api-client])

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :h2 {:class "text-2xl font-semibold"} "Students "
             ($ :span {:class "text-muted-foreground font-normal text-lg"}
                (str "(" (count (or students [])) ")")))
          ($ button/Button {:variant "outline"
                            :size "sm"
                            :on-click #(rf/dispatch [::dashboard-events/open-add-student-modal])}
             ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                      :width "16"
                      :height "16"
                      :viewBox "0 0 24 24"
                      :fill "none"
                      :stroke "currentColor"
                      :strokeWidth "2"
                      :strokeLinecap "round"
                      :strokeLinejoin "round"
                      :class "mr-2"}
                ($ :path {:d "M12 5v14"})
                ($ :path {:d "M5 12h14"}))
             "Add Student"))

       ;; Bulk action bar (appears when students are selected)
       (when (pos? selected-count)
         ($ bulk-action-bar {:selected-count selected-count
                             :advisors advisors
                             :on-assign handle-bulk-assign
                             :on-clear handle-clear-selection
                             :assigning? bulk-assigning?}))

       ;; Toolbar: scope (My/All) → search → filter → action (Export)
       ($ :div {:class "flex items-center gap-3 py-3"}
          ($ :div {:class "flex items-center border rounded-md"}
             ($ button/Button {:variant (if showing-all? "ghost" "secondary")
                               :size "sm"
                               :class "rounded-r-none"
                               :on-click #(when showing-all? (toggle-show-all))
                               :disabled loading?}
                "My Students")
             ($ button/Button {:variant (if showing-all? "secondary" "ghost")
                               :size "sm"
                               :class "rounded-l-none"
                               :on-click #(when-not showing-all? (toggle-show-all))
                               :disabled loading?}
                "All Students"))
          ($ :input {:type "text"
                     :placeholder "Search by name..."
                     :class "h-9 w-64 rounded-md border border-input bg-transparent px-3 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                     :value (or search-text "")
                     :onChange #(set-search-text! (.. % -target -value))})
          ($ :div {:class "flex-1"})
          ($ filter-popover {:advisors advisors
                             :schools schools
                             :graduation-years graduation-years
                             :selected-status selected-status
                             :selected-school selected-school
                             :selected-advisor selected-advisor
                             :selected-graduation-year selected-graduation-year
                             :showing-all? showing-all?})
          ($ button/Button {:variant "outline"
                            :size "sm"
                            :disabled (zero? (.-length search-filtered-js))
                            :on-click #(csv/download!
                                        (str "students-" (csv/today-iso) ".csv")
                                        (csv/students->csv search-filtered-js))}
             ($ :svg {:xmlns "http://www.w3.org/2000/svg"
                      :width "16"
                      :height "16"
                      :viewBox "0 0 24 24"
                      :fill "none"
                      :stroke "currentColor"
                      :strokeWidth "2"
                      :strokeLinecap "round"
                      :strokeLinejoin "round"
                      :class "mr-2"}
                ($ :path {:d "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"})
                ($ :polyline {:points "7 10 12 15 17 10"})
                ($ :line {:x1 "12" :y1 "15" :x2 "12" :y2 "3"}))
             "Export CSV"))

       ;; Active filter chips
       ($ filter-pills {:selected-status selected-status
                        :selected-school selected-school
                        :selected-advisor selected-advisor
                        :selected-graduation-year selected-graduation-year
                        :advisors advisors})

       ;; Data table — no toolbar, just data
       ($ DataTable
          {:columns columns
           :data search-filtered-js
           :onRowClick handle-student-click
           :getRowHref get-student-href
           :pageSize 10
           :loading loading?
           :emptyMessage "No students found"})

       ;; Add student modal
       ($ add-student-modal))))

;; Standalone version with its own header (legacy)
(defui main []
  (let [[logging-out? set-logging-out!] (use-state false)
        students (use-subscribe [::dashboard-subs/students-raw])
        loading? (use-subscribe [::dashboard-subs/loading?])
        advisors (use-subscribe [::dashboard-subs/advisors])
        selected-ids (use-subscribe [::dashboard-subs/selected-student-ids])
        selected-count (use-subscribe [::dashboard-subs/selected-count])
        bulk-assigning? (use-subscribe [::dashboard-subs/bulk-assigning?])

        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        handle-logout (fn []
                       (set-logging-out! true)
                       (rf/dispatch [::dashboard-events/logout api-client navigate!]))

        handle-student-click (fn [student]
                              (let [student-id (aget student "student/id")]
                                (navigate! :hub-student-detail {:student-id (str student-id)})))

        get-student-href (use-callback
                          (fn [student]
                            (let [student-id (aget student "student/id")]
                              (student-href (str student-id))))
                          [])

        handle-assign (fn [student-id advisor-id]
                        (rf/dispatch [::dashboard-events/assign-student api-client student-id advisor-id]))

        handle-archive (fn [student-id]
                         (rf/dispatch [::dashboard-events/archive-student api-client student-id]))

        handle-toggle-select (use-callback
                              (fn [student-id]
                                (rf/dispatch [::dashboard-events/toggle-student-selection student-id]))
                              [])

        handle-select-all (use-callback
                           (fn [student-ids]
                             (if (seq student-ids)
                               (rf/dispatch [::dashboard-events/select-all-students student-ids])
                               (rf/dispatch [::dashboard-events/clear-selection])))
                           [])

        handle-clear-selection (use-callback
                                #(rf/dispatch [::dashboard-events/clear-selection])
                                [])

        handle-bulk-assign (use-callback
                            (fn [advisor-id]
                              (rf/dispatch [::dashboard-events/assign-students api-client (vec selected-ids) advisor-id]))
                            [api-client selected-ids])

        students-js (use-memo #(students->js (or students [])) [students])

        columns (use-memo
                 #(make-student-columns {:advisors advisors
                                         :on-assign handle-assign
                                         :on-archive handle-archive
                                         :selected-ids selected-ids
                                         :on-toggle-select handle-toggle-select
                                         :on-select-all handle-select-all
                                         :students students-js
                                         :get-student-href get-student-href})
                 [advisors handle-assign handle-archive selected-ids handle-toggle-select handle-select-all students-js get-student-href])]

    ;; Fetch students on mount
    (use-effect
      (fn []
        (rf/dispatch [::dashboard-events/fetch api-client])
        js/undefined)
      [api-client])

    ($ :div {:class "min-h-screen bg-background"}
       ;; Header with logout button
       ($ :header {:class "border-b"}
          ($ :div {:class "flex h-16 items-center px-8"}
             ($ :h1 {:class "text-xl font-semibold"} "Advisor Dashboard")
             ($ button/Button {:variant "outline"
                               :size "sm"
                               :class "ml-auto"
                               :on-click handle-logout
                               :disabled logging-out?}
                (if logging-out? "Logging out..." "Logout"))))

       ;; Main content
       ($ :main {:class "container mx-auto px-8 py-8"}
          ($ :div {:class "max-w-6xl mx-auto"}
             ($ :h2 {:class "text-2xl font-semibold mb-6"} "Students")

             ;; Bulk action bar (appears when students are selected)
             (when (pos? selected-count)
               ($ :div {:class "mb-4"}
                  ($ bulk-action-bar {:selected-count selected-count
                                      :advisors advisors
                                      :on-assign handle-bulk-assign
                                      :on-clear handle-clear-selection
                                      :assigning? bulk-assigning?})))

             ($ DataTable
                {:columns columns
                 :data students-js
                 :searchKey "student/name"
                 :searchPlaceholder "Search by name..."
                 :onRowClick handle-student-click
                 :getRowHref get-student-href
                 :pageSize 10
                 :loading loading?
                 :emptyMessage "No students found"}))))))
