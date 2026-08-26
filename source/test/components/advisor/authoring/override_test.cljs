(ns components.advisor.authoring.override-test
  "Issue 0019 — S3. Tests for the PURE prose / tile-value override support fns the
   advisor authoring view uses to edit any narrative prose (:text-edits, top-level)
   and override any headline tile value (:section-edits, deep-merged one level into a
   named section data map), round-tripping through the EXISTING
   save-recommendation-customization → apply-ref-overrides path.

   Behavior is exercised through the public fns only (data→data), so it survives
   refactors of the view. The set/revert fns MIRROR the backend apply-ref-overrides
   :text-edits + :section-edits branches (queries.clj): the sourced pool record is
   never mutated; DROPPING an override (revert) leaves the ref byte-identical to a
   never-overridden one, so the sourced value returns on the next resolve."
  (:require [cljs.test :refer [deftest testing is]]
            [components.advisor.authoring.override :as override]
            [components.rec-demo.adapter :as adapter]))

;; ===========================================================================
;; overrides — the ref's override submaps (for the authoring context read-back)
;; ===========================================================================

(deftest overrides-reads-the-override-submaps
  (testing "just the :text-edits + :section-edits keys; nil-safe"
    (is (= {:text-edits {:name "X"} :section-edits {:grad-rate {:rate "70%"}}}
           (override/overrides {:id "i" :programs []
                                :text-edits {:name "X"}
                                :section-edits {:grad-rate {:rate "70%"}}})))
    (is (= {} (override/overrides {:id "i"})))
    (is (= {} (override/overrides nil)))))

;; ===========================================================================
;; text-edits — top-level field overrides (prose vectors / top-level values)
;; ===========================================================================

(deftest text-edited?-membership
  (testing "true only when the top-level key carries a text-edit"
    (is (true?  (override/text-edited? {:text-edits {:about-school-narrative ["a"]}}
                                       :about-school-narrative)))
    (is (false? (override/text-edited? {:text-edits {:about-school-narrative ["a"]}}
                                       :costs-narrative)))
    (is (false? (override/text-edited? {} :about-school-narrative)))
    (is (false? (override/text-edited? nil :about-school-narrative)))))

(deftest set-text-edit-adds-top-level-override
  (testing "records a top-level override on the ref (adds :text-edits when absent)"
    (is (= {:id "i" :text-edits {:about-school-narrative ["new"]}}
           (override/set-text-edit {:id "i"} :about-school-narrative ["new"])))
    (is (= {:id "i" :text-edits {:name "X" :costs-narrative ["c"]}}
           (override/set-text-edit {:id "i" :text-edits {:name "X"}}
                                   :costs-narrative ["c"])))))

(deftest revert-text-edit-drops-just-that-key
  (testing "a sibling text-edit survives"
    (is (= {:id "i" :text-edits {:name "X"}}
           (override/revert-text-edit {:id "i" :text-edits {:name "X"
                                                            :costs-narrative ["c"]}}
                                      :costs-narrative))))
  (testing "reverting the LAST text-edit dissocs :text-edits entirely, so the ref is
            byte-identical to a never-edited one (exact revert-to-source)"
    (let [reverted (override/revert-text-edit
                     {:id "i" :text-edits {:about-school-narrative ["a"]}}
                     :about-school-narrative)]
      (is (= {:id "i"} reverted))
      (is (not (contains? reverted :text-edits))))))

;; ===========================================================================
;; section-edits — {section-key {field val}} deep-merged one level into a section
;; ===========================================================================

(deftest section-edited?-membership
  (testing "true only when the named section carries an edit for that field"
    (is (true?  (override/section-edited? {:section-edits {:grad-rate {:rate "70%"}}}
                                          :grad-rate :rate)))
    (is (false? (override/section-edited? {:section-edits {:grad-rate {:rate "70%"}}}
                                          :grad-rate :indicator))
        "a sibling field in the same section is not edited")
    (is (false? (override/section-edited? {:section-edits {:grad-rate {:rate "70%"}}}
                                          :costs :net))
        "a different section is not edited")
    (is (false? (override/section-edited? {} :grad-rate :rate)))
    (is (false? (override/section-edited? nil :grad-rate :rate)))))

(deftest set-section-edit-deep-merges-one-level
  (testing "records a per-field section override (creates the nesting when absent)"
    (is (= {:id "i" :section-edits {:grad-rate {:rate "72%"}}}
           (override/set-section-edit {:id "i"} :grad-rate :rate "72%")))
    (is (= {:id "i" :section-edits {:grad-rate {:rate "72%"} :costs {:net 0}}}
           (override/set-section-edit {:id "i" :section-edits {:grad-rate {:rate "72%"}}}
                                      :costs :net 0))
        "a section override in a DIFFERENT section is added alongside")
    (is (= {:id "i" :section-edits {:overview {:credential-line "L1"
                                              :student-connection "L2"}}}
           (override/set-section-edit {:id "i" :section-edits {:overview {:credential-line "L1"}}}
                                      :overview :student-connection "L2"))
        "a second field in the SAME section is added alongside (deep-merge one level)")))

