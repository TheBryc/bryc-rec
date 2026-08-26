(ns components.crm.student.testing-history
  "Custom editor and pure helpers for the Fellow Testing History profile tab."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/label" :refer [Label]]
            ["/gen/shadcn/components/ui/select" :as select]
            ["lucide-react" :refer [Plus Pencil Trash2]]))

(def test-names
  ["ACT" "PSAT / NMSQT" "SAT" "TOEFL iBT" "AP Exam" "WorkKeys" "ASVAB"])

(def test-fields
  {"ACT" [{:key :composite-score :label "Composite Score" :type :number :min 1 :max 36 :required true}
          {:key :english-score :label "English Score" :type :number :min 1 :max 36}
          {:key :math-score :label "Math Score" :type :number :min 1 :max 36}
          {:key :reading-score :label "Reading Score" :type :number :min 1 :max 36}
          {:key :science-score :label "Science Score" :type :number :min 1 :max 36}
          {:key :writing-score :label "Writing Score" :type :number :min 2 :max 12}]
   "PSAT / NMSQT" [{:key :total-score :label "Total Score" :type :number :required true}
                   {:key :reading-writing-score :label "Reading & Writing Score" :type :number}
                   {:key :math-score :label "Math Score" :type :number}
                   {:key :selection-index :label "Selection Index" :type :number}]
   "SAT" [{:key :total-score :label "Total Score" :type :number :required true}
          {:key :reading-writing-score :label "Reading & Writing Score" :type :number}
          {:key :math-score :label "Math Score" :type :number}]
   "TOEFL iBT" [{:key :overall-score :label "Overall Score" :type :number :required true}
                {:key :reading-score :label "Reading Score" :type :number}
                {:key :listening-score :label "Listening Score" :type :number}
                {:key :speaking-score :label "Speaking Score" :type :number}
                {:key :writing-score :label "Writing Score" :type :number}]
   "AP Exam" [{:key :score :label "Score" :type :number :min 1 :max 5 :required true}]
   "WorkKeys" [{:key :ncrc-credential :label "NCRC Credential" :type :select :options ["Bronze" "Silver" "Gold" "Platinum"] :required true}
               {:key :applied-math-level :label "Applied Math Level" :type :number :min 3 :max 7}
               {:key :graphic-literacy-level :label "Graphic Literacy Level" :type :number :min 3 :max 7}
               {:key :workplace-documents-level :label "Workplace Documents Level" :type :number :min 3 :max 7}]
   "ASVAB" [{:key :afqt-score :label "AFQT Score (%)" :type :number :required true}
            {:key :general-science-score :label "General Science Score" :type :number}
            {:key :arithmetic-reasoning-score :label "Arithmetic Reasoning Score" :type :number}
            {:key :word-knowledge-score :label "Word Knowledge Score" :type :number}
            {:key :paragraph-comprehension-score :label "Paragraph Comprehension Score" :type :number}
            {:key :mathematics-knowledge-score :label "Mathematics Knowledge Score" :type :number}
            {:key :electronics-information-score :label "Electronics Information Score" :type :number}
            {:key :auto-information-score :label "Auto Information Score" :type :number}
            {:key :shop-information-score :label "Shop Information Score" :type :number}
            {:key :mechanical-comprehension-score :label "Mechanical Comprehension Score" :type :number}
            {:key :assembling-objects-score :label "Assembling Objects Score" :type :number}]})

(defn parse-number [value]
  (cond
    (number? value) value
    (string? value) (let [n (js/parseFloat value)]
                      (when-not (js/isNaN n) n))
    :else nil))

(defn normalize-test-entry [entry]
  (let [test-name (or (:test-name entry)
                      (when (or (:composite-score entry) (:score entry)) "ACT")
                      "ACT")
        composite (or (:composite-score entry)
                      (when (= test-name "ACT") (:score entry)))]
    (cond-> (assoc entry
                   :test-name test-name
                   :test-date (or (:test-date entry) (:date entry)))
      (some? composite) (assoc :composite-score (parse-number composite)))))

