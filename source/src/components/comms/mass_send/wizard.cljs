(ns components.comms.mass-send.wizard
  "Surface 4: mass-send wizard at /hub/messages/compose. Three-step flow —
   Audience (typeahead-driven multi-select), Compose (SMS body +
   optional template), Review (counts + send). On submit, dispatches
   :communications/start-broadcast and routes to the delivery-report page.

   Token expansion ({first-name} etc.) happens server-side per recipient,
   so the body the staff member writes is the canonical merge template."
  (:require [uix.core :as uix :refer [defui $ use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.context.interface :as context]
            [components.crm.contact-typeahead :refer [contact-typeahead]]
            [components.crm.student.avatar :refer [student-avatar]]
            [components.comms.contact-link :refer [contact-link]]
            [components.comms.email-preview :as email-preview]
            [components.comms.templates.insert-popover :refer [insert-template-popover]]
            [components.comms.tokens :as tokens]
            [components.comms.mass-send.audience-builder
             :refer [predicate-row preview-summary field-options]]
            [components.comms.mass-send.attachments :refer [attachment-list]]
            [store.comms.mass-send :as ms]
            [store.comms.core :as comms]
            [store.comms.identity :as identity]
            [store.comms.templates :as templates]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["/gen/tiptap/RichTextEditor" :as rte]
            ["lucide-react" :refer [X MessageSquare Mail ChevronRight Send Plus Type Code2 Check]]))

;; =============================================================================
;; Horizontal stepper
;; =============================================================================

(def steps
  [[:audience "Audience"]
   [:channel "Channel"]
   [:compose "Message"]
   [:review "Review"]])

(defn- step-index [k]
  (or (some (fn [[i [sk _]]] (when (= sk k) i))
            (map-indexed vector steps))
      0))

(defui ^:private stepper [{:keys [active]}]
  (let [active-idx (step-index active)]
    ($ :nav {:aria-label "Progress"
             :class "flex items-center gap-2 sm:gap-3"}
       (for [[idx [k label]] (map-indexed vector steps)]
         (let [done? (< idx active-idx)
               current? (= idx active-idx)]
           ($ :div {:key (name k) :class "flex items-center gap-2 sm:gap-3"}
              (when (pos? idx)
                ($ :div {:class (str "h-px w-5 sm:w-10 "
                                     (if (<= idx active-idx)
                                       "bg-gray-950/25" "bg-gray-950/10"))}))
              ($ :div {:class "flex items-center gap-1.5 sm:gap-2"}
                 ($ :span {:class (str "flex size-5 shrink-0 items-center justify-center "
                                       "rounded-full text-xs font-medium tabular-nums "
                                       (cond
                                         current? "bg-gray-950 text-white"
                                         done? "bg-gray-950/10 text-foreground"
                                         :else "bg-gray-950/5 text-muted-foreground"))}
                    (inc idx))
                 ($ :span {:class (str "hidden sm:inline text-sm "
                                       (if current?
                                         "font-medium text-foreground"
                                         "text-muted-foreground"))}
                    label))))))))

;; =============================================================================
;; Audience step
;; =============================================================================

(defui ^:private recipient-chip [{:keys [contact on-remove]}]
  (let [name (or (str/trim (str (or (get-in contact [:field-values :first-name])
                                    (:first-name contact) "") " "
                                (or (get-in contact [:field-values :last-name])
                                    (:last-name contact) "")))
                 (:email contact))]
    ($ :span {:class (str "inline-flex items-center gap-1 rounded-full "
                          "bg-gray-950/5 px-2.5 py-1 text-xs font-medium")}
       ($ :span {:class "max-w-[14ch] truncate"} name)
       ($ :button {:type "button"
                   :on-click #(on-remove (:id contact))
                   :class "rounded-full hover:bg-gray-950/10 p-0.5"}
          ($ X {:class "size-3"})))))

(defui ^:private section-eyebrow [{:keys [label]}]
  ($ :div {:class "flex items-center gap-2"}
     ($ :h3 {:class "text-xs font-semibold uppercase tracking-widest text-muted-foreground"}
        label)
     ($ :div {:class "h-px flex-1 bg-gray-950/5"})))

(defui ^:private audience-step [{:keys [api-client]}]
  (let [w (use-subscribe [::ms/wizard])
        {:keys [type-slug include-guardians?]} (use-subscribe [::ms/audience])
        predicates (use-subscribe [::ms/audience-predicates])
        field-defs (use-subscribe [::ms/audience-field-defs])
        preview (use-subscribe [::ms/audience-preview])
        contacts (use-subscribe [::ms/recipient-contacts])
        recipients (vals contacts)
        fields (field-options field-defs)
        matched-total (get-in preview [:counts :total] 0)
        manual-count (count (:recipient-ids w))
        on-pick (fn [contact]
                  (rf/dispatch [::ms/toggle-recipient (:id contact)])
                  (rf/dispatch [::ms/set-recipient-contacts
                                (vals (assoc contacts (:id contact) contact))]))]
    ;; Field-defs + initial preview are kicked off by the wizard host
    ;; (after reset-wizard); type switches reload via ::set-audience-type.
    ;; This effect just re-previews (debounced) whenever the filter changes.
    (use-effect
      (fn []
        (rf/dispatch [::ms/request-preview api-client])
        js/undefined)
      [api-client type-slug include-guardians?
       (pr-str predicates)])
    ($ :section {:class "scroll-mt-[11rem]"}
       ($ :div {:class "space-y-5"}
          ;; Contact type
          ($ :div {:class "inline-flex items-center gap-1 bg-muted/50 rounded-lg p-1"}
             (for [[k label] [["student" "Students"] ["guardian" "Guardians"]]]
               ($ :button {:key k
                           :type "button"
                           :class (str "px-3 py-1 rounded-md text-sm font-medium transition-colors "
                                       (if (= type-slug k)
                                         "bg-background text-foreground shadow-sm"
                                         "text-muted-foreground hover:text-foreground"))
                           :on-click #(rf/dispatch [::ms/set-audience-type k api-client])}
                  label)))

          ;; Filters
          ($ :div {:class "space-y-3"}
             ($ section-eyebrow {:label "Filters"})
             (if (seq predicates)
               ($ :div {:class "space-y-2"}
                  (for [p predicates]
                    ($ predicate-row {:key (str (:id p))
                                      :pred p
                                      :fields fields})))
               ($ :p {:class "text-sm text-muted-foreground"}
                  "No filters yet — add one to target a group, or search for specific people below."))
             ($ button/Button
                {:type "button" :variant "ghost" :size "sm"
                 :class "h-7 px-2 text-xs gap-1"
                 :on-click #(rf/dispatch [::ms/add-predicate])}
                ($ Plus {:class "size-3.5"}) "Add filter")
             (when (= type-slug "student")
               ($ :label {:class "flex items-center gap-2 text-sm text-muted-foreground"}
                  ($ Checkbox
                     {:checked include-guardians?
                      :onCheckedChange #(rf/dispatch [::ms/set-include-guardians %])})
                  "Also include the guardians of matched students")))

          ;; Preview
          ($ :div {:class "space-y-2"}
             ($ section-eyebrow {:label "Audience"})
             ($ preview-summary {:channel (:channel w)}))

          ;; Add someone specific
          ($ :div {:class "space-y-2"}
             ($ section-eyebrow {:label "Add someone specific"})
             ($ contact-typeahead
                {:type-slug type-slug
                 :api-client api-client
                 :placeholder (str "Search " type-slug "s by name or email…")
                 :on-select on-pick})
             (when (seq recipients)
               ($ :div {:class "flex flex-wrap gap-1.5"}
                  (for [c recipients]
                    ($ recipient-chip {:key (str (:id c))
                                       :contact c
                                       :on-remove
                                       (fn [id]
                                         (rf/dispatch [::ms/toggle-recipient id])
                                         (rf/dispatch [::ms/set-recipient-contacts
                                                       (vals (dissoc contacts id))]))}))))))
       ($ :div {:class "mt-6 flex justify-end"}
          ($ button/Button
             {:disabled (and (zero? matched-total) (zero? manual-count))
              :class "gap-1.5"
              :on-click #(rf/dispatch [::ms/resolve-and-lock :channel])}
             "Next"
             ($ ChevronRight {:class "size-3.5"}))))))

;; =============================================================================
;; Channel step
;; =============================================================================

(defui ^:private channel-card [{:keys [active? icon title description on-click]}]
  ($ :button
     {:type "button"
      :aria-pressed active?
      :on-click on-click
      :class (str "flex w-full items-start gap-3 rounded-md border p-4 text-left transition-colors "
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring "
                  (if active?
                    "border-gray-950/30 bg-gray-950/2 ring-1 ring-gray-950/20"
                    "border-gray-950/10 hover:border-gray-950/20 hover:bg-muted/40"))}
     ($ :span {:class (str "flex size-9 shrink-0 items-center justify-center rounded-md "
                           (if active?
                             "bg-gray-950 text-white"
                             "bg-muted text-muted-foreground"))}
        ($ icon {:class "size-4"}))
     ($ :div {:class "min-w-0 flex-1 space-y-0.5"}
        ($ :div {:class "flex items-center justify-between gap-2"}
           ($ :p {:class "text-base font-medium text-foreground sm:text-sm"} title)
           (when active?
             ($ Check {:class "size-4 shrink-0 text-foreground"})))
        ($ :p {:class "text-base/7 text-muted-foreground text-pretty sm:text-sm/6"} description))))

(defui ^:private mode-option [{:keys [active? icon title on-click]}]
  ($ :button
     {:type "button"
      :on-click on-click
      :class (str "inline-flex h-8 flex-1 items-center justify-center gap-1.5 rounded-md px-2 "
                  "text-xs font-medium transition-colors "
                  (if active?
                    "bg-background text-foreground shadow-sm"
                    "text-muted-foreground hover:text-foreground"))}
     ($ icon {:class "size-3.5"})
     title))

(defui ^:private channel-step []
  (let [{:keys [channel]} (use-subscribe [::ms/wizard])]
    ($ :section {:class "scroll-mt-[11rem]"}
       ($ :div {:class "max-w-2xl space-y-3"}
          ($ section-eyebrow {:label "Delivery method"})
          ($ :div {:class "grid gap-3 sm:grid-cols-2"}
             ($ channel-card
                {:active? (= channel :email)
                 :icon Mail
                 :title "Email"
                 :description "A subject line and your saved email design. Can also include external recipients."
                 :on-click #(rf/dispatch [::ms/set-channel :email])})
             ($ channel-card
                {:active? (= channel :sms)
                 :icon MessageSquare
                 :title "SMS"
                 :description "A short text to CRM recipients who have a phone number on file."
                 :on-click #(rf/dispatch [::ms/set-channel :sms])})))
       ($ :div {:class "mt-6 flex justify-between"}
          ($ button/Button {:variant "ghost"
                            :on-click #(rf/dispatch [::ms/set-step :audience])}
             "Back")
          ($ button/Button {:class "gap-1.5"
                            :on-click #(rf/dispatch [::ms/set-step :compose])}
             "Next"
             ($ ChevronRight {:class "size-3.5"}))))))

;; =============================================================================
;; Compose step
;; =============================================================================

(defui ^:private compose-step [{:keys [api-client]}]
  (let [{:keys [channel title subject body-format body body-html external-recipients-text attachments]} (use-subscribe [::ms/wizard])
        layout (use-subscribe [::templates/email-layout])
        advisor? (use-subscribe [::identity/advisor?])
        contacts (use-subscribe [::ms/recipient-contacts])
        sample (first (vals contacts))
        body-ref (use-ref nil)
        html-ref (use-ref nil)
        rte-ref (use-ref nil)
        ;; Email editor: rich (TipTap) by default, or raw-HTML "power mode".
        ;; Both write :body-html. SMS always uses the plain :body.
        [raw-html? set-raw-html!] (uix/use-state false)
        email? (= channel :email)
        html? (and email? (= body-format :html))
        active-body (if html? body-html body)
        preview-html (email-preview/render-email-preview-html
                      {:layout-html (:html layout)
                       :body-format (if html? :html :plain)
                       :body body
                       :body-html body-html})]
    ($ :section {:class "scroll-mt-[11rem]"}
       ($ :div {:class (if email?
                         "grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(22rem,0.9fr)]"
                         "space-y-3")}
          ($ :div {:class "space-y-3"}
          ($ :div {:class "flex items-center justify-between gap-3"}
             ($ :div {:class "inline-flex items-center gap-2 text-sm font-medium"}
                (if email?
                  ($ Mail {:class "size-4 text-muted-foreground"})
                  ($ MessageSquare {:class "size-4 text-muted-foreground"}))
                (if email? "Email broadcast" "SMS broadcast"))
             ($ button/Button {:type "button"
                               :variant "ghost"
                               :size "sm"
                               :class "h-8 px-2 text-xs"
                               :on-click #(rf/dispatch [::ms/set-step :channel])}
                "Change"))
          (if email?
            ($ :div {:class "grid gap-3 sm:grid-cols-2"}
               ($ Input
                  {:value (or title "")
                   :placeholder "Broadcast title"
                   :class "border-0 shadow-none ring-1 ring-black/10"
                   :on-change #(rf/dispatch-sync [::ms/set-title (.. % -target -value)])})
               ($ Input
                  {:value (or subject "")
                   :placeholder "Email subject"
                   :class "border-0 shadow-none ring-1 ring-black/10"
                   :on-change #(rf/dispatch-sync [::ms/set-subject (.. % -target -value)])}))
            ($ Input
               {:value (or title "")
                :placeholder "Broadcast title"
                :class "border-0 shadow-none ring-1 ring-black/10"
                :on-change #(rf/dispatch-sync [::ms/set-title (.. % -target -value)])}))
          ;; Insert template — re-uses the conversation panel's popover by
          ;; bridging through the comms core's set-body/set-subject events
          ;; (body-sync forwards them into the wizard draft).
          ($ :div {:class "flex items-center gap-2"}
             ($ insert-template-popover {:api-client api-client :contact sample})
             (when advisor?
               ($ :p {:class "text-xs text-muted-foreground"}
                  "Tokens expand per recipient at send time.")))
          (when email?
            ($ :div {:class "inline-flex w-full max-w-sm items-center gap-1 rounded-lg bg-muted/60 p-1"}
               ($ mode-option
                  {:active? (not raw-html?)
                   :icon Type
                   :title "Rich"
                   :on-click #(set-raw-html! false)})
               ($ mode-option
                  {:active? raw-html?
                   :icon Code2
                   :title "HTML"
                   :on-click #(set-raw-html! true)})))
          ;; Body — SMS: plain text; Email: rich editor or raw-HTML power mode.
          ($ :div {:class "space-y-1.5"}
             (cond
               (not email?)
               ($ Textarea
                  {:value (or body "")
                   :ref body-ref
                   :placeholder "Short message text"
                   :rows 5
                   :class "ring-1 ring-black/10 border-0 shadow-none"
                   :on-change #(rf/dispatch-sync [::ms/set-body (.. % -target -value)])})

               raw-html?
               ($ Textarea
                  {:value (or body-html "")
                   :ref html-ref
                   :placeholder "<p>Hi {first-name},</p>"
                   :rows 10
                   :class "font-mono text-xs ring-1 ring-black/10 border-0 shadow-none"
                   :on-change #(rf/dispatch-sync [::ms/set-body-html (.. % -target -value)])})

               :else
               ($ rte/RichTextEditor
                  {:ref rte-ref
                   :value (or body-html "")
                   :placeholder "Hi {first-name}, …"
                   :on-change #(rf/dispatch-sync [::ms/set-body-html %])}))
             (when advisor?
               (if (and email? (not raw-html?))
                 ($ tokens/token-bar
                    {:on-insert (fn [token]
                                  (when-let [ed @rte-ref] (.insertToken ed token)))})
                 ($ tokens/token-bar
                    {:input-ref (if email? html-ref body-ref)
                     :value (or active-body "")
                     :on-change #(rf/dispatch-sync [(if email? ::ms/set-body-html ::ms/set-body) %])})))
             (when-not email?
               ($ tokens/preview-line {:text body})))
          ($ :div {:class "space-y-1.5"}
             ($ :label {:class "text-sm font-medium"} "Attachments")
             ($ attachment-list
                {:api-client api-client
                 :attachments (or attachments [])
                 :on-add #(rf/dispatch [::ms/add-attachment %])
                 :on-remove #(rf/dispatch [::ms/remove-attachment %])}))
          (when email?
            ($ :div {:class "space-y-1.5"}
               ($ :label {:class "text-sm font-medium"} "External email recipients")
               ($ Textarea
                  {:value (or external-recipients-text "")
                   :placeholder "admissions@example.edu, partner@example.org"
                   :rows 3
                   :class "ring-1 ring-black/10 border-0 shadow-none"
                   :on-change #(rf/dispatch-sync [::ms/set-external-recipients-text
                                                  (.. % -target -value)])})
               ($ :p {:class "text-xs text-muted-foreground"}
                  "Separate email addresses with commas or new lines."))))
          (when email?
            ($ :div {:class "space-y-2"}
               ($ :p {:class "text-xs font-semibold uppercase tracking-widest text-muted-foreground"}
                  "Preview")
               ($ email-preview/email-preview-frame
                  {:html preview-html
                   :empty-message "Write email content to preview it."}))))
       ($ :div {:class "mt-4 flex justify-between"}
          ($ button/Button {:variant "ghost"
                            :on-click #(rf/dispatch [::ms/set-step :channel])}
             "Back")
          ($ button/Button {:disabled (or (and email? (str/blank? subject))
                                          (str/blank? active-body))
                            :class "gap-1.5"
                            :on-click #(rf/dispatch [::ms/set-step :review])}
             "Next"
             ($ ChevronRight {:class "size-3.5"}))))))

;; =============================================================================
;; Review step
;; =============================================================================

(defui ^:private review-step [{:keys [api-client]}]
  (let [{:keys [channel subject body-format body body-html recipient-ids external-recipients-text
                attachments sending? error]} (use-subscribe [::ms/wizard])
        layout (use-subscribe [::templates/email-layout])
        contacts (use-subscribe [::ms/recipient-contacts])
        n (count recipient-ids)
        email? (= channel :email)
        html? (and email? (= body-format :html))
        active-body (if html? body-html body)
        preview-html (email-preview/render-email-preview-html
                      {:layout-html (:html layout)
                       :body-format (if html? :html :plain)
                       :body body
                       :body-html body-html})
        external-count (count (remove str/blank?
                                      (map str/trim
                                           (str/split (if email? (or external-recipients-text "") "") #"[,\n]"))))]
    ($ :section {:class "scroll-mt-[11rem]"}
       ($ :div {:class "rounded-md ring-1 ring-black/5 divide-y divide-gray-950/5 bg-gray-950/2"}
          ($ :div {:class "px-3 py-2 flex items-baseline gap-3"}
             ($ :span {:class "text-xs uppercase tracking-widest text-muted-foreground w-24"} "Audience")
             ($ :span {:class "text-sm font-medium tabular-nums"}
                (str n " CRM, " external-count " external")))
          ($ :div {:class "px-3 py-2 flex items-baseline gap-3"}
             ($ :span {:class "text-xs uppercase tracking-widest text-muted-foreground w-24"} "Channel")
             ($ :span {:class "text-sm font-medium"}
                (if email? "Email" "SMS")))
          (when email?
            ($ :div {:class "px-3 py-2 flex items-baseline gap-3"}
               ($ :span {:class "text-xs uppercase tracking-widest text-muted-foreground w-24"} "Subject")
               ($ :span {:class "text-sm font-medium"} subject)))
          (when email?
            ($ :div {:class "px-3 py-2 flex items-baseline gap-3"}
               ($ :span {:class "text-xs uppercase tracking-widest text-muted-foreground w-24"} "Content")
               ($ :span {:class "text-sm font-medium"}
                  (if html? "HTML" "Plain text"))))
          ($ :div {:class "px-3 py-2"}
             ($ :p {:class "text-xs uppercase tracking-widest text-muted-foreground mb-1"} "Body")
             (if email?
               ($ email-preview/email-preview-frame
                  {:html preview-html
                   :empty-message "No email preview available."})
               ($ :pre {:class "text-sm/6 whitespace-pre-wrap font-sans text-pretty"}
                  active-body)))
          (when (seq attachments)
            ($ :div {:class "px-3 py-2 flex items-baseline gap-3"}
               ($ :span {:class "text-xs uppercase tracking-widest text-muted-foreground w-24"} "Attachments")
               ($ :span {:class "text-sm font-medium"}
                  (str/join ", " (map :file-name attachments))))))
       (when (seq contacts)
         ($ :details {:class "mt-3 text-sm"}
            ($ :summary {:class "cursor-pointer text-muted-foreground hover:text-foreground"}
               (str "Preview recipient list (" n ")"))
            ($ :ul {:class "mt-2 grid grid-cols-2 gap-x-4 gap-y-1"}
               (for [c (vals contacts)]
                 (let [nm (str (or (get-in c [:field-values :first-name]) "")
                                    " "
                                    (or (get-in c [:field-values :last-name]) ""))]
                   ($ :li {:key (str (:id c)) :class "flex items-center gap-2 text-xs"}
                      ($ student-avatar {:name nm :size :sm})
                      ($ contact-link
                         {:contact-id (:id c)
                          :type-slug (:type-slug c)
                          :label nm
                          :class "truncate"})))))))
       (when error
         ($ :p {:class "mt-3 text-sm text-rose-700"} error))
       ($ :div {:class "mt-4 flex justify-between"}
          ($ button/Button {:variant "ghost"
                            :on-click #(rf/dispatch [::ms/set-step :compose])}
             "Back")
          ($ button/Button {:disabled (or sending?
                                          (and (zero? n) (zero? external-count))
                                          (and email? (str/blank? subject))
                                          (str/blank? active-body))
                            :class "gap-1.5"
                            :on-click #(rf/dispatch [::ms/submit api-client])}
             ($ Send {:class "size-3.5"})
             (if sending? "Sending…" "Send broadcast"))))))

;; =============================================================================
;; Sent step (handoff to report page)
;; =============================================================================

(defui ^:private sent-step []
  (let [{:keys [last-run-id]} (use-subscribe [::ms/wizard])
        ctx (context/use-context)
        navigate! (:router/navigate! ctx)]
    (use-effect
      (fn []
        (when last-run-id
          (navigate! :hub-message-broadcast {:run-id (str last-run-id)}))
        js/undefined)
      [navigate! last-run-id])
    ($ :p {:class "py-12 text-center text-sm text-muted-foreground italic"}
       "Sending…")))

;; =============================================================================
;; Body / subject sync — bridge wizard composer body to the comms.composer
;; cache that the Insert template popover writes into.
;; =============================================================================

(defn- plain->html
  "Wrap plain text (e.g. an inserted template body) as simple HTML paragraphs
   so it renders in the rich email editor. Merge tokens like {first-name} pass
   through untouched for server-side expansion."
  [s]
  (->> (str/split (or s "") #"\r?\n")
       (map (fn [line]
              (str "<p>"
                   (-> line
                       (str/replace "&" "&amp;")
                       (str/replace "<" "&lt;")
                       (str/replace ">" "&gt;"))
                   "</p>")))
       (str/join)))

(defui ^:private body-sync []
  ;; The insert-template popover writes into [:comms :composer] via
  ;; ::comms/set-body / ::comms/set-subject. Forward those into the
  ;; wizard draft so the popover is plug-compatible without ceremony.
  ;; Email composes as HTML, so a plain template body is wrapped before it
  ;; reaches the rich editor; SMS keeps the plain body.
  (let [{:keys [channel]} (use-subscribe [::ms/wizard])
        body (use-subscribe [::comms/body])
        subject (use-subscribe [::comms/subject])]
    (use-effect
      (fn []
        (when (seq body)
          (if (= channel :email)
            (rf/dispatch [::ms/set-body-html (plain->html body)])
            (rf/dispatch [::ms/set-body body])))
        js/undefined)
      [body channel])
    (use-effect
      (fn []
        (when (seq subject) (rf/dispatch [::ms/set-subject subject]))
        js/undefined)
      [subject])
    nil))

;; =============================================================================
;; Page
;; =============================================================================

(defui mass-send-wizard []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        {:keys [step]} (use-subscribe [::ms/wizard])]
    (use-effect
      (fn []
        ;; reset-wizard is a synchronous reg-event-db, so the audience
        ;; type-slug is in the db before these run — no mount-order race.
        (rf/dispatch [::ms/reset-wizard])
        (rf/dispatch [::comms/set-channel :email])
        (rf/dispatch [::identity/ensure-profile api-client])
        (rf/dispatch [::templates/load-email-layout api-client])
        (rf/dispatch [::ms/load-field-defs api-client])
        (rf/dispatch [::ms/request-preview api-client])
        js/undefined)
      [api-client])
    ($ :div {:class "flex w-full max-w-none flex-col"}
       ($ body-sync)
       ($ :div {:class "sticky top-0 z-10 -mt-6 flex items-center justify-between gap-3 border-b border-gray-950/5 bg-background/95 pt-6 pb-3 backdrop-blur"}
          ($ :h1 {:class "text-xl font-semibold tracking-tight text-foreground sm:text-lg"}
             "New broadcast")
          ($ button/Button {:variant "ghost"
                            :class "h-8 px-3 text-xs"
                            :on-click #(navigate! :hub-messages)}
             "Cancel"))
       ;; Horizontal stepper — sole step indicator (no per-step eyebrows)
       (when (not= step :sent)
         ($ :div {:class "border-b border-gray-950/5 py-4"}
            ($ stepper {:active step})))
       ($ :div {:class "min-w-0 py-6"}
          (case step
            :audience ($ audience-step {:api-client api-client})
            :channel ($ channel-step)
            :compose ($ compose-step {:api-client api-client})
            :review ($ review-step {:api-client api-client})
            :sent ($ sent-step)
            ($ audience-step {:api-client api-client}))))))
