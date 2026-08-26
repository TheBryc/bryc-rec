(ns store.advisor.meetings.subs
  "Advisor meetings page subscriptions"
  (:require [re-frame.core :as rf]))

;; Student summary (from screen query)
(rf/reg-sub
  ::student
  (fn [db _]
    (get-in db [:advisor :meetings :student])))

;; Current student ID
(rf/reg-sub
  ::student-id
  (fn [db _]
    (get-in db [:advisor :meetings :student-id])))

;; Current meeting (from editor screen query)
(rf/reg-sub
  ::current-meeting
  (fn [db _]
    (get-in db [:advisor :meetings :current-meeting])))

;; Hydrated meetings from normalized cache
(rf/reg-sub
  ::meetings
  (fn [db _]
    (let [meeting-ids (get-in db [:advisor :meetings :meetings-list] [])
          meetings-by-id (get-in db [:advisor :meetings-by-id] {})]
      (if (seq meeting-ids)
        (mapv #(get meetings-by-id %) meeting-ids)
        []))))

;; Loading and error state
(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :meetings :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :meetings :error])))

;; Start meeting state
(rf/reg-sub
  ::starting-meeting?
  (fn [db _]
    (get-in db [:advisor :meetings :starting-meeting?])))

(rf/reg-sub
  ::start-error
  (fn [db _]
    (get-in db [:advisor :meetings :start-error])))

(rf/reg-sub
  ::new-meeting-id
  (fn [db _]
    (get-in db [:advisor :meetings :new-meeting-id])))

;; Saving state for transcript analysis items (map of update-id -> bool)
(rf/reg-sub
  ::saving
  (fn [db _]
    (get-in db [:advisor :meetings :saving] {})))
