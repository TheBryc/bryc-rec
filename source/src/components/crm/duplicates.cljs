(ns components.crm.duplicates
  (:require [uix.core :as uix :refer [defui $ use-effect use-state]]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["lucide-react" :refer [ExternalLink Loader2]]
            [anomalies :refer [anomaly?]]
            [components.api.interface :as api]
            [components.context.interface :as context]))

(defn- response-value [response k]
  (or (get response k)
      (get-in response [:query/result k])))

(defn- field-values [contact]
  (:field-values contact))

(defn- full-name [fv]
  (let [name (str/trim (str (or (:first-name fv) "")
                           " "
                           (or (:last-name fv) "")))]
    (when-not (str/blank? name) name)))

(defn- contact-name [contact]
  (let [fv (field-values contact)]
    (or (full-name fv)
        (:display-name contact)
        (:display-name fv)
        (:email fv)
        "Unnamed contact")))

(defn- titleize [s]
  (->> (str/split (str/replace (name s) #"-" " ") #" ")
       (remove str/blank?)
       (map str/capitalize)
       (str/join " ")))

(defn- contact-type-slug [contact]
  (some-> (:type-slug contact) str))

(defn- contact-type-label [contact]
  (case (contact-type-slug contact)
    "student" "Student"
    "guardian" "Guardian"
    "staff" "Staff"
    "volunteer" "Volunteer"
    (titleize (or (contact-type-slug contact) "contact"))))

(defn- contact-url [contact]
  (let [contact-id (js/encodeURIComponent (str (:id contact)))]
    (case (contact-type-slug contact)
      "student" (str "/hub/students/view?student-id=" contact-id)
      "guardian" (str "/hub/guardians/view?guardian-id=" contact-id)
      nil)))

(defn- summary-fields [contact]
  (case (contact-type-slug contact)
    "student" [[:birthdate "DOB"]
               [:high-school "School"]
               [:email "Email"]
               [:phone "Phone"]]
    "guardian" [[:email "Email"]
                [:phone "Phone"]
                [:mailing-address "Mailing address"]]
    "staff" [[:email "Email"]
             [:phone "Phone"]
             [:title "Title"]
             [:role "Role"]]
    "volunteer" [[:email "Email"]
                 [:phone "Phone"]
                 [:sub-type "Sub-type"]
                 [:sterling-status "Sterling status"]]
    [[:email "Email"]
     [:phone "Phone"]]))

(defn- fact [contact k]
  (let [v (get (field-values contact) k)]
    (cond
      (set? v) (str/join ", " v)
      (keyword? v) (name v)
      (some? v) (str v)
      :else "—")))

(defn- match-label [match-type]
  (case match-type
    :name-birthdate-school "Name, DOB, school"
    :name-birthdate "Name and DOB"
    :multi-signal "Multiple signals"
    :email "Email"
    :phone "Phone"
    (name match-type)))

(defn- sentenceize [s]
  (str/capitalize (str/replace (name s) #"-" " ")))

(defn- field-label [field]
  (case field
    :advisor-status "Advisor status"
    :bryc-status "BRYC status"
    :graduation-year "Graduation year"
    :high-school "School"
    (titleize field)))

(defn- format-value [value]
  (cond
    (nil? value) "—"
    (keyword? value) (sentenceize value)
    (set? value) (str/join ", " (map format-value value))
    (sequential? value) (str/join ", " (map format-value value))
    :else (str value)))

(defn- resolution-label [resolution]
  (case resolution
    :primary "Keep primary"
    :secondary "Use secondary"
    :combine "Combine"
    (titleize resolution)))

(defn- conflict-value-cell [value]
  ($ :div {:class "max-h-32 overflow-y-auto whitespace-pre-wrap break-words"}
     (format-value value)))

(defui contact-summary [{:keys [contact]}]
  (let [profile-url (contact-url contact)]
    ($ :div {:class "min-w-0 space-y-2"}
       ($ :div {:class "flex min-w-0 items-center gap-2"}
          ($ :h3 {:class "truncate text-sm font-semibold"} (contact-name contact))
          ($ badge/Badge {:variant "outline" :class "shrink-0"} (contact-type-label contact))
          (when profile-url
            ($ :a {:href profile-url
                   :class "shrink-0 text-muted-foreground hover:text-foreground"
                   :aria-label "Open profile"}
               ($ ExternalLink {:class "h-4 w-4"}))))
       ($ :dl {:class "grid grid-cols-[112px_1fr] gap-x-3 gap-y-1 text-sm"}
          (for [[field label] (summary-fields contact)]
            ($ :<> {:key (name field)}
               ($ :dt {:class "font-medium text-foreground"} label)
               ($ :dd {:class "truncate text-muted-foreground"} (fact contact field))))))))

(defui merge-preview-dialog
  [{:keys [candidate preview open? loading? resolving? on-open-change on-resolve]}]
  (let [conflicts (:conflicts preview)
        primary (:primary preview)
        secondary (:secondary preview)
        encrypted-summary (:encrypted-field-summary preview)
        encrypted-fields? (pos? (or (:present-field-count encrypted-summary) 0))]
    ($ dialog/Dialog {:open open? :onOpenChange on-open-change}
       ($ dialog/DialogContent {:class "flex max-h-[calc(100vh-2rem)] flex-col overflow-hidden sm:max-w-5xl"}
          ($ dialog/DialogHeader {:class "shrink-0"}
             ($ dialog/DialogTitle "Review duplicate")
             ($ dialog/DialogDescription
                "Confirm identity, review conflicts, then merge or keep the records separate."))
          ($ :div {:class "min-h-0 flex-1 overflow-y-auto pr-1"}
             (cond
               loading?
               ($ :div {:class "flex h-40 items-center justify-center"}
                  ($ Loader2 {:class "h-5 w-5 animate-spin text-muted-foreground"}))

               preview
               ($ :div {:class "space-y-6"}
                  ($ :div {:class "grid gap-4 md:grid-cols-2"}
                     ($ :div {:class "space-y-3"}
                        ($ badge/Badge {:variant "secondary"} "Recommended primary")
                        ($ contact-summary {:contact primary}))
                     ($ :div {:class "space-y-3"}
                        ($ badge/Badge {:variant "outline"} "Will be marked merged")
                        ($ contact-summary {:contact secondary})))
                  ($ :div {:class "space-y-2"}
                     ($ :h3 {:class "text-sm font-semibold"} "Conflicts")
                     (if (seq conflicts)
                       ($ :div {:class "-mx-2 overflow-x-auto"}
                          ($ :div {:class "inline-block min-w-[720px] px-2 align-middle"}
                             ($ :table {:class "w-full table-fixed text-sm"}
                                ($ :thead
                                   ($ :tr {:class "border-b"}
                                      ($ :th {:class "w-[18%] py-2 pr-4 text-left font-medium"} "Field")
                                      ($ :th {:class "w-[32%] px-4 py-2 text-left font-medium"} "Primary")
                                      ($ :th {:class "w-[32%] px-4 py-2 text-left font-medium"} "Secondary")
                                      ($ :th {:class "w-[18%] py-2 pl-4 text-left font-medium"} "Default")))
                                ($ :tbody
                                   (for [{:keys [field primary-value secondary-value resolution]} conflicts]
                                     ($ :tr {:key (name field) :class "border-b align-top"}
                                        ($ :td {:class "py-2 pr-4 font-medium break-words"} (field-label field))
                                        ($ :td {:class "px-4 py-2 text-muted-foreground"} (conflict-value-cell primary-value))
                                        ($ :td {:class "px-4 py-2 text-muted-foreground"} (conflict-value-cell secondary-value))
                                        ($ :td {:class "py-2 pl-4 break-words"} (resolution-label resolution))))))))
                       ($ :p {:class "text-sm text-muted-foreground"}
                          "No field conflicts need review.")))
                  (when encrypted-fields?
                    ($ :div {:class "rounded-md bg-muted p-3 text-sm text-muted-foreground"}
                       "Encrypted fields are hidden here. Merge will keep the recommended primary record's value when present."))
                  ($ :div {:class "space-y-2"}
                     ($ :h3 {:class "text-sm font-semibold"} "Relationship impact")
                     ($ :p {:class "text-sm text-muted-foreground"}
                        (str (count (:relationship-impact preview))
                             " secondary relationship"
                             (when (not= 1 (count (:relationship-impact preview))) "s")
                             " will be transferred or ended as duplicate."))))

               :else
               ($ :p {:class "text-sm text-muted-foreground"} "Select a candidate to review.")))
          ($ dialog/DialogFooter {:class "shrink-0 border-t pt-4"}
             ($ button/Button
                {:type "button"
                 :variant "outline"
                 :disabled resolving?
                 :on-click #(on-resolve candidate :dismiss nil)}
                "Dismiss")
             ($ button/Button
                {:type "button"
                 :variant "outline"
                 :disabled resolving?
                 :on-click #(on-resolve candidate :keep_both nil)}
                "Keep both")
             ($ button/Button
                {:type "button"
                 :disabled (or resolving? (nil? preview))
                 :on-click #(on-resolve candidate
                                        :merge
                                        {:primary-contact-id (:id primary)
                                         :secondary-contact-id (:id secondary)})}
                "Merge"))))))

(defui duplicate-review-page []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        [loading? set-loading!] (use-state true)
        [error set-error!] (use-state nil)
        [candidates set-candidates!] (use-state [])
        [selected set-selected!] (use-state nil)
        [preview set-preview!] (use-state nil)
        [preview-loading? set-preview-loading!] (use-state false)
        [resolving? set-resolving!] (use-state false)
        [reload-key set-reload-key!] (use-state 0)]
    (use-effect
      (fn []
        (when api-client
          (set-loading! true)
          (set-error! nil)
          (go
            (let [response (<! (api/query api-client {:query/name :crm/list-duplicate-candidates
                                                       :status :pending}))]
              (set-loading! false)
              (if (anomaly? response)
                (set-error! (or (:anomalies/message response) "Failed to load duplicates"))
                (set-candidates! (vec (or (response-value response :duplicates) [])))))))
        js/undefined)
      [api-client reload-key])
    (let [open-preview!
          (fn [candidate]
            (set-selected! candidate)
            (set-preview! nil)
            (set-preview-loading! true)
            (go
              (let [response (<! (api/query api-client {:query/name :crm/preview-merge
                                                         :duplicate-id (:id candidate)}))]
                (set-preview-loading! false)
                (if (anomaly? response)
                  (set-error! (or (:anomalies/message response) "Failed to preview merge"))
                  (set-preview! (or (:query/result response) response))))))
          resolve!
          (fn [candidate resolution merge-config]
            (set-resolving! true)
            (go
              (let [command (cond-> {:command/name :crm/resolve-duplicate
                                     :duplicate-id (:id candidate)
                                     :resolution resolution}
                              merge-config (assoc :merge-config merge-config))
                    response (<! (api/command api-client command))]
                (set-resolving! false)
                (if (anomaly? response)
                  (set-error! (or (:anomalies/message response) "Failed to resolve duplicate"))
                  (do
                    (set-selected! nil)
                    (set-preview! nil)
                    (set-reload-key! inc))))))]
      ($ :div {:class "space-y-6"}
         ($ :div {:class "flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between"}
            ($ :div
               ($ :h1 {:class "text-2xl font-semibold"} "Duplicate review")
               ($ :p {:class "mt-1 text-sm text-muted-foreground"}
                  "Review suspected duplicate contacts before merging or keeping records separate."))
            ($ button/Button {:type "button"
                              :variant "outline"
                              :on-click #(set-reload-key! inc)}
               "Refresh"))
         (when error
           ($ :div {:class "rounded-md bg-destructive/10 p-3 text-sm text-destructive"} error))
         (cond
           loading?
           ($ :div {:class "flex h-48 items-center justify-center"}
              ($ Loader2 {:class "h-5 w-5 animate-spin text-muted-foreground"}))

           (empty? candidates)
           ($ :div {:class "flex h-48 items-center justify-center text-sm text-muted-foreground"}
              "No pending duplicates")

           :else
           ($ :div {:class "-mx-6 -my-2 overflow-x-auto whitespace-nowrap"}
              ($ :div {:class "inline-block min-w-full px-6 py-2 align-middle"}
                 ($ :table {:class "w-full text-sm"}
                    ($ :thead
                       ($ :tr {:class "border-b"}
                          ($ :th {:class "whitespace-nowrap py-2 pr-4 text-left font-medium"} "Match")
                          ($ :th {:class "whitespace-nowrap px-4 py-2 text-left font-medium"} "Contact")
                          ($ :th {:class "whitespace-nowrap px-4 py-2 text-left font-medium"} "Potential duplicate")
                          ($ :th {:class "whitespace-nowrap py-2 pl-4 text-right font-medium"} "Action")))
                    ($ :tbody
                       (for [candidate candidates]
                         ($ :tr {:key (str (:id candidate)) :class "border-b"}
                            ($ :td {:class "py-3 pr-4"}
                               ($ :div {:class "flex items-center gap-2"}
                                  ($ badge/Badge {:variant "secondary"} (match-label (:match-type candidate)))
                                  ($ :span {:class "text-muted-foreground"}
                                     (str (js/Math.round (* 100 (or (:confidence candidate) 0))) "%"))))
                            ($ :td {:class "px-4 py-3"}
                               ($ contact-summary {:contact (:contact candidate)}))
                            ($ :td {:class "px-4 py-3"}
                               ($ contact-summary {:contact (:potential-duplicate candidate)}))
                            ($ :td {:class "py-3 pl-4 text-right"}
                               ($ button/Button {:type "button"
                                                 :size "sm"
                                                 :variant "outline"
                                                 :on-click #(open-preview! candidate)}
                                  "Review")))))))))
         ($ merge-preview-dialog
            {:candidate selected
             :preview preview
             :open? (some? selected)
             :loading? preview-loading?
             :resolving? resolving?
             :on-open-change (fn [open?]
                               (when-not open?
                                 (set-selected! nil)
                                 (set-preview! nil)))
             :on-resolve resolve!})))))
