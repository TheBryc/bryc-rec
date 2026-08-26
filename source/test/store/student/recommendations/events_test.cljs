(ns store.student.recommendations.events-test
  "Pure re-frame event tests for the student recommendations store. Tests the event
   HANDLER functions directly (data->data through the public handler fn) — never the
   DOM, never a live fetch. Survives view refactors."
  (:require [cljs.test :refer [deftest testing is]]
            [store.student.recommendations.events :as events]))

;; ===========================================================================
;; In-page Student preview (0051) — the dense advisor editor seeds THIS student
;; store from the advisor store's CURRENT resolved selection + advisor message so
;; it can render the REAL redesigned student page inline. These tests pin the seed
;; handler: it writes the advisor's resolved + message under [:student
;; :recommendations …] (which the student subs read) AND leaves the sibling advisor
;; store path untouched (so previewing never corrupts the advisor's editing state).
;; ===========================================================================

(deftest seed-preview-writes-resolved-and-message-under-student-path
  (let [advisor-resolved {:student {:name "Ada Lovelace"}
                          :institutions {:safety [{:id "s1"}]}
                          :scholarships [{:id "sch-1"}]}
        db {:advisor {:recommendations {:resolved advisor-resolved
                                        :advisor-message "Great fit list."
                                        :selection {:institutions {:safety [{:id "s1" :programs []}]}}}}}
        db' (events/seed-preview-db
             db
             [::events/seed-preview {:resolved advisor-resolved
                                     :advisor-message "Great fit list."}])]
    (testing "the student store now holds the advisor's resolved (what the student subs read)"
      (is (= advisor-resolved (get-in db' [:student :recommendations :resolved]))))
    (testing "the advisor message is carried onto the student store"
      (is (= "Great fit list." (get-in db' [:student :recommendations :advisor-message]))))
    (testing "the preview is ready to render immediately — not loading, no error"
      (is (false? (get-in db' [:student :recommendations :loading?])))
      (is (nil? (get-in db' [:student :recommendations :error]))))
    (testing "seeding the student store does NOT corrupt the sibling advisor store"
      (is (= (:advisor db) (:advisor db'))
          "the advisor recommendations state (resolved, message, selection) is untouched"))))

;; ===========================================================================
;; Advisor identity card (0059) — the :advisor map from the Student-view query
;; (load-success) AND the in-editor preview (seed-preview) must land under
;; [:student :recommendations :advisor] where the ::advisor sub reads it.
;; ===========================================================================

(def ^:private sample-advisor
  {:name "Alex Rivera" :email "alex@thebryc.org"
   :scheduling-url "https://cal.example/alex" :profile-photo-url nil})

(deftest load-success-carries-advisor
  (let [db' (events/load-success-db
             {}
             [::events/load-success {:pool {:x 1}
                                     :resolved {:student {:name "Maya Johnson"}}
                                     :advisor sample-advisor
                                     :advisor-message "Great list."
                                     :pool-id "p1"}])]
    (testing "the advisor map lands under the student store advisor path"
      (is (= sample-advisor (get-in db' [:student :recommendations :advisor]))))
    (testing "the rest of the payload still lands (no regression)"
      (is (= {:student {:name "Maya Johnson"}} (get-in db' [:student :recommendations :resolved])))
      (is (= "Great list." (get-in db' [:student :recommendations :advisor-message])))
      (is (false? (get-in db' [:student :recommendations :loading?]))))))

(deftest seed-preview-carries-advisor
  (let [db' (events/seed-preview-db
             {}
             [::events/seed-preview {:resolved {:student {:name "Ada Lovelace"}}
                                     :advisor-message "Great fit list."
                                     :advisor sample-advisor}])]
    (testing "the preview carries the advisor onto the student store"
      (is (= sample-advisor (get-in db' [:student :recommendations :advisor]))))))
