(ns components.crm.student.tabs.activities
  "Activities section — extracurricular activities editor."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(def activities-sub-schema
  {:type :vector
   :add-button-text "Add Activity"
   :empty-message "No activities added yet."
   :item-label-key :activity
   :item-schema
   {:type :map
    :fields [{:key :activity
              :type :string
              :required true
              :label "Activity Name"
              :placeholder "Enter activity name..."}
             {:key :role
              :type :string
              :required false
              :label "Role"
              :placeholder "e.g., President, Member, Volunteer..."}
             {:key :grades-involved
              :type :multi-select
              :required false
              :label "Grades Involved"
              :options ["9th" "10th" "11th" "12th"]}
             {:key :category
              :type :single-select
              :required false
              :label "Category"
              :options ["School-Based Activity"
                        "Community-Based Activity"
                        "Work Experience"
                        "Home & Family"]}]}})

(defui activities-tab
  "Activities section with extracurricular activities editor.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ($ sub-header {:title "Extracurricular Activities"})
       ($ schema-vector-editor
          {:sub-schema activities-sub-schema
           :value (:student/extracurricular-activities student)
           :saving? saving?
           :on-save #(rf/dispatch [::student-events/save-field "activities" % api-client])})
       ($ field-error {:field-slug "activities"}))))
