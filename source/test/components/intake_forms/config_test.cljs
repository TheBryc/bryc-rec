(ns components.intake-forms.config-test
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.string :as str]
            [components.intake-forms.core :as intake-forms]))

(defn- field-by-key [field-key]
  (->> (:groups intake-forms/intake-config)
       (mapcat :fields)
       (filter #(= field-key (:key %)))
       first))

(deftest intake-sms-consent-is-optional-test
  (testing "student SMS consent is optional for Twilio compliance"
    (let [sms-field (field-by-key :intake.answered/sms-consent)]
      (is (some? sms-field))
      (is (= :boolean-checkbox (:type sms-field)))
      (is (true? (:optional sms-field)))
      (is (str/includes? (:description sms-field) "not required to complete this form")))))

(deftest guardian-phone-does-not-imply-sms-consent-test
  (testing "guardian phone copy does not treat phone submission as SMS consent"
    (let [guardian-phone-field (field-by-key :intake.answered/parent-guardian-phone)
          description (:description guardian-phone-field)]
      (is (some? guardian-phone-field))
      (is (not (str/includes? description "agree to receive text messages")))
      (is (not (str/includes? description "By providing this phone number"))))))
