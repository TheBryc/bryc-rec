(ns components.crm.student.tabs.credentials
  "Credentials section — encrypted login credentials for academic platforms."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-encrypted]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(defui credentials-tab
  "Credentials section with encrypted login fields for academic platforms.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        advisee? (status/advisee? (get field-values :bryc-status))
        online-gradebook (fn []
                           ($ :<>
                              ($ sub-header {:title "Online Gradebook"})
                              ($ field-grid
                                 ($ field-cell {:label "Website" :span :full}
                                    ($ editable-text
                                       {:value (get field-values :online-gradebook-website)
                                        :placeholder "Enter website URL"
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-field "online-gradebook-website" % api-client])})
                                    ($ field-error {:field-slug "online-gradebook-website"}))
                                 ($ field-cell {:label "Username"}
                                    ($ editable-encrypted
                                       {:value (get field-values :online-grade-book-username)
                                        :placeholder "Enter username..."
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-username" % api-client])})
                                    ($ field-error {:field-slug "online-grade-book-username"}))
                                 ($ field-cell {:label "Password"}
                                    ($ editable-encrypted
                                       {:value (get field-values :online-grade-book-password)
                                        :placeholder "Enter password..."
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-password" % api-client])})
                                    ($ field-error {:field-slug "online-grade-book-password"})))))
        masteryprep (fn []
                      ($ :<>
                         ($ sub-header {:title "MasteryPrep"})
                         ($ field-grid
                            ($ field-cell {:label "Login Email"}
                               ($ editable-encrypted
                                  {:value (get field-values :masteryprep-login-email)
                                   :placeholder "Enter email..."
                                   :saving? saving?
                                   :on-save #(rf/dispatch [::student-events/save-encrypted-field "masteryprep-login-email" % api-client])})
                               ($ field-error {:field-slug "masteryprep-login-email"}))
                            ($ field-cell {:label "Login Password"}
                               ($ editable-encrypted
                                  {:value (get field-values :masteryprep-login-password)
                                   :placeholder "Enter password..."
                                   :saving? saving?
                                   :on-save #(rf/dispatch [::student-events/save-encrypted-field "masteryprep-login-password" % api-client])})
                               ($ field-error {:field-slug "masteryprep-login-password"})))))
        uworld (fn []
                 ($ :<>
                    ($ sub-header {:title "UWorld"})
                    ($ field-grid
                       ($ field-cell {:label "Login Email"}
                          ($ editable-encrypted
                             {:value (get field-values :uworld-login-email)
                              :placeholder "Enter email..."
                              :saving? saving?
                              :on-save #(rf/dispatch [::student-events/save-encrypted-field "uworld-login-email" % api-client])})
                          ($ field-error {:field-slug "uworld-login-email"}))
                       ($ field-cell {:label "Login Password"}
                          ($ editable-encrypted
                             {:value (get field-values :uworld-login-password)
                              :placeholder "Enter password..."
                              :saving? saving?
                              :on-save #(rf/dispatch [::student-events/save-encrypted-field "uworld-login-password" % api-client])})
                          ($ field-error {:field-slug "uworld-login-password"})))))
        act-org (fn []
                  ($ :<>
                     ($ sub-header {:title "ACT.org"})
                     ($ field-grid
                        ($ field-cell {:label "Login Email"}
                           ($ editable-encrypted
                              {:value (get field-values :act-dot-org-username)
                               :placeholder "Enter email..."
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-username" % api-client])})
                           ($ field-error {:field-slug "act-dot-org-username"}))
                        ($ field-cell {:label "Login Password"}
                           ($ editable-encrypted
                              {:value (get field-values :act-dot-org-password)
                               :placeholder "Enter password..."
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-password" % api-client])})
                           ($ field-error {:field-slug "act-dot-org-password"})))))
        collegeboard (fn []
                       ($ :<>
                          ($ sub-header {:title "Collegeboard"})
                          ($ field-grid
                             ($ field-cell {:label "Username"}
                                ($ editable-encrypted
                                   {:value (get field-values :collegeboard-username)
                                    :placeholder "Enter username..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-encrypted-field "collegeboard-username" % api-client])})
                                ($ field-error {:field-slug "collegeboard-username"}))
                             ($ field-cell {:label "Password"}
                                ($ editable-encrypted
                                   {:value (get field-values :collegeboard-password)
                                    :placeholder "Enter password..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-encrypted-field "collegeboard-password" % api-client])})
                                ($ field-error {:field-slug "collegeboard-password"})))))]
    (if advisee?
      ($ nested-tabs
         {:tabs [{:id "online-gradebook" :label "Online Gradebook" :render online-gradebook}
                 {:id "masteryprep" :label "MasteryPrep" :render masteryprep}
                 {:id "uworld" :label "UWorld" :render uworld}
                 {:id "act-org" :label "ACT.org" :render act-org}
                 {:id "collegeboard" :label "Collegeboard" :render collegeboard}]})
      ($ :<>
       ;; Online Gradebook
       ($ sub-header {:title "Online Gradebook"})
       ($ field-grid
          ($ field-cell {:label "Website" :span :full}
             ($ editable-text
                {:value (get field-values :online-gradebook-website)
                 :placeholder "Enter website URL"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "online-gradebook-website" % api-client])})
             ($ field-error {:field-slug "online-gradebook-website"}))
          ($ field-cell {:label "Username"}
             ($ editable-encrypted
                {:value (get field-values :online-grade-book-username)
                 :placeholder "Enter username..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-username" % api-client])})
             ($ field-error {:field-slug "online-grade-book-username"}))
          ($ field-cell {:label "Password"}
             ($ editable-encrypted
                {:value (get field-values :online-grade-book-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-password" % api-client])})
             ($ field-error {:field-slug "online-grade-book-password"})))

       ;; MasteryPrep
       ($ sub-header {:title "MasteryPrep"})
       ($ field-grid
          ($ field-cell {:label "Login Email"}
             ($ editable-encrypted
                {:value (get field-values :masteryprep-login-email)
                 :placeholder "Enter email..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "masteryprep-login-email" % api-client])})
             ($ field-error {:field-slug "masteryprep-login-email"}))
          ($ field-cell {:label "Login Password"}
             ($ editable-encrypted
                {:value (get field-values :masteryprep-login-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "masteryprep-login-password" % api-client])})
             ($ field-error {:field-slug "masteryprep-login-password"})))

       ;; UWorld
       ($ sub-header {:title "UWorld"})
       ($ field-grid
          ($ field-cell {:label "Login Email"}
             ($ editable-encrypted
                {:value (get field-values :uworld-login-email)
                 :placeholder "Enter email..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "uworld-login-email" % api-client])})
             ($ field-error {:field-slug "uworld-login-email"}))
          ($ field-cell {:label "Login Password"}
             ($ editable-encrypted
                {:value (get field-values :uworld-login-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "uworld-login-password" % api-client])})
             ($ field-error {:field-slug "uworld-login-password"})))

       ;; ACT.org
       ($ sub-header {:title "ACT.org"})
       ($ field-grid
          ($ field-cell {:label "Login Email"}
             ($ editable-encrypted
                {:value (get field-values :act-dot-org-username)
                 :placeholder "Enter email..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-username" % api-client])})
             ($ field-error {:field-slug "act-dot-org-username"}))
          ($ field-cell {:label "Login Password"}
             ($ editable-encrypted
                {:value (get field-values :act-dot-org-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-password" % api-client])})
             ($ field-error {:field-slug "act-dot-org-password"})))

       ;; Collegeboard
       ($ sub-header {:title "Collegeboard"})
       ($ field-grid
          ($ field-cell {:label "Username"}
             ($ editable-encrypted
                {:value (get field-values :collegeboard-username)
                 :placeholder "Enter username..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "collegeboard-username" % api-client])})
             ($ field-error {:field-slug "collegeboard-username"}))
          ($ field-cell {:label "Password"}
             ($ editable-encrypted
                {:value (get field-values :collegeboard-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "collegeboard-password" % api-client])})
             ($ field-error {:field-slug "collegeboard-password"})))))))
