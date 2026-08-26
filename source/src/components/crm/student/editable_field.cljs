(ns components.crm.student.editable-field
  "Reusable click-to-edit field component with auto-save on blur."
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref use-callback]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/select" :as select]
            ["/gen/shadcn/components/ui/popover" :as popover]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["/gen/shadcn/components/ui/label" :refer [Label]]
            ["/gen/shadcn/components/ui/badge" :refer [Badge]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["lucide-react" :refer [X Eye EyeOff]]))

;; Click-to-edit text field
(defui editable-text
  "Click-to-edit text field with auto-save on blur.
   Props:
   - value: current value (string)
   - on-save: (fn [new-value]) called when value changes
   - placeholder: placeholder text when empty
   - saving?: boolean indicating save in progress
   - validate: (fn [value]) returns nil if valid, or error message string
   - class: additional CSS classes for display mode
   - input-class: additional CSS classes for input"
  [{:keys [value on-save placeholder saving? validate class input-class disabled?]}]
  (let [[editing? set-editing!] (use-state false)
        [local-value set-local-value!] (use-state (or value ""))
        [original-value set-original-value!] (use-state value)
        [error set-error!] (use-state nil)
        input-ref (use-ref nil)

        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-original-value! value)
                         (set-local-value! (or value ""))
                         (set-error! nil)
                         (set-editing! true)))
                     [value disabled?])

        cancel-edit (use-callback
                      (fn []
                        (set-local-value! (or original-value ""))
                        (set-error! nil)
                        (set-editing! false))
                      [original-value])

        validate-and-save (use-callback
                            (fn []
                              (let [trimmed (str/trim local-value)]
                                (if (and validate (seq trimmed))
                                  ;; Run validation if provided and value is non-empty
                                  (if-let [err (validate trimmed)]
                                    (set-error! err)
                                    ;; Valid - save and close
                                    (do
                                      (when (and on-save (not= trimmed (or original-value "")))
                                        (on-save trimmed))
                                      (set-editing! false)))
                                  ;; No validation or empty - just save
                                  (do
                                    (when (and on-save (not= trimmed (or original-value "")))
                                      (on-save trimmed))
                                    (set-editing! false)))))
                            [local-value original-value on-save validate])

        handle-key-down (use-callback
                          (fn [e]
                            (case (.-key e)
                              "Escape" (cancel-edit)
                              "Enter" (validate-and-save)
                              nil))
                          [cancel-edit validate-and-save])]

    ;; Focus input when entering edit mode
    (use-effect
      (fn []
        (when (and editing? @input-ref)
          (.focus @input-ref)
          (.select @input-ref))
        js/undefined)
      [editing?])

    (if editing?
      ($ :div {:class "space-y-1"}
         ($ Input
            {:ref input-ref
             :value local-value
             :on-change #(do
                           (set-local-value! (.. % -target -value))
                           (set-error! nil))
             :on-blur validate-and-save
             :on-key-down handle-key-down
             :placeholder placeholder
             :disabled saving?
             :class (str "h-auto py-1 px-2 " (when error "border-destructive ") input-class)})
         (when error
           ($ :p {:class "text-xs text-destructive"} error)))
      ($ :span
         {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-all "
                      "transition-all duration-150 hover:bg-accent/50 hover:ring-1 hover:ring-border/50 "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent hover:ring-0 ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit
          :on-focus start-edit}
         (if (and value (not= value ""))
           (str value)
           ($ :span {:class "text-muted-foreground/70"} (or placeholder "Click to edit")))))))

