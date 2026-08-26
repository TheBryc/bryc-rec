(ns store.advisor.recommendations.events
  "Re-frame events for recommendations state management"
  (:require [re-frame.core :as rf]
            [components.advisor.authoring.hide :as hide]
            [components.advisor.authoring.override :as override]
            [store.advisor.recommendations.effects :as effects]))

;; ============================================================================
;; Phase 1: Loading + Basic UI State
;; ============================================================================

;; Load recommendations
(rf/reg-event-fx
  ::load-recommendations
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (-> db
             (assoc-in [:advisor :recommendations :loading?] true)
             (assoc-in [:advisor :recommendations :error] nil)
             (assoc-in [:advisor :recommendations :student-id] student-id))
     ::effects/load-recommendations
     {:student-id student-id
      :api-client api-client
      :on-success [::load-success]
      :on-failure [::load-failure]}}))

(defn load-success-db
  "PURE re-frame event-db handler for the recommendations-screen load. Extracted
   (named) so it's tested directly (data->data). Carries the 0059 :advisor identity
   map (the advisee's assigned advisor, resolved server-side from the SAME 'advises'
   relationship the token student view uses) onto the advisor store so the in-editor
   Student preview can thread it into the seed. Present-by-data: no :advisor ⇒ nil."
  [db [_ {:keys [student pool pool-id selection resolved advisor advisor-message field-definitions recommendation-status]}]]
  (let [field-map (when field-definitions
                    (reduce (fn [m fd] (assoc m (:slug fd) fd)) {} field-definitions))]
    (-> db
        (assoc-in [:advisor :recommendations :loading?] false)
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :recommendation-status] recommendation-status)
        (assoc-in [:crm :field-definitions] field-map)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        ;; :resolved = the selection applied to the pool (0016 authoring read-through).
        (assoc-in [:advisor :recommendations :resolved] resolved)
        (assoc-in [:advisor :recommendations :advisor] advisor)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message))))

(rf/reg-event-db ::load-success load-success-db)

;; Silent reload — refreshes recommendations data without showing loading spinner.
;; Used after field edits in the sidebar to avoid a full-page flash.
(rf/reg-event-fx
  ::reload-recommendations
  (fn [{:keys [db]} [_ student-id api-client]]
    {::effects/load-recommendations
     {:student-id student-id
      :api-client api-client
      :on-success [::load-success]
      :on-failure [::load-failure]}}))

(rf/reg-event-db
  ::load-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :recommendations :loading?] false)
        (assoc-in [:advisor :recommendations :error] "Failed to load recommendations"))))

;; ============================================================================
;; Legacy-pool migration (0021, ADR 0005) — lazy on first open in the new view
;; ============================================================================

;; Fire the re-projection migration for a legacy old-shape pool. Guarded by
;; :migration-attempted? so it runs at most once per session (a no-op server-side when
;; the pool is already new-shape). Shows a migrating? loading state; on success silently
;; reloads so the re-projected pool + migrated About/Costs prose render.
(rf/reg-event-fx
  ::migrate-recommendations
  (fn [{:keys [db]} [_ student-id api-client]]
    (let [pool-id (get-in db [:advisor :recommendations :pool-id])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :migrating?] true)
               (assoc-in [:advisor :recommendations :migration-attempted?] true))
       ::effects/migrate-recommendations
       {:student-id student-id
        :pool-id pool-id
        :api-client api-client
        :on-success [::migrate-success student-id api-client]
        :on-failure [::migrate-failure]}})))

(rf/reg-event-fx
  ::migrate-success
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (assoc-in db [:advisor :recommendations :migrating?] false)
     ;; Silent reload — pull the re-projected pool + migrated selection into the view.
     :dispatch [::reload-recommendations student-id api-client]}))

(rf/reg-event-db
  ::migrate-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :migrating?] false)
        (assoc-in [:advisor :recommendations :error] "Failed to migrate recommendations"))))

;; UI State: Active Tab
(rf/reg-event-db
  ::set-active-tab
  (fn [db [_ tab]]
    (assoc-in db [:advisor :recommendations :active-tab] tab)))

;; Detail Sheet State
(rf/reg-event-db
  ::open-detail-sheet
  (fn [db [_ item]]
    (assoc-in db [:advisor :recommendations :detail-sheet] item)))

(rf/reg-event-db
  ::close-detail-sheet
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :detail-sheet] nil)
        (assoc-in [:advisor :recommendations :program-detail-sheet] nil))))

(rf/reg-event-db
  ::open-program-detail-sheet
  (fn [db [_ data]]
    (assoc-in db [:advisor :recommendations :program-detail-sheet] data)))

(rf/reg-event-db
  ::close-program-detail-sheet
  (fn [db _]
    (assoc-in db [:advisor :recommendations :program-detail-sheet] nil)))

;; Issue 0062 — ::move-institution-to-category REMOVED. STR is a read-only engine fact
;; (:str-badge); advisors no longer manually re-label a school's STR category. Both the
;; dense editor and the student viewer now group by :str-badge, so a manual bucket move
;; changed nothing the student saw. Select/reorder still address the real selection bucket.

;; Save Customization
(rf/reg-event-fx
  ::save-customization
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])
          selection (get-in db [:advisor :recommendations :selection])]
      {:db (assoc-in db [:advisor :recommendations :saving?] true)
       ::effects/save-customization
       {:student-id student-id
        :pool-id pool-id
        :selection selection
        :api-client api-client
        :on-success [::save-success]
        :on-failure [::save-failure]}})))

(rf/reg-event-db
  ::save-success
  (fn [db [_ selection]]
    (-> db
        (assoc-in [:advisor :recommendations :saving?] false)
        (assoc-in [:advisor :recommendations :saved-selection] selection))))

(rf/reg-event-db
  ::save-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :recommendations :saving?] false)
        (assoc-in [:advisor :recommendations :error] "Failed to save customization"))))

;; ============================================================================
;; Hide-a-block (0018 — S2). Toggle a section-key into/out of a selection ref's
;; :hidden-sections via the PURE hide/toggle-hidden, applied to the REAL working
;; selection ref (never the display order, never a fork of the override logic), then
;; autosave — round-tripping through save-recommendation-customization →
;; :hidden-sections → apply-ref-overrides so the hide persists + reverts to source.
;; ============================================================================

