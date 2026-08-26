(ns components.advisor.authoring.hide
  "PURE hide-a-block / section-gating support for the advisor-default authoring view
   (issue 0018 — S2). Data→data only: no effects, no components, no re-frame. Unit-tested
   through its public fns and reused by the authoring view so that hiding ANY section
   block — a program section (Salary/Careers/…) OR a School-anchored block (About This
   School / Costs) — goes through the EXISTING save-recommendation-customization →
   :hidden-sections → apply-ref-overrides path, applied to the REAL selection refs (never
   a fork of the override logic).

   The gating fns MIRROR the backend `apply-ref-overrides` :hidden-sections branch
   (student_ops_service.core.queries) so the WYSIWYG frontend render matches EXACTLY what
   the backend projection returns on reload: a hidden section is removed from the record's
   :sections vector (and its same-named data key dropped). This is what makes
   `rec-demo/school-sections` — which had IGNORED :hidden-sections — honor a School hide.")

;; ===========================================================================
;; School-anchored section keys (rendered ONCE per school; About + Costs)
;; ===========================================================================

(def school-section-keys
  "The School-anchored section-keys, in render order (rec-demo school-sections)."
  [:about-school :costs])

;; ===========================================================================
;; Read a record/ref's hidden set
;; ===========================================================================

(defn hidden-sections
  "PURE: the section-keys hidden on a record/ref (its :hidden-sections) as a set. nil-safe."
  [record]
  (set (:hidden-sections record)))

(defn hidden?
  "PURE: is `section-key` hidden on this record/ref?"
  [record section-key]
  (contains? (hidden-sections record) section-key))

;; ===========================================================================
;; Toggle — the pure ref mutation the hide affordance dispatches
;; ===========================================================================

(defn toggle-hidden
  "PURE: toggle `section-key` in a ref's :hidden-sections — adds it when absent, removes it
   when present. Pre-existing duplicates are de-duped. When the toggle empties the set, the
   :hidden-sections key is DISSOC'd entirely, so a fully-reverted ref is byte-identical to a
   never-hidden one (revert-to-source is exact — the sourced/computed value is restored)."
  [ref section-key]
  (let [cur (vec (distinct (:hidden-sections ref)))
        nxt (if (some #{section-key} cur)
              (vec (remove #{section-key} cur))
              (conj cur section-key))]
    (if (seq nxt)
      (assoc ref :hidden-sections nxt)
      (dissoc ref :hidden-sections))))

;; ===========================================================================
;; Gating — the school-sections FIX + program-section present-by-data
;; ===========================================================================

(defn section-shown?
  "PURE: should `section-key` render for `record`, honoring its present-by-data :sections?
   When the record carries a :sections vector, the section shows ONLY when it is a member
   — so a hidden section (removed from :sections by the backend override projection, or by
   `apply-hidden`) is gated OUT. When the record carries NO :sections (the hardcoded demo
   schools / old-shape records), there is nothing to gate on, so it shows (present-by-data).

   This is the fix that makes `rec-demo/school-sections` honor :hidden-sections: it had
   rendered About-This-School / Costs straight from the adapter, ignoring :sections, so
   hiding a School block silently no-op'd."
  [record section-key]
  (if (contains? record :sections)
    (boolean (some #{section-key} (:sections record)))
    true))

(defn apply-hidden
  "PURE frontend mirror of the backend `apply-ref-overrides` :hidden-sections branch
   (student_ops_service.core.queries): given a resolved `record` and a `hidden` coll of
   section-keys (the working, not-yet-reloaded session overrides), remove each hidden
   section's same-named data key AND strip it from :sections, so the Student-preview render
   matches EXACTLY what the backend projection returns on reload (WYSIWYG). A no-op when
   `hidden` is empty, so the demo path (no hides) is untouched."
  [record hidden]
  (let [hset (set hidden)]
    (if (seq hset)
      (cond-> (apply dissoc record hset)
        (contains? record :sections)
        (assoc :sections (vec (remove hset (:sections record)))))
      record)))

(defn edit-section-keys
  "PURE: the ordered section-keys to render in EDIT mode — the record's currently-shown
   sections UNION the ones the advisor has hidden, in `order` (the canonical section-order).
   So a hidden section STILL renders its 'Show block' ghost even after a reload has stripped
   it from the record (the section's data is gone, but its key is known from the ref's
   :hidden-sections and its heading is static). `shown` = the record's live :sections;
   `hidden` = the ref's :hidden-sections. Keys outside `order` are appended stably."
  [order shown hidden]
  (let [present (set (concat shown hidden))
        ordered (filter present order)
        extras  (remove (set order) (distinct (concat shown hidden)))]
    (vec (concat ordered extras))))

;; ===========================================================================
;; Locate a resolved card's ref in the working selection (by id) so a toggle updates
;; the RIGHT ref — never the display order, never a fork of the event logic.
;; ===========================================================================

(def ^:private college-categories [:safety :target :reach])

(defn find-institution-ref
  "PURE: locate an institution ref in a selection's :institutions buckets by id. Returns
   {:category <kw> :ref <inst-ref>} for the bucket that holds it, or nil when absent. Scans
   :safety→:target→:reach."
  [selection id]
  (some (fn [category]
          (some (fn [ref] (when (= (:id ref) id)
                            {:category category :ref ref}))
                (get-in selection [:institutions category] [])))
        college-categories))

(defn find-program-ref
  "PURE: locate a nested program ref (inside an institution ref's :programs) by program id.
   Returns {:category <kw> :institution-id <id> :ref <prog-ref>}, or nil when absent."
  [selection id]
  (some (fn [category]
          (some (fn [inst-ref]
                  (some (fn [prog-ref]
                          (when (= (:id prog-ref) id)
                            {:category category
                             :institution-id (:id inst-ref)
                             :ref prog-ref}))
                        (:programs inst-ref [])))
                (get-in selection [:institutions category] [])))
        college-categories))
