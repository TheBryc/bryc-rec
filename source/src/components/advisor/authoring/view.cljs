(ns components.advisor.authoring.view
  "Issue 0016 — S1. The advisor-default recommendations AUTHORING read-through.

   Behind the per-tenant cutover flag (DEFAULT OFF), the advisor's recommendations view
   renders the SAME redesigned rec_demo section components the student sees — driven by
   the advisor store's already-present :resolved (selection applied to the pool, from the
   ONE recommendations-screen query; no re-fetch) — so the advisor authors WYSIWYG.

   Layered on top are the advisor-ONLY overlays the student view hides (all PURE, from
   components.advisor.authoring.overlays):
     • a SOURCE badge on every card (AI-Generated vs Advisor-Added),
     • a CUSTOMIZED chip on an AI item the advisor overrode,
     • a STALENESS banner from the advisor query's recommendation-status.

   READ-ONLY — NO edit affordances (add-custom / reorder / inline edit are S2/S3/S4).
   The old advisor UI, and the student view, are untouched."
  (:require [uix.core :as uix :refer [defui $ use-effect use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.rec-demo.core :as rec-demo]
            [components.student.recommendations-page :as student-recs]
            [components.advisor.authoring.overlays :as ov]
            [components.advisor.coming-soon-button :as csb]
            [components.advisor.authoring.selection :as sel]
            [components.advisor.authoring.hide :as hide]
            [components.advisor.authoring.override :as override]
            [components.advisor.add-custom-institution-modal :refer [add-custom-institution-modal]]
            [components.advisor.add-custom-program-modal :refer [add-custom-program-modal]]
            [components.advisor.add-custom-scholarship-modal :refer [add-custom-scholarship-modal]]
            [components.advisor.add-custom-apprenticeship-modal :refer [add-custom-apprenticeship-modal]]
            [components.advisor.add-custom-short-term-program-modal :refer [add-custom-short-term-program-modal]]
            [components.context.interface :as context]
            [config.core :as config]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.shared.loading-states :refer [loading-spinner error-banner]]
            [components.shared.affordability-callout :refer [affordability-callout]]
            [components.shared.terms-to-know :refer [terms-to-know]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/tabs" :as tabs]))

;; ===========================================================================
;; Cutover flag wiring (impure glue around the PURE predicate)
;; ===========================================================================

(defn- url-param [param-name]
  (when-let [search (some-> js/window .-location .-search)]
    (.get (js/URLSearchParams. search) param-name)))

(defn cutover-enabled?
  "Impure: is the advisor authoring cutover ON for this advisee/tenant? Reads the
   compile-time config (config/advisor-authoring-cutover-config — DEFAULT ON since the
   0022 S6 cutover, global default + per-tenant allowlist) and the `?authoring` URL
   override, then delegates to the PURE ov/cutover-with-override. The URL is a symmetric
   QA/dev affordance: `?authoring=off` is the kill-switch (old UI even when default ON),
   `?authoring=on` forces the authoring view. `tenant-id` is the advisee's tenant when
   known (present-by-data)."
  ([] (cutover-enabled? nil))
  ([tenant-id]
   (boolean
     (ov/cutover-with-override (url-param "authoring")
                               (config/advisor-authoring-cutover-config)
                               tenant-id))))

;; ===========================================================================
;; Overlay UI primitives (advisor-only)
;; ===========================================================================

(defui source-pill [{:keys [badge]}]
  (let [advisor-added? (:advisor-added? badge)]
    ($ :span {:class (str "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold "
                          (if advisor-added?
                            "bg-[#e8f0ff] text-[#1d4ed8] ring-1 ring-[#c3d6ff]"
                            "bg-[#eef1f2] text-[#4b5563] ring-1 ring-[#d7dde0]"))
              :data-overlay "source-badge"
              :data-source (name (:key badge))}
       (:label badge))))

(defui customized-pill [{:keys [chip]}]
  ($ :span {:class "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold bg-[#fff7e6] text-[#9a6a00] ring-1 ring-[#e8a93b]"
            :data-overlay "customized"}
     (:label chip)))

(defui overlay-strip
  "The advisor-only strip above a recommendation card: its source badge (always) and a
   Customized chip (AI items the advisor overrode). Read-only — informational only."
  [{:keys [item]}]
  (let [badge (ov/source-badge item)
        chip (ov/customized-chip item)]
    ($ :div {:class "flex flex-wrap items-center gap-2 mb-1.5" :data-overlay "strip"}
       ($ source-pill {:badge badge})
       (when chip ($ customized-pill {:chip chip})))))

;; ===========================================================================
;; Edit ⇄ Student-preview toggle (0018 — S2, the ratified Variant-C global switch)
;; ===========================================================================

(defui edit-mode-toggle
  "The ONE global switch that flips ALL advisor inline affordances: Edit (affordances
   visible — hide-a-block, etc.) ⇄ Student preview (the pixel-clean WYSIWYG the student
   receives). Pure presentational; the parent owns the edit-mode? state."
  [{:keys [edit-mode? on-edit on-preview]}]
  ($ :div {:class "inline-flex rounded-full bg-white ring-1 ring-[#a9d8de] p-1"
           :data-overlay "edit-mode-toggle" :data-edit-mode (str (boolean edit-mode?))}
     ($ :button {:class (str "px-4 py-1.5 rounded-full text-sm font-semibold transition-colors "
                             (if edit-mode? "bg-[#05a09c] text-white" "text-[#2a6465]"))
                 :data-action "edit-mode" :on-click on-edit}
        "✎ Edit")
     ($ :button {:class (str "px-4 py-1.5 rounded-full text-sm font-semibold transition-colors "
                             (if edit-mode? "text-[#2a6465]" "bg-[#2a6465] text-white"))
                 :data-action "preview-mode" :on-click on-preview}
        "👁 Student preview")))

;; ===========================================================================
;; Selection / reorder affordances (S4 — dispatch the EXISTING selection events)
;; ===========================================================================

(defui selection-controls
  "Advisor-only select/deselect + reorder controls for a card. PURE presentational —
   the enabled/disabled state comes from the PURE selection fns and every click
   dispatches an EXISTING position-based selection event (no forked logic). A
   deselected card stays visible (the WYSIWYG :resolved projection only refreshes on
   reload) but is dimmed + toggled so the change is legible immediately and reversible
   in-session."
  [{:keys [selected? can-up? can-down? on-toggle on-up on-down]}]
  ($ :div {:class "flex items-center gap-2 mb-1.5" :data-overlay "selection-controls"}
     ($ button/Button
        {:variant (if selected? "outline" "default") :size "sm" :class "text-xs h-7"
         :data-action "toggle-select" :data-selected (str (boolean selected?))
         :on-click on-toggle}
        (if selected? "Deselect" "Re-select"))
     ($ button/Button
        {:variant "ghost" :size "sm" :class "text-xs h-7 w-7 p-0" :disabled (not can-up?)
         :data-action "move-up" :title "Move up" :on-click on-up} "↑")
     ($ button/Button
        {:variant "ghost" :size "sm" :class "text-xs h-7 w-7 p-0" :disabled (not can-down?)
         :data-action "move-down" :title "Move down" :on-click on-down} "↓")))

(defui add-custom-button
  "Advisor-only '+ Add …' trigger — DISABLED pending scope agreement (Cameron:
   revert the add-custom workaround, disable with a 'Coming soon' hover). The
   :on-click prop is accepted and IGNORED so call sites stay untouched; the
   shared coming-soon-button renders a disabled button in a tooltip trigger."
  [{:keys [label]}]
  ($ csb/coming-soon-button {:label label}))

