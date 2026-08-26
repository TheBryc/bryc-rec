(ns components.advisor.short-term-program-table-panel
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [components.advisor.coming-soon-button :as csb]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.advisor.authoring.override :as override]
            [components.advisor.authoring.editable-overview :as edit-ov]
            [components.advisor.institution-detail-sheet :refer [read-only-bullets overview-display]]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/separator" :as separator]
            ["/gen/shadcn/components/ui/sortable-table" :as sortable]))

(defn find-by-id [items id]
  (first (filter #(= (:id %) id) items)))

(defn derive-career-paths [program]
  (or (when (vector? (:career-paths program)) (:career-paths program))
      (let [primary (get-in program [:primary-career :title])
            others (mapv :title (:other-careers program))]
        (vec (remove empty? (into (if primary [primary] []) others))))))

(defui short-term-program-detail-body
  "Props-driven body of the short-term detail sheet. Present-by-data. Extracted so it is
   drivable by a pure dom-server render test.

   0082 — the CURRENT :overview MAP prose (Credential / Why It Fits / Good to Know / Day to
   Day) is now INLINE-EDITABLE in the dense editor (PRD 0011), plain like main — NO revert
   chip. It wires to the ::set-short-term-section-edit / ::revert-short-term-section-edit
   override events (keyed by the flat short-term ref :id), landing {:section-edits {:overview
   {<field> v}}} on the REAL ref (apply-ref-overrides deep-merges it) and reverting EXACTLY
   on clear. The NEW :overview map keys only (never legacy :personalized-overview). Editors
   render present-by-data via the shared overview-display seam → editable-overview; no
   renderable :overview ⇒ no Overview block. Deterministic context — award badge, Career
   Paths, Program URL — stays READ-ONLY. `program-id`/`api-client` inject the persistence
   effect; a render test may omit them (editors dispatch only on blur, never during SSR)."
  [{:keys [merged career-paths program-id api-client]}]
  (let [set-f (fn [field v]
                (rf/dispatch [::events/set-short-term-section-edit
                              program-id :overview field v api-client]))
        rev-f (fn [field]
                (rf/dispatch [::events/revert-short-term-section-edit
                              program-id :overview field api-client]))]
    ($ :div {:class "space-y-6 px-8 pb-8"}
     ;; Read-only header info
     (when (:award-level-name merged)
       ($ badge/Badge {:variant "secondary" :class "text-xs"}
          (:award-level-name merged)))

     ($ separator/Separator)

     ;; CURRENT Overview (:overview map, mirrors the viewer) — INLINE-EDITABLE, present-by-data.
     (edit-ov/overview-editor {:overview (overview-display merged) :set-f set-f :rev-f rev-f})

     (read-only-bullets {:title "Career Paths" :bullets career-paths})

     ($ separator/Separator)

     ;; Program URL (read-only)
     ($ :div
        ($ :label {:class "text-xs font-medium text-muted-foreground"} "Program URL")
        (if (not-empty (:program-url merged))
          ($ :a {:href (:program-url merged) :target "_blank" :rel "noopener noreferrer"
                 :class "text-sm text-primary underline mt-1.5 block break-all"}
             (:program-url merged))
          ($ :p {:class "text-sm text-muted-foreground italic mt-1.5"} "None"))))))

(defui short-term-program-detail-sheet [{:keys [api-client]}]
  ;; 0082 — the :overview map prose is INLINE-EDITABLE (PRD 0011); Career Paths + Program URL
  ;; stay READ-ONLY (sourced context). The advisor still selects/deselects + reorders from the
  ;; table; only the AI Overview prose is editable here.
  (let [detail-sheet (use-subscribe [::subs/detail-sheet])
        is-open? (and (use-subscribe [::subs/detail-sheet-open?])
                      (= (:type detail-sheet) :short-term-program))
        pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])

        prog-id (:id detail-sheet)
        program (when (and is-open? prog-id)
                  (find-by-id (get pool :short-term-programs []) prog-id))
        text-edits (when (and is-open? prog-id selection)
                     (get-in selection [:text-edits :short-term-programs prog-id]))
        ;; The short-term ref carries the working :section-edits (the Overview override trail).
        ;; Deep-merge them via apply-overrides so the sheet shows the EFFECTIVE (WYSIWYG)
        ;; Overview — exactly what apply-ref-overrides resolves on reload / the student sees.
        prog-ref (when (and is-open? prog-id selection)
                   (find-by-id (get-in selection [:short-term-programs] []) prog-id))
        merged (when program
                 (-> (merge program text-edits)
                     (override/apply-overrides prog-ref)))
        career-paths (when merged (derive-career-paths merged))]

    ($ sheet/Sheet
       {:open (boolean is-open?)
        :on-open-change (fn [open?]
                          (when-not open? (rf/dispatch [::events/close-detail-sheet])))}
       ($ sheet/SheetContent {:class "w-[560px] sm:max-w-[560px] overflow-y-auto" :side "right"}
          ($ sheet/SheetHeader {:class "pb-6 px-8 pt-8"}
             ($ sheet/SheetTitle {:class "text-lg"} (or (:program-title merged) "Program Details"))
             ($ sheet/SheetDescription
                (when merged
                  (str (or (:institution-name merged) "")
                       (when (:city merged) (str " — " (:city merged) ", " (:state merged)))))))

          (when (and is-open? merged)
            ($ short-term-program-detail-body {:merged merged :career-paths career-paths
                                               :program-id prog-id :api-client api-client}))))))

(defui short-term-program-table-panel [{:keys [api-client]}]
  (let [pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])
        programs (vec (get pool :short-term-programs []))
        selected-ids (mapv :id (get-in selection [:short-term-programs] []))
        selected-set (set selected-ids)
        selected-positions (into {} (map-indexed (fn [pos id] [id pos]) selected-ids))
        selected-progs (keep (fn [sid] (find-by-id programs sid)) selected-ids)
        unselected-progs (remove #(contains? selected-set (:id %)) programs)
        all-progs (concat selected-progs unselected-progs)]

    ($ :div {:class "space-y-2"}
       ($ :div {:class "flex items-center justify-between mb-2"}
          ($ :h3 {:class "text-sm font-semibold"} "Short-Term Programs")
          ($ csb/coming-soon-button {:label "+ Add Program"}))
       (when (seq all-progs)
         ($ sortable/SortableTableProvider
            {:items (to-array (mapv str selected-ids))
             :on-reorder (fn [old-idx new-idx]
                           (rf/dispatch [::events/reorder-short-term-programs
                                         old-idx new-idx api-client]))}
            ($ table/Table
               ($ table/TableHeader
                  ($ table/TableRow
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead "Program")
                     ($ table/TableHead {:class "hidden sm:table-cell"} "Institution")
                     ($ table/TableHead {:class "hidden md:table-cell"} "Award")))
               ($ table/TableBody
                  (for [prog all-progs]
                    (let [prog-id (:id prog)
                          is-selected (contains? selected-set prog-id)
                          text-edits (get-in selection [:text-edits :short-term-programs prog-id])
                          merged (merge prog text-edits)
                          row-click (fn [_]
                                      (rf/dispatch [::events/open-detail-sheet
                                                    {:type :short-term-program :id prog-id}]))]
                      (if is-selected
                        ($ sortable/SortableTableRow
                           {:key prog-id :id (str prog-id)
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ sortable/DragHandleCell)
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked true
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-short-term-program-selection
                                                                     prog-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:program-title merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:institution-name merged))
                           ($ table/TableCell {:class "hidden md:table-cell"}
                              (when (:award-level-name merged)
                                ($ badge/Badge {:variant "secondary" :class "text-xs"}
                                   (:award-level-name merged)))))
                        ($ table/TableRow
                           {:key prog-id
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ table/TableCell {:class "w-10"})
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked false
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-short-term-program-selection
                                                                     prog-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:program-title merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:institution-name merged))
                           ($ table/TableCell {:class "hidden md:table-cell"}
                              (when (:award-level-name merged)
                                ($ badge/Badge {:variant "secondary" :class "text-xs"}
                                   (:award-level-name merged))))))))))))

       ;; Detail sheet
       ($ short-term-program-detail-sheet {:api-client api-client}))))
