(ns components.shared.str-groups
  "Shared PURE STR-group display helpers — the ONE definition of how a college
   institution maps to its S·T·R display group, reused by BOTH the student viewer
   (components.student.recommendations-page) and the advisor dense editor
   (components.advisor.college-table-panel) so advisor + student always read the
   IDENTICAL Safety/Target/Reach categories (PRD 0008 / issue 0062).

   The pool's :institutions :safety/:target/:reach buckets group by ADMISSION
   DIFFICULTY relative to the student (acceptance-inclusive verdict); open-admission
   schools (2-year / no ACT, carrying :str-badge {:key \"open\"}) land inside Safety.
   For DISPLAY we re-group ALL collected institutions by each institution's OWN
   :str-badge :key into the fixed order Open Admission → Safety → Target → Reach —
   never mutating the pool buckets. DISPLAY re-grouping only.")

(def str-group-order
  "Fixed top→bottom display order for the college STR groups."
  ["open" "safety" "target" "reach"])

(def str-group-labels
  "STR group :key → the section heading shown above that group's cards."
  {"open" "Open Admission"
   "safety" "Safety"
   "target" "Target"
   "reach" "Reach"})

(def ^:private str-group-set
  "The four valid display-group keys, for membership tests."
  (set str-group-order))

(defn str-group-key
  "PURE: the display-group key for ONE college institution. Prefer the school's
   OWN :str-badge :key (name-coerced so string OR keyword keys resolve) when it's
   one of the four display groups. :str-badge is OPTIONAL (the engine attaches it
   only when non-nil), so when it's nil/unknown FALL BACK to the record's own
   admission tier — :admissions-likelihood (\"Safety\"/\"Target\"/\"Reach\") — and
   failing that DEFAULT to \"safety\". This guarantees every institution maps to
   exactly one group and is NEVER silently dropped."
  [inst]
  (let [badge (some-> inst :str-badge :key name)]
    (if (contains? str-group-set badge)
      badge
      (let [tier (some-> inst :admissions-likelihood name clojure.string/lower-case)]
        (if (contains? str-group-set tier)
          tier
          "safety")))))

(defn group-institutions-by-str
  "PURE: re-group a flat seq of college institutions into the fixed display order
   Open Admission → Safety → Target → Reach. Each institution's group is its OWN
   :str-badge :key, falling back to its :admissions-likelihood tier then \"safety\"
   (see `str-group-key`) so NO institution is ever dropped — every input lands in
   exactly one output group. Returns an ordered vector of [key insts] pairs,
   omitting any empty group (present-by-data). group-by preserves input order within
   each group, so a score-sorted (or bucket-ordered) input stays ordered per group.
   DISPLAY re-grouping only — the pool buckets are untouched."
  [insts]
  (let [by-key (group-by str-group-key insts)]
    (into []
          (keep (fn [k]
                  (when-let [g (seq (get by-key k))]
                    [k (vec g)])))
          str-group-order)))
