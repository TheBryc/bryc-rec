(ns components.advisor.setup-overgrad-modal
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

(defui setup-overgrad-modal [{:keys [open? on-close student on-refresh-student]}]
  (let [[confirming? set-confirming!] (use-state false)
        ctx (context/use-context)
        api-client (:api/client ctx)

        handle-confirm (fn []
                        (set-confirming! true)
                        (go
                          (let [response (<! (api/command api-client
                                                         {:command/name :student-ops/setup-overgrad-profile
                                                          :student-id (:student/id student)}))]
                            (if (anomaly? response)
                              (js/console.error "Failed to confirm Overgrad setup:" response)
                              (do
                                (js/console.log "Overgrad profile setup confirmed")
                                (when on-refresh-student
                                  (on-refresh-student (:student/id student)))
                                (on-close)))
                            (set-confirming! false))))]

    ($ dialog/Dialog {:open open?
                      :on-open-change on-close}
       ($ dialog/DialogContent {:class "sm:max-w-[425px]"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle "Setup Overgrad Profile")
             ($ dialog/DialogDescription
                (str "Confirm that you have set up the Overgrad profile for " (:student/name student))))
          ($ :div {:class "grid gap-4 py-4"}
             ($ :p {:class "text-sm text-muted-foreground"}
                "Click confirm once you have completed setting up the student's Overgrad profile."))
          ($ dialog/DialogFooter
             ($ button/Button {:variant "outline"
                              :on-click on-close
                              :disabled confirming?}
                "Cancel")
             ($ button/Button {:on-click handle-confirm
                              :disabled confirming?}
                (if confirming? "Confirming..." "Confirm Setup")))))))
