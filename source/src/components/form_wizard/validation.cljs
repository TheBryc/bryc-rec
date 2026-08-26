(ns components.form-wizard.validation
  "Validation utilities and answer transformation for form wizard"
  (:require [clojure.string :as str]))

;; Validation functions
(defn validate-field-format
  "Basic format validation for common field types.
   Full validation is handled by CRM backend."
  ([_field-key value field-visible? input-type] (validate-field-format _field-key value field-visible? input-type nil))
  ([_field-key value field-visible? input-type field]
  (let [;; Extract actual value for either-or "other" fields
        actual-value (if (and (map? value) (:other value))
                      (:other value)
                      value)]
    (cond
      ;; Skip validation entirely if field is not visible
      (not field-visible?)
      nil

      ;; Skip validation if field is empty (let required check handle it later)
      (or (nil? actual-value) (= actual-value "") (and (vector? actual-value) (empty? actual-value)))
      nil

      ;; Basic email format check
      (and (= input-type "email") (string? actual-value))
      (when-not (re-matches #"^[^@\s]+@[^@\s]+\.[^@\s]+$" actual-value)
        "Please enter a valid email address")

      ;; Basic phone format check (10 digits)
      (and (= input-type "tel") (string? actual-value))
      (when-not (= 10 (count (str/replace actual-value #"[^\d]" "")))
        "Please enter a 10-digit phone number")

      ;; Basic URL format check
      (and (= input-type "url") (string? actual-value))
      (when-not (re-matches #"^https?://.*" actual-value)
        "Please enter a valid URL starting with http:// or https://")

      ;; Number range/integer validation
      (and (= input-type "number") (some? actual-value) (not= actual-value ""))
      (let [n (js/parseFloat actual-value)
            min-val (some-> (:min field) js/parseFloat)
            max-val (some-> (:max field) js/parseFloat)
            step (some-> (:step field) js/parseFloat)]
        (cond
          (js/isNaN n) "Please enter a valid number"
          (and (= step 1.0) (not= n (js/Math.floor n))) "Please enter a whole number"
          (and min-val (< n min-val)) (str "Please enter a number " min-val " or higher")
          (and max-val (> n max-val)) (str "Please enter a number " max-val " or lower")
          :else nil))

      :else nil))))

(defn validate-field-required
  "Check if required field is empty (for submit validation)"
  [field-key value field-optional? field-visible? field-type min-items]
  (let [;; Extract actual value for either-or "other" fields
        actual-value (if (and (map? value) (:other value))
                      (:other value)
                      value)]
    (cond
      ;; Skip validation entirely if field is not visible
      (not field-visible?)
      nil

      ;; Handle file fields — value is a file-id string
      (and (= field-type :file)
           (not field-optional?)
           (or (nil? actual-value) (= actual-value "")))
      "Please upload a file"

      ;; Handle dynamic list fields with min-items requirement
      (and (= field-type :dynamic-list)
           (vector? actual-value)
           (< (count actual-value) (or min-items 0)))
      (str "Please add at least " (or min-items 1) " item" (when (> (or min-items 1) 1) "s"))

      ;; For required consent checkboxes, `false` is present but incomplete.
      (and (= field-type :boolean-checkbox)
           (not field-optional?)
           (not (true? actual-value)))
      "This field is required"

      ;; If field is visible and empty, check if it's optional
      (and (or (nil? actual-value)
               (= actual-value "")
               (and (vector? actual-value) (empty? actual-value)))
           (not field-optional?))
      "This field is required"

    ;; Field has value or is optional
    :else
    nil)))

(defn validate-field-complete
  "Full validation including both format and required checks"
  [field-key value field-optional? field-visible? field-type min-items input-type & [field]]
  (or (validate-field-required field-key value field-optional? field-visible? field-type min-items)
      (validate-field-format field-key value field-visible? input-type field)))

;; Visibility predicates
(defn field-visible?
  "Check if a field should be visible based on its visibility conditions"
  [field answers]
  (if-let [show-when (:show-when field)]
    (let [{:keys [field-key has-value? equals]} show-when]
      (cond
        has-value?
        (let [value (get answers field-key)]
          (and (some? value)
               (not= value "")
               (if (coll? value)
                 (seq value)  ; Use seq instead of not empty
                 true)))

        equals
        (= (get answers field-key) equals)

        :else
        true))
    true)) ; Field is visible by default if no conditions

(defn group-visible?
  "Check if a group should be visible based on its visibility conditions"
  [group answers]
  (if-let [show-when (:show-when group)]
    (let [{:keys [field-key has-value? equals not-equals]} show-when]
      (cond
        has-value?
        (let [value (get answers field-key)]
          (and (some? value)
               (not= value "")
               (if (coll? value)
                 (seq value)
                 true)))

        equals
        (= (get answers field-key) equals)

        not-equals
        (not= (get answers field-key) not-equals)

        :else
        true))
    true)) ; Group is visible by default if no conditions

(defn field-required?
  "Whether a field is required given the current answers. Mirrors the
   `:show-when` predicate shape via `:required-when {:field-key … :equals /
   :has-value? / :not-equals …}`. When `:required-when` is present its predicate
   decides required-ness; otherwise falls back to the static `:optional` flag
   (default required). This single predicate subsumes the scattered `:optional`
   checks so conditional-required works everywhere."
  [field answers]
  (if-let [req (:required-when field)]
    (let [{:keys [field-key has-value? equals not-equals]} req]
      (cond
        has-value?
        (let [value (get answers field-key)]
          (and (some? value)
               (not= value "")
               (if (coll? value) (boolean (seq value)) true)))

        equals
        (= (get answers field-key) equals)

        not-equals
        (not= (get answers field-key) not-equals)

        :else
        true))
    (not (:optional field false))))

;; Answer transformation functions
(defn transform-dynamic-list-item
  "Transform a single dynamic list item, handling either-or fields within it"
  [item]
  (reduce-kv (fn [acc item-key item-value]
               (assoc acc item-key
                      (if (and (map? item-value) (:other item-value))
                        ;; Extract "other" value for either-or fields
                        (:other item-value)
                        ;; Use value as-is for regular fields
                        item-value)))
             {} item))

(defn transform-answers-to-backend
  "Transform UI answers to backend schema format"
  [answers]
  (->> answers
       (map (fn [[field-key value]]
              (let [;; All fields now use :answer as the data key
                    data-key :answer
                    ;; Process dynamic list items to handle either-or fields within them
                    clean-value (cond
                                  ;; Dynamic list fields - transform each item
                                  (vector? value)
                                  (mapv transform-dynamic-list-item value)

                                  ;; Either-or "other" option - extract the string value
                                  (and (map? value) (:other value))
                                  (:other value)

                                  ;; Regular option - use as-is
                                  :else
                                  value)]
                {field-key {data-key clean-value}})))
       vec))

(defn transform-dynamic-list-item-from-backend
  "Transform a single dynamic list item from backend.
   Simply passes through values as-is since CRM stores actual values."
  [item _field-key _config]
  ;; CRM stores values directly, no transformation needed
  item)

(defn transform-answers-from-backend
  "Transform backend answers to UI format.
   Handles vector format (from auto-save) and map format,
   and extracts :answer values from field data."
  [backend-answers & [{:keys [config]}]]
  (let [;; Handle both vector format (from auto-save) and map format
        answer-entries (if (vector? backend-answers)
                         (mapcat seq backend-answers) ; Convert vector of maps to flat entries
                         backend-answers)]
    (->> answer-entries
         (map (fn [[field-key field-data]]
                ;; field-key is already the UI key (e.g., :intake.answered/student-highschool)
                ;; field-data is {:answer "value"}
                (let [value (:answer field-data)]
                  (cond
                    ;; Check if this is a dynamic list field
                    (vector? value)
                    [field-key (mapv #(transform-dynamic-list-item-from-backend % field-key config) value)]

                    ;; All other fields pass through as-is
                    :else
                    [field-key value]))))
         (into {}))))

;; Progress calculation
(defn calculate-progress
  "Calculate form progress based on completed required fields"
  [groups answers]
  (let [all-visible-fields (mapcat :fields groups)
        visible-required-fields (filter #(and (field-visible? % answers)
                                              (field-required? % answers))
                                       all-visible-fields)
        total-fields (count visible-required-fields)
        completed-fields (count (filter #(let [field-key (:key %)
                                               value (get answers field-key)]
                                           (if (= (:type %) :boolean-checkbox)
                                             (true? value)
                                             (and (some? value)
                                                  (not= value "")
                                                  (if (coll? value) (seq value) true))))
                                       visible-required-fields))]
    (if (> total-fields 0)
      (/ completed-fields total-fields)
      0)))

;; Field lookup helper
(defn find-field-in-groups
  "Find field definition by key in visible groups"
  [groups field-key]
  (some (fn [group]
          (some (fn [field]
                  (when (= (:key field) field-key)
                    field))
                (:fields group)))
        groups))

;; Email validation
(defn validate-email
  "Validate email format"
  [email]
  (cond
    (empty? email) "Email is required"
    (not (re-matches #".+@.+\..+" email)) "Please enter a valid email address"
    :else nil))
