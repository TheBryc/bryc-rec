(ns components.advisor.transcript-modal
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [clojure.string]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/table" :as table]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :refer [anomaly?]]))

(defui transcript-modal [{:keys [open? on-close student on-refresh-student]}]
  (let [[edited-transcript set-edited-transcript!] (use-state nil)
        [saving? set-saving!] (use-state false)
        ctx (context/use-context)
        api-client (:api/client ctx)

        original-transcript-data (get-in student [:student/transcript :transcript/data])
        transcript-id (get-in student [:student/transcript :transcript/id])
        transcript (or edited-transcript original-transcript-data)
        has-changes? (and edited-transcript
                         (not= edited-transcript original-transcript-data))

        toggle-weighted (fn [idx]
                         (let [current (or edited-transcript original-transcript-data)]
                           (set-edited-transcript!
                             (vec (map-indexed
                                    (fn [i course]
                                      (if (= i idx)
                                        (assoc course :weighted (not (:weighted course)))
                                        course))
                                    current)))))

        toggle-core (fn [idx]
                     (let [current (or edited-transcript original-transcript-data)]
                       (set-edited-transcript!
                         (vec (map-indexed
                                (fn [i course]
                                  (if (= i idx)
                                    (assoc course :core (not (:core course)))
                                    course))
                                current)))))

        handle-save (fn []
                     (set-saving! true)
                     (go
                       (let [normalized-transcript (mapv (fn [course]
                                                           (-> course
                                                               (update :credit #(js/parseFloat (str %)))
                                                               (update :earned #(js/parseFloat (str %)))))
                                                         edited-transcript)
                             response (<! (api/command api-client
                                                      {:command/name :student-ops/edit-grade-transcript
                                                       :student-id (:student/id student)
                                                       :transcript-id transcript-id
                                                       :transcript normalized-transcript}))]
                         (if (anomaly? response)
                           (js/console.error "Failed to save transcript:" response)
                           (do
                             (js/console.log "Transcript saved successfully")
                             (when on-refresh-student
                               (on-refresh-student (:student/id student)))
                             (set-edited-transcript! nil)))
                         (set-saving! false))))

        handle-cancel (fn []
                       (set-edited-transcript! nil))]

    ($ dialog/Dialog {:open open?
                      :on-open-change on-close}
       ($ dialog/DialogContent {:class "!w-[900px] !h-[85vh] !max-w-[900px] p-6 flex flex-col"}
          ($ dialog/DialogHeader {:class "flex-shrink-0"}
             ($ dialog/DialogTitle "Grade Transcript")
             ($ dialog/DialogDescription
                (str "Transcript for " (:student/name student))))

          (if transcript
            ($ :div {:class "flex-1 overflow-y-auto"}
               ($ table/Table
                  ($ table/TableHeader
                     ($ table/TableRow
                        ($ table/TableHead {:class "w-64"} "Course Name")
                        ($ table/TableHead {:class "w-20"} "Year")
                        ($ table/TableHead {:class "w-32"} "Grades")
                        ($ table/TableHead {:class "w-20"} "Credit")
                        ($ table/TableHead {:class "w-20"} "Earned")
                        ($ table/TableHead {:class "w-24"} "Weighted")
                        ($ table/TableHead {:class "w-20"} "Core")))
                  ($ table/TableBody
                     (for [[idx course] (map-indexed vector transcript)]
                       ($ table/TableRow {:key idx
                                         :class (when (even? idx) "bg-muted/30")}
                          ($ table/TableCell {:class "font-medium w-64"} (:name course))
                          ($ table/TableCell {:class "w-20"} (:year course))
                          ($ table/TableCell {:class "w-32"} (clojure.string/join ", " (:grades course)))
                          ($ table/TableCell {:class "w-20"} (:credit course))
                          ($ table/TableCell {:class "w-20"} (:earned course))
                          ($ table/TableCell {:class "w-24"}
                             ($ :input {:key (str "weighted-" idx "-" (:weighted course))
                                       :type "checkbox"
                                       :checked (:weighted course)
                                       :on-change #(toggle-weighted idx)
                                       :class "h-4 w-4 cursor-pointer"}))
                          ($ table/TableCell {:class "w-20"}
                             ($ :input {:key (str "core-" idx "-" (:core course))
                                       :type "checkbox"
                                       :checked (:core course)
                                       :on-change #(toggle-core idx)
                                       :class "h-4 w-4 cursor-pointer"})))))))
            ($ :div {:class "flex-1 flex items-center justify-center"}
               ($ :p {:class "text-muted-foreground"} "No transcript available.")))

          ($ dialog/DialogFooter {:class "flex-shrink-0 flex gap-2"}
             ($ button/Button {:variant "outline"
                              :on-click on-close}
                "Close")
             (when has-changes?
               ($ :<>
                  ($ button/Button {:variant "outline"
                                   :on-click handle-cancel
                                   :disabled saving?}
                     "Cancel")
                  ($ button/Button {:on-click handle-save
                                   :disabled saving?}
                     (if saving? "Saving..." "Save")))))))))
