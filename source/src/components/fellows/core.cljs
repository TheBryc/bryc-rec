(ns components.fellows.core
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
            [store.fellows.events :as fellows-events]
            [store.fellows.subs :as fellows-subs]
            [utils.csv :as csv]
            [clojure.string :as str]))

;; =============================================================================
;; Helpers
;; =============================================================================

(defn student-href
  "Build a URL path for the student detail page."
  [student-id]
  (str "/hub/students/view?student-id=" (js/encodeURIComponent (str student-id))))

(defn students->js [students]
  (clj->js students :keyword-fn #(if (namespace %)
                                   (str (namespace %) "/" (name %))
                                   (name %))))

;; =============================================================================
;; Row Actions
;; =============================================================================

(defui archive-fellow-dialog [{:keys [open? on-open-change student-name on-confirm]}]
  ($ alert-dialog/AlertDialog {:open open? :onOpenChange on-open-change}
     ($ alert-dialog/AlertDialogContent {:on-click (fn [e] (.stopPropagation e))}
        ($ alert-dialog/AlertDialogHeader
           ($ alert-dialog/AlertDialogTitle "Archive fellow?")
           ($ alert-dialog/AlertDialogDescription
              (str "Archive " student-name
                   " and remove them from fellows and student dashboard screens. Their detail page remains available by direct link.")))
        ($ alert-dialog/AlertDialogFooter
           ($ alert-dialog/AlertDialogCancel "Cancel")
           ($ alert-dialog/AlertDialogAction
              {:class "bg-destructive text-white hover:bg-destructive/90"
               :on-click on-confirm}
              "Archive")))))

(defui row-actions-cell [{:keys [student program-managers on-assign on-archive]}]
  (let [email (aget student "student/email")
        phone (aget student "student/phone")
        student-id (aget student "student/id")
        student-name (aget student "student/name")
        current-pm-id (aget student "student/assigned-pm-id")
        current-pm-name (aget student "student/assigned-pm-name")
        [archive-open? set-archive-open!] (use-state false)
        copy-to-clipboard (fn [text label]
                           (-> (js/navigator.clipboard.writeText text)
                               (.then #(toast (str label " copied to clipboard")))
                               (.catch #(toast.error "Failed to copy to clipboard"))))]
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
               {:on-click (fn [_e]
                           (copy-to-clipboard email "Email"))}
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
                  ($ :path {:d "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"}))
               "Copy email"))
          (when phone
            ($ dropdown/DropdownMenuItem
               {:on-click (fn [_e]
                           (copy-to-clipboard phone "Phone"))}
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
                  ($ :path {:d "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"}))
               "Copy phone"))
          (when (seq program-managers)
            ($ :<>
             ($ dropdown/DropdownMenuSeparator)
             ($ dropdown/DropdownMenuSub
                ($ dropdown/DropdownMenuSubTrigger
                   ($ :span {:class "flex items-center gap-2"}
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
                         ($ :path {:d "M16 3.13a4 4 0 0 1 0 7.75"}))
                      (if current-pm-name
                        current-pm-name
                        "Assign PM")))
                ($ dropdown/DropdownMenuSubContent
                   (for [pm program-managers
                         :let [pm-id (:pm-id pm)
                               pm-name (:name pm)
                               is-current (= (str pm-id) (str current-pm-id))]]
                     ($ dropdown/DropdownMenuItem
                        {:key (str pm-id)
                         :on-click (fn [_]
                                    (when-not is-current
                                      (on-assign student-id pm-id)))
                         :disabled is-current
                         :class (when is-current "font-semibold")}
                        pm-name
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
                  "Archive fellow")))))
     ($ archive-fellow-dialog
        {:open? archive-open?
         :on-open-change set-archive-open!
         :student-name (or student-name "this fellow")
         :on-confirm (fn []
                       (set-archive-open! false)
                       (on-archive student-id))})))))

;; =============================================================================
;; Table Columns
;; =============================================================================

(defn make-fellow-columns [{:keys [program-managers on-assign on-archive selected-ids on-toggle-select on-select-all students get-student-href]}]
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

       ;; Name — clickable, navigates to detail, NO status label
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
                          name-val (.. props -row (getValue "student/name"))]
                      (if href
                        ($ :a {:href href
                               :class "font-medium text-foreground hover:underline"
                               :on-click (fn [e]
                                           (if (or (.-metaKey e) (.-ctrlKey e))
                                             (.stopPropagation e)
                                             (.preventDefault e)))}
                           name-val)
                        ($ :div {:class "font-medium"} name-val))))}

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

       ;; Program Manager
       #js {:accessorKey "student/assigned-pm-name"
            :size 160
            :minSize 100
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Program Manager"}))
            :cell (fn [props]
                    (let [value (.. props -row (getValue "student/assigned-pm-name"))]
                      (if (and value (not= value ""))
                        ($ :span {:class "text-sm"} value)
                        ($ :span {:class "text-sm text-muted-foreground"} "Unassigned"))))}

       ;; Row actions (3-dots menu)
       #js {:id "actions"
            :size 40
            :header ""
            :cell (fn [props]
                    ($ row-actions-cell {:student (.. props -row -original)
                                         :program-managers program-managers
                                         :on-assign on-assign
                                         :on-archive on-archive}))
            :enableSorting false}])

