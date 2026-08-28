(ns components.rec-demo.core
  "PROTOTYPE — Rec UI redesign demo page, v2 (route: /rec-demo).

   v2 restructure (per BRYC_Rec_v2_Build_Plan.md):
     • Two-column page: LEFT = Advisor Messages (note + login-driven advisor card);
       RIGHT = 'Check these out:' (Safety/Target/Reach legend, Pathway
       Recommendations, school tiles, Terms to Know).
     • School tile → 'Learn More' → per-pathway sections as a one-at-a-time
       accordion (Overview · What's Cool · Salary & Job Opportunities · Career
       Paths) followed by the SCHOOL-anchored sections (About This School · What
       It Costs You) rendered once per school.
     • TEAL branding; 'Pathway' (not 'Program') throughout; no 'new' labels.

   All content is hardcoded (components.rec-demo.data). Every discipline-specific
   field is conditional so the same components generalize to any bachelor's
   pathway. Section display order follows the plan exactly (data.cljs `section-order`);
   grad-rate norming is sector-aware and carried per-school (each record's :grad-rate),
   so About This School + What It Costs You render identically for 4-year and 2-year schools."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [clojure.string :as str]
            [components.rec-demo.data :as data]
            [components.rec-demo.charts :as charts]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/accordion" :as accordion]))

;; ============================================================================
;; Small shared primitives
;; ============================================================================

(defn onet-url [soc] (str "https://www.onetonline.org/link/summary/" soc))

;; School logo — CONSISTENTLY ATTAINABLE for any school: a curated asset when one
;; exists (:logo), otherwise a favicon derived from the school's website domain
;; (works for every school with a domain — no per-school hand-fetching).
;; Production-grade path: a curated per-UNITID logo store; this is the fallback.
(defn favicon-url [website]
  (when (seq website)
    (let [domain (-> website (str/replace #"^https?://" "") (str/replace #"^www\." "")
                     (str/split #"/") first)]
      (str "https://icons.duckduckgo.com/ip3/" domain ".ico"))))

(defui ext-link
  "Inline external link: hover tooltip (title), opens in a new tab (Build Plan §4)."
  [{:keys [href label title class]}]
  ($ :a {:href href :target "_blank" :rel "noopener noreferrer"
         :title (or title href)
         :class (str "text-[#007f81] font-semibold hover:underline underline-offset-2 "
                     "decoration-[#40bfbb] " class)}
     label))

(defui section-eyebrow
  [{:keys [label]}]
  ($ :div {:class "flex items-center mb-4"}
     ($ :div {:class "w-2 h-2 bg-[#05a09c] rounded-full mr-3"})
     ($ :h3 {:class "text-sm font-semibold uppercase tracking-wide text-[#2a6465] font-head"} label)))

(defui bullet-list
  [{:keys [bullets dot]}]
  ($ :ul {:class "space-y-3"}
     (for [[i b] (map-indexed vector (remove nil? bullets))]
       ($ :li {:key i :class "flex text-sm md:text-base text-[#313335] leading-relaxed"}
          ($ :span {:class "mr-3 text-[#05a09c] shrink-0"} (or dot "•"))
          ($ :span b)))))

(defui stars
  [{:keys [n size color]}]
  ($ :div {:class (str "flex items-center gap-0.5 " (or size "text-xl"))}
     (for [i (range 5)]
       ($ :span {:key i :class (if (< i n) (or color "text-[#05a09c]") "text-white/40")} "★"))))

(defui str-badge
  [{:keys [classification]}]
  ($ :span {:class "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold text-white"
            :style {:background-color (:color classification)}}
     (:label classification)))

(defui read-more
  "Shows `head` items, then a Read More / Read Less toggle revealing `tail`."
  [{:keys [head tail label-more label-less]}]
  (let [[open? set-open!] (use-state false)]
    ($ :div
       head
       (when open? tail)
       (when tail
         ($ :button {:class "mt-3 text-sm font-semibold text-[#007f81] hover:underline underline-offset-2"
                     :on-click #(set-open! not)}
            (if open? (or label-less "Read Less ▲") (or label-more "Read More ▼")))))))

;; ============================================================================
;; Section 1 — Overview (exactly 6 bullets; caveat conditional)
;; ============================================================================

(defui overview-section [{:keys [pathway]}]
  (let [{:keys [credential-line student-connection caveat day-to-day]} (:overview pathway)
        lic (:licensure-exam pathway)]
    ($ :div {:class "space-y-4"}
       ($ bullet-list
          {:bullets (concat [credential-line student-connection caveat] day-to-day)})
       (when lic
         ($ :p {:class "text-sm text-[#313335] leading-relaxed bg-[#d0ecef]/50 rounded-lg p-3"}
            "The degree alone doesn't license you — you also pass the "
            ($ ext-link {:href (:url lic) :label (:name lic)
                         :title (str (:name lic) " — " (:note lic))})
            ", " (:note lic) "."))
       ;; §6.6 — every Overview links to the program page (program_url), omitted when absent.
       (when-let [u (:program-url pathway)]
         ($ ext-link {:href u :label "See the full program page →" :title (:name pathway)})))))

;; ============================================================================
;; Section 2 — What's Cool About This Pathway (≤4 qualitative callouts)
;; ============================================================================

(defui callout-card [{:keys [tag title body accent]}]
  ($ :div {:class (str "border-l-4 rounded-r-xl shadow-sm p-4 " accent)}
     ($ :div {:class "text-[11px] font-semibold uppercase tracking-wide text-[#676868] mb-1"} tag)
     ($ :div {:class "text-sm md:text-base font-semibold text-[#2a6465] mb-1 font-head"} title)
     ($ :p {:class "text-sm text-[#313335] leading-relaxed"} body)))

(defui whats-cool-section [{:keys [pathway]}]
  ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-3"}
     (for [[i c] (map-indexed vector (take 4 (:whats-cool pathway)))]
       ($ callout-card (assoc c :key i)))))

;; ============================================================================
;; Section 3 — Salary & Job Opportunities (chart + banded sentence + 3 tiles)
;; ============================================================================

(defui salary-tile [{:keys [kind value label detail]}]
  (let [hot? (= kind :stars)]
    ($ :div {:class (str "rounded-xl shadow-sm p-4 flex flex-col gap-1.5 "
                         (if hot? "bg-[#05a09c] ring-1 ring-[#05a09c]"
                                  "bg-[#f3fafb] ring-1 ring-[#cdeaee]"))}
       (if hot?
         ($ stars {:n value :size "text-3xl" :color "text-white"})
         ($ :div {:class "text-2xl font-bold text-[#2a6465] font-head"} value))
       ($ :div {:class (str "text-xs font-semibold uppercase tracking-wide "
                            (if hot? "text-white" "text-[#676868]"))} label)
       ($ :div {:class (str "text-xs leading-relaxed " (if hot? "text-white/90" "text-[#313335]"))} detail))))

(defn- demand-word [stars]
  (cond (>= stars 4) "High demand" (= stars 3) "Steady demand" :else "Some demand"))

(defui salary-section
  "Earnings source precedence is PSEO-FIRST (Upgrade §4.2): program-level PSEO earnings are
   used whenever the CIP cascade (6→4→2 digit) finds a published row (:earnings). The LWC
   occupation median is the FALLBACK only when no PSEO row exists (e.g. an apprenticeship).
   PSEO shows Year-1 + Year-5 medians; occupation mode is labeled 'median' (§4.4). Growth
   and total openings are shown as DISTINCT measures (§4.8). Institution-agnostic."
  [{:keys [pathway school]}]
  (let [s (:salary pathway)
        area (or (:living-wage-area school) data/home-metro)
        usd charts/usd
        band (:living-wage-band s)
        band-color (case band "Above" "#2f9e44" "Below" "#c92a4a" "#e8a93b")]
    (if (:earnings s)
      ;; ---- PSEO mode (program-level earnings) ----
      (let [stars (or (:lwc-stars pathway) 0)
            ;; y-axis scales to the program's own earnings (Process Tech Y5 = $124k > old 100k cap)
            ymax (int (* 1.15 (apply max (conj (map :value (:earnings s)) (:living-wage-area-value s)))))
            chart-data
            (concat
              (for [e (:earnings s)]
                {:name (:label e) :value (:value e) :label (usd (:value e)) :fill charts/color-earnings})
              [{:name (str "Living Wage —\n" area "\n(Single Adult)")
                :value (:living-wage-area-value s) :label (usd (:living-wage-area-value s)) :fill charts/color-col}
               {:name (str "Living Wage —\n" data/home-state "\n(Single Adult)")
                :value (:living-wage-state-value s) :label (usd (:living-wage-state-value s)) :fill charts/color-col}])
            demand  (demand-word stars)
            wage    (case band "Above" "earn above a living wage" "Near" "earn near a living wage"
                               "Below" "earn below a living wage" "have competitive earnings")
            verdict (cond (and (>= stars 4) (= band "Above")) "Strong & stable"
                          (>= stars 3) "Solid" :else "Mixed")
            tiles [{:kind :stars :value stars :label "Job Demand (LWC)"
                    :detail (str "LWC rates this " stars " of 5 — " (.toLowerCase demand) " in " data/home-state ".")}
                   {:kind :stat :value (:growth-rate s) :label "10-Year Growth"
                    :detail (str "≈" (:growth-net-new s) " net new jobs over 10 years; " (:growth-openings s)
                                 " total openings incl. replacement/turnover.")}
                   {:kind :summary :value verdict :label "Bottom line"
                    :detail (str demand ". Graduates " wage ", with clear room to grow into higher-paying roles.")}]]
        ($ :div {:class "space-y-5"}
           ($ charts/salary-vs-col-chart
              {:title "Median Earnings vs. Cost of Living"
               :legend-items [{:color charts/color-earnings :label "Graduate earnings (median)"}
                              {:color charts/color-col :label "Living wage for a single adult"}]
               :y-max ymax
               :data chart-data})
           (when (:earnings-source s)
             ($ :div {:class "text-xs italic text-[#676868]"}
                (str "Program-level graduate earnings — " (:earnings-source s) ".")))
           ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-4 text-sm md:text-base text-[#313335] leading-relaxed"}
              "Based on first-year earnings, graduates of this pathway tend to earn "
              ($ :span {:class "font-bold" :style {:color band-color}} band)
              (str " a living wage in " area "."))
           ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-3"}
              (for [[i t] (map-indexed vector tiles)]
                ($ salary-tile (assoc t :key i))))))
      ;; ---- LWC occupation mode (no PSEO for this school) ----
      ;; Earnings chart degrades to a single occupation-wage bar vs. the living wage
      ;; (Build Plan §5.2), clearly labeled occupation-level.
      (let [o (:occupation s)
            stars (or (:stars o) 0)
            demand (demand-word stars)
            chart-data
            ;; short bar name (full occupation title is in the legend) to avoid x-axis overlap
            [{:name "Median Wage" :value (:median o) :label (usd (:median o)) :fill charts/color-earnings}
             {:name (str "Living Wage —\n" area "\n(Single Adult)")
              :value data/living-wage-area-amount :label (usd data/living-wage-area-amount) :fill charts/color-col}
             {:name (str "Living Wage —\n" data/home-state "\n(Single Adult)")
              :value data/living-wage-state-amount :label (usd data/living-wage-state-amount) :fill charts/color-col}]]
        ($ :div {:class "space-y-4"}
           ($ :div {:class "text-xs italic text-[#676868]"}
              (str "Occupation-level wage (Louisiana Workforce Commission) — program-level (PSEO) "
                   "earnings aren't published for this program, so this shows the MEDIAN statewide "
                   "wage for " (:soc-title o) " (" (:education o) ")."))
           ($ charts/salary-vs-col-chart
              {:title "Median Wage vs. Cost of Living"
               :legend-items [{:color charts/color-earnings :label (str (:soc-title o) " — median Louisiana wage")}
                              {:color charts/color-col :label "Living wage for a single adult"}]
               :y-max (int (* 1.15 (max (:median o) data/living-wage-area-amount)))
               :data chart-data})
           ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-4 text-sm md:text-base text-[#313335] leading-relaxed"}
              "Based on the median Louisiana wage for this occupation, workers tend to earn "
              ($ :span {:class "font-bold" :style {:color band-color}} band)
              (str " a living wage in " area "."))
           ($ :div {:class "grid grid-cols-1 md:grid-cols-2 gap-3"}
              ($ salary-tile {:key 0 :kind :stars :value stars :label "Job Demand (LWC)"
                              :detail (str "LWC rates this " stars " of 5 — " (.toLowerCase demand) " in " data/home-state ".")})
              ($ salary-tile {:key 1 :kind :stat :value (:growth-pct o) :label "10-Year Growth"
                              :detail (str "≈" (:net-new o) " net new jobs; " (:openings o) " total openings incl. replacement.")})))))))

;; ============================================================================
;; Section 4 — Career Paths (O*NET-linked + advanced-cred flags + PUMS)
;; ============================================================================

(defui career-row [{:keys [title soc requirement desc]}]
  ($ :div {:class "bg-[#f3fafb] rounded-xl shadow-sm ring-1 ring-[#cdeaee] p-4"}
     ($ :div {:class "flex flex-wrap items-center gap-2 mb-1"}
        ($ ext-link {:href (onet-url soc) :label title
                     :title (str "O*NET career profile — " title)
                     :class "text-base font-head"})
        (when requirement
          ($ :span {:class "inline-block rounded-full border border-[#e8a93b] bg-[#fff7e6] text-[#9a6a00] text-[10px] font-semibold px-2 py-0.5"}
             requirement)))
     ($ :p {:class "text-sm text-[#313335] leading-relaxed"} desc)))

(defui pums-subsection [{:keys [pums field]}]
  ($ :div {:class "bg-[#f2f3f4] rounded-xl p-4 space-y-3"}
     ($ :div
        ($ :div {:class "text-sm font-semibold text-[#2a6465] font-head"}
           "What degree-holders actually do")
        ($ :p {:class "text-xs text-[#676868]"}
           (str "Where " (:scope pums) " " (.toLowerCase (or field "")) "-degree holders actually work "
                "(field-of-degree data, not just this school).")))
     (when (:under-50-callout? pums)
       ($ :div {:class "rounded-lg bg-[#fff7e6] border border-[#e8a93b] p-3 text-sm text-[#9a6a00]"}
          "Heads up: fewer than half of graduates in this field work in a directly-related job — worth talking through what that means for your plan."))
     ($ :div {:class "space-y-2"}
        (for [[i o] (map-indexed vector (:occupations pums))]
          ($ :div {:key i :class "flex items-center justify-between gap-3 bg-white rounded-lg px-3 py-2 ring-1 ring-[#cdeaee]"}
             ($ :div {:class "min-w-0"}
                (if (:onet o)
                  ($ ext-link {:href (onet-url (:onet o)) :label (:title o)
                               :title (str "O*NET — " (:title o)) :class "text-sm"})
                  ($ :span {:class "font-semibold text-sm text-[#313335]"} (:title o)))
                (when (:note o)
                  ($ :div {:class "text-[11px] text-[#676868]"} (:note o))))
             ($ :div {:class "text-right shrink-0"}
                ($ :div {:class "text-sm font-bold text-[#2a6465] font-head"} (:pct o))
                ($ :div {:class "text-[11px] text-[#676868]"}
                   (str "~$" (.toLocaleString (:median o) "en-US") "/yr"))))))))

(defui careers-section [{:keys [pathway]}]
  (let [roles (:roles (:careers pathway))
        pums (:pums (:careers pathway))
        base (vec roles)
        head-roles (subvec base 0 (min 3 (count base)))
        tail-roles (when (> (count base) 3) (subvec base 3))]
    ($ :div {:class "space-y-4"}
       ($ read-more
          {:head ($ :div {:class "space-y-3"}
                    (for [[i r] (map-indexed vector head-roles)] ($ career-row (assoc r :key i))))
           :tail (when tail-roles
                   ($ :div {:class "space-y-3 mt-3"}
                      (for [[i r] (map-indexed vector tail-roles)] ($ career-row (assoc r :key (+ 100 i))))))
           :label-more "Show advanced-practice paths ▼"
           :label-less "Show fewer ▲"})
       ;; PUMS — bachelor's only (Build Plan §4 / spec). Field resolves the same way as the chip.
       (when (and (:bachelors? pathway) pums)
         ($ pums-subsection {:pums pums :field (or (:field pathway) (data/cip->field (:cip pathway)))})))))

;; ============================================================================
;; Section 5 — About This School (school-anchored; 3-figure callout)
;; ============================================================================

(defui callout-figure
  "indicator = optional {:label :color} pill (e.g. grad-rate norm High/Medium/Low)."
  [{:keys [value label sub indicator]}]
  ($ :div {:class "bg-white rounded-xl shadow-sm ring-1 ring-[#bcdfe5] p-4 text-center"}
     ($ :div {:class "text-2xl md:text-3xl font-bold text-[#2a6465] font-head"} value)
     ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mt-1"} label)
     (when indicator
       ($ :div {:class "mt-2 inline-block rounded-full px-2 py-0.5 text-[10px] font-bold"
                :style {:background-color (str (:color indicator) "1f")
                        :color (:color indicator)}}
          (:label indicator)))
     (when sub ($ :div {:class "text-xs text-[#676868] mt-1 leading-snug"} sub))))

(defui about-school-section
  "School-anchored 'About This School' — SIX hero stat boxes (Upgrade §5.2), no prose. Assembled
   from whatever the school carries, so a selective 4-year (Acceptance) and an open-admission
   2-year (Transfer-Out) share the code. Boxes: [Acceptance | Transfer-Out] · Graduation Rate
   (normed) · dynamic Student-Group Enrollment (§4.6) · Avg Debt at Graduation · First-Year
   Retention · Campus (setting + distance-when-relevant). The old prose bullets + 'Target' line
   are dropped (§5.2); the STR badge still lives on the school tile header."
  [{:keys [school student]}]
  (let [selective? (some? (:act-25 school))
        gr (:grad-rate school)
        gcolor (case (:indicator gr) "High" "#2f9e44" "Low" "#c92a4a" "#676868")
        to (:transfer-out school)
        total (.toLocaleString (:enrollment-total school) "en-US")
        race (:race-group student)
        grp (get (:enrollment-races school) race)
        debt (:avg-debt (:costs school))
        usd (fn [n] (str "$" (.toLocaleString n "en-US")))
        loc (str (:city school) ", " (:state school)
                 (when (:distance-relevant? school) (str " · " (:distance school))))
        figs (cond-> []
               ;; Box 1 — Acceptance (selective) OR Transfer-Out (open-admission)
               (and selective? (:acceptance school))
               (conj {:value (:acceptance school) :label "Acceptance Rate"})
               (and (not selective?) to)
               (conj {:value (:rate to) :label "Transfer-Out Rate" :sub (:sub to)})
               ;; Box 2 — Graduation rate + norm (LIVE integration: the norm pill +
               ;; peer sub only when the record carries norming — out-of-state
               ;; records ship an unnormed rate; present-by-data, no empty chrome)
               gr
               (conj (cond-> {:value (:rate gr) :label (:label gr)}
                       (:indicator gr)
                       (assoc :indicator {:label (:indicator gr) :color gcolor})
                       (and (:delta gr) (:normed-against gr))
                       (assoc :sub (str (:delta gr) " pts vs. " (:normed-against gr)
                                        (when (:peer-average gr)
                                          (str " (avg " (:peer-average gr) "%)"))))))
               ;; Box 3 — dynamic student-group enrollment (§4.6)
               true
               (conj (if (and (:minority? student) grp)
                       {:value (:pct grp) :label (str race " enrollment")
                        :sub (str (.toLocaleString (:count grp) "en-US") " of " total)}
                       {:value total :label "Total enrollment"}))
               ;; Box 4 — average debt at graduation (Scorecard GRAD_DEBT_MDN)
               debt
               (conj {:value (usd debt) :label "Avg Debt at Graduation" :sub "median, completers"})
               ;; Box 5 — first-year retention
               (:retention school)
               (conj {:value (:retention school) :label "First-Year Retention"})
               ;; Box 6 — campus setting + location (distance only when relevant)
               (:setting-type school)
               (conj {:value (:setting-type school) :label "Campus" :sub loc}))]
    ($ :div {:class "grid grid-cols-2 md:grid-cols-3 gap-3"}
       (for [[i f] (map-indexed vector figs)]
         ($ callout-figure (assoc f :key i))))))

;; ============================================================================
;; Section 6 — What It Costs You (school-anchored; recomputed waterfall)
;; ============================================================================

(defui costs-section
  "School-anchored 'What It Costs You' — INSTITUTION-AGNOSTIC waterfall. Reads generic
   cost-of-attendance keys (:tuition-fees, OPTIONAL :living/:living-label, :books, :coa,
   :tops/:tops-name, :pell, :net, :aid-covered, optional :avg-debt, :commuter?,
   :fully-covered?). A residential school includes :living (on-campus room & board — a real
   charge); a COMMUTER school OMITS :living so the COA shows school-charged DIRECT costs only
   (tuition, fees, books). When aid ≥ COA the net is $0 and the copy says so. Axis scales to COA."
  [{:keys [school]}]
  (let [c (:costs school)
        usd (fn [n] (str "$" (.toLocaleString n "en-US")))
        living (or (:living c) (:room-board-on c))   ;; nil for commuter schools (no living in COA)
        living-label (or (:living-label c) "Room & board")
        coa (:coa c)
        after-tops (max 0 (- coa (:tops c)))
        net (:net c)
        y-max (js/Math.ceil (* 1.12 coa))
        direct (+ (:tuition-fees c) (:books c))   ;; tuition+fees+books = school-charged direct cost
        fully-covered? (or (:fully-covered? c) (<= net 0))
        cost-words (if living
                     (if (:commuter? c) "tuition, fees, books, and living costs" "tuition, fees, housing, and books")
                     "tuition, fees, and books")]
    ($ :div {:class "space-y-4"}
       ($ charts/financial-aid-waterfall
          {:title "Your Estimated Annual Cost — After Financial Aid"
           :legend-items [{:color charts/color-earnings
                           :label (str "Tuition & fees " (usd (:tuition-fees c))
                                       (when living (str " + " living-label " " (usd living)))
                                       ;; LIVE integration: books line only when carried
                                       (when (:books c) (str " + Books " (usd (:books c)))))}
                          {:color charts/color-net
                           :label (if (pos? (or (:tops c) 0))
                                    (str "Your estimated net cost after " (:tops-name c) " + Pell")
                                    "Your estimated net cost after Pell")}]
           :y-max y-max
           ;; LIVE integration: the "After TOPS" step only when TOPS actually reduces
           ;; the cost (out-of-state schools carry :tops 0 — no dead identical bar).
           :data (cond-> [{:name (if living "Total Cost\nof Attendance" "Tuition, Fees\n& Books")
                           :value coa :label (usd coa) :fill charts/color-earnings}]
                   (pos? (or (:tops c) 0))
                   (conj {:name (str "After\n" (:tops-name c)) :value after-tops
                          :label (usd after-tops) :fill charts/color-earnings})
                   :always
                   (conj {:name "Your Net Cost\n(after Pell)" :value net
                          :label (usd net) :fill charts/color-net}))})
       ;; §5.3 — numeric detail is folded into the graph; keep only ACTION bullets. The
       ;; "live at home" move only applies to a school commutable from the student's home
       ;; (§5.3 fix): omit it for a school outside the home metro (e.g. SLU in Hammond).
       ($ bullet-list
          {:bullets
           [(if (:commutable? school)
              "Lower your net cost: live at home instead of on campus, and apply for scholarships (see the Scholarships tab)."
              "Lower your net cost: apply for scholarships (see the Scholarships tab), and weigh on-campus vs. off-campus housing.")
            "Loans can cover what's left — but they're borrowed money you repay with interest, a deferred cost, not free aid."]})
       ($ :div {:class "rounded-xl bg-[#fff7e6] border border-[#e8a93b] p-4"}
          ($ :p {:class "text-sm text-[#9a6a00] leading-relaxed"}
             ($ :span {:class "font-bold"} "This is an estimate only. ")
             "You must complete the "
             ($ ext-link {:href "https://studentaid.gov" :label "FAFSA"
                          :title "studentaid.gov — complete the FAFSA"})
             " and receive your award letter to know your true cost."))
       ;; Net-price + financial-aid links (§4.7, HD2024). Both scheme-normalized so they open.
       ($ :div {:class "flex flex-col gap-1"}
          ($ ext-link {:href (:npc-url c) :label (str "Estimate your net cost — " (:short-name school) "'s Net Price Calculator →")
                       :title (str (:short-name school) " — Net Price Calculator")})
          (when (:faid-url c)
            ($ ext-link {:href (:faid-url c) :label (str "Explore aid — " (:short-name school) "'s Financial Aid office →")
                         :title (str (:short-name school) " — Financial Aid")}))))))

;; ============================================================================
;; v3 per-type section renderers (CTE)
;; ============================================================================

(defui time-to-credential-section [{:keys [pathway]}]
  ;; §8 reframe: the INTENDED full-time length + the universal on-time actions (§6.4). No
  ;; misleading average-completion figure, no annual-graduates count. Standardized across all
  ;; program types (Degree, AAS, certificate/diploma, transfer associate, apprenticeship).
  (let [t (:time-to-credential pathway)]
    ($ :div {:class "space-y-4"}
       (when (:intended t)
         ($ :div {:class "bg-[#f3fafb] rounded-xl shadow-sm ring-1 ring-[#cdeaee] p-4 text-center"}
            ($ :div {:class "text-2xl font-bold text-[#2a6465] font-head"} (:intended t))
            ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mt-1"}
               "Intended length (full-time)")))
       ($ :div {:class "space-y-2"}
          ($ :div {:class "text-sm font-semibold text-[#2a6465] font-head"} "Finish on time — the moves that matter")
          ($ bullet-list {:bullets data/on-time-actions})))))

(defui rules-section [{:keys [pathway]}]
  ;; §6: plain-language "Terminal" definition (§6.1) OR transfer-risk warning (§6.2), by flag,
  ;; then the rules-of-the-game (§6.3) for this pathway's :rules-type. Institution-agnostic.
  (let [rules (get data/rules-of-game (:rules-type pathway))]
    ($ :div {:class "space-y-4"}
       (when (:terminal? pathway)
         ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-4 text-sm text-[#313335] leading-relaxed"}
            ($ :span {:class "font-semibold text-[#2a6465]"} "What \"terminal\" means: ") data/terminal-def))
       (when (:transfer? pathway)
         ($ :div {:class "rounded-xl bg-[#fff7e6] border border-[#e8a93b] p-4 text-sm text-[#9a6a00] leading-relaxed"}
            data/transfer-risk))
       (when (seq rules)
         ($ bullet-list {:bullets rules})))))

(defui transfer-plan-section [{:keys [pathway]}]
  (let [tp (:transfer-plan pathway)
        o (:outcomes tp)]
    ($ :div {:class "space-y-4"}
       ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-4"}
          ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mb-1"} "Transfer destination")
          ($ :div {:class "text-base font-semibold text-[#2a6465] font-head"} (:destination tp))
          ($ :p {:class "text-sm text-[#313335] mt-1 leading-relaxed"} (:framing tp)))
       (when (:la-guarantee tp)
         ($ :div {:class "rounded-xl bg-[#f3fafb] ring-1 ring-[#cdeaee] p-3 text-sm text-[#313335] leading-relaxed"}
            ($ :span {:class "font-semibold text-[#2a6465]"} "Louisiana guarantee: ") (:la-guarantee tp)))
       ;; Transfer→bachelor's outcomes (national; sourced & annotated)
       (when o
         ($ :div {:class "space-y-2"}
            ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868]"}
               "How transfer students do (national)")
            ($ :div {:class "grid grid-cols-3 gap-2"}
               (for [[i [v note]] (map-indexed vector [[(:transfer-rate o) (:transfer-rate-note o)]
                                                       [(:transfer-bachelors o) (:transfer-bachelors-note o)]
                                                       [(:overall o) (:overall-note o)]])]
                 ($ :div {:key i :class "bg-[#f3fafb] rounded-xl shadow-sm ring-1 ring-[#cdeaee] p-3 text-center"}
                    ($ :div {:class "text-xl font-bold text-[#2a6465] font-head"} v)
                    ($ :div {:class "text-[11px] text-[#676868] mt-1 leading-snug"} note))))
            ($ :p {:class "text-[11px] italic text-[#676868] leading-relaxed"} (:source o))))
       ($ bullet-list {:bullets [(:cost-note tp) (:articulation-note tp)]})
       (when (seq (:destination-careers tp))
         ($ :div {:class "space-y-2"}
            ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868]"}
               "Where it leads (at the bachelor's)")
            (for [[i r] (map-indexed vector (:destination-careers tp))]
              ($ career-row (assoc r :key i))))))))

