(ns components.advisor.add-student-modal
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.context.interface :as context]
            [components.shared.form-modal :refer [form-modal]]
            [store.advisor.dashboard.events :as dashboard-events]
            [store.advisor.dashboard.subs :as dashboard-subs]))

(defui add-student-modal []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)]
    ($ form-modal
       {:open? (use-subscribe [::dashboard-subs/show-add-student-modal?])
        :submitting? (use-subscribe [::dashboard-subs/add-student-submitting?])
        :error (use-subscribe [::dashboard-subs/add-student-error])
        :title "Add Student"
        :description "Manually add a new student to the system"
        :fields [{:key :first-name :label "First Name *" :type :text :placeholder "e.g., John"}
                 {:key :last-name :label "Last Name *" :type :text :placeholder "e.g., Smith"}
                 {:key :email :label "Email *" :type :email :placeholder "e.g., john.smith@example.com"}]
        :initial-state {:first-name "" :last-name "" :email ""}
        :valid? (fn [d] (and (seq (:first-name d)) (seq (:last-name d))
                             (seq (:email d)) (str/includes? (:email d) "@")))
        :on-submit #(rf/dispatch [::dashboard-events/submit-add-student % api-client])
        :on-close #(rf/dispatch [::dashboard-events/close-add-student-modal])
        :submit-label "Add Student"
        :submitting-label "Adding..."})))
