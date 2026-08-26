(ns store.crm.student.subs
  "Student detail page subscriptions"
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

;; Current student ID
(rf/reg-sub
  ::current-student-id
  (fn [db _]
    (get-in db [:advisor :student-detail :current-student-id])))

;; Student data from normalized cache
(rf/reg-sub
  ::student
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])]
      (get-in db [:advisor :students-by-id student-id]))))

;; Loading and error state
(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :student-detail :loading?])))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :student-detail :error])))

;; Color Profile Editing
(rf/reg-sub
  ::editing-color-profile?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :color-profile?])))

(rf/reg-sub
  ::color-profile-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :color-profile-value])))

;; Involvement Score Editing
(rf/reg-sub
  ::editing-involvement-score?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :involvement-score?])))

(rf/reg-sub
  ::involvement-score-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :involvement-score-value])))

;; ACT Score Editing
(rf/reg-sub
  ::editing-act-score?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :act-score?])))

(rf/reg-sub
  ::act-score-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :act-score-value])))

;; Unweighted GPA Editing
(rf/reg-sub
  ::editing-unweighted-gpa?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :unweighted-gpa?])))

(rf/reg-sub
  ::unweighted-gpa-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :unweighted-gpa-value])))

;; Weighted Core GPA Editing
(rf/reg-sub
  ::editing-weighted-core-gpa?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :weighted-core-gpa?])))

(rf/reg-sub
  ::weighted-core-gpa-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :weighted-core-gpa-value])))

;; TOPS Award Editing
(rf/reg-sub
  ::editing-tops-award?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :tops-award?])))

(rf/reg-sub
  ::tops-award-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :tops-award-value])))

;; WorkKeys Score Editing
(rf/reg-sub
  ::editing-work-keys-score?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :work-keys-score?])))

(rf/reg-sub
  ::work-keys-score-value
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :work-keys-score-value])))

;; Common editing state
(rf/reg-sub
  ::saving?
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :saving?])))

(rf/reg-sub
  ::edit-error
  (fn [db _]
    (get-in db [:advisor :student-detail :editing :error])))

;; Recommendation regeneration
(rf/reg-sub
  ::recommendation-status
  :<- [::student]
  (fn [student _]
    (:recommendation-status student)))

;; Now reads from server state via recommendation-status
(rf/reg-sub
  ::regenerating-recommendations?
  :<- [::recommendation-status]
  (fn [rec-status _]
    (:generation-in-progress? rec-status)))

(rf/reg-sub
  ::generation-started-at
  :<- [::recommendation-status]
  (fn [rec-status _]
    (:generation-started-at rec-status)))

