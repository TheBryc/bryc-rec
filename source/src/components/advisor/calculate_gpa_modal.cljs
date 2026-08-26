(ns components.advisor.calculate-gpa-modal
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

(defui calculate-gpa-modal [{:keys [open? on-close student on-refresh-student]}]
  (let [[calculating? set-calculating!] (use-state false)
        ctx (context/use-context)
        api-client (:api/client ctx)

        handle-calculate (fn []
                          (set-calculating! true)
                          (go
                            (let [calc-response (<! (api/command api-client
                                                                {:command/name :student-ops/calculate-gpa
                                                                 :student-id (:student/id student)}))]
                              (if (anomaly? calc-response)
                                (js/console.error "Failed to calculate GPA:" calc-response)
                                (do
                                  (js/console.log "GPA calculation initiated")
                                  (when on-refresh-student
                                    (on-refresh-student (:student/id student)))
                                  (on-close)))
                              (set-calculating! false))))]

    ($ dialog/Dialog {:open open?
                      :on-open-change on-close}
       ($ dialog/DialogContent {:class "sm:max-w-[425px]"}
          ($ dialog/DialogHeader
             ($ dialog/DialogTitle "Calculate GPA")
             ($ dialog/DialogDescription
                (str "Calculate the GPA for " (:student/name student))))
          ($ :div {:class "grid gap-4 py-4"}
             ($ :p {:class "text-sm text-muted-foreground"}
                "Click calculate to process the grade transcript and compute the GPA."))
          ($ dialog/DialogFooter
             ($ button/Button {:variant "outline"
                              :on-click on-close
                              :disabled calculating?}
                "Cancel")
             ($ button/Button {:on-click handle-calculate
                              :disabled calculating?}
                (if calculating? "Calculating..." "Calculate")))))))
