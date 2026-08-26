(ns store.form-wizard.effects
  "Re-frame effects for form wizard SessionManager calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]
            [components.form-wizard.session :as session]))

(rf/reg-fx
  ::create-session
  (fn [{:keys [email session-manager on-success on-failure]}]
    (when (and email session-manager)
      (go
        (let [response (<! (or (session/create-session session-manager {:email email})
                               (session/create-session session-manager {:email email :form-id "bryc-intake-form"})))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::send-resume-link
  (fn [{:keys [email session-manager on-success on-failure]}]
    (when (and email session-manager)
      (go
        (let [response (<! (or (session/send-resume-link session-manager {:email email})
                               (session/send-resume-link session-manager {:email email :form-id "bryc-intake-form"})))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::validate-token
  (fn [{:keys [token session-manager on-success on-failure]}]
    (when (and token session-manager)
      (go
        (let [response (<! (or (session/validate-session-token session-manager {:token token})
                               (session/validate-session-token session-manager {:token token})))]
          (if (or (anomaly? response) (:error response))
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::save-answers
  (fn [{:keys [session-id email answers session-manager survey-type on-success on-failure]}]
    (if-not (and session-id session-manager)
      (js/console.warn "[save-answers effect] SKIPPING - missing session-id or session-manager")
      (go
        (let [response (<! (session/save-session-state session-manager (cond-> {:session-id session-id
                                                                                 :answers answers
                                                                                 :email email}
                                                                        survey-type (assoc :survey-type survey-type))))]
          (if (and response (anomaly? response))
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

(rf/reg-fx
  ::load-session
  (fn [{:keys [session-id session-manager on-success on-failure]}]
    (when (and session-id session-manager)
      (go
        (let [response (<! (or (session/load-session-state session-manager {:session-id session-id})
                               (session/load-session-state session-manager {:session-id session-id})))]
          (if (and response (anomaly? response))
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success (or response {}))))))))))
