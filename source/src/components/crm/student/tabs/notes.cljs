(ns components.crm.student.tabs.notes
  "Notes section — advisor summary and narrative additions."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.student.editable-field :refer [editable-textarea]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def narrative-additions-sub-schema
  {:type :vector
   :add-button-text "Add"
   :empty-message "No narrative additions yet."
   :item-label-key :text
   :item-schema
   {:type :map
    :fields [{:key :text
              :type :string
              :required true
              :label "Narrative Addition"
              :input-type "textarea"
              :rows 3
              :placeholder "Enter narrative addition..."}]}})

;; =============================================================================
;; Notes Tab
;; =============================================================================

(defui notes-tab
  "Notes tab with advisor summary and narrative additions.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ;; Summary
       ($ sub-header {:title "Summary"})
       ($ field-grid
          ($ field-cell {:label "Student Summary" :span :full}
             ($ editable-textarea
                {:value (:student/summary student)
                 :placeholder "Add student summary..."
                 :saving? saving?
                 :rows 4
                 :on-save #(rf/dispatch [::student-events/save-field "summary" % api-client])})
             ($ field-error {:field-slug "summary"})))

       ;; Narrative Additions
       ($ sub-header {:title "Narrative Additions"})
       ($ schema-vector-editor
          {:sub-schema narrative-additions-sub-schema
           :value (:student/narrative-additions student)
           :saving? saving?
           :on-save #(rf/dispatch [::student-events/save-field "narrative-additions" % api-client])})
       ($ field-error {:field-slug "narrative-additions"}))))
