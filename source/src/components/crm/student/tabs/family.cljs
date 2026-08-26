(ns components.crm.student.tabs.family
  "Family section — guardians, siblings, and other affiliated contacts.

   Reads from the extended student projection (`:student.guardian/*`,
   `:student.secondary-guardian/*`, `:student/siblings`), so legacy data and
   relationship-sourced data render through the same slots. The projection
   prefers relationship data and falls back to legacy student fields, so
   this component never needs to know the difference."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell nested-tabs]]
            [components.crm.student.editable-field :refer [editable-text editable-select editable-tag-select]]
            [components.crm.student.avatar :refer [student-avatar]]
            [components.crm.student.add-guardian-sheet :refer [add-guardian-sheet]]
            [components.crm.student.add-sibling-sheet :refer [add-sibling-sheet]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            [components.shared.field-error :refer [field-error]]
            [components.crm.student.status :as status]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Option constants
;; =============================================================================

(def sibling-relationship-labels
  #{"Sister" "Brother" "Sibling" "Half-Sister" "Half-Brother"})

;; =============================================================================
;; Sub-schemas
;; =============================================================================

(def children-sub-schema
  {:type :vector
   :add-button-text "Add Child"
   :empty-message "No children added."
   :item-label-key :name
   :item-schema
   {:type :map
    :fields [{:key :name :type :string :required true :label "Name" :placeholder "Enter child's name..."}]}})

(def affiliated-contacts-sub-schema
  {:type :vector
   :add-button-text "Add Contact"
   :empty-message "No affiliated contacts added."
   :item-label-key :name
   :item-schema
   {:type :map
    :fields [{:key :name :type :string :required true :label "Name" :placeholder "Enter contact name..."}
             {:key :relationship :type :string :required false :label "Relationship" :placeholder "e.g., Aunt, Cousin..."}
             {:key :contact-id :type :string :required false :label "Contact ID" :placeholder "Enter contact ID..."}]}})

;; =============================================================================
;; Visual primitives
;; =============================================================================

(defui section-label
  "Uppercase, tracking-wide section label that matches the existing tab
   typography vocabulary but with right-aligned actions."
  [{:keys [title detail action]}]
  ($ :div {:class "flex items-baseline justify-between pt-6 pb-3 first:pt-0"}
     ($ :div {:class "flex items-baseline gap-3"}
        ($ :h3 {:class "text-xs font-semibold uppercase tracking-wider text-muted-foreground"} title)
        (when detail
          ($ :span {:class "text-xs text-muted-foreground/80"} detail)))
     (when action action)))

(defui status-dot
  "Status indicator dot. Filled accent for active populations
   (Fellow / Advisee / Senior), outline for alumni/dismissed,
   half-filled for prospect/applicant/waitlist."
  [{:keys [status aria-label]}]
  (let [status-key (status/status-key status)
        variant (cond
                  (#{:fellow :advisee :college-fellow :vulnerable-fellow} status-key) :filled
                  (#{:alumni :dismissed :in-school} status-key) :outline
                  (#{:prospect :applicant :waitlist} status-key) :half
                  :else :outline)
        class (case variant
                :filled "bg-amber-500 border-amber-500"
                :half "bg-amber-500/40 border-amber-500"
                :outline "bg-transparent border-muted-foreground/40")]
    ($ :span {:role "img"
              :aria-label (or aria-label (status/status-label status))
              :class (str "inline-block w-2 h-2 rounded-full border " class)})))

(defn- bryc-status-label [status]
  (or (status/status-label status) "—"))

;; =============================================================================
;; Sibling cards
;; =============================================================================

(defui guardian-card
  "Compact navigable card for a guardian. Mirrors the sibling-card shape:
   avatar + name + relationship + one secondary line (email or phone) +
   optional 'Primary contact' indicator. Click-through to the guardian's
   profile page where all inline editing lives."
  [{:keys [first-name last-name email phone relationship contact-id primary?]}]
  (let [full-name (str/trim (str (when first-name first-name)
                                 (when last-name (str " " last-name))))
        display (if (str/blank? full-name) "Unnamed guardian" full-name)
        href (when contact-id
               (str "/hub/guardians/view?guardian-id="
                    (js/encodeURIComponent (str contact-id))))
        secondary (or email phone)
        wrapper-class (str "block w-44 rounded-lg border border-border/70 bg-background "
                           "px-3 py-3 transition-all "
                           (if href
                             "hover:border-border hover:-translate-y-px hover:shadow-sm "
                             "opacity-90 cursor-default "))]
    ($ (if href :a :div)
       (cond-> {:class wrapper-class}
         href (assoc :href href))
       ($ :div {:class "flex items-center gap-2.5 mb-2"}
          ($ student-avatar {:name display :size :sm})
          ($ :div {:class "min-w-0"}
             ($ :p {:class (str "text-sm font-medium truncate "
                                (when (str/blank? full-name) "text-muted-foreground/70 italic"))}
                display)
             (when (and relationship (not (str/blank? relationship)))
               ($ :p {:class "text-xs text-muted-foreground"} relationship))))
       (when secondary
         ($ :p {:class "text-xs text-muted-foreground truncate mb-1"} secondary))
       (when primary?
         ($ :p {:class "text-xs text-muted-foreground"} "Primary contact")))))

(defui sibling-card
  "Compact, navigable sibling card. The whole card is an anchor to the
   sibling's profile so screen readers and middle-click both work."
  [{:keys [sibling]}]
  (let [name (or (:name sibling) "Unknown")
        contact-id (:contact-id sibling)
        legacy? (= :legacy (:source sibling))
        href (when (and contact-id (not legacy?))
               (str "/hub/students/view?student-id=" (js/encodeURIComponent (str contact-id))))
        bryc-status (:bryc-status sibling)
        status-label (when bryc-status (bryc-status-label bryc-status))
        relationship (:relationship sibling)
        wrapper-class (str "block w-44 rounded-lg border border-border/70 bg-background "
                           "px-3 py-3 transition-all "
                           (if href
                             "hover:border-border hover:-translate-y-px hover:shadow-sm "
                             "opacity-90 cursor-default "))]
    ($ (if href :a :div)
       (cond-> {:class wrapper-class}
         href (assoc :href href))
       ($ :div {:class "flex items-center gap-2.5 mb-2"}
          ($ student-avatar {:name name :size :sm})
          ($ :div {:class "min-w-0"}
             ($ :p {:class "text-sm font-medium truncate"} name)
             (when-let [grade (:grade sibling)]
               ($ :p {:class "text-xs text-muted-foreground"} grade))))
       ($ :div {:class "flex items-center gap-1.5 text-xs"}
          (when bryc-status ($ status-dot {:status bryc-status :aria-label status-label}))
          ($ :span {:class "text-muted-foreground"}
             (cond
               legacy? (or relationship "Sibling")
               status-label status-label
               relationship relationship
               :else "Sibling"))))))

(defui siblings-row
  [{:keys [siblings]}]
  ($ :<>
     ($ section-label
        {:title "Siblings"
         :detail (when (seq siblings)
                   (str (count siblings) " " (if (= 1 (count siblings)) "sibling" "siblings")))
         :action ($ :button
                    {:type "button"
                     :class "text-xs font-medium text-primary hover:underline"
                     :on-click #(rf/dispatch [::student-events/open-add-sibling-sheet])}
                    "+ Add sibling")})
     (if (seq siblings)
       ($ :div {:class "flex flex-wrap gap-3"}
          (for [s siblings]
            ($ sibling-card {:key (str (:contact-id s) "-" (:name s))
                             :sibling s})))
       ($ :p {:class "text-sm text-muted-foreground"}
          "No siblings linked yet."))))

;; =============================================================================
;; Family Tab
;; =============================================================================

(defui family-tab
  "Family tab.
   Props:
   - student: student data map (extended projection)
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])
        special-circumstances-options (use-subscribe [::student-subs/field-options "special-circumstances"])
        legacy-fellow-options (use-subscribe [::student-subs/field-options "legacy-fellow"])
        family-resp-options (use-subscribe [::student-subs/field-options "family-responsibilities"])
        family-hours-options (use-subscribe [::student-subs/field-options "family-responsibilities-hours"])
        bryc-status (get field-values :bryc-status)
        fellow? (status/fellow? bryc-status)
        advisee? (status/advisee? bryc-status)
        alumni? (status/alumni? bryc-status)
        fellow-or-alumni? (or fellow? alumni?)
        ;; Secondary guardian comes from the projection (relationship → legacy fallback).
        secondary-from-relationship? (= :relationship (:student.secondary-guardian/source student))
        ;; Siblings: prefer projection's vetted list (already handles fallback);
        ;; the projection emits an empty vector when no data exists.
        siblings (:student/siblings student)
        ;; Affiliated contacts: when siblings come from sibling-of relationships,
        ;; the legacy vector still surfaces here for non-sibling relatives.
        ;; When siblings come from legacy data, surface only non-sibling entries
        ;; in this section to avoid double-rendering.
        siblings-from-legacy? (= :legacy (:source (first siblings)))
        affiliated-contacts (let [raw (get field-values :affiliated-contacts)]
                              (if siblings-from-legacy?
                                ;; Siblings already render above from this same field;
                                ;; filter them out here.
                                (vec (remove #(sibling-relationship-labels (:relationship %)) raw))
                                raw))
        render-guardians (fn []
                           ($ :<>
                              ($ section-label
                                 {:title "Guardians"
                                  :action ($ :button
                                             {:type "button"
                                              :class "text-xs font-medium text-primary hover:underline"
                                              :on-click #(rf/dispatch [::student-events/open-add-guardian-sheet])}
                                             "+ Add guardian")})
                              (let [primary-guardian {:first-name (:student.guardian/first-name student)
                                                      :last-name (:student.guardian/last-name student)
                                                      :email (:student.guardian/email student)
                                                      :phone (:student.guardian/phone student)
                                                      :relationship (:student.guardian/relationship student)
                                                      :contact-id (:student.guardian/contact-id student)
                                                      :primary? true}
                                    secondary-guardian
                                    (cond
                                      secondary-from-relationship?
                                      {:first-name (:student.secondary-guardian/first-name student)
                                       :last-name (:student.secondary-guardian/last-name student)
                                       :email (:student.secondary-guardian/email student)
                                       :phone (:student.secondary-guardian/phone student)
                                       :relationship (:student.secondary-guardian/relationship student)
                                       :contact-id (:student.secondary-guardian/contact-id student)
                                       :primary? false}

                                      (or (get field-values :secondary-guardian-name)
                                          (get field-values :secondary-guardian-email)
                                          (get field-values :secondary-guardian-phone))
                                      (let [n (get field-values :secondary-guardian-name)]
                                        {:first-name (when n (first (str/split (str/trim n) #"\s+" 2)))
                                         :last-name (when n (second (str/split (str/trim n) #"\s+" 2)))
                                         :email (get field-values :secondary-guardian-email)
                                         :phone (get field-values :secondary-guardian-phone)
                                         :relationship (get field-values :secondary-guardian-relationship)
                                         :contact-id nil
                                         :primary? false}))]
                                ($ :div {:class "flex flex-wrap gap-3"}
                                   (when (:contact-id primary-guardian)
                                     ($ guardian-card primary-guardian))
                                   (when secondary-guardian
                                     ($ guardian-card secondary-guardian))))))
        render-family-context (fn []
                                ($ :<>
                                   ($ section-label {:title "Family Context"})
                                   ($ field-grid
                                      ($ field-cell {:label "Special Circumstances" :span :full}
                                         ($ editable-tag-select
                                            {:value (get field-values :special-circumstances)
                                             :options special-circumstances-options
                                             :placeholder "Select circumstances"
                                             :saving? saving?
                                             :on-save #(rf/dispatch [::student-events/save-field "special-circumstances" % api-client])})
                                         ($ field-error {:field-slug "special-circumstances"}))
                                      ($ field-cell {:label "Family Responsibilities" :span :full}
                                         ($ editable-tag-select
                                            {:value (get field-values :family-responsibilities)
                                             :options (or family-resp-options [])
                                             :placeholder "Not answered"
                                             :saving? saving?
                                             :on-save #(rf/dispatch [::student-events/save-field "family-responsibilities" % api-client])})
                                         ($ field-error {:field-slug "family-responsibilities"}))
                                      ($ field-cell {:label "Family Responsibilities Hours/Week"}
                                         ($ editable-select
                                            {:value (get field-values :family-responsibilities-hours)
                                             :options (or family-hours-options ["0" "1–4" "5–10" "More than 10"])
                                             :placeholder "Not answered"
                                             :saving? saving?
                                             :as-string? true
                                             :on-save #(rf/dispatch [::student-events/save-field "family-responsibilities-hours" % api-client])})
                                         ($ field-error {:field-slug "family-responsibilities-hours"})))))
        render-affiliated-contacts (fn []
                                     ($ :<>
                                        ($ section-label {:title "Affiliated Contacts (other family)"})
                                        ($ schema-vector-editor
                                           {:sub-schema affiliated-contacts-sub-schema
                                            :value affiliated-contacts
                                            :saving? saving?
                                            :on-save #(rf/dispatch [::student-events/save-field "affiliated-contacts" % api-client])})
                                        ($ field-error {:field-slug "affiliated-contacts"})))]

    (if advisee?
      ($ :<>
         ($ nested-tabs
            {:tabs (cond-> [{:id "guardians" :label "Guardians" :render render-guardians}
                            {:id "siblings" :label "Siblings" :render #($ siblings-row {:siblings siblings})}
                            {:id "family-context" :label "Family Context" :render render-family-context}]
                     (and fellow-or-alumni? (or (seq affiliated-contacts) (not (seq siblings))))
                     (conj {:id "affiliated-contacts" :label "Affiliated Contacts" :render render-affiliated-contacts}))})
         ($ add-guardian-sheet {:api-client api-client})
         ($ add-sibling-sheet {:api-client api-client}))
      ($ :<>
       ;; Guardians — slim navigable cards. Editing happens on the guardian's
       ;; profile page (`/hub/guardians/view?guardian-id=…`); this tab just shows
       ;; the relationships at a glance. The "+ Add guardian" link opens the
       ;; existing find-or-create sheet.
       ($ section-label
          {:title "Guardians"
           :action ($ :button
                      {:type "button"
                       :class "text-xs font-medium text-primary hover:underline"
                       :on-click #(rf/dispatch [::student-events/open-add-guardian-sheet])}
                      "+ Add guardian")})
       (let [primary-guardian {:first-name (:student.guardian/first-name student)
                               :last-name (:student.guardian/last-name student)
                               :email (:student.guardian/email student)
                               :phone (:student.guardian/phone student)
                               :relationship (:student.guardian/relationship student)
                               :contact-id (:student.guardian/contact-id student)
                               :primary? true}
             ;; Secondary guardian card data — relationship-sourced takes precedence,
             ;; legacy fields are the fallback. The projection's contact-id may be
             ;; absent for legacy data; the card downgrades to non-navigable in that case.
             secondary-guardian
             (cond
               secondary-from-relationship?
               {:first-name (:student.secondary-guardian/first-name student)
                :last-name (:student.secondary-guardian/last-name student)
                :email (:student.secondary-guardian/email student)
                :phone (:student.secondary-guardian/phone student)
                :relationship (:student.secondary-guardian/relationship student)
                :contact-id (:student.secondary-guardian/contact-id student)
                :primary? false}

               (or (get field-values :secondary-guardian-name)
                   (get field-values :secondary-guardian-email)
                   (get field-values :secondary-guardian-phone))
               (let [n (get field-values :secondary-guardian-name)]
                 {:first-name (when n (first (str/split (str/trim n) #"\s+" 2)))
                  :last-name (when n (second (str/split (str/trim n) #"\s+" 2)))
                  :email (get field-values :secondary-guardian-email)
                  :phone (get field-values :secondary-guardian-phone)
                  :relationship (get field-values :secondary-guardian-relationship)
                  :contact-id nil
                  :primary? false}))]
         ($ :div {:class "flex flex-wrap gap-3"}
            (when (:contact-id primary-guardian)
              ($ guardian-card primary-guardian))
            (when secondary-guardian
              ($ guardian-card secondary-guardian))))

       ;; Siblings
       ($ siblings-row {:siblings siblings})

       ;; Family context fields (special circumstances + family responsibilities)
       ($ section-label {:title "Family Context"})
       ($ field-grid
          ($ field-cell {:label "Special Circumstances" :span :full}
             ($ editable-tag-select
                {:value (get field-values :special-circumstances)
                 :options special-circumstances-options
                 :placeholder "Select circumstances"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "special-circumstances" % api-client])})
             ($ field-error {:field-slug "special-circumstances"}))
          ($ field-cell {:label "Family Responsibilities" :span :full}
             ($ editable-tag-select
                {:value (get field-values :family-responsibilities)
                 :options (or family-resp-options [])
                 :placeholder "Not answered"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "family-responsibilities" % api-client])})
             ($ field-error {:field-slug "family-responsibilities"}))
          ($ field-cell {:label "Family Responsibilities Hours/Week"}
             ($ editable-select
                {:value (get field-values :family-responsibilities-hours)
                 :options (or family-hours-options ["0" "1–4" "5–10" "More than 10"])
                 :placeholder "Not answered"
                 :saving? saving?
                 :as-string? true
                 :on-save #(rf/dispatch [::student-events/save-field "family-responsibilities-hours" % api-client])})
             ($ field-error {:field-slug "family-responsibilities-hours"}))
          (when fellow-or-alumni?
            ($ field-cell {:label "Legacy Fellow"}
               ($ editable-select
                  {:value (get field-values :legacy-fellow)
                   :options legacy-fellow-options
                   :placeholder "Select"
                   :saving? saving?
                   :as-string? true
                   :on-save #(rf/dispatch [::student-events/save-field "legacy-fellow" % api-client])})
               ($ field-error {:field-slug "legacy-fellow"})))
          (when alumni?
            ($ field-cell {:label "Spouse Name"}
               ($ editable-text
                  {:value (get field-values :spouse-name)
                   :placeholder "Enter spouse name"
                   :saving? saving?
                   :on-save #(rf/dispatch [::student-events/save-field "spouse-name" % api-client])})
               ($ field-error {:field-slug "spouse-name"}))))

       ;; Children (alumni only)
       (when alumni?
         ($ :<>
            ($ section-label {:title "Children"})
            ($ schema-vector-editor
               {:sub-schema children-sub-schema
                :value (get field-values :children)
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "children" % api-client])})
            ($ field-error {:field-slug "children"})))

       ;; Affiliated contacts (other family — non-sibling relatives)
       (when (and fellow-or-alumni? (or (seq affiliated-contacts) (not (seq siblings))))
         ($ :<>
            ($ section-label {:title "Affiliated Contacts (other family)"})
            ($ schema-vector-editor
               {:sub-schema affiliated-contacts-sub-schema
                :value affiliated-contacts
                :saving? saving?
                :on-save #(rf/dispatch [::student-events/save-field "affiliated-contacts" % api-client])})
            ($ field-error {:field-slug "affiliated-contacts"})))

       ;; Sheets — render once at the end of the tab; visibility controlled by store subs.
       ($ add-guardian-sheet {:api-client api-client})
       ($ add-sibling-sheet {:api-client api-client})))))
