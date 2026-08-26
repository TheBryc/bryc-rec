(ns config.core
  (:require [clojure.string :as str]))

(goog-define API_BASE_URL "http://localhost:8081")

(defn api-base-url []
  API_BASE_URL)

;; ---------------------------------------------------------------------------
;; Advisor-default authoring cutover flag (issue 0016 — S1; global-ON in 0022 — S6;
;; REVERTED to DEFAULT OFF in 0041). DEFAULT OFF: the advisor's recommendations EDIT
;; experience is the pre-existing DENSE editor (components.advisor.recommendations-page),
;; and the redesigned rec_demo view is the STUDENT preview + published student output
;; only (reached via the token-gated /student/recommendations route + the dense editor's
;; "Share with Student" link). The redesigned authoring read-through is RETAINED behind
;; this flag — build with -DADVISOR_AUTHORING_CUTOVER=on (or use the ?authoring=on URL
;; override) to preview/opt in to the authoring view; ?authoring=off forces the dense
;; editor. Both are compile-time closure-defines so the flip is per-environment/per-tenant
;; without a code change.
;; ---------------------------------------------------------------------------

(goog-define ADVISOR_AUTHORING_CUTOVER "off")          ;; "on" | "off" — global default (0041: OFF → dense editor)
(goog-define ADVISOR_AUTHORING_CUTOVER_TENANTS "")      ;; comma-separated tenant allowlist

(defn advisor-authoring-cutover-config
  "Reads the compile-time cutover closure-defines into the pure-predicate config shape
   {:default? bool :tenants #{tenant-id …}}. DEFAULT OFF (0041 revert) — :default? is on
   only when ADVISOR_AUTHORING_CUTOVER is explicitly built as \"on\" (opt in to the
   redesigned authoring view; the dense editor is otherwise the advisor edit surface)."
  []
  {:default? (= "on" ADVISOR_AUTHORING_CUTOVER)
   :tenants  (into #{} (remove str/blank?) (str/split ADVISOR_AUTHORING_CUTOVER_TENANTS #","))})