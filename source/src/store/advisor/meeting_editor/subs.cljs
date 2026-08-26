(ns store.advisor.meeting-editor.subs
  "Meeting editor subscriptions"
  (:require [re-frame.core :as rf]))

;; Current meeting from normalized cache
(rf/reg-sub
  ::current-meeting
  (fn [db _]
    (let [meeting-id (get-in db [:advisor :meeting-editor :current-meeting-id])]
      (get-in db [:advisor :meetings-by-id meeting-id]))))

;; Current notes
(rf/reg-sub
  ::notes
  (fn [db _]
    (get-in db [:advisor :meeting-editor :notes])))

;; Notes loaded flag
(rf/reg-sub
  ::notes-loaded?
  (fn [db _]
    (get-in db [:advisor :meeting-editor :notes-loaded?] false)))

;; Saving state
(rf/reg-sub
  ::saving?
  (fn [db _]
    (get-in db [:advisor :meeting-editor :saving?] false)))

;; Has unsaved changes
(rf/reg-sub
  ::save-pending?
  (fn [db _]
    (get-in db [:advisor :meeting-editor :save-pending?] false)))

;; Last saved timestamp
(rf/reg-sub
  ::last-saved
  (fn [db _]
    (get-in db [:advisor :meeting-editor :last-saved])))

;; Error state
(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :meeting-editor :error])))

;; Is meeting completed (read-only mode)?
(rf/reg-sub
  ::is-completed?
  :<- [::current-meeting]
  (fn [meeting _]
    (= :completed (:meeting/status meeting))))

;; =============================================================================
;; Student Credentials (for inline editing during intake meeting)
;; =============================================================================

(rf/reg-sub
  ::credentials
  (fn [db _]
    (get-in db [:advisor :meeting-editor :credentials])))

(rf/reg-sub
  ::credentials-loading?
  (fn [db _]
    (get-in db [:advisor :meeting-editor :credentials-loading?] false)))
