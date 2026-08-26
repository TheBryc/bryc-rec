(ns components.advisor.intake-meeting-page
  (:require [uix.core :as uix :refer [defui $ use-effect use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/textarea" :as textarea]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["/gen/shadcn/components/ui/card" :as card]
            ["lucide-react" :refer [Upload Clock CheckCircle2]]
            [components.context.interface :as context]
            [components.advisor.upload-meeting-transcript-modal :refer [upload-meeting-transcript-modal]]
            [components.advisor.transcript-analysis-review :refer [transcript-analysis-review]]
            [components.crm.student.editable-field :refer [editable-text editable-encrypted]]
            [cljs.reader :as reader]
            [store.advisor.meeting-editor.events :as editor-events]
            [store.advisor.meeting-editor.subs :as editor-subs]
            [store.advisor.meetings.events :as meetings-events]))

(defn format-relative-time [date]
  (let [now (js/Date.)
        seconds (/ (- (.getTime now) (.getTime date)) 1000)]
    (cond
      (< seconds 10) "just now"
      (< seconds 60) (str (js/Math.floor seconds) "s ago")
      (< seconds 3600) (str (js/Math.floor (/ seconds 60)) "m ago")
      :else (str (js/Math.floor (/ seconds 3600)) "h ago"))))

(defn migrate-section-notes
  "Migrates notes from old section IDs to new ones for backwards compatibility.
   Existing :overgrad-onboarding notes are moved to :extracurricular-involvement."
  [notes]
  (if (contains? notes :overgrad-onboarding)
    (-> notes
        (assoc :extracurricular-involvement (get notes :overgrad-onboarding))
        (dissoc :overgrad-onboarding))
    notes))

(def agenda-sections
  [{:id :rapport-building
    :title "Rapport-Building & Meeting Purpose"
    :duration "~5 min"
    :prompts
    ["Tell the student about yourself and get to know them on a personal level"
     "Explain meeting purpose is to understand student's goals for life after high school"
     "Note for student and guardian that these are the guiding principles our organization operates under in the advising process:"
     "\"As we go through the post–secondary process and explore your options, please know that our goal at BRYC is to find your best fit. We will always:\""
     "• Help you get into the best programs possible."
     "• Focus on affordable options with the greatest Return On Investment."
     "• Look for paths that lead to financially stable careers."
     "• And most importantly, honor your personal vision and goals."
     "Explain you will be taking detailed notes and may need to pause for clarifications"]}

   {:id :profile-review
    :title "Profile Review and Missing Information"
    :duration "~10 min"
    :prompts
    ["Briefly overview the student's submitted intake responses to confirm accuracy and ensure all pertinent information for their profile has been included."
     "Crucially, ensure login credentials (J Campus / ACT.org) are collected during the meeting for any advisee who did not share the information in their intake form."
     "This is essential for tracking academic progress."]}

   {:id :extracurricular-involvement
    :title "Extracurricular Involvement"
    :duration "~15 min"
    :prompts
    ["Walk me through your current extracurricular activities. What clubs, sports, or organizations are you involved in?"
     "Do you have any jobs or regular responsibilities outside of school? This includes paid work, family business help, or caregiving."
     "Tell me about any volunteer work or community service you participate in."
     "What hobbies or personal interests do you pursue in your free time?"
     "For any activities where you hold a leadership role, describe your responsibilities."
     "Approximately how many hours per week do you spend on your most significant activities?"
     "What skills or experiences have you gained from these activities that you're most proud of?"]}

   {:id :postsecondary-goals
    :title "Postsecondary Goals & Preferences"
    :duration "~10 min"
    :prompts
    ["What career fields or industries interest you most right now? It's okay if you're unsure."
     "What postsecondary pathways are you considering? (Examples: 4-year university, community college, trade/technical school, apprenticeship, military, direct workforce entry)"
     "Are you open to exploring pathways you haven't considered before, or do you have a strong preference?"
     "How important is staying close to home versus being open to relocating for your education?"
     "What financial factors are most important to your family when considering postsecondary options?"
     "What does your ideal timeline look like after graduation? (Gap year, immediate enrollment, etc.)"
     "Is there anything specific you want to achieve or experience during your postsecondary journey?"]}

   {:id :next-steps
    :title "Next Steps"
    :duration "2-5 min"
    :prompts
    ["Thank the student/guardian for their time and openness."
     "Reiterate that they now have access to their initial recommendations in Overgrad and that you will be adding more options as you continue to work together."
     "Ask the student/guardian if they have any final questions."]}])

;; Credentials editor component for inline editing during intake meeting
(defui credentials-editor
  "Compact credentials editor for Grade Book and ACT.org login info.
   Props:
   - student-id: UUID of the student
   - disabled?: boolean, whether editing is disabled (meeting completed)
   - api-client: API client for saving"
  [{:keys [student-id disabled? api-client]}]
  (let [credentials (use-subscribe [::editor-subs/credentials])
        loading? (use-subscribe [::editor-subs/credentials-loading?])]
    ($ card/Card {:class "bg-muted/30"}
       ($ card/CardHeader {:class "pb-2"}
          ($ card/CardTitle {:class "text-sm font-medium"} "Credentials"))
       ($ card/CardContent {:class "space-y-4"}
          (if loading?
            ($ :p {:class "text-sm text-muted-foreground"} "Loading credentials...")
            ($ :<>
               ;; Grade Book section
               ($ :div {:class "space-y-2"}
                  ($ :h4 {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"} "Grade Book")
                  ($ :div {:class "grid grid-cols-[80px_1fr] gap-x-2 gap-y-1 items-center"}
                     ($ :span {:class "text-xs text-muted-foreground"} "URL")
                     ($ editable-text
                        {:value (:online-grade-book-url credentials)
                         :placeholder "Enter URL..."
                         :disabled? disabled?
                         :class "text-sm"
                         :on-save #(rf/dispatch [::editor-events/save-credential-field
                                                 student-id :online-grade-book-url % api-client])})
                     ($ :span {:class "text-xs text-muted-foreground"} "Username")
                     ($ editable-encrypted
                        {:value (:online-grade-book-username credentials)
                         :placeholder "Enter username..."
                         :disabled? disabled?
                         :class "text-sm"
                         :on-save #(rf/dispatch [::editor-events/save-encrypted-credential-field
                                                 student-id :online-grade-book-username % api-client])})
                     ($ :span {:class "text-xs text-muted-foreground"} "Password")
                     ($ editable-encrypted
                        {:value (:online-grade-book-password credentials)
                         :placeholder "Enter password..."
                         :disabled? disabled?
                         :class "text-sm"
                         :on-save #(rf/dispatch [::editor-events/save-encrypted-credential-field
                                                 student-id :online-grade-book-password % api-client])})))
               ;; ACT.org section
               ($ :div {:class "space-y-2 pt-2 border-t"}
                  ($ :h4 {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"} "ACT.org")
                  ($ :div {:class "grid grid-cols-[80px_1fr] gap-x-2 gap-y-1 items-center"}
                     ($ :span {:class "text-xs text-muted-foreground"} "Username")
                     ($ editable-encrypted
                        {:value (:act-dot-org-username credentials)
                         :placeholder "Enter username..."
                         :disabled? disabled?
                         :class "text-sm"
                         :on-save #(rf/dispatch [::editor-events/save-encrypted-credential-field
                                                 student-id :act-dot-org-username % api-client])})
                     ($ :span {:class "text-xs text-muted-foreground"} "Password")
                     ($ editable-encrypted
                        {:value (:act-dot-org-password credentials)
                         :placeholder "Enter password..."
                         :disabled? disabled?
                         :class "text-sm"
                         :on-save #(rf/dispatch [::editor-events/save-encrypted-credential-field
                                                 student-id :act-dot-org-password % api-client])})))))))))

