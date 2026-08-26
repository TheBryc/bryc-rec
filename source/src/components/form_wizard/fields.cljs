(ns components.form-wizard.fields
  "Field renderer components for form wizard"
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["/gen/shadcn/components/ui/textarea" :as textarea]
            ["/gen/shadcn/components/ui/select" :as select]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/radio-group" :as radio]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/date-picker" :as date-picker]
            [clojure.string :as str]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

;; Phone number formatting utilities
(defn format-phone-number
  "Format phone number as (XXX) XXX-XXXX as user types"
  [value]
  (let [digits-only (str/replace (or value "") #"[^\d]" "")]
    (cond
      (empty? digits-only) ""
      (<= (count digits-only) 3) digits-only
      (<= (count digits-only) 6) (str "(" (subs digits-only 0 3) ") " (subs digits-only 3))
      :else (str "(" (subs digits-only 0 3) ") " (subs digits-only 3 6) "-" (subs digits-only 6 10)))))

(defn unformat-phone-number
  "Extract just the digits from a formatted phone number for storage"
  [formatted-value]
  (str/replace (or formatted-value "") #"[^\d]" ""))

;; Helper for field options
(defn get-field-options
  "Get options for a field from CRM field-options map.
   field-options is a map of {:field-slug [options]}.
   For sub-fields, also checks parent-field's sub-schema."
  [field-options field-slug sub-field-key]
  (if sub-field-key
    ;; Look up sub-field options from parent field's sub-schema
    (get-in field-options [field-slug :sub-fields sub-field-key] [])
    ;; Look up top-level field options
    (get-in field-options [field-slug :options] [])))

;; Legacy helper for enum options (deprecated, use get-field-options)
(defn get-enum-options
  "Extract enum options from schema - DEPRECATED, use get-field-options"
  [utils-schemas schema-key]
  (let [schema (get utils-schemas schema-key)]
    (cond
      ;; Handle [:enum option1 option2 ...]
      (and (vector? schema) (= :enum (first schema)))
      (rest schema)

      ;; Handle direct vector of options
      (vector? schema)
      schema

      ;; Handle set of options
      (set? schema)
      (vec schema)

      :else
      [])))

;; Basic field components
(defui text-field [{:keys [field value on-change on-blur]}]
  (let [is-phone? (= (:input-type field) "tel")
        is-number? (= (:input-type field) "number")
        integer-only? (= (:step field) "1")
        display-value (if is-phone? (format-phone-number value) (or value ""))
        handle-change (fn [raw-value]
                        (cond
                          is-phone?
                          (on-change (unformat-phone-number raw-value))

                          is-number?
                          (when (or (= raw-value "")
                                    (if integer-only?
                                      (re-matches #"\d*" raw-value)
                                      (re-matches #"\d*(\.\d*)?" raw-value)))
                            (on-change raw-value))

                          :else
                          (on-change raw-value)))]
    ($ input/Input
       (cond-> {:type (:input-type field "text")
                :inputMode (when is-number? (if integer-only? "numeric" "decimal"))
                :placeholder (:placeholder field (:label field))
                :value display-value
                :maxLength (if is-phone? 14 (:max-length field))
                :onChange #(handle-change (.. % -target -value))
                :onBlur #(when on-blur (on-blur))}
         (:step field) (assoc :step (:step field))
         (:min field) (assoc :min (:min field))
         (:max field) (assoc :max (:max field))))))

(defui textarea-field [{:keys [field value on-change on-blur]}]
  ($ textarea/Textarea
     {:placeholder (:placeholder field (:label field))
      :value (or value "")
      :maxLength (:max-length field)
      :rows (:rows field 4)
      :onChange #(on-change (.. % -target -value))
      :onBlur #(when on-blur (on-blur))}))

(defui date-field [{:keys [field value on-change _on-blur]}]
  ;; Note: on-blur is intentionally not used for date fields because:
  ;; 1. Date fields save immediately on change (like select/radio/checkbox)
  ;; 2. The blur handler would have a stale value from the closure
  ($ date-picker/DatePicker
     {:value (or value "")
      :placeholder (:placeholder field "Pick a date")
      :onChange on-change
      :fromYear (:from-year field)
      :toYear (:to-year field)}))

(defui select-field [{:keys [field value on-change]}]
  (let [options (vec (:options field []))
        to-string (fn [v] (if (string? v) v (str v)))
        ;; Handle values that come back as sets from CRM (unwrap single-element sets)
        normalized-value (cond
                           (set? value) (first value)
                           :else value)]
    ($ select/Select
       {:value (to-string (or normalized-value ""))
        :onValueChange on-change}
       ($ select/SelectTrigger
          ($ select/SelectValue {:placeholder (:placeholder field "Select...")}))
       ($ select/SelectContent
          (for [option options]
            (let [opt-str (to-string option)]
              ($ select/SelectItem
                 {:key opt-str :value opt-str}
                 opt-str)))))))

(defui radio-field [{:keys [field value on-change]}]
  (let [options (vec (:options field []))
        to-string (fn [v] (if (string? v) v (str v)))
        ;; Handle values that come back as sets from CRM (unwrap single-element sets)
        normalized-value (cond
                           (set? value) (first value)
                           :else value)]
    ($ radio/RadioGroup
       {:value (to-string (or normalized-value ""))
        :onValueChange on-change}
       (for [option options]
         (let [opt-str (to-string option)]
           ($ :div {:key opt-str :class "flex items-center space-x-2"}
              ($ radio/RadioGroupItem {:value opt-str :id opt-str})
              ($ :label {:htmlFor opt-str :class "text-sm"} opt-str)))))))

(defui checkbox-field [{:keys [field value on-change]}]
  (let [selected-set (if (set? value) value (set value))
        options (vec (:options field []))
        to-string (fn [v] (if (string? v) v (str v)))]
    ($ :div {:class "space-y-2"}
       (for [option options]
         (let [opt-str (to-string option)]
           ($ :div {:key opt-str :class "flex items-center space-x-2"}
              ($ checkbox/Checkbox
                 {:checked (contains? selected-set option)
                  :onCheckedChange #(let [new-set (if %
                                                    (conj selected-set option)
                                                    (disj selected-set option))]
                                      (on-change new-set))})
              ($ :label {:class "text-sm"} opt-str)))))))

(defui boolean-checkbox-field [{:keys [field value on-change]}]
  ($ :div {:class "flex items-start space-x-2"}
     ($ checkbox/Checkbox
        {:checked (true? value)
         :onCheckedChange #(on-change (true? %))})
     ($ :label {:class "text-sm leading-snug"}
        (:checkbox-label field (:label field)))))

(defui either-or-field [{:keys [field value on-change on-change-immediate on-blur]}]
  (let [is-other? (and (map? value) (:other value))
        selected-value (if is-other? "other" (if (string? value) value (str (or value ""))))
        to-string (fn [v] (if (string? v) v (str v)))
        options (->> (:options field [])
                     (remove #(#{"Other" "Other (write-in)"} (to-string %)))
                     vec)]
    ($ :div {:class "space-y-2"}
       ($ select/Select
          {:value selected-value
           :onValueChange #(if (= % "other")
                             (on-change {:other ""})  ; Don't save when selecting "other", just update state
                             (on-change-immediate %))}  ; Save immediately when selecting a regular option
          ($ select/SelectTrigger
             ($ select/SelectValue {:placeholder (:placeholder field "Select...")}))
          ($ select/SelectContent
             (concat
               (for [option options]
                 (let [opt-str (to-string option)]
                   ($ select/SelectItem
                      {:key opt-str :value opt-str}
                      opt-str)))
               [($ select/SelectItem
                   {:key "other" :value "other"}
                   "Other")])))
       (when is-other?
         ($ input/Input
            {:placeholder "Please specify..."
             :value (:other value "")
             :onChange #(on-change {:other (.. % -target -value)})  ; Just update state, don't save
             :onBlur #(when on-blur (on-blur))})))))

;; Dynamic list sub-components
(defui dynamic-list-item-field [{:keys [sub-field item-value on-change on-change-immediate on-blur]}]
  (let [field-key (:key sub-field)
        value (get item-value field-key)]
    (case (:type sub-field)
      :text
      ($ text-field
         {:field sub-field
          :value value
          :on-change #(on-change field-key %)
          :on-blur #(when on-blur (on-blur field-key value))})

      :textarea
      ($ textarea-field
         {:field sub-field
          :value value
          :on-change #(on-change field-key %)
          :on-blur #(when on-blur (on-blur field-key value))})

      :select
      ($ select-field
         {:field sub-field
          :value value
          :on-change #(on-change-immediate field-key %)})

      :radio
      ($ radio-field
         {:field sub-field
          :value value
          :on-change #(on-change-immediate field-key %)})

      :checkbox
      ($ checkbox-field
         {:field sub-field
          :value value
          :on-change #(on-change-immediate field-key %)})

      :boolean-checkbox
      ($ boolean-checkbox-field
         {:field sub-field
          :value value
          :on-change #(on-change-immediate field-key %)})

      :either-or
      ($ either-or-field
         {:field sub-field
          :value value
          :on-change #(on-change field-key %)  ; For text input changes
          :on-change-immediate #(on-change-immediate field-key %)  ; For dropdown changes
          :on-blur #(when on-blur (on-blur field-key value))})

      ($ :div {:class "text-red-500"} (str "Unknown sub-field type: " (:type sub-field))))))

(defui dynamic-list-item [{:keys [item sub-fields on-change on-save-with-updated-items on-remove expanded? on-toggle-expand index item-label on-blur]}]
  ($ card/Card {:class "mb-2"}
     ($ card/CardHeader {:class "py-2 px-3"}
        ($ :div {:class "flex items-center justify-between"}
           ($ :button
              {:onClick on-toggle-expand
               :class "flex-1 text-left flex items-center space-x-2 hover:text-blue-600"}
              ($ :span {:class "text-gray-400"} (if expanded? "▼" "▶"))
              ($ :span {:class "font-medium"} (or item-label (str "Item " (inc index)))))
           ($ button/Button
              {:variant "ghost"
               :size "sm"
               :onClick on-remove
               :class "text-red-500 hover:text-red-700"}
              "Remove")))
     (when expanded?
       ($ card/CardContent {:class "pt-0"}
          ($ :div {:class "space-y-4"}
             ;; sub-fields already have :options populated from process-groups-with-answers
             (for [sub-field sub-fields]
               ($ :div {:key (:key sub-field)}
                  ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"}
                     (:label sub-field)
                     (when-not (:optional sub-field)
                       ($ :span {:class "text-red-500 ml-1"} "*")))
                  ($ dynamic-list-item-field
                     {:sub-field sub-field
                      :item-value item
                      :on-change (fn [field-key value]
                                   (on-change (assoc item field-key value)))
                      :on-change-immediate (fn [field-key value]
                                             (let [new-item (assoc item field-key value)]
                                               (on-change new-item)
                                               (when on-save-with-updated-items
                                                 (on-save-with-updated-items new-item))))
                      :on-blur #(when on-blur (on-blur))}))))))))

(defui dynamic-list-field [{:keys [field value on-change on-blur-with-value]}]
  (let [[expanded-items set-expanded-items!] (use-state #{})
        items (or value [])
        {:keys [min-items max-items add-button-text empty-message
                sub-fields item-label-fn collapsed-preview-fn]
         :or {min-items 0
              max-items 10
              add-button-text "Add Item"
              empty-message "No items added yet"
              item-label-fn (fn [item idx] (str "Item " (inc idx)))}} field

        add-item! (fn []
                    (when (< (count items) max-items)
                      (let [new-index (count items)
                            new-items (conj (vec items) {})]
                        (on-change new-items)
                        (set-expanded-items! #(conj % new-index))
                        ;; Don't save when just adding empty item - wait for actual data
                        )))

        remove-item! (fn [index]
                       (let [new-items (vec (concat (subvec items 0 index)
                                                    (subvec items (inc index))))]
                         (on-change new-items)
                         (set-expanded-items! #(disj % index))
                         ;; Trigger save with the new value
                         (when on-blur-with-value (on-blur-with-value new-items))))

        update-item! (fn [index new-item]
                       (on-change (assoc (vec items) index new-item)))]

    ($ :div {:class "space-y-2"}
       (when (empty? items)
         ($ :div {:class "text-gray-500 italic text-sm py-4 text-center border-2 border-dashed border-gray-300 rounded-lg"}
            empty-message))

       ($ :div {:class "space-y-2"}
          (map-indexed
           (fn [idx item]
             ($ dynamic-list-item
                {:key idx
                 :index idx
                 :item item
                 :sub-fields sub-fields
                 :item-label (item-label-fn item idx)
                 :expanded? (contains? expanded-items idx)
                 :on-toggle-expand #(set-expanded-items!
                                     (fn [items]
                                       (if (contains? items idx)
                                         (disj items idx)
                                         (conj items idx))))
                 :on-change #(update-item! idx %)
                 :on-save-with-updated-items (fn [updated-item]
                                               (let [new-items (assoc (vec items) idx updated-item)]
                                                 (when on-blur-with-value (on-blur-with-value new-items))))
                 :on-remove #(remove-item! idx)
                 :on-blur #(when on-blur-with-value (on-blur-with-value items))}))
           items))

       (when (< (count items) max-items)
         ($ button/Button
            {:variant "outline"
             :onClick add-item!
             :class "w-full"}
            ($ :span {:class "mr-2"} "+")
            add-button-text))

       (when (and (> min-items 0) (< (count items) min-items))
         ($ :div {:class "text-amber-600 text-sm mt-2"}
            (str "Please add at least " min-items " item" (when (> min-items 1) "s")))))))

;; =============================================================================
;; File upload field
;; =============================================================================

(defui file-field
  "File upload field with presigned URL upload to S3.
   Props:
   - field: field config with :accept, :max-size-mb
   - value: current value (file-id string or nil)
   - on-change: callback with new value
   - session-id: current wizard session ID"
  [{:keys [field value on-change session-id]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        [upload-state set-upload-state!] (use-state nil) ;; nil | :uploading | :uploaded | :error
        [file-name set-file-name!] (use-state nil)
        [error-msg set-error-msg!] (use-state nil)
        accept (or (:accept field) ".pdf,.jpg,.jpeg,.png")
        max-size-mb (or (:max-size-mb field) 10)
        max-size-bytes (* max-size-mb 1024 1024)

        handle-file (fn [file]
                      (when (and file api-client)
                        (if (> (.-size file) max-size-bytes)
                          (do
                            (set-error-msg! (str "File too large. Maximum size is " max-size-mb "MB."))
                            (set-upload-state! :error))
                          (do
                            (set-error-msg! nil)
                            (set-upload-state! :uploading)
                            (set-file-name! (.-name file))
                            (go
                              (let [presign-resp (<! (api/command api-client
                                                       {:command/name :intake/presign-file-upload
                                                        :session-id session-id
                                                        :field-key (name (:key field))
                                                        :content-type (.-type file)}))]
                                (if (anomaly? presign-resp)
                                  (do
                                    (set-upload-state! :error)
                                    (set-error-msg! "Could not get upload URL. Please try again."))
                                  (let [upload-url (:url presign-resp)
                                        file-id (:file-id presign-resp)]
                                    (-> (js/fetch upload-url
                                          (clj->js {:method "PUT"
                                                    :body file
                                                    :headers {"Content-Type" (.-type file)}}))
                                        (.then (fn [resp]
                                                 (if (.-ok resp)
                                                   (do
                                                     (set-upload-state! :uploaded)
                                                     (on-change (str file-id)))
                                                   (do
                                                     (set-upload-state! :error)
                                                     (set-error-msg! "Upload failed. Please try again.")))))
                                        (.catch (fn [_]
                                                  (set-upload-state! :error)
                                                  (set-error-msg! "Upload failed. Please try again."))))))))))))

        handle-input-change (fn [e]
                              (let [files (.. e -target -files)]
                                (when (> (.-length files) 0)
                                  (handle-file (aget files 0)))))

        handle-drop (fn [e]
                      (.preventDefault e)
                      (let [files (.. e -dataTransfer -files)]
                        (when (> (.-length files) 0)
                          (handle-file (aget files 0)))))

        handle-remove (fn []
                        (set-upload-state! nil)
                        (set-file-name! nil)
                        (set-error-msg! nil)
                        (on-change nil))]

    (cond
      ;; Uploaded state — show file info
      (or (= upload-state :uploaded) (and value (nil? upload-state)))
      ($ :div {:class "flex items-center gap-3 p-3 bg-green-50 border border-green-200 rounded-lg"}
         ($ :span {:class "text-green-600 text-lg"} "✓")
         ($ :div {:class "flex-1 min-w-0"}
            ($ :p {:class "text-sm font-medium text-green-800 truncate"}
               (or file-name (str "File uploaded (" value ")")))
            ($ :p {:class "text-xs text-green-600"} "Uploaded successfully"))
         ($ button/Button {:variant "ghost" :size "sm" :onClick handle-remove}
            "Remove"))

      ;; Uploading state
      (= upload-state :uploading)
      ($ :div {:class "flex items-center gap-3 p-4 border-2 border-dashed border-blue-300 rounded-lg bg-blue-50"}
         ($ :div {:class "animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"})
         ($ :p {:class "text-sm text-blue-700"} (str "Uploading " (or file-name "file") "...")))

      ;; Error state
      (= upload-state :error)
      ($ :div
         ($ :div {:class "p-4 border-2 border-dashed border-red-300 rounded-lg bg-red-50 text-center"}
            ($ :p {:class "text-sm text-red-700 mb-2"} (or error-msg "Upload failed"))
            ($ button/Button {:variant "outline" :size "sm"
                              :onClick #(do (set-upload-state! nil) (set-error-msg! nil))}
               "Try Again")))

      ;; Default — drop zone
      :else
      ($ :div {:class "relative"
               :onDragOver #(.preventDefault %)
               :onDrop handle-drop}
         ($ :label {:class "flex flex-col items-center justify-center p-6 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-blue-400 hover:bg-blue-50/50 transition-colors"}
            ($ :span {:class "text-2xl mb-2"} "📄")
            ($ :p {:class "text-sm font-medium text-gray-700"} "Drop file here or click to browse")
            ($ :p {:class "text-xs text-gray-500 mt-1"}
               (str "Accepted: " accept " (max " max-size-mb "MB)"))
            ($ :input {:type "file"
                       :accept accept
                       :class "absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                       :onChange handle-input-change}))))))
