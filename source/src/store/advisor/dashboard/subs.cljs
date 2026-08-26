(ns store.advisor.dashboard.subs
  "Dashboard-related re-frame subscriptions"
  (:require [re-frame.core :as rf]))

;; Raw data subscriptions
(rf/reg-sub
  ::students-raw
  (fn [db _]
    (get-in db [:advisor :dashboard :students])))

(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :dashboard :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :dashboard :error])))

(rf/reg-sub
  ::student-count
  :<- [::students-raw]
  (fn [students _]
    (count students)))

(rf/reg-sub
  ::showing-all?
  (fn [db _]
    (get-in db [:advisor :dashboard :showing-all?] false)))

(rf/reg-sub
  ::advisor-contact-id
  (fn [db _]
    (get-in db [:advisor :dashboard :advisor-contact-id])))

;; Status counts for summary cards
(rf/reg-sub
  ::status-counts
  :<- [::students-raw]
  (fn [students _]
    (frequencies (map :student/advisor-status students))))

;; Selected status filter
(rf/reg-sub
  ::selected-status-filter
  (fn [db _]
    (get-in db [:advisor :dashboard :status-filter])))

;; Selected school filter
(rf/reg-sub
  ::selected-school-filter
  (fn [db _]
    (get-in db [:advisor :dashboard :school-filter])))

;; Selected advisor filter
(rf/reg-sub
  ::selected-advisor-filter
  (fn [db _]
    (get-in db [:advisor :dashboard :advisor-filter])))

;; Selected graduation year filter
(rf/reg-sub
  ::selected-graduation-year-filter
  (fn [db _]
    (get-in db [:advisor :dashboard :graduation-year-filter])))

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

;; Unique sorted graduation years for filter dropdown
(rf/reg-sub
  ::graduation-years
  :<- [::students-raw]
  (fn [students _]
    (->> students
         (map :student/graduation-year)
         (remove nil?)
         distinct
         sort
         vec)))

;; Advisors list (for assignment dropdown)
(rf/reg-sub
  ::advisors
  (fn [db _]
    (get-in db [:advisor :dashboard :advisors] [])))

;; Count of unassigned students
(rf/reg-sub
  ::unassigned-count
  :<- [::students-raw]
  (fn [students _]
    (count (filter #(nil? (:student/assigned-advisor-id %)) students))))

;; Filtered students (by status, school, graduation-year, and/or advisor)
(rf/reg-sub
  ::students
  :<- [::students-raw]
  :<- [::selected-status-filter]
  :<- [::selected-school-filter]
  :<- [::selected-advisor-filter]
  :<- [::selected-graduation-year-filter]
  (fn [[students status-filter school-filter advisor-filter graduation-year-filter] _]
    (cond-> students
      (= status-filter :unassigned)
      (->> (filterv #(nil? (:student/assigned-advisor-id %))))

      (and status-filter (not= status-filter :unassigned))
      (->> (filterv #(= (:student/advisor-status %) status-filter)))

      school-filter
      (->> (filterv #(= (:student/school %) school-filter)))

      graduation-year-filter
      (->> (filterv #(= (:student/graduation-year %) graduation-year-filter)))

      advisor-filter
      (->> (filterv #(= (str (:student/assigned-advisor-id %)) (str advisor-filter)))))))

;; Selection state for bulk operations
(rf/reg-sub
  ::selected-student-ids
  (fn [db _]
    (get-in db [:advisor :dashboard :selected-student-ids] #{})))

(rf/reg-sub
  ::selected-count
  :<- [::selected-student-ids]
  (fn [selected-ids _]
    (count selected-ids)))

(rf/reg-sub
  ::bulk-assigning?
  (fn [db _]
    (get-in db [:advisor :dashboard :bulk-assigning?] false)))

;; Add student modal subscriptions
(rf/reg-sub
  ::show-add-student-modal?
  (fn [db _]
    (get-in db [:advisor :dashboard :add-student-modal :show?] false)))

(rf/reg-sub
  ::add-student-submitting?
  (fn [db _]
    (get-in db [:advisor :dashboard :add-student-modal :submitting?] false)))

(rf/reg-sub
  ::add-student-error
  (fn [db _]
    (get-in db [:advisor :dashboard :add-student-modal :error])))
