(ns store.advisor.recommendations.subs
  "Re-frame subscriptions for recommendations state"
  (:require [re-frame.core :as rf]
            [components.advisor.authoring.selection :as sel]))

;; ============================================================================
;; Phase 1: Basic Subscriptions
;; ============================================================================

;; Student summary (from screen query)
(rf/reg-sub
  ::student
  (fn [db _]
    (get-in db [:advisor :recommendations :student])))

;; Recommendation status (staleness / generation progress)
(rf/reg-sub
  ::recommendation-status
  (fn [db _]
    (get-in db [:advisor :recommendations :recommendation-status])))

(rf/reg-sub
  ::regenerating-recommendations?
  :<- [::recommendation-status]
  (fn [rec-status _]
    (:generation-in-progress? rec-status)))

;; Loading state
(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :recommendations :loading?] true)))

;; Error state
(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :recommendations :error])))

;; Pool data (immutable from backend)
(rf/reg-sub
  ::pool
  (fn [db _]
    (get-in db [:advisor :recommendations :pool])))

;; Resolved recommendations (selection applied to the pool) — the SAME :resolved the
;; student query returns, used by the advisor authoring read-through (0016). WYSIWYG:
;; the advisor sees the redesigned rec_demo presentation the student will see.
(rf/reg-sub
  ::resolved
  (fn [db _]
    (get-in db [:advisor :recommendations :resolved])))

;; Pool ID
(rf/reg-sub
  ::pool-id
  (fn [db _]
    (get-in db [:advisor :recommendations :pool-id])))

;; Current selection (working state with changes)
(rf/reg-sub
  ::selection
  (fn [db _]
    (get-in db [:advisor :recommendations :selection])))

;; Saved selection (last persisted state)
(rf/reg-sub
  ::saved-selection
  (fn [db _]
    (get-in db [:advisor :recommendations :saved-selection])))

;; Legacy-pool migration (0021) — is a re-projection migration in flight?
(rf/reg-sub
  ::migrating?
  (fn [db _]
    (get-in db [:advisor :recommendations :migrating?] false)))

;; Has the lazy migration already been attempted this session? (guards a re-fire loop)
(rf/reg-sub
  ::migration-attempted?
  (fn [db _]
    (get-in db [:advisor :recommendations :migration-attempted?] false)))

;; Active tab
(rf/reg-sub
  ::active-tab
  (fn [db _]
    (get-in db [:advisor :recommendations :active-tab] "colleges")))

;; Saving state
(rf/reg-sub
  ::saving?
  (fn [db _]
    (get-in db [:advisor :recommendations :saving?] false)))

;; Derived: Has changes?
(rf/reg-sub
  ::has-changes?
  :<- [::selection]
  :<- [::saved-selection]
  (fn [[selection saved-selection] _]
    (and selection
         saved-selection
         (not= selection saved-selection))))

;; Advisor identity (0059) — the advisee's assigned advisor {:name :position :email
;; :scheduling-url :headshot-url}, resolved server-side from the SAME "advises"
;; relationship the token student view uses (so the in-editor Student preview shows
;; the identical advisor card). Present-by-data: nil when the student has no active
;; advisor (card omitted).
(rf/reg-sub
  ::advisor
  (fn [db _]
    (get-in db [:advisor :recommendations :advisor])))

;; Advisor message (from selection override if exists, else from pool/backend default)
(rf/reg-sub
  ::advisor-message
  (fn [db _]
    (or (get-in db [:advisor :recommendations :selection :advisor-message])
        (get-in db [:advisor :recommendations :advisor-message]))))

;; ============================================================================
;; Card State Subscriptions (used by student view card components)
;; ============================================================================

