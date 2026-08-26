(ns store.advisor.meetings.effects
  "Re-frame effects for advisor meetings API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-meetings-screen
  (fn [{:keys [student-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student-ops/meetings-screen
                                                   :student-id student-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Pass entire response (student, meetings)
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::fetch-meeting-editor-screen
  (fn [{:keys [student-id meeting-id api-client on-success on-failure]}]
    (when (and student-id meeting-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student-ops/meeting-editor-screen
                                                   :student-id student-id
                                                   :meeting-id meeting-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Pass entire response (student, meeting, meetings)
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::start-meeting
  (fn [{:keys [student-id meeting-type api-client on-success on-failure]}]
    (when (and student-id meeting-type api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/start-meeting
                                                     :student-id student-id
                                                     :meeting-type meeting-type}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success (:meeting-id response) student-id meeting-type)))))))))

;; =============================================================================
;; Transcript Analysis Review Effects
;; =============================================================================

(rf/reg-fx
  ::accept-transcript-update
  (fn [{:keys [student-id meeting-id update-id field-slug new-value api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client {:command/name :student-ops/accept-transcript-update
                                                   :student-id student-id
                                                   :meeting-id meeting-id
                                                   :update-id update-id
                                                   :field-slug field-slug
                                                   :new-value new-value}))]
        (if (anomaly? response)
          (when on-failure (rf/dispatch (conj on-failure response)))
          (when on-success (rf/dispatch on-success)))))))

(rf/reg-fx
  ::reject-transcript-update
  (fn [{:keys [student-id meeting-id update-id api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client {:command/name :student-ops/reject-transcript-update
                                                   :student-id student-id
                                                   :meeting-id meeting-id
                                                   :update-id update-id}))]
        (if (anomaly? response)
          (when on-failure (rf/dispatch (conj on-failure response)))
          (when on-success (rf/dispatch on-success)))))))

(rf/reg-fx
  ::accept-transcript-narrative
  (fn [{:keys [student-id meeting-id narrative-id narrative-text api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client {:command/name :student-ops/accept-transcript-narrative
                                                   :student-id student-id
                                                   :meeting-id meeting-id
                                                   :narrative-id narrative-id
                                                   :narrative-text narrative-text}))]
        (if (anomaly? response)
          (when on-failure (rf/dispatch (conj on-failure response)))
          (when on-success (rf/dispatch on-success)))))))

(rf/reg-fx
  ::reject-transcript-narrative
  (fn [{:keys [student-id meeting-id narrative-id api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client {:command/name :student-ops/reject-transcript-narrative
                                                   :student-id student-id
                                                   :meeting-id meeting-id
                                                   :narrative-id narrative-id}))]
        (if (anomaly? response)
          (when on-failure (rf/dispatch (conj on-failure response)))
          (when on-success (rf/dispatch on-success)))))))
