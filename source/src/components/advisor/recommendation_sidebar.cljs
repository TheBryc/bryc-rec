(ns components.advisor.recommendation-sidebar
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [components.crm.student.field-registry :refer [field-configs student-field]]
            [components.shared.field-error :refer [field-error]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/collapsible" :as collapsible]))

;; =============================================================================
;; Layout Helpers
;; =============================================================================

(defui compact-field
  "Compact label/value row. Label on top, value directly below with minimal spacing."
  [{:keys [label value]}]
  ($ :div {:class "py-1.5"}
     ($ :dt {:class "text-xs text-muted-foreground mb-0.5"} label)
     ($ :dd {:class "text-sm"} (or value "—"))))

(defn- chevron-down-icon [{:keys [open?]}]
  ($ :svg {:xmlns "http://www.w3.org/2000/svg" :width "12" :height "12" :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor" :strokeWidth "2" :strokeLinecap "round" :strokeLinejoin "round"
           :class (str "transition-transform duration-200 " (when-not open? "-rotate-90"))}
     ($ :polyline {:points "6 9 12 15 18 9"})))

(defui collapsible-section [{:keys [title default-open children]}]
  (let [[open? set-open!] (use-state (boolean default-open))]
    ($ :div {:class "border-b border-border/50 pb-3 last:border-b-0 last:pb-0"}
       ($ collapsible/Collapsible {:open open? :onOpenChange set-open!}
          ($ collapsible/CollapsibleTrigger {:asChild true}
             ($ :button {:class "flex items-center gap-1.5 w-full text-xs font-medium text-muted-foreground uppercase tracking-wide hover:text-foreground transition-colors py-1.5"
                         :type "button"}
                (chevron-down-icon {:open? open?})
                title))
          ($ collapsible/CollapsibleContent {:class "pt-1"}
             children)))))

;; =============================================================================
;; Editable Field Helper
;; =============================================================================

(defui sidebar-field
  "Renders a compact field row with the shared student-field renderer.
   Subscribes to field options from the shared CRM state."
  [{:keys [field-slug student saving? on-save field-values]}]
  (let [config (get field-configs field-slug)
        options (use-subscribe [::student-subs/field-options field-slug])]
    (when config
      ($ compact-field
         {:label (:label config)
          :value ($ :<>
                    ($ student-field {:field-slug field-slug
                                     :student student
                                     :field-values field-values
                                     :options options
                                     :saving? saving?
                                     :on-save on-save})
                    ($ field-error {:field-slug field-slug}))}))))

;; =============================================================================
;; Student Profile Card — Recommendation Inputs
;; =============================================================================

(defui student-profile-card [{:keys [api-client]}]
  (let [;; Prefer CRM student (loaded by student-page-wrapper) for consistent field access
        crm-student (use-subscribe [::student-subs/student])
        rec-student (use-subscribe [::subs/student])
        student (or crm-student rec-student)
        student-id (:student/id student)
        field-values (or (use-subscribe [::student-subs/field-values]) {})
        saving? (use-subscribe [::student-subs/saving?])

        save-field (fn [field-slug value]
                     (rf/dispatch [::student-events/save-field-for-student
                                   student-id field-slug value api-client
                                   {:on-success [::events/reload-recommendations student-id api-client]}]))]

    (when student
      ($ :div {:class "space-y-4"}
         ;; Title
         ($ :div {:class "pb-2 mb-1 border-b"}
            ($ :h3 {:class "text-sm font-semibold"} "Recommendation Inputs")
            ($ :p {:class "text-xs text-muted-foreground"} "Fields that drive AI recommendations"))

         ;; Academics
         ($ collapsible-section {:title "Academics" :default-open true}
            ($ :dl
               ($ sidebar-field {:field-slug "weighted-core-gpa" :student student :field-values field-values :saving? saving? :on-save #(save-field "weighted-core-gpa" %)})
               ($ sidebar-field {:field-slug "act-score" :student student :field-values field-values :saving? saving? :on-save #(save-field "act-score" %)})
               ($ sidebar-field {:field-slug "tops-award" :student student :field-values field-values :saving? saving? :on-save #(save-field "tops-award" %)})
               ($ sidebar-field {:field-slug "work-keys-score" :student student :field-values field-values :saving? saving? :on-save #(save-field "work-keys-score" %)})
               ($ sidebar-field {:field-slug "color-profile" :student student :field-values field-values :saving? saving? :on-save #(save-field "color-profile" %)})))

         ;; Career & Goals
         ($ collapsible-section {:title "Career & Goals" :default-open true}
            ($ :dl
               ($ sidebar-field {:field-slug "career-fields" :student student :field-values field-values :saving? saving? :on-save #(save-field "career-fields" %)})
               ($ sidebar-field {:field-slug "post-hs-goals" :student student :field-values field-values :saving? saving? :on-save #(save-field "post-hs-goals" %)})
               ($ sidebar-field {:field-slug "schools-of-interest" :student student :field-values field-values :saving? saving? :on-save #(save-field "schools-of-interest" %)})
               ($ sidebar-field {:field-slug "preferences" :student student :field-values field-values :saving? saving? :on-save #(save-field "preferences" %)})))

         ;; Narrative
         ($ collapsible-section {:title "Narrative" :default-open true}
            ($ :dl
               ($ sidebar-field {:field-slug "open-response" :student student :field-values field-values :saving? saving? :on-save #(save-field "open-response" %)})
               ($ sidebar-field {:field-slug "summary" :student student :field-values field-values :saving? saving? :on-save #(save-field "summary" %)})
               ($ sidebar-field {:field-slug "narrative-additions" :student student :field-values field-values :saving? saving? :on-save #(save-field "narrative-additions" %)})))

         ;; Profile
         ($ collapsible-section {:title "Profile" :default-open true}
            ($ :dl
               ($ sidebar-field {:field-slug "gender" :student student :field-values field-values :saving? saving? :on-save #(save-field "gender" %)})
               ($ sidebar-field {:field-slug "pronouns" :student student :field-values field-values :saving? saving? :on-save #(save-field "pronouns" %)})
               ($ sidebar-field {:field-slug "race-ethnicity" :student student :field-values field-values :saving? saving? :on-save #(save-field "race-ethnicity" %)})
               ($ sidebar-field {:field-slug "primary-language" :student student :field-values field-values :saving? saving? :on-save #(save-field "primary-language" %)})))

         ;; Engagement
         ($ collapsible-section {:title "Engagement" :default-open true}
            ($ :dl
               ($ sidebar-field {:field-slug "involvement-score" :student student :field-values field-values :saving? saving? :on-save #(save-field "involvement-score" %)})
               ($ sidebar-field {:field-slug "activities" :student student :field-values field-values :saving? saving? :on-save #(save-field "activities" %)})))))))

;; =============================================================================
;; Main Sidebar
;; =============================================================================

(defui advisor-message-card [{:keys [api-client]}]
  (let [advisor-message (use-subscribe [::subs/advisor-message])
        [local-message set-local-message!] (use-state nil)]

    ;; Sync local message with subscription
    (use-effect
     (fn []
       (set-local-message! advisor-message)
       js/undefined)
     [advisor-message])

    ($ card/Card {:class "bg-card border"}
       ($ card/CardHeader {:class "pb-3"}
          ($ card/CardTitle {:class "text-sm font-medium"} "Advisor Message"))
       ($ card/CardContent {:class "pt-0"}
          (let [display-value (or local-message advisor-message "")]
            ($ :div {:class "grid after:content-[attr(data-value)] after:whitespace-pre-wrap after:invisible after:text-sm after:leading-relaxed [grid-template-areas:'textarea'] after:[grid-area:textarea] after:p-3 after:border-2 after:border-transparent"
                     :data-value (str display-value " ")}
               ($ :textarea
                  {:value display-value
                   :class "w-full text-sm text-foreground leading-relaxed bg-transparent border rounded-md resize-none focus:outline-none focus:ring-2 focus:ring-ring p-3 [grid-area:textarea] overflow-hidden min-h-[120px]"
                   :placeholder "Write a personalized message to the student..."
                   :on-change (fn [e]
                                (let [new-val (.. e -target -value)]
                                  (set-local-message! new-val)
                                  (rf/dispatch [::events/edit-advisor-message new-val api-client])))})))))))

(defui recommendation-sidebar [{:keys [api-client]}]
  ($ student-profile-card {:api-client api-client}))
