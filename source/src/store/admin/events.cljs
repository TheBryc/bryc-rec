(ns store.admin.events
  "Admin-related re-frame events"
  (:require [re-frame.core :as rf]
            [store.admin.effects :as admin-fx]
            ["sonner" :refer [toast]]))

(rf/reg-event-fx
  ::load-generations
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:admin :loading?] true)
     ::admin-fx/fetch-generations {:api-client api-client
                                   :on-success [::load-generations-success]
                                   :on-failure [::load-generations-failure]}}))

(rf/reg-event-db
  ::load-generations-success
  (fn [db [_ response]]
    (-> db
        (assoc-in [:admin :generations] (:in-progress response))
        (assoc-in [:admin :completed] (:completed response))
        (assoc-in [:admin :failed] (:failed response))
        (assoc-in [:admin :stats] (:stats response))
        (assoc-in [:admin :loading?] false)
        (assoc-in [:admin :error] nil))))

(rf/reg-event-db
  ::load-generations-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:admin :loading?] false)
        (assoc-in [:admin :error] error))))

(rf/reg-event-fx
  ::retry-generation
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (update-in db [:admin :retrying] (fnil conj #{}) (str student-id))
     ::admin-fx/retry-generation {:api-client api-client
                                  :student-id student-id
                                  :on-success [::retry-success api-client]
                                  :on-failure [::retry-failure student-id]}}))

(rf/reg-event-fx
  ::retry-success
  (fn [{:keys [db]} [_ api-client _response]]
    {:db (assoc-in db [:admin :retrying] #{})
     :dispatch [::load-generations api-client]}))

(rf/reg-event-fx
  ::retry-failure
  (fn [{:keys [db]} [_ student-id error]]
    (toast.error (or (:anomalies/message error) "Retry failed"))
    {:db (-> db
             (update-in [:admin :retrying] disj (str student-id)))}))
