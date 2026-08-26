(ns components.crm.student.tabs.academics
  "Academics tab for the student CRM detail view.
   Contains academic performance fields and test score history."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/button" :as button]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-number editable-select editable-encrypted]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.formatting :refer [format-keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.context.interface :as context]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def ap-coursework-sub-schema
  {:type :vector
   :add-button-text "Add AP Course"
   :empty-message "No AP courses."
   :item-label-key :course-name
   :item-schema
   {:type :map
    :fields [{:key :course-name
              :type :string
              :required true
              :label "Course Name"
              :placeholder "e.g., AP Calculus AB"}
             {:key :grade-status
              :type :string
              :label "Grade/Status"
              :placeholder "e.g., A, In Progress..."}]}})

(def test-score-history-sub-schema
  {:type :vector
   :add-button-text "Add Test Score"
   :empty-message "No test scores recorded."
   :item-label-key :test-name
   :item-schema
   {:type :map
    :fields [{:key :test-name
              :type :string
              :required true
              :label "Test Name"
              :placeholder "e.g., ACT, SAT, WorkKeys..."}
             {:key :score
              :type :string
              :required true
              :label "Score"
              :placeholder "Enter score..."}
             {:key :date
              :type :date
              :label "Test Date"}]}})

(defui school-subtab [{:keys [student field-values api-client saving?]}]
  (let [middle-school-options (use-subscribe [::student-subs/field-options "middle-school"])
        school-system-options (use-subscribe [::student-subs/field-options "school-system"])
        school-parish-options (use-subscribe [::student-subs/field-options "school-parish"])]
    ($ :<>
       ($ sub-header {:title "School"})
       ($ field-grid
          ($ field-cell {:label "High School"}
             ($ editable-text {:value (:student/school student)
                               :placeholder "Add school"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "high-school" % api-client])})
             ($ field-error {:field-slug "high-school"}))
          ($ field-cell {:label "Middle School"}
             ($ editable-select {:value (:student/middle-school student)
                                 :options middle-school-options
                                 :placeholder "Select middle school"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "middle-school" % api-client])})
             ($ field-error {:field-slug "middle-school"}))
          ($ field-cell {:label "School System"}
             ($ editable-select {:value (get field-values :school-system)
                                 :options school-system-options
                                 :placeholder "Select school system"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "school-system" % api-client])})
             ($ field-error {:field-slug "school-system"}))
          ($ field-cell {:label "School Parish"}
             ($ editable-select {:value (get field-values :school-parish)
                                 :options school-parish-options
                                 :placeholder "Select school parish"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "school-parish" % api-client])})
             ($ field-error {:field-slug "school-parish"}))))))

(defui gpa-subtab [{:keys [student field-values api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "GPA"})
     ($ field-grid
        ($ field-cell {:label "Self-Reported GPA"}
           ($ editable-number {:value (:student/self-reported-gpa student)
                               :placeholder "0.00"
                               :min 0 :max 4.0 :step 0.01
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "self-reported-gpa" % api-client])})
           ($ field-error {:field-slug "self-reported-gpa"}))
        ($ field-cell {:label "Unweighted Cumulative GPA"}
           ($ editable-number {:value (:student/unweighted-cumulative-gpa student)
                               :placeholder "0.00"
                               :min 0 :max 4.0 :step 0.01
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "unweighted-gpa" % api-client])})
           ($ field-error {:field-slug "unweighted-gpa"}))
        ($ field-cell {:label "Weighted Cumulative GPA"}
           ($ editable-number {:value (get field-values :weighted-cumulative-gpa)
                               :placeholder "0.00"
                               :min 0 :max 5.0 :step 0.01
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "weighted-cumulative-gpa" % api-client])})
           ($ field-error {:field-slug "weighted-cumulative-gpa"}))
        ($ field-cell {:label "Weighted Core GPA"}
           ($ editable-number {:value (:student/weighted-core-gpa student)
                               :placeholder "0.00"
                               :min 0 :max 5.0 :step 0.01
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "weighted-core-gpa" % api-client])})
           ($ field-error {:field-slug "weighted-core-gpa"})))))

(defui coursework-subtab [{:keys [field-values api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "Coursework"})
     ($ :div {:class "space-y-6"}
        ($ :div
           ($ :div {:class "mb-2 text-sm font-medium"} "Dual Enrollment Courses")
           ($ schema-vector-editor
              {:sub-schema {:type :vector
                            :add-button-text "Add Course"
                            :empty-message "No dual enrollment courses."
                            :item-label-key :course-name
                            :item-schema {:type :map
                                          :fields [{:key :course-name :type :string :required true :label "Course Name"}
                                                   {:key :institution :type :string :label "Institution"}
                                                   {:key :grade-status :type :string :label "Grade/Status"}]}}
               :value (get field-values :dual-enrollment)
               :saving? saving?
               :on-save #(rf/dispatch [::student-events/save-field "dual-enrollment" % api-client])})
           ($ field-error {:field-slug "dual-enrollment"}))
        ($ :div
           ($ :div {:class "mb-2 text-sm font-medium"} "AP Coursework")
           ($ schema-vector-editor
              {:sub-schema ap-coursework-sub-schema
               :value (get field-values :ap-coursework)
               :saving? saving?
               :on-save #(rf/dispatch [::student-events/save-field "ap-coursework" % api-client])})
           ($ field-error {:field-slug "ap-coursework"})))))

(defui online-gradebook-subtab [{:keys [field-values api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "Online Gradebook"})
     ($ field-grid
        ($ field-cell {:label "Online Gradebook Website" :span :full}
           ($ editable-text {:value (get field-values :online-gradebook-website)
                             :placeholder "Enter website URL"
                             :saving? saving?
                             :on-save #(rf/dispatch [::student-events/save-field "online-gradebook-website" % api-client])})
           ($ field-error {:field-slug "online-gradebook-website"}))
        ($ field-cell {:label "Online Gradebook Username"}
           ($ editable-encrypted {:value (get field-values :online-grade-book-username)
                                  :placeholder "Enter username..."
                                  :saving? saving?
                                  :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-username" % api-client])})
           ($ field-error {:field-slug "online-grade-book-username"}))
        ($ field-cell {:label "Online Gradebook Password"}
           ($ editable-encrypted {:value (get field-values :online-grade-book-password)
                                  :placeholder "Enter password..."
                                  :saving? saving?
                                  :on-save #(rf/dispatch [::student-events/save-encrypted-field "online-grade-book-password" % api-client])})
           ($ field-error {:field-slug "online-grade-book-password"})))))

(defui transcript-subtab [{:keys [student]}]
  (let [ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        student-id (str (:student/id student))]
    ($ :<>
       ($ sub-header {:title "Transcript"})
       ($ :div {:class "flex flex-col items-start gap-3"}
          ($ :p {:class "text-sm text-muted-foreground"}
             "View and manage this fellow's uploaded grade transcripts, courses, and GPA.")
          ($ button/Button {:on-click #(navigate! :hub-transcript {:student-id student-id})}
             "Open Transcript")))))

(defui academics-tab [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        props {:student student :field-values field-values :api-client api-client :saving? saving?}]
    ($ nested-tabs
       {:tabs [{:id "school" :label "School" :render #($ school-subtab props)}
               {:id "gpa" :label "GPA" :render #($ gpa-subtab props)}
               {:id "coursework" :label "Coursework" :render #($ coursework-subtab props)}
               {:id "online-gradebook" :label "Online Gradebook" :render #($ online-gradebook-subtab props)}
               {:id "transcript" :label "Transcript" :render #($ transcript-subtab props)}]})))
