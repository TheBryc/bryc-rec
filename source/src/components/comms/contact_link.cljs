(ns components.comms.contact-link
  "Shared helper for linking a broadcast recipient / replier name to their
   CRM contact detail page. Students and guardians have separate detail routes
   keyed by contact-id; anything else (staff, external recipients with no
   contact-id) renders as plain text."
  (:require [uix.core :refer [defui $]]
            [components.context.interface :as context]))

(defn contact-route
  "Returns [route-name params] for a CRM contact by type-slug, or nil when the
   contact isn't linkable (no id, or a type without a detail page)."
  [contact-id type-slug]
  (when contact-id
    (case (some-> type-slug name)
      "student" [:hub-student-detail {:student-id (str contact-id)}]
      "guardian" [:hub-guardian-detail {:guardian-id (str contact-id)}]
      nil)))

(defui contact-link
  "Renders `label` as a link to the contact's detail page when linkable,
   otherwise as plain text.
   Props: :contact-id :type-slug :label :class"
  [{:keys [contact-id type-slug label class]}]
  (let [ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        route (contact-route contact-id type-slug)]
    (if route
      ($ :button {:type "button"
                  :on-click (fn [e]
                              (.preventDefault e)
                              (apply navigate! route))
                  :class (str "text-left text-primary hover:underline " (or class ""))}
         label)
      ($ :span {:class class} label))))
