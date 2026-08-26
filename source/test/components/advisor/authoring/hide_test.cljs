(ns components.advisor.authoring.hide-test
  "Issue 0018 — S2. Tests for the PURE hide-a-block / section-gating support fns the
   advisor authoring view uses to hide any section block (program section OR
   School-anchored About/Costs) and round-trip it through the EXISTING
   save-recommendation-customization → :hidden-sections → apply-ref-overrides path.

   Behavior is exercised through the public fns only (data→data), so it survives
   refactors of the view. The gating fns MIRROR the backend apply-ref-overrides
   :hidden-sections branch (queries.clj) so the WYSIWYG frontend render matches EXACTLY
   what the backend projection returns on reload."
  (:require [cljs.test :refer [deftest testing is]]
            [components.advisor.authoring.hide :as hide]))

;; ===========================================================================
;; hidden-sections / hidden? — read a record/ref's hidden set (nil-safe)
;; ===========================================================================

(deftest hidden-sections-reads-the-set
  (testing "the :hidden-sections vector as a set; nil-safe"
    (is (= #{:salary} (hide/hidden-sections {:hidden-sections [:salary]})))
    (is (= #{:about-school :costs}
           (hide/hidden-sections {:hidden-sections [:about-school :costs]})))
    (is (= #{} (hide/hidden-sections {})))
    (is (= #{} (hide/hidden-sections nil)))))

(deftest hidden?-membership
  (testing "true only when the section-key is in :hidden-sections"
    (is (true?  (hide/hidden? {:hidden-sections [:salary :costs]} :salary)))
    (is (true?  (hide/hidden? {:hidden-sections [:salary :costs]} :costs)))
    (is (false? (hide/hidden? {:hidden-sections [:salary]} :careers)))
    (is (false? (hide/hidden? {} :salary)))
    (is (false? (hide/hidden? nil :salary)))))

;; ===========================================================================
;; toggle-hidden — pure ref update; empty → dissoc (revert-to-source is exact)
;; ===========================================================================

(deftest toggle-hidden-adds-when-absent
  (testing "hides a not-yet-hidden section"
    (is (= {:id "x" :hidden-sections [:salary]}
           (hide/toggle-hidden {:id "x"} :salary)))
    (is (= {:id "x" :hidden-sections [:salary :costs]}
           (hide/toggle-hidden {:id "x" :hidden-sections [:salary]} :costs)))))

(deftest toggle-hidden-removes-when-present
  (testing "un-hides an already-hidden section"
    (is (= {:id "x" :hidden-sections [:costs]}
           (hide/toggle-hidden {:id "x" :hidden-sections [:salary :costs]} :salary)))))

(deftest toggle-hidden-dissocs-when-empty
  (testing "reverting the LAST hidden section drops the key entirely, so a fully-reverted
            ref is byte-identical to a never-hidden one (exact revert-to-source)"
    (is (= {:id "x"}
           (hide/toggle-hidden {:id "x" :hidden-sections [:salary]} :salary)))
    (is (not (contains? (hide/toggle-hidden {:id "x" :hidden-sections [:salary]} :salary)
                        :hidden-sections)))))

(deftest toggle-hidden-dedupes
  (testing "a pre-existing duplicate never survives a toggle"
    (is (= {:id "x" :hidden-sections [:salary :costs]}
           (hide/toggle-hidden {:id "x" :hidden-sections [:salary :salary]} :costs)))))

;; ===========================================================================
;; section-shown? — the school-sections FIX: honor present-by-data :sections
;; ===========================================================================

(deftest section-shown?-gates-on-sections
  (testing "when the record carries :sections, a section shows ONLY when it's a member"
    (is (true?  (hide/section-shown? {:sections [:about-school :costs]} :about-school)))
    (is (true?  (hide/section-shown? {:sections [:about-school :costs]} :costs)))
    ;; About hidden → removed from :sections → must NOT show (the bug this fixes)
    (is (false? (hide/section-shown? {:sections [:costs]} :about-school)))
    (is (false? (hide/section-shown? {:sections []} :costs))))
  (testing "no :sections key (hardcoded demo schools / old-shape) → present-by-data, shows"
    (is (true? (hide/section-shown? {:grad-rate {}} :about-school)))
    (is (true? (hide/section-shown? {} :costs)))))

;; ===========================================================================
;; apply-hidden — frontend mirror of the backend apply-ref-overrides hidden branch
;; ===========================================================================

(deftest apply-hidden-strips-key-and-sections
  (testing "removes each hidden section's same-named data key AND strips it from :sections,
            matching the backend projection (queries.clj apply-ref-overrides)"
    (let [record {:sections [:about-school :costs]
                  :about-school {:tiles []} :costs {:coa 100}}]
      (is (= {:sections [:costs] :costs {:coa 100}}
             (hide/apply-hidden record [:about-school]))))))

(deftest apply-hidden-noop-when-empty
  (testing "no hidden sections → the record is returned unchanged (demo path is neutral)"
    (let [record {:sections [:salary] :salary {:x 1}}]
      (is (= record (hide/apply-hidden record [])))
      (is (= record (hide/apply-hidden record nil))))))

(deftest apply-hidden-record-without-sections
  (testing "a record with no :sections still drops the hidden data key (no :sections touch)"
    (is (= {:costs {:coa 1}}
           (hide/apply-hidden {:about-school {} :costs {:coa 1}} [:about-school])))))

;; ===========================================================================
;; edit-section-keys — EDIT-mode list = shown ∪ hidden, in canonical order
;; ===========================================================================

(deftest edit-section-keys-unions-shown-and-hidden
  (testing "so a hidden section still renders its 'Show block' ghost after a reload has
            stripped it from the record — in canonical section order"
    (is (= [:overview :salary :careers]
           (hide/edit-section-keys [:overview :salary :careers]
                                   [:overview :careers]   ;; :salary stripped (hidden)
                                   [:salary])))
    (is (= [:about-school :costs]
           (hide/edit-section-keys [:about-school :costs]
                                   [:costs]               ;; About hidden
                                   [:about-school])))))

(deftest edit-section-keys-no-hidden-is-shown-in-order
  (testing "with nothing hidden it is just the shown sections in canonical order"
    (is (= [:overview :salary]
           (hide/edit-section-keys [:overview :salary :careers] [:overview :salary] [])))))

;; ===========================================================================
;; find-institution-ref / find-program-ref — locate a resolved card's ref by id so
;; a toggle updates the RIGHT ref in the working selection (never a fork)
;; ===========================================================================

(def sel
  {:institutions
   {:safety [{:id "s1" :programs [{:id "p1"} {:id "p2"}]}]
    :target [{:id "t1" :programs [{:id "p3"}]}]
    :reach  []}})

(deftest find-institution-ref-scans-buckets
  (testing "locate an institution ref + its bucket category by id"
    (is (= {:category :safety :ref {:id "s1" :programs [{:id "p1"} {:id "p2"}]}}
           (hide/find-institution-ref sel "s1")))
    (is (= :target (:category (hide/find-institution-ref sel "t1"))))
    (is (nil? (hide/find-institution-ref sel "nope")))))

(deftest find-program-ref-scans-nested
  (testing "locate a nested program ref + its owning institution/category by program id"
    (is (= {:category :safety :institution-id "s1" :ref {:id "p2"}}
           (hide/find-program-ref sel "p2")))
    (is (= {:category :target :institution-id "t1" :ref {:id "p3"}}
           (hide/find-program-ref sel "p3")))
    (is (nil? (hide/find-program-ref sel "nope")))))