(defui funding-section [{:keys [pathway]}]
  (let [f (:funding pathway)
        usd (fn [n] (str "$" (.toLocaleString n "en-US")))]
    ($ :div {:class "space-y-3"}
       ($ bullet-list
          {:bullets
           [(str "Tuition & fees: about " (usd (:tuition f)) "/year (" (:tuition-flag f) ").")
            (str "Pell Grant: up to " (usd (:pell f)) "/year for eligible students.")
            (str (:tops f) (when (:commuter? f) "; commuter — no room & board in this estimate."))]})
       ($ :div {:class "rounded-xl bg-[#fff7e6] border border-[#e8a93b] p-4"}
          ($ :p {:class "text-sm text-[#9a6a00] leading-relaxed"}
             ($ :span {:class "font-bold"} "Estimate only. ")
             "Complete the "
             ($ ext-link {:href "https://studentaid.gov" :label "FAFSA"
                          :title "studentaid.gov — complete the FAFSA"})
             " to confirm your aid.")))))

(defui earn-section [{:keys [pathway]}]
  (let [w (:wage-progression pathway)]
    ($ :div {:class "space-y-3"}
       ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-4 text-sm md:text-base text-[#313335] leading-relaxed"}
          ($ :span {:class "font-semibold text-[#2a6465]"} "Earn while you learn. ")
          "You're paid as an apprentice from day one — no tuition debt.")
       (when w
         ($ :div {:class "grid grid-cols-2 gap-3"}
            ($ :div {:class "bg-[#f3fafb] rounded-xl shadow-sm ring-1 ring-[#cdeaee] p-4 text-center"}
               ($ :div {:class "text-2xl font-bold text-[#2a6465] font-head"} (:start w))
               ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mt-1"} "Starting wage"))
            ($ :div {:class "bg-[#f3fafb] rounded-xl shadow-sm ring-1 ring-[#cdeaee] p-4 text-center"}
               ($ :div {:class "text-2xl font-bold text-[#2a6465] font-head"} (:average w))
               ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mt-1"} "Average wage"))))
       ($ :p {:class "text-xs text-[#676868] leading-relaxed"}
          "Program length and the journey credential depend on the sponsor (your advisor can confirm via DOL Apprenticeship.gov)."))))

;; ============================================================================
;; Per-pathway accordion (one-at-a-time) + school-anchored sections
;; ============================================================================

(defui section-item [{:keys [value heading children tint?]}]
  ;; tint? = soft teal wash for SCHOOL-anchored sections, to set them apart from
  ;; the white program/pathway sections.
  ($ accordion/AccordionItem
     {:value value
      :class (str "rounded-xl shadow-sm ring-1 border-0 px-4 "
                  (if tint? "bg-[#dceef1] ring-[#a9d8de]" "bg-white ring-[#bcdfe5]"))}
     ($ accordion/AccordionTrigger {:class "hover:no-underline py-4"}
        ($ :span {:class "text-base font-semibold text-[#2a6465] text-left font-head"} heading))
     ;; px-1.5 so inner box rings/shadows clear AccordionContent's overflow-hidden
     ;; (shadcn applies overflow-hidden for the open/close animation).
     ($ accordion/AccordionContent {:class "pb-5 pt-1 px-1.5"} children)))

(def pathway-section-meta
  {:overview           {:heading "Overview"                      :render overview-section}
   :whats-cool         {:heading "What's Cool About This Pathway" :render whats-cool-section}
   :salary             {:heading "Salary & Job Opportunities"    :render salary-section}
   :careers            {:heading "Career Paths"                  :render careers-section}
   :time-to-credential {:heading "Time & Completion"            :render time-to-credential-section}
   :transfer-plan      {:heading "Transfer Plan"                 :render transfer-plan-section}
   :rules              {:heading "Making It Pay Off"            :render rules-section}
   :funding            {:heading "Cost & Funding"               :render funding-section}
   :earn               {:heading "Earn While You Learn"         :render earn-section}})

(defui pathway-sections
  "The pathway-specific sections (1–4) as a one-at-a-time accordion, Build Plan §3 order.
   school/student are threaded through so school-aware sections (e.g. salary copy) can
   self-assemble their strings rather than hardcode them."
  [{:keys [pathway school student]}]
  ;; Per-pathway :sections drives which sections show (CTE programs differ from the
  ;; 4-year); falls back to the global section-order so the existing SLU BSN is unchanged.
  ;; §5.1 — sub-sections start COLLAPSED (snapshot view); no auto-unfurled Overview.
  ($ accordion/Accordion {:type "single" :collapsible true :class "space-y-3"}
     (for [k (or (:sections pathway) data/section-order)
           :let [{:keys [heading render]} (pathway-section-meta k)]]
       ($ section-item {:key (name k) :value (name k) :heading heading}
          ($ render {:pathway pathway :school school :student student})))))

(defn pathway-chips
  "Chips DERIVED per-pathway from sourced fields, so ANY recommended program produces them:
     • field        — data/cip->field on the program CIP (4-digit series → 2-digit family);
                      a program may override with :field (e.g. a catalog name).
     • credential   — :credential-level (award_level_name — controlled vocabulary)
     • demand       — CONDITIONAL on the primary SOC's LWC star rating (≥4 High · 3 Steady)
   The field chip is now DETERMINISTIC from the CIP, not a hand-shortened title."
  [{:keys [field credential-level lwc-stars cip]}]
  (->> [(or field (data/cip->field cip))
        credential-level
        (cond
          (>= (or lwc-stars 0) 4) "High demand"
          (= lwc-stars 3)         "Steady demand"
          :else                   nil)]
       (remove nil?)))

(defui pathway-row
  "A recommended pathway that unfurls its own details when clicked (v1 model).
   Multiple pathways can sit under one school; each unfurls independently, while
   About This School + What It Costs You are anchored once to the school below."
  [{:keys [pathway school student default-open?]}]
  (let [[open? set-open!] (use-state (boolean default-open?))]
    ($ :div {:class "bg-white rounded-xl shadow-sm ring-1 ring-[#bcdfe5] overflow-hidden"}
       ($ :button {:class "w-full flex items-center justify-between gap-3 p-4 text-left hover:bg-[#f3fafb] transition-colors"
                   :on-click #(set-open! not)}
          ($ :div {:class "min-w-0"}
             ($ :div {:class "font-semibold text-[#2a6465] font-head"}
                (str (:name pathway) (when (:acronym pathway) (str " (" (:acronym pathway) ")"))))
             ($ :div {:class "text-sm text-[#676868] mt-0.5"} (:track pathway))
             ;; Classification tags (§3, amber) + derived chips (field · credential · demand, teal).
             ($ :div {:class "flex flex-wrap gap-2 mt-2"}
                (for [t (:tags pathway)]
                  ($ :span {:key t :class "rounded-full bg-[#fff7e6] text-[#9a6a00] ring-1 ring-[#e8a93b] text-xs font-semibold px-2.5 py-0.5"} t))
                (for [t (pathway-chips pathway)]
                  ($ :span {:key t :class "rounded-full bg-[#d0ecef] text-[#007f81] text-xs font-medium px-2.5 py-0.5"} t))))
          ($ :span {:class "shrink-0 text-sm font-semibold text-[#007f81]"}
             (if open? "Hide ▲" "View pathway ▼")))
       (when open?
         ($ :div {:class "px-3 md:px-4 pb-4 pt-2 bg-[#f2f3f4]"}
            ($ pathway-sections {:pathway pathway :school school :student student}))))))

(defui school-sections
  "School-anchored sections — rendered ONCE per school. Every school (4-year or 2-year)
   goes through the SAME data-driven About + Cost components; what shows is driven by the
   fields the school record carries, not by school type. A 2-year that lacks a field (e.g.
   acceptance rate for an open-admission college) simply omits that figure."
  [{:keys [school student]}]
  ;; §5.1 — About This School + What It Costs You start COLLAPSED (no auto-unfurl).
  ($ accordion/Accordion {:type "multiple" :class "space-y-3"}
     ($ section-item {:value "about-school" :heading "About This School" :tint? true}
        ($ about-school-section {:school school :student student}))
     ($ section-item {:value "costs" :heading "What It Costs You" :tint? true}
        ($ costs-section {:school school}))))

(defui school-expanded
  "What unfurls under a school tile: the recommended pathways FOR THE CURRENT CATEGORY (each
   unfurls its own sections) followed by the school-anchored About This School + What It Costs You."
  [{:keys [school pathways student]}]
  ($ :div {:class "space-y-5"}
     ($ :div {:class "space-y-3"}
        ($ section-eyebrow {:label (str (count pathways) " Recommended Pathway"
                                        (when (> (count pathways) 1) "s"))})
        ;; Pathways start COLLAPSED — the student clicks one to unfurl its details (§5.1).
        (for [p pathways]
          ($ pathway-row {:key (:id p) :pathway p :school school :student student :default-open? false})))
     ($ school-sections {:school school :student student})))

;; ============================================================================
;; School tile (Pathway Recommendations) — Learn More expander
;; ============================================================================

(defui school-tile [{:keys [school pathways student]}]
  (let [[open? set-open!] (use-state false)
        ;; LIVE integration: a carried engine classification wins (the engine's own
        ;; Safety/Target/Reach banding); classify-str stays the demo fallback.
        cls (or (:classification school)
                (data/classify-str (:act student) (:act-25 school) (:act-75 school)))]
    ($ card/Card {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] border-0 overflow-hidden"}
       ($ :div {:class "p-5 space-y-3"}
          ;; Logo + Safety/Target/Reach badge — vertically centered together.
          ($ :div {:class "flex items-center justify-between gap-4"}
             ;; LIVE integration: uniform header for every school — a fixed square
             ;; mark (curated local asset, else domain favicon) beside the name.
             ;; Wordmark-instead-of-name rendering made tiles inconsistent.
             ($ :div {:class "flex items-center gap-3 min-w-0"}
                (when-let [m (or (:logo school) (favicon-url (:website school)))]
                  ($ :img {:src m :alt "" :class "w-10 h-10 shrink-0 object-contain"}))
                ($ :h3 {:class "text-lg font-semibold text-[#2a6465] font-head"} (:name school)))
             ;; Selective schools show Safety/Target/Reach; open-admission schools
             ;; (no ACT — e.g. 2-year/CTE) show a neutral "Open Admission" chip.
             ($ str-badge {:classification cls}))
          ($ :div {:class "flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-[#676868]"}
             ($ :span (str (:city school) ", " (:state school)))
             ($ :span "·") ($ :span (:type school))
             ;; LIVE integration: engine records can lack a website — drop the
             ;; separator + link entirely instead of a dangling "·".
             (when (seq (:website school))
               ($ :<>
                  ($ :span "·")
                  ($ ext-link {:href (:website school) :label (:website-label school)
                               :title (:website school) :class "text-sm"}))))
          ;; School-level chips removed — descriptors are per-pathway now (see pathway-row),
          ;; since programs at one school vary in field, credential, and demand.
          ($ :button {:class "mt-1 w-full rounded-xl bg-[#05a09c] hover:bg-[#007f81] text-white font-semibold py-2.5 transition-colors"
                      :on-click #(set-open! not)}
             (if open? "Show Less ▲" "Learn More ▼")))
       (when open?
         ($ :div {:class "px-3 md:px-5 pb-5 pt-1 bg-[#f2f3f4]"}
            ($ :div {:class "pt-4"}
               ($ school-expanded {:school school :pathways pathways :student student})))))))

;; ============================================================================
;; Left column — Advisor Messages
;; ============================================================================

(defui advisor-card [{:keys [advisor]}]
  ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-5"}
     ($ :div {:class "flex items-center gap-4"}
        ($ :img {:src (:headshot advisor)
                 :alt (:name advisor)
                 :class "w-16 h-16 rounded-full object-cover ring-2 ring-[#d0ecef]"})
        ($ :div
           ($ :div {:class "font-semibold text-[#2a6465] font-head"} (:name advisor))
           ($ :div {:class "text-sm text-[#676868]"} (:title advisor))))
     ($ :div {:class "mt-4 space-y-1.5 text-sm"}
        ($ :div "Email me: "
           ($ ext-link {:href (str "mailto:" (:email advisor)) :label (:email advisor)
                        :title (str "Email " (:name advisor))}))
        ;; LIVE integration: present-by-data — no dangling link when the staff
        ;; record has no scheduling URL yet (populates once acquired).
        (when (seq (:appointment-url advisor))
          ($ :div
             ($ ext-link {:href (:appointment-url advisor) :label "Schedule an appointment →"
                          :title "Book time with me"}))))))

(defui advisor-messages [{:keys [advisor]}]
  ($ :div {:class "space-y-4"}
     ($ section-eyebrow {:label (:messages-label advisor)})
     ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-5 border-l-4 border-l-[#05a09c]"}
        ($ :p {:class "text-sm md:text-base text-[#313335] leading-relaxed"} (:note advisor)))
     ($ advisor-card {:advisor advisor})))

;; ============================================================================
;; Right column — "Check these out:"
;; ============================================================================

(defui str-legend []
  ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-4"}
     ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mb-2"}
        "Safety · Target · Reach")
     ($ :div {:class "space-y-1.5"}
        (for [{:keys [key label def color]} data/str-legend]
          ($ :div {:key key :class "flex items-start gap-2 text-sm"}
             ($ :span {:class "mt-1.5 w-2.5 h-2.5 rounded-full shrink-0" :style {:background-color color}})
             ($ :span ($ :span {:class "font-semibold text-[#313335]"} (str label ": ")) def))))))

(defui terms-to-know []
  ($ :div {:class "bg-[#d0ecef]/40 rounded-2xl p-4"}
     ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#2a6465] mb-2 font-head"}
        "Terms to Know")
     ($ :dl {:class "space-y-2"}
        (for [{:keys [term def]} data/terms-to-know]
          ($ :div {:key term :class "text-sm"}
             ($ :dt {:class "font-semibold text-[#313335] inline"} (str term ": "))
             ($ :dd {:class "inline text-[#676868]"} def))))))

;; --- Standalone program card (provider-only, e.g. an apprenticeship at a JATC) — reuses the
;;     same section system as a school pathway, with the provider shown, but no About/Cost. ---
(defui standalone-card [{:keys [program student default-open?]}]
  (let [[open? set-open!] (use-state (boolean default-open?))]
    ($ :div {:class "bg-white rounded-xl shadow-sm ring-1 ring-[#bcdfe5] overflow-hidden"}
       ($ :button {:class "w-full flex items-center justify-between gap-3 p-4 text-left hover:bg-[#f3fafb] transition-colors"
                   :on-click #(set-open! not)}
          ($ :div {:class "min-w-0"}
             ($ :div {:class "font-semibold text-[#2a6465] font-head"}
                (str (:name program) (when (:acronym program) (str " (" (:acronym program) ")"))))
             ($ :div {:class "text-sm text-[#676868] mt-0.5"}
                (str (:provider program) " · " (:credential-level program)))
             ($ :div {:class "flex flex-wrap gap-2 mt-2"}
                (for [t (:tags program)]
                  ($ :span {:key t :class "rounded-full bg-[#fff7e6] text-[#9a6a00] ring-1 ring-[#e8a93b] text-xs font-semibold px-2.5 py-0.5"} t))
                (for [t (pathway-chips program)]
                  ($ :span {:key t :class "rounded-full bg-[#d0ecef] text-[#007f81] text-xs font-medium px-2.5 py-0.5"} t))))
          ($ :span {:class "shrink-0 text-sm font-semibold text-[#007f81]"}
             (if open? "Hide ▲" "View ▼")))
       (when open?
         ($ :div {:class "px-3 md:px-4 pb-4 pt-2 bg-[#f2f3f4]"}
            ($ pathway-sections {:pathway program :school nil :student student}))))))

;; --- Scholarship card — EXPANDABLE like program cards (§5.5); advisor-verified (§9). ---
(defui scholarship-card [{:keys [s]}]
  (let [[open? set-open!] (use-state false)]
    ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#bcdfe5] overflow-hidden"}
       ($ :button {:class "w-full text-left p-5 hover:bg-[#f3fafb] transition-colors"
                   :on-click #(set-open! not)}
          ($ :div {:class "flex items-start justify-between gap-3"}
             ($ :div {:class "min-w-0"}
                ($ :div {:class "font-semibold text-[#2a6465] font-head text-base"} (:name s))
                ($ :div {:class "text-sm text-[#676868]"} (:sponsor s)))
             ($ :div {:class "text-right shrink-0"}
                ($ :div {:class "text-xl font-bold text-[#05a09c] font-head"} (:award s))
                ($ :div {:class "text-xs text-[#676868]"} (str "Due " (:deadline s)))))
          ;; LIVE integration: Advisor-verified pill removed (Tavidee 2026-08-28) —
          ;; engine-sourced scholarships are not individually advisor-verified.
          ($ :div {:class "mt-2 flex items-center justify-end gap-2"}
             ($ :span {:class "text-sm font-semibold text-[#007f81]"} (if open? "Hide ▲" "Details ▼"))))
       (when open?
         ;; space-y-5 separates the blocks (why-fits · eligibility · tips · apply) more than the
         ;; 12px between bullets within a list, so the two bullet groups read as distinct sections.
         ($ :div {:class "px-5 pb-5 space-y-5"}
            ($ :div {:class "rounded-xl bg-[#d0ecef]/50 p-3 text-sm text-[#313335] leading-relaxed"}
               ($ :span {:class "font-semibold text-[#2a6465]"} "Why it fits: ") (:why-fits s))
            ($ bullet-list {:bullets [(str "Eligibility: " (:eligibility s))
                                      (str "Open to: " (:target-levels s) " students")
                                      (:selection s)]})
            ($ :div {:class "space-y-2"}
               ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868]"} "Application tips")
               ($ bullet-list {:bullets (:tips s)}))
            ($ ext-link {:href (:url s) :label "Apply / learn more →" :title (:name s)}))))))

;; --- Tabs ---
(defui tab-button [{:keys [label active? on-click]}]
  ($ :button {:class (str "flex-1 px-3 py-2 text-sm font-semibold rounded-lg transition-colors "
                          (if active? "bg-[#05a09c] text-white" "text-[#2a6465] hover:bg-[#d0ecef]/60"))
              :on-click on-click}
     label))

(defui category-tab
  "A Degree or Career-Technical tab (Upgrade §2). Shows the school tiles whose pathways fall in
   this category (school-anchored About/Cost per school), then any standalone-provider programs.
   A school appears in a tab only if it has ≥1 pathway there — so BRCC shows in both tabs (with
   different pathways each) and its About/Cost renders in each."
  [{:keys [category student]}]
  (let [school-groups (for [s data/schools
                            :let [ps (filter #(= category (:category %)) (get data/school-programs (:unitid s)))]
                            :when (seq ps)]
                        {:school s :pathways ps})
        standalones (filter #(= category (:category %)) data/standalone-programs)]
    ($ :div {:class "space-y-4"}
       ($ str-legend)
       ($ :div {:class "space-y-3"}
          ($ section-eyebrow {:label "Pathway Recommendations"})
          (for [{:keys [school pathways]} school-groups]
            ($ school-tile {:key (:unitid school) :school school :pathways pathways :student student}))
          (for [p standalones]
            ($ standalone-card {:key (:id p) :program p :student student})))
       ($ terms-to-know))))

(defui scholarships-tab []
  ($ :div {:class "space-y-3"}
     ($ section-eyebrow {:label "Scholarships"})
     ($ :p {:class "text-xs text-[#676868] leading-relaxed"}
        "Scholarships are advisor-verified and change often — your advisor keeps this list current and can add their own.")
     (for [s data/scholarship-list]
       ($ scholarship-card {:key (:id s) :s s}))))

(defui recommendations-column [{:keys [student]}]
  (let [[tab set-tab!] (use-state :degree)]
    ($ :div {:class "space-y-4"}
       ($ :h2 {:class "text-xl font-semibold text-[#2a6465] font-head"} "Check these out:")
       ($ :div {:class "flex gap-1 bg-white rounded-xl ring-1 ring-[#d0ecef] p-1"}
          (for [{:keys [key label]} data/categories]
            ($ tab-button {:key (name key) :label label :active? (= tab key) :on-click #(set-tab! key)})))
       (if (= tab :scholarships)
         ($ scholarships-tab)
         ($ category-tab {:category tab :student student})))))

;; ============================================================================
;; Page
;; ============================================================================

(defui rec-demo-page [_props]
  (let [s data/student]
    ($ :div {:class "min-h-screen bg-[#f2f3f4]"}
       ;; Header bar — dark teal, BRYC white-circle logo + first-name title
       ($ :div {:class "bg-[#2a6465] shadow-sm px-6 md:px-8 py-4"}
          ($ :div {:class "max-w-6xl mx-auto flex items-center gap-3"}
             ($ :img {:src "assets/bryc-logo-white.png" :alt "BRYC"
                      :class "w-10 h-10 rounded-full"})
             ($ :h1 {:class "text-lg md:text-2xl font-bold text-white font-head"}
                (str (:first-name s) "'s Personalized Recommendations"))))

       ;; Two-column body
       ($ :div {:class "max-w-6xl mx-auto px-4 md:px-8 py-8"}
          ($ :div {:class "grid grid-cols-1 lg:grid-cols-12 gap-6 items-start"}
             ($ :div {:class "lg:col-span-4 lg:sticky lg:top-6"}
                ($ advisor-messages {:advisor data/advisor}))
             ($ :div {:class "lg:col-span-8"}
                ($ recommendations-column {:student s})))))))
