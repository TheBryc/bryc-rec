(ns components.advisor.authoring.overlays-test
  "Issue 0016 — S1. Tests for the PURE cutover-flag + advisor-only overlay fns that
   drive the advisor-default authoring read-through. Behavior is exercised through the
   public fns only (data→data), so it survives refactors of the view."
  (:require [cljs.test :refer [deftest testing is are]]
            [components.advisor.authoring.overlays :as ov]))

;; ===========================================================================
;; Cutover flag — DEFAULT OFF, per-tenant-scopeable
;; ===========================================================================

(deftest cutover-default-off
  (testing "with no config, or an empty config, the cutover is OFF (old UI shows)"
    (is (false? (ov/authoring-cutover-enabled? nil "tenant-a")))
    (is (false? (ov/authoring-cutover-enabled? {} "tenant-a")))
    (is (false? (ov/authoring-cutover-enabled? {:default? false :tenants #{}} "tenant-a")))))

(deftest cutover-global-default-on
  (testing "global default on → enabled for ANY tenant (even nil)"
    (is (true? (ov/authoring-cutover-enabled? {:default? true} "tenant-a")))
    (is (true? (ov/authoring-cutover-enabled? {:default? true} nil)))))

(deftest cutover-per-tenant-allowlist
  (testing "default off, but the tenant is on the allowlist → enabled for THAT tenant only"
    (let [cfg {:default? false :tenants #{"tenant-a" "tenant-b"}}]
      (is (true?  (ov/authoring-cutover-enabled? cfg "tenant-a")))
      (is (true?  (ov/authoring-cutover-enabled? cfg "tenant-b")))
      (is (false? (ov/authoring-cutover-enabled? cfg "tenant-c")) "a tenant not on the list stays OFF")
      (is (false? (ov/authoring-cutover-enabled? cfg nil)) "no tenant + default off → OFF"))))

;; Issue 0022 — S6. URL-override kill-switch composed over the config predicate.
(deftest cutover-with-override-url-kill-switch
  (testing "?authoring=off is the kill-switch — forces the OLD view even when the global
            default is ON (the S6 default)"
    (is (false? (ov/cutover-with-override "off" {:default? true} "tenant-a")))
    (is (false? (ov/cutover-with-override "off" {:default? true} nil))))
  (testing "?authoring=on forces the authoring view even when config is OFF (QA preview)"
    (is (true? (ov/cutover-with-override "on" {:default? false :tenants #{}} nil)))
    (is (true? (ov/cutover-with-override "on" nil "tenant-c"))))
  (testing "no override → the config default decides (default-ON → authoring; OFF → old)"
    (is (true?  (ov/cutover-with-override nil {:default? true} nil)))
    (is (false? (ov/cutover-with-override nil {:default? false :tenants #{}} "tenant-a")))
    (is (false? (ov/cutover-with-override "" {:default? false :tenants #{}} "tenant-a"))
        "an empty/blank override is not a downgrade — falls through to config")))

;; ===========================================================================
;; Source provenance badge (AI vs advisor-added)
;; ===========================================================================

(deftest source-badge-ai-generated
  (testing "an :ai (or missing) source → AI-Generated badge"
    (are [item] (= {:key :ai-generated :label "AI-Generated" :advisor-added? false}
                   (ov/source-badge item))
      {:source :ai}
      {:source "ai"}
      {}                          ;; missing source → AI-Generated
      {:id 1 :program-title "X"})))

(deftest source-badge-advisor-added
  (testing "a :custom source → Advisor-Added badge (keyword OR string source resolves)"
    (is (= {:key :advisor-added :label "Advisor-Added" :advisor-added? true}
           (ov/source-badge {:source :custom})))
    (is (= {:key :advisor-added :label "Advisor-Added" :advisor-added? true}
           (ov/source-badge {:source "custom"})))))

(deftest source-badge-present-for-all-record-types
  (testing "the badge reads only the shared :source field, so it resolves for institutions,
            programs, scholarships, apprenticeships, and short-term programs alike"
    (are [item advisor-added?] (= advisor-added? (:advisor-added? (ov/source-badge item)))
      {:institution-name "Nicholls" :source :ai}            false
      {:program-title "Nursing" :source :custom}            true
      {:name "MASWE Scholarship" :source :ai}               false
      {:apprenticeship-title "Carpentry" :source :custom}   true
      {:program-title "Medical Coding" :source :ai}         false)))

;; ===========================================================================
;; Customized indicator
;; ===========================================================================

(deftest customized-signals
  (testing "customized? is true on any advisor-override signal (present-by-data)"
    (is (true?  (ov/customized? {:source :custom})) "advisor-added is inherently customized")
    (is (true?  (ov/customized? {:is-customized? true})) "explicit engine flag")
    (is (true?  (ov/customized? {:customized? true})) "alternate explicit flag")
    (is (true?  (ov/customized? {:source :ai :text-edits {:why-fits-bullets ["edited"]}}))
        "a non-empty text override marks an AI item customized"))
  (testing "a pristine AI item with no overrides is NOT customized"
    (is (false? (ov/customized? {:source :ai})))
    (is (false? (ov/customized? {:source :ai :text-edits {}})) "an empty text-edits map is not an override")
    (is (false? (ov/customized? {})))))

(deftest customized-chip-only-for-edited-ai-items
  (testing "the Customized chip shows for an AI item the advisor overrode …"
    (is (= {:key :customized :label "Customized"}
           (ov/customized-chip {:source :ai :text-edits {:why-fits-bullets ["edited"]}}))))
  (testing "… but NOT for a pristine AI item, and NOT for an advisor-added item
            (that one is already flagged by its Advisor-Added source badge)"
    (is (nil? (ov/customized-chip {:source :ai})))
    (is (nil? (ov/customized-chip {:source :custom})) "advisor-added → no redundant chip")))

;; ===========================================================================
;; Legacy-pool migration detection (0021)
;; ===========================================================================

(deftest needs-migration-old-shape-triggers
  (testing "a resolved set with an OLD-shape record (no :sections) needs migration"
    (is (true? (ov/needs-migration?
                {:institutions {:safety [{:id 1 :institution-name "Nicholls"}] ;; no :sections
                                :target [] :reach []}})))
    (is (true? (ov/needs-migration?
                {:institutions {:safety [{:id 1 :sections [:about-school]
                                          :programs [{:id 2}]}] ;; nested program old-shape
                                :target [] :reach []}}))
        "an old-shape NESTED program triggers migration even if the school is new-shape")
    (is (true? (ov/needs-migration?
                {:institutions {:safety [] :target [] :reach []}
                 :short-term-programs [{:id 9 :program-title "LPN"}]}))
        "an old-shape short-term program triggers migration")))

(deftest needs-migration-new-shape-does-not-trigger
  (testing "a fully new-shape resolved set (every record carries :sections) does NOT migrate"
    (is (false? (ov/needs-migration?
                 {:institutions {:safety [{:id 1 :sections [:about-school :costs]
                                           :programs [{:id 2 :sections [:overview]}]}]
                                 :target [] :reach []}
                  :short-term-programs [{:id 9 :sections [:overview]}]
                  :apprenticeships [{:id 3 :sections [:earn]}]
                  ;; scholarships are never presented with :sections — must be ignored
                  :scholarships [{:id 4 :name "TOPS"}]})))))

(deftest needs-migration-empty-is-nil-safe
  (testing "an absent / empty resolved set does NOT trigger migration"
    (is (false? (ov/needs-migration? nil)))
    (is (false? (ov/needs-migration? {})))
    (is (false? (ov/needs-migration? {:institutions {:safety [] :target [] :reach []}})))))

;; ===========================================================================
;; Staleness (from recommendation-status)
;; ===========================================================================

(deftest staleness-fresh-is-nil
  (testing "no status, or a status that is neither generating nor stale → no overlay"
    (is (nil? (ov/staleness nil)))
    (is (nil? (ov/staleness {})))
    (is (nil? (ov/staleness {:needs-regeneration? false :generation-in-progress? false})))))

(deftest staleness-generating
  (testing "generation-in-progress? → a :generating overlay (takes precedence)"
    (let [s (ov/staleness {:generation-in-progress? true :needs-regeneration? true})]
      (is (= :generating (:state s)))
      (is (some? (:label s))))))

(deftest staleness-needs-regeneration
  (testing "needs-regeneration? → a :stale overlay carrying last-generated-at + change-count"
    (let [s (ov/staleness {:needs-regeneration? true
                           :last-generated-at "2026-06-01T00:00:00Z"
                           :changes [{:description "GPA changed"} {:description "New ACT"}]})]
      (is (= :stale (:state s)))
      (is (= "2026-06-01T00:00:00Z" (:last-generated-at s)))
      (is (= 2 (:change-count s)))))
  (testing "a stale status with no changes/timestamp still renders (present-by-data)"
    (let [s (ov/staleness {:needs-regeneration? true})]
      (is (= :stale (:state s)))
      (is (nil? (:change-count s)))
      (is (nil? (:last-generated-at s))))))
