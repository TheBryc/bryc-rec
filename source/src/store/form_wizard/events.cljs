(ns store.form-wizard.events
  "Form wizard events"
  (:require [re-frame.core :as rf]
            [store.form-wizard.effects :as wizard-fx]
            [components.form-wizard.validation :as validation]))

;; Initialize wizard state
(rf/reg-event-db
  ::initialize
  (fn [db [_ {:keys [survey-type]}]]
    (assoc db :form-wizard {:screen :email-capture
                            :email nil
                            :session-id nil
                            :session-token nil
                            :session-error nil
                            :resume-link-sent? false
                            :is-processing? false
                            :validation-error nil
                            :current-group 0
                            :answers {}
                            :validation-errors {}
                            :saved-fields #{}
                            :show-validation? false
                            :field-progress 0.0
                            :submission-state nil
                            :survey-type survey-type})))

;; Set survey type (intake vs senior)
(rf/reg-event-db
  ::set-survey-type
  (fn [db [_ survey-type]]
    (assoc-in db [:form-wizard :survey-type] survey-type)))

;; Reset wizard to initial state
(rf/reg-event-db
  ::reset
  (fn [db _]
    (assoc db :form-wizard {:screen :email-capture
                            :email nil
                            :session-id nil
                            :session-token nil
                            :session-error nil
                            :resume-link-sent? false
                            :is-processing? false
                            :validation-error nil
                            :current-group 0
                            :answers {}
                            :validation-errors {}
                            :saved-fields #{}
                            :show-validation? false
                            :field-progress 0.0
                            :submission-state nil})))

;; Screen state
(rf/reg-event-db
  ::set-screen
  (fn [db [_ screen]]
    (assoc-in db [:form-wizard :screen] screen)))

;; Email capture
(rf/reg-event-db
  ::set-email
  (fn [db [_ email]]
    (assoc-in db [:form-wizard :email] email)))

(rf/reg-event-db
  ::set-validation-error
  (fn [db [_ error]]
    (assoc-in db [:form-wizard :validation-error] error)))

(rf/reg-event-db
  ::set-processing
  (fn [db [_ processing?]]
    (assoc-in db [:form-wizard :is-processing?] processing?)))

(rf/reg-event-db
  ::set-session-error
  (fn [db [_ error]]
    (assoc-in db [:form-wizard :session-error] error)))

(rf/reg-event-db
  ::set-resume-link-sent
  (fn [db [_ sent?]]
    (assoc-in db [:form-wizard :resume-link-sent?] sent?)))

;; Start form - create session
(rf/reg-event-fx
  ::start-form
  (fn [{:keys [db]} [_ session-manager]]
    (let [email (get-in db [:form-wizard :email])
          validation-error (validation/validate-email email)]
      (if validation-error
        {:db (assoc-in db [:form-wizard :validation-error] validation-error)}
        {:db (-> db
                 (assoc-in [:form-wizard :is-processing?] true)
                 (assoc-in [:form-wizard :validation-error] nil))
         ::wizard-fx/create-session {:email email
                                     :session-manager session-manager
                                     :on-success [::start-form-success]
                                     :on-failure [::start-form-failure]}}))))

(rf/reg-event-db
  ::start-form-success
  (fn [db [_ response]]
    (js/console.log "[start-form-success] response:" (pr-str response)
                    "session-id:" (pr-str (:session-id response)))
    (-> db
        (assoc-in [:form-wizard :session-id] (:session-id response))
        (assoc-in [:form-wizard :session-token] (:token response))
        (assoc-in [:form-wizard :screen] :form)
        (assoc-in [:form-wizard :is-processing?] false)
        (assoc-in [:form-wizard :session-error] nil))))

(rf/reg-event-db
  ::start-form-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:form-wizard :is-processing?] false)
        (assoc-in [:form-wizard :session-error] {:error :failed
                                                 :message "Failed to create session. Please try again."}))))

;; Resume form - send link
(rf/reg-event-fx
  ::resume-form
  (fn [{:keys [db]} [_ session-manager]]
    (let [email (get-in db [:form-wizard :email])
          validation-error (validation/validate-email email)]
      (if validation-error
        {:db (assoc-in db [:form-wizard :validation-error] validation-error)}
        {:db (-> db
                 (assoc-in [:form-wizard :is-processing?] true)
                 (assoc-in [:form-wizard :validation-error] nil)
                 (assoc-in [:form-wizard :resume-link-sent?] false))
         ::wizard-fx/send-resume-link {:email email
                                       :session-manager session-manager
                                       :on-success [::resume-form-success]
                                       :on-failure [::resume-form-failure]}}))))

(rf/reg-event-db
  ::resume-form-success
  (fn [db [_ _response]]
    (-> db
        (assoc-in [:form-wizard :is-processing?] false)
        (assoc-in [:form-wizard :resume-link-sent?] true)
        (assoc-in [:form-wizard :session-error] nil))))