(rf/reg-event-fx
  ::toggle-institution-section-hidden
  (fn [{:keys [db]} [_ category inst-id section-key api-client]]
    (let [selection (get-in db [:advisor :recommendations :selection])
          refs (get-in selection [:institutions category] [])
          new-refs (mapv (fn [r]
                           (if (= (:id r) inst-id)
                             (hide/toggle-hidden r section-key)
                             r))
                         refs)]
      {:db (assoc-in db [:advisor :recommendations :selection :institutions category] new-refs)
       :dispatch [::perform-autosave api-client]})))

(rf/reg-event-fx
  ::toggle-program-section-hidden
  (fn [{:keys [db]} [_ category inst-id prog-id section-key api-client]]
    (let [selection (get-in db [:advisor :recommendations :selection])
          refs (get-in selection [:institutions category] [])
          new-refs (mapv (fn [r]
                           (if (= (:id r) inst-id)
                             (update r :programs
                                     (fn [progs]
                                       (mapv (fn [p]
                                               (if (= (:id p) prog-id)
                                                 (hide/toggle-hidden p section-key)
                                                 p))
                                             progs)))
                             r))
                         refs)]
      {:db (assoc-in db [:advisor :recommendations :selection :institutions category] new-refs)
       :dispatch [::perform-autosave api-client]})))

;; ============================================================================
;; Prose / tile-value override (0019 — S3). Edit any narrative prose (:text-edits,
;; top-level) or override any headline tile value (:section-edits, deep-merged one
;; level into a named section data map) via the PURE override/ fns, applied to the
;; REAL working selection ref (never the display order, never a fork of the override
;; logic), then autosave — round-tripping through save-recommendation-customization →
;; :text-edits/:section-edits → apply-ref-overrides so the edit persists + reverts to
;; source. Charts + the LWC star rating carry NO point-edit event (hide-or-keep only).
;; ============================================================================

