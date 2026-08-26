(ns components.advisor.authoring.selection-test
  "Issue 0020 — S4. Tests for the PURE selection-transform support fns the advisor
   authoring view uses to dispatch the EXISTING position-based selection/reorder
   events against the REAL selection model (never the display order, never a fork of
   the event logic). Behavior is exercised through the public fns only (data→data),
   so it survives refactors of the view."
  (:require [cljs.test :refer [deftest testing is]]
            [components.advisor.authoring.selection :as sel]))

;; ===========================================================================
;; index-of-id — the reorder position for a flat ref list
;; ===========================================================================

(deftest index-of-id-finds-position
  (testing "0-based position of the ref whose :id matches"
    (is (= 0 (sel/index-of-id [{:id "a"} {:id "b"} {:id "c"}] "a")))
    (is (= 1 (sel/index-of-id [{:id "a"} {:id "b"} {:id "c"}] "b")))
    (is (= 2 (sel/index-of-id [{:id "a"} {:id "b"} {:id "c"}] "c"))))
  (testing "absent id → nil (not 0, so a missing item is never treated as first)"
    (is (nil? (sel/index-of-id [{:id "a"} {:id "b"}] "z")))
    (is (nil? (sel/index-of-id [] "a")))))

;; ===========================================================================
;; institution-selection-position — locate an institution in the SELECTION buckets
;; ===========================================================================

(def sample-selection
  {:institutions {:safety [{:id "s1"} {:id "s2"}]
                  :target [{:id "t1"} {:id "t2"} {:id "t3"}]
                  :reach  [{:id "r1"}]}
   :scholarships [{:id "sch1"} {:id "sch2"}]
   :apprenticeships [{:id "ap1"}]
   :short-term-programs [{:id "st1"} {:id "st2"}]})

(deftest institution-selection-position-scans-buckets
  (testing "finds the item's real selection category + 0-based position + bucket count"
    (is (= {:category :safety :position 0 :count 2}
           (sel/institution-selection-position sample-selection "s1")))
    (is (= {:category :safety :position 1 :count 2}
           (sel/institution-selection-position sample-selection "s2")))
    (is (= {:category :target :position 2 :count 3}
           (sel/institution-selection-position sample-selection "t3")))
    (is (= {:category :reach :position 0 :count 1}
           (sel/institution-selection-position sample-selection "r1"))))
  (testing "an id in no bucket (e.g. a deselected item, or a stale resolved card) → nil"
    (is (nil? (sel/institution-selection-position sample-selection "gone")))
    (is (nil? (sel/institution-selection-position nil "s1")))))

;; ===========================================================================
;; list-selection-position — position + count for a flat selection ref list
;; ===========================================================================

(deftest list-selection-position-for-flat-lists
  (testing "scholarships / apprenticeships / short-term reorder position + count"
    (is (= {:position 0 :count 2}
           (sel/list-selection-position (:scholarships sample-selection) "sch1")))
    (is (= {:position 1 :count 2}
           (sel/list-selection-position (:short-term-programs sample-selection) "st2")))
    (is (= {:position 0 :count 1}
           (sel/list-selection-position (:apprenticeships sample-selection) "ap1"))))
  (testing "absent id → nil"
    (is (nil? (sel/list-selection-position (:scholarships sample-selection) "nope")))
    (is (nil? (sel/list-selection-position [] "x")))))

;; ===========================================================================
;; selected-id? — is a resolved card still in the selection (drives toggle state)
;; ===========================================================================

(deftest selected-id?-membership
  (testing "true when the id is present in the ref list, false otherwise"
    (is (true?  (sel/selected-id? [{:id "a"} {:id "b"}] "a")))
    (is (false? (sel/selected-id? [{:id "a"} {:id "b"}] "z")))
    (is (false? (sel/selected-id? [] "a")))
    (is (false? (sel/selected-id? nil "a")))))

(deftest institution-selected?-across-buckets
  (testing "true when the institution id is in ANY selection bucket"
    (is (true?  (sel/institution-selected? sample-selection "t2")))
    (is (false? (sel/institution-selected? sample-selection "gone")))))

