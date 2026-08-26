(ns components.advisor.apprenticeship-table-panel-test
  "0069 — the apprenticeship detail sheet was BLANK ON CLICK. Root cause (live console):
   the CURRENT generated pool ships :overview as the NEW overview MAP
   ({:credential-line :student-connection :caveat :day-to-day}), NOT the plain string the
   engine schema declares. The old body rendered (:overview merged) raw via read-only-field,
   so React tried to render a cljs map as a child — 'Objects are not valid as a React child
   (found: object with keys {key, val, __hash …})' (a map iterates to MapEntries) — and the
   whole sheet blanked.

   The fix drives everything through the PURE, props-driven `apprenticeship-detail-body`:
   present-by-data + nil-safe. :overview MAP → shared program-overview-section; :overview
   STRING → read-only-field; nil → nothing. Salary renders only for numeric values."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.advisor.apprenticeship-table-panel :as panel]))

(defn- render-body [merged]
  (dom-server/render-to-string ($ panel/apprenticeship-detail-body {:merged merged})))

;; The record shape the LIVE pool actually ships (overview is a MAP).
(def overview-map-record
  {:apprenticeship-title    "General Apprenticeship Apprenticeship"
   :company-name            "Mia's Medical Academy"
   :city                    "Baton Rouge"
   :state                   "LA"
   :source                  :ai
   :overview                {:credential-line    "Entry-level clinical support apprenticeship."
                             :student-connection "Because you want to build medical devices, this gives clinical context."
                             :caveat             "This is a trade apprenticeship, not a bachelor's degree."}
   :starting-salary-annual  29990.0
   :average-salary-annual   37310.0
   :bullets                 ["Delivers clinical-support competencies." "Connects you to local employers."]
   :requirements            ["High school diploma or GED" "18 years of age"]
   :application-url         "https://example.com/apply"})

;; ---------------------------------------------------------------------------
;; The blank-fix: an :overview MAP renders (no crash), via the shared overview section.
;; ---------------------------------------------------------------------------

(deftest overview-map-renders-without-crashing
  (testing "a MAP :overview renders its fields read-only — the blank/crash is fixed"
    (let [html (render-body overview-map-record)]
      (is (re-find #"Overview" html) "the Overview heading renders")
      (is (re-find #"want to build medical devices" html) "the :student-connection renders")
      (is (re-find #"Entry-level clinical support" html) "the :credential-line renders")
      (is (re-find #"trade apprenticeship" html) "the :caveat renders"))))

;; ---------------------------------------------------------------------------
;; Noncanonical :overview strings are ignored.
;; ---------------------------------------------------------------------------

(deftest overview-string-is-ignored
  (testing "a plain-string :overview is outside the canonical contract"
    (let [html (render-body (assoc overview-map-record
                                   :overview "$14/hr starting, growing to $17/hr average."))]
      (is (not (re-find #"14/hr starting" html))))))

;; ---------------------------------------------------------------------------
;; Present-by-data: no :overview ⇒ no Overview block, no crash.
;; ---------------------------------------------------------------------------

(deftest no-overview-present-by-data
  (testing "a record with NO :overview renders no Overview block and does not crash"
    (let [html (render-body (dissoc overview-map-record :overview))]
      (is (string? html) "renders")
      (is (not (re-find #"Overview" html)) "no Overview block without :overview"))))

;; ---------------------------------------------------------------------------
;; Salary is nil-safe: nil / non-number never reaches .toLocaleString.
;; ---------------------------------------------------------------------------

(deftest salary-nil-safe
  (testing "nil salaries render no salary block and do not crash"
    (let [html (render-body (dissoc overview-map-record
                                    :starting-salary-annual :average-salary-annual))]
      (is (not (re-find #"Starting Salary" html)) "no starting-salary line")
      (is (not (re-find #"Average Salary" html)) "no average-salary line")))
  (testing "a non-number salary is ignored (no .toLocaleString crash)"
    (let [html (render-body (assoc overview-map-record
                                   :starting-salary-annual "not-a-number"
                                   :average-salary-annual nil))]
      (is (string? html) "renders without throwing")
      (is (not (re-find #"Starting Salary" html)) "non-number starting salary not shown")))
  (testing "a numeric salary renders formatted"
    (let [html (render-body overview-map-record)]
      (is (re-find #"Starting Salary" html) "starting salary line present")
      (is (re-find #"29,990" html) "starting salary formatted with grouping")
      (is (re-find #"37,310" html) "average salary formatted with grouping"))))

;; ---------------------------------------------------------------------------
;; Bullets / requirements are present-by-data.
;; ---------------------------------------------------------------------------

(deftest bullets-present-by-data
  (testing "bullets + requirements render when present"
    (let [html (render-body overview-map-record)]
      (is (re-find #"Why This Fits" html))
      (is (re-find #"clinical-support competencies" html))
      (is (re-find #"Requirements" html))
      (is (re-find #"High school diploma" html))))
  (testing "missing bullets ⇒ 'None' (no crash)"
    (let [html (render-body (dissoc overview-map-record :bullets :requirements))]
      (is (string? html) "renders"))))

;; ---------------------------------------------------------------------------
;; A minimal record (post-regen-safe) renders cleanly.
;; ---------------------------------------------------------------------------

(deftest minimal-record-renders
  (testing "a bare apprenticeship (title only) renders without crashing"
    (let [html (render-body {:apprenticeship-title "X"})]
      (is (re-find #"Application URL" html) "the sheet still renders")
      (is (not (re-find #"Overview" html)) "no Overview block"))))

;; ---------------------------------------------------------------------------
;; 0081 (PRD 0011) — the AI Overview prose (Credential / Why It Fits / Good to Know) + the
;; top-level "Why This Fits" :bullets are now INLINE-EDITABLE in the dense editor. Salary,
;; Requirements, Application URL, Title/Company/Location stay READ-ONLY. No revert chip.
;; ---------------------------------------------------------------------------

(defn- render-edit-body [merged]
  (dom-server/render-to-string
   ($ panel/apprenticeship-detail-body
      {:merged merged :app-id "app-1" :api-client (js-obj)})))

(deftest overview-map-is-editable
  (testing "the three Overview prose fields render as text editors (<textarea>), not read-only <p> values"
    (let [html (render-edit-body overview-map-record)]
      (is (re-find #"Overview" html) "the Overview heading renders")
      (is (re-find #"Credential" html) "Credential label renders")
      (is (re-find #"Why It Fits" html) "Why It Fits label renders")
      (is (re-find #"Good to Know" html) "Good to Know label renders")
      (is (<= 3 (count (re-seq #"<textarea" html))) "at least three prose textareas render")
      (is (re-find #"Entry-level clinical support" html) ":credential-line is a textarea value")
      (is (re-find #"want to build medical devices" html) ":student-connection is a textarea value")
      (is (re-find #"trade apprenticeship" html) ":caveat is a textarea value")))
  (testing "NO revert-to-source chip anywhere in the editor"
    (is (not (re-find #"(?i)revert" (render-edit-body overview-map-record))))))

(deftest why-this-fits-is-an-editable-bullet-list
  (testing "'Why This Fits' (:bullets) renders as an editable bullet list (rows + Add), not read-only bullets"
    (let [html (render-edit-body overview-map-record)]
      (is (re-find #"Why This Fits" html) "the Why This Fits heading renders")
      (is (re-find #"data-editable-field=\"why-this-fits\"" html) "the why-this-fits editor is present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"clinical-support competencies" html) "existing bullets render as editable inputs")
      ;; editable rows are <input>, not a read-only <ul class=list-disc>
      (is (re-find #"<input" html) "bullets render as editable inputs"))))

(deftest sourced-fields-stay-read-only-alongside-editable-overview
  (testing "Salary / Requirements / Application URL stay read-only while Overview + bullets are editable"
    (let [html (render-edit-body overview-map-record)]
      ;; sourced salary: plain text, never an input
      (is (re-find #"Starting Salary" html) "starting salary line present")
      (is (re-find #"29,990" html) "salary is read-only formatted text")
      ;; Requirements: read-only bullet list (list-disc), NOT inputs
      (is (re-find #"Requirements" html) "Requirements heading kept")
      (is (re-find #"list-disc" html) "Requirements renders as a read-only bullet list")
      (is (re-find #"High school diploma" html) "requirement item renders read-only")
      ;; Application URL: read-only anchor
      (is (re-find #"Application URL" html) "Application URL kept")
      (is (re-find #"<a [^>]*href=\"https://example.com/apply\"" html) "URL is a read-only link"))))

(deftest overview-string-never-enters-editable-path
  (testing "a noncanonical string overview is neither rendered nor editable"
    (let [html (render-edit-body (assoc overview-map-record
                                        :overview "$14/hr starting, growing to $17/hr average."))]
      (is (not (re-find #"14/hr starting" html)))
      (is (not (re-find #"Credential" html)) "no editable Credential field for a string overview"))))

(deftest editable-overview-present-by-data
  (testing "no :overview map ⇒ no Overview prose editors (present-by-data), but Why-This-Fits editor still available"
    (let [html (render-edit-body (dissoc overview-map-record :overview))]
      (is (not (re-find #"Credential" html)) "no Overview prose fields without an :overview map")
      (is (re-find #"Why This Fits" html) "the Why This Fits editable list is still offered"))))
