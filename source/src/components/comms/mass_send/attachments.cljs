(ns components.comms.mass-send.attachments
  "Multi-file attachment uploader for the mass-send composer. Follows the same
   presign -> PUT -> done flow as components.advisor.upload-grade-transcript-modal,
   generalized to a list rather than a single file. Limits mirror the backend
   validation in communications-service (see core/commands.clj)."
  (:require [uix.core :as uix :refer [defui $ use-state use-ref]]
            [clojure.string :as str]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["lucide-react" :refer [Paperclip X Loader2]]))

(def max-count 1)
(def max-bytes (* 5 1024 1024))
(def allowed-types #{"image/jpeg" "image/png" "image/gif" "image/webp" "application/pdf"})

(defn- human-size [bytes]
  (cond
    (< bytes 1024) (str bytes " B")
    (< bytes (* 1024 1024)) (str (.toFixed (/ bytes 1024) 1) " KB")
    :else (str (.toFixed (/ bytes (* 1024 1024)) 1) " MB")))

(defui attachment-list
  [{:keys [api-client attachments on-add on-remove]}]
  (let [[uploading? set-uploading!] (use-state false)
        [error set-error!] (use-state nil)
        input-ref (use-ref nil)
        upload-file
        (fn [file]
          (cond
            (>= (count attachments) max-count)
            (set-error! (if (= 1 max-count)
                          "You can attach only 1 file"
                          (str "You can attach up to " max-count " files")))

            (not (contains? allowed-types (.-type file)))
            (set-error! "Only images (JPEG, PNG, GIF, WEBP) and PDF are allowed")

            (> (.-size file) max-bytes)
            (set-error! "Attachments must be 5MB or smaller")

            :else
            (do
              (set-error! nil)
              (set-uploading! true)
              (go
                (let [presign (<! (api/command
                                    api-client
                                    {:command/name :communications/presign-broadcast-attachment-url
                                     :file-name (.-name file)
                                     :content-type (.-type file)}))]
                  (if (anomaly? presign)
                    (do (set-error! "Could not prepare upload")
                        (set-uploading! false))
                    (let [{:keys [url file-id file-name]} presign]
                      (-> (js/fetch url #js {:method "PUT"
                                             :body file
                                             :headers #js {"Content-Type" (.-type file)}})
                          (.then (fn [response]
                                   (set-uploading! false)
                                   (if (.-ok response)
                                     (on-add {:file-id file-id
                                              :file-name file-name
                                              :content-type (.-type file)
                                              :size (.-size file)})
                                     (set-error! "Upload failed"))))
                          (.catch (fn [_]
                                    (set-uploading! false)
                                    (set-error! "Upload failed")))))))))))
        handle-change
        (fn [e]
          (let [files (.. e -target -files)]
            (dotimes [i (.-length files)]
              (upload-file (aget files i)))
            (set! (.. e -target -value) "")))]
    ($ :div {:class "space-y-2"}
       ($ :input {:ref input-ref
                  :type "file"
                  :multiple (> max-count 1)
                  :class "hidden"
                  :accept (str/join "," allowed-types)
                  :on-change handle-change})
       ($ button/Button
          {:type "button"
           :variant "outline"
           :size "sm"
           :disabled (or uploading? (>= (count attachments) max-count))
           :class "h-8 gap-1.5 px-2 text-xs"
           :on-click #(when-let [el @input-ref] (.click el))}
          (if uploading?
            ($ Loader2 {:class "size-3.5 animate-spin"})
            ($ Paperclip {:class "size-3.5"}))
          (if uploading? "Uploading…" "Attach file"))
       (when (seq attachments)
         ($ :ul {:class "space-y-1"}
            (for [a attachments]
              ($ :li {:key (str (:file-id a))
                      :class "flex items-center justify-between gap-2 rounded-md px-2 py-1 text-xs ring-1 ring-black/10"}
                 ($ :span {:class "truncate"}
                    (:file-name a) " · " (human-size (or (:size a) 0)))
                 ($ :button {:type "button"
                             :on-click #(on-remove (:file-id a))
                             :class "shrink-0 rounded-full p-0.5 hover:bg-gray-950/10"}
                    ($ X {:class "size-3"}))))))
       (when error
         ($ :p {:class "text-xs text-rose-700"} error))
       ($ :p {:class "text-xs text-muted-foreground"}
          (if (= 1 max-count)
            "One file, 5MB max. Images or PDF."
            (str "Up to " max-count " files, 5MB each. Images or PDF."))))))
