(ns config.core-test
  "Issue 0041 — flip advisor edit back to the dense editor. This REVERTS the 0022 — S6
   global cutover: the advisor's recommendations EDIT experience is the pre-existing
   DENSE editor, and the redesigned rec_demo view becomes the STUDENT preview + published
   output only.

   The flip is a change to the compile-time DEFAULT of the ADVISOR_AUTHORING_CUTOVER
   closure-define. In the :node-test build no override is supplied, so
   `advisor-authoring-cutover-config` reads its declared default — asserting the default
   config here pins the cutover to global-OFF, and composing it through the PURE gate
   predicate proves the OLD dense editor is the default edit surface for ANY advisee. The
   `?authoring=on` URL override remains a working escape hatch (preview the authoring
   view)."
  (:require [cljs.test :refer [deftest testing is]]
            [config.core :as config]
            [components.advisor.authoring.overlays :as ov]))

(deftest cutover-default-is-global-off
  (testing "the compile-time default flips the cutover config back to global-OFF"
    (is (false? (:default? (config/advisor-authoring-cutover-config)))
        "ADVISOR_AUTHORING_CUTOVER declared default is \"off\" → :default? false")))

(deftest default-config-routes-to-dense-editor-for-any-advisee
  (testing "through the PURE gate predicate the DEFAULT config DISABLES the authoring
            view for any tenant — including a nil/unknown tenant — so the route renders
            the OLD dense editor"
    (let [cfg (config/advisor-authoring-cutover-config)]
      (is (false? (ov/authoring-cutover-enabled? cfg "any-tenant")))
      (is (false? (ov/authoring-cutover-enabled? cfg nil))
          "no tenant + global-off default → old dense editor (the reverted default)"))))

(deftest authoring-on-override-still-forces-authoring-view
  (testing "?authoring=on remains a working escape hatch — it forces the redesigned
            authoring view even against the default-OFF config"
    (let [cfg (config/advisor-authoring-cutover-config)]
      (is (true? (ov/cutover-with-override "on" cfg nil)))
      (is (true? (ov/cutover-with-override "on" cfg "any-tenant")))))
  (testing "no override / kill-switch falls through to the default-OFF config (dense editor)"
    (let [cfg (config/advisor-authoring-cutover-config)]
      (is (false? (ov/cutover-with-override nil cfg nil)))
      (is (false? (ov/cutover-with-override "off" cfg nil))))))
