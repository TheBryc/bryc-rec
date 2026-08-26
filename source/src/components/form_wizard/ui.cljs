(ns components.form-wizard.ui
  "Reusable UI components for form wizard"
  (:require [uix.core :refer [defui $]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["/gen/shadcn/components/ui/card" :as card]
            [components.form-wizard.fields :as fields]
            [components.form-wizard.navigation :refer [markdown-content]]))

;; Welcome header component
(defui welcome-header [{:keys [header-config]}]
  (let [{:keys [icon title subtitle]
         :or {icon "🎓"
              title "Welcome to Your BRYC Journey!"
              subtitle "We're excited to help you plan your path after high school. This quick survey helps us understand your goals and create a personalized plan just for you."}} header-config]
    ($ :div {:class "text-center mb-8"}
       ($ :div {:class "inline-flex items-center justify-center w-16 h-16 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full mb-4"}
          ($ :span {:class "text-white text-2xl"} icon))
       ($ :h1 {:class "text-3xl md:text-4xl font-bold text-gray-800 mb-3"} title)
       ($ :p {:class "text-lg text-gray-600 max-w-2xl mx-auto leading-relaxed"} subtitle))))

;; Email capture screen
(defui email-capture-screen [{:keys [email on-email-change on-start-form on-resume-form validation-error session-error resume-link-sent is-processing]}]
  ($ :div {:class "max-w-lg mx-auto w-full"}
     ($ card/Card {:class "bg-white/90 backdrop-blur-sm shadow-xl border-0"}
        ($ card/CardHeader {:class "bg-gradient-to-r from-blue-50 to-purple-50 text-center pt-8 pb-6"}
           ($ :div {:class "flex items-center justify-center w-16 h-16 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full mx-auto mb-4"}
              ($ :span {:class "text-white text-2xl"} "📧"))
           ($ card/CardTitle {:class "text-2xl font-bold text-gray-800 mb-2"} "Get Started")
           ($ :p {:class "text-gray-600"} "Enter your email to begin or resume your form"))

        ($ card/CardContent {:class "space-y-4"}
           ;; Session error message (for expired links, etc.)
           (when session-error
             ($ :div {:class "mb-4 p-4 bg-amber-50 border border-amber-200 rounded-lg"}
                ($ :div
                   ($ :h3 {:class "text-sm font-medium text-amber-800"}
                      (case (:error session-error)
                        :expired "Link Expired"
                        :failed "Resume Failed"
                        "Session Error"))
                   ($ :p {:class "text-sm text-amber-700 mt-1"}
                      (:message session-error)))))

           ;; Success message when resume link is sent
           (when resume-link-sent
             ($ :div {:class "mb-4 p-4 bg-green-50 border border-green-200 rounded-lg"}
                ($ :div
                   ($ :h3 {:class "text-sm font-medium text-green-800"}
                      "Link Sent")
                   ($ :p {:class "text-sm text-green-700 mt-1"}
                      "Check your email for the link to resume your form."))))

           ($ :div
              ($ :label {:class "block text-sm font-medium text-gray-700 mb-2"} "Email Address")
              ($ input/Input
                 {:type "email"
                  :placeholder "your.email@example.com"
                  :value (or email "")
                  :onChange #(on-email-change (.. % -target -value))
                  :class (when validation-error "border-red-500")})
              (when validation-error
                ($ :p {:class "text-red-500 text-sm mt-1"} validation-error)))

           ($ :div {:class "space-y-3"}
              ($ button/Button
                 {:onClick on-start-form
                  :disabled is-processing
                  :class (str "w-full bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 text-white font-semibold py-3 "
                             (when is-processing "opacity-50 cursor-not-allowed"))}
                 (if is-processing "Processing..." "Start"))

              ($ :div {:class "relative"}
                 ($ :div {:class "absolute inset-0 flex items-center"}
                    ($ :div {:class "w-full border-t border-gray-300"}))
                 ($ :div {:class "relative flex justify-center text-sm"}
                    ($ :span {:class "px-2 bg-white text-gray-500"} "or")))

              ($ button/Button
                 {:onClick on-resume-form
                  :disabled is-processing
                  :variant "outline"
                  :class (str "w-full border-gray-300 text-gray-700 hover:bg-gray-50 py-3 flex items-center justify-center "
                             (when is-processing "opacity-50 cursor-not-allowed"))}
                 (if is-processing
                   ($ :<>
                      ($ :svg {:class "animate-spin -ml-1 mr-2 h-4 w-4 text-gray-700"
                               :xmlns "http://www.w3.org/2000/svg"
                               :fill "none"
                               :viewBox "0 0 24 24"}
                         ($ :circle {:class "opacity-25"
                                    :cx "12"
                                    :cy "12"
                                    :r "10"
                                    :stroke "currentColor"
                                    :strokeWidth "4"})
                         ($ :path {:class "opacity-75"
                                  :fill "currentColor"
                                  :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}))
                      "Sending...")
                   "Resume")))))))

;; Helper function for field visibility
(defn field-visible?
  "Check if a field should be visible based on its visibility conditions"
  [field answers]
  (if-let [show-when (:show-when field)]
    (let [{:keys [field-key has-value? equals]} show-when]
      (cond
        has-value?
        (let [value (get answers field-key)]
          (and (some? value)
               (not= value "")
               (if (coll? value)
                 (seq value)  ; Use seq instead of not empty
                 true)))

        equals
        (= (get answers field-key) equals)

        :else
        true))
    true)) ; Field is visible by default if no conditions

;; Helper for the (required)/(optional) label. Mirrors
;; components.form-wizard.validation/field-required? (kept local, like
;; field-visible? above, to match this ns's existing pattern).
(defn field-required?
  "Whether a field is required given current answers (:required-when predicate,
   else static :optional flag — default required)."
  [field answers]
  (if-let [req (:required-when field)]
    (let [{:keys [field-key has-value? equals not-equals]} req]
      (cond
        has-value?
        (let [value (get answers field-key)]
          (and (some? value)
               (not= value "")
               (if (coll? value) (boolean (seq value)) true)))

        equals
        (= (get answers field-key) equals)

        not-equals
        (not= (get answers field-key) not-equals)

        :else
        true))
    (not (:optional field false))))

;; Form field wrapper with validation display
(defui form-field [{:keys [field answers validation-errors saved-fields on-answer-change on-answer-change-and-save on-field-blur api-client session-id]}]
  (let [field-key (:key field)
        value (get answers field-key)
        error (get validation-errors field-key)
        is-saved? (contains? saved-fields field-key)
        ;; Use immediate save for radio, select, checkbox, boolean-checkbox, date fields; blur save for text fields
        ;; Either-or fields need both handlers
        change-handler (if (contains? #{:radio :select :checkbox :boolean-checkbox :date} (:type field))
                         on-answer-change-and-save
                         on-answer-change)
        field-props (if (= (:type field) :either-or)
                      {:field field
                       :value value
                       :on-change #(on-answer-change field-key %)  ; For text input changes
                       :on-change-immediate #(on-answer-change-and-save field-key %)  ; For dropdown changes
                       :on-blur #(when on-field-blur (on-field-blur field-key value))}
                      {:field field
                       :value value
                       :on-change #(change-handler field-key %)
                       :on-blur #(when on-field-blur (on-field-blur field-key value))})]
    ($ :div {:class "mb-8 p-4 rounded-xl bg-gradient-to-r from-gray-50/50 to-blue-50/30 border border-gray-200/50 hover:border-blue-300/50 transition-colors duration-200"}
       ; Enhanced label with icon and save indicator
       ($ :div {:class "flex items-center justify-between mb-3"}
          ($ :div {:class "flex items-center"}
             ($ :div {:class "w-2 h-2 shrink-0 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full mr-3"})
             ($ :label {:class "block text-base font-semibold text-gray-800"}
                (:label field)
                (let [required? (field-required? field answers)]
                  (if required?
                    ($ :span {:class (str "text-xs font-normal ml-1 "
                                         (if error "text-red-500" "text-gray-400"))}
                       "(required)")
                    ($ :span {:class "text-sm font-normal text-gray-500 ml-2"} "(optional)")))))
          ; Saved indicator
          (when is-saved?
            ($ :div {:class "flex items-center text-green-600 text-xs font-medium transition-opacity duration-500"}
               ($ :span {:class "mr-1"} "✓")
               "Saved")))

       (when (:description field)
         ($ markdown-content {:content (:description field)
                              :class-name "text-sm text-gray-600 mb-4 pl-5 italic"}))

       ($ :div {:class "pl-5"}
          (case (:type field)
            :text ($ fields/text-field field-props)
            :textarea ($ fields/textarea-field field-props)
            :date ($ fields/date-field field-props)
            :select ($ fields/select-field field-props)
            :radio ($ fields/radio-field field-props)
            :checkbox ($ fields/checkbox-field field-props)
            :boolean-checkbox ($ fields/boolean-checkbox-field field-props)
            :either-or ($ fields/either-or-field field-props)
            :dynamic-list ($ fields/dynamic-list-field
                             {:field field
                              :value value
                              :on-change #(change-handler field-key %)
                              :on-blur-with-value #(when on-field-blur
                                                     ;; Pass the new value directly
                                                     (on-field-blur field-key %))})
            :file ($ fields/file-field
                     {:field field
                      :value value
                      :on-change #(on-answer-change-and-save field-key %)
                      :api-client api-client
                      :session-id session-id})
            ($ :div {:class "text-red-500"} (str "Unknown field type: " (:type field))))
          (when error
            ($ :p {:class "text-sm text-red-500 mt-1"} error))))))

;; Compact grid layout for the O*NET RIASEC assessment (60 binary items).
;; Reuses the normal per-field answer/save path (each item is a real wizard
;; field; selecting a cell calls on-answer-change-and-save like a :radio field)
;; but renders rows+columns instead of 60 stacked cards.
(defui riasec-grid [{:keys [group answers validation-errors on-answer-change-and-save]}]
  (let [fields (filterv #(field-visible? % answers) (:fields group))
        total (count fields)
        answered (count (filter #(some? (get answers (:key %))) fields))
        ;; option values/labels are uniform across items (from the riasec component)
        opts (:options (first fields) [1 0])
        labels (:option-labels (first fields) {})]
    ($ :div {:class "space-y-3"}
       ($ :div {:class "flex items-center justify-between text-sm font-medium text-gray-600 sticky top-0 bg-white/90 backdrop-blur py-2 z-10"}
          ($ :span (str answered " of " total " answered"))
          (when (< answered total)
            ($ :span {:class "text-gray-400"} (str (- total answered) " remaining"))))
       (for [field fields
             :let [fk (:key field)
                   v (get answers fk)
                   err (get validation-errors fk)]]
         ($ :div {:key fk
                  :class (str "flex items-center justify-between gap-4 rounded-lg border px-4 py-3 "
                              (if err "border-red-400 bg-red-50/40" "border-gray-200 bg-white"))}
            ($ :span {:class "text-sm text-gray-800 flex-1"} (:label field))
            ($ :div {:class "flex shrink-0 gap-2"}
               (for [opt opts]
                 ($ :button
                    {:key (str fk "-" opt)
                     :type "button"
                     :on-click #(on-answer-change-and-save fk opt)
                     :class (str "px-3 py-1.5 rounded-md text-xs font-semibold border transition-colors "
                                 (if (= v opt)
                                   "bg-gradient-to-r from-blue-500 to-purple-600 text-white border-transparent"
                                   "bg-white text-gray-600 border-gray-300 hover:border-blue-400"))}
                    (get labels opt (str opt))))))))))

;; Form group container
(defui form-group [{:keys [group answers validation-errors saved-fields on-answer-change on-answer-change-and-save on-field-blur api-client session-id]}]
  (if (= :riasec-grid (:layout group))
    ($ riasec-grid {:group group
                     :answers answers
                     :validation-errors validation-errors
                     :on-answer-change-and-save on-answer-change-and-save})
    ($ :div {:class "space-y-2"}
       (for [field (:fields group)
             :when (field-visible? field answers)]
         ($ form-field
            {:key (:key field)
             :field field
             :answers answers
             :validation-errors validation-errors
             :saved-fields saved-fields
             :on-answer-change on-answer-change
             :on-answer-change-and-save on-answer-change-and-save
             :on-field-blur on-field-blur
             :api-client api-client
             :session-id session-id})))))
