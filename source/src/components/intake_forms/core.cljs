(ns components.intake-forms.core
  "Intake form configuration.

   Field options are fetched from CRM contact type metadata at runtime.
   Fields reference CRM field slugs via :crm-field key.

   The O*NET RIASEC assessment step is generated from the `riasec` component
   (single source of truth for the instrument; never inlined here)."
  (:require [ai.obney.bryc.riasec.interface :as riasec]))


;; Full intake form configuration
(def intake-config
  {:id "bryc-intake-form"
   :groups
   [{:id :role
     :title "Let's Get Started! 👋"
     :description "**First**, let's identify who's completing this form so we can personalize your experience."
     :fields [{:key :intake.answered/role
               :type :radio
               :label "Are you a student or parent/guardian?"
               ;; Survey-specific field, not in CRM - inline options
               :options ["Student" "Parent/Guardian"]}]}

    {:id :student-basics
     :title "Tell Us About Yourself 📚"
     :description "Now let's gather some basic information about you to help us understand your educational journey."
     :fields [{:key :intake.answered/student-first-name
               :type :text
               :label "Student First Name"}
              {:key :intake.answered/student-last-name
               :type :text
               :label "Student Last Name"}
              {:key :intake.answered/student-phone
               :type :text
               :label "Student Phone Number"
               :input-type "tel"}
              {:key :intake.answered/sms-consent
               :type :boolean-checkbox
               :label "Optional SMS updates"
               :checkbox-label "I agree to receive text messages from BRYC."
               :optional true
               :description "Checking this box is optional and is not required to complete this form or receive BRYC advising services. If you opt in, BRYC may send text messages about advising, including conversations with your advisor and reminders about deadlines, applications, and meetings. Message and data rates may apply. Message frequency varies. Reply **STOP** to unsubscribe or **HELP** for help. See our [Privacy Policy](/privacy) and [Terms](/terms)."}
              {:key :intake.answered/student-personal-email
               :type :text
               :label "Student Email"
               :input-type "email"}
              {:key :intake.answered/student-highschool
               :type :either-or
               :label "High School"
               :crm-field :high-school}
              {:key :intake.answered/graduation-year
               :type :select
               :label "Graduation Year"
               :crm-field :graduation-year}
              {:key :intake.answered/how-did-you-get-here
               :type :checkbox
               :label "How did you get here?"
               :crm-field :how-found-us}]}

    {:id :detailed-info
     :title "Getting to Know You Better 🌟"
     :description "To provide the best personalized guidance, we'd love to learn more about your background and family."
     :fields [{:key :intake.answered/gender
               :type :select
               :label "Gender"
               :crm-field :gender}
              {:key :intake.answered/race-ethnicity
               :type :select
               :label "Race/Ethnicity"
               :crm-field :race-ethnicity}
              {:key :intake.answered/race-ethnicity-elaboration
               :type :textarea
               :label "Race/Ethnicity Elaboration (optional)"
               :rows 3
               :optional true
               :show-when {:field-key :intake.answered/race-ethnicity :equals "Multi-Racial / Race Not Listed"}}
              {:key :intake.answered/primary-language
               :type :either-or
               :label "Primary Language"
               :crm-field :primary-language}
              {:key :intake.answered/home-zip
               :type :text
               :label "Home Zip Code"}
              {:key :intake.answered/student-birthdate
               :type :date
               :label "Student Birthdate"
               :placeholder "Pick a date"
               :from-year 1990
               :to-year 2015}
              {:key :intake.answered/parent-guardian-first-name
               :type :text
               :label "Parent/Guardian First Name"}
              {:key :intake.answered/parent-guardian-last-name
               :type :text
               :label "Parent/Guardian Last Name"}
              {:key :intake.answered/parent-guardian-relationship
               :type :either-or
               :label "Relationship to Student"
               :relationship-property :relationship-type
               :relationship-type-slug "guardian-of"}
              {:key :intake.answered/parent-guardian-email
               :type :text
               :label "Parent/Guardian Email"
               :input-type "email"}
              {:key :intake.answered/parent-guardian-phone
               :type :text
               :label "Parent/Guardian Phone"
               :input-type "tel"
               :description "Provide a parent or guardian phone number BRYC can use for advising records and follow-up when needed."}]}

    {:id :academic-performance
     :title "Your Transcript 📄"
     :description "Optional — upload your JCampus grade transcript as a PDF if you have one. You can skip this and continue."
     :fields [{:key :intake.answered/grade-transcript
               :type :file
               :label "Grade Transcript"
               :description "Upload your JCampus grade transcript as a PDF."
               :accept ".pdf"
               :max-size-mb 10
               :optional true}]}

    #_{:id :account-access
     :title "Account Information 🔐"
     :description "To provide comprehensive guidance, we may need access to academic records and test scores from these platforms."
     :fields [{:key :intake.answered/online-gradebook-website-answered
               :type :text
               :label "Online Grade Book Website"
               :placeholder "e.g., PowerSchool, Infinite Campus, etc."}
              {:key :intake.answered/online-grade-book-username
               :type :text
               :label "Online Grade Book Username"}
              {:key :intake.answered/online-grade-book-password
               :type :text
               :label "Online Grade Book Password"
               :input-type "password"}
              {:key :intake.answered/act-dot-org-username
               :type :text
               :label "ACT.org Username"}
              {:key :intake.answered/act-dot-org-password
               :type :text
               :label "ACT.org Password"
               :input-type "password"}]}

    {:id :career-certainty
     :title "Your Direction 🧭"
     :description "One quick question so we can tailor the rest of this for you."
     :fields [{:key :intake.answered/career-certainty
               :type :radio
               :label "How clear are you about your future direction?"
               :crm-field :career-certainty
               :options ["I'm not sure yet" "I already know what I want to do"]}]}

    {:id :riasec-assessment
     :title "Interest Profiler 🧩"
     :description (str "These activities help us understand your interests so we can "
                       "suggest careers and programs that fit you. For each one, choose "
                       "whether you'd like to do it — there are no right or wrong answers.\n\n"
                       "_Includes the O\\*NET Interest Profiler Short Form, developed by "
                       "the National Center for O\\*NET Development for the U.S. "
                       "Department of Labor, Employment & Training Administration._")
     :layout :riasec-grid
     :show-when {:field-key :intake.answered/career-certainty
                 :equals "I'm not sure yet"}
     :fields (riasec/wizard-fields)}

    {:id :goals-and-interests
     :title "Your Dreams & Aspirations ✨"
     :description "Finally, let's explore your future goals and interests to help create a personalized roadmap for success."
     :fields [{:key :intake.answered/post-highschool-goals
               :type :radio
               :label "Post-High School Goals"
               :crm-field :post-hs-goals}
              {:key :intake.answered/interesting-schools-or-programs
               :type :textarea
               :label "Schools or Programs of Interest"
               :description "List any specific colleges or programs you're already considering. Leave blank if you're not sure yet. (e.g. \"I'm interested in attending Southern for their nursing program\" or \"I want to attend a community college and study construction management\")"
               :rows 4
               :optional true}
              {:key :intake.answered/preferences-and-circumstances
               :type :checkbox
               :label "Preferences and Circumstances"
               :crm-field :preferences}
              ;; Conditionally required by the certainty router: required for the
              ;; "I already know what I want to do" path, optional otherwise (the
              ;; "I'm not sure yet" path completes the RIASEC assessment instead).
              {:key :intake.answered/open-response-future
               :type :textarea
               :label "Where do you see yourself this time next year, and what career are you working toward?"
               :rows 4
               :required-when {:field-key :intake.answered/career-certainty
                               :equals "I already know what I want to do"}}
              {:key :intake.answered/open-response-strengths
               :type :textarea
               :label "What are you good at, and what are you passionate about?"
               :rows 4
               :required-when {:field-key :intake.answered/career-certainty
                               :equals "I already know what I want to do"}}
              {:key :intake.answered/open-response-success
               :type :textarea
               :label "Who or what does success look like to you?"
               :rows 4
               :required-when {:field-key :intake.answered/career-certainty
                               :equals "I already know what I want to do"}}]}]})

