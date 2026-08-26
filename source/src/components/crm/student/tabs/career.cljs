(ns components.crm.student.tabs.career
  "Career tab for the student CRM detail view.
   Contains employment records, internships, and professional community fields."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.student.editable-field :refer [editable-select editable-tag-select]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.formatting :refer [format-keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def employment-records-sub-schema
  {:type :vector
   :add-button-text "Add Employment"
   :empty-message "No employment records."
   :item-label-key :employer
   :item-schema
   {:type :map
    :fields [{:key :employer
              :type :string
              :required true
              :label "Employer"
              :placeholder "Enter employer name..."}
             {:key :job-title
              :type :string
              :label "Job Title"
              :placeholder "Enter job title..."}
             {:key :year-hired
              :type :number
              :label "Year Hired"}
             {:key :career-industry
              :type :string
              :label "Career Industry"
              :placeholder "Enter industry..."}
             {:key :career-awards
              :type :string
              :label "Career Awards"
              :input-type "textarea"
              :rows 2}
             {:key :career-notes
              :type :string
              :label "Notes"
              :input-type "textarea"
              :rows 2}]}})

(def internship-records-sub-schema
  {:type :vector
   :add-button-text "Add Internship"
   :empty-message "No internship records."
   :item-label-key :employer
   :item-schema
   {:type :map
    :fields [{:key :employer
              :type :string
              :required true
              :label "Employer"
              :placeholder "Enter employer name..."}
             {:key :title
              :type :string
              :label "Title"
              :placeholder "Enter title..."}
             {:key :year
              :type :number
              :label "Year"}
             {:key :career-industry
              :type :string
              :label "Career Industry"
              :placeholder "Enter industry..."}]}})

;; =============================================================================
;; Career Tab
;; =============================================================================

(defui career-tab
  "Career tab with employment, internship, and professional community fields.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-professional-communities-options (use-subscribe [::student-subs/field-options "bryc-professional-communities"])
        military-options (use-subscribe [::student-subs/field-options "military"])
        employed-options (use-subscribe [::student-subs/field-options "employed"])
        bryc-status (get field-values :bryc-status)
        fellow? (status/fellow? bryc-status)
        alumni? (status/alumni? bryc-status)
        fellow-or-alumni? (or fellow? alumni?)]

    ($ :<>
       ;; Professional Communities & Military
       ($ sub-header {:title "Career"})
       ($ field-grid
          ;; BRYC Professional Communities
          (when fellow-or-alumni?
            ($ field-cell {:label "BRYC Professional Communities" :span :full}
               ($ editable-tag-select
                  {:value (get field-values :bryc-professional-communities)
                   :options (or bryc-professional-communities-options [])
                   :placeholder "Select communities..."
                   :saving? saving?
                   :on-save #(rf/dispatch [::student-events/save-field "bryc-professional-communities" % api-client])})
               ($ field-error {:field-slug "bryc-professional-communities"})))

          ;; Military
          (when alumni?
            ($ field-cell {:label "Military"}
               ($ editable-select
                  {:value (get field-values :military)
                   :options military-options
                   :placeholder "Select..."
                   :saving? saving?
                   :as-string? true
                   :on-save #(rf/dispatch [::student-events/save-field "military" % api-client])})
               ($ field-error {:field-slug "military"})))

          ;; Employed
          (when alumni?
            ($ field-cell {:label "Employed"}
               ($ editable-select
                  {:value (get field-values :employed)
                   :options employed-options
                   :placeholder "Select..."
                   :saving? saving?
                   :on-save #(rf/dispatch [::student-events/save-field "employed" % api-client])})
               ($ field-error {:field-slug "employed"}))))

       ;; Employment Records
       (when alumni?
         ($ :<>
            ($ sub-header {:title "Employment Records"})
            ($ schema-vector-editor
               {:sub-schema employment-records-sub-schema
                :value (get field-values :employment-records)
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "employment-records" % api-client])})
            ($ field-error {:field-slug "employment-records"})))

       ;; Internship Records
       (when alumni?
         ($ :<>
            ($ sub-header {:title "Internship Records"})
            ($ schema-vector-editor
               {:sub-schema internship-records-sub-schema
                :value (get field-values :internship-records)
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "internship-records" % api-client])})
            ($ field-error {:field-slug "internship-records"}))))))
