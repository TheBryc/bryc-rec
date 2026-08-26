(ns components.shared.formatting
  "Shared formatting utilities for display values."
  (:require [clojure.string :as str]))

(defn format-keyword
  "Converts a keyword to Title Case string. e.g. :tops-award → \"Tops Award\""
  [kw]
  (when kw
    (-> (name kw)
        (str/replace "-" " ")
        (str/replace "_" " ")
        (str/split #" ")
        (->> (map str/capitalize)
             (str/join " ")))))

(defn format-phone
  "Formats a phone number for display. e.g. \"5045551234\" → \"(504) 555-1234\""
  [phone]
  (when phone
    (let [digits (str/replace phone #"\D" "")]
      (cond
        (= (count digits) 10)
        (str "(" (subs digits 0 3) ") " (subs digits 3 6) "-" (subs digits 6 10))
        (= (count digits) 11)
        (str "+1 (" (subs digits 1 4) ") " (subs digits 4 7) "-" (subs digits 7 11))
        :else phone))))

(defn format-relative-time
  "Formats a date/timestamp to relative time. e.g. \"2h ago\", \"Yesterday\", \"3 days ago\""
  [value]
  (when value
    (try
      (let [now (js/Date.)
            then (if (instance? js/Date value) value (js/Date. value))
            diff-ms (- (.getTime now) (.getTime then))
            diff-min (/ diff-ms 60000)
            diff-hours (/ diff-ms 3600000)
            diff-days (/ diff-hours 24)]
        (cond
          (js/isNaN (.getTime then)) (str value)
          (< diff-min 1) "Just now"
          (< diff-min 60) (str (Math/floor diff-min) "m ago")
          (< diff-hours 24) (str (Math/floor diff-hours) "h ago")
          (< diff-days 2) "Yesterday"
          (< diff-days 7) (str (Math/floor diff-days) " days ago")
          (< diff-days 30) (str (Math/floor (/ diff-days 7)) "w ago")
          (< diff-days 365) (str (Math/floor (/ diff-days 30)) "mo ago")
          :else (str (Math/floor (/ diff-days 365)) "y ago")))
      (catch js/Error _e (str value)))))

(defn days-since
  "Returns integer number of days since the given date/timestamp. Returns nil if invalid."
  [value]
  (when value
    (try
      (let [then (if (instance? js/Date value) value (js/Date. value))
            now (js/Date.)
            diff-ms (- (.getTime now) (.getTime then))]
        (when-not (js/isNaN (.getTime then))
          (Math/floor (/ diff-ms 86400000))))
      (catch js/Error _e nil))))

(defn normalize-phone
  "Strips non-digit characters from a phone number for backend storage."
  [phone]
  (when phone
    (let [digits (str/replace phone #"\D" "")]
      (if (and (= (count digits) 11) (str/starts-with? digits "1"))
        (subs digits 1)
        digits))))

(defn validate-email
  "Returns nil for valid or empty email; returns an error string otherwise.
   Mirrors the relaxed pattern used by the intake form-wizard."
  [email]
  (when (and email (not (str/blank? email)))
    (when-not (re-matches #"^[^@\s]+@[^@\s]+\.[^@\s]+$" (str/trim email))
      "Please enter a valid email address")))

(defn validate-phone
  "Returns nil for valid or empty phone; returns an error string otherwise.
   Accepts any input that contains exactly 10 digits (or 11 starting with 1)
   after non-digit characters are stripped."
  [phone]
  (when (and phone (not (str/blank? phone)))
    (let [digits (str/replace phone #"\D" "")
          n (count digits)]
      (when-not (or (= n 10)
                    (and (= n 11) (str/starts-with? digits "1")))
        "Please enter a 10-digit phone number"))))
