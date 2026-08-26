(ns store.admin.subs
  "Admin-related re-frame subscriptions"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::generations
  (fn [db _]
    (get-in db [:admin :generations])))

(rf/reg-sub
  ::completed
  (fn [db _]
    (get-in db [:admin :completed])))

(rf/reg-sub
  ::failed
  (fn [db _]
    (get-in db [:admin :failed])))

(rf/reg-sub
  ::stats
  (fn [db _]
    (get-in db [:admin :stats])))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:admin :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:admin :error])))

(rf/reg-sub
  ::retrying
  (fn [db _]
    (get-in db [:admin :retrying] #{})))

(rf/reg-sub
  ::retry-error
  (fn [db _]
    (get-in db [:admin :retry-error])))
