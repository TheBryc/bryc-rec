(ns utils.csv
  "Client-side CSV generation and download helpers."
  (:require [clojure.string :as str]))

(defn escape-field
  "Escape a single CSV field per RFC 4180."
  [v]
  (let [s (if (nil? v) "" (str v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(def ^:private student-accessor-keys
  ["student/name" "student/school" "student/email" "student/phone"])

(def ^:private student-header
  "Name,School,Email,Phone")

(defn students->csv
  "Build a CSV string from a JS array of student records (as passed to the DataTable)."
  [students-js]
  (let [rows (.map students-js
                   (fn [s]
                     (->> student-accessor-keys
                          (map #(escape-field (aget s %)))
                          (str/join ","))))]
    (str student-header "\n" (str/join "\n" rows))))

(defn today-iso
  "Local-date YYYY-MM-DD for filename stamping."
  []
  (let [d (js/Date.)
        pad (fn [n] (if (< n 10) (str "0" n) (str n)))]
    (str (.getFullYear d) "-" (pad (inc (.getMonth d))) "-" (pad (.getDate d)))))

(defn download!
  "Trigger a browser download of `content` as a file named `filename`."
  [filename content]
  (let [blob (js/Blob. #js [content] #js {:type "text/csv;charset=utf-8"})
        url (js/URL.createObjectURL blob)
        a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.appendChild js/document.body a)
    (.click a)
    (.removeChild js/document.body a)
    (js/URL.revokeObjectURL url)))
