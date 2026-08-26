(ns components.advisor.add-custom-short-term-program-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]))

(defui add-custom-short-term-program-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)]
    ($ form-modal
       {:open? (use-subscribe [::recommendations-subs/show-add-short-term-program-modal?])
        :submitting? (use-subscribe [::recommendations-subs/add-short-term-program-submitting?])
        :error (use-subscribe [::recommendations-subs/add-short-term-program-error])
        :title "Add Custom Short-Term Program"
        :description "Manually add a certificate or short-term training program"
        :max-width "sm:max-w-[600px] max-h-[90vh] overflow-y-auto"
        :fields [{:key :program-title :label "Program Title *" :type :text :placeholder "e.g., Licensed Practical Nurse Training"}
                 {:key :institution-name :label "Institution Name *" :type :text :placeholder "e.g., Baton Rouge Community College"}
                 {:key :award-level-name :label "Award Level *" :type :select
                  :options ["Certificate < 1 year" "Certificate 1-2 years"]}
                 {:key :city :label "City *" :type :text :placeholder "e.g., Baton Rouge"}
                 {:key :state :label "State *" :type :text :placeholder "e.g., LA"}]
        :initial-state {:program-title "" :institution-name "" :award-level-name "" :city "" :state ""}
        :valid? (fn [d] (and (seq (:program-title d)) (seq (:institution-name d))
                             (seq (:award-level-name d)) (seq (:city d)) (seq (:state d))))
        :on-submit #(rf/dispatch [::recommendations-events/submit-add-short-term-program % api-client])
        :on-close #(rf/dispatch [::recommendations-events/close-add-short-term-program-modal])
        :submit-label "Add Program"
        :submitting-label "Adding..."})))
