(ns store.advisor.dashboard.events
  "Dashboard-related re-frame events"
  (:require [re-frame.core :as rf]
            [store.advisor.effects :as advisor-fx]
            [store.auth.events :as auth-events]
            [clojure.string :as str]
            ["sonner" :refer [toast]]))

;; =============================================================================
;; URL Sync
;; =============================================================================

(defn- get-current-filters
  "Extract current filter state from db as a map suitable for URL params."
  [db]
  (let [dashboard (get-in db [:advisor :dashboard])]
    (cond-> {}
      (:status-filter dashboard)
      (assoc :status (name (:status-filter dashboard)))

      (:school-filter dashboard)
      (assoc :school (:school-filter dashboard))

      (:advisor-filter dashboard)
      (assoc :advisor (str (:advisor-filter dashboard)))

      (:graduation-year-filter dashboard)
      (assoc :graduation-year (:graduation-year-filter dashboard))

      (:showing-all? dashboard)
      (assoc :show-all "true"))))

(rf/reg-fx
  ::sync-filters-to-url
  (fn [filters]
    (let [params (->> filters
                      (remove (fn [[_ v]] (nil? v)))
                      (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent (str v))))))
          qs (when (seq params) (str "?" (str/join "&" params)))
          path (str "/hub/advising" qs)]
      (.replaceState js/window.history nil "" path))))

;; Initialize filters from URL query params on mount
(rf/reg-event-fx
  ::initialize-filters-from-url
  (fn [{:keys [db]} [_ query-params api-client]]
    (let [{:keys [status school advisor graduation-year show-all]} query-params
          show-all? (= show-all "true")
          new-db (cond-> db
                   status
                   (assoc-in [:advisor :dashboard :status-filter] (keyword status))
                   school
                   (assoc-in [:advisor :dashboard :school-filter] school)
                   advisor
                   (assoc-in [:advisor :dashboard :advisor-filter] advisor)
                   graduation-year
                   (assoc-in [:advisor :dashboard :graduation-year-filter] graduation-year))]
      {:db new-db
       :dispatch [::fetch api-client show-all?]})))

;; =============================================================================
;; Data Fetching
;; =============================================================================

(rf/reg-event-fx
  ::fetch
  (fn [{:keys [db]} [_ api-client show-all?]]
    ;; Always fetch fresh data, but prevent concurrent fetches
    (when-not (get-in db [:advisor :dashboard :loading?])
      {:db (assoc-in db [:advisor :dashboard :loading?] true)
       ::advisor-fx/fetch-dashboard {:api-client api-client
                                     :show-all? show-all?
                                     :on-success [::fetch-success]
                                     :on-failure [::fetch-failure]}})))

(rf/reg-event-db
  ::fetch-success
  (fn [db [_ response]]
    (let [students (get response :students [])
          advisors (get response :advisors [])
          advisor-contact-id (:advisor-contact-id response)
          showing-all? (:showing-all? response)
          students-by-id (into {} (map (fn [s] [(:student/id s) s]) students))]
      (-> db
          (assoc-in [:advisor :dashboard :students] students)
          (assoc-in [:advisor :dashboard :advisors] advisors)
          (assoc-in [:advisor :dashboard :advisor-contact-id] advisor-contact-id)
          (assoc-in [:advisor :dashboard :showing-all?] showing-all?)
          (assoc-in [:advisor :students-by-id] students-by-id)  ; Populate normalized cache
          (assoc-in [:advisor :dashboard :loading?] false)
          (assoc-in [:advisor :dashboard :error] nil)))))

(rf/reg-event-db
  ::fetch-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :dashboard :loading?] false)
        (assoc-in [:advisor :dashboard :error] error))))