(defn- update-institution-ref
  "PURE: apply `f` to the institution ref with `inst-id` in `category`, in db."
  [db category inst-id f]
  (update-in db [:advisor :recommendations :selection :institutions category]
             (fn [refs] (mapv #(if (= (:id %) inst-id) (f %) %) refs))))

(defn- update-program-ref
  "PURE: apply `f` to the program ref `prog-id` nested in institution `inst-id`
   (`category`), in db."
  [db category inst-id prog-id f]
  (update-in db [:advisor :recommendations :selection :institutions category]
             (fn [refs]
               (mapv (fn [r]
                       (if (= (:id r) inst-id)
                         (update r :programs
                                 (fn [progs]
                                   (mapv #(if (= (:id %) prog-id) (f %) %) progs)))
                         r))
                     refs))))

(defn- update-apprenticeship-ref
  "PURE: apply `f` to the apprenticeship ref with `app-id` in the flat
   [:selection :apprenticeships] ref list, in db."
  [db app-id f]
  (update-in db [:advisor :recommendations :selection :apprenticeships]
             (fn [refs] (mapv #(if (= (:id %) app-id) (f %) %) refs))))

(defn- update-short-term-ref
  "PURE: apply `f` to the short-term-program ref with `program-id` in the flat
   [:selection :short-term-programs] ref list, in db."
  [db program-id f]
  (update-in db [:advisor :recommendations :selection :short-term-programs]
             (fn [refs] (mapv #(if (= (:id %) program-id) (f %) %) refs))))

(defn- update-scholarship-ref
  "PURE: apply `f` to the scholarship ref with `schol-id` in the flat
   [:selection :scholarships] ref list, in db."
  [db schol-id f]
  (update-in db [:advisor :recommendations :selection :scholarships]
             (fn [refs] (mapv #(if (= (:id %) schol-id) (f %) %) refs))))

;; ---- Institution-level prose (:text-edits) + tile (:section-edits) overrides ----

(rf/reg-event-fx
  ::set-institution-text-edit
  (fn [{:keys [db]} [_ category inst-id text-key value api-client]]
    {:db (update-institution-ref db category inst-id
                                 #(override/set-text-edit % text-key value))
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx
  ::revert-institution-text-edit
  (fn [{:keys [db]} [_ category inst-id text-key api-client]]
    {:db (update-institution-ref db category inst-id
                                 #(override/revert-text-edit % text-key))
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx
  ::set-institution-section-edit
  (fn [{:keys [db]} [_ category inst-id section-key field value api-client]]
    {:db (update-institution-ref db category inst-id
                                 #(override/set-section-edit % section-key field value))
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx
  ::revert-institution-section-edit
  (fn [{:keys [db]} [_ category inst-id section-key field api-client]]
    {:db (update-institution-ref db category inst-id
                                 #(override/revert-section-edit % section-key field))
     :dispatch [::perform-autosave api-client]}))

;; ---- Program-level prose (:text-edits) + tile (:section-edits) overrides ----

(rf/reg-event-fx
  ::set-program-text-edit
  (fn [{:keys [db]} [_ category inst-id prog-id text-key value api-client]]
    {:db (update-program-ref db category inst-id prog-id
                             #(override/set-text-edit % text-key value))
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx
  ::revert-program-text-edit
  (fn [{:keys [db]} [_ category inst-id prog-id text-key api-client]]
    {:db (update-program-ref db category inst-id prog-id
                             #(override/revert-text-edit % text-key))
     :dispatch [::perform-autosave api-client]}))

;; Named fx-handlers so the section-edit set/revert branches are testable purely,
;; data->data, with the autosave dispatch merely RETURNED (the api-client faked). The
;; dense-editor college detail sheet's inline-editable Overview prose fields dispatch
;; [::set-program-section-edit category inst-id prog-id :overview <field> value api-client]
;; on blur, and [::revert-program-section-edit …] when the field is cleared to empty —
;; landing {:section-edits {:overview {<field> v}}} on the REAL program ref so
;; apply-ref-overrides deep-merges it and clearing reverts exactly to the AI source.
(defn set-program-section-edit-fx
  [{:keys [db]} [_ category inst-id prog-id section-key field value api-client]]
  {:db (update-program-ref db category inst-id prog-id
                           #(override/set-section-edit % section-key field value))
   :dispatch [::perform-autosave api-client]})

(defn revert-program-section-edit-fx
  [{:keys [db]} [_ category inst-id prog-id section-key field api-client]]
  {:db (update-program-ref db category inst-id prog-id
                           #(override/revert-section-edit % section-key field))
   :dispatch [::perform-autosave api-client]})

(rf/reg-event-fx ::set-program-section-edit set-program-section-edit-fx)

(rf/reg-event-fx ::revert-program-section-edit revert-program-section-edit-fx)

;; 0079 — apprenticeship + short-term-program Overview prose fields inline-editable in the
;; dense editor (schema + backend already carry :section-edits for these refs, mirroring
;; program refs). Same named-fx-handler pattern as the program section-edit above: the
;; detail sheet's Overview inputs dispatch [::set-apprenticeship-section-edit app-id
;; :overview <field> value api-client] on blur (landing {:section-edits {:overview
;; {<field> v}}} on the REAL apprenticeship ref, deep-merged by apply-ref-overrides) and
;; [::revert-apprenticeship-section-edit app-id :overview <field> api-client] when cleared
;; (reverting EXACTLY to the AI source). Short-term mirrors it via program-id. Slices C/D
;; wire the detail-sheet UI to these events.
(defn set-apprenticeship-section-edit-fx
  [{:keys [db]} [_ app-id section-key field value api-client]]
  {:db (update-apprenticeship-ref db app-id
                                  #(override/set-section-edit % section-key field value))
   :dispatch [::perform-autosave api-client]})

(defn revert-apprenticeship-section-edit-fx
  [{:keys [db]} [_ app-id section-key field api-client]]
  {:db (update-apprenticeship-ref db app-id
                                  #(override/revert-section-edit % section-key field))
   :dispatch [::perform-autosave api-client]})

(defn set-short-term-section-edit-fx
  [{:keys [db]} [_ program-id section-key field value api-client]]
  {:db (update-short-term-ref db program-id
                              #(override/set-section-edit % section-key field value))
   :dispatch [::perform-autosave api-client]})

(defn revert-short-term-section-edit-fx
  [{:keys [db]} [_ program-id section-key field api-client]]
  {:db (update-short-term-ref db program-id
                              #(override/revert-section-edit % section-key field))
   :dispatch [::perform-autosave api-client]})

(rf/reg-event-fx ::set-apprenticeship-section-edit set-apprenticeship-section-edit-fx)

(rf/reg-event-fx ::revert-apprenticeship-section-edit revert-apprenticeship-section-edit-fx)

(rf/reg-event-fx ::set-short-term-section-edit set-short-term-section-edit-fx)

(rf/reg-event-fx ::revert-short-term-section-edit revert-short-term-section-edit-fx)

;; 0081 — the apprenticeship "Why This Fits" bullets are the top-level :bullets vector
;; (NOT inside a section map), so they override via the :text-edits seam (a FLAT top-level
;; field override), NOT :section-edits. ::apprenticeship-ref already carries :text-edits
;; (schemas.clj:351, [:map-of :keyword :any]) and the backend apply-ref-overrides already
;; flat-merges :text-edits, so a {:text-edits {:bullets v}} override round-trips + reverts
;; EXACTLY (override/revert-text-edit drops it → AI source :bullets returns). The dense-
;; editor apprenticeship sheet's "Why This Fits" editable bullet list dispatches
;; [::set-apprenticeship-text-edit app-id :bullets value api-client] on commit and
;; [::revert-apprenticeship-text-edit app-id :bullets api-client] when cleared. (This is
;; deliberately NOT the legacy ::edit-apprenticeship-bullets selection-level event, which the
;; dense-editor-scope tripwire forbids — this rides the ref override trail like the Overview
;; section-edits, so override/apply-overrides projects it in the WYSIWYG merge.)
(defn set-apprenticeship-text-edit-fx
  [{:keys [db]} [_ app-id text-key value api-client]]
  {:db (update-apprenticeship-ref db app-id
                                  #(override/set-text-edit % text-key value))
   :dispatch [::perform-autosave api-client]})

(defn revert-apprenticeship-text-edit-fx
  [{:keys [db]} [_ app-id text-key api-client]]
  {:db (update-apprenticeship-ref db app-id
                                  #(override/revert-text-edit % text-key))
   :dispatch [::perform-autosave api-client]})

(rf/reg-event-fx ::set-apprenticeship-text-edit set-apprenticeship-text-edit-fx)

(rf/reg-event-fx ::revert-apprenticeship-text-edit revert-apprenticeship-text-edit-fx)

;; 0085 — the scholarship detail sheet renders THREE AI-generated prose fields (Why This
;; Scholarship Fits ← :personalized-explanation, Description ← :description, Application Tips
;; ← :application-tips). Per PRD 0011 (Daryl 22-Jul: AI-generated ⇒ editable) these are now
;; INLINE-EDITABLE in the dense editor. They are FLAT top-level scholarship fields (NOT inside
;; a section map), so — exactly like the apprenticeship :bullets text-edit (0081) — they ride
;; the :text-edits seam, NOT :section-edits. ::scholarship-ref already carries :text-edits
;; (schemas.clj:346) and the backend apply-ref-overrides already flat-merges :text-edits for
;; scholarships, so a {:text-edits {<field> v}} override round-trips + reverts EXACTLY
;; (override/revert-text-edit drops it → the AI-source value returns). The dense-editor
;; scholarship sheet's editable prose fields dispatch [::set-scholarship-text-edit schol-id
;; <field> value api-client] on blur and [::revert-scholarship-text-edit schol-id <field>
;; api-client] when cleared. Name / Award / Selection Criteria / Deadline / URL stay read-only.
;; (Deliberately NOT the legacy ::edit-scholarship selection-level event, which the dense-
;; editor-scope tripwire forbids — this rides the ref override trail so override/apply-overrides
;; projects it in the WYSIWYG merge.)
(defn set-scholarship-text-edit-fx
  [{:keys [db]} [_ schol-id text-key value api-client]]
  {:db (update-scholarship-ref db schol-id
                               #(override/set-text-edit % text-key value))
   :dispatch [::perform-autosave api-client]})

(defn revert-scholarship-text-edit-fx
  [{:keys [db]} [_ schol-id text-key api-client]]
  {:db (update-scholarship-ref db schol-id
                               #(override/revert-text-edit % text-key))
   :dispatch [::perform-autosave api-client]})

(rf/reg-event-fx ::set-scholarship-text-edit set-scholarship-text-edit-fx)

(rf/reg-event-fx ::revert-scholarship-text-edit revert-scholarship-text-edit-fx)

;; ============================================================================
;; Helpers
;; ============================================================================

(defn move-item
  "Move an item from old-idx to new-idx in a vector."
  [v old-idx new-idx]
  (let [item (nth v old-idx)
        removed (into (subvec v 0 old-idx) (subvec v (inc old-idx)))
        before (subvec removed 0 new-idx)
        after (subvec removed new-idx)]
    (into (conj before item) after)))

;; ============================================================================
;; Phase 2: Selection State
;; ============================================================================

;; Institution Selection Events (with autosave)
;; Named fx-handler so the SELECT (promote-from-pool) / DESELECT branches are testable
;; purely, data->data, with the autosave dispatch merely RETURNED. Registered under the
;; SAME event keyword — the advisor authoring view's 'Show to student' promote control
;; dispatches [::toggle-institution-selection category inst-id api-client] (SELECT branch).
(defn toggle-institution-selection-fx
  [{:keys [db]} [_ category inst-id api-client]]
  (let [current-selection (get-in db [:advisor :recommendations :selection])
        inst-refs (get-in current-selection [:institutions category] [])
        is-selected (some #(= (:id %) inst-id) inst-refs)
        new-refs (if is-selected
                   ;; Deselect: remove this institution ref
                   (vec (remove #(= (:id %) inst-id) inst-refs))
                   ;; Select (promote): add new institution ref with empty programs
                   (conj inst-refs {:id inst-id :programs []}))
        new-selection (assoc-in current-selection [:institutions category] new-refs)
        ;; Clean up text-edits for deselected institution
        new-selection (if is-selected
                        (update-in new-selection [:text-edits category]
                                   #(dissoc % inst-id))
                        new-selection)
        card-path [:institution category inst-id]]
    {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx ::toggle-institution-selection toggle-institution-selection-fx)

(rf/reg-event-fx
  ::move-institution-up
  (fn [{:keys [db]} [_ category position api-client]]
    (when (> position 0)
      (let [current-selection (get-in db [:advisor :recommendations :selection])
            category-path [:institutions category]
            institutions (get-in current-selection category-path [])
            ;; Swap with previous institution
            inst-at-pos (nth institutions position)
            inst-before (nth institutions (dec position))
            new-institutions (-> (vec institutions)
                                 (assoc (dec position) inst-at-pos)
                                 (assoc position inst-before))
            new-selection (assoc-in current-selection category-path new-institutions)]
        {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
         :dispatch [::perform-autosave api-client]}))))

(rf/reg-event-fx
  ::move-institution-down
  (fn [{:keys [db]} [_ category position api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          category-path [:institutions category]
          institutions (get-in current-selection category-path [])]
      (when (< position (dec (count institutions)))
        (let [;; Swap with next institution
              inst-at-pos (nth institutions position)
              inst-after (nth institutions (inc position))
              new-institutions (-> (vec institutions)
                                   (assoc position inst-after)
                                   (assoc (inc position) inst-at-pos))
              new-selection (assoc-in current-selection category-path new-institutions)]
          {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
           :dispatch [::perform-autosave api-client]})))))

(rf/reg-event-fx
  ::reorder-institutions
  (fn [{:keys [db]} [_ category old-idx new-idx api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          category-path [:institutions category]
          institutions (get-in current-selection category-path [])
          new-institutions (move-item (vec institutions) old-idx new-idx)
          new-selection (assoc-in current-selection category-path new-institutions)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

;; Program Selection Events (with autosave)
;; Named fx-handler (same keyword) so the SELECT (promote-within-school) / DESELECT
;; branches are testable purely, data->data. The advisor authoring view's per-school
;; program promote control dispatches [::toggle-program-selection category inst-id
;; prog-id api-client] (SELECT branch) to show a pool program to the student.
(defn toggle-program-selection-fx
  [{:keys [db]} [_ category inst-id prog-id api-client]]
  (let [current-selection (get-in db [:advisor :recommendations :selection])
        inst-refs (get-in current-selection [:institutions category] [])
        inst-ref-idx (.indexOf (mapv :id inst-refs) inst-id)
        inst-ref (nth inst-refs inst-ref-idx)
        current-prog-ids (mapv :id (:programs inst-ref []))
        is-selected (some #(= % prog-id) current-prog-ids)
        new-prog-ids (if is-selected
                       (vec (remove #(= % prog-id) current-prog-ids))
                       ;; Select (promote): append the pool program's id
                       (conj current-prog-ids prog-id))
        new-prog-refs (mapv (fn [id] {:id id}) new-prog-ids)
        new-inst-ref (assoc inst-ref :programs new-prog-refs)
        new-inst-refs (assoc (vec inst-refs) inst-ref-idx new-inst-ref)
        new-selection (assoc-in current-selection [:institutions category] new-inst-refs)
        ;; Clean up text-edits for deselected program
        new-selection (if is-selected
                        (update-in new-selection [:text-edits category inst-id]
                                   #(dissoc % prog-id))
                        new-selection)
        card-path [:institution category inst-id]]
    {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
     :dispatch [::perform-autosave api-client]}))

(rf/reg-event-fx ::toggle-program-selection toggle-program-selection-fx)

(rf/reg-event-fx
  ::move-program-up
  (fn [{:keys [db]} [_ category inst-id position api-client]]
    (when (> position 0)
      (let [current-selection (get-in db [:advisor :recommendations :selection])
            inst-refs (get-in current-selection [:institutions category] [])
            inst-ref-idx (.indexOf (mapv :id inst-refs) inst-id)
            inst-ref (nth inst-refs inst-ref-idx)
            programs (:programs inst-ref [])
            ;; Swap with previous program
            prog-at-pos (nth programs position)
            prog-before (nth programs (dec position))
            new-programs (-> (vec programs)
                             (assoc (dec position) prog-at-pos)
                             (assoc position prog-before))
            new-inst-ref (assoc inst-ref :programs new-programs)
            new-inst-refs (assoc (vec inst-refs) inst-ref-idx new-inst-ref)
            new-selection (assoc-in current-selection [:institutions category] new-inst-refs)]
        {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
         :dispatch [::perform-autosave api-client]}))))

(rf/reg-event-fx
  ::move-program-down
  (fn [{:keys [db]} [_ category inst-id position api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          inst-refs (get-in current-selection [:institutions category] [])
          inst-ref-idx (.indexOf (mapv :id inst-refs) inst-id)
          inst-ref (nth inst-refs inst-ref-idx)
          programs (:programs inst-ref [])]
      (when (< position (dec (count programs)))
        (let [;; Swap with next program
              prog-at-pos (nth programs position)
              prog-after (nth programs (inc position))
              new-programs (-> (vec programs)
                               (assoc position prog-after)
                               (assoc (inc position) prog-at-pos))
              new-inst-ref (assoc inst-ref :programs new-programs)
              new-inst-refs (assoc (vec inst-refs) inst-ref-idx new-inst-ref)
              new-selection (assoc-in current-selection [:institutions category] new-inst-refs)]
          {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
           :dispatch [::perform-autosave api-client]})))))

(rf/reg-event-fx
  ::reorder-programs
  (fn [{:keys [db]} [_ category inst-id old-idx new-idx api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          inst-refs (get-in current-selection [:institutions category] [])
          inst-ref-idx (.indexOf (mapv :id inst-refs) inst-id)
          inst-ref (nth inst-refs inst-ref-idx)
          programs (:programs inst-ref [])
          new-programs (move-item (vec programs) old-idx new-idx)
          new-inst-ref (assoc inst-ref :programs new-programs)
          new-inst-refs (assoc (vec inst-refs) inst-ref-idx new-inst-ref)
          new-selection (assoc-in current-selection [:institutions category] new-inst-refs)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

;; Scholarship Selection Events (with autosave)
(rf/reg-event-fx
  ::toggle-scholarship-selection
  (fn [{:keys [db]} [_ schol-id api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          current-ids (mapv :id (get-in current-selection [:scholarships] []))
          is-selected (some #(= % schol-id) current-ids)
          new-ids (if is-selected
                        (vec (remove #(= % schol-id) current-ids))
                        (conj current-ids schol-id))
          new-refs (mapv (fn [id] {:id id}) new-ids)
          new-selection (assoc-in current-selection [:scholarships] new-refs)
          card-path [:scholarship schol-id]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

(rf/reg-event-fx
  ::move-scholarship-up
  (fn [{:keys [db]} [_ position api-client]]
    (when (> position 0)
      (let [current-selection (get-in db [:advisor :recommendations :selection])
            scholarships (get-in current-selection [:scholarships] [])
            ;; Swap with previous scholarship
            schol-at-pos (nth scholarships position)
            schol-before (nth scholarships (dec position))
            new-scholarships (-> (vec scholarships)
                                 (assoc (dec position) schol-at-pos)
                                 (assoc position schol-before))
            new-selection (assoc-in current-selection [:scholarships] new-scholarships)]
        {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
         :dispatch [::perform-autosave api-client]}))))

(rf/reg-event-fx
  ::move-scholarship-down
  (fn [{:keys [db]} [_ position api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          scholarships (get-in current-selection [:scholarships] [])]
      (when (< position (dec (count scholarships)))
        (let [;; Swap with next scholarship
              schol-at-pos (nth scholarships position)
              schol-after (nth scholarships (inc position))
              new-scholarships (-> (vec scholarships)
                                   (assoc position schol-after)
                                   (assoc (inc position) schol-at-pos))
              new-selection (assoc-in current-selection [:scholarships] new-scholarships)]
          {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
           :dispatch [::perform-autosave api-client]})))))

(rf/reg-event-fx
  ::reorder-scholarships
  (fn [{:keys [db]} [_ old-idx new-idx api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          scholarships (get-in current-selection [:scholarships] [])
          new-scholarships (move-item (vec scholarships) old-idx new-idx)
          new-selection (assoc-in current-selection [:scholarships] new-scholarships)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

;; Apprenticeship Selection Events (immediate save)
(rf/reg-event-fx
  ::toggle-apprenticeship-selection
  (fn [{:keys [db]} [_ app-id api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          current-ids (mapv :id (get-in current-selection [:apprenticeships] []))
          is-selected (some #(= % app-id) current-ids)
          new-ids (if is-selected
                        (vec (remove #(= % app-id) current-ids))
                        (conj current-ids app-id))
          new-refs (mapv (fn [id] {:id id}) new-ids)
          new-selection (assoc-in current-selection [:apprenticeships] new-refs)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

(rf/reg-event-fx
  ::move-apprenticeship-up
  (fn [{:keys [db]} [_ position api-client]]
    (when (> position 0)
      (let [current-selection (get-in db [:advisor :recommendations :selection])
            apprenticeships (get-in current-selection [:apprenticeships] [])
            ;; Swap with previous apprenticeship
            app-at-pos (nth apprenticeships position)
            app-before (nth apprenticeships (dec position))
            new-apprenticeships (-> (vec apprenticeships)
                                    (assoc (dec position) app-at-pos)
                                    (assoc position app-before))
            new-selection (assoc-in current-selection [:apprenticeships] new-apprenticeships)]
        {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
         :dispatch [::perform-autosave api-client]}))))

(rf/reg-event-fx
  ::move-apprenticeship-down
  (fn [{:keys [db]} [_ position api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          apprenticeships (get-in current-selection [:apprenticeships] [])]
      (when (< position (dec (count apprenticeships)))
        (let [;; Swap with next apprenticeship
              app-at-pos (nth apprenticeships position)
              app-after (nth apprenticeships (inc position))
              new-apprenticeships (-> (vec apprenticeships)
                                      (assoc position app-after)
                                      (assoc (inc position) app-at-pos))
              new-selection (assoc-in current-selection [:apprenticeships] new-apprenticeships)]
          {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
           :dispatch [::perform-autosave api-client]})))))

(rf/reg-event-fx
  ::reorder-apprenticeships
  (fn [{:keys [db]} [_ old-idx new-idx api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          apprenticeships (get-in current-selection [:apprenticeships] [])
          new-apprenticeships (move-item (vec apprenticeships) old-idx new-idx)
          new-selection (assoc-in current-selection [:apprenticeships] new-apprenticeships)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

;; Short-Term Program Selection Events (immediate save)
(rf/reg-event-fx
  ::toggle-short-term-program-selection
  (fn [{:keys [db]} [_ program-id api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          current-ids (mapv :id (get-in current-selection [:short-term-programs] []))
          is-selected (some #(= % program-id) current-ids)
          new-ids (if is-selected
                        (vec (remove #(= % program-id) current-ids))
                        (conj current-ids program-id))
          new-refs (mapv (fn [id] {:id id}) new-ids)
          new-selection (assoc-in current-selection [:short-term-programs] new-refs)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

(rf/reg-event-fx
  ::move-short-term-program-up
  (fn [{:keys [db]} [_ position api-client]]
    (when (> position 0)
      (let [current-selection (get-in db [:advisor :recommendations :selection])
            programs (get-in current-selection [:short-term-programs] [])
            ;; Swap with previous program
            prog-at-pos (nth programs position)
            prog-before (nth programs (dec position))
            new-programs (-> (vec programs)
                            (assoc (dec position) prog-at-pos)
                            (assoc position prog-before))
            new-selection (assoc-in current-selection [:short-term-programs] new-programs)]
        {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
         :dispatch [::perform-autosave api-client]}))))

(rf/reg-event-fx
  ::move-short-term-program-down
  (fn [{:keys [db]} [_ position api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          programs (get-in current-selection [:short-term-programs] [])]
      (when (< position (dec (count programs)))
        (let [;; Swap with next program
              prog-at-pos (nth programs position)
              prog-after (nth programs (inc position))
              new-programs (-> (vec programs)
                              (assoc position prog-after)
                              (assoc (inc position) prog-at-pos))
              new-selection (assoc-in current-selection [:short-term-programs] new-programs)]
          {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
           :dispatch [::perform-autosave api-client]})))))

(rf/reg-event-fx
  ::reorder-short-term-programs
  (fn [{:keys [db]} [_ old-idx new-idx api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          programs (get-in current-selection [:short-term-programs] [])
          new-programs (move-item (vec programs) old-idx new-idx)
          new-selection (assoc-in current-selection [:short-term-programs] new-programs)]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::perform-autosave api-client]})))

;; ============================================================================
;; Phase 3: Text Edits (with autosave)
;; ============================================================================

;; Institution bullet edits (updates selection directly, triggers autosave)
(rf/reg-event-fx
  ::edit-institution-bullets
  (fn [{:keys [db]} [_ category inst-id edits api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          ;; Save text edits for institution-level fields (why-fits, financial)
          new-selection (-> current-selection
                            (assoc-in [:text-edits category inst-id :why-fits-bullets]
                                     (:why-fits-bullets edits))
                            (assoc-in [:text-edits category inst-id :financial-bullets]
                                     (:financial-bullets edits)))
          ;; Save program text edits to central map (keyed by UUID)
          prog-text-edits (:program-text-edits edits)
          new-selection (if prog-text-edits
                          (reduce (fn [sel [prog-id prog-edits]]
                                   (assoc-in sel [:text-edits category inst-id prog-id] prog-edits))
                                 new-selection
                                 prog-text-edits)
                          new-selection)
          card-path [:institution category inst-id]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::schedule-autosave card-path api-client]})))

;; Scholarship edits (updates selection directly, triggers autosave)
(rf/reg-event-fx
  ::edit-scholarship
  (fn [{:keys [db]} [_ schol-id edits api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          new-selection (assoc-in current-selection [:text-edits :scholarships schol-id] edits)
          card-path [:scholarship schol-id]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::schedule-autosave card-path api-client]})))

;; Apprenticeship bullet edits (updates selection directly, triggers autosave)
(rf/reg-event-fx
  ::edit-apprenticeship-bullets
  (fn [{:keys [db]} [_ app-id edits api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          new-selection (assoc-in current-selection [:text-edits :apprenticeships app-id] edits)
          card-path [:apprenticeship app-id]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::schedule-autosave card-path api-client]})))

;; Short-term program field edits (updates selection directly, triggers autosave)
(rf/reg-event-fx
  ::edit-short-term-program-field
  (fn [{:keys [db]} [_ program-id edits api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          new-selection (assoc-in current-selection [:text-edits :short-term-programs program-id] edits)
          card-path [:short-term-program program-id]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::schedule-autosave card-path api-client]})))

;; Advisor message edit (updates selection directly, triggers autosave)
(rf/reg-event-fx
  ::edit-advisor-message
  (fn [{:keys [db]} [_ message api-client]]
    (let [current-selection (get-in db [:advisor :recommendations :selection])
          new-selection (assoc current-selection :advisor-message message)
          card-path [:advisor-message]]
      {:db (assoc-in db [:advisor :recommendations :selection] new-selection)
       :dispatch [::schedule-autosave card-path api-client]})))

;; ============================================================================
;; Phase 4: Card State Management (used by student view card components)
;; ============================================================================

;; Toggle card expanded state
(rf/reg-event-db
  ::toggle-card-expanded
  (fn [db [_ card-path]]
    (let [expanded-set (get-in db [:advisor :recommendations :card-state :expanded] #{})
          new-expanded (if (contains? expanded-set card-path)
                        (disj expanded-set card-path)
                        (conj expanded-set card-path))]
      (assoc-in db [:advisor :recommendations :card-state :expanded] new-expanded))))

;; ============================================================================
;; Phase 5: Autosave
;; ============================================================================

;; Schedule autosave for a card (debounced)
(rf/reg-event-fx
  ::schedule-autosave
  (fn [{:keys [db]} [_ card-path api-client]]
    {:db (-> db
             (update-in [:advisor :recommendations :autosave-pending] (fnil conj #{}) card-path)
             (assoc-in [:advisor :recommendations :save-status] "pending"))
     ::effects/schedule-autosave {:card-path card-path
                                   :api-client api-client
                                   :delay 3000}}))

;; Perform autosave (triggered by debounce timer)
(rf/reg-event-fx
  ::perform-autosave
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])
          selection (get-in db [:advisor :recommendations :selection])]
      {:db (assoc-in db [:advisor :recommendations :save-status] "saving")
       ::effects/save-customization
       {:student-id student-id
        :pool-id pool-id
        :selection selection
        :api-client api-client
        :on-success [::autosave-success]
        :on-failure [::autosave-failure]}})))

;; Autosave success
(rf/reg-event-db
  ::autosave-success
  (fn [db [_ selection]]
    (-> db
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :autosave-pending] #{})
        (assoc-in [:advisor :recommendations :save-status] "saved"))))

;; Autosave failure
(rf/reg-event-db
  ::autosave-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :save-status] "error")
        (assoc-in [:advisor :recommendations :error] "Failed to autosave changes"))))

;; ============================================================================
;; Phase 6: Share with Student
;; ============================================================================

;; Generate student view link
(rf/reg-event-fx
  ::generate-student-view-link
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])]
      {:db (assoc-in db [:advisor :recommendations :generating-link?] true)
       ::effects/generate-student-view-link
       {:student-id student-id
        :pool-id pool-id
        :api-client api-client
        :on-success [::generate-link-success]
        :on-failure [::generate-link-failure]}})))

(rf/reg-event-db
  ::generate-link-success
  (fn [db [_ {:keys [short-url]}]]
    (-> db
        (assoc-in [:advisor :recommendations :generating-link?] false)
        (assoc-in [:advisor :recommendations :student-view-link] short-url)
        (assoc-in [:advisor :recommendations :show-link-modal?] true))))

(rf/reg-event-db
  ::generate-link-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :recommendations :generating-link?] false)
        (assoc-in [:advisor :recommendations :error] "Failed to generate student view link"))))

(rf/reg-event-db
  ::close-link-modal
  (fn [db _]
    (assoc-in db [:advisor :recommendations :show-link-modal?] false)))

;; ============================================================================
;; Custom Item Modals
;; ============================================================================

;; Add Custom Institution Modal
(rf/reg-event-db
  ::open-add-institution-modal
  (fn [db [_ category]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-institution :show?] true)
        (assoc-in [:advisor :recommendations :modals :add-institution :category] category)
        (assoc-in [:advisor :recommendations :modals :add-institution :error] nil))))

(rf/reg-event-db
  ::close-add-institution-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-institution :show?] false)
        (assoc-in [:advisor :recommendations :modals :add-institution :category] nil)
        (assoc-in [:advisor :recommendations :modals :add-institution :error] nil))))

(rf/reg-event-fx
  ::submit-add-institution
  (fn [{:keys [db]} [_ form-data api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])
          category (get-in db [:advisor :recommendations :modals :add-institution :category])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :modals :add-institution :submitting?] true)
               ;; Store api-client for use in refetch
               (assoc-in [:advisor :recommendations :api-client] api-client))
       ::effects/add-custom-institution
       {:student-id student-id
        :pool-id pool-id
        :category category
        :institution form-data
        :api-client api-client
        :on-success [::add-institution-success]
        :on-failure [::add-institution-failure]}})))

(rf/reg-event-fx
  ::add-institution-success
  (fn [{:keys [db]} [_ _result]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          api-client (get-in db [:advisor :recommendations :api-client])]
      {:db db ;; Keep modal open with submitting state
       ::effects/load-recommendations
       {:student-id student-id
        :api-client api-client
        :on-success [::add-institution-refetch-complete]
        :on-failure [::add-institution-refetch-failure]}})))

(rf/reg-event-db
  ::add-institution-refetch-complete
  (fn [db [_ {:keys [student pool pool-id selection advisor-message]}]]
    (-> db
        ;; Update all recommendations data (same as ::load-success)
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message)
        ;; Close modal and clear submitting state
        (assoc-in [:advisor :recommendations :modals :add-institution :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-institution :show?] false))))

(rf/reg-event-db
  ::add-institution-refetch-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-institution :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-institution :error] "Failed to add institution"))))

(rf/reg-event-db
  ::add-institution-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-institution :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-institution :error] "Failed to add institution"))))

;; Add Custom Program Modal
(rf/reg-event-db
  ::open-add-program-modal
  (fn [db [_ institution-context]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-program :show?] true)
        (assoc-in [:advisor :recommendations :modals :add-program :institution-context] institution-context)
        (assoc-in [:advisor :recommendations :modals :add-program :error] nil))))

(rf/reg-event-db
  ::close-add-program-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-program :show?] false)
        (assoc-in [:advisor :recommendations :modals :add-program :institution-context] nil)
        (assoc-in [:advisor :recommendations :modals :add-program :error] nil))))

(rf/reg-event-fx
  ::submit-add-program
  (fn [{:keys [db]} [_ form-data api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])
          institution-context (get-in db [:advisor :recommendations :modals :add-program :institution-context])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :modals :add-program :submitting?] true)
               ;; Store api-client for use in refetch
               (assoc-in [:advisor :recommendations :api-client] api-client))
       ::effects/add-custom-program
       {:student-id student-id
        :pool-id pool-id
        :institution-id (:institution-id institution-context)
        :program form-data
        :api-client api-client
        :on-success [::add-program-success]
        :on-failure [::add-program-failure]}})))

(rf/reg-event-fx
  ::add-program-success
  (fn [{:keys [db]} [_ _result]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          api-client (get-in db [:advisor :recommendations :api-client])]
      {:db db ;; Keep modal open with submitting state
       ::effects/load-recommendations
       {:student-id student-id
        :api-client api-client
        :on-success [::add-program-refetch-complete]
        :on-failure [::add-program-refetch-failure]}})))

(rf/reg-event-db
  ::add-program-refetch-complete
  (fn [db [_ {:keys [student pool pool-id selection advisor-message]}]]
    (-> db
        ;; Update all recommendations data (same as ::load-success)
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message)
        ;; Close modal and clear submitting state
        (assoc-in [:advisor :recommendations :modals :add-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-program :show?] false))))

(rf/reg-event-db
  ::add-program-refetch-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-program :error] "Failed to add program"))))

(rf/reg-event-db
  ::add-program-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-program :error] "Failed to add program"))))

;; Add Custom Scholarship Modal
(rf/reg-event-db
  ::open-add-scholarship-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-scholarship :show?] true)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :error] nil))))

(rf/reg-event-db
  ::close-add-scholarship-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-scholarship :show?] false)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :error] nil))))

(rf/reg-event-fx
  ::submit-add-scholarship
  (fn [{:keys [db]} [_ form-data api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :modals :add-scholarship :submitting?] true)
               (assoc-in [:advisor :recommendations :api-client] api-client))
       ::effects/add-custom-scholarship
       {:student-id student-id
        :pool-id pool-id
        :scholarship form-data
        :api-client api-client
        :on-success [::add-scholarship-success]
        :on-failure [::add-scholarship-failure]}})))

(rf/reg-event-fx
  ::add-scholarship-success
  (fn [{:keys [db]} [_ _result]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          api-client (get-in db [:advisor :recommendations :api-client])]
      {:db db
       ::effects/load-recommendations
       {:student-id student-id
        :api-client api-client
        :on-success [::add-scholarship-refetch-complete]
        :on-failure [::add-scholarship-refetch-failure]}})))

(rf/reg-event-db
  ::add-scholarship-refetch-complete
  (fn [db [_ {:keys [student pool pool-id selection advisor-message]}]]
    (-> db
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :show?] false))))

(rf/reg-event-db
  ::add-scholarship-refetch-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-scholarship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :error] "Failed to add scholarship"))))

(rf/reg-event-db
  ::add-scholarship-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-scholarship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-scholarship :error] "Failed to add scholarship"))))

