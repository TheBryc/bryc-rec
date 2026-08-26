(ns components.rec-demo.transfer-plan-test
  "0064 — the Transfer Plan renderer guards (present-by-data). The engine now PRODUCES
   :destination / :framing / :cost-note / :articulation-note / per-career :desc, and the
   renderer already reads them; these tests pin the guards through a real dom-server
   render:
     - a full transfer program renders destination + 2+2 framing + both bullets + the
       curated 'where it leads' career card with its one-line description;
     - a nil :destination renders NO 'Transfer destination' box (not an empty one);
     - a GENERAL degree (no :destination-careers) renders the destination + bullets but
       NO 'Where it leads' career block."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.rec-demo.core :as core]))

(defn- render [pathway]
  (dom-server/render-to-string ($ core/transfer-plan-section {:pathway pathway})))

(def full-tp
  {:destination "Business Administration (BS)"
   :framing "2 + 2: finish the associate at BRCC, then transfer toward the bachelor's."
   :la-guarantee "Louisiana's statewide transfer degree (AALT/ASLT) guarantees your lower-division general-education credits transfer to any Louisiana public university."
   :cost-note "BRCC tuition now (~$3,237/yr); the bachelor's portion continues at a 4-year — full cost-to-degree depends on the destination you choose."
   :articulation-note "Course articulation, credit-applicability, and transfer-completion rates aren't in the repo yet — your advisor maps them (BOR Statewide Articulation; CCRC/NSC transfer outcomes)."
   :destination-careers [{:title "Chief Executives" :soc "11-1011"
                          :desc "Determine and formulate policies. Shown at the bachelor's level (the transfer destination)."}]})

(deftest full-transfer-program-renders-everything
  (testing "destination box, 2+2 framing, both bullets, and the described career card render"
    (let [html (render {:transfer-plan full-tp})]
      (is (re-find #"Transfer destination" html) "destination box heading renders")
      (is (re-find #"Business Administration \(BS\)" html) "destination label renders")
      (is (re-find #"finish the associate at BRCC" html) "2+2 framing renders")
      (is (re-find #"BRCC tuition now" html) "cost bullet renders")
      (is (re-find #"aren.{0,6}t in the repo yet" html) "articulation caveat renders")
      (is (re-find #"Where it leads" html) "'Where it leads' block renders")
      (is (re-find #"Chief Executives" html) "curated career title renders")
      (is (re-find #"Shown at the bachelor" html) "the O*NET-grounded :desc renders"))))

(deftest nil-destination-renders-no-box
  (testing "present-by-data: no :destination ⇒ the 'Transfer destination' box is NOT rendered"
    (let [html (render {:transfer-plan (dissoc full-tp :destination :framing)})]
      (is (not (re-find #"Transfer destination" html)) "no empty destination box")
      (is (not (re-find #"finish the associate at BRCC" html)) "framing gone with the box")
      ;; the rest of the plan still renders (guarantee + bullets)
      (is (re-find #"BRCC tuition now" html) "the cost bullet still renders"))))

(deftest general-degree-renders-no-careers
  (testing "a GENERAL degree (generic destination, no :destination-careers) renders the
            destination + bullets but NO 'Where it leads' career block"
    (let [tp (-> full-tp
                 (assoc :destination "a bachelor's degree of your choice")
                 (dissoc :destination-careers))
          html (render {:transfer-plan tp})]
      (is (re-find #"a bachelor.{0,6}s degree of your choice" html) "generic destination renders")
      (is (re-find #"BRCC tuition now" html) "cost bullet renders")
      (is (not (re-find #"Where it leads" html)) "no 'Where it leads' block for a general degree"))))

(deftest no-cost-note-renders-no-empty-bullet
  (testing "present-by-data: a nil :cost-note renders only the articulation bullet (no blank)"
    (let [html (render {:transfer-plan (dissoc full-tp :cost-note)})]
      (is (not (re-find #"BRCC tuition now" html)) "no cost bullet")
      (is (re-find #"aren.{0,6}t in the repo yet" html) "articulation bullet still renders"))))