(defui agenda-section [{:keys [section notes on-notes-change disabled? student-id api-client]}]
  (let [is-profile-review? (= (:id section) :profile-review)
        ;; Local state for textarea value (prevents cursor jumping)
        [local-notes set-local-notes!] (use-state (or notes ""))
        handle-change (fn [text]
                        (set-local-notes! text)
                        (on-notes-change (:id section) text))]
    ;; Sync from parent when notes are first loaded
    (use-effect
      (fn []
        (when notes
          (set-local-notes! notes))
        js/undefined)
      [notes])
    ($ :div {:class "mb-8"}
       ($ :div {:class "grid grid-cols-2 gap-8"}
          ;; Left: Agenda content
          ($ :div {:class "pr-8 border-r"}
             ($ :div {:class "flex items-baseline justify-between mb-3"}
                ($ :h3 {:class "text-xl font-semibold"} (:title section))
                ($ :span {:class "text-sm text-muted-foreground font-medium"} (:duration section)))
             ($ :div {:class "mt-4 bg-muted/30 rounded-lg p-4 space-y-3"}
                (for [[idx prompt] (map-indexed vector (:prompts section))]
                  ($ :p {:key idx :class "text-sm leading-relaxed text-foreground"}
                     prompt))))
          ;; Right: Credentials (for profile-review) + Notes
          ($ :div {:class "pl-2 space-y-4"}
             ;; Show credentials editor for profile-review section
             (when is-profile-review?
               ($ credentials-editor {:student-id student-id
                                      :disabled? disabled?
                                      :api-client api-client}))
             ;; Notes textarea
             ($ :div
                (when is-profile-review?
                  ($ :label {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide mb-2 block"} "Notes"))
                ($ textarea/Textarea {:value (or local-notes "")
                                     :on-change #(handle-change (.. % -target -value))
                                     :placeholder "Enter your notes for this section..."
                                     :disabled disabled?
                                     :rows (if is-profile-review? 6 12)
                                     :class "font-mono text-sm"})))))))

(defui intake-meeting-page [{:keys [student-name student-id meeting-id meeting meeting-notes meeting-status
                                    meeting-transcript meeting-analysis-status meeting-analysis-result
                                    on-complete on-back]}]
  (let [;; Subscribe to editor state
        notes (use-subscribe [::editor-subs/notes])
        saving? (use-subscribe [::editor-subs/saving?])
        last-saved (use-subscribe [::editor-subs/last-saved])

        ;; Local state for triggering re-renders
        [_rerender-key set-rerender-key!] (use-state 0)
        [upload-modal-open? set-upload-modal-open!] (use-state false)

        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Derive completion state from prop (more reliable than subscription)
        is-completed? (= meeting-status :completed)

        ;; Derived state for transcript/analysis
        has-transcript? (boolean meeting-transcript)
        analysis-in-progress? (= meeting-analysis-status :in-progress)
        analysis-completed? (= meeting-analysis-status :completed)

        handle-notes-change (fn [section-id text]
                              (let [current-notes (or notes {})
                                    updated-notes (assoc current-notes section-id text)]
                                (rf/dispatch [::editor-events/update-notes updated-notes])))

        handle-complete (fn []
                          (rf/dispatch [::editor-events/complete-meeting])
                          ;; Callback after completion
                          (js/setTimeout #(when on-complete (on-complete notes)) 500))

        handle-upload-complete (fn []
                                 (set-upload-modal-open! false)
                                 ;; Refresh meeting data to get updated transcript/analysis status
                                 (rf/dispatch [::meetings-events/load-editor-screen
                                               (parse-uuid student-id)
                                               meeting-id
                                               api-client]))]

    ;; Ref to capture meeting-notes without triggering effect re-runs
    ;; (notes are available on mount since parent renders only when meeting is loaded)
    (let [meeting-notes-ref (uix/use-ref meeting-notes)]
      (reset! meeting-notes-ref meeting-notes)

      ;; Initialize editor on mount
      (use-effect
        (fn []
          (when meeting-id
            (let [raw-notes @meeting-notes-ref
                  ;; Parse existing notes (EDN map) and migrate old section IDs
                  initial-notes (if (and raw-notes (not= raw-notes ""))
                                  (try
                                    (let [parsed (reader/read-string raw-notes)]
                                      (if (map? parsed)
                                        (migrate-section-notes parsed)
                                        {}))
                                    (catch :default e
                                      (js/console.error "Failed to parse meeting notes:" e)
                                      {}))
                                  {})]
              (rf/dispatch [::editor-events/init-editor meeting-id initial-notes api-client])))
          ;; Cleanup on unmount
          (fn []
            (rf/dispatch [::editor-events/cleanup])))
        [meeting-id api-client]))

    ;; Load student credentials on mount
    (use-effect
      (fn []
        (when (and student-id api-client)
          (rf/dispatch [::editor-events/load-student-credentials (parse-uuid student-id) api-client]))
        js/undefined)
      [student-id api-client])

    ;; Force re-render every 10 seconds to update "Saved X ago" text
    (use-effect
      (fn []
        (when last-saved
          (let [interval-id (js/setInterval
                             #(set-rerender-key! (fn [k] (inc k)))
                             10000)] ; 10 seconds
            (fn []
              (js/clearInterval interval-id))))
        js/undefined)
      [last-saved])

    ($ :<>
       ;; Upload Modal
       ($ upload-meeting-transcript-modal {:open? upload-modal-open?
                                           :on-close #(set-upload-modal-open! false)
                                           :student-id (parse-uuid student-id)
                                           :meeting-id meeting-id
                                           :on-upload-complete handle-upload-complete})

       ($ :div {:class "flex flex-col h-screen bg-background"}
          ;; Header
          ($ :div {:class "border-b bg-gradient-to-b from-background to-muted/20 p-6 flex-shrink-0"}
             ($ :div {:class "max-w-7xl mx-auto"}
                ($ :div {:class "flex items-center justify-between"}
                   ;; Save status
                   ($ :div {:class "flex items-center gap-2"}
                      (when (and (not is-completed?) (or saving? last-saved))
                        ($ :span {:class "text-xs text-muted-foreground"}
                           (cond
                             saving? "Saving..."
                             last-saved (str "Saved " (format-relative-time (js/Date. last-saved)))
                             :else ""))))
                   ($ :div {:class "flex items-center gap-3"}
                      ;; Upload transcript button (when meeting is completed and no transcript yet)
                      (when (and is-completed? (not has-transcript?))
                        ($ button/Button {:variant "outline"
                                         :on-click #(set-upload-modal-open! true)}
                           ($ Upload {:class "h-4 w-4 mr-2"})
                           "Upload Transcript"))
                      ;; Complete meeting button (when not completed)
                      (when (not is-completed?)
                        ($ button/Button {:on-click handle-complete
                                         :disabled saving?}
                           (if saving? "Saving..." "Complete Meeting")))))))

          ;; Content
          ($ :div {:class "flex-1 overflow-y-auto"}
             ($ :div {:class "max-w-7xl mx-auto p-8"}
                ($ :div {:class "space-y-6"}
                   ;; Analysis status indicator (when in progress)
                   (when analysis-in-progress?
                     ($ alert/Alert {:class "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950"}
                        ($ Clock {:class "h-4 w-4 text-amber-600 dark:text-amber-400"})
                        ($ alert/AlertTitle {:class "text-amber-900 dark:text-amber-100"}
                           "Transcript Analysis In Progress")
                        ($ alert/AlertDescription {:class "text-amber-800 dark:text-amber-200"}
                           "AI is analyzing the transcript to extract profile updates. "
                           "This typically takes about 2-3 minutes.")))

                   ;; Analysis review (when completed)
                   (when analysis-completed?
                     ($ :div {:class "space-y-4"}
                        ($ :div {:class "flex items-center gap-2"}
                           ($ CheckCircle2 {:class "h-5 w-5 text-green-600"})
                           ($ :h2 {:class "text-xl font-semibold"} "Transcript Analysis Ready for Review"))
                        ($ transcript-analysis-review {:analysis-result meeting-analysis-result
                                                       :student-id (parse-uuid student-id)
                                                       :meeting-id meeting-id
                                                       :meeting meeting
                                                       :on-applied handle-upload-complete})))

                   ;; Agenda sections
                   (for [[idx section] (map-indexed vector agenda-sections)]
                     ($ agenda-section {:key idx
                                       :section section
                                       :notes (get notes (:id section))
                                       :on-notes-change handle-notes-change
                                       :disabled? is-completed?
                                       :student-id (parse-uuid student-id)
                                       :api-client api-client})))))))))