;; List of all field keys in order (the 60 RIASEC item keys are generated from
;; the riasec component — never hand-listed).
(def all-field-keys
  (into
  [:intake.answered/role

  ;; 1
  :intake.answered/student-first-name
  :intake.answered/student-last-name
  :intake.answered/student-phone
  :intake.answered/student-personal-email
  :intake.answered/student-birthdate
  :intake.answered/gender
  :intake.answered/student-highschool
  :intake.answered/graduation-year
  :intake.answered/how-did-you-get-here

  ;; 2
  :intake.answered/race-ethnicity
  :intake.answered/race-ethnicity-elaboration
  :intake.answered/primary-language
  :intake.answered/home-zip

  ;; 4
  :intake.answered/parent-guardian-first-name
  :intake.answered/parent-guardian-last-name
  :intake.answered/parent-guardian-relationship
  :intake.answered/parent-guardian-email
  :intake.answered/parent-guardian-phone

  ;; 5
  :intake.answered/grade-transcript

  ;; 6.5 — certainty router + RIASEC assessment (60 keys appended below)
  :intake.answered/career-certainty

  ;; 7
  :intake.answered/post-highschool-goals
  :intake.answered/interesting-schools-or-programs
  :intake.answered/preferences-and-circumstances
  :intake.answered/open-response-future
  :intake.answered/open-response-strengths
  :intake.answered/open-response-success]
  (riasec/wizard-field-keys)))

;; Map from backend schema key to field configuration for reference
(def field-config-by-key
  "Quick lookup of field configuration by key"
  (->> (:groups intake-config)
       (mapcat :fields)
       (map (fn [field] [(:key field) field]))
       (into {})))

;; Export configuration
(def config
  {:intake intake-config
   :all-field-keys all-field-keys
   :field-config-by-key field-config-by-key})
