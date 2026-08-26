(ns components.advisor.transcript-analysis-review
  "Component for reviewing and approving transcript analysis results using data tables.
   Persists changes immediately when advisor accepts items via backend commands."
  (:require [uix.core :as uix :refer [defui $ use-state use-callback use-memo use-effect]]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["/gen/shadcn/components/ui/separator" :as separator]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/data-table" :refer [DataTable]]
            ["/gen/shadcn/components/ui/data-table-column-header" :refer [DataTableColumnHeader]]
            ["/gen/shadcn/components/ui/decision-actions" :refer [DecisionActions]]
            ["lucide-react" :refer [AlertTriangle Info ChevronDown ChevronUp Loader2]]
            [re-frame.core :as rf]
            [components.shared.formatting :refer [format-keyword]]
            [store.advisor.meetings.events :as meeting-events]
            [store.advisor.meetings.subs :as meetings-subs]
            [store.crm.student.subs :as student-subs]
            [components.context.interface :as context]))

;; Get display name for a field from field definitions
;; Falls back to formatted field name if not found
(defn get-field-display-name [field-definitions field-key]
  (let [;; Convert field key to slug format (remove namespace, use string)
        slug (cond
               (keyword? field-key) (name field-key)
               (string? field-key) field-key
               :else (str field-key))
        ;; Look up in field definitions
        field-def (get field-definitions slug)]
    (or (:name field-def)
        (format-keyword (keyword slug)))))

