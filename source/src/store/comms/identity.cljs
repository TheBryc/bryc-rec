(ns store.comms.identity
  "Current logged-in staff identity for the comms surfaces. Used to gate
   advisor-only affordances (merge tags). Loaded lazily via :advisor/profile
   and cached in app-db. Safe default: anything other than a confirmed
   advisor role is treated as non-advisor."
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-profile
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :advisor/profile}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-event-fx
  ::ensure-profile
  ;; Idempotent: fetch the staff profile once, then cache. No-op while
  ;; loading or once loaded.
  (fn [{:keys [db]} [_ api-client]]
    (let [status (get-in db [:comms :identity :status])]
      (if (#{:loading :loaded} status)
        {}
        {:db (assoc-in db [:comms :identity :status] :loading)
         ::fetch-profile {:api-client api-client
                          :on-success [::load-profile-success]
                          :on-failure [::load-profile-failure]}}))))

(rf/reg-event-db
  ::load-profile-success
  (fn [db [_ result]]
    (-> db
        (assoc-in [:comms :identity :status] :loaded)
        (assoc-in [:comms :identity :contact-id] (:contact-id result))
        (assoc-in [:comms :identity :staff] (:staff result)))))

(rf/reg-event-db
  ::load-profile-failure
  (fn [db [_ _err]]
    ;; Safe default: treat as non-advisor on failure.
    (assoc-in db [:comms :identity :status] :error)))

(rf/reg-sub
  ::current-staff
  (fn [db _] (get-in db [:comms :identity :staff])))

(rf/reg-sub
  ::advisor?
  :<- [::current-staff]
  (fn [staff _] (= :advisor (:role staff))))
