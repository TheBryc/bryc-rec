(ns components.crm.staff
  "CRM list and detail surfaces for BRYC staff contacts."
  (:require [uix.core :refer [defui $ use-callback use-effect use-state]]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            [components.api.interface :as api]
            [components.context.interface :as context]
            [components.crm.profile-photo :refer [profile-photo]]
            [anomalies :refer [anomaly?]]))

(defn display-name [contact]
  (let [fv (:field-values contact)]
    (str/trim (str (:first-name fv) " " (:last-name fv)))))

(defui staff-list [{:keys [_current-match]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        [contacts set-contacts!] (use-state [])
        [loading? set-loading!] (use-state true)
        [search set-search!] (use-state "")]
    (use-effect
     (fn []
       (go
         (let [result (<! (api/query api-client {:query/name :crm/list-contacts
                                                 :type-slug "staff"}))]
           (set-loading! false)
           (when-not (anomaly? result) (set-contacts! (:contacts result)))))
       js/undefined)
     [api-client])
    (let [needle (str/lower-case search)
          visible (filter (fn [contact]
                            (let [fv (:field-values contact)]
                              (or (str/includes? (str/lower-case (display-name contact)) needle)
                                  (str/includes? (str/lower-case (or (:email fv) "")) needle))))
                          contacts)]
      ($ :div {:class "mx-auto max-w-5xl space-y-6"}
         ($ :div
            ($ :p {:class "text-sm font-medium uppercase tracking-wider text-teal-700"} "People")
            ($ :h1 {:class "text-3xl font-semibold tracking-tight"} "Staff")
            ($ :p {:class "mt-1 text-muted-foreground"} "Advisor identities and staff contact details."))
         ($ :input {:type "search" :value search :placeholder "Search staff by name or email"
                    :class "w-full rounded-lg border bg-background px-4 py-3 text-sm"
                    :on-change #(set-search! (.. % -target -value))})
         (cond
           loading? ($ :p {:class "py-12 text-center text-muted-foreground"} "Loading staff…")
           (empty? visible) ($ :p {:class "rounded-xl border p-8 text-center text-muted-foreground"} "No staff found.")
           :else
           ($ :div {:class "overflow-hidden rounded-xl border bg-card"}
              (for [contact visible
                    :let [fv (:field-values contact)
                          contact-name (display-name contact)]]
                ($ :button {:key (str (:id contact)) :type "button"
                            :class "flex w-full items-center gap-4 border-b p-4 text-left last:border-b-0 hover:bg-muted/50"
                            :on-click #(navigate! :hub-staff-detail {:staff-id (:id contact)})}
                   ($ profile-photo {:api-client api-client :contact-id (:id contact)
                                     :file-id (:profile-photo fv) :name contact-name
                                     :size-class "h-12 w-12"})
                   ($ :div {:class "min-w-0 flex-1"}
                      ($ :div {:class "font-medium"} contact-name)
                      ($ :div {:class "truncate text-sm text-muted-foreground"} (:email fv)))
                   ($ :div {:class "hidden text-sm text-muted-foreground sm:block"}
                      (or (:title fv) (some-> (:role fv) name str/capitalize) "Staff"))))))))))

(defui editable-field [{:keys [label value type options on-save]}]
  (let [[draft set-draft!] (use-state (or value ""))]
    (use-effect (fn [] (set-draft! (or value "")) js/undefined) [value])
    ($ :label {:class "space-y-1.5"}
       ($ :span {:class "text-sm font-medium"} label)
       (if options
         ($ :select {:value (if (keyword? draft) (name draft) draft)
                     :class "w-full rounded-md border bg-background px-3 py-2"
                     :on-change (fn [e]
                                  (let [v (.. e -target -value)]
                                    (set-draft! v)
                                    (on-save (keyword v))))}
            (for [option options]
              ($ :option {:key (name option) :value (name option)} (str/capitalize (name option)))))
         ($ :input {:type (or type "text") :value draft
                    :class "w-full rounded-md border bg-background px-3 py-2"
                    :on-change #(set-draft! (.. % -target -value))
                    :on-blur #(when (not= draft (or value "")) (on-save draft))})))))

(defui staff-detail [{:keys [current-match]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        staff-id (some-> (get-in current-match [:query-params :staff-id]) parse-uuid)
        [contact set-contact!] (use-state nil)
        [error set-error!] (use-state nil)
        load! (use-callback
               (fn []
                 (go
                   (let [result (<! (api/query api-client {:query/name :crm/get-contact
                                                          :contact-id staff-id}))]
                     (if (anomaly? result) (set-error! "Staff contact not found.")
                         (set-contact! (:contact result))))))
               [api-client staff-id])
        save! (fn [slug value]
                (go
                  (let [result (<! (api/command api-client {:command/name :crm/set-contact-field
                                                           :contact-id staff-id
                                                           :field-slug slug :value value}))]
                    (if (anomaly? result)
                      (set-error! "Could not save that field.")
                      (set-contact! (fn [c] (assoc-in c [:field-values (keyword slug)] value)))))))]
    (use-effect (fn [] (when staff-id (load!)) js/undefined) [load! staff-id])
    (if-not contact
      ($ :div {:class "py-12 text-center text-muted-foreground"} (or error "Loading staff contact…"))
      (let [fv (:field-values contact)
            full-name (display-name contact)]
        ($ :div {:class "mx-auto max-w-4xl space-y-6"}
           ($ :button {:type "button" :class "text-sm font-medium text-teal-700 hover:underline"
                       :on-click #(navigate! :hub-staff)} "← Back to staff")
           ($ :section {:class "rounded-2xl border bg-card p-6 shadow-sm"}
              ($ :div {:class "flex flex-col gap-6 sm:flex-row sm:items-center"}
                 ($ profile-photo {:api-client api-client :contact-id (:id contact)
                                   :file-id (:profile-photo fv) :name full-name :editable? true
                                   :size-class "h-24 w-24"
                                   :on-change #(set-contact! (fn [c] (assoc-in c [:field-values :profile-photo] %)))})
                 ($ :div
                    ($ :p {:class "text-sm font-medium uppercase tracking-wider text-teal-700"} "Staff contact")
                    ($ :h1 {:class "text-3xl font-semibold tracking-tight"} full-name)
                    ($ :p {:class "mt-1 text-muted-foreground"} (or (:title fv) "BRYC staff")))))
           (when error ($ :div {:class "rounded-md bg-destructive/10 p-3 text-sm text-destructive"} error))
           ($ :section {:class "rounded-2xl border bg-card p-6"}
              ($ :h2 {:class "mb-5 text-lg font-semibold"} "Contact details")
              ($ :div {:class "grid gap-5 sm:grid-cols-2"}
                 ($ editable-field {:label "First name" :value (:first-name fv) :on-save #(save! "first-name" %)})
                 ($ editable-field {:label "Last name" :value (:last-name fv) :on-save #(save! "last-name" %)})
                 ($ editable-field {:label "Email" :type "email" :value (:email fv) :on-save #(save! "email" %)})
                 ($ editable-field {:label "Phone" :type "tel" :value (:phone fv) :on-save #(save! "phone" %)})
                 ($ editable-field {:label "Internal title" :value (:title fv) :on-save #(save! "title" %)})
                 ($ editable-field {:label "Role" :value (:role fv)
                                    :options [:advisor :senior-team :program-manager]
                                    :on-save #(save! "role" %)})
                 ($ editable-field {:label "Scheduling URL" :type "url" :value (:scheduling-url fv)
                                    :on-save #(save! "scheduling-url" %)})
                 ($ :label {:class "flex items-center gap-3 self-end rounded-md border px-3 py-2"}
                    ($ :input {:type "checkbox" :checked (boolean (:active fv))
                               :on-change #(save! "active" (.. % -target -checked))})
                    ($ :span {:class "text-sm font-medium"} "Active staff member")))))))))
