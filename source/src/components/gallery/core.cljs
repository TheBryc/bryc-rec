(ns components.gallery.core
  "BRYC Design System Gallery — showcases design tokens, custom components, and shadcn/ui primitives."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            [clojure.string :as str]
            ;; shadcn components
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :refer [Badge]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/textarea" :refer [Textarea]]
            ["/gen/shadcn/components/ui/select" :as select]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/tabs" :as tabs]
            ["/gen/shadcn/components/ui/alert" :as alert]
            ["/gen/shadcn/components/ui/separator" :refer [Separator]]
            ["lucide-react" :refer [AlertCircle Info]]
            ;; BRYC custom components
            [components.crm.student.editable-field :as ef]
            [components.crm.student.avatar :refer [student-avatar]]
            [components.crm.student.profile-layout :refer [content-section]]
            [components.crm.schema-form-renderer :refer [schema-vector-editor]]
            ;; Shared library
            [components.shared.formatting :as fmt]
            [components.shared.form-modal :refer [form-modal]]
            [components.shared.loading-states :refer [loading-spinner error-banner empty-state async-content]]))

;; =============================================================================
;; Layout Helpers
;; =============================================================================

(defui gallery-section [{:keys [id title children]}]
  ($ :div {:id id :class "mb-12 scroll-mt-6"}
     ($ :h2 {:class "text-lg font-semibold mb-4 pb-2 border-b"} title)
     children))

(defui specimen [{:keys [label children]}]
  ($ :div {:class "mb-6"}
     (when label
       ($ :p {:class "text-xs text-muted-foreground font-mono mb-2"} label))
     ($ :div {:class "p-4 border rounded-lg bg-card"} children)))

;; =============================================================================
;; Sidebar Navigation
;; =============================================================================

(def nav-items
  [{:id "principles" :label "Design Principles" :children []}
   {:id "foundations" :label "Foundations"
    :children [{:id "colors" :label "Colors"}
               {:id "typography" :label "Typography"}
               {:id "spacing" :label "Spacing & Radius"}]}
   {:id "bryc-components" :label "BRYC Components"
    :children [{:id "editable-fields" :label "Editable Fields"}
               {:id "schema-editor" :label "Schema Vector Editor"}
               {:id "layout" :label "Layout Primitives"}
               {:id "avatar" :label "Avatar"}]}
   {:id "shared-lib" :label "Shared Library"
    :children [{:id "formatting" :label "Formatting Utils"}
               {:id "form-modal-demo" :label "Form Modal"}
               {:id "loading-states" :label "Loading States"}]}
   {:id "shadcn" :label "shadcn/ui"
    :children [{:id "buttons" :label "Buttons"}
               {:id "badges" :label "Badges"}
               {:id "cards" :label "Cards"}
               {:id "inputs" :label "Inputs"}
               {:id "dialogs" :label "Dialogs"}
               {:id "tabs-demo" :label "Tabs"}
               {:id "alerts" :label "Alerts"}]}])

