(ns store.fellows.subs
  "Fellows dashboard re-frame subscriptions"
  (:require [re-frame.core :as rf]))

;; Raw data subscriptions
(rf/reg-sub
  ::students-raw
  (fn [db _]
    (get-in db [:fellows :dashboard :students])))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:fellows :dashboard :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:fellows :dashboard :error])))

(rf/reg-sub
  ::student-count
  :<- [::students-raw]
  (fn [students _]
    (count students)))

(rf/reg-sub
  ::showing-all?
  (fn [db _]
    (get-in db [:fellows :dashboard :showing-all?] false)))

(rf/reg-sub
  ::staff-contact-id
  (fn [db _]
    (get-in db [:fellows :dashboard :staff-contact-id])))

;; Selected school filter
(rf/reg-sub
  ::selected-school-filter
  (fn [db _]
    (get-in db [:fellows :dashboard :school-filter])))

;; Selected PM filter
(rf/reg-sub
  ::selected-pm-filter
  (fn [db _]
    (get-in db [:fellows :dashboard :pm-filter])))

;; Unique sorted school names for filter dropdown
(rf/reg-sub
  ::schools
  :<- [::students-raw]
  (fn [students _]
    (->> students
         (map :student/school)
         (remove nil?)
         distinct
         sort
         vec)))

;; Program managers list (for assignment dropdown)
(rf/reg-sub
  ::program-managers
  (fn [db _]
    (get-in db [:fellows :dashboard :program-managers] [])))

;; Count of unassigned students
(rf/reg-sub
  ::unassigned-count
  :<- [::students-raw]
  (fn [students _]
    (count (filter #(nil? (:student/assigned-pm-id %)) students))))

;; Filtered students (by school and/or PM)
(rf/reg-sub
  ::students
  :<- [::students-raw]
  :<- [::selected-school-filter]
  :<- [::selected-pm-filter]
  (fn [[students school-filter pm-filter] _]
    (cond-> students
      school-filter
      (->> (filterv #(= (:student/school %) school-filter)))

      pm-filter
      (->> (filterv #(= (str (:student/assigned-pm-id %)) (str pm-filter)))))))

;; Selection state for bulk operations
(rf/reg-sub
  ::selected-student-ids
  (fn [db _]
    (get-in db [:fellows :dashboard :selected-student-ids] #{})))

(rf/reg-sub
  ::selected-count
  :<- [::selected-student-ids]
  (fn [selected-ids _]
    (count selected-ids)))

(rf/reg-sub
  ::bulk-assigning?
  (fn [db _]
    (get-in db [:fellows :dashboard :bulk-assigning?] false)))
