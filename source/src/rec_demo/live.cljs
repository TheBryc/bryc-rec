(ns rec-demo.live
  "LIVE entry — the rec-redesign PROTOTYPE components fed by REAL resolved data.

   index.html fetches the student's recommendation (share token -> transit ->
   decoded JSON) BEFORE loading this bundle and stashes it on
   js/window.__BRYC_REC_DATA__. This ns adapts the resolved pool records into
   the exact prop shapes components.rec-demo.core consumes and composes the
   page 1:1 with rec-demo-page (header / two-column / tabs / tiles).

   NO component restyling here — every visual comes from core.cljs verbatim."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [uix.dom :as dom]
            [clojure.string :as str]
            [components.rec-demo.data :as data]
            [components.rec-demo.core :as core]))

;; ---------------------------------------------------------------------------
;; helpers
;; ---------------------------------------------------------------------------

(defn- pct-str [x]
  (when (number? x) (str (js/Math.round (* 100 (if (<= x 1) x (/ x 100)))) "%")))

(defn- domain-label [website]
  (when (seq website)
    (-> website (str/replace #"^https?://" "") (str/replace #"^www\." "") (str/replace #"/$" ""))))

(defn- onet-code [role]
  (or (some-> (:onet-link role) (str/split #"/summary/") last)
      (some-> (:soc role) (str ".00"))))

(def ^:private wc-accents
  ["border-l-[#05a09c] bg-[#d0ecef]/60"
   "border-l-[#007f81] bg-[#b1e1e9]/50"
   "border-l-[#40bfbb] bg-[#d0ecef]/60"
   "border-l-[#2a6465] bg-[#d0ecef]/40"])

(defn- trim-title [t]
  (some-> t (str/replace #"[.\s]+$" "") str/trim))

;; ---------------------------------------------------------------------------
;; pathway adapter — resolved program record -> prototype pathway props
;; ---------------------------------------------------------------------------

(defn- ->role [r]
  {:title (:title r)
   :soc (onet-code r)
   :requirement (when (:advanced-credential? r) "Advanced degree typically required")
   :desc (or (:desc r)
             (when (:median r)
               (str "Typical pay about $" (.toLocaleString (:median r) "en-US") "/yr"
                    (when (:education r) (str " · " (:education r) " typical")))))})

(defn- ->pums [p]
  (when p
    {:scope (or (:scope p) "Louisiana")
     :under-50-callout? (boolean (:under-50-callout? p))
     :occupations (mapv (fn [o]
                          {:title (:title o)
                           :onet (some-> (:onet-link o) (str/split #"/summary/") last)
                           :pct (:pct o)
                           :median (:median o)
                           :note (:note o)})
                        (:occupations p))}))

(defn- ->whats-cool [wc]
  (vec (map-indexed (fn [i c]
                      {:title (:title c) :body (:body c) :tag (:tag c)
                       :accent (or (:accent c) (nth wc-accents (mod i (count wc-accents))))})
                    (take 4 wc))))

(defn- bachelors? [p]
  (boolean (some-> (:award-level-name p) (str/includes? "Bachelor"))))

(defn- ->pathway [p]
  (let [b? (bachelors? p)
        salary (:salary p)
        careers (:careers p)
        ov (:overview p)
        wc (:whats-cool p)
        ttc-len (or (get-in p [:time-to-credential :designed :length])
                    (get-in p [:time-to-credential :intended]))
        available (cond-> #{}
                    ov (conj :overview)
                    (seq wc) (conj :whats-cool)
                    salary (conj :salary)
                    (or (seq (:roles careers)) (:pums careers)) (conj :careers)
                    ttc-len (conj :time-to-credential))
        sections (filterv available data/section-order)]
    (cond-> {:id (:id p)
             :name (trim-title (:program-title p))
             :cip (:cip-code p)
             :credential-level (:award-level-name p)
             :bachelors? b?
             :category (if b? :degree :career-technical)
             :lwc-stars (or (:stars salary) (get-in salary [:occupation :stars]))
             :sections (if (seq sections) sections [:overview])}
      ov               (assoc :overview ov)
      (:program-url p) (assoc :program-url (:program-url p))
      (seq wc)         (assoc :whats-cool (->whats-cool wc))
      salary           (assoc :salary
                              (cond-> salary
                                (:citation salary) (assoc :earnings-source (:citation salary))))
      careers          (assoc :careers
                              (cond-> {}
                                (seq (:roles careers)) (assoc :roles (mapv ->role (:roles careers)))
                                (:pums careers) (assoc :pums (->pums (:pums careers)))))
      ttc-len          (assoc :time-to-credential {:intended ttc-len}))))

;; ---------------------------------------------------------------------------
;; school adapter — resolved institution record -> prototype school props
;; ---------------------------------------------------------------------------

(def ^:private str-colors
  {"safety" "#2f9e44" "target" "#e8a93b" "reach" "#c92a4a" "open" "#676868"})

(defn- ->classification [badge]
  (when-let [k (some-> (:key badge) name)]
    {:key k :label (or (:label badge) (str/capitalize k)) :color (get str-colors k "#676868")}))

;; Curated local logo set (assets/logos/<IPEDS unitid>.png) — one uniform mark
;; per school, keyed by resolved institution name because engine pool records
;; carry no unitid. Pool logo-urls vary wildly in size/quality; these don't.
(def ^:private logo-files
  {"bates college" "160977" "baton rouge community college" "437103"
   "berea college" "156295" "college of the ozarks" "178697"
   "delta college of arts & technology" "366270" "ferris state university" "169910"
   "georgetown university" "131496" "grambling state university" "159009"
   "howard university" "131520" "iti technical college" "159197"
   "lake superior state university" "170639"
   "louisiana state university and agricultural & mechanical college" "159391"
   "louisiana state university at alexandria" "159382"
   "louisiana tech university" "159647" "nicholls state university" "159966"
   "pace university" "194310" "russell sage college" "195128"
   "saint xavier university" "148627" "scripps college" "123165"
   "southern university and a & m college" "160621"
   "southern university at new orleans" "160630" "university of houston" "225511"
   "university of minnesota-morris" "174251" "university of new orleans" "159939"
   "university of tulsa" "207971"})

(defn- curated-logo [school-name]
  (when-let [uid (get logo-files (some-> school-name str/lower-case str/trim))]
    (str "assets/logos/" uid ".png")))

(defn- ->school [i]
  (let [loc (:location i)
        ad (:admissions-data i)
        costs (:costs i)
        in-state? (= "LA" (:state loc))
        grp-pct (:enrollment-group-pct i)
        gr (:grad-rate i)]
    (cond-> {:unitid (str (or (:unitid i) (:id i)))
             :name (or (:institution-name i) (:name i))
             :short-name (or (:institution-name i) (:name i))
             :city (:city loc)
             :state (:state loc)
             :type (some-> (first (:sectors i)) (str/replace #",? 4-year or above" ", 4-year")
                           (str/replace #",? 2-year or above" ", 2-year"))
             :website (:website i)
             :website-label (domain-label (:website i))
             :logo (or (curated-logo (or (:institution-name i) (:name i)))
                       (:logo-url i))
             :classification (->classification (:str-badge i))
             :commutable? (and in-state? (= "Baton Rouge" (:city loc)))
             :distance-relevant? (not in-state?)}
      (:act-25th ad)         (assoc :act-25 (:act-25th ad))
      (:act-75th ad)         (assoc :act-75 (:act-75th ad))
      (:acceptance-rate ad)  (assoc :acceptance (pct-str (:acceptance-rate ad)))
      (map? gr)              (assoc :grad-rate gr)
      (:transfer-out i)      (assoc :transfer-out (:transfer-out i))
      (:enrollment-total i)  (assoc :enrollment-total (:enrollment-total i))
      grp-pct                (assoc :enrollment-races
                                    {"Black/African American"
                                     {:pct grp-pct :count (:enrollment-group-count i)}})
      (:retention i)         (assoc :retention (:retention i))
      (get-in i [:residential :type]) (assoc :setting-type (get-in i [:residential :type]))
      (:distance i)          (assoc :distance (:distance i))
      costs                  (assoc :costs
                                    (cond-> costs
                                      (get-in i [:avg-debt :amount])
                                      (assoc :avg-debt (get-in i [:avg-debt :amount])))))))

;; ---------------------------------------------------------------------------
;; scholarship adapter (present-by-data; whole-string sentinels dropped)
;; ---------------------------------------------------------------------------

(defn- present [v]
  (when (and v (string? v)
             (not (contains? #{"not_found" "unknown" "n/a" "tbd" "none" ""}
                             (str/lower-case (str/trim v)))))
    v))

(defn- ->scholarship [s]
  {:id (:id s)
   :name (:name s)
   :sponsor (present (:sponsor s))
   ;; Ranged awards arrive as two bare amounts ("$500 $1,000") — join with an
   ;; en dash so ranges read as ranges (Tavidee 2026-08-28).
   :award (let [a (or (present (:award-amount-display s)) (present (:award s)) "See site")]
            (str/replace a #"(\$[\d,.]+[KkMm]?)\s+(?=\$)" "$1 – "))
   :deadline (or (present (:deadline s)) "varies")
   :why-fits (present (:personalized-explanation s))
   :eligibility (or (present (:eligibility s)) "see application page")
   :target-levels (or (:target-levels s) "high-school senior")
   :selection (present (:selection-criteria s))
   :tips (let [t (:application-tips s)]
           (cond (string? t) (some-> (present t) vector)
                 (sequential? t) (vec (keep present t))
                 :else nil))
   :url (present (:application-url s))})

;; ---------------------------------------------------------------------------
;; student / advisor
;; ---------------------------------------------------------------------------

(defn- parse-act [insts]
  (some (fn [i]
          (some->> (:ability-position i)
                   (re-find #"ACT (\d+)")
                   second js/parseInt))
        insts))

(defn- ->student [resolved insts]
  (let [nm (str/trim (or (get-in resolved [:student :name]) ""))]
    {:name (if (seq nm) nm "Your")
     :first-name (if (seq nm) (first (str/split nm #"\s+")) "Your")
     :act (or (parse-act insts) 0)
     :race-group "Black/African American"
     :minority? true}))

;; ---------------------------------------------------------------------------
;; page — composition mirrors core/rec-demo-page exactly (same classes)
;; ---------------------------------------------------------------------------

(defui advisor-column [{:keys [advisor message]}]
  ($ :div {:class "space-y-4"}
     ($ core/section-eyebrow {:label "A Message From Your Advisor"})
     (when (seq message)
       ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-5 border-l-4 border-l-[#05a09c]"}
          ($ :p {:class "text-sm md:text-base text-[#313335] leading-relaxed"} message)))
     (when advisor
       ;; Subtitle by staff email — the advisor payload carries no role/title.
       ;; Fellows see their assigned College Counselor; advisees see Tavidee.
       ($ core/advisor-card
          {:advisor {:name (:name advisor)
                     :title (case (:email advisor)
                              "tavidee@thebryc.org" "BRYC Senior Advisor"
                              "rachel@thebryc.org" "Senior Counselor / Writing Lead"
                              "BRYC College Team")
                     :email (:email advisor)
                     :appointment-url (:scheduling-url advisor)
                     :headshot (or (:profile-photo-url advisor)
                                   "assets/bryc-logo-white.png")}}))))

(defui live-category-tab [{:keys [schools category student]}]
  (let [groups (for [{:keys [school pathways]} schools
                     :let [ps (filterv #(= category (:category %)) pathways)]
                     :when (seq ps)]
                 {:school school :pathways ps})]
    ($ :div {:class "space-y-4"}
       ($ core/str-legend)
       ($ :div {:class "space-y-3"}
          ($ core/section-eyebrow {:label "Pathway Recommendations"})
          (for [{:keys [school pathways]} groups]
            ($ core/school-tile {:key (:unitid school) :school school
                                 :pathways pathways :student student})))
       ($ core/terms-to-know))))

(defui live-scholarships-tab [{:keys [scholarships]}]
  ($ :div {:class "space-y-3"}
     ($ core/section-eyebrow {:label "Scholarships"})
     ($ :p {:class "text-xs text-[#676868] leading-relaxed"}
        "Scholarship details change often — always confirm amounts and deadlines on the application page. Your advisor can add opportunities to this list.")
     (for [s scholarships]
       ($ core/scholarship-card {:key (:id s) :s s}))))

(defui live-recommendations-column [{:keys [schools scholarships student]}]
  (let [[tab set-tab!] (use-state :degree)
        has-ct? (some (fn [{:keys [pathways]}] (some #(= :career-technical (:category %)) pathways)) schools)
        tabs (cond-> [{:key :degree :label "Bachelor's"}]
               has-ct? (conj {:key :career-technical :label "Career-Technical"})
               (seq scholarships) (conj {:key :scholarships :label "Scholarships"}))]
    ($ :div {:class "space-y-4"}
       ($ :h2 {:class "text-xl font-semibold text-[#2a6465] font-head"} "Check these out:")
       (when (> (count tabs) 1)
         ($ :div {:class "flex gap-1 bg-white rounded-xl ring-1 ring-[#d0ecef] p-1"}
            (for [{:keys [key label]} tabs]
              ($ core/tab-button {:key (name key) :label label :active? (= tab key)
                                  :on-click #(set-tab! key)}))))
       (if (= tab :scholarships)
         ($ live-scholarships-tab {:scholarships scholarships})
         ($ live-category-tab {:schools schools :category tab :student student})))))

(defui live-page [{:keys [payload]}]
  (let [resolved (:resolved payload)
        insts (vec (concat (get-in resolved [:institutions :safety])
                           (get-in resolved [:institutions :target])
                           (get-in resolved [:institutions :reach])))
        student (->student resolved insts)
        schools (vec (for [i insts]
                       {:school (->school i)
                        :pathways (mapv ->pathway (:programs i))}))
        scholarships (mapv ->scholarship (:scholarships resolved))
        advisor (:advisor payload)
        message (:advisor-message payload)
        left? (or advisor (seq message))]
    ($ :div {:class "min-h-screen bg-[#f2f3f4]"}
       ($ :div {:class "bg-[#2a6465] shadow-sm px-6 md:px-8 py-4"}
          ($ :div {:class "max-w-6xl mx-auto flex items-center gap-3"}
             ($ :img {:src "assets/bryc-logo-white.png" :alt "BRYC"
                      :class "w-10 h-10 rounded-full"})
             ($ :h1 {:class "text-lg md:text-2xl font-bold text-white font-head"}
                (str (:name student) "'s Personalized Recommendations"))))
       ($ :div {:class "max-w-6xl mx-auto px-4 md:px-8 py-8"}
          (if left?
            ;; two-column layout — identical classes to rec-demo-page
            ($ :div {:class "grid grid-cols-1 lg:grid-cols-12 gap-6 items-start"}
               ($ :div {:class "lg:col-span-4 lg:sticky lg:top-6"}
                  ($ advisor-column {:advisor advisor :message message}))
               ($ :div {:class "lg:col-span-8"}
                  ($ live-recommendations-column
                     {:schools schools :scholarships scholarships :student student})))
            ;; no advisor data yet -> single full-width column (no grid; only
            ;; classes the demo build's css already contains)
            ($ live-recommendations-column
               {:schools schools :scholarships scholarships :student student}))))))

(defui error-page [{:keys [msg]}]
  ($ :div {:class "min-h-screen bg-[#f2f3f4] flex items-center justify-center"}
     ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-8 max-w-md text-center"}
        ($ :p {:class "text-sm md:text-base text-[#313335] leading-relaxed"} msg))))

(defonce root
  (dom/create-root (js/document.getElementById "root")))

(defn render []
  (let [raw (.-__BRYC_REC_DATA__ js/window)]
    (if raw
      (dom/render-root ($ live-page {:payload (js->clj raw :keywordize-keys true)}) root)
      (dom/render-root ($ error-page {:msg (or (.-__BRYC_REC_ERROR__ js/window)
                                               "Could not load your recommendations — ask your advisor for a fresh link.")})
                       root))))

(defn ^:dev/after-load re-render [] (render))

(defn init [] (render))
