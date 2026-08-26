(ns store.advisor.recommendations.events-test
  "Pure re-frame event tests for the advisor recommendations store. Tests the event
   HANDLER functions directly (data->data through the public handler fn), with the
   http/API effect INJECTED as a re-frame effect map the handler merely RETURNS — never
   the DOM, never a live POST. Survives view refactors."
  (:require [cljs.test :refer [deftest testing is]]
            [store.advisor.recommendations.events :as events]))

;; ===========================================================================
;; Promote from the full pool — the advisor authoring Edit view renders the
;; unselected pool institutions/programs as 'Show to student' controls that dispatch
;; the EXISTING selection-toggle events. These tests pin the SELECT (promote) branch
;; of those handlers: given a pool school/program NOT yet in the selection, the handler
;; adds it to the working selection and autosaves — no new event. Pure data->data with
;; the api-client faked and the autosave dispatch merely RETURNED.
;; ===========================================================================

(deftest toggle-institution-selection-promotes-a-pool-school
  (let [fake-api-client (js-obj)
        ;; The target bucket has one selected school; the advisor promotes pool school
        ;; "pool-9" (not yet selected) via the promote control.
        db {:advisor {:recommendations {:selection {:institutions {:target [{:id "sel-1" :programs []}]}}}}}
        fx (events/toggle-institution-selection-fx
            {:db db}
            [::events/toggle-institution-selection :target "pool-9" fake-api-client])
        refs (get-in (:db fx) [:advisor :recommendations :selection :institutions :target])]
    (testing "the promote/SELECT branch conj's the pool school's ref onto its category bucket"
      (is (= [{:id "sel-1" :programs []} {:id "pool-9" :programs []}] refs)
          "the previously-selected school is kept and the promoted pool school is appended"))
    (testing "promotion autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest toggle-program-selection-promotes-a-pool-program-within-a-school
  (let [fake-api-client (js-obj)
        ;; School "sch-1" is selected with program "p-keep"; the advisor promotes the
        ;; school's pool program "p-new" (not yet selected) via the program promote control.
        db {:advisor {:recommendations {:selection {:institutions {:target [{:id "sch-1" :programs [{:id "p-keep"}]}]}}}}}
        fx (events/toggle-program-selection-fx
            {:db db}
            [::events/toggle-program-selection :target "sch-1" "p-new" fake-api-client])
        progs (get-in (:db fx) [:advisor :recommendations :selection :institutions :target 0 :programs])]
    (testing "the promote/SELECT branch conj's the pool program's ref under the school"
      (is (= [{:id "p-keep"} {:id "p-new"}] progs)
          "the kept program stays and the promoted pool program is appended"))
    (testing "program promotion autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

;; ===========================================================================
;; 0080 — College-program Overview prose fields inline-editable in the dense editor.
;; The detail sheet's Overview inputs dispatch [::set-program-section-edit …] on blur
;; and [::revert-program-section-edit …] when cleared. These pin the handlers
;; data->data: the section-edit lands as {:section-edits {:overview {<field> v}}} on the
;; REAL program ref (the customization trail, deep-merged by apply-ref-overrides), and
;; clearing reverts EXACTLY (the ref goes byte-identical to a never-edited one).
;; ===========================================================================

(deftest set-program-section-edit-lands-overview-edit-on-the-program-ref
  (let [fake-api-client (js-obj)
        db {:advisor {:recommendations {:selection {:institutions
                                                    {:target [{:id "sch-1"
                                                               :programs [{:id "p-keep"}
                                                                          {:id "p-edit"}]}]}}}}}
        fx (events/set-program-section-edit-fx
            {:db db}
            [::events/set-program-section-edit :target "sch-1" "p-edit"
             :overview :student-connection "You love biology, so nursing fits." fake-api-client])
        progs (get-in (:db fx) [:advisor :recommendations :selection
                                :institutions :target 0 :programs])]
    (testing "the edit deep-merges into {:section-edits {:overview {:student-connection v}}} on ONLY the target program ref"
      (is (= [{:id "p-keep"}
              {:id "p-edit"
               :section-edits {:overview {:student-connection "You love biology, so nursing fits."}}}]
             progs)
          "the sibling program ref is untouched; the pool record is never mutated (edit lives on the ref)"))
    (testing "editing autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest set-program-section-edit-preserves-a-sibling-overview-field
  (testing "editing :caveat while :student-connection is already overridden keeps BOTH (deep-merge one level)"
    (let [db {:advisor {:recommendations {:selection {:institutions
                                                      {:target [{:id "sch-1"
                                                                 :programs [{:id "p-edit"
                                                                             :section-edits {:overview {:student-connection "kept"}}}]}]}}}}}
          fx (events/set-program-section-edit-fx
              {:db db}
              [::events/set-program-section-edit :target "sch-1" "p-edit"
               :overview :caveat "Pass the NCLEX." (js-obj)])
          prog (get-in (:db fx) [:advisor :recommendations :selection
                                 :institutions :target 0 :programs 0])]
      (is (= {:overview {:student-connection "kept" :caveat "Pass the NCLEX."}}
             (:section-edits prog))))))

(deftest revert-program-section-edit-reverts-exactly
  (testing "clearing the only overridden Overview field drops :section-edits entirely → ref byte-identical to never-edited → AI source returns"
    (let [db {:advisor {:recommendations {:selection {:institutions
                                                      {:target [{:id "sch-1"
                                                                 :programs [{:id "p-edit"
                                                                             :section-edits {:overview {:student-connection "advisor words"}}}]}]}}}}}
          fx (events/revert-program-section-edit-fx
              {:db db}
              [::events/revert-program-section-edit :target "sch-1" "p-edit"
               :overview :student-connection (js-obj)])
          prog (get-in (:db fx) [:advisor :recommendations :selection
                                 :institutions :target 0 :programs 0])]
      (is (= {:id "p-edit"} prog) "the ref is left with no override submap at all (exact revert-to-source)")
      (is (not (contains? prog :section-edits))))))

;; ===========================================================================
;; 0079 — apprenticeship + short-term-program Overview prose fields inline-editable.
;; The detail sheet dispatches [::set-apprenticeship-section-edit app-id :overview
;; <field> value api-client] on blur and [::revert-apprenticeship-section-edit app-id
;; :overview <field> api-client] when cleared (short-term mirrors it via program-id).
;; These pin the handlers data->data: the section-edit lands as {:section-edits
;; {:overview {<field> v}}} on the REAL ref in the FLAT [:selection :apprenticeships] /
;; [:selection :short-term-programs] list (deep-merged by apply-ref-overrides), and
;; clearing reverts EXACTLY (the ref goes byte-identical to a never-edited one).
;; ===========================================================================

(deftest set-apprenticeship-section-edit-lands-overview-edit-on-the-apprenticeship-ref
  (let [fake-api-client (js-obj)
        db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-keep"}
                                                                      {:id "app-edit"}]}}}}
        fx (events/set-apprenticeship-section-edit-fx
            {:db db}
            [::events/set-apprenticeship-section-edit "app-edit"
             :overview :credential-line "A registered electrical apprenticeship." fake-api-client])
        refs (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships])]
    (testing "the edit deep-merges into {:section-edits {:overview {:credential-line v}}} on ONLY the target apprenticeship ref"
      (is (= [{:id "app-keep"}
              {:id "app-edit"
               :section-edits {:overview {:credential-line "A registered electrical apprenticeship."}}}]
             refs)
          "the sibling apprenticeship ref is untouched; the pool record is never mutated"))
    (testing "editing autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest set-apprenticeship-section-edit-preserves-a-sibling-overview-field
  (testing "editing :caveat while :credential-line is already overridden keeps BOTH (deep-merge one level)"
    (let [db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-edit"
                                                                         :section-edits {:overview {:credential-line "kept"}}}]}}}}
          fx (events/set-apprenticeship-section-edit-fx
              {:db db}
              [::events/set-apprenticeship-section-edit "app-edit"
               :overview :caveat "Program admission differs." (js-obj)])
          app (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships 0])]
      (is (= {:overview {:credential-line "kept" :caveat "Program admission differs."}}
             (:section-edits app))))))

