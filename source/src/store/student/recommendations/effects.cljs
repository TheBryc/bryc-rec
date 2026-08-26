(ns store.student.recommendations.effects
  "Re-frame effects for student recommendations API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::load-recommendations
  (fn [{:keys [token api-client on-success on-failure]}]
    (when (and token api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student/recommendations
                                                   :token token}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))
