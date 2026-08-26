(ns components.crm.student.testing-history-test
  (:require [cljs.test :refer [deftest is testing]]
            [components.crm.student.testing-history :as testing-history]))

(deftest normalizes-legacy-act-history-test
  (testing "legacy date/composite entries render as ACT records"
    (is (= [{:date "2025-10-01"
             :score "20"
             :test-name "ACT"
             :test-date "2025-10-01"
             :composite-score 20}]
           (testing-history/normalized-history [{:date "2025-10-01" :score "20"}])))))

(deftest act-summary-test
  (let [history [{:test-date "2025-09-01" :test-name "ACT" :composite-score 18}
                 {:test-date "2025-12-01" :test-name "ACT" :composite-score 24}
                 {:test-date "2025-10-01" :test-name "SAT" :total-score 1100}]]
    (testing "highest ACT ignores non-ACT records"
      (is (= 24 (testing-history/highest-act-score history))))
    (testing "entering score prefers existing saved ACT score"
      (is (= 19 (testing-history/entering-act-score history 19))))
    (testing "entering score falls back to earliest ACT record"
      (is (= 18 (testing-history/entering-act-score history nil))))
    (testing "points gained uses highest minus entering"
      (is (= 5 (testing-history/points-gained history 19))))))

(deftest tops-eligibility-test
  (testing "maps highest ACT score to existing TOPS award labels"
    (is (= "—" (testing-history/tops-eligibility-label nil)))
    (is (= "None" (testing-history/tops-eligibility-label 19)))
    (is (= "TOPS Opportunity" (testing-history/tops-eligibility-label 20)))
    (is (= "TOPS Performance" (testing-history/tops-eligibility-label 23)))
    (is (= "TOPS Honors" (testing-history/tops-eligibility-label 27)))))

(deftest required-test-entry-test
  (testing "ACT requires date, name, and composite"
    (is (false? (testing-history/required-test-entry? {:test-date "2025-10-01" :test-name "ACT"})))
    (is (true? (testing-history/required-test-entry? {:test-date "2025-10-01"
                                                      :test-name "ACT"
                                                      :composite-score 22}))))
  (testing "WorkKeys requires NCRC"
    (is (false? (testing-history/required-test-entry? {:test-date "2025-10-01" :test-name "WorkKeys"})))
    (is (true? (testing-history/required-test-entry? {:test-date "2025-10-01"
                                                      :test-name "WorkKeys"
                                                      :ncrc-credential "Silver"})))))
