(ns components.advisor.bullet-text
  "Shared rendering helper for recommendation bullet text.

   Bullets are plain strings, but advisors may paste raw URLs into them. This
   namespace turns those URLs into clickable links at render time without using
   dangerouslySetInnerHTML, so it stays XSS-safe."
  (:require [clojure.string :as str]
            [uix.core :refer [$]]))

(defn linkify-text
  "Render a text string as a sequence of hiccup children, wrapping any http(s)
   URL in an <a>. Returns a vector of plain strings and <a> elements. Works on
   any field (bullets or prose) — returns plain text when no URL is present.

   Trailing sentence punctuation (.,;:!?) and closing brackets are kept out of
   the link. Link clicks stop propagation so they don't toggle the enclosing
   card."
  [s]
  (let [s (or s "")
        re (js/RegExp. "https?://[^\\s]+" "g")]
    (loop [acc [] last-idx 0]
      (if-let [m (.exec re s)]
        (let [idx (.-index m)
              raw (aget m 0)
              url (str/replace raw #"[.,;:!?)\]]+$" "")
              trailing (subs raw (count url))]
          (recur (cond-> acc
                   (> idx last-idx) (conj (subs s last-idx idx))
                   :always (conj ($ :a {:key idx
                                        :href url
                                        :target "_blank"
                                        :rel "noopener noreferrer"
                                        :class "text-primary underline underline-offset-2 break-words"
                                        :on-click (fn [e] (.stopPropagation e))}
                                    url))
                   (seq trailing) (conj trailing))
                 (+ idx (count raw))))
        (cond-> acc
          (< last-idx (count s)) (conj (subs s last-idx)))))))