(rf/reg-event-fx
  ::toggle-show-all
  (fn [{:keys [db]} [_ api-client]]
    (let [currently-showing-all? (get-in db [:advisor :dashboard :showing-all?] false)
          new-show-all? (not currently-showing-all?)
          new-db (cond-> (assoc-in db [:advisor :dashboard :showing-all?] new-show-all?)
                   (not new-show-all?)
                   (assoc-in [:advisor :dashboard :advisor-filter] nil))]
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

;; Status filter - set directly (no toggle behavior, use dropdown now)
(rf/reg-event-fx
  ::set-status-filter
  (fn [{:keys [db]} [_ status]]
    (let [new-db (assoc-in db [:advisor :dashboard :status-filter] status)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

;; Keep toggle for backwards compat with status cards if needed
(rf/reg-event-fx
  ::toggle-status-filter
  (fn [{:keys [db]} [_ status]]
    (let [current (get-in db [:advisor :dashboard :status-filter])
          new-status (if (= current status) nil status)
          new-db (assoc-in db [:advisor :dashboard :status-filter] new-status)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-status-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:advisor :dashboard :status-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::set-school-filter
  (fn [{:keys [db]} [_ school]]
    (let [new-db (assoc-in db [:advisor :dashboard :school-filter] school)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-school-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:advisor :dashboard :school-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::set-advisor-filter
  (fn [{:keys [db]} [_ advisor-id]]
    (let [new-db (assoc-in db [:advisor :dashboard :advisor-filter] advisor-id)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-advisor-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:advisor :dashboard :advisor-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::set-graduation-year-filter
  (fn [{:keys [db]} [_ graduation-year]]
    (let [new-db (assoc-in db [:advisor :dashboard :graduation-year-filter] graduation-year)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-graduation-year-filter
  (fn [{:keys [db]} _]
    (let [new-db (assoc-in db [:advisor :dashboard :graduation-year-filter] nil)]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

(rf/reg-event-fx
  ::clear-all-filters
  (fn [{:keys [db]} _]
    (let [new-db (-> db
                     (assoc-in [:advisor :dashboard :status-filter] nil)
                     (assoc-in [:advisor :dashboard :school-filter] nil)
                     (assoc-in [:advisor :dashboard :advisor-filter] nil)
                     (assoc-in [:advisor :dashboard :graduation-year-filter] nil))]
      {:db new-db
       ::sync-filters-to-url (get-current-filters new-db)})))

;; =============================================================================
;; Advisor Assignment
;; =============================================================================

(rf/reg-event-fx
  ::assign-student
  (fn [{:keys [db]} [_ api-client student-id advisor-id]]
    {:db (assoc-in db [:advisor :dashboard :assigning?] true)
     ::advisor-fx/assign-student {:api-client api-client
                                  :student-id student-id
                                  :advisor-id advisor-id
                                  :on-success [::assign-success api-client]
                                  :on-failure [::assign-failure]}}))

(rf/reg-event-fx
  ::assign-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:advisor :dashboard :showing-all?] false)]
      {:db (assoc-in db [:advisor :dashboard :assigning?] false)
       ;; Refresh dashboard data to reflect the new assignment
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::assign-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :dashboard :assigning?] false)
        (assoc-in [:advisor :dashboard :assign-error] error))))

(rf/reg-event-fx
  ::archive-student
  (fn [{:keys [db]} [_ api-client student-id]]
    {:db (assoc-in db [:advisor :dashboard :archiving-student-id] student-id)
     ::advisor-fx/archive-student {:api-client api-client
                                   :student-id student-id
                                   :reason "Archived from advising dashboard"
                                   :on-success [::archive-success api-client]
                                   :on-failure [::archive-failure]}}))

(rf/reg-event-fx
  ::archive-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:advisor :dashboard :showing-all?] false)]
      (toast.success "Advisee archived")
      {:db (-> db
               (assoc-in [:advisor :dashboard :archiving-student-id] nil)
               (update-in [:advisor :dashboard :selected-student-ids]
                          (fnil disj #{})
                          (get-in db [:advisor :dashboard :archiving-student-id])))
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::archive-failure
  (fn [db [_ error]]
    (toast.error (or (:anomalies/message error) "Failed to archive advisee"))
    (-> db
        (assoc-in [:advisor :dashboard :archiving-student-id] nil)
        (assoc-in [:advisor :dashboard :archive-error] error))))

;; =============================================================================
;; Selection (bulk operations)
;; =============================================================================

(rf/reg-event-db
  ::toggle-student-selection
  (fn [db [_ student-id]]
    (let [current (get-in db [:advisor :dashboard :selected-student-ids] #{})]
      (assoc-in db [:advisor :dashboard :selected-student-ids]
                (if (contains? current student-id)
                  (disj current student-id)
                  (conj current student-id))))))

(rf/reg-event-db
  ::select-all-students
  (fn [db [_ student-ids]]
    (assoc-in db [:advisor :dashboard :selected-student-ids] (set student-ids))))

(rf/reg-event-db
  ::clear-selection
  (fn [db _]
    (assoc-in db [:advisor :dashboard :selected-student-ids] #{})))

;; Bulk advisor assignment events
(rf/reg-event-fx
  ::assign-students
  (fn [{:keys [db]} [_ api-client student-ids advisor-id]]
    {:db (assoc-in db [:advisor :dashboard :bulk-assigning?] true)
     ::advisor-fx/assign-students {:api-client api-client
                                   :student-ids student-ids
                                   :advisor-id advisor-id
                                   :on-success [::assign-students-success api-client]
                                   :on-failure [::assign-students-failure]}}))

(rf/reg-event-fx
  ::assign-students-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:advisor :dashboard :showing-all?] false)]
      {:db (-> db
               (assoc-in [:advisor :dashboard :bulk-assigning?] false)
               (assoc-in [:advisor :dashboard :selected-student-ids] #{}))
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::assign-students-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :dashboard :bulk-assigning?] false)
        (assoc-in [:advisor :dashboard :bulk-assign-error] error))))

;; =============================================================================
;; Add Student Modal
;; =============================================================================

(rf/reg-event-db
  ::open-add-student-modal
  (fn [db _]
    (assoc-in db [:advisor :dashboard :add-student-modal :show?] true)))

(rf/reg-event-db
  ::close-add-student-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :dashboard :add-student-modal :show?] false)
        (assoc-in [:advisor :dashboard :add-student-modal :error] nil))))

(rf/reg-event-fx
  ::submit-add-student
  (fn [{:keys [db]} [_ form-data api-client]]
    {:db (assoc-in db [:advisor :dashboard :add-student-modal :submitting?] true)
     ::advisor-fx/add-student {:api-client api-client
                                :first-name (:first-name form-data)
                                :last-name (:last-name form-data)
                                :email (:email form-data)
                                :on-success [::add-student-success api-client]
                                :on-failure [::add-student-failure]}}))

(rf/reg-event-fx
  ::add-student-success
  (fn [{:keys [db]} [_ api-client _response]]
    (let [show-all? (get-in db [:advisor :dashboard :showing-all?] false)]
      {:db (-> db
               (assoc-in [:advisor :dashboard :add-student-modal :show?] false)
               (assoc-in [:advisor :dashboard :add-student-modal :submitting?] false)
               (assoc-in [:advisor :dashboard :add-student-modal :error] nil))
       :dispatch [::fetch api-client show-all?]})))

(rf/reg-event-db
  ::add-student-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :dashboard :add-student-modal :submitting?] false)
        (assoc-in [:advisor :dashboard :add-student-modal :error] error))))
