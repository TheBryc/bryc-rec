(ns components.crm.student.tabs.admin
  "Health tab for the student CRM detail view."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-textarea editable-select editable-tag-select editable-encrypted]]
            [components.shared.formatting :refer [format-keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Section: Health
;; =============================================================================

(defui health-section [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        diet-options (use-subscribe [::student-subs/field-options "dietary-restrictions"])
        otc-options (use-subscribe [::student-subs/field-options "otc-medications"])
        neurodivergent-identity-options (use-subscribe [::student-subs/field-options "neurodivergent-identity"])
        bryc-status (get field-values :bryc-status)
        fellow? (status/fellow? bryc-status)
        alumni? (status/alumni? bryc-status)
        fellow-or-alumni? (or fellow? alumni?)
        advisee? (status/advisee? bryc-status)]
    ($ :<>
       ($ sub-header {:title "Health"})
       ($ field-grid
       ;; Allergies
       (when fellow-or-alumni?
         ($ field-cell {:label "Allergies" :span :full}
            ($ editable-textarea
               {:value (get field-values :allergies)
                :placeholder "List any allergies..."
                :saving? saving?
                :rows 2
                :on-save #(rf/dispatch [::student-events/save-field "allergies" % api-client])})
            ($ field-error {:field-slug "allergies"})))

       ;; Dietary Restrictions
       (when fellow-or-alumni?
         ($ field-cell {:label "Dietary Restrictions" :span :full}
            ($ editable-tag-select
               {:value (get field-values :dietary-restrictions)
                :options (or diet-options [])
                :placeholder "Select dietary restrictions..."
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "dietary-restrictions" % api-client])})
            ($ field-error {:field-slug "dietary-restrictions"})))

       ;; Activity Restrictions
       (when fellow?
         ($ field-cell {:label "Activity Restrictions" :span :full}
            ($ editable-textarea
               {:value (get field-values :activity-restrictions)
                :placeholder "List any activity restrictions..."
                :saving? saving?
                :rows 2
                :on-save #(rf/dispatch [::student-events/save-field "activity-restrictions" % api-client])})
            ($ field-error {:field-slug "activity-restrictions"})))

       ;; Prescriptions
       (when fellow?
         ($ field-cell {:label "Prescriptions" :span :full}
            ($ editable-encrypted
               {:value (get field-values :prescriptions)
                :placeholder "Enter prescription medications..."
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-encrypted-field "prescriptions" % api-client])})
            ($ field-error {:field-slug "prescriptions"})))

       ;; OTC Medications
       (when fellow?
         ($ field-cell {:label "OTC Medications" :span :full}
            ($ editable-tag-select
               {:value (get field-values :otc-medications)
                :options (or otc-options [])
                :placeholder "Select OTC medications..."
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "otc-medications" % api-client])})
            ($ field-error {:field-slug "otc-medications"})))

       ;; Neurodivergent Identity
       (when (or fellow? alumni? advisee?)
         ($ field-cell {:label "Neurodivergent Identity"}
            ($ editable-select
               {:value (let [v (get field-values :neurodivergent-identity)]
                         (cond (true? v) "Yes" (false? v) "No" :else v))
                :options neurodivergent-identity-options
                :placeholder "Select..."
                :saving? saving?
                :as-string? true
                :on-save #(rf/dispatch [::student-events/save-field "neurodivergent-identity" (= % "Yes") api-client])})
            ($ field-error {:field-slug "neurodivergent-identity"})))

       ;; Neurodivergent Opportunities Interest
       (let [neurodivergent-opps-options (use-subscribe [::student-subs/field-options "neurodivergent-opportunities"])]
         ($ field-cell {:label "Neurodivergent Opportunities Interest"}
            ($ editable-select
               {:value (get field-values :neurodivergent-opportunities)
                :options (or neurodivergent-opps-options ["Yes, please." "No, thank you."])
                :placeholder "Not answered"
                :saving? saving?
                :as-string? true
                :on-save #(rf/dispatch [::student-events/save-field "neurodivergent-opportunities" % api-client])})
            ($ field-error {:field-slug "neurodivergent-opportunities"})))))))

;; =============================================================================
;; Admin (Health) Tab - Main Component
;; =============================================================================

(def fellow-profile-statuses #{:fellow :vulnerable-fellow})

(defui fellow-physical-health-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        diet-options (use-subscribe [::student-subs/field-options "dietary-restrictions"])]
    ($ :<>
       ($ sub-header {:title "Physical Health"})
       ($ field-grid
          ($ field-cell {:label "Non-Food Allergies" :span :full}
             ($ editable-textarea {:value (get field-values :allergies)
                                   :placeholder "List any non-food allergies..."
                                   :saving? saving?
                                   :rows 2
                                   :on-save #(rf/dispatch [::student-events/save-field "allergies" % api-client])})
             ($ field-error {:field-slug "allergies"}))
          ($ field-cell {:label "Dietary Restrictions" :span :full}
             ($ editable-tag-select {:value (get field-values :dietary-restrictions)
                                     :options (or diet-options [])
                                     :placeholder "Select dietary restrictions..."
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "dietary-restrictions" % api-client])})
             ($ field-error {:field-slug "dietary-restrictions"}))
          ($ field-cell {:label "Activity Restrictions" :span :full}
             ($ editable-textarea {:value (get field-values :activity-restrictions)
                                   :placeholder "List any activity restrictions..."
                                   :saving? saving?
                                   :rows 2
                                   :on-save #(rf/dispatch [::student-events/save-field "activity-restrictions" % api-client])})
             ($ field-error {:field-slug "activity-restrictions"}))))))

(defui fellow-mental-health-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        neurodivergent-identity-options (use-subscribe [::student-subs/field-options "neurodivergent-identity"])]
    ($ :<>
       ($ sub-header {:title "Mental Health"})
       ($ field-grid
          ($ field-cell {:label "Neurodivergent Identity"}
             ($ editable-select {:value (let [v (get field-values :neurodivergent-identity)]
                                          (cond (true? v) "Yes" (false? v) "No" :else v))
                                 :options neurodivergent-identity-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "neurodivergent-identity" (= % "Yes") api-client])})
             ($ field-error {:field-slug "neurodivergent-identity"}))))))

