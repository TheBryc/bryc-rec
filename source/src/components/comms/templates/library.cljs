(ns components.comms.templates.library
  "Surface 3 of the comms feature: the templates library page at
   /hub/messages/templates. Two sections — Org library + My snippets —
   share an inline edit sheet for create/update and a delete affordance.

   Mail-merge tokens supported in body: {first-name}, {last-name},
   {email}, {phone}, {full-name}. Tokens resolve at insert time against the
   recipient (see store.comms.templates/expand-tokens)."
  (:require [uix.core :as uix :refer [defui $ use-effect use-ref use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.context.interface :as context]
            [components.comms.email-preview :as email-preview]
            [components.comms.tokens :as tokens]
            [store.comms.identity :as identity]
            [store.comms.templates :as templates]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/collapsible" :as collapsible]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/sheet" :refer [Sheet SheetContent SheetHeader SheetTitle SheetFooter]]
            ["lucide-react" :refer [Plus Trash2 Pencil Code2 Mail ChevronDown]]))

;; =============================================================================
;; Field primitives — consistent label + control rhythm
;; =============================================================================

(defui ^:private field
  "A labeled form row. `for` ties the label to the control via id."
  [{:keys [label html-for hint children]}]
  ($ :div {:class "space-y-1.5"}
     ($ :label {:class "text-sm font-medium text-foreground"
                :htmlFor html-for}
        label)
     children
     (when hint
       ($ :p {:class "text-xs text-muted-foreground"} hint))))

(defui ^:private segmented
  "A segmented single-select. Options is a vec of [value label]."
  [{:keys [value options on-change]}]
  ($ :div {:class "inline-flex w-full items-center gap-1 rounded-lg bg-muted/60 p-1"}
     (for [[k lbl] options]
       ($ :button
          {:key (name k)
           :type "button"
           :on-click #(on-change k)
           :class (str "flex-1 rounded-md px-3 py-1.5 text-sm font-medium "
                       "transition-colors "
                       (if (= value k)
                         "bg-background text-foreground shadow-sm"
                         "text-muted-foreground hover:text-foreground"))}
          lbl))))

;; =============================================================================
;; Edit sheet
;; =============================================================================