;; Click-to-edit textarea field
(defui editable-textarea
  "Click-to-edit textarea with auto-save on blur.
   Props:
   - value: current value (string)
   - on-save: (fn [new-value]) called when value changes
   - placeholder: placeholder text when empty
   - saving?: boolean indicating save in progress
   - rows: number of rows for textarea (default 3)
   - class: additional CSS classes"
  [{:keys [value on-save placeholder saving? rows class disabled?]}]
  (let [[editing? set-editing!] (use-state false)
        [local-value set-local-value!] (use-state (or value ""))
        [original-value set-original-value!] (use-state value)
        textarea-ref (use-ref nil)

        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-original-value! value)
                         (set-local-value! (or value ""))
                         (set-editing! true)))
                     [value disabled?])

        cancel-edit (use-callback
                      (fn []
                        (set-local-value! (or original-value ""))
                        (set-editing! false))
                      [original-value])

        save-and-close (use-callback
                         (fn []
                           (let [trimmed (clojure.string/trim local-value)]
                             (when (and on-save (not= trimmed (or original-value "")))
                               (on-save trimmed))
                             (set-editing! false)))
                         [local-value original-value on-save])

        handle-key-down (use-callback
                          (fn [e]
                            (when (= (.-key e) "Escape")
                              (cancel-edit)))
                          [cancel-edit])]

    ;; Focus textarea when entering edit mode
    (use-effect
      (fn []
        (when (and editing? @textarea-ref)
          (.focus @textarea-ref))
        js/undefined)
      [editing?])

    (if editing?
      ($ Textarea
         {:ref textarea-ref
          :value local-value
          :on-change #(set-local-value! (.. % -target -value))
          :on-blur save-and-close
          :on-key-down handle-key-down
          :placeholder placeholder
          :disabled saving?
          :rows (or rows 3)
          :class "resize-none"})
      ($ :div
         {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-colors "
                      "hover:bg-muted/50 hover:ring-1 hover:ring-border/50 whitespace-pre-wrap "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit}
         (if (and value (not= value ""))
           (str value)
           ($ :span {:class "text-muted-foreground/70"} (or placeholder "Click to edit")))))))

;; Click-to-edit number field
(defui editable-number
  "Click-to-edit number field with auto-save on blur.
   Props:
   - value: current value (number or nil)
   - on-save: (fn [new-value]) called when value changes (receives number)
   - placeholder: placeholder text when empty
   - saving?: boolean indicating save in progress
   - min: minimum value
   - max: maximum value
   - step: step increment (default 1)
   - format-fn: (fn [value]) formats display value (default str)
   - class: additional CSS classes"
  [{:keys [value on-save placeholder saving? min max step format-fn class disabled?]}]
  (let [[editing? set-editing!] (use-state false)
        [local-value set-local-value!] (use-state (if value (str value) ""))
        [original-value set-original-value!] (use-state value)
        [error set-error!] (use-state nil)
        input-ref (use-ref nil)
        format-fn (or format-fn str)

        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-original-value! value)
                         (set-local-value! (if value (str value) ""))
                         (set-error! nil)
                         (set-editing! true)))
                     [value disabled?])

        cancel-edit (use-callback
                      (fn []
                        (set-local-value! (if original-value (str original-value) ""))
                        (set-error! nil)
                        (set-editing! false))
                      [original-value])

        validate-and-save (use-callback
                            (fn []
                              (if (= local-value "")
                                ;; Empty value - save as nil
                                (do
                                  (when (and on-save (some? original-value))
                                    (on-save nil))
                                  (set-editing! false))
                                ;; Parse and validate
                                (let [parsed (js/parseFloat local-value)]
                                  (cond
                                    (js/isNaN parsed)
                                    (set-error! "Invalid number")

                                    (and min (< parsed min))
                                    (set-error! (str "Must be at least " min))

                                    (and max (> parsed max))
                                    (set-error! (str "Must be at most " max))

                                    :else
                                    (do
                                      (when (and on-save (not= parsed original-value))
                                        (on-save parsed))
                                      (set-editing! false))))))
                            [local-value original-value on-save min max])

        handle-key-down (use-callback
                          (fn [e]
                            (case (.-key e)
                              "Escape" (cancel-edit)
                              "Enter" (validate-and-save)
                              nil))
                          [cancel-edit validate-and-save])]

    ;; Focus input when entering edit mode
    (use-effect
      (fn []
        (when (and editing? @input-ref)
          (.focus @input-ref)
          (.select @input-ref))
        js/undefined)
      [editing?])

    (if editing?
      ($ :div {:class "space-y-1"}
         ($ Input
            {:ref input-ref
             :type "number"
             :value local-value
             :on-change #(set-local-value! (.. % -target -value))
             :on-blur validate-and-save
             :on-key-down handle-key-down
             :placeholder placeholder
             :disabled saving?
             :min min
             :max max
             :step (or step 1)
             :class (str "h-auto py-1 px-2 " (when error "border-destructive"))})
         (when error
           ($ :p {:class "text-xs text-destructive"} error)))
      ($ :span
         {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-all inline-block w-full "
                      "transition-all duration-150 hover:bg-accent/50 hover:ring-1 hover:ring-border/50 "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent hover:ring-0 ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit}
         (if (some? value)
           (str (format-fn value))
           ($ :span {:class "text-muted-foreground/70"} (or placeholder "—")))))))

