(ns components.crm.student.tabs.status
  "Status section — BRYC status and advisor status, displayed at the top."
  (:require [uix.core :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell]]
            [components.crm.student.editable-field :refer [editable-select]]
            [components.crm.student.sidebar :refer [advisor-status-options keyword->status-label status-label->keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(defui status-tab
  "Status fields — BRYC status and advisor workflow status.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-status-options (use-subscribe [::student-subs/field-options "bryc-status"])
        bryc-status (get field-values :bryc-status)
        advisee? (status/advisee? bryc-status)]

    ($ field-grid
       ($ field-cell {:label "BRYC Status"}
          ($ editable-select
             {:value bryc-status
              :options bryc-status-options
              :placeholder "Set status..."
              :saving? saving?
              :as-string? true
              :on-save #(rf/dispatch [::student-events/save-field "bryc-status" % api-client])})
          ($ field-error {:field-slug "bryc-status"}))

       (when advisee?
         ($ field-cell {:label "Advisor Status"}
            ($ editable-select
               {:value (keyword->status-label (:student/advisor-status student))
                :options advisor-status-options
                :placeholder "Set status..."
                :saving? saving?
                :as-string? true
                :on-save #(rf/dispatch [::student-events/save-field "advisor-status" (status-label->keyword %) api-client])})
            ($ field-error {:field-slug "advisor-status"}))))))
