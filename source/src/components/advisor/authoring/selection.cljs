(ns components.advisor.authoring.selection
  "PURE selection-transform support for the advisor-default authoring view (issue
   0020 — S4). Data→data only: no effects, no components, no re-frame. Unit-tested
   through its public fns and reused by the authoring view so that select / deselect /
   reorder go through the EXISTING position-based selection events
   (::toggle-*-selection, ::move-*-up/down) applied to the REAL selection model —
   never the STR display order, and never a fork of the event logic.

   The authoring view renders the WYSIWYG :resolved projection, whose colleges are
   re-grouped for DISPLAY by :str-badge (Open→Safety→Target→Reach). That display order
   is NOT the selection bucket order the reorder events operate on, so these fns locate
   each card's TRUE selection category + position by scanning the selection itself.")

(def college-categories
  "The selection institution buckets, in the order the reorder events address them."
  [:safety :target :reach])

(defn index-of-id
  "PURE: 0-based position of the first ref whose :id = id within items, or nil when
   absent. nil (not 0) for a missing item, so a stale/deselected card is never treated
   as the first element."
  [items id]
  (first (keep-indexed (fn [i it] (when (= (:id it) id) i)) items)))

(defn selected-id?
  "PURE: is id present in the given selection ref list (each ref a map with :id)?
   Drives the toggle's checked state for a resolved card."
  [refs id]
  (boolean (some #(= (:id %) id) refs)))

(defn institution-selection-position
  "PURE: locate an institution id inside a selection's :institutions buckets. Returns
   {:category <kw> :position <idx> :count <n>} for the bucket that holds it, or nil if
   the id is in no bucket (a deselected item, or a resolved card gone stale). Scans
   :safety→:target→:reach. The authoring view feeds :category + :position straight to
   the EXISTING ::move-institution-up/down + ::toggle-institution-selection events, so
   reorder acts on the real selection model regardless of the STR display order."
  [selection id]
  (some (fn [category]
          (let [refs (get-in selection [:institutions category] [])]
            (when-let [idx (index-of-id refs id)]
              {:category category :position idx :count (count refs)})))
        college-categories))

(defn institution-selected?
  "PURE: is the institution id selected in ANY selection bucket?"
  [selection id]
  (some? (institution-selection-position selection id)))

;; ---------------------------------------------------------------------------
;; Issue 0062 — the dense editor now DISPLAYS colleges grouped by :str-badge (the
;; SAME grouping the student viewer uses), while the SELECTION persistence stays
;; bucketed :safety/:target/:reach. A school whose pool bucket ≠ its :str-badge (e.g.
;; Nicholls: :safety bucket, "target" badge) must still toggle/reorder against its
;; REAL selection bucket. These PURE fns give the editor that mapping WITHOUT forking
;; institution-selection-position or the position-based selection events.
;; ---------------------------------------------------------------------------

(defn pool-category
  "PURE: the source pool bucket an institution was flattened from (see
   flatten-pool-institutions), or nil. Read back the ::pool-category tag."
  [inst]
  (::pool-category inst))

(defn flatten-pool-institutions
  "PURE: flatten the pool's :safety/:target/:reach institution buckets into ONE seq for
   :str-badge display grouping, tagging each institution with its SOURCE pool bucket under
   ::pool-category (read via `pool-category`) so the dense editor's toggle/reorder can
   address the REAL selection bucket even after the display re-groups by :str-badge.
   Buckets concatenate in :safety→:target→:reach order, preserving each bucket's pool
   order. nil pool → []."
  [pool]
  (vec (mapcat (fn [category]
                 (mapv #(assoc % ::pool-category category)
                       (get-in pool [:institutions category] [])))
               college-categories)))

(defn institution-toggle-category
  "PURE: the selection bucket a card's toggle must address — its REAL selection bucket
   when the card is already selected (so a deselect removes it from the right bucket),
   else its source pool bucket (::pool-category) when unselected (so a select/promote adds
   it to the bucket it was pooled in). Reuses institution-selection-position; never forks
   it. nil when neither is known."
  [selection inst]
  (or (:category (institution-selection-position selection (:id inst)))
      (pool-category inst)))

(defn drag-reorder-in-bucket
  "PURE (issue 0062 follow-up — Daryl): translate a DRAG within a displayed :str-badge
   group — whose SELECTED cards may span several selection buckets — into a reorder
   CONFINED to the moved card's OWN selection bucket, reusing the position-based
   ::reorder-institutions event (move-item) without forking it.

   `display-selected-ids` is the group's SELECTED ids in display order (same-bucket cards
   appear in bucket order — the dense editor sorts selected rows by [bucket, position]).
   `old-idx`/`new-idx` are the SortableTable drag indices: old-idx = original index of the
   dragged card, new-idx = original index of the card dropped ON (both into the items
   array), interpreted by move-item's double-splice (arrayMove) semantics.

   Returns {:category <bucket-kw> :old-idx <bucket-pos> :new-idx <bucket-pos>} to feed
   ::reorder-institutions, or nil for a NO-OP — including a drop that only crosses
   OTHER-bucket cards (the card keeps its bucket + position; only same-bucket siblings in
   the group reorder relative to each other). The moved card can NEVER change bucket.

   Mapping: the moved card's new rank AMONG its same-bucket siblings in the display
   (`s-new` = how many same-bucket cards precede the drop slot in the post-removal display
   list) is converted to a bucket index via that bucket's same-badge selected positions, so
   move-item lands the card after exactly `s-new` same-bucket cards. Cards of other buckets
   in the group are never counted, so a drag that crosses only them is a no-op."
  [selection display-selected-ids old-idx new-idx]
  (let [ids (vec display-selected-ids)
        moved-id (get ids old-idx)
        pos (institution-selection-position selection moved-id)]
    (when pos
      (let [category (:category pos)
            bucket-old (:position pos)
            same-bucket? (fn [id]
                           (= category (:category (institution-selection-position selection id))))
            without (into (subvec ids 0 old-idx) (subvec ids (inc old-idx)))
            n (max 0 (min new-idx (count without)))
            s-old (count (filter same-bucket? (subvec ids 0 (min old-idx (count ids)))))
            s-new (count (filter same-bucket? (subvec without 0 n)))]
        (when (not= s-new s-old)
          ;; This bucket's same-badge (== same display group) selected positions, in
          ;; post-removal bucket coordinates (drop the moved card, shift indices above it).
          (let [q (->> ids
                       (filter same-bucket?)
                       (map #(:position (institution-selection-position selection %)))
                       (remove #(= % bucket-old))
                       (map #(if (> % bucket-old) (dec %) %))
                       sort vec)
                bucket-new (if (< s-new (count q))
                             (nth q s-new)
                             (inc (peek q)))]
            {:category category :old-idx bucket-old :new-idx bucket-new}))))))

(defn list-selection-position
  "PURE: {:position <idx> :count <n>} of id within a flat selection ref list
   (scholarships / apprenticeships / short-term-programs), or nil when absent. Feeds
   the EXISTING position-based ::move-*-up/down events for those record types."
  [refs id]
  (when-let [idx (index-of-id refs id)]
    {:position idx :count (count refs)}))

(defn can-move-up?
  "PURE: may the item at position move up? Only when it is not already first (and the
   position is known)."
  [position]
  (boolean (and (some? position) (pos? position))))

(defn can-move-down?
  "PURE: may the item at position move down within a list of `total` items? Only when
   it is not already last (and both are known)."
  [position total]
  (boolean (and (some? position) (some? total) (< position (dec total)))))

;; ===========================================================================
;; Promote from the full pool (Edit view). The FULL pool is in the store; only the
;; resolved top-K schools render. These PURE fns compute what's AVAILABLE TO PROMOTE
;; — the pool minus the current selection — so the view can render compact 'Show to
;; student' cards whose control dispatches the EXISTING ::toggle-*-selection events.
;; Data→data; the sub ::unselected-institutions is the same computation, kept in sync.
;; ===========================================================================

(defn unselected-institutions
  "PURE: given the immutable `pool` and the working `selection`, the per-category pool
   institutions NOT selected in ANY bucket — the 'available to promote' set. For a
   bucket with N pool institutions and K selected (K<N), returns exactly the N−K
   non-selected records, preserving pool order. A school reassigned into another
   category's selection is excluded from its original pool bucket (cross-category).
   Returns nil when either the pool or the selection is absent (present-by-data —
   nothing to render). Mirrors the ::unselected-institutions sub by construction."
  [pool selection]
  (when (and pool selection)
    (let [selected-ids (set (concat
                             (mapv :id (get-in selection [:institutions :safety] []))
                             (mapv :id (get-in selection [:institutions :target] []))
                             (mapv :id (get-in selection [:institutions :reach] []))))]
      (reduce
       (fn [acc category]
         (assoc acc category
                (vec (remove #(contains? selected-ids (:id %))
                             (get-in pool [:institutions category] [])))))
       {}
       college-categories))))

(defn selected-program-ids
  "PURE: the set of program ids currently selected under institution `inst-id` in
   `category` of the selection (empty set when the school, or its :programs, is absent)."
  [selection category inst-id]
  (let [refs (get-in selection [:institutions category] [])
        inst-ref (first (filter #(= (:id %) inst-id) refs))]
    (set (mapv :id (:programs inst-ref)))))

(defn unselected-programs
  "PURE: the school's pool programs NOT already selected — the programs available to
   promote WITHIN a school. `pool-programs` is the school's full pool :programs;
   `selected-ids` a set/seq of already-selected program ids. For M pool programs and J
   selected (J<M) returns the M−J non-selected, preserving pool order."
  [pool-programs selected-ids]
  (let [sel-set (set selected-ids)]
    (vec (remove #(contains? sel-set (:id %)) pool-programs))))

(defn find-pool-institution
  "PURE: the pool institution record (carrying its FULL :programs) for `inst-id` in
   `category`'s pool bucket, or nil. The resolved card only carries SELECTED programs,
   so the view looks the school up in the pool to enumerate its unselected programs."
  [pool category inst-id]
  (first (filter #(= (:id %) inst-id)
                 (get-in pool [:institutions category] []))))

;; ===========================================================================
;; resolve-selection — apply the working selection to the immutable pool → the SAME
;; :resolved shape the student query returns. A cljs PORT of the backend
;; `ai.obney.bryc.student-ops-service.core.queries/resolve-selection` (kept in lock-step;
;; see that ns for the canonical definition). The in-page Student preview (0051) seeds
;; from THIS — computed from the LIVE ::pool + ::selection — so it reflects the advisor's
;; latest selection with NO backend round-trip, instead of the stale [:advisor
;; :recommendations :resolved] the query wrote only on load. Data→data; pure.
;; ===========================================================================

(defn find-by-id
  "PURE: the first item in `items` whose :id = id, or nil. Mirrors queries/find-by-id."
  [items id]
  (first (filter #(= (:id %) id) items)))

(defn apply-ref-overrides
  "PURE: merge a selection ref's advisor overrides over a SOURCED pool record — a cljs
   mirror of queries/apply-ref-overrides. `:section-edits` deep-merge (one level) into the
   named data map; `:text-edits` are flat top-level overrides applied last; `:hidden-sections`
   drop a section (and its same-named data key) from the record + :sections. No override →
   the sourced record verbatim."
  [record ref]
  (let [with-sections (reduce-kv (fn [r k edits] (update r k merge edits))
                                 record (or (:section-edits ref) {}))
        with-text     (if-let [te (:text-edits ref)] (merge with-sections te) with-sections)]
    (if-let [hidden (seq (:hidden-sections ref))]
      (let [hset (set hidden)]
        (cond-> (apply dissoc with-text hidden)
          (contains? with-text :sections)
          (assoc :sections (vec (remove hset (:sections with-text))))))
      with-text)))

(defn resolve-institution
  "PURE: resolve an institution ref to its full pool record with advisor overrides applied,
   programs filtered to the SELECTED ones (in selection order). Mirrors queries/resolve-institution."
  [pool-institutions inst-ref]
  (let [inst (find-by-id pool-institutions (:id inst-ref))
        inst-with-edits (apply-ref-overrides inst inst-ref)
        resolved-programs (mapv (fn [prog-ref]
                                  (apply-ref-overrides
                                   (find-by-id (:programs inst) (:id prog-ref))
                                   prog-ref))
                                (:programs inst-ref))]
    (assoc inst-with-edits :programs resolved-programs)))

(defn resolve-selection
  "PURE: the working `selection` applied to the immutable `pool` → the `:resolved` shape the
   student view renders. Institutions resolve across ALL pool buckets (a cross-category
   reassignment still finds its source); flat lists (scholarships/apprenticeships/short-term)
   resolve by id. A cljs port of queries/resolve-selection — keep the two in lock-step."
  [pool selection]
  (let [all-pool-insts (concat (get-in pool [:institutions :safety] [])
                               (get-in pool [:institutions :target] [])
                               (get-in pool [:institutions :reach] []))
        resolve-category (fn [category]
                           (mapv #(resolve-institution all-pool-insts %)
                                 (get-in selection [:institutions category] [])))]
    {:institutions {:safety (resolve-category :safety)
                    :target (resolve-category :target)
                    :reach  (resolve-category :reach)}
     :scholarships (mapv (fn [ref] (apply-ref-overrides
                                    (find-by-id (:scholarships pool) (:id ref)) ref))
                         (:scholarships selection []))
     :apprenticeships (mapv (fn [ref] (apply-ref-overrides
                                       (find-by-id (:apprenticeships pool) (:id ref)) ref))
                            (:apprenticeships selection []))
     :short-term-programs (mapv (fn [ref] (apply-ref-overrides
                                           (find-by-id (:short-term-programs pool) (:id ref)) ref))
                                (:short-term-programs selection []))
     :custom-sections (:custom-sections selection)}))