(deftest revert-apprenticeship-section-edit-reverts-exactly
  (testing "clearing the only overridden Overview field drops :section-edits entirely → ref byte-identical to never-edited"
    (let [db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-edit"
                                                                         :section-edits {:overview {:credential-line "advisor words"}}}]}}}}
          fx (events/revert-apprenticeship-section-edit-fx
              {:db db}
              [::events/revert-apprenticeship-section-edit "app-edit"
               :overview :credential-line (js-obj)])
          app (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships 0])]
      (is (= {:id "app-edit"} app) "the ref is left with no override submap at all (exact revert-to-source)")
      (is (not (contains? app :section-edits))))))

(deftest set-short-term-section-edit-lands-overview-edit-on-the-short-term-ref
  (let [fake-api-client (js-obj)
        db {:advisor {:recommendations {:selection {:short-term-programs [{:id "stp-keep"}
                                                                          {:id "stp-edit"}]}}}}
        fx (events/set-short-term-section-edit-fx
            {:db db}
            [::events/set-short-term-section-edit "stp-edit"
             :overview :credential-line "A short welding certificate." fake-api-client])
        refs (get-in (:db fx) [:advisor :recommendations :selection :short-term-programs])]
    (testing "the edit deep-merges into {:section-edits {:overview {:credential-line v}}} on ONLY the target short-term ref"
      (is (= [{:id "stp-keep"}
              {:id "stp-edit"
               :section-edits {:overview {:credential-line "A short welding certificate."}}}]
             refs)
          "the sibling short-term ref is untouched; the pool record is never mutated"))
    (testing "editing autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest revert-short-term-section-edit-reverts-exactly
  (testing "clearing the only overridden Overview field drops :section-edits entirely → ref byte-identical to never-edited"
    (let [db {:advisor {:recommendations {:selection {:short-term-programs [{:id "stp-edit"
                                                                             :section-edits {:overview {:credential-line "advisor words"}}}]}}}}
          fx (events/revert-short-term-section-edit-fx
              {:db db}
              [::events/revert-short-term-section-edit "stp-edit"
               :overview :credential-line (js-obj)])
          stp (get-in (:db fx) [:advisor :recommendations :selection :short-term-programs 0])]
      (is (= {:id "stp-edit"} stp) "the ref is left with no override submap at all (exact revert-to-source)")
      (is (not (contains? stp :section-edits))))))

;; ===========================================================================
;; 0081 — the apprenticeship "Why This Fits" bullets are the top-level :bullets vector
;; (NOT inside a section), so they override via the :text-edits seam (a FLAT top-level
;; field override) — NOT :section-edits, and deliberately NOT the legacy selection-level
;; ::edit-apprenticeship-bullets (the dense-editor-scope tripwire forbids that event in the
;; panel). The editable bullet list dispatches [::set-apprenticeship-text-edit app-id
;; :bullets value api-client] on commit and [::revert-apprenticeship-text-edit app-id
;; :bullets api-client] when every row is cleared. These pin the handlers data->data: the
;; edit lands as {:text-edits {:bullets v}} on the REAL ref (apply-ref-overrides flat-merges
;; it) and clearing reverts EXACTLY (the ref goes byte-identical to a never-edited one).
;; ===========================================================================

(deftest set-apprenticeship-text-edit-lands-bullets-edit-on-the-apprenticeship-ref
  (let [fake-api-client (js-obj)
        db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-keep"}
                                                                      {:id "app-edit"}]}}}}
        fx (events/set-apprenticeship-text-edit-fx
            {:db db}
            [::events/set-apprenticeship-text-edit "app-edit"
             :bullets ["Builds electrical competencies." "Connects you to local employers."]
             fake-api-client])
        refs (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships])]
    (testing "the edit lands as {:text-edits {:bullets v}} on ONLY the target apprenticeship ref"
      (is (= [{:id "app-keep"}
              {:id "app-edit"
               :text-edits {:bullets ["Builds electrical competencies."
                                      "Connects you to local employers."]}}]
             refs)
          "the sibling apprenticeship ref is untouched; the pool record is never mutated"))
    (testing "editing autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest set-apprenticeship-text-edit-preserves-a-sibling-section-edit
  (testing "editing :bullets while an Overview section-edit is already set keeps BOTH override submaps"
    (let [db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-edit"
                                                                         :section-edits {:overview {:credential-line "kept"}}}]}}}}
          fx (events/set-apprenticeship-text-edit-fx
              {:db db}
              [::events/set-apprenticeship-text-edit "app-edit"
               :bullets ["Fresh bullet."] (js-obj)])
          app (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships 0])]
      (is (= {:overview {:credential-line "kept"}} (:section-edits app)) "the Overview section-edit survives")
      (is (= {:bullets ["Fresh bullet."]} (:text-edits app)) "the bullets text-edit is added alongside"))))