;; Add Custom Apprenticeship Modal
(rf/reg-event-db
  ::open-add-apprenticeship-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :show?] true)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :error] nil))))

(rf/reg-event-db
  ::close-add-apprenticeship-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :show?] false)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :error] nil))))

(rf/reg-event-fx
  ::submit-add-apprenticeship
  (fn [{:keys [db]} [_ form-data api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :modals :add-apprenticeship :submitting?] true)
               (assoc-in [:advisor :recommendations :api-client] api-client))
       ::effects/add-custom-apprenticeship
       {:student-id student-id
        :pool-id pool-id
        :apprenticeship form-data
        :api-client api-client
        :on-success [::add-apprenticeship-success]
        :on-failure [::add-apprenticeship-failure]}})))

(rf/reg-event-fx
  ::add-apprenticeship-success
  (fn [{:keys [db]} [_ _result]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          api-client (get-in db [:advisor :recommendations :api-client])]
      {:db db
       ::effects/load-recommendations
       {:student-id student-id
        :api-client api-client
        :on-success [::add-apprenticeship-refetch-complete]
        :on-failure [::add-apprenticeship-refetch-failure]}})))

(rf/reg-event-db
  ::add-apprenticeship-refetch-complete
  (fn [db [_ {:keys [student pool pool-id selection advisor-message]}]]
    (-> db
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :show?] false))))

