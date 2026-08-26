(ns components.crm.schema-form-renderer
  "Generic form renderer driven by CRM sub-schemas.
   Converts sub-schema definitions to UIx form components with full UI hints support."
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/label" :refer [Label]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/select" :as select]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["lucide-react" :refer [Plus Pencil Trash2]]))

;; =============================================================================
;; Field Renderers
;; =============================================================================

(defmulti render-schema-field
  "Render a form field based on its sub-schema type.
   Dispatches on (:type field-schema)"
  (fn [{:keys [field-schema]}] (:type field-schema)))

(defmethod render-schema-field :string
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [input-type placeholder rows]} field-schema]
    (if (or (= input-type "textarea") rows)
      ($ Textarea
         {:value (or value "")
          :placeholder placeholder
          :rows (or rows 3)
          :disabled disabled?
          :on-change #(on-change (.. % -target -value))})
      ($ Input
         {:type (or input-type "text")
          :value (or value "")
          :placeholder placeholder
          :disabled disabled?
          :on-change #(on-change (.. % -target -value))}))))

(defmethod render-schema-field :number
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [placeholder min max step]} field-schema]
    ($ Input
       {:type "number"
        :value (if (some? value) (str value) "")
        :placeholder placeholder
        :min min
        :max max
        :step (or step 1)
        :disabled disabled?
        :on-change #(let [v (.. % -target -value)]
                      (on-change (when (seq v) (js/parseFloat v))))})))

(defmethod render-schema-field :boolean
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [label]} field-schema]
    ($ :div {:class "flex items-center space-x-2"}
       ($ Checkbox
          {:id (str "bool-" (name (:key field-schema)))
           :checked (boolean value)
           :onCheckedChange on-change
           :disabled disabled?})
       (when label
         ($ Label
            {:htmlFor (str "bool-" (name (:key field-schema)))
             :class "text-sm font-normal cursor-pointer"}
            label)))))

(defmethod render-schema-field :single-select
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [options placeholder]} field-schema]
    ($ select/Select
       {:value (or value "")
        :onValueChange on-change
        :disabled disabled?}
       ($ select/SelectTrigger {:class "w-full"}
          ($ select/SelectValue {:placeholder (or placeholder "Select...")}))
       ($ select/SelectContent
          (for [option options]
            ($ select/SelectItem {:key option :value option} option))))))

(defmethod render-schema-field :multi-select
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [options]} field-schema
        selected-set (or value #{})]
    ($ :div {:class "flex flex-wrap gap-4"}
       (for [option options]
         (let [checked? (contains? selected-set option)]
           ($ :div {:key option :class "flex items-center space-x-2"}
              ($ Checkbox
                 {:id (str "ms-" option)
                  :checked checked?
                  :disabled disabled?
                  :onCheckedChange #(on-change (if %
                                                 (conj selected-set option)
                                                 (disj selected-set option)))})
              ($ Label {:htmlFor (str "ms-" option)
                        :class "text-sm font-normal cursor-pointer"}
                 option)))))))

(defmethod render-schema-field :keyword
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [placeholder]} field-schema]
    ($ Input
       {:type "text"
        :value (if (keyword? value) (name value) (or value ""))
        :placeholder placeholder
        :disabled disabled?
        :on-change #(let [v (.. % -target -value)]
                      (on-change (when (seq v) (keyword v))))})))

