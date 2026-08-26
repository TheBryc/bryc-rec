(ns components.senior-survey.core
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [components.form-wizard.interface :as form-wizard]
            [components.form-wizard.core :as wizard-core]
            [components.senior-forms.interface :as senior-forms]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :as anom :refer [anomaly?]]))

(defn build-field-options
  "Build field-options map from CRM contact type field definitions.
   Returns map of {:field-slug {:options [...] :sub-fields {...}}}"
  [field-definitions]
  (reduce
    (fn [acc field-def]
      (let [slug (:slug field-def)
            options (:options field-def)
            sub-schema (:sub-schema field-def)
            item-schema-fields (get-in sub-schema [:item-schema :fields])]
        (cond-> acc
          options
          (assoc-in [slug :options] (vec options))
          item-schema-fields
          (assoc slug (reduce
                        (fn [m sub-field]
                          (let [sub-key (name (:key sub-field))
                                sub-opts (:options sub-field)]
                            (if sub-opts
                              (assoc-in m [:sub-fields sub-key] (vec sub-opts))
                              m)))
                        (get acc slug {})
                        item-schema-fields)))))
    {}
    field-definitions))

(defui main [{:keys [current-match]}]
  (let [[submission-state set-submission-state] (use-state nil)
        [success-message set-success-message] (use-state nil)
        [field-options set-field-options!] (use-state nil)
        [loading-options? set-loading-options!] (use-state true)
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Create session manager with :senior survey type
        session-manager (when api-client
                          (wizard-core/create-remote-session-manager api-client :senior))

        ;; Fetch CRM student contact type metadata on mount
        _ (use-effect
            (fn []
              (when api-client
                (go
                  (let [student-response (<! (api/query api-client {:query/name :crm/get-contact-type
                                                                     :type-slug "student"}))]
                    (when-not (anomaly? student-response)
                      (let [student-fields (:field-definitions (:contact-type student-response))
                            student-opts (build-field-options student-fields)]
                        (set-field-options! student-opts)))
                    (set-loading-options! false))))
              js/undefined)
            [api-client])

        handle-submit (fn [backend-answers session-data reset-form-fn]
                        (set-submission-state :submitting)
                        (go
                          (let [{:keys [email session-id]} session-data
                                response (<! (api/command api-client {:command/name :intake/submit-survey
                                                                      :email email
                                                                      :survey-id session-id
                                                                      :survey-type :senior}))]
                            (if (anomaly? response)
                              (do
                                (set-submission-state :error)
                                (set-success-message (or (::anom/message response) "Failed to submit survey. Please try again."))
                                (js/setTimeout
                                  (fn []
                                    (set-submission-state nil)
                                    (set-success-message nil))
                                  5000))
                              (do
                                (set-submission-state :success)
                                (set-success-message "Thank you for completing the Senior Year Intake Survey! Your BRYC counselor will follow up with you soon.")
                                (js/setTimeout
                                  (fn []
                                    (reset-form-fn)
                                    (set-submission-state nil)
                                    (set-success-message nil))
                                  3000))))))]

    ($ :<>
       (when submission-state
         ($ :div {:class "fixed inset-0 bg-black/50 flex items-center justify-center z-50"}
            ($ :div {:class "bg-white rounded-lg p-8 max-w-md mx-4 shadow-xl"}
               (cond
                 (= submission-state :submitting)
                 ($ :div {:class "text-center"}
                    ($ :div {:class "animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"})
                    ($ :p {:class "text-gray-600"} "Submitting your survey..."))

                 (= submission-state :success)
                 ($ :div {:class "text-center"}
                    ($ :div {:class "text-green-500 text-5xl mb-4"} "✓")
                    ($ :h3 {:class "text-xl font-semibold mb-2"} "Success!")
                    ($ :p {:class "text-gray-600"} success-message))

                 (= submission-state :error)
                 ($ :div {:class "text-center"}
                    ($ :div {:class "text-red-500 text-5xl mb-4"} "✗")
                    ($ :h3 {:class "text-xl font-semibold mb-2"} "Submission Error")
                    ($ :p {:class "text-gray-600"} (or success-message "There was an error submitting your survey. Please try again.")))))))

       (if loading-options?
         ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 flex items-center justify-center"}
            ($ :div {:class "text-center"}
               ($ :div {:class "animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"})
               ($ :p {:class "text-gray-600"} "Loading form...")))
         ($ form-wizard/wizard
            {:config senior-forms/config
             :field-options (or field-options {})
             :current-match current-match
             :on-submit handle-submit
             :submission-state submission-state
             :session-manager (or session-manager wizard-core/default-local-session-manager)
             :header-config {:icon "🎓"
                             :title "BRYC Senior Year Intake Survey"
                             :subtitle "Class of 2027"}})))))
