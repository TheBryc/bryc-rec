(ns store.advisor.recommendations.effects
  "Re-frame effects for recommendations API calls and transform functions"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [clojure.string]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

;; ============================================================================
;; Transform Functions
;; ============================================================================
;; CRITICAL: These functions handle the dual representation pattern.
;; The API returns edits embedded in institution/program refs,
;; but the UI state uses a central :text-edits map as source of truth.
;; These must remain exactly correct to avoid data loss.

(defn extract-edits-from-api
  "Extract text-edits from embedded refs into central map (UUID-based).
   The API returns edits embedded in institution/program refs,
   but the UI state uses a central :text-edits map as source of truth."
  [selection]
  (let [institutions (:institutions selection)
        scholarships (:scholarships selection)
        apprenticeships (:apprenticeships selection)

        ;; Extract institution text-edits into central map structure (UUID-based)
        inst-text-edits (reduce
                         (fn [acc category]
                           (let [inst-refs (get institutions category [])]
                             (reduce
                              (fn [acc inst-ref]
                                (let [inst-id (:id inst-ref)
                                      inst-edits (:text-edits inst-ref)
                                      prog-refs (:programs inst-ref [])
                                      ;; Extract institution-level edits
                                      acc (if inst-edits
                                            (-> acc
                                                (assoc-in [category inst-id :why-fits-bullets]
                                                         (:why-fits-bullets inst-edits))
                                                (assoc-in [category inst-id :financial-bullets]
                                                         (:financial-bullets inst-edits)))
                                            acc)
                                      ;; Extract program-level edits (UUID-based)
                                      acc (reduce (fn [acc prog-ref]
                                                   (let [prog-id (:id prog-ref)
                                                         prog-edits (:text-edits prog-ref)]
                                                     (if prog-edits
                                                       (assoc-in acc [category inst-id prog-id] prog-edits)
                                                       acc)))
                                                 acc
                                                 prog-refs)]
                                  acc))
                              acc
                              inst-refs)))
                         {}
                         [:safety :target :reach])

        ;; Extract scholarship text-edits (UUID-based)
        scholarship-text-edits (reduce
                                (fn [acc schol-ref]
                                  (let [schol-id (:id schol-ref)
                                        schol-edits (:text-edits schol-ref)]
                                    (if schol-edits
                                      (assoc-in acc [:scholarships schol-id] schol-edits)
                                      acc)))
                                inst-text-edits
                                (or scholarships []))

        ;; Extract apprenticeship text-edits (UUID-based)
        apprenticeship-text-edits (reduce
                                   (fn [acc app-ref]
                                     (let [app-id (:id app-ref)
                                           app-edits (:text-edits app-ref)]
                                       (if app-edits
                                         (assoc-in acc [:apprenticeships app-id] app-edits)
                                         acc)))
                                   scholarship-text-edits
                                   (or apprenticeships []))

        ;; Extract short-term-program text-edits (UUID-based)
        short-term-programs (:short-term-programs selection)
        text-edits (reduce
                    (fn [acc prog-ref]
                      (let [prog-id (:id prog-ref)
                            prog-edits (:text-edits prog-ref)]
                        (if prog-edits
                          (assoc-in acc [:short-term-programs prog-id] prog-edits)
                          acc)))
                    apprenticeship-text-edits
                    (or short-term-programs []))

        ;; Strip text-edits from refs (keep only UUIDs). :hidden-sections (the advisor
        ;; hide-a-block override, 0018) is PRESERVED on the cleaned ref — it is a
        ;; section-key vector, not central-mapped like text-edits — so a saved hide
        ;; survives a reload into the working selection (re-hiding another section later
        ;; never silently un-hides the earlier ones). Present-by-data: absent when nothing
        ;; is hidden, so a never-hidden ref stays byte-identical.
        clean-prog-refs (fn [prog-ref]
                         (cond-> {:id (:id prog-ref)}
                           (seq (:hidden-sections prog-ref))
                           (assoc :hidden-sections (vec (:hidden-sections prog-ref)))))
        clean-inst-refs (fn [inst-ref]
                         (cond-> {:id (:id inst-ref)
                                  :programs (mapv clean-prog-refs (:programs inst-ref []))}
                           (seq (:hidden-sections inst-ref))
                           (assoc :hidden-sections (vec (:hidden-sections inst-ref)))))
        clean-simple-ref (fn [ref] {:id (:id ref)})

        clean-institutions (-> institutions
                               (update :safety (partial mapv clean-inst-refs))
                               (update :target (partial mapv clean-inst-refs))
                               (update :reach (partial mapv clean-inst-refs)))
        clean-scholarships (mapv clean-simple-ref (or scholarships []))
        clean-apprenticeships (mapv clean-simple-ref (or apprenticeships []))
        clean-short-term-programs (mapv clean-simple-ref (or short-term-programs []))]

    (assoc selection
           :institutions clean-institutions
           :scholarships clean-scholarships
           :apprenticeships clean-apprenticeships
           :short-term-programs clean-short-term-programs
           :text-edits text-edits)))

(defn build-selection-for-api
  "Build selection with text-edits embedded in refs for API submission (UUID-based).
   The central :text-edits map is the source of truth in UI state,
   but the API expects edits embedded in institution/program refs."
  [selection]
  (let [text-edits (:text-edits selection)

        embed-prog-edits (fn [category inst-id prog-ref]
                          (let [prog-id (:id prog-ref)
                                prog-edits (get-in text-edits [category inst-id prog-id])]
                            (if prog-edits
                              (assoc prog-ref :text-edits prog-edits)
                              prog-ref)))

        embed-inst-edits (fn [category inst-ref]
                          (let [inst-id (:id inst-ref)
                                updated-progs (mapv #(embed-prog-edits category inst-id %)
                                                   (:programs inst-ref []))
                                inst-edits (select-keys (get-in text-edits [category inst-id])
                                                       [:why-fits-bullets :financial-bullets])]
                            (cond-> (assoc inst-ref :programs updated-progs)
                              (seq inst-edits) (assoc :text-edits inst-edits))))

        embed-scholarship-edits (fn [schol-ref]
                                 (let [schol-id (:id schol-ref)
                                       schol-edits (get-in text-edits [:scholarships schol-id])]
                                   (if schol-edits
                                     (assoc schol-ref :text-edits schol-edits)
                                     schol-ref)))

        embed-apprenticeship-edits (fn [app-ref]
                                    (let [app-id (:id app-ref)
                                          app-edits (get-in text-edits [:apprenticeships app-id])]
                                      (if app-edits
                                        (assoc app-ref :text-edits app-edits)
                                        app-ref)))

        embed-short-term-program-edits (fn [prog-ref]
                                        (let [prog-id (:id prog-ref)
                                              prog-edits (get-in text-edits [:short-term-programs prog-id])]
                                          (if prog-edits
                                            (assoc prog-ref :text-edits prog-edits)
                                            prog-ref)))]

    (-> selection
        (update-in [:institutions :safety] (partial mapv (partial embed-inst-edits :safety)))
        (update-in [:institutions :target] (partial mapv (partial embed-inst-edits :target)))
        (update-in [:institutions :reach] (partial mapv (partial embed-inst-edits :reach)))
        (update :scholarships (partial mapv embed-scholarship-edits))
        (update :apprenticeships (partial mapv embed-apprenticeship-edits))
        (update :short-term-programs (partial mapv embed-short-term-program-edits))
        (dissoc :text-edits))))

;; ============================================================================
;; API Effects
;; ============================================================================

(rf/reg-fx
  ::load-recommendations
  (fn [{:keys [student-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        (let [response (<! (api/query api-client {:query/name :student-ops/recommendations-screen
                                                   :student-id student-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            ;; Extract embedded edits into central map for UI state
            (let [ui-selection (extract-edits-from-api (:selection response))]
              (rf/dispatch (conj on-success
                                 {:student (:student response)
                                  :pool (:pool response)
                                  :pool-id (:pool-id response)
                                  :selection ui-selection
                                  ;; Same :resolved (selection applied to the pool) the
                                  ;; student query returns — carried through from the ONE
                                  ;; recommendations-screen query so the advisor authoring
                                  ;; read-through renders WYSIWYG without a re-fetch (0016).
                                  :resolved (:resolved response)
                                  ;; 0059 advisor identity — the advisee's assigned
                                  ;; advisor, resolved server-side from the SAME
                                  ;; "advises" relationship the token student view
                                  ;; uses, so the in-editor preview shows the
                                  ;; identical advisor card (present-by-data: nil).
                                  :advisor (:advisor response)
                                  :advisor-message (:advisor-message response)
                                  :field-definitions (:field-definitions response)
                                  :recommendation-status (:recommendation-status response)})))))))))

(rf/reg-fx
  ::save-customization
  (fn [{:keys [student-id pool-id selection api-client on-success on-failure]}]
    (when (and student-id pool-id selection api-client)
      (go
        ;; Transform selection to API format (embed edits in refs)
        (let [api-selection (build-selection-for-api selection)
              response (<! (api/command api-client
                                       {:command/name :student-ops/save-recommendation-customization
                                        :student-id student-id
                                        :pool-id pool-id
                                        :selection api-selection}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success selection)))))))))

;; ============================================================================
;; Legacy-pool migration Effect (0021, ADR 0005)
;; ============================================================================

(rf/reg-fx
  ::migrate-recommendations
  (fn [{:keys [student-id pool-id api-client on-success on-failure]}]
    (when (and student-id api-client)
      (go
        (let [response (<! (api/command api-client
                                        (cond-> {:command/name :student-ops/migrate-recommendations-pool
                                                 :student-id student-id}
                                          pool-id (assoc :pool-id pool-id))))]
          (if (anomaly? response)
            (when on-failure (rf/dispatch (conj on-failure response)))
            (when on-success (rf/dispatch on-success))))))))

;; ============================================================================
;; Autosave Effect
;; ============================================================================

(def autosave-timers (atom {}))

(rf/reg-fx
  ::schedule-autosave
  (fn [{:keys [card-path api-client delay]}]
    (when (and card-path api-client)
      ;; Clear existing timer for this card
      (when-let [timer (get @autosave-timers card-path)]
        (js/clearTimeout timer))
      ;; Schedule new autosave
      (swap! autosave-timers assoc card-path
             (js/setTimeout
              (fn []
                (swap! autosave-timers dissoc card-path)
                (rf/dispatch [:store.advisor.recommendations.events/perform-autosave api-client]))
              (or delay 3000))))))

;; ============================================================================
;; Generate Student View Link Effect
;; ============================================================================

(rf/reg-fx
  ::generate-student-view-link
  (fn [{:keys [student-id pool-id api-client on-success on-failure]}]
    (when (and student-id pool-id api-client)
      (go
        (let [response (<! (api/command api-client
                                       {:command/name :student-ops/generate-student-view-link
                                        :student-id student-id
                                        :pool-id pool-id}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

;; ============================================================================
;; Add Custom Item Effects
;; ============================================================================

(rf/reg-fx
  ::add-custom-institution
  (fn [{:keys [student-id pool-id category institution api-client on-success on-failure]}]
    (when (and student-id pool-id category institution api-client)
      (go
        (let [;; Build institution with required fields
              institution-data (merge
                                {:id (random-uuid)
                                 :source :custom
                                 :institution-name (:name institution)
                                 :name (:name institution)
                                 :admissions-likelihood (case category
                                                          :safety "Safety"
                                                          :target "Target"
                                                          :reach "Reach")
                                 :location {:city (:city institution)
                                           :state (:state institution)}
                                 :programs []
                                 :why-fits-bullets []
                                 :financial-bullets []}
                                institution)
              response (<! (api/command api-client
                                       {:command/name :student-ops/add-custom-institution
                                        :student-id student-id
                                        :pool-id pool-id
                                        :category category
                                        :institution institution-data}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::add-custom-program
  (fn [{:keys [student-id pool-id institution-id program api-client on-success on-failure]}]
    (when (and student-id pool-id institution-id program api-client)
      (go
        (let [;; Build program with required fields
              program-data (merge
                            {:id (random-uuid)
                             :source :custom
                             :program-title (:program-title program)
                             :institution-name (:institution-name program)
                             :city (:city program)
                             :state (:state program)
                             :personalized-overview (or (:personalized-overview program) "")
                             :program-specific-bullets []}
                            program)
              response (<! (api/command api-client
                                       {:command/name :student-ops/add-custom-program
                                        :student-id student-id
                                        :pool-id pool-id
                                        :institution-id institution-id
                                        :program program-data}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::add-custom-scholarship
  (fn [{:keys [student-id pool-id scholarship api-client on-success on-failure]}]
    (when (and student-id pool-id scholarship api-client)
      (go
        (let [scholarship-data (merge
                                {:id (random-uuid)
                                 :source :custom}
                                scholarship)
              response (<! (api/command api-client
                                       {:command/name :student-ops/add-custom-scholarship
                                        :student-id student-id
                                        :pool-id pool-id
                                        :scholarship scholarship-data}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::add-custom-apprenticeship
  (fn [{:keys [student-id pool-id apprenticeship api-client on-success on-failure]}]
    (when (and student-id pool-id apprenticeship api-client)
      (go
        (let [;; Parse requirements from textarea (split by newlines)
              requirements-text (:requirements apprenticeship "")
              requirements-vec (if (seq requirements-text)
                                (vec (remove empty? (clojure.string/split-lines requirements-text)))
                                [])
              apprenticeship-data (merge
                                   {:id (random-uuid)
                                    :source :custom
                                    :requirements requirements-vec}
                                   (dissoc apprenticeship :requirements))
              response (<! (api/command api-client
                                       {:command/name :student-ops/add-custom-apprenticeship
                                        :student-id student-id
                                        :pool-id pool-id
                                        :apprenticeship apprenticeship-data}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))

(rf/reg-fx
  ::add-custom-short-term-program
  (fn [{:keys [student-id pool-id short-term-program api-client on-success on-failure]}]
    (when (and student-id pool-id short-term-program api-client)
      (go
        (let [short-term-program-data (merge
                                       {:id (random-uuid)
                                        :source :custom}
                                       short-term-program)
              response (<! (api/command api-client
                                       {:command/name :student-ops/add-custom-short-term-program
                                        :student-id student-id
                                        :pool-id pool-id
                                        :short-term-program short-term-program-data}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch (conj on-success response)))))))))
