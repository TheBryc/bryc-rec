(ns store.student.recommendations.subs
  "Re-frame subscriptions for student view of recommendations"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:student :recommendations :loading?] false)))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:student :recommendations :error])))

(rf/reg-sub
  ::pool
  (fn [db _]
    (get-in db [:student :recommendations :pool])))

(rf/reg-sub
  ::resolved
  (fn [db _]
    (get-in db [:student :recommendations :resolved])))

(rf/reg-sub
  ::advisor
  (fn [db _]
    (get-in db [:student :recommendations :advisor])))

(rf/reg-sub
  ::advisor-message
  (fn [db _]
    (get-in db [:student :recommendations :advisor-message])))

(rf/reg-sub
  ::pool-id
  (fn [db _]
    (get-in db [:student :recommendations :pool-id])))
