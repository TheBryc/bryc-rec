(ns components.advisor.editor-editable-boundary-test
  "0083 (PRD 0011, Stream 3 Slice E) — a durable GUARD that LOCKS the editable/read-only
   boundary across all three dense-editor detail sheets + scholarships. Slices 0080/0081/0082
   made the AI-generated prose editable; this slice pins the boundary so a future change can't
   silently (a) make a SOURCED/deterministic field editable, or (b) turn an in-scope prose
   field read-only.

   The boundary (PRD 0011 table):
   | sheet          | EDITABLE (AI prose)                                      | READ-ONLY (sourced)                     |
   | College        | Overview cred/why-it-fits/good-to-know/day-to-day + URL  | Career Paths                            |
   | Apprenticeship | Overview credential/why-it-fits/good-to-know + :bullets | Salary, Requirements, Application URL   |
   | Short-term     | Overview credential/why-it-fits/good-to-know/day-to-day | Career Paths, Program URL               |
   | Scholarship    | Why It Fits / Description / Application Tips (0085)      | Name, Award, Selection Criteria,        |
   |                |                                                         | Deadline, Application URL                |

   Editable marker = `<textarea>` / `<input>` under `data-editable-field=\"prose|day-to-day|
   why-this-fits\"`. Read-only marker = `read-only-field` (<p>) / `read-only-bullets`
   (<ul class=list-disc>) / a plain `<a href>` anchor — never an input.

   Asserted through a real dom-server render of the props-driven detail bodies (same as the
   0080/0081/0082 tests). 0085 extended editability to the scholarship AI prose (Daryl 22-Jul),
   so the scholarship sheet's body is now the props-driven `scholarship-detail-body` and is
   rendered + asserted the same way — replacing its earlier read-only source tripwire."
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.string :as str]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.advisor.institution-detail-sheet :as inst]
            [components.advisor.apprenticeship-table-panel :as appr]
            [components.advisor.short-term-program-table-panel :as stp]
            [components.advisor.scholarship-table-panel :as schol]))

;; ---------------------------------------------------------------------------
;; Boundary helpers — extract the editable surface of a rendered sheet so we can
;; prove a sourced token is (or is NOT) reachable as an edit.
;; ---------------------------------------------------------------------------