(defui ^:private editor-sheet
  [{:keys [api-client]}]
  (let [open? (use-subscribe [::templates/editor-open?])
        draft (use-subscribe [::templates/editor-draft])
        saving? (use-subscribe [::templates/saving?])
        error (use-subscribe [::templates/editor-error])
        advisor? (use-subscribe [::identity/advisor?])
        body-ref (use-ref nil)
        valid? (and draft
                    (seq (str/trim (or (:name draft) "")))
                    (or (not= :email (:channel draft))
                        (seq (str/trim (or (:subject draft) ""))))
                    (seq (str/trim (or (:body draft) ""))))
        on-save #(when valid?
                   (rf/dispatch [::templates/save-template api-client
                                 (cond-> (select-keys draft [:name :channel :subject :body :body-html :owner-scope])
                                   (:template-id draft) (assoc :template-id (:template-id draft)))]))]
    ($ Sheet {:open open?
              :onOpenChange #(when-not %
                               (rf/dispatch [::templates/close-editor]))}
       ($ SheetContent {:class "w-full gap-0 p-0 sm:max-w-md"}
          ($ SheetHeader {:class "border-b border-gray-950/5 px-6 py-4"}
             ($ SheetTitle {:class "text-base tracking-tight"}
                (if (:template-id draft) "Edit template" "New template")))
          (when draft
            ($ :<>
               ;; Scrollable body
               ($ :div {:class "flex-1 space-y-5 overflow-y-auto px-6 py-5"}
                  ($ field {:label "Name" :html-for "tmpl-name"}
                     ($ Input {:id "tmpl-name"
                               :name "template-name"
                               :autoComplete "off"
                               :data-1p-ignore "true"
                               :data-lpignore "true"
                               :data-form-type "other"
                               :value (or (:name draft) "")
                               :placeholder "Welcome — Fellow"
                               :on-change #(rf/dispatch-sync [::templates/set-draft-field :name (.. % -target -value)])
                               :class "border-0 shadow-none ring-1 ring-black/10"}))
                  ($ field {:label "Channel"}
                     ($ segmented {:value (:channel draft)
                                   :options [[:email "Email"] [:sms-alert "SMS alert"]]
                                   :on-change #(rf/dispatch [::templates/set-draft-field :channel %])}))
                  (when (= :email (:channel draft))
                    ($ field {:label "Subject" :html-for "tmpl-subject"}
                       ($ Input {:id "tmpl-subject"
                                 :name "template-subject"
                                 :autoComplete "off"
                                 :data-1p-ignore "true"
                                 :data-lpignore "true"
                                 :data-form-type "other"
                                 :value (or (:subject draft) "")
                                 :placeholder "Program update"
                                 :on-change #(rf/dispatch-sync [::templates/set-draft-field :subject (.. % -target -value)])
                                 :class "border-0 shadow-none ring-1 ring-black/10"})))
                  ($ field {:label "Scope"
                            :hint (if (= :org (:owner-scope draft))
                                    "Shared with everyone in the workspace."
                                    "Only visible to you.")}
                     ($ segmented {:value (:owner-scope draft)
                                   :options [[:user "My snippet"] [:org "Org library"]]
                                   :on-change #(rf/dispatch [::templates/set-draft-field :owner-scope %])}))
                  ($ field {:label "Body" :html-for "tmpl-body"}
                     ($ :<>
                        ($ Textarea {:id "tmpl-body"
                                     :name "template-body"
                                     :ref body-ref
                                     :autoComplete "off"
                                     :data-1p-ignore "true"
                                     :data-lpignore "true"
                                     :data-form-type "other"
                                     :value (or (:body draft) "")
                                     :placeholder "Hi {first-name}, …"
                                     :rows 8
                                     :on-change #(rf/dispatch-sync [::templates/set-draft-field :body (.. % -target -value)])
                                     :class "resize-y border-0 shadow-none ring-1 ring-black/10"})
                        (when advisor?
                          ($ tokens/token-bar
                             {:input-ref body-ref
                              :value (or (:body draft) "")
                              :on-change #(rf/dispatch-sync [::templates/set-draft-field :body %])}))
                        ($ tokens/preview-line {:text (:body draft)})))
                  (when error
                    ($ :p {:class "text-sm text-destructive"} error)))
               ;; Pinned footer
               ($ SheetFooter {:class "flex-row justify-end gap-2 border-t border-gray-950/5 px-6 py-4"}
                  ($ button/Button
                     {:type "button"
                      :variant "ghost"
                      :on-click #(rf/dispatch [::templates/close-editor])}
                     "Cancel")
                  ($ button/Button
                     {:type "button"
                      :disabled (or saving? (not valid?))
                      :on-click on-save}
                     (if saving? "Saving…" "Save template")))))))))

;; =============================================================================
;; List section
;; =============================================================================

(def default-layout-html
  "<!doctype html>
<html>
  <body style='margin:0; padding:0; background:#f7f2e8; font-family:Arial, Helvetica, sans-serif; color:#22313f;'>
    <div style='display:none; max-height:0; overflow:hidden; opacity:0; color:transparent;'>
      A BRYC update for Fellows, families, volunteers, and partners.
    </div>

    <table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='width:100%; background:#f7f2e8; border-collapse:collapse;'>
      <tr>
        <td align='center' style='padding:28px 14px;'>
          <table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='width:100%; max-width:640px; background:#ffffff; border-collapse:collapse; border:1px solid #e8dfd0;'>
            <tr>
              <td align='center' style='padding:24px 28px 20px 28px; background:#ffffff;'>
                <a href='https://thebryc.org/' style='text-decoration:none;'>
                  <img src='https://thebryc.org/wp-content/uploads/2022/02/cropped-Main-logo-transparent-background-1536x449.png' width='230' alt='Baton Rouge Youth Coalition' style='display:block; width:230px; max-width:100%; height:auto; border:0;'>
                </a>
              </td>
            </tr>

            <tr>
              <td style='background:#079a9a; padding:22px 28px; text-align:center;'>
                <div style='font-size:13px; line-height:18px; letter-spacing:2px; text-transform:uppercase; color:#fff2bd; font-weight:bold;'>
                  Enter. Persist. Graduate.
                </div>
                <div style='padding-top:8px; font-size:15px; line-height:22px; color:#ffffff;'>
                  Helping students enter best-fit programs, persist through college, and launch meaningful careers.
                </div>
              </td>
            </tr>

            <tr>
              <td style='height:8px; background:#f2c84b; font-size:8px; line-height:8px;'>&nbsp;</td>
            </tr>

            <tr>
              <td style='padding:32px 28px 20px 28px; font-size:16px; line-height:25px; color:#22313f;'>
                {{content}}
              </td>
            </tr>

            <tr>
              <td style='padding:0 28px 28px 28px;'>
                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='border-collapse:collapse; background:#fbf8f1; border-left:4px solid #7f3f98;'>
                  <tr>
                    <td style='padding:16px 18px; font-size:14px; line-height:21px; color:#394855;'>
                      BRYC is a free program that helps students succeed in high school, earn degrees, and secure jobs.
                    </td>
                  </tr>
                </table>
              </td>
            </tr>

            <tr>
              <td style='background:#22313f; padding:24px 28px;'>
                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' style='border-collapse:collapse;'>
                  <tr>
                    <td style='font-size:13px; line-height:20px; color:#ffffff; font-weight:bold;'>
                      Baton Rouge Youth Coalition
                    </td>
                  </tr>
                  <tr>
                    <td style='padding-top:6px; font-size:12px; line-height:19px; color:#d9e1e8;'>
                      448 N 11th Street, Baton Rouge, LA 70802<br>
                      (225) 456-5752&nbsp;&nbsp;|&nbsp;&nbsp;<a href='https://thebryc.org/' style='color:#f2c84b; text-decoration:underline;'>thebryc.org</a>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>

          <div style='max-width:640px; padding:14px 8px 0 8px; font-size:11px; line-height:17px; color:#6b7280; text-align:center;'>
            You are receiving this message from the Baton Rouge Youth Coalition.
          </div>
        </td>
      </tr>
    </table>
  </body>
</html>")

(defui ^:private email-layout-section
  "Compact, collapsed-by-default panel for the org-wide email layout. The layout
   rarely changes, so it stays out of the way of the template library until an
   advisor expands it to edit the design."
  [{:keys [api-client]}]
  (let [draft (use-subscribe [::templates/email-layout-draft])
        loading? (use-subscribe [::templates/email-layout-loading?])
        saving? (use-subscribe [::templates/email-layout-saving?])
        error (use-subscribe [::templates/email-layout-error])
        [expanded? set-expanded!] (use-state false)
        [source-open? set-source-open!] (use-state false)
        valid? (email-preview/has-placeholder? draft)
        has-design? (seq (str/trim (or draft "")))
        preview-html (email-preview/render-email-preview-html
                      {:layout-html draft
                       :body-format :html
                       :body-html email-preview/sample-content})]
    ($ collapsible/Collapsible
       {:open expanded?
        :onOpenChange set-expanded!
        :class "scroll-mt-[11rem] overflow-hidden rounded-md border border-gray-950/10 bg-white"}
       ;; Compact trigger row — always visible, shows status at a glance
       ($ collapsible/CollapsibleTrigger {:asChild true}
          ($ :button
             {:type "button"
              :class (str "flex w-full items-center gap-3 px-4 py-3 text-left transition-colors "
                          "hover:bg-muted/40 focus-visible:outline-none "
                          "focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset")}
             ($ :span {:class "flex size-9 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground"}
                ($ Mail {:class "size-4"}))
             ($ :div {:class "min-w-0 flex-1"}
                ($ :div {:class "flex flex-wrap items-center gap-x-2 gap-y-1"}
                   ($ :h2 {:class "text-base font-semibold tracking-tight text-foreground sm:text-sm"}
                      "Default email design")
                   ($ :span {:class (str "rounded-full px-2 py-0.5 text-sm font-medium sm:text-xs "
                                         (if valid?
                                           "bg-emerald-50 text-emerald-700"
                                           "bg-amber-50 text-amber-800"))}
                      (if valid? "Ready" "Needs content marker")))
                ($ :p {:class "mt-0.5 truncate text-sm text-muted-foreground sm:text-xs"}
                   "Wraps every email broadcast · rarely needs changes"))
             ($ :span {:class "flex shrink-0 items-center gap-1.5 text-sm text-muted-foreground sm:text-xs"}
                ($ :span {:class "max-sm:hidden"} (if expanded? "Hide" "Customize"))
                ($ ChevronDown {:class (str "size-4 transition-transform duration-200 "
                                            (when expanded? "rotate-180"))}))))
       ($ collapsible/CollapsibleContent
          ($ :div {:class "space-y-5 border-t border-gray-950/5 p-4"}
             ($ :p {:class "max-w-[64ch] text-base/7 text-muted-foreground sm:text-sm/6"}
                "This design wraps every email broadcast. The message itself is inserted into the saved layout automatically.")
             ($ :div {:class "grid gap-5 lg:grid-cols-[minmax(0,1fr)_21rem]"}
                ($ :div {:class "min-w-0"}
                   ($ email-preview/email-preview-frame
                      {:html preview-html
                       :invalid? (and has-design? (not valid?))
                       :empty-message "No email design saved yet."}))
                ($ :aside {:class "space-y-4"}
                   ($ :div {:class "space-y-1"}
                      ($ :h3 {:class "text-base font-semibold text-foreground sm:text-sm"} "Design controls")
                      ($ :p {:class "text-base/7 text-muted-foreground sm:text-sm/6"}
                         "Use the starter design or paste a design from an AI tool."))
                   ($ :div {:class "grid gap-2"}
                      ($ button/Button
                         {:type "button"
                          :disabled (or loading? saving? (not valid?))
                          :class "h-9 w-full justify-center text-sm sm:h-8"
                          :on-click #(rf/dispatch [::templates/save-email-layout api-client])}
                         (if saving? "Saving…" "Save design"))
                      ($ button/Button
                         {:type "button"
                          :variant "outline"
                          :disabled loading?
                          :class "h-8 w-full justify-center gap-2 text-sm sm:h-7"
                          :on-click #(set-source-open! (not source-open?))}
                         ($ Code2 {:class "size-4"})
                         (if source-open? "Hide source" "Paste or edit design"))
                      ($ button/Button
                         {:type "button"
                          :variant "ghost"
                          :disabled loading?
                          :class "h-8 w-full justify-center text-sm sm:h-7"
                          :on-click #(do
                                       (rf/dispatch-sync [::templates/set-email-layout-draft default-layout-html])
                                       (set-source-open! true))}
                         "Use starter design"))
                   ($ :div {:class "rounded-md bg-muted/40 p-3"}
                      ($ :p {:class (str "text-base/7 sm:text-sm/6 "
                                      (if valid? "text-muted-foreground" "text-rose-700"))}
                         (if valid?
                           "Broadcast messages will appear at the content marker."
                           "Add the content marker so broadcast messages have a place to appear.")))
                   (when error
                     ($ :p {:class "text-sm text-destructive"} error))))
             ($ collapsible/Collapsible {:open source-open? :onOpenChange set-source-open!}
                ($ collapsible/CollapsibleContent
                   ($ :div {:class "rounded-md bg-muted/40 p-4"}
                      ($ :div {:class "mb-3 flex flex-wrap items-start justify-between gap-3"}
                         ($ :div {:class "space-y-1"}
                            ($ :h3 {:class "text-base font-semibold text-foreground sm:text-sm"} "Email design source")
                            ($ :p {:class "text-base/7 text-muted-foreground sm:text-sm/6"}
                               "Paste the email design from your AI tool here."))
                         ($ button/Button
                            {:type "button"
                             :variant "outline"
                             :size "sm"
                             :disabled loading?
                             :class "h-8 px-2 text-sm sm:h-7 sm:text-xs"
                             :on-click #(rf/dispatch-sync [::templates/set-email-layout-draft
                                                           (email-preview/add-placeholder draft)])}
                            "Add content marker"))
                      ($ Textarea
                         {:value (or draft "")
                          :placeholder default-layout-html
                          :aria-label "Email design source"
                          :name "email-design-source"
                          :rows 14
                          :class "min-h-[20rem] font-mono text-sm resize-y border-0 shadow-none ring-1 ring-black/10 sm:text-xs max-sm:text-base/6"
                          :on-change #(rf/dispatch-sync [::templates/set-email-layout-draft
                                                          (.. % -target -value)])})
                      ($ :p {:class "mt-3 text-base/7 text-muted-foreground sm:text-sm/6"}
                         "Images need public links. Use regular links for buttons; scripts, forms, and embedded video will not work in email.")))))))))

(defui ^:private template-card
  [{:keys [t api-client]}]
  (let [channel-label (case (:channel t)
                        :email "Email"
                        :sms-alert "SMS alert"
                        :sms "SMS"
                        (some-> (:channel t) name))
        saved-date (or (some-> (:saved-at t) (subs 0 10)) "Not saved")]
    ($ :div {:class "group flex items-start gap-3 py-4 first:pt-0 last:pb-0"}
       ($ :div {:class "min-w-0 flex-1 space-y-1"}
          ($ :div {:class "flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1"}
             ($ :p {:class "truncate text-base font-medium tracking-tight text-foreground sm:text-sm"} (:name t))
             ($ :span {:class "rounded-full bg-muted px-2 py-0.5 text-sm font-medium text-muted-foreground sm:text-xs"}
                channel-label))
          ($ :p {:class "text-base/7 text-muted-foreground line-clamp-2 sm:text-sm/6"} (:body t))
          ($ :p {:class "text-sm text-muted-foreground sm:text-xs"} (str "Last edited " saved-date)))
       ($ :div {:class "flex shrink-0 items-center gap-1"}
          ($ button/Button
             {:variant "ghost" :size "sm"
              :class "h-8 px-2 text-sm gap-1 sm:h-7 sm:text-xs"
              :on-click #(rf/dispatch [::templates/open-editor t])}
             ($ Pencil {:class "h-3.5 w-3.5"})
             "Edit")
          ($ button/Button
             {:variant "ghost" :size "sm"
              :aria-label "Delete template"
              :class "h-8 px-2 text-sm gap-1 text-muted-foreground hover:text-rose-700 sm:h-7 sm:text-xs"
              :on-click #(rf/dispatch [::templates/delete-template api-client (:template-id t)])}
             ($ Trash2 {:class "h-3.5 w-3.5"}))))))

(defui ^:private templates-section
  [{:keys [title items api-client empty-message]}]
  ($ :section {:class "scroll-mt-[11rem] rounded-md border border-gray-950/10 bg-white p-4"}
     ($ :div {:class "mb-1 flex items-center justify-between gap-3"}
        ($ :h3 {:class "text-base font-semibold tracking-tight text-foreground sm:text-sm"}
           title)
        ($ :span {:class "text-sm text-muted-foreground sm:text-xs"}
           (str (count items) " saved")))
     (if (seq items)
       ($ :div {:class "divide-y divide-gray-950/5"}
          (for [t items]
            ($ template-card {:key (str (:template-id t))
                               :t t
                               :api-client api-client})))
       ($ :div {:class "py-8 text-center"}
          ($ :p {:class "text-base/7 text-muted-foreground sm:text-sm/6"} empty-message)))))

;; =============================================================================
;; Page
;; =============================================================================

(defui templates-library []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        org (use-subscribe [::templates/org])
        snippets (use-subscribe [::templates/user-snippets])
        loading? (use-subscribe [::templates/loading?])]

    (use-effect
      (fn []
        (rf/dispatch [::templates/load-templates api-client])
        (rf/dispatch [::templates/load-email-layout api-client])
        (rf/dispatch [::identity/ensure-profile api-client])
        js/undefined)
      [api-client])

    ($ :div {:class "flex w-full max-w-none flex-col"}
       ($ :div {:class "sticky top-0 z-10 -mt-6 border-b border-gray-950/5 bg-background/95 pt-6 pb-4 backdrop-blur"}
          ($ :div {:class "flex flex-wrap items-start justify-between gap-3"}
             ($ :div
                ($ :h1 {:class "text-xl font-semibold tracking-tight text-foreground sm:text-lg"}
                   "Message templates")
                ($ :p {:class "mt-1 max-w-[68ch] text-base/7 text-muted-foreground sm:text-sm/6"}
                   "Manage reusable message snippets and the default design used for email broadcasts."))
          ($ button/Button
             {:size "sm"
              :variant "outline"
              :class "h-9 px-3 text-sm gap-1.5 sm:h-8 sm:text-xs"
              :on-click #(rf/dispatch [::templates/open-editor nil])}
             ($ Plus {:class "h-3.5 w-3.5"})
             "New template")))

       (if loading?
         ($ :p {:class "px-4 py-10 text-center text-base/7 text-muted-foreground sm:text-sm/6"} "Loading…")
         ($ :div {:class "space-y-8 py-6"}
            ($ :section {:class "scroll-mt-[11rem]"}
               ($ :div {:class "mb-4 flex items-end justify-between gap-3"}
                  ($ :div
                     ($ :h2 {:class "text-lg font-semibold tracking-tight text-foreground"}
                        "Template library")
                     ($ :p {:class "mt-1 text-base/7 text-muted-foreground sm:text-sm/6"}
                        "Reusable copy for broadcasts and one-to-one messages.")))
               ($ :div {:class "grid gap-5 xl:grid-cols-2"}
                  ($ templates-section
                     {:title "Org library"
                      :items org
                      :api-client api-client
                      :empty-message "No org-wide templates yet."})
                  ($ templates-section
                     {:title "My snippets"
                      :items snippets
                      :api-client api-client
                      :empty-message "No personal snippets yet."})))
            ($ email-layout-section {:api-client api-client})))

       ($ editor-sheet {:api-client api-client}))))
