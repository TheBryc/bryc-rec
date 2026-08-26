(ns store.crm.guardian.core
  "Re-frame events / subs / one effect for the guardian profile page.
   Single namespace by design — the guardian page is small and creating a
   parallel three-file structure for the few events here would be overkill.
   Reuses :crm/set-contact-field via the existing student-fx effect for saves
   (the effect is contact-id agnostic; only the page-side bookkeeping differs)."
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]
            [store.crm.student.effects :as student-fx]))

;; =============================================================================
;; One-shot effect — fat detail query
;; =============================================================================

(rf/reg-fx
  ::fetch-guardian-detail-screen
  (fn [{:keys [guardian-id api-client on-success on-failure]}]
    (when (and guardian-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :crm/guardian-detail-screen
                                                   :guardian-id guardian-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

;; =============================================================================
;; Events
;; =============================================================================

(rf/reg-event-db
  ::set-guardian-id
  (fn [db [_ guardian-id]]
    (assoc-in db [:advisor :guardian-detail :current-guardian-id] guardian-id)))

(rf/reg-event-fx
  ::load-screen
  (fn [{:keys [db]} [_ guardian-id api-client]]
    (let [already-loaded? (and (= guardian-id (get-in db [:advisor :guardian-detail :current-guardian-id]))
                               (get-in db [:advisor :guardians-by-id guardian-id]))]
      {:db (-> db
               (assoc-in [:advisor :guardian-detail :current-guardian-id] guardian-id)
               (assoc-in [:advisor :guardian-detail :loading?] (not already-loaded?)))
       ::fetch-guardian-detail-screen {:guardian-id guardian-id
                                        :api-client api-client
                                        :on-success [::load-screen-success]
                                        :on-failure [::load-screen-failure]}})))

(rf/reg-event-db
  ::load-screen-success
  (fn [db [_ {:keys [guardian linked-students contact-type field-options]}]]
    (let [expected-id (get-in db [:advisor :guardian-detail :current-guardian-id])
          actual-id (:id guardian)]
      (if (= expected-id actual-id)
        (-> db
            (assoc-in [:advisor :guardians-by-id actual-id] guardian)
            (assoc-in [:advisor :guardian-detail :linked-students] linked-students)
            (assoc-in [:advisor :guardian-detail :contact-type] contact-type)
            (assoc-in [:advisor :guardian-detail :field-options] field-options)
            (assoc-in [:advisor :guardian-detail :loading?] false)
            (assoc-in [:advisor :guardian-detail :error] nil))
        db))))

(rf/reg-event-db
  ::load-screen-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :guardian-detail :loading?] false)
        (assoc-in [:advisor :guardian-detail :error] error))))

(rf/reg-event-fx
  ::refresh-screen
  (fn [{:keys [db]} [_ guardian-id api-client]]
    {::fetch-guardian-detail-screen {:guardian-id guardian-id
                                      :api-client api-client
                                      :on-success [::load-screen-success]
                                      :on-failure [::load-screen-failure]}}))

;; =============================================================================
;; Field saving — delegates to student-fx/set-contact-field (contact-id agnostic)
;; =============================================================================

(rf/reg-event-fx
  ::save-field
  (fn [{:keys [db]} [_ field-slug value api-client]]
    (let [guardian-id (get-in db [:advisor :guardian-detail :current-guardian-id])]
      {:db (assoc-in db [:advisor :guardian-detail :field-saving field-slug] true)
       ::student-fx/set-contact-field {:contact-id guardian-id
                                        :field-slug field-slug
                                        :value value
                                        :api-client api-client
                                        :on-success [::save-field-success field-slug]
                                        :on-failure [::save-field-failure field-slug]}})))

(rf/reg-event-fx
  ::save-field-success
  (fn [{:keys [db]} [_ field-slug guardian-id _new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :guardian-detail :field-saving field-slug] false)
             (update-in [:advisor :guardian-detail :field-errors] dissoc field-slug))
     :dispatch [::refresh-screen guardian-id api-client]}))

(rf/reg-event-db
  ::save-field-failure
  (fn [db [_ field-slug error]]
    (let [error-msg (or (:anomalies/message error)
                        (:cognitect.anomalies/message error)
                        (:message error)
                        (str "Failed to save " field-slug))]
      (-> db
          (assoc-in [:advisor :guardian-detail :field-saving field-slug] false)
          (assoc-in [:advisor :guardian-detail :field-errors field-slug] error-msg)))))

;; =============================================================================
;; Subs
;; =============================================================================

(rf/reg-sub
  ::current-guardian-id
  (fn [db _]
    (get-in db [:advisor :guardian-detail :current-guardian-id])))

(rf/reg-sub
  ::guardian
  (fn [db _]
    (let [id (get-in db [:advisor :guardian-detail :current-guardian-id])]
      (get-in db [:advisor :guardians-by-id id]))))

(rf/reg-sub
  ::linked-students
  (fn [db _]
    (get-in db [:advisor :guardian-detail :linked-students] [])))

(rf/reg-sub
  ::field-options
  (fn [db _]
    (get-in db [:advisor :guardian-detail :field-options] {})))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :guardian-detail :loading?])))

(rf/reg-sub
  ::field-saving?
  (fn [db [_ field-slug]]
    (boolean (get-in db [:advisor :guardian-detail :field-saving field-slug]))))

(rf/reg-sub
  ::field-error
  (fn [db [_ field-slug]]
    (get-in db [:advisor :guardian-detail :field-errors field-slug])))
