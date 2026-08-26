(ns components.shared.str-groups-test
  "Issue 0062 — the SHARED PURE STR-group display helpers, the ONE definition reused
   by the student viewer AND the advisor dense editor. Behavior is exercised through
   the public fns only (data→data)."
  (:require [cljs.test :refer [deftest testing is]]
            [components.shared.str-groups :as sg]))

(defn- inst [name k]
  {:institution-name name :str-badge {:key k :label name}})

(deftest str-group-order-and-labels
  (testing "fixed display order + human labels are stable"
    (is (= ["open" "safety" "target" "reach"] sg/str-group-order))
    (is (= "Open Admission" (get sg/str-group-labels "open")))
    (is (= "Safety" (get sg/str-group-labels "safety")))
    (is (= "Target" (get sg/str-group-labels "target")))
    (is (= "Reach" (get sg/str-group-labels "reach")))))

(deftest str-group-key-prefers-own-badge
  (testing "the school's OWN :str-badge :key drives the group (string or keyword)"
    (is (= "open"   (sg/str-group-key (inst "BRCC" "open"))))
    (is (= "target" (sg/str-group-key (inst "Nicholls" "target"))))
    (is (= "safety" (sg/str-group-key {:str-badge {:key :safety}}))
        "a keyword :key is name-coerced")))

(deftest str-group-key-falls-back-then-defaults
  (testing "no badge → :admissions-likelihood tier; unknown → safety (never dropped)"
    (is (= "reach"  (sg/str-group-key {:admissions-likelihood "Reach"})))
    (is (= "target" (sg/str-group-key {:admissions-likelihood "Target"})))
    (is (= "safety" (sg/str-group-key {:admissions-likelihood "Unknown"})))
    (is (= "safety" (sg/str-group-key {:institution-name "Bare"})))))

(deftest group-institutions-by-str-orders-and-omits-empty
  (testing "mixed institutions re-group into open→safety→target→reach, empty groups omitted"
    (let [insts [(inst "Reach U" "reach") (inst "BRCC" "open")
                 (inst "Target State" "target")]
          grouped (sg/group-institutions-by-str insts)]
      (is (= ["open" "target" "reach"] (mapv first grouped))
          "fixed order, safety omitted (present-by-data)")
      (is (= ["BRCC"] (map :institution-name (second (nth grouped 0)))))
      (is (= 3 (reduce + (map (comp count second) grouped))) "none dropped"))))

(deftest group-institutions-by-str-preserves-input-order-within-group
  (testing "group-by keeps input order within each group (needed for bucket-order mapping)"
    (let [insts [(inst "T1" "target") (inst "T2" "target") (inst "T3" "target")]
          [[_ members]] (sg/group-institutions-by-str insts)]
      (is (= ["T1" "T2" "T3"] (map :institution-name members))))))

(deftest editor-grouping-when-pool-bucket-differs-from-str-badge
  (testing "a school pooled under one admission bucket but badged another displays under its OWN :str-badge
            (the Nicholls case — :safety pool bucket, 'target' badge — reads Target in the editor, matching the viewer)"
    (let [;; What the editor feeds group-institutions-by-str: the flattened pool with each
          ;; school carrying its source pool bucket. Grouping keys on :str-badge ONLY, so the
          ;; mismatched school leaves its pool bucket's heading and joins its badge's group.
          flattened [{:id "saf1" :str-badge {:key "safety"} :pool-bucket :safety}
                     {:id "nicholls" :str-badge {:key "target"} :pool-bucket :safety}
                     {:id "slu" :str-badge {:key "target"} :pool-bucket :target}]
          grouped (sg/group-institutions-by-str flattened)
          group-of (fn [id]
                     (some (fn [[k members]] (when (some #(= id (:id %)) members) k)) grouped))]
      (is (= "safety" (group-of "saf1")))
      (is (= "target" (group-of "nicholls")) "Nicholls (safety pool bucket) displays under Target, its str-badge")
      (is (= "target" (group-of "slu")))
      (is (= ["saf1"] (map :id (second (first (filter #(= "safety" (first %)) grouped)))))
          "the safety heading no longer contains the target-badged school"))))
