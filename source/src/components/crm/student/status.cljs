(ns components.crm.student.status
  "Compatibility helpers for BRYC status values.
   Stored values are moving from keyword enums to canonical display strings;
   these helpers support both shapes during and after reconciliation."
  (:require [clojure.string :as str]))

(def status-label->key
  {"Fellow" :fellow
   "Vulnerable Fellow" :vulnerable-fellow
   "Advisee" :advisee
   "In-School Student" :in-school
   "College Fellow" :college-fellow
   "Alumni" :alumni
   "Prospect" :prospect
   "Applicant" :applicant
   "Waitlist" :waitlist
   "Dismissed (Self-Dismissed)" :dismissed
   "Dismissed (Over-Committed)" :dismissed
   "Dismissed (0% Attendance)" :dismissed
   "Dismissed (Low Attendance)" :dismissed
   "Dismissed (Other)" :dismissed
   "Denied" :denied})

(def key->status-label
  {:fellow "Fellow"
   :vulnerable-fellow "Vulnerable Fellow"
   :advisee "Advisee"
   :in-school "In-School Student"
   :college-fellow "College Fellow"
   :alumni "Alumni"
   :prospect "Prospect"
   :applicant "Applicant"
   :waitlist "Waitlist"
   :dismissed "Dismissed"
   :denied "Denied"})

(defn status-key [status]
  (cond
    (keyword? status) status
    (string? status) (or (get status-label->key status)
                         (some-> status
                                 str/lower-case
                                 (str/replace #"[^a-z0-9]+" "-")
                                 (str/replace #"(^-|-$)" "")
                                 keyword))
    :else nil))

(defn status-in? [statuses status]
  (contains? statuses (status-key status)))

(defn fellow-profile? [status]
  (status-in? #{:fellow :vulnerable-fellow} status))

(defn fellow? [status]
  (= :fellow (status-key status)))

(defn advisee? [status]
  (= :advisee (status-key status)))

(defn alumni? [status]
  (status-in? #{:alumni :college-fellow} status))

(defn status-label [status]
  (or (get key->status-label (status-key status))
      (when (some? status) (str status))))
