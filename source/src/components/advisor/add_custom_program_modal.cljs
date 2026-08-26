(ns components.advisor.add-custom-program-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]))

(defui add-custom-program-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        institution-context (use-subscribe [::recommendations-subs/add-program-institution-context])]
    ($ form-modal
       {:open? (use-subscribe [::recommendations-subs/show-add-program-modal?])
        :submitting? (use-subscribe [::recommendations-subs/add-program-submitting?])
        :error (use-subscribe [::recommendations-subs/add-program-error])
        :title "Add Custom Program"
        :title-extra (when institution-context (str " - " (:institution-name institution-context)))
        :description "Manually add a program to this institution"
        :max-width "sm:max-w-[600px]"
        :fields [{:key :program-title :label "Program Title *" :type :text :placeholder "e.g., Computer Science - B.S."}
                 {:key :program-url :label "Program URL (optional)" :type :url :placeholder "https://..."}
                 {:key :award-level-name :label "Award Level *" :type :select
                  :options ["Associate's degree" "Bachelor's degree"]}
                 {:key :personalized-overview :label "Overview" :type :textarea :rows 4
                  :placeholder "Brief description of why this program fits the student..."}]
        :initial-state {:program-title "" :program-url "" :award-level-name "" :personalized-overview ""}
        :valid? (fn [d] (and (seq (:program-title d)) (seq (:award-level-name d))))
        :on-submit (fn [d]
                     (let [program-data (merge d (select-keys institution-context
                                                              [:institution-name :city :state]))]
                       (rf/dispatch [::recommendations-events/submit-add-program program-data api-client])))
        :on-close #(rf/dispatch [::recommendations-events/close-add-program-modal])
        :submit-label "Add Program"
        :submitting-label "Adding..."})))
