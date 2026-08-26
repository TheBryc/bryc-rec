(ns rec-demo.student-harness
  "Capstone live-QA harness — the REAL token-gated student view rendered with REAL
   data, no API/token needed, for ALL saved pool events.

   Two modes, chosen by the `student` URL query param:
   - NO param (or unknown)  → an INDEX landing page: a clickable grid of every student
     (from `rec-demo.student-pools/students-index`), each linking to `?student=<slug>`.
   - `?student=<slug>`      → seeds the re-frame app-db the REAL subs read, then mounts
     the REAL `components.student.recommendations-page`, with a floating back-link to the index.

   Pools live in `rec-demo.student-pools/pools-edn` as PLAIN EDN STRINGS (generated from
   the real saved pool events, reader tags stringified), parsed at runtime with
   `cljs.reader/read-string` → `{:resolved <pool> :advisor-message <str> :name <str>}`."
  (:require [uix.core :refer [$ defui]]
            [uix.dom :as dom]
            [cljs.reader :as reader]
            [re-frame.core :as rf]
            [components.context.interface :as context]
            [components.student.recommendations-page :refer [recommendations-page]]
            [rec-demo.default-selection :refer [apply-default-selection]]
            [rec-demo.student-pools :as student-pools]))

(defn- query-param [param-name]
  (when-let [search (.-search js/window.location)]
    (.get (js/URLSearchParams. search) param-name)))

(defn- selected-key []
  (let [param (query-param "student")]
    (when (and param (contains? student-pools/pools-edn param)) param)))

(defn- selected-pool [k]
  (reader/read-string (get student-pools/pools-edn k)))

;; ---------------------------------------------------------------------------
;; Index landing page
;; ---------------------------------------------------------------------------

(defui student-card [{:keys [s]}]
  (let [{:keys [slug name gpa act track career race]} s]
    ($ :a {:href (str "?student=" slug)
           :class "block rounded-2xl bg-white border border-slate-200 p-5 shadow-sm hover:shadow-md hover:border-[#2a6465] transition no-underline"}
       ($ :div {:class "flex items-start justify-between gap-2 mb-2"}
          ($ :div {:class "text-lg font-semibold text-slate-800"} name)
          ($ :span {:class "shrink-0 text-xs font-medium px-2 py-1 rounded-full bg-[#2a6465]/10 text-[#2a6465]"} track))
       ($ :div {:class "text-sm text-slate-500 mb-1"} (str "GPA " gpa " · ACT " act))
       ($ :div {:class "text-sm font-medium text-slate-700"} career)
       ($ :div {:class "text-xs text-slate-400 mt-2"} race))))

(defui index-page []
  ($ :div {:class "min-h-screen bg-gradient-to-br from-blue-50 via-indigo-50 to-purple-50"}
     ($ :div {:class "bg-[#2a6465] px-6 md:px-8 py-5"}
        ($ :h1 {:class "text-white text-xl md:text-2xl font-bold"} "BRYC — Student Recommendation Profiles")
        ($ :p {:class "text-white/80 text-sm mt-1"} "Click a student to open their personalized recommendation view."))
     ($ :div {:class "max-w-[1100px] mx-auto p-6 md:p-8"}
        ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"}
           (for [s student-pools/students-index]
             ($ student-card {:key (:slug s) :s s}))))))

(defui back-link []
  ($ :a {:href "?"
         :class "fixed top-3 right-3 z-50 text-xs font-medium px-3 py-2 rounded-full bg-white/90 border border-slate-200 shadow-sm text-slate-700 hover:text-[#2a6465] no-underline"}
     "← All students"))

;; ---------------------------------------------------------------------------
;; Seed + mount
;; ---------------------------------------------------------------------------

(rf/reg-event-db ::seed (fn [_ [_ data]] {:student {:recommendations data}}))

(defonce root (dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  (dom/render-root
   (if-let [k (selected-key)]
     ($ context/app-provider {:context {:api/client nil :dev/show-banner false}}
        ($ :<> {} ($ back-link) ($ recommendations-page {})))
     ($ index-page {}))
   root))

(defn init []
  (when-let [k (selected-key)]
    (let [{:keys [resolved advisor-message name]} (selected-pool k)]
      (rf/dispatch-sync [::seed {;; Match REALITY: the real app renders the engine's
                                 ;; pre-selected default (top-3 institutions per tier,
                                 ;; top-2 programs each, top-5 scholarships/short-term/
                                 ;; apprenticeships) — NOT the full curated pool. Apply
                                 ;; that PURE trim at seed time so the demo stops
                                 ;; over-showing.
                                 :resolved (apply-default-selection
                                            (cond-> resolved
                                              name (assoc-in [:student :name] name)))
                                 :advisor-message advisor-message
                                 :loading? false
                                 :error nil}])))
  (render))
