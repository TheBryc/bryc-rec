(ns components.advisor.share-student-link-modal
  "Modal component for sharing student view link"
  (:require [uix.core :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/button" :as button]))

(defui share-student-link-modal
  [{:keys [open? link generating? on-close]}]
  (let [[copied? set-copied!] (use-state false)]
    ;; Reset copied state when modal closes or link changes
    (use-effect
     (fn []
       (when (or (not open?) (not link))
         (set-copied! false))
       js/undefined)
     [open? link])

    ($ dialog/Dialog
       {:open open?
        :onOpenChange #(when-not % (on-close))}
       ($ dialog/DialogContent
          {:class "sm:max-w-md"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle "Share with Student")
             ($ dialog/DialogDescription
                "Copy this link and share it with your student. They can view their recommendations without logging in."))
          ($ :div
             {:class "space-y-4 py-4"}
             (if generating?
               ($ :div
                  {:class "flex items-center justify-center py-8"}
                  ($ :div
                     {:class "text-muted-foreground"}
                     "Generating link..."))
               (when link
                 ($ :div
                    {:class "flex items-center space-x-2"}
                    ($ :input
                       {:type "text"
                        :value link
                        :read-only true
                        :class "flex-1 px-3 py-2 text-sm border rounded-md bg-muted"})
                    ($ button/Button
                       {:size "sm"
                        :variant (if copied? "default" "outline")
                        :on-click (fn []
                                   (when link
                                     (.writeText (.-clipboard js/navigator) link)
                                     (set-copied! true)
                                     ;; Reset after 2 seconds
                                     (js/setTimeout #(set-copied! false) 2000)))}
                       (if copied? "Copied!" "Copy"))))))
          ($ dialog/DialogFooter
             ($ button/Button
                {:variant "outline"
                 :on-click on-close}
                "Close"))))))