(defmethod render-schema-field :date
  [{:keys [field-schema value on-change disabled?]}]
  (let [{:keys [placeholder]} field-schema]
    ($ Input
       {:type "date"
        :value (or value "")
        :placeholder placeholder
        :disabled disabled?
        :on-change #(on-change (.. % -target -value))})))

(defmethod render-schema-field :default
  [{:keys [field-schema value on-change disabled?]}]
  ($ Input
     {:type "text"
      :value (or (str value) "")
      :placeholder (:placeholder field-schema)
      :disabled disabled?
      :on-change #(on-change (.. % -target -value))}))

;; =============================================================================
;; Schema Item Form
;; =============================================================================

(defui schema-item-form
  "Render a form for editing a single item based on sub-schema field definitions.
   Props:
   - fields: vector of field schemas from sub-schema
   - item-value: current item data (map)
   - on-save: (fn [item-data]) called when save clicked
   - on-cancel: (fn []) called when cancel clicked
   - saving?: boolean indicating save in progress"
  [{:keys [fields item-value on-save on-cancel saving?]}]
  (let [[form-data set-form-data!] (use-state (or item-value {}))

        update-field (fn [key value]
                       (set-form-data! #(assoc % key value)))

        valid? (every? (fn [{:keys [key required]}]
                         (or (not required)
                             (let [v (get form-data key)]
                               (and (some? v)
                                    (if (string? v) (seq v) true)
                                    (if (set? v) (seq v) true)))))
                       fields)]

    ($ :div {:class "space-y-4"}
       (for [{:keys [key label required description] :as field} fields]
         ($ :div {:key key :class "space-y-2"}
            ($ Label {:htmlFor (name key)}
               label
               (when required ($ :span {:class "text-red-500 ml-1"} "*")))
            (when description
              ($ :p {:class "text-xs text-muted-foreground"} description))
            ;; Call multimethod directly - not a React component
            (render-schema-field
             {:field-schema field
              :value (get form-data key)
              :on-change #(update-field key %)
              :disabled? saving?})))

       ($ :div {:class "flex justify-end gap-2 pt-4"}
          ($ button/Button
             {:variant "outline"
              :on-click on-cancel
              :disabled saving?}
             "Cancel")
          ($ button/Button
             {:on-click #(when valid? (on-save form-data))
              :disabled (or (not valid?) saving?)}
             (if saving? "Saving..." "Save"))))))

;; =============================================================================
;; Schema Vector Editor
;; =============================================================================

(defn- sort-grades
  "Sort grade strings numerically (9th, 10th, 11th, 12th)"
  [grades]
  (sort-by #(js/parseInt (re-find #"\d+" %)) grades))

(defui schema-item-card
  "Display card for a single item in a schema vector.
   Props:
   - item: item data map
   - fields: field schemas for display
   - item-label-key: key to use for main label
   - on-edit: (fn []) called when edit clicked
   - on-delete: (fn []) called when delete clicked
   - disabled?: boolean to disable actions"
  [{:keys [item fields item-label-key on-edit on-delete disabled?]}]
  (let [main-label (get item (or item-label-key (-> fields first :key)))
        ;; Build subtitle from other fields
        subtitle-parts (->> fields
                            (remove #(= (:key %) item-label-key))
                            (map (fn [{:keys [key type]}]
                                   (let [v (get item key)]
                                     (cond
                                       (nil? v) nil
                                       (set? v) (when (seq v) (str/join ", " (sort-grades v)))
                                       (string? v) (when (seq v) v)
                                       :else (str v)))))
                            (filter some?))]
    ($ :div {:class "py-3 border-b last:border-b-0 group relative"}
       ($ :div {:class "flex items-start justify-between gap-2"}
          ($ :div {:class "flex-1 min-w-0"}
             ($ :p {:class "font-medium truncate"} (or main-label "Untitled"))
             (when (seq subtitle-parts)
               ($ :p {:class "text-sm text-muted-foreground mt-1 truncate"}
                  (str/join " • " subtitle-parts))))

          ;; Action buttons (visible on hover)
          ($ :div {:class "flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"}
             ($ button/Button
                {:variant "ghost"
                 :size "icon"
                 :class "h-8 w-8"
                 :on-click on-edit
                 :disabled disabled?}
                ($ Pencil {:class "h-4 w-4"}))
             ($ button/Button
                {:variant "ghost"
                 :size "icon"
                 :class "h-8 w-8 text-destructive hover:text-destructive"
                 :on-click on-delete
                 :disabled disabled?}
                ($ Trash2 {:class "h-4 w-4"})))))))

(defui schema-vector-editor
  "Schema-driven vector editor with add/edit/delete functionality.
   Props:
   - sub-schema: the sub-schema definition for the vector field
   - value: current vector of items
   - on-save: (fn [items]) called when items change
   - saving?: boolean indicating save in progress"
  [{:keys [sub-schema value on-save saving?]}]
  (let [{:keys [item-schema add-button-text empty-message item-label-key]} sub-schema
        fields (or (:fields item-schema) [])

        [dialog-open? set-dialog-open!] (use-state false)
        [editing-index set-editing-index!] (use-state nil)
        [local-saving? set-local-saving!] (use-state false)

        items (or value [])

        open-add-dialog (fn []
                          (set-editing-index! nil)
                          (set-dialog-open! true))

        open-edit-dialog (fn [idx]
                           (set-editing-index! idx)
                           (set-dialog-open! true))

        handle-save (fn [item-data]
                      (set-local-saving! true)
                      (let [new-items (if (some? editing-index)
                                        (assoc (vec items) editing-index item-data)
                                        (conj (vec items) item-data))]
                        (on-save new-items)
                        (set-dialog-open! false)
                        (set-local-saving! false)))

        handle-delete (fn [idx]
                        (when (js/confirm "Are you sure you want to delete this item?")
                          (let [new-items (vec (concat (subvec (vec items) 0 idx)
                                                       (subvec (vec items) (inc idx))))]
                            (on-save new-items))))

        handle-cancel (fn []
                        (set-dialog-open! false)
                        (set-editing-index! nil))]

    ($ :div {:class "space-y-3"}
       ;; Item list
       (if (seq items)
         (for [[idx item] (map-indexed vector items)]
           ($ schema-item-card
              {:key idx
               :item item
               :fields fields
               :item-label-key item-label-key
               :on-edit #(open-edit-dialog idx)
               :on-delete #(handle-delete idx)
               :disabled? (or saving? local-saving?)}))
         ($ :p {:class "text-sm text-muted-foreground italic"}
            (or empty-message "No items added yet.")))

       ;; Add button
       ($ button/Button
          {:variant "outline"
           :size "sm"
           :on-click open-add-dialog
           :disabled (or saving? local-saving?)
           :class "mt-2"}
          ($ Plus {:class "h-4 w-4 mr-2"})
          (or add-button-text "Add Item"))

       ;; Edit/Add dialog
       ($ dialog/Dialog {:open dialog-open? :onOpenChange set-dialog-open!}
          ($ dialog/DialogContent {:class "sm:max-w-md"}
             ($ dialog/DialogHeader
                ($ dialog/DialogTitle
                   (if (some? editing-index) "Edit Item" "Add Item")))
             ($ schema-item-form
                {:fields fields
                 :item-value (when (some? editing-index) (get items editing-index))
                 :on-save handle-save
                 :on-cancel handle-cancel
                 :saving? local-saving?}))))))