;; Check if a card is expanded
(rf/reg-sub
  ::card-expanded?
  (fn [db [_ card-path]]
    (contains? (get-in db [:advisor :recommendations :card-state :expanded] #{}) card-path)))

;; ============================================================================
;; Detail Sheet Subscriptions
;; ============================================================================

(rf/reg-sub
  ::detail-sheet
  (fn [db _]
    (get-in db [:advisor :recommendations :detail-sheet])))

(rf/reg-sub
  ::detail-sheet-open?
  :<- [::detail-sheet]
  (fn [detail-sheet _]
    (some? detail-sheet)))

;; Program Detail Sheet (secondary left sheet)
(rf/reg-sub
  ::program-detail-sheet
  (fn [db _]
    (get-in db [:advisor :recommendations :program-detail-sheet])))

(rf/reg-sub
  ::program-detail-sheet-open?
  :<- [::program-detail-sheet]
  (fn [detail-sheet _]
    (some? detail-sheet)))

;; Unselected institutions per category (pool items not selected in ANY category) —
;; the 'available to promote' set the advisor authoring Edit view renders. Delegates to
;; the PURE, unit-tested sel/unselected-institutions so the sub, the view, and the tests
;; share ONE definition of "the FULL pool minus the current selection".
(rf/reg-sub
  ::unselected-institutions
  :<- [::pool]
  :<- [::selection]
  (fn [[pool selection] _]
    (sel/unselected-institutions pool selection)))

;; ============================================================================
;; Autosave Subscriptions
;; ============================================================================

;; Save status ("idle" | "pending" | "saving" | "saved" | "error")
(rf/reg-sub
  ::save-status
  (fn [db _]
    (get-in db [:advisor :recommendations :save-status] "idle")))

;; Check if a card has pending autosave
(rf/reg-sub
  ::card-autosave-pending?
  (fn [db [_ card-path]]
    (contains? (get-in db [:advisor :recommendations :autosave-pending] #{}) card-path)))

;; Check if there are any unsaved text edits (for UI indicator)
(rf/reg-sub
  ::has-unsaved-text-edits?
  :<- [::save-status]
  (fn [save-status _]
    (contains? #{"pending" "saving"} save-status)))

;; ============================================================================
;; Share with Student Subscriptions
;; ============================================================================

;; Generating link state
(rf/reg-sub
  ::generating-link?
  (fn [db _]
    (get-in db [:advisor :recommendations :generating-link?] false)))

;; Student view link
(rf/reg-sub
  ::student-view-link
  (fn [db _]
    (get-in db [:advisor :recommendations :student-view-link])))

;; Show link modal
(rf/reg-sub
  ::show-link-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :show-link-modal?] false)))

;; ============================================================================
;; Custom Item Modal Subscriptions
;; ============================================================================

;; Add Institution Modal
(rf/reg-sub
  ::show-add-institution-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-institution :show?] false)))

(rf/reg-sub
  ::add-institution-submitting?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-institution :submitting?] false)))

(rf/reg-sub
  ::add-institution-category
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-institution :category])))

(rf/reg-sub
  ::add-institution-error
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-institution :error])))

;; Add Program Modal
(rf/reg-sub
  ::show-add-program-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-program :show?] false)))

(rf/reg-sub
  ::add-program-submitting?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-program :submitting?] false)))

(rf/reg-sub
  ::add-program-institution-context
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-program :institution-context])))

(rf/reg-sub
  ::add-program-error
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-program :error])))

;; ============================================================================
;; Add Custom Scholarship Modal Subscriptions
;; ============================================================================

(rf/reg-sub
  ::show-add-scholarship-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-scholarship :show?] false)))

(rf/reg-sub
  ::add-scholarship-submitting?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-scholarship :submitting?] false)))

(rf/reg-sub
  ::add-scholarship-error
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-scholarship :error])))

;; ============================================================================
;; Add Custom Apprenticeship Modal Subscriptions
;; ============================================================================

(rf/reg-sub
  ::show-add-apprenticeship-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-apprenticeship :show?] false)))

(rf/reg-sub
  ::add-apprenticeship-submitting?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-apprenticeship :submitting?] false)))

(rf/reg-sub
  ::add-apprenticeship-error
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-apprenticeship :error])))

;; ============================================================================
;; Add Short-Term Program Modal Subscriptions
;; ============================================================================

(rf/reg-sub
  ::show-add-short-term-program-modal?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-short-term-program :show?] false)))

(rf/reg-sub
  ::add-short-term-program-submitting?
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-short-term-program :submitting?] false)))

(rf/reg-sub
  ::add-short-term-program-error
  (fn [db _]
    (get-in db [:advisor :recommendations :modals :add-short-term-program :error])))

