(ns components.crm.student.tabs.profile-advisee
  "Advisee-only personal section profile flavor."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-textarea editable-select editable-select-with-other editable-tag-select]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.formatting :refer [format-phone]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(def narrative-additions-sub-schema
  {:type :vector
   :add-button-text "Add"
   :empty-message "No narrative additions yet."
   :item-label-key :text
   :item-schema
   {:type :map
    :fields [{:key :text
              :type :string
              :required true
              :label "Narrative Addition"
              :input-type "textarea"
              :rows 3
              :placeholder "Enter narrative addition..."}]}})

(defui identity-tab [{:keys [student field-values api-client saving?]}]
  (let [bryc-status-options (use-subscribe [::student-subs/field-options "bryc-status"])
        suffix-options (use-subscribe [::student-subs/field-options "suffix"])]
    ($ :<>
       ($ sub-header {:title "Identity & Status"})
       ($ field-grid
          ($ field-cell {:label "First Name"}
             ($ editable-text {:value (:student/first-name student)
                               :placeholder "Set first name"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "first-name" % api-client])})
             ($ field-error {:field-slug "first-name"}))
          ($ field-cell {:label "Last Name"}
             ($ editable-text {:value (:student/last-name student)
                               :placeholder "Set last name"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "last-name" % api-client])})
             ($ field-error {:field-slug "last-name"}))
          ($ field-cell {:label "Preferred Name"}
             ($ editable-text {:value (get field-values :preferred-name)
                               :placeholder "Set preferred name"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "preferred-name" % api-client])})
             ($ field-error {:field-slug "preferred-name"}))
          ($ field-cell {:label "Suffix"}
             ($ editable-select {:value (get field-values :name-suffix)
                                 :options suffix-options
                                 :placeholder "Select suffix"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "name-suffix" % api-client])})
             ($ field-error {:field-slug "name-suffix"}))
          ($ field-cell {:label "BRYC Status"}
             ($ editable-select {:value (get field-values :bryc-status)
                                 :options bryc-status-options
                                 :placeholder "Set status..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "bryc-status" % api-client])})
             ($ field-error {:field-slug "bryc-status"}))))))

(defui demographics-tab [{:keys [student field-values api-client saving?]}]
  (let [pronouns-options (use-subscribe [::student-subs/field-options "pronouns"])
        gender-options (use-subscribe [::student-subs/field-options "gender"])
        race-ethnicity-options (use-subscribe [::student-subs/field-options "race-ethnicity"])
        citizenship-options (use-subscribe [::student-subs/field-options "citizenship-status"])
        immigration-options (use-subscribe [::student-subs/field-options "immigration-status"])]
    ($ :<>
       ($ sub-header {:title "Demographics"})
       ($ field-grid
          ($ field-cell {:label "Pronouns"}
             ($ editable-select {:value (:student/pronouns student)
                                 :options pronouns-options
                                 :placeholder "Add pronouns"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "pronouns" % api-client])})
             ($ field-error {:field-slug "pronouns"}))
          ($ field-cell {:label "Gender"}
             ($ editable-select {:value (:student/gender student)
                                 :options gender-options
                                 :placeholder "Add gender"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "gender" % api-client])})
             ($ field-error {:field-slug "gender"}))
          ($ field-cell {:label "Ethnicity" :span :full}
             ($ editable-tag-select {:value (:student/ethnicity student)
                                     :options race-ethnicity-options
                                     :placeholder "Select ethnicity"
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "race-ethnicity" % api-client])})
             ($ field-error {:field-slug "race-ethnicity"}))
          ($ field-cell {:label "Birthdate"}
             ($ editable-text {:value (:student/birthdate student)
                               :placeholder "Set birthdate"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "birthdate" % api-client])})
             ($ field-error {:field-slug "birthdate"}))
          ($ field-cell {:label "Citizenship Status"}
             ($ editable-select {:value (get field-values :citizenship-status)
                                 :options citizenship-options
                                 :placeholder "Select status"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "citizenship-status" % api-client])})
             ($ field-error {:field-slug "citizenship-status"}))
          ($ field-cell {:label "Immigration Status"}
             ($ editable-select {:value (get field-values :immigration-status)
                                 :options immigration-options
                                 :placeholder "Not answered"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "immigration-status" % api-client])})
             ($ field-error {:field-slug "immigration-status"}))))))

