(ns components.crm.student.profile-layout
  "Two-column CRM-style layout for student profile page."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/separator" :refer [Separator]]

            [components.crm.profile-photo :refer [profile-photo]]
            [components.crm.student.editable-field :refer [editable-text]]
            [components.shared.formatting :refer [format-phone]]
            [store.crm.student.events :as student-events]
            [components.context.interface :as context]))

;; Profile header with avatar and key info
(defui profile-header
  "Header section with avatar, name, school, contact, and action buttons.
   Props:
   - student: student map with :student/name, :student/school, :student/email, :student/phone
   - actions: vector of {:label string :route-name keyword :route-params map}
   - api-client: API client for saving
   - saving?: boolean indicating save in progress"
  [{:keys [student field-values actions api-client saving?]}]
  (let [ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        grade (get field-values :grade)
        preferred-name (get field-values :preferred-name)
        name-suffix (get field-values :name-suffix)]
    ($ :div {:class "border-b bg-card"}
       ($ :div {:class "p-6"}
          ($ :div {:class "flex flex-col md:flex-row items-start gap-4 md:gap-6"}
             ;; Avatar
             ($ profile-photo {:api-client api-client
                               :contact-id (:student/id student)
                               :file-id (get field-values :profile-photo)
                               :name (:student/name student)
                               :editable? true})

             ;; Info
             ($ :div {:class "flex-1 min-w-0"}
                ($ :div {:class "text-2xl font-semibold"}
                   ($ editable-text
                      {:value (:student/name student)
                       :placeholder "Add name..."
                       :saving? saving?
                       :class "text-2xl font-semibold"
                       :on-save #(rf/dispatch [::student-events/save-field "name" % api-client])}))
                ;; Preferred name & suffix (only shown when at least one has a value)
                (when (or preferred-name name-suffix)
                  ($ :div {:class "flex items-center gap-3 mt-0.5 text-sm text-muted-foreground"}
                     (when preferred-name
                       ($ :span {:class "inline-flex items-center gap-1"}
                          ($ :span {:class "text-xs font-medium"} "Preferred:")
                          ($ editable-text
                             {:value preferred-name
                              :placeholder "Set preferred name"
                              :saving? saving?
                              :on-save #(rf/dispatch [::student-events/save-field "preferred-name" % api-client])})))
                     (when name-suffix
                       ($ :span {:class "inline-flex items-center gap-1"}
                          ($ :span {:class "text-xs font-medium"} "Suffix:")
                          ($ editable-text
                             {:value name-suffix
                              :placeholder "Set suffix"
                              :saving? saving?
                              :on-save #(rf/dispatch [::student-events/save-field "name-suffix" % api-client])})))))
                ;; School + grade inline
                ($ :p {:class "text-muted-foreground mt-1"}
                   ($ editable-text
                      {:value (:student/school student)
                       :placeholder "Add school..."
                       :saving? saving?
                       :on-save #(rf/dispatch [::student-events/save-field "high-school" % api-client])})
                   (when grade
                     (str " · " grade))))

             ;; Action buttons
             (when (seq actions)
               ($ :div {:class "flex items-center gap-2 flex-wrap mt-4 md:mt-0"}
                  (for [{:keys [label route-name route-params primary? on-click icon]} actions]
                    ($ button/Button
                       {:key label
                        :variant (if primary? "default" "outline")
                        :size "sm"
                        :on-click (or on-click #(navigate! route-name (or route-params {})))}
                       (when icon ($ icon {:class "h-4 w-4 mr-2"}))
                       label)))))))))

;; Sidebar card component
(defui sidebar-card
  "Card component for sidebar sections.
   Props:
   - title: section title
   - children: card content"
  [{:keys [title children]}]
  ($ card/Card {:class "mb-3"}
     ($ card/CardHeader {:class "pb-2 pt-4 px-4"}
        ($ card/CardTitle {:class "text-sm font-medium"} title))
     ($ card/CardContent {:class "pt-0 px-4 pb-3"}
        children)))

;; Field row for sidebar
(defui field-row
  "Simple label/value row for sidebar cards.
   Props:
   - label: field label
   - value: field value (string or component)
   - class: additional classes"
  [{:keys [label value class]}]
  ($ :div {:class (str "py-2 first:pt-0 last:pb-0 " class)}
     (when label
       ($ :dt {:class "text-xs font-medium text-muted-foreground mb-0.5"} label))
     ($ :dd {:class "text-sm"} (or value "—"))))

;; Main content section
(defui content-section
  "Section component for main content area.
   Props:
   - title: section title
   - children: section content"
  [{:keys [title children]}]
  ($ :section {:class "mb-8"}
     ($ :h2 {:class "text-lg font-semibold mb-4"} title)
     ($ card/Card
        ($ card/CardContent {:class "pt-6"}
           children))))

;; Two-column layout wrapper
(defui profile-layout
  "Two-column CRM-style profile layout.
   Props:
   - student: student data map
   - actions: action buttons for header
   - sidebar: sidebar content (React element)
   - children: main content area
   - api-client: API client for saving
   - saving?: boolean indicating save in progress"
  [{:keys [student field-values actions sidebar children api-client saving?]}]
  ($ :div {:class "flex flex-col h-full min-h-0"}
     ;; Header
     ($ profile-header {:student student :field-values field-values :actions actions :api-client api-client :saving? saving?})

     ;; Two-column body
     ($ :div {:class "flex-1 overflow-hidden"}
        ($ :div {:class "h-full flex flex-col lg:flex-row"}
           ;; Left sidebar (sticky on desktop, stacked on mobile)
           ($ :aside {:class "w-full lg:w-80 flex-shrink-0 border-b lg:border-b-0 lg:border-r lg:overflow-y-auto p-4"}
              sidebar)

           ;; Main content (scrollable)
           ($ :main {:class "flex-1 overflow-y-auto p-6 lg:p-8"}
              ($ :div {:class "max-w-3xl xl:max-w-5xl 2xl:max-w-none"}
                 children))))))
