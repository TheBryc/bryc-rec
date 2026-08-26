(ns components.rec-demo.adapter-test
  "UI-1 seam tests: the PURE engine-record → component-props adapter, driven by a
   REAL saved-pool institution (components.rec-demo.sample/real-institution)."
  (:require [cljs.test :refer [deftest testing is are]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.rec-demo.sample :as sample]
            [components.rec-demo.adapter :as adapter]
            [components.rec-demo.core :as core]
            [components.rec-demo.data :as data]))

(def inst sample/real-institution)

(defn- figure [props kind]
  (some #(when (= kind (:kind %)) %) (:figures props)))

;; ---------------------------------------------------------------------------
;; school->about-props
;; ---------------------------------------------------------------------------

(deftest about-props-carries-grad-rate-tile
  (testing "grad-rate tile carries rate + norm indicator from the real record"
    (let [f (figure (adapter/school->about-props inst) :grad-rate)]
      (is (some? f) "a grad-rate figure is present")
      (is (= "30%" (:value f)))
      (is (= "4-Year Graduation Rate" (:label f)))
      (is (= "High" (:indicator f)) "carries the High/Medium/Low norm indicator"))))

(deftest about-props-carries-demographic-enrollment-tile
  (testing "demographic enrollment tile (own-group %) with count-of-total sub"
    (let [f (figure (adapter/school->about-props inst) :enrollment)]
      (is (some? f))
      (is (= "17.9%" (:value f)))
      (is (= "Black/African American enrollment" (:label f)))
      (is (= "985 of 5,518 students" (:sub f)) "commafies count + total"))))

;; 0046 — the Admission (Safety/Target/Reach) tile is DROPPED from the six-tile
;; grid (STR stays HEADER-level via school->card-props). A fully-populated 4-year
;; school shows exactly 6 tiles: grad-rate · enrollment · acceptance · retention ·
;; avg-debt · residential.
(deftest about-props-drops-admission-figure
  (testing "the Admission (Safety/Target/Reach) tile is NOT in the six-tile grid"
    (let [enriched (assoc inst
                          :avg-debt {:amount 22113 :caption "median, completers"}
                          :residential {:type "Residential" :city "Hammond"
                                        :distance-label "~45 miles from Baton Rouge"})
          props    (adapter/school->about-props enriched)]
      (is (nil? (figure props :admission)) "no admission figure in the grid")
      (is (not (some #(= "Admission" (:label %)) (:figures props)))
          "no tile carries the 'Admission' label")
      (is (= 6 (count (:figures props)))
          "a fully-populated 4-year school shows exactly 6 tiles")
      ;; STR is still carried for the CARD HEADER (present-by-data) — it just left the grid
      (is (some? (:classification (adapter/school->card-props enriched)))
          "the Safety/Target/Reach badge stays header-level"))))

(deftest about-props-carries-ability-position-and-retention
  (let [props (adapter/school->about-props inst)]
    (is (= "your ACT 19 is at their 25th (19), below the 75th (24)" (:ability-position props)))
    (is (= "77%" (:retention props)))))

(deftest about-props-carries-narrative-vector
  (testing "OUR :about-school-narrative bullets are carried verbatim"
    (let [props (adapter/school->about-props inst)]
      (is (vector? (:narrative props)))
      (is (= 3 (count (:narrative props))))
      (is (= (:about-school-narrative inst) (:narrative props))))))

(deftest about-props-drops-legacy-cruft
  (testing "no legacy cruft leaks into props"
    (let [props (adapter/school->about-props inst)]
      (is (nil? (:why-fits-bullets props)))
      (is (nil? (:financial-bullets props)))
      (is (nil? (:cost-summary props)))
      (is (nil? (:admissions-data props))))))

;; ---------------------------------------------------------------------------
;; school->costs-props
;; ---------------------------------------------------------------------------

(deftest costs-props-carries-waterfall-and-advice-bullets
  (let [props (adapter/school->costs-props inst)]
    (is (= (:costs inst) (:costs props)) "the :costs waterfall map passes through")
    (is (= 20979 (get-in props [:costs :coa])))
    ;; 0055 — the dollar-restating LLM :costs-narrative is REPLACED by the prototype's
    ;; templated affordability advice (Fixed text). Nicholls (has :living, not commuter)
    ;; → the residential branch.
    (is (not= (:costs-narrative inst) (:narrative props))
        "no longer the LLM dollar bullets")
    (is (= 2 (count (:narrative props))))
    (is (re-find #"weigh on-campus vs\. off-campus housing" (first (:narrative props)))
        "residential (non-commuter) affordability bullet")
    (is (re-find #"Loans can cover what's left" (second (:narrative props)))
        "the always-on loans bullet")
    ;; short-name (falls back to institution-name) + present-by-data links: Nicholls
    ;; carries :npc-url but NO :faid-url in the sample → NPC link only.
    (is (= "Nicholls State University" (:short-name props)))
    (is (= 1 (count (:links props))) "NPC only — the sample has no FAIDURL")
    (is (re-find #"Net Price Calculator" (:label (first (:links props)))))
    (is (nil? (:why-fits-bullets props)) "no legacy cruft")))

(deftest costs-advice-bullets-branch
  (testing "commuter → live-at-home bullet; residential → on/off-campus bullet; both keep the loans bullet"
    (let [commuter    (adapter/costs-advice-bullets {:commuter? true})
          residential (adapter/costs-advice-bullets {})]
      (is (= 2 (count commuter)))
      (is (re-find #"live at home instead of on campus" (first commuter)))
      (is (re-find #"apply for scholarships \(see the Scholarships tab\)" (first commuter)))
      (is (re-find #"weigh on-campus vs\. off-campus housing" (first residential)))
      (is (not (re-find #"live at home" (first residential))) "residential does NOT tell them to live at home")
      (is (= (second commuter) (second residential)) "same loans bullet on both branches")
      (is (re-find #"deferred cost, not free aid" (second commuter))))))

(deftest costs-links-present-by-data
  (testing "two links when both URLs present; NPC-only when :faid-url absent; templated with short-name"
    (let [both     (adapter/costs-links {:npc-url "https://x.edu/npc" :faid-url "https://x.edu/faid"} "Xavier")
          npc-only (adapter/costs-links {:npc-url "https://x.edu/npc"} "Xavier")
          none     (adapter/costs-links {} "Xavier")]
      (is (= 2 (count both)))
      (is (= "Estimate your net cost — Xavier's Net Price Calculator →" (:label (first both))))
      (is (= "https://x.edu/npc" (:href (first both))))
      (is (= "Explore aid — Xavier's Financial Aid office →" (:label (second both))))
      (is (= "https://x.edu/faid" (:href (second both))))
      (is (= 1 (count npc-only)) "no :faid-url → only the NPC link (present-by-data)")
      (is (= "Estimate your net cost — Xavier's Net Price Calculator →" (:label (first npc-only))))
      (is (empty? none) "neither URL present → no links"))))

(deftest costs-section-render-proof
  (testing "RENDER PROOF: two present-by-data links (templated) + advice bullets + FAFSA disclaimer kept"
    (let [residential (assoc-in inst [:costs :faid-url] "https://www.nicholls.edu/financial-aid/")
          props (adapter/school->costs-props residential)
          html  (dom-server/render-to-string ($ core/costs-section (assoc props :record residential)))]
      (is (re-find #"Net Price Calculator" html) "NPC link renders")
      (is (re-find #"Financial Aid office" html) "Financial Aid office link renders")
      (is (re-find #"Estimate your net cost — Nicholls State University" html)
          "the NPC link is templated with the school name")
      (is (re-find #"weigh on-campus vs\. off-campus housing" html) "residential advice bullet renders")
      (is (re-find #"complete the" html) "the FAFSA disclaimer is preserved")
      (is (re-find #"FAFSA" html))
      (is (not (re-find #"published cost of attendance is" html)) "no restated-dollar LLM bullets")
      (is (not (re-find #"See this school's cost & financial-aid info" html))
          "the old single generic link is gone"))))

(deftest about-props-carries-acceptance-and-retention-figures
  (testing "acceptance-rate (from :admissions-data) + first-year retention render as tiles"
    (let [props (adapter/school->about-props inst)
          acc   (figure props :acceptance)
          ret   (figure props :retention)]
      (is (some? acc) "acceptance-rate figure present")
      (is (= "98%" (:value acc)) "0..1 acceptance fraction → whole-percent string")
      (is (= "Acceptance Rate" (:label acc)))
      (is (some? ret) "first-year retention figure present")
      (is (= "77%" (:value ret)) "retention passes through the carried string")
      (is (= "First-Year Retention" (:label ret)))
      ;; existing tiles are NOT removed
      (is (some? (figure props :grad-rate)) "grad-rate tile still present")
      (is (some? (figure props :enrollment)) "enrollment tile still present")
      (is (nil? (figure props :admission)) "admission tile removed from the grid (0046)")
      ;; :retention is ALSO still carried as a top-level prop (unchanged)
      (is (= "77%" (:retention props))))))

(deftest about-props-acceptance-and-retention-omitted-when-absent
  (testing "no acceptance-rate / no retention → those tiles omitted (no dead tile)"
    (let [props (adapter/school->about-props
                 (-> inst (dissoc :retention) (update :admissions-data dissoc :acceptance-rate)))]
      (is (nil? (figure props :acceptance)) "no acceptance-rate → no acceptance tile")
      (is (nil? (figure props :retention)) "no retention → no retention tile")
      (is (some? (figure props :grad-rate)) "other tiles unaffected"))))

;; ---------------------------------------------------------------------------
;; 0047 — per-school-type tile #1: a 4-year selective school shows ACCEPTANCE
;; RATE; a 2-year / open-admission school shows a TRANSFER-OUT RATE tile instead
;; (V3 spec §5.2), NEVER a fabricated "0% acceptance". The engine (about_school.clj)
;; already attaches :transfer-out (IPEDS GR, GRTYPE 35 / CHRTSTAT 22) for community
;; colleges; the adapter picks the tile by school type — never both.
;; ---------------------------------------------------------------------------

(def two-year-inst
  "A 2-year / open-admission institution (BRCC-like): the engine keeps acceptance
   nil (open-admission schools report no acceptance_rate) and attaches a :transfer-out
   figure (IPEDS GR, GRTYPE 35 / CHRTSTAT 22)."
  (-> inst
      (assoc :sectors ["Public, 2-year"]
             :transfer-out {:rate "17%" :rate-num 17.0 :count 167 :cohort 964
                            :timeframe "within 3 years"
                            :sub "≈167 of 964 first-time students transfer to continue elsewhere (within 3 yrs)"
                            :source "IPEDS Graduation Rates — transfer-out, 2024"})
      (update :admissions-data dissoc :acceptance-rate)))

(deftest about-props-two-year-shows-transfer-out-not-acceptance
  (testing "a 2-year / open-admission school shows Transfer-Out Rate, never a 0% acceptance"
    (let [props (adapter/school->about-props two-year-inst)
          tr    (figure props :transfer-out)]
      (is (some? tr) "a transfer-out figure is present for a 2-year school")
      (is (= "17%" (:value tr)) "value is the transfer-out % (traces to IPEDS GR)")
      (is (= "Transfer-Out Rate" (:label tr)))
      (is (re-find #"167 of 964" (:sub tr)) "sub carries the ≈N of M explanation")
      (is (re-find #"transfer to continue elsewhere" (:sub tr)))
      (is (nil? (figure props :acceptance)) "NO acceptance tile for a 2-year school")
      (is (not (some #(= "0%" (:value %)) (:figures props)))
          "NEVER a 0% figure anywhere in the grid"))))

(deftest about-props-four-year-shows-acceptance-not-transfer-out
  (testing "a 4-year selective school still shows Acceptance Rate, and no transfer-out"
    (let [props (adapter/school->about-props inst)]
      (is (some? (figure props :acceptance)) "acceptance tile present for a 4-year school")
      (is (= "98%" (:value (figure props :acceptance))))
      (is (nil? (figure props :transfer-out)) "NO transfer-out tile for a 4-year school"))))

;; ---------------------------------------------------------------------------
;; present-by-data
;; ---------------------------------------------------------------------------

(deftest costs-props-absent-when-no-costs
  (is (nil? (adapter/school->costs-props (dissoc inst :costs)))
      "an institution with no :costs yields no costs-props"))

(deftest about-enrollment-falls-back-to-total
  (testing "without a demographic %, the enrollment tile shows total enrollment"
    (let [f (figure (adapter/school->about-props
                     (dissoc inst :enrollment-group-pct :enrollment-group-count :enrollment-group-label))
                    :enrollment)]
      (is (= "5,518" (:value f)))
      (is (= "Total enrollment" (:label f))))))

(deftest about-figures-omitted-when-data-missing
  (testing "missing grad-rate / str-badge → those figures are omitted"
    (let [props (adapter/school->about-props (dissoc inst :grad-rate :str-badge))]
      (is (nil? (figure props :grad-rate)))
      (is (nil? (figure props :admission)))
      (is (some? (figure props :enrollment)) "enrollment still present"))))

(deftest about-narrative-omitted-when-missing
  (is (nil? (:narrative (adapter/school->about-props (dissoc inst :about-school-narrative))))))

;; ---------------------------------------------------------------------------
;; 0039 (epic match-tavidee) — Residential/Campus tile. The engine attaches a
;; structured :residential map (type/housing?/city/distance-label); the adapter
;; composes the tile (value = Residential/Commuter, label = Campus, sub = city ·
;; distance-label · Notion Setting). Distance-0 → "Located in Baton Rouge" (never
;; "0 miles"); present-by-data when the record carries no :residential.
;; ---------------------------------------------------------------------------

(deftest about-props-carries-residential-tile
  (testing "residential tile: type value + Campus label + city/distance sub"
    (let [f (figure (adapter/school->about-props
                     (assoc inst :residential {:type "Residential" :housing? true
                                               :city "Hammond"
                                               :distance-label "~45 miles from Baton Rouge"}))
                    :residential)]
      (is (some? f) "a residential figure is present")
      (is (= "Residential" (:value f)))
      (is (= "Campus" (:label f)))
      (is (re-find #"Hammond" (:sub f)) "city in the sub")
      (is (re-find #"~45 miles from Baton Rouge" (:sub f)) "distance label in the sub"))))

(deftest about-props-residential-commuter-distance-zero
  (testing "commuter + distance-0 → 'Located in Baton Rouge', never '0 miles', no city dup"
    (let [f (figure (adapter/school->about-props
                     (assoc inst :residential {:type "Commuter" :housing? false
                                               :city "Baton Rouge"
                                               :distance-label "Located in Baton Rouge"}))
                    :residential)]
      (is (= "Commuter" (:value f)))
      (is (= "Located in Baton Rouge" (:sub f)) "distance-0 line; city not duplicated")
      (is (not (re-find #"0 miles" (:sub f))) "never renders '0 miles'"))))

(deftest about-props-residential-setting-enrichment
  (testing "Notion Setting (top-level :setting) is appended to the residential sub"
    (let [f (figure (adapter/school->about-props
                     (assoc inst :setting "Suburban"
                            :residential {:type "Residential" :city "Hammond"
                                          :distance-label "~45 miles from Baton Rouge"}))
                    :residential)]
      (is (re-find #"Suburban" (:sub f)) "Notion Setting enriches the sub"))))

(deftest about-props-residential-omitted-when-absent
  (testing "no :residential → no residential tile (present-by-data)"
    (is (nil? (figure (adapter/school->about-props (dissoc inst :residential)) :residential))
        "Nicholls fixture carries no :residential")))

(deftest about-school-section-renders-residential-tile
  (testing "RENDER PROOF: the Residential/Campus tile renders (value + label + sub)"
    (let [html (dom-server/render-to-string
                ($ core/about-school-section
                   (adapter/school->about-props
                    (assoc inst :setting "Suburban"
                           :residential {:type "Residential" :city "Hammond"
                                         :distance-label "~45 miles from Baton Rouge"}))))]
      (is (re-find #"Residential" html) "the Residential value renders")
      (is (re-find #"Campus" html) "the Campus label renders")
      (is (re-find #"45 miles from Baton Rouge" html) "the distance line renders")
      (is (re-find #"Suburban" html) "the Notion Setting renders"))))

;; ---------------------------------------------------------------------------
;; 0038 (epic match-tavidee) — Avg Debt at Graduation tile. The engine attaches
;; :avg-debt {:amount <GRAD_DEBT_MDN> :caption "median, completers"}; the adapter
;; formats "$X" (commafied) with the caption sub. Present-by-data when the record
;; carries no :avg-debt. The number traces to the Scorecard GRAD_DEBT_MDN column.
;; ---------------------------------------------------------------------------

(deftest about-props-carries-avg-debt-tile
  (testing "avg-debt tile: '$22,113' value + 'Avg Debt at Graduation' label + 'median, completers' sub"
    (let [f (figure (adapter/school->about-props
                     (assoc inst :avg-debt {:amount 22113 :caption "median, completers"}))
                    :avg-debt)]
      (is (some? f) "an avg-debt figure is present")
      (is (= "$22,113" (:value f)) "commafied dollar amount, traces to GRAD_DEBT_MDN")
      (is (= "Avg Debt at Graduation" (:label f)))
      (is (= "median, completers" (:sub f))))))

(deftest about-props-avg-debt-omitted-when-absent
  (testing "no :avg-debt → no avg-debt tile (present-by-data)"
    (is (nil? (figure (adapter/school->about-props (dissoc inst :avg-debt)) :avg-debt))
        "Nicholls fixture carries no :avg-debt")))

(deftest about-school-section-renders-avg-debt-tile
  (testing "RENDER PROOF: the Avg Debt tile renders (value + label + caption)"
    (let [html (dom-server/render-to-string
                ($ core/about-school-section
                   (adapter/school->about-props
                    (assoc inst :avg-debt {:amount 22113 :caption "median, completers"}))))]
      (is (re-find #"\$22,113" html) "the debt value renders")
      (is (re-find #"Avg Debt at Graduation" html) "the label renders")
      (is (re-find #"median, completers" html) "the caption renders"))))

;; ===========================================================================
;; UI-3 — school-card HEADER props (name/city/state/type/website + STR badge),
;; driven by the SAME real institution record.
;; ===========================================================================

(deftest card-props-carries-header-fields
  (testing "name from institution-name; city/state from :location; type from :sectors"
    (let [props (adapter/school->card-props inst)]
      (is (= "Nicholls State University" (:name props)))
      (is (= "Thibodaux" (:city props)))
      (is (= "LA" (:state props)))
      (is (= "Public, 4-year or above" (:type props)) "type is the first sector"))))

(deftest card-props-carries-website-when-present
  (testing "website + label carried when the record has them"
    (let [props (adapter/school->card-props inst)]
      (is (= "https://www.nicholls.edu" (:website props)))
      (is (= "nicholls.edu" (:website-label props))))))

(deftest card-props-website-omitted-when-absent
  (testing "no :website on the record → :website/:website-label omitted (present-by-data)"
    (let [props (adapter/school->card-props (dissoc inst :website :website-label))]
      (is (nil? (:website props)))
      (is (nil? (:website-label props))))))

(deftest card-props-classification-from-str-badge
  (testing "classification lifts OUR :str-badge to {:key :label :color}, color by key"
    (let [cls (:classification (adapter/school->card-props inst))]
      (is (= "target" (:key cls)))
      (is (= "Target" (:label cls)))
      (is (= "#e8a93b" (:color cls)) "target → amber"))))

(deftest card-props-str-color-map-total
  (testing "STR color map is total over {safety target reach open}"
    (are [k color] (= color (:color (:classification
                                     (adapter/school->card-props
                                      (assoc inst :str-badge {:key k :label k})))))
      "safety" "#2f9e44"      ;; green
      "target" "#e8a93b"      ;; amber
      "reach"  "#c92a4a"      ;; red
      "open"   "#676868")     ;; neutral
    (testing "keyword keys resolve too (name-coerced)"
      (is (= "#e8a93b" (:color (:classification
                                (adapter/school->card-props
                                 (assoc inst :str-badge {:key :target :label "Target"})))))))))

(deftest card-props-present-by-data
  (testing "missing :location / :sectors → those header fields omitted"
    (let [props (adapter/school->card-props (dissoc inst :location :sectors))]
      (is (nil? (:city props)))
      (is (nil? (:state props)))
      (is (nil? (:type props)))
      (is (some? (:name props)) "name still present")
      (is (some? (:classification props)) "STR badge still present"))))

;; ===========================================================================
;; 0086 (Fix B) — school-header sentinel guard. A data-poor institution carries
;; whole-string sentinels (city="Unknown", sectors=["Unknown"]) that rendered as
;; "Unknown, LA · Unknown". Route :city/:state/:type through the SAME `present-value`
;; guard (0072) so a sentinel/blank is dropped (present-by-data) — "Unknown, LA ·
;; Unknown" collapses to just the real state "LA". Real values pass through unchanged.
;; ===========================================================================

(deftest card-props-drops-sentinel-city-and-type-keeps-real-state
  (testing "city='Unknown' → :city absent; state='LA' kept; sectors=['Unknown'] → :type absent"
    (let [props (adapter/school->card-props
                 (assoc inst
                        :location {:city "Unknown" :state "LA"}
                        :sectors ["Unknown"]))]
      (is (nil? (:city props)) "sentinel city dropped (present-by-data)")
      (is (= "LA" (:state props)) "real state survives")
      (is (nil? (:type props)) "sentinel sector dropped (present-by-data)"))))

(deftest card-props-real-city-state-type-pass-through
  (testing "real city/state/type are unchanged by the sentinel guard"
    (let [props (adapter/school->card-props
                 (assoc inst
                        :location {:city "Thibodaux" :state "LA"}
                        :sectors ["Public, 4-year or above"]))]
      (is (= "Thibodaux" (:city props)))
      (is (= "LA" (:state props)))
      (is (= "Public, 4-year or above" (:type props))))))

;; ===========================================================================
;; 0074 — render-seam GROUP FALLBACK for the STR badge. A school card rendered
;; inside a Safety/Target/Reach group ALWAYS shows a badge: it uses its OWN
;; :str-badge when present, else falls back to the GROUP tier the page renders it
;; under (so the badge is consistent-by-construction with its visible group
;; heading and can never be missing — e.g. ITI Technical College, an Unknown-
;; sector / Unknown-admissions school with NO :str-badge, under the Safety group).
;; A card outside any STR group (no group threaded) still shows no tier badge
;; (present-by-data — no false tier).
;; ===========================================================================

(deftest card-props-own-str-badge-wins-over-group
  (testing "institution WITH its OWN :str-badge + a group tier → uses its OWN badge"
    ;; inst carries {:key \"target\" :label \"Target\"}; render it under a :safety
    ;; group — the OWN Target badge must win, the group must NOT override it.
    (let [cls (:classification (adapter/school->card-props inst :safety))]
      (is (= "target" (:key cls)) "the school's own STR tier is kept")
      (is (= "Target" (:label cls)))
      (is (= "#e8a93b" (:color cls)) "own badge → amber, NOT the group's green"))))

(deftest card-props-group-fallback-when-no-str-badge
  (testing "institution WITHOUT :str-badge + group :safety → derive the green Safety badge"
    (let [cls (:classification (adapter/school->card-props (dissoc inst :str-badge) :safety))]
      (is (some? cls) "a data-poor school in the Safety group STILL shows a badge")
      (is (= "safety" (:key cls)))
      (is (= "Safety" (:label cls)) "label matches the group heading")
      (is (= "#2f9e44" (:color cls)) "green — the group's STR color"))
    (testing "keyword AND string group keys resolve, across all tiers"
      (are [group key color] (let [cls (:classification
                                        (adapter/school->card-props (dissoc inst :str-badge) group))]
                               (and (= key (:key cls)) (= color (:color cls))))
        :safety  "safety" "#2f9e44"
        :target  "target" "#e8a93b"
        :reach   "reach"  "#c92a4a"
        "safety" "safety" "#2f9e44"))))

(deftest card-props-no-group-no-badge-no-classification
  (testing "no :str-badge + NO group → no classification (present-by-data, no false tier)"
    (is (nil? (:classification (adapter/school->card-props (dissoc inst :str-badge))))
        "single-arity (no group) → no fallback")
    (is (nil? (:classification (adapter/school->card-props (dissoc inst :str-badge) nil)))
        "explicit nil group → no fallback")))

(deftest str-badge-is-skinny-one-line
  (testing "CSS snapshot: the str-badge pill class pins it to one line (Defect A)"
    (let [html (dom-server/render-to-string
                ($ core/str-badge
                   {:classification {:key "safety" :label "Safety" :color "#2f9e44"}}))]
      (is (re-find #"whitespace-nowrap" html) "badge never wraps to two lines")
      (is (re-find #"shrink-0" html) "badge does not compress in the flex row"))))

;; ===========================================================================
;; School Profiles (0037) — the card-header LOGO. The engine attaches the Notion
;; profile's :logo-url onto the institution; the adapter lifts it to :logo, and the
;; card header renders the logo <img> when present, defaulting to the title/favicon
;; when absent (present-by-data). Numeric stats stay IPEDS.
;; ===========================================================================

(deftest card-props-carries-logo-when-present
  (testing ":logo-url on the institution → :logo in the card-header props"
    (let [props (adapter/school->card-props (assoc inst :logo-url "https://slu.edu/wordmark.png"))]
      (is (= "https://slu.edu/wordmark.png" (:logo props))))))

(deftest card-props-logo-omitted-when-absent
  (testing "no :logo-url on the record → no :logo (present-by-data → title fallback)"
    (let [props (adapter/school->card-props (dissoc inst :logo-url))]
      (is (nil? (:logo props))))))

(deftest student-school-card-renders-logo-when-present
  (testing "RENDER PROOF: a school WITH a Notion :logo-url renders the logo <img>"
    (let [html (dom-server/render-to-string
                ($ core/student-school-card
                   {:institution (assoc inst :logo-url "https://slu.edu/wordmark.png")
                    :student {}}))]
      (is (re-find #"slu\.edu/wordmark\.png" html) "the logo image src renders on the header"))))

(deftest student-school-card-falls-back-to-title-when-no-logo
  (testing "RENDER PROOF: a school with NO :logo-url falls back to the school name (title)"
    (let [html (dom-server/render-to-string
                ($ core/student-school-card
                   {:institution (dissoc inst :logo-url) :student {}}))]
      (is (re-find #"Nicholls State University" html) "the school name (title) renders as the fallback")
      (is (not (re-find #"wordmark\.png" html)) "no logo image when the record carries none"))))

;; ===========================================================================
;; 0076 — School-card blank logo box: the header logo <img> guard was truthiness-
;; only with NO onError, so a well-formed-but-broken Notion logo URL reserved its
;; h-9/h-10 box and painted nothing (Northwestern State University's empty ~40px
;; box). The fix is a client-side degradation seam: the logo <img> carries an
;; :on-error that falls logo → favicon → name-only (no reserved box). Two sites
;; (student-school-card header + the older school-tile) share `core/school-logo`;
;; the pure `core/logo-element` builds the stage's <img> with an injected
;; state-advance capability (set-stage!) so the fallback is asserted WITHOUT a DOM.
;; ===========================================================================

(deftest logo-element-logo-stage-carries-onerror-advancing-to-favicon
  (testing "the header logo <img> carries an :on-error that degrades logo→favicon (the missing guard)"
    (let [calls (atom [])
          el    (core/logo-element {:stage :logo :logo "https://slu.edu/wordmark.png"
                                    :name "SLU" :website "https://slu.edu"
                                    :set-stage! #(swap! calls conj %)})]
      (is (some? el) "the logo stage renders an <img> element")
      (is (fn? (.. el -props -onError)) "the logo <img> carries an onError handler")
      (is (re-find #"h-9" (.. el -props -className)) "working-logo sizing/classes are unchanged")
      ((.. el -props -onError))
      (is (= [:favicon] @calls) "onError degrades logo→favicon"))))

(deftest logo-element-logo-stage-caps-width-tightly
  (testing "0086 (Fix A): the header logo <img> caps its width via a fixed pixel token (max-w-[140px]), NOT maxWidth 40% — a wide SVG wordmark can never dominate the header and push the name to center"
    (let [el (core/logo-element {:stage :logo :logo "https://x.edu/nsula-logo.svg"
                                 :name "Northwestern" :website "https://www.nsula.edu"
                                 :set-stage! identity})
          class (.. el -props -className)]
      (is (re-find #"max-w-\[140px\]" class) "the logo carries the fixed 140px width cap")
      (is (re-find #"h-9" class) "the height constraint (h-9/h-10) is kept — working logos unchanged")
      (is (re-find #"object-contain" class) "object-contain is kept so working logos scale correctly")
      (is (not (and (.. el -props -style) (= "40%" (.. el -props -style -maxWidth))))
          "the old maxWidth 40% inline cap is gone (it let a wide wordmark dominate)"))))

(deftest logo-element-favicon-stage-degrades-to-name-only
  (testing "the favicon fallback carries an :on-error that degrades favicon→name-only, NOT the reserved logo box"
    (let [calls (atom [])
          el    (core/logo-element {:stage :favicon :website "https://www.nicholls.edu"
                                    :name "Nicholls" :set-stage! #(swap! calls conj %)})]
      (is (some? el) "favicon stage renders (the website resolves a favicon)")
      (is (fn? (.. el -props -onError)) "the favicon <img> carries an onError handler")
      (is (re-find #"w-7" (.. el -props -className)) "favicon uses the small favicon box")
      (is (not (re-find #"h-9" (.. el -props -className))) "favicon is NOT the reserved h-9/h-10 logo box")
      ((.. el -props -onError))
      (is (= [:name-only] @calls) "onError degrades favicon→name-only"))))

(deftest logo-element-name-only-reserves-no-box
  (testing "name-only renders NOTHING — no reserved logo box (this empty box IS the bug)"
    (is (nil? (core/logo-element {:stage :name-only :name "Nicholls" :set-stage! identity}))
        "name-only renders no <img> at all (name flush-left, no reserved space)")))

(deftest logo-element-favicon-without-website-reserves-no-box
  (testing "favicon stage with no resolvable website → no <img> (falls through to name-only, no box)"
    (is (nil? (core/logo-element {:stage :favicon :website nil :name "X" :set-stage! identity}))
        "no website → no favicon → no reserved box")))

(deftest school-logo-error-state-renders-favicon-then-name-only
  (testing "RENDER PROOF: school-logo at the favicon stage renders the favicon, no h-9/h-10 box"
    (let [html (dom-server/render-to-string
                ($ core/school-logo {:initial-stage :favicon
                                     :website "https://www.nicholls.edu" :name "Nicholls"}))]
      (is (re-find #"icons\.duckduckgo\.com" html) "degrades to the domain favicon")
      (is (not (re-find #"h-9" html)) "no reserved h-9/h-10 logo box in the favicon fallback")))
  (testing "RENDER PROOF: school-logo at the name-only stage renders NO img box at all"
    (let [html (dom-server/render-to-string
                ($ core/school-logo {:initial-stage :name-only :name "Nicholls"}))]
      (is (not (re-find #"<img" html)) "name-only reserves no logo box (the empty-box bug is gone)")
      (is (not (re-find #"h-9" html)) "no reserved h-9/h-10 logo box"))))

(deftest school-tile-renders-logo-through-fallback-seam
  (testing "the older school-tile renders its present logo through core/school-logo (both sites fixed)"
    (let [html (dom-server/render-to-string
                ($ core/school-tile
                   {:school {:name "Northwestern State University"
                             :logo "https://x.edu/logo.png" :website "https://www.nsula.edu"
                             :act-25 18 :act-75 25}
                    :student {:act 20}}))]
      (is (re-find #"x\.edu/logo\.png" html) "school-tile still renders a present logo (unchanged working path)")
      (is (re-find #"Northwestern State University" html) "the school name renders alongside the logo"))))

;; ===========================================================================
;; UI-2 — program-anchored adapter (Overview / Salary / Careers), driven by a
;; REAL saved-pool PROGRAM (components.rec-demo.sample/real-program).
;; ===========================================================================

(def prog sample/real-program)

;; ---------------------------------------------------------------------------
;; program->overview-props
;; ---------------------------------------------------------------------------

(deftest overview-props-drops-in-the-overview-map
  (testing "OUR :overview map (credential-line/student-connection/caveat) passes through"
    (let [props (adapter/program->overview-props prog)]
      (is (= (get-in prog [:overview :credential-line]) (:credential-line props)))
      (is (= (get-in prog [:overview :student-connection]) (:student-connection props)))
      (is (= (get-in prog [:overview :caveat]) (:caveat props))))))

(deftest overview-props-carries-canonical-summary
  (is (= "Earn while learning."
         (:summary (adapter/program->overview-props
                    {:overview {:summary "Earn while learning."}})))))

(deftest overview-props-drops-legacy-cruft
  (testing "top-level legacy cruft never leaks into overview props"
    (let [props (adapter/program->overview-props prog)]
      (is (nil? (:personalized-overview props)))
      (is (nil? (:outcome-bullet props)))
      (is (nil? (:career-summary props)))
      (is (nil? (:best-for props)))
      (is (nil? (:key-differentiators props))))))

(deftest overview-props-legacy-python-era-fallback
  ;; Tavidee's old pools: Python-era programs carry prose under :personalized-overview /
  ;; :best-for / :program-specific-bullets and NO :overview map at all. Without a
  ;; fallback the Overview block rendered empty for every last-year program. The
  ;; legacy text maps into the CANONICAL prop names only — the legacy keys themselves
  ;; still never leak (overview-props-drops-legacy-cruft stands).
  (let [legacy {:program-title "Nursing"
                :personalized-overview "A hands-on BSN program."
                :best-for "Students who want patient-facing work."
                :program-specific-bullets ["Clinical rotations start year 2"
                                           "NCLEX pass rate above state average"]}]
    (testing "legacy prose renders through the canonical props when :overview is absent"
      (let [props (adapter/program->overview-props legacy)]
        (is (= "A hands-on BSN program." (:summary props)))
        (is (= "Students who want patient-facing work." (:student-connection props)))
        (is (= ["Clinical rotations start year 2" "NCLEX pass rate above state average"]
               (:extra-bullets props)))
        (is (nil? (:personalized-overview props)) "the legacy KEY itself still never leaks")))
    (testing "a real :overview map WINS outright — the fallback never mixes eras"
      (let [both (assoc legacy :overview {:summary "New-era summary."})
            props (adapter/program->overview-props both)]
        (is (= "New-era summary." (:summary props)))
        (is (nil? (:student-connection props))
            "no legacy field bleeds in beside a present :overview map")))))

(deftest overview-props-optional-day-to-day-and-licensure
  (testing "day-to-day / licensure-exam carried only when present (present-by-data)"
    (let [bare (adapter/program->overview-props prog)]
      (is (nil? (:day-to-day bare)) "real program carries no day-to-day")
      (is (nil? (:licensure-exam bare)) "real program carries no licensure-exam"))
    (let [enriched (adapter/program->overview-props
                    (-> prog
                        (assoc-in [:overview :day-to-day] ["task a" "task b"])
                        (assoc :licensure-exam {:name "EXAM" :url "u" :note "n"})))]
      (is (= ["task a" "task b"] (:day-to-day enriched)))
      (is (= {:name "EXAM" :url "u" :note "n"} (:licensure-exam enriched))))))

(deftest overview-props-folds-apprenticeship-differentiation-bullets
  (testing "an APPRENTICESHIP's :bullets fold into the Overview as :extra-bullets (option 2)"
    (let [appr  (assoc prog :apprenticeship-title "Welder-Fitter Apprenticeship"
                            :bullets ["union-backed credentials" "hands-on wiring + code"])
          props (adapter/program->overview-props appr)]
      (is (= ["union-backed credentials" "hands-on wiring + code"] (:extra-bullets props)))))
  (testing "a COLLEGE/short-term program's :bullets are the Making-It-Pay-Off caveats — NOT folded"
    (let [college (assoc prog :bullets ["some rules caveat"])] ;; no :apprenticeship-title
      (is (nil? (:extra-bullets (adapter/program->overview-props college))))))
  (testing "an apprenticeship with NO :bullets carries no :extra-bullets (present-by-data)"
    (is (nil? (:extra-bullets (adapter/program->overview-props (assoc prog :apprenticeship-title "X")))))))

;; ---------------------------------------------------------------------------
;; program->salary-props
;; ---------------------------------------------------------------------------

(deftest salary-props-carries-chart-inputs
  (testing "chart inputs (earnings + living-wage band/area/state) pass through"
    (let [props (adapter/program->salary-props prog)]
      (is (= (get-in prog [:salary :earnings]) (:earnings props)))
      (is (= "Below" (:living-wage-band props)))
      (is (= 45496 (:living-wage-area-value props)))
      (is (= 42370 (:living-wage-state-value props))))))

(deftest salary-props-carries-demand-figures
  (testing "deterministic demand figures (stars/growth) pass through"
    (let [props (adapter/program->salary-props prog)]
      (is (= 4 (:stars props)) "stars lifted from inside :salary")
      (is (= "-7.12%" (:growth-rate props)))
      (is (= "-43" (:growth-net-new props)) "0053 — net-new jobs lifted to the salary props")
      (is (= "339" (:growth-openings props))))))

;; ---------------------------------------------------------------------------
;; 0053 — the deterministic Salary BOTTOM-LINE tier badge (replaces LLM :safe-bet).
;; PURE derives over the LWC ★ + living-wage band (EXACT prototype logic).
;; ---------------------------------------------------------------------------

(deftest salary-verdict-tier-matrix
  (testing "★≥4 AND band Above → 'Strong & stable'"
    (is (= "Strong & stable" (adapter/salary-verdict 5 "Above")))
    (is (= "Strong & stable" (adapter/salary-verdict 4 "Above"))))
  (testing "the Above gate is REQUIRED for 'Strong & stable' — ★4/★5 + Near/Below = 'Solid'"
    (is (= "Solid" (adapter/salary-verdict 4 "Near")) "★4+Near = 'Solid', NOT 'Strong'")
    (is (= "Solid" (adapter/salary-verdict 5 "Near")))
    (is (= "Solid" (adapter/salary-verdict 4 "Below")))
    (is (= "Solid" (adapter/salary-verdict 5 "Below"))))
  (testing "★3 → 'Solid' at any band (incl. Above — the ≥4 gate isn't met)"
    (is (= "Solid" (adapter/salary-verdict 3 "Above")))
    (is (= "Solid" (adapter/salary-verdict 3 "Near")))
    (is (= "Solid" (adapter/salary-verdict 3 "Below"))))
  (testing "the 'Mixed' FLOOR: ★≤2 → 'Mixed' regardless of band"
    (is (= "Mixed" (adapter/salary-verdict 2 "Above")))
    (is (= "Mixed" (adapter/salary-verdict 2 "Near")))
    (is (= "Mixed" (adapter/salary-verdict 1 "Above")))
    (is (= "Mixed" (adapter/salary-verdict 0 "Below")))))

(deftest salary-demand-word-branches
  (are [word stars] (= word (adapter/salary-demand-word stars))
    "High demand"   5
    "High demand"   4
    "Steady demand" 3
    "Some demand"   2
    "Some demand"   0))

(deftest salary-wage-phrase-branches
  (are [phrase band] (= phrase (adapter/salary-wage-phrase band))
    "earn above a living wage" "Above"
    "earn near a living wage"  "Near"
    "earn below a living wage" "Below")
  (testing "a nil/unknown band falls back to 'have competitive earnings' (present-by-data)"
    (is (= "have competitive earnings" (adapter/salary-wage-phrase nil)))
    (is (= "have competitive earnings" (adapter/salary-wage-phrase "Unknown")))))

(deftest salary-bottom-line-composes-verdict-and-sentence
  (testing "★5 + Above → Strong & stable + High-demand/above-wage sentence"
    (let [bl (adapter/salary-bottom-line 5 "Above")]
      (is (= "Strong & stable" (:verdict bl)))
      (is (= "High demand. Graduates earn above a living wage, with clear room to grow into higher-paying roles."
             (:sentence bl)))))
  (testing "★3 + Near → Solid + Steady-demand/near-wage sentence"
    (let [bl (adapter/salary-bottom-line 3 "Near")]
      (is (= "Solid" (:verdict bl)))
      (is (= "Steady demand. Graduates earn near a living wage, with clear room to grow into higher-paying roles."
             (:sentence bl)))))
  (testing "★2 + Below → the Mixed floor + Some-demand/below-wage sentence"
    (let [bl (adapter/salary-bottom-line 2 "Below")]
      (is (= "Mixed" (:verdict bl)))
      (is (re-find #"Some demand\. Graduates earn below a living wage" (:sentence bl)))))
  (testing "present-by-data: no ★ demand figure → nil (no bottom-line card)"
    (is (nil? (adapter/salary-bottom-line nil "Above")))))

(deftest salary-props-carries-bottom-line-not-safe-bet
  (testing "0053 — the deterministic :bottom-line is derived; the LLM :safe-bet is DROPPED"
    (let [props (adapter/program->salary-props prog)]
      ;; prog: ★4 + band "Below" → 'Solid'
      (is (= "Solid" (get-in props [:bottom-line :verdict])))
      (is (= "High demand. Graduates earn below a living wage, with clear room to grow into higher-paying roles."
             (get-in props [:bottom-line :sentence])))
      (is (nil? (:safe-bet props)) "the LLM :safe-bet is no longer surfaced")))
  (testing "present-by-data: a :salary with no ★ carries no :bottom-line"
    (let [props (adapter/program->salary-props
                 (-> prog (update :salary dissoc :stars) (dissoc :lwc-stars)))]
      (is (nil? (:bottom-line props)) "no stars → no bottom-line card"))))

(deftest salary-section-renders-net-new-and-tier-badge
  (testing "RENDER PROOF (0053): the PSEO growth tile shows net-new + total openings,
            and the bottom-line renders the tier verdict badge + templated sentence"
    (let [html (dom-server/render-to-string
                ($ core/salary-section
                   {:salary-props (adapter/program->salary-props prog)
                    :pathway {:acronym "BSA"}
                    :school {:short-name "NSU"}}))]
      ;; net-new copy on the 10-Year Growth card (growth_10yr -43, openings 339)
      (is (re-find #"net new jobs over 10 years" html) "net-new copy renders on the growth card")
      (is (re-find #"43 net new jobs" html) "the net-new figure renders")
      (is (re-find #"339 total openings incl. replacement/turnover" html) "total openings renders")
      ;; deterministic tier badge — prog is ★4 + band Below → 'Solid'
      (is (re-find #"Solid" html) "the tier verdict badge renders")
      (is (re-find #"Bottom line:" html) "the Bottom line label renders")
      (is (re-find #"High demand\. Graduates earn below a living wage" html) "the templated sentence renders")
      ;; the old LLM :safe-bet text is GONE
      (is (not (re-find #"there is strong local demand for this occupation" html))
          "the legacy LLM safe-bet copy no longer renders"))))

(deftest salary-props-stars-fall-back-to-program-lwc-stars
  (testing "when :salary has no :stars, fall back to the program's :lwc-stars"
    (let [props (adapter/program->salary-props
                 (-> prog (update :salary dissoc :stars) (assoc :lwc-stars 5)))]
      (is (= 5 (:stars props))))))

(deftest salary-props-carries-our-narrative
  (testing "OUR :descriptor is carried for the section to render (0053: :safe-bet is DROPPED)"
    (let [props (adapter/program->salary-props prog)]
      (is (= (get-in prog [:salary :descriptor]) (:descriptor props)))
      (is (nil? (:safe-bet props)) "the LLM :safe-bet is no longer surfaced (deterministic tier badge)"))))

(deftest salary-props-nil-when-no-salary
  (is (nil? (adapter/program->salary-props (dissoc prog :salary)))
      "a program with no :salary yields no salary-props"))

;; ---------------------------------------------------------------------------
;; program->careers-props
;; ---------------------------------------------------------------------------

(deftest careers-props-carries-roles-normalized
  (testing "roles pass through as a vector; the :soc drives the O*NET link via :onet-link tail"
    (let [props (adapter/program->careers-props prog)
          roles (:roles props)]
      (is (vector? roles))
      (is (= 5 (count roles)))
      (is (= "Chief Executives" (:title (first roles))))
      ;; :soc normalized to the O*NET-SOC (with .00) so (onet-url soc) matches :onet-link
      (is (= "11-1011.00" (:soc (first roles))))
      ;; advanced-credential? role surfaces a requirement badge label
      (let [teacher (some #(when (= "Business Teachers, Postsecondary" (:title %)) %) roles)]
        (is (some? (:requirement teacher)) "advanced-credential role carries a requirement label"))
      ;; deterministic median/education figure available as the row detail
      (is (re-find #"158,179" (:desc (first roles))) "median figure surfaced in the role detail"))))

(deftest careers-props-carries-pums-normalized
  (testing "pums passes through; occupations normalized (:onet from :onet-link, title falls back to soc)"
    (let [pums (:pums (adapter/program->careers-props prog))
          occs (:occupations pums)]
      (is (= "Louisiana" (:scope pums)))
      (is (true? (:under-50-callout? pums)))
      (is (= "Business Management And Administration" (:field pums)))
      ;; occ with :onet-link → :onet is the O*NET-SOC code
      (let [fin (some #(when (= "Financial Managers" (:title %)) %) occs)]
        (is (= "11-3031.00" (:onet fin))))
      ;; occ with NO title falls back to its SOC so it still renders
      (let [broad (some #(when (= "11-91XX" (:title %)) %) occs)]
        (is (some? broad) "titleless occupation renders under its SOC")
        (is (nil? (:onet broad)) "no O*NET link for the broad census bucket")))))

(deftest careers-props-carries-our-summary
  (testing "OUR :summary warm lead line is carried"
    (is (= (get-in prog [:careers :summary])
           (:summary (adapter/program->careers-props prog))))))

(deftest careers-props-nil-when-no-careers
  (is (nil? (adapter/program->careers-props (dissoc prog :careers)))
      "a program with no :careers yields no careers-props"))

(deftest careers-props-present-by-data-pums-omitted
  (testing "a program whose careers carries no pums omits :pums but keeps roles/summary"
    (let [props (adapter/program->careers-props (update prog :careers dissoc :pums))]
      (is (nil? (:pums props)))
      (is (seq (:roles props)))
      (is (some? (:summary props))))))

;; ---------------------------------------------------------------------------
;; split-career-roles — 0049. Partition normalized roles so BASE roles (attainable at
;; the pathway's own credential) are always shown and ADVANCED-credential roles (the
;; :requirement-badged ones) are revealed behind the "Show advanced-practice paths"
;; toggle — matching Tavidee's demo's grouping, but driven by OUR advanced-credential
;; flag instead of a hand-authored ordering. Order preserved WITHIN each group.
;; ---------------------------------------------------------------------------

(deftest split-career-roles-partitions-base-vs-advanced
  (testing "advanced-credential roles (carrying a :requirement badge) go to :advanced; the rest to :base, order preserved"
    (let [roles (:roles (adapter/program->careers-props prog))
          {:keys [base advanced]} (adapter/split-career-roles roles)]
      ;; sample/real-program: only 'Business Teachers, Postsecondary' is advanced-credential? true
      (is (= ["Chief Executives" "Industrial Production Managers" "Sales Managers" "Management Analysts"]
             (mapv :title base))
          "the four base roles are always-shown, in their original order (NOT the first-3-by-index)")
      (is (= ["Business Teachers, Postsecondary"] (mapv :title advanced))
          "the single advanced-credential role is tucked behind the toggle"))))

(deftest split-career-roles-no-advanced-yields-empty-tail
  (testing "when no role is advanced, :advanced is empty (so the caller renders no toggle) and every role is base"
    (let [roles [{:title "A" :desc "x"} {:title "B" :desc "y"}]
          {:keys [base advanced]} (adapter/split-career-roles roles)]
      (is (= ["A" "B"] (mapv :title base)))
      (is (empty? advanced)))))

(deftest split-career-roles-all-advanced-never-hides-everything
  (testing "when EVERY role is advanced, they all fall to :base so the section is never empty behind a toggle"
    (let [roles [{:title "A" :requirement "needs more"} {:title "B" :requirement "needs more"}]
          {:keys [base advanced]} (adapter/split-career-roles roles)]
      (is (= ["A" "B"] (mapv :title base)))
      (is (empty? advanced)))))

;; ---------------------------------------------------------------------------
;; cap-career-groups — 0088 / PRD 0012. Cap each split group to the best-5. The
;; roles arrive already ordered by LWC star (engine 0087) and split preserves that
;; order, so a plain take-5 keeps the strongest; attainable roles beyond 5 are
;; intentionally dropped. Silent — nothing new displayed.
;; ---------------------------------------------------------------------------

(deftest cap-career-groups-caps-each-group-at-five-preserving-order
  (testing "8 base + 8 advanced → 5 + 5, keeping the leading (strongest-outlook) 5 in order"
    (let [base (mapv (fn [i] {:title (str "B" i)}) (range 8))
          advanced (mapv (fn [i] {:title (str "A" i)}) (range 8))
          {:keys [base advanced]} (adapter/cap-career-groups {:base base :advanced advanced})]
      (is (= ["B0" "B1" "B2" "B3" "B4"] (mapv :title base))
          "the first 5 base roles are kept, in their original order")
      (is (= ["A0" "A1" "A2" "A3" "A4"] (mapv :title advanced))
          "the first 5 advanced roles are kept, in their original order"))))

(deftest cap-career-groups-under-five-base-shows-all-empty-advanced-hidden
  (testing "3 base + 0 advanced → all 3 base shown, advanced empty (toggle-hidden path)"
    (let [{:keys [base advanced]} (adapter/cap-career-groups
                                   {:base [{:title "A"} {:title "B"} {:title "C"}] :advanced []})]
      (is (= ["A" "B" "C"] (mapv :title base)))
      (is (empty? advanced)))))

(deftest cap-career-groups-degenerate-all-advanced-collapsed-to-base-still-capped
  (testing "when split collapsed everything to :base (all-advanced degenerate), the cap still trims to 5"
    (let [collapsed (adapter/split-career-roles
                     (mapv (fn [i] {:title (str "R" i) :requirement "needs more"}) (range 7)))
          {:keys [base advanced]} (adapter/cap-career-groups collapsed)]
      (is (= ["R0" "R1" "R2" "R3" "R4"] (mapv :title base))
          "the collapsed base (all 7 advanced fell to :base) is capped at 5, order preserved")
      (is (empty? advanced)))))

;; ---------------------------------------------------------------------------
;; program->time-to-credential-props — the Time & Completion card shape. The REAL
;; engine emits :time-to-credential as a map of SUB-MAPS ({:designed {…} :typical-
;; actual {…} :completers {…}}), NOT display strings — the reader must never pass a
;; raw map as a React child. This adapter flattens them into string tiles + notes.
;; ---------------------------------------------------------------------------

(def real-time-completion
  "A REAL engine :time-to-credential shape (phases/time_completion.clj): each of
   :designed / :typical-actual / :completers is a MAP, not a display string."
  {:program-title "Registered Nursing/Registered Nurse."
   :time-to-credential
   {:designed {:scope "designed" :label "Designed length" :length "4 years"
               :credential "Bachelor's degree" :years 4
               :note "Nominal length by credential type; a program's catalog length may differ."}
    :typical-actual {:scope "school-wide" :label "Typical time to degree" :level "Bachelor's"
                     :years 4.6 :years-exact 4.63 :display "4.6 years"
                     :note "School-wide across all first-time full-time Bachelor's students at this institution — not specific to this program."}
    :completers {:scope "program-specific" :label "Completers per year" :count 56
                 :cip "51.3801"
                 :note "Program-specific: total credentials awarded in this CIP at this institution."}}})

(deftest time-to-credential-props-maps-engine-shape
  (testing "engine sub-MAPS → flat string tiles (designed length+credential, typical display, completers count)"
    (let [props (adapter/program->time-to-credential-props real-time-completion)
          cards (:cards props)]
      (is (= 3 (count cards)) "designed + typical-actual + completers → 3 tiles")
      (is (every? string? (map :value cards))
          "EVERY tile value is a STRING — never a raw map (the React-child crash)")
      ;; designed length string + credential (as the sub-label)
      (let [d (first cards)]
        (is (= "4 years" (:value d)) "designed :length is the tile value string")
        (is (= "Designed length" (:label d)))
        (is (= "Bachelor's degree" (:sub d)) "credential surfaced as the sub-label"))
      ;; typical-actual display + note
      (is (= "4.6 years" (:value (second cards))) "typical-actual :display string")
      ;; completers count + note
      (is (= "56" (:value (nth cards 2))) "completers :count as a STRING")
      ;; the notes are the engine notes (strings), never a map dumped into prose
      (is (every? string? (:notes props)))
      (is (some #(re-find #"School-wide" %) (:notes props)) "typical-actual note carried")
      (is (some #(re-find #"Program-specific" %) (:notes props)) "completers note carried"))))

(deftest time-to-credential-props-present-by-data
  (testing "a missing sub-map → its tile omitted (no crash); nil when nothing resolves"
    (let [only-designed (adapter/program->time-to-credential-props
                         {:time-to-credential {:designed {:length "2 years" :credential "Associate"}}})]
      (is (= 1 (count (:cards only-designed))) "only the designed tile")
      (is (= "2 years" (:value (first (:cards only-designed))))))
    (is (nil? (adapter/program->time-to-credential-props {}))
        "no :time-to-credential data → nil (no section, present-by-data)")
    (is (nil? (adapter/program->time-to-credential-props {:time-to-credential {}}))
        "empty :time-to-credential → nil (no dead section)")))

(deftest time-to-credential-props-tolerates-demo-shape
  (testing "standalone-demo shape (:designed/:actual STRINGS + top-level :completions) still maps"
    (let [demo  {:time-to-credential {:designed "~2 years full-time" :actual "≈4.6 years"
                                      :actual-note "School-wide average across BRCC associate programs."}
                 :completions {:per-year 131 :year "2024"}}
          props (adapter/program->time-to-credential-props demo)
          cards (:cards props)]
      (is (= 3 (count cards)))
      (is (every? string? (map :value cards)) "demo string values stay strings")
      (is (= "~2 years full-time" (:value (first cards))))
      (is (= "≈4.6 years" (:value (second cards))))
      (is (= "131" (:value (nth cards 2))) "top-level :completions :per-year → tile"))))

(deftest time-to-credential-section-renders-engine-shape-without-crashing
  (testing "RENDER PROOF: the REAL engine sub-map shape renders as STRINGS — reproduces
            the reported 'Objects are not valid as a React child' crash (a raw map child)
            and proves it's fixed (render throws if any tile value is a map)"
    (let [html (dom-server/render-to-string
                ($ core/time-to-credential-section {:pathway real-time-completion}))]
      (is (re-find #"4 years" html) "designed :length renders as a tile value")
      ;; apostrophe is HTML-escaped by React (&#x27;), so match around it
      (is (re-find #"Bachelor.{0,6}s degree" html) "credential renders as the sub-label")
      (is (re-find #"4.6 years" html) "typical-actual :display renders")
      (is (re-find #"56" html) "completers :count renders")
      (is (re-find #"School-wide" html) "typical-actual :note renders as a caption")
      (is (not (re-find #"\[object Object\]" html)) "no stringified map leaks into the DOM"))))

;; ---------------------------------------------------------------------------
;; 0054 — Time & Completion no longer carries the degree-type CAVEATS: they moved to
;; the per-program 'Making It Pay Off' (:rules) section. This card must NOT surface them.
;; ---------------------------------------------------------------------------

(deftest time-to-credential-props-no-longer-carries-caveats
  (testing "even if a legacy record still had :time-to-credential :caveats, the card drops them (0054)"
    (let [legacy (assoc-in real-time-completion [:time-to-credential :caveats]
                           [{:title nil :body "Enroll full-time if you can."}])
          props  (adapter/program->time-to-credential-props legacy)]
      (is (nil? (:caveats props)) "Time & Completion props never carry the degree-type :caveats (in :rules)")
      (is (seq (:cards props)) "the tiles still render"))))

;; ---------------------------------------------------------------------------
;; 0061 — Time & Completion DOES surface the UNIVERSAL on-time actions ('Finish on time —
;; the moves that matter'). The engine attaches them under :time-to-credential
;; :on-time-actions as {:title :body} maps; the adapter FLATTENS them to STRINGS (:on-time)
;; and the reader renders them below the length card. Present-by-data.
;; ---------------------------------------------------------------------------

(def real-time-completion-with-on-time
  "A real :time-to-credential shape carrying the 0061 universal :on-time-actions caveats."
  (assoc-in real-time-completion [:time-to-credential :on-time-actions]
            [{:title nil :body "Enroll full-time if you can — it strongly raises your odds of finishing."}
             {:title "Watch out" :body "Take a full 15-credit load each term."}]))

(deftest time-to-credential-props-surfaces-on-time-actions
  (testing "engine :on-time-actions maps → flat STRING :on-time (title-less = body; titled = 'title: body')"
    (let [props (adapter/program->time-to-credential-props real-time-completion-with-on-time)]
      (is (vector? (:on-time props)))
      (is (every? string? (:on-time props)) "EVERY on-time bullet is a STRING — never a raw map child")
      (is (= "Enroll full-time if you can — it strongly raises your odds of finishing."
             (first (:on-time props))) "a title-less on-time bullet is just its body")
      (is (= "Watch out: Take a full 15-credit load each term."
             (second (:on-time props))) "a titled on-time bullet renders 'title: body'")
      (is (seq (:cards props)) "the length/typical/completers tiles still render")))
  (testing "present-by-data: NO :on-time-actions ⇒ no :on-time (length card only)"
    (is (nil? (:on-time (adapter/program->time-to-credential-props real-time-completion)))
        "an engine section without on-time carries no :on-time (empty Notion reality)")))

(deftest time-to-credential-section-renders-on-time-actions
  (testing "RENDER PROOF: the 'Finish on time' heading + on-time bullets render below the length card"
    (let [html (dom-server/render-to-string
                ($ core/time-to-credential-section {:pathway real-time-completion-with-on-time}))]
      (is (re-find #"Finish on time" html) "the on-time heading renders")
      (is (re-find #"Enroll full-time" html) "a title-less on-time bullet renders")
      (is (re-find #"Watch out" html) "a titled on-time bullet renders its title")
      (is (re-find #"4 years" html) "the length card still renders")
      (is (not (re-find #"\[object Object\]" html)) "no stringified map leaks into the DOM")))
  (testing "present-by-data: NO on-time ⇒ the 'Finish on time' block does NOT render"
    (let [html (dom-server/render-to-string
                ($ core/time-to-credential-section {:pathway real-time-completion}))]
      (is (not (re-find #"Finish on time" html))
          "empty Notion reality ⇒ Time & Completion is the length card only"))))

;; ---------------------------------------------------------------------------
;; 0054 — Making It Pay Off (:rules): the engine attaches a per-program :rules datum
;; for NON-BACHELOR'S programs ({:bullets [{:title :body}] :terminal? :transfer?
;; :terminal <str> :transfer <str>}). The adapter flattens bullets to STRINGS (never a
;; raw map React child); the reader renders terminal/transfer callouts + a bullet-list.
;; ---------------------------------------------------------------------------

(def real-rules-terminal
  "A terminal program's engine :rules datum: a terminal callout + on-time bullets."
  {:program-title "Welding Technology."
   :rules {:terminal? true :transfer? false
           :terminal "Terminal program: designed to take you straight into a job after you finish."
           :bullets [{:title nil :body "Enroll full-time if you can — it strongly raises your odds of finishing."}
                     {:title "Watch out" :body "Getting hired often requires passing a licensing exam."}]}})

(def real-rules-transfer
  "A transfer-associate program's engine :rules datum: a transfer-risk callout + bullets."
  {:program-title "Associate of Science — Biology."
   :rules {:terminal? false :transfer? true
           :transfer "Heads up: a transfer-designed program only pays off if you transfer and finish."
           :bullets [{:title nil :body "Choose a field-specific program, not general studies."}]}})

(deftest rules-props-flattens-bullets-and-passes-flags
  (testing "terminal → :terminal? + :terminal callout text; bullets flattened to STRINGS"
    (let [props (adapter/program->rules-props real-rules-terminal)]
      (is (true? (:terminal? props)))
      (is (nil? (:transfer? props)) "no transfer flag on a pure terminal program")
      (is (= "Terminal program: designed to take you straight into a job after you finish."
             (:terminal props)))
      (is (vector? (:bullets props)))
      (is (every? string? (:bullets props)) "EVERY bullet is a STRING — never a raw map child")
      (is (= "Enroll full-time if you can — it strongly raises your odds of finishing."
             (first (:bullets props))) "a title-less bullet is just its body")
      (is (= "Watch out: Getting hired often requires passing a licensing exam."
             (second (:bullets props))) "a titled bullet renders 'title: body'")))
  (testing "transfer-associate → :transfer? + :transfer callout text"
    (let [props (adapter/program->rules-props real-rules-transfer)]
      (is (true? (:transfer? props)))
      (is (nil? (:terminal? props)))
      (is (= "Heads up: a transfer-designed program only pays off if you transfer and finish."
             (:transfer props)))))
  (testing "present-by-data: a program with NO :rules → nil (a bachelor's shows none)"
    (is (nil? (adapter/program->rules-props {:program-title "BSN"}))
        "no :rules data → nil (no Making It Pay Off section)")))

(deftest rules-section-renders-callouts-and-bullets
  (testing "RENDER PROOF: terminal callout + bullets render as strings — never a raw map child"
    (let [html (dom-server/render-to-string
                ($ core/rules-section {:pathway real-rules-terminal}))]
      (is (re-find #"terminal" html) "the terminal callout heading renders")
      (is (re-find #"straight into a job" html) "the terminal definition renders")
      (is (re-find #"Enroll full-time" html) "an on-time bullet renders")
      (is (re-find #"Watch out" html) "a titled bullet renders its title")
      (is (not (re-find #"\[object Object\]" html)) "no stringified map leaks into the DOM")))
  (testing "RENDER PROOF: transfer-risk callout renders"
    (let [html (dom-server/render-to-string
                ($ core/rules-section {:pathway real-rules-transfer}))]
      (is (re-find #"transfer" html) "the transfer callout renders")
      (is (re-find #"only pays off if you transfer" html) "the transfer-risk heads-up renders")))
  (testing "present-by-data: no :rules ⇒ the section renders nothing"
    (let [html (dom-server/render-to-string
                ($ core/rules-section {:pathway {:program-title "BSN"}}))]
      (is (or (nil? html) (= "" html) (not (re-find #"terminal|transfer|Enroll" html)))
          "a bachelor's (no :rules) renders no Making It Pay Off content"))))

(deftest about-school-section-renders-acceptance-and-retention-tiles
  (testing "RENDER PROOF: the new Acceptance Rate + First-Year Retention figures render as tiles"
    (let [html (dom-server/render-to-string
                ($ core/about-school-section (adapter/school->about-props inst)))]
      (is (re-find #"Acceptance Rate" html) "acceptance tile label renders")
      (is (re-find #"98%" html) "acceptance value renders")
      (is (re-find #"First-Year Retention" html) "retention tile label renders")
      (is (re-find #"77%" html) "retention value renders")
      (is (re-find #"4-Year Graduation Rate" html) "existing grad-rate tile still renders"))))

;; 0046 — the About-This-School block is STAT BOXES ONLY: the narrative
;; interpretation bullets are no longer rendered under the tile grid.
(deftest about-school-section-omits-narrative-bullets
  (testing "RENDER PROOF: the block renders tiles only — no narrative bullets under the grid"
    (let [html (dom-server/render-to-string
                ($ core/about-school-section
                   (assoc (adapter/school->about-props inst)
                          :narrative ["UNIQ-NARRATIVE-MARKER-XYZ"])))]
      (is (not (re-find #"UNIQ-NARRATIVE-MARKER-XYZ" html))
          "an injected narrative bullet does NOT render under the tile grid")
      (is (not (re-find #"sits at their 25th percentile" html))
          "the real ability-readiness narrative bullet does not render")
      (is (re-find #"4-Year Graduation Rate" html) "the stat tiles still render"))))

;; ---------------------------------------------------------------------------
;; program->whats-cool-props — What's Cool slice 3, driven by the REAL SLU BSN
;; engine program (components.rec-demo.sample/real-whats-cool-program): 4 engine
;; {:title :body :source} points → 4 callout cards {:title :body :accent}.
;; ---------------------------------------------------------------------------

(def wc-prog sample/real-whats-cool-program)

(deftest whats-cool-props-maps-each-point-to-a-styled-card
  (testing "4 engine points → 4 cards, each with title, body, and an accent"
    (let [cards (adapter/program->whats-cool-props wc-prog)]
      (is (vector? cards))
      (is (= 4 (count cards)))
      (is (every? #(and (some? (:title %)) (some? (:body %)) (some? (:accent %))) cards)
          "every card carries a title, body, and an accent border class"))))

(deftest whats-cool-props-titles-and-bodies-verbatim
  (testing "titles and bodies pass through verbatim from the engine points"
    (let [cards (adapter/program->whats-cool-props wc-prog)
          points (:whats-cool wc-prog)]
      (is (= (mapv :title points) (mapv :title cards)))
      (is (= (mapv :body points) (mapv :body cards))))))

(deftest whats-cool-props-cycles-accents
  (testing "accents cycle the rec-demo palette across the cards (all 4 distinct here)"
    (let [accents (mapv :accent (adapter/program->whats-cool-props wc-prog))]
      (is (= 4 (count (distinct accents))) "4 cards get 4 distinct cycled accents")
      (is (every? #(re-find #"border-l-" %) accents) "each accent is a left-border class"))))

(deftest whats-cool-props-drops-source
  (testing ":source provenance is dropped (not rendered)"
    (is (every? #(nil? (:source %)) (adapter/program->whats-cool-props wc-prog)))))

(deftest whats-cool-props-nil-when-absent
  (testing "a program with no :whats-cool yields nil (present-by-data)"
    (is (nil? (adapter/program->whats-cool-props (dissoc wc-prog :whats-cool))))
    (is (nil? (adapter/program->whats-cool-props prog))
        "the Northwestern business program carries no :whats-cool")))

(deftest whats-cool-props-preserves-existing-tag-and-accent
  (testing "a point that already carries :tag/:accent (the standalone demo's shape) keeps them"
    (let [demo-point {:tag "Pass Rate" :title "97% pass" :body "b"
                      :accent "border-l-[#05a09c] bg-[#d0ecef]/60"}
          [card] (adapter/program->whats-cool-props {:whats-cool [demo-point]})]
      (is (= "Pass Rate" (:tag card)) "existing tag preserved")
      (is (= "border-l-[#05a09c] bg-[#d0ecef]/60" (:accent card)) "existing accent preserved"))))

;; ===========================================================================
;; 0014 — program->short-term-card-props: the short-term / apprenticeship card
;; HEADER props, tolerant of BOTH real track shapes.
;; ===========================================================================

(def st-prog sample/real-short-term-program)
(def appr sample/real-apprenticeship)

(deftest short-term-card-props-short-term-track
  (testing "real short-term record → title/provider/credential/field/stars"
    (let [props (adapter/program->short-term-card-props st-prog)]
      (is (= "Health Information/Medical Records Technology/Technician" (:title props))
          "title from :program-title, trailing period trimmed (0056)")
      (is (= "River Parishes Community College" (:provider props))
          "provider from :institution-name")
      (is (= "Certificate 1-2 years" (:credential props))
          "credential from :award-level-name")
      (is (= "Health Information" (:field props))
          "0056 — a stale 'Career Pathway <code>' cluster-name is REPLACED by the clean title topic, never a code")
      (is (= 3 (:stars props)) "stars from inside :salary"))))

(deftest short-term-card-props-apprenticeship-track
  (testing "real apprenticeship record → title/provider/credential/stars (different keys)"
    (let [props (adapter/program->short-term-card-props appr)]
      (is (= "General Apprenticeship Apprenticeship" (:title props))
          "title from :apprenticeship-title")
      (is (= "Assoc. Builders/ Contractors, Inc.- Pelican" (:provider props))
          "provider from :company-name")
      (is (= "Entry-Level Trade" (:credential props))
          "credential from :program-type")
      (is (= 4 (:stars props)) "stars from [:earn :demand :stars]"))))

(deftest short-term-card-props-no-nils
  (testing "no header field is nil for either real track"
    (doseq [p [st-prog appr]]
      (let [props (adapter/program->short-term-card-props p)]
        (is (every? some? (vals props)) "no nil values leak into the card props")))))

(deftest short-term-card-props-info-url
  (testing "info-url resolves across tracks (funding/earn/program-url/application-url)"
    (is (= "https://www.rpcc.edu/medical-coding"
           (:info-url (adapter/program->short-term-card-props st-prog))))
    (is (= "https://apprenticeshipla.com/apprenticeships/general-carpenter-millwright/"
           (:info-url (adapter/program->short-term-card-props appr))))))

(deftest short-term-card-props-separator-guarded
  (testing "when a provider/credential part is missing, the other stays present so the
            card never renders a bare ' · ' separator"
    (let [only-provider (adapter/program->short-term-card-props (dissoc st-prog :award-level-name))
          only-cred     (adapter/program->short-term-card-props (dissoc st-prog :institution-name))]
      (is (some? (:provider only-provider)))
      (is (nil? (:credential only-provider)))
      (is (nil? (:provider only-cred)))
      (is (some? (:credential only-cred))))))

;; ===========================================================================
;; 0015 — program->pathway-header-props: the COLLEGE pathway-row collapsed header.
;; Same class of bug as 0014 — the header read demo keys (:name/:track/:field/
;; :credential-level/:lwc-stars) absent from REAL college program records, which
;; carry :program-title / :award-level-name / :cluster-name and demand :stars INSIDE
;; :salary. The adapter maps real→header props with demo-shape fallbacks.
;; ===========================================================================

(deftest pathway-header-props-real-college-program
  (testing "real college program → title/credential/stars from the REAL keys"
    (let [props (adapter/program->pathway-header-props sample/real-program)]
      (is (= "Business Administration and Management, General" (:title props))
          "title from :program-title, trailing period trimmed (0056)")
      (is (= "Bachelor's degree" (:credential props))
          "credential from :award-level-name")
      (is (= 4 (:stars props)) "stars lifted from inside :salary"))))

(deftest pathway-header-props-field-clean-topic
  (testing "0056 — field is a CLEAN topic: NCES cluster-name decoration stripped, code/unknown never rendered"
    (is (= "Health Professions"
           (:field (adapter/program->pathway-header-props
                    (assoc sample/real-program :cluster-name "Health Professions (Nursing, Dental)"))))
        "a resolved NCES topic renders with the sibling decoration stripped")
    (is (= "Business Administration and Management, General"
           (:field (adapter/program->pathway-header-props
                    (assoc sample/real-program :cluster-name "Career Pathway 15.06 (Industrial Technology)"))))
        "a stale 'Career Pathway <code>' cluster-name is REPLACED by the clean program title topic")
    (is (= "Business Administration and Management, General"
           (:field (adapter/program->pathway-header-props
                    (assoc sample/real-program :cluster-name "Unknown"))))
        "an 'Unknown' cluster-name (clean-program default) never renders — falls back to the title topic")
    (is (= "Nursing" (:field (adapter/program->pathway-header-props {:field "Nursing"})))
        "no cluster-name → field falls back to demo :field (present-by-data)")))

;; 0056 — the pure clean-topic + trim-title helpers (behavior through public fns)
(deftest trim-title-strips-trailing-punctuation
  (is (= "Mechanical Engineering" (adapter/trim-title "Mechanical Engineering.")))
  (is (= "Surgical Technology/Technologist" (adapter/trim-title "Surgical Technology/Technologist.")))
  (is (= "Biochemistry" (adapter/trim-title "Biochemistry")) "no trailing punct → unchanged")
  (is (nil? (adapter/trim-title nil)) "non-string → nil"))

(deftest clean-topic-guarantees-no-unknown
  (testing "clean-topic never yields a code/'unknown', always a real topic when a cluster is present"
    (is (= "Health Professions" (adapter/clean-topic "Health Professions (Nursing, Dental)" "X"))
        "decoration stripped")
    (is (= "Mechanical Engineering" (adapter/clean-topic "Mechanical Engineering" "X"))
        "bare NCES topic passes through")
    (is (= "Electrician" (adapter/clean-topic "Career Pathway 99.00" "Electrician/Wiring."))
        "present-but-code → clean title topic (first '/' segment, trimmed)")
    (is (= "Electrician" (adapter/clean-topic "Unknown" "Electrician"))
        "present-but-'Unknown' → clean title topic")
    (is (nil? (adapter/clean-topic nil "X"))
        "absent cluster-name → nil (caller falls to demo :field, present-by-data)")
    (is (nil? (adapter/clean-topic "   " "X")) "blank cluster-name → nil")))

(deftest clean-topic-shortens-verbose-nces-title
  (testing "a verbose official NCES series title renders as a clean short chip (its
            first comma-clause) — deterministic, still from the dictionary, no hand-map"
    (is (= "Registered Nursing"
           (adapter/clean-topic
            "Registered Nursing, Nursing Administration, Nursing Research and Clinical Nursing"
            "Nursing"))
        "51.38 → first comma-clause")
    (is (= "Business Administration"
           (adapter/clean-topic "Business Administration, Management and Operations" "X"))
        "52.02 → first comma-clause")
    (is (= "Computer and Information Sciences"
           (adapter/clean-topic "Computer and Information Sciences, General" "X"))
        "11.01 → drops the trailing ', General'")
    (is (= "Registered Nursing"
           (adapter/clean-topic
            "Registered Nursing, Nursing Administration (Registered Nurse, Dental Hygiene)"
            "RN"))
        "decoration stripped BEFORE the comma-clause, so siblings never leak in")
    (is (= "Mechanical Engineering"
           (adapter/clean-topic "Mechanical Engineering" "X"))
        "no comma → unchanged")))

(deftest pathway-header-props-demo-shape-fallbacks
  (testing "demo pathway keys still map (no regression to the standalone demo)"
    (let [demo  {:name "Bachelor of Science in Nursing" :acronym "BSN"
                 :track "Generic Baccalaureate Track" :credential-level "Bachelor's"
                 :field "Nursing" :lwc-stars 5}
          props (adapter/program->pathway-header-props demo)]
      (is (= "Bachelor of Science in Nursing" (:title props)) "title falls back to :name")
      (is (= "BSN" (:acronym props)))
      (is (= "Generic Baccalaureate Track" (:track props)))
      (is (= "Bachelor's" (:credential props)) "credential falls back to :credential-level")
      (is (= 5 (:stars props)) "stars fall back to :lwc-stars")
      (is (= "Nursing" (:field props))))))

(deftest pathway-header-props-present-by-data
  (testing "missing header parts are omitted — never a blank title or a nil leak"
    (let [props (adapter/program->pathway-header-props {:program-title "X"})]
      (is (= "X" (:title props)))
      (is (every? some? (vals props)) "no nil values leak into the header props")
      (is (nil? (:credential props)))
      (is (nil? (:field props)))
      (is (nil? (:stars props)))
      (is (nil? (:acronym props)))
      (is (nil? (:track props))))
    (testing "a program carrying NO title yields no :title key (rather than a blank header)"
      (is (nil? (:title (adapter/program->pathway-header-props
                         {:award-level-name "Associate's degree"})))))))

;; ===========================================================================
;; 0014 — scholarship->card-props: real scholarship → demo scholarship-card props.
;; ===========================================================================

(def schol sample/real-scholarship)

(deftest scholarship-card-props-maps-real-fields
  (testing "name/award/deadline/why-fits/selection/url map from the real record"
    (let [props (adapter/scholarship->card-props schol)]
      (is (= "MASWE Scholarship" (:name props)))
      (is (= "$6,750" (:award props)) "award prefers :award-amount-display")
      (is (= "December 1." (:deadline props)))
      (is (= (:personalized-explanation schol) (:why-fits props)))
      (is (= "Applications are reviewed based on three main" (:selection props)))
      (is (= "http://swe.org" (:url props))))))

(deftest scholarship-card-props-wraps-tips-string
  (testing ":application-tips is a STRING — wrapped into a one-element vector"
    (let [props (adapter/scholarship->card-props schol)]
      (is (vector? (:tips props)))
      (is (= 1 (count (:tips props))))
      (is (= (:application-tips schol) (first (:tips props)))))))

(deftest scholarship-card-props-award-falls-back
  (testing "award falls back to :award when :award-amount-display is absent"
    (is (= "$6,750" (:award (adapter/scholarship->card-props (dissoc schol :award-amount-display)))))))

(deftest scholarship-card-props-present-by-data
  (testing "fields the real record lacks (:sponsor/:eligibility) are omitted"
    (let [props (adapter/scholarship->card-props schol)]
      (is (nil? (:sponsor props)) "no :sponsor on real records → omitted")
      (is (nil? (:eligibility props)) "no :eligibility on real records → omitted"))))

;; ===========================================================================
;; 0070 — scholarship card cleanup (viewer): surface :description (adapter),
;; advisor-verified badge + collapse-by-default (student-scholarship-card).
;; ===========================================================================

(deftest scholarship-card-props-carries-description
  (testing "0070 — :description is carried present-by-data (generated but never surfaced before)"
    (let [with-desc (adapter/scholarship->card-props
                     (assoc schol :description "Recognizes education beyond a four-year degree."))
          without   (adapter/scholarship->card-props schol)]
      (is (= "Recognizes education beyond a four-year degree." (:description with-desc))
          "carries :description when the record has one")
      (is (nil? (:description without))
          "no :description on the record → omitted (present-by-data)")))
  (testing "0070 — a BLANK :description (real records carry \"\"; empty string is truthy in
            cljs) is treated as absent, so the card never renders an empty Description block"
    (is (nil? (:description (adapter/scholarship->card-props (assoc schol :description ""))))
        "empty-string :description → omitted")
    (is (nil? (:description (adapter/scholarship->card-props (assoc schol :description "   "))))
        "whitespace-only :description → omitted")))

(deftest student-scholarship-card-collapsed-by-default
  (testing "0070 — the card renders COLLAPSED: header (name + advisor-verified badge)
            visible, the body (Why-it-fits / Application tips) hidden until expanded"
    (let [html (dom-server/render-to-string
                ($ core/student-scholarship-card {:scholarship schol}))]
      ;; header always visible
      (is (re-find #"MASWE Scholarship" html) "the name is in the always-visible header")
      (is (re-find #"Advisor-verified" html) "the advisor-verified badge is in the header")
      ;; body collapsed by default (no default-open?)
      (is (not (re-find #"Why it fits" html)) "the Why-it-fits body is hidden until expanded")
      (is (not (re-find #"Application tips" html)) "the Application-tips body is hidden until expanded"))))

(deftest student-scholarship-card-description-is-first-detail-bullet
  (testing "0070 (Daryl) — :description renders as the FIRST detail bullet, UNDER Why-it-fits
            and ABOVE Application tips, with NO separate 'Description' heading/label; badge present"
    (let [schol+ (assoc schol :description "Recognizes education beyond a four-year degree."
                              :selection-criteria "Reviewed for leadership.")
          html   (dom-server/render-to-string
                  ($ core/student-scholarship-card {:scholarship schol+ :default-open? true}))
          why-idx  (.indexOf html "Why it fits")
          desc-idx (.indexOf html "Recognizes education beyond a four-year degree")
          sel-idx  (.indexOf html "Reviewed for leadership")
          tips-idx (.indexOf html "Application tips")]
      (is (re-find #"Advisor-verified" html) "the badge is present on the expanded card too")
      (is (> desc-idx -1) "the description text renders")
      (is (neg? (.indexOf html "Description")) "NO separate 'Description' heading/label")
      (is (< why-idx desc-idx) "description is positioned UNDER Why-it-fits")
      (is (< desc-idx sel-idx) "description is the FIRST bullet (before the other detail bullets)")
      (is (< desc-idx tips-idx) "description is positioned ABOVE Application tips"))))

(deftest student-scholarship-card-no-description-bullet-when-absent
  (testing "0070 — present-by-data: no :description → the description text never renders (no crash)"
    (let [html (dom-server/render-to-string
                ($ core/student-scholarship-card {:scholarship schol :default-open? true}))]
      (is (re-find #"Why it fits" html) "the expanded body still renders")
      (is (neg? (.indexOf html "Description")) "no 'Description' label ever renders")))
  (testing "0070 — a BLANK :description (real data carries \"\") renders NO description bullet
            (regression: live QA found an empty block from an empty string)"
    (let [html (dom-server/render-to-string
                ($ core/student-scholarship-card {:scholarship (assoc schol :description "  ") :default-open? true}))]
      (is (re-find #"Why it fits" html) "the expanded body still renders")
      (is (neg? (.indexOf html "Description")) "a blank :description renders nothing"))))

;; ===========================================================================
;; What's Cool RENDER-GATING (student view) — a REAL engine program that CARRIES
;; :whats-cool did NOT surface the "What's Cool About This Pathway" block when its
;; pathway was expanded. Two independent gates, both PURE + present-by-data:
;;   1. pathway-section-keys — which sections render (whats-cool must appear when
;;      the program carries :whats-cool data, even if :sections omitted it).
;;   2. pathway-default-section — which section OPENS on expand. Real engine
;;      programs never carry :overview in :sections (they lead with :whats-cool or
;;      :salary), so the old hardcoded "overview" default opened NOTHING and left
;;      the first section (Destiny's SLU nursing What's Cool) collapsed & unseen.
;;
;; Fixtures mirror the REAL pool record (verified via clojure.edn parse of the
;; saved pool event): the SLU Registered Nursing program carries
;;   :sections [:whats-cool :salary :careers :time-to-credential] + :whats-cool data.
;; ===========================================================================

(def slu-nursing
  "Real SLU Registered Nursing program shape: :sections leads with :whats-cool and
   the :whats-cool data is present (the EDGE direct-admit differentiator)."
  {:program-title "Registered Nursing/Registered Nurse."
   :cluster-name "Career Pathway 51.38 (Registered Nursing)"
   :award-level-name "Bachelor's degree"
   :sections [:whats-cool :salary :careers :time-to-credential]
   :whats-cool [{:title "EDGE direct-admit pathway for HS seniors (3.7 GPA / 25 ACT)"
                 :body "Apply by January 15 of your senior year and declare nursing."
                 :source "Notion Differentiators + content sections; Active"}]})

(def salary-first-program
  "A real program that carries NO :whats-cool and whose :sections lead with :salary."
  {:program-title "Biology/Biological Sciences, General."
   :sections [:salary :careers :time-to-credential]})

;; --- pathway-section-keys (present-by-data selection) ---

(deftest section-keys-passes-through-when-sections-lists-whats-cool
  (testing "a program whose :sections already lists :whats-cool is returned as-is"
    (is (= [:whats-cool :salary :careers :time-to-credential]
           (core/pathway-section-keys slu-nursing)))))

(deftest section-keys-splices-whats-cool-when-data-present-but-sections-omits
  (testing "PRESENT-BY-DATA: :whats-cool DATA present but :sections omits it → spliced in"
    (let [prog {:sections [:overview :salary :careers]
                :whats-cool [{:title "t" :body "b"}]}
          ks (core/pathway-section-keys prog)]
      (is (some #{:whats-cool} ks) "whats-cool is included despite :sections omitting it")
      (is (= [:overview :whats-cool :salary :careers] ks)
          "spliced at its canonical slot — right after :overview"))))

(deftest section-keys-no-whats-cool-when-no-data
  (testing "present-by-data: no :whats-cool data → whats-cool NOT added (no empty block)"
    (is (not (some #{:whats-cool} (core/pathway-section-keys salary-first-program)))
        "a program with no whats-cool data shows no whats-cool section")))

(deftest section-keys-empty-whats-cool-is-not-spliced
  (testing "an EMPTY :whats-cool [] is treated as no data (no empty block)"
    (let [prog {:sections [:salary :careers] :whats-cool []}]
      (is (not (some #{:whats-cool} (core/pathway-section-keys prog)))))))

(deftest section-keys-legacy-fallback-is-present-by-data
  ;; Daryl on the legacy render: "if the block is blank … it just doesn't show for the
  ;; old recs. We would rather have them not there than have them there then when we
  ;; expand it's just blank." A legacy (Python-era) pathway carries NO :sections, so the
  ;; fallback used the FULL global section order — every heading rendered as an empty
  ;; accordion shell (What's Cool, Salary, Career Paths…). The fallback must be filtered
  ;; present-by-data; an engine-declared :sections vector stays authoritative untouched.
  (testing "a legacy pathway (no :sections, only legacy overview prose) renders ONLY Overview"
    (let [legacy {:program-title "Registered Nursing/Registered Nurse"
                  :personalized-overview "This Registered Nursing program is an excellent fit."
                  :best-for "Students committed to a BSN."}]
      (is (= [:overview] (core/pathway-section-keys legacy))
          "no empty What's-Cool/Salary/Careers shells — just the block that has content")))
  (testing "fallback keeps any section that DOES carry data"
    (let [mixed {:program-title "X"
                 :personalized-overview "Legacy prose."
                 :whats-cool [{:title "t" :body "b"}]
                 :salary {:band "ok"}}]
      (is (= [:overview :whats-cool :salary] (core/pathway-section-keys mixed))
          "present-by-data over the fallback order, canonical ordering preserved")))
  (testing "a pathway with NO content at all renders no sections (and therefore no accordion)"
    (is (= [] (core/pathway-section-keys {:program-title "Bare"}))))
  (testing "an engine :sections vector is still authoritative — byte-identical for new pools"
    (is (= [:whats-cool :salary :careers :time-to-credential]
           (core/pathway-section-keys slu-nursing))
        "declared sections pass through exactly as before, even if some were data-empty")))

;; --- pathway-section-keys :overview splice (0057 — the sections-freeze repair) ---

(def overview-program
  "Real engine shape (0057): :sections is FROZEN [:salary :careers :time-to-credential]
   (no :overview, because present-by-data ran before narrate attached the prose) yet the
   record carries generated :overview prose. The UI splice repairs it present-by-data."
  {:program-title "Mechanical Engineering."
   :sections [:salary :careers :time-to-credential]
   :overview {:credential-line "The BS in Mechanical Engineering is a 4-year degree that prepares you to design machines."
              :student-connection "It fits your interest in building things."
              :caveat ""}})

(deftest section-keys-splices-overview-when-data-present-but-sections-omits
  (testing "PRESENT-BY-DATA (0057): :overview DATA present but the frozen :sections omits it → spliced at the FRONT"
    (let [ks (core/pathway-section-keys overview-program)]
      (is (= :overview (first ks)) "overview leads (section 1)")
      (is (= [:overview :salary :careers :time-to-credential] ks)))))

(deftest section-keys-no-overview-when-blank
  (testing "present-by-data: an all-blank :overview (empty strings only) → :overview NOT spliced (no empty heading)"
    (let [prog {:sections [:salary :careers]
                :overview {:credential-line "" :student-connection "" :caveat ""}}]
      (is (not (some #{:overview} (core/pathway-section-keys prog)))
          "an overview with no non-blank field adds no section"))))

(deftest section-keys-splices-both-overview-and-whats-cool
  (testing "both :overview + :whats-cool data, frozen :sections omits both → overview front, whats-cool section 2"
    (let [prog {:sections [:salary :careers :time-to-credential]
                :overview {:credential-line "what it is"}
                :whats-cool [{:title "t" :body "b"}]}]
      (is (= [:overview :whats-cool :salary :careers :time-to-credential]
             (core/pathway-section-keys prog))
          "overview leads, whats-cool spliced right after it"))))

;; --- 0057: the "See the full program page →" link (program_url) ---

(deftest overview-props-carries-program-url
  (testing "program->overview-props carries :program-url present-by-data (for the full-program-page link)"
    (is (= "https://x.edu/nursing"
           (:program-url (adapter/program->overview-props
                          {:overview {:credential-line "c"} :program-url "https://x.edu/nursing"}))))
    (is (nil? (:program-url (adapter/program->overview-props {:overview {:credential-line "c"}})))
        "absent when the record carries no :program-url")))

(deftest overview-section-renders-program-page-link
  (testing "RENDER PROOF: the Overview renders 'See the full program page →' when :program-url present"
    (let [html (dom-server/render-to-string
                ($ core/overview-section
                   {:overview-props {:credential-line "The BSN is a 4-year degree."
                                     :program-url "https://x.edu/nursing"}}))]
      (is (re-find #"See the full program page" html) "the link label renders")
      (is (re-find #"x\.edu/nursing" html) "the program_url href renders")))
  (testing "present-by-data: no :program-url → no program-page link"
    (let [html (dom-server/render-to-string
                ($ core/overview-section {:overview-props {:credential-line "c"}}))]
      (is (not (re-find #"See the full program page" html))))))

;; --- pathway-default-section (which section opens on expand) ---

(deftest default-section-is-whats-cool-for-slu-nursing
  (testing "Destiny's SLU nursing pathway OPENS on :whats-cool so the block appears on expand"
    (is (= "whats-cool" (core/pathway-default-section slu-nursing)))))

(deftest default-section-is-first-present-not-phantom-overview
  (testing "a real program leading with :salary opens :salary — NOT the phantom :overview"
    (is (= "salary" (core/pathway-default-section salary-first-program)))))

(deftest default-section-still-overview-for-standalone-demo
  (testing "no regression: a standalone-demo program whose :sections lead with :overview still opens overview"
    (is (= "overview"
           (core/pathway-default-section
            {:sections [:overview :salary :careers :time-to-credential]})))))

;; --- RENDER proof (no false green): the SECTION actually renders, not just props ---

(defn- render-pathway-sections [program]
  (dom-server/render-to-string
   ($ core/pathway-sections {:pathway program :school nil :student nil})))

(deftest whats-cool-block-renders-and-is-open-on-expand
  (testing "rendering the SLU nursing pathway surfaces the What's Cool trigger AND its
            content (the EDGE differentiator) — visible by default, not just non-nil props"
    (let [html (render-pathway-sections slu-nursing)]
      (is (re-find #"Cool About This Pathway" html)
          "the What's Cool section trigger renders")
      (is (re-find #"EDGE direct-admit" html)
          "the What's Cool CONTENT is open/visible on expand (default-value opens it)"))))

(deftest no-whats-cool-block-when-program-lacks-data
  (testing "present-by-data: a program with no :whats-cool renders no What's Cool trigger"
    (let [html (render-pathway-sections salary-first-program)]
      (is (not (re-find #"Cool About This Pathway" html))
          "no What's Cool section for a program that carries no whats-cool data"))))

;; ===========================================================================
;; Slice 0033 (epic match-tavidee) — apprenticeship data-drift + the
;; "Earn while you learn" 4th header pill on apprenticeship cards.
;; ===========================================================================

;; --- Fix 2 — data-drift: the adapter falls back to the OLD demo header keys
;;     (:name / :provider / :credential-level) so an old-shape apprenticeship
;;     / short-term record still renders a title/provider/credential. ---

(deftest short-term-card-props-old-demo-header-keys
  (testing "an OLD-shape record (:name/:provider/:credential-level) still resolves the header"
    (let [old   {:name "Electrical Apprenticeship"
                 :provider "Baton Rouge Electrical JATC"
                 :credential-level "Registered Apprenticeship"}
          props (adapter/program->short-term-card-props old)]
      (is (= "Electrical Apprenticeship" (:title props)) "title falls back to :name")
      (is (= "Baton Rouge Electrical JATC" (:provider props)) "provider falls back to :provider")
      (is (= "Registered Apprenticeship" (:credential props)) "credential falls back to :credential-level"))))

(deftest short-term-card-props-demo-electrical-apprenticeship-renders
  (testing "the standalone-demo electrical apprenticeship now resolves a full header (data-drift fixed)"
    (let [props (adapter/program->short-term-card-props data/electrical-apprenticeship)]
      (is (= "Electrical Apprenticeship" (:title props)))
      (is (= "Baton Rouge Electrical JATC" (:provider props)))
      (is (= "Registered Apprenticeship" (:credential props))))))

(deftest short-term-card-props-demo-lpn-renders
  (testing "the standalone-demo LPN short-term program (old :name/:provider/:credential-level) resolves too"
    (let [props (adapter/program->short-term-card-props data/lpn)]
      (is (= "Licensed Practical/Vocational Nurse" (:title props)))
      (is (= "Baton Rouge Community College" (:provider props)))
      (is (= "Technical Diploma" (:credential props))))))

;; --- Fix 1 — the adapter flags an apprenticeship so the card can add the
;;     "Earn while you learn" 4th header pill (not just the expanded Overview). ---

(deftest short-term-card-props-flags-apprenticeship
  (testing ":apprenticeship? true for an apprenticeship, falsey for a plain short-term program"
    (is (true? (:apprenticeship? (adapter/program->short-term-card-props sample/real-apprenticeship)))
        "real apprenticeship (earn-while-learn) is flagged")
    (is (true? (:apprenticeship? (adapter/program->short-term-card-props data/electrical-apprenticeship)))
        "the demo apprenticeship is flagged")
    (is (true? (:apprenticeship? (adapter/program->short-term-card-props {:degree-type :apprenticeship})))
        "the engine 0032 :degree-type :apprenticeship is flagged")
    (is (not (:apprenticeship? (adapter/program->short-term-card-props sample/real-short-term-program)))
        "a plain short-term program is NOT flagged")
    (is (not (:apprenticeship? (adapter/program->short-term-card-props data/lpn)))
        "the LPN technical diploma is NOT flagged")))

(deftest short-term-card-renders-earn-while-you-learn-pill
  (testing "an apprenticeship card shows the 'Earn while you learn' 4th header pill"
    (let [html (dom-server/render-to-string
                ($ core/short-term-card {:program data/electrical-apprenticeship}))]
      (is (re-find #"Earn while you learn" html) "the earn-while-you-learn pill renders on the apprenticeship card"))))

(deftest short-term-card-no-earn-pill-for-plain-short-term
  (testing "a plain short-term program does NOT show the earn-while-you-learn pill"
    (let [html (dom-server/render-to-string
                ($ core/short-term-card {:program sample/real-short-term-program}))]
      (is (not (re-find #"Earn while you learn" html)) "no earn pill on a non-apprenticeship card"))))

;; ---------------------------------------------------------------------------
;; 0060 — program->costs-props + the short-term 'What It Costs You' section
;; ---------------------------------------------------------------------------

(def ^:private st-costs-reported
  {:tuition-fees 3335 :books 0 :coa 3335 :tops 2846 :tops-name "TOPS-Tech"
   :pell 7395 :net 0 :aid-covered 3335 :fully-covered? true :commuter? true
   :disclaimer "This is an estimate only." :fafsa-url "https://studentaid.gov"
   :npc-url "https://www.rpcc.edu/HD-npc" :faid-url "https://www.rpcc.edu/HD-faid"})

(def ^:private st-program-reported
  {:program-title "Welding Technology/Welder."
   :institution-name "River Parishes Community College"
   :award-level-name "Certificate 1-2 years"
   :sections [:costs]
   :costs st-costs-reported})

(def ^:private st-program-unreported
  {:program-title "Cosmetology."
   :institution-name "Beauty School"
   :sections [:costs]
   :costs {:commuter? true :tops-name "TOPS-Tech"
           :tuition-note "This school hasn't published its tuition — contact them for current pricing."
           :pell 7395 :disclaimer "This is an estimate only." :fafsa-url "https://studentaid.gov"
           :npc-url "https://www.beauty.edu/npc"}})

(deftest program-costs-props-carries-waterfall-and-links
  (testing "a short-term program's OWN :costs → costs-section props (mirrors school->costs-props)"
    (let [props (adapter/program->costs-props st-program-reported)]
      (is (= st-costs-reported (:costs props)) "the program's :costs map passes through")
      (is (= "River Parishes Community College" (:short-name props)))
      (is (= 2 (count (:narrative props))) "commuter affordability advice bullets")
      (is (re-find #"live at home" (first (:narrative props))) "commuter branch")
      (is (= 2 (count (:links props))) "NPC + Financial Aid office (both URLs present)")
      (is (re-find #"Net Price Calculator" (:label (first (:links props)))))
      (is (re-find #"Financial Aid office" (:label (second (:links props))))))))

(deftest program-costs-props-absent-when-no-costs
  (is (nil? (adapter/program->costs-props (dissoc st-program-reported :costs)))
      "no program-level :costs → nil (present-by-data; degree/apprenticeship pathways)")
  (is (nil? (adapter/program->costs-props sample/real-short-term-program))
      "a short-term program with no :costs attaches no costs section"))

(deftest pathway-costs-section-render-reported
  (testing "reported tuition → the net-cost waterfall chart (costs-section reused verbatim)"
    (let [html (dom-server/render-to-string ($ core/pathway-costs-section {:pathway st-program-reported}))]
      (is (re-find #"Your Estimated Annual Cost" html) "the net-cost chart title renders")
      (is (re-find #"After.*TOPS-Tech" html) "the TOPS-Tech waterfall step renders")
      (is (re-find #"Net Price Calculator" html) "the NPC link renders")
      (is (re-find #"Financial Aid office" html) "the Financial Aid office link renders")
      (is (re-find #"FAFSA" html) "the fixed FAFSA disclaimer is kept")
      (is (not (re-find #"published its tuition" html)) "no unreported note when tuition is known"))))

(deftest pathway-costs-section-render-unreported
  (testing "unreported tuition → graceful cost-helper copy, NO broken chart"
    (let [html (dom-server/render-to-string ($ core/pathway-costs-section {:pathway st-program-unreported}))]
      (is (re-find #"published its tuition" html) "the fixed cost-helper note renders as content")
      (is (re-find #"FAFSA" html) "the FAFSA disclaimer still renders")
      (is (re-find #"Net Price Calculator" html) "the present-by-data NPC link renders")
      (is (not (re-find #"Your Estimated Annual Cost" html)) "no net-cost chart when tuition is unreported"))))

(deftest pathway-costs-section-omitted-when-no-costs
  (testing "present-by-data: a pathway with no :costs renders nothing"
    (let [html (dom-server/render-to-string ($ core/pathway-costs-section {:pathway (dissoc st-program-reported :costs)}))]
      (is (not (re-find #"Your Estimated Annual Cost" html)))
      (is (not (re-find #"published its tuition" html))))))

(deftest short-term-card-renders-what-it-costs-you
  (testing "an expanded short-term card with :costs shows the 'What It Costs You' section end-to-end"
    (let [html (dom-server/render-to-string
                ($ core/short-term-card {:program st-program-reported :default-open? true}))]
      (is (re-find #"What It Costs You" html) "the section heading renders via pathway-section-meta")
      (is (re-find #"Your Estimated Annual Cost" html) "the net-cost chart renders inside the card")))
  (testing "a short-term card WITHOUT :costs shows no costs section (present-by-data)"
    (let [html (dom-server/render-to-string
                ($ core/short-term-card {:program sample/real-short-term-program :default-open? true}))]
      (is (not (re-find #"What It Costs You" html)) "no costs section when the program carries no :costs"))))

;; ===========================================================================
;; 0072 — never render a NOT_FOUND / nil / blank sentinel in the student view.
;; A pure present-value / absent? guard at the render seam: absent = nil,
;; blank/whitespace-only, or the WHOLE trimmed value (case-insensitive) equal to a
;; source sentinel (#{NOT_FOUND UNKNOWN N/A TBD NONE}). Present → the ORIGINAL value;
;; absent → nil (present-by-data ⇒ the key is omitted ⇒ nothing renders). Confirmed
;; live: scholarship :award / :deadline / :award-amount-display carry "NOT_FOUND";
;; :deadline-urgency-current carries "UNKNOWN". NEVER substring-match.
;; ===========================================================================

(deftest present-value-unit-table
  (testing "absent → nil (nil, blank, whitespace, each case-insensitive sentinel)"
    (are [v] (nil? (adapter/present-value v))
      nil "" "   " "\t\n"
      "NOT_FOUND" "not_found" " Not_Found "
      "UNKNOWN" "unknown"
      "N/A" "n/a" "TBD" "tbd" "NONE" "none"))
  (testing "present → the ORIGINAL (untrimmed) value passes through"
    (is (= "$6,750" (adapter/present-value "$6,750")))
    (is (= "  December 1  " (adapter/present-value "  December 1  "))
        "a present value keeps its original (untrimmed) form")
    (is (= 42 (adapter/present-value 42)) "a non-string non-nil value is present"))
  (testing "NEVER substring-match — a sentinel embedded mid-string is REAL text, kept"
    (is (= "Award: NOT_FOUND funds" (adapter/present-value "Award: NOT_FOUND funds")))
    (is (= "Deadline is N/A this year" (adapter/present-value "Deadline is N/A this year")))))

(deftest scholarship-card-props-drops-sentinel-award-and-deadline
  (testing "sentinel :award/:deadline → those keys ABSENT (nothing renders)"
    (let [props (adapter/scholarship->card-props
                 (assoc schol :award-amount-display "NOT_FOUND" :award "NOT_FOUND"
                        :deadline "UNKNOWN"))]
      (is (nil? (:award props)) "a NOT_FOUND award is dropped (guards the display/award fallback RESULT)")
      (is (nil? (:deadline props)) "an UNKNOWN deadline is dropped")
      (is (= "MASWE Scholarship" (:name props)) "real fields still pass through"))))

(deftest scholarship-card-props-keeps-real-award-and-deadline
  (testing "a real award/deadline passes through unchanged (the guard only drops sentinels)"
    (let [props (adapter/scholarship->card-props schol)]
      (is (= "$6,750" (:award props)))
      (is (= "December 1." (:deadline props))))))

(deftest scholarship-card-props-sentinel-case-and-whitespace-tolerant
  (testing "case/whitespace tolerance across the guarded pass-through string fields"
    (let [props (adapter/scholarship->card-props
                 (assoc schol :award-amount-display " not_found " :award " not_found "
                        :deadline "  n/a  "
                        :selection-criteria "None"
                        :personalized-explanation "  "
                        :application-url "TBD"))]
      (is (nil? (:award props)) "' not_found ' → absent")
      (is (nil? (:deadline props)) "'  n/a  ' → absent")
      (is (nil? (:selection props)) "'None' → absent")
      (is (nil? (:why-fits props)) "whitespace-only → absent")
      (is (nil? (:url props)) "'TBD' url → absent"))))

(deftest scholarship-card-props-drops-sentinel-tip-keeps-real-tip
  (testing "a sentinel tip is dropped while a real tip in the same vector survives"
    (let [props (adapter/scholarship->card-props
                 (assoc schol :application-tips ["NOT_FOUND" "Apply early — funds run out." "  "]))]
      (is (= ["Apply early — funds run out."] (:tips props))
          "only the real tip survives; the sentinel + blank tips are dropped")))
  (testing "a scholarship whose ONLY tip is a sentinel → :tips omitted (present-by-data)"
    (is (nil? (:tips (adapter/scholarship->card-props (assoc schol :application-tips "NOT_FOUND"))))))
  (testing "a real single-string tip still wraps into a one-element vector (no regression)"
    (is (= [(:application-tips schol)]
           (:tips (adapter/scholarship->card-props schol))))))

(deftest scholarship-card-props-drops-sentinel-sponsor-eligibility-description
  (testing "sentinel :sponsor/:eligibility/:description → keys omitted"
    (let [props (adapter/scholarship->card-props
                 (assoc schol :sponsor "N/A" :eligibility "UNKNOWN" :description "NOT_FOUND"))]
      (is (nil? (:sponsor props)))
      (is (nil? (:eligibility props)))
      (is (nil? (:description props))))))

;; ===========================================================================
;; 0073 — amber "Terminal"/"Transfer" classification chip in the program card header.
;; Sourced from the program's :rules :terminal?/:transfer? — the SAME datum the Making
;; It Pay Off callout (rules-section) reads, so the chip and the callout can never
;; disagree. The amber chip renders FIRST, before the teal derived chips. A bachelor's
;; (no :rules) shows NEITHER (present-by-data). NOT the school-level :str-badge.
;; ===========================================================================

(deftest program-classification-pure-derive
  (testing "the pure derive: terminal/transfer/both/neither/no-rules"
    (is (= [{:label "Terminal"}] (adapter/program-classification {:rules {:terminal? true}})))
    (is (= [{:label "Transfer"}] (adapter/program-classification {:rules {:transfer? true}})))
    (is (= [{:label "Terminal"} {:label "Transfer"}]
           (adapter/program-classification {:rules {:terminal? true :transfer? true}}))
        "Terminal FIRST, then Transfer")
    (is (nil? (adapter/program-classification {:rules {:terminal? false :transfer? false}}))
        "a :rules with neither flag → nil (no chip)")
    (is (nil? (adapter/program-classification {})) "no :rules → nil (a bachelor's)"))
  (testing "0075 — reads :degree-classification (pipeline data, present even without caveats)"
    (is (= [{:label "Terminal"}]
           (adapter/program-classification {:degree-classification {:terminal? true}}))
        "badge from :degree-classification with NO :rules (caveats empty)")
    (is (= [{:label "Transfer"}]
           (adapter/program-classification {:degree-classification {:transfer? true}})))
    (is (= [{:label "Terminal"}]
           (adapter/program-classification {:degree-classification {:terminal? true}
                                            :rules {:terminal? false :transfer? false}}))
        ":degree-classification WINS over an empty :rules")))

(deftest pathway-header-props-terminal-classification
  (testing "a terminal program → header props carry a Terminal classification (from :rules)"
    (let [props (adapter/program->pathway-header-props
                 (assoc sample/real-program :rules {:terminal? true :transfer? false}))]
      (is (= [{:label "Terminal"}] (:classifications props))))))

(deftest pathway-header-props-transfer-classification
  (testing "a transfer-designed program → a Transfer classification"
    (let [props (adapter/program->pathway-header-props
                 (assoc sample/real-program :rules {:terminal? false :transfer? true}))]
      (is (= [{:label "Transfer"}] (:classifications props))))))

(deftest pathway-header-props-no-classification-for-bachelors
  (testing "a bachelor's / program with no :rules → NO classification (present-by-data)"
    (is (nil? (:classifications (adapter/program->pathway-header-props sample/real-program)))
        "real-program carries no :rules → no Terminal chip (bachelor's must NOT show one)")
    (is (nil? (:classifications (adapter/program->pathway-header-props
                                 (assoc sample/real-program :rules {:terminal? false :transfer? false}))))
        "a :rules with neither flag → no chip")))

(deftest short-term-card-props-terminal-classification
  (testing "the short-term/apprenticeship adapter ALSO surfaces the classification (from :rules)"
    (is (= [{:label "Terminal"}]
           (:classifications (adapter/program->short-term-card-props
                              (assoc st-prog :rules {:terminal? true :transfer? false})))))
    (is (= [{:label "Transfer"}]
           (:classifications (adapter/program->short-term-card-props
                              (assoc st-prog :rules {:terminal? false :transfer? true}))))
        "an LPN-style transfer program shows Transfer on the short-term card too")
    (is (nil? (:classifications (adapter/program->short-term-card-props st-prog)))
        "no :rules → no classification chip on the short-term card either")))

(deftest pathway-row-renders-amber-terminal-chip-first
  (testing "RENDER PROOF: the college pathway-row header renders an amber Terminal chip
            FIRST, before the teal derived chips"
    (let [html (dom-server/render-to-string
                ($ core/pathway-row
                   {:pathway (assoc sample/real-program :rules {:terminal? true :transfer? false})
                    :school nil :student nil}))
          amber-idx (.indexOf html "#fff7e6")   ;; the amber classification chip token
          teal-idx  (.indexOf html "#d0ecef")]  ;; the first teal derived chip token
      (is (re-find #"Terminal" html) "the Terminal chip renders")
      (is (re-find #"#fff7e6" html) "the amber classification token (reused from the transfer-risk callout) is used")
      (is (and (> amber-idx -1) (> teal-idx -1) (< amber-idx teal-idx))
          "the amber classification chip renders BEFORE the teal derived chips"))))

(deftest short-term-card-renders-amber-terminal-chip-first
  (testing "RENDER PROOF: the short-term/apprenticeship card header renders an amber
            Terminal chip FIRST"
    (let [html (dom-server/render-to-string
                ($ core/short-term-card
                   {:program (assoc st-prog :rules {:terminal? true :transfer? false})}))
          amber-idx (.indexOf html "#fff7e6")   ;; the amber classification chip token
          teal-idx  (.indexOf html "#d0ecef")]  ;; the first teal derived chip token
      (is (re-find #"Terminal" html) "the Terminal chip renders on the short-term card")
      (is (re-find #"#fff7e6" html) "the amber token is used")
      (is (and (> amber-idx -1) (> teal-idx -1) (< amber-idx teal-idx))
          "amber classification chip before the teal derived chips"))))

(deftest pathway-row-no-amber-chip-for-bachelors
  (testing "RENDER PROOF: a bachelor's (no :rules) renders NO amber classification chip"
    (let [html (dom-server/render-to-string
                ($ core/pathway-row {:pathway sample/real-program :school nil :student nil}))]
      (is (not (re-find #"#fff7e6" html)) "no amber classification token when the program has no :rules")
      (is (re-find #"Business Administration" html) "the teal derived chips still render"))))
