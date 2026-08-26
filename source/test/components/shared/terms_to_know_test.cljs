(ns components.shared.terms-to-know-test
  "Slice 0040 (epic match-tavidee) — the 'Terms to Know' glossary block.
   A DETERMINISTIC, present-by-data defui (Fixed text — NOT advisor-editable, NO
   LLM). Rendered once at the bottom of the recommendations tab content in BOTH
   the student page AND the advisor authoring view. Tested through a real
   dom-server render so the fixed definitions are proven to surface, and proven
   to be input-independent (same fixed text regardless of any props)."
  (:require [cljs.test :refer [deftest testing is]]
            [uix.core :refer [$]]
            [uix.dom.server :as dom-server]
            [components.shared.terms-to-know :as glossary]))

(defn- render
  ([] (render nil))
  ([props] (dom-server/render-to-string ($ glossary/terms-to-know props))))

(deftest terms-to-know-renders-heading
  (testing "the glossary surfaces the 'Terms to Know' heading"
    (is (re-find #"Terms to Know" (render))
        "the section heading renders")))

(deftest terms-to-know-renders-all-five-terms
  (testing "all five glossary terms render as headwords"
    (let [html (render)]
      (is (re-find #"Median" html) "Median term renders")
      (is (re-find #"Living wage" html) "Living wage term renders")
      (is (re-find #"Net cost" html) "Net cost term renders")
      (is (re-find #"TOPS" html) "TOPS term renders")
      (is (re-find #"Pell Grant" html) "Pell Grant term renders"))))

(deftest terms-to-know-renders-each-definition
  (testing "each term's fixed definition prose renders in full"
    (let [html (render)]
      (is (re-find #"half of people earn more, half earn less" html)
          "Median definition renders")
      (is (re-find #"income a single adult needs to cover basic costs" html)
          "Living wage definition renders")
      (is (re-find #"Baton Rouge" html)
          "Living wage names the local area")
      (is (re-find #"after grants and scholarships" html)
          "Net cost definition renders")
      (is (re-find #"not the sticker price" html)
          "Net cost contrasts with sticker price")
      (is (re-find #"Louisiana(?:'|&#x27;|&#39;)s state scholarship" html)
          "TOPS definition renders")
      (is (re-find #"TOPS-Tech" html)
          "TOPS names the 2-year/technical variant")
      (is (re-find #"federal grant for students with financial need" html)
          "Pell Grant definition renders")
      (is (re-find #"money you don't repay|money you don&#x27;t repay|money you don&#39;t repay" html)
          "Pell Grant stresses it is not repaid"))))

(deftest terms-to-know-is-deterministic-regardless-of-input
  (testing "Fixed text — the block renders byte-identical HTML no matter the props"
    (let [a (render nil)
          b (render {:student {:name "Maria"} :institutions [1 2 3]})
          c (render {:anything "else"})]
      (is (= a b) "props do not change the rendered glossary")
      (is (= a c) "unrelated props do not change the rendered glossary"))))
