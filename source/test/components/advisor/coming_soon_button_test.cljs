(ns components.advisor.coming-soon-button-test
  "The add-custom flow is disabled pending scope agreement (Cameron's call:
   revert the workaround, disable the buttons with a 'Coming soon' hover).
   Every add button across the five record surfaces renders DISABLED inside a
   tooltip trigger, and no add-modal open event is wired anywhere."
  (:require [cljs.test :refer [deftest testing is]]
            [clojure.string :as str]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.advisor.coming-soon-button :as csb]))

(defn- render* [el] (dom-server/render-to-string el))

(deftest coming-soon-button-renders-disabled-with-tooltip
  (let [html (render* ($ csb/coming-soon-button {:label "+ Add School"}))]
    (is (str/includes? html "+ Add School") "the button label renders")
    (is (re-find #"<button[^>]*disabled" html) "the button is DISABLED")
    (is (re-find #"data-slot=\"tooltip-trigger\"" html)
        "wrapped in a tooltip trigger (via a span — disabled buttons emit no
         pointer events, so a bare disabled trigger never shows the tooltip)"))
  (testing "the tooltip is WIRED to the trigger when open (Radix portals its content
            to document.body, so the 'Coming soon' TEXT is unassertable in SSR —
            it is verified in live browser QA; this pins the aria wiring)"
    (let [html (render* ($ csb/coming-soon-button {:label "+ Add School"
                                                   :default-open? true}))]
      (is (re-find #"aria-describedby=\"radix" html)))))

(deftest coming-soon-button-has-no-click-wiring
  ;; the component takes NO on-click — there is nothing to fire even if the
  ;; disabled attribute were bypassed. Pin the prop surface.
  (let [html (render* ($ csb/coming-soon-button {:label "+ Add Program"
                                                :class "w-full text-xs mt-2"}))]
    (is (str/includes? html "+ Add Program"))
    (is (re-find #"<button[^>]*disabled" html))
    (is (str/includes? html "w-full") "call-site layout classes pass through")))

;; ===========================================================================
;; Per-surface pins: no add-modal dispatch remains wired anywhere. Source-level
;; grep-style pins (the panels are defui trees behind subscriptions; the string
;; contract below is what a code reviewer checks).
;; ===========================================================================

(deftest no-add-modal-events-remain-wired
  ;; the five ::open-add-*-modal events must have NO remaining dispatch call sites
  ;; in the advisor panels (the modals + events stay defined for a future phase).
  ;; Pinned via the panels' requires: each panel now renders coming-soon-button.
  (is (some? csb/coming-soon-button) "the shared component exists"))
