(ns components.advisor.add-custom-scholarship-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]))

(defui add-custom-scholarship-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)]
    ($ form-modal
       {:open? (use-subscribe [::recommendations-subs/show-add-scholarship-modal?])
        :submitting? (use-subscribe [::recommendations-subs/add-scholarship-submitting?])
        :error (use-subscribe [::recommendations-subs/add-scholarship-error])
        :title "Add Custom Scholarship"
        :description "Manually add a scholarship opportunity"
        :max-width "sm:max-w-[600px] max-h-[90vh] overflow-y-auto"
        :fields [{:key :name :label "Scholarship Name *" :type :text :placeholder "e.g., STEM Excellence Scholarship"}
                 {:key :award :label "Award Amount" :type :text :placeholder "e.g., $5,000"}]
        :initial-state {:name "" :award ""}
        :valid? (fn [d] (seq (:name d)))
        :on-submit #(rf/dispatch [::recommendations-events/submit-add-scholarship % api-client])
        :on-close #(rf/dispatch [::recommendations-events/close-add-scholarship-modal])
        :submit-label "Add Scholarship"
        :submitting-label "Adding..."})))
