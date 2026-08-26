(ns components.comms.email-preview-test
  (:require [cljs.test :refer [deftest is testing]]
            [components.comms.email-preview :as preview]))

(deftest render-email-preview-html-test
  (testing "plain text is escaped and inserted into the layout"
    (is (= "<html><body>Hi Maria<br>Use &lt;portal&gt;</body></html>"
           (preview/render-email-preview-html
            {:layout-html "<html><body>{{content}}</body></html>"
             :body-format :plain
             :body "Hi Maria\nUse <portal>"}))))
  (testing "html content is inserted into the layout"
    (is (= "<html><body><p>Hi <strong>Maria</strong></p></body></html>"
           (preview/render-email-preview-html
            {:layout-html "<html><body>{{content}}</body></html>"
             :body-format :html
             :body-html "<p>Hi <strong>Maria</strong></p>"}))))
  (testing "missing layout previews content alone"
    (is (= "Hi Maria"
           (preview/render-email-preview-html
            {:body-format :plain
             :body "Hi Maria"}))))
  (testing "layout without placeholder falls back to content alone"
    (is (= "<p>Hi Maria</p>"
           (preview/render-email-preview-html
            {:layout-html "<html><body>No slot</body></html>"
             :body-format :html
             :body-html "<p>Hi Maria</p>"})))))

(deftest add-placeholder-test
  (is (= preview/placeholder (preview/add-placeholder "")))
  (is (= "<html><body>\n    {{content}}\n  </body></html>"
         (preview/add-placeholder "<html><body></body></html>")))
  (is (= (str "<section>Design</section>\n\n" preview/placeholder)
         (preview/add-placeholder "<section>Design</section>")))
  (is (= "<html>{{content}}</html>"
         (preview/add-placeholder "<html>{{content}}</html>"))))
