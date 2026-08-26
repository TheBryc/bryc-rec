(ns components.crm.contact-typeahead
  "Reusable typeahead for searching CRM contacts of a given type.
   Wraps shadcn cmdk's Command primitive. Server-side search via the
   :crm/typeahead-contacts query (case-insensitive substring across name,
   email, phone). Two-character minimum is enforced server-side; debounced
   client-side at 150ms.

   Local state lives entirely inside the component — no re-frame round-trip
   for the input/results, since they're transient to a single sheet's open
   lifetime. Selection bubbles up via on-select."
  (:require [uix.core :as uix :refer [defui $ use-state use-ref use-effect use-callback]]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [components.crm.student.avatar :refer [student-avatar]]
            [anomalies :refer [anomaly?]]
            ["/gen/shadcn/components/ui/command" :as cmd]))

(def ^:private debounce-ms 150)

(defn- secondary-line
  "Render the second line under each result. Email + grade for students,
   email for guardians/staff."
  [contact]
  (let [email (:email contact)
        grade (:grade contact)
        type-slug (:type-slug contact)]
    (cond
      (and email grade (= "student" type-slug)) (str email " · " grade)
      email email
      grade grade
      :else nil)))

(defui contact-typeahead
  "Props:
   - :type-slug      \"student\" | \"guardian\" | \"staff\" — restricts search
   - :api-client     API client (from parent sheet)
   - :on-select      (fn [contact-map]) — called with the selected slim contact
   - :placeholder    optional input placeholder
   - :exclude-id     optional contact-id to drop from results (e.g. self in sibling search)
   - :empty-hint     optional string shown when term ≥ 2 chars and no matches"
  [{:keys [type-slug api-client on-select placeholder exclude-id empty-hint]}]
  (let [[term set-term!]           (use-state "")
        [results set-results!]     (use-state [])
        [searching? set-searching!] (use-state false)
        [searched? set-searched!]   (use-state false)
        timer-ref                   (use-ref nil)
        active?                     (>= (count term) 2)

        run-search!
        (use-callback
          (fn [t]
            (when (and api-client (>= (count t) 2))
              (set-searching! true)
              (go
                (let [response (<! (api/query api-client {:query/name :crm/typeahead-contacts
                                                          :term t
                                                          :type-slug type-slug
                                                          :limit 8}))]
                  (set-searching! false)
                  (set-searched! true)
                  (if (anomaly? response)
                    (set-results! [])
                    (let [raw (or (:results response) [])
                          filtered (if exclude-id
                                     (filterv #(not= (:id %) exclude-id) raw)
                                     raw)]
                      (set-results! filtered)))))))
          [api-client type-slug exclude-id])

        on-input-change
        (use-callback
          (fn [next]
            (set-term! next)
            (set-searched! false)
            (when-let [t @timer-ref]
              (js/clearTimeout t)
              (reset! timer-ref nil))
            (cond
              (< (count next) 2)
              (do (set-results! []) (set-searching! false))

              :else
              (reset! timer-ref
                      (js/setTimeout #(run-search! next) debounce-ms))))
          [run-search!])]

    ;; Cancel any pending timer when the component unmounts.
    (use-effect
      (fn []
        (fn []
          (when-let [t @timer-ref]
            (js/clearTimeout t)
            (reset! timer-ref nil))))
      [])

    ($ cmd/Command
       {:should-filter false  ;; server does the filtering
        :class "rounded-lg border bg-background"}
       ($ cmd/CommandInput
          {:value term
           :on-value-change on-input-change
           :placeholder (or placeholder "Type to search…")})
       (when active?
         ($ cmd/CommandList
            (cond
              searching?
              ($ :div {:class "py-6 text-center text-sm text-muted-foreground"}
                 "Searching…")

              (and searched? (empty? results))
              ($ cmd/CommandEmpty
                 (or empty-hint "No matches."))

              (seq results)
              ($ cmd/CommandGroup
                 (for [contact results]
                   (let [name (or (:display-name contact)
                                  (str (:first-name contact) " " (:last-name contact))
                                  "Unnamed")
                         second (secondary-line contact)
                         ;; cmdk's onSelect fires with the item value (string),
                         ;; not a DOM event — so this branch never touches
                         ;; preventDefault. The inner DOM handlers below DO get
                         ;; events and call preventDefault on those.
                         ;; After a pick, clear the field + results so the
                         ;; dropdown closes (it stays mounted in multi-add
                         ;; flows like the mass-send audience step).
                         pick! (fn []
                                 (when on-select (on-select contact))
                                 (when-let [t @timer-ref]
                                   (js/clearTimeout t)
                                   (reset! timer-ref nil))
                                 (set-term! "")
                                 (set-results! [])
                                 (set-searched! false)
                                 (set-searching! false))]
                     ($ cmd/CommandItem
                        {:key (str (:id contact))
                         :value (str (:id contact))
                         :on-select (fn [_value] (pick!))}
                        ($ :div {:class "flex items-center gap-2.5 w-full cursor-pointer"
                                 :on-mouse-down (fn [e]
                                                  (.preventDefault e)
                                                  (pick!))
                                 :on-click (fn [e]
                                             (.preventDefault e)
                                             (pick!))}
                           ($ student-avatar {:name name :size :sm})
                           ($ :div {:class "min-w-0 flex-1"}
                              ($ :p {:class "text-sm font-medium truncate"} name)
                              (when second
                                ($ :p {:class "text-xs text-muted-foreground truncate"} second))))))))))))))