(deftest revert-apprenticeship-text-edit-reverts-exactly
  (testing "clearing every bullet drops :text-edits entirely → ref byte-identical to never-edited → AI source :bullets returns"
    (let [db {:advisor {:recommendations {:selection {:apprenticeships [{:id "app-edit"
                                                                         :text-edits {:bullets ["advisor bullet"]}}]}}}}
          fx (events/revert-apprenticeship-text-edit-fx
              {:db db}
              [::events/revert-apprenticeship-text-edit "app-edit" :bullets (js-obj)])
          app (get-in (:db fx) [:advisor :recommendations :selection :apprenticeships 0])]
      (is (= {:id "app-edit"} app) "the ref is left with no override submap at all (exact revert-to-source)")
      (is (not (contains? app :text-edits))))))

;; ===========================================================================
;; 0085 — the scholarship AI prose fields (Why This Scholarship Fits ←
;; :personalized-explanation, Description ← :description, Application Tips ←
;; :application-tips) are FLAT top-level scholarship fields, so they override via the
;; :text-edits seam (a FLAT top-level field override) — NOT :section-edits, and deliberately
;; NOT the legacy selection-level ::edit-scholarship. The dense-editor scholarship sheet's
;; editable prose fields dispatch [::set-scholarship-text-edit schol-id <field> value
;; api-client] on blur and [::revert-scholarship-text-edit schol-id <field> api-client] when
;; cleared. These pin the handlers data->data: the edit lands as {:text-edits {<field> v}} on
;; the REAL scholarship ref (apply-ref-overrides flat-merges it) and clearing reverts EXACTLY
;; (the ref goes byte-identical to a never-edited one → the AI source returns).
;; ===========================================================================

