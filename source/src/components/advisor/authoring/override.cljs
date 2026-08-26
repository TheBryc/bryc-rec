(ns components.advisor.authoring.override
  "PURE prose / tile-value override support for the advisor-default authoring view
   (issue 0019 — S3). Data→data only: no effects, no components, no re-frame. Unit-
   tested through its public fns and reused by the authoring view so that editing ANY
   narrative prose OR overriding ANY headline tile value goes through the EXISTING
   save-recommendation-customization → :text-edits/:section-edits → apply-ref-overrides
   path, applied to the REAL selection refs (never a fork of the override logic).

   These set/revert fns MIRROR the backend `apply-ref-overrides` branches
   (student_ops_service.core.queries):
     :text-edits    — a FLAT top-level field override on the record (prose vectors like
                      :about-school-narrative / :costs-narrative; top-level values). The
                      advisor's final word, merged last.
     :section-edits — {section-data-key {field val}} DEEP-MERGED one level into the
                      named section data map (a headline tile value like
                      {:grad-rate {:rate \"72%\"}}, or section-local prose like
                      {:overview {:student-connection \"…\"}}).

   The sourced pool record is NEVER mutated — all overrides live on the ref (the
   customization trail). DROPPING an override (revert) leaves the ref byte-identical to
   a never-overridden one, so the sourced/computed value returns on the next resolve
   (exact revert-to-source), exactly like `hide/toggle-hidden` does for :hidden-sections."
  (:require [clojure.string :as str]))

;; ===========================================================================
;; Read a ref's override submaps (for the authoring context read-back)
;; ===========================================================================

(defn overrides
  "PURE: the override submaps carried on a ref — just :text-edits + :section-edits.
   nil-safe. The authoring view hands the returned map to the predicates below to
   decide whether a block shows its amber override ring + Revert-to-source chip."
  [ref]
  (select-keys ref [:text-edits :section-edits]))

;; ===========================================================================
;; :text-edits — top-level field overrides (prose vectors / top-level values)
;; ===========================================================================

(defn text-edited?
  "PURE: is top-level key `k` overridden on this ref (via :text-edits)?"
  [ref k]
  (contains? (:text-edits ref) k))

(defn set-text-edit
  "PURE: record a top-level override `k`→`value` on the ref's :text-edits (adds the
   :text-edits map when absent). Used for whole-narrative prose (e.g.
   :about-school-narrative / :costs-narrative)."
  [ref k value]
  (assoc-in ref [:text-edits k] value))

(defn revert-text-edit
  "PURE: DROP the :text-edits override for `k`. When it was the last text-edit, the
   :text-edits key is dissoc'd entirely so the ref is byte-identical to a never-edited
   one (revert-to-source is exact — the sourced top-level value is restored)."
  [ref k]
  (let [nxt (dissoc (:text-edits ref) k)]
    (if (seq nxt)
      (assoc ref :text-edits nxt)
      (dissoc ref :text-edits))))

;; ===========================================================================
;; :section-edits — {section-key {field val}} deep-merged one level into a section
;; ===========================================================================

(defn section-edited?
  "PURE: is `field` in named section `section-key` overridden on this ref?"
  [ref section-key field]
  (contains? (get-in ref [:section-edits section-key]) field))

(defn set-section-edit
  "PURE: record a per-field override for `section-key`/`field`→`value` on the ref's
   :section-edits (creating the nesting when absent). Deep-merges one level, so a
   sibling field in the same section — and sibling keys in the sourced section map —
   survive. Used for a headline tile value ({:grad-rate {:rate v}}) or section-local
   prose ({:overview {:student-connection v}})."
  [ref section-key field value]
  (assoc-in ref [:section-edits section-key field] value))

(defn revert-section-edit
  "PURE: DROP the section-edit for `section-key`/`field`. When it was the last field in
   that section, the section key is dropped; when that was the last section, the
   :section-edits key is dissoc'd entirely (revert-to-source is exact — the sourced
   tile/prose value is restored)."
  [ref section-key field]
  (let [inner (dissoc (get-in ref [:section-edits section-key]) field)
        se    (if (seq inner)
                (assoc (:section-edits ref) section-key inner)
                (dissoc (:section-edits ref) section-key))]
    (if (seq se)
      (assoc ref :section-edits se)
      (dissoc ref :section-edits))))

;; ===========================================================================
;; Read the EFFECTIVE (working, not-yet-reloaded) override value on a ref
;; ===========================================================================

(defn effective-text
  "PURE: the working :text-edits value for `k` on the ref if one is set, else `sourced`.
   Lets the authoring view render the advisor's just-saved prose immediately (WYSIWYG
   from the working selection) before the next re-resolve refreshes :resolved."
  [ref k sourced]
  (if (text-edited? ref k) (get-in ref [:text-edits k]) sourced))

(defn effective-section
  "PURE: the working :section-edits value for `section-key`/`field` on the ref if set,
   else `sourced` — the immediate WYSIWYG value for a just-overridden tile / prose field."
  [ref section-key field sourced]
  (if (section-edited? ref section-key field)
    (get-in ref [:section-edits section-key field])
    sourced))

;; ===========================================================================
;; apply-overrides — frontend mirror of the backend apply-ref-overrides prose/tile
;; branches (queries.clj): deep-merge :section-edits one level, then flat-merge
;; :text-edits on top. (Hidden sections are handled separately by hide/apply-hidden.)
;; Used to project a resolved record with the WORKING (session) overrides so the
;; Student-preview render matches EXACTLY what the backend resolve returns on reload.
;; ===========================================================================

(defn apply-overrides
  "PURE: apply a ref's working :section-edits (deep-merge one level) + :text-edits
   (flat top-level merge) onto a resolved `record`. A no-op when the ref carries
   neither, so the demo / student path (no ref) is untouched. nil-ref safe."
  [record ref]
  (let [with-sections (reduce-kv (fn [r k edits] (update r k merge edits))
                                 record (or (:section-edits ref) {}))]
    (if-let [te (:text-edits ref)]
      (merge with-sections te)
      with-sections)))

;; ===========================================================================
;; Narrative textarea seam — a bullet vector <-> a blank-line-separated string
;; ===========================================================================

(defn bullets->text
  "PURE: join a narrative bullet vector into one blank-line-separated string for the
   edit textarea. nil-safe (nil → \"\")."
  [bullets]
  (str/join "\n\n" (map str bullets)))

(defn text->bullets
  "PURE: split an edited textarea string back into a narrative bullet vector — one
   bullet per blank-line-separated paragraph, surrounding whitespace trimmed, empty
   paragraphs dropped. nil-safe (nil → [])."
  [s]
  (->> (str/split (str s) #"\n\n+")
       (map str/trim)
       (remove str/blank?)
       vec))
