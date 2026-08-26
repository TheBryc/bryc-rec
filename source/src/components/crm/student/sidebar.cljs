(ns components.crm.student.sidebar
  "Sidebar components for the student CRM detail view.
   Contains status/assignment, contact, school, team, program,
   community, and identity cards."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.profile-layout :refer [sidebar-card field-row]]
            [components.crm.student.editable-field :refer [editable-text editable-number editable-select editable-tag-select editable-encrypted]]
            [components.shared.formatting :refer [format-keyword format-phone normalize-phone validate-email validate-phone]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Advisor Status helpers
;; =============================================================================

(def advisor-status-options
  ["Needs Initial Text"
   "No Response to Initial Communication"
   "Needs to Schedule Intake Interview"
   "Needs to Conduct Intake Interview"
   "Intake Interview Completed"
   "Needs Recommendation Document"
   "Recommendations Sent"
   "Not Eligible"
   "Duplicate"])

(defn keyword->status-label [kw]
  (when kw
    (-> (name kw)
        (str/replace "-" " ")
        (str/split #" ")
        (->> (map str/capitalize)
             (str/join " ")))))

(defn status-label->keyword [label]
  (when label
    (-> label
        str/lower-case
        (str/replace " " "-")
        keyword)))

;; =============================================================================
;; Sidebar Cards
;; =============================================================================

(defui status-assignment-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-status-options (use-subscribe [::student-subs/field-options "bryc-status"])
        returning-options (use-subscribe [::student-subs/field-options "returning-or-new"])]
    ($ sidebar-card {:title "Status & Assignment"}
       ($ :dl
          (when (status/advisee? (get field-values :bryc-status))
            ($ field-row {:label "Advisor Status"
                          :value ($ :<>
                                    ($ editable-select
                                       {:value (keyword->status-label (:student/advisor-status student))
                                        :options advisor-status-options
                                        :placeholder "Set status..."
                                        :saving? saving?
                                        :as-string? true
                                        :on-save #(rf/dispatch [::student-events/save-field "advisor-status" (status-label->keyword %) api-client])})
                                    ($ field-error {:field-slug "advisor-status"}))}))
          ($ field-row {:label "Returning / New"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (get field-values :returning-or-new)
                                      :options returning-options
                                      :placeholder "Select..."
                                      :saving? saving?
                                      :as-string? true
                                      :on-save #(rf/dispatch [::student-events/save-field "returning-or-new" % api-client])})
                                  ($ field-error {:field-slug "returning-or-new"}))})
          ($ field-row {:label "BRYC Status"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (get field-values :bryc-status)
                                      :options bryc-status-options
                                      :placeholder "Set status..."
                                      :saving? saving?
                                      :as-string? true
                                      :on-save #(rf/dispatch [::student-events/save-field "bryc-status" % api-client])})
                                  ($ field-error {:field-slug "bryc-status"}))})))))

(defui contact-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ sidebar-card {:title "Contact"}
       ($ :dl
          ($ field-row {:label "Email"
                        :value ($ :<>
                                  ($ editable-text
                                     {:value (:student/email student)
                                      :placeholder "Add email"
                                      :saving? saving?
                                      :validate validate-email
                                      :on-save #(rf/dispatch [::student-events/save-field "email" % api-client])})
                                  ($ field-error {:field-slug "email"}))})
          ($ field-row {:label "Phone"
                        :value ($ :<>
                                  ($ editable-text
                                     {:value (format-phone (:student/phone student))
                                      :placeholder "Add phone"
                                      :saving? saving?
                                      :validate validate-phone
                                      :on-save #(rf/dispatch [::student-events/save-field "phone" (normalize-phone %) api-client])})
                                  ($ field-error {:field-slug "phone"}))})))))

(defui school-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        graduation-year-options (use-subscribe [::student-subs/field-options "graduation-year"])]
    ($ sidebar-card {:title "School"}
       ($ :dl
          ($ field-row {:label "High School"
                        :value ($ :<>
                                  ($ editable-text
                                     {:value (:student/school student)
                                      :placeholder "Add school"
                                      :saving? saving?
                                      :on-save #(rf/dispatch [::student-events/save-field "high-school" % api-client])})
                                  ($ field-error {:field-slug "high-school"}))})
          ($ field-row {:label "Graduation Year"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (:student/graduation-year student)
                                      :options graduation-year-options
                                      :placeholder "Select year"
                                      :saving? saving?
                                      :as-string? true
                                      :on-save #(rf/dispatch [::student-events/save-field "graduation-year" % api-client])})
                                  ($ field-error {:field-slug "graduation-year"}))})))))

(defui team-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ sidebar-card {:title "Team"}
       ($ :dl
          ($ field-row {:label "Advisor"
                        :value (or (:student/advisor-name student) "Unassigned")})
          ($ field-row {:label "Cohort"
                        :value (format-keyword (get field-values :cohort))})))))

