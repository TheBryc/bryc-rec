(ns components.crm.student.tabs.senior-year
  "Senior year planning tab for the student CRM detail view."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-textarea editable-number editable-select editable-tag-select]]
            [components.shared.formatting :refer [format-keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Senior Year Tab
;; =============================================================================

(def fellow-profile-statuses #{:fellow :vulnerable-fellow})

(defui legacy-senior-year-tab [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        first-name (first (str/split (or (:student/name student) "") #" "))
        senior-intake-survey-options (use-subscribe [::student-subs/field-options "senior-intake-survey"])
        admissions-plan-options (use-subscribe [::student-subs/field-options "admissions-plan"])
        color-profile-options (use-subscribe [::student-subs/field-options "color-profile"])
        goals-options (use-subscribe [::student-subs/field-options "post-hs-goals"])
        career-fields-options (use-subscribe [::student-subs/field-options "career-fields"])
        preferences-options (use-subscribe [::student-subs/field-options "preferences"])
        postsecondary-preferences-options (use-subscribe [::student-subs/field-options "postsecondary-preferences"])
        graduation-honors-options (use-subscribe [::student-subs/field-options "graduation-honors"])
        louisiana-young-hero-options (use-subscribe [::student-subs/field-options "louisiana-young-hero"])]

    ($ :<>
       ;; --- Senior Year Status ---
       ($ sub-header {:title "Senior Year Status"})
       ($ field-grid
          ($ field-cell {:label "Senior Year Intake Survey"}
             ($ editable-select
                {:value (get field-values :senior-intake-survey)
                 :options (or senior-intake-survey-options [])
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "senior-intake-survey" % api-client])})
             ($ field-error {:field-slug "senior-intake-survey"}))
          ($ field-cell {:label "Involvement Score"}
             ($ editable-number
                 {:value (:student/involvement-score student)
                  :placeholder "--"
                 :min 1 :max 4 :step 1
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "involvement-score" % api-client])})
             ($ field-error {:field-slug "involvement-score"}))
          ($ field-cell {:label "Color Profile"}
             ($ editable-select
                {:value (:student/color-profile student)
                 :options (or color-profile-options [])
                 :placeholder "Select..."
                 :saving? saving?
                 :format-fn format-keyword
                 :on-save #(rf/dispatch [::student-events/save-field "color-profile" % api-client])})
             ($ field-error {:field-slug "color-profile"}))
          ($ field-cell {:label "Postsecondary Preferences" :span :full}
             ($ editable-tag-select
                {:value (get field-values :postsecondary-preferences)
                 :options (or postsecondary-preferences-options [])
                 :placeholder "Select preferences..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "postsecondary-preferences" % api-client])})
             ($ field-error {:field-slug "postsecondary-preferences"})))

       ;; --- Senior Academics ---
       ($ sub-header {:title "Senior Academics"})
       ($ field-grid
          ($ field-cell {:label "Weighted Cumulative GPA"}
             ($ editable-number
                {:value (get field-values :weighted-cumulative-gpa)
                 :placeholder "--"
                 :min 0 :max 5.0 :step 0.01
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "weighted-cumulative-gpa" % api-client])})
             ($ field-error {:field-slug "weighted-cumulative-gpa"}))
          ($ field-cell {:label "Survey Response: Involvement"}
             ($ editable-text
                {:value (get field-values :survey-involvement)
                 :placeholder "Click to edit"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "survey-involvement" % api-client])})
             ($ field-error {:field-slug "survey-involvement"}))
          ($ field-cell {:label "Survey Response: Majors/Fields" :span :full}
             ($ editable-tag-select
                {:value (get field-values :survey-majors-fields)
                 :options (or (use-subscribe [::student-subs/field-options "survey-majors-fields"]) [])
                 :placeholder "Add majors/fields..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "survey-majors-fields" % api-client])})
             ($ field-error {:field-slug "survey-majors-fields"})))

       ;; --- Admissions ---
       ($ sub-header {:title "Admissions"})
       ($ field-grid
          ($ field-cell {:label "Admissions Plan"}
             ($ editable-select
                {:value (get field-values :admissions-plan)
                 :options (or admissions-plan-options [])
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "admissions-plan" % api-client])})
             ($ field-error {:field-slug "admissions-plan"}))
          ($ field-cell {:label "Total Submissions"}
             ($ editable-number
                {:value (get field-values :total-submissions)
                 :placeholder "--"
                 :min 0 :step 1
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "total-submissions" % api-client])})
             ($ field-error {:field-slug "total-submissions"}))
          ($ field-cell {:label "Total Acceptances"}
             ($ editable-number
                {:value (get field-values :total-acceptances)
                 :placeholder "--"
                 :min 0 :step 1
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "total-acceptances" % api-client])})
             ($ field-error {:field-slug "total-acceptances"})))

       ;; --- Awards & Honors ---
       ($ sub-header {:title "Awards & Honors"})
       ($ field-grid
          ($ field-cell {:label "Graduation Honors"}
             ($ editable-tag-select
                {:value (get field-values :graduation-honors)
                 :options graduation-honors-options
                 :placeholder "Select honors..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "graduation-honors" % api-client])})
             ($ field-error {:field-slug "graduation-honors"}))
          ($ field-cell {:label "Louisiana Young Hero"}
             ($ editable-select
                {:value (get field-values :louisiana-young-hero)
                 :options louisiana-young-hero-options
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "louisiana-young-hero" % api-client])})
             ($ field-error {:field-slug "louisiana-young-hero"})))

       ;; --- In Their Own Words ---
       ($ sub-header {:title "In Their Own Words"})
       ($ field-grid
          ($ field-cell {:label "Open Response" :span :full}
             ($ editable-textarea
                {:value (:student/open-response student)
                 :placeholder "Student's open response from intake..."
                 :saving? saving?
                 :rows 4
                 :on-save #(rf/dispatch [::student-events/save-field "open-response" % api-client])})
             ($ field-error {:field-slug "open-response"})))

       ;; --- What [FirstName] Wants ---
       ($ sub-header {:title (str "What " first-name " Wants")})
       ($ field-grid
          ($ field-cell {:label "Post-High School Goals" :span :full}
             ($ editable-tag-select
                {:value (:student/goals student)
                 :options goals-options
                 :placeholder "Add goals..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "post-hs-goals" % api-client])})
             ($ field-error {:field-slug "post-hs-goals"}))
          ($ field-cell {:label "Career Fields of Interest" :span :full}
             ($ editable-tag-select
                {:value (:student/career-fields student)
                 :options career-fields-options
                 :placeholder "Add career fields..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "career-fields" % api-client])})
             ($ field-error {:field-slug "career-fields"}))
          ($ field-cell {:label "Schools or Programs of Interest" :span :full}
             ($ editable-textarea
                {:value (:student/interests student)
                 :placeholder "Add schools or programs of interest..."
                 :saving? saving?
                 :rows 3
                 :on-save #(rf/dispatch [::student-events/save-field "schools-of-interest" % api-client])})
             ($ field-error {:field-slug "schools-of-interest"}))
          ($ field-cell {:label "Preferences and Circumstances" :span :full}
             ($ editable-tag-select
                {:value (:student/preferences student)
                 :options preferences-options
                 :placeholder "Add preferences..."
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "preferences" % api-client])})
             ($ field-error {:field-slug "preferences"}))))))

(defui fellow-senior-year-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        senior-intake-survey-options (use-subscribe [::student-subs/field-options "senior-intake-survey"])]
    ($ :<>
       ($ sub-header {:title "Senior Intake Survey"})
       ($ field-grid
          ($ field-cell {:label "Senior Intake Survey"}
             ($ editable-select
                {:value (get field-values :senior-intake-survey)
                 :options (or senior-intake-survey-options [])
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "senior-intake-survey" % api-client])})
             ($ field-error {:field-slug "senior-intake-survey"}))))))

