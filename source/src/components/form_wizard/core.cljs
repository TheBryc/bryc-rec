(ns components.form-wizard.core
  "Main wizard orchestrator component"
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/card" :as card]
            [components.form-wizard.session :as session]
            [components.form-wizard.fields :as fields]
            [components.form-wizard.navigation :as nav]
            [components.form-wizard.ui :as ui]
            [components.form-wizard.validation :as validation]
            [store.form-wizard.events :as wizard-events]
            [store.form-wizard.subs :as wizard-subs]))

;; Re-export commonly needed functions and values
(def default-local-session-manager session/default-local-session-manager)
(def create-remote-session-manager session/create-remote-session-manager)

(defn populate-field-options
  "Populate :options on a field from field-options map.
   Handles :crm-field, :crm-sub-field, :relationship-property, and inline :options."
  [field field-options parent-crm-field]
  (cond
    ;; Already has inline options
    (:options field) field

    ;; Has :relationship-property (for fields that get options from relationship type properties)
    (:relationship-property field)
    (let [rel-type-slug (:relationship-type-slug field)
          prop-slug (name (:relationship-property field))
          lookup-key (str rel-type-slug "/" prop-slug)
          options (get-in field-options [lookup-key :options])]
      (assoc field :options (vec (or options []))))

    ;; Has :crm-sub-field (for dynamic-list sub-fields)
    (:crm-sub-field field)
    (let [sub-field-key (name (:crm-sub-field field))  ; Convert keyword to string
          parent-slug (when parent-crm-field (name parent-crm-field))  ; Convert parent too
          options (fields/get-field-options field-options parent-slug sub-field-key)]
      (assoc field :options (vec options)))

    ;; Has :crm-field (top-level field)
    (:crm-field field)
    (let [crm-slug (name (:crm-field field))  ; Convert keyword to string for lookup
          options (fields/get-field-options field-options crm-slug nil)]
      (assoc field :options (vec options)))

    :else field))

