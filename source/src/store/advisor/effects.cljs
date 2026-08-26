(ns store.advisor.effects
  "Re-frame effects for advisor API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::fetch-dashboard
  (fn [{:keys [api-client show-all? on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client
                                      (cond-> {:query/name :advisor/advising-screen}
                                        (some? show-all?) (assoc :show-all? show-all?))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Pass full response with :students, :advisors, :advisor-contact-id, :showing-all?
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::assign-student
  (fn [{:keys [api-client student-id advisor-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :advisor/assign-student
                                         :student-id student-id
                                         :advisor-id advisor-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::assign-students
  (fn [{:keys [api-client student-ids advisor-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :advisor/assign-students
                                         :student-ids (vec student-ids)
                                         :advisor-id advisor-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::archive-student
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

(rf/reg-fx
  ::add-student
  (fn [{:keys [api-client first-name last-name email on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :crm/create-contact
                                         :type-slug "student"
                                         :field-values {:first-name first-name
                                                        :last-name last-name
                                                        :email email}
                                         :attribution {:source :manual_entry
                                                       :recorded-at (.toISOString (js/Date.))}}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure (or (:message response) "Failed to add student")))
            (rf/dispatch (conj on-success response))))))))