(defui advisee-senior-year-tab [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        first-name (first (str/split (or (:student/name student) "") #" "))
        senior-intake-survey-options (use-subscribe [::student-subs/field-options "senior-intake-survey"])
        admissions-plan-options (use-subscribe [::student-subs/field-options "admissions-plan"])
        color-profile-options (use-subscribe [::student-subs/field-options "color-profile"])
        goals-options (use-subscribe [::student-subs/field-options "post-hs-goals"])
        career-fields-options (use-subscribe [::student-subs/field-options "career-fields"])
        preferences-options (use-subscribe [::student-subs/field-options "preferences"])
        postsecondary-preferences-options (use-subscribe [::student-subs/field-options "postsecondary-preferences"])
        graduation-honors-options (use-subscribe [::student-subs/field-options "graduation-honors"])
        louisiana-young-hero-options (use-subscribe [::student-subs/field-options "louisiana-young-hero"])
        survey-majors-fields-options (use-subscribe [::student-subs/field-options "survey-majors-fields"])
        render-status (fn []
                        ($ :<>
                           ($ sub-header {:title "Senior Year Status"})
                           ($ field-grid
                              ($ field-cell {:label "Senior Year Intake Survey"}
                                 ($ editable-select
                                     {:value (get field-values :senior-intake-survey)
                                     :options (or senior-intake-survey-options [])
                                     :placeholder "Select..."
                                     :saving? saving?
                                     :as-string? true
                                     :on-save #(rf/dispatch [::student-events/save-field "senior-intake-survey" % api-client])})
                                 ($ field-error {:field-slug "senior-intake-survey"}))
                              ($ field-cell {:label "Involvement Score"}
                                 ($ editable-number
                                     {:value (:student/involvement-score student)
                                     :placeholder "--"
                                     :min 1 :max 4 :step 1
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "involvement-score" % api-client])})
                                 ($ field-error {:field-slug "involvement-score"}))
                              ($ field-cell {:label "Color Profile"}
                                 ($ editable-select
                                    {:value (:student/color-profile student)
                                     :options (or color-profile-options [])
                                     :placeholder "Select..."
                                     :saving? saving?
                                     :format-fn format-keyword
                                     :on-save #(rf/dispatch [::student-events/save-field "color-profile" % api-client])})
                                 ($ field-error {:field-slug "color-profile"}))
                              ($ field-cell {:label "Postsecondary Preferences" :span :full}
                                 ($ editable-tag-select
                                    {:value (get field-values :postsecondary-preferences)
                                     :options (or postsecondary-preferences-options [])
                                     :placeholder "Select preferences..."
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "postsecondary-preferences" % api-client])})
                                 ($ field-error {:field-slug "postsecondary-preferences"})))))
        render-academics (fn []
                           ($ :<>
                              ($ sub-header {:title "Senior Academics"})
                              ($ field-grid
                                 ($ field-cell {:label "Weighted Cumulative GPA"}
                                    ($ editable-number
                                       {:value (get field-values :weighted-cumulative-gpa)
                                        :placeholder "--"
                                        :min 0 :max 5.0 :step 0.01
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-field "weighted-cumulative-gpa" % api-client])})
                                    ($ field-error {:field-slug "weighted-cumulative-gpa"}))
                                 ($ field-cell {:label "Survey Response: Involvement"}
                                    ($ editable-text
                                       {:value (get field-values :survey-involvement)
                                        :placeholder "Click to edit"
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-field "survey-involvement" % api-client])})
                                    ($ field-error {:field-slug "survey-involvement"}))
                                 ($ field-cell {:label "Survey Response: Majors/Fields" :span :full}
                                    ($ editable-tag-select
                                       {:value (get field-values :survey-majors-fields)
                                        :options (or survey-majors-fields-options [])
                                        :placeholder "Add majors/fields..."
                                        :saving? saving?
                                        :on-save #(rf/dispatch [::student-events/save-field "survey-majors-fields" % api-client])})
                                    ($ field-error {:field-slug "survey-majors-fields"})))))
        render-admissions (fn []
                            ($ :<>
                               ($ sub-header {:title "Admissions"})
                               ($ field-grid
                                  ($ field-cell {:label "Admissions Plan"}
                                     ($ editable-select
                                         {:value (get field-values :admissions-plan)
                                         :options (or admissions-plan-options [])
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :as-string? true
                                         :on-save #(rf/dispatch [::student-events/save-field "admissions-plan" % api-client])})
                                     ($ field-error {:field-slug "admissions-plan"}))
                                  ($ field-cell {:label "Total Submissions"}
                                     ($ editable-number
                                        {:value (get field-values :total-submissions)
                                         :placeholder "--"
                                         :min 0 :step 1
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "total-submissions" % api-client])})
                                     ($ field-error {:field-slug "total-submissions"}))
                                  ($ field-cell {:label "Total Acceptances"}
                                     ($ editable-number
                                        {:value (get field-values :total-acceptances)
                                         :placeholder "--"
                                         :min 0 :step 1
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "total-acceptances" % api-client])})
                                     ($ field-error {:field-slug "total-acceptances"})))))
        render-awards (fn []
                        ($ :<>
                           ($ sub-header {:title "Awards & Honors"})
                           ($ field-grid
                              ($ field-cell {:label "Graduation Honors"}
                                 ($ editable-tag-select
                                    {:value (get field-values :graduation-honors)
                                     :options graduation-honors-options
                                     :placeholder "Select honors..."
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "graduation-honors" % api-client])})
                                 ($ field-error {:field-slug "graduation-honors"}))
                              ($ field-cell {:label "Louisiana Young Hero"}
                                 ($ editable-select
                                    {:value (get field-values :louisiana-young-hero)
                                     :options louisiana-young-hero-options
                                     :placeholder "Select..."
                                     :saving? saving?
                                     :as-string? true
                                     :on-save #(rf/dispatch [::student-events/save-field "louisiana-young-hero" % api-client])})
                                 ($ field-error {:field-slug "louisiana-young-hero"})))))
        render-responses (fn []
                           ($ :<>
                              ($ sub-header {:title "In Their Own Words"})
                              ($ field-grid
                                 ($ field-cell {:label "Open Response" :span :full}
                                    ($ editable-textarea
                                       {:value (:student/open-response student)
                                        :placeholder "Student's open response from intake..."
                                        :saving? saving?
                                        :rows 4
                                        :on-save #(rf/dispatch [::student-events/save-field "open-response" % api-client])})
                                    ($ field-error {:field-slug "open-response"})))))
        render-goals (fn []
                       ($ :<>
                          ($ sub-header {:title (str "What " first-name " Wants")})
                          ($ field-grid
                             ($ field-cell {:label "Post-High School Goals" :span :full}
                                ($ editable-tag-select
                                   {:value (:student/goals student)
                                    :options goals-options
                                    :placeholder "Add goals..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-field "post-hs-goals" % api-client])})
                                ($ field-error {:field-slug "post-hs-goals"}))
                             ($ field-cell {:label "Career Fields of Interest" :span :full}
                                ($ editable-tag-select
                                   {:value (:student/career-fields student)
                                    :options career-fields-options
                                    :placeholder "Add career fields..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-field "career-fields" % api-client])})
                                ($ field-error {:field-slug "career-fields"}))
                             ($ field-cell {:label "Schools or Programs of Interest" :span :full}
                                ($ editable-textarea
                                   {:value (:student/interests student)
                                    :placeholder "Add schools or programs of interest..."
                                    :saving? saving?
                                    :rows 3
                                    :on-save #(rf/dispatch [::student-events/save-field "schools-of-interest" % api-client])})
                                ($ field-error {:field-slug "schools-of-interest"}))
                             ($ field-cell {:label "Preferences and Circumstances" :span :full}
                                ($ editable-tag-select
                                   {:value (:student/preferences student)
                                    :options preferences-options
                                    :placeholder "Add preferences..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-field "preferences" % api-client])})
                                ($ field-error {:field-slug "preferences"})))))]
    ($ nested-tabs
       {:tabs [{:id "status" :label "Status" :render render-status}
               {:id "academics" :label "Academics" :render render-academics}
               {:id "admissions" :label "Admissions" :render render-admissions}
               {:id "awards" :label "Awards" :render render-awards}
               {:id "responses" :label "Responses" :render render-responses}
               {:id "goals" :label "Goals" :render render-goals}]})))

(defui senior-year-tab [{:keys [field-values] :as props}]
  (cond
    (status/status-in? fellow-profile-statuses (get field-values :bryc-status))
    ($ fellow-senior-year-tab props)

    (status/advisee? (get field-values :bryc-status))
    ($ advisee-senior-year-tab props)

    :else
    ($ legacy-senior-year-tab props)))
