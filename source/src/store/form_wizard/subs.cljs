(ns store.form-wizard.subs
  "Form wizard subscriptions"
  (:require [re-frame.core :as rf]))

;; Screen state
(rf/reg-sub
  ::screen
  (fn [db _]
    (get-in db [:form-wizard :screen])))

;; Email capture
(rf/reg-sub
  ::email
  (fn [db _]
    (get-in db [:form-wizard :email])))

(rf/reg-sub
  ::validation-error
  (fn [db _]
    (get-in db [:form-wizard :validation-error])))

(rf/reg-sub
  ::is-processing?
  (fn [db _]
    (get-in db [:form-wizard :is-processing?])))

(rf/reg-sub
  ::session-error
  (fn [db _]
    (get-in db [:form-wizard :session-error])))

(rf/reg-sub
  ::resume-link-sent?
  (fn [db _]
    (get-in db [:form-wizard :resume-link-sent?])))

;; Session
(rf/reg-sub
  ::session-id
  (fn [db _]
    (get-in db [:form-wizard :session-id])))

(rf/reg-sub
  ::session-token
  (fn [db _]
    (get-in db [:form-wizard :session-token])))

;; Form state
(rf/reg-sub
  ::current-group
  (fn [db _]
    (get-in db [:form-wizard :current-group])))

(rf/reg-sub
  ::answers
  (fn [db _]
    (get-in db [:form-wizard :answers])))

(rf/reg-sub
  ::validation-errors
  (fn [db _]
    (get-in db [:form-wizard :validation-errors])))

(rf/reg-sub
  ::saved-fields
  (fn [db _]
    (get-in db [:form-wizard :saved-fields])))

(rf/reg-sub
  ::show-validation?
  (fn [db _]
    (get-in db [:form-wizard :show-validation?])))

(rf/reg-sub
  ::field-progress
  (fn [db _]
    (get-in db [:form-wizard :field-progress])))

;; Submission
(rf/reg-sub
  ::submission-state
  (fn [db _]
    (get-in db [:form-wizard :submission-state])))
