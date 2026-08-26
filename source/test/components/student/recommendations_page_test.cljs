(ns components.student.recommendations-page-test
  "Slice 0015 — PURE display re-grouping of college institutions by their own
   :str-badge :key into the fixed order Open Admission → Safety → Target → Reach
   (present-by-data: empty groups omitted). DISPLAY re-grouping only; the pool's
   :institutions buckets are never mutated."
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.set :as set]
            [components.student.recommendations-page :as page]
            [components.rec-demo.adapter :as adapter]))

(defn- inst [name k]
  {:institution-name name :str-badge {:key k :label name}})

(deftest group-institutions-by-str-orders-open-first
  (testing "mixed institutions re-group into open → safety → target → reach"
    (let [insts [(inst "Reach U" "reach")
                 (inst "BRCC" "open")
                 (inst "Safety College" "safety")
                 (inst "Target State" "target")
                 (inst "Fortis" "open")]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["open" "safety" "target" "reach"] (mapv first grouped))
          "groups appear in the fixed display order, open first")
      (is (= 2 (count (second (nth grouped 0)))) "both open-admission schools in the open group")
      (is (= #{"BRCC" "Fortis"}
             (set (map :institution-name (second (nth grouped 0))))))
      (is (= ["Safety College"] (map :institution-name (second (nth grouped 1)))))
      (is (= ["Target State"] (map :institution-name (second (nth grouped 2)))))
      (is (= ["Reach U"] (map :institution-name (second (nth grouped 3))))))))

(deftest group-institutions-by-str-omits-empty-groups
  (testing "present-by-data — categories with no institutions are omitted"
    (let [insts [(inst "BRCC" "open") (inst "Reach U" "reach")]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["open" "reach"] (mapv first grouped))
          "only non-empty groups, still in fixed order (safety/target skipped)"))))

(deftest group-institutions-by-str-places-each-by-own-badge
  (testing "every institution lands in the group matching its own :str-badge :key; count preserved"
    (let [insts [(inst "A" "target") (inst "B" "open") (inst "C" "target")
                 (inst "D" "safety") (inst "E" "reach") (inst "F" "open")]
          grouped (page/group-institutions-by-str insts)
          total (reduce + (map (comp count second) grouped))]
      (is (= (count insts) total) "count preserved across the re-grouping")
      (doseq [[k members] grouped
              m members]
        (is (= k (get-in m [:str-badge :key]))
            "each institution sits under the group matching its own badge key")))))

(deftest group-institutions-by-str-coerces-keyword-keys
  (testing ":str-badge :key may be a keyword (:open) — still resolves to its group"
    (let [insts [{:institution-name "Kw Open" :str-badge {:key :open :label "Open Admission"}}
                 {:institution-name "Kw Safety" :str-badge {:key :safety :label "Safety"}}]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["open" "safety"] (mapv first grouped))))))

;; ---------------------------------------------------------------------------
;; :str-badge is OPTIONAL (the engine's about_school.clj only attaches it when
;; non-nil). An institution with a nil/unknown :str-badge :key must NEVER be
;; silently dropped — it FALLS BACK to its own :admissions-likelihood tier
;; (Safety/Target/Reach), else "safety". Every input institution appears in
;; exactly one output group.
;; ---------------------------------------------------------------------------

