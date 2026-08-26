(ns components.advisor.institution-detail-sheet-test
  "0065 — the dense-editor detail sheets must stop showing DEAD legacy prose and show the
   CURRENT read-only Overview from the NEW :overview map (adapter.cljs:371-383 shape).

   READ-ONLY only (piece 1). The program column's Overview is swapped OFF the legacy
   :personalized-overview string ONTO the :overview map {:credential-line :student-connection
   :caveat :day-to-day}; the dead Expected-Outcomes (:outcome-bullet) / Career-Summary
   (:career-summary) blocks are gone. Present-by-data: no :overview map ⇒ no Overview block.

   Driven through the PUBLIC interfaces: the pure `overview-display` seam and a real
   dom-server render of `program-overview-section` / `program-editor-column`."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.advisor.authoring.override :as override]
            [components.advisor.institution-detail-sheet :as sheet]))

(def full-overview
  {:overview {:credential-line    "Associate of Science in Nursing (ADN)"
              :student-connection "Because you love biology and helping people, nursing fits."
              :caveat             "You must pass the NCLEX to be licensed."
              :day-to-day         ["Chart patient vitals" "Administer medications"]}})

;; A program that STILL carries the dead legacy prose (as a stale pool would). The new
;; Overview must read ONLY the :overview map — never these.
(def legacy-laden
  (merge full-overview
         {:program-title           "Nursing"
          :program-url             "https://example.edu/nursing"
          :personalized-overview   "LEGACY-OVERVIEW-STRING-should-not-render"
          :outcome-bullet          "LEGACY-OUTCOME-should-not-render"
          :career-summary          "LEGACY-CAREER-SUMMARY-should-not-render"}))

;; ---------------------------------------------------------------------------
;; overview-display — the pure present-by-data seam
;; ---------------------------------------------------------------------------

(deftest overview-display-surfaces-the-new-map
  (testing "reads the :overview submap fields, present-by-data"
    (let [d (sheet/overview-display legacy-laden)]
      (is (= "Associate of Science in Nursing (ADN)" (:credential-line d)))
      (is (= "Because you love biology and helping people, nursing fits." (:student-connection d)))
      (is (= "You must pass the NCLEX to be licensed." (:caveat d)))
      (is (= ["Chart patient vitals" "Administer medications"] (:day-to-day d))))))

(deftest overview-display-surfaces-canonical-summary
  (is (= {:summary "Earn while learning."}
         (sheet/overview-display {:overview {:summary "Earn while learning."}}))))