(defui fellow-medication-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        otc-options (use-subscribe [::student-subs/field-options "otc-medications"])]
    ($ :<>
       ($ sub-header {:title "Medication"})
       ($ field-grid
          ($ field-cell {:label "Prescriptions" :span :full}
             ($ editable-encrypted {:value (get field-values :prescriptions)
                                    :placeholder "Enter prescription medications..."
                                    :saving? saving?
                                    :on-save #(rf/dispatch [::student-events/save-encrypted-field "prescriptions" % api-client])})
             ($ field-error {:field-slug "prescriptions"}))
          ($ field-cell {:label "OTC Medications" :span :full}
             ($ editable-tag-select {:value (get field-values :otc-medications)
                                     :options (or otc-options [])
                                     :placeholder "Select OTC medications..."
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "otc-medications" % api-client])})
             ($ field-error {:field-slug "otc-medications"}))))))

(defui fellow-forms-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        onboarding-options (use-subscribe [::student-subs/field-options "onboarding-form"])]
    ($ :<>
       ($ sub-header {:title "Waivers & Forms"})
       ($ field-grid
          ($ field-cell {:label "Onboarding Form"}
             ($ editable-select {:value (get field-values :onboarding-form)
                                 :options onboarding-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "onboarding-form" % api-client])})
             ($ field-error {:field-slug "onboarding-form"}))
          ($ field-cell {:label "Signed Waivers" :span :full}
             ($ :div {:class "py-1 text-sm text-muted-foreground"} "Use the existing Waivers data field for detailed tracking."))))))

(defui fellow-health-tab [{:keys [field-values api-client]}]
  ($ nested-tabs
     {:tabs [{:id "physical-health" :label "Physical Health" :render #($ fellow-physical-health-tab {:field-values field-values :api-client api-client})}
             {:id "mental-health" :label "Mental Health" :render #($ fellow-mental-health-tab {:field-values field-values :api-client api-client})}
             {:id "medication" :label "Medication" :render #($ fellow-medication-tab {:field-values field-values :api-client api-client})}
             {:id "forms" :label "Waivers & Forms" :render #($ fellow-forms-tab {:field-values field-values :api-client api-client})}]}))

(defui advisee-mental-health-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        neurodivergent-identity-options (use-subscribe [::student-subs/field-options "neurodivergent-identity"])]
    ($ :<>
       ($ sub-header {:title "Mental Health"})
       ($ field-grid
          ($ field-cell {:label "Neurodivergent Identity"}
             ($ editable-select
                {:value (let [v (get field-values :neurodivergent-identity)]
                          (cond (true? v) "Yes" (false? v) "No" :else v))
                 :options neurodivergent-identity-options
                 :placeholder "Select..."
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "neurodivergent-identity" (= % "Yes") api-client])})
             ($ field-error {:field-slug "neurodivergent-identity"}))))))

(defui advisee-opportunities-tab [{:keys [field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        neurodivergent-opps-options (use-subscribe [::student-subs/field-options "neurodivergent-opportunities"])]
    ($ :<>
       ($ sub-header {:title "Opportunities"})
       ($ field-grid
          ($ field-cell {:label "Neurodivergent Opportunities Interest"}
             ($ editable-select
                {:value (get field-values :neurodivergent-opportunities)
                 :options (or neurodivergent-opps-options ["Yes, please." "No, thank you."])
                 :placeholder "Not answered"
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "neurodivergent-opportunities" % api-client])})
             ($ field-error {:field-slug "neurodivergent-opportunities"}))))))

(defui advisee-health-tab [{:keys [field-values api-client]}]
  ($ nested-tabs
     {:tabs [{:id "mental-health" :label "Mental Health" :render #($ advisee-mental-health-tab {:field-values field-values :api-client api-client})}
             {:id "opportunities" :label "Opportunities" :render #($ advisee-opportunities-tab {:field-values field-values :api-client api-client})}]}))

(defui admin-tab [{:keys [student field-values api-client]}]
  (cond
    (status/status-in? fellow-profile-statuses (get field-values :bryc-status))
    ($ fellow-health-tab {:field-values field-values :api-client api-client})

    (status/advisee? (get field-values :bryc-status))
    ($ advisee-health-tab {:field-values field-values :api-client api-client})

    :else
    ($ health-section {:field-values field-values :api-client api-client})))
