(ns components.comms.mass-send.delivery-report
  "Delivery-report page for a mass-send run at
   /hub/messages/broadcasts/:run-id. Shows the stat tiles (sent /
   skipped / failed) plus a per-recipient table."
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.context.interface :as context]
            [components.comms.contact-link :refer [contact-link]]
            [store.comms.mass-send :as ms]
            ["lucide-react" :refer [Check XCircle MinusCircle]]))

(defn- status-pill [status]
  (let [{:keys [class label icon]}
        (case status
          :sent {:class "bg-emerald-50 text-emerald-700 ring-emerald-200"
                 :label "Sent" :icon Check}
          :skipped {:class "bg-gray-100 text-gray-700 ring-gray-200"
                    :label "Skipped" :icon MinusCircle}
          :failed {:class "bg-rose-50 text-rose-700 ring-rose-200"
                   :label "Failed" :icon XCircle}
          {:class "bg-gray-100 text-gray-700 ring-gray-200"
           :label (str status) :icon MinusCircle})]
    ($ :span {:class (str "inline-flex items-center gap-1 rounded-full ring-1 "
                          "px-2 py-0.5 text-xs font-medium " class)}
       ($ icon {:class "size-3"})
       label)))

(defn- delivery-pill [status error-code]
  (let [{:keys [class label]}
        (case status
          :delivered {:class "bg-emerald-50 text-emerald-700 ring-emerald-200" :label "Delivered"}
          :undelivered {:class "bg-amber-50 text-amber-800 ring-amber-200" :label "Undelivered"}
          :failed {:class "bg-rose-50 text-rose-700 ring-rose-200" :label "Failed"}
          :sent {:class "bg-gray-100 text-gray-600 ring-gray-200" :label "Sent"}
          :queued {:class "bg-gray-100 text-gray-600 ring-gray-200" :label "Queued"}
          nil)]
    (when class
      ($ :span {:class (str "inline-flex items-center rounded-full ring-1 ring-inset "
                            "px-2 py-0.5 text-xs font-medium " class)}
         (if (and error-code (#{:undelivered :failed} status))
           (str label " · " error-code)
           label)))))

(defui ^:private stat-tile [{:keys [label value tone]}]
  ($ :div {:class (str "rounded-md ring-1 ring-black/5 px-4 py-3 "
                       (case tone
                         :sent "bg-emerald-50/40"
                         :delivered "bg-emerald-50/60"
                         :skipped "bg-gray-50"
                         :failed "bg-rose-50/40"
                         "bg-gray-50"))}
     ($ :p {:class "text-xs uppercase tracking-widest text-muted-foreground"} label)
     ($ :p {:class "mt-1 text-2xl font-semibold tabular-nums"} value)))

(defn- ->uuid [x]
  (cond
    (uuid? x) x
    (string? x) (try (parse-uuid x)
                     (catch :default _ nil))
    :else nil))

(defui delivery-report [{:keys [run-id]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        run-id* (->uuid run-id)
        run (use-subscribe [::ms/run run-id*])
        loading? (use-subscribe [::ms/run-loading?])]
    (use-effect
      (fn []
        (when run-id*
          (rf/dispatch [::ms/load-run api-client run-id*]))
        js/undefined)
      [run-id* api-client])
    ($ :div {:class "flex w-full max-w-none flex-col"}
       ($ :div {:class "sticky top-0 z-10 -mt-6 flex items-center justify-between gap-3 border-b border-gray-950/5 bg-background/95 pt-6 pb-3 backdrop-blur"}
          ($ :div {:class "min-w-0"}
             ($ :h1 {:class "truncate text-xl font-semibold tracking-tight text-foreground sm:text-lg"}
                (or (:title run) (:subject run) "Broadcast"))
             ($ :p {:class "mt-1 text-sm text-muted-foreground tabular-nums"}
                (or (some-> (or (:started-at run) (:scheduled-at run) (:created-at run)) (subs 0 19)) "")))
          ($ :button {:type "button"
                      :on-click #(navigate! :hub-messages)
                      :class "text-xs font-medium tracking-tight text-muted-foreground hover:text-foreground"}
             "← Broadcasts"))
       (cond
         loading?
         ($ :p {:class "py-10 text-center text-sm text-muted-foreground"} "Loading…")

         (nil? run)
         ($ :p {:class "py-10 text-center text-sm italic text-muted-foreground"}
            "Run not found.")

         :else
         ($ :div {:class "py-6 space-y-6"}
            ;; Summary
            ($ :div {:class "rounded-md ring-1 ring-black/5 p-3 text-sm"}
               (when (:subject run)
                 ($ :<>
                    ($ :p {:class "text-xs uppercase tracking-widest text-muted-foreground mb-1"}
                       "Subject")
                    ($ :p {:class "mb-3 text-sm/6 font-medium"} (:subject run))))
               ($ :p {:class "text-xs uppercase tracking-widest text-muted-foreground mb-1"}
                  "Body")
               ($ :pre {:class "text-sm/6 whitespace-pre-wrap font-sans text-pretty"}
                  (:body run))
               (when (seq (:attachments run))
                 ($ :<>
                    ($ :p {:class "mt-3 text-xs uppercase tracking-widest text-muted-foreground mb-1"}
                       "Attachments")
                    ($ :p {:class "text-sm/6"}
                       (str/join ", " (map :file-name (:attachments run)))))))
            ;; Stat tiles
            ($ :div {:class "grid grid-cols-2 sm:grid-cols-5 gap-3"}
               ($ stat-tile {:label "Total" :value (:total run 0)})
               ($ stat-tile {:label "Sent" :value (:sent run 0) :tone :sent})
               ($ stat-tile {:label "Delivered" :value (:delivered run 0) :tone :delivered})
               ($ stat-tile {:label "Skipped" :value (:skipped run 0) :tone :skipped})
               ($ stat-tile {:label "Failed" :value (:failed run 0) :tone :failed}))
            ;; Replies
            (when (seq (:replies run))
              ($ :section {:class "scroll-mt-[11rem]"}
                 ($ :div {:class "flex items-center gap-2 mb-3"}
                    ($ :h2 {:class "text-xs font-semibold uppercase tracking-widest text-muted-foreground"}
                       (str "Replies (" (count (:replies run)) ")"))
                    ($ :div {:class "flex-1 h-px bg-gray-950/5"}))
                 ($ :div {:class "divide-y divide-gray-950/5"}
                    (for [{:keys [message-id from-address body channel subject received-at
                                  resolved-contact candidates]}
                          (:replies run)]
                      ($ :div {:key (str message-id)
                               :class "py-2.5"}
                         ($ :div {:class "flex items-center justify-between gap-3"}
                            ($ :div {:class "min-w-0 flex-1 text-sm/6 font-medium"}
                               (cond
                                 resolved-contact
                                 ($ contact-link
                                    {:contact-id (:id resolved-contact)
                                     :type-slug (:type-slug resolved-contact)
                                     :label (:display-name resolved-contact)})

                                 (seq candidates)
                                 ($ :span {:class "inline-flex flex-wrap items-center gap-x-2 gap-y-0.5"}
                                    ($ :span {:class "text-muted-foreground font-normal"} "Might be:")
                                    (for [c candidates]
                                      ($ contact-link
                                         {:key (str (:id c))
                                          :contact-id (:id c)
                                          :type-slug (:type-slug c)
                                          :label (str (:display-name c)
                                                      (when (:type-slug c)
                                                        (str " (" (name (:type-slug c)) ")")))})))

                                 :else
                                 ($ :span {:class "truncate"} (or from-address "(unknown)"))))
                            ($ :div {:class "flex shrink-0 items-center gap-2 text-xs text-muted-foreground tabular-nums"}
                               ($ :span {:class "rounded-full bg-gray-950/5 px-1.5 py-0.5 font-medium normal-nums uppercase"}
                                  (name (or channel :sms)))
                               ($ :span (some-> received-at (subs 11 16)))))
                         (when (seq subject)
                            ($ :p {:class "mt-0.5 text-sm/6 font-medium text-foreground/80"} subject))
                         ($ :p {:class "mt-0.5 text-sm/6 text-pretty whitespace-pre-wrap text-muted-foreground"}
                            body))))))
            ;; Recipient table
            ($ :section {:class "scroll-mt-[11rem]"}
               ($ :div {:class "flex items-center gap-2 mb-3"}
                  ($ :h2 {:class "text-xs font-semibold uppercase tracking-widest text-muted-foreground"}
                     "Recipients")
                  ($ :div {:class "flex-1 h-px bg-gray-950/5"}))
               (if (seq (:attempts run))
                 ($ :div {:class "divide-y divide-gray-950/5"}
                    (for [{:keys [recipient-id display-name status reason attempted-at
                                  channel external-recipient-email recipient-address
                                  delivery-status delivery-error-code contact]}
                          (:attempts run)]
                      ($ :div {:key (str channel ":" recipient-id ":" external-recipient-email)
                               :class "py-2 flex items-center justify-between gap-3"}
                         ($ :div {:class "min-w-0 flex-1"}
                            ($ :p {:class "text-sm/6 truncate"}
                               (if recipient-id
                                 ($ contact-link
                                    {:contact-id recipient-id
                                     :type-slug (:type-slug contact)
                                     :label (or display-name recipient-address "(unknown)")})
                                 (or external-recipient-email recipient-address "(unknown)")))
                            ($ :p {:class "text-xs text-muted-foreground truncate"}
                               (str/upper-case (name (or channel :sms))))
                            (when reason
                              ($ :p {:class "text-xs text-muted-foreground truncate"}
                                 reason)))
                         ($ :div {:class "flex shrink-0 items-center gap-2 text-xs text-muted-foreground tabular-nums"}
                            ($ :span (some-> attempted-at (subs 11 16)))
                            (status-pill status)
                            (delivery-pill delivery-status delivery-error-code)))))
                 ($ :p {:class "text-sm italic text-muted-foreground"}
                    "No recipient attempts recorded.")))))
       )))
