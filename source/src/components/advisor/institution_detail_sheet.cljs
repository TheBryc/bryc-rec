(ns components.advisor.institution-detail-sheet
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [components.advisor.coming-soon-button :as csb]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.advisor.authoring.override :as override]
            [components.advisor.institution-table :refer [find-institution-in-pool]]
            [components.advisor.bullet-text :refer [linkify-text]]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/separator" :as separator]
            ["/gen/shadcn/components/ui/sortable-list" :as sortable]))

(defn find-by-id [items id]
  (first (filter #(= (:id %) id) items)))

(defn derive-career-paths [program]
  (or (when (vector? (:career-paths program)) (:career-paths program))
      (let [primary (get-in program [:primary-career :title])
            others (mapv :title (:other-careers program))]
        (vec (remove empty? (into (if primary [primary] []) others))))))

;; ---- Read-only display helpers (shared by the dense-editor detail sheets) ----
;; The dense editor is SELECTION-only (issue 0042): sourced figures and Notion-sourced
;; prose are shown read-only for context, never edited. As soon as an advisor edits sourced
;; content the citation is meaningless, so these render values with no edit affordance.

;; Prose and bullets render URLs as links (`linkify-text`) — retained from the
;; advisor-side URL-rendering work merged from main.
(defn read-only-field [{:keys [label value]}]
  ($ :div
     ($ :label {:class "text-xs font-medium text-muted-foreground"} label)
     ($ :p {:class "text-sm leading-relaxed mt-1.5 whitespace-pre-wrap"}
        (if (not-empty value) (linkify-text value) ($ :span {:class "text-muted-foreground italic"} "None")))))

(defn read-only-bullets [{:keys [title bullets]}]
  ($ :div {:class "space-y-3"}
     ($ :h4 {:class "text-sm font-semibold"} title)
     (if (seq (remove empty? (or bullets [])))
       ($ :ul {:class "list-disc list-inside text-sm space-y-1"}
          (for [[idx b] (map-indexed vector bullets)]
            ($ :li {:key idx} (linkify-text b))))
       ($ :p {:class "text-sm text-muted-foreground italic"} "None"))))

;; ---- Program Overview: the CURRENT :overview map (0065) ----
;; The student view's Overview is the NEW :overview map (adapter.cljs:371-383 shape:
;; {:credential-line :student-connection :caveat :day-to-day}) — NOT the legacy
;; :personalized-overview / :outcome-bullet / :career-summary strings. This seam reads
;; ONLY the :overview submap, present-by-data, so the dense editor shows what the student
;; actually sees. READ-ONLY (piece 1) — making :student-connection editable is piece 2.

(defn overview-display
  "Pure present-by-data view of a program's NEW :overview map for the dense-editor program
   column. Reads ONLY the :overview submap — the legacy :personalized-overview /
   :outcome-bullet / :career-summary strings never leak in. Returns a map of the PRESENT
   overview fields, or nil when there is no renderable overview content (no :overview map,
   or every field blank) so the caller omits the Overview block entirely."
  [program]
  (when-let [ov (not-empty (:overview program))]
    (let [day-to-day (vec (remove empty? (or (:day-to-day ov) [])))]
      (not-empty
       (cond-> {}
         (not-empty (:summary ov))             (assoc :summary (:summary ov))
         (not-empty (:credential-line ov))    (assoc :credential-line (:credential-line ov))
         (not-empty (:student-connection ov))  (assoc :student-connection (:student-connection ov))
         (not-empty (:caveat ov))              (assoc :caveat (:caveat ov))
         (seq day-to-day)                      (assoc :day-to-day day-to-day))))))

(defui program-overview-section
  "READ-ONLY Overview built from the CURRENT :overview map (0065), present-by-data.
   Renders nothing when the program has no renderable :overview content."
  [{:keys [program]}]
  (when-let [ov (overview-display program)]
    ($ :div {:class "space-y-3"}
       ($ :h4 {:class "text-sm font-semibold"} "Overview")
       (when (:summary ov)
         (read-only-field {:label "Summary" :value (:summary ov)}))
       (when (:credential-line ov)
         (read-only-field {:label "Credential" :value (:credential-line ov)}))
       (when (:student-connection ov)
         (read-only-field {:label "Why It Fits" :value (:student-connection ov)}))
       (when (:caveat ov)
         (read-only-field {:label "Good to Know" :value (:caveat ov)}))
       (when (seq (:day-to-day ov))
         (read-only-bullets {:title "Day to Day" :bullets (:day-to-day ov)})))))

;; ---- Program Overview: INLINE-EDITABLE (0080, piece 2) ----
;; The AI-generated Overview prose is editable in the dense editor, plain like main —
;; NO amber ring, NO revert-to-source chip. Each prose field is a blur-committed text
;; editor; Day to Day is an editable bullet list (add / remove / edit rows). Every edit
;; persists via the EXISTING override machinery: ::set-program-section-edit lands
;; {:section-edits {:overview {<field> v}}} on the REAL program ref (apply-ref-overrides
;; deep-merges it), and clearing a field to empty routes to ::revert-program-section-edit
;; so the AI source returns EXACTLY (the pool record is never mutated). The `on-set` /
;; `on-revert` effects are INJECTED so the editors stay dumb + rendering is unit-testable.

(defn- editable-overview-field
  "One inline-editable Overview PROSE field (Credential / Why It Fits / Good to Know).
   Uncontrolled textarea keyed by the effective value (WYSIWYG resets on re-resolve).
   Commits on blur: non-blank → `on-set`; cleared to blank → `on-revert` (AI source
   returns). No revert chip."
  [{:keys [label value on-set on-revert]}]
  ($ :div {:data-editable-field "prose"}
     ($ :label {:class "text-xs font-medium text-muted-foreground"} label)
     ($ :textarea
        {:key (str value)
         :default-value (str value)
         :rows 2
         :class "w-full mt-1.5 px-2 py-1.5 text-sm leading-relaxed border rounded-md resize-y bg-background"
         :on-blur (fn [e]
                    (let [v (.. e -target -value)]
                      (when (not= v (str value))
                        (if (str/blank? v) (on-revert) (on-set v)))))})))

(defn- editable-overview-bullets
  "Editable Day-to-Day bullet list (add / remove / edit rows), like main. Each row is a
   blur-committed text input; × removes a row, \"+ Add\" appends an empty one. The WHOLE
   vector is persisted as a section-edit ({:overview {:day-to-day v}}); clearing every row
   routes to `on-revert` so the AI source returns."
  [{:keys [title bullets on-set on-revert]}]
  (let [bullets (vec bullets)
        commit  (fn [next]
                  (let [cleaned (vec (remove str/blank? next))]
                    (if (seq cleaned) (on-set cleaned) (on-revert))))]
    ($ :div {:class "space-y-2" :data-editable-field "day-to-day"}
       ($ :h4 {:class "text-sm font-semibold"} title)
       ($ :div {:class "space-y-2"}
          (for [[idx b] (map-indexed vector bullets)]
            ($ :div {:key (str idx "-" b) :class "flex gap-2 items-center"}
               ($ :input
                  {:type "text"
                   :default-value b
                   :class "flex-1 px-2 py-1 text-sm border rounded-md bg-background"
                   :on-blur (fn [e]
                              (let [v (.. e -target -value)]
                                (when (not= v b)
                                  (commit (assoc bullets idx v)))))})
               ($ button/Button
                  {:variant "ghost" :size "sm" :class "h-auto py-1 px-2 flex-shrink-0"
                   :on-click (fn [_]
                               (commit (into (subvec bullets 0 idx) (subvec bullets (inc idx)))))}
                  "×"))))
       ($ button/Button
          {:variant "outline" :size "sm" :class "w-full text-xs"
           :on-click (fn [_] (on-set (conj bullets "")))}
          "+ Add"))))

(defui program-overview-editor
  "INLINE-EDITABLE Overview built from the CURRENT :overview map (0065), present-by-data.
   Renders nothing when the program has no renderable :overview content. Each field wires
   directly to the EXISTING program-ref override events (set on blur, revert on clear)."
  [{:keys [program category inst-id prog-id api-client]}]
  (when-let [ov (overview-display program)]
    (let [set-f (fn [field v]
                  (rf/dispatch [::events/set-program-section-edit
                                category inst-id prog-id :overview field v api-client]))
          rev-f (fn [field]
                  (rf/dispatch [::events/revert-program-section-edit
                                category inst-id prog-id :overview field api-client]))]
      ($ :div {:class "space-y-3"}
         ($ :h4 {:class "text-sm font-semibold"} "Overview")
         (when (:summary ov)
           (editable-overview-field
              {:label "Summary" :value (:summary ov)
               :on-set #(set-f :summary %) :on-revert #(rev-f :summary)}))
         (when (:credential-line ov)
           (editable-overview-field
              {:label "Credential" :value (:credential-line ov)
               :on-set #(set-f :credential-line %) :on-revert #(rev-f :credential-line)}))
         (when (:student-connection ov)
           (editable-overview-field
              {:label "Why It Fits" :value (:student-connection ov)
               :on-set #(set-f :student-connection %) :on-revert #(rev-f :student-connection)}))
         (when (:caveat ov)
           (editable-overview-field
              {:label "Good to Know" :value (:caveat ov)
               :on-set #(set-f :caveat %) :on-revert #(rev-f :caveat)}))
         (when (seq (:day-to-day ov))
           (editable-overview-bullets
              {:title "Day to Day" :bullets (:day-to-day ov)
               :on-set #(set-f :day-to-day %) :on-revert #(rev-f :day-to-day)}))))))

(defui program-detail-item
  [{:keys [program pool-id selected? on-toggle on-click-program active?]}]
  ($ :div {}
     ;; Compact row
     ($ :div {:class (str "flex items-center gap-2 py-2 px-3 rounded-md cursor-pointer "
                          (if active? "bg-accent" "hover:bg-muted/50"))
              :on-click (fn [_] (when on-click-program (on-click-program)))}
        ;; Drag handle for selected, spacer for unselected
        (if selected?
          ($ sortable/DragHandle {:class "flex-shrink-0"})
          ($ :div {:class "w-7 flex-shrink-0"}))
        ;; Checkbox
        ($ :div {:class "flex-shrink-0"
                 :on-click (fn [e] (.stopPropagation e))}
           ($ checkbox/Checkbox
              {:checked selected?
               :on-checked-change (fn [_] (when on-toggle (on-toggle)))}))
        ;; Title
        ($ :span {:class "text-sm font-medium truncate flex-1"} (:program-title program))
        ;; Award badge
        (when (:award-level-name program)
          ($ badge/Badge {:variant "secondary" :class "text-xs flex-shrink-0"}
             (:award-level-name program))))))

;; ---- Program column (left side inside the sheet) — READ-ONLY (0042) ----

(defui program-editor-column
  "Left column inside the sheet. The AI-generated Overview prose (:overview map, 0065) is
   INLINE-EDITABLE (0080) — Credential / Why It Fits / Good to Know / Day to Day, wired to
   the program-ref override events. Program URL is ALSO editable (0090) — advisors fix
   broken/missing links; it rides the SAME generic :text-edits override seam (a blank/missing
   URL renders an EMPTY field so a link can be added). Career Paths (deterministic role list)
   stays READ-ONLY (a sourced citation, never edited)."
  [{:keys [merged career-paths institution category inst-id prog-id api-client on-close]}]
  ($ :div {:class "w-[440px] flex-shrink-0 border-r flex flex-col h-full"}
     ;; Header
     ($ :div {:class "flex items-center justify-between px-6 pt-8 pb-4 flex-shrink-0"}
        ($ :div {:class "min-w-0 flex-1"}
           ($ :h2 {:class "text-lg font-semibold truncate"}
              (or (:program-title merged) "Program Details"))
           (when merged
             ($ :p {:class "text-sm text-muted-foreground truncate mt-0.5"}
                (str (or (:institution-name institution) "")
                     (when (:award-level-name merged)
                       (str " — " (:award-level-name merged)))))))
        ($ button/Button
           {:variant "ghost" :size "sm" :class "h-8 w-8 p-0 flex-shrink-0 ml-2"
            :on-click (fn [_] (on-close))}
           "\u2715"))

     ($ separator/Separator)

     ;; Body — scrollable, read-only
     ($ :div {:class "space-y-5 px-6 py-5 flex-1 overflow-y-auto"}
        ;; 0080 — the CURRENT Overview (:overview map, 0065) is now inline-EDITABLE, NOT the
        ;; legacy :personalized-overview. The dead Expected-Outcomes (:outcome-bullet) /
        ;; Career-Summary (:career-summary) blocks are gone (the student sees Salary/Careers).
        ($ program-overview-editor {:program merged :category category
                                    :inst-id inst-id :prog-id prog-id :api-client api-client})
        ;; Career Paths = deterministic role list, READ-ONLY (a sourced citation).
        (read-only-bullets {:title "Career Paths" :bullets career-paths})
        ($ separator/Separator)
        ;; 0090 — Program URL is INLINE-EDITABLE (some sourced links are broken/route back to
        ;; the same page). It rides the SAME generic program-ref :text-edits override seam as
        ;; the Overview prose, so no new store/backend plumbing. NO when-let guard: a blank/
        ;; missing URL renders an EMPTY editable field so the advisor can add one.
        (editable-overview-field
           {:label "Program URL"
            :value (:program-url merged)
            :on-set    #(rf/dispatch [::events/set-program-text-edit
                                      category inst-id prog-id :program-url % api-client])
            :on-revert #(rf/dispatch [::events/revert-program-text-edit
                                      category inst-id prog-id :program-url api-client])}))))

;; ---- Main sheet ----

(defui institution-detail-sheet [{:keys [api-client]}]
  (let [detail-sheet (use-subscribe [::subs/detail-sheet])
        is-open? (use-subscribe [::subs/detail-sheet-open?])
        pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])
        prog-sheet (use-subscribe [::subs/program-detail-sheet])
        prog-open? (use-subscribe [::subs/program-detail-sheet-open?])

        ;; Only render content when we have institution type
        is-institution? (= (:type detail-sheet) :institution)
        inst-id (:id detail-sheet)
        category (:category detail-sheet)

        ;; Look up institution data from pool (cross-category)
        institution (when (and is-institution? inst-id pool)
                      (find-institution-in-pool pool inst-id))
        location (get institution :location {})
        pool-programs (or (:programs institution) [])

        ;; Selection state
        inst-refs (when (and is-institution? selection category)
                    (get-in selection [:institutions category] []))
        inst-ref (when inst-refs
                   (first (filter #(= (:id %) inst-id) inst-refs)))
        selected-prog-ids (when inst-ref (mapv :id (:programs inst-ref [])))

        ;; Text edits — 0065 dropped the school-level :why-fits-bullets / :financial-bullets
        ;; reads (dead legacy prose the student view no longer uses). We still dissoc those
        ;; keys so any stale saved school-level edit can't leak into a program's merge.
        text-edits (when (and is-institution? selection category inst-id)
                     (get-in selection [:text-edits category inst-id]))
        prog-text-edits (apply dissoc (or text-edits {}) [:why-fits-bullets :financial-bullets])

        ;; Program detail state
        active-prog-id (:program-id prog-sheet)
        active-program (when active-prog-id (find-by-id pool-programs active-prog-id))
        active-prog-edits (get prog-text-edits active-prog-id)
        ;; The program ref carries the working :section-edits (the Overview override trail).
        ;; Deep-merge them onto the pool program so the sheet shows the EFFECTIVE Overview
        ;; (WYSIWYG) — exactly what apply-ref-overrides resolves on reload / the student sees.
        active-prog-ref (when (and inst-ref active-prog-id)
                          (first (filter #(= (:id %) active-prog-id) (:programs inst-ref []))))
        active-merged (when active-program
                        (-> (if active-prog-edits (merge active-program active-prog-edits) active-program)
                            (override/apply-overrides active-prog-ref)))
        active-career-paths (when active-merged (derive-career-paths active-merged))]

    ($ sheet/Sheet
       {:open (boolean (and is-open? is-institution?))
        :on-open-change (fn [open?]
                          (when-not open?
                            (rf/dispatch [::events/close-detail-sheet])))}
       ($ sheet/SheetContent
          {:class (str "sm:max-w-none overflow-hidden transition-[width] duration-300 "
                       (if (and prog-open? active-merged)
                         "w-[1000px]"
                         "w-[560px]"))
           :side "right"}

          ;; Two-column flex layout
          ($ :div {:class "flex h-full"}

             ;; Left column: program editor (conditionally rendered)
             (when (and prog-open? active-merged)
               ($ program-editor-column
                  {:merged active-merged
                   :career-paths active-career-paths
                   :institution institution
                   :category category
                   :inst-id inst-id
                   :prog-id active-prog-id
                   :api-client api-client
                   :on-close #(rf/dispatch [::events/close-program-detail-sheet])}))

             ;; Right column: institution content (always rendered)
             ($ :div {:class "w-[560px] flex-shrink-0 overflow-y-auto h-full"}
                ($ sheet/SheetHeader {:class "pb-6 px-8 pt-8"}
                   ($ sheet/SheetTitle {:class "text-lg"}
                      (or (:institution-name institution) "Institution Details"))
                   ($ sheet/SheetDescription
                      (when institution
                        (str (:city location) ", " (:state location)))))

                (when (and is-institution? institution)
                  ($ :div {:class "space-y-6 px-8 pb-8"}

                     ;; Issue 0062 — Category is a read-only engine fact (:str-badge); the
                     ;; manual STR re-label dropdown was removed (advisors curate WHICH
                     ;; schools show + their order, not the STR label).

                     ;; Programs Section
                     (let [selected-set (set (or selected-prog-ids []))
                           selected-programs (keep (fn [sel-id]
                                                     (let [prog (find-by-id pool-programs sel-id)
                                                           edits (get prog-text-edits sel-id)]
                                                       (when prog (if edits (merge prog edits) prog))))
                                                   selected-prog-ids)
                           unselected-programs (remove #(contains? selected-set (:id %)) pool-programs)
                           open-prog-sheet (fn [pid]
                                            (rf/dispatch [::events/open-program-detail-sheet
                                                          {:program-id pid
                                                           :institution-id inst-id
                                                           :category category}]))]
                       ($ :div {:class "space-y-1"}
                          ($ :h3 {:class "text-sm font-semibold mb-2"}
                             (str "Programs (" (count selected-prog-ids) "/" (count pool-programs) ")"))

                          ;; Selected programs: sortable
                          (when (seq selected-programs)
                            ($ sortable/SortableListProvider
                               {:items (to-array (mapv #(str (:id %)) selected-programs))
                                :on-reorder (fn [old-idx new-idx]
                                              (rf/dispatch [::events/reorder-programs
                                                            category inst-id old-idx new-idx api-client]))}
                               (for [prog selected-programs]
                                 (let [pid (:id prog)]
                                   ($ sortable/SortableListItem
                                      {:key (str pid) :id (str pid)}
                                      ($ program-detail-item
                                         {:program prog
                                          :pool-id pid
                                          :selected? true
                                          :active? (= pid active-prog-id)
                                          :on-toggle #(rf/dispatch [::events/toggle-program-selection
                                                                    category inst-id pid api-client])
                                          :on-click-program #(open-prog-sheet pid)}))))))

                          ;; Unselected programs
                          (when (seq unselected-programs)
                            ($ :div {:class (when (seq selected-programs) "mt-1 border-t pt-1")}
                               (for [prog unselected-programs]
                                 (let [pid (:id prog)]
                                   ($ program-detail-item
                                      {:key pid
                                       :program prog
                                       :pool-id pid
                                       :selected? false
                                       :active? (= pid active-prog-id)
                                       :on-toggle #(rf/dispatch [::events/toggle-program-selection
                                                                  category inst-id pid api-client])
                                       :on-click-program #(open-prog-sheet pid)})))))

                          ;; Add Custom Program — DISABLED pending scope agreement
                          ;; ('Coming soon' hover; Cameron: revert the add-custom
                          ;; workaround).
                          ($ csb/coming-soon-button {:label "+ Add Custom Program"
                                                     :class "w-full mt-2"})))

                     ($ separator/Separator)

                     ;; 0065 — "Why This School Fits" (:why-fits-bullets) and "Financial Aid"
                     ;; (:financial-bullets) removed: dead legacy prose. The student view is
                     ;; the 6 About-This-School tiles + "What It Costs You", not these bullets.

                     ;; Admissions data (read-only)
                     (when-let [admissions (:admissions institution)]
                       ($ :div {:class "bg-muted/30 rounded-lg p-4 space-y-1.5"}
                          ($ :h4 {:class "text-sm font-semibold"} "Admissions")
                          (when (:acceptance-rate admissions)
                            ($ :div {:class "text-sm text-muted-foreground"}
                               (str "Acceptance Rate: " (.toFixed (* 100 (:acceptance-rate admissions)) 1) "%")))
                          (when (:avg-gpa admissions)
                            ($ :div {:class "text-sm text-muted-foreground"}
                               (str "Average GPA: " (.toFixed (:avg-gpa admissions) 2))))))))))))))
