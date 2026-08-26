(ns store.advisor.meeting-editor.events
  "Meeting editor events for both intake and freeform meetings"
  (:require [re-frame.core :as rf]
            [store.advisor.meeting-editor.effects :as editor-fx]
            [store.crm.student.effects :as student-fx]))

;; Initialize editor with meeting data
(rf/reg-event-db
  ::init-editor
  (fn [db [_ meeting-id initial-notes api-client]]
    (-> db
        (assoc-in [:advisor :meeting-editor :current-meeting-id] meeting-id)
        (assoc-in [:advisor :meeting-editor :notes] initial-notes)
        (assoc-in [:advisor :meeting-editor :notes-loaded?] true)
        (assoc-in [:advisor :meeting-editor :saving?] false)
        (assoc-in [:advisor :meeting-editor :save-pending?] false)
        (assoc-in [:advisor :meeting-editor :last-saved] nil)
        (assoc-in [:advisor :meeting-editor :error] nil)
        (assoc-in [:advisor :meeting-editor :api-client] api-client))))

;; Update notes and trigger debounced save
(rf/reg-event-fx
  ::update-notes
  (fn [{:keys [db]} [_ new-notes]]
    (let [old-timer-id (get-in db [:advisor :meeting-editor :debounce-timer-id])]
      {:db (-> db
               (assoc-in [:advisor :meeting-editor :notes] new-notes)
               (assoc-in [:advisor :meeting-editor :save-pending?] true))
       ;; Cancel old timer and start new one
       ::editor-fx/cancel-debounce-timer {:timer-id old-timer-id}
       ::editor-fx/start-debounce-timer {:delay-ms 3000
                                         :on-trigger [::trigger-save]}})))

;; Triggered by debounce timer
(rf/reg-event-fx
  ::trigger-save
  (fn [{:keys [db]} _]
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])
          meeting (get-in db [:advisor :meetings-by-id meeting-id])
          student-id (:meeting/student-id meeting)
          notes (get-in db [:advisor :meeting-editor :notes])
          api-client (get-in db [:advisor :meeting-editor :api-client])
          save-pending? (get-in db [:advisor :meeting-editor :save-pending?])]
      (if (and meeting-id student-id save-pending?)
        {:db (-> db
                 (assoc-in [:advisor :meeting-editor :saving?] true)
                 (assoc-in [:advisor :meeting-editor :error] nil))
         ::editor-fx/save-notes-api {:meeting-id meeting-id
                                     :student-id student-id
                                     :notes notes
                                     :api-client api-client
                                     :on-success [::save-success]
                                     :on-failure [::save-failure]}}
        ;; No changes to save
        {:db db}))))

;; Manual save (for explicit save button if needed)
(rf/reg-event-fx
  ::save-notes
  (fn [{:keys [db]} _]
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])
          meeting (get-in db [:advisor :meetings-by-id meeting-id])
          student-id (:meeting/student-id meeting)
          notes (get-in db [:advisor :meeting-editor :notes])
          api-client (get-in db [:advisor :meeting-editor :api-client])]
      {:db (-> db
               (assoc-in [:advisor :meeting-editor :saving?] true)
               (assoc-in [:advisor :meeting-editor :error] nil))
       ::editor-fx/save-notes-api {:meeting-id meeting-id
                                   :student-id student-id
                                   :notes notes
                                   :api-client api-client
                                   :on-success [::save-success]
                                   :on-failure [::save-failure]}})))

(rf/reg-event-db
  ::save-success
  (fn [db _]
    (-> db
        (assoc-in [:advisor :meeting-editor :saving?] false)
        (assoc-in [:advisor :meeting-editor :save-pending?] false)
        (assoc-in [:advisor :meeting-editor :last-saved] (.now js/Date))
        (assoc-in [:advisor :meeting-editor :error] nil))))

(rf/reg-event-db
  ::save-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :meeting-editor :saving?] false)
        (assoc-in [:advisor :meeting-editor :error] error))))

;; Complete meeting workflow: save first, then complete
(rf/reg-event-fx
  ::complete-meeting
  (fn [{:keys [db]} _]
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])
          meeting (get-in db [:advisor :meetings-by-id meeting-id])
          student-id (:meeting/student-id meeting)
          notes (get-in db [:advisor :meeting-editor :notes])
          api-client (get-in db [:advisor :meeting-editor :api-client])
          save-pending? (get-in db [:advisor :meeting-editor :save-pending?])]
      (if save-pending?
        ;; Save first, then complete
        {:db (-> db
                 (assoc-in [:advisor :meeting-editor :saving?] true)
                 (assoc-in [:advisor :meeting-editor :error] nil))
         ::editor-fx/save-notes-api {:meeting-id meeting-id
                                     :student-id student-id
                                     :notes notes
                                     :api-client api-client
                                     :on-success [::save-then-complete]
                                     :on-failure [::save-failure]}}
        ;; No pending changes, complete directly
        {:db (-> db
                 (assoc-in [:advisor :meeting-editor :saving?] true)
                 (assoc-in [:advisor :meeting-editor :error] nil))
         ::editor-fx/complete-meeting-api {:meeting-id meeting-id
                                           :student-id student-id
                                           :api-client api-client
                                           :on-success [::complete-success]
                                           :on-failure [::complete-failure]}}))))

