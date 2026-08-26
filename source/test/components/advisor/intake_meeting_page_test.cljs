(ns components.advisor.intake-meeting-page-test
  (:require [cljs.test :refer [deftest testing is]]
            [components.advisor.intake-meeting-page :refer [migrate-section-notes]]))

(deftest migrate-section-notes-test
  (testing "migrates :overgrad-onboarding to :extracurricular-involvement"
    (let [old-notes {:rapport-building "rapport notes"
                     :profile-review "profile notes"
                     :overgrad-onboarding "onboarding notes"
                     :next-steps "next steps notes"}
          migrated (migrate-section-notes old-notes)]
      (is (= "onboarding notes" (:extracurricular-involvement migrated))
          "old notes should be moved to new section")
      (is (nil? (:overgrad-onboarding migrated))
          "old section key should be removed")
      (is (= "rapport notes" (:rapport-building migrated))
          "other sections should be unchanged")
      (is (= "profile notes" (:profile-review migrated))
          "other sections should be unchanged")
      (is (= "next steps notes" (:next-steps migrated))
          "other sections should be unchanged")))

  (testing "preserves notes without :overgrad-onboarding unchanged"
    (let [new-notes {:rapport-building "rapport notes"
                     :profile-review "profile notes"
                     :extracurricular-involvement "involvement notes"
                     :postsecondary-goals "goals notes"
                     :next-steps "next steps notes"}
          result (migrate-section-notes new-notes)]
      (is (= new-notes result)
          "notes with new section IDs should be unchanged")))

  (testing "handles empty notes map"
    (is (= {} (migrate-section-notes {}))
        "empty map should return empty map"))

  (testing "handles partial notes"
    (let [partial {:overgrad-onboarding "only this section"}
          migrated (migrate-section-notes partial)]
      (is (= {:extracurricular-involvement "only this section"} migrated)
          "should migrate even with only the old section"))))