(defui program-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ sidebar-card {:title "Program"}
       ($ :dl
          ($ field-row {:label "Track"
                        :value (format-keyword (get field-values :track))})
          ($ field-row {:label "Tier"
                        :value (format-keyword (get field-values :tier))})))))

(defui community-card [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        bryc-team-options (use-subscribe [::student-subs/field-options "bryc-team"])
        bryc-donor-options (use-subscribe [::student-subs/field-options "bryc-donor"])]
    ($ sidebar-card {:title "Community"}
       ($ :dl
          ($ field-row {:label "BRYC VIP"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (get field-values :bryc-vip)
                                      :options ["Yes" "No"]
                                      :placeholder "Select..."
                                      :saving? saving?
                                      :as-string? true
                                      :on-save #(rf/dispatch [::student-events/save-field "bryc-vip" % api-client])})
                                  ($ field-error {:field-slug "bryc-vip"}))})
          ($ field-row {:label "BRYC Team"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (get field-values :bryc-team)
                                      :options bryc-team-options
                                      :placeholder "Select..."
                                      :saving? saving?
                                      :on-save #(rf/dispatch [::student-events/save-field "bryc-team" % api-client])})
                                  ($ field-error {:field-slug "bryc-team"}))})
          ($ field-row {:label "BRYC Donor"
                        :value ($ :<>
                                  ($ editable-select
                                     {:value (get field-values :bryc-donor)
                                      :options bryc-donor-options
                                      :placeholder "Select..."
                                      :saving? saving?
                                      :on-save #(rf/dispatch [::student-events/save-field "bryc-donor" % api-client])})
                                  ($ field-error {:field-slug "bryc-donor"}))})
          ($ field-row {:label "Alumni Engagement Score"
                        :value ($ :<>
                                  ($ editable-number
                                     {:value (get field-values :alumni-engagement-score)
                                      :min 0
                                      :max 3
                                      :step 1
                                      :placeholder "0-3"
                                      :saving? saving?
                                      :on-save #(rf/dispatch [::student-events/save-field "alumni-engagement-score" % api-client])})
                                  ($ field-error {:field-slug "alumni-engagement-score"}))})))))

;; =============================================================================
;; Main Sidebar Component
;; =============================================================================

(defui student-sidebar
  "Full sidebar for the student CRM detail view.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [bryc-status (get field-values :bryc-status)
        saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ;; Identity card (preferred name & suffix)
       ($ sidebar-card {:title "Identity"}
          ($ :dl
             ($ field-row {:label "Preferred Name"
                           :value ($ :<>
                                     ($ editable-text
                                        {:value (get field-values :preferred-name)
                                         :placeholder "Set preferred name"
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "preferred-name" % api-client])})
                                     ($ field-error {:field-slug "preferred-name"}))})
             ($ field-row {:label "Suffix"
                           :value ($ :<>
                                     ($ editable-text
                                        {:value (get field-values :name-suffix)
                                         :placeholder "e.g., Jr., III"
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "name-suffix" % api-client])})
                                     ($ field-error {:field-slug "name-suffix"}))})))
       ;; Demographics at-a-glance (Elin: race/gender/birthdate near the name)
       (let [gender (get field-values :gender)
             ethnicity (get field-values :race-ethnicity)
             birthdate (get field-values :birthdate)]
         (when (or gender ethnicity birthdate)
           ($ sidebar-card {:title "Demographics"}
              ($ :dl
                 (when gender
                   ($ field-row {:label "Gender" :value gender}))
                 (when ethnicity
                   ($ field-row {:label "Ethnicity"
                                 :value (if (set? ethnicity)
                                          (clojure.string/join ", " (sort ethnicity))
                                          (str ethnicity))}))
                 (when birthdate
                   ($ field-row {:label "Birthdate" :value (str birthdate)}))))))
       ($ status-assignment-card {:student student :field-values field-values :api-client api-client})
       ($ contact-card {:student student :field-values field-values :api-client api-client})
       ($ school-card {:student student :field-values field-values :api-client api-client})
       ($ team-card {:student student :field-values field-values :api-client api-client})
       ($ program-card {:student student :field-values field-values :api-client api-client})
       (when (status/alumni? bryc-status)
         ($ community-card {:student student :field-values field-values :api-client api-client})))))
