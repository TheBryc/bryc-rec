(ns components.crm.student.field-registry
  "Shared field configuration and renderer for CRM student fields.
   Single source of truth for how each editable field is rendered.
   Used by both CRM student detail tabs and recommendations sidebar."
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]
            [components.crm.student.editable-field :refer [editable-text editable-number editable-select editable-select-with-other editable-textarea editable-tag-select editable-boolean]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.formatting :refer [format-keyword]]))

;; =============================================================================
;; Short Display Labels
;; =============================================================================

(def goals-short-labels
  {"I strongly prefer to attend a four-year college right away." "Four-year college"
   "I prefer a four-year college but am open to starting at community college and then transferring." "Four-year (open to CC transfer)"
   "I prefer a 1- or 2-year program at a community or technical college that prepares me for immediate entry to a specific career." "Community/technical college"
   "I want to do little or no more school but still be prepared to get a good job." "Minimal school, job-ready"
   "I am interested in the skilled trades (e.g., electrician, carpenter, HVAC, welder, etc.)." "Skilled trades"
   "I want to enlist in the military." "Military"
   "I am completely open-minded, as long as it's affordable and makes sense for my life." "Open-minded"})

(def preferences-short-labels
  {"I will be the first in my family to earn a degree." "First-gen college"
   "I prefer to attend an HBCU (historically Black college / university)." "Prefers HBCU"
   "I prefer to attend an Hispanic-serving institution." "Prefers HSI"
   "I prefer to stay in Louisiana after high school." "Stay in LA"
   "I prefer to leave Louisiana after high school." "Leave LA"
   "I plan to do ROTC in college." "ROTC"
   "I'm a serious artist and want to pursue my art after high school." "Serious artist"
   "I'm a serious athlete, for sure good enough to get recruited." "Serious athlete"
   "I'm an undocumented immigrant or have a complicated citizenship status." "Immigration considerations"
   "I have a disability." "Has disability"
   "None of the above" "None"})

;; =============================================================================
;; Sub-Schemas (for vector editors)
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

(def activities-sub-schema
  {:type :vector
   :add-button-text "Add Activity"
   :empty-message "No activities added yet."
   :item-label-key :activity
   :item-schema
   {:type :map
    :fields [{:key :activity
              :type :string
              :required true
              :label "Activity Name"
              :placeholder "Enter activity name..."}
             {:key :role
              :type :string
              :required false
              :label "Role"
              :placeholder "e.g., President, Member, Volunteer..."}
             {:key :grades-involved
              :type :multi-select
              :required false
              :label "Grades Involved"
              :options ["9th" "10th" "11th" "12th"]}
             {:key :category
              :type :single-select
              :required false
              :label "Category"
              :options ["School-Based Activity"
                        "Community-Based Activity"
                        "Work Experience"
                        "Home & Family"]}]}})

(def ap-coursework-sub-schema
  {:type :vector
   :add-button-text "Add AP Course"
   :empty-message "No AP courses."
   :item-label-key :course-name
   :item-schema
   {:type :map
    :fields [{:key :course-name
              :type :string
              :required true
              :label "Course Name"
              :placeholder "e.g., AP Calculus AB"}
             {:key :grade-status
              :type :string
              :label "Grade/Status"
              :placeholder "e.g., A, In Progress..."}]}})

(def dual-enrollment-sub-schema
  {:type :vector
   :add-button-text "Add Course"
   :empty-message "No dual enrollment courses."
   :item-label-key :course-name
   :item-schema
   {:type :map
    :fields [{:key :course-name :type :string :required true :label "Course Name"}
             {:key :institution :type :string :label "Institution"}
             {:key :grade-status :type :string :label "Grade/Status"}]}})

(def test-score-history-sub-schema
  {:type :vector
   :add-button-text "Add Test Score"
   :empty-message "No test scores recorded."
   :item-label-key :test-name
   :item-schema
   {:type :map
    :fields [{:key :test-name
              :type :string
              :required true
              :label "Test Name"
              :placeholder "e.g., ACT, SAT, WorkKeys..."}
             {:key :score
              :type :string
              :required true
              :label "Score"
              :placeholder "Enter score..."}
             {:key :date
              :type :date
              :label "Test Date"}]}})

;; =============================================================================
;; Field Configurations
;; =============================================================================
;; Each config maps a CRM field-slug to rendering metadata.
;; :type determines which editable component to use.
;; :key is the keyword used to read the value from the student map.
;; Options come from the CRM field definitions (dynamic), not hardcoded here.