;; Sort grades numerically
(defn sort-grades [grades]
  (sort-by #(js/parseInt (re-find #"\d+" (str %))) grades))

;; Check if a vector looks like activities (vector of maps with :activity key)
(defn activities-vector? [v]
  (and (or (vector? v) (seq? v))
       (seq v)
       (every? map? v)
       (every? #(contains? % :activity) v)))

;; Render an activity as a mini card
(defui render-activity [{:keys [activity]}]
  (let [name (:activity activity)
        role (:role activity)
        grades (:grades-involved activity)
        category (:category activity)
        subtitle-parts (filter some?
                               [(when role role)
                                (when (seq grades)
                                  (str "Grades " (str/join ", " (sort-grades grades))))
                                (when category category)])]
    ($ :div {:class "p-2 bg-muted/50 rounded-md"}
       ($ :p {:class "font-medium text-sm"} (or name "Untitled"))
       (when (seq subtitle-parts)
         ($ :p {:class "text-xs text-muted-foreground mt-0.5"}
            (str/join " • " subtitle-parts))))))

;; Render a set/vector as badges
(defui render-tags [{:keys [values]}]
  ($ :div {:class "flex flex-wrap gap-1.5"}
     (for [v (sort-grades values)]
       ($ badge/Badge {:key (str v) :variant "secondary" :class "text-xs whitespace-normal text-left break-words"}
          (if (keyword? v) (format-keyword v) (str v))))))

;; Render a generic map as key-value pairs
(defui render-map-value [{:keys [m]}]
  ($ :div {:class "space-y-1"}
     (for [[k v] m]
       ($ :div {:key (str k) :class "text-sm"}
          ($ :span {:class "font-medium text-muted-foreground"}
             (str (if (keyword? k) (format-keyword k) k) ": "))
          ($ :span
             (cond
               (keyword? v) (format-keyword v)
               (set? v) (str/join ", " (map #(if (keyword? %) (format-keyword %) %) v))
               (vector? v) (str/join ", " (map #(if (keyword? %) (format-keyword %) %) v))
               :else (str v)))))))

;; Render any Clojure value with rich UI
(defui render-value [{:keys [value]}]
  (cond
    (nil? value)
    ($ :span {:class "text-muted-foreground"} "—")

    (keyword? value)
    ($ :span (format-keyword value))

    (string? value)
    (if (str/blank? value)
      ($ :span {:class "text-muted-foreground"} "—")
      ($ :span value))

    (boolean? value)
    ($ badge/Badge {:variant (if value "default" "secondary")}
       (if value "Yes" "No"))

    (number? value)
    ($ :span (str value))

    (and (or (set? value) (vector? value) (seq? value))
         (empty? value))
    ($ :span {:class "text-muted-foreground"} "—")

    (activities-vector? value)
    ($ :div {:class "space-y-2"}
       (for [[idx activity] (map-indexed vector value)]
         ($ render-activity {:key idx :activity activity})))

    (or (set? value) (vector? value) (seq? value))
    ($ render-tags {:values value})

    (map? value)
    (if (empty? value)
      ($ :span {:class "text-muted-foreground"} "—")
      ($ render-map-value {:m value}))

    :else
    ($ :span (str value))))

;; Format value to string (for JS DataTable compatibility)
(defn format-value-str [v]
  (cond
    (nil? v) "—"
    (keyword? v) (format-keyword v)
    (string? v) (if (str/blank? v) "—" v)
    (boolean? v) (if v "Yes" "No")
    (number? v) (str v)
    (set? v) (if (empty? v) "—" (str/join ", " (sort-grades (map #(if (keyword? %) (format-keyword %) (str %)) v))))
    (vector? v) (if (empty? v) "—" (str/join ", " (map #(if (keyword? %) (format-keyword %) (str %)) v)))
    (map? v) (if (empty? v) "—" "[Complex value]")
    :else (str v)))

;; Helper to convert conflicts to JS objects for DataTable
;; We store both the original clj value and a string version for sorting
(defn conflicts->js [conflicts field-definitions]
  (clj->js
   (map-indexed
    (fn [idx conflict]
      {"_index" idx
       "field" (get-field-display-name field-definitions (:field conflict))
       "field-key" (:field conflict)
       "profile-value" (:profile-value conflict)
       "profile-value-str" (format-value-str (:profile-value conflict))
       "transcript-value" (:transcript-value conflict)
       "transcript-value-str" (format-value-str (:transcript-value conflict))
       "recommendation" (when-let [rec (:recommendation conflict)] (name rec))
       "reason" (:reason conflict)})
    conflicts)))

;; Column definitions for the conflicts table (read-only informational)
(def conflicts-columns
  #js [#js {:accessorKey "field"
            :size 140
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Field"}))
            :cell (fn [props]
                    (let [field (.. props -row (getValue "field"))]
                      ($ :span {:class "font-medium whitespace-normal break-words"}
                         field)))}

       #js {:accessorKey "profile-value-str"
            :size 200
            :header "Current Value"
            :cell (fn [props]
                    (let [value (js->clj (.. props -row -original) :keywordize-keys true)
                          clj-value (:profile-value value)]
                      ($ :div {:class "py-1 whitespace-normal break-words"}
                         ($ render-value {:value clj-value}))))}

       #js {:accessorKey "transcript-value-str"
            :size 200
            :header "From Transcript"
            :cell (fn [props]
                    (let [value (js->clj (.. props -row -original) :keywordize-keys true)
                          clj-value (:transcript-value value)]
                      ($ :div {:class "py-1 whitespace-normal break-words"}
                         ($ render-value {:value clj-value}))))}

       #js {:accessorKey "recommendation"
            :size 130
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Recommendation"}))
            :cell (fn [props]
                    (let [rec (.. props -row (getValue "recommendation"))]
                      (when rec
                        ($ badge/Badge {:variant (case rec
                                                   "update" "default"
                                                   "keep-profile" "secondary"
                                                   "needs-verification" "destructive"
                                                   "outline")
                                        :class "whitespace-normal text-left break-words"}
                           (format-keyword (keyword rec))))))}

       #js {:accessorKey "reason"
            :size 250
            :header "Reason"
            :cell (fn [props]
                    (let [reason (.. props -row (getValue "reason"))]
                      (when reason
                        ($ :span {:class "text-sm text-muted-foreground whitespace-normal break-words"}
                           reason))))}])

;; Updates table - now receives vector of {:id uuid :field string :value any}
;; Decisions are tracked by UUID
(defui updates-table [{:keys [updates field-definitions decisions saving on-accept on-reject]}]
  ($ :div {:class "rounded-md border"}
     ($ table/Table
        ($ table/TableHeader
           ($ table/TableRow
              ($ table/TableHead {:class "w-[180px]"} "Field")
              ($ table/TableHead "New Value")
              ($ table/TableHead {:class "w-[100px]"} "")))
        ($ table/TableBody
           (if (seq updates)
             (for [update-item updates
                   :let [update-id (:id update-item)
                         field (:field update-item)
                         value (:value update-item)
                         decision (get decisions update-id)
                         is-saving? (get saving update-id)
                         field-display (get-field-display-name field-definitions field)]]
               ($ table/TableRow {:key (str update-id)
                                  :data-decision (case decision
                                                   :accepted "approved"
                                                   :rejected "rejected"
                                                   nil)}
                  ($ table/TableCell {:class "font-medium whitespace-pre-wrap break-words"} field-display)
                  ($ table/TableCell {:class "py-2"}
                     ($ render-value {:value value}))
                  ($ table/TableCell
                     (if is-saving?
                       ($ :div {:class "flex justify-center"}
                          ($ Loader2 {:class "h-4 w-4 animate-spin text-muted-foreground"}))
                       ($ DecisionActions {:decision (case decision
                                                       :accepted "approved"
                                                       :rejected "rejected"
                                                       nil)
                                           :onApprove #(on-accept update-item)
                                           :onReject #(on-reject update-item)})))))
             ($ table/TableRow
                ($ table/TableCell {:colSpan 3 :class "h-16 text-center text-muted-foreground"}
                   "No updates")))))))

;; Narratives table - now receives vector of {:id uuid :text string}
;; Decisions are tracked by UUID
(defui narratives-table [{:keys [narratives decisions saving on-accept on-reject]}]
  ($ :div {:class "rounded-md border"}
     ($ table/Table
        ($ table/TableHeader
           ($ table/TableRow
              ($ table/TableHead "Insight")
              ($ table/TableHead {:class "w-[100px]"} "")))
        ($ table/TableBody
           (if (seq narratives)
             (for [narrative-item narratives
                   :let [narrative-id (:id narrative-item)
                         text (:text narrative-item)
                         decision (get decisions narrative-id)
                         is-saving? (get saving narrative-id)]]
               ($ table/TableRow {:key (str narrative-id)
                                  :data-decision (case decision
                                                   :accepted "approved"
                                                   :rejected "rejected"
                                                   nil)}
                  ($ table/TableCell {:class "text-sm whitespace-pre-wrap break-words"} text)
                  ($ table/TableCell
                     (if is-saving?
                       ($ :div {:class "flex justify-center"}
                          ($ Loader2 {:class "h-4 w-4 animate-spin text-muted-foreground"}))
                       ($ DecisionActions {:decision (case decision
                                                       :accepted "approved"
                                                       :rejected "rejected"
                                                       nil)
                                           :onApprove #(on-accept narrative-item)
                                           :onReject #(on-reject narrative-item)})))))
             ($ table/TableRow
                ($ table/TableCell {:colSpan 2 :class "h-16 text-center text-muted-foreground"}
                   "No new insights")))))))

(defui section-header [{:keys [title count expanded? on-toggle]}]
  ($ :button {:class "flex items-center justify-between w-full p-2 hover:bg-muted/50 rounded-lg transition-colors"
              :on-click on-toggle}
     ($ :div {:class "flex items-center gap-2"}
        ($ :h3 {:class "font-semibold"} title)
        (when count
          ($ badge/Badge {:variant "secondary"} count)))
     (if expanded?
       ($ ChevronUp {:class "h-4 w-4"})
       ($ ChevronDown {:class "h-4 w-4"}))))

(defui transcript-analysis-review [{:keys [analysis-result student-id meeting-id meeting on-applied]}]
  (let [;; Get api-client from context
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to field definitions for display names
        field-definitions (use-subscribe [::student-subs/field-definitions])

        ;; Get persisted decisions from meeting (by UUID)
        persisted-update-decisions (or (:meeting/update-decisions meeting) {})
        persisted-narrative-decisions (or (:meeting/narrative-decisions meeting) {})

        ;; Get saving state from re-frame (tracks which item UUIDs are currently saving)
        saving (use-subscribe [::meetings-subs/saving])

        ;; Expanded sections state
        [expanded set-expanded!] (use-state {:updates true :conflicts true :narratives true})

        {:keys [updates conflicts narrative-additions narrative-conflicts
                confidence-scores update-summary requires-human-review]} analysis-result

        toggle-section (use-callback
                        (fn [section]
                          (set-expanded! #(update % section not)))
                        [])

        ;; Handle accepting a profile update - calls backend command
        handle-accept-update (use-callback
                              (fn [update-item]
                                (rf/dispatch [::meeting-events/accept-transcript-update
                                              {:student-id student-id
                                               :meeting-id meeting-id
                                               :update-id (:id update-item)
                                               :field-slug (:field update-item)
                                               :new-value (:value update-item)
                                               :api-client api-client}]))
                              [student-id meeting-id api-client])

        ;; Handle rejecting a profile update
        handle-reject-update (use-callback
                              (fn [update-item]
                                (rf/dispatch [::meeting-events/reject-transcript-update
                                              {:student-id student-id
                                               :meeting-id meeting-id
                                               :update-id (:id update-item)
                                               :api-client api-client}]))
                              [student-id meeting-id api-client])

        ;; Handle accepting a narrative
        handle-accept-narrative (use-callback
                                 (fn [narrative-item]
                                   (rf/dispatch [::meeting-events/accept-transcript-narrative
                                                 {:student-id student-id
                                                  :meeting-id meeting-id
                                                  :narrative-id (:id narrative-item)
                                                  :narrative-text (:text narrative-item)
                                                  :api-client api-client}]))
                                 [student-id meeting-id api-client])

        ;; Handle rejecting a narrative
        handle-reject-narrative (use-callback
                                 (fn [narrative-item]
                                   (rf/dispatch [::meeting-events/reject-transcript-narrative
                                                 {:student-id student-id
                                                  :meeting-id meeting-id
                                                  :narrative-id (:id narrative-item)
                                                  :api-client api-client}]))
                                 [student-id meeting-id api-client])

        ;; Approve all - trigger individual saves for all pending items
        approve-all (use-callback
                     (fn []
                       ;; Accept all pending updates
                       (doseq [update-item updates
                               :when (not (get persisted-update-decisions (:id update-item)))]
                         (handle-accept-update update-item))
                       ;; Accept all pending narratives
                       (doseq [narrative-item narrative-additions
                               :when (not (get persisted-narrative-decisions (:id narrative-item)))]
                         (handle-accept-narrative narrative-item)))
                     [updates narrative-additions persisted-update-decisions persisted-narrative-decisions
                      handle-accept-update handle-accept-narrative])

        ;; Count completed items
        total-count (+ (count updates) (count narrative-additions))
        decided-count (+ (count persisted-update-decisions) (count persisted-narrative-decisions))
        accepted-count (+ (count (filter #(= :accepted (val %)) persisted-update-decisions))
                          (count (filter #(= :accepted (val %)) persisted-narrative-decisions)))
        all-decided? (= decided-count total-count)]

    ($ :div {:class "space-y-6"}
       ;; Summary
       (when update-summary
         ($ alert/Alert {:class (if requires-human-review
                                  "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950"
                                  "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950")}
            (if requires-human-review
              ($ AlertTriangle {:class "h-4 w-4 text-amber-600"})
              ($ Info {:class "h-4 w-4 text-green-600"}))
            ($ alert/AlertTitle (if requires-human-review "Review Required" "Analysis Complete"))
            ($ alert/AlertDescription update-summary)))

       ;; Confidence scores
       (when (seq confidence-scores)
         ($ :div {:class "flex flex-wrap gap-2"}
            (for [[domain score] confidence-scores]
              ($ badge/Badge {:key (name domain)
                             :variant (cond
                                        (>= score 0.8) "default"
                                        (>= score 0.5) "secondary"
                                        :else "destructive")}
                 (str (name domain) ": " (int (* 100 score)) "%")))))

       ;; Conflicts (informational - shows discrepancies between profile and transcript)
       (when (seq conflicts)
         ($ card/Card {:class "border-amber-200 dark:border-amber-800"}
            ($ card/CardHeader {:class "pb-2"}
               ($ section-header {:title "Discrepancies"
                                  :count (count conflicts)
                                  :expanded? (:conflicts expanded)
                                  :on-toggle #(toggle-section :conflicts)})
               (when (:conflicts expanded)
                 ($ :p {:class "text-sm text-muted-foreground mt-1"}
                    "Fields where the transcript differs from the current profile")))
            (when (:conflicts expanded)
              ($ card/CardContent {:class "pt-0"}
                 ($ DataTable {:columns conflicts-columns
                               :data (conflicts->js conflicts field-definitions)
                               :showPagination false
                               :pageSize 50
                               :emptyMessage "No discrepancies found"})))))

       ;; Profile updates - Using simple table with real-time persistence
       (when (seq updates)
         ($ card/Card
            ($ card/CardHeader {:class "pb-2"}
               ($ section-header {:title "Profile Updates"
                                  :count (count updates)
                                  :expanded? (:updates expanded)
                                  :on-toggle #(toggle-section :updates)}))
            (when (:updates expanded)
              ($ card/CardContent {:class "pt-0"}
                 ($ updates-table {:updates updates
                                   :field-definitions field-definitions
                                   :decisions persisted-update-decisions
                                   :saving saving
                                   :on-accept handle-accept-update
                                   :on-reject handle-reject-update})))))

       ;; Narrative additions - Using simple table with real-time persistence
       (when (seq narrative-additions)
         ($ card/Card
            ($ card/CardHeader {:class "pb-2"}
               ($ section-header {:title "New Insights"
                                  :count (count narrative-additions)
                                  :expanded? (:narratives expanded)
                                  :on-toggle #(toggle-section :narratives)}))
            (when (:narratives expanded)
              ($ card/CardContent {:class "pt-0"}
                 ($ narratives-table {:narratives narrative-additions
                                      :decisions persisted-narrative-decisions
                                      :saving saving
                                      :on-accept handle-accept-narrative
                                      :on-reject handle-reject-narrative})))))

       ;; Status bar
       ($ separator/Separator)
       ($ :div {:class "flex justify-between items-center"}
          ($ :div {:class "flex items-center gap-4"}
             ($ :span {:class "text-sm text-muted-foreground"}
                (str accepted-count " of " total-count " accepted"))
             (when-not all-decided?
               ($ button/Button {:variant "outline"
                                 :on-click approve-all}
                  "Accept All")))
          (when (and all-decided? on-applied)
            ($ button/Button {:on-click on-applied}
               "Done"))))))
