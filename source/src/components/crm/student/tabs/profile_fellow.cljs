(ns components.crm.student.tabs.profile-fellow
  "Fellow-only personal section profile flavor."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.tabs.family :refer [family-tab]]
            [components.crm.student.editable-field :refer [editable-text editable-select editable-select-with-other editable-tag-select]]
            [components.shared.formatting :refer [format-phone]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(defui identity-tab [{:keys [student field-values api-client saving?]}]
  (let [pronouns-options (use-subscribe [::student-subs/field-options "pronouns"])
        t-shirt-options (use-subscribe [::student-subs/field-options "t-shirt-size"])
        suffix (or (get field-values :suffix) (get field-values :name-suffix))
        last-name-display (str/trim (str (or (:student/last-name student) "")
                                         (when suffix (str " " suffix))))]
    ($ :<>
       ($ sub-header {:title "Identity"})
       ($ field-grid
          ($ field-cell {:label "First Name"}
             ($ editable-text {:value (:student/first-name student)
                               :placeholder "Set first name"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "first-name" % api-client])})
             ($ field-error {:field-slug "first-name"}))
          ($ field-cell {:label "Last Name"}
             ($ editable-text {:value last-name-display
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
          ($ field-cell {:label "Pronouns"}
             ($ editable-select {:value (:student/pronouns student)
                                 :options pronouns-options
                                 :placeholder "Add pronouns"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "pronouns" % api-client])})
             ($ field-error {:field-slug "pronouns"}))
          ($ field-cell {:label "Birthdate"}
             ($ editable-text {:value (:student/birthdate student)
                               :placeholder "Set birthdate"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "birthdate" % api-client])})
             ($ field-error {:field-slug "birthdate"}))
          ($ field-cell {:label "T-Shirt Size"}
             ($ editable-select {:value (get field-values :t-shirt-size)
                                 :options t-shirt-options
                                 :placeholder "Select size"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "t-shirt-size" % api-client])})
             ($ field-error {:field-slug "t-shirt-size"}))))))

(defui demographics-tab [{:keys [student field-values api-client saving?]}]
  (let [gender-options (use-subscribe [::student-subs/field-options "gender"])
        race-ethnicity-options (use-subscribe [::student-subs/field-options "race-ethnicity"])
        citizenship-options (use-subscribe [::student-subs/field-options "citizenship-status"])
        primary-language-options (use-subscribe [::student-subs/field-options "primary-language"])
        income-status-options (use-subscribe [::student-subs/field-options "income-status"])
        household-income-options (use-subscribe [::student-subs/field-options "household-income-range"])
        special-circumstances-options (use-subscribe [::student-subs/field-options "special-circumstances"])]
    ($ :<>
       ($ sub-header {:title "Demographics"})
       ($ field-grid
          ($ field-cell {:label "Race / Ethnicity" :span :full}
             ($ editable-tag-select {:value (:student/ethnicity student)
                                     :options race-ethnicity-options
                                     :placeholder "Select race / ethnicity"
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "race-ethnicity" % api-client])})
             ($ field-error {:field-slug "race-ethnicity"}))
          ($ field-cell {:label "Gender"}
             ($ editable-select {:value (:student/gender student)
                                 :options gender-options
                                 :placeholder "Add gender"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "gender" % api-client])})
             ($ field-error {:field-slug "gender"}))
          ($ field-cell {:label "Citizenship Status"}
             ($ editable-select {:value (get field-values :citizenship-status)
                                 :options citizenship-options
                                 :placeholder "Select status"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "citizenship-status" % api-client])})
             ($ field-error {:field-slug "citizenship-status"}))
          ($ field-cell {:label "Primary Language"}
             ($ editable-select-with-other {:value (:student/primary-language student)
                                            :options primary-language-options
                                            :placeholder "Select language"
                                            :custom-placeholder "Enter language"
                                            :saving? saving?
                                            :on-save #(rf/dispatch [::student-events/save-field "primary-language" % api-client])})
             ($ field-error {:field-slug "primary-language"}))
          ($ field-cell {:label "Household Income Range"}
             ($ editable-select {:value (get field-values :household-income-range)
                                 :options household-income-options
                                 :placeholder "Select range"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "household-income-range" % api-client])})
             ($ field-error {:field-slug "household-income-range"}))
          ($ field-cell {:label "Income Status" :span :full}
             ($ editable-tag-select {:value (get field-values :income-status)
                                     :options income-status-options
                                     :placeholder "Select income status"
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "income-status" % api-client])})
             ($ field-error {:field-slug "income-status"}))
          ($ field-cell {:label "Special Circumstances" :span :full}
             ($ editable-tag-select {:value (get field-values :special-circumstances)
                                     :options special-circumstances-options
                                     :placeholder "Select circumstances"
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "special-circumstances" % api-client])})
             ($ field-error {:field-slug "special-circumstances"}))))))

(defui contact-tab [{:keys [student field-values api-client saving?]}]
  (let [state-options (use-subscribe [::student-subs/field-options "state"])
        parish-options (use-subscribe [::student-subs/field-options "parish"])]
    ($ :<>
       ($ sub-header {:title "Contact"})
       ($ field-grid
          ($ field-cell {:label "Personal Email"}
             ($ editable-text {:value (:student/email student)
                               :placeholder "Add email"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "email" % api-client])})
             ($ field-error {:field-slug "email"}))
          ($ field-cell {:label "Cell Phone"}
             ($ editable-text {:value (format-phone (:student/phone student))
                               :placeholder "Add phone"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "phone" % api-client])})
             ($ field-error {:field-slug "phone"}))
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
          ($ field-cell {:label "Zip Code"}
             ($ editable-text {:value (:student/home-zip student)
                               :placeholder "Add zip code"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "home-zip" % api-client])})
             ($ field-error {:field-slug "home-zip"}))
          ($ field-cell {:label "Parish"}
             ($ editable-select {:value (get field-values :parish)
                                 :options parish-options
                                 :placeholder "Select parish"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "parish" % api-client])})
             ($ field-error {:field-slug "parish"}))))))

(defui fellow-profile-tab [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        props {:student student :field-values field-values :api-client api-client :saving? saving?}]
    ($ nested-tabs
       {:tabs [{:id "identity" :label "Identity" :render #($ identity-tab props)}
               {:id "demographics" :label "Demographics" :render #($ demographics-tab props)}
               {:id "contact" :label "Contact" :render #($ contact-tab props)}
               {:id "household" :label "Household" :render #($ family-tab props)}]})))