(rf/reg-event-fx
  ::save-then-complete
  (fn [{:keys [db]} _]
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])
          meeting (get-in db [:advisor :meetings-by-id meeting-id])
          student-id (:meeting/student-id meeting)
          api-client (get-in db [:advisor :meeting-editor :api-client])]
      {:db (-> db
               (assoc-in [:advisor :meeting-editor :save-pending?] false)
               (assoc-in [:advisor :meeting-editor :last-saved] (.now js/Date)))
       ::editor-fx/complete-meeting-api {:meeting-id meeting-id
                                         :student-id student-id
                                         :api-client api-client
                                         :on-success [::complete-success]
                                         :on-failure [::complete-failure]}})))

(rf/reg-event-fx
  ::complete-success
  (fn [{:keys [db]} [_ on-complete-callback]]
    ;; Update the meeting in the cache to mark as completed
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])
          completed-at (.toISOString (js/Date.))]
      {:db (-> db
               (assoc-in [:advisor :meeting-editor :saving?] false)
               ;; Update in meetings-by-id cache
               (assoc-in [:advisor :meetings-by-id meeting-id :meeting/status] :completed)
               (assoc-in [:advisor :meetings-by-id meeting-id :meeting/completed-at] completed-at)
               ;; Also update current-meeting (used by meetings-subs)
               (assoc-in [:advisor :meetings :current-meeting :meeting/status] :completed)
               (assoc-in [:advisor :meetings :current-meeting :meeting/completed-at] completed-at))
       ;; Invoke callback if provided (for navigation)
       :dispatch-later [{:ms 100 :dispatch [::invoke-callback on-complete-callback]}]})))

(rf/reg-event-fx
  ::invoke-callback
  (fn [_ [_ callback]]
    (when callback
      (callback))
    {}))

(rf/reg-event-db
  ::complete-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :meeting-editor :saving?] false)
        (assoc-in [:advisor :meeting-editor :error] error))))

;; Cleanup editor state on unmount
(rf/reg-event-fx
  ::cleanup
  (fn [{:keys [db]} _]
    (let [timer-id (get-in db [:advisor :meeting-editor :debounce-timer-id])]
      {:db (update db :advisor dissoc :meeting-editor)
       ::editor-fx/cancel-debounce-timer {:timer-id timer-id}})))

;; Store timer ID
(rf/reg-event-db
  ::set-timer-id
  (fn [db [_ timer-id]]
    (assoc-in db [:advisor :meeting-editor :debounce-timer-id] timer-id)))

;; =============================================================================
;; Student Credentials (for inline editing during intake meeting)
;; =============================================================================

;; Load student credentials for display in the credentials editor
(rf/reg-event-fx
  ::load-student-credentials
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (assoc-in db [:advisor :meeting-editor :credentials-loading?] true)
     ::student-fx/fetch-student-detail-screen {:student-id student-id
                                                :api-client api-client
                                                :on-success [::load-credentials-success]
                                                :on-failure [::load-credentials-failure]}}))

(rf/reg-event-db
  ::load-credentials-success
  (fn [db [_ {:keys [student]}]]
    (-> db
        (assoc-in [:advisor :meeting-editor :credentials]
                  {:online-grade-book-url (:student/online-grade-book-url student)
                   :online-grade-book-username (:student/online-grade-book-username student)
                   :online-grade-book-password (:student/online-grade-book-password student)
                   :act-dot-org-username (:student/act-dot-org-username student)
                   :act-dot-org-password (:student/act-dot-org-password student)})
        (assoc-in [:advisor :meeting-editor :credentials-loading?] false))))

(rf/reg-event-db
  ::load-credentials-failure
  (fn [db [_ _error]]
    (assoc-in db [:advisor :meeting-editor :credentials-loading?] false)))

;; Save credential field - updates local state optimistically and dispatches save
(rf/reg-event-fx
  ::save-credential-field
  (fn [{:keys [db]} [_ student-id field-slug value api-client]]
    {:db (assoc-in db [:advisor :meeting-editor :credentials field-slug] value)
     ::student-fx/set-contact-field {:contact-id student-id
                                     :field-slug (name field-slug)
                                     :value value
                                     :api-client api-client
                                     :on-success [::save-credential-success field-slug]
                                     :on-failure [::save-credential-failure field-slug]}}))

;; Save encrypted credential field
(rf/reg-event-fx
  ::save-encrypted-credential-field
  (fn [{:keys [db]} [_ student-id field-slug value api-client]]
    {:db (assoc-in db [:advisor :meeting-editor :credentials field-slug] value)
     ::student-fx/set-encrypted-contact-field {:contact-id student-id
                                               :field-slug (name field-slug)
                                               :value value
                                               :api-client api-client
                                               :on-success [::save-credential-success field-slug]
                                               :on-failure [::save-credential-failure field-slug]}}))

(rf/reg-event-db
  ::save-credential-success
  (fn [db [_ _field-slug _student-id _value _api-client]]
    ;; Local state already updated optimistically
    db))

(rf/reg-event-db
  ::save-credential-failure
  (fn [db [_ field-slug _error]]
    ;; Could show error toast here - for now just log
    (js/console.error "Failed to save credential field:" (name field-slug))
    db))
