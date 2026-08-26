(ns components.advisor.upload-meeting-transcript-modal
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/label" :as label]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["lucide-react" :refer [Upload FileText Clock]]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

(defui upload-meeting-transcript-modal [{:keys [open? on-close student-id meeting-id on-upload-complete]}]
  (let [[file set-file!] (use-state nil)
        [file-name set-file-name!] (use-state "")
        [uploading? set-uploading!] (use-state false)
        [upload-started? set-upload-started!] (use-state false)
        ctx (context/use-context)
        api-client (:api/client ctx)

        handle-file-change (fn [e]
                            (let [files (.. e -target -files)]
                              (when (> (.-length files) 0)
                                (let [file (aget files 0)]
                                  (set-file! file)
                                  (set-file-name! (.-name file))))))

        handle-upload (fn []
                       (when file
                         (set-uploading! true)
                         (go
                           ;; First get presigned URL
                           (let [presign-response (<! (api/command api-client
                                                                  {:command/name :student-ops/presign-meeting-transcript-url}))]
                             (if (anomaly? presign-response)
                               (do
                                 (js/console.error "Failed to get presigned URL:" presign-response)
                                 (set-uploading! false))
                               (let [presigned-url (:url presign-response)
                                     file-id (:file-id presign-response)]
                                 ;; Upload file to S3
                                 (-> (js/fetch presigned-url
                                             #js {:method "PUT"
                                                  :body file
                                                  :headers #js {"Content-Type" "text/csv"}})
                                     (.then (fn [response]
                                              (if (.-ok response)
                                                (do
                                                  ;; Confirm upload to backend (auto-starts analysis)
                                                  (go
                                                    (let [upload-response (<! (api/command api-client
                                                                                         {:command/name :student-ops/upload-meeting-transcript
                                                                                          :student-id student-id
                                                                                          :meeting-id meeting-id
                                                                                          :file-id file-id}))]
                                                      (if (anomaly? upload-response)
                                                        (do
                                                          (js/console.error "Failed to confirm upload:" upload-response)
                                                          (set-uploading! false))
                                                        (do
                                                          (js/console.log "Meeting transcript uploaded, analysis started")
                                                          (set-upload-started! true)
                                                          (set-uploading! false)
                                                          (when on-upload-complete
                                                            (on-upload-complete)))))))
                                                (do
                                                  (js/console.error "Failed to upload file")
                                                  (set-uploading! false)))))
                                     (.catch (fn [error]
                                               (js/console.error "Upload error:" error)
                                               (set-uploading! false))))))))))]

    ($ dialog/Dialog {:open open?
                      :on-open-change (fn [open]
                                       (when-not open
                                         (set-upload-started! false)
                                         (set-file! nil)
                                         (set-file-name! "")
                                         (on-close)))}
       ($ dialog/DialogContent {:class "sm:max-w-[500px]"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle "Upload Meeting Transcript")
             ($ dialog/DialogDescription
                "Upload the transcript file (.csv) from your meeting"))

          (if upload-started?
            ;; Success state
            ($ :div {:class "py-6"}
               ($ alert/Alert {:class "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950"}
                  ($ Clock {:class "h-4 w-4 text-amber-600 dark:text-amber-400"})
                  ($ alert/AlertTitle {:class "text-amber-900 dark:text-amber-100"}
                     "Analysis Started")
                  ($ alert/AlertDescription {:class "text-amber-800 dark:text-amber-200"}
                     "Your transcript has been uploaded and AI analysis has started. "
                     "This typically takes about 2-3 minutes. "
                     "You will be notified when the analysis is complete."))
               ($ :div {:class "mt-4 flex justify-end"}
                  ($ button/Button {:on-click on-close}
                     "Done")))

            ;; Upload form
            ($ :<>
               ($ :div {:class "grid gap-4 py-4"}
                  ($ :div {:class "grid gap-2"}
                     ($ label/Label {:html-for "transcript"} "Transcript File (.csv)")
                     ($ :div {:class "flex items-center gap-2 p-4 border-2 border-dashed rounded-lg hover:border-primary/50 transition-colors"}
                        ($ FileText {:class "h-8 w-8 text-muted-foreground"})
                        ($ :div {:class "flex-1"}
                           ($ :input {:id "transcript"
                                     :type "file"
                                     :accept ".csv"
                                     :on-change handle-file-change
                                     :class "w-full cursor-pointer"})
                           (when file-name
                             ($ :p {:class "text-sm text-muted-foreground mt-1"}
                                "Selected: " file-name)))))

                  ($ :div {:class "text-sm text-muted-foreground bg-muted/50 p-3 rounded-lg"}
                     ($ :p {:class "font-medium mb-1"} "What happens next:")
                     ($ :ul {:class "list-disc list-inside space-y-1"}
                        ($ :li "AI will analyze the transcript for profile updates")
                        ($ :li "Analysis takes about 2-3 minutes")
                        ($ :li "You'll review and approve any changes"))))

               ($ dialog/DialogFooter
                  ($ button/Button {:variant "outline"
                                   :on-click on-close
                                   :disabled uploading?}
                     "Cancel")
                  ($ button/Button {:on-click handle-upload
                                   :disabled (or (not file) uploading?)}
                     ($ Upload {:class "h-4 w-4 mr-2"})
                     (if uploading? "Uploading..." "Upload & Analyze")))))))))