;; =============================================================================
;; Bulk Action Bar
;; =============================================================================

(defui bulk-action-bar [{:keys [selected-count program-managers on-assign on-clear assigning?]}]
  ($ :div {:class "flex items-center gap-4 p-3 bg-muted/50 border rounded-lg"}
     ($ :span {:class "text-sm font-medium"}
        (str selected-count " fellow" (when (> selected-count 1) "s") " selected"))
     ($ dropdown/DropdownMenu
        ($ dropdown/DropdownMenuTrigger {:asChild true}
           ($ button/Button {:variant "default"
                             :size "sm"
                             :disabled assigning?}
              (if assigning?
                "Assigning..."
                "Assign to...")))
        ($ dropdown/DropdownMenuContent {:align "start"}
           ($ dropdown/DropdownMenuLabel "Select Program Manager")
           ($ dropdown/DropdownMenuSeparator)
           (for [pm program-managers
                 :let [pm-id (:pm-id pm)
                       pm-name (:name pm)]]
             ($ dropdown/DropdownMenuItem
                {:key (str pm-id)
                 :on-click #(on-assign pm-id)}
                pm-name))))
     ($ button/Button {:variant "ghost"
                       :size "sm"
                       :on-click on-clear}
        "Clear selection")))

;; =============================================================================
;; Filter Popover
;; =============================================================================

(defn- count-active-filters [selected-school selected-pm]
  (count (remove nil? [selected-school selected-pm])))

(defui filter-popover
  [{:keys [program-managers schools selected-school selected-pm]}]
  (let [active-count (count-active-filters selected-school selected-pm)]
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
                                    :on-click #(rf/dispatch [::fellows-events/clear-all-filters])}
                     "Clear all")))
             (when (seq schools)
               ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-xs font-medium text-muted-foreground"} "School")
                  ($ select/Select
                     {:value (or selected-school "all")
                      :onValueChange (fn [v]
                                       (if (= v "all")
                                         (rf/dispatch [::fellows-events/clear-school-filter])
                                         (rf/dispatch [::fellows-events/set-school-filter v])))}
                     ($ select/SelectTrigger {:class "w-full h-9"}
                        ($ select/SelectValue {:placeholder "All Schools"}))
                     ($ select/SelectContent
                        ($ select/SelectItem {:value "all"} "All Schools")
                        (for [school schools]
                          ($ select/SelectItem {:key school :value school} school))))))
             (when (> (count program-managers) 1)
               ($ :div {:class "space-y-1.5"}
                  ($ :label {:class "text-xs font-medium text-muted-foreground"} "Program Manager")
                  ($ select/Select
                     {:value (or (some-> selected-pm str) "all")
                      :onValueChange (fn [v]
                                       (if (= v "all")
                                         (rf/dispatch [::fellows-events/clear-pm-filter])
                                         (rf/dispatch [::fellows-events/set-pm-filter v])))}
                     ($ select/SelectTrigger {:class "w-full h-9"}
                        ($ select/SelectValue {:placeholder "All PMs"}))
                     ($ select/SelectContent
                        ($ select/SelectItem {:value "all"} "All PMs")
                        (for [pm program-managers
                              :let [id (str (:pm-id pm))]]
                          ($ select/SelectItem {:key id :value id} (:name pm))))))))))))

;; =============================================================================
;; Filter Pills
;; =============================================================================

(defui filter-pills [{:keys [selected-school selected-pm program-managers]}]
  (let [has-any? (or selected-school selected-pm)
        pm-name (when selected-pm
                  (->> program-managers
                       (some #(when (= (str (:pm-id %)) (str selected-pm))
                                (:name %)))))]
    (when has-any?
      ($ :div {:class "flex items-center gap-2 flex-wrap"}
         (when selected-school
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "School: " selected-school)
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::fellows-events/clear-school-filter])}
                 "\u00d7")))
         (when selected-pm
           ($ :span {:class "inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium bg-secondary text-secondary-foreground"}
              (str "PM: " (or pm-name "..."))
              ($ :button {:class "ml-1 hover:text-foreground"
                          :on-click #(rf/dispatch [::fellows-events/clear-pm-filter])}
                 "\u00d7")))
         (when (> (count (remove nil? [selected-school selected-pm])) 1)
           ($ button/Button {:variant "ghost"
                             :size "sm"
                             :class "text-xs h-7"
                             :on-click #(rf/dispatch [::fellows-events/clear-all-filters])}
              "Clear all"))))))

;; =============================================================================
;; Main Fellows Content
;; =============================================================================

