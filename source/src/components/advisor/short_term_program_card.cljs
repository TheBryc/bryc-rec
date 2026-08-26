(ns components.advisor.short-term-program-card
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

(defn derive-career-paths
  "Derive a flat vector of career title strings from program data.
   Prefers :career-paths if already present (from advisor edits),
   otherwise extracts titles from :primary-career and :other-careers."
  [program]
  (or (when (vector? (:career-paths program)) (:career-paths program))
      (let [primary (get-in program [:primary-career :title])
            others (mapv :title (:other-careers program))]
        (vec (remove empty? (into (if primary [primary] []) others))))))

(defui short-term-program-card
  [{:keys [program selected? edit-mode? on-toggle on-move-up on-move-down pool-id
           ;; Optional controlled expand props (for student view)
           expanded? on-toggle-expand] :as props}]
  (let [;; Get api-client from context
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Card path for autosave state
        card-path [:short-term-program pool-id]

        ;; Always call the hook (React rules) but only use its value when not controlled
        subscribed-expanded? (use-subscribe [::recommendations-subs/card-expanded? card-path])
        controlled? (contains? props :expanded?)
        expanded? (if controlled? expanded? subscribed-expanded?)
        toggle-expand (if controlled?
                        on-toggle-expand
                        #(rf/dispatch [::recommendations-events/toggle-card-expanded card-path]))

        ;; Editable fields (can be modified by advisor)
        personalized-overview-edit (:personalized-overview program)
        outcome-bullet-edit (:outcome-bullet program)
        career-summary-edit (:career-summary program)
        career-paths (derive-career-paths program)
        program-url-edit (:program-url program)

        ;; Read-only fields (from AI/backend)
        program-title (:program-title program)
        institution-name (:institution-name program)
        award-level-name (:award-level-name program)
        city (:city program)
        state (:state program)

        ;; Dispatch a single field override while preserving the other edits.
        update-field (fn [field-key new-val]
                       (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                     pool-id
                                     (assoc {:personalized-overview personalized-overview-edit
                                             :outcome-bullet outcome-bullet-edit
                                             :career-summary career-summary-edit
                                             :career-paths career-paths
                                             :program-url program-url-edit}
                                            field-key new-val)
                                     api-client]))
        ;; Render a vector-valued field as an editable/read-only bullet list.
        render-bullet-field
        (fn [{:keys [label value field-key add-label]}]
          (let [bullets (if (vector? value) value [])]
            ($ :div
               ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} label)
               (if edit-mode?
                 ($ :div {:class "space-y-2"}
                    (for [[idx b] (map-indexed vector bullets)]
                      ($ :div {:key idx :class "flex gap-2"}
                         ($ :input
                            {:type "text"
                             :value b
                             :class "flex-1 px-3 py-2 text-sm border rounded-md"
                             :on-change (fn [e]
                                          (update-field field-key (assoc (vec bullets) idx (.. e -target -value))))
                             :on-click (fn [e] (.stopPropagation e))})
                         ($ button/Button
                            {:variant "ghost" :size "sm" :class "h-auto py-1 px-2"
                             :on-click (fn [e]
                                         (.stopPropagation e)
                                         (update-field field-key (vec (concat (take idx bullets) (drop (inc idx) bullets)))))}
                            "×")))
                    ($ button/Button
                       {:variant "outline" :size "sm" :class "mt-2"
                        :on-click (fn [e]
                                    (.stopPropagation e)
                                    (update-field field-key (conj (vec bullets) "")))}
                       (or add-label "+ Add")))
                 ($ :ul {:class "space-y-3"}
                    (for [[idx b] (map-indexed vector bullets)]
                      ($ :li {:key idx :class "text-sm md:text-base flex text-gray-600 leading-relaxed"}
                         ($ :span {:class "mr-3 text-blue-400"} "•")
                         ($ :span (linkify-text b)))))))))]
    ($ card/Card
       {:class (str "relative transition-all duration-300 ease-in-out bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm border-0 cursor-pointer hover:shadow-lg hover:scale-[1.01] "
                    (when selected? "shadow-md"))
        :on-click toggle-expand}
       ($ card/CardHeader {:class "p-6"}
          ($ :div {:class "flex items-start justify-between gap-6"}
             ($ :div {:class "flex-1 space-y-2"}
                ;; Program Title
                ($ card/CardTitle {:class "text-lg md:text-xl font-semibold text-gray-700"}
                   program-title)

                ;; Institution Name
                ($ :div {:class "text-sm font-medium text-gray-600"}
                   institution-name)

                ;; Award Level (read-only)
                (when award-level-name
                  ($ :div {:class "flex flex-wrap gap-2 mt-2"}
                     ($ badge/Badge {:variant "secondary" :class "text-xs"}
                        award-level-name)))

                ;; City, State
                ($ :div {:class "text-sm text-muted-foreground mt-1"}
                   (str city ", " state)))
             ($ :div {:class "flex flex-col items-end gap-2"}
                ;; Top row: up, down, selected badge
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

            ;; Personalized Overview (editable)
            (when (or personalized-overview-edit edit-mode?)
              ($ :div
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Overview")
                 (if edit-mode?
                   ($ :textarea
                      {:value (or personalized-overview-edit "")
                       :className "w-full px-3 py-2 text-sm border rounded-md min-h-[100px]"
                       :placeholder "Program overview..."
                       :on-change (fn [e]
                                   (let [new-val (.. e -target -value)]
                                     (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                                  pool-id
                                                  {:personalized-overview new-val
                                                   :outcome-bullet outcome-bullet-edit
                                                   :career-summary career-summary-edit
                                                   :career-paths career-paths
                                                   :program-url program-url-edit}
                                                  api-client])))
                       :on-click (fn [e] (.stopPropagation e))})
                   ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                      (linkify-text personalized-overview-edit)))))

            ;; Expected Outcomes — bullets for vector values (new/AI data),
            ;; legacy textarea/paragraph for plain strings.
            (when (or edit-mode? (if (vector? outcome-bullet-edit) (seq outcome-bullet-edit) outcome-bullet-edit))
              (if (vector? outcome-bullet-edit)
                (render-bullet-field {:label "Expected Outcomes"
                                      :value outcome-bullet-edit
                                      :field-key :outcome-bullet
                                      :add-label "+ Add Outcome"})
                ($ :div
                   ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Expected Outcomes")
                   (if edit-mode?
                     ($ :textarea
                        {:value (or outcome-bullet-edit "")
                         :className "w-full px-3 py-2 text-sm border rounded-md min-h-[80px]"
                         :placeholder "Expected outcomes for students..."
                         :on-change (fn [e] (update-field :outcome-bullet (.. e -target -value)))
                         :on-click (fn [e] (.stopPropagation e))})
                     ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                        (linkify-text outcome-bullet-edit))))))

            ;; Career Summary — same bullets-vs-text split.
            (when (or edit-mode? (if (vector? career-summary-edit) (seq career-summary-edit) career-summary-edit))
              (if (vector? career-summary-edit)
                (render-bullet-field {:label "Career Summary"
                                      :value career-summary-edit
                                      :field-key :career-summary
                                      :add-label "+ Add Career Summary"})
                ($ :div
                   ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Career Summary")
                   (if edit-mode?
                     ($ :textarea
                        {:value (or career-summary-edit "")
                         :className "w-full px-3 py-2 text-sm border rounded-md min-h-[80px]"
                         :placeholder "Career outlook and opportunities..."
                         :on-change (fn [e] (update-field :career-summary (.. e -target -value)))
                         :on-click (fn [e] (.stopPropagation e))})
                     ($ :p {:class "text-sm md:text-base text-gray-600 leading-relaxed max-w-prose"}
                        (linkify-text career-summary-edit))))))

            ;; Career Paths (editable bullet list)
            (when (or (seq career-paths) edit-mode?)
              ($ :div {:class "mt-3"}
                 ($ :h4 {:class "text-base font-semibold text-gray-700 mb-4"} "Career Paths")
                 (if edit-mode?
                   ($ :div {:class "space-y-2"}
                      (for [[idx path] (map-indexed vector career-paths)]
                        ($ :div {:key idx :class "flex gap-2"}
                           ($ :input
                              {:type "text"
                               :value path
                               :class "flex-1 px-3 py-2 text-sm border rounded-md"
                               :on-change (fn [e]
                                           (let [new-val (.. e -target -value)
                                                 new-paths (assoc (vec career-paths) idx new-val)]
                                             (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                                          pool-id
                                                          {:personalized-overview personalized-overview-edit
                                                           :outcome-bullet outcome-bullet-edit
                                                           :career-summary career-summary-edit
                                                           :career-paths new-paths
                                                           :program-url program-url-edit}
                                                          api-client])))
                               :on-click (fn [e] (.stopPropagation e))})
                           ($ button/Button
                              {:variant "ghost"
                               :size "sm"
                               :class "h-auto py-1 px-2"
                               :on-click (fn [e]
                                          (.stopPropagation e)
                                          (let [new-paths (vec (concat (take idx career-paths)
                                                                      (drop (inc idx) career-paths)))]
                                            (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                                         pool-id
                                                         {:personalized-overview personalized-overview-edit
                                                          :outcome-bullet outcome-bullet-edit
                                                          :career-summary career-summary-edit
                                                          :career-paths new-paths
                                                          :program-url program-url-edit}
                                                         api-client])))}
                              "×")))
                      ($ button/Button
                         {:variant "outline"
                          :size "sm"
                          :class "mt-2"
                          :on-click (fn [e]
                                    (.stopPropagation e)
                                    (let [new-paths (conj (vec career-paths) "")]
                                      (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                                   pool-id
                                                   {:personalized-overview personalized-overview-edit
                                                    :outcome-bullet outcome-bullet-edit
                                                    :career-summary career-summary-edit
                                                    :career-paths new-paths
                                                    :program-url program-url-edit}
                                                   api-client])))}
                         "+ Add Career Path"))
                   ($ :ul {:class "space-y-3"}
                      (for [[idx path] (map-indexed vector career-paths)]
                        ($ :li {:key idx :class "text-sm md:text-base flex text-gray-600 leading-relaxed"}
                           ($ :span {:class "mr-3 text-blue-400"} "•")
                           ($ :span (linkify-text path))))))))

            ;; Program URL (editable) with Google Search
            ($ :div
               (when edit-mode?
                 ($ :div {:class "flex items-center justify-between mb-4"}
                    ($ :h4 {:class "text-base font-semibold text-gray-700"} "Program URL")
                    ;; Google Search button (always available)
                    ($ button/Button
                       {:variant "outline"
                        :size "sm"
                        :on-click (fn [e]
                                   (.stopPropagation e)
                                   (let [search-query (str program-title " " institution-name)
                                         encoded-query (js/encodeURIComponent search-query)
                                         search-url (str "https://www.google.com/search?q=" encoded-query)]
                                     (.open js/window search-url "_blank")))}
                       "🔍 Search Google")))
               (if edit-mode?
                 ($ :input
                    {:type "url"
                     :value (or program-url-edit "")
                     :className "w-full px-3 py-2 text-sm border rounded-md"
                     :placeholder "https://..."
                     :on-change (fn [e]
                                 (let [new-val (.. e -target -value)]
                                   (rf/dispatch [::recommendations-events/edit-short-term-program-field
                                                pool-id
                                                {:personalized-overview personalized-overview-edit
                                                 :outcome-bullet outcome-bullet-edit
                                                 :career-summary career-summary-edit
                                                 :career-paths career-paths
                                                 :program-url new-val}
                                                api-client])))
                     :on-click (fn [e] (.stopPropagation e))})
                 (when program-url-edit
                   ($ :a {:href program-url-edit
                          :target "_blank"
                          :rel "noopener noreferrer"
                          :class "text-sm text-primary hover:underline underline-offset-2 inline-block"
                          :on-click (fn [e] (.stopPropagation e))}
                      "Learn More & Apply →")))))))))
