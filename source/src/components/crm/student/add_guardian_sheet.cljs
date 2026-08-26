(ns components.crm.student.add-guardian-sheet
  "Side sheet that adds a guardian to the current student.
   Two paths in one form:
     1. Typeahead — find an existing guardian, link via :guardian-of.
     2. 'Create new' fallback — entered manually when the typeahead doesn't
        surface the right person.
   The new relationship is always non-primary; intake / migrations control primary."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [components.crm.contact-typeahead :refer [contact-typeahead]]
            [components.shared.formatting :refer [format-phone validate-email validate-phone]]
            ["/gen/shadcn/components/ui/sheet" :as sheet]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["/gen/shadcn/components/ui/label" :as ui-label]
            ["/gen/shadcn/components/ui/select" :as ui-select]
            ["/gen/shadcn/components/ui/separator" :as separator]))

(def ^:private guardian-relationship-options
  ["Aunt" "Brother" "Cousin" "Father" "Foster Parent" "Grandfather"
   "Grandmother" "Legal Guardian" "Mother" "Sister" "Stepfather"
   "Stepmother" "Uncle"])

(defui ^:private text-row
  "Labelled input row. Optional :validate (fn [v]) runs on blur and shows an
   error below the field. Optional :on-blur (fn [v]) lets the parent reformat
   the stored value (e.g., format-phone) when the field loses focus."
  [{:keys [label k value on-change on-blur validate placeholder type]}]
  (let [[error set-error!] (use-state nil)]
    ($ :div {:class "space-y-1.5"}
       ($ ui-label/Label {:html-for (str "ag-" (name k))} label)
       ($ input/Input {:id (str "ag-" (name k))
                       :type (or type "text")
                       :value (or value "")
                       :placeholder placeholder
                       :class (when error "border-destructive")
                       :on-change #(do
                                     (set-error! nil)
                                     (on-change (.. % -target -value)))
                       :on-blur #(let [v (.. % -target -value)]
                                   (when on-blur (on-blur v))
                                   (set-error! (when validate (validate v))))})
       (when error
         ($ :p {:class "text-xs text-destructive"} error)))))

(defui ^:private select-row [{:keys [label value options on-change placeholder]}]
  ($ :div {:class "space-y-1.5"}
     ($ ui-label/Label label)
     ($ ui-select/Select {:value (or value "")
                          :on-value-change on-change}
        ($ ui-select/SelectTrigger {:class "w-full"}
           ($ ui-select/SelectValue {:placeholder (or placeholder "Select…")}))
        ($ ui-select/SelectContent
           (for [opt options]
             ($ ui-select/SelectItem {:key opt :value opt} opt))))))

