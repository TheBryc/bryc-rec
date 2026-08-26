(ns store.advisor.meeting-editor.effects
  "Re-frame effects for meeting editor"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

;; Save meeting notes API call
(rf/reg-fx
  ::save-notes-api
  (fn [{:keys [meeting-id student-id notes api-client on-success on-failure]}]
    (when (and meeting-id student-id api-client)
      (go
        (let [;; Convert notes to string if it's a map (intake meeting)
              notes-str (if (map? notes)
                          (pr-str notes)
                          notes)
              response (<! (api/command api-client {:command/name :student-ops/save-meeting-notes
                                                    :meeting-id meeting-id
                                                    :student-id student-id
                                                    :notes notes-str}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

;; Complete meeting API call
(rf/reg-fx
  ::complete-meeting-api
  (fn [{:keys [meeting-id student-id api-client on-success on-failure]}]
    (when (and meeting-id student-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/complete-meeting
                                                    :meeting-id meeting-id
                                                    :student-id student-id}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

;; Start debounce timer
(rf/reg-fx
  ::start-debounce-timer
  (fn [{:keys [delay-ms on-trigger]}]
    (let [timer-id (js/setTimeout
                     (fn []
                       (rf/dispatch on-trigger))
                     delay-ms)]
      ;; Store the timer ID so we can cancel it
      (rf/dispatch [:store.advisor.meeting-editor.events/set-timer-id timer-id]))))

;; Cancel debounce timer
(rf/reg-fx
  ::cancel-debounce-timer
  (fn [{:keys [timer-id]}]
    (when timer-id
      (js/clearTimeout timer-id))))
