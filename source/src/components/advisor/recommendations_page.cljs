(ns components.advisor.recommendations-page
  (:require [uix.core :as uix :refer [defui $ use-effect use-state use-memo]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.advisor.authoring.selection :as sel]
            [components.advisor.recommendation-sidebar :refer [recommendation-sidebar student-profile-card
                                                              advisor-message-card]]
            [components.crm.student.detail-view :refer [recommendation-alert]]
            [components.advisor.college-table-panel :refer [college-table-panel]]
            [components.advisor.institution-detail-sheet :refer [institution-detail-sheet]]
            [components.advisor.apprenticeship-table-panel :refer [apprenticeship-table-panel]]
            [components.advisor.scholarship-table-panel :refer [scholarship-table-panel]]
            [components.advisor.short-term-program-table-panel :refer [short-term-program-table-panel]]
            [components.advisor.share-student-link-modal :refer [share-student-link-modal]]
            [components.advisor.add-custom-institution-modal :refer [add-custom-institution-modal]]
            [components.advisor.add-custom-program-modal :refer [add-custom-program-modal]]
            [components.advisor.add-custom-scholarship-modal :refer [add-custom-scholarship-modal]]
            [components.advisor.add-custom-apprenticeship-modal :refer [add-custom-apprenticeship-modal]]
            [components.advisor.add-custom-short-term-program-modal :refer [add-custom-short-term-program-modal]]
            [components.context.interface :as context]
            [components.student.recommendations-page :refer [recommendations-page] :rename {recommendations-page student-recommendations-page}]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [store.student.recommendations.events :as student-events]
            [components.shared.loading-states :refer [loading-spinner error-banner empty-state]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/tabs" :as tabs]))

(defn- sidebar-toggle-icon []
  ($ :svg {:xmlns "http://www.w3.org/2000/svg" :width "16" :height "16" :viewBox "0 0 24 24"
           :fill "none" :stroke "currentColor" :strokeWidth "2"
           :strokeLinecap "round" :strokeLinejoin "round"}
     ($ :rect {:width "18" :height "18" :x "3" :y "3" :rx "2"})
     ($ :path {:d "M9 3v18"})))

(defui edit-preview-toggle
  "Edit ⇄ Student-preview switch for the dense editor (0051). Edit shows the dense
   editor; Student preview shows the REAL redesigned student page (read-only) for the
   current advisee. Pure presentational; the parent owns the preview? state."
  [{:keys [preview? on-edit on-preview]}]
  ($ :div {:class "inline-flex rounded-full bg-white ring-1 ring-[#a9d8de] p-0.5"
           :data-overlay "edit-preview-toggle" :data-preview-mode (str (boolean preview?))}
     ($ :button {:class (str "px-3 py-1 rounded-full text-xs font-semibold transition-colors "
                             (if preview? "text-[#2a6465]" "bg-[#05a09c] text-white"))
                 :data-action "edit-mode" :on-click on-edit}
        "✎ Edit")
     ($ :button {:class (str "px-3 py-1 rounded-full text-xs font-semibold transition-colors "
                             (if preview? "bg-[#2a6465] text-white" "text-[#2a6465]"))
                 :data-action "preview-mode" :on-click on-preview}
        "👁 Student preview")))

(defui recommendations-page [{:keys [current-match]}]
  (let [student-id (parse-uuid (get-in current-match [:query-params :student-id]))
        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        ;; Subscriptions
        loading? (use-subscribe [::subs/loading?])
        error (use-subscribe [::subs/error])
        pool (use-subscribe [::subs/pool])
        active-tab (use-subscribe [::subs/active-tab])
        save-status (use-subscribe [::subs/save-status])

        ;; Recommendation status
        student (use-subscribe [::subs/student])
        rec-status (use-subscribe [::subs/recommendation-status])
        regenerating? (use-subscribe [::subs/regenerating-recommendations?])

        ;; Share link modal
        show-link-modal? (use-subscribe [::subs/show-link-modal?])
        student-view-link (use-subscribe [::subs/student-view-link])
        generating-link? (use-subscribe [::subs/generating-link?])

        ;; In-page Student preview (0051) — the advisor's LIVE resolved selection +
        ;; advisor message. The backend-loaded `::subs/resolved` is only written on load
        ;; and is NOT recomputed when the advisor edits the selection, so previewing off it
        ;; showed a STALE list (a just-selected school never appeared). Instead we resolve
        ;; the LIVE `::pool` + `::selection` here — the same source of truth the dense editor
        ;; edits — via the PURE `sel/resolve-selection` (a cljs mirror of the backend
        ;; queries/resolve-selection). We preserve only `:student` (the plumbed advisee name
        ;; for the hero) off the loaded resolved, since it is not selection-derived.
        selection (use-subscribe [::subs/selection])
        loaded-resolved (use-subscribe [::subs/resolved])
        resolved (use-memo
                  (fn []
                    (when (and pool selection)
                      (assoc (sel/resolve-selection pool selection)
                             :student (:student loaded-resolved))))
                  [pool selection loaded-resolved])
        advisor-message (use-subscribe [::subs/advisor-message])
        ;; The advisee's assigned advisor identity (0059) — resolved server-side from
        ;; the SAME "advises" relationship the token student view uses, so the
        ;; in-editor Student preview shows the identical advisor card. Threaded into
        ;; the seed below; present-by-data (nil ⇒ the card is omitted, no crash).
        advisor (use-subscribe [::subs/advisor])

        ;; Sidebar state
        [sidebar-open? set-sidebar-open!] (use-state true)
        ;; Edit ⇄ Student-preview mode (0051). Edit is the dense editor; preview renders
        ;; the REAL redesigned student page read-only for the current advisee.
        [preview? set-preview!] (use-state false)]

    ;; Load recommendations on mount
    (use-effect
     (fn []
       (when student-id
         (rf/dispatch [::events/load-recommendations student-id api-client]))
       js/undefined)
     [student-id api-client])

    ;; Seed the student store whenever preview is entered (and re-seed if the advisor's
    ;; resolved/message change while previewing) so the preview always reflects the
    ;; advisor's LATEST selection. Writes only under [:student :recommendations …] — the
    ;; advisor store is untouched.
    (use-effect
     (fn []
       (when preview?
         (rf/dispatch [::student-events/seed-preview
                       {:resolved resolved
                        :advisor advisor
                        :advisor-message advisor-message}]))
       js/undefined)
     [preview? resolved advisor advisor-message])

    ($ :div {:class "flex flex-col min-h-screen bg-background"}

       ;; Header
       ($ :div {:class "bg-card border-b px-6 py-3 flex-shrink-0"}
          ($ :div {:class "flex items-center justify-between"}
             ;; Save status
             ($ :div {:class "flex items-center gap-2"}
                (when (not loading?)
                  (cond
                    (or (= save-status "pending") (= save-status "saving"))
                    ($ :span {:class "text-xs text-blue-600"} "Saving...")

                    (= save-status "saved")
                    ($ :span {:class "text-xs text-green-600"} "All changes saved")

                    (= save-status "error")
                    ($ :span {:class "text-xs text-red-600"} "Save failed"))))

             ($ :div {:class "flex items-center gap-2"}
                ;; Edit ⇄ Student-preview toggle (0051) — only meaningful once there's a
                ;; resolved selection to preview.
                (when (and (not loading?) pool)
                  ($ edit-preview-toggle {:preview? preview?
                                          :on-edit #(set-preview! false)
                                          :on-preview #(set-preview! true)}))
                ;; Sidebar toggle in header (Edit mode only — the preview is the full-width
                ;; student page).
                (when (and (not loading?) (not preview?))
                  ($ button/Button {:variant "ghost" :size "icon"
                                    :on-click #(set-sidebar-open! not)
                                    :title (if sidebar-open? "Hide student profile" "Show student profile")
                                    :class "h-8 w-8"}
                     (sidebar-toggle-icon)))
                (when (and (not loading?) pool)
                  ($ button/Button
                     {:variant "outline" :size "sm"
                      :on-click #(rf/dispatch [::events/generate-student-view-link api-client])}
                     "Share with Student")))))

       ;; Content
       ($ :div {:class "flex-1 overflow-hidden"}
          (cond
           ;; Student preview (0051) — the REAL redesigned student page, read-only, for the
           ;; current advisee, driven by the student store we seed above. No editing
           ;; affordances render (it's the student component, not the dense editor).
           (and preview? (not loading?))
           ($ :div {:class "h-full overflow-y-auto"}
              ($ student-recommendations-page {:embedded? true}))

           loading?
           ($ :div {:class "p-6"}
              ($ loading-spinner {:text "Loading recommendations..."}))

           error
           ($ :div {:class "p-6"}
              ($ error-banner {:error error}))

           :else
           (let [has-pool? (and pool
                                (or (seq (get-in pool [:institutions :safety] []))
                                    (seq (get-in pool [:institutions :target] []))
                                    (seq (get-in pool [:institutions :reach] []))
                                    (seq (get pool :scholarships []))
                                    (seq (get pool :apprenticeships []))
                                    (seq (get pool :short-term-programs []))))]
             ($ :div {:class "flex h-full"}
                ;; Collapsible Left Sidebar
                ($ :div {:class (str "flex-shrink-0 transition-[width] duration-200 ease-in-out overflow-hidden border-r "
                                     (if sidebar-open? "w-[340px]" "w-0 border-r-0"))}
                   ($ :div {:class "w-[340px] h-full overflow-y-auto p-4"}
                      ;; Recommendation status alert
                      (when (and (not loading?) student)
                        ($ recommendation-alert {:student student
                                                 :api-client api-client
                                                 :rec-status rec-status
                                                 :regenerating? regenerating?}))
                      (if has-pool?
                        ($ recommendation-sidebar {:api-client api-client})
                        ($ student-profile-card {:api-client api-client}))))

                ;; Main Content
                ($ :div {:class "flex-1 overflow-y-auto min-w-0 p-6"}
                   (if has-pool?
                     ($ :<>
                        ;; Advisor Message above tabs
                        ($ :div {:class "mb-4"}
                           ($ advisor-message-card {:api-client api-client}))

                        ($ tabs/Tabs {:value active-tab
                                      :on-value-change #(rf/dispatch [::events/set-active-tab %])}
                           ($ tabs/TabsList {:class "mb-4"}
                              ($ tabs/TabsTrigger {:value "colleges"} "Colleges")
                              ($ tabs/TabsTrigger {:value "apprenticeships"} "Apprenticeships")
                              ($ tabs/TabsTrigger {:value "short-term-programs"} "Short-Term")
                              ($ tabs/TabsTrigger {:value "scholarships"} "Scholarships"))

                           ($ tabs/TabsContent {:value "colleges"}
                              ($ college-table-panel {:api-client api-client}))

                           ($ tabs/TabsContent {:value "apprenticeships"}
                              ($ apprenticeship-table-panel {:api-client api-client}))

                           ($ tabs/TabsContent {:value "short-term-programs"}
                              ($ short-term-program-table-panel {:api-client api-client}))

                           ($ tabs/TabsContent {:value "scholarships"}
                              ($ scholarship-table-panel {:api-client api-client})))
                        ($ institution-detail-sheet {:api-client api-client}))
                     ($ :div {:class "flex flex-col items-center justify-center py-20 gap-3"}
                        ($ :div {:class "rounded-full bg-muted p-4 mb-2"}
                           ($ :svg {:class "w-8 h-8 text-muted-foreground" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "1.5"}
                              ($ :path {:strokeLinecap "round" :strokeLinejoin "round"
                                        :d "M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z"})))
                        ($ :h3 {:class "text-lg font-semibold"} "No Recommendations Yet")
                        ($ :p {:class "text-sm text-muted-foreground text-center max-w-sm"}
                           "Personalized college, apprenticeship, and scholarship recommendations will appear here once they have been generated for this student."))))))))

       ;; Modals
       ($ share-student-link-modal
          {:open? show-link-modal?
           :link student-view-link
           :generating? generating-link?
           :on-close #(rf/dispatch [::events/close-link-modal])})
       ($ add-custom-institution-modal)
       ($ add-custom-program-modal)
       ($ add-custom-scholarship-modal)
       ($ add-custom-apprenticeship-modal)
       ($ add-custom-short-term-program-modal))))
