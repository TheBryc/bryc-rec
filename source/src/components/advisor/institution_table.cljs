(ns components.advisor.institution-table
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [store.advisor.recommendations.events :as events]
            [components.advisor.authoring.selection :as sel]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/sortable-table" :as sortable]))

(defn find-by-id [items id]
  (first (filter #(= (:id %) id) items)))

(defn find-institution-in-pool
  "Find institution data from pool regardless of category (needed after reassignment)"
  [pool inst-id]
  (or (find-by-id (get-in pool [:institutions :safety]) inst-id)
      (find-by-id (get-in pool [:institutions :target]) inst-id)
      (find-by-id (get-in pool [:institutions :reach]) inst-id)))

(def ^:private bucket-order
  "Display ordering of the selection buckets within a :str-badge group's selected rows.
   Same-bucket rows therefore appear in bucket order — the invariant the drag→bucket
   position mapping (sel/drag-reorder-in-bucket) relies on."
  {"safety" 0 "target" 1 "reach" 2})

(defui institution-body-cells
  "The shared (non-drag) cells of an institution row: checkbox, name, location, programs."
  [{:keys [institution selected? on-toggle]}]
  (let [location (get institution :location {})
        programs (get institution :programs [])
        prog-count (count programs)]
    ($ :<>
       ;; Checkbox
       ($ table/TableCell {:class "w-10 pr-0"}
          ($ checkbox/Checkbox
             {:checked (boolean selected?)
              :on-checked-change (fn [_] (when on-toggle (on-toggle)))
              :on-click (fn [e] (.stopPropagation e))}))
       ;; Name
       ($ table/TableCell {:class "font-medium whitespace-normal"}
          (:institution-name institution))
       ;; Location
       ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
          (str (:city location) ", " (:state location)))
       ;; Programs
       ($ table/TableCell {:class "hidden md:table-cell"}
          (when (> prog-count 0)
            ($ badge/Badge {:variant "secondary" :class "text-xs"}
               (str prog-count " prog")))))))

(defui institution-row
  "One row of a :str-badge display group (issue 0062). STR is a read-only engine fact —
   there is NO per-row Category dropdown. Selected rows are DRAGGABLE (issue 0062 follow-up):
   dragging reorders the card only among its SAME selection-bucket siblings; select/reorder
   address the card's REAL selection bucket, so a card whose pool bucket ≠ its :str-badge
   still toggles/reorders in the correct bucket."
  [{:keys [institution inst-id selected? on-toggle on-open-detail]}]
  (let [row-props {:class "cursor-pointer hover:bg-muted/50"
                   :on-click (fn [e]
                               (.stopPropagation e)
                               (when on-open-detail (on-open-detail)))}
        body ($ institution-body-cells
                {:institution institution :selected? selected? :on-toggle on-toggle})]
    (if selected?
      ;; Selected rows are draggable (reorder within their own selection bucket)
      ($ sortable/SortableTableRow
         (merge row-props {:id (str inst-id)})
         ($ sortable/DragHandleCell)
         body)
      ;; Unselected rows are plain, with an empty drag-handle placeholder cell
      ($ table/TableRow row-props
         ($ table/TableCell {:class "w-10"})
         body))))

(defui institution-table
  "Table for ONE :str-badge display group (issue 0062). `institutions` are that group's
   schools, each tagged (via sel/flatten-pool-institutions) with its source pool bucket
   under ::pool-category. Selected schools list first — ordered by their REAL selection
   bucket + position so a drag maps cleanly to bucket order — then the unselected pool
   schools in pool order. A drag reorders a card ONLY among its same-selection-bucket
   siblings (sel/drag-reorder-in-bucket); a drop that would cross into another bucket's card
   is a no-op. Toggle + reorder address each card's real selection bucket."
  [{:keys [institutions selection api-client]}]
  (let [selected? (fn [inst] (sel/institution-selected? selection (:id inst)))
        sel-pos (fn [inst] (sel/institution-selection-position selection (:id inst)))
        selected-insts (->> institutions
                            (filter selected?)
                            (sort-by (fn [inst]
                                       (let [p (sel-pos inst)]
                                         [(get bucket-order (name (:category p)) 9)
                                          (:position p)]))))
        unselected-insts (remove selected? institutions)
        all-insts (concat selected-insts unselected-insts)
        display-selected-ids (mapv :id selected-insts)]
    ($ sortable/SortableTableProvider
       {:items (to-array (mapv str display-selected-ids))
        :on-reorder (fn [old-idx new-idx]
                      ;; Confine the drag to the moved card's OWN selection bucket; a
                      ;; cross-bucket-only drop returns nil (no dispatch = snaps back).
                      (when-let [move (sel/drag-reorder-in-bucket selection display-selected-ids old-idx new-idx)]
                        (rf/dispatch [::events/reorder-institutions
                                      (:category move) (:old-idx move) (:new-idx move) api-client])))}
       ($ table/Table
          ($ table/TableHeader
             ($ table/TableRow
                ($ table/TableHead {:class "w-10"} "")
                ($ table/TableHead {:class "w-10"} "")
                ($ table/TableHead "Name")
                ($ table/TableHead {:class "hidden sm:table-cell"} "Location")
                ($ table/TableHead {:class "hidden md:table-cell"} "Programs")))
          ($ table/TableBody
             (for [inst all-insts]
               (let [inst-id (:id inst)
                     is-selected (selected? inst)
                     bucket (sel/institution-toggle-category selection inst)]
                 ($ institution-row
                    {:key inst-id
                     :institution inst
                     :inst-id inst-id
                     :selected? is-selected
                     :on-toggle #(rf/dispatch [::events/toggle-institution-selection bucket inst-id api-client])
                     :on-open-detail #(rf/dispatch [::events/open-detail-sheet
                                                     {:type :institution :id inst-id :category bucket}])}))))))))
