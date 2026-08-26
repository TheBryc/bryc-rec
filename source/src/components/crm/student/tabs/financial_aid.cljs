(ns components.crm.student.tabs.financial-aid
  "Financial Aid tab for the student CRM detail view.
   Contains income, grants/aid, and scholarship tracking fields."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-number editable-select editable-tag-select]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.formatting :refer [format-keyword]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

(def other-scholarships-sub-schema
  {:type :vector
   :add-button-text "Add Scholarship"
   :empty-message "No other scholarships."
   :item-label-key :name
   :item-schema
   {:type :map
    :fields [{:key :name
              :type :string
              :required true
              :label "Scholarship Name"
              :placeholder "Enter scholarship name..."}
             {:key :amount
              :type :number
              :label "Amount"}]}})

(defui financial-aid-tab
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        full-ride-options (use-subscribe [::student-subs/field-options "full-ride"])
        school-debt-options (use-subscribe [::student-subs/field-options "school-debt"])
        gates-scholar-options (use-subscribe [::student-subs/field-options "gates-scholar"])
        questbridge-scholar-options (use-subscribe [::student-subs/field-options "questbridge-scholar"])
        bonner-scholar-options (use-subscribe [::student-subs/field-options "bonner-scholar"])
        torch-scholar-options (use-subscribe [::student-subs/field-options "torch-scholar"])
        bryc-scholarships-options (use-subscribe [::student-subs/field-options "bryc-scholarships"])
        household-income-range-options (use-subscribe [::student-subs/field-options "household-income-range"])
        income-status-options (use-subscribe [::student-subs/field-options "income-status"])
        pell-redeemed-options (use-subscribe [::student-subs/field-options "pell-redeemed"])
        tops-redeemed-options (use-subscribe [::student-subs/field-options "tops-redeemed"])
        work-study-options (use-subscribe [::student-subs/field-options "work-study"])
        bryc-status (get field-values :bryc-status)
        fellow? (status/fellow? bryc-status)
        alumni? (status/alumni? bryc-status)
        fellow-or-alumni? (or fellow? alumni?)
        advisee? (status/advisee? bryc-status)
        render-income (fn []
                        ($ :<>
                           ($ sub-header {:title "Income"})
                           ($ field-grid
                              ($ field-cell {:label "Household Income Range"}
                                 ($ editable-select
                                    {:value (get field-values :household-income-range)
                                     :options household-income-range-options
                                     :placeholder "Select income range"
                                     :saving? saving?
                                     :as-string? true
                                     :on-save #(rf/dispatch [::student-events/save-field "household-income-range" % api-client])})
                                 ($ field-error {:field-slug "household-income-range"}))
                              ($ field-cell {:label "Income Status" :span :full}
                                 ($ editable-tag-select
                                    {:value (get field-values :income-status)
                                     :options income-status-options
                                     :placeholder "Select income status"
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "income-status" % api-client])})
                                 ($ field-error {:field-slug "income-status"})))))
        render-grants-aid (fn []
                            ($ :<>
                               ($ sub-header {:title "Grants & Aid"})
                               ($ field-grid
                                  ($ field-cell {:label "Pell Redeemed"}
                                     ($ editable-select
                                        {:value (get field-values :pell-redeemed)
                                         :options pell-redeemed-options
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :as-string? true
                                         :on-save #(rf/dispatch [::student-events/save-field "pell-redeemed" % api-client])})
                                     ($ field-error {:field-slug "pell-redeemed"}))
                                  ($ field-cell {:label "TOPS Redeemed"}
                                     ($ editable-select
                                        {:value (get field-values :tops-redeemed)
                                         :options tops-redeemed-options
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :as-string? true
                                         :on-save #(rf/dispatch [::student-events/save-field "tops-redeemed" % api-client])})
                                     ($ field-error {:field-slug "tops-redeemed"}))
                                  ($ field-cell {:label "Total Gift Aid Awarded"}
                                     ($ editable-number
                                        {:value (get field-values :total-gift-aid-awarded)
                                         :placeholder "--"
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "total-gift-aid-awarded" % api-client])})
                                     ($ field-error {:field-slug "total-gift-aid-awarded"}))
                                  ($ field-cell {:label "Total Gift Aid Redeemed"}
                                     ($ editable-number
                                        {:value (get field-values :total-gift-aid-redeemed)
                                         :placeholder "--"
                                         :saving? saving?
                                         :on-save #(rf/dispatch [::student-events/save-field "total-gift-aid-redeemed" % api-client])})
                                     ($ field-error {:field-slug "total-gift-aid-redeemed"}))
                                  ($ field-cell {:label "Full Ride"}
                                     ($ editable-select
                                        {:value (get field-values :full-ride)
                                         :options (or full-ride-options [])
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :format-fn format-keyword
                                         :on-save #(rf/dispatch [::student-events/save-field "full-ride" % api-client])})
                                     ($ field-error {:field-slug "full-ride"}))
                                  ($ field-cell {:label "Work Study"}
                                     ($ editable-select
                                        {:value (get field-values :work-study)
                                         :options work-study-options
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :as-string? true
                                         :on-save #(rf/dispatch [::student-events/save-field "work-study" % api-client])})
                                     ($ field-error {:field-slug "work-study"}))
                                  ($ field-cell {:label "School Debt"}
                                     ($ editable-select
                                        {:value (get field-values :school-debt)
                                         :options (or school-debt-options [])
                                         :placeholder "Select..."
                                         :saving? saving?
                                         :as-string? true
                                         :on-save #(rf/dispatch [::student-events/save-field "school-debt" % api-client])})
                                     ($ field-error {:field-slug "school-debt"})))))
        render-scholarships (fn []
                              ($ :<>
                                 ($ sub-header {:title "Scholarships"})
                                 ($ field-grid
                                    ($ field-cell {:label "Gates Scholar"}
                                       ($ editable-select
                                          {:value (get field-values :gates-scholar)
                                           :options (or gates-scholar-options [])
                                           :placeholder "Select..."
                                           :saving? saving?
                                           :format-fn format-keyword
                                           :on-save #(rf/dispatch [::student-events/save-field "gates-scholar" % api-client])})
                                       ($ field-error {:field-slug "gates-scholar"}))
                                    ($ field-cell {:label "QuestBridge Scholar"}
                                       ($ editable-select
                                          {:value (get field-values :questbridge-scholar)
                                           :options (or questbridge-scholar-options [])
                                           :placeholder "Select..."
                                           :saving? saving?
                                           :format-fn format-keyword
                                           :on-save #(rf/dispatch [::student-events/save-field "questbridge-scholar" % api-client])})
                                       ($ field-error {:field-slug "questbridge-scholar"}))
                                    ($ field-cell {:label "Bonner Scholar"}
                                       ($ editable-select
                                          {:value (get field-values :bonner-scholar)
                                           :options (or bonner-scholar-options [])
                                           :placeholder "Select..."
                                           :saving? saving?
                                           :format-fn format-keyword
                                           :on-save #(rf/dispatch [::student-events/save-field "bonner-scholar" % api-client])})
                                       ($ field-error {:field-slug "bonner-scholar"}))
                                    ($ field-cell {:label "Torch Scholar"}
                                       ($ editable-select
                                          {:value (get field-values :torch-scholar)
                                           :options (or torch-scholar-options [])
                                           :placeholder "Select..."
                                           :saving? saving?
                                           :format-fn format-keyword
                                           :on-save #(rf/dispatch [::student-events/save-field "torch-scholar" % api-client])})
                                       ($ field-error {:field-slug "torch-scholar"}))
                                    (when fellow-or-alumni?
                                      ($ field-cell {:label "BRYC Scholarships" :span :full}
                                         ($ editable-tag-select
                                            {:value (get field-values :bryc-scholarships)
                                             :options (or bryc-scholarships-options [])
                                             :placeholder "Select scholarships..."
                                             :saving? saving?
                                             :on-save #(rf/dispatch [::student-events/save-field "bryc-scholarships" % api-client])})
                                         ($ field-error {:field-slug "bryc-scholarships"}))))
                                 ($ sub-header {:title "Other Scholarships"})
                                 ($ schema-vector-editor
                                    {:sub-schema other-scholarships-sub-schema
                                     :value (get field-values :other-scholarships)
                                     :saving? saving?
                                     :on-save #(rf/dispatch [::student-events/save-field "other-scholarships" % api-client])})
                                 ($ field-error {:field-slug "other-scholarships"})))]

    (if advisee?
      ($ nested-tabs
         {:tabs [{:id "income" :label "Income" :render render-income}
                 {:id "grants-aid" :label "Grants & Aid" :render render-grants-aid}
                 {:id "scholarships" :label "Scholarships" :render render-scholarships}]})
      ($ :<>
       ;; Income
       ($ sub-header {:title "Income"})
       ($ field-grid
          ($ field-cell {:label "Household Income Range"}
             ($ editable-select
                {:value (get field-values :household-income-range)
                 :options household-income-range-options
                 :placeholder "Select income range"
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "household-income-range" % api-client])})
             ($ field-error {:field-slug "household-income-range"}))
          ($ field-cell {:label "Income Status" :span :full}
             ($ editable-tag-select
                {:value (get field-values :income-status)
                 :options income-status-options
                 :placeholder "Select income status"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "income-status" % api-client])})
             ($ field-error {:field-slug "income-status"})))

       ;; Grants & Aid
       (when (or fellow? advisee? alumni?)
         ($ :<>
            ($ sub-header {:title "Grants & Aid"})
            ($ field-grid
               ($ field-cell {:label "Pell Redeemed"}
                  ($ editable-select
                     {:value (get field-values :pell-redeemed)
                      :options pell-redeemed-options
                      :placeholder "Select..."
                      :saving? saving?
                      :as-string? true
                      :on-save #(rf/dispatch [::student-events/save-field "pell-redeemed" % api-client])})
                  ($ field-error {:field-slug "pell-redeemed"}))
               ($ field-cell {:label "TOPS Redeemed"}
                  ($ editable-select
                     {:value (get field-values :tops-redeemed)
                      :options tops-redeemed-options
                      :placeholder "Select..."
                      :saving? saving?
                      :as-string? true
                      :on-save #(rf/dispatch [::student-events/save-field "tops-redeemed" % api-client])})
                  ($ field-error {:field-slug "tops-redeemed"}))
               ($ field-cell {:label "Total Gift Aid Awarded"}
                  ($ editable-number
                     {:value (get field-values :total-gift-aid-awarded)
                      :placeholder "--"
                      :saving? saving?
                      :on-save #(rf/dispatch [::student-events/save-field "total-gift-aid-awarded" % api-client])})
                  ($ field-error {:field-slug "total-gift-aid-awarded"}))
               ($ field-cell {:label "Total Gift Aid Redeemed"}
                  ($ editable-number
                     {:value (get field-values :total-gift-aid-redeemed)
                      :placeholder "--"
                      :saving? saving?
                      :on-save #(rf/dispatch [::student-events/save-field "total-gift-aid-redeemed" % api-client])})
                  ($ field-error {:field-slug "total-gift-aid-redeemed"}))
               ($ field-cell {:label "Full Ride"}
                  ($ editable-select
                     {:value (get field-values :full-ride)
                      :options (or full-ride-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :format-fn format-keyword
                      :on-save #(rf/dispatch [::student-events/save-field "full-ride" % api-client])})
                  ($ field-error {:field-slug "full-ride"}))
               ($ field-cell {:label "Work Study"}
                  ($ editable-select
                     {:value (get field-values :work-study)
                      :options work-study-options
                      :placeholder "Select..."
                      :saving? saving?
                      :as-string? true
                      :on-save #(rf/dispatch [::student-events/save-field "work-study" % api-client])})
                  ($ field-error {:field-slug "work-study"}))
               ($ field-cell {:label "School Debt"}
                  ($ editable-select
                     {:value (get field-values :school-debt)
                      :options (or school-debt-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :as-string? true
                      :on-save #(rf/dispatch [::student-events/save-field "school-debt" % api-client])})
                  ($ field-error {:field-slug "school-debt"})))))

       ;; Scholarships
       (when (or fellow? advisee? alumni?)
         ($ :<>
            ($ sub-header {:title "Scholarships"})
            ($ field-grid
               ($ field-cell {:label "Gates Scholar"}
                  ($ editable-select
                     {:value (get field-values :gates-scholar)
                      :options (or gates-scholar-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :format-fn format-keyword
                      :on-save #(rf/dispatch [::student-events/save-field "gates-scholar" % api-client])})
                  ($ field-error {:field-slug "gates-scholar"}))
               ($ field-cell {:label "QuestBridge Scholar"}
                  ($ editable-select
                     {:value (get field-values :questbridge-scholar)
                      :options (or questbridge-scholar-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :format-fn format-keyword
                      :on-save #(rf/dispatch [::student-events/save-field "questbridge-scholar" % api-client])})
                  ($ field-error {:field-slug "questbridge-scholar"}))
               ($ field-cell {:label "Bonner Scholar"}
                  ($ editable-select
                     {:value (get field-values :bonner-scholar)
                      :options (or bonner-scholar-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :format-fn format-keyword
                      :on-save #(rf/dispatch [::student-events/save-field "bonner-scholar" % api-client])})
                  ($ field-error {:field-slug "bonner-scholar"}))
               ($ field-cell {:label "Torch Scholar"}
                  ($ editable-select
                     {:value (get field-values :torch-scholar)
                      :options (or torch-scholar-options [])
                      :placeholder "Select..."
                      :saving? saving?
                      :format-fn format-keyword
                      :on-save #(rf/dispatch [::student-events/save-field "torch-scholar" % api-client])})
                  ($ field-error {:field-slug "torch-scholar"}))
               (when fellow-or-alumni?
                 ($ field-cell {:label "BRYC Scholarships" :span :full}
                    ($ editable-tag-select
                       {:value (get field-values :bryc-scholarships)
                        :options (or bryc-scholarships-options [])
                        :placeholder "Select scholarships..."
                        :saving? saving?
                        :on-save #(rf/dispatch [::student-events/save-field "bryc-scholarships" % api-client])})
                    ($ field-error {:field-slug "bryc-scholarships"}))))
            ($ sub-header {:title "Other Scholarships"})
            ($ schema-vector-editor
               {:sub-schema other-scholarships-sub-schema
                :value (get field-values :other-scholarships)
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "other-scholarships" % api-client])})
            ($ field-error {:field-slug "other-scholarships"})))))))
