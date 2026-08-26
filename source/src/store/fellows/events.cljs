(ns store.fellows.events
  "Fellows dashboard re-frame events"
  (:require [re-frame.core :as rf]
            [store.fellows.effects :as fellows-fx]
            [store.auth.events :as auth-events]
            [clojure.string :as str]
            ["sonner" :refer [toast]]))

;; =============================================================================
;; URL Sync
;; =============================================================================

(defn- get-current-filters
  "Extract current filter state from db as a map suitable for URL params."
  [db]
  (let [dashboard (get-in db [:fellows :dashboard])]
    (cond-> {}
      (:school-filter dashboard)
      (assoc :school (:school-filter dashboard))

      (:pm-filter dashboard)
      (assoc :pm (str (:pm-filter dashboard)))

      (:showing-all? dashboard)
      (assoc :show-all "true"))))

(rf/reg-fx
  ::sync-filters-to-url
  (fn [filters]
    (let [params (->> filters
                      (remove (fn [[_ v]] (nil? v)))
                      (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent (str v))))))
          qs (when (seq params) (str "?" (str/join "&" params)))
          path (str "/hub/fellows" qs)]
      (.replaceState js/window.history nil "" path))))

;; Initialize filters from URL query params on mount
(rf/reg-event-fx
  ::initialize-filters-from-url
  (fn [{:keys [db]} [_ query-params api-client]]
    (let [{:keys [school pm show-all]} query-params
          show-all? (= show-all "true")
          new-db (cond-> db
                   school
                   (assoc-in [:fellows :dashboard :school-filter] school)
                   pm
                   (assoc-in [:fellows :dashboard :pm-filter] pm))]
      {:db new-db
       :dispatch [::fetch api-client show-all?]})))

;; =============================================================================
;; Data Fetching
;; =============================================================================

(rf/reg-event-fx
  ::fetch
  (fn [{:keys [db]} [_ api-client show-all?]]
    ;; Always fetch fresh data, but prevent concurrent fetches
    (when-not (get-in db [:fellows :dashboard :loading?])
      {:db (assoc-in db [:fellows :dashboard :loading?] true)
       ::fellows-fx/fetch-fellows {:api-client api-client
                                   :show-all? show-all?
                                   :on-success [::fetch-success]
                                   :on-failure [::fetch-failure]}})))

(rf/reg-event-db
  ::fetch-success
  (fn [db [_ response]]
    (let [students (get response :students [])
          program-managers (get response :program-managers [])
          staff-contact-id (:staff-contact-id response)
          showing-all? (:showing-all? response)
          students-by-id (into {} (map (fn [s] [(:student/id s) s]) students))]
      (-> db
          (assoc-in [:fellows :dashboard :students] students)
          (assoc-in [:fellows :dashboard :program-managers] program-managers)
          (assoc-in [:fellows :dashboard :staff-contact-id] staff-contact-id)
          (assoc-in [:fellows :dashboard :showing-all?] showing-all?)
          (assoc-in [:fellows :students-by-id] students-by-id)
          (assoc-in [:fellows :dashboard :loading?] false)
          (assoc-in [:fellows :dashboard :error] nil)))))

(rf/reg-event-db
  ::fetch-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:fellows :dashboard :loading?] false)
        (assoc-in [:fellows :dashboard :error] error))))

