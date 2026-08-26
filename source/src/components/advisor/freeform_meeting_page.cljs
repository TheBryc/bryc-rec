(ns components.advisor.freeform-meeting-page
  (:require [uix.core :as uix :refer [defui $ use-effect use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/textarea" :as textarea]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["lucide-react" :refer [Upload Clock CheckCircle2]]
            [components.context.interface :as context]
            [components.advisor.upload-meeting-transcript-modal :refer [upload-meeting-transcript-modal]]
            [components.advisor.transcript-analysis-review :refer [transcript-analysis-review]]
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

(defui freeform-meeting-page [{:keys [student-name student-id meeting-id meeting meeting-notes meeting-status
                                      meeting-transcript meeting-analysis-status meeting-analysis-result
                                      on-complete on-back]}]
  (let [;; Subscribe to editor state (used for save status, not textarea value)
        saving? (use-subscribe [::editor-subs/saving?])
        last-saved (use-subscribe [::editor-subs/last-saved])

        ;; Local state for textarea value (prevents cursor jumping)
        [local-notes set-local-notes!] (use-state (or meeting-notes ""))
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

        handle-notes-change (fn [text]
                              (set-local-notes! text)
                              (rf/dispatch [::editor-events/update-notes text]))

        handle-complete (fn []
                          (rf/dispatch [::editor-events/complete-meeting])
                          ;; Callback after completion
                          (js/setTimeout #(when on-complete (on-complete local-notes)) 500))

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
            (let [initial (or @meeting-notes-ref "")]
              (set-local-notes! initial)
              (rf/dispatch [::editor-events/init-editor meeting-id initial api-client])))
          ;; Cleanup on unmount
          (fn []
            (rf/dispatch [::editor-events/cleanup])))
        [meeting-id api-client]))

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
             ($ :div {:class "max-w-5xl mx-auto"}
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
             ($ :div {:class "max-w-5xl mx-auto p-8"}
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

                   ;; Notes section
                   ($ :div {:class "space-y-4"}
                      ($ :div {:class "text-sm text-muted-foreground"}
                         "Use this space to take notes during your meeting.")
                      ($ textarea/Textarea {:value (or local-notes "")
                                           :on-change #(handle-notes-change (.. % -target -value))
                                           :placeholder "Enter your meeting notes here..."
                                           :disabled is-completed?
                                           :class "font-mono text-sm min-h-[600px] resize-none"})))))))))