;; ===========================================================================
;; Promote from the full pool (Edit mode only). The whole pool already lives in the
;; store; the read-through renders only the resolved top-K. These SECONDARY, muted
;; "available to promote" affordances render the UNSELECTED pool (schools + a school's
;; unselected programs) so the advisor can show one to the student — each 'Show to
;; student' click dispatches the EXISTING ::toggle-*-selection SELECT branch (no new
;; event). Hidden in Student preview (the parent gates them behind edit-mode?).
;; ===========================================================================

(defui promote-institution-card
  "Compact 'available to promote' card for a pool school NOT in the current selection.
   'Show to student' dispatches the EXISTING ::toggle-institution-selection SELECT
   branch — the school joins the selection + autosaves. Muted/dashed 'add' styling so
   it reads as secondary to the selected cards."
  [{:keys [institution category api-client]}]
  (let [inst-id (:id institution)
        label (or (:institution-name institution) (:name institution) "Untitled school")]
    ($ :div {:class "flex items-center justify-between gap-3 rounded-xl border border-dashed border-[#c3d0d2] bg-[#f7fafa] px-4 py-2.5"
             :data-overlay "promote-institution" :data-inst-id (str inst-id)}
       ($ :span {:class "text-sm text-[#4b5563] truncate"} label)
       ($ button/Button
          {:variant "outline" :size "sm" :class "text-xs h-7 shrink-0"
           :data-action "promote-institution"
           :on-click #(rf/dispatch [::events/toggle-institution-selection category inst-id api-client])}
          "+ Show to student"))))

(defui promote-program-row
  "Compact promote control for a selected school's pool program NOT yet shown. 'Show to
   student' dispatches the EXISTING ::toggle-program-selection SELECT branch (promote
   WITHIN a school). Secondary/muted styling."
  [{:keys [program category institution-id api-client]}]
  (let [prog-id (:id program)
        label (or (:program-title program) (:name program) "Untitled program")]
    ($ :div {:class "flex items-center justify-between gap-3 rounded-lg border border-dashed border-[#d7dde0] bg-white/60 px-3 py-2"
             :data-overlay "promote-program" :data-prog-id (str prog-id)}
       ($ :span {:class "text-xs text-[#4b5563] truncate"} label)
       ($ button/Button
          {:variant "outline" :size "sm" :class "text-xs h-6 shrink-0"
           :data-action "promote-program"
           :on-click #(rf/dispatch [::events/toggle-program-selection category institution-id prog-id api-client])}
          "+ Show to student"))))

(defui promote-programs-block
  "The unselected pool programs for ONE selected school (its full pool :programs minus
   its selected program ids), each a promote control. Renders NOTHING when every pool
   program is already shown (present-by-data)."
  [{:keys [pool selection category institution-id api-client]}]
  (let [pool-inst (sel/find-pool-institution pool category institution-id)
        selected-ids (sel/selected-program-ids selection category institution-id)
        unsel (sel/unselected-programs (:programs pool-inst) selected-ids)]
    (when (seq unsel)
      ($ :div {:class "mt-2 space-y-1.5 pl-3 border-l-2 border-[#e5eaea]"
               :data-section "promote-programs" :data-inst-id (str institution-id)}
         ($ :div {:class "text-[11px] font-medium uppercase tracking-wide text-[#8a9294]"}
            "Available programs to promote")
         (for [p unsel]
           ($ promote-program-row {:key (:id p) :program p :category category
                                   :institution-id institution-id :api-client api-client}))))))

