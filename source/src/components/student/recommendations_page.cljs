(ns components.student.recommendations-page
  "Student view for recommendations - read-only version of advisor view"
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.rec-demo.core :as rec-demo]
            ;; Retained from main's URL-rendering work: the advisor message is prose
            ;; that may contain links.
            [components.advisor.bullet-text :refer [linkify-text]]
            [components.context.interface :as context]
            [store.student.recommendations.events :as recommendations-events]
            [store.student.recommendations.subs :as recommendations-subs]
            [components.shared.loading-states :refer [loading-spinner error-banner]]
            [components.shared.affordability-callout :refer [affordability-callout]]
            [components.shared.terms-to-know :refer [terms-to-know]]
            [components.shared.str-groups :as str-groups]
            ["/gen/shadcn/components/ui/tabs" :as tabs]))

(defn category-label [category]
  (case category
    :safety "Safety"
    :target "Target"
    :reach "Reach"))

;; ---------------------------------------------------------------------------
;; Slice 0015 / issue 0062 — PURE display re-grouping of college institutions.
;; These four helpers now live in the SHARED ns components.shared.str-groups so
;; the advisor dense editor + this viewer share ONE definition (advisor and
;; student always read the identical Safety/Target/Reach categories). Re-exported
;; here as local aliases so existing internal + external references (this ns's
;; own uses + components.advisor.authoring.view's `student-recs/…` refs) keep
;; resolving with no behavior change (pure refactor).
;; ---------------------------------------------------------------------------

(def str-group-order str-groups/str-group-order)
(def str-group-labels str-groups/str-group-labels)
(def str-group-key str-groups/str-group-key)
(def group-institutions-by-str str-groups/group-institutions-by-str)

;; ---------------------------------------------------------------------------
;; Slice 0033 (epic match-tavidee; ADR 0008) — degree-type SECTION grouping.
;; The engine (0032) tags every rendered record with :section ∈ {:bachelors
;; :career-technical}. The UI groups the tabs by that tag. An OLD (pre-0032) pool
;; carries no :section, so we FALL BACK to a UI-side derivation from the record's
;; own sector signals: a 4-year institution → :bachelors, a 2-year → :career-
;; technical (short-term programs / apprenticeships live in their own top-level
;; lists and are ALWAYS career-technical). Present-by-data — a record is never
;; dropped (an unknown institution defaults to :bachelors, a college's track).
;; Career-Technical is ONE interleaved flow (Variant A) — schools + short-term +
;; apprenticeships together, never split schools-vs-apprenticeships.
;; ---------------------------------------------------------------------------

(def ^:private section-name-set
  "The two valid degree-type section names, for membership tests (name-coerced so a
   string OR keyword :section resolves)."
  #{"bachelors" "career-technical"})

(defn- two-year-institution?
  "PURE: does this institution read as a 2-year (Career-Technical) school? Scans its
   own sector signals — :sectors (engine vector), :sector (demo string), :type
   (demo label), :kind (:two-year) — for a 2-year marker."
  [inst]
  (let [text (-> (concat (:sectors inst)
                         [(:sector inst) (:type inst) (some-> (:kind inst) name)])
                 (->> (remove nil?) (str/join " "))
                 str/lower-case)]
    (boolean (re-find #"2-year|2 year|two-year|two year" text))))

(defn institution-section
  "PURE: the degree-type section (:bachelors | :career-technical) for ONE college
   institution. Prefers the engine 0032 :section tag (name-coerced so a string OR
   keyword resolves). When absent (an OLD pre-0032 pool) FALLS BACK to a sector
   derivation — a 2-year → :career-technical, everything else → :bachelors (a
   college's track). Never nil, never drops a record."
  [inst]
  (let [tag (some-> (:section inst) name)]
    (cond
      (contains? section-name-set tag)     (keyword tag)
      (two-year-institution? inst)         :career-technical
      :else                                :bachelors)))

;; ---------------------------------------------------------------------------
;; Slice 0078 (ADR 0008) — PER-PROGRAM section split. Each program carries its OWN
;; :section tag (engine sections.clj tag-record), while the institution's collapsed
;; :section is only its FIRST program's — so a MIXED-degree school (a bachelor's +
;; a terminal program) must appear in BOTH tabs, showing only that tab's programs.
;; We split at RENDER (one institution record; programs grouped, never duplicated /
;; re-bucketed — duplicating would fork institution-level overrides).
;; ---------------------------------------------------------------------------

(defn institution-in-section-with-programs
  "PURE: `inst` scoped to ONE degree-type `section` (:bachelors | :career-technical).

   When ANY program carries the engine 0032 :section tag, returns the institution with
   :programs FILTERED to that section (nil when none match) — the per-program split so a
   mixed school appears in BOTH tabs with disjoint program subsets.

   When NO program carries a tag, per-program filtering has nothing to key on, so the
   WHOLE institution routes by `institution-section`'s sector fallback, programs intact.
   That covers the two real shapes the strict filter silently hid:
     - a LEGACY pool (Python-era :advisor/generated-recommendations-pool — tag-pool only
       runs at generation, so last-year records have :programs but no :section anywhere;
       the strict filter emptied every college and left only the Scholarships tab), and
     - a school the advisor just CHECKED (the promote handler writes {:id … :programs []},
       and the resolver always assocs :programs — the school the advisor explicitly chose
       must render, with no program cards, rather than vanish from the preview).
   One tagged program is enough to stay strict — the fallback never loosens a NEW pool.
   Never mutates other institution fields; never drops an untagged record."
  [section inst]
  (let [progs     (:programs inst)
        any-tag?  (boolean (some :section progs))]
    (if (and (contains? inst :programs) any-tag?)
      (let [matching (filterv #(= (name section) (some-> (:section %) name)) progs)]
        (when (seq matching)
          (assoc inst :programs matching)))
      (when (= section (institution-section inst)) inst))))

(defn institutions-in-section
  "PURE: `insts` scoped to `section` (:bachelors | :career-technical), preserving input
   order. Each institution with ≥1 program in that section is kept with its :programs
   FILTERED to that section (per-program split, slice 0078) — so a mixed-degree school
   appears in BOTH section lists with disjoint program subsets. A program-less record
   falls back to whole-institution membership (institution-in-section-with-programs)."
  [section insts]
  (into [] (keep #(institution-in-section-with-programs section %) insts)))

(def ^:private ct-str-rank
  "STR display rank for the Career-Technical interleave — open-admission (and the
   tier-less apprenticeships / short-term programs) lead, then Safety → Target →
   Reach terminal schools."
  {"open" 0 "safety" 1 "target" 2 "reach" 3})

(defn career-technical-flow
  "PURE Variant-A interleave for the Career-Technical section: the career-technical
   college institutions + ALL short-term programs + ALL apprenticeships in ONE ranked
   flow (NOT split schools-vs-apprenticeships). Schools keep their STR badge and are
   ordered by STR rank; the tier-less apprenticeships and short-term programs take the
   leading (open) rank so they interleave with the open-admission 2-years, and the
   terminal tiered schools (Safety/Target/Reach) follow. STABLE — input order is
   preserved within a rank. Each item is {:kind (:school|:short-term|:apprenticeship)
   :record <the record>}. Empty when there are no career-technical records."
  [ct-institutions short-term-programs apprenticeships]
  (let [schools (map (fn [i] {:kind :school :record i
                              :rank (get ct-str-rank (str-group-key i) 1)})
                     ct-institutions)
        shorts  (map (fn [p] {:kind :short-term :record p :rank 0}) short-term-programs)
        apps    (map (fn [a] {:kind :apprenticeship :record a :rank 0}) apprenticeships)]
    (->> (concat schools shorts apps)
         (sort-by :rank)
         (mapv #(dissoc % :rank)))))

(defn personalized-title
  "PURE: the branded hero title for the recommendations page. The student's name
   when carried (NEVER a hardcoded name), a generic fallback otherwise. Shared
   verbatim by the student page AND the advisor authoring view's Student-preview
   so the hero reads identically."
  [student-name]
  (if (str/blank? student-name)
    "Your Personalized Recommendations"
    (str (str/trim student-name) "'s Personalized Recommendations")))

;; ---------------------------------------------------------------------------
;; Slice 0059 — advisor identity card. The Student-view query resolves the
;; advising staff (via the "advises" relationship) into an :advisor map
;; {:name :email :scheduling-url :profile-photo-url}. We REUSE the existing
;; rec-demo `advisor-card` component verbatim, which expects
;; {:headshot :name :title :email :appointment-url}, so `advisor->card-props`
;; is the PURE adapter between the two shapes. When there's no headshot URL we
;; synthesize an initials avatar (an inline SVG data-URI) so the card still shows
;; a circular avatar — never a broken image.
;; ---------------------------------------------------------------------------

(defn advisor-initials
  "PURE: up to two uppercase initials from an advisor's name (first + last word).
   Blank/nil name → \"\"."
  [name]
  (let [parts (->> (str/split (str/trim (or name "")) #"\s+")
                   (remove str/blank?))]
    (->> parts
         (take 2)
         (map #(str/upper-case (subs % 0 1)))
         (apply str))))

(defn initials-avatar-uri
  "An inline SVG data-URI showing the advisor's initials on the brand teal-tint
   circle — the headshot fallback so `advisor-card`'s <img> always renders an
   avatar (never a broken image) when no headshot URL is set."
  [name]
  (let [inits (advisor-initials name)
        svg (str "<svg xmlns='http://www.w3.org/2000/svg' width='64' height='64' viewBox='0 0 64 64'>"
                 "<rect width='64' height='64' rx='32' fill='#d0ecef'/>"
                 "<text x='32' y='32' dy='0.35em' text-anchor='middle' "
                 "font-family='sans-serif' font-size='24' font-weight='600' fill='#2a6465'>"
                 inits "</text></svg>")]
    (str "data:image/svg+xml," (js/encodeURIComponent svg))))

(defn advisor->card-props
  "PURE adapter: the Student-view query's :advisor map → the props the reused
   rec-demo `advisor-card` expects. nil advisor → nil (card omitted,
   present-by-data). A blank :profile-photo-url → an initials-avatar data-URI."
  [advisor]
  (when advisor
    {:name (:name advisor)
     :title "BRYC Senior Advisor"
     :email (:email advisor)
     :appointment-url (:scheduling-url advisor)
     :headshot-fallback (initials-avatar-uri (:name advisor))
     :headshot (let [h (some-> (:profile-photo-url advisor) str/trim)]
                 (if (seq h) h (initials-avatar-uri (:name advisor))))}))

(defn get-query-param [param-name]
  "Extract query parameter from URL"
  (when-let [search (.-search js/window.location)]
    (let [params (js/URLSearchParams. search)]
      (.get params param-name))))

(defui category-section
  "STR-grouped (Safety/Target/Reach) college cards. Each resolved institution now
   renders through the rec-demo `student-school-card` (header via the PURE adapter
   school->card-props; 'Learn More' → About This School + What It Costs You + the
   institution's programs' Overview/Salary/Careers, all through the UI-1/UI-2
   adapters) — replacing the OLD prose `advisor/institution-card`. Each card
   self-manages its own expand state, so no expanded-set threading is needed."
  [{:keys [heading str-group institutions student]}]
  (when (seq institutions)
    ($ :div {:class "space-y-2"}
       ($ :h3 {:class "text-sm font-semibold uppercase tracking-wide text-muted-foreground"}
          heading)
       ($ :div {:class "space-y-4"}
          (for [[idx inst] (map-indexed vector institutions)]
            ($ rec-demo/student-school-card
               {:key idx
                :institution inst
                ;; 0074 (Defect B): thread the S·T·R group tier this card is rendered
                ;; under so a school missing its OWN :str-badge falls back to the group's
                ;; badge (never a missing badge under a known STR heading).
                :str-group str-group
                :student student}))))))

(defui recommendations-page [{:keys [embedded?]}]
  ;; `embedded?` (0051) — when the advisor previews this page INSIDE the dense editor
  ;; (a narrower container than the full-page token route), force the single-column
  ;; layout so the 500px advisor-message column doesn't squeeze the recommendations
  ;; column (which overlaps the tabs). Default (token route) keeps the 2-column layout.
  (let [ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to student recommendations state
        loading? (use-subscribe [::recommendations-subs/loading?])
        error (use-subscribe [::recommendations-subs/error])
        resolved (use-subscribe [::recommendations-subs/resolved])
        advisor-message (use-subscribe [::recommendations-subs/advisor-message])
        advisor (use-subscribe [::recommendations-subs/advisor])
        advisor-card-props (advisor->card-props advisor)
        student (:student resolved)
        ;; Branded-header title: the student's name when carried, generic otherwise
        ;; (NEVER a hardcoded name).
        title (personalized-title (:name student))

        ;; Calculate which categories have content
        safety-institutions (get-in resolved [:institutions :safety] [])
        target-institutions (get-in resolved [:institutions :target] [])
        reach-institutions (get-in resolved [:institutions :reach] [])
        ;; ALL college institutions from the pool's safety+target+reach buckets. The
        ;; pool buckets themselves are never mutated.
        all-college-institutions (vec (concat safety-institutions
                                              target-institutions
                                              reach-institutions))
        short-term-programs (get-in resolved [:short-term-programs] [])
        apprenticeships (get-in resolved [:apprenticeships] [])
        scholarships (get-in resolved [:scholarships] [])

        ;; Slice 0033 — group by the engine 0032 :section (Bachelor's / Career-
        ;; Technical), with the UI-side fallback for an OLD pool (institution-section).
        ;; Bachelor's = its institutions, DISPLAY re-grouped Open→Safety→Target→Reach
        ;; (slice 0015). Career-Technical = its institutions + all short-term programs +
        ;; all apprenticeships, INTERLEAVED in one ranked flow (Variant A).
        bachelors-institutions (institutions-in-section :bachelors all-college-institutions)
        career-technical-institutions (institutions-in-section :career-technical all-college-institutions)
        bachelors-groups (group-institutions-by-str bachelors-institutions)
        career-technical-items (career-technical-flow career-technical-institutions
                                                      short-term-programs
                                                      apprenticeships)
        has-bachelors? (seq bachelors-institutions)
        has-career-technical? (seq career-technical-items)
        has-scholarships? (seq scholarships)

        ;; Determine which tabs to show and the default tab (present-by-data)
        available-tabs (cond-> []
                         has-bachelors? (conj "bachelors")
                         has-career-technical? (conj "career-technical")
                         has-scholarships? (conj "scholarships"))
        default-tab (first available-tabs)
        tab-count (count available-tabs)]

    ;; Load recommendations on mount using token from URL
    (use-effect
     (fn []
       (when-let [token (get-query-param "token")]
         (rf/dispatch [::recommendations-events/load-recommendations token api-client]))
       js/undefined)
     [api-client])

    ($ :div {:class "flex flex-col min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50"}

       ;; Header — dark-teal branded bar with the BRYC white logo + student title.
       ($ :div {:class "bg-[#2a6465] shadow-sm px-6 md:px-8 py-4 flex-shrink-0"}
          ($ :div {:class "flex items-center gap-3"}
             ($ :img {:src "/assets/bryc-logo-white.png" :alt "BRYC"
                      :class "w-10 h-10 rounded-full"})
             ($ :h1 {:class "text-lg md:text-2xl font-bold text-white font-head"} title)))

       ;; Content
       ($ :div {:class "flex-1 flex flex-col overflow-hidden p-8"}
          (cond
           loading?
           ($ loading-spinner {:text "Loading your recommendations..."})

           error
           ($ error-banner {:error (str "Error loading recommendations: "
                                        (or (:cognitect.anomalies/message error) error))})

           (nil? resolved)
           ($ loading-spinner {:text "No recommendations found"})

           :else
           ($ :div {:class "flex justify-center h-full"}
              ;; Slice 0052 — row gap so the stacked (single-column / embedded
              ;; preview) advisor-message + advisor card don't bump the tab bar.
              ;; gap-x-0 keeps the lg two-column layout's horizontal spacing (the
              ;; left column already carries lg:pr-8) unchanged. In the EMBEDDED
              ;; in-editor preview the layout is ALWAYS single-column (the two
              ;; columns stack), so the left block (advisor message + card) sits
              ;; directly above the tab bar — a larger row gap (gap-y-12) gives it
              ;; breathing room. The REAL token view keeps gap-y-8 (irrelevant at lg
              ;; where it's two side-by-side columns; preserved for its own mobile
              ;; single-column stacking) — unchanged.
              ($ :div {:class (str "w-full max-w-[1600px] grid grid-cols-1 gap-x-0 "
                                   (if embedded? "gap-y-20" "gap-y-8")
                                   (when-not embedded? " lg:grid-cols-[500px_1fr]"))}
                 ;; Left Sidebar
                 ($ :div {:class "lg:pr-8 space-y-8 overflow-y-auto pb-8 lg:pb-0"}
                    ;; Advisor Message Section
                    (when advisor-message
                      ($ :div {:class "space-y-4"}
                         ($ :div {:class "flex items-center"}
                            ($ :div {:class "w-2 h-2 bg-gradient-to-r from-blue-400 to-purple-500 rounded-full mr-3 opacity-70"})
                            ($ :h2 {:class "text-sm font-semibold uppercase tracking-wide text-gray-600"}
                               "Message from Your Advisor"))
                         ($ :div {:class "bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm p-6 transition-all duration-300 ease-in-out hover:shadow-md"}
                            ($ :div {:class "text-sm md:text-base text-gray-700 leading-relaxed whitespace-pre-wrap max-w-prose"}
                               (linkify-text advisor-message)))))
                    ;; Slice 0059 — advisor identity card, reusing the rec-demo
                    ;; advisor-card verbatim (initials avatar when no headshot).
                    ;; Present-by-data: rendered only when the query resolved an
                    ;; advisor from the "advises" relationship.
                    (when advisor-card-props
                      ($ rec-demo/advisor-card {:advisor advisor-card-props})))


                 ;; Right Content - Tabbed Interface
                 ($ :div {:class "overflow-y-auto"}
                    ($ :div {:class "max-w-4xl mx-auto pb-12"}
                       ($ tabs/Tabs {:default-value (or default-tab "bachelors")}
                          ;; Tab navigation - only show tabs with content
                          ($ tabs/TabsList {:class (str "mb-8 grid w-full h-auto p-1 sm:p-2 bg-white/50 backdrop-blur-sm rounded-2xl shadow-sm "
                                                        (case tab-count
                                                          1 "grid-cols-1"
                                                          2 "grid-cols-2"
                                                          3 "grid-cols-3"
                                                          "grid-cols-4"))}
                             (when has-bachelors?
                               ($ tabs/TabsTrigger {:value "bachelors" :class "text-sm sm:text-lg py-2 px-2 sm:py-4 sm:px-6 rounded-xl transition-all duration-300 ease-in-out data-[state=active]:shadow-sm"} "Bachelor's"))
                             (when has-career-technical?
                               ($ tabs/TabsTrigger {:value "career-technical" :class "text-sm sm:text-lg py-2 px-2 sm:py-4 sm:px-6 rounded-xl transition-all duration-300 ease-in-out data-[state=active]:shadow-sm"} "Career-Technical"))
                             (when has-scholarships?
                               ($ tabs/TabsTrigger {:value "scholarships" :class "text-sm sm:text-lg py-2 px-2 sm:py-4 sm:px-6 rounded-xl transition-all duration-300 ease-in-out data-[state=active]:shadow-sm"} "Scholarships")))

                          ;; Bachelor's Tab — 4-year + transfer pathways, DISPLAY re-grouped
                          ;; Open→Safety→Target→Reach (slice 0015), with the S·T·R legend on top.
                          ($ tabs/TabsContent {:value "bachelors" :class "space-y-6 pt-2"}
                             ;; College Aid Pro informational section (SHARED — see
                             ;; components.shared.affordability-callout; the advisor
                             ;; authoring view renders the identical block).
                             ($ affordability-callout)
                             ($ rec-demo/str-legend)
                             (for [[str-key insts] bachelors-groups]
                               ($ category-section
                                  {:key str-key
                                   :heading (get str-group-labels str-key str-key)
                                   ;; 0074 (Defect B): the group tier ("open"/"safety"/
                                   ;; "target"/"reach") this group's cards fall back to.
                                   :str-group str-key
                                   :institutions insts
                                   :student student})))

                          ;; Career-Technical Tab — ONE interleaved "Pathway Recommendations"
                          ;; flow (Variant A): 2-year/terminal schools (each carrying its own
                          ;; STR / Open-Admission badge) + short-term programs + apprenticeships,
                          ;; in ranked order. The S·T·R legend sits on top (college-bearing).
                          ($ tabs/TabsContent {:value "career-technical" :class "space-y-6 pt-2"}
                             ($ affordability-callout)
                             ($ rec-demo/str-legend)
                             ($ :div {:class "space-y-4"}
                                ($ rec-demo/section-eyebrow {:label "Pathway Recommendations"})
                                (for [[idx {:keys [kind record]}] (map-indexed vector career-technical-items)]
                                  (case kind
                                    :school ($ rec-demo/student-school-card
                                               ;; 0078: Career-Technical cards carry NO
                                               ;; S/T/R badge (ADR 0008 — S/T/R is a
                                               ;; within-Bachelor's grouping). A genuine
                                               ;; Open-Admission badge may still survive.
                                               {:key idx :institution record :student student
                                                :career-technical? true})
                                    ($ rec-demo/short-term-card
                                       {:key idx :program record :student student})))))

                          ;; Scholarships Tab — demo-style rec-demo card driven by the
                          ;; PURE adapter (adapter/scholarship->card-props): award/
                          ;; deadline/why-fits/tips/apply from the real scholarship
                          ;; fields, present-by-data.
                          ($ tabs/TabsContent {:value "scholarships" :class "space-y-4"}
                             (for [[idx schol] (map-indexed vector scholarships)]
                               ($ rec-demo/student-scholarship-card
                                  {:key idx
                                   :scholarship schol}))))
                       ;; Terms to Know — SHARED fixed glossary (deterministic,
                       ;; NO LLM, NOT advisor-editable), rendered once at the
                       ;; bottom of the recommendations content.
                       ($ terms-to-know))))))))))