(defui fellows-content
  "Content-only fellows view for use within hub-layout."
  [{:keys [student-route-name current-match]
    :or {student-route-name :hub-student-detail}}]
  (let [students (use-subscribe [::fellows-subs/students])
        loading? (use-subscribe [::fellows-subs/loading?])
        showing-all? (use-subscribe [::fellows-subs/showing-all?])
        program-managers (use-subscribe [::fellows-subs/program-managers])
        schools (use-subscribe [::fellows-subs/schools])
        selected-school (use-subscribe [::fellows-subs/selected-school-filter])
        selected-pm (use-subscribe [::fellows-subs/selected-pm-filter])
        ;; Selection state for bulk operations
        selected-ids (use-subscribe [::fellows-subs/selected-student-ids])
        selected-count (use-subscribe [::fellows-subs/selected-count])
        bulk-assigning? (use-subscribe [::fellows-subs/bulk-assigning?])

        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        toggle-show-all (fn []
                          (rf/dispatch [::fellows-events/toggle-show-all api-client]))

        handle-student-click (fn [student]
                              (let [student-id (aget student "student/id")]
                                (navigate! student-route-name {:student-id (str student-id)})))

        get-student-href (use-callback
                          (fn [student]
                            (let [student-id (aget student "student/id")]
                              (student-href (str student-id))))
                          [])

        handle-assign (fn [student-id pm-id]
                        (rf/dispatch [::fellows-events/assign-fellow-pm api-client student-id pm-id]))

        handle-archive (fn [student-id]
                         (rf/dispatch [::fellows-events/archive-fellow api-client student-id]))

        ;; Selection handlers for bulk operations
        handle-toggle-select (use-callback
                              (fn [student-id]
                                (rf/dispatch [::fellows-events/toggle-student-selection student-id]))
                              [])

        handle-select-all (use-callback
                           (fn [student-ids]
                             (if (seq student-ids)
                               (rf/dispatch [::fellows-events/select-all-students student-ids])
                               (rf/dispatch [::fellows-events/clear-selection])))
                           [])

        handle-clear-selection (use-callback
                                #(rf/dispatch [::fellows-events/clear-selection])
                                [])

        handle-bulk-assign (use-callback
                            (fn [pm-id]
                              (rf/dispatch [::fellows-events/assign-fellows-pm api-client (vec selected-ids) pm-id]))
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
                 #(make-fellow-columns {:program-managers program-managers
                                        :on-assign handle-assign
                                        :on-archive handle-archive
                                        :selected-ids selected-ids
                                        :on-toggle-select handle-toggle-select
                                        :on-select-all handle-select-all
                                        :students search-filtered-js
                                        :get-student-href get-student-href})
                 [program-managers handle-assign handle-archive selected-ids handle-toggle-select handle-select-all search-filtered-js get-student-href])]

    ;; Initialize filters from URL on mount, then fetch
    (use-effect
      (fn []
        (let [qp (:query-params current-match)]
          (if (seq qp)
            (rf/dispatch [::fellows-events/initialize-filters-from-url qp api-client])
            (rf/dispatch [::fellows-events/fetch api-client])))
        js/undefined)
      [current-match api-client])

    ($ :div {:class "space-y-4"}
       ;; Header
       ($ :div {:class "flex items-center justify-between"}
          ($ :h2 {:class "text-2xl font-semibold"} "Fellows "
             ($ :span {:class "text-muted-foreground font-normal text-lg"}
                (str "(" (count (or students [])) ")")))
          ($ button/Button {:variant "outline"
                            :size "sm"
                            :on-click #(rf/dispatch [::fellows-events/open-add-fellow-modal])}
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
             "Add Fellow"))

       ;; Bulk action bar (appears when fellows are selected)
       (when (pos? selected-count)
         ($ bulk-action-bar {:selected-count selected-count
                             :program-managers program-managers
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
                "My Fellows")
             ($ button/Button {:variant (if showing-all? "secondary" "ghost")
                               :size "sm"
                               :class "rounded-l-none"
                               :on-click #(when-not showing-all? (toggle-show-all))
                               :disabled loading?}
                "All Fellows"))
          ($ :input {:type "text"
                     :placeholder "Search by name..."
                     :class "h-9 w-64 rounded-md border border-input bg-transparent px-3 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                     :value (or search-text "")
                     :onChange #(set-search-text! (.. % -target -value))})
          ($ :div {:class "flex-1"})
          ($ filter-popover {:program-managers program-managers
                             :schools schools
                             :selected-school selected-school
                             :selected-pm selected-pm})
          ($ button/Button {:variant "outline"
                            :size "sm"
                            :disabled (zero? (.-length search-filtered-js))
                            :on-click #(csv/download!
                                        (str "fellows-" (csv/today-iso) ".csv")
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
       ($ filter-pills {:selected-school selected-school
                        :selected-pm selected-pm
                        :program-managers program-managers})

       ;; Data table — no toolbar, just data
       ($ DataTable
          {:columns columns
           :data search-filtered-js
           :onRowClick handle-student-click
           :getRowHref get-student-href
           :pageSize 10
           :loading loading?
           :emptyMessage "No fellows found"}))))