;; ===========================================================================
;; Issue 0062 — the dense editor DISPLAYS colleges grouped by :str-badge but PERSISTS
;; selection in the :safety/:target/:reach buckets. A school whose pool bucket ≠ its
;; :str-badge (e.g. Nicholls: :safety bucket, "target" badge) must still toggle/reorder
;; against its REAL selection bucket. These guard flatten-pool-institutions,
;; institution-toggle-category, and the reorder-maps-to-bucket-position invariant.
;; ===========================================================================

(def mismatch-pool
  ;; Nicholls carries a "target" :str-badge but is pooled in :safety (acceptance-inclusive
  ;; verdict). SLU is a genuine :target-bucket target school. Both display under the
  ;; "target" STR group, yet persist in DIFFERENT selection buckets.
  {:institutions
   {:safety [{:id "saf1" :str-badge {:key "safety"}}
             {:id "nicholls" :str-badge {:key "target"}}]
    :target [{:id "slu" :str-badge {:key "target"}}]
    :reach  [{:id "rch1" :str-badge {:key "reach"}}]}})

(deftest flatten-pool-tags-source-bucket
  (testing "every institution is flattened once, in :safety→:target→:reach order, tagged with its pool bucket"
    (let [flat (sel/flatten-pool-institutions mismatch-pool)]
      (is (= ["saf1" "nicholls" "slu" "rch1"] (mapv :id flat)) "flattened in bucket order")
      (is (= :safety (sel/pool-category (first flat))))
      (is (= :safety (sel/pool-category (nth flat 1))) "Nicholls' SOURCE bucket is :safety despite its target badge")
      (is (= :target (sel/pool-category (nth flat 2))))
      (is (= :reach  (sel/pool-category (nth flat 3))))))
  (testing "nil pool → empty (present-by-data)"
    (is (= [] (sel/flatten-pool-institutions nil)))))

(deftest institution-toggle-category-resolves-real-bucket
  (let [flat (sel/flatten-pool-institutions mismatch-pool)
        nicholls (nth flat 1)                       ; :safety bucket, "target" badge
        slu (nth flat 2)]                           ; :target bucket, "target" badge
    (testing "UNSELECTED card toggles into its SOURCE pool bucket (not its str-badge group)"
      (let [empty-sel {:institutions {:safety [] :target [] :reach []}}]
        (is (= :safety (sel/institution-toggle-category empty-sel nicholls))
            "selecting Nicholls (shown under Target) adds it to the :safety bucket")
        (is (= :target (sel/institution-toggle-category empty-sel slu)))))
    (testing "SELECTED card toggles against its REAL selection bucket"
      (let [sel-state {:institutions {:safety [{:id "nicholls"}] :target [] :reach []}}]
        (is (= :safety (sel/institution-toggle-category sel-state nicholls))
            "deselecting Nicholls removes it from :safety where it truly lives")))))

(deftest reorder-maps-to-real-bucket-position
  (testing "a target-BADGE school living in the :safety bucket reorders in :safety at its true position"
    ;; Display 'Target' group = [nicholls(:safety#1), slu(:target#0)]. Reordering Nicholls
    ;; must act on the :safety bucket at position 1 — NOT the displayed group index, and NOT
    ;; the :target bucket it visually sits beside.
    (let [selection {:institutions {:safety [{:id "saf1"} {:id "nicholls"}]
                                    :target [{:id "slu"}]
                                    :reach  []}}
          pos (sel/institution-selection-position selection "nicholls")]
      (is (= {:category :safety :position 1 :count 2} pos)
          "reorder addresses the real bucket :safety at index 1 (up/down swap within :safety)")
      (is (true?  (sel/can-move-up? (:position pos))) "Nicholls (idx 1) can move up within :safety")
      (is (false? (sel/can-move-down? (:position pos) (:count pos))) "it is last in :safety → cannot move down")
      ;; SLU, sharing the Target DISPLAY group, addresses a DIFFERENT bucket entirely.
      (is (= :target (:category (sel/institution-selection-position selection "slu")))
          "the neighbor in the same display group reorders in :target, proving no cross-bucket bleed"))))

;; ===========================================================================
;; Issue 0062 follow-up (Daryl) — DRAG-to-reorder restored in the dense editor, SCOPED
;; to within a single selection bucket. A displayed :str-badge group can mix buckets, so
;; drag-reorder-in-bucket maps a SortableTable drop to a reorder CONFINED to the moved
;; card's own bucket; a drop that only crosses OTHER-bucket cards is a no-op (the card
;; never changes bucket). Display order of a bucket's cards = bucket order (the table
;; sorts selected rows by [bucket, position]).
;; ===========================================================================

