(ns components.crm.student.tabs.bryc-program
  "BRYC Program section — timeline and involvement tracking."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-select]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def honors-achievements-sub-schema
  {:type :vector
   :add-button-text "Add Honor/Achievement"
   :empty-message "No honors or achievements added yet."
   :item-label-key :name
   :item-schema
   {:type :map
    :fields [{:key :name
              :type :string
              :required true
              :label "Name"
              :placeholder "Enter honor/achievement..."}]}})

;; =============================================================================
;; BRYC Program Tab
;; =============================================================================

(defn pretty-contact-id [contact-id]
  (when contact-id
    (str contact-id)))

(defn contact-id-lines [contact-id]
  (when-let [display-id (pretty-contact-id contact-id)]
    (let [parts (str/split display-id #"-")]
      (if (= 5 (count parts))
        [(str/join "-" (take 3 parts))
         (str/join "-" (drop 3 parts))]
        [display-id]))))

(defui status-tab [{:keys [student field-values api-client saving?]}]
  (let [bryc-status-options (use-subscribe [::student-subs/field-options "bryc-status"])
        returning-options (use-subscribe [::student-subs/field-options "returning-or-new"])
        grade-options (use-subscribe [::student-subs/field-options "grade"])
        graduation-year-options (use-subscribe [::student-subs/field-options "graduation-year"])
        contact-id (:student/id student)
        display-id-lines (contact-id-lines contact-id)]
    ($ :<>
       ($ sub-header {:title "Status"})
       ($ field-grid
          ($ field-cell {:label "BRYC Student ID"}
             (if (seq display-id-lines)
               ($ :div {:class "py-1"}
                  ($ :span {:class "inline-flex max-w-full flex-col rounded-md border bg-muted px-2 py-1 font-mono text-xs font-medium leading-relaxed tracking-wide text-foreground"
                            :title (str contact-id)}
                     (for [line display-id-lines]
                       ($ :span {:key line :class "whitespace-nowrap"} line))))
               ($ :div {:class "py-1 text-sm text-muted-foreground"} "Contact ID missing")))
          ($ field-cell {:label "BRYC Status"}
             ($ editable-select {:value (get field-values :bryc-status)
                                 :options bryc-status-options
                                 :placeholder "Set status..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "bryc-status" % api-client])})
             ($ field-error {:field-slug "bryc-status"}))
          ($ field-cell {:label "Grade"}
             ($ editable-select {:value (get field-values :grade)
                                 :options grade-options
                                 :placeholder "Select grade"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "grade" % api-client])})
             ($ field-error {:field-slug "grade"}))
          ($ field-cell {:label "High School Graduating Year"}
             ($ editable-select {:value (get field-values :graduation-year)
                                 :options graduation-year-options
                                 :placeholder "Select year"
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "graduation-year" % api-client])})
             ($ field-error {:field-slug "graduation-year"}))
          ($ field-cell {:label "New / Returning"}
             ($ editable-select {:value (get field-values :returning-or-new)
                                 :options returning-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "returning-or-new" % api-client])})
             ($ field-error {:field-slug "returning-or-new"}))))))

(defui schedule-tab [{:keys [field-values api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "Schedule"})
     ($ field-grid
        ($ field-cell {:label "Program Day"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :program-day) "—")))
        ($ field-cell {:label "Campus"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :campus) "—")))
        ($ field-cell {:label "Room"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :room) "—")))
        ($ field-cell {:label "Transportation"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :transportation) "—"))))))

(defui people-tab [{:keys [field-values api-client saving?]}]
  ($ :<>
     ($ sub-header {:title "People"})
     ($ field-grid
        ($ field-cell {:label "Program Manager"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :program-manager) "—")))
        ($ field-cell {:label "Mentor"}
           ($ :div {:class "py-1 text-sm"} (or (get field-values :mentor) "—"))))))

(defui timeline-tab [{:keys [field-values api-client saving?]}]
  (let [year-joined-options (use-subscribe [::student-subs/field-options "year-joined"])
        grade-joined-options (use-subscribe [::student-subs/field-options "grade-joined"])
        four-year-fellow-options (use-subscribe [::student-subs/field-options "four-year-fellow"])
        five-year-fellow-options (use-subscribe [::student-subs/field-options "five-year-fellow"])
        legacy-fellow-options (use-subscribe [::student-subs/field-options "legacy-fellow"])]
    ($ :<>
       ($ sub-header {:title "Timeline"})
       ($ field-grid
          ($ field-cell {:label "Year Joined"}
             ($ editable-select {:value (get field-values :year-joined)
                                 :options (or year-joined-options [])
                                 :placeholder "Select year"
                                 :saving? saving?
                                 :on-save #(rf/dispatch [::student-events/save-field "year-joined" % api-client])})
             ($ field-error {:field-slug "year-joined"}))
          ($ field-cell {:label "Grade Joined"}
             ($ editable-select {:value (get field-values :grade-joined)
                                 :options (or grade-joined-options [])
                                 :placeholder "Select grade"
                                 :saving? saving?
                                 :on-save #(rf/dispatch [::student-events/save-field "grade-joined" % api-client])})
             ($ field-error {:field-slug "grade-joined"}))
          ($ field-cell {:label "Four-Year Fellow"}
             ($ editable-select {:value (get field-values :four-year-fellow)
                                 :options four-year-fellow-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "four-year-fellow" % api-client])})
             ($ field-error {:field-slug "four-year-fellow"}))
          ($ field-cell {:label "Five-Year Fellow"}
             ($ editable-select {:value (get field-values :five-year-fellow)
                                 :options five-year-fellow-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "five-year-fellow" % api-client])})
             ($ field-error {:field-slug "five-year-fellow"}))
          ($ field-cell {:label "Legacy Fellow"}
             ($ editable-select {:value (get field-values :legacy-fellow)
                                 :options legacy-fellow-options
                                 :placeholder "Select..."
                                 :saving? saving?
                                 :as-string? true
                                 :on-save #(rf/dispatch [::student-events/save-field "legacy-fellow" % api-client])})
             ($ field-error {:field-slug "legacy-fellow"}))
          ($ field-cell {:label "Fellow Folder"}
             ($ editable-text {:value (get field-values :google-drive-folder)
                               :placeholder "Google Drive folder URL"
                               :saving? saving?
                               :on-save #(rf/dispatch [::student-events/save-field "google-drive-folder" % api-client])})
             ($ field-error {:field-slug "google-drive-folder"}))))))

(defui bryc-program-tab
  "BRYC Program tab with timeline and involvement fields.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        props {:student student :field-values field-values :api-client api-client :saving? saving?}]
    ($ nested-tabs
       {:tabs [{:id "status" :label "Status" :render #($ status-tab props)}
               {:id "schedule" :label "Schedule" :render #($ schedule-tab props)}
               {:id "people" :label "People" :render #($ people-tab props)}
               {:id "timeline" :label "Timeline" :render #($ timeline-tab props)}]})))
