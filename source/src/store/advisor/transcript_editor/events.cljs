(ns store.advisor.transcript-editor.events
  "Transcript editor page events"
  (:require [re-frame.core :as rf]
            [store.advisor.transcript-editor.effects :as transcript-fx]))

;; Initialize transcript editor for a student
(rf/reg-event-fx
  ::load-transcript
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (-> db
             (assoc-in [:advisor :transcript-editor :current-student-id] student-id)
             (assoc-in [:advisor :transcript-editor :loading?] true)
             (assoc-in [:advisor :transcript-editor :error] nil))
     ::transcript-fx/fetch-transcript-screen {:student-id student-id
                                               :api-client api-client
                                               :on-success [::load-success]
                                               :on-failure [::load-failure]}}))

;; Transcript screen data loaded successfully
(rf/reg-event-db
  ::load-success
  (fn [db [_ response]]
    (let [{:keys [student transcript-history current-transcript-id current-transcript]} response
          student-id (:student/id student)
          transcript-data (get current-transcript :transcript/data)
          transcript-id (get current-transcript :transcript/id)]
      (-> db
          ;; Store student summary
          (assoc-in [:advisor :transcript-editor :student] student)
          ;; Store transcript history
          (assoc-in [:advisor :transcript-editor :transcript-history] transcript-history)
          ;; Store current transcript ID
          (assoc-in [:advisor :transcript-editor :current-transcript-id] current-transcript-id)
          ;; Store original transcript in editor state
          (assoc-in [:advisor :transcript-editor :editing :original-transcript] transcript-data)
          (assoc-in [:advisor :transcript-editor :editing :transcript-id] transcript-id)
          (assoc-in [:advisor :transcript-editor :editing :transcript] nil)  ; Clear working copy
          (assoc-in [:advisor :transcript-editor :loading?] false)
          (assoc-in [:advisor :transcript-editor :error] nil)))))

;; Failed to load transcript screen
(rf/reg-event-db
  ::load-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :transcript-editor :loading?] false)
        (assoc-in [:advisor :transcript-editor :error] "Failed to load transcript"))))

;; Toggle weighted flag on a course
(rf/reg-event-db
  ::toggle-weighted
  (fn [db [_ idx]]
    (let [original (get-in db [:advisor :transcript-editor :editing :original-transcript])
          working-copy (get-in db [:advisor :transcript-editor :editing :transcript])
          current (or working-copy original)
          updated (vec (map-indexed
                         (fn [i course]
                           (if (= i idx)
                             (assoc course :weighted (not (:weighted course)))
                             course))
                         current))]
      (assoc-in db [:advisor :transcript-editor :editing :transcript] updated))))

;; Toggle core flag on a course
(rf/reg-event-db
  ::toggle-core
  (fn [db [_ idx]]
    (let [original (get-in db [:advisor :transcript-editor :editing :original-transcript])
          working-copy (get-in db [:advisor :transcript-editor :editing :transcript])
          current (or working-copy original)
          updated (vec (map-indexed
                         (fn [i course]
                           (if (= i idx)
                             (assoc course :core (not (:core course)))
                             course))
                         current))]
      (assoc-in db [:advisor :transcript-editor :editing :transcript] updated))))

;; Save transcript edits
(rf/reg-event-fx
  ::save-transcript
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :transcript-editor :current-student-id])
          transcript-id (get-in db [:advisor :transcript-editor :editing :transcript-id])
          transcript (get-in db [:advisor :transcript-editor :editing :transcript])]
      {:db (assoc-in db [:advisor :transcript-editor :saving?] true)
       ::transcript-fx/save-transcript {:student-id student-id
                                        :transcript-id transcript-id
                                        :transcript transcript
                                        :api-client api-client
                                        :on-success [::save-success api-client]
                                        :on-failure [::save-failure]}})))

;; Save succeeded
(rf/reg-event-fx
  ::save-success
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :transcript-editor :current-student-id])]
      {:db (-> db
               (assoc-in [:advisor :transcript-editor :saving?] false)
               (assoc-in [:advisor :transcript-editor :save-success?] true))
       ;; Re-fetch transcript screen to get latest data
       ::transcript-fx/fetch-transcript-screen {:student-id student-id
                                                 :api-client api-client
                                                 :on-success [::refresh-success]
                                                 :on-failure [::refresh-failure]}
       ;; Schedule clearing success indicator after 2 seconds
       ::transcript-fx/schedule-clear-success {:delay-ms 2000
                                               :on-trigger [::clear-save-success]}})))

