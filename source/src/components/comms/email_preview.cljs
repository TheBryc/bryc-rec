(ns components.comms.email-preview
  (:require [clojure.string :as str]
            [uix.core :as uix :refer [defui $]]))

(def placeholder "{{content}}")

(def sample-content
  "<p>Hi Maria,</p><p>This is a sample BRYC update shown inside the default email layout.</p>")

(defn has-placeholder? [s]
  (str/includes? (or s "") placeholder))

(defn add-placeholder [s]
  (let [html (or s "")]
    (cond
      (has-placeholder? html)
      html

      (str/blank? html)
      placeholder

      (re-find #"(?i)</body>" html)
      (str/replace-first html #"(?i)</body>" (str "\n    " placeholder "\n  </body>"))

      :else
      (str html "\n\n" placeholder))))

(defn escape-html [s]
  (-> (or s "")
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(defn plain-text->html [s]
  (-> (escape-html s)
      (str/replace #"\r\n?" "\n")
      (str/replace #"\n" "<br>")))

(defn render-email-preview-html
  [{:keys [layout-html body-format body body-html]}]
  (let [content (case (or body-format :plain)
                  :html (or body-html "")
                  (plain-text->html body))]
    (cond
      (has-placeholder? layout-html)
      (str/replace layout-html placeholder content)

      (seq content)
      content

      :else
      "")))

(defui email-preview-frame
  [{:keys [html invalid? empty-message]}]
  ($ :div {:class "min-h-[18rem] overflow-hidden rounded-md bg-white ring-1 ring-black/10"}
     (cond
       invalid?
       ($ :div {:class "flex min-h-[18rem] items-center justify-center px-6 text-center text-sm text-rose-700"}
          "Add the content marker to render the layout preview.")

       (str/blank? html)
       ($ :div {:class "flex min-h-[18rem] items-center justify-center px-6 text-center text-sm text-muted-foreground"}
          (or empty-message "Nothing to preview yet."))

       :else
       ($ :iframe
          {:title "Email preview"
           :sandbox ""
           :srcDoc html
           :class "h-[28rem] w-full border-0 bg-white"}))))
