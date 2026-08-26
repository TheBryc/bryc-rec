(ns components.crm.student.add-sibling-sheet
  "Side sheet that links an existing student as a sibling of the current student.
   Typeahead-only — adding a brand-new student is intake-flow business and out
   of scope here. The 'sibling-of' relationship is symmetric (resolved via
   inverse-name on the relationship type), so a single record covers both ends."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [components.crm.contact-typeahead :refer [contact-typeahead]]
            [components.crm.student.avatar :refer [student-avatar]]
            [components.crm.student.status :as status]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/button" :as button]))

(defn- bryc-status-label [status]
  (status/status-label status))

(defui add-sibling-sheet
  [{:keys [api-client]}]
  (let [open?   (use-subscribe [::student-subs/add-sibling-sheet-open?])
        found   (use-subscribe [::student-subs/add-sibling-sheet-found])
        saving? (use-subscribe [::student-subs/add-sibling-sheet-saving?])
        error   (use-subscribe [::student-subs/add-sibling-sheet-error])
        current-student-id (use-subscribe [:store.crm.student.subs/current-student-id])
        on-close #(rf/dispatch [::student-events/close-add-sibling-sheet])
        on-typeahead-select (fn [contact]
                              (when (not= (:id contact) current-student-id)
                                (rf/dispatch [::student-events/set-add-sibling-found contact])))
        clear-found! #(rf/dispatch [::student-events/set-add-sibling-found nil])
        found-id (:id found)
        found-name (when found-id
                     (or (:display-name found)
                         (str (:first-name found) " " (:last-name found))))
        bryc-status (:bryc-status found)
        status-label (bryc-status-label bryc-status)]
    ($ sheet/Sheet {:open open? :on-open-change (fn [v] (when-not v (on-close)))}
       ($ sheet/SheetContent {:side "right" :class "sm:max-w-md w-[420px] flex flex-col"}
          ($ sheet/SheetHeader {:class "px-6 pt-6"}
             ($ sheet/SheetTitle "Link a sibling")
             ($ sheet/SheetDescription
                "Search for an existing student. Linked siblings appear in both students' Family tabs."))
          ($ :div {:class "flex-1 overflow-y-auto px-6 py-4 space-y-4"}
             (if found-id
               ;; Selected sibling preview + Change action.
               ($ :div {:class "rounded-lg border bg-muted/30 px-4 py-3 flex items-center gap-3"}
                  ($ student-avatar {:name found-name :size :sm})
                  ($ :div {:class "min-w-0 flex-1"}
                     ($ :p {:class "text-sm font-medium truncate"} found-name)
                     ($ :p {:class "text-xs text-muted-foreground truncate"}
                        (cond-> ""
                          (:email found) (str (:email found))
                          (and (:email found) status-label) (str " · ")
                          status-label (str status-label))))
                  ($ :button
                     {:type "button"
                      :class "text-xs text-muted-foreground hover:text-foreground shrink-0"
                      :on-click clear-found!}
                     "Change"))
               ;; Typeahead.
               ($ contact-typeahead
                  {:type-slug "student"
                   :api-client api-client
                   :placeholder "Type a sibling's name, email, or phone…"
                   :empty-hint "No matching student. New students enter through intake."
                   :exclude-id current-student-id
                   :on-select on-typeahead-select}))
             (when error
               ($ :div {:class "text-sm text-destructive"} error)))
          ($ sheet/SheetFooter {:class "px-6 py-4 border-t flex-row gap-2 justify-end"}
             ($ button/Button {:variant "outline" :on-click on-close} "Cancel")
             ($ button/Button
                {:disabled (or saving? (not found-id))
                 :on-click #(rf/dispatch [::student-events/link-found-sibling api-client])}
                (if saving? "Linking…" "Link sibling")))))))