;; Refresh after save succeeded
(rf/reg-event-db
  ::refresh-success
  (fn [db [_ response]]
    (let [{:keys [student transcript-history current-transcript-id current-transcript]} response
          transcript-data (get current-transcript :transcript/data)
          transcript-id (get current-transcript :transcript/id)]
      (-> db
          ;; Update student summary
          (assoc-in [:advisor :transcript-editor :student] student)
          ;; Update transcript history
          (assoc-in [:advisor :transcript-editor :transcript-history] transcript-history)
          ;; Update current transcript ID
          (assoc-in [:advisor :transcript-editor :current-transcript-id] current-transcript-id)
          ;; Update original transcript
          (assoc-in [:advisor :transcript-editor :editing :original-transcript] transcript-data)
          (assoc-in [:advisor :transcript-editor :editing :transcript-id] transcript-id)
          ;; Clear working copy (edits are now saved)
          (assoc-in [:advisor :transcript-editor :editing :transcript] nil)))))

;; Refresh after save failed
(rf/reg-event-db
  ::refresh-failure
  (fn [db [_ error]]
    ;; Log error but don't show to user - save was successful
    (do
      (js/console.error "Failed to refresh transcript after save:" error)
      db)))

;; Save failed
(rf/reg-event-db
  ::save-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :transcript-editor :saving?] false)
        (assoc-in [:advisor :transcript-editor :error] "Failed to save transcript"))))

;; Cancel edits
(rf/reg-event-db
  ::cancel-edit
  (fn [db _]
    (assoc-in db [:advisor :transcript-editor :editing :transcript] nil)))

;; Set upload modal open state
(rf/reg-event-db
  ::set-upload-modal-open
  (fn [db [_ open?]]
    (assoc-in db [:advisor :transcript-editor :upload-modal-open?] open?)))

;; Clear success indicator
(rf/reg-event-db
  ::clear-save-success
  (fn [db _]
    (assoc-in db [:advisor :transcript-editor :save-success?] false)))

;; Delete transcript
(rf/reg-event-fx
  ::delete-transcript
  (fn [{:keys [db]} [_ transcript-id api-client]]
    (let [student-id (get-in db [:advisor :transcript-editor :current-student-id])]
      {:db (assoc-in db [:advisor :transcript-editor :deleting?] true)
       ::transcript-fx/delete-transcript {:student-id student-id
                                           :transcript-id transcript-id
                                           :api-client api-client
                                           :on-success [::delete-success api-client]
                                           :on-failure [::delete-failure]}})))

;; Delete succeeded
(rf/reg-event-fx
  ::delete-success
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :transcript-editor :current-student-id])]
      {:db (-> db
               (assoc-in [:advisor :transcript-editor :deleting?] false)
               (assoc-in [:advisor :transcript-editor :delete-confirm-id] nil))
       ;; Re-fetch transcript screen to get latest data
       ::transcript-fx/fetch-transcript-screen {:student-id student-id
                                                 :api-client api-client
                                                 :on-success [::refresh-success]
                                                 :on-failure [::refresh-failure]}})))

;; Delete failed
(rf/reg-event-db
  ::delete-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :transcript-editor :deleting?] false)
        (assoc-in [:advisor :transcript-editor :error] "Failed to delete transcript"))))

;; Set delete confirmation dialog state
(rf/reg-event-db
  ::set-delete-confirm
  (fn [db [_ transcript-id]]
    (assoc-in db [:advisor :transcript-editor :delete-confirm-id] transcript-id)))

;; View PDF - get presigned URL and open in new tab
(rf/reg-event-fx
  ::view-pdf
  (fn [{:keys [db]} [_ file-id api-client]]
    {::transcript-fx/get-transcript-pdf-url {:file-id file-id
                                              :api-client api-client
                                              :on-success [::open-pdf-url]
                                              :on-failure [::view-pdf-failure]}}))

;; Open PDF URL in new tab
(rf/reg-event-fx
  ::open-pdf-url
  (fn [_ [_ url]]
    (js/window.open url "_blank")
    {}))

;; View PDF failed
(rf/reg-event-db
  ::view-pdf-failure
  (fn [db [_ error]]
    (js/console.error "Failed to get PDF URL:" error)
    (assoc-in db [:advisor :transcript-editor :error] "Failed to open PDF")))

;; Cleanup on unmount
(rf/reg-event-db
  ::cleanup
  (fn [db _]
    (assoc-in db [:advisor :transcript-editor] nil)))