;; =============================================================================
;; Raw CRM field-values (dynamic fields not mapped to :student/* keys)
;; =============================================================================

(rf/reg-sub
  ::field-values
  :<- [::student]
  (fn [student _]
    (:student/field-values student)))

;; =============================================================================
;; Field definitions / options (for dynamic select options)
;; =============================================================================

(rf/reg-sub
  ::field-definitions
  (fn [db _]
    (get-in db [:crm :field-definitions])))

(rf/reg-sub
  ::field-options
  :<- [::field-definitions]
  (fn [field-defs [_ field-slug]]
    (when-let [field-def (get field-defs field-slug)]
      (let [options (:options field-def)]
        ;; Normalize options to [{:value x :label "Display"}] format
        (when (seq options)
          (mapv (fn [opt]
                  (cond
                    ;; keyword_enum - keep as keyword
                    (keyword? opt)
                    {:value opt
                     :label (-> (name opt)
                                (str/replace "-" " ")
                                (str/split #" ")
                                (->> (map str/capitalize)
                                     (str/join " ")))}
                    ;; string options
                    (string? opt)
                    {:value opt :label opt}
                    ;; already a map
                    (map? opt)
                    opt
                    :else
                    {:value opt :label (str opt)}))
                options))))))

(rf/reg-sub
  ::contact-type-loading?
  (fn [db _]
    (get-in db [:advisor :student-detail :contact-type-loading?])))

;; =============================================================================
;; Per-field saving and error state
;; =============================================================================

(rf/reg-sub
  ::field-saving?
  (fn [db [_ field-slug]]
    (get-in db [:advisor :student-detail :field-saving field-slug])))

(rf/reg-sub
  ::field-error
  (fn [db [_ field-slug]]
    (get-in db [:advisor :student-detail :field-errors field-slug])))

;; =============================================================================
;; Communication Subscriptions
;; =============================================================================

(rf/reg-sub
  ::communications
  (fn [db _]
    (get-in db [:advisor :student-detail :communications])))

(rf/reg-sub
  ::communications-total
  (fn [db _]
    (get-in db [:advisor :student-detail :communications-total])))

(rf/reg-sub
  ::communications-loading?
  (fn [db _]
    (get-in db [:advisor :student-detail :communications-loading?])))

(rf/reg-sub
  ::communications-error
  (fn [db _]
    (get-in db [:advisor :student-detail :communications-error])))

(rf/reg-sub
  ::communication-modal-open?
  (fn [db _]
    (get-in db [:advisor :student-detail :communication-modal :open?])))

(rf/reg-sub
  ::communication-modal-form
  (fn [db _]
    (get-in db [:advisor :student-detail :communication-modal :form])))

(rf/reg-sub
  ::communication-modal-saving?
  (fn [db _]
    (get-in db [:advisor :student-detail :communication-modal :saving?])))

(rf/reg-sub
  ::communication-modal-error
  (fn [db _]
    (get-in db [:advisor :student-detail :communication-modal :error])))

(rf/reg-sub
  ::communication-modal-mode
  (fn [db _]
    (get-in db [:advisor :student-detail :communication-modal :mode])))

(rf/reg-sub
  ::deleting-communication-id
  (fn [db _]
    (get-in db [:advisor :student-detail :deleting-communication-id])))

;; =============================================================================
;; Transcript Analysis Subscriptions
;; =============================================================================

(rf/reg-sub
  ::transcript-analysis-applying?
  (fn [db _]
    (get-in db [:advisor :student-detail :transcript-analysis :applying?])))

(rf/reg-sub
  ::transcript-analysis-error
  (fn [db _]
    (get-in db [:advisor :student-detail :transcript-analysis :error])))

;; =============================================================================
;; Add-guardian sheet
;; =============================================================================

(rf/reg-sub
  ::add-guardian-sheet-open?
  (fn [db _]
    (boolean (get-in db [:advisor :student-detail :add-guardian-sheet :open?]))))

(rf/reg-sub
  ::add-guardian-sheet-form
  (fn [db _]
    (get-in db [:advisor :student-detail :add-guardian-sheet :form] {})))

(rf/reg-sub
  ::add-guardian-sheet-found
  (fn [db _]
    (get-in db [:advisor :student-detail :add-guardian-sheet :found])))

(rf/reg-sub
  ::add-guardian-sheet-saving?
  (fn [db _]
    (boolean (get-in db [:advisor :student-detail :add-guardian-sheet :saving?]))))

(rf/reg-sub
  ::add-guardian-sheet-error
  (fn [db _]
    (get-in db [:advisor :student-detail :add-guardian-sheet :error])))

;; =============================================================================
;; Add-sibling sheet
;; =============================================================================

(rf/reg-sub
  ::add-sibling-sheet-open?
  (fn [db _]
    (boolean (get-in db [:advisor :student-detail :add-sibling-sheet :open?]))))

(rf/reg-sub
  ::add-sibling-sheet-form
  (fn [db _]
    (get-in db [:advisor :student-detail :add-sibling-sheet :form] {})))

(rf/reg-sub
  ::add-sibling-sheet-found
  (fn [db _]
    (get-in db [:advisor :student-detail :add-sibling-sheet :found])))

(rf/reg-sub
  ::add-sibling-sheet-saving?
  (fn [db _]
    (boolean (get-in db [:advisor :student-detail :add-sibling-sheet :saving?]))))

(rf/reg-sub
  ::add-sibling-sheet-error
  (fn [db _]
    (get-in db [:advisor :student-detail :add-sibling-sheet :error])))