(defn- editable-field-markers
  "Every `data-editable-field=\"X\"` value present in the rendered HTML — the complete set
   of edit affordances the sheet exposes. The boundary guard: this set must be a SUBSET of
   the allowed prose keys; a sourced field turning editable would introduce a new marker."
  [html]
  (set (map second (re-seq #"data-editable-field=\"([^\"]+)\"" html))))

(defn- editable-blob
  "The concatenated INNER text of every <textarea> plus every <input> tag — i.e. everything
   the advisor can type into. A sourced value that appears here is (wrongly) editable; a
   sourced value absent here (but present in the full HTML) is rendered read-only."
  [html]
  (str/join "\n"
            (concat (map second (re-seq #"<textarea[^>]*>([\s\S]*?)</textarea>" html))
                    (map first (re-seq #"<input[^>]*>" html)))))

(def ^:private allowed-editable-markers
  "The ONLY edit affordances any detail sheet may expose (AI prose)."
  #{"prose" "day-to-day" "why-this-fits"})

;; ===========================================================================
;; COLLEGE PROGRAM — institution_detail_sheet.cljs
;; ===========================================================================

(def college-program
  {:program-title         "Nursing"
   :program-url           "https://example.edu/nursing"
   :personalized-overview "LEGACY-should-not-render"
   :overview {:credential-line    "Associate of Science in Nursing (ADN)"
              :student-connection "Because you love biology and helping people, nursing fits."
              :caveat             "You must pass the NCLEX to be licensed."
              :day-to-day         ["Chart patient vitals" "Administer medications"]}})

(defn- render-college []
  (dom-server/render-to-string
   ($ inst/program-editor-column
      {:merged college-program
       :career-paths ["Registered Nurse" "Nurse Practitioner"]
       :institution {:institution-name "Example University"}
       :category :target :inst-id "inst-1" :prog-id "prog-1"
       :api-client (js-obj)
       :on-close (fn [])})))

(deftest college-overview-prose-is-editable
  (testing "Credential / Why It Fits / Good to Know render as prose textareas"
    (let [html (render-college)]
      (is (re-find #"data-editable-field=\"prose\"" html) "prose edit affordance present")
      (is (<= 3 (count (re-seq #"<textarea" html))) "three prose textareas (credential/why-it-fits/good-to-know)")
      (is (re-find #"Associate of Science in Nursing" html) ":credential-line is editable text")
      (is (re-find #"love biology and helping people" html) ":student-connection is editable text")
      (is (re-find #"pass the NCLEX" html) ":caveat is editable text")))
  (testing "Day to Day renders as an editable bullet list (rows + Add)"
    (let [html (render-college)]
      (is (re-find #"data-editable-field=\"day-to-day\"" html) "day-to-day edit affordance present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"Chart patient vitals" html) "existing day-to-day rows are editable inputs"))))

(deftest college-career-paths-read-only-url-editable
  (testing "Career Paths is a read-only bullet list, never an input"
    (let [html (render-college)]
      (is (re-find #"Career Paths" html) "Career Paths heading kept")
      (is (re-find #"list-disc" html) "Career Paths renders read-only (list-disc)")
      (is (re-find #"Registered Nurse" html) "career-path role renders")
      (is (not (str/includes? (editable-blob html) "Registered Nurse"))
          "career-path role is NOT inside any editable input")))
  ;; 0090 — Program URL flipped from read-only anchor to EDITABLE (advisors fix broken links).
  ;; It rides the allowed "prose" edit affordance, so no NEW marker is introduced.
  (testing "Program URL is an editable field prefilled with the URL, never a read-only anchor"
    (let [html (render-college)]
      (is (re-find #"Program URL" html) "Program URL kept")
      (is (not (re-find #"<a [^>]*href=\"https://example.edu/nursing\"" html))
          "URL is no longer a read-only anchor")
      (is (str/includes? (editable-blob html) "example.edu/nursing")
          "the URL IS inside an editable input, prefilled"))))

(deftest college-exposes-only-prose-edit-affordances
  (testing "the ONLY edit markers are the AI-prose ones — no sourced field is editable"
    (let [markers (editable-field-markers (render-college))]
      (is (seq markers) "the sheet has edit affordances (it is not accidentally all read-only)")
      (is (every? allowed-editable-markers markers)
          (str "college exposes a non-prose edit affordance: "
               (pr-str (remove allowed-editable-markers markers)))))))

;; ===========================================================================
;; APPRENTICESHIP — apprenticeship_table_panel.cljs
;; ===========================================================================

(def apprenticeship
  {:apprenticeship-title   "General Apprenticeship"
   :company-name           "Mia's Medical Academy"
   :city "Baton Rouge" :state "LA" :source :ai
   :overview {:credential-line    "Entry-level clinical support apprenticeship."
              :student-connection "Because you want to build medical devices, this gives clinical context."
              :caveat             "This is a trade apprenticeship, not a bachelor's degree."}
   :starting-salary-annual 29990.0
   :average-salary-annual  37310.0
   :bullets                ["Delivers clinical-support competencies." "Connects you to local employers."]
   :requirements           ["High school diploma or GED" "18 years of age"]
   :application-url        "https://example.com/apply"})

(defn- render-appr []
  (dom-server/render-to-string
   ($ appr/apprenticeship-detail-body
      {:merged apprenticeship :app-id "app-1" :api-client (js-obj)})))

(deftest apprenticeship-overview-and-bullets-are-editable
  (testing "Overview prose (Credential / Why It Fits / Good to Know) renders as prose textareas"
    (let [html (render-appr)]
      (is (re-find #"data-editable-field=\"prose\"" html) "prose edit affordance present")
      (is (<= 3 (count (re-seq #"<textarea" html))) "three prose textareas")
      (is (re-find #"Entry-level clinical support" html) ":credential-line editable")
      (is (re-find #"want to build medical devices" html) ":student-connection editable")
      (is (re-find #"trade apprenticeship" html) ":caveat editable")))
  (testing "Why This Fits (:bullets) renders as an editable bullet list"
    (let [html (render-appr)]
      (is (re-find #"data-editable-field=\"why-this-fits\"" html) "why-this-fits edit affordance present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"<input" html) "bullets render as editable inputs")
      (is (re-find #"clinical-support competencies" html) "existing bullets are editable"))))

(deftest apprenticeship-sourced-fields-are-read-only
  (testing "Starting/Average salary render as plain read-only text, never an input"
    (let [html (render-appr)]
      (is (re-find #"Starting Salary" html) "starting salary line present")
      (is (re-find #"29,990" html) "salary rendered as formatted text")
      (is (re-find #"37,310" html) "average salary rendered as formatted text")
      (is (not (str/includes? (editable-blob html) "29,990")) "salary is NOT inside an editable input")
      (is (not (str/includes? (editable-blob html) "37,310")) "avg salary is NOT inside an editable input")))
  (testing "Requirements render as a read-only bullet list, never inputs"
    (let [html (render-appr)]
      (is (re-find #"Requirements" html) "Requirements heading kept")
      (is (re-find #"list-disc" html) "Requirements renders read-only (list-disc)")
      (is (re-find #"High school diploma" html) "requirement item renders")
      (is (not (str/includes? (editable-blob html) "High school diploma"))
          "a requirement is NOT inside any editable input")))
  (testing "Application URL is a read-only anchor, never an input"
    (let [html (render-appr)]
      (is (re-find #"Application URL" html) "Application URL kept")
      (is (re-find #"<a [^>]*href=\"https://example.com/apply\"" html) "URL is a read-only link")
      (is (not (str/includes? (editable-blob html) "example.com/apply"))
          "the URL is NOT inside any editable input"))))

(deftest apprenticeship-exposes-only-prose-edit-affordances
  (testing "the ONLY edit markers are AI-prose ones (Overview + why-this-fits)"
    (let [markers (editable-field-markers (render-appr))]
      (is (seq markers) "the sheet has edit affordances")
      (is (every? allowed-editable-markers markers)
          (str "apprenticeship exposes a non-prose edit affordance: "
               (pr-str (remove allowed-editable-markers markers)))))))

;; ===========================================================================
;; SHORT-TERM PROGRAM — short_term_program_table_panel.cljs
;; ===========================================================================

(def short-term
  {:program-title    "Sterile Processing Technology/Technician."
   :award-level-name "Certificate < 1 year"
   :program-url      "https://example.edu/sterile-processing"
   :overview {:credential-line    "Certificate in Sterile Processing (< 1 year)."
              :student-connection "Because you love hands-on healthcare work, this quick credential gets you into a hospital."
              :caveat             "Requires standing for long shifts."
              :day-to-day         ["Sterilize surgical instruments" "Track sterilization loads"]}})

(defn- render-stp []
  (dom-server/render-to-string
   ($ stp/short-term-program-detail-body
      {:merged short-term :career-paths ["Surgical Technologist" "Central Service Tech"]
       :program-id "stp-1" :api-client (js-obj)})))

(deftest short-term-overview-prose-is-editable
  (testing "Credential / Why It Fits / Good to Know render as prose textareas"
    (let [html (render-stp)]
      (is (re-find #"data-editable-field=\"prose\"" html) "prose edit affordance present")
      (is (<= 3 (count (re-seq #"<textarea" html))) "three prose textareas")
      (is (re-find #"Sterile Processing" html) ":credential-line editable")
      (is (re-find #"love hands-on healthcare work" html) ":student-connection editable")
      (is (re-find #"standing for long shifts" html) ":caveat editable")))
  (testing "Day to Day renders as an editable bullet list"
    (let [html (render-stp)]
      (is (re-find #"data-editable-field=\"day-to-day\"" html) "day-to-day edit affordance present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"Sterilize surgical instruments" html) "existing day-to-day rows are editable"))))

(deftest short-term-career-paths-and-url-are-read-only
  (testing "Career Paths is a read-only bullet list, never an input"
    (let [html (render-stp)]
      (is (re-find #"Career Paths" html) "Career Paths heading kept")
      (is (re-find #"list-disc" html) "Career Paths renders read-only (list-disc)")
      (is (re-find #"Surgical Technologist" html) "career-path role renders")
      (is (not (str/includes? (editable-blob html) "Surgical Technologist"))
          "career-path role is NOT inside any editable input")))
  (testing "Program URL is a read-only anchor, never an input"
    (let [html (render-stp)]
      (is (re-find #"Program URL" html) "Program URL kept")
      (is (re-find #"<a [^>]*href=\"https://example.edu/sterile-processing\"" html) "URL is a read-only link")
      (is (not (str/includes? (editable-blob html) "example.edu/sterile-processing"))
          "the URL is NOT inside any editable input"))))

(deftest short-term-exposes-only-prose-edit-affordances
  (testing "the ONLY edit markers are the AI-prose ones"
    (let [markers (editable-field-markers (render-stp))]
      (is (seq markers) "the sheet has edit affordances")
      (is (every? allowed-editable-markers markers)
          (str "short-term exposes a non-prose edit affordance: "
               (pr-str (remove allowed-editable-markers markers)))))))

;; ===========================================================================
;; SCHOLARSHIP — scholarship_table_panel.cljs
;; 0085 (PRD 0011, Daryl 22-Jul: AI-generated ⇒ editable): the three AI-generated prose
;; fields — Why This Scholarship Fits (:personalized-explanation) / Description
;; (:description) / Application Tips (:application-tips) — are now INLINE-EDITABLE (prose
;; textareas via the shared editable-overview-field). Name / Award / Selection Criteria /
;; Deadline / Application URL stay READ-ONLY. Asserted through a real dom-server render of the
;; props-driven `scholarship-detail-body`.
;; ===========================================================================

(def scholarship
  {:name                     "STEM Excellence Award"
   :award                    "$5,000"
   :personalized-explanation "Because you excel at biology, this rewards your science drive."
   :description              "A merit scholarship for students pursuing STEM degrees."
   :selection-criteria       "3.5+ GPA and a demonstrated commitment to science."
   :application-tips         "Submit two recommendation letters and a personal essay early."
   :deadline                 "March 1, 2026"
   :application-url          "https://example.org/stem-award"})

(defn- render-schol []
  (dom-server/render-to-string
   ($ schol/scholarship-detail-body
      {:merged scholarship :schol-id "schol-1" :api-client (js-obj)})))

(deftest scholarship-ai-prose-is-editable
  (testing "Why It Fits / Description / Application Tips render as prose textareas"
    (let [html (render-schol)]
      (is (re-find #"data-editable-field=\"prose\"" html) "prose edit affordance present")
      (is (<= 3 (count (re-seq #"<textarea" html))) "three prose textareas (why-fits/description/application-tips)")
      (is (re-find #"excel at biology" html) ":personalized-explanation is editable text")
      (is (re-find #"merit scholarship for students" html) ":description is editable text")
      (is (re-find #"two recommendation letters" html) ":application-tips is editable text"))))

(deftest scholarship-sourced-fields-are-read-only
  (testing "Name / Award / Selection Criteria / Deadline render read-only, never inputs"
    (let [html (render-schol)]
      (is (re-find #"Scholarship Name" html) "Name label kept")
      (is (re-find #"STEM Excellence Award" html) "name renders")
      (is (not (str/includes? (editable-blob html) "STEM Excellence Award"))
          "the scholarship name is NOT inside any editable input")
      (is (re-find #"Selection Criteria" html) "Selection Criteria heading kept")
      (is (not (str/includes? (editable-blob html) "demonstrated commitment to science"))
          "selection criteria is NOT inside any editable input")
      (is (not (str/includes? (editable-blob html) "$5,000"))
          "the award is NOT inside any editable input")
      (is (not (str/includes? (editable-blob html) "March 1"))
          "the deadline is NOT inside any editable input")))
  (testing "Application URL is a read-only anchor, never an input"
    (let [html (render-schol)]
      (is (re-find #"Application URL" html) "Application URL kept")
      (is (re-find #"<a [^>]*href=\"https://example.org/stem-award\"" html) "URL is a read-only link")
      (is (not (str/includes? (editable-blob html) "example.org/stem-award"))
          "the URL is NOT inside any editable input"))))

(deftest scholarship-exposes-only-prose-edit-affordances
  (testing "the ONLY edit markers are the AI-prose ones — no sourced field is editable"
    (let [markers (editable-field-markers (render-schol))]
      (is (seq markers) "the sheet has edit affordances (it is not accidentally all read-only)")
      (is (every? allowed-editable-markers markers)
          (str "scholarship exposes a non-prose edit affordance: "
               (pr-str (remove allowed-editable-markers markers)))))))
