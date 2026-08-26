(ns components.crm.student.tabs.community
  "Community section — alumni engagement and BRYC community fields."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.student.editable-field :refer [editable-select editable-number]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Community Tab
;; =============================================================================

(defui community-tab
  "Community tab with alumni engagement and BRYC community fields.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-team-options (use-subscribe [::student-subs/field-options "bryc-team"])
        bryc-vip-options (use-subscribe [::student-subs/field-options "bryc-vip"])
        bryc-donor-options (use-subscribe [::student-subs/field-options "bryc-donor"])]

    ($ :<>
       ($ sub-header {:title "Community"})
       ($ field-grid
          ($ field-cell {:label "BRYC VIP"}
             ($ editable-select
                {:value (get field-values :bryc-vip)
                 :options bryc-vip-options
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "bryc-vip" % api-client])})
             ($ field-error {:field-slug "bryc-vip"}))
          ($ field-cell {:label "BRYC Team"}
             ($ editable-select
                {:value (get field-values :bryc-team)
                 :options bryc-team-options
                 :placeholder "Select team..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "bryc-team" % api-client])})
             ($ field-error {:field-slug "bryc-team"}))
          ($ field-cell {:label "BRYC Donor"}
             ($ editable-select
                {:value (get field-values :bryc-donor)
                 :options bryc-donor-options
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "bryc-donor" % api-client])})
             ($ field-error {:field-slug "bryc-donor"}))
          ($ field-cell {:label "Alumni Engagement Score"}
             ($ editable-number
                {:value (get field-values :alumni-engagement-score)
                 :placeholder "0-3"
                 :min 0 :max 3 :step 1
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "alumni-engagement-score" % api-client])})
             ($ field-error {:field-slug "alumni-engagement-score"}))))))