(deftest set-scholarship-text-edit-lands-prose-edit-on-the-scholarship-ref
  (let [fake-api-client (js-obj)
        db {:advisor {:recommendations {:selection {:scholarships [{:id "schol-keep"}
                                                                   {:id "schol-edit"}]}}}}
        fx (events/set-scholarship-text-edit-fx
            {:db db}
            [::events/set-scholarship-text-edit "schol-edit"
             :personalized-explanation "Because you excel at biology, this rewards your drive."
             fake-api-client])
        refs (get-in (:db fx) [:advisor :recommendations :selection :scholarships])]
    (testing "the edit lands as {:text-edits {:personalized-explanation v}} on ONLY the target ref"
      (is (= [{:id "schol-keep"}
              {:id "schol-edit"
               :text-edits {:personalized-explanation "Because you excel at biology, this rewards your drive."}}]
             refs)
          "the sibling scholarship ref is untouched; the pool record is never mutated"))
    (testing "editing autosaves through the EXISTING autosave path, carrying the api-client"
      (is (= [::events/perform-autosave fake-api-client] (:dispatch fx))))))

(deftest set-scholarship-text-edit-preserves-a-sibling-prose-field
  (testing "editing :description while :personalized-explanation is already edited keeps BOTH fields"
    (let [db {:advisor {:recommendations {:selection {:scholarships [{:id "schol-edit"
                                                                      :text-edits {:personalized-explanation "kept"}}]}}}}
          fx (events/set-scholarship-text-edit-fx
              {:db db}
              [::events/set-scholarship-text-edit "schol-edit"
               :description "A merit scholarship for STEM students." (js-obj)])
          schol (get-in (:db fx) [:advisor :recommendations :selection :scholarships 0])]
      (is (= {:personalized-explanation "kept"
              :description "A merit scholarship for STEM students."}
             (:text-edits schol))
          "the sibling prose field survives; the new field is added alongside"))))

