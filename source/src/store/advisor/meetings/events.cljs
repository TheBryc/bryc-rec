(ns store.advisor.meetings.events
  "Advisor meetings page events"
  (:require [re-frame.core :as rf]
            [store.advisor.meetings.effects :as meetings-fx]))

;; Track current student ID
(rf/reg-event-db
  ::set-student-id
  (fn [db [_ student-id]]
    (assoc-in db [:advisor :meetings :student-id] student-id)))

;; =============================================================================
;; Screen Query - Fat Query Pattern
;; =============================================================================

;; Load meetings screen data with one query
(rf/reg-event-fx
  ::load-screen
  (fn [{:keys [db]} [_ student-id api-client]]
    (when-not (and (get-in db [:advisor :meetings :loading?])
                   (= student-id (get-in db [:advisor :meetings :student-id])))
      {:db (-> db
               (assoc-in [:advisor :meetings :student-id] student-id)
               (assoc-in [:advisor :meetings :loading?] true)
               (assoc-in [:advisor :meetings :error] nil))
       ::meetings-fx/fetch-meetings-screen {:student-id student-id
                                             :api-client api-client
                                             :on-success [::load-screen-success]
                                             :on-failure [::load-screen-failure]}})))

(rf/reg-event-db
  ::load-screen-success
  (fn [db [_ {:keys [student meetings]}]]
    (let [expected-student-id (get-in db [:advisor :meetings :student-id])
          actual-student-id (:student/id student)]
      (if (= expected-student-id actual-student-id)
        (let [meetings-map (into {} (map (fn [m] [(:meeting/id m) m]) meetings))
              meeting-ids (mapv :meeting/id meetings)]
          (-> db
              (assoc-in [:advisor :meetings :student] student)
              (update-in [:advisor :meetings-by-id] merge meetings-map)
              (assoc-in [:advisor :meetings :meetings-list] meeting-ids)
              (assoc-in [:advisor :meetings :loading?] false)
              (assoc-in [:advisor :meetings :error] nil)))
        db))))

(rf/reg-event-db
  ::load-screen-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :meetings :loading?] false)
        (assoc-in [:advisor :meetings :error] error))))

;; Load meeting editor screen data with one query
(rf/reg-event-fx
  ::load-editor-screen
  (fn [{:keys [db]} [_ student-id meeting-id api-client]]
    {:db (-> db
             (assoc-in [:advisor :meetings :student-id] student-id)
             (assoc-in [:advisor :meetings :loading?] true)
             (assoc-in [:advisor :meetings :error] nil))
     ::meetings-fx/fetch-meeting-editor-screen {:student-id student-id
                                                 :meeting-id meeting-id
                                                 :api-client api-client
                                                 :on-success [::load-editor-screen-success]
                                                 :on-failure [::load-screen-failure]}}))

(rf/reg-event-db
  ::load-editor-screen-success
  (fn [db [_ {:keys [student meeting meetings]}]]
    (let [meetings-map (into {} (map (fn [m] [(:meeting/id m) m]) meetings))
          meeting-ids (mapv :meeting/id meetings)
          meeting-id (:meeting/id meeting)]
      (-> db
          (assoc-in [:advisor :meetings :student] student)
          (assoc-in [:advisor :meetings :current-meeting] meeting)
          (update-in [:advisor :meetings-by-id] merge meetings-map)
          ;; Also ensure current meeting is in meetings-by-id (for meeting-editor subs)
          (assoc-in [:advisor :meetings-by-id meeting-id] meeting)
          (assoc-in [:advisor :meetings :meetings-list] meeting-ids)
          (assoc-in [:advisor :meetings :loading?] false)
          (assoc-in [:advisor :meetings :error] nil)))))

;; Start new meeting
(rf/reg-event-fx
  ::start-meeting
  (fn [{:keys [db]} [_ student-id meeting-type api-client]]
    {:db (-> db
             (assoc-in [:advisor :meetings :starting-meeting?] true)
             (assoc-in [:advisor :meetings :start-error] nil))
     ::meetings-fx/start-meeting {:student-id student-id
                                  :meeting-type meeting-type
                                  :api-client api-client
                                  :on-success [::start-success]
                                  :on-failure [::start-failure]}}))

