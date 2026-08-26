(ns components.senior-forms.config-test
  (:require [cljs.test :refer [deftest testing is]]
            [components.senior-forms.core :as senior-forms]))

(deftest senior-form-has-transcript-upload-test
  (testing "senior form config includes grade transcript file upload field"
    (let [all-fields (->> (:groups senior-forms/senior-config)
                          (mapcat :fields))
          transcript-field (first (filter #(= :senior.answered/grade-transcript (:key %)) all-fields))]
      (is (some? transcript-field) "Should have a grade-transcript field")
      (is (= :file (:type transcript-field)) "Should be a :file type")
      (is (= ".pdf" (:accept transcript-field)) "Should only accept PDF")
      (is (true? (:optional transcript-field)) "Should be optional"))))

(deftest senior-form-id-test
  (testing "senior form has correct ID"
    (is (= "bryc-senior-intake-form" (:id senior-forms/senior-config)))))

(deftest senior-form-groups-test
  (testing "senior form has expected number of groups"
    (is (= 5 (count (:groups senior-forms/senior-config)))
        "Should have 5 groups (basics, academics, login, extracurriculars, goals)")))

(deftest senior-sms-consent-is-optional-test
  (testing "senior SMS consent is optional for Twilio compliance"
    (let [all-fields (->> (:groups senior-forms/senior-config)
                          (mapcat :fields))
          sms-field (first (filter #(= :senior.answered/sms-consent (:key %)) all-fields))]
      (is (some? sms-field))
      (is (= :boolean-checkbox (:type sms-field)))
      (is (true? (:optional sms-field)))
      (is (re-find #"not required to complete this form" (:description sms-field))))))

(deftest senior-work-keys-score-is-optional-test
  (testing "senior WorkKeys score is optional"
    (let [all-fields (->> (:groups senior-forms/senior-config)
                          (mapcat :fields))
          work-keys-field (first (filter #(= :senior.answered/work-keys-score (:key %)) all-fields))]
      (is (some? work-keys-field))
      (is (= :select (:type work-keys-field)))
      (is (= :work-keys-score (:crm-field work-keys-field)))
      (is (true? (:optional work-keys-field))))))
