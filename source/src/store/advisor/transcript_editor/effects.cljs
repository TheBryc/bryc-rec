(ns store.advisor.transcript-editor.effects
  "Re-frame effects for transcript editor API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-transcript-screen
  (fn [{:keys [student-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student-ops/transcript-screen
                                                   :student-id student-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::save-transcript
  (fn [{:keys [student-id transcript-id transcript api-client on-success on-failure]}]
    (when (and student-id transcript-id transcript api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/edit-grade-transcript
                                                     :student-id student-id
                                                     :transcript-id transcript-id
                                                     :transcript transcript}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::delete-transcript
  (fn [{:keys [student-id transcript-id api-client on-success on-failure]}]
    (when (and student-id transcript-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/delete-grade-transcript
                                                     :student-id student-id
                                                     :transcript-id transcript-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::get-transcript-pdf-url
  (fn [{:keys [file-id api-client on-success on-failure]}]
    (when (and file-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/presign-grade-transcript-get-url
                                                     :file-id file-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success (:url response)))))))))

;; Timer effect for auto-clearing success indicator
(rf/reg-fx
  ::schedule-clear-success
  (fn [{:keys [delay-ms on-trigger]}]
    (js/setTimeout
      (fn []
        (rf/dispatch on-trigger))
      delay-ms)))