(defui add-guardian-sheet
  [{:keys [api-client]}]
  (let [open?    (use-subscribe [::student-subs/add-guardian-sheet-open?])
        form     (use-subscribe [::student-subs/add-guardian-sheet-form])
        found    (use-subscribe [::student-subs/add-guardian-sheet-found])
        saving?  (use-subscribe [::student-subs/add-guardian-sheet-saving?])
        error    (use-subscribe [::student-subs/add-guardian-sheet-error])
        [creating-new? set-creating-new!] (use-state false)
        set-field! (fn [k v] (rf/dispatch [::student-events/set-add-guardian-form-field k v]))
        on-close   (fn []
                     (set-creating-new! false)
                     (rf/dispatch [::student-events/close-add-guardian-sheet]))
        on-typeahead-select (fn [contact]
                              (set-creating-new! false)
                              (rf/dispatch [::student-events/set-add-guardian-found contact]))
        clear-found! (fn []
                       (rf/dispatch [::student-events/set-add-guardian-found nil]))
        found-id (:id found)
        found-fv (:field-values found)
        found-name (when found-id
                     (or (:display-name found)
                         (str (:first-name found-fv) " " (:last-name found-fv))
                         (str (:first-name found) " " (:last-name found))))]
    ($ sheet/Sheet {:open open? :on-open-change (fn [v] (when-not v (on-close)))}
       ($ sheet/SheetContent {:side "right" :class "sm:max-w-md w-[440px] flex flex-col"}
          ($ sheet/SheetHeader {:class "px-6 pt-6"}
             ($ sheet/SheetTitle "Add a guardian")
             ($ sheet/SheetDescription
                "Search the directory or create a new guardian. Adds a non-primary guardian relationship to this student."))
          ($ :div {:class "flex-1 overflow-y-auto px-6 py-4 space-y-5"}
             ;; --- Typeahead path ---
             (when (not creating-new?)
               ($ :section {:class "space-y-3"}
                  ($ :h4 {:class "text-xs font-semibold uppercase tracking-wider text-muted-foreground"}
                     "Find existing guardian")
                  (if found-id
                    ;; Selected: show summary + clear
                    ($ :div {:class "rounded-lg border bg-muted/30 px-4 py-3"}
                       ($ :div {:class "flex items-center justify-between gap-3"}
                          ($ :div {:class "min-w-0"}
                             ($ :p {:class "text-sm font-medium truncate"} found-name)
                             ($ :p {:class "text-xs text-muted-foreground truncate"}
                                (or (:email found) (:email found-fv) "—")))
                          ($ :button {:type "button"
                                      :class "text-xs text-muted-foreground hover:text-foreground"
                                      :on-click clear-found!}
                             "Change")))
                    ;; Not yet selected: show typeahead
                    ($ contact-typeahead
                       {:type-slug "guardian"
                        :api-client api-client
                        :placeholder "Type a name, email, or phone…"
                        :empty-hint "No matching guardian. Create a new one below."
                        :on-select on-typeahead-select}))))

             ;; --- Create-new toggle (visible only when nothing selected) ---
             (when (not found-id)
               ($ :div {:class "flex justify-center"}
                  ($ :button
                     {:type "button"
                      :class "text-xs font-medium text-primary hover:underline"
                      :on-click #(set-creating-new! (not creating-new?))}
                     (if creating-new?
                       "← Back to search"
                       "Don't see them? Create a new guardian"))))

             ;; --- Create-new form ---
             (when creating-new?
               ($ :section {:class "space-y-3"}
                  ($ :h4 {:class "text-xs font-semibold uppercase tracking-wider text-muted-foreground"}
                     "New guardian")
                  ($ :div {:class "grid grid-cols-2 gap-3"}
                     ($ text-row {:label "First name"
                                  :k :first-name
                                  :value (:first-name form)
                                  :on-change #(set-field! :first-name %)})
                     ($ text-row {:label "Last name"
                                  :k :last-name
                                  :value (:last-name form)
                                  :on-change #(set-field! :last-name %)}))
                  ($ text-row {:label "Email"
                               :k :email
                               :type "email"
                               :value (:email form)
                               :on-change #(set-field! :email %)
                               :validate validate-email
                               :placeholder "guardian@example.com"})
                  ($ text-row {:label "Phone"
                               :k :phone
                               :type "tel"
                               :value (:phone form)
                               :on-change #(set-field! :phone %)
                               :on-blur #(when (and % (not (str/blank? %))
                                                    (nil? (validate-phone %)))
                                           (set-field! :phone (format-phone %)))
                               :validate validate-phone
                               :placeholder "(504) 555-1234"})
                  ($ text-row {:label "Mailing address"
                               :k :mailing-address
                               :value (:mailing-address form)
                               :on-change #(set-field! :mailing-address %)})))

             ;; --- Relationship picker (always shown when there's something to save) ---
             (when (or found-id creating-new?)
               ($ :<>
                  ($ separator/Separator)
                  ($ :section {:class "space-y-3"}
                     ($ :h4 {:class "text-xs font-semibold uppercase tracking-wider text-muted-foreground"}
                        "Relationship to student")
                     ($ select-row {:label "Relationship"
                                    :value (:relationship-type form)
                                    :options guardian-relationship-options
                                    :on-change #(set-field! :relationship-type %)}))))

             (when error
               ($ :div {:class "text-sm text-destructive"} error)))

          ($ sheet/SheetFooter {:class "px-6 py-4 border-t flex-row gap-2 justify-end"}
             ($ button/Button {:variant "outline" :on-click on-close} "Cancel")
             ($ button/Button
                {:disabled (or saving?
                               (and (not found-id)
                                    (not (and creating-new? (:first-name form) (:email form)))))
                 :on-click (fn []
                             (if found-id
                               (rf/dispatch [::student-events/link-found-guardian api-client])
                               (rf/dispatch [::student-events/create-and-link-guardian api-client])))}
                (if saving? "Saving…" (if found-id "Link guardian" "Add guardian"))))))))
