(ns components.advisor.add-custom-apprenticeship-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]))

(defui add-custom-apprenticeship-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)]
    ($ form-modal
       {:open? (use-subscribe [::recommendations-subs/show-add-apprenticeship-modal?])
        :submitting? (use-subscribe [::recommendations-subs/add-apprenticeship-submitting?])
        :error (use-subscribe [::recommendations-subs/add-apprenticeship-error])
        :title "Add Custom Apprenticeship"
        :description "Manually add an apprenticeship opportunity"
        :max-width "sm:max-w-[600px] max-h-[90vh] overflow-y-auto"
        :fields [{:key :apprenticeship-title :label "Apprenticeship Title *" :type :text :placeholder "e.g., Electrical Apprenticeship"}
                 {:key :company-name :label "Company Name *" :type :text :placeholder "e.g., ABC Electric Co."}
                 {:key :city :label "City *" :type :text :placeholder "e.g., Baton Rouge"}
                 {:key :state :label "State *" :type :text :placeholder "e.g., LA"}]
        :initial-state {:apprenticeship-title "" :company-name "" :city "" :state ""}
        :valid? (fn [d] (and (seq (:apprenticeship-title d)) (seq (:company-name d))
                             (seq (:city d)) (seq (:state d))))
        :on-submit #(rf/dispatch [::recommendations-events/submit-add-apprenticeship % api-client])
        :on-close #(rf/dispatch [::recommendations-events/close-add-apprenticeship-modal])
        :submit-label "Add Apprenticeship"
        :submitting-label "Adding..."})))
