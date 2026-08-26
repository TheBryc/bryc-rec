(ns components.comms.templates.insert-popover
  "Insert-template popover for the conversation composer. Lists templates
   matching the current channel (org first, then per-user snippets); on
   selection, expands mail-merge tokens against the recipient and stuffs
   the composer's body + subject."
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.comms.core :as comms]
            [store.comms.templates :as templates]
            ["/gen/shadcn/components/ui/popover" :refer [Popover PopoverTrigger PopoverContent]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["lucide-react" :refer [FilePlus2]]))

(defui ^:private template-row
  [{:keys [t on-pick]}]
  ($ :button
     {:type "button"
      :on-click #(on-pick t)
      :class (str "block w-full px-3 py-2 text-left text-sm "
                  "rounded-md transition-colors hover:bg-gray-950/2 "
                  "focus-visible:outline-2 focus-visible:outline-blue-500 "
                  "-outline-offset-1")}
     ($ :p {:class "font-medium tracking-tight truncate"} (:name t))
     (when-let [body (:body t)]
       ($ :p {:class "mt-0.5 text-xs text-muted-foreground line-clamp-2"} body))))

(defui insert-template-popover
  "Compact popover trigger; expects api-client + the active recipient contact."
  [{:keys [api-client contact]}]
  (let [[open? set-open!] (use-state false)
        channel (use-subscribe [::comms/channel])
        {:keys [org user]} (use-subscribe [::templates/templates-for-channel channel])
        loading? (use-subscribe [::templates/loading?])]
    (use-effect
      (fn []
        (when open?
          (rf/dispatch [::templates/load-templates api-client]))
        js/undefined)
      [open? api-client])
    (let [;; Insert the template verbatim — tokens stay as {first-name} etc.
          ;; Expansion happens at SEND time (per-recipient on the backend),
          ;; so a single template works for both 1:1 and mass sends and the
          ;; preview line shows the resolved sample.
          pick (fn [{:keys [body subject]}]
                 (rf/dispatch-sync [::comms/set-body (or body "")])
                 (when (= :email channel)
                   (rf/dispatch-sync [::comms/set-subject (or subject "")]))
                 (set-open! false))]
      ($ Popover {:open open? :onOpenChange set-open!}
         ($ PopoverTrigger {:asChild true}
            ($ button/Button
               {:type "button"
                :size "sm"
                :variant "ghost"
                :class "h-7 px-2 text-xs gap-1"}
               ($ FilePlus2 {:class "h-3.5 w-3.5"})
               "Insert template"))
         ($ PopoverContent {:class "w-80 p-2" :align "start"}
            (cond
              loading?
              ($ :p {:class "px-3 py-4 text-center text-sm text-muted-foreground"} "Loading…")

              (and (empty? org) (empty? user))
              ($ :p {:class "px-3 py-4 text-center text-sm italic text-muted-foreground"}
                 (str "No " (name (or channel :sms)) " templates yet."))

              :else
              ($ :div {:class "max-h-80 overflow-y-auto"}
                 (when (seq org)
                   ($ :<>
                      ($ :p {:class (str "px-3 py-1 text-xs font-bold uppercase "
                                          "tracking-widest text-muted-foreground")}
                         "Org library")
                      (for [t org]
                        ($ template-row {:key (str (:template-id t))
                                          :t t :on-pick pick}))))
                 (when (seq user)
                   ($ :<>
                      ($ :p {:class (str "px-3 py-1 mt-1 text-xs font-bold uppercase "
                                          "tracking-widest text-muted-foreground")}
                         "My snippets")
                      (for [t user]
                        ($ template-row {:key (str (:template-id t))
                                          :t t :on-pick pick})))))))))))