(defn scroll-to! [id]
  (when-let [el (js/document.getElementById id)]
    (.scrollIntoView el #js {:behavior "smooth" :block "start"})))

(defui sidebar-nav []
  ($ :aside {:class "sticky top-0 h-screen w-56 shrink-0 border-r flex flex-col bg-card overflow-y-auto"}
     ($ :div {:class "flex items-center gap-2 px-4 h-14 border-b"}
        ($ :span {:class "text-sm font-semibold tracking-tight"} "BRYC Design System"))
     ($ :nav {:class "flex-1 px-3 py-4 space-y-4"}
        (for [group nav-items]
          ($ :div {:key (:id group)}
             ($ :button {:on-click #(scroll-to! (:id group))
                         :class "text-xs font-semibold text-muted-foreground uppercase tracking-wider cursor-pointer"}
                (:label group))
             ($ :div {:class "mt-1 space-y-0.5"}
                (for [child (:children group)]
                  ($ :button {:key (:id child)
                              :on-click #(scroll-to! (:id child))
                              :class "block w-full text-left text-sm px-2 py-1 rounded hover:bg-accent text-foreground/80 hover:text-foreground transition-colors cursor-pointer"}
                     (:label child)))))))))

;; =============================================================================
;; Foundations: Colors
;; =============================================================================

(def color-tokens
  [{:name "background" :var "--background"}
   {:name "foreground" :var "--foreground"}
   {:name "primary" :var "--primary"}
   {:name "primary-foreground" :var "--primary-foreground"}
   {:name "secondary" :var "--secondary"}
   {:name "secondary-foreground" :var "--secondary-foreground"}
   {:name "muted" :var "--muted"}
   {:name "muted-foreground" :var "--muted-foreground"}
   {:name "accent" :var "--accent"}
   {:name "accent-foreground" :var "--accent-foreground"}
   {:name "destructive" :var "--destructive"}
   {:name "border" :var "--border"}
   {:name "input" :var "--input"}
   {:name "ring" :var "--ring"}
   {:name "card" :var "--card"}
   {:name "popover" :var "--popover"}
   {:name "chart-1" :var "--chart-1"}
   {:name "chart-2" :var "--chart-2"}
   {:name "chart-3" :var "--chart-3"}
   {:name "chart-4" :var "--chart-4"}
   {:name "chart-5" :var "--chart-5"}
   {:name "sidebar" :var "--sidebar"}
   {:name "sidebar-primary" :var "--sidebar-primary"}
   {:name "sidebar-accent" :var "--sidebar-accent"}])

(defui color-swatch [{:keys [name css-var]}]
  ($ :div {:class "flex items-center gap-3"}
     ($ :div {:class "w-10 h-10 rounded-md border shadow-sm shrink-0"
              :style {:background (str "var(" css-var ")")}})
     ($ :div
        ($ :p {:class "text-sm font-medium"} name)
        ($ :p {:class "text-xs text-muted-foreground font-mono"} css-var))))

(defui colors-section []
  ($ gallery-section {:id "colors" :title "Color Palette"}
     ($ :p {:class "text-sm text-muted-foreground mb-4"}
        "Semantic color tokens from the OkLCh color system. Colors reference CSS custom properties and adapt to light/dark mode.")
     ($ :div {:class "grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4"}
        (for [{:keys [name var]} color-tokens]
          ($ color-swatch {:key var :name name :css-var var})))))

;; =============================================================================
;; Foundations: Typography
;; =============================================================================

(defui typography-section []
  ($ gallery-section {:id "typography" :title "Typography"}
     ($ :p {:class "text-sm text-muted-foreground mb-4"}
        "System font stack with Tailwind utility classes.")
     ($ specimen {:label "Heading Scale"}
        ($ :div {:class "space-y-3"}
           ($ :h1 {:class "text-3xl font-bold"} "Heading 1 — text-3xl font-bold")
           ($ :h2 {:class "text-2xl font-semibold"} "Heading 2 — text-2xl font-semibold")
           ($ :h3 {:class "text-xl font-semibold"} "Heading 3 — text-xl font-semibold")
           ($ :h4 {:class "text-lg font-medium"} "Heading 4 — text-lg font-medium")))
     ($ specimen {:label "Body & Utility Text"}
        ($ :div {:class "space-y-2"}
           ($ :p {:class "text-base"} "Body text — text-base (default)")
           ($ :p {:class "text-sm"} "Small text — text-sm")
           ($ :p {:class "text-xs"} "Extra small — text-xs")
           ($ :p {:class "text-sm text-muted-foreground"} "Muted text — text-sm text-muted-foreground")
           ($ :p {:class "text-sm font-mono"} "Monospace — text-sm font-mono")))))

;; =============================================================================
;; Foundations: Spacing & Radius
;; =============================================================================

(defui spacing-section []
  ($ gallery-section {:id "spacing" :title "Spacing & Radius"}
     ($ specimen {:label "Border Radius Tokens"}
        ($ :div {:class "flex flex-wrap gap-4 items-end"}
           (for [[label cls] [["sm" "rounded-sm"] ["md" "rounded-md"] ["lg" "rounded-lg"] ["xl" "rounded-xl"] ["full" "rounded-full"]]]
             ($ :div {:key label :class "text-center"}
                ($ :div {:class (str "w-16 h-16 bg-primary " cls)})
                ($ :p {:class "text-xs text-muted-foreground mt-1 font-mono"} (str "rounded-" label))))))))

;; =============================================================================
;; BRYC Components: Editable Fields
;; =============================================================================

(defui editable-fields-section []
  (let [[text-val set-text!] (use-state "Jane Doe")
        [textarea-val set-textarea!] (use-state "I want to build technology that helps my community.")
        [num-val set-num!] (use-state 3.5)
        [select-val set-select!] (use-state "junior")
        [tags-val set-tags!] (use-state #{"Nursing" "Biology"})
        [encrypted-val set-encrypted!] (use-state "s3cr3t-p@ss")]
    ($ gallery-section {:id "editable-fields" :title "Editable Fields"}
       ($ :p {:class "text-sm text-muted-foreground mb-4"}
          "Click-to-edit fields with auto-save on blur. The core interaction pattern for the CRM.")

       ($ specimen {:label "editable-text — Click the value to edit"}
          ($ :div {:class "max-w-xs"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Student Name")
             ($ :div {:class "mt-1"}
                ($ ef/editable-text {:value text-val :on-save set-text! :placeholder "Click to edit"}))))

       ($ specimen {:label "editable-text — Empty state"}
          ($ :div {:class "max-w-xs"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Preferred Name")
             ($ :div {:class "mt-1"}
                ($ ef/editable-text {:value nil :on-save identity :placeholder "Set preferred name"}))))

       ($ specimen {:label "editable-textarea — Multiline click-to-edit"}
          ($ :div {:class "max-w-md"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Open Response")
             ($ :div {:class "mt-1"}
                ($ ef/editable-textarea {:value textarea-val :on-save set-textarea! :placeholder "Click to edit..." :rows 3}))))

       ($ specimen {:label "editable-number — With min/max/step"}
          ($ :div {:class "max-w-xs"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "GPA")
             ($ :div {:class "mt-1"}
                ($ ef/editable-number {:value num-val :on-save set-num! :placeholder "--" :min 0 :max 5.0 :step 0.01}))))

       ($ specimen {:label "editable-select — Dropdown with keyword options"}
          ($ :div {:class "max-w-xs"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Grade Level")
             ($ :div {:class "mt-1"}
                ($ ef/editable-select {:value select-val
                                       :options ["freshman" "sophomore" "junior" "senior"]
                                       :placeholder "Select..."
                                       :as-string? true
                                       :on-save set-select!}))))

       ($ specimen {:label "editable-tag-select — Tags with multi-select"}
          ($ :div {:class "max-w-md"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Majors/Fields")
             ($ :div {:class "mt-1"}
                ($ ef/editable-tag-select {:value tags-val
                                           :options ["Nursing" "Biology" "Engineering" "Business" "Computer Science" "Psychology"]
                                           :placeholder "Add majors..."
                                           :on-save set-tags!}))))

       ($ specimen {:label "editable-encrypted — Masked field with reveal toggle"}
          ($ :div {:class "max-w-xs"}
             ($ :label {:class "text-sm font-medium text-muted-foreground"} "Gradebook Password")
             ($ :div {:class "mt-1"}
                ($ ef/editable-encrypted {:value encrypted-val :on-save set-encrypted! :placeholder "Enter password..."})))))))

;; =============================================================================
;; BRYC Components: Schema Vector Editor
;; =============================================================================

(def mock-course-schema
  {:type :vector
   :add-button-text "Add Course"
   :empty-message "No courses added yet."
   :item-label-key :name
   :item-schema
   {:type :map
    :fields [{:key :name :type :string :required true :label "Course Name" :placeholder "e.g., AP Chemistry"}
             {:key :grade :type :single-select :label "Status" :options ["In Progress" "Completed" "Planned"]}]}})

(defui schema-editor-section []
  (let [[items set-items!] (use-state [{:name "AP English Literature" :grade "Completed"}
                                       {:name "AP Calculus AB" :grade "In Progress"}])]
    ($ gallery-section {:id "schema-editor" :title "Schema Vector Editor"}
       ($ :p {:class "text-sm text-muted-foreground mb-4"}
          "Schema-driven add/edit/delete editor for structured data like AP courses, test scores, and employment records.")
       ($ specimen {:label "schema-vector-editor — With mock AP coursework data"}
          ($ :div {:class "max-w-lg"}
             ($ schema-vector-editor {:sub-schema mock-course-schema
                                      :value items
                                      :on-save set-items!}))))))

;; =============================================================================
;; BRYC Components: Layout Primitives
;; =============================================================================

(defui layout-section []
  ($ gallery-section {:id "layout" :title "Layout Primitives"}
     ($ specimen {:label "content-section — Card wrapper with title heading"}
        ($ content-section {:title "Example Section"}
           ($ :p {:class "text-sm text-muted-foreground"} "Content sections wrap tab content in a card with consistent heading style. Used throughout the CRM detail view.")))))

;; =============================================================================
;; BRYC Components: Avatar
;; =============================================================================

(defui avatar-section []
  ($ gallery-section {:id "avatar" :title "Avatar"}
     ($ :p {:class "text-sm text-muted-foreground mb-4"}
        "Hash-based color selection from a 12-color palette. Initials derived from the student's name.")
     ($ specimen {:label "student-avatar — Sizes sm, md, lg, xl"}
        ($ :div {:class "flex items-end gap-4"}
           ($ :div {:class "text-center"}
              ($ student-avatar {:name "Maria Garcia" :size :sm})
              ($ :p {:class "text-xs text-muted-foreground mt-1"} ":sm"))
           ($ :div {:class "text-center"}
              ($ student-avatar {:name "James Wilson" :size :md})
              ($ :p {:class "text-xs text-muted-foreground mt-1"} ":md"))
           ($ :div {:class "text-center"}
              ($ student-avatar {:name "Aisha Johnson" :size :lg})
              ($ :p {:class "text-xs text-muted-foreground mt-1"} ":lg"))
           ($ :div {:class "text-center"}
              ($ student-avatar {:name "Chen Wei" :size :xl})
              ($ :p {:class "text-xs text-muted-foreground mt-1"} ":xl"))))
     ($ specimen {:label "Color variation — Different names produce different colors"}
        ($ :div {:class "flex flex-wrap gap-3"}
           (for [name ["Alex" "Brianna" "Carlos" "Diana" "Emmanuel" "Fatima" "Garrett" "Hannah"]]
             ($ :div {:key name :class "text-center"}
                ($ student-avatar {:name name :size :md})
                ($ :p {:class "text-xs text-muted-foreground mt-1"} name)))))))

;; =============================================================================
;; Shared Library: Formatting Utils
;; =============================================================================

(defui formatting-section []
  ($ gallery-section {:id "formatting" :title "Formatting Utils"}
     ($ :p {:class "text-sm text-muted-foreground mb-4"}
        "Pure functions from components.shared.formatting — used across all CRM tabs, sidebar, and advisor views.")
     ($ specimen {:label "format-keyword — Converts kebab-case keywords to Title Case"}
        ($ :div {:class "space-y-1 font-mono text-sm"}
           ($ :p (str ":tops-award → \"" (fmt/format-keyword :tops-award) "\""))
           ($ :p (str ":needs-initial-text → \"" (fmt/format-keyword :needs-initial-text) "\""))
           ($ :p (str ":race_ethnicity → \"" (fmt/format-keyword :race_ethnicity) "\""))))
     ($ specimen {:label "format-phone — Formats digits into (504) 555-1234"}
        ($ :div {:class "space-y-1 font-mono text-sm"}
           ($ :p (str "\"5045551234\" → \"" (fmt/format-phone "5045551234") "\""))
           ($ :p (str "\"15045551234\" → \"" (fmt/format-phone "15045551234") "\""))
           ($ :p (str "\"504\" → \"" (fmt/format-phone "504") "\""))))))

;; =============================================================================
;; Shared Library: Form Modal
;; =============================================================================

(defui form-modal-section []
  (let [[open? set-open!] (use-state false)
        [submitted set-submitted!] (use-state nil)]
    ($ gallery-section {:id "form-modal-demo" :title "Form Modal"}
       ($ :p {:class "text-sm text-muted-foreground mb-4"}
          "Data-driven modal from components.shared.form-modal — used by all 6 advisor add/edit modals. Pass a config map of fields, validation, and handlers.")
       ($ specimen {:label "form-modal — Click to open a demo modal"}
          ($ :div
             ($ button/Button {:on-click #(set-open! true)} "Open Demo Modal")
             (when submitted
               ($ :p {:class "text-sm text-green-600 mt-2"}
                  (str "Submitted: " (pr-str submitted))))
             ($ form-modal
                {:open? open?
                 :submitting? false
                 :error nil
                 :title "Add Demo Item"
                 :description "This is a gallery demo of the shared form-modal component."
                 :fields [{:key :name :label "Name *" :type :text :placeholder "e.g., Jane Doe"}
                          {:key :role :label "Role" :type :select :options ["Student" "Advisor" "Admin"]}
                          {:key :notes :label "Notes" :type :textarea :rows 3 :placeholder "Any additional notes..."}]
                 :initial-state {:name "" :role "" :notes ""}
                 :valid? (fn [d] (seq (:name d)))
                 :on-submit (fn [d] (set-submitted! d) (set-open! false))
                 :on-close #(set-open! false)
                 :submit-label "Add Item"
                 :submitting-label "Adding..."}))))))

;; =============================================================================
;; Shared Library: Loading States
;; =============================================================================

(defui loading-states-section []
  (let [[demo-state set-demo-state!] (use-state :ready)]
    ($ gallery-section {:id "loading-states" :title "Loading States"}
       ($ :p {:class "text-sm text-muted-foreground mb-4"}
          "Shared loading/error/empty state components from components.shared.loading-states.")
       ($ specimen {:label "loading-spinner"}
          ($ loading-spinner {:text "Loading recommendations..."}))
       ($ specimen {:label "error-banner"}
          ($ error-banner {:error "Something went wrong. Please try again."}))
       ($ specimen {:label "empty-state"}
          ($ empty-state {:title "No Data Yet" :message "Content will appear here once it has been generated."}))
       ($ specimen {:label "async-content — Toggle between states"}
          ($ :div
             ($ :div {:class "flex gap-2 mb-3"}
                ($ button/Button {:size "sm" :variant (if (= demo-state :loading) "default" "outline")
                                  :on-click #(set-demo-state! :loading)} "Loading")
                ($ button/Button {:size "sm" :variant (if (= demo-state :error) "default" "outline")
                                  :on-click #(set-demo-state! :error)} "Error")
                ($ button/Button {:size "sm" :variant (if (= demo-state :ready) "default" "outline")
                                  :on-click #(set-demo-state! :ready)} "Ready"))
             ($ async-content
                {:loading? (= demo-state :loading)
                 :error (when (= demo-state :error) "Failed to load student data.")
                 :loading-text "Loading student..."}
                ($ :div {:class "p-4 bg-green-50 border border-green-200 rounded-lg text-sm"}
                   "Content loaded successfully. This is the ready state.")))))))

;; =============================================================================
;; shadcn/ui: Buttons
;; =============================================================================

(defui buttons-section []
  ($ gallery-section {:id "buttons" :title "Buttons"}
     ($ specimen {:label "Variants"}
        ($ :div {:class "flex flex-wrap gap-3"}
           ($ button/Button {:variant "default"} "Default")
           ($ button/Button {:variant "secondary"} "Secondary")
           ($ button/Button {:variant "outline"} "Outline")
           ($ button/Button {:variant "ghost"} "Ghost")
           ($ button/Button {:variant "destructive"} "Destructive")))
     ($ specimen {:label "Sizes"}
        ($ :div {:class "flex flex-wrap items-center gap-3"}
           ($ button/Button {:size "sm"} "Small")
           ($ button/Button {:size "default"} "Default")
           ($ button/Button {:size "lg"} "Large")))
     ($ specimen {:label "States"}
        ($ :div {:class "flex flex-wrap gap-3"}
           ($ button/Button {} "Enabled")
           ($ button/Button {:disabled true} "Disabled")))))

;; =============================================================================
;; shadcn/ui: Badges
;; =============================================================================

(defui badges-section []
  ($ gallery-section {:id "badges" :title "Badges"}
     ($ specimen {:label "Variants"}
        ($ :div {:class "flex flex-wrap gap-3"}
           ($ Badge {:variant "default"} "Default")
           ($ Badge {:variant "secondary"} "Secondary")
           ($ Badge {:variant "outline"} "Outline")
           ($ Badge {:variant "destructive"} "Destructive")))))

;; =============================================================================
;; shadcn/ui: Cards
;; =============================================================================

(defui cards-section []
  ($ gallery-section {:id "cards" :title "Cards"}
     ($ specimen {:label "Card with header, description, and content"}
        ($ :div {:class "max-w-sm"}
           ($ card/Card
              ($ card/CardHeader
                 ($ card/CardTitle "Student Overview")
                 ($ card/CardDescription "Summary of student progress and status."))
              ($ card/CardContent
                 ($ :p {:class "text-sm"} "Cards are the primary container for grouped content throughout the CRM.")))))))

;; =============================================================================
;; shadcn/ui: Inputs
;; =============================================================================

(defui inputs-section []
  ($ gallery-section {:id "inputs" :title "Inputs"}
     ($ specimen {:label "Text Input"}
        ($ :div {:class "max-w-xs"}
           ($ Input {:placeholder "Enter text..."})))
     ($ specimen {:label "Textarea"}
        ($ :div {:class "max-w-md"}
           ($ Textarea {:placeholder "Enter longer text..." :rows 3})))
     ($ specimen {:label "Select"}
        ($ :div {:class "max-w-xs"}
           ($ select/Select
              ($ select/SelectTrigger
                 ($ select/SelectValue {:placeholder "Select an option..."}))
              ($ select/SelectContent
                 ($ select/SelectItem {:value "option-1"} "Option 1")
                 ($ select/SelectItem {:value "option-2"} "Option 2")
                 ($ select/SelectItem {:value "option-3"} "Option 3")))))))

;; =============================================================================
;; shadcn/ui: Dialogs
;; =============================================================================

(defui dialogs-section []
  ($ gallery-section {:id "dialogs" :title "Dialogs"}
     ($ specimen {:label "Dialog — Click button to open"}
        ($ dialog/Dialog
           ($ dialog/DialogTrigger {:asChild true}
              ($ button/Button {:variant "outline"} "Open Dialog"))
           ($ dialog/DialogContent
              ($ dialog/DialogHeader
                 ($ dialog/DialogTitle "Confirm Action")
                 ($ dialog/DialogDescription "Are you sure you want to proceed? This action cannot be undone."))
              ($ :div {:class "flex justify-end gap-2 mt-4"}
                 ($ dialog/DialogClose {:asChild true}
                    ($ button/Button {:variant "outline"} "Cancel"))
                 ($ dialog/DialogClose {:asChild true}
                    ($ button/Button {} "Confirm"))))))))

;; =============================================================================
;; shadcn/ui: Tabs
;; =============================================================================

(defui tabs-section []
  ($ gallery-section {:id "tabs-demo" :title "Tabs"}
     ($ specimen {:label "Tab navigation"}
        ($ tabs/Tabs {:defaultValue "tab-1"}
           ($ tabs/TabsList
              ($ tabs/TabsTrigger {:value "tab-1"} "Profile")
              ($ tabs/TabsTrigger {:value "tab-2"} "Academics")
              ($ tabs/TabsTrigger {:value "tab-3"} "Senior Year"))
           ($ tabs/TabsContent {:value "tab-1"}
              ($ :p {:class "text-sm text-muted-foreground p-4"} "Profile tab content"))
           ($ tabs/TabsContent {:value "tab-2"}
              ($ :p {:class "text-sm text-muted-foreground p-4"} "Academics tab content"))
           ($ tabs/TabsContent {:value "tab-3"}
              ($ :p {:class "text-sm text-muted-foreground p-4"} "Senior Year tab content"))))))

;; =============================================================================
;; shadcn/ui: Alerts
;; =============================================================================

(defui alerts-section []
  ($ gallery-section {:id "alerts" :title "Alerts"}
     ($ specimen {:label "Info alert"}
        ($ alert/Alert
           ($ Info {:class "h-4 w-4"})
           ($ alert/AlertTitle "Information")
           ($ alert/AlertDescription "This is an informational alert message.")))
     ($ specimen {:label "Destructive alert"}
        ($ alert/Alert {:variant "destructive"}
           ($ AlertCircle {:class "h-4 w-4"})
           ($ alert/AlertTitle "Error")
           ($ alert/AlertDescription "Something went wrong. Please try again.")))))

;; =============================================================================
;; Main Gallery Page
;; =============================================================================

(defui gallery-page [{:keys []}]
  ($ :div {:class "flex h-screen bg-background"}
     ($ sidebar-nav)
     ($ :main {:class "flex-1 overflow-y-auto"}
        ($ :div {:class "px-8 py-6 max-w-5xl"}
           ($ :h1 {:class "text-2xl font-semibold mb-2"} "BRYC Design System")
           ($ :p {:class "text-muted-foreground mb-8"} "Living reference for design tokens, custom components, and shadcn/ui primitives.")

           ;; Design Principles
           ($ gallery-section {:id "principles" :title "Design Principles"}
              ($ :div {:class "grid grid-cols-1 sm:grid-cols-2 gap-4"}
                 (for [{:keys [title description]}
                       [{:title "Card-Forward"
                         :description "Every surface is a card. Subtle shadows, generous whitespace, rounded corners. The card is the interface."}
                        {:title "Pages Over Modals"
                         :description "Modals are for confirmations only. Every form and detail view gets a full page with room for contextual insight, related data, and intelligent suggestions."}
                        {:title "Dopamine-Inducing"
                         :description "Premium feel in every interaction. Typography, spacing, and depth are tuned to feel satisfying. If it doesn't spark joy, it's not done."}
                        {:title "Monochromatic Warmth"
                         :description "Black and white palette with warm undertones. Content is the color, not the chrome. The interface disappears so the work shines."}
                        {:title "Instrument Sans + JetBrains Mono"
                         :description "Clean, modern type pairing. Professional but not clinical. Readable at every size."}
                        {:title "Insights Not Information"
                         :description "Tables show insights that drive action, not raw data dumps. If a column doesn't help you make a decision at a glance, it belongs on the detail page."}
                        {:title "Clean Tables, Clickable Rows"
                         :description "No status columns, no inline action buttons. Rows are clickable and navigate to the detail page. Destructive actions live in the Danger Zone, not the table."}
                        {:title "Hierarchy Over Bombardment"
                         :description "Detail pages flow left to right, not top to bottom. Typography and layout carry the weight of the subject matter. Actions are presented as a menu — never all on screen at once."}]]
                   ($ :div {:key title :class "p-4 border rounded-lg bg-card"}
                      ($ :h3 {:class "text-sm font-semibold mb-1"} title)
                      ($ :p {:class "text-sm text-muted-foreground"} description)))))

           ($ Separator {:class "my-8"})

           ;; Foundations
           ($ :div {:id "foundations"})
           ($ colors-section)
           ($ typography-section)
           ($ spacing-section)

           ($ Separator {:class "my-8"})

           ;; BRYC Components
           ($ :div {:id "bryc-components"})
           ($ editable-fields-section)
           ($ schema-editor-section)
           ($ layout-section)
           ($ avatar-section)

           ($ Separator {:class "my-8"})

           ;; Shared Library
           ($ :div {:id "shared-lib"})
           ($ formatting-section)
           ($ form-modal-section)
           ($ loading-states-section)

           ($ Separator {:class "my-8"})

           ;; shadcn/ui
           ($ :div {:id "shadcn"})
           ($ buttons-section)
           ($ badges-section)
           ($ cards-section)
           ($ inputs-section)
           ($ dialogs-section)
           ($ tabs-section)
           ($ alerts-section)))))