;; Inline editable boolean field
(defui editable-boolean
  "Inline boolean checkbox with immediate save.
   Props:
   - value: current value (boolean or nil)
   - on-save: (fn [new-value]) called when value changes
   - saving?: boolean indicating save in progress
   - disabled?: boolean"
  [{:keys [value on-save saving? disabled? class]}]
  (let [checked? (true? value)
        id (str "boolean-" (random-uuid))]
    ($ :div {:class (str "flex items-center gap-2 " class)}
       ($ Checkbox
          {:id id
           :checked checked?
           :disabled (or saving? disabled?)
           :onCheckedChange #(let [new-value (true? %)]
                               (when (and on-save (not= new-value checked?))
                                 (on-save new-value)))})
       ($ Label
          {:htmlFor id
           :class "text-sm font-normal"}
          (if checked? "Yes" "No")))))

;; Click-to-edit select field
(defui editable-select
  "Click-to-edit select field with auto-save on change.
   Props:
   - value: current value (keyword or string)
   - on-save: (fn [new-value]) called when value changes
   - options: vector of {:value :key :label \"Display\"} OR vector of strings
   - placeholder: placeholder text when no value
   - saving?: boolean indicating save in progress
   - format-fn: (fn [value]) formats display value
   - class: additional CSS classes
   - as-string?: when true, keep values as strings instead of converting to keywords"
  [{:keys [value on-save options placeholder saving? format-fn class disabled? as-string?]}]
  (let [[editing? set-editing!] (use-state false)
        [local-value set-local-value!] (use-state value)

        ;; Normalize options: if vector of strings, convert to {:value :label} format
        normalized-options (mapv (fn [opt]
                                   (cond
                                     (map? opt) opt
                                     (keyword? opt) {:value opt :label (-> (name opt) (str/replace "-" " ") str/capitalize)}
                                     (string? opt) {:value opt :label opt}
                                     :else {:value opt :label (str opt)}))
                                 options)

        ;; Find label for current value (always ensure string output for React)
        display-value (or (when format-fn (format-fn value))
                          (->> normalized-options
                               (filter #(= (:value %) value))
                               first
                               :label)
                          (when value
                            (cond
                              (keyword? value) (name value)
                              (string? value) value
                              :else (str value))))

        handle-change (use-callback
                        (fn [new-val]
                          (let [parsed (if as-string? new-val (keyword new-val))]
                            (set-local-value! parsed)
                            (when (and on-save (not= parsed value))
                              (on-save parsed))
                            (set-editing! false)))
                        [value on-save as-string?])

        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-local-value! value)
                         (set-editing! true)))
                     [value disabled?])]

    (if editing?
      (let [value-to-string (fn [v] (if (keyword? v) (name v) (str v)))]
        ($ select/Select
           {:value (when local-value (value-to-string local-value))
            :onValueChange handle-change
            :onOpenChange #(when-not % (set-editing! false))
            :defaultOpen true
            :disabled saving?}
           ($ select/SelectTrigger {:class "h-auto py-1"}
              ($ select/SelectValue {:placeholder placeholder}))
           ($ select/SelectContent
              (for [{:keys [value label]} normalized-options]
                ($ select/SelectItem {:key (value-to-string value) :value (value-to-string value)}
                   (if (string? label) label (str label)))))))
      ($ :span
         {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-all inline-block w-full "
                      "transition-all duration-150 hover:bg-accent/50 hover:ring-1 hover:ring-border/50 "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent hover:ring-0 ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit}
         (if display-value
           (str display-value)
           ($ :span {:class "text-muted-foreground/70"} (or placeholder "Select...")))))))