(rf/reg-event-db
  ::start-success
  (fn [db [_ meeting-id student-id meeting-type]]
    ;; Optimistically add the new meeting to the local cache
    ;; This avoids the race condition where we navigate to the meeting page
    ;; before the fetch-meetings completes
    (let [new-meeting {:meeting/id meeting-id
                       :meeting/student-id student-id
                       :meeting/type meeting-type
                       ;; Use ISO 8601 format to match backend timestamps
                       :meeting/started-at (.toISOString (js/Date.))
                       :meeting/status :in-progress
                       :meeting/notes ""}
          current-meetings (get-in db [:advisor :meetings :meetings-list] [])]
      (-> db
          ;; Add to normalized cache
          (assoc-in [:advisor :meetings-by-id meeting-id] new-meeting)
          ;; Add to meetings list (prepend since we sort by started-at desc)
          (assoc-in [:advisor :meetings :meetings-list] (into [meeting-id] current-meetings))
          ;; Store the new meeting-id for navigation
          (assoc-in [:advisor :meetings :new-meeting-id] meeting-id)
          ;; Clear starting state
          (assoc-in [:advisor :meetings :starting-meeting?] false)
          (assoc-in [:advisor :meetings :start-error] nil)))))

(rf/reg-event-db
  ::start-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :meetings :starting-meeting?] false)
        (assoc-in [:advisor :meetings :start-error] error))))

;; Clear new meeting ID after navigation
(rf/reg-event-db
  ::clear-new-meeting-id
  (fn [db _]
    (assoc-in db [:advisor :meetings :new-meeting-id] nil)))

;; =============================================================================
;; Transcript Analysis Review Events
;; =============================================================================

;; Accept/Reject update events track saving state by update-id
(rf/reg-event-fx
  ::accept-transcript-update
  (fn [{:keys [db]} [_ {:keys [student-id meeting-id update-id field-slug new-value api-client]}]]
    {:db (assoc-in db [:advisor :meetings :saving update-id] true)
     ::meetings-fx/accept-transcript-update
     {:student-id student-id
      :meeting-id meeting-id
      :update-id update-id
      :field-slug field-slug
      :new-value new-value
      :api-client api-client
      :on-success [::accept-update-success meeting-id update-id]
      :on-failure [::accept-update-failure meeting-id update-id]}}))

(rf/reg-event-fx
  ::accept-update-success
  (fn [{:keys [db]} [_ meeting-id update-id]]
    {:db (-> db
             (assoc-in [:advisor :meetings :saving update-id] false)
             ;; Update local meeting state with the decision (both current-meeting and meetings-by-id)
             (assoc-in [:advisor :meetings :current-meeting :meeting/update-decisions update-id] :accepted)
             (assoc-in [:advisor :meetings-by-id meeting-id :meeting/update-decisions update-id] :accepted))
     ;; Refresh the meeting to get authoritative state
     :dispatch [::load-editor-screen
                (get-in db [:advisor :meetings :student-id])
                meeting-id
                (get-in db [:advisor :api-client])]}))

(rf/reg-event-db
  ::accept-update-failure
  (fn [db [_ _meeting-id update-id error]]
    (-> db
        (assoc-in [:advisor :meetings :saving update-id] false)
        (assoc-in [:advisor :meetings :error update-id] error))))

(rf/reg-event-fx
  ::reject-transcript-update
  (fn [{:keys [db]} [_ {:keys [student-id meeting-id update-id api-client]}]]
    {:db (assoc-in db [:advisor :meetings :saving update-id] true)
     ::meetings-fx/reject-transcript-update
     {:student-id student-id
      :meeting-id meeting-id
      :update-id update-id
      :api-client api-client
      :on-success [::reject-update-success meeting-id update-id]
      :on-failure [::reject-update-failure meeting-id update-id]}}))