(rf/reg-event-db
  ::add-apprenticeship-refetch-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :error] "Failed to add apprenticeship"))))

(rf/reg-event-db
  ::add-apprenticeship-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-apprenticeship :error] "Failed to add apprenticeship"))))

;; Add Custom Short-Term Program Modal
(rf/reg-event-db
  ::open-add-short-term-program-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :show?] true)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :error] nil))))

(rf/reg-event-db
  ::close-add-short-term-program-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :show?] false)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :error] nil))))

(rf/reg-event-fx
  ::submit-add-short-term-program
  (fn [{:keys [db]} [_ form-data api-client]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          pool-id (get-in db [:advisor :recommendations :pool-id])]
      {:db (-> db
               (assoc-in [:advisor :recommendations :modals :add-short-term-program :submitting?] true)
               (assoc-in [:advisor :recommendations :api-client] api-client))
       ::effects/add-custom-short-term-program
       {:student-id student-id
        :pool-id pool-id
        :short-term-program form-data
        :api-client api-client
        :on-success [::add-short-term-program-success]
        :on-failure [::add-short-term-program-failure]}})))

(rf/reg-event-fx
  ::add-short-term-program-success
  (fn [{:keys [db]} [_ _result]]
    (let [student-id (get-in db [:advisor :recommendations :student-id])
          api-client (get-in db [:advisor :recommendations :api-client])]
      {:db db
       ::effects/load-recommendations
       {:student-id student-id
        :api-client api-client
        :on-success [::add-short-term-program-refetch-complete]
        :on-failure [::add-short-term-program-refetch-failure]}})))

(rf/reg-event-db
  ::add-short-term-program-refetch-complete
  (fn [db [_ {:keys [student pool pool-id selection advisor-message]}]]
    (-> db
        (assoc-in [:advisor :recommendations :student] student)
        (assoc-in [:advisor :recommendations :pool] pool)
        (assoc-in [:advisor :recommendations :pool-id] pool-id)
        (assoc-in [:advisor :recommendations :selection] selection)
        (assoc-in [:advisor :recommendations :saved-selection] selection)
        (assoc-in [:advisor :recommendations :advisor-message] advisor-message)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :show?] false))))

(rf/reg-event-db
  ::add-short-term-program-refetch-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :error] "Failed to add short-term program"))))

(rf/reg-event-db
  ::add-short-term-program-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :submitting?] false)
        (assoc-in [:advisor :recommendations :modals :add-short-term-program :error] "Failed to add short-term program"))))