(deftest revert-section-edit-drops-just-that-field
  (testing "a sibling field in the same section survives"
    (is (= {:id "i" :section-edits {:overview {:credential-line "L1"}}}
           (override/revert-section-edit
             {:id "i" :section-edits {:overview {:credential-line "L1"
                                                :student-connection "L2"}}}
             :overview :student-connection))))
  (testing "reverting the last field in a section drops that section key"
    (is (= {:id "i" :section-edits {:costs {:net 0}}}
           (override/revert-section-edit
             {:id "i" :section-edits {:grad-rate {:rate "72%"} :costs {:net 0}}}
             :grad-rate :rate))))
  (testing "reverting the last field of the last section dissocs :section-edits entirely
            (exact revert-to-source — the ref is byte-identical to a never-overridden one)"
    (let [reverted (override/revert-section-edit
                     {:id "i" :section-edits {:grad-rate {:rate "72%"}}}
                     :grad-rate :rate)]
      (is (= {:id "i"} reverted))
      (is (not (contains? reverted :section-edits))))))

;; ===========================================================================
;; bullets <-> text — the textarea seam for narrative prose (round-trip stable)
;; ===========================================================================

(deftest bullets-text-roundtrip
  (testing "a narrative vector joins to a blank-line-separated string and splits back"
    (let [bullets ["First point about the school." "Second point about it."]]
      (is (= "First point about the school.\n\nSecond point about it."
             (override/bullets->text bullets)))
      (is (= bullets (override/text->bullets (override/bullets->text bullets))))))
  (testing "blank/whitespace-only paragraphs are dropped; surrounding whitespace trimmed"
    (is (= ["a" "b"] (override/text->bullets "a\n\n\n  \n\nb")))
    (is (= ["a" "b"] (override/text->bullets "  a  \n\n  b  ")))
    (is (= [] (override/text->bullets "   \n\n  "))))
  (testing "nil-safe"
    (is (= "" (override/bullets->text nil)))
    (is (= [] (override/text->bullets nil)))))

;; ===========================================================================
;; effective-* + apply-overrides — WYSIWYG from the working (not-yet-reloaded) ref
;; ===========================================================================

(deftest effective-text-prefers-working-override
  (testing "returns the working text-edit when set, else the sourced value"
    (is (= ["Advisor"] (override/effective-text {:text-edits {:about-school-narrative ["Advisor"]}}
                                                :about-school-narrative ["Sourced"])))
    (is (= ["Sourced"] (override/effective-text {} :about-school-narrative ["Sourced"])))
    (is (= ["Sourced"] (override/effective-text nil :about-school-narrative ["Sourced"])))))

(deftest effective-section-prefers-working-override
  (testing "returns the working section-edit field when set, else the sourced value"
    (is (= "72%" (override/effective-section {:section-edits {:grad-rate {:rate "72%"}}}
                                             :grad-rate :rate "26%")))
    (is (= "26%" (override/effective-section {} :grad-rate :rate "26%")))
    (is (= "26%" (override/effective-section nil :grad-rate :rate "26%")))))

(deftest apply-overrides-mirrors-the-backend-merge
  (testing "deep-merges :section-edits one level, then flat-merges :text-edits; a
            no-op with no ref (demo / student path untouched)"
    (let [record {:grad-rate {:rate "26%" :indicator "Low"}
                  :about-school-narrative ["Sourced."]}]
      (is (= record (override/apply-overrides record nil)) "no ref → untouched")
      (is (= record (override/apply-overrides record {:id "i"})) "bare ref → untouched")
      (let [ref {:section-edits {:grad-rate {:rate "72%"}}
                 :text-edits {:about-school-narrative ["Advisor."]}}
            projected (override/apply-overrides record ref)]
        (is (= "72%" (get-in projected [:grad-rate :rate])) "tile deep-merge LANDED")
        (is (= "Low" (get-in projected [:grad-rate :indicator])) "sibling section field sourced")
        (is (= ["Advisor."] (:about-school-narrative projected)) "prose text-edit merged on top")))))

;; ===========================================================================
;; Data-key correctness — the KEY an override writes IS the key the render reads.
;;
;; The backend `apply-ref-overrides` deep-merges :section-edits one level into the
;; record's named section data map, and merges :text-edits at the top level. These
;; tests simulate the POST-resolve record (the merge already applied by the backend,
;; exactly as `presentation_rails_test.clj` proves at the source) and run the REAL
;; PURE adapter over it, proving each override lands on the key the section render
;; surfaces — falsifying the "writes a key nothing reads" bug the redesign warned of.
;; ===========================================================================

