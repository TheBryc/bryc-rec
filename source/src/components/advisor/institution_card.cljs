(ns components.advisor.institution-card
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [components.advisor.coming-soon-button :as csb]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as recommendations-events]
            [store.advisor.recommendations.subs :as recommendations-subs]
            [components.context.interface :as context]
            [components.advisor.bullet-text :refer [linkify-text]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/button" :as button]))

(defn find-by-id
  "Find an item in a collection by its :id field"
  [items id]
  (first (filter #(= (:id %) id) items)))

;; ============================================================================
;; Form Primitive Components
;; ============================================================================

(defui text-input-with-label
  [{:keys [label value placeholder on-change on-click class-name]}]
  ($ :div {:class (or class-name "")}
     ($ :label {:class "text-xs font-medium text-muted-foreground"} label)
     ($ :input
        {:type "text"
         :value (or value "")
         :placeholder placeholder
         :class "w-full px-3 py-2 text-sm border rounded-md mt-1"
         :on-change on-change
         :on-click on-click})))

(defui textarea-with-label
  [{:keys [label value rows on-change on-click class-name]}]
  ($ :div {:class (or class-name "")}
     ($ :label {:class "text-xs font-medium text-muted-foreground"} label)
     ($ :textarea
        {:value (or value "")
         :class "w-full px-3 py-2 text-sm border rounded-md mt-1"
         :rows (or rows 4)
         :on-change on-change
         :on-click on-click})))

(defui select-with-label
  [{:keys [label value options on-change on-click class-name]}]
  (let [select-value (if (nil? value) "" value)]
    ($ :div {:class (or class-name "")}
       ($ :label {:class "text-xs font-medium text-muted-foreground"} label)
       ($ :select
          {:value select-value
           :class "w-full px-3 py-2 text-sm border rounded-md mt-1"
           :on-change on-change
           :on-click on-click}
          (for [option options]
            ($ :option {:key (:value option) :value (:value option)}
               (:label option)))))))

;; ============================================================================
;; Small UI Components
;; ============================================================================

(defui selection-badge
  [{:keys [selected? on-click]}]
  ($ badge/Badge
     {:variant (if selected? "default" "outline")
      :class "cursor-pointer hover:opacity-80 transition-opacity"
      :on-click (fn [e]
                 (.stopPropagation e)
                 (when on-click (on-click)))}
     (if selected? "Selected" "Add")))

(defui move-buttons
  [{:keys [on-move-up on-move-down]}]
  ($ :<>
     (when on-move-up
       ($ button/Button
          {:variant "ghost"
           :size "sm"
           :on-click (fn [e]
                      (.stopPropagation e)
                      (on-move-up))}
          "↑"))
     (when on-move-down
       ($ button/Button
          {:variant "ghost"
           :size "sm"
           :on-click (fn [e]
                      (.stopPropagation e)
                      (on-move-down))}
          "↓"))))

(defui add-item-button
  [{:keys [label on-click]}]
  ($ button/Button
     {:variant "outline"
      :size "sm"
      :class "mt-2"
      :on-click (fn [e]
                 (.stopPropagation e)
                 (on-click))}
     label))

(defui section-divider []
  ($ :div {:class "h-px bg-gradient-to-r from-transparent via-gray-200 to-transparent"}))

;; ============================================================================
;; Section Header Component
;; ============================================================================

(defui section-header
  [{:keys [count singular-label plural-label class-name]}]
  ($ :h3 {:class (or class-name "text-lg font-semibold text-gray-700 mb-6")}
     (str count " " (if (> count 1) plural-label singular-label))))

;; ============================================================================
;; Bullet Components
;; ============================================================================

(defui bullet-item
  [{:keys [bullet idx editing? on-change on-remove]}]
  (if editing?
    ($ :div {:key idx :class "flex gap-2"}
       ($ :input
          {:type "text"
           :value bullet
           :class "flex-1 px-3 py-2 text-sm border rounded-md"
           :on-change (fn [e]
                       (let [new-val (.. e -target -value)]
                         (on-change new-val)))
           :on-click (fn [e] (.stopPropagation e))})
       ($ button/Button
          {:variant "ghost"
           :size "sm"
           :class "h-auto py-1 px-2"
           :on-click (fn [e]
                      (.stopPropagation e)
                      (on-remove))}
          "×"))
    ($ :li {:key idx :class "text-sm md:text-base flex text-gray-600 leading-relaxed"}
       ($ :span {:class "mr-3 text-blue-400"} "•")
       ($ :span (linkify-text bullet)))))

(defui bullet-list-section
  [{:keys [title bullets editing? on-update-bullet on-remove-bullet on-add-bullet show? class-name]}]
  (when show?
    ($ :div {:class (or class-name "")}
       ($ :h4 {:class "text-base font-semibold text-gray-700 mb-6"} title)
       (if editing?
         ($ :div {:class "space-y-2"}
            (for [[idx bullet] (map-indexed vector bullets)]
              ($ bullet-item
                 {:key idx
                  :bullet bullet
                  :idx idx
                  :editing? editing?
                  :on-change (fn [new-val]
                              (on-update-bullet idx new-val))
                  :on-remove (fn []
                              (on-remove-bullet idx))}))
            ($ add-item-button
               {:label "+ Add Bullet"
                :on-click on-add-bullet}))
         ($ :ul {:class "space-y-3"}
            (for [[idx bullet] (map-indexed vector bullets)]
              ($ bullet-item
                 {:key idx
                  :bullet bullet
                  :idx idx
                  :editing? editing?})))))))

;; ============================================================================
;; Career Paths Helper
;; ============================================================================

(defn derive-career-paths
  "Derive a flat vector of career title strings from program data.
   Prefers :career-paths if already present (from advisor edits),
   otherwise extracts titles from :primary-career and :other-careers."
  [program]
  (or (when (vector? (:career-paths program)) (:career-paths program))
      (let [primary (get-in program [:primary-career :title])
            others (mapv :title (:other-careers program))]
        (vec (remove empty? (into (if primary [primary] []) others))))))

;; ============================================================================
;; Program Components
;; ============================================================================

(defui program-item
  [{:keys [program pool-id selected? edit-mode? editing? on-toggle on-move-up on-move-down
           on-update-program institution-name]}]
  ($ :div
     {:key pool-id
      :class (str "bg-white/40 backdrop-blur-sm rounded-xl p-5 shadow-sm transition-all duration-200 "
                  (when (and edit-mode? (not selected?)) "opacity-70 ")
                  (when (and edit-mode? selected?) "shadow-md "))}

     ;; Header with controls (in edit mode)
     (when edit-mode?
       ($ :div {:class "flex items-center justify-end gap-2 mb-3"}
          (when selected?
            ($ move-buttons
               {:on-move-up on-move-up
                :on-move-down on-move-down}))
          ($ selection-badge {:selected? selected?
                              :on-click (fn []
                                         (.stopPropagation js/event)
                                         (when on-toggle (on-toggle)))})))

     (let [career-paths (derive-career-paths program)]
       (if editing?
         ($ :div {:class "space-y-3"}
            ($ text-input-with-label
               {:label "Program Title"
                :value (:program-title program)
                :on-change (fn [e]
                            (let [new-val (.. e -target -value)]
                              (on-update-program pool-id :program-title new-val)))
                :on-click (fn [e] (.stopPropagation e))})
            ($ select-with-label
               {:label "Award Level"
                :value (:award-level-name program)
                :options [{:value "" :label "Select award level..."}
                          {:value "Certificate" :label "Certificate"}
                          {:value "Associate's degree" :label "Associate's degree"}
                          {:value "Bachelor's degree" :label "Bachelor's degree"}
                          {:value "Master's degree" :label "Master's degree"}
                          {:value "Doctoral degree" :label "Doctoral degree"}]
                :on-change (fn [e]
                            (let [new-val (.. e -target -value)]
                              (on-update-program pool-id :award-level-name new-val)))
                :on-click (fn [e] (.stopPropagation e))})
            ($ textarea-with-label
               {:label "Description"
                :value (:personalized-overview program)
                :rows 4
                :on-change (fn [e]
                            (let [new-val (.. e -target -value)]
                              (on-update-program pool-id :personalized-overview new-val)))
                :on-click (fn [e] (.stopPropagation e))})
            ;; Expected Outcomes — bullets when the value is a vector (new/AI data),
            ;; otherwise the legacy single-string textarea (unchanged).
            (if (vector? (:outcome-bullet program))
              ($ bullet-list-section
                 {:title "Expected Outcomes"
                  :class-name "mt-3"
                  :bullets (:outcome-bullet program)
                  :editing? true
                  :on-update-bullet (fn [idx new-val]
                                     (let [new-bullets (assoc (vec (:outcome-bullet program)) idx new-val)]
                                       (on-update-program pool-id :outcome-bullet new-bullets)))
                  :on-remove-bullet (fn [idx]
                                     (let [bs (:outcome-bullet program)
                                           new-bullets (vec (concat (take idx bs) (drop (inc idx) bs)))]
                                       (on-update-program pool-id :outcome-bullet new-bullets)))
                  :on-add-bullet (fn []
                                  (let [new-bullets (conj (vec (:outcome-bullet program)) "")]
                                    (on-update-program pool-id :outcome-bullet new-bullets)))
                  :show? true})
              ($ textarea-with-label
                 {:label "Expected Outcomes"
                  :value (:outcome-bullet program)
                  :rows 3
                  :on-change (fn [e]
                              (let [new-val (.. e -target -value)]
                                (on-update-program pool-id :outcome-bullet new-val)))
                  :on-click (fn [e] (.stopPropagation e))}))
            ;; Career Summary — same bullets-vs-textarea split.
            (if (vector? (:career-summary program))
              ($ bullet-list-section
                 {:title "Career Summary"
                  :class-name "mt-3"
                  :bullets (:career-summary program)
                  :editing? true
                  :on-update-bullet (fn [idx new-val]
                                     (let [new-bullets (assoc (vec (:career-summary program)) idx new-val)]
                                       (on-update-program pool-id :career-summary new-bullets)))
                  :on-remove-bullet (fn [idx]
                                     (let [bs (:career-summary program)
                                           new-bullets (vec (concat (take idx bs) (drop (inc idx) bs)))]
                                       (on-update-program pool-id :career-summary new-bullets)))
                  :on-add-bullet (fn []
                                  (let [new-bullets (conj (vec (:career-summary program)) "")]
                                    (on-update-program pool-id :career-summary new-bullets)))
                  :show? true})
              ($ textarea-with-label
                 {:label "Career Summary"
                  :value (:career-summary program)
                  :rows 3
                  :on-change (fn [e]
                              (let [new-val (.. e -target -value)]
                                (on-update-program pool-id :career-summary new-val)))
                  :on-click (fn [e] (.stopPropagation e))}))
            ($ bullet-list-section
               {:title "Career Paths"
                :class-name "mt-3"
                :bullets career-paths
                :editing? true
                :on-update-bullet (fn [idx new-val]
                                   (let [new-paths (assoc (vec career-paths) idx new-val)]
                                     (on-update-program pool-id :career-paths new-paths)))
                :on-remove-bullet (fn [idx]
                                   (let [new-paths (vec (concat (take idx career-paths)
                                                                (drop (inc idx) career-paths)))]
                                     (on-update-program pool-id :career-paths new-paths)))
                :on-add-bullet (fn []
                                (let [new-paths (conj (vec career-paths) "")]
                                  (on-update-program pool-id :career-paths new-paths)))
                :show? true})
            ($ text-input-with-label
               {:label "Program URL"
                :value (:program-url program)
                :placeholder "https://..."
                :on-change (fn [e]
                            (let [new-val (.. e -target -value)]
                              (on-update-program pool-id :program-url new-val)))
                :on-click (fn [e] (.stopPropagation e))})
            ($ button/Button
               {:variant "outline"
                :size "sm"
                :class "h-auto py-1 px-2"
                :on-click (fn [e]
                           (.stopPropagation e)
                           (let [search-query (str institution-name " " (:program-title program))
                                 encoded-query (js/encodeURIComponent search-query)
                                 search-url (str "https://www.google.com/search?q=" encoded-query)]
                             (.open js/window search-url "_blank")))}
               "🔍 Search Google"))
         ($ :<>
            ($ :div {:class "flex items-start justify-between gap-4 mb-3"}
               ($ :h4 {:class "font-semibold text-base text-gray-700 flex-1"}
                  (:program-title program))
               (when (:program-url program)
                 ($ :a {:href (:program-url program)
                        :target "_blank"
                        :rel "noopener noreferrer"
                        :class "text-sm text-primary hover:underline underline-offset-2 whitespace-nowrap"
                        :on-click (fn [e] (.stopPropagation e))}
                    "View Program →")))
            (when (:award-level-name program)
              ($ :div {:class "flex flex-wrap gap-2 mb-3"}
                 ($ badge/Badge {:variant "secondary" :class "text-xs"}
                    (:award-level-name program))))
            (when (:personalized-overview program)
              ($ :p {:class "text-sm md:text-base leading-relaxed text-gray-600 max-w-prose mb-3"}
                 (linkify-text (:personalized-overview program))))
            ;; Expected Outcomes — bullets for vector values (new/AI data),
            ;; legacy paragraph for plain strings.
            (let [ob (:outcome-bullet program)]
              (when (if (vector? ob) (seq ob) ob)
                (if (vector? ob)
                  ($ bullet-list-section
                     {:title "Expected Outcomes"
                      :class-name "space-y-2"
                      :bullets ob
                      :editing? false
                      :show? true})
                  ($ :div {:class "space-y-2"}
                     ($ :h4 {:class "text-base font-semibold text-gray-700"}
                        "Expected Outcomes")
                     ($ :p {:class "text-sm md:text-base leading-relaxed text-gray-600 max-w-prose"}
                        (linkify-text ob))))))
            (let [cs (:career-summary program)]
              (when (if (vector? cs) (seq cs) cs)
                (if (vector? cs)
                  ($ bullet-list-section
                     {:title "Career Summary"
                      :class-name "space-y-2 mt-3"
                      :bullets cs
                      :editing? false
                      :show? true})
                  ($ :div {:class "space-y-2 mt-3"}
                     ($ :h4 {:class "text-base font-semibold text-gray-700"}
                        "Career Summary")
                     ($ :p {:class "text-sm md:text-base leading-relaxed text-gray-600 max-w-prose"}
                        (linkify-text cs))))))
            (when (seq career-paths)
              ($ bullet-list-section
                 {:title "Career Paths"
                  :class-name "mt-3"
                  :bullets career-paths
                  :editing? false
                  :show? true})))))))

(defui programs-section
  [{:keys [pool-programs selected-program-ids program-text-edits
           edit-mode? editing?
           on-toggle-program on-move-program-up on-move-program-down
           on-update-program institution-name institution-id category pool-id
           city state show?]}]
  (when show?
    (let [selected-set (set selected-program-ids)
          ;; Build a map of program pool-id to position in selection
          selected-positions (into {} (map-indexed (fn [pos id] [id pos]) selected-program-ids))
          ;; Build list of programs to render in correct order
          programs-to-render (if edit-mode?
                              ;; Edit mode: show selected programs first (in order), then unselected
                              (let [selected-items (map (fn [selected-id]
                                                          (let [prog (find-by-id pool-programs selected-id)
                                                                text-edits (get program-text-edits selected-id)]
                                                            {:pool-id selected-id
                                                             :program (if text-edits (merge prog text-edits) prog)
                                                             :is-selected true}))
                                                        selected-program-ids)
                                    unselected-items (keep (fn [prog]
                                                             (when-not (contains? selected-set (:id prog))
                                                               {:pool-id (:id prog)
                                                                :program prog
                                                                :is-selected false}))
                                                           pool-programs)]
                                (concat selected-items unselected-items))
                              ;; View mode: show only selected programs in selection order
                              (map (fn [selected-id]
                                    (let [prog (find-by-id pool-programs selected-id)
                                          text-edits (get program-text-edits selected-id)]
                                      {:pool-id selected-id
                                       :program (if text-edits (merge prog text-edits) prog)
                                       :is-selected true}))
                                   selected-program-ids))
          selected-count (count (filter :is-selected programs-to-render))
          has-unselected? (< selected-count (count programs-to-render))]
      ($ :div
         ($ section-header
            {:count (if edit-mode? (count pool-programs) (count selected-program-ids))
             :singular-label "Program"
             :plural-label "Programs"})
         ($ :div {:class "space-y-4"}
            (for [[idx {:keys [pool-id program is-selected]}] (map-indexed vector programs-to-render)]
              ($ :<>
                 {:key pool-id}
                 ;; Show divider after last selected program (before unselected programs)
                 (when (and edit-mode?
                           has-unselected?
                           (= idx selected-count))
                   ($ :div {:class "flex items-center gap-4 py-3"}
                      ($ :div {:class "flex-1 h-px bg-gradient-to-r from-transparent via-gray-300 to-gray-300"})
                      ($ :div {:class "text-xs font-medium text-muted-foreground uppercase tracking-wider"}
                         "Other Programs")
                      ($ :div {:class "flex-1 h-px bg-gradient-to-l from-transparent via-gray-300 to-gray-300"})))
                 (let [position (get selected-positions pool-id)
                       is-first? (and position (= position 0))
                       is-last? (and position (= position (dec (count selected-program-ids))))]
                   ($ program-item
                      {:key pool-id
                       :program program
                       :pool-id pool-id
                       :selected? is-selected
                       :edit-mode? edit-mode?
                       :editing? editing?
                       :institution-name institution-name
                       :on-toggle (when edit-mode?
                                   #(on-toggle-program pool-id))
                       :on-move-up (when (and edit-mode? is-selected (not is-first?))
                                    #(on-move-program-up position))
                       :on-move-down (when (and edit-mode? is-selected (not is-last?))
                                      #(on-move-program-down position))
                       :on-update-program on-update-program}))))
            ;; Add Custom Program button (only in edit mode)
            (when edit-mode?
              ($ :div {:class "pt-2"}
                 ;; DISABLED pending scope agreement — 'Coming soon' hover
                 ;; (Cameron: revert the add-custom workaround). The span stops
                 ;; propagation so a click can't toggle the card.
                 ($ :span {:on-click (fn [e] (.stopPropagation e))}
                    ($ csb/coming-soon-button {:label "+ Add Custom Program"
                                               :class "w-full"})))))))))

;; ============================================================================
;; Cost Summary Component
;; ============================================================================

(defui cost-summary-section
  [{:keys [cost show?]}]
  (when show?
    ($ :div {:class "text-sm pt-6"}
       ($ :div {:class "text-muted-foreground text-xs mb-1"} "Estimated Annual Cost")
       ($ :div {:class "font-medium"}
          (if (:avg-annual cost)
            (str "$" (.toLocaleString (int (:avg-annual cost))))
            (str "$" (.toLocaleString (int (:min-annual cost)))
                 " - $"
                 (.toLocaleString (int (:max-annual cost)))))))))

;; ============================================================================
;; ============================================================================
;; Header Components
;; ============================================================================

(defui institution-title-location
  [{:keys [institution-name location programs-count expanded?]}]
  ($ :div {:class "flex-1"}
     ($ card/CardTitle {:class "text-lg md:text-xl font-semibold text-gray-700 mb-2"}
        institution-name)
     ($ :div {:class "text-sm text-muted-foreground"}
        (str (:city location) ", " (:state location)))
     (when (and (not expanded?) (> programs-count 0))
       ($ :div {:class "text-sm text-muted-foreground mt-1"}
          (str programs-count
               " Program"
               (when (> programs-count 1) "s"))))))

(defui card-control-buttons
  [{:keys [on-move-up on-move-down edit-mode? selected? expanded? on-toggle-expand on-toggle]}]
  ($ :div {:class "flex flex-col items-end gap-2"}
     ($ :div {:class "flex items-center gap-2"}
        ($ move-buttons
           {:on-move-up on-move-up
            :on-move-down on-move-down})
        (when edit-mode?
          ($ selection-badge {:selected? selected?
                              :on-click on-toggle})))
     ($ button/Button
        {:variant "ghost"
         :size "sm"
         :on-click (fn [e]
                    (.stopPropagation e)
                    (on-toggle-expand))}
        (if expanded? "Collapse" "Expand"))))

(defui card-header-section
  [{:keys [institution-name location programs-count expanded? on-move-up on-move-down
           edit-mode? selected? on-toggle-expand on-toggle]}]
  ($ card/CardHeader {:class "p-6"}
     ($ :div {:class "flex items-start justify-between gap-6"}
        ($ institution-title-location
           {:institution-name institution-name
            :location location
            :programs-count programs-count
            :expanded? expanded?})
        ($ card-control-buttons
           {:on-move-up on-move-up
            :on-move-down on-move-down
            :edit-mode? edit-mode?
            :selected? selected?
            :expanded? expanded?
            :on-toggle-expand on-toggle-expand
            :on-toggle on-toggle}))))

;; ============================================================================
;; Main Institution Card Component
;; ============================================================================

(defui institution-card
  [{:keys [institution institution-pool selected-program-ids program-text-edits
           selected? edit-mode? on-toggle on-toggle-program on-move-program-up on-move-program-down
           on-move-up on-move-down category pool-id
           ;; Optional controlled expand props (for student view)
           expanded? on-toggle-expand] :as props}]
  (let [;; Get api-client from context
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Card path for autosave state
        card-path [:institution category pool-id]

        ;; Always call the hook (React rules) but only use its value when not controlled
        subscribed-expanded? (use-subscribe [::recommendations-subs/card-expanded? card-path])
        controlled? (contains? props :expanded?)
        expanded? (if controlled? expanded? subscribed-expanded?)
        toggle-expand (if controlled?
                        on-toggle-expand
                        #(rf/dispatch [::recommendations-events/toggle-card-expanded card-path]))

        ;; Use current values directly (no edit buffer)
        why-fits-edits (:why-fits-bullets institution)
        financial-edits (:financial-bullets institution)
        program-edits-map program-text-edits

        location (get institution :location {})
        cost (get institution :cost-summary {})
        pool-programs (or (:programs institution-pool) [])]
    ($ card/Card
       {:class (str "relative transition-all duration-300 ease-in-out bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm border-0 cursor-pointer hover:shadow-lg hover:scale-[1.01] "
                    (when selected? "shadow-md"))
        :on-click toggle-expand}

       ;; Header
       ($ card-header-section
          {:institution-name (:institution-name institution)
           :location location
           :programs-count (count selected-program-ids)
           :expanded? expanded?
           :on-move-up on-move-up
           :on-move-down on-move-down
           :edit-mode? edit-mode?
           :selected? selected?
           :on-toggle-expand toggle-expand
           :on-toggle on-toggle})

       ;; Content
       (when expanded?
         ($ card/CardContent {:class "space-y-8 pt-0 px-6 pb-6"}

            ;; Programs Section
            ($ programs-section
               {:pool-programs pool-programs
                :selected-program-ids selected-program-ids
                :program-text-edits program-edits-map
                :edit-mode? edit-mode?
                :editing? edit-mode?  ;; Always editable when in edit mode (no separate card edit state)
                :institution-name (:institution-name institution)
                :institution-id pool-id
                :category category
                :pool-id pool-id
                :city (:city location)
                :state (:state location)
                :on-toggle-program on-toggle-program
                :on-move-program-up on-move-program-up
                :on-move-program-down on-move-program-down
                :on-update-program (fn [prog-pool-id field new-val]
                                    ;; Update selection directly and trigger autosave
                                    (let [new-prog-edits (assoc-in program-edits-map [prog-pool-id field] new-val)]
                                      (rf/dispatch [::recommendations-events/edit-institution-bullets
                                                   category
                                                   pool-id
                                                   {:why-fits-bullets why-fits-edits
                                                    :financial-bullets financial-edits
                                                    :program-text-edits new-prog-edits}
                                                   api-client])))
                :show? (or edit-mode? (seq pool-programs) (seq selected-program-ids))})

            ;; Divider
            ($ section-divider)

            ;; Why It Fits Section
            ($ bullet-list-section
               {:title "Why This School Fits"
                :bullets why-fits-edits
                :editing? edit-mode?  ;; Always editable when in edit mode
                :on-update-bullet (fn [idx new-val]
                                   ;; Update selection directly and trigger autosave
                                   (let [new-bullets (assoc (vec why-fits-edits) idx new-val)]
                                     (rf/dispatch [::recommendations-events/edit-institution-bullets
                                                  category
                                                  pool-id
                                                  {:why-fits-bullets new-bullets
                                                   :financial-bullets financial-edits
                                                   :program-text-edits program-edits-map}
                                                  api-client])))
                :on-remove-bullet (fn [idx]
                                   ;; Remove bullet and trigger autosave
                                   (let [new-bullets (vec (concat (take idx why-fits-edits)
                                                                  (drop (inc idx) why-fits-edits)))]
                                     (rf/dispatch [::recommendations-events/edit-institution-bullets
                                                  category
                                                  pool-id
                                                  {:why-fits-bullets new-bullets
                                                   :financial-bullets financial-edits
                                                   :program-text-edits program-edits-map}
                                                  api-client])))
                :on-add-bullet (fn []
                                ;; Add bullet and trigger autosave
                                (let [new-bullets (conj (vec why-fits-edits) "")]
                                  (rf/dispatch [::recommendations-events/edit-institution-bullets
                                               category
                                               pool-id
                                               {:why-fits-bullets new-bullets
                                                :financial-bullets financial-edits
                                                :program-text-edits program-edits-map}
                                               api-client])))
                :show? (or edit-mode? (seq (:why-fits-bullets institution)) (seq why-fits-edits))})

            ;; Financial Aid Section
            ($ bullet-list-section
               {:title "Financial Aid"
                :bullets financial-edits
                :editing? edit-mode?  ;; Always editable when in edit mode
                :on-update-bullet (fn [idx new-val]
                                   ;; Update selection directly and trigger autosave
                                   (let [new-bullets (assoc (vec financial-edits) idx new-val)]
                                     (rf/dispatch [::recommendations-events/edit-institution-bullets
                                                  category
                                                  pool-id
                                                  {:why-fits-bullets why-fits-edits
                                                   :financial-bullets new-bullets
                                                   :program-text-edits program-edits-map}
                                                  api-client])))
                :on-remove-bullet (fn [idx]
                                   ;; Remove bullet and trigger autosave
                                   (let [new-bullets (vec (concat (take idx financial-edits)
                                                                  (drop (inc idx) financial-edits)))]
                                     (rf/dispatch [::recommendations-events/edit-institution-bullets
                                                  category
                                                  pool-id
                                                  {:why-fits-bullets why-fits-edits
                                                   :financial-bullets new-bullets
                                                   :program-text-edits program-edits-map}
                                                  api-client])))
                :on-add-bullet (fn []
                                ;; Add bullet and trigger autosave
                                (let [new-bullets (conj (vec financial-edits) "")]
                                  (rf/dispatch [::recommendations-events/edit-institution-bullets
                                               category
                                               pool-id
                                               {:why-fits-bullets why-fits-edits
                                                :financial-bullets new-bullets
                                                :program-text-edits program-edits-map}
                                               api-client])))
                :show? (or edit-mode? (seq (:financial-bullets institution)) (seq financial-edits))})

)))))
