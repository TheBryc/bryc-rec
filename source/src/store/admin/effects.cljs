(ns store.admin.effects
  "Re-frame effects for admin API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-generations
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :admin/recommendation-generations}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::retry-generation
  (fn [{:keys [api-client student-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/start-recommendations-generation
                                                    :student-id student-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))
