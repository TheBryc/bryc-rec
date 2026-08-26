(ns components.shared.affordability-callout-test
  "The 'Important: Affordability & Financial Aid' callout, extracted from the
   student recommendations page into a SHARED defui so the advisor authoring
   view's Colleges tab shows the identical block. Always-on (present-by-data:
   the student page renders it unconditionally). Tested through a real render so
   the extraction is proven to surface the same content, not just non-nil props."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.shared.affordability-callout :as callout]))

(defn- render []
  (dom-server/render-to-string ($ callout/affordability-callout)))

(deftest affordability-callout-renders-heading-and-key-content
  (testing "the shared callout surfaces the heading, College Aid Pro, the request link, and the FAFSA prose"
    (let [html (render)]
      (is (re-find #"Important: Affordability &amp;? Financial Aid" html)
          "the section heading renders")
      (is (re-find #"College Aid Pro" html)
          "the College Aid Pro copy renders")
      (is (re-find #"thebryc.org/request" html)
          "the request-access link text renders")
      (is (re-find #"https://thebryc.org/request" html)
          "the request-access link href renders")
      (is (re-find #"complete your FAFSA" html)
          "the FAFSA prose renders"))))
