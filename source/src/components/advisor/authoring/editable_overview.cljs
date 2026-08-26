(ns components.advisor.authoring.editable-overview
  "Shared INLINE-EDITABLE Overview primitives for the dense-editor detail sheets (PRD 0011).
   The AI-generated Overview prose is editable in the dense editor, plain like main — NO
   amber ring, NO revert-to-source chip. Each prose field is a blur-committed text editor;
   bullet lists (Day to Day / Why This Fits) are editable add/remove/edit row lists. Every
   edit persists via the EXISTING override machinery on the REAL selection ref; clearing a
   field to empty routes to the revert callback so the AI source returns EXACTLY (the pool
   record is never mutated). The `on-set` / `on-revert` effects are INJECTED so these editors
   stay dumb + rendering is unit-testable.

   Slice B (0080) grew these as private helpers inside institution_detail_sheet; slices C/D
   (0081/0082 — apprenticeship + short-term) reuse the SAME UX, so they live here as one
   shared, pure (effects-injected) source of truth. The college sheet keeps its own copy
   (that slice is closed); new panels bind to these.

   These are plain `defn`s returning UIX elements — call them DIRECTLY as `(f {…})`, never
   via `$` (a Clojure-map prop passed through `$` to a non-`defui` fn resolves to nil)."
  (:require [clojure.string :as str]
            [uix.core :refer [$]]
            ["/gen/shadcn/components/ui/button" :as button]))

(defn editable-overview-field
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

(defn editable-overview-bullets
  "Editable bullet list (add / remove / edit rows), like main — used for Day to Day AND for
   the apprenticeship 'Why This Fits' bullets. Each row is a blur-committed text input; ×
   removes a row, \"+ Add\" appends an empty one. The WHOLE vector is persisted via `on-set`;
   clearing every row routes to `on-revert` so the AI source returns."
  [{:keys [title bullets data-field on-set on-revert]}]
  (let [bullets (vec bullets)
        commit  (fn [next]
                  (let [cleaned (vec (remove str/blank? next))]
                    (if (seq cleaned) (on-set cleaned) (on-revert))))]
    ($ :div {:class "space-y-2" :data-editable-field (or data-field "bullets")}
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

(defn overview-editor
  "Generic INLINE-EDITABLE Overview from a present-by-data display map (`overview-display`
   output: {:summary :credential-line :student-connection :caveat :day-to-day}). Renders nothing when
   `overview` is blank. Each field wires to the injected `set-f`/`rev-f`:
     (set-f field value)  — persist a field override
     (rev-f field)        — revert a field to source
   The 4 fields present-by-data; Day to Day is an editable bullet list, the rest textareas."
  [{:keys [overview set-f rev-f]}]
  (when (seq overview)
    ($ :div {:class "space-y-3"}
       ($ :h4 {:class "text-sm font-semibold"} "Overview")
       (when (:summary overview)
         (editable-overview-field
          {:label "Summary" :value (:summary overview)
           :on-set #(set-f :summary %) :on-revert #(rev-f :summary)}))
       (when (:credential-line overview)
         (editable-overview-field
          {:label "Credential" :value (:credential-line overview)
           :on-set #(set-f :credential-line %) :on-revert #(rev-f :credential-line)}))
       (when (:student-connection overview)
         (editable-overview-field
          {:label "Why It Fits" :value (:student-connection overview)
           :on-set #(set-f :student-connection %) :on-revert #(rev-f :student-connection)}))
       (when (:caveat overview)
         (editable-overview-field
          {:label "Good to Know" :value (:caveat overview)
           :on-set #(set-f :caveat %) :on-revert #(rev-f :caveat)}))
       (when (seq (:day-to-day overview))
         (editable-overview-bullets
          {:title "Day to Day" :bullets (:day-to-day overview) :data-field "day-to-day"
           :on-set #(set-f :day-to-day %) :on-revert #(rev-f :day-to-day)})))))
