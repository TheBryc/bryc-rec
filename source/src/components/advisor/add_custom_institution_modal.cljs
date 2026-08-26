(ns components.advisor.add-custom-institution-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]))

(defui add-custom-institution-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        category (use-subscribe [::recommendations-subs/add-institution-category])]
    ($ form-modal
       {:open? (use-subscribe [::recommendations-subs/show-add-institution-modal?])
        :submitting? (use-subscribe [::recommendations-subs/add-institution-submitting?])
        :error (use-subscribe [::recommendations-subs/add-institution-error])
        :title "Add Custom Institution"
        :title-extra (when category
                       (str " - " (case category :safety "Safety" :target "Target" :reach "Reach" "") " School"))
        :description "Manually add an institution to this category"
        :fields [{:key :name :label "Institution Name *" :type :text :placeholder "e.g., Louisiana State University"}
                 {:key :city :label "City *" :type :text :placeholder "e.g., Baton Rouge"}
                 {:key :state :label "State *" :type :text :placeholder "e.g., LA"}]
        :initial-state {:name "" :city "" :state ""}
        :valid? (fn [d] (and (seq (:name d)) (seq (:city d)) (seq (:state d))))
        :on-submit #(rf/dispatch [::recommendations-events/submit-add-institution % api-client])
        :on-close #(rf/dispatch [::recommendations-events/close-add-institution-modal])
        :submit-label "Add Institution"
        :submitting-label "Adding..."})))
