(ns components.advisor.short-term-program-table-panel-test
  "0069 — the short-term detail sheet showed DEAD legacy prose that 0066 stopped generating:
   :personalized-overview (Overview), :outcome-bullet (Expected Outcomes), :career-summary
   (Career Summary). After the next regen those keys vanish and the sheet would blank on
   them. This strips the three dead reads and instead renders the CURRENT :overview MAP
   (narrate_programs.clj writes it onto all tracks; the redesigned viewer shows it for
   short-term), plus the deterministic context: award badge, Career Paths, Program URL.
   The :overview map is rendered via the shared present-by-data program-overview-section.

   Driven through the PURE, props-driven `short-term-program-detail-body` (dom-server)."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.advisor.short-term-program-table-panel :as panel]))

(defn- render-body [merged career-paths]
  (dom-server/render-to-string
   ($ panel/short-term-program-detail-body {:merged merged :career-paths career-paths})))

;; A record that STILL carries the dead legacy prose (as the current pool does) — none of it
;; may render.
(def legacy-laden
  {:program-title         "Sterile Processing Technology/Technician."
   :award-level-name      "Certificate < 1 year"
   :program-url           "https://example.edu/sterile-processing"
   :personalized-overview "LEGACY-PERSONALIZED-OVERVIEW-should-not-render"
   :outcome-bullet        "LEGACY-OUTCOME-BULLET-should-not-render"
   :career-summary        "LEGACY-CAREER-SUMMARY-should-not-render"})

(deftest dead-legacy-prose-is-gone
  (testing "the three dead legacy reads never render, even when the record carries them"
    (let [html (render-body legacy-laden ["Surgical Technologist"])]
      (is (not (re-find #"LEGACY-PERSONALIZED-OVERVIEW" html)) ":personalized-overview gone")
      (is (not (re-find #"LEGACY-OUTCOME-BULLET" html)) ":outcome-bullet gone")
      (is (not (re-find #"LEGACY-CAREER-SUMMARY" html)) ":career-summary gone")
      (is (not (re-find #"Expected Outcomes" html)) "no Expected Outcomes block")
      (is (not (re-find #"Career Summary" html)) "no Career Summary block"))))

;; A record carrying the CURRENT :overview MAP (as narrate_programs.clj writes for all tracks).
(def overview-map-laden
  (assoc legacy-laden
         :overview {:credential-line    "Certificate in Sterile Processing (< 1 year)."
                    :student-connection "Because you love hands-on healthcare work, this quick credential gets you into a hospital."
                    :day-to-day         ["Sterilize surgical instruments" "Track sterilization loads"]}))

(deftest overview-map-renders
  (testing "a short-term program WITH an :overview map renders the overview content (mirrors the viewer)"
    (let [html (render-body overview-map-laden ["Surgical Technologist"])]
      (is (re-find #"Overview" html) "the Overview heading renders")
      (is (re-find #"Sterile Processing" html) "the :credential-line renders")
      (is (re-find #"love hands-on healthcare work" html) "the :student-connection renders")
      (is (re-find #"Sterilize surgical instruments" html) "the :day-to-day bullets render")
      ;; the dead legacy prose is STILL never rendered, even alongside the map
      (is (not (re-find #"LEGACY-PERSONALIZED-OVERVIEW" html)) "no legacy overview string")
      (is (not (re-find #"Expected Outcomes" html)) "no Expected Outcomes block")
      (is (not (re-find #"Career Summary" html)) "no Career Summary block"))))

(deftest no-overview-map-no-block
  (testing "present-by-data: a short-term program with NO :overview map renders NO Overview block"
    (let [html (render-body legacy-laden ["Surgical Technologist"])]
      (is (not (re-find #"Overview" html)) "no Overview heading when there is no :overview map"))))

(deftest deterministic-context-kept
  (testing "award badge, Career Paths, and Program URL still render"
    (let [html (render-body legacy-laden ["Surgical Technologist" "Central Service Tech"])]
      (is (re-find #"Certificate &lt; 1 year|Certificate < 1 year" html) "award badge renders")
      (is (re-find #"Career Paths" html) "Career Paths heading renders")
      (is (re-find #"Surgical Technologist" html) "career path items render")
      (is (re-find #"Program URL" html) "Program URL label renders")
      (is (re-find #"example.edu/sterile-processing" html) "the URL renders"))))

(deftest present-by-data-no-crash
  (testing "a bare record (post-regen-safe: no dead keys, no url, no career paths) renders cleanly"
    (let [html (render-body {:program-title "X"} [])]
      (is (string? html) "renders")
      (is (not (re-find #"Overview" html)) "no Overview block")
      (is (re-find #"Career Paths" html) "Career Paths section still present (present-by-data 'None')")
      (is (re-find #"Program URL" html) "Program URL label still present"))))

;; ---------------------------------------------------------------------------
;; 0082 (PRD 0011) — the :overview map prose (Credential / Why It Fits / Good to Know / Day
;; to Day) is now INLINE-EDITABLE in the dense editor. Career Paths + Program URL stay
;; READ-ONLY. Only the NEW :overview map keys (never legacy :personalized-overview). No chip.
;; ---------------------------------------------------------------------------

(defn- render-edit-body [merged career-paths]
  (dom-server/render-to-string
   ($ panel/short-term-program-detail-body
      {:merged merged :career-paths career-paths :program-id "stp-1" :api-client (js-obj)})))

(deftest overview-map-is-editable
  (testing "the three Overview prose fields render as text editors (<textarea>) + Day to Day as an editable bullet list"
    (let [html (render-edit-body overview-map-laden ["Surgical Technologist"])]
      (is (re-find #"Overview" html) "the Overview heading renders")
      (is (re-find #"Credential" html) "Credential label renders")
      (is (re-find #"Why It Fits" html) "Why It Fits label renders")
      ;; two prose fields present in the fixture (credential-line + student-connection) → textareas
      (is (<= 2 (count (re-seq #"<textarea" html))) "prose fields render as textareas")
      (is (re-find #"Sterile Processing" html) ":credential-line is a textarea value")
      (is (re-find #"love hands-on healthcare work" html) ":student-connection is a textarea value")
      ;; Day to Day = editable bullet list
      (is (re-find #"Day to Day" html) "Day to Day heading renders")
      (is (re-find #"data-editable-field=\"day-to-day\"" html) "the day-to-day editor is present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"Sterilize surgical instruments" html) "existing day-to-day rows render as editable inputs")
      ;; legacy prose never leaks in
      (is (not (re-find #"LEGACY-PERSONALIZED-OVERVIEW" html)) "no legacy overview string")))
  (testing "NO revert-to-source chip anywhere in the editor"
    (is (not (re-find #"(?i)revert" (render-edit-body overview-map-laden ["Surgical Technologist"]))))))

(deftest career-paths-and-url-stay-read-only
  (testing "Career Paths + Program URL stay read-only while the Overview is editable"
    (let [html (render-edit-body overview-map-laden ["Surgical Technologist" "Central Service Tech"])]
      ;; Career Paths = read-only bullet list (list-disc), NOT inputs
      (is (re-find #"Career Paths" html) "Career Paths heading kept")
      (is (re-find #"list-disc" html) "Career Paths renders as a read-only bullet list")
      (is (re-find #"Surgical Technologist" html) "career path role renders read-only")
      ;; Program URL = read-only anchor
      (is (re-find #"Program URL" html) "Program URL kept")
      (is (re-find #"<a [^>]*href=\"https://example.edu/sterile-processing\"" html) "URL is a read-only link"))))

(deftest editable-overview-present-by-data
  (testing "no :overview map ⇒ NO Overview editors (present-by-data), Career Paths + URL still render"
    (let [html (render-edit-body legacy-laden ["Surgical Technologist"])]
      (is (not (re-find #"Overview" html)) "no Overview block without an :overview map")
      (is (not (re-find #"<textarea" html)) "no prose textareas without an :overview map")
      (is (re-find #"Career Paths" html) "Career Paths still renders")
      (is (re-find #"Program URL" html) "Program URL still renders"))))