(rf/reg-event-fx
  ::toggle-show-all
  (fn [{:keys [db]} [_ api-client]]
    (let [currently-showing-all? (get-in db [:fellows :dashboard :showing-all?] false)
          new-show-all? (not currently-showing-all?)
          new-db (assoc-in db [:fellows :dashboard :showing-all?] new-show-all?)]
      {:db new-db
       :dispatch [::fetch api-client new-show-all?]
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::logout
  (fn [_ctx [_ api-client navigate-fn]]
    ;; Delegate to centralized auth logout
    {:dispatch [::auth-events/logout api-client navigate-fn]}))

;; =============================================================================
;; Filter Events (all sync to URL)
;; =============================================================================

(rf/reg-event-fx
  ::set-school-filter
  (fn [{:keys [db]} [_ school]]
    (let [new-db (assoc-in db [:fellows :dashboard :school-filter] school)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-school-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:fellows :dashboard :school-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::set-pm-filter
  (fn [{:keys [db]} [_ pm-id]]
    (let [new-db (assoc-in db [:fellows :dashboard :pm-filter] pm-id)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-pm-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:fellows :dashboard :pm-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-all-filters
  (fn [{:keys [db]} _]
    (let [new-db (-> db
                     (assoc-in [:fellows :dashboard :school-filter] nil)
                     (assoc-in [:fellows :dashboard :pm-filter] nil))]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

;; =============================================================================
;; PM Assignment
;; =============================================================================

(rf/reg-event-fx
  ::assign-fellow-pm
  (fn [{:keys [db]} [_ api-client student-id pm-id]]
    {:db (assoc-in db [:fellows :dashboard :assigning?] true)
     ::fellows-fx/assign-fellow-pm {:api-client api-client
                                    :student-id student-id
                                    :pm-id pm-id
                                    :on-success [::assign-success api-client]
                                    :on-failure [::assign-failure]}}))

(rf/reg-event-fx
  ::assign-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:fellows :dashboard :showing-all?] false)]
      {:db (assoc-in db [:fellows :dashboard :assigning?] false)
       ;; Refresh dashboard data to reflect the new assignment
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::assign-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:fellows :dashboard :assigning?] false)
        (assoc-in [:fellows :dashboard :assign-error] error))))

(rf/reg-event-fx
  ::archive-fellow
  (fn [{:keys [db]} [_ api-client student-id]]
    {:db (assoc-in db [:fellows :dashboard :archiving-student-id] student-id)
     ::fellows-fx/archive-fellow {:api-client api-client
                                  :student-id student-id
                                  :reason "Archived from fellows dashboard"
                                  :on-success [::archive-success api-client]
                                  :on-failure [::archive-failure]}}))

(rf/reg-event-fx
  ::archive-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:fellows :dashboard :showing-all?] false)]
      (toast.success "Fellow archived")
      {:db (-> db
               (assoc-in [:fellows :dashboard :archiving-student-id] nil)
               (update-in [:fellows :dashboard :selected-student-ids]
                          (fnil disj #{})
                          (get-in db [:fellows :dashboard :archiving-student-id])))
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::archive-failure
  (fn [db [_ error]]
    (toast.error (or (:anomalies/message error) "Failed to archive fellow"))
    (-> db
        (assoc-in [:fellows :dashboard :archiving-student-id] nil)
        (assoc-in [:fellows :dashboard :archive-error] error))))

;; =============================================================================
;; Selection (bulk operations)
;; =============================================================================

(rf/reg-event-db
  ::toggle-student-selection
  (fn [db [_ student-id]]
    (let [current (get-in db [:fellows :dashboard :selected-student-ids] #{})]
      (assoc-in db [:fellows :dashboard :selected-student-ids]
                (if (contains? current student-id)
                  (disj current student-id)
                  (conj current student-id))))))

(rf/reg-event-db
  ::select-all-students
  (fn [db [_ student-ids]]
    (assoc-in db [:fellows :dashboard :selected-student-ids] (set student-ids))))

(rf/reg-event-db
  ::clear-selection
  (fn [db _]
    (assoc-in db [:fellows :dashboard :selected-student-ids] #{})))

;; Bulk PM assignment events
(rf/reg-event-fx
  ::assign-fellows-pm
  (fn [{:keys [db]} [_ api-client student-ids pm-id]]
    {:db (assoc-in db [:fellows :dashboard :bulk-assigning?] true)
     ::fellows-fx/assign-fellows-pm {:api-client api-client
                                     :student-ids student-ids
                                     :pm-id pm-id
                                     :on-success [::assign-fellows-pm-success api-client]
                                     :on-failure [::assign-fellows-pm-failure]}}))

(rf/reg-event-fx
  ::assign-fellows-pm-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:fellows :dashboard :showing-all?] false)]
      {:db (-> db
               (assoc-in [:fellows :dashboard :bulk-assigning?] false)
               (assoc-in [:fellows :dashboard :selected-student-ids] #{}))
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::assign-fellows-pm-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:fellows :dashboard :bulk-assigning?] false)
        (assoc-in [:fellows :dashboard :bulk-assign-error] error))))

;; =============================================================================
;; Add Fellow Modal
;; =============================================================================

(rf/reg-event-db
  ::open-add-fellow-modal
  (fn [db _]
    (assoc-in db [:fellows :dashboard :add-fellow-modal :show?] true)))

(rf/reg-event-db
  ::close-add-fellow-modal
  (fn [db _]
    (-> db
        (assoc-in [:fellows :dashboard :add-fellow-modal :show?] false)
        (assoc-in [:fellows :dashboard :add-fellow-modal :error] nil))))
