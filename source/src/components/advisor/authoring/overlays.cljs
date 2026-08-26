(ns components.advisor.authoring.overlays
  "PURE overlay + cutover-flag logic for the advisor-default authoring read-through
   (issue 0016 — S1). No effects, no components — data→data only, so it is unit-tested
   through its public fns and reused by the read-only authoring view.

   Two concerns:
     • CUTOVER FLAG — a per-tenant-scopeable predicate, DEFAULT OFF. When OFF the old
       advisor UI still shows; when ON the redesigned rec_demo presentation renders.
     • ADVISOR-ONLY OVERLAYS the student view hides — a SOURCE badge (AI-generated vs
       advisor-added), a CUSTOMIZED chip (an AI item the advisor overrode), and a
       STALENESS banner (from the advisor query's recommendation-status). All are
       present-by-data — nothing is invented for a record that lacks the signal.")

;; ===========================================================================
;; Cutover flag — per-tenant-scopeable, DEFAULT OFF
;; ===========================================================================

(defn authoring-cutover-enabled?
  "PURE cutover predicate — DEFAULT OFF. Given the cutover `config`
   {:default? bool :tenants #{tenant-id …}} and a `tenant-id`, the redesigned advisor
   authoring view is enabled when the GLOBAL default is on OR the tenant is in the
   per-tenant allowlist. A nil/empty config, or a tenant not on the list with the
   default off, → OFF (old UI shows)."
  [config tenant-id]
  (boolean
    (and config
         (or (:default? config)
             (and (some? tenant-id)
                  (contains? (set (:tenants config)) tenant-id))))))

(defn cutover-with-override
  "PURE: resolve the effective cutover decision, layering an explicit URL `override`
   (\"on\" | \"off\" | nil/blank) over the config predicate. Issue 0022 — S6 flips the
   config DEFAULT to global-ON, so the URL becomes a symmetric QA/dev affordance:
     • \"off\" → the KILL-SWITCH — forces the OLD advisor UI even when the default is ON;
     • \"on\"  → forces the authoring view (preview an off tenant);
     • otherwise → authoring-cutover-enabled? (the config default) decides.
   The impure glue (view/cutover-enabled?) just reads the URL param + config and calls
   this, keeping the decision unit-testable."
  [override config tenant-id]
  (cond
    (= "off" override) false
    (= "on" override)  true
    :else              (authoring-cutover-enabled? config tenant-id)))

;; ===========================================================================
;; Source provenance badge (AI-generated vs advisor-added)
;; ===========================================================================

(defn advisor-added?
  "PURE: is this recommendation item advisor-added (custom), not AI-generated? True
   only when the item's :source is :custom (name-coerced so \"custom\" or :custom both
   resolve). A missing :source (or :ai) → not advisor-added."
  [item]
  (= "custom" (some-> (:source item) name)))

(defn source-badge
  "PURE: item → source badge descriptor {:key :label :advisor-added?}. An item with
   :source :custom → Advisor-Added; anything else (incl. :ai / missing) → AI-Generated.
   Present for EVERY record type (institution / program / scholarship / apprenticeship
   / short-term) since it reads only the shared :source field."
  [item]
  (if (advisor-added? item)
    {:key :advisor-added :label "Advisor-Added" :advisor-added? true}
    {:key :ai-generated  :label "AI-Generated"  :advisor-added? false}))

;; ===========================================================================
;; Customized indicator
;; ===========================================================================

(defn customized?
  "PURE: has the advisor customized THIS item's content? True when the item carries an
   explicit customization flag (:is-customized?/:customized?), OR non-empty advisor
   text overrides (:text-edits), OR is advisor-added (:source :custom). Tolerant across
   the possible engine signals (present-by-data)."
  [item]
  (boolean
    (or (:is-customized? item)
        (:customized? item)
        (seq (:text-edits item))
        (advisor-added? item))))

(defn customized-chip
  "PURE: the advisor-only 'Customized' chip for an item, or nil (present-by-data).
   Shown only for an AI-generated item the advisor has OVERRIDDEN — an advisor-ADDED
   item is already flagged by its Advisor-Added source badge, so it gets no redundant
   chip."
  [item]
  (when (and (customized? item) (not (advisor-added? item)))
    {:key :customized :label "Customized"}))

;; ===========================================================================
;; Legacy-pool migration (0021, ADR 0005) — needs re-projection?
;; ===========================================================================

(defn old-shape-record?
  "PURE: is a resolved recommendation record still OLD-shape — i.e. missing the
   present-by-data `:sections` vector the redesign renders from? A re-projected (new-shape)
   record always carries `:sections` (possibly empty), so this is false for it."
  [record]
  (not (contains? record :sections)))

(defn needs-migration?
  "PURE: does this student's RESOLVED recommendation set need a legacy re-projection
   migration (0021)? True when ANY presented record (college institution or its nested
   program, short-term program, or apprenticeship) is still old-shape (no `:sections`).
   Scholarships are never presented with `:sections`, so they are ignored. nil-safe → an
   absent/empty resolved set does NOT trigger migration."
  [resolved]
  (let [colleges (concat (get-in resolved [:institutions :safety] [])
                         (get-in resolved [:institutions :target] [])
                         (get-in resolved [:institutions :reach] []))
        records (concat colleges
                        (mapcat #(:programs % []) colleges)
                        (get-in resolved [:short-term-programs] [])
                        (get-in resolved [:apprenticeships] []))]
    (boolean (some old-shape-record? records))))

;; ===========================================================================
;; Staleness (from the advisor query's recommendation-status)
;; ===========================================================================

(defn staleness
  "PURE: recommendation-status → the advisor-only staleness overlay, or nil when the
   set is fresh (present-by-data). :generation-in-progress? → {:state :generating};
   :needs-regeneration? → {:state :stale} carrying :last-generated-at + :change-count
   when the status supplies them."
  [rec-status]
  (cond
    (nil? rec-status) nil

    (:generation-in-progress? rec-status)
    {:state :generating :label "Generating…"}

    (:needs-regeneration? rec-status)
    (let [changes (:changes rec-status)]
      (cond-> {:state :stale
               :label "Recommendations need update"}
        (:last-generated-at rec-status) (assoc :last-generated-at (:last-generated-at rec-status))
        (seq changes)                   (assoc :change-count (count changes))))

    :else nil))
