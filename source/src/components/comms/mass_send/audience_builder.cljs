(ns components.comms.mass-send.audience-builder
  "Audience/segment filter builder for the mass-send wizard. Renders
   field/operator/value predicate rows over a contact type's field
   definitions (plus synthetic status/tags), driving the debounced
   :crm/build-audience preview in store.comms.mass-send.

   Per-data-type value controls reuse render-schema-field from
   components.crm.schema-form-renderer rather than reinventing inputs."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.schema-form-renderer :refer [render-schema-field]]
            [components.crm.student.avatar :refer [student-avatar]]
            [store.comms.mass-send :as ms]
            ["/gen/shadcn/components/ui/select" :as sel]
            ["/gen/shadcn/components/ui/popover" :refer [Popover PopoverTrigger PopoverContent]]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["lucide-react" :refer [X Plus Search Check ChevronsUpDown]]))

;; =============================================================================
;; Filterable fields + operators
;; =============================================================================

(def ^:private non-filterable
  #{:encrypted :data :long_text :url})

(def ^:private synthetic-fields
  [{:slug "status" :name "Contact status" :data-type :status :scope :status
    :options ["active" "inactive" "archived" "merged"]}
   {:slug "tags" :name "Tags" :data-type :tags :scope :tags}])

(defn- ->display [x] (if (keyword? x) (name x) (str x)))

(defn field-options
  "Filterable field defs (+ synthetic status/tags) as
   {:slug :name :data-type :options :scope}."
  [field-defs]
  (into (mapv (fn [fd]
                {:slug (:slug fd)
                 :name (or (:name fd) (:slug fd))
                 :data-type (:data-type fd)
                 :options (mapv ->display (:options fd))})
              (remove #(non-filterable (:data-type %)) field-defs))
        synthetic-fields))

(defn- data-type->ops
  [dt]
  (case dt
    (:keyword_enum :single_select :status)
    [[:eq "is"] [:is-any-of "is any of"]]
    (:multi_select :tags)
    [[:has-any-of "has any of"] [:has-all-of "has all of"]]
    :number
    [[:eq "is"] [:gte "≥"] [:lte "≤"] [:between "between"]]
    :date
    [[:eq "is"] [:gte "on/after"] [:lte "on/before"] [:between "between"]]
    :boolean
    [[:is-true "is true"] [:is-false "is false"]]
    ;; :text :email :phone and fallback
    [[:contains "contains"] [:eq "is"]]))

(defn- op-needs-value? [op]
  (not (#{:is-true :is-false} op)))

;; =============================================================================
;; Value control (reuses render-schema-field leaves)
;; =============================================================================

(defn- leaf
  "One value input via render-schema-field."
  [schema value on-change]
  (render-schema-field {:field-schema schema
                        :value value
                        :on-change on-change}))

(defui ^:private value-control
  [{:keys [data-type op options value on-change]}]
  (cond
    (not (op-needs-value? op)) nil

    (= op :between)
    (let [t (if (= data-type :date) :date :number)]
      ($ :div {:class "flex items-center gap-2"}
         (leaf {:type t :placeholder "from"} (:lo value)
               #(on-change (assoc (or value {}) :lo %)))
         ($ :span {:class "text-xs text-muted-foreground"} "–")
         (leaf {:type t :placeholder "to"} (:hi value)
               #(on-change (assoc (or value {}) :hi %)))))

    ;; multi-value among known options
    (and (#{:is-any-of :has-any-of :has-all-of} op) (seq options))
    (leaf {:type :multi-select :options options}
          (set value)
          #(on-change (vec %)))

    ;; tags / multi with no options → comma-separated text
    (#{:has-any-of :has-all-of} op)
    (leaf {:type :string :placeholder "tag, tag, …"}
          (when (sequential? value) (str/join ", " value))
          #(on-change (->> (str/split (or % "") #",")
                           (map str/trim)
                           (remove str/blank?)
                           vec)))

    (#{:keyword_enum :single_select :status} data-type)
    (leaf {:type :single-select :options options :placeholder "Select…"}
          value on-change)

    (= data-type :number)
    (leaf {:type :number :placeholder "value"} value on-change)

    (= data-type :date)
    (leaf {:type :date} value on-change)

    :else
    (leaf {:type :string :placeholder "value"} value on-change)))

;; =============================================================================
;; Predicate row
;; =============================================================================

(defui ^:private dropdown
  [{:keys [value placeholder items on-change class]}]
  ($ sel/Select {:value (or value "") :onValueChange on-change}
     ($ sel/SelectTrigger {:class (or class "w-full")}
        ($ sel/SelectValue {:placeholder placeholder}))
     ($ sel/SelectContent
        (for [[v label] items]
          ($ sel/SelectItem {:key (str v) :value (str v)} label)))))

(defui ^:private field-combobox
  "Searchable single-select for the predicate field. The CRM exposes 200+
   filterable fields, so a plain select is unusable — this filters by name
   as you type."
  [{:keys [value placeholder items on-change]}]
  (let [[open? set-open!] (use-state false)
        [query set-query!] (use-state "")
        by-slug (into {} items)
        selected-label (get by-slug value)
        q (str/lower-case (str/trim query))
        filtered (if (str/blank? q)
                   items
                   (filter (fn [[_ label]]
                             (str/includes? (str/lower-case (str label)) q))
                           items))
        close! (fn [] (set-open! false) (set-query! ""))]
    ($ Popover {:open open?
                :onOpenChange (fn [o] (set-open! o) (when-not o (set-query! "")))}
       ($ PopoverTrigger {:asChild true}
          ($ :button
             {:type "button"
              :role "combobox"
              :aria-expanded open?
              :class (str "flex h-9 w-full items-center justify-between gap-2 rounded-md "
                          "border border-input bg-transparent px-3 py-2 text-sm "
                          "transition-colors hover:bg-muted/40 focus-visible:outline-none "
                          "focus-visible:ring-2 focus-visible:ring-ring")}
             ($ :span {:class (str "truncate "
                                   (if selected-label "text-foreground" "text-muted-foreground"))}
                (or selected-label placeholder))
             ($ ChevronsUpDown {:class "size-4 shrink-0 opacity-50"})))
       ($ PopoverContent {:class "w-(--radix-popover-trigger-width) min-w-72 p-0" :align "start"}
          ($ :div {:class "flex items-center gap-2 border-b border-gray-950/5 px-3"}
             ($ Search {:class "size-4 shrink-0 text-muted-foreground"})
             ($ :input
                {:value query
                 :autoFocus true
                 :placeholder "Search fields…"
                 :aria-label "Search fields"
                 :class (str "h-9 w-full bg-transparent py-2 text-base outline-none sm:text-sm "
                             "placeholder:text-muted-foreground")
                 :on-change #(set-query! (.. % -target -value))}))
          (if (seq filtered)
            ($ :div {:role "listbox" :class "max-h-72 overflow-y-auto p-1"}
               (for [[slug label] filtered]
                 ($ :button
                    {:key (str slug)
                     :type "button"
                     :role "option"
                     :aria-selected (= slug value)
                     :on-click (fn [] (on-change slug) (close!))
                     :class (str "flex w-full items-center justify-between gap-2 rounded-sm "
                                 "px-2 py-1.5 text-left text-base transition-colors "
                                 "hover:bg-muted sm:text-sm "
                                 (when (= slug value) "bg-muted/60"))}
                    ($ :span {:class "truncate"} label)
                    (when (= slug value)
                      ($ Check {:class "size-4 shrink-0 text-foreground"})))))
            ($ :p {:class "px-3 py-6 text-center text-base text-muted-foreground sm:text-sm"}
               "No fields match."))))))

(defui ^:private predicate-row
  [{:keys [pred fields]}]
  (let [by-slug (into {} (map (juxt :slug identity)) fields)
        fdef (get by-slug (:slug pred))
        dt (:data-type fdef)
        ops (when dt (data-type->ops dt))
        upd (fn [patch] (rf/dispatch [::ms/update-predicate (:id pred) patch]))]
    ($ :div {:class "flex flex-col gap-2 sm:flex-row sm:items-start"}
       ;; Field
       ($ :div {:class "sm:w-52"}
          ($ field-combobox
             {:value (:slug pred)
              :placeholder "Field"
              :items (map (juxt :slug :name) fields)
              :on-change (fn [slug]
                           (let [f (get by-slug slug)]
                             (upd {:slug slug
                                   :data-type (:data-type f)
                                   :scope (:scope f)})))}))
       ;; Operator
       ($ :div {:class "sm:w-36"}
          ($ dropdown
             {:value (some-> (:op pred) name)
              :placeholder "Operator"
              :items (map (fn [[k l]] [(name k) l]) ops)
              :on-change #(upd {:op (keyword %) :value nil})}))
       ;; Value
       ($ :div {:class "min-w-0 flex-1"}
          (when (and dt (:op pred))
            ($ value-control
               {:data-type dt
                :op (:op pred)
                :options (:options fdef)
                :value (:value pred)
                :on-change #(upd {:value %})})))
       ;; Remove
       ($ :button
          {:type "button"
           :aria-label "Remove filter"
           :class (str "mt-1 inline-flex size-7 shrink-0 items-center "
                       "justify-center rounded-md text-muted-foreground "
                       "hover:bg-gray-950/5 hover:text-foreground")
           :on-click #(rf/dispatch [::ms/remove-predicate (:id pred)])}
          ($ X {:class "size-3.5"})))))

;; =============================================================================
;; Preview summary
;; =============================================================================

(defui preview-summary
  [{:keys [channel]}]
  (let [preview (use-subscribe [::ms/audience-preview])
        loading? (use-subscribe [::ms/audience-preview-loading?])
        error (use-subscribe [::ms/audience-preview-error])
        {:keys [total missing-sms missing-email opted-out]} (:counts preview)
        missing (if (= channel :email) missing-email missing-sms)]
    ($ :div {:class "space-y-2"}
       (cond
         error
         ($ :p {:class "text-sm text-rose-700"} error)

         loading?
         ($ :p {:class "text-sm text-muted-foreground"} "Previewing…")

         (nil? preview)
         ($ :p {:class "text-sm text-muted-foreground"}
            "No audience selected yet. Add a filter or search for specific people.")

         (:empty? preview)
         ($ :p {:class "text-sm text-muted-foreground"}
            "No audience selected yet. Add a filter or search for specific people.")

         :else
         ($ :p {:class "text-sm text-muted-foreground tabular-nums"}
            ($ :span {:class "font-medium text-foreground"}
               (str total " matched"))
            (when (pos? (or missing 0))
              (str " · " missing " missing "
                   (if (= channel :email) "email" "phone")))
            (when (pos? (or opted-out 0))
              (str " · " opted-out " opted out"))))
       (when (and preview (seq (:recipients preview)))
         ($ :details {:class "text-sm"}
            ($ :summary {:class "cursor-pointer text-muted-foreground hover:text-foreground"}
               (str "Preview recipients (" (count (:recipients preview)) ")"))
            ($ :ul {:class "mt-2 grid grid-cols-1 gap-y-1 sm:grid-cols-2 sm:gap-x-4"}
               (for [r (:recipients preview)]
                 ($ :li {:key (str (:contact-id r))
                         :class "flex items-center gap-2 text-xs"}
                    ($ student-avatar {:name (:display-name r) :size :sm})
                    ($ :span {:class "truncate"} (:display-name r))))))))))
