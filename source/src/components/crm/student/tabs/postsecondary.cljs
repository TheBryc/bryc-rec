(ns components.crm.student.tabs.postsecondary
  "Postsecondary tab for the student CRM detail view.
   Contains postsecondary education records."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def postsecondary-records-sub-schema
  {:type :vector
   :add-button-text "Add Record"
   :empty-message "No postsecondary records."
   :item-label-key :institution
   :item-schema
   {:type :map
    :fields [{:key :institution
              :type :string
              :required true
              :label "Institution"
              :placeholder "Enter institution name..."}
             {:key :postsecondary-type
              :type :single-select
              :label "Type"
              :options ["Four-Year" "Two-Year" "Vocational/Technical" "Military Academy" "Graduate School" "Continuing Ed"]}
             {:key :enrollment-status
              :type :single-select
              :label "Enrollment Status"
              :options ["Enrolled" "Graduated" "Transferred" "Unenrolled"]}
             {:key :starting-year
              :type :number
              :label "Starting Year"}
             {:key :final-year
              :type :number
              :label "Final/Graduating Year"}
             {:key :major
              :type :string
              :label "Major"
              :placeholder "Enter major..."}
             {:key :minor
              :type :string
              :label "Minor"
              :placeholder "Enter minor..."}
             {:key :additional-major-minor
              :type :string
              :label "Additional Major/Minor"
              :placeholder "Enter additional major/minor..."}
             {:key :degree-earned
              :type :single-select
              :label "Degree/Credential"
              :options ["Associate" "Certificate" "Technical" "Bachelor's" "Master's" "Doctorate" "Continuing Ed"]}
             {:key :degree-full-name
              :type :string
              :label "Degree Full Name"
              :placeholder "Enter full degree name..."}
             {:key :graduation-honors
              :type :string
              :label "Graduation Honors"
              :input-type "textarea"
              :rows 2}
             {:key :other-awards
              :type :string
              :label "Other Awards/Honors"
              :input-type "textarea"
              :rows 2}
             {:key :campus-involvement
              :type :string
              :label "Campus Involvement"
              :input-type "textarea"
              :rows 2}
             {:key :research
              :type :string
              :label "Research"
              :input-type "textarea"
              :rows 2}
             {:key :study-abroad
              :type :string
              :label "Study Abroad"
              :input-type "textarea"
              :rows 2}]}})

;; =============================================================================
;; Postsecondary Tab
;; =============================================================================

(defui postsecondary-tab
  "Postsecondary tab with education records.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-status (get field-values :bryc-status)
        alumni? (status/alumni? bryc-status)]

    (if alumni?
      ($ :<>
         ($ sub-header {:title "Postsecondary Records"})
         ($ schema-vector-editor
            {:sub-schema postsecondary-records-sub-schema
             :value (get field-values :postsecondary-records)
             :saving? saving?
             :on-save #(rf/dispatch [::student-events/save-field "postsecondary-records" % api-client])})
         ($ field-error {:field-slug "postsecondary-records"}))
      ($ :<>
         ($ sub-header {:title "Postsecondary Records"})
         ($ :p {:class "text-sm text-muted-foreground"} "Postsecondary records are available for alumni students.")))))
