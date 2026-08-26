(ns components.form-wizard.validation-test
  (:require [cljs.test :refer [deftest testing is]]
            [components.form-wizard.validation :as v]))

(deftest file-field-required-validation-test
  (testing "file field with no value returns error when required"
    (is (= "Please upload a file"
           (v/validate-field-required :test/file nil false true :file nil))))

  (testing "file field with empty string returns error when required"
    (is (= "Please upload a file"
           (v/validate-field-required :test/file "" false true :file nil))))

  (testing "file field with value passes when required"
    (is (nil? (v/validate-field-required :test/file "some-file-id" false true :file nil))))

  (testing "file field with no value passes when optional"
    (is (nil? (v/validate-field-required :test/file nil true true :file nil))))

  (testing "file field skips validation when not visible"
    (is (nil? (v/validate-field-required :test/file nil false false :file nil)))))

(deftest boolean-checkbox-required-validation-test
  (testing "required boolean checkbox fails when missing or false"
    (is (= "This field is required"
           (v/validate-field-required :test/consent nil false true :boolean-checkbox nil)))
    (is (= "This field is required"
           (v/validate-field-required :test/consent false false true :boolean-checkbox nil))))

  (testing "required boolean checkbox passes only when true"
    (is (nil? (v/validate-field-required :test/consent true false true :boolean-checkbox nil))))

  (testing "optional or hidden boolean checkbox does not require true"
    (is (nil? (v/validate-field-required :test/consent false true true :boolean-checkbox nil)))
    (is (nil? (v/validate-field-required :test/consent false false false :boolean-checkbox nil)))))

(deftest field-visibility-test
  (testing "field with no show-when is always visible"
    (is (true? (v/field-visible? {:key :test} {}))))

  (testing "field with show-when equals checks answer"
    (is (true? (v/field-visible?
                {:key :test :show-when {:field-key :role :equals "Student"}}
                {:role "Student"})))
    (is (false? (v/field-visible?
                 {:key :test :show-when {:field-key :role :equals "Student"}}
                 {:role "Parent"})))))

(deftest number-format-validation-test
  (let [gpa-field {:input-type "number" :min "0" :max "4" :step "0.01"}
        act-field {:input-type "number" :min "1" :max "36" :step "1"}]
    (testing "GPA accepts decimal values in range"
      (is (nil? (v/validate-field-format :gpa "3.75" true "number" gpa-field))))

    (testing "GPA rejects values outside 0-4 range"
      (is (= "Please enter a number 0 or higher"
             (v/validate-field-format :gpa "-0.1" true "number" gpa-field)))
      (is (= "Please enter a number 4 or lower"
             (v/validate-field-format :gpa "4.1" true "number" gpa-field))))

    (testing "ACT requires whole number in 1-36 range"
      (is (= "Please enter a whole number"
             (v/validate-field-format :act "30.5" true "number" act-field)))
      (is (= "Please enter a number 36 or lower"
             (v/validate-field-format :act "37" true "number" act-field))))))

(deftest field-required-test
  (testing "no :required-when and no :optional -> required (default)"
    (is (true? (v/field-required? {:key :x} {}))))

  (testing ":optional true (no :required-when) -> not required"
    (is (false? (v/field-required? {:key :x :optional true} {}))))

  (testing ":required-when :equals decides required-ness"
    (let [f {:key :x :optional true
             :required-when {:field-key :c :equals "I already know what I want to do"}}]
      (is (true? (v/field-required? f {:c "I already know what I want to do"})))
      (is (false? (v/field-required? f {:c "I'm not sure yet"})))
      (is (false? (v/field-required? f {})) "blank antecedent -> not required")))

  (testing ":required-when overrides static :optional when its predicate matches"
    ;; even though :optional true, required-when match makes it required
    (is (true? (v/field-required?
                {:key :x :optional true :required-when {:field-key :c :equals "y"}}
                {:c "y"}))))

  (testing ":required-when :not-equals and :has-value?"
    (is (true? (v/field-required?
                {:key :x :required-when {:field-key :c :not-equals "skip"}} {:c "go"})))
    (is (false? (v/field-required?
                 {:key :x :required-when {:field-key :c :not-equals "skip"}} {:c "skip"})))
    (is (true? (v/field-required?
                {:key :x :required-when {:field-key :c :has-value? true}} {:c "anything"})))
    (is (false? (v/field-required?
                 {:key :x :required-when {:field-key :c :has-value? true}} {:c ""})))))

(deftest progress-respects-conditional-required-test
  (testing "calculate-progress denominator follows :required-when as certainty flips"
    (let [groups [{:fields [{:key :a
                             :required-when {:field-key :c :equals "know"}}]}]]
      ;; required (counts toward denominator) but empty -> 0% complete
      (is (= 0 (v/calculate-progress groups {:c "know"})))
      ;; not required (denominator 0) -> progress 0 (no required fields)
      (is (= 0 (v/calculate-progress groups {:c "unsure"}))))))

(deftest progress-requires-boolean-checkbox-true-test
  (testing "required boolean checkbox is complete only when true"
    (let [groups [{:fields [{:key :consent :type :boolean-checkbox}]}]]
      (is (= 0 (v/calculate-progress groups {})))
      (is (= 0 (v/calculate-progress groups {:consent false})))
      (is (= 1 (v/calculate-progress groups {:consent true}))))))
