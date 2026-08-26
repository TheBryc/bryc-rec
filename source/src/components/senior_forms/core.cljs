(ns components.senior-forms.core
  "Senior intake survey form configuration.

   Matches the BRYC Class of 2027 Senior Intake Survey (Google Form).
   Field options are fetched from CRM contact type metadata at runtime.
   Fields reference CRM field slugs via :crm-field key.

   Where questions overlap with the advisee intake form, the same wording
   and field keys are used for consistency.")

(def senior-config
  {:id "bryc-senior-intake-form"
   :survey-type :senior
   :groups
   [{:id :basic-info
     :title "Let's Get Started!"
     :description "Thank you for completing this questionnaire thoroughly. Please be honest and candid. It will help your BRYC counselor get to know you so that we can best support your post-high school plans!"
     :fields [{:key :senior.answered/first-name
               :type :text
               :label "First Name"}
              {:key :senior.answered/last-name
               :type :text
               :label "Last Name"}
              {:key :senior.answered/phone
               :type :text
               :label "Cell Phone"
               :input-type "tel"}
              {:key :senior.answered/sms-consent
               :type :boolean-checkbox
               :label "Optional SMS updates"
               :checkbox-label "I agree to receive text messages from BRYC."
               :optional true
               :description "Checking this box is optional and is not required to complete this form or receive BRYC advising services. If you opt in, BRYC may send text messages about advising, including conversations with your advisor and reminders about deadlines, applications, and meetings. Message and data rates may apply. Message frequency varies. Reply **STOP** to unsubscribe or **HELP** for help. See our [Privacy Policy](/privacy) and [Terms](/terms)."}
              {:key :senior.answered/email
               :type :text
               :label "Email"
               :input-type "email"}
              {:key :senior.answered/high-school
               :type :either-or
               :label "Which school will you attend in 2026–2027?"
               :crm-field :high-school}]}

    {:id :academics
     :title "Academics"
     :description "Tell us about your test scores and grades so your BRYC counselor can help you find high-quality schools and programs."
     :fields [{:key :senior.answered/highest-act-score
               :type :text
               :input-type "number"
               :step "1"
               :min "1"
               :max "36"
               :optional true
               :label "If you've taken an official ACT, what is your highest composite score?"
               :description "Enter a number between 1-36, or leave blank if you haven't taken it"}
              {:key :senior.answered/work-keys-score
               :type :select
               :label "If you've taken a WorkKeys assessment, what is the highest level you earned?"
               :crm-field :work-keys-score
               :optional true}
              {:key :senior.answered/cumulative-gpa
               :type :text
               :input-type "number"
               :step "0.01"
               :min "0"
               :max "4"
               :label "What is your cumulative, unweighted GPA?"
               :description "This is on a scale of 0.0–4.0 and should include all of your high school grades."}
              {:key :senior.answered/grade-transcript
               :type :file
               :label "Grade Transcript"
               :description "Upload your JCampus grade transcript as a PDF."
               :accept ".pdf"
               :crm-field :transcripts
               :optional true}
              {:key :senior.answered/neurodivergent-opportunities
               :type :radio
               :label "Some programs and scholarships specifically support students with ADHD, autism, dyslexia, or other learning differences. Would you like to know more about these opportunities?"
               :options ["Yes, please." "No, thank you."]
               :optional true}]}

    {:id :login-info
     :title "Account Information \uD83D\uDD10"
     :description "Please provide your username and password for each website below so BRYC can verify your academic data. If you do not have a login or decline to provide it, please write N/A."
     :fields [{:key :senior.answered/online-gradebook-website
               :type :text
               :label "Online Gradebook Website"
               :placeholder "e.g., https://jcampus.ebrschools.org"
               :input-type "url"
               :optional true}
              {:key :senior.answered/online-grade-book-username
               :type :text
               :label "Online Gradebook Username"
               :optional true}
              {:key :senior.answered/online-grade-book-password
               :type :text
               :label "Online Gradebook Password"
               :input-type "password"
               :optional true}
              {:key :senior.answered/act-dot-org-username
               :type :text
               :label "ACT.org Account Email"
               :optional true}
              {:key :senior.answered/act-dot-org-password
               :type :text
               :label "ACT.org Account Password"
               :input-type "password"
               :optional true}
              {:key :senior.answered/collegeboard-username
               :type :text
               :label "Collegeboard.com Account Username"
               :optional true}
              {:key :senior.answered/collegeboard-password
               :type :text
               :label "Collegeboard.com Account Password"
               :input-type "password"
               :optional true}]}

    {:id :extracurriculars
     :title "Beyond the Classroom \uD83C\uDFC6"
     :description "In this section, tell us about your most significant commitments outside of class. We want to know how you spend your time and where you've taken on leadership roles. \n\n Examples of activities could include:\n,  Academic (Beta, Honor Society, etc.),  Art (visual, crafts, digital, writing, poetry, fashion, etc.),  Athletics (sports),  Career readiness,  Community service / volunteering,  Computer / technology,  Culinary (cooking, baking, catering),  Dance, Environmental,  Family responsibilities (caring for younger/elderly relatives, etc.),  Greek organizations (fraternities/sororities),  Internship, Journalism,  Junior ROTC, LGBTQ activities,  Music (instrumental, vocal),  Personal business,  Political,  Religious (choir, youth group, ministry, etc.),  Research,  Robotics,  School spirit (cheerleading),  Science / math,  Social justice,  Social media presence,  Student government,  Theater / drama,  Work (full or part-time job, family business, etc.)"
     :fields [{:key :senior.answered/survey-involvement
               :type :radio
               :label "Which statement best describes your extracurricular involvement?"
               :options ["I have a full plate of activities, including multiple long-term commitments. I HAVE held formal leadership roles (president, captain, lead, etc.) and/or informal leadership experiences."
                         "I have a full plate of activities, including multiple long-term commitments. I have NOT held formal leadership roles (president, captain, lead, etc.) but have some informal leadership experiences."
                         "I've tried out several activities, but I haven't been deeply involved or held any formal leadership roles."
                         "I haven't really been involved with extracurricular activities or held any formal leadership roles."]}
              {:key :senior.answered/extracurricular-activities
               :type :dynamic-list
               :label "Your Activities & Commitments"
               :min-items 0
               :max-items 10
               :add-button-text "Add Activity"
               :empty-message "No activities added yet. Click below to add your first activity."
               :optional true
               :item-label-fn (fn [item idx]
                                (or (:activity item)
                                    (str "Activity " (inc idx))))
               :crm-field :activities
               :sub-fields [{:key :activity
                             :type :textarea
                             :label "What was this activity?"
                             :description "Describe an extracurricular activity, job, or responsibility you've been involved in."
                             :placeholder "e.g., Basketball team, Part-time job at local store, Caring for younger siblings"
                             :rows 3
                             :optional false}
                            {:key :grades-involved
                             :type :checkbox
                             :label "Which grades were you involved?"
                             :crm-sub-field :grades-involved
                             :optional false}
                            {:key :category
                             :type :select
                             :label "What category does this fall under?"
                             :crm-sub-field :category
                             :optional false}
                            {:key :role
                             :type :either-or
                             :label "What role did you have?"
                             :options ["Ambassador" "Board / Council Member" "Captain / Co-Captain"
                                       "Committee Chair" "Committee Member" "Director" "Editor"
                                       "Founder" "Lead" "Member" "Organizer / Event Coordinator"
                                       "Club Officer" "President" "Secretary"
                                       "Section Leader (e.g. band, choir)" "Trainer" "Treasurer"
                                       "Vice President"]
                             :optional false}]}
              {:key :senior.answered/family-responsibilities
               :type :checkbox
               :label "Most schools also want to know about your family responsibilities. Which responsibilities do you have at least once per week? (check all that apply)"
               :options ["Care for a sibling or someone in your household"
                         "Household chores like cleaning, cooking, or lawn care"
                         "Work in a family-owned business or work to support your family"
                         "Run errands with or without your Guardians"
                         "I don't have any responsibilities at home."]
               :optional true}
              {:key :senior.answered/family-responsibilities-hours
               :type :radio
               :label "Approximately how many hours per week do you spend on these responsibilities?"
               :options ["0" "1–4" "5–10" "More than 10"]
               :optional true}]}

    {:id :goals-and-interests
     :title "Post-High School Goals"
     :description "Please give us as much information as possible about your goals for after high school so that we can provide the best counseling possible. The more you tell us, the better."
     :fields [{:key :senior.answered/post-highschool-goals
               :type :radio
               :label "Which statement best describes your plans for after high school?"
               :crm-field :post-hs-goals}
              {:key :senior.answered/interesting-schools-or-programs
               :type :textarea
               :label "What are your top 1–3 schools? (two-year and/or four-year)"
               :rows 3
               :optional true}
              {:key :senior.answered/preferences-and-circumstances
               :type :checkbox
               :label "Check ANY of the following that apply to you"
               :crm-field :preferences}
              {:key :senior.answered/majors-fields
               :type :checkbox
               :label "List the major(s) or field(s) that you plan to pursue."
               :description "e.g., nursing, marketing, finance, cosmetology, culinary arts, engineering, film, etc."
               :crm-field :survey-majors-fields}
              {:key :senior.answered/interesting-career-fields
               :type :checkbox
               :label "List any professions that interest you."
               :description "Select all career fields that interest you."
               :crm-field :career-fields}
              {:key :senior.answered/open-response
               :type :textarea
               :label "What else should we know about your goals?"
               :description "Please share as much as possible about your future goals and how you see your post-high school education getting you there. The more you tell us, the better."
               :rows 5}
              {:key :senior.answered/immigration-status
               :type :radio
               :label "Your citizenship or immigration status may impact which post-high school pathways BRYC recommends for you. This information is strictly confidential. Which of the following best describes you?"
               :options ["I'm a U.S. Citizen"
                         "I'm not a U.S. Citizen or I'm unsure of my status."
                         "It's complicated, and I'd like to discuss it."
                         "I prefer not to respond."]
               :optional true}]}]})