(deftest group-institutions-by-str-never-drops-badgeless-institution
  (testing "3 safety schools, one lacking :str-badge → all 3 render (not 2)"
    (let [insts [(assoc (inst "Safety A" "safety") :admissions-likelihood "Safety")
                 ;; No :str-badge at all — must NOT be dropped.
                 {:institution-name "Safety B (no badge)" :admissions-likelihood "Safety"}
                 (assoc (inst "Safety C" "safety") :admissions-likelihood "Safety")]
          grouped (page/group-institutions-by-str insts)
          total (reduce + (map (comp count second) grouped))]
      (is (= 3 total) "all three institutions survive the re-grouping")
      (is (= #{"Safety A" "Safety B (no badge)" "Safety C"}
             (set (mapcat (comp #(map :institution-name %) second) grouped)))
          "the badgeless school is present, not silently dropped")
      (is (= ["safety"] (mapv first grouped))
          "the badgeless safety school falls back into the safety group"))))

(deftest group-institutions-by-str-falls-back-by-admissions-likelihood
  (testing "nil :str-badge → grouped by the record's own admissions tier"
    (let [insts [{:institution-name "R" :admissions-likelihood "Reach"}
                 {:institution-name "T" :admissions-likelihood "Target"}
                 {:institution-name "S" :admissions-likelihood "Safety"}]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["safety" "target" "reach"] (mapv first grouped))
          "each badgeless school lands in its admissions-likelihood group")
      (is (= 3 (reduce + (map (comp count second) grouped))) "none dropped"))))

(deftest group-institutions-by-str-unknown-tier-defaults-to-safety
  (testing "no badge AND no usable tier → defaults into safety (never dropped)"
    (let [insts [{:institution-name "Mystery" :admissions-likelihood "Unknown"}
                 {:institution-name "Bare"}]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["safety"] (mapv first grouped)))
      (is (= 2 (reduce + (map (comp count second) grouped))) "both default to safety, none dropped"))))

(deftest group-institutions-by-str-known-badge-still-wins
  (testing "a present, known :str-badge :key still drives the group (fallback unused)"
    (let [insts [(assoc (inst "Target Wins" "target") :admissions-likelihood "Reach")]
          grouped (page/group-institutions-by-str insts)]
      (is (= ["target"] (mapv first grouped))
          "the explicit badge key beats the admissions-likelihood fallback"))))

;; ---------------------------------------------------------------------------
;; PURE branded hero title — the student's name (NEVER hardcoded) when carried,
;; a generic fallback otherwise. Shared verbatim by the student page AND the
;; advisor authoring view's Student-preview so the hero reads identically.
;; ---------------------------------------------------------------------------

(deftest personalized-title-uses-student-name
  (testing "a carried name → «Name»'s Personalized Recommendations"
    (is (= "Maria's Personalized Recommendations" (page/personalized-title "Maria")))
    (is (= "Jordan Lee's Personalized Recommendations" (page/personalized-title "Jordan Lee")))))

(deftest personalized-title-generic-fallback
  (testing "nil / blank name → the generic hero (never a hardcoded name)"
    (is (= "Your Personalized Recommendations" (page/personalized-title nil)))
    (is (= "Your Personalized Recommendations" (page/personalized-title "")))
    (is (= "Your Personalized Recommendations" (page/personalized-title "   "))
        "a whitespace-only name is treated as absent, not a whitespace title")))

;; ---------------------------------------------------------------------------
;; Slice 0033 (epic match-tavidee) — degree-type SECTION grouping.
;; Records now carry the engine 0032 :section ∈ {:bachelors :career-technical}.
;; The UI groups the tabs by :section, with a UI-side fallback derivation for an
;; OLD (pre-0032) pool that carries none (4-year → Bachelor's, 2-year → Career-
;; Technical). Career-Technical is ONE interleaved flow (Variant A) — schools +
;; short-term programs + apprenticeships together, NOT split schools-vs-apprentices.
;; ---------------------------------------------------------------------------

(deftest institution-section-reads-engine-tag
  (testing "prefers the engine 0032 :section keyword (name-coerced so a string resolves too)"
    (is (= :bachelors (page/institution-section {:section :bachelors})))
    (is (= :career-technical (page/institution-section {:section :career-technical})))
    (is (= :career-technical (page/institution-section {:section "career-technical"}))
        "a string :section still resolves")))

(deftest institution-section-fallback-by-sector
  (testing "an OLD pool (no :section) derives 4-year → :bachelors, 2-year → :career-technical"
    (is (= :bachelors (page/institution-section {:sectors ["Public, 4-year or above"]})))
    (is (= :career-technical (page/institution-section {:sectors ["Public, 2-year"]})))
    (is (= :career-technical (page/institution-section {:type "Public 2-Year" :kind :two-year})))
    (is (= :bachelors (page/institution-section {:institution-name "Bare College"}))
        "no sector signal at all → defaults to :bachelors (a college's track, never dropped)")))

(deftest institutions-partition-by-section
  (testing "institutions split into their section by the engine tag"
    (let [b {:institution-name "SLU"  :section :bachelors}
          c {:institution-name "BRCC" :section :career-technical}]
      (is (= ["SLU"]  (mapv :institution-name (page/institutions-in-section :bachelors [b c]))))
      (is (= ["BRCC"] (mapv :institution-name (page/institutions-in-section :career-technical [b c])))))))

(deftest career-technical-flow-interleaves-all-three-kinds
  (testing "career-technical schools + short-term programs + apprenticeships land in ONE flow"
    (let [ct-school {:institution-name "BRCC" :section :career-technical :str-badge {:key "open"}}
          short-t   {:program-title "LPN"}
          appr      {:apprenticeship-title "Electrical"}
          flow      (page/career-technical-flow [ct-school] [short-t] [appr])]
      (is (= 3 (count flow)) "all three records present in ONE interleaved list (not split)")
      (is (= #{:school :short-term :apprenticeship} (set (map :kind flow))) "each kind present")
      (is (= "BRCC" (->> flow (filter #(= :school (:kind %))) first :record :institution-name))))))

(deftest career-technical-flow-variant-a-order
  (testing "Variant A — the tier-less apprenticeship interleaves BETWEEN the open + tiered schools"
    (let [open-school   {:institution-name "BRCC" :section :career-technical :str-badge {:key "open"}}
          target-school {:institution-name "SLU"  :section :career-technical :str-badge {:key "target"}}
          appr          {:apprenticeship-title "Electrical"}
          flow          (page/career-technical-flow [open-school target-school] [] [appr])
          labels        (mapv (fn [{:keys [kind record]}]
                                (if (= :school kind) (:institution-name record) :appr))
                              flow)]
      (is (= ["BRCC" :appr "SLU"] labels)
          "open 2-year → tier-less apprenticeship → target terminal school (prototype order)"))))

(deftest career-technical-flow-present-by-data
  (testing "an empty flow when there are no career-technical records at all"
    (is (= [] (page/career-technical-flow [] [] [])))))

;; ---------------------------------------------------------------------------
;; Slice 0078 (ADR 0008) — PER-PROGRAM section split. Each program carries its own
;; :section tag; the institution's collapsed :section is only its first program's.
;; A mixed-degree school must appear in BOTH tabs, showing only that tab's programs
;; (one institution record; programs grouped at render, never duplicated). And the
;; Career-Technical card carries NO Safety/Target/Reach badge (Open-Admission or none).
;; ---------------------------------------------------------------------------

(deftest institution-in-section-with-programs-filters-to-section
  (testing "returns the institution with :programs filtered to the section; nil for none"
    (let [mixed {:institution-name "LSU-Alexandria"
                 :programs [{:program-title "Clinical Lab Science" :section :bachelors}
                            {:program-title "Nursing" :section :career-technical}]}]
      (is (= [{:program-title "Clinical Lab Science" :section :bachelors}]
             (:programs (page/institution-in-section-with-programs :bachelors mixed)))
          "bachelors view keeps only the bachelors program")
      (is (= [{:program-title "Nursing" :section :career-technical}]
             (:programs (page/institution-in-section-with-programs :career-technical mixed)))
          "career-technical view keeps only the terminal program")
      (is (= "LSU-Alexandria" (:institution-name (page/institution-in-section-with-programs :bachelors mixed)))
          "other institution fields are preserved (one record)"))
    (let [bach-only {:institution-name "SLU"
                     :programs [{:program-title "History" :section :bachelors}]}]
      (is (nil? (page/institution-in-section-with-programs :career-technical bach-only))
          "nil when the institution has NO program in that section")
      (is (some? (page/institution-in-section-with-programs :bachelors bach-only)))))
  (testing "a string :section on a program still resolves"
    (let [inst {:institution-name "X" :programs [{:section "bachelors"}]}]
      (is (some? (page/institution-in-section-with-programs :bachelors inst))))))

(deftest legacy-pool-institutions-fall-back-to-sector-routing
  ;; Tavidee's bug: last-year (Python-era, :advisor/generated-recommendations-pool)
  ;; records carry :programs but NO per-program :section tag — tag-pool only runs at
  ;; generation. The strict per-program filter returned [] → nil → every college
  ;; dropped from BOTH tabs → only Scholarships rendered. The fixture below is the
  ;; REAL stored shape (lifted from bases/api/resources/api/scenario.edn, 2025-11
  ;; events) — the synthetic old-shape fixtures elsewhere all carry new-era keys,
  ;; which is exactly how this went unnoticed.
  (let [legacy-4yr {:institution-name "Southeastern Louisiana University"
                    :name "Southeastern Louisiana University"
                    :sectors ["Public, 4-year or above"]
                    :admissions-likelihood "Safety"
                    :is-apprenticeship false
                    :why-fits-bullets ["Strong nursing pipeline"]
                    :programs [{:program-title "Nursing"
                                :personalized-overview "A hands-on BSN program."
                                :best-for "Students who want patient-facing work."
                                :sector "Public, 4-year or above"}]}
        legacy-2yr {:institution-name "Baton Rouge Community College"
                    :sectors ["Public, 2-year"]
                    :admissions-likelihood "Safety"
                    :programs [{:program-title "Process Technology"
                                :personalized-overview "Fast track to plant work."
                                :sector "Public, 2-year"}]}]
    (testing "an untagged legacy 4-year routes WHOLE to Bachelor's, keeping all its programs"
      (let [out (page/institution-in-section-with-programs :bachelors legacy-4yr)]
        (is (some? out) "the record must not be dropped")
        (is (= ["Nursing"] (mapv :program-title (:programs out)))
            "its programs ride along unfiltered — none carries a tag to filter by")
        (is (nil? (page/institution-in-section-with-programs :career-technical legacy-4yr))
            "…and it does NOT also appear under Career-Technical (sector routing is exclusive)")))
    (testing "an untagged legacy 2-year routes WHOLE to Career-Technical"
      (is (some? (page/institution-in-section-with-programs :career-technical legacy-2yr)))
      (is (nil? (page/institution-in-section-with-programs :bachelors legacy-2yr))))
    (testing "the tabs come back for a legacy pool"
      (is (= ["Southeastern Louisiana University"]
             (mapv :institution-name (page/institutions-in-section :bachelors [legacy-4yr legacy-2yr]))))
      (is (= ["Baton Rouge Community College"]
             (mapv :institution-name (page/institutions-in-section :career-technical [legacy-4yr legacy-2yr])))))))

(deftest tagged-programs-still-win-over-sector-fallback
  (testing "one tagged program is enough to use strict per-program routing — the fallback
            must never loosen a NEW pool (mixed schools keep their disjoint split)"
    (let [partially-tagged {:institution-name "LSU-A"
                            :sectors ["Public, 4-year or above"]
                            :programs [{:program-title "Tagged" :section :career-technical}
                                       {:program-title "Untagged"}]}]
      (is (nil? (page/institution-in-section-with-programs :bachelors partially-tagged))
          "with a tag present, the 4-year sector must NOT drag it into Bachelor's")
      (is (= ["Tagged"]
             (mapv :program-title (:programs (page/institution-in-section-with-programs
                                              :career-technical partially-tagged))))
          "strict filtering applies; the untagged program is filtered out as before"))))

(deftest checked-school-with-empty-programs-stays-visible
  ;; The editor checkbox promotes a school as {:id … :programs []}; the resolver then
  ;; assocs :programs [] and the strict filter hid the school the advisor JUST checked.
  (testing "an empty :programs vector falls back to whole-institution sector routing"
    (let [checked {:institution-name "Loyola University New Orleans"
                   :sectors ["Private not-for-profit, 4-year or above"]
                   :programs []}]
      (is (some? (page/institution-in-section-with-programs :bachelors checked))
          "the school renders (with no program cards) instead of vanishing")
      (is (nil? (page/institution-in-section-with-programs :career-technical checked))))))

(deftest mixed-school-appears-in-both-sections-with-disjoint-programs
  (testing "one mixed school appears in BOTH section lists, each scoped to its own programs"
    (let [bach-prog {:program-title "Clinical Lab Science" :section :bachelors}
          term-prog {:program-title "Nursing" :section :career-technical}
          mixed {:institution-name "LSU-Alexandria" :section :bachelors
                 :programs [bach-prog term-prog]}
          bachelors (page/institutions-in-section :bachelors [mixed])
          career-technical (page/institutions-in-section :career-technical [mixed])]
      (is (= ["LSU-Alexandria"] (mapv :institution-name bachelors))
          "the mixed school appears in the Bachelor's list")
      (is (= ["LSU-Alexandria"] (mapv :institution-name career-technical))
          "the mixed school ALSO appears in the Career-Technical list")
      (is (= [bach-prog] (:programs (first bachelors))) "Bachelor's list shows only its bachelors program")
      (is (= [term-prog] (:programs (first career-technical))) "Career-Technical list shows only its terminal program")
      (is (empty? (set/intersection
                   (set (:programs (first bachelors)))
                   (set (:programs (first career-technical)))))
          "the two program subsets are DISJOINT"))))

(deftest single-section-school-appears-only-in-its-section
  (testing "a bachelor's-only school only in Bachelor's; a terminal-only school only in Career-Technical"
    (let [bach-only {:institution-name "SLU" :section :bachelors
                     :programs [{:program-title "History" :section :bachelors}]}
          term-only {:institution-name "BRCC" :section :career-technical
                     :programs [{:program-title "Welding" :section :career-technical}]}
          insts [bach-only term-only]]
      (is (= ["SLU"] (mapv :institution-name (page/institutions-in-section :bachelors insts)))
          "only the bachelor's-only school in Bachelor's")
      (is (= ["BRCC"] (mapv :institution-name (page/institutions-in-section :career-technical insts)))
          "only the terminal-only school in Career-Technical"))))

;; ---------------------------------------------------------------------------
;; 0078 — Career-Technical cards carry NO S/T/R badge (ADR 0008: S/T/R is a within-
;; Bachelor's grouping). A genuine Open-Admission badge may still survive. Bachelor's
;; cards (career-technical? nil/false) keep their S/T/R badge unchanged.
;; ---------------------------------------------------------------------------

(deftest career-technical-card-suppresses-str-badge
  (testing "career-technical? true drops a Safety/Target/Reach classification"
    (let [safety-inst {:institution-name "LSU-Alexandria"
                       :str-badge {:key "safety" :label "Safety"}}]
      (is (some? (:classification (adapter/school->card-props safety-inst nil false)))
          "the Bachelor's card KEEPS its Safety classification")
      (is (nil? (:classification (adapter/school->card-props safety-inst nil true)))
          "the Career-Technical card OMITS the Safety classification")))
  (testing "a genuine Open-Admission badge survives on a career-technical card"
    (let [open-inst {:institution-name "BRCC"
                     :str-badge {:key "open" :label "Open Admission"}}
          cls (:classification (adapter/school->card-props open-inst nil true))]
      (is (some? cls) "the Open-Admission badge survives")
      (is (= "open" (some-> (:key cls) name)) "and it is the open badge, never S/T/R"))))

;; ---------------------------------------------------------------------------
;; Slice 0059 — advisor identity card. `advisor->card-props` is the PURE adapter
;; from the Student-view query's :advisor map {:name :position :email
;; :scheduling-url :headshot-url} to the reused rec-demo `advisor-card` props
;; {:headshot :name :title :email :appointment-url}. No advisor → nil (card
;; omitted, present-by-data). No headshot → an initials-avatar data-URI.
;; ---------------------------------------------------------------------------

(deftest advisor-initials-first-and-last
  (testing "up to two uppercase initials from first + last word"
    (is (= "AR" (page/advisor-initials "Alex Rivera")))
    (is (= "M" (page/advisor-initials "Maya")))
    (is (= "AQ" (page/advisor-initials "Alex Q. Rivera")) "first two words")
    (is (= "" (page/advisor-initials nil)) "nil → no initials")
    (is (= "" (page/advisor-initials "   ")) "blank → no initials")))

(deftest advisor->card-props-nil-when-no-advisor
  (testing "no advisor → nil so the card is omitted (present-by-data)"
    (is (nil? (page/advisor->card-props nil)))))

(deftest advisor->card-props-maps-shape
  (testing "the query :advisor shape maps onto the rec-demo advisor-card props"
    (let [props (page/advisor->card-props
                 {:name "Alex Rivera"
                  :email "alex@thebryc.org" :scheduling-url "https://cal.example/alex"
                  :profile-photo-url "https://img.example/alex.jpg"})]
      (is (= "Alex Rivera" (:name props)))
      (is (= "BRYC Senior Advisor" (:title props)) "title is the fixed BRYC role")
      (is (= "alex@thebryc.org" (:email props)))
      (is (= "https://cal.example/alex" (:appointment-url props)) ":scheduling-url → :appointment-url")
      (is (= "https://img.example/alex.jpg" (:headshot props)) "real headshot url passes through"))))

(deftest advisor->card-props-initials-avatar-fallback
  (testing "a blank headshot-url → an initials-avatar data-URI (never a broken image)"
    (let [props (page/advisor->card-props
                 {:name "Alex Rivera"
                  :email "alex@thebryc.org" :scheduling-url "" :profile-photo-url ""})]
      (is (clojure.string/starts-with? (:headshot props) "data:image/svg+xml,")
          "fallback is an inline SVG data-URI")
      (is (clojure.string/includes? (:headshot props) "AR")
          "the initials appear in the SVG"))))
