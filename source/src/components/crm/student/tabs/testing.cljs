(ns components.crm.student.tabs.testing
  "Testing section — ACT summary, full testing history, and testing credentials."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-encrypted editable-tag-select]]
            [components.crm.student.testing-history :as testing-history]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(defn display-value [value]
  (if (some? value) (str value) "—"))

(defui readonly-metric [{:keys [label value]}]
  ($ field-cell {:label label}
     ($ :div {:class "min-h-9 rounded border border-transparent py-1 text-sm font-medium"}
        (display-value value))))

(defui act-scores-tab [{:keys [student field-values]}]
  (let [history (get field-values :test-score-history)
        entering (testing-history/entering-act-score history (:student/act-score student))
        highest (testing-history/highest-act-score history)
        gained (testing-history/points-gained history (:student/act-score student))]
    ($ :<>
       ($ sub-header {:title "ACT Scores"})
       ($ field-grid
          ($ readonly-metric {:label "Entering ACT Score" :value entering})
          ($ readonly-metric {:label "Highest ACT Score" :value highest})
          ($ readonly-metric {:label "Points Gained" :value gained})
          ($ readonly-metric {:label "TOPS Eligibility"
                              :value (testing-history/tops-eligibility-label highest)})
          ($ readonly-metric {:label "WorkKeys Score"
                              :value (:student/work-keys-score student)})))))

(defui testing-history-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ($ sub-header {:title "Testing History"})
       ($ testing-history/testing-history-editor
          {:value (get field-values :test-score-history)
           :saving? saving?
           :on-save #(rf/dispatch [::student-events/save-field "test-score-history" % api-client])})
       ($ field-error {:field-slug "test-score-history"}))))

(defui act-login-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ($ sub-header {:title "ACT Login Credentials"})
       ($ field-grid
          ($ field-cell {:label "ACT Login Email"}
             ($ editable-encrypted
                {:value (get field-values :act-dot-org-username)
                 :placeholder "Enter email..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-username" % api-client])})
             ($ field-error {:field-slug "act-dot-org-username"}))
          ($ field-cell {:label "ACT Login Password"}
             ($ editable-encrypted
                {:value (get field-values :act-dot-org-password)
                 :placeholder "Enter password..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-encrypted-field "act-dot-org-password" % api-client])})
             ($ field-error {:field-slug "act-dot-org-password"}))))))

(defui school-accommodations-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        school-accommodations-options (use-subscribe [::student-subs/field-options "school-accommodations"])]
    ($ :<>
       ($ sub-header {:title "School Accommodations"})
       ($ field-grid
          ($ field-cell {:label "School Accommodations" :span :full}
             ($ editable-tag-select
                {:value (get field-values :school-accommodations)
                 :options school-accommodations-options
                 :placeholder "Select accommodations"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "school-accommodations" % api-client])})
             ($ field-error {:field-slug "school-accommodations"}))))))

(defui testing-tab [{:keys [student field-values api-client]}]
  ($ nested-tabs
     {:tabs [{:id "act-scores"
              :label "ACT Scores"
              :render #($ act-scores-tab {:student student :field-values field-values})}
             {:id "testing-history"
              :label "Testing History"
              :render #($ testing-history-tab {:field-values field-values :api-client api-client})}
             {:id "act-login"
              :label "ACT Login Credentials"
              :render #($ act-login-tab {:field-values field-values :api-client api-client})}
             {:id "school-accommodations"
              :label "School Accommodations"
              :render #($ school-accommodations-tab {:field-values field-values :api-client api-client})}]}))
