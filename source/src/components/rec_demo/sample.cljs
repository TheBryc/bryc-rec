(ns components.rec-demo.sample
  "A single REAL engine institution record, lifted verbatim (school-anchored subset)
   from a saved pool event (pool_event_0014.edn → Nicholls State University, a
   'Target' admit). Used by the UI-1 dev harness AND the adapter tests so the render
   and the tests exercise the SAME real shape.

   Only the school-anchored keys UI-1 consumes are kept (About-This-School + Costs),
   plus a few LEGACY cruft keys (:why-fits-bullets/:financial-bullets/:cost-summary/
   :admissions-data) retained on purpose so tests can prove the adapter DROPS them.")

(def real-institution
  {:institution-name "Nicholls State University"
   :name "Nicholls State University"
   :sections [:about-school :costs]

   ;; --- CARD-HEADER data (UI-3): the real institution-summary header keys.
   ;; :location is the {:city :state} map (engine `institution-summary`), :sectors
   ;; the sector vector, :website the school site. Present so school->card-props
   ;; resolves a real header (city/state/type/website + STR badge). ---
   :location {:city "Thibodaux" :state "LA"}
   :sectors ["Public, 4-year or above"]
   :website "https://www.nicholls.edu"
   :website-label "nicholls.edu"

   ;; --- About-This-School TILE data (top-level on the institution) ---
   :grad-rate {:rate "30%" :rate-num 29.8
               :label "4-Year Graduation Rate"
               :normed-against "similar-size LA public 4-years"
               :peer-average "23.0" :peer-n 10 :delta "+6.8" :indicator "High"
               :method (str "IPEDS gr2024 bachelor's completers within 100% of normal time ÷ adjusted "
                            "cohort (UNITID 159966 = 29.8%). Peer avg = mean 4-yr rate across LA public "
                            "4-years in the same UG-enrollment band (n=10). Indicator: High ≥ +5 pts · "
                            "Low ≤ −5 · Medium otherwise.")}
   :str-badge {:key "target" :label "Target"}
   :ability-position "your ACT 19 is at their 25th (19), below the 75th (24)"
   :retention "77%"
   :enrollment-total 5518
   :enrollment-group-label "Black/African American"
   :enrollment-group-count 985
   :enrollment-group-pct "17.9%"

   ;; --- OUR grounded narrative (render THESE, not inline-computed bullets) ---
   :about-school-narrative
   ["Target admit: this school lists you as a Target for admission."
    (str "Your ACT 19 sits at their 25th percentile (19) and below their 75th (24), which "
         "aligns with your described readiness for hands-on, short programs.")
    (str "Seventy-seven percent (77%) of first-year students return for year two, which "
         "suggests many students stick around long enough to complete the next step in a program.")]

   :costs {:tuition-fees 8741 :books 1300 :living 10938 :living-label "Room & board"
           :coa 20979 :pell 7395 :aid-covered 7395 :net 13584 :tops 0 :tops-name "TOPS Opportunity"
           :disclaimer (str "This is an estimate only. You must complete the FAFSA and receive your "
                            "financial-aid award letter to know your true cost.")
           :fafsa-url "https://studentaid.gov"
           :npc-url "https://www.nicholls.edu/net-price-calculator/"}
   :costs-narrative
   ["The school’s published cost of attendance is $20,979 for the year."
    "That total includes $8,741 for tuition and fees, $1,300 for books, and $10,938 for Room & board."
    "A Pell Grant of $7,395 would reduce your out-of-pocket cost to about $13,584."]

   ;; --- LEGACY cruft (kept so the adapter can be proven to DROP it) ---
   :why-fits-bullets ["Your ACT score of 19 matches Nicholls State’s 25th percentile (19)…"]
   :financial-bullets ["After your Pell Grant, your net tuition ranges from $1,836.75-$3,061.25 per year…"]
   :cost-summary {:min-annual 0.0 :max-annual 0.0 :avg-annual 0.0}
   :admissions-data {:act-25th 19.0 :act-75th 24.0 :acceptance-rate 0.9842726081258192}
   :source :ai})

;; ---------------------------------------------------------------------------
;; UI-2 — a single REAL engine PROGRAM record, lifted verbatim (program-anchored
;; subset) from the SAME saved pool event (pool_event_0014.edn → Northwestern
;; State University, "Business Administration and Management, General.", a
;; bachelor's PSEO program). Used by the UI-2 harness AND the adapter tests so the
;; render and the tests exercise the SAME real program shape.
;;
;; Note the shape deltas from the demo data (proven by the adapter/component seam):
;;   • roles carry :onet-link/:median/:education/:advanced-credential? and NO :desc
;;   • pums occupations carry :onet-link (not :onet) + :soc, and some carry NO :title
;;   • demand :stars live INSIDE :salary (not :lwc-stars on the program)
;; Plus LEGACY cruft top-level keys (:personalized-overview/:outcome-bullet/
;; :career-summary/:best-for/:key-differentiators) retained on purpose so the
;; adapter can be proven to DROP them.
;; ---------------------------------------------------------------------------

(def real-program
  {:program-title "Business Administration and Management, General."
   :award-level-name "Bachelor's degree"
   :institution-name "Northwestern State University of Louisiana"
   :name "Business Administration and Management, General."
   :sections [:salary :careers :time-to-credential]

   ;; --- OUR narrative Overview map (DROPS INTO overview-section) ---
   :overview
   {:credential-line
    (str "The bachelor's program in business administration and management at Northwestern State "
         "University of Louisiana is designed to prepare graduates for leadership and operational "
         "roles in business, such as Chief Executives and Industrial Production Managers.")
    :student-connection
    (str "Because you want to master welding and eventually start your own business, this program "
         "could give you management and leadership skills that would be useful if you run a welding "
         "or construction business.")
    :caveat
    (str "A heads-up: this is a bachelor's-level, academic management program rather than a short, "
         "hands-on welding or trade credential, so it may not match your preference for a one- or "
         "two-year program focused on immediate technical training.")}

   ;; --- Salary: deterministic FIGURES (chart + demand) + OUR narrative ---
   :salary
   {:mode :pseo
    :earnings [{:label "1 Year Out"  :value 38358}
               {:label "5 Years Out" :value 57404}
               {:label "10 Years Out" :value 68340}]
    :living-wage-area-value 45496
    :living-wage-state-value 42370
    :living-wage-band "Below"
    :stars 4                          ;; ← demand stars live INSIDE :salary
    :growth-rate "-7.12%"
    :growth-net-new "-43"             ;; ← 0053 net-new jobs (growth_10yr)
    :growth-openings "339"
    :citation "PSEO 2025Q4 — CIP 52.0201"
    :demand {:soc "11-1011" :soc-title "Chief Executives" :median 158179
             :stars 4 :growth-pct "-7.12%" :net-new "-43" :openings "339" :education "Bachelor's degree"}
    :descriptor "Graduates from this pathway are reported as earning below the living-wage band."   ;; ← OUR narrative
    ;; :safe-bet is LEGACY (0053): the Salary bottom line is now a DETERMINISTIC tier
    ;; badge derived from :stars + :living-wage-band — the adapter no longer reads it.
    :safe-bet "Chief Executives — there is strong local demand for this occupation."}

   ;; --- Careers: deterministic FIGURES (roles + pums) + OUR narrative ---
   :careers
   {:roles
    [{:soc "11-1011" :title "Chief Executives"
      :onet-link "https://www.onetonline.org/link/summary/11-1011.00"
      :advanced-credential? false :median 158179 :education "Bachelor's degree"}
     {:soc "11-3051" :title "Industrial Production Managers"
      :onet-link "https://www.onetonline.org/link/summary/11-3051.00"
      :advanced-credential? false :median 129000 :education "Bachelor's degree"}
     {:soc "11-2022" :title "Sales Managers"
      :onet-link "https://www.onetonline.org/link/summary/11-2022.00"
      :advanced-credential? false :median 107338 :education "Bachelor's degree"}
     {:soc "25-1011" :title "Business Teachers, Postsecondary"
      :onet-link "https://www.onetonline.org/link/summary/25-1011.00"
      :advanced-credential? true :median 97058 :education "Doctoral or professional degree"}
     {:soc "13-1111" :title "Management Analysts"
      :onet-link "https://www.onetonline.org/link/summary/13-1111.00"
      :advanced-credential? false :median 93870 :education "Bachelor's degree"}]
    :pums
    {:scope "Louisiana"
     :field "Business Management And Administration"
     :degfieldd "6203"
     :total-in-field 33913
     :top-occupation-pct "4.6%"
     :under-50-callout? true
     :occupations
     [{:weighted-n 1576 :occsoc "1191XX" :soc "11-91XX" :pct "4.6%" :median 90000 :in-field? false}
      {:onet-link "https://www.onetonline.org/link/summary/11-3031.00" :weighted-n 1429
       :occsoc "113031" :title "Financial Managers" :soc "11-3031" :pct "4.2%" :median 89877 :in-field? false}
      {:onet-link "https://www.onetonline.org/link/summary/25-2020.00" :weighted-n 1373
       :occsoc "252020" :soc "25-2020" :pct "4%" :median 46661 :in-field? false}
      {:weighted-n 1122 :occsoc "2310XX" :soc "23-10XX" :pct "3.3%" :median 140000 :in-field? false}
      {:onet-link "https://www.onetonline.org/link/summary/11-1021.00" :weighted-n 881
       :occsoc "111021" :title "General and Operations Managers" :soc "11-1021" :pct "2.6%" :median 85000 :in-field? true}]}
    :summary
    "You could pursue roles such as Chief Executives, Industrial Production Managers, or Construction Managers."}

   ;; --- LEGACY cruft (kept so the adapter can be proven to DROP it) ---
   :personalized-overview "LEGACY: an inline-computed overview paragraph the adapter must drop."
   :outcome-bullet "LEGACY: a single outcome bullet the adapter must drop."
   :career-summary "LEGACY: a career-summary string the adapter must drop."
   :best-for ["LEGACY best-for tag"]
   :key-differentiators ["LEGACY differentiator"]
   :source :ai})

;; ---------------------------------------------------------------------------
;; What's Cool slice 3 — a REAL engine PROGRAM record carrying :whats-cool,
;; lifted VERBATIM from the whats_cool.edn addendum for
;;   Southeastern Louisiana University | Registered Nursing/Registered Nurse. (SLU BSN).
;; The engine (What's Cool slice 2) attaches :whats-cool [{:title :body :source} …]
;; to program records; each point carries NO :tag/:accent (those are UI concerns the
;; adapter supplies). Used by the whats-cool adapter test AND the UI-3 harness so the
;; test and the real-data render exercise the SAME real shape.
;; ---------------------------------------------------------------------------

(def real-whats-cool-program
  {:name "Registered Nursing/Registered Nurse."
   :acronym "BSN"
   :institution-name "Southeastern Louisiana University"
   :whats-cool
   [{:title "EDGE direct-admit pathway for HS seniors (3.7 GPA / 25 ACT)"
     :body (str "Apply by January 15 of your senior year and declare nursing; EDGE admits HS students "
                "with a minimum 3.7 unweighted GPA and 25 ACT, then requires completion of all "
                "prerequisites within three semesters, a 3.0 cumulative GPA, and at least a C in every "
                "prerequisite.")
     :source "Notion Differentiators + content sections; Active"}
    {:title "Ochsner employer pipeline with scholarship support"
     :body (str "SLU documents clinical partnerships with Ochsner Health (Baton Rouge); the LaNeaf "
                "Ochsner Scholarship funds students who participate in the Ochsner Summer Nurse Tech "
                "program or complete Ochsner Baton Rouge clinical rotations.")
     :source "Notion Differentiators + content sections; Active"}
    {:title "Clinical training in Baton Rouge for the final three semesters"
     :body (str "Prerequisites are completed at Hammond, then the last three semesters take place at the "
                "SLU Baton Rouge Center (Essen Lane), which has patient simulation and skills-practice "
                "labs and clinical placements at Ochsner Hospital Baton Rouge and Baton Rouge General "
                "Medical Center.")
     :source "Notion Differentiators + content sections; Active"}
    {:title "Articulation into SLU MSN consortium and BSN-to-DNP pathways"
     :body (str "SLU offers articulation into its MSN through a consortium with McNeese, Nicholls, and "
                "UL Lafayette, and provides BSN-to-DNP pathways.")
     :source "Notion Differentiators + content sections; Active"}]})

;; ---------------------------------------------------------------------------
;; 0014 — secondary-track cards. REAL short-term-program + apprenticeship records
;; (card-header subset) lifted verbatim from the saved pool events
;; (pool_event_student-1.edn :short-term-programs[0]; pool_event_student-2.edn
;; :apprenticeships[0]). Used by the program->short-term-card-props tests so the
;; adapter is proven tolerant of BOTH tracks' distinct key shapes:
;;   • short-term carries :program-title / :institution-name / :award-level-name /
;;     :cluster-name, and demand :stars live at [:salary :stars] and [:salary :demand :stars]
;;   • apprenticeship carries :apprenticeship-title / :company-name / :program-type
;;     (NO :award-level-name), and demand :stars live at [:earn :demand :stars]
;; ---------------------------------------------------------------------------

(def real-short-term-program
  {:program-title "Health Information/Medical Records Technology/Technician."
   :institution-name "River Parishes Community College"
   :award-level-name "Certificate 1-2 years"
   :cluster-name "Career Pathway 51.07 (Long Term Care Administration, Health Information)"
   :program-url "https://www.rpcc.edu/medical-coding"
   :funding {:info-url "https://www.rpcc.edu/medical-coding"}
   :salary {:mode :pseo
            :stars 3
            :demand {:soc "29-9021" :soc-title "Health Information Technologists and Medical Registrars"
                     :median 53943 :stars 3 :growth-pct "+15.17%" :openings "142"
                     :education "Associate's degree"}}})

(def real-apprenticeship
  {:apprenticeship-title "General Apprenticeship Apprenticeship"
   :company-name "Assoc. Builders/ Contractors, Inc.- Pelican"
   :program-type "Entry-Level Trade"
   :application-url "https://apprenticeshipla.com/apprenticeships/general-carpenter-millwright/"
   :earn {:earn-while-learn? true
          :demand {:soc "47-2061" :soc-title "Construction Laborers" :median 39310
                   :stars 4 :growth-pct "+13.42%" :openings "32,683"
                   :education "No formal educational credential"}
          :info-url "https://apprenticeshipla.com/apprenticeships/general-carpenter-millwright/"}})

;; ---------------------------------------------------------------------------
;; 0014 — scholarships tab. A REAL scholarship record lifted verbatim from the
;; saved pool event (pool_event_student-1.edn :scholarships[0], MASWE). Note the
;; shape deltas the scholarship->card-props adapter must handle:
;;   • award reads :award-amount-display (falling back to :award)
;;   • why-fits ← :personalized-explanation; selection ← :selection-criteria
;;   • :application-tips is a STRING (must be wrapped in a vector for bullet-list)
;;   • url ← :application-url; NO :sponsor/:eligibility (present-by-data → omitted)
;; ---------------------------------------------------------------------------

(def real-scholarship
  {:name "MASWE Scholarship"
   :award "$6,750"
   :award-amount-display "$6,750"
   :deadline "December 1."
   :personalized-explanation
   (str "As a high-achieving female student pursuing Biomedical Engineering, you perfectly align with "
        "the Society of Women Engineers' mission to support women in achieving their full potential as "
        "engineering leaders.")
   :application-tips
   (str "Highlight your leadership as Science Olympiad Team Captain to demonstrate your potential as a "
        "future leader in engineering, a core part of the SWE mission.")
   :selection-criteria "Applications are reviewed based on three main"
   :application-url "http://swe.org"})