(defn process-groups-with-answers
  "Process config groups with current answers to determine visibility.
   field-options is a map of {:field-slug {:options [...] :sub-fields {...}}}."
  [config field-options answers]
  (->> (:groups config)
       (map (fn [group]
              (assoc group :fields
                     (map (fn [field]
                            (let [parent-crm-field (:crm-field field)
                                  field-with-options (populate-field-options field field-options nil)
                                  ;; Also populate options for sub-fields in dynamic-list
                                  field-with-sub-options
                                  (if (:sub-fields field-with-options)
                                    (update field-with-options :sub-fields
                                            (fn [sub-fields]
                                              (mapv #(populate-field-options % field-options parent-crm-field)
                                                    sub-fields)))
                                    field-with-options)]
                              field-with-sub-options))
                          (:fields group)))))
       (filter #(validation/group-visible? % answers))
       vec))

;; Helper functions for session initialization
(defn get-token-from-url []
  (let [url-params (js/URLSearchParams. js/window.location.search)]
    (.get url-params "token")))

(defn clear-token-from-url []
  (.replaceState js/window.history nil "" js/window.location.pathname))

;; Main wizard component
(defui wizard [{:keys [config field-options on-submit _current-match session-manager header-config]}]
  ;; Set survey-type from config on mount (always dispatch, even nil, to clear stale state)
  (use-effect
    (fn []
      (rf/dispatch [::wizard-events/set-survey-type (:survey-type config)])
      js/undefined)
    [config])
  (let [;; Subscribe to all wizard state
        screen (use-subscribe [::wizard-subs/screen])
        email (use-subscribe [::wizard-subs/email])
        session-id (use-subscribe [::wizard-subs/session-id])
        session-error (use-subscribe [::wizard-subs/session-error])
        resume-link-sent? (use-subscribe [::wizard-subs/resume-link-sent?])
        is-processing? (use-subscribe [::wizard-subs/is-processing?])
        validation-error (use-subscribe [::wizard-subs/validation-error])
        current-group (use-subscribe [::wizard-subs/current-group])
        answers (use-subscribe [::wizard-subs/answers])
        validation-errors (use-subscribe [::wizard-subs/validation-errors])
        saved-fields (use-subscribe [::wizard-subs/saved-fields])
        show-validation? (use-subscribe [::wizard-subs/show-validation?])

        ;; Process visible groups
        groups (process-groups-with-answers config field-options answers)
        total-groups (count groups)
        safe-current-group (min current-group (dec (max total-groups 1)))
        current-group-data (when (and (> total-groups 0) (< safe-current-group total-groups))
                             (nth groups safe-current-group))

        ;; Calculate progress
        field-progress (validation/calculate-progress groups answers)

        ;; Session data reconstruction (for on-submit callback compatibility)
        session-data (when session-id {:session-id session-id :email email})

        ;; Event handlers
        handle-email-change (fn [new-email]
                              (rf/dispatch [::wizard-events/set-email new-email])
                              (rf/dispatch [::wizard-events/set-validation-error nil])
                              (rf/dispatch [::wizard-events/set-session-error nil])
                              (rf/dispatch [::wizard-events/set-resume-link-sent false]))

        handle-start-form (fn []
                            (rf/dispatch [::wizard-events/start-form session-manager]))

        handle-resume-form (fn []
                             (rf/dispatch [::wizard-events/resume-form session-manager]))

        handle-answer-change (fn [field-key value]
                               (rf/dispatch [::wizard-events/update-answer field-key value]))

        handle-answer-change-and-save (fn [field-key value]
                                         (rf/dispatch [::wizard-events/update-answer field-key value])
                                         (rf/dispatch [::wizard-events/save-field field-key value session-manager]))

        handle-field-blur (fn [field-key value]
                            ;; Validate field on blur
                            (let [field (validation/find-field-in-groups groups field-key)
                                  field-visible? (validation/field-visible? field answers)
                                  input-type (:input-type field)
                                  error (validation/validate-field-format field-key value field-visible? input-type field)
                                  new-errors (if error
                                              (assoc validation-errors field-key error)
                                              (dissoc validation-errors field-key))]
                              (rf/dispatch [::wizard-events/set-validation-errors new-errors]))
                            ;; Auto-save
                            (rf/dispatch [::wizard-events/save-field field-key value session-manager]))

        ;; Validation helpers
        get-visible-fields-in-group (fn [group-data answers]
                                      (when group-data
                                        (filter #(validation/field-visible? % answers)
                                               (:fields group-data))))

        validate-fields (fn [fields answers]
                          (for [field fields
                                :let [field-key (:key field)
                                      value (get answers field-key)
                                      ;; conditional-required: :required-when (else static :optional)
                                      field-optional? (not (validation/field-required? field answers))
                                      field-visible? true
                                      field-type (:type field)
                                      min-items (:min-items field)
                                      input-type (:input-type field)
                                      error (validation/validate-field-complete field-key value field-optional? field-visible? field-type min-items input-type field)]]
                            [field-key error]))

        get-validation-errors (fn [validation-results]
                                (into {} (filter second validation-results)))

        validate-current-group (fn []
                                 (let [current-fields (get-visible-fields-in-group current-group-data answers)
                                       validation-results (when current-fields
                                                            (validate-fields current-fields answers))
                                       errors (get-validation-errors (or validation-results []))]
                                   (rf/dispatch [::wizard-events/set-validation-errors (merge validation-errors errors)])
                                   (empty? errors)))

        validate-all-fields (fn []
                              (let [all-visible-fields (filter #(validation/field-visible? % answers)
                                                               (mapcat :fields groups))
                                    validation-results (validate-fields all-visible-fields answers)
                                    errors (get-validation-errors validation-results)]
                                (rf/dispatch [::wizard-events/set-validation-errors errors])
                                (empty? errors)))

        ;; Navigation handlers
        calculate-next-step (fn [current-step answers]
                              (let [new-groups (process-groups-with-answers config field-options answers)
                                    new-total-groups (count new-groups)
                                    next-group-index (inc current-step)]
                                (min next-group-index (dec (max new-total-groups 1)))))

        handle-next (fn []
                      (if (validate-current-group)
                        (do
                          (rf/dispatch [::wizard-events/set-show-validation false])
                          (rf/dispatch [::wizard-events/set-current-group (calculate-next-step safe-current-group answers)])
                          (js/window.scrollTo 0 0))
                        (rf/dispatch [::wizard-events/set-show-validation true])))

        handle-prev (fn []
                      (rf/dispatch [::wizard-events/set-show-validation false])
                      (rf/dispatch [::wizard-events/prev-group])
                      (js/window.scrollTo 0 0))

        handle-submit (fn []
                        (if (validate-all-fields)
                          (do
                            (rf/dispatch [::wizard-events/set-show-validation false])
                            (let [backend-answers (validation/transform-answers-to-backend answers)]
                              (when on-submit
                                (on-submit backend-answers session-data
                                          ;; reset-form callback
                                          (fn [] (rf/dispatch [::wizard-events/reset]))))))
                          (rf/dispatch [::wizard-events/set-show-validation true])))]

    ;; Initialize session on component mount
    (use-effect (fn []
                  (when session-manager
                    (when-let [token (get-token-from-url)]
                      (rf/dispatch [::wizard-events/validate-token token session-manager config field-options])
                      (clear-token-from-url)))
                  js/undefined)
                [session-manager config field-options])

    ;; Update field progress when answers change
    (use-effect (fn []
                  (rf/dispatch [::wizard-events/set-field-progress field-progress])
                  js/undefined)
                [field-progress])

    ;; Render wizard UI
    (case screen
      :email-capture
      ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 p-4 md:p-8 animate-in fade-in duration-500"}
         ($ :div {:class "max-w-4xl mx-auto"}
            ($ ui/welcome-header {:header-config header-config})
            ($ :div {:class "flex items-center justify-center min-h-[40vh]"}
               ($ ui/email-capture-screen
                  {:email email
                   :on-email-change handle-email-change
                   :on-start-form handle-start-form
                   :on-resume-form handle-resume-form
                   :validation-error validation-error
                   :session-error session-error
                   :resume-link-sent resume-link-sent?
                   :is-processing is-processing?}))))

      :form
      ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 p-4 md:p-8 animate-in fade-in duration-500"}
         ($ :div {:class "max-w-4xl mx-auto"}
            ($ card/Card {:class "max-w-3xl mx-auto bg-white/80 backdrop-blur-sm shadow-xl border-0 overflow-hidden mb-20"}
               ($ nav/form-header {:current-group-data current-group-data
                                   :field-progress field-progress
                                   :current-step safe-current-group
                                   :total-steps total-groups
                                   :on-prev handle-prev
                                   :on-next handle-next
                                   :on-submit handle-submit})

               ($ card/CardContent
                  ($ ui/form-group
                     {:group current-group-data
                      :answers answers
                      :validation-errors validation-errors
                      :saved-fields saved-fields
                      :on-answer-change handle-answer-change
                      :on-answer-change-and-save handle-answer-change-and-save
                      :on-field-blur handle-field-blur
                      :session-id session-id})
                  ($ nav/wizard-navigation
                     {:current-group safe-current-group
                      :total-groups total-groups
                      :on-prev handle-prev
                      :on-next handle-next
                      :on-submit handle-submit
                      :show-validation-message show-validation?})))

            ($ nav/progress-bar {:current-step safe-current-group
                                 :total-steps total-groups
                                 :field-progress field-progress})))

      ;; Default fallback
      ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50 p-4 md:p-8"}
         ($ :div {:class "max-w-4xl mx-auto text-center"}
            ($ :p "Loading..."))))))
