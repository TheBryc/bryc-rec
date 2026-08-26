(ns rec-demo.harness
  "Dev harness for the REAL-data Playwright render.

   UI-1: the school-anchored sections (About This School + What It Costs You) for
   ONE REAL engine institution record (sample/real-institution).

   UI-2: the PROGRAM-anchored sections (Overview · Salary & Job Opportunities ·
   Career Paths) for ONE REAL engine program record (sample/real-program), each
   routed through the PURE adapter (components.rec-demo.adapter/program->*-props)
   into the shared section components. This is what the UI-2 live-QA targets:
   OUR :overview drops in; the earnings-vs-COL chart + demand tiles are the
   deterministic FIGURES while OUR :descriptor/:safe-bet frame them; the roles list
   + PUMS panel are the FIGURES while OUR :summary is the warm lead line — advisor
   voice, no figure dumped into the prose."
  (:require [uix.core :refer [$ defui]]
            [uix.dom :as dom]
            [components.rec-demo.core :as core]
            [components.rec-demo.adapter :as adapter]
            [components.rec-demo.sample :as sample]
            ["/gen/shadcn/components/ui/accordion" :as accordion]))

(defui program-sections
  "Mounts the REAL program's Overview/Salary/Careers via the adapter, all open, so
   the live render shows all three at once. Present-by-data: a section renders only
   when the adapter yields props for it."
  [{:keys [program]}]
  (let [overview-props   (adapter/program->overview-props program)
        whats-cool-props (adapter/program->whats-cool-props program)
        salary-props     (adapter/program->salary-props program)
        careers-props    (adapter/program->careers-props program)]
    ($ accordion/Accordion {:type "multiple"
                            :default-value #js ["overview" "whats-cool" "salary" "careers"]
                            :class "space-y-3"}
       (when (seq overview-props)
         ($ core/section-item {:value "overview" :heading "Overview"}
            ($ core/overview-section {:overview-props overview-props})))
       ;; What's Cool slice 3 — present-by-data: the section shows only when the
       ;; adapter yields props (i.e. the program carries :whats-cool).
       (when (seq whats-cool-props)
         ($ core/section-item {:value "whats-cool" :heading "What's Cool About This Pathway"}
            ($ core/whats-cool-section {:whats-cool-props whats-cool-props})))
       (when salary-props
         ($ core/section-item {:value "salary" :heading "Salary & Job Opportunities"}
            ($ core/salary-section {:salary-props salary-props :pathway program :school nil})))
       (when careers-props
         ($ core/section-item {:value "careers" :heading "Career Paths"}
            ($ core/careers-section {:careers-props careers-props :pathway program}))))))

(defui harness-page [_]
  ($ :div {:class "min-h-screen bg-[#f2f3f4]"}
     ($ :div {:class "bg-[#2a6465] shadow-sm px-6 md:px-8 py-4"}
        ($ :div {:class "max-w-3xl mx-auto"}
           ($ :h1 {:class "text-lg md:text-2xl font-bold text-white font-head"}
              (str (:program-title sample/real-program) " — Program Sections"))
           ($ :p {:class "text-sm text-white/80 mt-1"}
              (str (:institution-name sample/real-program)
                   " · UI-2 harness · REAL engine program → adapter → Overview + Salary + Careers"))))
     ($ :div {:class "max-w-3xl mx-auto px-4 md:px-8 py-8 space-y-8"}
        ;; What's Cool slice 3 — a REAL program that CARRIES :whats-cool (SLU BSN):
        ;; the "What's Cool About This Pathway" section shows 4 styled callout cards.
        ($ :div
           ($ :h2 {:class "text-base font-semibold text-[#2a6465] font-head mb-2"}
              (str (:institution-name sample/real-whats-cool-program)
                   " — WITH :whats-cool (expect 4 styled cards)"))
           ($ program-sections {:program sample/real-whats-cool-program}))
        ;; UI-2 — program-anchored sections; this program carries NO :whats-cool, so
        ;; the What's Cool section is ABSENT (present-by-data).
        ($ :div
           ($ :h2 {:class "text-base font-semibold text-[#2a6465] font-head mb-2"}
              (str (:institution-name sample/real-program)
                   " — NO :whats-cool (expect no What's Cool section)"))
           ($ program-sections {:program sample/real-program}))
        ;; UI-1 — school-anchored sections (kept for continuity of the harness)
        ($ core/school-sections {:school sample/real-institution}))))

(defonce root
  (dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  (dom/render-root ($ harness-page {}) root))

(defn init []
  (render))