(deftest revert-scholarship-text-edit-reverts-exactly
  (testing "clearing the only edited prose field drops :text-edits entirely → ref byte-identical to never-edited → AI source returns"
    (let [db {:advisor {:recommendations {:selection {:scholarships [{:id "schol-edit"
                                                                      :text-edits {:application-tips "advisor tips"}}]}}}}
          fx (events/revert-scholarship-text-edit-fx
              {:db db}
              [::events/revert-scholarship-text-edit "schol-edit" :application-tips (js-obj)])
          schol (get-in (:db fx) [:advisor :recommendations :selection :scholarships 0])]
      (is (= {:id "schol-edit"} schol) "the ref is left with no override submap at all (exact revert-to-source)")
      (is (not (contains? schol :text-edits))))))

;; ===========================================================================
;; Advisor identity card (0059) in the in-editor Student preview — the
;; recommendations-screen query resolves the advisee's assigned advisor (the
;; SAME advisor `resolve-student-advisor` returns for the token view) into an
;; :advisor map. `load-success-db` must land it under
;; [:advisor :recommendations :advisor] so the ::advisor sub can thread it into
;; the preview seed (otherwise the preview omits the advisor card). Present-by-
;; data: no advisor in the payload → nil there (card omitted, no crash).
;; ===========================================================================

(def ^:private sample-advisor
  {:name "Alex Rivera" :email "alex@thebryc.org"
   :scheduling-url "https://cal.example/alex" :profile-photo-url nil})

(deftest load-success-carries-advisor-identity
  (let [db' (events/load-success-db
             {}
             [::events/load-success {:student {:student/name "Maya Johnson"}
                                     :pool {:x 1}
                                     :pool-id "p1"
                                     :selection {:institutions {:safety []}}
                                     :resolved {:student {:name "Maya Johnson"}}
                                     :advisor sample-advisor
                                     :advisor-message "Great list."}])]
    (testing "the advisor identity map lands under the advisor store advisor path"
      (is (= sample-advisor (get-in db' [:advisor :recommendations :advisor]))))
    (testing "the rest of the payload still lands (no regression)"
      (is (= {:x 1} (get-in db' [:advisor :recommendations :pool])))
      (is (= "p1" (get-in db' [:advisor :recommendations :pool-id])))
      (is (= "Great list." (get-in db' [:advisor :recommendations :advisor-message])))
      (is (false? (get-in db' [:advisor :recommendations :loading?]))))))

(deftest load-success-omits-advisor-present-by-data
  (testing "no :advisor in the payload → nil under the advisor path (card omitted, no crash)"
    (let [db' (events/load-success-db
               {}
               [::events/load-success {:student {:student/name "Maya Johnson"}
                                       :pool {:x 1}
                                       :pool-id "p1"
                                       :selection nil
                                       :resolved nil
                                       :advisor-message nil}])]
      (is (nil? (get-in db' [:advisor :recommendations :advisor]))))))
