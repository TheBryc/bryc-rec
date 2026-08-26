(ns components.crm.student.tabs.core-test
  "Tests for CRM student section routing and profile flavor selection."
  (:require [cljs.test :refer [deftest is testing]]
            [components.crm.student.tabs.core :as sections]))

(deftest fellow-sections-test
  (testing "fellow statuses use the PDF feedback profile flavor"
    (is (= ["personal" "bryc-program" "academics" "testing" "senior-year" "health" "communications"]
           (mapv :id (sections/visible-sections :fellow))))
    (is (= ["personal" "bryc-program" "academics" "testing" "senior-year" "health" "communications"]
           (mapv :id (sections/visible-sections :vulnerable-fellow))))))

(deftest non-fellow-sections-test
  (testing "advisee statuses use the advisee CRM profile sections"
    (is (= ["status" "personal" "academics" "senior-year"
            "financial-aid" "activities" "credentials" "family" "communications"]
           (mapv :id (sections/visible-sections :advisee)))))
  (testing "advisee profile uses its own screen component without changing section filtering"
    (is (contains? sections/advisee-profile-statuses :advisee))
    (is (not (contains? sections/fellow-profile-statuses :advisee))))
  (testing "alumni keep legacy sections plus alumni-specific sections"
    (is (contains? (set (map :id (sections/visible-sections :alumni))) "postsecondary"))
    (is (not (contains? (set (map :id (sections/visible-sections :alumni))) "testing")))))

(deftest default-section-test
  (testing "default section remains the legacy default"
    (is (= "communications" sections/default-section-id))))