(defn- option-value [opt]
  (cond
    (map? opt) (:value opt)
    :else opt))

(defn- option-label [opt]
  (cond
    (map? opt) (:label opt)
    (keyword? opt) (-> (name opt) (str/replace "-" " ") str/capitalize)
    (string? opt) opt
    :else (str opt)))

(defn- other-option? [opt]
  (#{"Other" "Other (write-in)"} (str (option-value opt))))

(defui editable-select-with-other
  "Click-to-edit single select where Other opens a write-in input.
   The saved value is always the selected base option or the typed custom text;
   the Other sentinel itself is not saved."
  [{:keys [value on-save options placeholder saving? class disabled? custom-placeholder]}]
  (let [[editing? set-editing!] (use-state false)
        base-options (->> options (remove other-option?) vec)
        normalized-options (mapv (fn [opt]
                                   {:value (str (option-value opt))
                                    :label (str (option-label opt))})
                                 base-options)
        base-values (into #{} (map :value normalized-options))
        stored-value (when (some? value) (str value))
        other-sentinel? (#{"Other" "Other (write-in)"} stored-value)
        custom-value? (and (seq stored-value)
                           (not (contains? base-values stored-value))
                           (not other-sentinel?))
        [selected-value set-selected-value!] (use-state (if (or custom-value? other-sentinel?) "other" stored-value))
        [custom-value set-custom-value!] (use-state (if custom-value? stored-value ""))
        display-value (cond
                        custom-value? stored-value
                        other-sentinel? "Other"
                        (seq stored-value) (or (some #(when (= (:value %) stored-value) (:label %))
                                                     normalized-options)
                                               stored-value)
                        :else nil)
        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-selected-value! (if (or custom-value? other-sentinel?) "other" stored-value))
                         (set-custom-value! (if custom-value? stored-value ""))
                         (set-editing! true)))
                     [disabled? custom-value? other-sentinel? stored-value])
        save-custom (use-callback
                      (fn []
                        (let [trimmed (str/trim custom-value)]
                          (when (seq trimmed)
                            (when (and on-save (not= trimmed stored-value))
                              (on-save trimmed)))
                          (set-editing! false)))
                      [custom-value stored-value on-save])
        handle-select (use-callback
                        (fn [new-val]
                          (set-selected-value! new-val)
                          (if (= new-val "other")
                            (set-custom-value! "")
                            (do
                              (when (and on-save (not= new-val stored-value))
                                (on-save new-val))
                              (set-editing! false))))
                        [stored-value on-save])]
    (if editing?
      ($ :div {:class "space-y-2"}
         ($ select/Select
            {:value (or selected-value "")
             :onValueChange handle-select
             :defaultOpen true
             :disabled saving?}
            ($ select/SelectTrigger {:class "h-auto py-1"}
               ($ select/SelectValue {:placeholder placeholder}))
            ($ select/SelectContent
               (for [{:keys [value label]} normalized-options]
                 ($ select/SelectItem {:key value :value value} label))
               ($ select/SelectItem {:key "other" :value "other"} "Other")))
         (when (= selected-value "other")
           ($ :div {:class "flex gap-2"}
              ($ Input
                 {:value custom-value
                  :on-change #(set-custom-value! (.. % -target -value))
                  :on-key-down #(when (= (.-key %) "Enter")
                                  (.preventDefault %)
                                  (save-custom))
                  :on-blur save-custom
                  :placeholder (or custom-placeholder "Please specify...")
                  :aria-label (or custom-placeholder "Please specify")
                  :class "h-8 text-sm max-sm:text-base"
                  :disabled saving?})
              ($ :button
                 {:type "button"
                  :class (str "px-2 h-8 text-sm rounded border hover:bg-muted "
                              (when (str/blank? custom-value) "opacity-50 cursor-not-allowed"))
                  :disabled (or saving? (str/blank? custom-value))
                  :on-click save-custom}
                 "Save"))))
      ($ :span
         {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-all inline-block w-full "
                      "hover:bg-accent/50 hover:ring-1 hover:ring-border/50 "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent hover:ring-0 ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit}
         (if display-value
           display-value
           ($ :span {:class "text-muted-foreground/70"} (or placeholder "Select...")))))))

