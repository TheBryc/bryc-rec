(ns components.advisor.dense-editor-scope-test
  "Issue 0042 — the reverted DENSE editor is scoped to SELECTION only. Cameron's rule:
   advisors must NOT edit sourced tiles/charts or Notion qualitative prose (\"as soon as
   they edit it, the citation is meaningless\"). Editable = select/deselect, promote,
   reorder/rank, category-reassign, add-custom — nothing else.

   The dense editor's editing surfaces are the four detail sheets it mounts (the institution
   detail sheet + the apprenticeship / short-term / scholarship table panels' embedded
   sheets). These are UIX components and the test harness has no jsdom / testing-library, so
   we cannot mount them to assert on rendered inputs. Instead this is a source TRIPWIRE
   (grep-style, sanctioned by the slice handoff): it reads the four detail-sheet source
   files and asserts (a) none of them reference a prose/tile-edit event, and (b) each still
   references its selection controls, so the scoping can't silently regress and we can't
   over-delete the selection affordances.

   The prose-edit event HANDLERS themselves stay registered — they are still dispatched by
   the parallel authoring-view cards (out of scope for the dense editor), so removing the
   handlers would break compilation. This guard pins that the DENSE EDITOR no longer reaches
   them."
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.string :as str]))

(def ^:private fs (js/require "fs"))
(def ^:private path (js/require "path"))

(defn- read-src
  "Read a src .cljs file (path relative to ui/advising-hub/src). Tries candidate roots so
   the test survives whether `node out/test/node-tests.js` runs from ui/advising-hub (the
   documented invocation) or the repo root."
  [rel]
  (let [candidates ["src" "ui/advising-hub/src"]
        found (some (fn [root]
                      (let [p (.join path (.cwd js/process) root rel)]
                        (when (.existsSync fs p) p)))
                    candidates)]
    (when-not found
      (throw (js/Error. (str "dense-editor-scope-test: could not locate src file '" rel
                             "' from cwd " (.cwd js/process)))))
    (.readFileSync fs found "utf8")))

;; For each dense-editor detail-sheet source file: the prose/tile-edit event names that MUST
;; be gone, and the selection-control event names that MUST remain (guarding over-deletion).
(def dense-editor-detail-sheets
  {;; Issue 0062 — the manual STR "Category" dropdown (::move-institution-to-category)
   ;; was removed (STR is a read-only engine fact); it is no longer a required control,
   ;; and must NOT reappear (forbidden).
   ;; Add-custom is DISABLED pending scope agreement (Cameron: revert the
   ;; workaround, 'Coming soon' hover) — the open-add-*-modal dispatches are now
   ;; FORBIDDEN in these sheets and each must render the shared coming-soon-button
   ;; instead (also required).
   "components/advisor/institution_detail_sheet.cljs"
   {:forbidden ["edit-institution-bullets" "move-institution-to-category"
                "open-add-program-modal"]
    :required  ["toggle-program-selection"
                "reorder-programs" "coming-soon-button"]}

   "components/advisor/scholarship_table_panel.cljs"
   {:forbidden ["edit-scholarship" "open-add-scholarship-modal"]
    :required  ["toggle-scholarship-selection" "reorder-scholarships"
                "coming-soon-button"]}

   "components/advisor/apprenticeship_table_panel.cljs"
   {:forbidden ["edit-apprenticeship-bullets" "open-add-apprenticeship-modal"]
    :required  ["toggle-apprenticeship-selection" "reorder-apprenticeships"
                "coming-soon-button"]}

   "components/advisor/short_term_program_table_panel.cljs"
   {:forbidden ["edit-short-term-program-field" "open-add-short-term-program-modal"]
    :required  ["toggle-short-term-program-selection" "reorder-short-term-programs"
                "coming-soon-button"]}

   ;; The remaining add surfaces carry the same cut:
   "components/advisor/college_table_panel.cljs"
   {:forbidden ["open-add-institution-modal"]
    :required  ["coming-soon-button"]}

   "components/advisor/institution_card.cljs"
   {:forbidden ["open-add-program-modal"]
    :required  ["coming-soon-button"]}

   "components/advisor/authoring/view.cljs"
   {:forbidden []
    :required  ["coming-soon-button"]}})

(deftest dense-editor-dispatches-no-prose-edit-events
  (doseq [[file {:keys [forbidden]}] dense-editor-detail-sheets]
    (let [src (read-src file)]
      (doseq [ev forbidden]
        (testing (str file " must not dispatch prose-edit event ::" ev)
          (is (not (str/includes? src ev))
              (str file " still references prose-edit event '" ev
                   "' — advisors must not edit sourced/Notion prose in the dense editor")))))))

(deftest dense-editor-keeps-selection-controls
  (doseq [[file {:keys [required]}] dense-editor-detail-sheets]
    (let [src (read-src file)]
      (doseq [ev required]
        (testing (str file " keeps selection control ::" ev)
          (is (str/includes? src ev)
              (str file " no longer references selection event '" ev
                   "' — select / promote / reorder / category / add-custom must remain")))))))
