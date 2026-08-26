(ns components.comms.tokens
  "Shared mail-merge token UI + logic.

   Single source of truth for the token vocabulary on the frontend. Mirrors
   the server-side expansion used by communications-service broadcast fanout.
   Exposes:

   - `tokens`        : [token label] pairs
   - `expand`        : pure token expansion against a contact's :field-values
   - `insert-at-caret!` : splice a token into a controlled input/textarea at
                          its caret, then restore focus + caret
   - `token-bar`     : clickable chip row that inserts tokens
   - `preview-line`  : one-line resolved preview against a fixed sample"
  (:require [uix.core :as uix :refer [defui $]]
            [clojure.string :as str]))

(def tokens
  "[token label] pairs. `token` is the literal spliced into the text;
   `label` is the human-facing chip."
  [["{first-name}" "First name"]
   ["{last-name}"  "Last name"]
   ["{full-name}"  "Full name"]
   ["{email}"      "Email"]
   ["{phone}"      "Phone"]])

(def sample-contact
  "Fixed sample used for the preview line so authors see tokens resolve."
  {:field-values {:first-name "Maya"
                  :last-name  "Johnson"
                  :email      "maya.johnson@example.com"
                  :phone      "(225) 555-1234"}})

(defn expand
  "Expand mail-merge tokens in `s` against `contact`'s :field-values.
   Mirrors the server-side fan-out / send-system-message expansion."
  [s contact]
  (if (str/blank? s)
    (or s "")
    (let [fv (:field-values contact)
          first-name (or (:first-name fv) "")
          last-name  (or (:last-name fv) "")]
      (-> s
          (str/replace "{first-name}" first-name)
          (str/replace "{last-name}" last-name)
          (str/replace "{full-name}" (str/trim (str first-name " " last-name)))
          (str/replace "{email}" (or (:email fv) ""))
          (str/replace "{phone}" (or (:phone fv) ""))))))

(defn- has-token? [s]
  (boolean (and s (re-find #"\{[a-z][a-z-]*\}" s))))

(defn insert-at-caret!
  "Splice `token` into `value` at the caret/selection of DOM node `el`,
   call `on-change` with the new string, then restore focus and place the
   caret immediately after the inserted token. Falls back to appending when
   the element has never held a selection."
  [el value on-change token]
  (let [s (or value "")
        start (if el (.-selectionStart el) (count s))
        ;; selectionStart can be nil for non-text inputs; guard it.
        start (if (nil? start) (count s) start)
        end (if el (.-selectionEnd el) start)
        end (if (nil? end) start end)
        new-val (str (subs s 0 start) token (subs s end))]
    (on-change new-val)
    (when el
      (js/requestAnimationFrame
       (fn []
         (.focus el)
         (let [pos (+ start (count token))]
           (.setSelectionRange el pos pos)))))))

(defui token-bar
  "Clickable token chips. Props:
   - input-ref : uix ref whose deref is the target <input>/<textarea> node
   - value     : current controlled string value
   - on-change : (fn [new-string]) — dispatch the value back to the store
   - on-insert : (fn [token]) — optional. When provided, used instead of the
                 caret-splice path (e.g. a rich-text editor that owns its own
                 insertion). Takes precedence over input-ref/value/on-change.

   `onMouseDown` is prevented so clicking a chip doesn't blur the field —
   the caret stays put and insertion is seamless."
  [{:keys [input-ref value on-change on-insert]}]
  ($ :div {:class "flex flex-wrap items-center gap-1"}
     ($ :span {:class "mr-0.5 text-xs text-muted-foreground"} "Insert:")
     (for [[token label] tokens]
       ($ :button
          {:key token
           :type "button"
           :on-mouse-down (fn [e] (.preventDefault e))
           :on-click #(if on-insert
                        (on-insert token)
                        (insert-at-caret! @input-ref value on-change token))
           :class (str "inline-flex items-center rounded-full px-2 py-0.5 "
                       "text-xs font-medium ring-1 ring-black/10 "
                       "text-muted-foreground transition-colors "
                       "hover:bg-gray-950/5 hover:text-foreground "
                       "focus-visible:outline-2 focus-visible:outline-offset-2 "
                       "focus-visible:outline-blue-500")}
          label))))

(defui preview-line
  "One-line resolved preview against the fixed sample contact. Renders
   nothing until the text actually contains a token, so it stays quiet
   for plain messages."
  [{:keys [text]}]
  (when (has-token? text)
    (let [resolved (expand text sample-contact)
          shown (if (> (count resolved) 140)
                  (str (subs resolved 0 140) "…")
                  resolved)]
      ($ :p {:class "text-xs text-muted-foreground"}
         ($ :span {:class "font-medium text-foreground/70"} "Preview ")
         "(sample: Maya Johnson) — "
         ($ :span {:class "text-foreground/80"} shown)))))
