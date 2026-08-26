(ns store.crm.student.effects
  "Re-frame effects for student detail API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]
            [tick.core :as t]))

(rf/reg-fx
  ::fetch-student-detail-screen
  (fn [{:keys [student-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student-ops/student-detail-screen
                                                   :student-id student-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Pass the entire response (student, contact-type, field-options)
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::set-contact-field
  (fn [{:keys [contact-id field-slug value api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :crm/set-contact-field
                                                     :contact-id contact-id
                                                     :field-slug field-slug
                                                     :value value}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success contact-id value api-client)))))))))

(rf/reg-fx
  ::set-encrypted-contact-field
  (fn [{:keys [contact-id field-slug value api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/set-encrypted-contact-field
                                                     :contact-id contact-id
                                                     :field-slug field-slug
                                                     :value value}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success contact-id value api-client)))))))))

(rf/reg-fx
  ::archive-contact
  (fn [{:keys [contact-id reason api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :crm/archive-contact
                                                 :contact-id contact-id}
                                          reason (assoc :reason reason))))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success contact-id api-client)))))))))

;; Guardian-specific effects (create-guardian-contact, create-guardian-relationship,
;; update-guardian-relationship) were removed when inline guardian editing
;; moved to the dedicated guardian profile page. The generic
;; :crm/create-relationship and :crm/update-relationship commands are used
;; through the typeahead-driven add-guardian-sheet effect ::create-relationship
;; (defined further below).

(rf/reg-fx
  ::regenerate-recommendations
  (fn [{:keys [student-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        ;; Use the async start command - returns immediately, generation happens in background
        (let [response (<! (api/command api-client {:command/name :student-ops/start-recommendations-generation
                                                     :student-id student-id}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

;; =============================================================================
;; Communication Effects
;; =============================================================================

(rf/reg-fx
  ::fetch-communications
  (fn [{:keys [contact-id api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :crm/get-contact-communications
                                                   :contact-id contact-id}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::log-communication
  (fn [{:keys [contact-id communication-type direction subject content
               duration-minutes outcome next-steps call-link occurred-at
               api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [is-call? (#{:voice-call :video-call} communication-type)
              ;; Convert datetime-local value (YYYY-MM-DDTHH:mm) to offset datetime
              ;; matching the backend's (str (time/now)) format
              occurred-at-iso (when (seq occurred-at)
                                (let [ldt (t/date-time occurred-at)
                                      local-tz (t/zone (.. js/Intl DateTimeFormat (resolvedOptions) -timeZone))
                                      zdt (t/in ldt local-tz)]
                                  (str (t/offset-date-time zdt))))
              command (cond-> {:command/name :crm/log-communication
                               :contact-id contact-id
                               :communication-type communication-type
                               :direction direction}
                        occurred-at-iso (assoc :occurred-at occurred-at-iso)
                        ;; Only include sender/recipient for message-based types
                        (not is-call?) (assoc :sender (if (= direction :outbound)
                                                        {} {:contact-id contact-id})
                                              :recipient (if (= direction :outbound)
                                                           {:contact-id contact-id} {}))
                        (seq subject) (assoc :subject subject)
                        (seq content) (assoc :content content)
                        duration-minutes (assoc :duration-minutes duration-minutes)
                        (seq outcome) (assoc :outcome outcome)
                        (seq next-steps) (assoc :next-steps next-steps)
                        (seq call-link) (assoc :call-link call-link))
              response (<! (api/command api-client command))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

(rf/reg-fx
  ::update-communication
  (fn [{:keys [contact-id communication-id changes api-client on-success on-failure]}]
    (when (and contact-id communication-id api-client)
      (go
        (let [response (<! (api/command api-client
                             {:command/name :crm/update-communication
                              :contact-id contact-id
                              :communication-id communication-id
                              :changes changes}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

(rf/reg-fx
  ::delete-communication
  (fn [{:keys [contact-id communication-id api-client on-success on-failure]}]
    (when (and contact-id communication-id api-client)
      (go
        (let [response (<! (api/command api-client
                             {:command/name :crm/delete-communication
                              :contact-id contact-id
                              :communication-id communication-id}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

;; =============================================================================
;; Transcript Analysis Effects
;; =============================================================================

(rf/reg-fx
  ::apply-transcript-analysis
  (fn [{:keys [student-id meeting-id approved-updates rejected-updates api-client on-success on-failure]}]
    (when (and student-id meeting-id api-client)
      (go
        (let [response (<! (api/command api-client {:command/name :student-ops/apply-transcript-analysis
                                                     :student-id student-id
                                                     :meeting-id meeting-id
                                                     :approved-updates approved-updates
                                                     :rejected-updates rejected-updates}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

;; =============================================================================
;; Generic CRM contact + relationship effects (for guardians, siblings,
;; and other entity-model UIs).
;; =============================================================================

(rf/reg-fx
  ::create-contact
  (fn [{:keys [type-slug field-values api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :crm/create-contact
                                                     :type-slug type-slug
                                                     :field-values field-values}))]
          (if (anomaly? response)
            (when on-failure (rf/dispatch (conj on-failure response)))
            (when on-success (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::create-relationship
  (fn [{:keys [type-slug source-contact-id target-contact-id properties is-primary
               api-client on-success on-failure]}]
    (when (and type-slug source-contact-id target-contact-id api-client)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :crm/create-relationship
                                                 :type-slug type-slug
                                                 :source-contact-id source-contact-id
                                                 :target-contact-id target-contact-id
                                                 :properties (or properties {})}
                                          (some? is-primary) (assoc :is-primary is-primary))))]
          (if (anomaly? response)
            (when on-failure (rf/dispatch (conj on-failure response)))
            (when on-success (rf/dispatch (conj on-success response)))))))))
