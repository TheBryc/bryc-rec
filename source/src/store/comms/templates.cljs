(ns store.comms.templates
  "Re-frame store for the templates library + composer 'Insert template' popover.
   Templates split into two scopes — :org (workspace-wide) and :user (personal
   snippets). Mail-merge token expansion happens client-side at insert time
   against the active recipient's CRM field-values."
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [components.comms.tokens :as tokens]
            [anomalies :refer [anomaly?]]))

;; =============================================================================
;; Token expansion — delegates to the shared single source of truth so the
;; popover, the chip bar, the preview, and the server stay in lockstep.
;; =============================================================================

(defn expand-tokens
  "Expand mail-merge tokens in `s` against `contact`. Pure — safe to call
   from a re-frame event handler or directly."
  [s contact]
  (tokens/expand s contact))

;; =============================================================================
;; Effects
;; =============================================================================

(rf/reg-fx
  ::fetch-templates
  (fn [{:keys [api-client channel on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query
                             api-client
                             (cond-> {:query/name :communications/templates}
                               channel (assoc :channel channel))))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::save-template-fx
  (fn [{:keys [api-client template on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command
                             api-client
                             (merge {:command/name :communications/save-template}
                                    template)))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::fetch-email-layout
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :communications/email-layout}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::save-email-layout-fx
  (fn [{:keys [api-client html on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                                        {:command/name :communications/save-email-layout
                                         :html html}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::delete-template-fx
  (fn [{:keys [api-client template-id on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command
                             api-client
                             {:command/name :communications/delete-template
                              :template-id template-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

;; =============================================================================
;; Events
;; =============================================================================

(rf/reg-event-fx
  ::load-templates
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:templates :loading?] true)
     ::fetch-templates {:api-client api-client
                        :on-success [::load-templates-success]
                        :on-failure [::load-templates-failure]}}))

(rf/reg-event-fx
  ::load-email-layout
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:templates :email-layout :loading?] true)
     ::fetch-email-layout {:api-client api-client
                           :on-success [::load-email-layout-success]
                           :on-failure [::load-email-layout-failure]}}))

(rf/reg-event-db
  ::load-email-layout-success
  (fn [db [_ {:keys [layout]}]]
    (-> db
        (assoc-in [:templates :email-layout :loading?] false)
        (assoc-in [:templates :email-layout :error] nil)
        (assoc-in [:templates :email-layout :layout] layout)
        (assoc-in [:templates :email-layout :draft-html] (:html layout)))))

(rf/reg-event-db
  ::load-email-layout-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:templates :email-layout :loading?] false)
        (assoc-in [:templates :email-layout :error] error))))

(rf/reg-event-db
  ::set-email-layout-draft
  (fn [db [_ html]]
    (assoc-in db [:templates :email-layout :draft-html] html)))

(rf/reg-event-fx
  ::save-email-layout
  (fn [{:keys [db]} [_ api-client]]
    (let [html (get-in db [:templates :email-layout :draft-html])]
      {:db (-> db
               (assoc-in [:templates :email-layout :saving?] true)
               (assoc-in [:templates :email-layout :error] nil))
       ::save-email-layout-fx {:api-client api-client
                               :html html
                               :on-success [::save-email-layout-success api-client]
                               :on-failure [::save-email-layout-failure]}})))

(rf/reg-event-fx
  ::save-email-layout-success
  (fn [{:keys [db]} [_ api-client _result]]
    {:db (assoc-in db [:templates :email-layout :saving?] false)
     :dispatch [::load-email-layout api-client]}))

(rf/reg-event-db
  ::save-email-layout-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:templates :email-layout :saving?] false)
        (assoc-in [:templates :email-layout :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to save email layout")))))

(rf/reg-event-db
  ::load-templates-success
  (fn [db [_ {:keys [org user]}]]
    (-> db
        (assoc-in [:templates :loading?] false)
        (assoc-in [:templates :error] nil)
        (assoc-in [:templates :org] (vec org))
        (assoc-in [:templates :user] (vec user)))))

(rf/reg-event-db
  ::load-templates-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:templates :loading?] false)
        (assoc-in [:templates :error] error))))

