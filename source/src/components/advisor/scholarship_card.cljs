(ns components.advisor.scholarship-card
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]
            [components.context.interface :as context]
            [components.advisor.bullet-text :refer [linkify-text]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/button" :as button]))

(defui scholarship-card
  [{:keys [scholarship selected? edit-mode? on-toggle on-move-up on-move-down pool-id
           ;; Optional controlled expand props (for student view)
           expanded? on-toggle-expand] :as props}]
  (let [;; Get api-client from context
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Card path for autosave state
        card-path [:scholarship pool-id]

        ;; Always call the hook (React rules) but only use its value when not controlled
        subscribed-expanded? (use-subscribe [::recommendations-subs/card-expanded? card-path])
        controlled? (contains? props :expanded?)
        expanded? (if controlled? expanded? subscribed-expanded?)
        toggle-expand (if controlled?
                        on-toggle-expand
                        #(rf/dispatch [::recommendations-events/toggle-card-expanded card-path]))

        ;; Use current values directly (no edit buffer)
        name-edit (:name scholarship)
        award-edit (:award scholarship)
        description-edit (:description scholarship)
        personalized-explanation-edit (:personalized-explanation scholarship)
        selection-criteria-edit (:selection-criteria scholarship)
        application-tips-edit (:application-tips scholarship)
        deadline-edit (:deadline scholarship)
        url-edit (:application-url scholarship)]
    ($ card/Card
       {:class (str "relative transition-all duration-300 ease-in-out bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm border-0 cursor-pointer hover:shadow-lg hover:scale-[1.01] "
                    (when selected? "shadow-md"))
        :on-click toggle-expand}
       ($ card/CardHeader {:class "p-6"}
          ($ :div {:class "flex items-start justify-between gap-6"}
             ($ :div {:class "flex-1 space-y-2"}
                ;; Scholarship Name
                (if edit-mode?
                  ($ :input
                     {:type "text"
                      :value name-edit
                      :className "text-xl font-semibold text-gray-700 w-full px-2 py-1 border rounded-md"
                      :placeholder "Scholarship Name"
                      :on-change (fn [e]
                                  (let [new-val (.. e -target -value)]
                                    (rf/dispatch [::recommendations-events/edit-scholarship
                                                 pool-id
                                                 {:name new-val
                                                  :award award-edit
                                                  :description description-edit
                                                  :personalized-explanation personalized-explanation-edit
                                                  :selection-criteria selection-criteria-edit
                                                  :application-tips application-tips-edit
                                                  :deadline deadline-edit
                                                  :application-url url-edit}
                                                 api-client])))
                      :on-click (fn [e] (.stopPropagation e))})
                  ($ card/CardTitle {:class "text-lg md:text-xl font-semibold text-gray-700"}
                     name-edit))

                ;; Award Amount
                (if edit-mode?
                  ($ :input
                     {:type "text"
                      :value award-edit
                      :className "text-sm text-muted-foreground w-full px-2 py-1 border rounded-md"
                      :placeholder "Award Amount"
                      :on-change (fn [e]
                                  (let [new-val (.. e -target -value)]
                                    (rf/dispatch [::recommendations-events/edit-scholarship
                                                 pool-id
                                                 {:name name-edit
                                                  :award new-val
                                                  :description description-edit
                                                  :personalized-explanation personalized-explanation-edit
                                                  :selection-criteria selection-criteria-edit
                                                  :application-tips application-tips-edit
                                                  :deadline deadline-edit
                                                  :application-url url-edit}
                                                 api-client])))
                      :on-click (fn [e] (.stopPropagation e))})
                  ($ :div {:class "text-sm text-muted-foreground"}
                     (str "Award: " award-edit))))
             ($ :div {:class "flex flex-col items-end gap-2"}
                ;; Top row: up, down, selected badge (with badge on right)
                ($ :div {:class "flex items-center gap-2"}
                   ;; Move up button
                   (when on-move-up
                     ($ button/Button
                        {:variant "ghost"
                         :size "sm"
                         :on-click (fn [e]
                                    (.stopPropagation e)
                                    (on-move-up))}
                        "↑"))
                   ;; Move down button
                   (when on-move-down
                     ($ button/Button
                        {:variant "ghost"
                         :size "sm"
                         :on-click (fn [e]
                                    (.stopPropagation e)
                                    (on-move-down))}
                        "↓"))
                   (when edit-mode?
                     ($ badge/Badge
                        {:variant (if selected? "default" "outline")
                         :class "cursor-pointer hover:opacity-80 transition-opacity"
                         :on-click (fn [e]
                                    (.stopPropagation e)
                                    (when on-toggle (on-toggle)))}
                        (if selected? "Selected" "Add"))))
                ;; Bottom row: expand button
                ($ button/Button
                   {:variant "ghost"
                    :size "sm"
                    :on-click (fn [e]
                                (.stopPropagation e)
                                (toggle-expand))}
                   (if expanded? "Collapse" "Expand")))))

       (when expanded?
         ($ card/CardContent {:class "space-y-6 pt-0 px-6 pb-6"}

            ;; Personalized explanation
            (when (or edit-mode? personalized-explanation-edit)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Why This Scholarship Fits You")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or personalized-explanation-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[100px]"
                       :placeholder "Explain why this scholarship is a good fit..."
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-scholarship
                                                  pool-id
                                                  {:name name-edit
                                                   :award award-edit
                                                   :description description-edit
                                                   :personalized-explanation new-val
                                                   :selection-criteria selection-criteria-edit
                                                   :application-tips application-tips-edit
                                                   :deadline deadline-edit
                                                   :application-url url-edit}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text personalized-explanation-edit)))))

            ;; Description
            (when (or edit-mode? description-edit)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Description")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or description-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[100px]"
                       :placeholder "Brief description of the scholarship..."
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-scholarship
                                                  pool-id
                                                  {:name name-edit
                                                   :award award-edit
                                                   :description new-val
                                                   :personalized-explanation personalized-explanation-edit
                                                   :selection-criteria selection-criteria-edit
                                                   :application-tips application-tips-edit
                                                   :deadline deadline-edit
                                                   :application-url url-edit}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text description-edit)))))

            ;; Selection criteria
            (when (or edit-mode? selection-criteria-edit)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Selection Criteria")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or selection-criteria-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[80px]"
                       :placeholder "What are they looking for in applicants?"
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-scholarship
                                                  pool-id
                                                  {:name name-edit
                                                   :award award-edit
                                                   :description description-edit
                                                   :personalized-explanation personalized-explanation-edit
                                                   :selection-criteria new-val
                                                   :application-tips application-tips-edit
                                                   :deadline deadline-edit
                                                   :application-url url-edit}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text selection-criteria-edit)))))

            ;; Application tips
            (when (or edit-mode? application-tips-edit)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Application Tips")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or application-tips-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[80px]"
                       :placeholder "Tips for applying..."
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-scholarship
                                                  pool-id
                                                  {:name name-edit
                                                   :award award-edit
                                                   :description description-edit
                                                   :personalized-explanation personalized-explanation-edit
                                                   :selection-criteria selection-criteria-edit
                                                   :application-tips new-val
                                                   :deadline deadline-edit
                                                   :application-url url-edit}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text application-tips-edit)))))

            ;; Deadline and URL
            ($ :div {:class "flex flex-col gap-4 pt-6"}
               ;; Deadline
               (when (or edit-mode? deadline-edit)
                 ($ :div
                    (if edit-mode?
                      ($ :div
                         ($ :label {:class "text-xs font-medium text-muted-foreground"} "Deadline")
                         ($ :input
                            {:type "text"
                             :value (or deadline-edit "")
                             :placeholder "e.g., March 15, 2025"
                             :class "w-full px-3 py-2 text-sm border rounded-md mt-1"
                             :on-change (fn [e]
                                         (let [new-val (.. e -target -value)]
                                           (rf/dispatch [::recommendations-events/edit-scholarship
                                                        pool-id
                                                        {:name name-edit
                                                         :award award-edit
                                                         :description description-edit
                                                         :personalized-explanation personalized-explanation-edit
                                                         :selection-criteria selection-criteria-edit
                                                         :application-tips application-tips-edit
                                                         :deadline new-val
                                                         :application-url url-edit}
                                                        api-client])))
                             :on-click (fn [e] (.stopPropagation e))}))
                      ($ :div {:class "text-sm"}
                         ($ :span {:class "text-gray-500"} "Deadline: ")
                         ($ :span {:class "font-medium text-gray-700"} deadline-edit)))))

               ;; Application URL
               ($ :div
                  (if edit-mode?
                    ;; Inline editable URL when in edit mode
                    ($ :div
                       ($ :label {:class "text-xs font-medium text-muted-foreground"} "Application URL")
                       ($ :input
                          {:type "text"
                           :value (or url-edit "")
                           :placeholder "https://..."
                           :class "w-full px-3 py-2 text-sm border rounded-md mt-1"
                           :on-change (fn [e]
                                       (let [new-val (.. e -target -value)]
                                         ;; Update selection directly and trigger autosave
                                         (rf/dispatch [::recommendations-events/edit-scholarship
                                                      pool-id
                                                      {:name name-edit
                                                       :award award-edit
                                                       :description description-edit
                                                       :personalized-explanation personalized-explanation-edit
                                                       :selection-criteria selection-criteria-edit
                                                       :application-tips application-tips-edit
                                                       :deadline deadline-edit
                                                       :application-url new-val}
                                                      api-client])))
                           :on-click (fn [e] (.stopPropagation e))}))
                    ;; Read-only link when not in edit mode
                    (when url-edit
                      ($ :a {:href url-edit
                             :target "_blank"
                             :rel "noopener noreferrer"
                             :class "text-sm text-primary hover:underline underline-offset-2 inline-block"
                             :on-click (fn [e] (.stopPropagation e))}
                         "Apply Now →"))))))))))