(deftest grad-rate-tile-override-lands-on-the-adapter-key
  (testing "overriding [:section-edits :grad-rate :rate] surfaces as the grad-rate tile
            VALUE (school->about-props → grad-rate figure :value), siblings sourced"
    (let [;; sourced institution grad-rate map
          sourced   {:grad-rate {:rate "26%" :label "Graduation Rate" :indicator "Low"}}
          ;; the ref the advisor tile builds
          ref       (override/set-section-edit {:id "i"} :grad-rate :rate "72%")
          ;; what the backend apply-ref-overrides does: (update rec :grad-rate merge …)
          resolved  (update sourced :grad-rate merge (get-in ref [:section-edits :grad-rate]))
          figures   (:figures (adapter/school->about-props resolved))
          grad-fig  (first (filter #(= :grad-rate (:kind %)) figures))]
      (is (= "72%" (:value grad-fig)) "overridden rate is the tile value (deep-merge LANDED)")
      (is (= "Low" (:indicator grad-fig)) "sibling indicator kept the sourced value"))))

(deftest salary-growth-tile-override-lands-on-the-adapter-key
  (testing "overriding [:section-edits :salary :growth-rate] surfaces as the PSEO
            10-Year Growth tile VALUE (program->salary-props → :growth-rate), sibling
            :salary fields sourced; revert drops it → the sourced growth returns"
    (let [;; sourced program :salary map (PSEO earnings mode)
          sourced   {:salary {:growth-rate "+8%" :growth-openings "1,200"
                              :stars 4 :descriptor "Strong demand."
                              :earnings [{:label "Median" :value 55000}]}}
          ;; the ref the advisor tile builds
          ref       (override/set-section-edit {:id "p"} :salary :growth-rate "+15%")
          ;; what the backend apply-ref-overrides does: (update rec :salary merge …)
          resolved  (update sourced :salary merge (get-in ref [:section-edits :salary]))
          props     (adapter/program->salary-props resolved)]
      (is (= "+15%" (:growth-rate props)) "overridden growth is the tile value (deep-merge LANDED)")
      (is (= "1,200" (:growth-openings props)) "sibling :salary field kept the sourced value")
      (is (= 4 (:stars props)) "sibling :salary star figure kept the sourced value")
      ;; revert drops the section-edit → the ref carries no override → sourced growth
      (is (not (contains? (override/revert-section-edit ref :salary :growth-rate) :section-edits))
          "revert leaves no override, so resolve returns the sourced :growth-rate"))))

(deftest about-narrative-prose-override-lands-on-the-adapter-key
  (testing "overriding top-level :about-school-narrative surfaces as school->about-props
            :narrative (revert → the sourced narrative returns)"
    (let [sourced  {:grad-rate {:rate "26%"} :about-school-narrative ["Sourced point."]}
          ref      (override/set-text-edit {:id "i"} :about-school-narrative ["Advisor point."])
          resolved (merge sourced (:text-edits ref))]  ;; backend :text-edits top-level merge
      (is (= ["Advisor point."] (:narrative (adapter/school->about-props resolved)))
          "overridden narrative is what the About section renders")
      ;; revert drops the text-edit → the ref carries no override → sourced narrative
      (is (empty? (:text-edits (override/revert-text-edit ref :about-school-narrative)))
          "revert leaves no override, so resolve returns the sourced :about-school-narrative"))))

;; 0055 — the What-It-Costs-You bullets are now the prototype's templated affordability
;; ADVICE (Fixed text, commuter/residential branch), like the FAFSA disclaimer — they are
;; NOT advisor-editable and no longer surface a :costs-narrative override. (The override
;; mechanism itself is unchanged and still drives :about-school-narrative + overviews.)
(deftest costs-narrative-is-fixed-advice-not-overridden
  (testing "school->costs-props :narrative is the deterministic advice, ignoring any
            :costs-narrative override (costs bullets are Fixed text)"
    (let [sourced  {:costs {:tuition-fees 9043 :net 7536} :costs-narrative ["Sourced cost note."]}
          ref      (override/set-text-edit {:id "i"} :costs-narrative ["Advisor cost note."])
          resolved (merge sourced (:text-edits ref))
          narr     (:narrative (adapter/school->costs-props resolved))]
      (is (not= ["Advisor cost note."] narr) "the costs bullets are fixed, not overridden")
      (is (= (adapter/costs-advice-bullets (:costs sourced)) narr)
          "the deterministic commuter/residential advice bullets render regardless of any override"))))

(deftest overview-prose-override-lands-on-the-adapter-key
  (testing "overriding [:section-edits :overview :student-connection] surfaces via
            program->overview-props, siblings in the same :overview section sourced"
    (let [sourced   {:overview {:credential-line "A 4-year nursing degree."
                                :student-connection "Sourced connection."
                                :caveat "Admission differs."}}
          ref       (override/set-section-edit {:id "p"} :overview :student-connection "Advisor connection.")
          resolved  (update sourced :overview merge (get-in ref [:section-edits :overview]))
          props     (adapter/program->overview-props resolved)]
      (is (= "Advisor connection." (:student-connection props)) "overridden overview prose LANDED")
      (is (= "A 4-year nursing degree." (:credential-line props)) "sibling overview prose sourced"))))
