(ns components.advisor.scholarship-table-panel
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [components.advisor.coming-soon-button :as csb]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.advisor.authoring.override :as override]
            [components.advisor.authoring.editable-overview :as edit-ov]
            [components.advisor.institution-detail-sheet :refer [read-only-field]]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/separator" :as separator]
            ["/gen/shadcn/components/ui/sortable-table" :as sortable]))

(defn find-by-id [items id]
  (first (filter #(= (:id %) id) items)))

(defui scholarship-detail-body
  "Props-driven body of the scholarship detail sheet. Present-by-data + nil-safe. Extracted
   from the sheet so it is drivable by a pure dom-server render test (same as the
   apprenticeship/short-term bodies).

   0085 (PRD 0011, Daryl 22-Jul: AI-generated ⇒ editable) — the three AI-generated prose
   fields are now INLINE-EDITABLE in the dense editor, plain like main (NO revert chip),
   reusing the shared `editable-overview-field`:
     Why This Scholarship Fits ← :personalized-explanation
     Description               ← :description
     Application Tips          ← :application-tips
   These are FLAT top-level scholarship fields (NOT inside a section map), so they persist via
   the :text-edits seam on the scholarship ref (keyed by the flat :id) — an edit dispatches
   [::set-scholarship-text-edit schol-id <field> v api-client] on blur (landing
   {:text-edits {<field> v}}, flat-merged by apply-ref-overrides) and
   [::revert-scholarship-text-edit schol-id <field> api-client] when cleared (reverting EXACTLY
   to the AI source). READ-ONLY, unchanged: Scholarship Name, Award Amount, Selection Criteria,
   Deadline, Application URL. `schol-id`/`api-client` inject the persistence effect; a render
   test may omit the api-client (editors dispatch only on blur)."
  [{:keys [merged schol-id api-client]}]
  (let [set-edit (fn [field v]
                   (rf/dispatch [::events/set-scholarship-text-edit schol-id field v api-client]))
        rev-edit (fn [field]
                   (rf/dispatch [::events/revert-scholarship-text-edit schol-id field api-client]))]
    ($ :div {:class "space-y-6 px-8 pb-8"}
       (read-only-field {:label "Scholarship Name" :value (:name merged)})
       (read-only-field {:label "Award Amount" :value (:award merged)})

       ($ separator/Separator)

       (edit-ov/editable-overview-field
        {:label "Why This Scholarship Fits" :value (:personalized-explanation merged)
         :on-set #(set-edit :personalized-explanation %)
         :on-revert #(rev-edit :personalized-explanation)})
       (edit-ov/editable-overview-field
        {:label "Description" :value (:description merged)
         :on-set #(set-edit :description %)
         :on-revert #(rev-edit :description)})
       (read-only-field {:label "Selection Criteria" :value (:selection-criteria merged)})
       (edit-ov/editable-overview-field
        {:label "Application Tips" :value (:application-tips merged)
         :on-set #(set-edit :application-tips %)
         :on-revert #(rev-edit :application-tips)})

       ($ separator/Separator)

       (read-only-field {:label "Deadline" :value (:deadline merged)})
       ($ :div
          ($ :label {:class "text-xs font-medium text-muted-foreground"} "Application URL")
          (if (not-empty (:application-url merged))
            ($ :a {:href (:application-url merged) :target "_blank" :rel "noopener noreferrer"
                   :class "text-sm text-primary underline mt-1.5 block break-all"}
               (:application-url merged))
            ($ :p {:class "text-sm text-muted-foreground italic mt-1.5"} "None"))))))

(defui scholarship-detail-sheet [{:keys [api-client]}]
  ;; 0085 — the three AI-generated prose fields (Why This Scholarship Fits / Description /
  ;; Application Tips) are INLINE-EDITABLE (PRD 0011); Name / Award / Selection Criteria /
  ;; Deadline / URL stay READ-ONLY (sourced context). The advisor still selects/deselects +
  ;; reorders from the table.
  (let [detail-sheet (use-subscribe [::subs/detail-sheet])
        is-open? (and (use-subscribe [::subs/detail-sheet-open?])
                      (= (:type detail-sheet) :scholarship))
        pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])

        schol-id (:id detail-sheet)
        scholarship (when (and is-open? schol-id)
                      (find-by-id (get pool :scholarships []) schol-id))
        text-edits (when (and is-open? schol-id selection)
                     (get-in selection [:text-edits :scholarships schol-id]))
        ;; The scholarship ref carries the working :text-edits prose overrides; apply-overrides
        ;; projects them so the sheet shows the EFFECTIVE (WYSIWYG) content — exactly what
        ;; apply-ref-overrides resolves on reload / the student sees.
        schol-ref (when (and is-open? schol-id selection)
                    (find-by-id (get-in selection [:scholarships] []) schol-id))
        merged (when scholarship
                 (-> (merge scholarship text-edits)
                     (override/apply-overrides schol-ref)))]

    ($ sheet/Sheet
       {:open (boolean is-open?)
        :on-open-change (fn [open?]
                          (when-not open? (rf/dispatch [::events/close-detail-sheet])))}
       ($ sheet/SheetContent {:class "w-[560px] sm:max-w-[560px] overflow-y-auto" :side "right"}
          ($ sheet/SheetHeader {:class "pb-6 px-8 pt-8"}
             ($ sheet/SheetTitle {:class "text-lg"} (or (:name merged) "Scholarship Details"))
             ($ sheet/SheetDescription (when (:award merged) (str "Award: " (:award merged)))))

          (when (and is-open? merged)
            ($ scholarship-detail-body {:merged merged :schol-id schol-id :api-client api-client}))))))

(defui scholarship-table-panel [{:keys [api-client]}]
  (let [pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])
        scholarships (vec (get pool :scholarships []))
        selected-ids (mapv :id (get-in selection [:scholarships] []))
        selected-set (set selected-ids)
        selected-positions (into {} (map-indexed (fn [pos id] [id pos]) selected-ids))
        selected-schols (keep (fn [sid] (find-by-id scholarships sid)) selected-ids)
        unselected-schols (remove #(contains? selected-set (:id %)) scholarships)
        all-schols (concat selected-schols unselected-schols)]

    ($ :div {:class "space-y-2"}
       ($ :div {:class "flex items-center justify-between mb-2"}
          ($ :h3 {:class "text-sm font-semibold"} "Scholarships")
          ($ csb/coming-soon-button {:label "+ Add Scholarship"}))
       (when (seq all-schols)
         ($ sortable/SortableTableProvider
            {:items (to-array (mapv str selected-ids))
             :on-reorder (fn [old-idx new-idx]
                           (rf/dispatch [::events/reorder-scholarships
                                         old-idx new-idx api-client]))}
            ($ table/Table
               ($ table/TableHeader
                  ($ table/TableRow
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead "Name")
                     ($ table/TableHead {:class "hidden sm:table-cell"} "Award")
                     ($ table/TableHead {:class "hidden md:table-cell"} "Deadline")))
               ($ table/TableBody
                  (for [schol all-schols]
                    (let [schol-id (:id schol)
                          is-selected (contains? selected-set schol-id)
                          text-edits (get-in selection [:text-edits :scholarships schol-id])
                          merged (merge schol text-edits)
                          row-click (fn [_]
                                      (rf/dispatch [::events/open-detail-sheet
                                                    {:type :scholarship :id schol-id}]))]
                      (if is-selected
                        ($ sortable/SortableTableRow
                           {:key schol-id :id (str schol-id)
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ sortable/DragHandleCell)
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked true
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-scholarship-selection
                                                                     schol-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:name merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:award merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden md:table-cell whitespace-normal"}
                              (:deadline merged)))
                        ($ table/TableRow
                           {:key schol-id
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ table/TableCell {:class "w-10"})
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked false
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-scholarship-selection
                                                                     schol-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:name merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:award merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden md:table-cell whitespace-normal"}
                              (:deadline merged))))))))))

       ;; Detail sheet
       ($ scholarship-detail-sheet {:api-client api-client}))))