(defn normalized-history [history]
  (mapv normalize-test-entry (or history [])))

(defn act-records [history]
  (filter #(= "ACT" (:test-name %)) (normalized-history history)))

(defn highest-act-score [history]
  (when-let [scores (seq (keep #(parse-number (:composite-score %)) (act-records history)))]
    (apply max scores)))

(defn entering-act-score [history fallback-score]
  (or (parse-number fallback-score)
      (some->> (act-records history)
               (sort-by #(or (:test-date %) "9999-99-99"))
               first
               :composite-score
               parse-number)))

(defn points-gained [history fallback-score]
  (let [highest (highest-act-score history)
        entering (entering-act-score history fallback-score)]
    (when (and highest entering)
      (- highest entering))))

(defn tops-eligibility-label [highest-score]
  (cond
    (nil? highest-score) "—"
    (>= highest-score 27) "TOPS Honors"
    (>= highest-score 23) "TOPS Performance"
    (>= highest-score 20) "TOPS Opportunity"
    :else "None"))

(defn required-test-entry? [entry]
  (let [normalized (normalize-test-entry entry)
        fields (get test-fields (:test-name normalized))]
    (every? (fn [{:keys [key required]}]
              (or (not required)
                  (let [v (get normalized key)]
                    (and (some? v)
                         (if (string? v) (seq (str/trim v)) true)))))
            (cons {:key :test-date :required true}
                  (cons {:key :test-name :required true} fields)))))

(defn field-value->string [value]
  (if (some? value) (str value) ""))

(defui select-input [{:keys [value options on-change disabled? placeholder]}]
  ($ select/Select
     {:value (or value "")
      :onValueChange on-change
      :disabled disabled?}
     ($ select/SelectTrigger {:class "w-full"}
        ($ select/SelectValue {:placeholder (or placeholder "Select...")}))
     ($ select/SelectContent
        (for [option options]
          ($ select/SelectItem {:key option :value option} option)))))

(defui score-field [{:keys [field value on-change saving?]}]
  (let [{:keys [key label type options required min max]} field]
    ($ :div {:class "space-y-2"}
       ($ Label {} label (when required ($ :span {:class "ml-1 text-red-500"} "*")))
       (if (= type :select)
         ($ select-input {:value value
                          :options options
                          :disabled? saving?
                          :on-change #(on-change key %)})
         ($ Input {:type "number"
                   :value (field-value->string value)
                   :min min
                   :max max
                   :disabled saving?
                   :on-change #(let [v (.. % -target -value)]
                                 (on-change key (when (seq v) (js/parseFloat v))))})))))

(defui test-entry-form [{:keys [item on-save on-cancel saving?]}]
  (let [[form-data set-form-data!] (use-state (normalize-test-entry item))
        test-name (or (:test-name form-data) "ACT")
        update-field! (fn [key value]
                        (set-form-data! #(assoc % key value)))
        save! #(when (required-test-entry? form-data)
                 (on-save (normalize-test-entry form-data)))]
    ($ :div {:class "space-y-4"}
       ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 gap-4"}
          ($ :div {:class "space-y-2"}
             ($ Label {} "Test Date" ($ :span {:class "ml-1 text-red-500"} "*"))
             ($ Input {:type "date"
                       :value (or (:test-date form-data) "")
                       :disabled saving?
                       :on-change #(update-field! :test-date (.. % -target -value))}))
          ($ :div {:class "space-y-2"}
             ($ Label {} "Test Name" ($ :span {:class "ml-1 text-red-500"} "*"))
             ($ select-input {:value test-name
                              :options test-names
                              :disabled? saving?
                              :on-change #(update-field! :test-name %)})))
       ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 gap-4"}
          (for [field (get test-fields test-name)]
            ($ score-field {:key (:key field)
                            :field field
                            :value (get form-data (:key field))
                            :saving? saving?
                            :on-change update-field!})))
       ($ :div {:class "flex justify-end gap-2 pt-4"}
          ($ button/Button {:variant "outline" :on-click on-cancel :disabled saving?} "Cancel")
          ($ button/Button {:on-click save! :disabled (or saving? (not (required-test-entry? form-data)))}
             (if saving? "Saving..." "Save"))))))

(defn test-entry-title [item]
  (let [item (normalize-test-entry item)]
    (str (:test-name item) (when-let [date (:test-date item)] (str " • " date)))))

(defn test-entry-summary [item]
  (let [item (normalize-test-entry item)
        parts (case (:test-name item)
                "ACT" [(when-let [v (:composite-score item)] (str "Composite " v))]
                "PSAT / NMSQT" [(when-let [v (:total-score item)] (str "Total " v))]
                "SAT" [(when-let [v (:total-score item)] (str "Total " v))]
                "TOEFL iBT" [(when-let [v (:overall-score item)] (str "Overall " v))]
                "AP Exam" [(when-let [v (:score item)] (str "Score " v))]
                "WorkKeys" [(when-let [v (:ncrc-credential item)] (str "NCRC " v))]
                "ASVAB" [(when-let [v (:afqt-score item)] (str "AFQT " v "%"))]
                [])]
    (or (some identity parts) "Scores not entered")))

(defui testing-history-editor [{:keys [value on-save saving?]}]
  (let [[dialog-open? set-dialog-open!] (use-state false)
        [editing-index set-editing-index!] (use-state nil)
        items (normalized-history value)
        open-add! (fn []
                    (set-editing-index! nil)
                    (set-dialog-open! true))
        open-edit! (fn [idx]
                     (set-editing-index! idx)
                     (set-dialog-open! true))
        close! #(set-dialog-open! false)
        save-item! (fn [item]
                     (let [next-items (if (some? editing-index)
                                        (assoc (vec items) editing-index item)
                                        (conj (vec items) item))]
                       (on-save next-items)
                       (set-dialog-open! false)))
        delete-item! (fn [idx]
                       (when (js/confirm "Delete this test record?")
                         (on-save (vec (concat (subvec (vec items) 0 idx)
                                               (subvec (vec items) (inc idx)))))))]
    ($ :div {:class "space-y-3"}
       (if (seq items)
         (for [[idx item] (map-indexed vector items)]
           ($ :div {:key idx :class "group flex items-center justify-between gap-3 border-b py-3 last:border-b-0"}
              ($ :div {:class "min-w-0"}
                 ($ :p {:class "font-medium truncate"} (test-entry-title item))
                 ($ :p {:class "text-sm text-muted-foreground"} (test-entry-summary item)))
              ($ :div {:class "flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100"}
                 ($ button/Button {:variant "ghost" :size "icon" :class "h-8 w-8" :disabled saving? :on-click #(open-edit! idx)}
                    ($ Pencil {:class "h-4 w-4"}))
                 ($ button/Button {:variant "ghost" :size "icon" :class "h-8 w-8 text-destructive hover:text-destructive" :disabled saving? :on-click #(delete-item! idx)}
                    ($ Trash2 {:class "h-4 w-4"})))))
         ($ :p {:class "text-sm text-muted-foreground italic"} "No test scores recorded."))
       ($ button/Button {:variant "outline" :size "sm" :disabled saving? :on-click open-add!}
          ($ Plus {:class "h-4 w-4 mr-2"})
          "Add Test")
       ($ dialog/Dialog {:open dialog-open? :onOpenChange set-dialog-open!}
          ($ dialog/DialogContent {:class "sm:max-w-2xl"}
             ($ dialog/DialogHeader
                ($ dialog/DialogTitle (if (some? editing-index) "Edit Test" "Add Test")))
             ($ test-entry-form
                {:item (when (some? editing-index) (get items editing-index))
                 :on-save save-item!
                 :on-cancel close!
                 :saving? saving?}))))))
