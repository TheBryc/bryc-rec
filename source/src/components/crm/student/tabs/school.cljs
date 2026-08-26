(ns components.crm.student.tabs.school
  "School section — high school, graduation year, middle school, system, parish, accommodations."
  (:require [uix.core :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell]]
            [components.crm.student.editable-field :refer [editable-text editable-select editable-tag-select]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(defui school-tab
  "School details — high school, graduation year, middle school, system, parish, accommodations.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        graduation-year-options (use-subscribe [::student-subs/field-options "graduation-year"])
        middle-school-options (use-subscribe [::student-subs/field-options "middle-school"])
        school-system-options (use-subscribe [::student-subs/field-options "school-system"])
        school-parish-options (use-subscribe [::student-subs/field-options "school-parish"])
        other-high-schools-options (use-subscribe [::student-subs/field-options "other-high-schools-attended"])
        school-accommodations-options (use-subscribe [::student-subs/field-options "school-accommodations"])]

    ($ field-grid
       ($ field-cell {:label "High School"}
          ($ editable-text
             {:value (:student/school student)
              :placeholder "Add school"
              :saving? saving?
              :on-save #(rf/dispatch [::student-events/save-field "high-school" % api-client])})
          ($ field-error {:field-slug "high-school"}))
       ($ field-cell {:label "Graduation Year"}
          ($ editable-select
             {:value (:student/graduation-year student)
              :options graduation-year-options
              :placeholder "Select year"
              :saving? saving?
              :as-string? true
              :on-save #(rf/dispatch [::student-events/save-field "graduation-year" % api-client])})
          ($ field-error {:field-slug "graduation-year"}))
       ($ field-cell {:label "Middle School"}
          ($ editable-select
             {:value (:student/middle-school student)
              :options middle-school-options
              :placeholder "Select middle school"
              :saving? saving?
              :as-string? true
              :on-save #(rf/dispatch [::student-events/save-field "middle-school" % api-client])})
          ($ field-error {:field-slug "middle-school"}))
       ($ field-cell {:label "School System"}
          ($ editable-select
             {:value (get field-values :school-system)
              :options school-system-options
              :placeholder "Select school system"
              :saving? saving?
              :as-string? true
              :on-save #(rf/dispatch [::student-events/save-field "school-system" % api-client])})
          ($ field-error {:field-slug "school-system"}))
       ($ field-cell {:label "School Parish"}
          ($ editable-select
             {:value (get field-values :school-parish)
              :options school-parish-options
              :placeholder "Select school parish"
              :saving? saving?
              :as-string? true
              :on-save #(rf/dispatch [::student-events/save-field "school-parish" % api-client])})
          ($ field-error {:field-slug "school-parish"}))
       ($ field-cell {:label "Other High Schools Attended" :span :full}
          ($ editable-tag-select
             {:value (get field-values :other-high-schools-attended)
              :options other-high-schools-options
              :placeholder "Select schools"
              :saving? saving?
              :on-save #(rf/dispatch [::student-events/save-field "other-high-schools-attended" % api-client])})
          ($ field-error {:field-slug "other-high-schools-attended"}))
       ($ field-cell {:label "School Accommodations" :span :full}
          ($ editable-tag-select
             {:value (get field-values :school-accommodations)
              :options school-accommodations-options
              :placeholder "Select accommodations"
              :saving? saving?
              :on-save #(rf/dispatch [::student-events/save-field "school-accommodations" % api-client])})
          ($ field-error {:field-slug "school-accommodations"})))))