;; Click-to-edit multi-select field (for sets of values)
(defui editable-multi-select
  "Click-to-edit multi-select field with checkboxes in a popover.
   Props:
   - value: current value (set of strings)
   - on-save: (fn [new-value]) called when value changes (receives set)
   - options: vector of {:value \"string\" :label \"Display\"}
   - placeholder: placeholder text when no values selected
   - saving?: boolean indicating save in progress
   - class: additional CSS classes"
  [{:keys [value on-save options placeholder saving? class disabled?]}]
  (let [[open? set-open!] (use-state false)
        [local-value set-local-value!] (use-state (or value #{}))
        [original-value set-original-value!] (use-state value)

        ;; Format selected values for display
        display-value (when (and value (seq value))
                        (str/join ", " (sort value)))

        toggle-option (use-callback
                        (fn [opt-value]
                          (set-local-value!
                            (fn [current]
                              (if (contains? current opt-value)
                                (disj current opt-value)
                                (conj current opt-value)))))
                        [])

        handle-open-change (use-callback
                             (fn [is-open]
                               (if is-open
                                 ;; Opening - save original
                                 (do
                                   (set-original-value! value)
                                   (set-local-value! (or value #{}))
                                   (set-open! true))
                                 ;; Closing - save if changed
                                 (do
                                   (when (and on-save (not= local-value (or original-value #{})))
                                     (on-save local-value))
                                   (set-open! false))))
                             [value local-value original-value on-save])]

    ($ popover/Popover {:open open? :onOpenChange handle-open-change}
       ($ popover/PopoverTrigger {:asChild true}
          ($ :span
             {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-colors "
                          "hover:bg-muted/50 inline-block "
                          (when saving? "opacity-50 ")
                          (when disabled? "cursor-default hover:bg-transparent ")
                          class)}
             (if display-value
               display-value
               ($ :span {:class "text-muted-foreground/70"} (or placeholder "Select...")))))
       ($ popover/PopoverContent {:class "w-64 p-3" :align "start"}
          ($ :div {:class "space-y-2"}
             (for [{:keys [value label]} options]
               (let [checked? (contains? local-value value)]
                 ($ :div {:key value :class "flex items-center space-x-2"}
                    ($ Checkbox
                       {:id (str "ms-" value)
                        :checked checked?
                        :onCheckedChange #(toggle-option value)
                        :disabled saving?})
                     ($ Label
                        {:htmlFor (str "ms-" value)
                         :class "text-sm font-normal cursor-pointer"}
                        label)))))))))

;; Click-to-edit field with tags display, checkbox options, and custom text input
(defui editable-tag-select
  "Click-to-edit field with tags, checkboxes, and write-in support.
   Props:
   - value: current value (set of strings)
   - on-save: (fn [new-value]) called when value changes (receives set)
   - options: vector of {:value \"string\" :label \"Display\"}
   - placeholder: placeholder text when no values selected
   - saving?: boolean indicating save in progress
   - display-fn: (fn [value]) returns display string for a tag value (default identity)
   - class: additional CSS classes"
  [{:keys [value on-save options placeholder saving? class disabled? display-fn]}]
  (let [;; Defensive: a legacy scalar (from a single->multi type change) must
        ;; never be iterated as a string of characters. Coerce to a set.
        value (cond
                (nil? value)  nil
                (set? value)  value
                (coll? value) (set value)
                :else         #{value})
        [open? set-open!] (use-state false)
        [local-value set-local-value!] (use-state (or value #{}))
        [original-value set-original-value!] (use-state value)
        [input-value set-input-value!] (use-state "")

        ;; Get option values for checking if a value is predefined
        option-values (into #{} (map :value options))

        toggle-option (use-callback
                        (fn [opt-value]
                          (set-local-value!
                            (fn [current]
                              (if (contains? current opt-value)
                                (disj current opt-value)
                                (conj current opt-value)))))
                        [])

        remove-value (use-callback
                       (fn [val]
                         (set-local-value! (fn [current] (disj current val))))
                       [])

        add-custom-value (use-callback
                           (fn []
                             (let [trimmed (str/trim input-value)]
                               (when (and (seq trimmed)
                                          (not (contains? local-value trimmed)))
                                 (set-local-value! (fn [current] (conj current trimmed)))
                                 (set-input-value! ""))))
                           [input-value local-value])

        handle-key-down (use-callback
                          (fn [e]
                            (when (= (.-key e) "Enter")
                              (.preventDefault e)
                              (add-custom-value)))
                          [add-custom-value])

        handle-open-change (use-callback
                             (fn [is-open]
                               (if is-open
                                 ;; Opening - save original
                                 (do
                                   (set-original-value! value)
                                   (set-local-value! (or value #{}))
                                   (set-input-value! "")
                                   (set-open! true))
                                 ;; Closing - save if changed
                                 (do
                                   (when (and on-save (not= local-value (or original-value #{})))
                                     (on-save local-value))
                                   (set-open! false))))
                             [value local-value original-value on-save])]

    ($ popover/Popover {:open open? :onOpenChange handle-open-change}
       ($ popover/PopoverTrigger {:asChild true}
          ($ :div
             {:class (str "cursor-pointer rounded px-2 py-1 -mx-2 -my-1 transition-colors "
                          "hover:bg-muted/50 "
                          (when saving? "opacity-50 ")
                          (when disabled? "cursor-default hover:bg-transparent ")
                          class)}
             (if (and value (seq value))
               ;; Display as tags
               (let [display (or display-fn identity)]
                 ($ :div {:class "flex flex-wrap gap-1.5"}
                    (for [v (sort value)]
                      ($ Badge {:key v :variant "secondary" :class "text-xs whitespace-normal text-left break-words"}
                         (display v)))))
               ($ :span {:class "text-muted-foreground/70"} (or placeholder "Select...")))))
       ($ popover/PopoverContent {:class "w-[600px] max-w-[90vw] p-3" :align "start"}
          ($ :div {:class "space-y-3"}
             ;; Selected tags with remove buttons
             (when (seq local-value)
               ($ :div {:class "flex flex-wrap gap-1.5 pb-2 border-b"}
                  (for [v (sort local-value)]
                    ($ Badge {:key v :variant "secondary" :class "text-xs pr-1 whitespace-normal text-left max-w-full"}
                       ($ :span {:class "break-words"} v)
                       ($ :button
                          {:type "button"
                           :class "ml-1 hover:bg-muted rounded-full p-0.5 flex-shrink-0"
                           :on-click (fn [e]
                                       (.stopPropagation e)
                                       (remove-value v))}
                          ($ X {:class "h-3 w-3"}))))))

             ;; Custom text input
             ($ :div {:class "flex gap-2"}
                ($ Input
                   {:value input-value
                    :on-change #(set-input-value! (.. % -target -value))
                    :on-key-down handle-key-down
                    :placeholder "Type to add..."
                    :class "h-8 text-sm"
                    :disabled saving?})
                ($ :button
                   {:type "button"
                    :class (str "px-2 h-8 text-sm rounded border hover:bg-muted "
                                (when (str/blank? input-value) "opacity-50 cursor-not-allowed"))
                    :disabled (or saving? (str/blank? input-value))
                    :on-click add-custom-value}
                   "Add"))

             ;; Checkbox options
             (when (seq options)
               ($ :div {:class "space-y-2 pt-2 border-t max-h-64 overflow-y-auto"}
                  (for [{:keys [value label]} options]
                    (let [checked? (contains? local-value value)]
                      ($ :div {:key value :class "flex items-start space-x-2"}
                         ($ Checkbox
                            {:id (str "tag-" value)
                             :checked checked?
                             :onCheckedChange #(toggle-option value)
                             :disabled saving?
                             :class "mt-0.5 flex-shrink-0"})
                         ($ Label
                            {:htmlFor (str "tag-" value)
                             :class "text-sm font-normal cursor-pointer leading-snug break-words"}
                            label)))))))))))

;; Click-to-edit encrypted/password field with reveal toggle
(defui editable-encrypted
  "Click-to-edit encrypted/password field with auto-save on blur.
   Shows masked value in display mode with reveal toggle.
   Props:
   - value: current value (string, decrypted)
   - on-save: (fn [new-value]) called when value changes
   - placeholder: placeholder text when empty
   - saving?: boolean indicating save in progress
   - class: additional CSS classes"
  [{:keys [value on-save placeholder saving? class disabled?]}]
  (let [[editing? set-editing!] (use-state false)
        [revealed? set-revealed!] (use-state false)
        [local-value set-local-value!] (use-state (or value ""))
        [original-value set-original-value!] (use-state value)
        input-ref (use-ref nil)

        has-value? (and value (not= value "") (not (str/blank? value)))

        start-edit (use-callback
                     (fn []
                       (when-not disabled?
                         (set-original-value! value)
                         (set-local-value! (or value ""))
                         (set-revealed! false)
                         (set-editing! true)))
                     [value disabled?])

        cancel-edit (use-callback
                      (fn []
                        (set-local-value! (or original-value ""))
                        (set-editing! false)
                        (set-revealed! false))
                      [original-value])

        save-and-close (use-callback
                         (fn []
                           (let [trimmed (str/trim local-value)]
                             ;; Only save if changed
                             (when (and on-save (not= trimmed (or original-value "")))
                               (on-save trimmed))
                             (set-editing! false)
                             (set-revealed! false)))
                         [local-value original-value on-save])

        handle-key-down (use-callback
                          (fn [e]
                            (case (.-key e)
                              "Escape" (cancel-edit)
                              "Enter" (save-and-close)
                              nil))
                          [cancel-edit save-and-close])

        toggle-reveal (use-callback
                        (fn [e]
                          (.stopPropagation e)
                          (.preventDefault e)
                          (set-revealed! not))
                        [])]

    ;; Focus input when entering edit mode
    (use-effect
      (fn []
        (when (and editing? @input-ref)
          (.focus @input-ref)
          (.select @input-ref))
        js/undefined)
      [editing?])

    (if editing?
      ;; Edit mode - input with reveal toggle
      ($ :div {:class "flex items-center gap-2"}
         ($ Input
            {:ref input-ref
             :type (if revealed? "text" "password")
             :value local-value
             :on-change #(set-local-value! (.. % -target -value))
             :on-blur save-and-close
             :on-key-down handle-key-down
             :placeholder placeholder
             :disabled saving?
             :class "h-auto py-1 px-2 flex-1"})
         ($ button/Button
            {:type "button"
             :variant "ghost"
             :size "sm"
             :class "h-8 w-8 p-0 flex-shrink-0"
             :on-mouse-down toggle-reveal}
            (if revealed?
              ($ EyeOff {:class "h-4 w-4"})
              ($ Eye {:class "h-4 w-4"}))))

      ;; Display mode - masked or placeholder
      ($ :div
         {:class (str "flex items-center gap-2 cursor-pointer rounded px-2 py-1 -mx-2 -my-1 "
                      "transition-all hover:bg-muted/50 hover:ring-1 hover:ring-border/50 "
                      (when saving? "opacity-50 ")
                      (when disabled? "cursor-default hover:bg-transparent hover:ring-0 ")
                      class)
          :role "button"
          :tab-index 0
          :on-click start-edit}
         ($ :span {:class "flex-1"}
            (if has-value?
              ;; Show masked dots or revealed value
              (if revealed?
                value
                "••••••••")
              ($ :span {:class "text-muted-foreground/70"}
                 (or placeholder "Click to set"))))
         ;; Reveal toggle in display mode (only if has value)
         (when has-value?
           ($ button/Button
              {:type "button"
               :variant "ghost"
               :size "sm"
               :class "h-8 w-8 p-0 flex-shrink-0"
               :on-click toggle-reveal}
              (if revealed?
                ($ EyeOff {:class "h-4 w-4"})
                ($ Eye {:class "h-4 w-4"}))))))))
