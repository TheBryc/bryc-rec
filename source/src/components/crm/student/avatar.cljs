(ns components.crm.student.avatar
  "Student avatar component with initials and consistent color generation."
  (:require [uix.core :as uix :refer [defui $]]
            [clojure.string :as str]))

;; Color palette for avatar backgrounds (accessible, professional colors)
(def avatar-colors
  ["bg-blue-500"
   "bg-emerald-500"
   "bg-violet-500"
   "bg-amber-500"
   "bg-rose-500"
   "bg-cyan-500"
   "bg-indigo-500"
   "bg-teal-500"
   "bg-orange-500"
   "bg-pink-500"
   "bg-sky-500"
   "bg-lime-500"])

(defn- name-hash
  "Simple string hash function for consistent color selection."
  [s]
  (reduce (fn [hash char]
            (let [code (.charCodeAt char 0)]
              (bit-or 0 (+ (bit-shift-left hash 5) (- hash) code))))
          0
          (or s "")))

(defn get-initials
  "Extract initials from a name (first letter of first and last name)."
  [name]
  (when (and name (not= name ""))
    (let [parts (str/split (str/trim name) #"\s+")
          first-initial (first (first parts))
          last-initial (when (> (count parts) 1) (first (last parts)))]
      (str/upper-case
        (str first-initial (or last-initial ""))))))

(defn get-color-for-name
  "Get a consistent color class for a name."
  [name]
  (let [hash (Math/abs (name-hash name))
        index (mod hash (count avatar-colors))]
    (nth avatar-colors index)))

(defui student-avatar
  "Avatar component displaying student initials with consistent color.
   Props:
   - name: student's full name
   - size: :sm (32px), :md (48px), :lg (64px), :xl (96px) - default :lg
   - class: additional CSS classes"
  [{:keys [name size class]}]
  (let [initials (get-initials name)
        bg-color (get-color-for-name name)
        size-class (case size
                     :sm "w-8 h-8 text-xs"
                     :md "w-12 h-12 text-sm"
                     :lg "w-16 h-16 text-xl"
                     :xl "w-24 h-24 text-3xl"
                     "w-16 h-16 text-xl")]
    ($ :div
       {:class (str "rounded-full flex items-center justify-center "
                    "text-white font-semibold select-none "
                    bg-color " " size-class " " class)}
       (or initials "?"))))
