(ns components.advisor.upload-grade-transcript-modal
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/label" :as label]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

(defui upload-grade-transcript-modal [{:keys [open? on-close student on-refresh-student on-upload-complete]}]
  (let [[file set-file!] (use-state nil)
        [file-name set-file-name!] (use-state "")
        [uploading? set-uploading!] (use-state false)
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
                                                                  {:command/name :student-ops/presign-grade-transcript-url}))]
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
                                                  :headers #js {"Content-Type" "application/pdf"}})
                                     (.then (fn [response]
                                              (if (.-ok response)
                                                (do
                                                  ;; Confirm upload to backend
                                                  (go
                                                    (let [upload-response (<! (api/command api-client
                                                                                         {:command/name :student-ops/upload-grade-transcript
                                                                                          :student-id (:student/id student)
                                                                                          :file-id file-id}))]
                                                      (if (anomaly? upload-response)
                                                        (js/console.error "Failed to confirm upload:" upload-response)
                                                        (do
                                                          (js/console.log "Grade transcript uploaded successfully")
                                                          (when on-refresh-student
                                                            (on-refresh-student (:student/id student)))
                                                          (if on-upload-complete
                                                            (on-upload-complete)
                                                            (on-close))))
                                                      (set-uploading! false))))
                                                (do
                                                  (js/console.error "Failed to upload file")
                                                  (set-uploading! false)))))
                                     (.catch (fn [error]
                                               (js/console.error "Upload error:" error)
                                               (set-uploading! false))))))))))]

    ($ dialog/Dialog {:open open?
                      :on-open-change on-close}
       ($ dialog/DialogContent {:class "sm:max-w-[425px]"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle "Upload Grade Transcript")
             ($ dialog/DialogDescription
                (str "Upload the grade transcript for " (:student/name student))))
          ($ :div {:class "grid gap-4 py-4"}
             ($ :div {:class "grid gap-2"}
                ($ label/Label {:html-for "transcript"} "Transcript File")
                ($ :input {:id "transcript"
                          :type "file"
                          :accept ".pdf"
                          :on-change handle-file-change
                          :class "w-full p-2 border rounded"})
                (when file-name
                  ($ :p {:class "text-sm text-muted-foreground mt-2"}
                     "Selected: " file-name))))
          ($ dialog/DialogFooter
             ($ button/Button {:variant "outline"
                              :on-click on-close
                              :disabled uploading?}
                "Cancel")
             ($ button/Button {:on-click handle-upload
                              :disabled (or (not file) uploading?)}
                (if uploading? "Uploading..." "Upload")))))))
