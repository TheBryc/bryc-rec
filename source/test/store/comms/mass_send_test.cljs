(ns store.comms.mass-send-test
  (:require [cljs.test :refer [deftest is testing]]
            [store.comms.mass-send :as ms]))

(deftest previewable-predicates-test
  (testing "empty or incomplete filters do not trigger an audience preview"
    (is (not (ms/previewable-predicates? [])))
    (is (not (ms/previewable-predicates? [{:id "p1" :slug nil :op nil :value nil}])))
    (is (not (ms/previewable-predicates? [{:id "p1" :slug "graduation-year" :op nil :value "2027"}])))
    (is (not (ms/previewable-predicates? [{:id "p1" :slug "graduation-year" :op :eq :value ""}])))
    (is (not (ms/previewable-predicates? [{:id "p1" :slug "graduation-year" :op :is-any-of :value []}]))))
  (testing "complete filters can preview a candidate segment"
    (is (ms/previewable-predicates? [{:id "p1" :slug "graduation-year" :op :eq :value "2027"}]))
    (is (ms/previewable-predicates? [{:id "p1" :slug "messaging-opt-out" :op :is-false :value nil}]))
    (is (ms/previewable-predicates? [{:id "p1" :slug "tags" :scope :tags :op :has-any-of :value ["cohort-a"]}]))))

(deftest html-fallback-text-test
  (is (= "Hi Maria\n\nUse & check <portal>"
         (ms/html->text "<p>Hi <strong>Maria</strong></p><p>Use &amp; check &lt;portal&gt;</p>"))))