(rf/reg-event-fx
  ::save-template
  (fn [{:keys [db]} [_ api-client template]]
    {:db (assoc-in db [:templates :saving?] true)
     ::save-template-fx {:api-client api-client
                         :template template
                         :on-success [::save-template-success api-client]
                         :on-failure [::save-template-failure]}}))

(rf/reg-event-fx
  ::save-template-success
  (fn [{:keys [db]} [_ api-client _result]]
    {:db (-> db
             (assoc-in [:templates :saving?] false)
             (assoc-in [:templates :editor :open?] false)
             (assoc-in [:templates :editor :draft] nil))
     :dispatch [::load-templates api-client]}))

(rf/reg-event-db
  ::save-template-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:templates :saving?] false)
        (assoc-in [:templates :editor :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to save template")))))

(rf/reg-event-fx
  ::delete-template
  (fn [{:keys [_db]} [_ api-client template-id]]
    {::delete-template-fx {:api-client api-client
                           :template-id template-id
                           :on-success [::load-templates api-client]
                           :on-failure [::load-templates-failure]}}))

;; --- Editor draft ---

(rf/reg-event-db
  ::open-editor
  (fn [db [_ template]]
    (-> db
        (assoc-in [:templates :editor :open?] true)
        (assoc-in [:templates :editor :draft]
                  (or template
                      {:name ""
                       :channel :email
                       :subject ""
                       :body ""
                       :owner-scope :user})))))

(rf/reg-event-db
  ::close-editor
  (fn [db _]
    (-> db
        (assoc-in [:templates :editor :open?] false)
        (assoc-in [:templates :editor :draft] nil)
        (assoc-in [:templates :editor :error] nil))))

(rf/reg-event-db
  ::set-draft-field
  (fn [db [_ k v]]
    (assoc-in db [:templates :editor :draft k] v)))

;; =============================================================================
;; Subs
;; =============================================================================

(rf/reg-sub
  ::loading?
  (fn [db _]
    (boolean (get-in db [:templates :loading?]))))

(rf/reg-sub
  ::org
  (fn [db _]
    (or (get-in db [:templates :org]) [])))

(rf/reg-sub
  ::user-snippets
  (fn [db _]
    (or (get-in db [:templates :user]) [])))

(rf/reg-sub
  ::templates-for-channel
  (fn [db [_ channel]]
    (let [match? #(= channel (:channel %))]
      {:org (vec (filter match? (or (get-in db [:templates :org]) [])))
       :user (vec (filter match? (or (get-in db [:templates :user]) [])))})))

(rf/reg-sub
  ::editor-open?
  (fn [db _]
    (boolean (get-in db [:templates :editor :open?]))))

(rf/reg-sub
  ::editor-draft
  (fn [db _]
    (get-in db [:templates :editor :draft])))

(rf/reg-sub
  ::editor-error
  (fn [db _]
    (get-in db [:templates :editor :error])))

(rf/reg-sub
  ::saving?
  (fn [db _]
    (boolean (get-in db [:templates :saving?]))))

(rf/reg-sub
  ::email-layout
  (fn [db _]
    (get-in db [:templates :email-layout :layout])))

(rf/reg-sub
  ::email-layout-draft
  (fn [db _]
    (or (get-in db [:templates :email-layout :draft-html]) "")))

(rf/reg-sub
  ::email-layout-loading?
  (fn [db _]
    (boolean (get-in db [:templates :email-layout :loading?]))))

(rf/reg-sub
  ::email-layout-saving?
  (fn [db _]
    (boolean (get-in db [:templates :email-layout :saving?]))))

(rf/reg-sub
  ::email-layout-error
  (fn [db _]
    (get-in db [:templates :email-layout :error])))