(rf/reg-event-fx
  ::reject-update-success
  (fn [{:keys [db]} [_ meeting-id update-id]]
    {:db (-> db
             (assoc-in [:advisor :meetings :saving update-id] false)
             ;; Update local meeting state with the decision (both current-meeting and meetings-by-id)
             (assoc-in [:advisor :meetings :current-meeting :meeting/update-decisions update-id] :rejected)
             (assoc-in [:advisor :meetings-by-id meeting-id :meeting/update-decisions update-id] :rejected))
     :dispatch [::load-editor-screen
                (get-in db [:advisor :meetings :student-id])
                meeting-id
                (get-in db [:advisor :api-client])]}))

(rf/reg-event-db
  ::reject-update-failure
  (fn [db [_ _meeting-id update-id error]]
    (-> db
        (assoc-in [:advisor :meetings :saving update-id] false)
        (assoc-in [:advisor :meetings :error update-id] error))))

;; Accept/Reject narrative events track saving state by narrative-id
(rf/reg-event-fx
  ::accept-transcript-narrative
  (fn [{:keys [db]} [_ {:keys [student-id meeting-id narrative-id narrative-text api-client]}]]
    {:db (assoc-in db [:advisor :meetings :saving narrative-id] true)
     ::meetings-fx/accept-transcript-narrative
     {:student-id student-id
      :meeting-id meeting-id
      :narrative-id narrative-id
      :narrative-text narrative-text
      :api-client api-client
      :on-success [::accept-narrative-success meeting-id narrative-id]
      :on-failure [::accept-narrative-failure meeting-id narrative-id]}}))

(rf/reg-event-fx
  ::accept-narrative-success
  (fn [{:keys [db]} [_ meeting-id narrative-id]]
    {:db (-> db
             (assoc-in [:advisor :meetings :saving narrative-id] false)
             ;; Update local meeting state with the decision (both current-meeting and meetings-by-id)
             (assoc-in [:advisor :meetings :current-meeting :meeting/narrative-decisions narrative-id] :accepted)
             (assoc-in [:advisor :meetings-by-id meeting-id :meeting/narrative-decisions narrative-id] :accepted))
     :dispatch [::load-editor-screen
                (get-in db [:advisor :meetings :student-id])
                meeting-id
                (get-in db [:advisor :api-client])]}))

(rf/reg-event-db
  ::accept-narrative-failure
  (fn [db [_ _meeting-id narrative-id error]]
    (-> db
        (assoc-in [:advisor :meetings :saving narrative-id] false)
        (assoc-in [:advisor :meetings :error narrative-id] error))))

(rf/reg-event-fx
  ::reject-transcript-narrative
  (fn [{:keys [db]} [_ {:keys [student-id meeting-id narrative-id api-client]}]]
    {:db (assoc-in db [:advisor :meetings :saving narrative-id] true)
     ::meetings-fx/reject-transcript-narrative
     {:student-id student-id
      :meeting-id meeting-id
      :narrative-id narrative-id
      :api-client api-client
      :on-success [::reject-narrative-success meeting-id narrative-id]
      :on-failure [::reject-narrative-failure meeting-id narrative-id]}}))

(rf/reg-event-fx
  ::reject-narrative-success
  (fn [{:keys [db]} [_ meeting-id narrative-id]]
    {:db (-> db
             (assoc-in [:advisor :meetings :saving narrative-id] false)
             ;; Update local meeting state with the decision (both current-meeting and meetings-by-id)
             (assoc-in [:advisor :meetings :current-meeting :meeting/narrative-decisions narrative-id] :rejected)
             (assoc-in [:advisor :meetings-by-id meeting-id :meeting/narrative-decisions narrative-id] :rejected))
     :dispatch [::load-editor-screen
                (get-in db [:advisor :meetings :student-id])
                meeting-id
                (get-in db [:advisor :api-client])]}))

(rf/reg-event-db
  ::reject-narrative-failure
  (fn [db [_ _meeting-id narrative-id error]]
    (-> db
        (assoc-in [:advisor :meetings :saving narrative-id] false)
        (assoc-in [:advisor :meetings :error narrative-id] error))))
