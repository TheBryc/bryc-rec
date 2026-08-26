(ns components.intake.core
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [components.form-wizard.interface :as form-wizard]
            [components.form-wizard.core :as wizard-core]
            [components.intake-forms.interface :as intake-forms]
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
            ;; Handle sub-schema for :data type fields (like activities)
            sub-schema (:sub-schema field-def)
            item-schema-fields (get-in sub-schema [:item-schema :fields])]
        (cond-> acc
          ;; Add top-level options
          options
          (assoc-in [slug :options] (vec options))
          ;; Add sub-field options from item-schema fields
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

(defn merge-field-options
  "Merge field options from multiple contact types"
  [& field-options-maps]
  (apply merge-with
         (fn [a b]
           (merge-with (fn [x y]
                         (if (and (vector? x) (vector? y))
                           (vec (distinct (concat x y)))
                           y))
                       a b))
         field-options-maps))

(defn build-relationship-property-options
  "Build field-options map from relationship type allowed-properties.
   Returns map keyed by 'rel-type-slug/property-slug' for lookup."
  [relationship-types]
  (reduce
    (fn [acc rel-type]
      (let [type-slug (:slug rel-type)
            allowed-props (:allowed-properties rel-type)]
        (reduce
          (fn [m prop]
            (let [prop-slug (:slug prop)
                  options (:options prop)
                  lookup-key (str type-slug "/" prop-slug)]
              (if options
                (assoc-in m [lookup-key :options] (vec options))
                m)))
          acc
          allowed-props)))
    {}
    relationship-types))

(defui main [{:keys [current-match]}]
  (let [[submission-state set-submission-state] (use-state nil) ; nil, :submitting, :success, :error
        [success-message set-success-message] (use-state nil)
        [field-options set-field-options!] (use-state nil)
        [loading-options? set-loading-options!] (use-state true)
        ctx (context/use-context)
        session-manager (:session/manager ctx)
        api-client (:api/client ctx)

        ;; Fetch CRM contact type metadata on mount
        _ (use-effect
            (fn []
              (when api-client
                (go
                  (let [;; Fetch student contact type
                        student-response (<! (api/query api-client {:query/name :crm/get-contact-type
                                                                     :type-slug "student"}))
                        ;; Fetch guardian contact type
                        guardian-response (<! (api/query api-client {:query/name :crm/get-contact-type
                                                                      :type-slug "guardian"}))
                        ;; Fetch guardian-of relationship type for relationship property options
                        guardian-rel-response (<! (api/query api-client {:query/name :crm/get-relationship-type
                                                                          :type-slug "guardian-of"}))]
                    (when-not (or (anomaly? student-response) (anomaly? guardian-response))
                      (let [student-fields (:field-definitions (:contact-type student-response))
                            guardian-fields (:field-definitions (:contact-type guardian-response))
                            student-opts (build-field-options student-fields)
                            guardian-opts (build-field-options guardian-fields)
                            ;; Build options from relationship type allowed-properties
                            rel-prop-opts (when-not (anomaly? guardian-rel-response)
                                            (build-relationship-property-options
                                              [(:relationship-type guardian-rel-response)]))
                            merged-opts (merge-field-options student-opts guardian-opts rel-prop-opts)]
                        (set-field-options! merged-opts)))
                    (set-loading-options! false))))
              js/undefined)
            [api-client])

        handle-submit (fn [backend-answers session-data reset-form-fn]
                        (js/console.log "Submitting survey with session data:" (pr-str session-data))
                        (set-submission-state :submitting)
                        (go
                          (let [{:keys [email session-id]} session-data
                                response (<! (api/command api-client {:command/name :intake/submit-survey
                                                                      :email email
                                                                      :survey-id session-id}))]
                            (if (anomaly? response)
                              (do
                                (js/console.error "Failed to submit survey:" (::anom/message response))
                                (set-submission-state :error)
                                (set-success-message (or (::anom/message response) "Failed to submit survey. Please try again."))
                                (js/setTimeout 
                                  (fn [] 
                                    (set-submission-state nil)
                                    (set-success-message nil))
                                  5000))
                              (do
                                (js/console.log "Survey submitted successfully!" (pr-str response))
                                (set-submission-state :success)
                                (set-success-message "Thank you for completing the survey! We'll be in touch soon.")
                                ;; Reset form after a delay
                                (js/setTimeout 
                                  (fn []
                                    (reset-form-fn)
                                    (set-submission-state nil)
                                    (set-success-message nil))
                                  3000))))))]
    
    ;; Show success/error overlay if submission state is set
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
       
       ; Form wizard with enhanced styling - header is now handled by the wizard itself
       (if loading-options?
         ;; Show loading while fetching CRM field options
         ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 flex items-center justify-center"}
            ($ :div {:class "text-center"}
               ($ :div {:class "animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"})
               ($ :p {:class "text-gray-600"} "Loading form...")))
         ;; Render wizard once field options are loaded
         ($ form-wizard/wizard
            {:config intake-forms/config
             :field-options (or field-options {})
             :current-match current-match
             :on-submit handle-submit
             :submission-state submission-state
             ;; Use RemoteSessionManager if API client is available, otherwise use local
             :session-manager (or session-manager wizard-core/default-local-session-manager)
             ;; Header configuration for the welcome screen
             :header-config {:icon "🎓"
                             :title "Welcome to Your BRYC Journey!"
                             :subtitle "We're excited to help you plan your path after high school. This quick survey helps us understand your goals and create a personalized plan just for you."}})))))