(rf/reg-event-db
  ::resume-form-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:form-wizard :is-processing?] false)
        (assoc-in [:form-wizard :session-error] {:error :failed
                                                 :message "Failed to send resume link. Please try again."}))))

;; Validate token (from URL or resume link)
(rf/reg-event-fx
  ::validate-token
  (fn [{:keys [db]} [_ token session-manager config field-options]]
    {:db (assoc-in db [:form-wizard :is-processing?] true)
     ::wizard-fx/validate-token {:token token
                                 :session-manager session-manager
                                 :on-success [::validate-token-success config field-options]
                                 :on-failure [::validate-token-failure]}}))

(rf/reg-event-db
  ::validate-token-success
  (fn [db [_ config field-options response]]
    (let [backend-answers (or (:answers response) {})
          ;; Transform backend answers to UI format
          ;; Note: field-options is passed for potential future either-or field handling
          ui-answers (validation/transform-answers-from-backend backend-answers {:config config})]
      (-> db
          (assoc-in [:form-wizard :session-id] (:session-id response))
          (assoc-in [:form-wizard :email] (:email response))
          (assoc-in [:form-wizard :session-token] nil)
          (assoc-in [:form-wizard :answers] ui-answers)
          (assoc-in [:form-wizard :screen] :form)
          (assoc-in [:form-wizard :is-processing?] false)
          (assoc-in [:form-wizard :session-error] nil)))))

(rf/reg-event-db
  ::validate-token-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:form-wizard :is-processing?] false)
        (assoc-in [:form-wizard :session-error] error)
        (assoc-in [:form-wizard :screen] :email-capture))))

;; Update answer
(rf/reg-event-db
  ::update-answer
  (fn [db [_ field-key value]]
    (-> db
        (assoc-in [:form-wizard :answers field-key] value)
        (update-in [:form-wizard :saved-fields] disj field-key))))

;; Save field (auto-save)
(rf/reg-event-fx
  ::save-field
  (fn [{:keys [db]} [_ field-key value session-manager]]
    (let [session-id (get-in db [:form-wizard :session-id])
          email (get-in db [:form-wizard :email])
          survey-type (get-in db [:form-wizard :survey-type])
          ;; Only send the single changed field, not all answers
          backend-answers (validation/transform-answers-to-backend {field-key value})]
      {:db (assoc-in db [:form-wizard :answers field-key] value)
       ::wizard-fx/save-answers {:session-id session-id
                                 :email email
                                 :answers backend-answers
                                 :survey-type survey-type
                                 :session-manager session-manager
                                 :on-success [::save-field-success field-key]
                                 :on-failure [::save-field-failure field-key]}})))

(rf/reg-event-db
  ::save-field-success
  (fn [db [_ field-key]]
    (update-in db [:form-wizard :saved-fields] conj field-key)))

(rf/reg-event-db
  ::save-field-failure
  (fn [db [_ field-key _error]]
    ;; Silently fail auto-save, keep field as unsaved
    (update-in db [:form-wizard :saved-fields] disj field-key)))

;; Clear saved indicator
(rf/reg-event-db
  ::clear-saved-field
  (fn [db [_ field-key]]
    (update-in db [:form-wizard :saved-fields] disj field-key)))

;; Navigation
(rf/reg-event-db
  ::set-current-group
  (fn [db [_ group-index]]
    (assoc-in db [:form-wizard :current-group] group-index)))

(rf/reg-event-db
  ::next-group
  (fn [db _]
    (update-in db [:form-wizard :current-group] inc)))

(rf/reg-event-db
  ::prev-group
  (fn [db _]
    (update-in db [:form-wizard :current-group] dec)))

(rf/reg-event-db
  ::set-show-validation
  (fn [db [_ show?]]
    (assoc-in db [:form-wizard :show-validation?] show?)))

;; Validation errors
(rf/reg-event-db
  ::set-validation-errors
  (fn [db [_ errors]]
    (assoc-in db [:form-wizard :validation-errors] errors)))

;; Field progress
(rf/reg-event-db
  ::set-field-progress
  (fn [db [_ progress]]
    (assoc-in db [:form-wizard :field-progress] progress)))

;; Submission
(rf/reg-event-db
  ::set-submission-state
  (fn [db [_ state]]
    (assoc-in db [:form-wizard :submission-state] state)))

(rf/reg-event-fx
  ::submit-form
  (fn [{:keys [db]} [_ on-submit-callback]]
    (let [answers (get-in db [:form-wizard :answers])]
      {:db (assoc-in db [:form-wizard :submission-state] :submitting)
       :dispatch-later [{:ms 100
                        :dispatch [::submit-form-execute on-submit-callback answers]}]})))

(rf/reg-event-fx
  ::submit-form-execute
  (fn [{:keys [db]} [_ on-submit-callback answers]]
    ;; Call the on-submit callback from props
    (when on-submit-callback
      (on-submit-callback answers))
    {:db (-> db
             (assoc-in [:form-wizard :submission-state] :success)
             (assoc-in [:form-wizard :screen] :success))}))
