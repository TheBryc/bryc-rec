(ns components.advisor.transcript-editor
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [clojure.string]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/table" :as table]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            [components.context.interface :as context]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.advisor.upload-grade-transcript-modal :refer [upload-grade-transcript-modal]]
            [store.advisor.transcript-editor.events :as transcript-events]
            [store.advisor.transcript-editor.subs :as transcript-subs]))

(defn format-date [date-str]
  (when date-str
    (try
      (let [date (js/Date. date-str)]
        (.toLocaleDateString date "en-US" #js {:month "short"
                                               :day "numeric"
                                               :year "numeric"
                                               :hour "2-digit"
                                               :minute "2-digit"}))
      (catch :default _ date-str))))

(defui transcript-history-table [{:keys [history current-transcript-id api-client on-delete on-view-pdf deleting?]}]
  (let [visible-history (filterv #(not (:deleted? %)) history)]
    (when (seq visible-history)
      ($ :div {:class "mb-8"}
         ($ :h2 {:class "text-lg font-semibold mb-4"} "Transcript History")
         ($ table/Table
            ($ table/TableHeader
               ($ table/TableRow
                  ($ table/TableHead {:class "w-48"} "Uploaded")
                  ($ table/TableHead {:class "w-24"} "Status")
                  ($ table/TableHead {:class "w-32"} "Actions")))
            ($ table/TableBody
               (for [entry visible-history]
                 (let [transcript-id (:transcript-id entry)
                       is-current? (= transcript-id current-transcript-id)]
                   ($ table/TableRow {:key (str transcript-id)
                                      :class (when is-current? "bg-primary/5")}
                      ($ table/TableCell {:class "w-48"}
                         (format-date (:uploaded-at entry)))
                      ($ table/TableCell {:class "w-24"}
                         (if is-current?
                           ($ :span {:class "text-primary font-medium"} "Current")
                           ($ :span {:class "text-muted-foreground"} "-")))
                      ($ table/TableCell {:class "w-32"}
                         ($ :div {:class "flex items-center gap-2"}
                            ($ button/Button {:variant "ghost"
                                              :size "sm"
                                              :on-click #(on-view-pdf (:file-id entry))}
                               "View PDF")
                            ($ button/Button {:variant "ghost"
                                              :size "sm"
                                              :class "text-destructive hover:text-destructive"
                                              :disabled deleting?
                                              :on-click #(on-delete transcript-id)}
                               "Delete"))))))))))))

(defui delete-confirm-dialog [{:keys [open? on-close on-confirm deleting?]}]
  ($ dialog/Dialog {:open open?
                    :on-open-change (fn [open] (when-not open (on-close)))}
     ($ dialog/DialogContent {:class "sm:max-w-[425px]"}
        ($ dialog/DialogHeader
           ($ dialog/DialogTitle "Delete Transcript")
           ($ dialog/DialogDescription
              "Are you sure you want to delete this transcript? This action cannot be undone."))
        ($ dialog/DialogFooter
           ($ button/Button {:variant "outline"
                            :on-click on-close
                            :disabled deleting?}
              "Cancel")
           ($ button/Button {:variant "destructive"
                            :on-click on-confirm
                            :disabled deleting?}
              (if deleting? "Deleting..." "Delete"))))))

(defui transcript-editor [{:keys [current-match]}]
  (let [student-id (get-in current-match [:query-params :student-id])
        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        ;; Subscriptions
        loading? (use-subscribe [::transcript-subs/loading?])
        student (use-subscribe [::transcript-subs/student])
        transcript (use-subscribe [::transcript-subs/transcript])
        transcript-history (use-subscribe [::transcript-subs/transcript-history])
        current-transcript-id (use-subscribe [::transcript-subs/current-transcript-id])
        has-changes? (use-subscribe [::transcript-subs/has-changes?])
        saving? (use-subscribe [::transcript-subs/saving?])
        save-success? (use-subscribe [::transcript-subs/save-success?])
        upload-modal-open? (use-subscribe [::transcript-subs/upload-modal-open?])
        deleting? (use-subscribe [::transcript-subs/deleting?])
        delete-confirm-id (use-subscribe [::transcript-subs/delete-confirm-id])

        ;; Event handlers
        toggle-weighted (fn [idx]
                         (rf/dispatch [::transcript-events/toggle-weighted idx]))

        toggle-core (fn [idx]
                     (rf/dispatch [::transcript-events/toggle-core idx]))

        handle-save (fn []
                     (rf/dispatch [::transcript-events/save-transcript api-client]))

        handle-cancel (fn []
                       (rf/dispatch [::transcript-events/cancel-edit]))

        handle-back (fn []
                     (navigate! :hub-student-detail {:student-id student-id}))

        handle-upload-modal-open (fn [open?]
                                   (rf/dispatch [::transcript-events/set-upload-modal-open open?]))

        handle-delete (fn [transcript-id]
                        (rf/dispatch [::transcript-events/set-delete-confirm transcript-id]))

        handle-delete-confirm (fn []
                                (rf/dispatch [::transcript-events/delete-transcript delete-confirm-id api-client]))

        handle-delete-cancel (fn []
                               (rf/dispatch [::transcript-events/set-delete-confirm nil]))

        handle-view-pdf (fn [file-id]
                          (rf/dispatch [::transcript-events/view-pdf file-id api-client]))]

    ;; Load transcript on mount
    (use-effect
      (fn []
        (rf/dispatch [::transcript-events/load-transcript (parse-uuid student-id) api-client])
        ;; Cleanup on unmount
        (fn []
          (rf/dispatch [::transcript-events/cleanup])))
      [student-id api-client])

    ($ :div {:class "min-h-screen bg-background flex flex-col"}
       ;; Action bar — only when there's a transcript to work with
       (when (or transcript (seq transcript-history))
         ($ :header {:class "border-b flex-shrink-0"}
            ($ :div {:class "flex h-14 items-center px-8 justify-end"}
               ($ :div {:class "flex items-center gap-3"}
                  (when save-success?
                    ($ :div {:class "text-sm text-green-600 font-medium flex items-center gap-1"}
                       ($ :span "\u2713")
                       ($ :span "Saved successfully")))
                  (when has-changes?
                    ($ :<>
                       ($ button/Button {:variant "outline"
                                        :on-click handle-cancel
                                        :disabled saving?}
                          "Cancel")
                       ($ button/Button {:on-click handle-save
                                        :disabled saving?}
                          (if saving? "Saving..." "Save"))))
                  ($ button/Button {:variant "outline"
                                   :on-click #(handle-upload-modal-open true)}
                     "Upload New")))))

       ;; Main content
       ($ :main {:class "flex-1 overflow-auto p-8"}
          (if loading?
            ($ :div {:class "flex items-center justify-center h-full"}
               ($ :p {:class "text-muted-foreground"} "Loading transcript..."))

            ($ :div {:class "max-w-5xl mx-auto"}
               ;; Transcript History Table
               ($ transcript-history-table
                  {:history (or transcript-history [])
                   :current-transcript-id current-transcript-id
                   :api-client api-client
                   :on-delete handle-delete
                   :on-view-pdf handle-view-pdf
                   :deleting? deleting?})

               ;; Current Transcript Editor
               (when transcript
                 ($ :<>
                    ($ :h2 {:class "text-lg font-semibold mb-4"} "Current Transcript")
                    ($ table/Table
                       ($ table/TableHeader
                          ($ table/TableRow
                             ($ table/TableHead {:class "w-64"} "Course Name")
                             ($ table/TableHead {:class "w-20"} "Year")
                             ($ table/TableHead {:class "w-32"} "Grades")
                             ($ table/TableHead {:class "w-20"} "Credit")
                             ($ table/TableHead {:class "w-20"} "Earned")
                             ($ table/TableHead {:class "w-24"} "Weighted")
                             ($ table/TableHead {:class "w-20"} "Core")))
                       ($ table/TableBody
                          (for [[idx course] (map-indexed vector transcript)]
                            ($ table/TableRow {:key idx
                                              :class (when (even? idx) "bg-muted/30")}
                               ($ table/TableCell {:class "font-medium w-64"} (:name course))
                               ($ table/TableCell {:class "w-20"} (:year course))
                               ($ table/TableCell {:class "w-32"} (clojure.string/join ", " (:grades course)))
                               ($ table/TableCell {:class "w-20"} (:credit course))
                               ($ table/TableCell {:class "w-20"} (:earned course))
                               ($ table/TableCell {:class "w-24"}
                                  ($ :input {:key (str "weighted-" idx "-" (:weighted course))
                                            :type "checkbox"
                                            :checked (:weighted course)
                                            :on-change #(toggle-weighted idx)
                                            :class "h-4 w-4 cursor-pointer"}))
                               ($ table/TableCell {:class "w-20"}
                                  ($ :input {:key (str "core-" idx "-" (:core course))
                                            :type "checkbox"
                                            :checked (:core course)
                                            :on-change #(toggle-core idx)
                                            :class "h-4 w-4 cursor-pointer"}))))))))

               ;; Empty state when no current transcript
               (when (and (not transcript) (empty? transcript-history))
                 ($ :div {:class "flex flex-col items-center justify-center py-20 gap-3"}
                    ($ :div {:class "rounded-full bg-muted p-4 mb-2"}
                       ($ :svg {:class "w-8 h-8 text-muted-foreground" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "1.5"}
                          ($ :path {:strokeLinecap "round" :strokeLinejoin "round"
                                    :d "M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z"})))
                    ($ :h3 {:class "text-lg font-semibold"} "No Transcript Yet")
                    ($ :p {:class "text-sm text-muted-foreground text-center max-w-sm"}
                       "Upload a grade transcript PDF to get started. The system will extract courses, grades, and credits automatically.")
                    ($ button/Button {:on-click #(handle-upload-modal-open true)}
                       "Upload Transcript"))))))

       ;; Delete confirmation dialog
       ($ delete-confirm-dialog
          {:open? (some? delete-confirm-id)
           :on-close handle-delete-cancel
           :on-confirm handle-delete-confirm
           :deleting? deleting?})

       ;; Upload modal
       (when student
         ($ upload-grade-transcript-modal
            {:open? upload-modal-open?
             :on-close #(handle-upload-modal-open false)
             :student student
             :on-refresh-student #(rf/dispatch [::transcript-events/load-transcript (parse-uuid student-id) api-client])})))))
