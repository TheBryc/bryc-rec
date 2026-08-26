(ns components.crm.profile-photo
  "Managed CRM profile photos. CRM stores a file UUID; this component resolves
   short-lived GET URLs and uploads directly to private S3 with a presigned PUT."
  (:require [uix.core :refer [defui $ use-callback use-effect use-state]]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]
            [clojure.string :as str]))

(def allowed-types #{"image/jpeg" "image/png" "image/webp"})
(def max-photo-bytes (* 5 1024 1024))

(defn initials [full-name]
  (->> (str/split (str/trim (or full-name "")) #"\s+")
       (remove str/blank?)
       (take 2)
       (map #(str/upper-case (subs % 0 1)))
       (apply str)))

(defui profile-photo
  [{:keys [api-client contact-id file-id name editable? on-change size-class]
    :or {size-class "h-20 w-20"}}]
  (let [[current-id set-current-id!] (use-state file-id)
        [photo-url set-photo-url!] (use-state nil)
        [status set-status!] (use-state :idle)
        [error set-error!] (use-state nil)
        [modal-open? set-modal-open!] (use-state false)
        load-url! (use-callback
                   (fn [id]
                     (if (and api-client contact-id id)
                       (go
                         (let [result (<! (api/query api-client
                                                    {:query/name :crm/photo-url
                                                     :contact-id contact-id
                                                     :field-slug "profile-photo"}))]
                           (if (anomaly? result)
                             (set-error! "Could not load photo")
                             (set-photo-url! (:url result)))))
                       (set-photo-url! nil)))
                   [api-client contact-id])]
    (use-effect
     (fn []
       (set-current-id! file-id)
       (load-url! file-id)
       js/undefined)
     [file-id load-url!])
    (letfn [(fail! [message]
              (set-status! :error)
              (set-error! message))
            (finalize! [presign]
              (go
                (let [saved (<! (api/command api-client
                                             {:command/name :crm/finalize-photo-upload
                                              :contact-id contact-id
                                              :field-slug "profile-photo"
                                              :file-id (:file-id presign)}))]
                  (if (anomaly? saved)
                    (fail! "The uploaded file could not be saved.")
                    (do
                      (set-current-id! (:file-id presign))
                      (set-status! :idle)
                      (load-url! (:file-id presign))
                      (set-modal-open! false)
                      (when on-change (on-change (:file-id presign))))))))
            (upload! [file]
              (cond
                (not (allowed-types (.-type file)))
                (fail! "Choose a JPEG, PNG, or WebP image.")

                (> (.-size file) max-photo-bytes)
                (fail! "Photo must be 5 MB or smaller.")

                :else
                (do
                  (set-status! :uploading)
                  (set-error! nil)
                  (go
                    (let [presign (<! (api/command api-client
                                                   {:command/name :crm/presign-photo-upload
                                                    :contact-id contact-id
                                                    :field-slug "profile-photo"
                                                    :content-type (.-type file)
                                                    :content-length (.-size file)}))]
                      (if (anomaly? presign)
                        (fail! "Could not prepare the photo upload.")
                        (-> (js/fetch (:url presign)
                                      #js {:method "PUT"
                                           :body file
                                           :headers #js {"Content-Type" (.-type file)}})
                            (.then (fn [response]
                                     (if (.-ok response)
                                       (finalize! presign)
                                       (fail! "Photo upload failed."))))
                            (.catch (fn [_] (fail! "Photo upload failed."))))))))))
            (remove! []
              (set-status! :uploading)
              (go
                (let [result (<! (api/command api-client
                                             {:command/name :crm/remove-photo
                                              :contact-id contact-id
                                              :field-slug "profile-photo"}))]
                  (if (anomaly? result)
                    (fail! "Could not remove photo.")
                    (do
                      (set-current-id! nil)
                      (set-photo-url! nil)
                      (set-status! :idle)
                      (set-modal-open! false)
                      (when on-change (on-change nil)))))))]
      (let [avatar
            ($ :div {:class (str size-class " shrink-0 overflow-hidden rounded-full bg-teal-600 text-white flex items-center justify-center font-semibold text-xl ring-2 ring-teal-100")}
               (if photo-url
                 ($ :img {:src photo-url :alt name :class "h-full w-full object-cover"
                          :on-error #(set-photo-url! nil)})
                 (or (initials name) "?")))]
        ($ :div {:class "flex items-center gap-4"}
           (if editable?
             ($ :button {:type "button"
                         :class "group relative shrink-0 cursor-pointer rounded-full focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-600 focus-visible:ring-offset-2"
                         :title (if current-id "Manage photo" "Add photo")
                         :on-click #(do (set-error! nil) (set-modal-open! true))}
                avatar
                ($ :span {:class "absolute inset-0 flex items-center justify-center rounded-full bg-black/45 text-xs font-medium text-white opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"}
                   "Edit")
                ($ :span {:class "sr-only"} (if current-id "Manage profile photo" "Add profile photo")))
             avatar)
           (when editable?
             ($ dialog/Dialog {:open modal-open? :on-open-change set-modal-open!}
                ($ dialog/DialogContent {:class "sm:max-w-md"}
                   ($ dialog/DialogHeader
                      ($ dialog/DialogTitle "Profile photo")
                      ($ dialog/DialogDescription
                         "Upload a JPEG, PNG, or WebP image up to 5 MB."))
                   ($ :div {:class "flex flex-col items-center gap-5 py-4"}
                      ($ :div {:class "h-32 w-32 overflow-hidden rounded-full bg-teal-600 text-white flex items-center justify-center text-3xl font-semibold ring-2 ring-teal-100"}
                         (if photo-url
                           ($ :img {:src photo-url :alt name :class "h-full w-full object-cover"
                                    :on-error #(set-photo-url! nil)})
                           (or (initials name) "?")))
                      ($ :label {:class "inline-flex cursor-pointer items-center rounded-md bg-teal-700 px-4 py-2 text-sm font-medium text-white hover:bg-teal-800"
                                 :aria-disabled (= :uploading status)}
                         (if (= :uploading status)
                           "Uploading…"
                           (if current-id "Choose a new photo" "Choose a photo"))
                         ($ :input {:type "file" :accept "image/jpeg,image/png,image/webp"
                                    :disabled (= :uploading status) :class "sr-only"
                                    :on-change #(when-let [file (aget (.. % -target -files) 0)]
                                                  (upload! file))}))
                      (when error ($ :p {:class "text-sm text-destructive"} error)))
                   ($ dialog/DialogFooter
                      (when current-id
                        ($ :button {:type "button"
                                    :class "rounded-md px-4 py-2 text-sm font-medium text-destructive hover:bg-destructive/10"
                                    :disabled (= :uploading status)
                                    :on-click remove!}
                           "Remove photo"))
                      ($ :button {:type "button"
                                  :class "rounded-md border px-4 py-2 text-sm font-medium hover:bg-muted"
                                  :disabled (= :uploading status)
                                  :on-click #(set-modal-open! false)}
                         "Cancel"))))))))))
