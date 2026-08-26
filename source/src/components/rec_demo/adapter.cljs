(ns components.rec-demo.adapter
  "UI-1 seam — PURE engine-record → component-props adapter.

   Maps a REAL recommendation-engine institution record (as saved in a pool event)
   to the props the school-anchored components (`about-school-section`,
   `costs-section`) consume: the About-This-School stat tiles + OUR grounded
   narrative bullet vectors, and the Costs waterfall map + OUR costs narrative.

   Principles:
     • PRESENT-BY-DATA — a missing tile/section/narrative is simply omitted; the
       components never recompute or invent copy.
     • DROP LEGACY CRUFT — :why-fits-bullets/:financial-bullets/:cost-summary/
       :admissions-* etc. are engine-internal and never surfaced here.
     • Our engine shapes largely already match the components, so this stays thin."
  (:require [clojure.string :as str]))

(defn commafy
  "Thousands-separated integer string, locale-independent (deterministic in tests)."
  [n]
  (when (some? n)
    (let [s (str (js/Math.abs n))
          grouped (->> (reverse s)
                       (partition-all 3)
                       (map #(apply str (reverse %)))
                       reverse
                       (str/join ","))]
      (str (when (neg? n) "-") grouped))))

;; ===========================================================================
;; 0072 — render-seam sentinel guard: never render a NOT_FOUND / nil / blank value.
;;
;; The scholarship source data (and other engine pools) use whole-string SENTINELS for
;; a missing field. Confirmed live in the scholarship pool: :award / :deadline /
;; :award-amount-display carry "NOT_FOUND"; :deadline-urgency-current carries "UNKNOWN".
;; The render-seam adapter treated any TRUTHY value as present, so "NOT_FOUND" flowed to
;; the DOM. `present-value` is the single choke-point guard: an absent value returns nil
;; (present-by-data ⇒ the key is omitted ⇒ nothing renders); a present one returns the
;; ORIGINAL value unchanged.
;; ===========================================================================

(def sentinel-values
  "The whole-string missing-data sentinels the source data uses for an absent field,
   compared case-insensitively AFTER trimming. Confirmed present in the codebase/live
   pool: NOT_FOUND, UNKNOWN, N/A, TBD, NONE. Lower-cased here for the case-insensitive
   membership test."
  #{"not_found" "unknown" "n/a" "tbd" "none"})

(defn absent?
  "PURE (0072). A user-facing value is ABSENT — must never render — when it is nil, a
   blank/whitespace-only string, or a string whose WHOLE trimmed value (case-insensitive)
   is a missing-data sentinel. NEVER substring-matches: 'Award: NOT_FOUND funds' is
   PRESENT (only an exact sentinel is guarded, so real text is never eaten). A non-string
   non-nil value (e.g. a number) is present."
  [v]
  (or (nil? v)
      (and (string? v)
           (let [t (str/trim v)]
             (or (empty? t)
                 (contains? sentinel-values (str/lower-case t)))))))

(defn present-value
  "PURE (0072). The render-seam guard: returns the ORIGINAL (untrimmed) value when
   present, else nil so an absent field's key is omitted (present-by-data — a NOT_FOUND/
   UNKNOWN/blank sentinel is dropped and nothing renders). See `absent?`."
  [v]
  (when-not (absent? v) v))

;; ===========================================================================
;; 0056 — Program-title + topic-chip hygiene
;;
;; Raw CSV program_title ships trailing periods ("Mechanical Engineering.") and the
;; engine's :cluster-name is the topic chip. This slice: (a) trims trailing
;; punctuation off titles, and (b) surfaces a CLEAN topic chip so "Career Pathway
;; <code>" / "Unknown" / "unknown" NEVER render — the engine now names clusters from
;; the official NCES CIP 2020 dictionary, and this adapter is the render-seam safety
;; net (falls back to the program's own clean title when a cluster-name is still a
;; code/unknown placeholder from stale data).
;; ===========================================================================

(defn trim-title
  "Trim trailing punctuation/whitespace from a program title. Keeps internal
   punctuation ('Surgical Technology/Technologist.' → '…Technologist'). Non-string
   → nil."
  [title]
  (when (string? title)
    (-> title (str/replace #"[.\s]+$" "") str/trim)))

(defn- strip-decoration
  "Drop a trailing ' (…)' sibling decoration from an engine cluster-name so the chip
   shows the bare topic ('Health Professions (Nursing, Dental)' → 'Health Professions')."
  [s]
  (when (string? s)
    (-> s (str/replace #"\s*\([^)]*\)\s*$" "") str/trim)))

(defn- code-or-unknown?
  "A cluster-name that is a bare CIP code label or an 'unknown' placeholder — must
   never render as a topic chip."
  [s]
  (or (str/starts-with? s "Career Pathway")
      (= "Unknown" s)
      (= "unknown" s)))

(defn- title-topic
  "A clean topic derived from a program title's first '/' segment (trailing
   punctuation trimmed) — the render-seam fallback that guarantees no 'unknown' chip."
  [program-title]
  (some-> program-title trim-title (str/split #"/") first str/trim not-empty))

(defn- short-topic
  "Shorten a verbose official NCES CIP title to its first comma-clause so the chip
   reads as a clean topic ('Registered Nursing, Nursing Administration, Nursing
   Research and Clinical Nursing' → 'Registered Nursing'; 'Computer and Information
   Sciences, General' → 'Computer and Information Sciences'). Deterministic — still
   derived from the official dictionary, NOT a hand-maintained short-label map. No
   comma → unchanged. (Callers strip the sibling ' (…)' decoration first, so a
   decoration's internal commas never leak into the clause.)"
  [s]
  (some-> s (str/split #",") first str/trim not-empty))

(defn clean-topic
  "Clean topic-chip label for a program. Prefers the engine :cluster-name (NCES CIP
   2020 topic, sibling decoration + trailing punctuation stripped). When the
   cluster-name is PRESENT but still a code/unknown placeholder ('Career Pathway
   <code>' / 'Unknown'), falls back to the program's own clean title so the chip is
   ALWAYS a real topic — never 'Career Pathway <code>'/'Unknown'/'unknown'. Returns
   nil only when NO cluster-name is present (so a bare demo record can fall through to
   its own demo :field, present-by-data)."
  [cluster-name program-title]
  (let [present? (and (string? cluster-name) (seq (str/trim cluster-name)))
        base     (some-> cluster-name strip-decoration trim-title short-topic)]
    (cond
      (and base (seq base) (not (code-or-unknown? base))) base
      present? (title-topic program-title)
      :else nil)))

(defn- grad-rate-figure
  "The graduation-rate tile: rate + High/Medium/Low norm indicator + peer sub."
  [{:keys [grad-rate]}]
  (when grad-rate
    (let [{:keys [rate label indicator delta normed-against peer-average]} grad-rate]
      {:kind :grad-rate
       :value rate
       :label label
       :indicator indicator
       :sub (when (and delta normed-against)
              (str delta " pts vs. " normed-against
                   (when peer-average (str " (avg " peer-average "%)"))))})))

(defn- enrollment-figure
  "Own-group demographic tile when a group % is carried; else total enrollment."
  [{:keys [enrollment-total enrollment-group-label enrollment-group-count enrollment-group-pct]}]
  (cond
    enrollment-group-pct
    {:kind :enrollment
     :value enrollment-group-pct
     :label (str enrollment-group-label " enrollment")
     :sub (str (commafy enrollment-group-count) " of " (commafy enrollment-total) " students")}

    enrollment-total
    {:kind :enrollment
     :value (commafy enrollment-total)
     :label "Total enrollment"}

    :else nil))

;; 0046 — the Admission (Safety/Target/Reach) tile was DROPPED from the six-tile
;; grid to match advisor Tavidee's V3 spec §5.2 (About This School = 6 stat boxes,
;; no admission tile). The STR/Open-Admission badge stays HEADER-level on the school
;; card (school->card-props :classification), NOT in the grid.

(defn- pct-string
  "An acceptance rate expressed 0..1 (the engine's `:acceptance-rate`) → a whole-percent
   string (e.g. 0.984 → \"98%\"); an already-percent number (>1) is used as-is. nil for
   a non-number."
  [rate]
  (when (number? rate)
    (str (js/Math.round (if (<= rate 1) (* 100 rate) rate)) "%")))

(defn- acceptance-figure
  "Acceptance-rate tile from the institution's `:admissions-data :acceptance-rate`
   (0..1) formatted as a whole-percent. nil when absent (present-by-data)."
  [inst]
  (when-let [v (pct-string (get-in inst [:admissions-data :acceptance-rate]))]
    {:kind :acceptance
     :value v
     :label "Acceptance Rate"}))

(defn- transfer-out-figure
  "Transfer-Out Rate tile (0047) from the engine-attached `:transfer-out` map
   (about_school.clj/transfer-out-tile — IPEDS Graduation Rates, GRTYPE 35 /
   CHRTSTAT 22). Shown for 2-year / open-admission community colleges INSTEAD of an
   acceptance rate (V3 spec §5.2). value = the % string (e.g. \"17%\"), label
   \"Transfer-Out Rate\", sub the '≈N of M first-time students transfer to continue
   elsewhere (within 3 yrs)' explanation. Every figure traces to the IPEDS GR
   transfer-out column — never fabricated. nil when the record carries no
   :transfer-out (4-year schools omit it; present-by-data)."
  [{:keys [transfer-out]}]
  (when-let [rate (:rate transfer-out)]
    (cond-> {:kind :transfer-out
             :value rate
             :label "Transfer-Out Rate"}
      (:sub transfer-out) (assoc :sub (:sub transfer-out)))))

(defn- admission-or-transfer-figure
  "Tile #1 of About-This-School, chosen by school type (0047, V3 spec §5.2): a
   2-year / open-admission school that carries an engine `:transfer-out` figure shows
   the Transfer-Out Rate tile; a 4-year selective school shows the Acceptance Rate
   tile. NEVER both, and NEVER a fabricated 0% acceptance — an open-admission school
   reports no acceptance_rate (the engine keeps it nil, not 0), so `acceptance-figure`
   yields nil there and only the transfer-out tile shows."
  [inst]
  (or (transfer-out-figure inst)
      (acceptance-figure inst)))

(defn- retention-figure
  "First-year retention tile from the ALREADY-carried `:retention` string (e.g. \"77%\").
   nil when absent (present-by-data)."
  [{:keys [retention]}]
  (when retention
    {:kind :retention
     :value retention
     :label "First-Year Retention"}))

(defn- avg-debt-figure
  "Avg Debt at Graduation tile (0038) from the engine-attached :avg-debt map
   {:amount <GRAD_DEBT_MDN> :caption \"median, completers\"}. value = '$X' (commafied),
   label = 'Avg Debt at Graduation', sub = the caption. The amount traces directly to
   the College Scorecard GRAD_DEBT_MDN (median-completer) column — never fabricated.
   nil when the record carries no :avg-debt (present-by-data)."
  [{:keys [avg-debt]}]
  (when-let [amount (:amount avg-debt)]
    {:kind :avg-debt
     :value (str "$" (commafy amount))
     :label "Avg Debt at Graduation"
     :sub (or (:caption avg-debt) "median, completers")}))

(defn- residential-figure
  "Residential/Campus tile (0039) from the engine-attached :residential map
   (type/housing?/city/distance-label) enriched with the Notion Setting. The Setting
   reads the top-level :setting (attached by School Profiles AFTER About-This-School,
   so composed here at adapter time; falls back to a :setting carried inside
   :residential). value = Residential/Commuter · label = Campus · sub = city ·
   distance-label · Setting — the city is DROPPED when the distance-label already names
   it (e.g. 'Located in Baton Rouge'), so a Baton Rouge school never reads 'Baton Rouge
   · Located in Baton Rouge'. nil when the record carries no :residential
   (present-by-data)."
  [{:keys [residential setting]}]
  (when residential
    (let [{:keys [type city distance-label]} residential
          setting (or setting (:setting residential))
          city    (when (and city (seq (str city))
                             (not (and distance-label (str/includes? distance-label city))))
                    city)
          parts   (remove nil? [city distance-label setting])
          sub     (when (seq parts) (str/join " · " parts))]
      (cond-> {:kind :residential
               :value type
               :label "Campus"}
        sub (assoc :sub sub)))))

(defn school->about-props
  "Engine institution → props for `about-school-section`: the stat tiles
   (grad-rate · demographic/total enrollment · acceptance-rate OR transfer-out ·
   first-year retention · avg-debt · residential/campus — NO admission tile, 0046),
   where tile #1 is chosen by school type (0047, V3 spec §5.2): a 4-year selective
   school shows Acceptance Rate; a 2-year / open-admission school shows Transfer-Out
   Rate instead (never a fabricated 0% acceptance, never both). The carried
   ability-position + retention, and OUR :about-school-narrative bullets (carried but
   no longer rendered under the grid, 0046). Acceptance-rate reads
   :admissions-data :acceptance-rate (0..1 → whole-percent); retention reads the
   carried :retention string; the Residential/Campus tile reads the engine-attached
   :residential map + the Notion :setting. Legacy cruft is dropped; missing
   tiles/narrative are omitted (present-by-data)."
  [inst]
  (let [figures   (vec (remove nil? [(grad-rate-figure inst)
                                     (enrollment-figure inst)
                                     (admission-or-transfer-figure inst)
                                     (retention-figure inst)
                                     (avg-debt-figure inst)
                                     (residential-figure inst)]))
        narrative (:about-school-narrative inst)]
    (cond-> {}
      (seq figures)             (assoc :figures figures)
      (:ability-position inst)  (assoc :ability-position (:ability-position inst))
      (:retention inst)         (assoc :retention (:retention inst))
      (seq narrative)           (assoc :narrative (vec narrative)))))

(defn costs-advice-bullets
  "0055 — the prototype's templated affordability ADVICE bullets (Fixed text — NOT the
   old LLM :costs-narrative that restated the waterfall dollars). Branches on the school
   type carried on :costs: a COMMUTER (2-year, :commuter? true) is told to live at home;
   a RESIDENTIAL (non-commuter) school is told to weigh on- vs off-campus housing. Both
   always carry the loans bullet (loans are borrowed money, a deferred cost)."
  [{:keys [commuter?]}]
  [(if commuter?
     "Lower your net cost: live at home instead of on campus, and apply for scholarships (see the Scholarships tab)."
     "Lower your net cost: apply for scholarships (see the Scholarships tab), and weigh on-campus vs. off-campus housing.")
   "Loans can cover what's left — but they're borrowed money you repay with interest, a deferred cost, not free aid."])

(defn costs-links
  "0055 — the present-by-data cost LINKS from :costs + the school short-name: the Net
   Price Calculator (when :npc-url) + the Financial Aid office (when :faid-url), with the
   EXACT prototype labels templated on the short-name. Empty when neither URL is present
   (a missing URL omits its link — no broken/empty link). Every URL traces to the record;
   nothing is guessed or constructed."
  [{:keys [npc-url faid-url]} short-name]
  (let [sn (or (when (and short-name (seq (str short-name))) (str short-name)) "this school")]
    (cond-> []
      npc-url  (conj {:href npc-url
                      :label (str "Estimate your net cost — " sn "'s Net Price Calculator →")
                      :title "Net Price Calculator"})
      faid-url (conj {:href faid-url
                      :label (str "Explore aid — " sn "'s Financial Aid office →")
                      :title "Financial Aid office"}))))

(defn school->costs-props
  "Engine institution → props for `costs-section`: the :costs waterfall map, OUR
   templated affordability :narrative advice bullets (0055 — commuter/residential Fixed
   text, replacing the dollar-restating LLM narrative), the school :short-name, and the
   present-by-data :links (NPC + Financial Aid office). nil when the institution carries
   no :costs (present-by-data). Legacy cruft is dropped."
  [inst]
  (when-let [costs (:costs inst)]
    (let [short-name (or (:short-name inst) (:institution-name inst) (:name inst))]
      {:costs      costs
       :short-name short-name
       :narrative  (costs-advice-bullets costs)
       :links      (costs-links costs short-name)})))

(defn program->costs-props
  "0060 — SHORT-TERM / certificate PROGRAM → props for the 'What It Costs You' section,
   mirroring `school->costs-props` but reading the PATHWAY's OWN :costs (funding.clj
   `short-term-costs` attaches a school-shaped :costs map on the program itself, not on
   an institution). Same props shape: the :costs map, the templated affordability
   :narrative advice bullets, the program's :short-name (the provider), and the present-
   by-data :links (NPC + Financial Aid office). nil when the program carries no :costs
   (present-by-data). Reported tuition ⇒ :costs has :coa (the net-cost chart, rendered
   by `costs-section` verbatim); unreported tuition ⇒ :costs has :tuition-note and no
   :coa (the caller renders the graceful cost-helper copy, no chart)."
  [pathway]
  (when-let [costs (:costs pathway)]
    (let [short-name (or (:short-name pathway) (:institution-name pathway)
                         (:provider pathway) (:name pathway))]
      {:costs      costs
       :short-name short-name
       :narrative  (costs-advice-bullets costs)
       :links      (costs-links costs short-name)})))

;; ===========================================================================
;; UI-3 seam — school-card HEADER props (name/city/state/type/website + STR badge)
;; ===========================================================================

(def str-color
  "OUR :str-badge :key → the Safety/Target/Reach pill color (rec-demo palette).
   TOTAL over {safety target reach open}: safety green · target amber · reach red ·
   open neutral. Keyed by the plain name so both string (\"target\") and keyword
   (:target) :key values resolve."
  {"safety" "#2f9e44"
   "target" "#e8a93b"
   "reach"  "#c92a4a"
   "open"   "#676868"})

(defn- str-classification
  "OUR :str-badge → the school-card classification {:key :label :color}: the color
   is mapped from the badge :key via `str-color` (name-coerced so keyword OR string
   keys resolve). nil when the record carries no :str-badge (present-by-data)."
  [{:keys [key label] :as str-badge}]
  (when str-badge
    (cond-> {:key key :label label}
      (some-> key name str-color) (assoc :color (str-color (name key))))))

(def ^:private str-group-labels
  "0074 render-seam fallback — the S·T·R display-group :key → the badge label shown
   when a card FALLS BACK to its group tier (mirrors the group headings the page
   renders above each group). Name-coerced so keyword OR string keys resolve."
  {"safety" "Safety"
   "target" "Target"
   "reach"  "Reach"
   "open"   "Open Admission"})

(defn- str-group-classification
  "0074 (Defect B): the render-seam GROUP FALLBACK classification. A school card is
   ALWAYS rendered inside a known Safety/Target/Reach group, so when the institution
   carries NO own :str-badge we derive the badge from the group tier the card is
   rendered under ({:key :label :color}, color via `str-color`) — the badge is then
   consistent-BY-CONSTRUCTION with its visible group heading and can never be missing.
   nil when no group is supplied (present-by-data — a card OUTSIDE any STR group, e.g.
   the tier-less apprenticeship/short-term lead flow, gets NO fabricated tier)."
  [str-group]
  (when-let [k (some-> str-group name)]
    (cond-> {:key k :label (get str-group-labels k k)}
      (str-color k) (assoc :color (str-color k)))))

(defn school->card-props
  "Engine institution → the rec-demo school-card HEADER props: :name (from
   :institution-name, falling back to :name), :city/:state (from the :location
   {:city :state} map), :type (the first of the :sectors vector), :website/
   :website-label (only when the record carries them), and :classification — OUR
   :str-badge lifted to {:key :label :color} with the STR color mapped by key.
   PRESENT-BY-DATA: any header field the record lacks is simply omitted; nothing
   is recomputed or invented.

   `str-group` (0074, optional) — the Safety/Target/Reach display group the card is
   being rendered under. When the institution has NO own :str-badge, the :classification
   FALLS BACK to that group's tier badge so a card inside a known STR group never shows
   a missing badge. A school WITH its own :str-badge keeps it (the group never overrides
   it). Omit / pass nil for a card outside any STR group → no fallback, no badge.

   `career-technical?` (0078, optional) — when true the card is rendered in the
   Career-Technical section, where Safety/Target/Reach is NOT a valid classification
   (ADR 0008 — S/T/R is a within-Bachelor's grouping). The S/T/R classification is
   SUPPRESSED; only a genuine Open-Admission badge (:key \"open\") survives. Bachelor's
   cards (the default, career-technical? nil/false) keep their S/T/R badge unchanged."
  ([inst] (school->card-props inst nil nil))
  ([inst str-group] (school->card-props inst str-group nil))
  ([inst str-group career-technical?]
  (let [{:keys [city state]} (:location inst)
        ;; 0086 (Fix B) — route the header location/type through the 0072
        ;; `present-value` guard so a data-poor institution's whole-string sentinel
        ;; (city="Unknown", sectors=["Unknown"]) is DROPPED (present-by-data) instead
        ;; of rendering "Unknown, LA · Unknown". A real value passes through unchanged.
        city    (present-value city)
        state   (present-value state)
        type    (present-value (first (:sectors inst)))
        cls0    (or (str-classification (:str-badge inst))
                    (str-group-classification str-group))
        ;; 0078: in the Career-Technical section, drop any Safety/Target/Reach
        ;; classification — keep ONLY a genuine Open-Admission badge.
        cls     (if (and career-technical?
                         (not= "open" (some-> (:key cls0) name)))
                  nil
                  cls0)]
    (cond-> {}
      (or (:institution-name inst) (:name inst))
      (assoc :name (or (:institution-name inst) (:name inst)))
      city                  (assoc :city city)
      state                 (assoc :state state)
      type                  (assoc :type type)
      (:website inst)       (assoc :website (:website inst))
      (:website-label inst) (assoc :website-label (:website-label inst))
      ;; School Profiles (0037): the Notion :logo-url (attached by the engine's
      ;; enrich-profile) lifts to :logo — the card header renders the logo <img> when
      ;; present, defaulting to the school name/favicon (title) when absent.
      (:logo-url inst)      (assoc :logo (:logo-url inst))
      cls                   (assoc :classification cls)))))

;; ===========================================================================
;; UI-2 seam — PROGRAM record → program-section props
;;
;; Same principles as the school seam: PRESENT-BY-DATA (a missing section/figure/
;; narrative is simply omitted) and DROP LEGACY CRUFT (:personalized-overview/
;; :outcome-bullet/:career-summary/:best-for/:key-differentiators are engine-
;; internal and never surfaced). Figures (earnings chart, demand stars/growth,
;; roles + PUMS) stay figures; warmth (:descriptor/:summary/:overview) is OUR
;; narrative — never a figure dumped into prose. (The Salary bottom line is a
;; DETERMINISTIC tier badge, 0053 — no longer the LLM :safe-bet.)
;; ===========================================================================

(defn program->overview-props
  "Program → props for `overview-section`. OUR :overview map (summary · credential-line ·
   student-connection · caveat) DROPS IN; the optional :day-to-day tasks and a
   top-level :licensure-exam are carried only when present. Top-level legacy cruft
   never leaks (we read only the :overview submap + :licensure-exam)."
  [program]
  (let [;; LEGACY (Python-era :advisor/generated-recommendations-pool) programs carry
        ;; their prose at top level (:personalized-overview / :best-for /
        ;; :program-specific-bullets) and no :overview map at all — the Overview block
        ;; rendered EMPTY for every last-year program. When :overview is absent, map
        ;; that prose onto the CANONICAL fields; a present :overview map wins outright
        ;; (eras never mix), and the legacy keys themselves still never leak.
        ov (or (:overview program)
               (when-let [legacy (:personalized-overview program)]
                 (cond-> {:summary legacy}
                   (:best-for program) (assoc :student-connection (:best-for program)))))
        legacy-bullets (when-not (:overview program)
                         (seq (:program-specific-bullets program)))
        ;; APPRENTICESHIPS carry no O*NET :day-to-day; their `:bullets` are the
        ;; differentiation set (the editor's "Why This Fits"). Daryl decision: fold
        ;; them into the Overview (option 2) as :extra-bullets — the apprenticeship's
        ;; equivalent of a college program's day-to-day. NOT applied to college/
        ;; short-term, whose `:bullets` are the Making-It-Pay-Off rules caveats.
        appr? (some? (:apprenticeship-title program))]
    (cond-> (select-keys ov [:summary :credential-line :student-connection :caveat])
      legacy-bullets                        (assoc :extra-bullets (vec legacy-bullets))
      (seq (:day-to-day ov))                (assoc :day-to-day (vec (:day-to-day ov)))
      (and appr? (seq (:bullets program)))  (assoc :extra-bullets (vec (:bullets program)))
      (:licensure-exam program)  (assoc :licensure-exam (:licensure-exam program))
      ;; 0057 — the program's official page for the "See the full program page →" link
      ;; (source column program_url, already carried onto the record). Present-by-data.
      (:program-url program)     (assoc :program-url (:program-url program)))))

;; ---------------------------------------------------------------------------
;; 0053 — the deterministic Salary BOTTOM-LINE tier badge (replaces the LLM
;; :safe-bet). PURE derives over the LWC demand ★ + the 3-way living-wage band —
;; the EXACT prototype logic (tavideehoskins/bryc-rec-demo salary-tile :summary).
;; ---------------------------------------------------------------------------

(defn salary-verdict
  "PURE (0053). The bottom-line tier WORD from the LWC demand ★ + the living-wage band:
   ★≥4 AND band \"Above\" → \"Strong & stable\"; ★≥3 → \"Solid\"; else \"Mixed\".
   The Above gate is REQUIRED for \"Strong & stable\" — so ★4 + \"Near\" = \"Solid\",
   NOT \"Strong\"; and any ★≤2 floors to \"Mixed\" regardless of band."
  [stars band]
  (cond (and (>= stars 4) (= band "Above")) "Strong & stable"
        (>= stars 3) "Solid"
        :else "Mixed"))

(defn salary-demand-word
  "PURE (0053). The demand phrase from the LWC ★: ≥4 \"High demand\"; =3 \"Steady
   demand\"; else \"Some demand\"."
  [stars]
  (cond (>= stars 4) "High demand" (= stars 3) "Steady demand" :else "Some demand"))

(defn salary-wage-phrase
  "PURE (0053). The wage phrase from the 3-way living-wage band; a nil/unknown band
   falls back to \"have competitive earnings\" (present-by-data)."
  [band]
  (case band
    "Above" "earn above a living wage"
    "Near"  "earn near a living wage"
    "Below" "earn below a living wage"
    "have competitive earnings"))

(defn salary-bottom-line
  "PURE (0053). The deterministic Salary bottom-line tier badge {:verdict :sentence}
   from the LWC ★ + living-wage band — the FIXED replacement for the old LLM
   :safe-bet. The sentence templates the demand word + wage phrase; nil when there is
   no ★ demand figure (present-by-data ⇒ no bottom-line card)."
  [stars band]
  (when (number? stars)
    {:verdict  (salary-verdict stars band)
     :sentence (str (salary-demand-word stars) ". Graduates " (salary-wage-phrase band)
                    ", with clear room to grow into higher-paying roles.")}))

(defn program->salary-props
  "Program → props for `salary-section`. Carries the CHART inputs (:earnings +
   :living-wage-band/:living-wage-area-value/:living-wage-state-value), the
   deterministic demand FIGURES (:stars — lifted from inside :salary, falling back
   to the program's :lwc-stars · :growth-rate · :growth-net-new · :growth-openings),
   the occupation fallback (:occupation/:mode), OUR :descriptor narrative, and the
   DETERMINISTIC :bottom-line tier badge (0053, {:verdict :sentence}) derived from
   the LWC ★ + living-wage band — NO LLM. nil when the program carries no :salary
   (present-by-data)."
  [program]
  (when-let [s (:salary program)]
    (let [stars  (or (:stars s) (:lwc-stars program))
          bottom (salary-bottom-line stars (:living-wage-band s))]
      (cond-> {}
        (:earnings s)                (assoc :earnings (:earnings s))
        (:living-wage-band s)        (assoc :living-wage-band (:living-wage-band s))
        (:living-wage-area-value s)  (assoc :living-wage-area-value (:living-wage-area-value s))
        (:living-wage-state-value s) (assoc :living-wage-state-value (:living-wage-state-value s))
        (some? stars)                (assoc :stars stars)
        (:growth-rate s)             (assoc :growth-rate (:growth-rate s))
        (:growth-net-new s)          (assoc :growth-net-new (:growth-net-new s))
        (:growth-openings s)         (assoc :growth-openings (:growth-openings s))
        (:occupation s)              (assoc :occupation (:occupation s))
        (:mode s)                    (assoc :mode (:mode s))
        (:descriptor s)              (assoc :descriptor (:descriptor s))
        bottom                       (assoc :bottom-line bottom)))))

(defn- onet-soc-from-link
  "The O*NET-SOC code (e.g. \"11-1011.00\") extracted from an :onet-link URL tail."
  [onet-link]
  (when (seq onet-link)
    (last (str/split onet-link #"/summary/"))))

(defn- program-role
  "Normalize a real engine career role to the `career-row` shape: :soc drives the
   O*NET link (so it's the O*NET-SOC with .00, taken from :onet-link when present),
   an advanced-credential role surfaces a :requirement label, and the deterministic
   median/education FIGURE fills the row detail when no :desc copy exists."
  [{:keys [title soc onet-link median education advanced-credential? requirement desc]}]
  (cond-> {:title title
           :soc (or (onet-soc-from-link onet-link) soc)}
    (or requirement advanced-credential?)
    (assoc :requirement (or requirement "Advanced degree typically required"))

    :always
    (assoc :desc (or desc
                     (when median
                       (str "Typical pay about $" (commafy median) "/yr"
                            (when education (str " · " education " typical"))))))))

(defn- program-pums-occupation
  "Normalize a real PUMS occupation to the `pums-subsection` shape: :onet is the
   O*NET-SOC code (from :onet-link) so the row links, and :title falls back to the
   SOC for broad census buckets that carry no title (they still render, unlinked)."
  [{:keys [onet onet-link soc title note pct median in-field?]}]
  (cond-> {:title (or title soc)
           :pct pct
           :median median}
    (or onet (onet-soc-from-link onet-link)) (assoc :onet (or onet (onet-soc-from-link onet-link)))
    note                                     (assoc :note note)
    (some? in-field?)                        (assoc :in-field? in-field?)))

(defn- program-pums
  "Normalize the PUMS panel: carry scope/field/callout + normalized occupations."
  [pums]
  (when pums
    (cond-> (select-keys pums [:scope :field :total-in-field :top-occupation-pct])
      (some? (:under-50-callout? pums)) (assoc :under-50-callout? (:under-50-callout? pums))
      (seq (:occupations pums))
      (assoc :occupations (mapv program-pums-occupation (:occupations pums))))))

(def whats-cool-accents
  "Left-border accent classes for the What's Cool callouts, CYCLED across the (≤4)
   cards — the same rec-demo palette the standalone demo hand-applies to its points
   (components.rec-demo.data)."
  ["border-l-[#05a09c] bg-[#d0ecef]/60"
   "border-l-[#007f81] bg-[#b1e1e9]/50"
   "border-l-[#40bfbb] bg-[#d0ecef]/60"
   "border-l-[#2a6465] bg-[#d0ecef]/40"])

(defn program->whats-cool-props
  "Program → props for `whats-cool-section`: each engine :whats-cool point
   {:title :body (:source)} → a `callout-card` map {:title :body :accent (:tag)}.
   The left-border :accent CYCLES `whats-cool-accents` across the cards; a :tag or
   :accent ALREADY on the point is PRESERVED (the standalone demo hand-labels its
   points) — nothing is invented for engine points (they carry no category tag).
   The :source provenance is DROPPED (never rendered). nil when the program carries
   no :whats-cool (present-by-data)."
  [program]
  (when-let [points (seq (:whats-cool program))]
    (vec (map-indexed
          (fn [i {:keys [title body tag accent]}]
            (cond-> {:title title
                     :body body
                     :accent (or accent (nth whats-cool-accents (mod i (count whats-cool-accents))))}
              tag (assoc :tag tag)))
          points))))

(defn split-career-roles
  "PURE (0049): partition normalized career roles (career-row shape) into
   {:base [...] :advanced [...]}. BASE roles — attainable at the pathway's own credential
   — are always shown; ADVANCED-credential roles (those the engine flags
   `advanced-credential?`, which `program-role` surfaces as a `:requirement` badge) are
   revealed behind the 'Show advanced-practice paths' toggle. This matches Tavidee's demo
   grouping, but is driven by OUR advanced-credential flag rather than his hand-authored
   ordering, so the toggle label is truthful regardless of engine role order. Order is
   preserved WITHIN each group. Degenerate cases (present-by-data): no advanced role →
   :advanced empty (caller renders no toggle); EVERY role advanced → they all fall to
   :base so the section is never hidden entirely behind a toggle."
  [roles]
  (let [advanced? (fn [r] (some? (:requirement r)))
        base (filterv (complement advanced?) roles)
        advanced (filterv advanced? roles)]
    (if (empty? base)
      {:base (vec roles) :advanced []}
      {:base base :advanced advanced})))

(defn cap-career-groups
  "PURE (0088 / PRD 0012): cap each career group to the best-5. The roles arrive already
   ordered by LWC career-outlook star from the engine (0087) and `split-career-roles`
   preserves that order within each group, so a plain `take 5` keeps the strongest.
   Attainable roles beyond 5 are intentionally dropped (the star ranking guarantees the
   5 kept are the strongest — that IS the crop). No re-sort; silent (nothing new surfaced)."
  [{:keys [base advanced]}]
  {:base (vec (take 5 base)) :advanced (vec (take 5 advanced))})

(defn program->careers-props
  "Program → props for `careers-section`. Carries the deterministic FIGURES — the
   :roles list (normalized to the `career-row` shape) and the :pums panel
   (normalized to the `pums-subsection` shape) — plus OUR :summary warm lead line.
   nil when the program carries no :careers; :pums omitted when absent
   (present-by-data)."
  [program]
  (when-let [c (:careers program)]
    (cond-> {}
      (seq (:roles c)) (assoc :roles (mapv program-role (:roles c)))
      (:pums c)        (assoc :pums (program-pums (:pums c)))
      (:summary c)     (assoc :summary (:summary c)))))

;; ===========================================================================
;; Time & Completion seam — program → Time-&-Completion card props
;;
;; Same class of bug as 0014/0015: `time-to-credential-section` read the standalone-
;; demo shape (:designed/:actual as display STRINGS + a TOP-LEVEL :completions map),
;; but the REAL engine (phases/time_completion.clj) emits :time-to-credential as a
;; map of SUB-MAPS — {:designed {:length :credential …} :typical-actual {:display
;; :note …} :completers {:count :note …}}. Rendering (:designed t) directly then
;; passed a MAP as a React child → "Objects are not valid as a React child" and the
;; panel blanked on expand. This adapter FLATTENS both shapes into string tiles +
;; note strings so the reader never renders a raw map.
;; ===========================================================================

(defn program->time-to-credential-props
  "Real program (or standalone-demo pathway) → props for `time-to-credential-section`:
     {:cards [{:value <string> :label <string> :sub? <string>} …]   ; the stat tiles
      :notes [<string> …]}                                          ; caption lines
   Maps the REAL engine :time-to-credential shape — where :designed / :typical-actual
   / :completers are MAPS (NOT display strings) — into FLAT string tiles: the designed
   :length (with :credential as the sub-label), the typical-actual :display, and the
   completers :count (stringified). Their :note strings become the caption lines.
   Also tolerates the standalone-demo shape (:designed/:actual STRINGS + :actual-note,
   and a TOP-LEVEL :completions {:per-year :year}) so the demo keeps rendering.
   PRESENT-BY-DATA: a card/note whose data is absent is omitted; nil when nothing
   resolves (no Time & Completion section)."
  [program]
  (let [t          (:time-to-credential program)
        designed   (:designed t)
        actual     (:typical-actual t)
        completers (:completers t)
        ;; 0054 — the DEGREE-TYPE Rules-of-the-Game caveats moved OFF Time & Completion
        ;; onto the per-program `:rules` ('Making It Pay Off') section
        ;; (`program->rules-props`). 0061 — the UNIVERSAL 'Finish on time' on-time actions
        ;; come BACK here (they render on EVERY card): flatten the engine's :on-time-actions
        ;; {:title :body} maps to STRINGS (title-less = body; titled = 'title: body'), never
        ;; a raw map React child. Present-by-data ⇒ absent/empty ⇒ no :on-time.
        on-time (->> (:on-time-actions t)
                     (keep (fn [c]
                             (let [b  (str (:body c))
                                   ti (str (:title c))]
                               (when (seq b)
                                 (if (seq ti) (str ti ": " b) b)))))
                     vec)
        demo-actual      (:actual t)          ;; standalone-demo string
        demo-completions (:completions program) ;; standalone-demo top-level map
        designed-card
        (cond
          (map? designed)  (when (seq (str (:length designed)))
                             (cond-> {:value (:length designed) :label "Designed length"}
                               (seq (str (:credential designed))) (assoc :sub (:credential designed))))
          (seq (str designed)) {:value (str designed) :label "Designed length"})
        actual-card
        (cond
          (map? actual)         (when (seq (str (:display actual)))
                                  {:value (:display actual) :label "Typical actual"})
          (seq (str demo-actual)) {:value (str demo-actual) :label "Typical actual"})
        completers-card
        (cond
          (map? completers)  (when (some? (:count completers))
                               {:value (str (:count completers)) :label "Graduates / year"})
          demo-completions   (when (some? (:per-year demo-completions))
                               {:value (str (:per-year demo-completions)) :label "Graduates / year"}))
        cards (vec (remove nil? [designed-card actual-card completers-card]))
        notes (vec (remove nil?
                    [(when (map? actual) (:note actual))
                     (:actual-note t)
                     (when (map? completers) (:note completers))
                     (when demo-completions
                       (str "Graduates / year is program-specific — " (:per-year demo-completions)
                            " completers in " (:year demo-completions)
                            " (BOR completions by CIP, CMPLRACE)."))]))]
    (when (seq cards)
      (cond-> {:cards cards}
        (seq notes)   (assoc :notes notes)
        (seq on-time) (assoc :on-time on-time)))))

;; ===========================================================================
;; 0054 — Making It Pay Off (:rules) seam — program → rules-section props
;;
;; The engine (phases/rules.clj) attaches a per-program :rules datum for NON-BACHELOR'S
;; programs (present-by-data; a bachelor's carries none): {:bullets [{:title :body} …]
;; :terminal? :transfer? :terminal <str> :transfer <str>}. The terminal-DEFINITION /
;; transfer-RISK callouts render as standalone boxes; the rest as a bullet-list. This
;; adapter FLATTENS each bullet map to a STRING (never a raw map React child).
;; ===========================================================================

(defn program->rules-props
  "Program → props for `rules-section` (Making It Pay Off): the flattened rules
   :bullets (STRINGS — a title-less caveat is just its :body, a titled one 'title: body'),
   the :terminal?/:transfer? callout flags, and the :terminal/:transfer callout text.
   nil when the program carries no :rules (present-by-data ⇒ a bachelor's shows none)."
  [program]
  (when-let [r (:rules program)]
    (let [bullets (->> (:bullets r)
                       (keep (fn [c]
                               (let [b  (str (:body c))
                                     ti (str (:title c))]
                                 (when (seq b)
                                   (if (seq ti) (str ti ": " b) b)))))
                       vec)]
      (cond-> {}
        (seq bullets)             (assoc :bullets bullets)
        (:terminal? r)            (assoc :terminal? true)
        (:transfer? r)            (assoc :transfer? true)
        (seq (str (:terminal r))) (assoc :terminal (:terminal r))
        (seq (str (:transfer r))) (assoc :transfer (:transfer r))))))

;; ===========================================================================
;; 0073 — program CLASSIFICATION chip (amber "Terminal"/"Transfer") for the card header.
;;
;; The engine attaches the terminal/transfer designation on the program's :rules datum
;; (phases/rules.clj) — the SAME datum the Making It Pay Off callout (`program->rules-props`
;; / core.cljs `rules-section`) reads, so the header chip and the callout can NEVER
;; disagree. A bachelor's carries no :rules ⇒ no classification chip (present-by-data —
;; a bachelor's must NOT show "Terminal"). This is DISTINCT from the school-level
;; :str-badge (Safety/Target/Reach) — a different concept.
;; ===========================================================================

(defn program-classification
  "PURE (0073; 0075). The program's amber CLASSIFICATION chip(s): a `Terminal` chip for a
   direct-to-work terminal credential, a `Transfer` chip for a transfer pathway (Terminal
   FIRST when a record somehow carries both). Reads the engine's :degree-classification —
   derived from the degree-type (PIPELINE data) and attached to EVERY non-bachelor's program
   INDEPENDENT of the Notion caveats (0075), so the badge shows for every student even when
   Making It Pay Off is dark. Falls back to the older :rules :terminal?/:transfer? flags for
   demo-baked pools generated before 0075. nil when neither is set (present-by-data — a
   bachelor's / :unknown shows none)."
  [program]
  (let [{:keys [terminal? transfer?]} (or (:degree-classification program)
                                          (:rules program))
        chips (cond-> []
                terminal? (conj {:label "Terminal"})
                transfer? (conj {:label "Transfer"}))]
    (when (seq chips) chips)))

;; ===========================================================================
;; 0014 seam — SHORT-TERM / APPRENTICESHIP card HEADER props
;;
;; ONE tolerant adapter for BOTH secondary tracks (both tabs render the same
;; `short-term-card`). Real short-term programs carry :program-title /
;; :institution-name / :award-level-name / :cluster-name with demand :stars inside
;; :salary; real apprenticeships carry :apprenticeship-title / :company-name /
;; :program-type with demand :stars inside :earn. PRESENT-BY-DATA: any header part
;; the record lacks is omitted so the card never renders a bare " · " subtitle.
;; ===========================================================================

(defn program->short-term-card-props
  "Real short-term-program OR apprenticeship record → the rec-demo `short-term-card`
   HEADER props: :title (from :program-title, falling back to :apprenticeship-title),
   :provider (:institution-name / :company-name), :credential (:award-level-name /
   :program-type), :field (:cluster-name / :field), :stars (the LWC demand rating,
   resolved across the short-term [:salary …] and apprenticeship [:earn :demand]
   shapes, falling back to a top-level :lwc-stars for the demo records), and
   :info-url (the program/apply link, resolved across tracks). PRESENT-BY-DATA:
   whatever the record lacks is omitted — the card guards the provider/credential
   separator so a missing part never leaves a stray ' · '."
  [p]
  ;; :title/:provider/:credential also fall back to the OLD standalone-demo header
  ;; keys (:name / :provider / :credential-level) — slice 0033 data-drift fix: the
  ;; demo's electrical-apprenticeship + LPN records carry ONLY those keys, so without
  ;; the fallback the card rendered a BLANK title/provider and dropped the credential.
  (let [raw-title  (or (:program-title p) (:apprenticeship-title p) (:name p))
        title      (or (trim-title raw-title) raw-title)
        provider   (or (:institution-name p) (:company-name p) (:provider p))
        credential (or (:award-level-name p) (:program-type p) (:credential-level p))
        field      (or (clean-topic (:cluster-name p) raw-title) (:field p))
        stars      (or (get-in p [:salary :occupation :stars])
                       (get-in p [:salary :demand :stars])
                       (get-in p [:salary :stars])
                       (get-in p [:earn :demand :stars])
                       (:lwc-stars p))
        info-url   (or (get-in p [:funding :info-url])
                       (get-in p [:earn :info-url])
                       (:program-url p)
                       (:application-url p)
                       (:info-url p))
        ;; Slice 0033 Fix 1 — flag an apprenticeship so `short-term-card` can add the
        ;; "Earn while you learn" 4th header pill (previously only in the expanded
        ;; Overview). Robust across the engine 0032 tag (:degree-type :apprenticeship),
        ;; the real pool shape (:apprenticeship-title / :program-type "Registered
        ;; Apprenticeship" / :earn earn-while-learn?) and the demo record (:type
        ;; :apprenticeship / top-level :earn-while-learn?).
        apprenticeship? (boolean
                         (or (= :apprenticeship (:degree-type p))
                             (= :apprenticeship (:type p))
                             (:apprenticeship-title p)
                             (:earn-while-learn? p)
                             (get-in p [:earn :earn-while-learn?])
                             (some? (:earn p))
                             (= "Registered Apprenticeship" (:program-type p))))
        ;; 0073 — the amber Terminal/Transfer classification chip(s), present-by-data
        ;; from the program's :rules (the SAME datum the Making It Pay Off callout reads).
        classifications (program-classification p)]
    (cond-> {}
      title              (assoc :title title)
      (:acronym p)       (assoc :acronym (:acronym p))
      provider           (assoc :provider provider)
      credential         (assoc :credential credential)
      field              (assoc :field field)
      (some? stars)      (assoc :stars stars)
      info-url           (assoc :info-url info-url)
      apprenticeship?    (assoc :apprenticeship? true)
      (seq classifications) (assoc :classifications classifications))))

;; ===========================================================================
;; 0015 seam — COLLEGE PATHWAY-ROW collapsed HEADER props
;;
;; Same class of bug as 0014: the college pathway-row header read the standalone-
;; demo keys (:name/:track/:field/:credential-level/:lwc-stars) — none of which a
;; REAL college program record carries, so the header rendered a BLANK title and
;; empty chips. Real records carry :program-title / :award-level-name / :cluster-name
;; and demand :stars INSIDE :salary. This adapter maps real→header props with the
;; demo-shape keys as fallbacks so the standalone demo keeps rendering.
;; PRESENT-BY-DATA: any part the record lacks is omitted (no blank title, no nil
;; leak, and the pathway-chips separator only shows the parts that exist).
;; ===========================================================================

(defn program->pathway-header-props
  "Real college program (or a standalone-demo pathway) → the rec-demo `pathway-row`
   collapsed-HEADER props: :title (from :program-title, falling back to the demo
   :name), :acronym, :track, :credential (from :award-level-name, falling back to
   the demo :credential-level), :field (from :cluster-name, falling back to the demo
   :field), and :stars (the LWC demand rating, lifted across the [:salary …] shapes,
   falling back to a top-level :lwc-stars for the demo records). PRESENT-BY-DATA:
   whatever the record lacks is omitted — the header never renders a blank title and
   the chips never surface a bare separator."
  [p]
  (let [raw-title  (or (:program-title p) (:name p))
        title      (or (trim-title raw-title) raw-title)
        credential (or (:award-level-name p) (:credential-level p))
        field      (or (clean-topic (:cluster-name p) raw-title) (:field p))
        stars      (or (get-in p [:salary :occupation :stars])
                       (get-in p [:salary :demand :stars])
                       (get-in p [:salary :stars])
                       (:lwc-stars p))
        ;; 0073 — the amber Terminal/Transfer classification chip(s), present-by-data
        ;; from the program's :rules (the SAME datum the Making It Pay Off callout reads).
        classifications (program-classification p)]
    (cond-> {}
      title              (assoc :title title)
      (:acronym p)       (assoc :acronym (:acronym p))
      (:track p)         (assoc :track (:track p))
      credential         (assoc :credential credential)
      field              (assoc :field field)
      (some? stars)      (assoc :stars stars)
      (seq classifications) (assoc :classifications classifications))))

;; ===========================================================================
;; 0014 seam — SCHOLARSHIP card props (real scholarship → demo scholarship-card)
;;
;; Maps a REAL engine scholarship record to the props the demo-style scholarship
;; card consumes. PRESENT-BY-DATA: :sponsor/:eligibility/:target-levels are omitted
;; when the record lacks them (real records carry none). The engine's
;; :application-tips is a STRING — wrapped into a one-element vector so the card's
;; bullet-list renders it. Engine-internal cruft (:score-breakdown/:match-reasons/
;; :ranking-rationale/:verification/…) is never surfaced.
;; ===========================================================================

(defn scholarship->card-props
  "Real scholarship record → demo scholarship-card props: :name, :award (from
   :award-amount-display, falling back to :award), :deadline, :why-fits (from
   :personalized-explanation), :selection (from :selection-criteria), :url (from
   :application-url), and :tips (the :application-tips STRING wrapped in a vector;
   an already-sequential tips value is carried as-is). :sponsor/:eligibility/
   :target-levels/:description are carried only when present (present-by-data — 0070
   surfaces the previously-unmapped :description)."
  [s]
  ;; 0072 — every user-facing pass-through STRING field is routed through
  ;; `present-value` so a NOT_FOUND / UNKNOWN / N/A / TBD / NONE / blank sentinel is
  ;; dropped (present-by-data ⇒ the key is omitted ⇒ nothing renders). :award guards
  ;; the RESULT of the :award-amount-display/:award fallback (both carry "NOT_FOUND"
  ;; live). Each :tips entry is guarded individually so a sentinel tip is dropped while
  ;; real tips in the same vector survive. This SUPERSEDES the 0070 blank-description
  ;; gate (present-value also drops the blank "" real records carry).
  (let [award    (present-value (or (:award-amount-display s) (:award s)))
        deadline (present-value (:deadline s))
        why-fits (present-value (:personalized-explanation s))
        desc     (present-value (:description s))
        selection (present-value (:selection-criteria s))
        url      (present-value (:application-url s))
        sponsor  (present-value (:sponsor s))
        elig     (present-value (:eligibility s))
        tips     (:application-tips s)
        tips-vec (cond
                   (string? tips)     (some-> (present-value tips) vector)
                   (sequential? tips) (not-empty (into [] (keep present-value) tips))
                   :else              nil)]
    (cond-> {}
      (:name s)          (assoc :name (:name s))
      award              (assoc :award award)
      deadline           (assoc :deadline deadline)
      why-fits           (assoc :why-fits why-fits)
      ;; 0070 — surface the engine-generated :description (present-by-data), under
      ;; Why-it-fits. Guarded by present-value (0072): real scholarships carry a BLANK
      ;; :description "" — and some carry "NOT_FOUND" — both are dropped.
      desc               (assoc :description desc)
      selection          (assoc :selection selection)
      url                (assoc :url url)
      sponsor            (assoc :sponsor sponsor)
      elig               (assoc :eligibility elig)
      (:target-levels s) (assoc :target-levels (:target-levels s))
      (seq tips-vec)     (assoc :tips tips-vec))))
