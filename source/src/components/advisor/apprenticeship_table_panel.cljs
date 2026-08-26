(ns components.advisor.apprenticeship-table-panel
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [re-frame.core :as rf]
            [components.advisor.coming-soon-button :as csb]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.advisor.authoring.override :as override]
            [components.advisor.authoring.editable-overview :as edit-ov]
            [components.advisor.institution-detail-sheet :refer [read-only-field read-only-bullets overview-display]]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/separator" :as separator]
            ["/gen/shadcn/components/ui/sortable-table" :as sortable]))

(defn find-by-id [items id]
  (first (filter #(= (:id %) id) items)))

(defn salary-amount
  "Nil-safe integer for a salary value: an int when the value is a number, else nil.
   Guards `.toLocaleString` — a nil / non-number salary (e.g. a custom entry) must never
   reach `(int …)`/`.toLocaleString`, which was one blank-render risk (0069)."
  [v]
  (when (number? v) (int v)))

(defn format-salary
  "Present-by-data $/year label for a numeric salary; nil for anything non-numeric."
  [v]
  (when-let [n (salary-amount v)]
    (str "$" (.toLocaleString n) "/year")))

(defui apprenticeship-detail-body
  "Props-driven body of the apprenticeship detail sheet. Present-by-data + nil-safe.
   Extracted from the sheet so it is drivable by a pure dom-server render test.

   0069 blank ROOT CAUSE: the live pool ships :overview as the NEW overview MAP
   ({:summary :credential-line :student-connection :caveat :day-to-day}).

   0081 (PRD 0011) — the AI Overview prose is now INLINE-EDITABLE in the dense editor, plain
   like main (NO revert chip). A MAP :overview renders the editable Overview (Credential / Why
   It Fits / Good to Know) wired to ::set-apprenticeship-section-edit (keyed by the flat ref
   :id; lands {:section-edits {:overview {<field> v}}}). nil ⇒ nothing. 'Why This Fits'
   (the top-level :bullets vector — NOT
   inside a section) is an editable bullet list wired to the :text-edits seam via
   ::set-apprenticeship-text-edit :bullets. READ-ONLY, unchanged: Title / Company / Location,
   Starting/Average salary, Requirements, Application URL. `app-id`/`api-client` inject the
   persistence effect; a render test may omit them (editors dispatch only on blur)."
  [{:keys [merged app-id api-client]}]
  (let [set-ov (fn [field v]
                 (rf/dispatch [::events/set-apprenticeship-section-edit
                               app-id :overview field v api-client]))
        rev-ov (fn [field]
                 (rf/dispatch [::events/revert-apprenticeship-section-edit
                               app-id :overview field api-client]))]
    ($ :div {:class "space-y-6 px-8 pb-8"}
     (read-only-field {:label "Title" :value (:apprenticeship-title merged)})
     (read-only-field {:label "Company" :value (:company-name merged)})
     (read-only-field {:label "Location" :value (str (or (:city merged) "") ", " (or (:state merged) ""))})

     ($ separator/Separator)

     ;; :overview — the canonical student-facing map ⇒ inline-editable; nil/blank omitted.
     (let [ov (:overview merged)]
       (when (map? ov)
         (edit-ov/overview-editor {:overview (overview-display merged)
                                   :set-f set-ov :rev-f rev-ov})))

     ;; Salary info (read-only) — nil-safe: only numeric salaries reach .toLocaleString.
     (let [starting (format-salary (:starting-salary-annual merged))
           average  (format-salary (:average-salary-annual merged))]
       (when (and (not= (:source merged) :custom) (or starting average))
         ($ :div {:class "bg-muted/30 rounded-lg p-4 space-y-1.5"}
            (when starting
              ($ :div {:class "text-sm"}
                 ($ :span {:class "text-muted-foreground"} "Starting Salary: ")
                 ($ :span {:class "font-medium"} starting)))
            (when average
              ($ :div {:class "text-sm"}
                 ($ :span {:class "text-muted-foreground"} "Average Salary: ")
                 ($ :span {:class "font-medium"} average))))))

     ;; "Why This Fits" — top-level :bullets, INLINE-EDITABLE via the :text-edits seam.
     (edit-ov/editable-overview-bullets
      {:title "Why This Fits" :bullets (:bullets merged) :data-field "why-this-fits"
       :on-set (fn [v] (rf/dispatch [::events/set-apprenticeship-text-edit
                                     app-id :bullets v api-client]))
       :on-revert (fn [] (rf/dispatch [::events/revert-apprenticeship-text-edit
                                       app-id :bullets api-client]))})
     (read-only-bullets {:title "Requirements" :bullets (:requirements merged)})

     ($ :div
        ($ :label {:class "text-xs font-medium text-muted-foreground"} "Application URL")
        (if (not-empty (:application-url merged))
          ($ :a {:href (:application-url merged) :target "_blank" :rel "noopener noreferrer"
                 :class "text-sm text-primary underline mt-1.5 block break-all"}
             (:application-url merged))
          ($ :p {:class "text-sm text-muted-foreground italic mt-1.5"} "None"))))))

(defui apprenticeship-detail-sheet [{:keys [api-client]}]
  ;; 0081 — the AI Overview prose + "Why This Fits" bullets are INLINE-EDITABLE (PRD 0011);
  ;; salary / requirements / URL stay READ-ONLY (sourced context). The advisor still
  ;; selects/deselects + reorders from the table.
  (let [detail-sheet (use-subscribe [::subs/detail-sheet])
        is-open? (and (use-subscribe [::subs/detail-sheet-open?])
                      (= (:type detail-sheet) :apprenticeship))
        pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])

        app-id (:id detail-sheet)
        apprenticeship (when (and is-open? app-id)
                         (find-by-id (get pool :apprenticeships []) app-id))
        text-edits (when (and is-open? app-id selection)
                     (get-in selection [:text-edits :apprenticeships app-id]))
        ;; The apprenticeship ref carries the working overrides (:section-edits for the
        ;; Overview prose + :text-edits {:bullets …} for "Why This Fits"). apply-overrides
        ;; projects both so the sheet shows the EFFECTIVE (WYSIWYG) content — exactly what
        ;; apply-ref-overrides resolves on reload / the student sees.
        app-ref (when (and is-open? app-id selection)
                  (find-by-id (get-in selection [:apprenticeships] []) app-id))
        merged (when apprenticeship
                 (-> (merge apprenticeship text-edits)
                     (override/apply-overrides app-ref)))]

    ($ sheet/Sheet
       {:open (boolean is-open?)
        :on-open-change (fn [open?]
                          (when-not open? (rf/dispatch [::events/close-detail-sheet])))}
       ($ sheet/SheetContent {:class "w-[560px] sm:max-w-[560px] overflow-y-auto" :side "right"}
          ($ sheet/SheetHeader {:class "pb-6 px-8 pt-8"}
             ($ sheet/SheetTitle {:class "text-lg"} (or (:apprenticeship-title merged) "Apprenticeship Details"))
             ($ sheet/SheetDescription
                (when merged (str (or (:company-name merged) "") " — " (or (:city merged) "") ", " (or (:state merged) "")))))

          (when (and is-open? merged)
            ($ apprenticeship-detail-body {:merged merged :app-id app-id :api-client api-client}))))))

(defui apprenticeship-table-panel [{:keys [api-client]}]
  (let [pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])
        apprenticeships (vec (get pool :apprenticeships []))
        selected-ids (mapv :id (get-in selection [:apprenticeships] []))
        selected-set (set selected-ids)
        selected-positions (into {} (map-indexed (fn [pos id] [id pos]) selected-ids))
        ;; Selected first, then unselected
        selected-apps (keep (fn [sid] (find-by-id apprenticeships sid)) selected-ids)
        unselected-apps (remove #(contains? selected-set (:id %)) apprenticeships)
        all-apps (concat selected-apps unselected-apps)]

    ($ :div {:class "space-y-2"}
       ($ :div {:class "flex items-center justify-between mb-2"}
          ($ :h3 {:class "text-sm font-semibold"} "Apprenticeships")
          ($ csb/coming-soon-button {:label "+ Add Apprenticeship"}))
       (when (seq all-apps)
         ($ sortable/SortableTableProvider
            {:items (to-array (mapv str selected-ids))
             :on-reorder (fn [old-idx new-idx]
                           (rf/dispatch [::events/reorder-apprenticeships
                                         old-idx new-idx api-client]))}
            ($ table/Table
               ($ table/TableHeader
                  ($ table/TableRow
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead {:class "w-10"} "")
                     ($ table/TableHead "Title")
                     ($ table/TableHead {:class "hidden sm:table-cell"} "Company")
                     ($ table/TableHead {:class "hidden md:table-cell"} "Location")))
               ($ table/TableBody
                  (for [app all-apps]
                    (let [app-id (:id app)
                          is-selected (contains? selected-set app-id)
                          text-edits (get-in selection [:text-edits :apprenticeships app-id])
                          merged (merge app text-edits)
                          row-click (fn [_]
                                      (rf/dispatch [::events/open-detail-sheet
                                                    {:type :apprenticeship :id app-id}]))]
                      (if is-selected
                        ($ sortable/SortableTableRow
                           {:key app-id :id (str app-id)
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ sortable/DragHandleCell)
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked true
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-apprenticeship-selection
                                                                     app-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:apprenticeship-title merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:company-name merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden md:table-cell whitespace-normal"}
                              (str (:city merged) ", " (:state merged))))
                        ($ table/TableRow
                           {:key app-id
                            :class "cursor-pointer hover:bg-muted/50"
                            :on-click row-click}
                           ($ table/TableCell {:class "w-10"})
                           ($ table/TableCell {:class "w-10 pr-0"}
                              ($ checkbox/Checkbox
                                 {:checked false
                                  :on-checked-change (fn [_]
                                                       (rf/dispatch [::events/toggle-apprenticeship-selection
                                                                     app-id api-client]))
                                  :on-click (fn [e] (.stopPropagation e))}))
                           ($ table/TableCell {:class "font-medium whitespace-normal"}
                              (:apprenticeship-title merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden sm:table-cell whitespace-normal"}
                              (:company-name merged))
                           ($ table/TableCell {:class "text-muted-foreground hidden md:table-cell whitespace-normal"}
                              (str (:city merged) ", " (:state merged)))))))))))

       ;; Detail sheet
       ($ apprenticeship-detail-sheet {:api-client api-client}))))
