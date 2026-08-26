(ns store.comms.core
  "Re-frame store for the conversation panel: events / subs / effects.
   Single namespace by design — same convention as store/crm/guardian/core.cljs."
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

;; =============================================================================
;; Effects
;; =============================================================================

(rf/reg-fx
  ::fetch-conversation-panel
  (fn [{:keys [contact-id conversation-id api-client on-success on-failure]}]
    (when (and (or contact-id conversation-id) api-client)
      (go
        (let [response (<! (api/query api-client
                                      (cond-> {:query/name :communications/conversation-panel}
                                        contact-id (assoc :contact-id contact-id)
                                        conversation-id (assoc :conversation-id conversation-id))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::send-message-fx
  (fn [{:keys [conversation-id body api-client on-success on-failure]}]
    (when (and conversation-id api-client)
      (go
        (let [response (<! (api/command
                             api-client
                             {:command/name :communications/send-message
                              :conversation-id conversation-id
                              :body body}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::create-conversation-fx
  (fn [{:keys [contact-id title api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :communications/create-conversation
                                                 :participant-contact-id contact-id}
                                          title (assoc :title title))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::create-thread-fx
  (fn [{:keys [contact-id title api-client on-success on-failure]}]
    (when (and contact-id api-client)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :communications/create-conversation
                                                 :participant-contact-id contact-id}
                                          title (assoc :title title))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::mark-read-fx
  (fn [{:keys [conversation-id api-client]}]
    (when (and conversation-id api-client)
      (go
        (<! (api/command api-client {:command/name :communications/mark-conversation-read
                                      :conversation-id conversation-id}))))))

(rf/reg-fx
  ::view-conversation-fx
  (fn [{:keys [conversation-id user-name api-client]}]
    (when (and conversation-id api-client)
      (go
        (<! (api/command api-client (cond-> {:command/name :communications/view-conversation
                                              :conversation-id conversation-id}
                                      user-name (assoc :user-name user-name))))))))

;; =============================================================================
;; Events — load / refresh
;; =============================================================================

(rf/reg-event-fx
  ::load-conversation
  (fn [{:keys [db]} [_ contact-id api-client]]
    {:db (-> db
             (assoc-in [:comms :current-contact-id] contact-id)
             (assoc-in [:comms :current-thread-mode] :contact)
             (assoc-in [:comms :loading?] true))
     ::fetch-conversation-panel {:contact-id contact-id
                                  :api-client api-client
                                  :on-success [::load-conversation-success contact-id]
                                  :on-failure [::load-conversation-failure]}}))

(rf/reg-event-fx
  ::load-conversation-by-id
  (fn [{:keys [db]} [_ conversation-id api-client]]
    {:db (-> db
             (assoc-in [:comms :current-contact-id] nil)
             (assoc-in [:comms :current-conversation-id] conversation-id)
             (assoc-in [:comms :current-thread-mode] :conversation)
             (assoc-in [:comms :loading?] true))
     ::fetch-conversation-panel {:conversation-id conversation-id
                                  :api-client api-client
                                  :on-success [::load-conversation-by-id-success conversation-id]
                                  :on-failure [::load-conversation-failure]}}))

(rf/reg-event-db
  ::load-conversation-success
  (fn [db [_ contact-id {:keys [contact conversation viewers participants staff]}]]
    (cond-> (-> db
                (assoc-in [:comms :loading?] false)
                (assoc-in [:comms :error] nil)
                (assoc-in [:comms :contacts-by-id contact-id] contact)
                (assoc-in [:comms :conversations-by-contact-id contact-id] conversation)
                (assoc-in [:comms :current-conversation-id] (:conversation-id conversation))
                (assoc-in [:comms :participants-by-contact-id contact-id] (vec participants))
                (assoc-in [:comms :staff-by-contact-id contact-id] (vec staff))
                (assoc-in [:comms :viewers-by-contact-id contact-id] (vec viewers)))
      (:conversation-id conversation)
      (assoc-in [:comms :conversations-by-id (:conversation-id conversation)] conversation))))

(rf/reg-event-db
  ::load-conversation-by-id-success
  (fn [db [_ conversation-id {:keys [conversation viewers participants staff]}]]
    (-> db
        (assoc-in [:comms :loading?] false)
        (assoc-in [:comms :error] nil)
        (assoc-in [:comms :current-conversation-id] conversation-id)
        (assoc-in [:comms :conversations-by-id conversation-id] conversation)
        (assoc-in [:comms :participants-by-conversation-id conversation-id] (vec participants))
        (assoc-in [:comms :staff-by-conversation-id conversation-id] (vec staff))
        (assoc-in [:comms :viewers-by-conversation-id conversation-id] (vec viewers)))))

(rf/reg-event-db
  ::load-conversation-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:comms :loading?] false)
        (assoc-in [:comms :error] error))))

(rf/reg-event-fx
  ::refresh-conversation
  (fn [{:keys [db]} [_ api-client]]
    (let [mode (get-in db [:comms :current-thread-mode])
          contact-id (get-in db [:comms :current-contact-id])
          conversation-id (get-in db [:comms :current-conversation-id])]
      (case mode
        :conversation
        (when conversation-id
          {::fetch-conversation-panel {:conversation-id conversation-id
                                        :api-client api-client
                                        :on-success [::load-conversation-by-id-success conversation-id]
                                        :on-failure [::load-conversation-failure]}})

        (when contact-id
          {::fetch-conversation-panel {:contact-id contact-id
                                        :api-client api-client
                                        :on-success [::load-conversation-success contact-id]
                                        :on-failure [::load-conversation-failure]}})))))

;; =============================================================================
;; Events — composer state (channel, body, subject)
;; =============================================================================

(rf/reg-event-db
  ::set-channel
  (fn [db [_ channel]]
    (cond-> (assoc-in db [:comms :composer :channel] channel)
      ;; Switching out of email clears subject; the locked plan calls for this.
      (not= channel :email) (update-in [:comms :composer] dissoc :subject))))

(rf/reg-event-db
  ::set-body
  (fn [db [_ body]]
    (assoc-in db [:comms :composer :body] body)))

(rf/reg-event-db
  ::set-subject
  (fn [db [_ subject]]
    (assoc-in db [:comms :composer :subject] subject)))

(rf/reg-event-db
  ::clear-composer
  (fn [db _]
    (-> db
        (update-in [:comms :composer] dissoc :body)
        (update-in [:comms :composer] dissoc :subject)
        (update-in [:comms :composer] dissoc :error))))

;; =============================================================================
;; Events — send
;; =============================================================================

(rf/reg-event-fx
  ::send-message
  (fn [{:keys [db]} [_ api-client]]
    (let [contact-id (get-in db [:comms :current-contact-id])
          {:keys [body]} (get-in db [:comms :composer])
          conversation-id (get-in db [:comms :current-conversation-id])]
      (when (and (or conversation-id contact-id) (seq body))
        (let [db' (-> db
                      (assoc-in [:comms :composer :sending?] true)
                      (assoc-in [:comms :composer :error] nil))]
          (if conversation-id
            {:db db'
             ::send-message-fx {:conversation-id conversation-id
                                :body body
                                :api-client api-client
                                :on-success [::send-message-success api-client]
                                :on-failure [::send-message-failure]}}
            {:db db'
             ::create-conversation-fx {:contact-id contact-id
                                       :api-client api-client
                                       :on-success [::create-before-send-success api-client]
                                       :on-failure [::send-message-failure]}}))))))

(rf/reg-event-fx
  ::create-before-send-success
  (fn [{:keys [db]} [_ api-client response]]
    (let [conversation-id (or (:conversation-id response)
                              (get-in response [:command/result :conversation-id]))]
      (if conversation-id
        {:db (assoc-in db [:comms :current-conversation-id] conversation-id)
         ::send-message-fx {:conversation-id conversation-id
                            :body (get-in db [:comms :composer :body])
                            :api-client api-client
                            :on-success [::send-message-success api-client]
                            :on-failure [::send-message-failure]}}
        {:db (-> db
                 (assoc-in [:comms :composer :sending?] false)
                 (assoc-in [:comms :composer :error] "Failed to create conversation"))}))))

(rf/reg-event-fx
  ::send-message-success
  (fn [{:keys [db]} [_ api-client _response]]
    {:db (-> db
             (assoc-in [:comms :composer :sending?] false)
             (update-in [:comms :composer] dissoc :body)
             (update-in [:comms :composer] dissoc :subject)
             (update-in [:comms :composer] dissoc :error))
     :dispatch [::refresh-conversation api-client]}))

(rf/reg-event-db
  ::send-message-failure
  (fn [db [_ error]]
    (let [msg (or (:cognitect.anomalies/message error)
                  (:anomalies/message error)
                  "Failed to send message")]
      (-> db
          (assoc-in [:comms :composer :sending?] false)
          (assoc-in [:comms :composer :error] msg)))))

;; =============================================================================
;; Events — read receipt + presence
;; =============================================================================

(rf/reg-event-fx
  ::mark-read
  (fn [{:keys [db]} [_ api-client]]
    (when-let [conversation-id (get-in db [:comms :current-conversation-id])]
      {::mark-read-fx {:conversation-id conversation-id :api-client api-client}})))

(rf/reg-event-fx
  ::view-conversation
  (fn [{:keys [db]} [_ api-client user-name]]
    (when-let [conversation-id (get-in db [:comms :current-conversation-id])]
      {::view-conversation-fx {:conversation-id conversation-id
                                :user-name user-name
                                :api-client api-client}})))

(rf/reg-event-fx
  ::create-thread
  (fn [{:keys [db]} [_ api-client contact-id title]]
    {:db (-> db
             (assoc-in [:comms :new-thread :creating?] true)
             (assoc-in [:comms :new-thread :error] nil))
     ::create-thread-fx {:contact-id contact-id
                         :title title
                         :api-client api-client
                         :on-success [::create-thread-success]
                         :on-failure [::create-thread-failure]}}))

(rf/reg-event-db
  ::create-thread-success
  (fn [db [_ response]]
    (-> db
        (assoc-in [:comms :new-thread :creating?] false)
        (assoc-in [:comms :new-thread :created-conversation-id]
                  (or (:conversation-id response)
                      (get-in response [:command/result :conversation-id])))
        (assoc-in [:comms :new-thread :error] nil))))

(rf/reg-event-db
  ::clear-created-thread
  (fn [db _]
    (update-in db [:comms :new-thread] dissoc :created-conversation-id)))

(rf/reg-event-db
  ::create-thread-failure
  (fn [db [_ error]]
    (let [msg (or (:cognitect.anomalies/message error)
                  (:anomalies/message error)
                  "Failed to start conversation")]
      (-> db
          (assoc-in [:comms :new-thread :creating?] false)
          (assoc-in [:comms :new-thread :error] msg)))))

;; =============================================================================
;; Subs
;; =============================================================================

(rf/reg-sub
  ::current-contact-id
  (fn [db _]
    (get-in db [:comms :current-contact-id])))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (boolean (get-in db [:comms :loading?]))))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:comms :error])))

(rf/reg-sub
  ::contact
  (fn [db _]
    (when-let [id (get-in db [:comms :current-contact-id])]
      (get-in db [:comms :contacts-by-id id]))))

(rf/reg-sub
  ::conversation
  (fn [db _]
    (let [mode (get-in db [:comms :current-thread-mode])]
      (case mode
        :conversation
        (when-let [id (get-in db [:comms :current-conversation-id])]
          (get-in db [:comms :conversations-by-id id]))

        (when-let [id (get-in db [:comms :current-contact-id])]
          (get-in db [:comms :conversations-by-contact-id id]))))))

(rf/reg-sub
  ::messages
  (fn [db _]
    (let [mode (get-in db [:comms :current-thread-mode])]
      (case mode
        :conversation
        (if-let [id (get-in db [:comms :current-conversation-id])]
          (or (get-in db [:comms :conversations-by-id id :messages]) [])
          [])

        (if-let [id (get-in db [:comms :current-contact-id])]
          (or (get-in db [:comms :conversations-by-contact-id id :messages]) [])
          [])))))

(rf/reg-sub
  ::participants
  (fn [db _]
    (let [mode (get-in db [:comms :current-thread-mode])]
      (case mode
        :conversation
        (if-let [id (get-in db [:comms :current-conversation-id])]
          (or (get-in db [:comms :participants-by-conversation-id id]) [])
          [])

        (if-let [id (get-in db [:comms :current-contact-id])]
          (or (get-in db [:comms :participants-by-contact-id id]) [])
          [])))))

(rf/reg-sub
  ::viewers
  (fn [db _]
    (let [mode (get-in db [:comms :current-thread-mode])]
      (case mode
        :conversation
        (if-let [id (get-in db [:comms :current-conversation-id])]
          (or (get-in db [:comms :viewers-by-conversation-id id]) [])
          [])

        (if-let [id (get-in db [:comms :current-contact-id])]
          (or (get-in db [:comms :viewers-by-contact-id id]) [])
          [])))))

(rf/reg-sub
  ::channel
  (fn [db _]
    (or (get-in db [:comms :composer :channel]) :sms)))

(rf/reg-sub
  ::body
  (fn [db _]
    (or (get-in db [:comms :composer :body]) "")))

(rf/reg-sub
  ::subject
  (fn [db _]
    (or (get-in db [:comms :composer :subject]) "")))

(rf/reg-sub
  ::sending?
  (fn [db _]
    (boolean (get-in db [:comms :composer :sending?]))))

(rf/reg-sub
  ::send-error
  (fn [db _]
    (get-in db [:comms :composer :error])))

(rf/reg-sub
  ::new-thread-creating?
  (fn [db _]
    (boolean (get-in db [:comms :new-thread :creating?]))))

(rf/reg-sub
  ::new-thread-error
  (fn [db _]
    (get-in db [:comms :new-thread :error])))

(rf/reg-sub
  ::created-thread-id
  (fn [db _]
    (get-in db [:comms :new-thread :created-conversation-id])))

;; =============================================================================
;; Inbox (Surface 2)
;; =============================================================================

(rf/reg-fx
  ::fetch-inbox
  (fn [{:keys [filter search api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query
                             api-client
                             (cond-> {:query/name :communications/inbox
                                      :filter (or filter :all)}
                               (and search (>= (count search) 2))
                               (assoc :search search))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-event-fx
  ::load-inbox
  (fn [{:keys [db]} [_ api-client]]
    (let [filter (or (get-in db [:comms :inbox :filter]) :all)
          search (get-in db [:comms :inbox :search])]
      {:db (-> db
               (assoc-in [:comms :inbox :loading?] true)
               (assoc-in [:comms :inbox :error] nil))
       ::fetch-inbox {:filter filter
                      :search search
                      :api-client api-client
                      :on-success [::load-inbox-success]
                      :on-failure [::load-inbox-failure]}})))

(rf/reg-event-db
  ::load-inbox-success
  (fn [db [_ {:keys [conversations total]}]]
    (-> db
        (assoc-in [:comms :inbox :loading?] false)
        (assoc-in [:comms :inbox :error] nil)
        (assoc-in [:comms :inbox :conversations] (vec conversations))
        (assoc-in [:comms :inbox :total] total))))

(rf/reg-event-db
  ::load-inbox-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:comms :inbox :loading?] false)
        (assoc-in [:comms :inbox :error] error))))

(rf/reg-event-fx
  ::set-inbox-filter
  (fn [{:keys [db]} [_ filter api-client]]
    {:db (assoc-in db [:comms :inbox :filter] filter)
     :dispatch [::load-inbox api-client]}))

(rf/reg-event-fx
  ::set-inbox-search
  (fn [{:keys [db]} [_ search api-client]]
    {:db (assoc-in db [:comms :inbox :search] search)
     :dispatch [::load-inbox api-client]}))

(rf/reg-sub
  ::inbox-conversations
  (fn [db _]
    (or (get-in db [:comms :inbox :conversations]) [])))

(rf/reg-sub
  ::inbox-total
  (fn [db _]
    (or (get-in db [:comms :inbox :total]) 0)))

(rf/reg-sub
  ::inbox-loading?
  (fn [db _]
    (boolean (get-in db [:comms :inbox :loading?]))))

(rf/reg-sub
  ::inbox-error
  (fn [db _]
    (get-in db [:comms :inbox :error])))

(rf/reg-sub
  ::inbox-filter
  (fn [db _]
    (or (get-in db [:comms :inbox :filter]) :all)))

(rf/reg-sub
  ::inbox-search
  (fn [db _]
    (or (get-in db [:comms :inbox :search]) "")))
