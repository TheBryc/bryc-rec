(ns store.fellows.effects
  "Re-frame effects for fellows API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-fellows
  (fn [{:keys [api-client show-all? on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client
                                      (cond-> {:query/name :fellows/fellows-screen}
                                        (some? show-all?) (assoc :show-all? show-all?))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Pass full response with :students, :program-managers, :staff-contact-id, :showing-all?
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::assign-fellow-pm
  (fn [{:keys [api-client student-id pm-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :fellows/assign-pm
                                         :student-id student-id
                                         :pm-id pm-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::assign-fellows-pm
  (fn [{:keys [api-client student-ids pm-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :fellows/assign-pms
                                         :student-ids (vec student-ids)
                                         :pm-id pm-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::archive-fellow
  (fn [{:keys [api-client student-id reason on-success on-failure]}]
    (when (and api-client student-id)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :crm/archive-contact
                                                 :contact-id student-id}
                                          reason (assoc :reason reason))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))