(defui contact-tab [{:keys [student api-client saving?]}]
  (let [primary-language-options (use-subscribe [::student-subs/field-options "primary-language"])]
    ($ :<>
       ($ sub-header {:title "Contact"})
       ($ field-grid
          ($ field-cell {:label "Email"}
             ($ editable-text {:value (:student/email student)
                               :placeholder "Add email"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "email" % api-client])})
             ($ field-error {:field-slug "email"}))
          ($ field-cell {:label "Phone"}
             ($ editable-text {:value (format-phone (:student/phone student))
                               :placeholder "Add phone"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "phone" % api-client])})
             ($ field-error {:field-slug "phone"}))
          ($ field-cell {:label "Primary Language"}
             ($ editable-select-with-other {:value (:student/primary-language student)
                                            :options primary-language-options
                                            :placeholder "Select language"
                                            :custom-placeholder "Enter language"
                                            :saving? saving?
                                            :on-save #(rf/dispatch [::student-events/save-field "primary-language" % api-client])})
             ($ field-error {:field-slug "primary-language"}))
          ($ field-cell {:label "Home Zip"}
             ($ editable-text {:value (:student/home-zip student)
                               :placeholder "Add zip code"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "home-zip" % api-client])})
             ($ field-error {:field-slug "home-zip"}))))))

(defui address-tab [{:keys [field-values api-client saving?]}]
  (let [state-options (use-subscribe [::student-subs/field-options "state"])
        parish-options (use-subscribe [::student-subs/field-options "parish"])]
    ($ :<>
       ($ sub-header {:title "Address"})
       ($ field-grid
          ($ field-cell {:label "Mailing Address" :span :full}
             ($ editable-text {:value (get field-values :mailing-address)
                               :placeholder "Enter mailing address"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "mailing-address" % api-client])})
             ($ field-error {:field-slug "mailing-address"}))
          ($ field-cell {:label "City"}
             ($ editable-text {:value (get field-values :city)
                               :placeholder "Enter city"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "city" % api-client])})
             ($ field-error {:field-slug "city"}))
          ($ field-cell {:label "State"}
             ($ editable-select {:value (get field-values :state)
                                 :options state-options
                                 :placeholder "Select state"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "state" % api-client])})
             ($ field-error {:field-slug "state"}))
          ($ field-cell {:label "Parish"}
             ($ editable-select {:value (get field-values :parish)
                                 :options parish-options
                                 :placeholder "Select parish"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "parish" % api-client])})
             ($ field-error {:field-slug "parish"}))))))

(defui summary-tab [{:keys [student api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "Summary"})
     ($ field-grid
        ($ field-cell {:label "Student Summary" :span :full}
           ($ editable-textarea {:value (:student/summary student)
                                 :placeholder "Add student summary..."
                                 :saving? saving?
                                 :rows 4
                                 :on-save #(rf/dispatch [::student-events/save-field "summary" % api-client])})
           ($ field-error {:field-slug "summary"})))
     ($ sub-header {:title "Narrative Additions"})
     ($ schema-vector-editor {:sub-schema narrative-additions-sub-schema
                              :value (:student/narrative-additions student)
                              :saving? saving?
                              :on-save #(rf/dispatch [::student-events/save-field "narrative-additions" % api-client])})
     ($ field-error {:field-slug "narrative-additions"})))

(defui advisee-profile-tab [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        props {:student student :field-values field-values :api-client api-client :saving? saving?}]
    ($ nested-tabs
       {:tabs [{:id "identity" :label "Identity" :render #($ identity-tab props)}
               {:id "demographics" :label "Demographics" :render #($ demographics-tab props)}
               {:id "contact" :label "Contact" :render #($ contact-tab props)}
               {:id "address" :label "Address" :render #($ address-tab props)}
               {:id "summary" :label "Summary" :render #($ summary-tab props)}]})))