(defui promote-institutions-section
  "The 'Available to promote' section: the FULL pool minus the current selection,
   grouped by selection category (Safety/Target/Reach). Renders NOTHING when every pool
   school is already selected (present-by-data). Mirrors the old UI's 'Unselected Pool'."
  [{:keys [unselected-institutions api-client]}]
  (let [total (reduce + 0 (map (comp count val) (or unselected-institutions {})))]
    (when (pos? total)
      ($ :div {:class "space-y-3 pt-2" :data-section "promote-pool"}
         ($ :h3 {:class "text-sm font-semibold uppercase tracking-wide text-[#676868]"}
            "Available to promote")
         ($ :p {:class "text-xs text-[#8a9294]"}
            "Pool schools not currently shown to this student — promote one to add it to their recommendations.")
         (for [category sel/college-categories]
           (let [insts (get unselected-institutions category [])]
             (when (seq insts)
               ($ :div {:key (name category) :class "space-y-2"}
                  ($ :div {:class "text-xs font-medium text-[#8a9294]"}
                     (case category :safety "Safety" :target "Target" :reach "Reach" (name category)))
                  (for [inst insts]
                    ($ promote-institution-card {:key (:id inst) :institution inst
                                                 :category category :api-client api-client}))))))))))

(defui carded
  "Wraps a rec_demo card with its advisor-only overlay strip and (S4) its selection /
   reorder controls. A deselected card is dimmed to signal it will drop on reload.

   0018: `authoring?` (Edit mode) gates the advisor-only chrome — in Student preview it is
   false, so the strip/controls/dimming vanish and only the pixel-clean student card shows."
  [{:keys [item controls selected? authoring? children]}]
  (if authoring?
    ($ :div {:class (str "space-y-0 " (when (false? selected?) "opacity-50"))}
       ($ overlay-strip {:item item})
       controls
       children)
    children))


(defui staleness-banner [{:keys [rec-status]}]
  (when-let [{:keys [state label last-generated-at change-count]} (ov/staleness rec-status)]
    ($ :div {:class (str "rounded-xl p-4 ring-1 mb-6 "
                         (if (= state :generating)
                           "bg-amber-50 ring-amber-200 text-amber-900"
                           "bg-blue-50 ring-blue-200 text-blue-900"))
             :data-overlay "staleness" :data-state (name state)}
       ($ :div {:class "text-sm font-semibold"} label)
       (when last-generated-at
         ($ :div {:class "text-xs mt-1"} (str "Last generated: " last-generated-at)))
       (when change-count
         ($ :div {:class "text-xs mt-0.5"}
            (str change-count " change" (when (> change-count 1) "s") " since last generation"))))))

;; ===========================================================================
;; Read-through page
;; ===========================================================================