(def field-configs
  {;; --- Academics ---
   "self-reported-gpa"    {:type :number   :label "Self-Reported GPA"      :key :student/self-reported-gpa
                           :min 0 :max 4.0 :step 0.01 :placeholder "0.00"}
   "unweighted-gpa"       {:type :number   :label "Unweighted Cumulative GPA" :key :student/unweighted-cumulative-gpa
                           :min 0 :max 4.0 :step 0.01 :placeholder "0.00"}
   "weighted-core-gpa"    {:type :number   :label "Weighted Core GPA"      :key :student/weighted-core-gpa
                           :min 0 :max 5.0 :step 0.01 :placeholder "0.00"}
   "act-score"            {:type :number   :label "ACT Score"              :key :student/act-score
                           :min 0 :max 36  :step 1    :placeholder "--"}
   "tops-award"           {:type :select   :label "TOPS Award"             :key :student/tops-award
                           :as-string? true}
   "work-keys-score"      {:type :select   :label "WorkKeys Score"         :key :student/work-keys-score
                           :format-fn format-keyword :as-string? true}
   "ap-coursework"        {:type :vector   :label "AP Coursework"          :key :student/ap-coursework
                           :sub-schema ap-coursework-sub-schema            :field-values-key :ap-coursework}
   "dual-enrollment"      {:type :vector   :label "Dual Enrollment"        :key :student/dual-enrollment
                           :sub-schema dual-enrollment-sub-schema          :field-values-key :dual-enrollment}
   "test-score-history"   {:type :vector   :label "Test Score History"      :key :student/test-score-history
                           :sub-schema test-score-history-sub-schema       :field-values-key :test-score-history}
   "transcripts"          {:type :text     :label "Transcripts"            :key :student/transcripts
                           :field-values-key :transcripts}

   ;; --- Senior Year / Career & Goals ---
   "color-profile"        {:type :select   :label "Color Profile"          :key :student/color-profile
                           :format-fn format-keyword}
   "career-fields"        {:type :tags     :label "Career Fields"          :key :student/career-fields}
   "post-hs-goals"        {:type :tags     :label "Post-HS Goals"          :key :student/goals
                           :display-fn #(get goals-short-labels % %)}
   "schools-of-interest"  {:type :textarea :label "Schools of Interest"    :key :student/interests
                           :rows 3}
   "preferences"          {:type :tags     :label "Preferences & Circumstances" :key :student/preferences
                           :display-fn #(get preferences-short-labels % %)}
   "open-response"        {:type :textarea :label "Open Response"          :key :student/open-response
                           :rows 4}
   "open-response-future" {:type :textarea :label "Where they see themselves next year / career" :key :student/open-response-future
                           :field-values-key :open-response-future :rows 4}
   "open-response-strengths" {:type :textarea :label "What they're good at / passionate about" :key :student/open-response-strengths
                           :field-values-key :open-response-strengths :rows 4}
   "open-response-success" {:type :textarea :label "What success looks like to them" :key :student/open-response-success
                           :field-values-key :open-response-success :rows 4}
   ;; --- Messaging ---
   "sms-consent"         {:type :boolean  :label "SMS Consent"            :key :student/sms-consent
                          :field-values-key :sms-consent}
   "messaging-opt-out"   {:type :boolean  :label "Messaging Opt-Out"      :key :student/messaging-opt-out
                          :field-values-key :messaging-opt-out}
   ;; --- RIASEC (Phase 2): routing answer + server-derived outputs. The 60
   ;; per-item response fields are intentionally NOT registered (not advisor-
   ;; facing one-by-one; instrument lives in the riasec component). ---
   "career-certainty"     {:type :select   :label "Career Certainty"       :key :student/career-certainty
                           :field-values-key :career-certainty}
   "holland-code"         {:type :text     :label "Holland Code (RIASEC)"   :key :student/holland-code
                           :field-values-key :holland-code :placeholder "—"}
   "riasec-r-score"       {:type :number   :label "RIASEC — Realistic"      :key :student/riasec-r-score
                           :field-values-key :riasec-r-score :min 0 :max 10 :placeholder "—"}
   "riasec-i-score"       {:type :number   :label "RIASEC — Investigative"  :key :student/riasec-i-score
                           :field-values-key :riasec-i-score :min 0 :max 10 :placeholder "—"}
   "riasec-a-score"       {:type :number   :label "RIASEC — Artistic"       :key :student/riasec-a-score
                           :field-values-key :riasec-a-score :min 0 :max 10 :placeholder "—"}
   "riasec-s-score"       {:type :number   :label "RIASEC — Social"         :key :student/riasec-s-score
                           :field-values-key :riasec-s-score :min 0 :max 10 :placeholder "—"}
   "riasec-e-score"       {:type :number   :label "RIASEC — Enterprising"   :key :student/riasec-e-score
                           :field-values-key :riasec-e-score :min 0 :max 10 :placeholder "—"}
   "riasec-c-score"       {:type :number   :label "RIASEC — Conventional"   :key :student/riasec-c-score
                           :field-values-key :riasec-c-score :min 0 :max 10 :placeholder "—"}
   "involvement-score"    {:type :number   :label "Involvement Score"      :key :student/involvement-score
                           :min 1 :max 4   :step 1    :placeholder "--"}

   ;; --- Narrative ---
   "summary"              {:type :textarea :label "Summary"                :key :student/summary
                           :rows 4}
   "narrative-additions"  {:type :vector   :label "Narrative Additions"    :key :student/narrative-additions
                           :sub-schema narrative-additions-sub-schema}
   "activities"           {:type :vector   :label "Activities"             :key :student/extracurricular-activities
                           :sub-schema activities-sub-schema}

   ;; --- Profile ---
   "gender"               {:type :select   :label "Gender"                 :key :student/gender
                           :as-string? true}
   "pronouns"             {:type :select   :label "Pronouns"               :key :student/pronouns
                           :as-string? true}
   "race-ethnicity"       {:type :tags     :label "Ethnicity"              :key :student/ethnicity}
   "primary-language"     {:type :select-other :label "Primary Language"   :key :student/primary-language
                           :as-string? true}})

;; =============================================================================
;; Legacy value handling
;; =============================================================================
;; A field's :data-type can change in place; existing contacts keep the value
;; written under the OLD type (decision: values stay opaque, shown read-only).
;; `legacy-value?` detects a stored value whose shape no longer matches the
;; editable component for the current type so we render it read-only instead of
;; feeding bad data into the editor (e.g. a scalar into a multi-select, which
;; would otherwise iterate a string character-by-character).

(defn legacy-value?
  "True when `value` is present but its shape is incompatible with the editable
   component for `field-type`. Conservative — only flags genuinely broken
   shapes so normal data is never mistaken for legacy."
  [field-type value]
  (and (some? value)
       (case field-type
         :tags             (not (coll? value))      ;; scalar fed to multi-select
         (:select :select-other) (coll? value)      ;; collection fed to single-select
         :vector           (not (sequential? value)) ;; non-seq fed to vector editor
         :number           (and (not (number? value))
                                (not (and (string? value)
                                          (re-matches #"\s*-?\d*\.?\d+\s*" value))))
         :boolean          (not (boolean? value))
         (:text :textarea) (coll? value)            ;; collection fed to text input
         false)))

(defui legacy-field-display
  "Read-only display for a value stored under a previous field type. The value
   is preserved and shown verbatim so nothing is lost; it is not editable here
   until migrated."
  [{:keys [value]}]
  ($ :div {:class "flex items-center gap-2 text-sm"}
     ($ :span {:class "text-foreground/80 break-words"}
        (cond
          (set? value)     (str/join ", " (map str (sort-by str value)))
          (coll? value)    (str/join ", " (map str value))
          (keyword? value) (name value)
          :else            (str value)))
     ($ :span {:class "shrink-0 rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-amber-700"
               :title "Stored under a previous field type — preserved read-only until migrated."}
        "legacy")))

;; =============================================================================
;; Field Renderer
;; =============================================================================

(defui student-field
  "Renders the appropriate editable component for a CRM field.
   Props:
   - field-slug: string field slug (key into field-configs)
   - student: student data map (or nil; will try field-values if :field-values-key set)
   - field-values: CRM field-values map (for fields not on student map)
   - options: vector of options (from field-options subscription)
   - saving?: boolean
   - on-save: (fn [new-value]) — called with the new value on save
   - placeholder: optional override for placeholder text"
  [{:keys [field-slug student field-values options saving? on-save placeholder]}]
  (let [config (get field-configs field-slug)
        value (if-let [fvk (:field-values-key config)]
                (get field-values fvk)
                (get student (:key config)))
        placeholder (or placeholder (:placeholder config) "—")]
    (when config
      (if (legacy-value? (:type config) value)
        ($ legacy-field-display {:value value})
       (case (:type config)
        :text
        ($ editable-text
           {:value value
            :placeholder placeholder
            :saving? saving?
            :on-save on-save})

        :number
        ($ editable-number
           {:value value
            :min (:min config)
            :max (:max config)
            :step (:step config)
            :placeholder placeholder
            :saving? saving?
            :on-save on-save})

        :select
        ($ editable-select
           {:value value
            :options (or options [])
            :placeholder placeholder
            :saving? saving?
            :format-fn (:format-fn config)
            :as-string? (:as-string? config)
            :on-save on-save})

        :select-other
        ($ editable-select-with-other
           {:value value
            :options (or options [])
            :placeholder placeholder
            :saving? saving?
            :custom-placeholder "Enter language"
            :on-save on-save})

        :tags
        ($ editable-tag-select
           {:value value
            :options (or options [])
            :placeholder placeholder
            :saving? saving?
            :display-fn (:display-fn config)
            :on-save on-save})

        :textarea
        ($ editable-textarea
           {:value value
            :placeholder placeholder
            :rows (:rows config 3)
            :saving? saving?
            :on-save on-save})

        :boolean
        ($ editable-boolean
           {:value value
            :saving? saving?
            :on-save on-save})

        :vector
        ($ schema-vector-editor
           {:sub-schema (:sub-schema config)
            :value value
            :saving? saving?
            :on-save on-save})

        nil)))))