(deftest overview-display-never-reads-legacy-strings
  (testing "the dead :personalized-overview / :outcome-bullet / :career-summary never leak in"
    (let [d (sheet/overview-display legacy-laden)
          vals (str (vals d))]
      (is (not (re-find #"LEGACY" vals)) "no legacy string surfaces via overview-display"))))

(deftest overview-display-present-by-data
  (testing "no :overview map ⇒ nil (the caller omits the block) — even with legacy strings present"
    (is (nil? (sheet/overview-display {:personalized-overview "still here"
                                       :outcome-bullet "still here"})))
    (is (nil? (sheet/overview-display {}))))
  (testing "an :overview map with only blank fields ⇒ nil"
    (is (nil? (sheet/overview-display {:overview {:credential-line "" :student-connection nil}}))))
  (testing "a partial :overview map keeps only the present fields"
    (let [d (sheet/overview-display {:overview {:student-connection "only this"}})]
      (is (= {:student-connection "only this"} d)))))

;; ---------------------------------------------------------------------------
;; program-overview-section — rendered read-only Overview
;; ---------------------------------------------------------------------------

(defn- render-overview [program]
  (dom-server/render-to-string ($ sheet/program-overview-section {:program program})))

(deftest program-overview-renders-the-new-map
  (testing "the current Overview text renders read-only"
    (let [html (render-overview legacy-laden)]
      (is (re-find #"Overview" html) "the Overview heading renders")
      (is (re-find #"love biology and helping people" html) "the :student-connection renders")
      (is (re-find #"Associate of Science in Nursing" html) "the :credential-line renders")
      (is (re-find #"pass the NCLEX" html) "the :caveat renders"))))

(deftest program-overview-hides-dead-legacy
  (testing "the dead legacy prose is NOT rendered even when the record still carries it"
    (let [html (render-overview legacy-laden)]
      (is (not (re-find #"LEGACY-OVERVIEW-STRING" html)) ":personalized-overview is gone")
      (is (not (re-find #"LEGACY-OUTCOME" html)) ":outcome-bullet (Expected Outcomes) is gone")
      (is (not (re-find #"LEGACY-CAREER-SUMMARY" html)) ":career-summary is gone")
      (is (not (re-find #"Expected Outcomes" html)) "no Expected Outcomes block")
      (is (not (re-find #"Career Summary" html)) "no Career Summary block"))))

(deftest program-overview-present-by-data
  (testing "no :overview map ⇒ NO Overview block rendered (no crash, no empty block)"
    (let [html (render-overview {:program-title "X"
                                 :personalized-overview "legacy only"})]
      (is (not (re-find #"Overview" html)) "no Overview heading when there is no :overview map"))))

;; ---------------------------------------------------------------------------
;; program-editor-column — the full left column keeps its non-dead parts
;; ---------------------------------------------------------------------------

(defn- render-column [merged]
  (dom-server/render-to-string
   ($ sheet/program-editor-column
      {:merged merged
       :career-paths ["Registered Nurse" "Nurse Practitioner"]
       :institution {:institution-name "Example University"}
       :on-close (fn [])})))

(deftest program-column-swaps-overview-and-keeps-the-rest
  (testing "new Overview + Career Paths + Program URL render; dead legacy blocks do not"
    (let [html (render-column legacy-laden)]
      (is (re-find #"love biology and helping people" html) "new Overview renders")
      (is (re-find #"Career Paths" html) "Career Paths kept")
      (is (re-find #"Registered Nurse" html) "career path items kept")
      (is (re-find #"Program URL" html) "Program URL kept")
      (is (not (re-find #"LEGACY-OVERVIEW-STRING" html)) "legacy overview gone")
      (is (not (re-find #"Expected Outcomes" html)) "Expected Outcomes gone")
      (is (not (re-find #"Career Summary" html)) "Career Summary gone")))
  (testing "present-by-data in the column: a program with no :overview still renders URL + Career Paths, no Overview"
    (let [html (render-column {:program-title "X"
                               :program-url "https://example.edu/x"
                               :personalized-overview "legacy only"})]
      (is (not (re-find #"Overview" html)) "no Overview block without an :overview map")
      (is (re-find #"Program URL" html) "URL still renders")
      (is (re-find #"Career Paths" html) "Career Paths still renders"))))

;; ---------------------------------------------------------------------------
;; 0080 (piece 2) — the four Overview PROSE fields are now inline-EDITABLE in the
;; dense editor's college program column: Credential / Why It Fits / Good to Know are
;; text editors (<textarea>), Day to Day is an editable bullet list (rows + "+ Add").
;; Career Paths (deterministic role list) stays READ-ONLY. Program URL is EDITABLE
;; (0090 — advisors fix broken/missing links). No revert chip.
;; ---------------------------------------------------------------------------

(defn- render-overview-editor [program]
  (dom-server/render-to-string
   ($ sheet/program-overview-editor
      {:program program :category :target :inst-id "inst-1" :prog-id "prog-1"
       :api-client (js-obj)})))

(deftest overview-editor-renders-four-editable-fields
  (testing "the three prose fields render as text editors bound to the :overview keys"
    (let [html (render-overview-editor full-overview)]
      (is (re-find #"Credential" html) "Credential label renders")
      (is (re-find #"Why It Fits" html) "Why It Fits label renders")
      (is (re-find #"Good to Know" html) "Good to Know label renders")
      ;; three prose fields → three <textarea> editors (NOT read-only <p> values)
      (is (<= 3 (count (re-seq #"<textarea" html))) "at least three prose textareas render")
      (is (re-find #"love biology and helping people" html) ":student-connection is the textarea value")
      (is (re-find #"Associate of Science in Nursing" html) ":credential-line is a textarea value")
      (is (re-find #"pass the NCLEX" html) ":caveat is a textarea value")))
  (testing "Day to Day renders as an editable bullet list (rows + Add), not read-only bullets"
    (let [html (render-overview-editor full-overview)]
      (is (re-find #"Day to Day" html) "Day to Day heading renders")
      (is (re-find #"data-editable-field=\"day-to-day\"" html) "the day-to-day editor is present")
      (is (re-find #"\+ Add" html) "an Add-row control renders")
      (is (re-find #"Chart patient vitals" html) "existing day-to-day rows render as editable inputs")))
  (testing "NO revert-to-source chip anywhere in the editor"
    (is (not (re-find #"(?i)revert" (render-overview-editor full-overview))))))

(deftest overview-editor-present-by-data
  (testing "no :overview map ⇒ the editor renders nothing (no crash, no empty inputs)"
    (is (= "" (render-overview-editor {:program-title "X" :personalized-overview "legacy"})))))

(deftest program-column-overview-editable-career-paths-readonly
  (testing "the WHOLE column: Overview prose + Program URL are editable while Career Paths stays read-only"
    (let [html (dom-server/render-to-string
                ($ sheet/program-editor-column
                   {:merged legacy-laden
                    :career-paths ["Registered Nurse" "Nurse Practitioner"]
                    :institution {:institution-name "Example University"}
                    :category :target :inst-id "inst-1" :prog-id "prog-1"
                    :api-client (js-obj)
                    :on-close (fn [])}))]
      ;; Overview = editable
      (is (re-find #"<textarea" html) "Overview prose fields are editable textareas")
      (is (re-find #"data-editable-field=\"day-to-day\"" html) "Day to Day is an editable bullet list")
      ;; Career Paths = read-only list (list-disc read-only bullets), NOT an input
      (is (re-find #"Career Paths" html) "Career Paths heading kept")
      (is (re-find #"list-disc" html) "Career Paths renders as a read-only bullet list")
      (is (re-find #"Registered Nurse" html) "career path role renders read-only")
      ;; Program URL = EDITABLE field (0090), not a read-only anchor
      (is (re-find #"Program URL" html) "Program URL label kept")
      (is (not (re-find #"<a [^>]*href=\"https://example.edu/nursing\"" html))
          "URL is NOT a read-only <a> link anymore")
      (is (re-find #"https://example.edu/nursing" html) "the current URL prefills the editable field")
      ;; no legacy prose
      (is (not (re-find #"LEGACY-OVERVIEW-STRING" html)) "legacy overview gone"))))

;; ---------------------------------------------------------------------------
;; 0090 — Program URL is INLINE-EDITABLE in the college program column. Broken/missing
;; links let advisors drop in a correct URL. It rides the SAME generic program-ref
;; :text-edits override seam as the other prose (::set-program-text-edit :program-url),
;; so a set round-trips through override/apply-overrides and a revert restores the
;; sourced URL EXACTLY (the pool record is never mutated). A blank/missing :program-url
;; renders an EMPTY editable field (not hidden) so the advisor can add one.
;; ---------------------------------------------------------------------------

(defn- render-column-with-ids [merged]
  (dom-server/render-to-string
   ($ sheet/program-editor-column
      {:merged merged
       :career-paths ["Registered Nurse"]
       :institution {:institution-name "Example University"}
       :category :target :inst-id "inst-1" :prog-id "prog-1"
       :api-client (js-obj)
       :on-close (fn [])})))

(deftest program-url-is-editable-field
  (testing "the Program URL renders as an editable field prefilled with the current URL, not an <a>"
    (let [html (render-column-with-ids legacy-laden)]
      (is (re-find #"Program URL" html) "Program URL label renders")
      (is (not (re-find #"<a [^>]*href=\"https://example.edu/nursing\"" html))
          "the URL is no longer a read-only anchor")
      (is (re-find #"https://example.edu/nursing" html) "the current URL prefills the editable field"))))

(deftest program-url-blank-still-renders-editable-field
  (testing "a MISSING :program-url still renders the (empty) editable Program URL field, not hidden"
    (let [html (render-column-with-ids (dissoc legacy-laden :program-url))]
      (is (re-find #"Program URL" html) "Program URL label renders even with no URL")
      (is (re-find #"<textarea" html) "an editable field is present to add a URL"))))

(deftest program-url-override-round-trip
  (testing "setting :program-url via the generic text-edit seam reads back through apply-overrides"
    (let [program {:program-title "Nursing" :program-url "https://old.edu/nursing"}
          ref     (override/set-text-edit {:id "prog-1"} :program-url "https://new.edu/nursing")
          merged  (override/apply-overrides program ref)]
      (is (= "https://new.edu/nursing" (:program-url merged))
          "the advisor's new URL wins on the read path (no allowlist blocks :program-url)")))
  (testing "reverting the :program-url text-edit restores the sourced URL EXACTLY (pool never mutated)"
    (let [program {:program-title "Nursing" :program-url "https://old.edu/nursing"}
          ref     (-> (override/set-text-edit {:id "prog-1"} :program-url "https://new.edu/nursing")
                      (override/revert-text-edit :program-url))
          merged  (override/apply-overrides program ref)]
      (is (= {:id "prog-1"} ref) "the ref is byte-identical to a never-edited one after revert")
      (is (= "https://old.edu/nursing" (:program-url merged)) "the sourced URL returns"))))
