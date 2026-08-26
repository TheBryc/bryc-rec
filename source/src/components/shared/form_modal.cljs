(ns components.shared.form-modal
  "Data-driven form modal component. Eliminates boilerplate across add/edit modals."
  (:require [uix.core :as uix :refer [defui $ use-state use-callback]]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/label" :as label]))

(defui form-field-renderer
  "Renders a single form field based on its spec."
  [{:keys [field form-data handle-change submitting?]}]
  (let [{:keys [key label type placeholder options rows]} field
        field-id (name key)
        value (get form-data key "")]
    ($ :div {:className "grid gap-2"}
       ($ label/Label {:htmlFor field-id} label)
       (case type
         :select
         ($ :select
            {:id field-id
             :className "w-full px-3 py-2 text-sm border rounded-md"
             :value value
             :onChange (handle-change key)
             :disabled submitting?}
            ($ :option {:value ""} (or placeholder "Select..."))
            (for [opt options]
              ($ :option {:key opt :value opt} opt)))

         :textarea
         ($ :textarea
            {:id field-id
             :className "w-full px-3 py-2 text-sm border rounded-md"
             :placeholder placeholder
             :rows (or rows 3)
             :value value
             :onChange (handle-change key)
             :disabled submitting?})

         ;; Default: text/email/url input
         ($ :input
            {:id field-id
             :type (name (or type :text))
             :className "w-full px-3 py-2 text-sm border rounded-md"
             :placeholder placeholder
             :value value
             :onChange (handle-change key)
             :disabled submitting?})))))

(defui form-modal
  "Data-driven form modal. Handles dialog shell, form state, validation, error display, and footer.

   Props:
   - open?           — boolean, controls dialog visibility
   - submitting?     — boolean, disables form during submission
   - error           — string or nil, displayed as error banner
   - title           — string, dialog title
   - description     — string, dialog description
   - fields          — vector of field specs: {:key :kw :label \"Label\" :type :text/:email/:url/:select/:textarea :placeholder \"...\" :options [...] :rows n}
   - initial-state   — map of field-key → initial-value
   - valid?          — (fn [form-data]) → boolean
   - on-submit       — (fn [form-data]) called when form is submitted and valid
   - on-close        — (fn []) called when dialog is closed
   - submit-label    — string, e.g. \"Add Student\"
   - submitting-label — string, e.g. \"Adding...\"
   - max-width       — string, e.g. \"sm:max-w-[600px]\" (default \"sm:max-w-[500px]\")
   - title-extra     — optional string appended to title (e.g. dynamic context)"
  [{:keys [open? submitting? error title description fields initial-state
           valid? on-submit on-close submit-label submitting-label
           max-width title-extra]}]
  (let [[form-data set-form-data!] (use-state (or initial-state {}))

        handle-change (use-callback
                        (fn [field]
                          (fn [e]
                            (let [value (.. e -target -value)]
                              (set-form-data! #(assoc % field value)))))
                        [])

        handle-close (use-callback
                       (fn []
                         (set-form-data! (or initial-state {}))
                         (when on-close (on-close)))
                       [on-close initial-state])

        handle-submit (use-callback
                        (fn []
                          (when (and valid? (valid? form-data))
                            (on-submit form-data)))
                        [valid? on-submit form-data])

        form-valid? (if valid? (valid? form-data) true)]

    ($ dialog/Dialog
       {:open open?
        :onOpenChange (fn [open?] (when-not open? (handle-close)))}

       ($ dialog/DialogContent
          {:className (or max-width "sm:max-w-[500px]")}

          ($ dialog/DialogHeader
             ($ dialog/DialogTitle (str title title-extra))
             ($ dialog/DialogDescription description))

          ($ :div {:className "grid gap-4 py-4"}
             (for [field fields]
               ($ form-field-renderer
                  {:key (name (:key field))
                   :field field
                   :form-data form-data
                   :handle-change handle-change
                   :submitting? submitting?}))

             (when error
               ($ :div {:className "text-sm text-red-600 bg-red-50 p-3 rounded-md"}
                  error)))

          ($ dialog/DialogFooter
             ($ button/Button
                {:variant "outline"
                 :onClick handle-close
                 :disabled submitting?}
                "Cancel")
             ($ button/Button
                {:onClick handle-submit
                 :disabled (or submitting? (not form-valid?))}
                (if submitting?
                  (or submitting-label "Submitting...")
                  (or submit-label "Submit"))))))))