(defui authoring-recommendations-page
  "The advisor-default recommendations authoring read-through (behind the cutover flag).
   Loads via the SAME advisor recommendations-screen query as the old view (one query,
   no re-fetch), reads :resolved + recommendation-status, and renders the redesigned
   rec_demo cards with advisor-only overlays. READ-ONLY."
  [{:keys [current-match]}]
  (let [student-id (some-> (get-in current-match [:query-params :student-id]) parse-uuid)
        ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Edit ⇄ Student-preview mode (0018 — S2). Edit shows the inline advisor
        ;; affordances; Preview is the pixel-clean WYSIWYG artifact the student receives.
        [edit-mode? set-edit-mode!] (use-state true)

        loading? (use-subscribe [::subs/loading?])
        error (use-subscribe [::subs/error])
        resolved (use-subscribe [::subs/resolved])
        ;; The FULL immutable pool + the per-category unselected pool schools (pool minus
        ;; the current selection). Drives the Edit-mode 'available to promote' cards — the
        ;; advisor can show a non-selected pool school/program to the student. (Both nil in
        ;; the empty state; the promote UI is Edit-mode-only + present-by-data.)
        pool (use-subscribe [::subs/pool])
        unselected-insts (use-subscribe [::subs/unselected-institutions])
        ;; Legacy-pool migration (0021, ADR 0005) — a re-projection is in flight, and
        ;; whether we've already fired it this session (guards a re-fire loop).
        migrating? (use-subscribe [::subs/migrating?])
        migration-attempted? (use-subscribe [::subs/migration-attempted?])
        ;; Working selection model — drives the select/reorder controls (S4). Reorder
        ;; positions are read from HERE (the real selection), never the STR display
        ;; order, so the EXISTING position-based events act on the right refs.
        selection (use-subscribe [::subs/selection])
        advisor-message (use-subscribe [::subs/advisor-message])
        rec-status (use-subscribe [::subs/recommendation-status])

        ;; Hide-a-block wiring (0018 — S2). The hide overrides live on the working
        ;; selection refs (:hidden-sections); these PURE closures locate a resolved card's
        ;; ref by id and toggle it through the EXISTING autosave → customization event path.
        hidden-of (fn [record]
                    (let [id (:id record)]
                      (hide/hidden-sections
                        (or (:ref (hide/find-institution-ref selection id))
                            (:ref (hide/find-program-ref selection id))))))
        on-toggle-hide (fn [record section-key]
                         (let [id (:id record)]
                           (if-let [{:keys [category]} (hide/find-institution-ref selection id)]
                             (rf/dispatch [::events/toggle-institution-section-hidden
                                           category id section-key api-client])
                             (when-let [{:keys [category institution-id]}
                                        (hide/find-program-ref selection id)]
                               (rf/dispatch [::events/toggle-program-section-hidden
                                             category institution-id id section-key api-client])))))
        ;; Prose / tile-value override wiring (0019 — S3). The overrides live on the
        ;; SAME working selection refs as the hides (:text-edits / :section-edits); these
        ;; PURE closures locate a resolved card's ref by id (institution OR nested program)
        ;; and dispatch through the EXISTING autosave → customization event path, so a prose
        ;; edit / tile override round-trips through apply-ref-overrides and reverts to source.
        override-of (fn [record]
                      (let [id (:id record)]
                        (override/overrides
                          (or (:ref (hide/find-institution-ref selection id))
                              (:ref (hide/find-program-ref selection id))))))
        dispatch-override (fn [record inst-ev prog-ev]
                            ;; inst-ev / prog-ev are (fn [category inst-id]) / (fn [category
                            ;; institution-id prog-id]) → the [event-vector] to dispatch.
                            (let [id (:id record)]
                              (if-let [{:keys [category]} (hide/find-institution-ref selection id)]
                                (rf/dispatch (inst-ev category id))
                                (when-let [{:keys [category institution-id]}
                                           (hide/find-program-ref selection id)]
                                  (rf/dispatch (prog-ev category institution-id id))))))
        on-set-text (fn [record text-key value]
                      (dispatch-override record
                        (fn [cat iid] [::events/set-institution-text-edit cat iid text-key value api-client])
                        (fn [cat iid pid] [::events/set-program-text-edit cat iid pid text-key value api-client])))
        on-revert-text (fn [record text-key]
                         (dispatch-override record
                           (fn [cat iid] [::events/revert-institution-text-edit cat iid text-key api-client])
                           (fn [cat iid pid] [::events/revert-program-text-edit cat iid pid text-key api-client])))
        on-set-section (fn [record section-key field value]
                         (dispatch-override record
                           (fn [cat iid] [::events/set-institution-section-edit cat iid section-key field value api-client])
                           (fn [cat iid pid] [::events/set-program-section-edit cat iid pid section-key field value api-client])))
        on-revert-section (fn [record section-key field]
                            (dispatch-override record
                              (fn [cat iid] [::events/revert-institution-section-edit cat iid section-key field api-client])
                              (fn [cat iid pid] [::events/revert-program-section-edit cat iid pid section-key field api-client])))
        authoring-ctx {:edit-mode? edit-mode? :hidden-of hidden-of :on-toggle on-toggle-hide
                       :override-of override-of
                       :on-set-text on-set-text :on-revert-text on-revert-text
                       :on-set-section on-set-section :on-revert-section on-revert-section}
        ;; In Student preview, project each college the SAME way the backend
        ;; apply-ref-overrides will on reload — apply the working prose / tile overrides
        ;; (:text-edits / :section-edits) AND strip hidden School blocks + program sections
        ;; — so the preview is WYSIWYG from the working selection (not stale :resolved). In
        ;; Edit mode the full record renders (the inline editors + ghosts come from the
        ;; working selection, not a projected record).
        ref-of (fn [record]
                 (let [id (:id record)]
                   (or (:ref (hide/find-institution-ref selection id))
                       (:ref (hide/find-program-ref selection id)))))
        project-record (fn [record]
                         (-> record
                             (override/apply-overrides (ref-of record))
                             (hide/apply-hidden (hidden-of record))))
        project-college (fn [inst]
                          (if edit-mode?
                            inst
                            (-> (project-record inst)
                                (update :programs (fn [progs] (mapv project-record progs))))))

        student (:student resolved)
        student-name (:name student)
        ;; Branded-header title. In Student-preview the hero MUST read exactly like
        ;; the student recommendations page ("«Name»'s Personalized Recommendations",
        ;; generic fallback, NEVER a hardcoded name) — reusing the SAME pure fn. In
        ;; Edit mode we keep the shorter authoring heading.
        title (if edit-mode?
                (if (seq student-name)
                  (str student-name "'s Recommendations")
                  "Recommendations")
                (student-recs/personalized-title student-name))

        ;; Tag each resolved college with its selection bucket category so a card keeps
        ;; that category through the STR display re-grouping (needed to re-select a
        ;; deselected card, whose id is no longer in the selection).
        tag-cat (fn [insts category] (mapv #(assoc % ::resolved-category category) insts))
        safety (tag-cat (get-in resolved [:institutions :safety] []) :safety)
        target (tag-cat (get-in resolved [:institutions :target] []) :target)
        reach (tag-cat (get-in resolved [:institutions :reach] []) :reach)
        all-colleges (vec (concat safety target reach))
        apprenticeships (get-in resolved [:apprenticeships] [])
        short-term-programs (get-in resolved [:short-term-programs] [])
        scholarships (get-in resolved [:scholarships] [])

        ;; Slice 0033 — group by the engine 0032 :section (Bachelor's / Career-Technical),
        ;; with the UI-side fallback for an OLD pool (student-recs/institution-section).
        ;; Bachelor's = its institutions, STR re-grouped Open→Safety→Target→Reach. Career-
        ;; Technical = its institutions + all short-term programs + all apprenticeships,
        ;; interleaved in one ranked flow (Variant A). ::resolved-category rides on each
        ;; college so the select/reorder controls still act on the right bucket.
        bachelors-institutions (student-recs/institutions-in-section :bachelors all-colleges)
        career-technical-institutions (student-recs/institutions-in-section :career-technical all-colleges)
        bachelors-groups (student-recs/group-institutions-by-str bachelors-institutions)
        career-technical-items (student-recs/career-technical-flow career-technical-institutions
                                                                   short-term-programs
                                                                   apprenticeships)

        has-bachelors? (seq bachelors-institutions)
        has-career-technical? (seq career-technical-items)
        has-scholarships? (seq scholarships)
        available-tabs (cond-> []
                         has-bachelors? (conj "bachelors")
                         has-career-technical? (conj "career-technical")
                         has-scholarships? (conj "scholarships"))
        default-tab (first available-tabs)
        tab-count (count available-tabs)

        ;; Per-record card renderers (closures over the authoring wiring), reused by the
        ;; Bachelor's tab AND the interleaved Career-Technical flow so a school / short-term
        ;; program / apprenticeship keeps its correct source-badge + select/reorder controls
        ;; wherever it appears. Each COLLEGE card is individually wrapped in the authoring
        ;; context Provider so the hide-a-block / override affordances light up on schools
        ;; (institutions + programs) but NOT on apprenticeship / short-term cards (which can't
        ;; carry :hidden-sections) — mirroring the pre-0033 behavior where only the colleges
        ;; tab provided the context.
        ;; 0078: `career-technical?` (optional) — this shared renderer backs BOTH the
        ;; Bachelor's tab (no flag → S/T/R badge kept) AND the Career-Technical flow
        ;; (flag true → S/T/R badge suppressed per ADR 0008, Open-Admission may survive).
        render-college-card
        (fn render-college-card
          ([idx inst] (render-college-card idx inst false))
          ([idx inst career-technical?]
          (let [inst-id (:id inst)
                resolved-cat (::resolved-category inst)
                pos (sel/institution-selection-position selection inst-id)
                selected? (some? pos)
                position (:position pos)
                total (:count pos)]
            ($ :div {:key (or inst-id idx)}
             ($ (.-Provider rec-demo/authoring-context) {:value authoring-ctx}
               ($ carded {:item inst :selected? selected?
                          :authoring? edit-mode?
                          :controls
                          ($ selection-controls
                             {:selected? selected?
                              :can-up? (sel/can-move-up? position)
                              :can-down? (sel/can-move-down? position total)
                              :on-toggle #(rf/dispatch [::events/toggle-institution-selection resolved-cat inst-id api-client])
                              :on-up #(rf/dispatch [::events/move-institution-up (:category pos) position api-client])
                              :on-down #(rf/dispatch [::events/move-institution-down (:category pos) position api-client])})}
                  (when edit-mode?
                    ($ :div {:class "flex items-center justify-end mb-1"}
                       ($ add-custom-button
                          {:label "+ Add Program" :data-add "program"
                           :on-click #(rf/dispatch [::events/open-add-program-modal
                                                    {:institution-id inst-id
                                                     :institution-name (or (:institution-name inst) (:name inst))
                                                     :city (get-in inst [:location :city])
                                                     :state (get-in inst [:location :state])}])})))
                  ($ rec-demo/student-school-card {:institution (project-college inst) :student student
                                                   :career-technical? career-technical?})
                  (when (and edit-mode? selected?)
                    ($ promote-programs-block {:pool pool :selection selection
                                               :category (:category pos)
                                               :institution-id inst-id
                                               :api-client api-client}))))))))
        render-apprenticeship-card
        (fn [idx app]
          (let [app-id (:id app)
                pos (sel/list-selection-position (get-in selection [:apprenticeships] []) app-id)
                selected? (some? pos)]
            ($ carded {:key (or app-id idx) :item app :selected? selected?
                       :authoring? edit-mode?
                       :controls
                       ($ selection-controls
                          {:selected? selected?
                           :can-up? (sel/can-move-up? (:position pos))
                           :can-down? (sel/can-move-down? (:position pos) (:count pos))
                           :on-toggle #(rf/dispatch [::events/toggle-apprenticeship-selection app-id api-client])
                           :on-up #(rf/dispatch [::events/move-apprenticeship-up (:position pos) api-client])
                           :on-down #(rf/dispatch [::events/move-apprenticeship-down (:position pos) api-client])})}
               ($ rec-demo/short-term-card {:program app :student student}))))
        render-short-term-card
        (fn [idx prog]
          (let [prog-id (:id prog)
                pos (sel/list-selection-position (get-in selection [:short-term-programs] []) prog-id)
                selected? (some? pos)]
            ($ carded {:key (or prog-id idx) :item prog :selected? selected?
                       :authoring? edit-mode?
                       :controls
                       ($ selection-controls
                          {:selected? selected?
                           :can-up? (sel/can-move-up? (:position pos))
                           :can-down? (sel/can-move-down? (:position pos) (:count pos))
                           :on-toggle #(rf/dispatch [::events/toggle-short-term-program-selection prog-id api-client])
                           :on-up #(rf/dispatch [::events/move-short-term-program-up (:position pos) api-client])
                           :on-down #(rf/dispatch [::events/move-short-term-program-down (:position pos) api-client])})}
               ($ rec-demo/short-term-card {:program prog :student student}))))]

    ;; Load recommendations on mount — the SAME advisor store event/query the old view
    ;; uses (one recommendations-screen query, no separate re-fetch).
    (use-effect
     (fn []
       (when student-id
         (rf/dispatch [::events/load-recommendations student-id api-client]))
       js/undefined)
     [student-id api-client])

    ;; Legacy-pool migration (0021, ADR 0005) — LAZILY on first open in this new view:
    ;; when the loaded pool is still old-shape (a resolved record lacks :sections),
    ;; re-project it in place (ids preserved) + auto-migrate legacy :why-fits/:financial
    ;; advisor prose into the About/Costs narrative overrides. Fires at most once per
    ;; session (guarded), shows a loading state, and silently reloads on success.
    (use-effect
     (fn []
       (when (and resolved student-id api-client
                  (ov/needs-migration? resolved)
                  (not migrating?)
                  (not migration-attempted?))
         (rf/dispatch [::events/migrate-recommendations student-id api-client]))
       js/undefined)
     [resolved student-id api-client migrating? migration-attempted?])

    ($ :div {:class "flex flex-col min-h-screen bg-[#f2f3f4]" :data-view "advisor-authoring"}
       ;; Branded header
       ($ :div {:class "bg-[#2a6465] shadow-sm px-6 md:px-8 py-4 flex-shrink-0"}
          ($ :div {:class "flex items-center gap-3"}
             ($ :img {:src "/assets/bryc-logo-white.png" :alt "BRYC" :class "w-10 h-10 rounded-full"})
             ($ :h1 {:class "text-lg md:text-2xl font-bold text-white font-head"} title)))

       ($ :div {:class "flex-1 p-6 md:p-8"}
          (cond
            loading?
            ($ loading-spinner {:text "Loading recommendations..."})

            ;; Legacy-pool re-projection migration in flight (0021) — one-time upgrade.
            migrating?
            ($ loading-spinner {:text "Upgrading this student's recommendations to the new view..."})

            error
            ($ error-banner {:error error})

            (nil? resolved)
            ($ :div {:class "flex flex-col items-center justify-center py-20 gap-2"}
               ($ :h3 {:class "text-lg font-semibold text-[#2a6465]"} "No Recommendations Yet")
               ($ :p {:class "text-sm text-[#676868] text-center max-w-sm"}
                  "Personalized recommendations will appear here once they have been generated for this student."))

            :else
            ($ :div {:class "max-w-4xl mx-auto"}
               ;; Advisor-only staleness banner (hidden in Student preview; present-by-data,
               ;; so nothing renders when absent).
               (when edit-mode?
                 ($ staleness-banner {:rec-status rec-status}))

               ;; Global Edit ⇄ Student-preview switch (0018 — S2)
               ($ :div {:class "flex items-center justify-end mb-4"}
                  ($ edit-mode-toggle {:edit-mode? edit-mode?
                                       :on-edit #(set-edit-mode! true)
                                       :on-preview #(set-edit-mode! false)}))

               ;; Advisor message (read-only)
               (when advisor-message
                 ($ :div {:class "mb-6 bg-white rounded-2xl shadow-sm ring-1 ring-[#d0ecef] p-5 border-l-4 border-l-[#05a09c]"}
                    ($ :div {:class "text-xs font-semibold uppercase tracking-wide text-[#676868] mb-2"}
                       "Message to Student")
                    ($ :p {:class "text-sm md:text-base text-[#313335] leading-relaxed whitespace-pre-wrap"}
                       advisor-message)))

               ($ tabs/Tabs {:default-value (or default-tab "bachelors")}
                  ($ tabs/TabsList {:class (str "mb-6 grid w-full h-auto p-1 bg-white/70 rounded-2xl shadow-sm "
                                                (case tab-count
                                                  1 "grid-cols-1" 2 "grid-cols-2" 3 "grid-cols-3" "grid-cols-4"))}
                     (when has-bachelors?
                       ($ tabs/TabsTrigger {:value "bachelors" :class "py-2 px-3 rounded-xl text-sm"} "Bachelor's"))
                     (when has-career-technical?
                       ($ tabs/TabsTrigger {:value "career-technical" :class "py-2 px-3 rounded-xl text-sm"} "Career-Technical"))
                     (when has-scholarships?
                       ($ tabs/TabsTrigger {:value "scholarships" :class "py-2 px-3 rounded-xl text-sm"} "Scholarships")))

                  ;; Bachelor's — 4-year + transfer college pathways, STR-grouped Open →
                  ;; Safety → Target → Reach (reusing the student page's PURE re-grouping),
                  ;; each card wrapped with overlays + S4 select/reorder controls (+ its own
                  ;; authoring context Provider so hide-a-block / overrides work on schools).
                  ($ tabs/TabsContent {:value "bachelors" :class "space-y-6 pt-2"}
                     ;; Affordability & Financial Aid callout — SHARED with the student page,
                     ;; shown in BOTH Edit and Student-preview modes (WYSIWYG with the student).
                     ($ affordability-callout)
                     ($ rec-demo/str-legend)
                     (when edit-mode?
                       ($ :div {:class "flex flex-wrap items-center gap-2" :data-section "add-institution"}
                          ($ add-custom-button {:label "+ Add Safety School" :data-add "institution-safety"
                                                :on-click #(rf/dispatch [::events/open-add-institution-modal :safety])})
                          ($ add-custom-button {:label "+ Add Target School" :data-add "institution-target"
                                                :on-click #(rf/dispatch [::events/open-add-institution-modal :target])})
                          ($ add-custom-button {:label "+ Add Reach School" :data-add "institution-reach"
                                                :on-click #(rf/dispatch [::events/open-add-institution-modal :reach])})))
                     (for [[str-key insts] bachelors-groups]
                       ($ :div {:key str-key :class "space-y-3"}
                          ($ :h3 {:class "text-sm font-semibold uppercase tracking-wide text-[#676868]"}
                             (get student-recs/str-group-labels str-key str-key))
                          ($ :div {:class "space-y-5"}
                             (for [[idx inst] (map-indexed vector insts)]
                               (render-college-card idx inst)))))
                     ;; The 'Available to promote' pool section (Edit mode only; hidden in
                     ;; Student preview) — the FULL pool minus the current selection, so a
                     ;; non-selected pool school can be promoted to the student.
                     (when edit-mode?
                       ($ promote-institutions-section {:unselected-institutions unselected-insts
                                                        :api-client api-client})))

                  ;; Career-Technical — ONE interleaved "Pathway Recommendations" flow
                  ;; (Variant A): 2-year/terminal schools (each carrying its own STR /
                  ;; Open-Admission badge) + short-term programs + apprenticeships, in ranked
                  ;; order. Each item keeps its own source-badge + select/reorder controls via
                  ;; the shared render-*-card closures. The add-custom triggers for the two
                  ;; standalone tracks sit at the top (Edit mode).
                  ($ tabs/TabsContent {:value "career-technical" :class "space-y-6 pt-2"}
                     ($ affordability-callout)
                     ($ rec-demo/str-legend)
                     (when edit-mode?
                       ($ :div {:class "flex flex-wrap justify-end gap-2"}
                          ($ :div {:data-section "add-apprenticeship"}
                             ($ add-custom-button {:label "+ Add Apprenticeship" :data-add "apprenticeship"
                                                   :on-click #(rf/dispatch [::events/open-add-apprenticeship-modal])}))
                          ($ :div {:data-section "add-short-term-program"}
                             ($ add-custom-button {:label "+ Add Short-Term Program" :data-add "short-term-program"
                                                   :on-click #(rf/dispatch [::events/open-add-short-term-program-modal])}))))
                     ($ :div {:class "space-y-5"}
                        ($ rec-demo/section-eyebrow {:label "Pathway Recommendations"})
                        (for [[idx {:keys [kind record]}] (map-indexed vector career-technical-items)]
                          (case kind
                            :school         (render-college-card idx record true)
                            :apprenticeship (render-apprenticeship-card idx record)
                            (render-short-term-card idx record)))))

                  ($ tabs/TabsContent {:value "scholarships" :class "space-y-5"}
                     (when edit-mode?
                       ($ :div {:class "flex justify-end" :data-section "add-scholarship"}
                          ($ add-custom-button {:label "+ Add Scholarship" :data-add "scholarship"
                                                :on-click #(rf/dispatch [::events/open-add-scholarship-modal])})))
                     (for [[idx schol] (map-indexed vector scholarships)]
                       (let [schol-id (:id schol)
                             pos (sel/list-selection-position (get-in selection [:scholarships] []) schol-id)
                             selected? (some? pos)]
                         ($ carded {:key (or schol-id idx) :item schol :selected? selected?
                                    :authoring? edit-mode?
                                    :controls
                                    ($ selection-controls
                                       {:selected? selected?
                                        :can-up? (sel/can-move-up? (:position pos))
                                        :can-down? (sel/can-move-down? (:position pos) (:count pos))
                                        :on-toggle #(rf/dispatch [::events/toggle-scholarship-selection schol-id api-client])
                                        :on-up #(rf/dispatch [::events/move-scholarship-up (:position pos) api-client])
                                        :on-down #(rf/dispatch [::events/move-scholarship-down (:position pos) api-client])})}
                            ($ rec-demo/student-scholarship-card {:scholarship schol}))))))

               ;; Terms to Know — SHARED fixed glossary (same deterministic block
               ;; as the student page), rendered once at the bottom of the
               ;; recommendations content in BOTH Edit and Student-preview.
               ($ terms-to-know)

               ;; The EXISTING reusable add-custom modals (self-contained: each reads its
               ;; own show?/submitting?/error subs and dispatches its own submit/close +
               ;; refetch events). Rendered once; opened by the buttons above.
               ($ add-custom-institution-modal)
               ($ add-custom-program-modal)
               ($ add-custom-scholarship-modal)
               ($ add-custom-apprenticeship-modal)
               ($ add-custom-short-term-program-modal)))))))