(def drag-selection
  ;; :safety bucket [A B C D] (B,D are target-badge); :target bucket [E F] (target-badge).
  ;; The "target" DISPLAY group's selected ids, in table order [safety-by-pos, target-by-pos]:
  ;;   [B(:safety#1) D(:safety#3) E(:target#0) F(:target#1)].
  {:institutions {:safety [{:id "A"} {:id "B"} {:id "C"} {:id "D"}]
                  :target [{:id "E"} {:id "F"}]
                  :reach  []}})

(def target-group-display-ids ["B" "D" "E" "F"])

(deftest drag-reorder-within-a-bucket
  (testing "dragging B (:safety#1) onto D (:safety#3) reorders WITHIN :safety, landing B after D"
    ;; SortableTable: old-idx = items index of the dragged card (B=0);
    ;; new-idx = items index of the card dropped ON (D=1).
    (is (= {:category :safety :old-idx 1 :new-idx 3}
           (sel/drag-reorder-in-bucket drag-selection target-group-display-ids 0 1))
        "maps to ::reorder-institutions :safety 1 3 → move-item [A B C D] → [A C D B]"))
  (testing "dragging F (:target#1) onto E (:target#0) reorders WITHIN :target, landing F before E"
    (is (= {:category :target :old-idx 1 :new-idx 0}
           (sel/drag-reorder-in-bucket drag-selection target-group-display-ids 3 2))
        "maps to ::reorder-institutions :target 1 0 → move-item [E F] → [F E]")))

(deftest drag-across-buckets-is-a-no-op
  (testing "dragging E (:target) to the TOP of the group (crossing only :safety cards B,D) does NOT move it"
    ;; old-idx = E = 2; new-idx = top card B = 0. E crosses no :target sibling → no-op,
    ;; so E stays in :target at its position — a card NEVER changes bucket via drag.
    (is (nil? (sel/drag-reorder-in-bucket drag-selection target-group-display-ids 2 0))
        "cross-bucket-only drop returns nil (no dispatch = snaps back, no bucket change)"))
  (testing "dragging E (:target) onto D (:safety) — still above its :target sibling F — is a no-op"
    ;; old-idx = E = 2; drop onto D (=1). E's rank among :target cards is unchanged (F still
    ;; below it), so no reorder fires and E stays in :target.
    (is (nil? (sel/drag-reorder-in-bucket drag-selection target-group-display-ids 2 1))
        "E crosses only a :safety card, not its :target sibling F → no bucket change"))
  (testing "an unknown / unselected dragged id → nil (nothing to move)"
    (is (nil? (sel/drag-reorder-in-bucket drag-selection ["ghost" "D"] 0 1)))))

;; ===========================================================================
;; can-move-up? / can-move-down? — bounds guards for the reorder buttons
;; ===========================================================================

(deftest reorder-bounds-guards
  (testing "up is disabled only at the top of the list"
    (is (false? (sel/can-move-up? 0)))
    (is (true?  (sel/can-move-up? 1)))
    (is (false? (sel/can-move-up? nil))))
  (testing "down is disabled only at the bottom of the list"
    (is (true?  (sel/can-move-down? 0 3)))
    (is (true?  (sel/can-move-down? 1 3)))
    (is (false? (sel/can-move-down? 2 3)) "last position can't move down")
    (is (false? (sel/can-move-down? 0 1)) "a single-item list can't move down")
    (is (false? (sel/can-move-down? nil 3)))))

;; ===========================================================================
;; unselected-institutions — the FULL pool minus what's already selected. This is
;; the 'available to promote' set the advisor authoring Edit view renders below each
;; selected group so a non-selected pool school can be shown to the student. For a
;; bucket with N pool institutions and K selected (K<N), the unselected set is EXACTLY
;; the N−K non-selected ones, in pool order. Mirrors the ::unselected-institutions sub.
;; ===========================================================================

(def sample-pool
  {:institutions {:safety [{:id "s1" :name "S-One"} {:id "s2" :name "S-Two"} {:id "s3" :name "S-Three"}]
                  :target [{:id "t1"} {:id "t2"} {:id "t3"} {:id "t4"}]
                  :reach  [{:id "r1"} {:id "r2"}]}})

(deftest unselected-institutions-is-N-minus-K
  (testing "with N pool schools in a bucket and K<N selected, the unselected set is the N−K non-selected, in pool order"
    (let [;; Selection: safety picks s1 (K=1 of N=3), target picks t1,t3 (K=2 of N=4),
          ;; reach picks nothing (K=0 of N=2).
          selection {:institutions {:safety [{:id "s1"}]
                                    :target [{:id "t1"} {:id "t3"}]
                                    :reach  []}}
          unsel (sel/unselected-institutions sample-pool selection)]
      (is (= [{:id "s2" :name "S-Two"} {:id "s3" :name "S-Three"}] (:safety unsel))
          "safety: 3 pool − 1 selected = the 2 non-selected, in pool order")
      (is (= [{:id "t2"} {:id "t4"}] (:target unsel))
          "target: 4 pool − 2 selected = the 2 non-selected, in pool order")
      (is (= [{:id "r1"} {:id "r2"}] (:reach unsel))
          "reach: nothing selected → the whole pool bucket is available to promote")))
  (testing "an institution selected in ANY category is hidden from its pool bucket (cross-category)"
    ;; s1 lives in the safety pool but was reassigned into the target selection bucket;
    ;; it must NOT reappear as 'available to promote' under safety.
    (let [selection {:institutions {:safety [] :target [{:id "s1"}] :reach []}}
          unsel (sel/unselected-institutions sample-pool selection)]
      (is (not (some #(= "s1" (:id %)) (:safety unsel)))
          "s1 selected under target is excluded from the safety available-to-promote list")))
  (testing "nil pool or nil selection → nil (present-by-data; nothing to render)"
    (is (nil? (sel/unselected-institutions nil {:institutions {}})))
    (is (nil? (sel/unselected-institutions sample-pool nil)))))

;; ===========================================================================
;; selected-program-ids / unselected-programs — promoting a program WITHIN a school.
;; A school's pool :programs minus its selected program ids are the programs available
;; to promote; the promote control feeds [category inst-id prog-id] to the EXISTING
;; ::toggle-program-selection event.
;; ===========================================================================

(def sample-selection-with-programs
  {:institutions {:target [{:id "t1" :programs [{:id "p1"} {:id "p3"}]}
                           {:id "t2" :programs []}]}})

(deftest selected-program-ids-for-a-school
  (testing "the set of program ids currently selected under a school in a category"
    (is (= #{"p1" "p3"} (sel/selected-program-ids sample-selection-with-programs :target "t1")))
    (is (= #{} (sel/selected-program-ids sample-selection-with-programs :target "t2")))
    (is (= #{} (sel/selected-program-ids sample-selection-with-programs :target "absent")))))

(deftest unselected-programs-is-M-minus-J
  (testing "a school's M pool programs minus its J selected = the M−J available to promote, in pool order"
    (let [pool-programs [{:id "p1" :name "P-One"} {:id "p2" :name "P-Two"}
                         {:id "p3"} {:id "p4"}]
          selected #{"p1" "p3"}]
      (is (= [{:id "p2" :name "P-Two"} {:id "p4"}]
             (sel/unselected-programs pool-programs selected))
          "4 pool programs − 2 selected = the 2 non-selected, in pool order")))
  (testing "nothing selected → every pool program is available; a seq of ids is accepted too"
    (is (= [{:id "p1"} {:id "p2"}]
           (sel/unselected-programs [{:id "p1"} {:id "p2"}] #{})))
    (is (= [{:id "p2"}]
           (sel/unselected-programs [{:id "p1"} {:id "p2"}] ["p1"])))))

;; ===========================================================================
;; find-pool-institution — the resolved card only carries SELECTED programs, so to
;; list a school's unselected programs the view looks the school up in the pool bucket
;; (which carries the FULL :programs).
;; ===========================================================================

(deftest find-pool-institution-by-id
  (testing "locates the pool institution (with its full :programs) by id within a category bucket"
    (let [pool {:institutions {:target [{:id "t1" :programs [{:id "p1"} {:id "p2"}]}
                                        {:id "t2" :programs []}]}}]
      (is (= [{:id "p1"} {:id "p2"}]
             (:programs (sel/find-pool-institution pool :target "t1"))))
      (is (nil? (sel/find-pool-institution pool :target "absent")))
      (is (nil? (sel/find-pool-institution pool :safety "t1"))))))

;; ===========================================================================
;; resolve-selection — apply the working selection to the immutable pool → the
;; SAME :resolved shape the student query returns (queries/resolve-selection, ported
;; to cljs so the in-page Student preview reflects the advisor's LIVE selection without
;; a backend round-trip; 0051). Each selection ref is resolved to its FULL pool record
;; (carrying the enriched tiles/whats-cool the student view renders), in selection order,
;; with programs filtered to the selected ones, advisor overrides merged.
;; ===========================================================================

(def resolve-pool
  {:institutions
   {:safety [{:id "s1" :name "S-One" :location "Baton Rouge, LA"
              :programs [{:id "p1" :name "Nursing"} {:id "p2" :name "Biology"}]}
             {:id "s2" :name "S-Two" :programs []}]
    :target [{:id "t1" :name "T-One"
              :programs [{:id "tp1" :name "Business"}]}]
    :reach  [{:id "r1" :name "R-One" :programs []}]}
   :scholarships [{:id "sch1" :name "TOPS"} {:id "sch2" :name "Pell"}]
   :apprenticeships [{:id "ap1" :name "Electrician"}]
   :short-term-programs [{:id "st1" :name "Welding"}]})

(deftest resolve-selection-applies-selection-to-pool
  (testing "selected institutions resolve to their FULL pool record, in selection order, programs filtered to selected"
    (let [selection {:institutions
                     {:safety [{:id "s1" :programs [{:id "p2"}]}]  ; only Biology, not Nursing
                      :target [{:id "t1" :programs [{:id "tp1"}]}]
                      :reach  []}
                     :scholarships [{:id "sch2"}]                   ; Pell only
                     :apprenticeships [{:id "ap1"}]
                     :short-term-programs []}
          resolved (sel/resolve-selection resolve-pool selection)]
      (is (= "S-One" (get-in resolved [:institutions :safety 0 :name]))
          "resolves to the full pool record (carries name/location/tiles the student view needs)")
      (is (= "Baton Rouge, LA" (get-in resolved [:institutions :safety 0 :location])))
      (is (= [{:id "p2" :name "Biology"}] (get-in resolved [:institutions :safety 0 :programs]))
          "programs filtered to the SELECTED ones only, resolved to full program records")
      (is (= ["T-One"] (mapv :name (get-in resolved [:institutions :target]))))
      (is (empty? (get-in resolved [:institutions :reach])))
      (is (= ["Pell"] (mapv :name (:scholarships resolved))) "scholarships resolved by id, in selection order")
      (is (= ["Electrician"] (mapv :name (:apprenticeships resolved))))
      (is (empty? (:short-term-programs resolved)))))
  (testing "newly-selected school appears; a school selected under a DIFFERENT bucket still resolves (pool searched across all buckets)"
    ;; s1 lives in the safety POOL bucket but the advisor moved it into the target selection.
    (let [selection {:institutions {:safety [] :target [{:id "s1" :programs []}] :reach []}}
          resolved (sel/resolve-selection resolve-pool selection)]
      (is (= ["S-One"] (mapv :name (get-in resolved [:institutions :target])))
          "a cross-category selection resolves against the full pool, not just the same-named bucket")
      (is (empty? (get-in resolved [:institutions :safety])))))
  (testing "advisor overrides (text-edits) merge over the sourced record"
    (let [selection {:institutions {:safety [{:id "s1" :text-edits {:name "Renamed"} :programs []}]
                                    :target [] :reach []}}
          resolved (sel/resolve-selection resolve-pool selection)]
      (is (= "Renamed" (get-in resolved [:institutions :safety 0 :name])))))
  (testing "empty selection → empty resolved buckets (present-by-data)"
    (let [resolved (sel/resolve-selection resolve-pool {:institutions {:safety [] :target [] :reach []}})]
      (is (empty? (get-in resolved [:institutions :safety])))
      (is (empty? (:scholarships resolved))))))
