(ns components.advisor.apprenticeship-card
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

(defui apprenticeship-card
  [{:keys [apprenticeship selected? edit-mode? on-toggle on-move-up on-move-down pool-id
           ;; Optional controlled expand props (for student view)
           expanded? on-toggle-expand] :as props}]
  (let [;; Get api-client from context
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Card path for autosave state
        card-path [:apprenticeship pool-id]

        ;; Always call the hook (React rules) but only use its value when not controlled
        subscribed-expanded? (use-subscribe [::recommendations-subs/card-expanded? card-path])
        controlled? (contains? props :expanded?)
        expanded? (if controlled? expanded? subscribed-expanded?)
        toggle-expand (if controlled?
                        on-toggle-expand
                        #(rf/dispatch [::recommendations-events/toggle-card-expanded card-path]))

        ;; Use current values directly (no edit buffer)
        bullets-edits (:bullets apprenticeship)
        requirements-edits (:requirements apprenticeship)
        url-edit (:application-url apprenticeship)

        ;; Core field edits
        title-edit (:apprenticeship-title apprenticeship)
        company-edit (:company-name apprenticeship)
        city-edit (:city apprenticeship)
        state-edit (:state apprenticeship)
        overview-edit (:overview apprenticeship)]
    ($ card/Card
       {:class (str "relative transition-all duration-300 ease-in-out bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm border-0 cursor-pointer hover:shadow-lg hover:scale-[1.01] "
                    (when selected? "shadow-md"))
        :on-click toggle-expand}
       ($ card/CardHeader {:class "p-6"}
          ($ :div {:class "flex items-start justify-between gap-6"}
             ($ :div {:class "flex-1 space-y-2"}
                ;; Apprenticeship Title
                (if edit-mode?
                  ($ :input
                     {:type "text"
                      :value title-edit
                      :className "text-xl font-semibold text-gray-700 w-full px-2 py-1 border rounded-md"
                      :placeholder "Apprenticeship Title"
                      :on-change (fn [e]
                                  (let [new-val (.. e -target -value)]
                                    (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                 pool-id
                                                 {:bullets bullets-edits
                                                  :requirements requirements-edits
                                                  :application-url url-edit
                                                  :apprenticeship-title new-val
                                                  :company-name company-edit
                                                  :city city-edit
                                                  :state state-edit
                                                  :overview overview-edit}
                                                 api-client])))
                      :on-click (fn [e] (.stopPropagation e))})
                  ($ card/CardTitle {:class "text-lg md:text-xl font-semibold text-gray-700"}
                     title-edit))

                ;; Company Name
                (if edit-mode?
                  ($ :input
                     {:type "text"
                      :value company-edit
                      :className "text-sm text-muted-foreground w-full px-2 py-1 border rounded-md"
                      :placeholder "Company Name"
                      :on-change (fn [e]
                                  (let [new-val (.. e -target -value)]
                                    (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                 pool-id
                                                 {:bullets bullets-edits
                                                  :requirements requirements-edits
                                                  :application-url url-edit
                                                  :apprenticeship-title title-edit
                                                  :company-name new-val
                                                  :city city-edit
                                                  :state state-edit
                                                  :overview overview-edit}
                                                 api-client])))
                      :on-click (fn [e] (.stopPropagation e))})
                  ($ :div {:class "text-sm text-muted-foreground"}
                     company-edit))

                ;; City and State
                (if edit-mode?
                  ($ :div {:class "flex gap-2"}
                     ($ :input
                        {:type "text"
                         :value city-edit
                         :className "text-sm text-muted-foreground flex-1 px-2 py-1 border rounded-md"
                         :placeholder "City"
                         :on-change (fn [e]
                                     (let [new-val (.. e -target -value)]
                                       (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                    pool-id
                                                    {:bullets bullets-edits
                                                     :requirements requirements-edits
                                                     :application-url url-edit
                                                     :apprenticeship-title title-edit
                                                     :company-name company-edit
                                                     :city new-val
                                                     :state state-edit
                                                     :overview overview-edit}
                                                    api-client])))
                         :on-click (fn [e] (.stopPropagation e))})
                     ($ :input
                        {:type "text"
                         :value state-edit
                         :className "text-sm text-muted-foreground w-20 px-2 py-1 border rounded-md"
                         :placeholder "State"
                         :on-change (fn [e]
                                     (let [new-val (.. e -target -value)]
                                       (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                    pool-id
                                                    {:bullets bullets-edits
                                                     :requirements requirements-edits
                                                     :application-url url-edit
                                                     :apprenticeship-title title-edit
                                                     :company-name company-edit
                                                     :city city-edit
                                                     :state new-val
                                                     :overview overview-edit}
                                                    api-client])))
                         :on-click (fn [e] (.stopPropagation e))}))
                  ($ :div {:class "text-sm text-muted-foreground"}
                     (str city-edit ", " state-edit))))
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

            ;; Overview
            (when (or overview-edit edit-mode?)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Overview")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or overview-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[100px]"
                       :placeholder "Description of the apprenticeship program..."
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                  pool-id
                                                  {:bullets bullets-edits
                                                   :requirements requirements-edits
                                                   :application-url url-edit
                                                   :apprenticeship-title title-edit
                                                   :company-name company-edit
                                                   :city city-edit
                                                   :state state-edit
                                                   :overview new-val}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text overview-edit)))))

            ;; Salary and details in a friendly callout card (only for non-custom apprenticeships)
            (when-not (= (:source apprenticeship) :custom)
              ($ :div {:class "bg-gradient-to-br from-blue-50 to-indigo-50 rounded-xl p-5 border border-blue-100"}
                 ($ :div {:class "grid grid-cols-2 gap-4"}
                    (when (:starting-salary-annual apprenticeship)
                      ($ :div
                         ($ :div {:class "text-xs text-blue-600 font-medium mb-1"} "Starting Salary")
                         ($ :div {:class "text-base font-semibold text-gray-800"}
                            (str "$" (.toLocaleString (int (:starting-salary-annual apprenticeship))) "/year"))))
                    (when (:average-salary-annual apprenticeship)
                      ($ :div
                         ($ :div {:class "text-xs text-blue-600 font-medium mb-1"} "Average Salary")
                         ($ :div {:class "text-base font-semibold text-gray-800"}
                            (str "$" (.toLocaleString (int (:average-salary-annual apprenticeship))) "/year"))))
                    (when (:program-type apprenticeship)
                      ($ :div
                         ($ :div {:class "text-xs text-blue-600 font-medium mb-1"} "Program Type")
                         ($ :div {:class "text-sm font-medium text-gray-700"}
                            (:program-type apprenticeship))))
                    (when (:distance-from-baton-rouge-miles apprenticeship)
                      ($ :div
                         ($ :div {:class "text-xs text-blue-600 font-medium mb-1"} "Distance")
                         ($ :div {:class "text-sm font-medium text-gray-700"}
                            (str (.toFixed (:distance-from-baton-rouge-miles apprenticeship) 1) " miles")))))))

            ;; Why this fits (bullets)
            (when (or edit-mode? (seq (:bullets apprenticeship)) (seq bullets-edits))
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-6"} "Why This Apprenticeship Fits You")
                 (if edit-mode?
                   ;; Inline editable bullets when in edit mode
                   ($ :div {:class "space-y-2"}
                      (for [[idx bullet] (map-indexed vector bullets-edits)]
                        ($ :div {:key idx :class "flex gap-2"}
                           ($ :input
                              {:type "text"
                               :value bullet
                               :class "flex-1 px-3 py-2 text-sm border rounded-md"
                               :on-change (fn [e]
                                           (let [new-val (.. e -target -value)
                                                 new-bullets (assoc (vec bullets-edits) idx new-val)]
                                             ;; Update selection directly and trigger autosave
                                             (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                          pool-id
                                                          {:bullets new-bullets
                                                           :requirements requirements-edits
                                                           :application-url url-edit
                                                           :apprenticeship-title title-edit
                                                           :company-name company-edit
                                                           :city city-edit
                                                           :state state-edit
                                                           :overview overview-edit}
                                                          api-client])))
                               :on-click (fn [e] (.stopPropagation e))})
                           ($ button/Button
                              {:variant "ghost"
                               :size "sm"
                               :class "h-auto py-1 px-2"
                               :on-click (fn [e]
                                          (.stopPropagation e)
                                          (let [new-bullets (vec (concat (take idx bullets-edits)
                                                                        (drop (inc idx) bullets-edits)))]
                                            ;; Remove bullet and trigger autosave
                                            (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                         pool-id
                                                         {:bullets new-bullets
                                                          :requirements requirements-edits
                                                          :application-url url-edit
                                                          :apprenticeship-title title-edit
                                                          :company-name company-edit
                                                          :city city-edit
                                                          :state state-edit
                                                          :overview overview-edit}
                                                         api-client])))}
                              "×")))
                      ($ button/Button
                         {:variant "outline"
                          :size "sm"
                          :class "mt-2"
                          :on-click (fn [e]
                                     (.stopPropagation e)
                                     (let [new-bullets (conj (vec bullets-edits) "")]
                                       ;; Add bullet and trigger autosave
                                       (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                    pool-id
                                                    {:bullets new-bullets
                                                     :requirements requirements-edits
                                                     :application-url url-edit
                                                     :apprenticeship-title title-edit
                                                     :company-name company-edit
                                                     :city city-edit
                                                     :state state-edit
                                                     :overview overview-edit}
                                                    api-client])))}
                         "+ Add Bullet"))
                   ;; Read-only bullets when not in edit mode
                   ($ :ul {:class "space-y-3"}
                      (for [[idx bullet] (map-indexed vector bullets-edits)]
                        ($ :li {:key idx :class "text-sm md:text-base flex text-gray-600 leading-relaxed"}
                           ($ :span {:class "mr-3 text-blue-400"} "•")
                           ($ :span (linkify-text bullet))))))))

            ;; Requirements
            (when (or edit-mode? (seq (:requirements apprenticeship)) (seq requirements-edits))
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-6"} "Requirements")
                 (if edit-mode?
                   ;; Inline editable requirements when in edit mode
                   ($ :div {:class "space-y-2"}
                      (for [[idx req] (map-indexed vector requirements-edits)]
                        ($ :div {:key idx :class "flex gap-2"}
                           ($ :input
                              {:type "text"
                               :value req
                               :class "flex-1 px-3 py-2 text-sm border rounded-md"
                               :on-change (fn [e]
                                           (let [new-val (.. e -target -value)
                                                 new-requirements (assoc (vec requirements-edits) idx new-val)]
                                             ;; Update selection directly and trigger autosave
                                             (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                          pool-id
                                                          {:bullets bullets-edits
                                                           :requirements new-requirements
                                                           :application-url url-edit
                                                           :apprenticeship-title title-edit
                                                           :company-name company-edit
                                                           :city city-edit
                                                           :state state-edit
                                                           :overview overview-edit}
                                                          api-client])))
                               :on-click (fn [e] (.stopPropagation e))})
                           ($ button/Button
                              {:variant "ghost"
                               :size "sm"
                               :class "h-auto py-1 px-2"
                               :on-click (fn [e]
                                          (.stopPropagation e)
                                          (let [new-requirements (vec (concat (take idx requirements-edits)
                                                                             (drop (inc idx) requirements-edits)))]
                                            ;; Remove requirement and trigger autosave
                                            (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                         pool-id
                                                         {:bullets bullets-edits
                                                          :requirements new-requirements
                                                          :application-url url-edit
                                                          :apprenticeship-title title-edit
                                                          :company-name company-edit
                                                          :city city-edit
                                                          :state state-edit
                                                          :overview overview-edit}
                                                         api-client])))}
                              "×")))
                      ($ button/Button
                         {:variant "outline"
                          :size "sm"
                          :class "mt-2"
                          :on-click (fn [e]
                                     (.stopPropagation e)
                                     (let [new-requirements (conj (vec requirements-edits) "")]
                                       ;; Add requirement and trigger autosave
                                       (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                    pool-id
                                                    {:bullets bullets-edits
                                                     :requirements new-requirements
                                                     :application-url url-edit
                                                     :apprenticeship-title title-edit
                                                     :company-name company-edit
                                                     :city city-edit
                                                     :state state-edit
                                                     :overview overview-edit}
                                                    api-client])))}
                         "+ Add Requirement"))
                   ;; Read-only requirements when not in edit mode
                   ($ :ul {:class "space-y-3"}
                      (for [[idx req] (map-indexed vector requirements-edits)]
                        ($ :li {:key idx :class "text-sm md:text-base flex text-gray-600 leading-relaxed"}
                           ($ :span {:class "mr-3 text-blue-400"} "•")
                           ($ :span (linkify-text req))))))))

            ;; Application URL
            ($ :div {:class "pt-4"}
               (if edit-mode?
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
                                      (rf/dispatch [::recommendations-events/edit-apprenticeship-bullets
                                                   pool-id
                                                   {:bullets bullets-edits
                                                    :requirements requirements-edits
                                                    :application-url new-val
                                                    :apprenticeship-title title-edit
                                                    :company-name company-edit
                                                    :city city-edit
                                                    :state state-edit
                                                    :overview overview-edit}
                                                   api-client])))
                        :on-click (fn [e] (.stopPropagation e))}))
                 ;; Read-only link when not in edit mode
                 (when url-edit
                   ($ :a {:href url-edit
                          :target "_blank"
                          :rel "noopener noreferrer"
                          :class "text-sm text-primary hover:underline underline-offset-2 inline-block"
                          :on-click (fn [e] (.stopPropagation e))}
                      "Learn More & Apply →")))))))))
