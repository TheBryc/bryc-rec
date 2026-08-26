(ns components.crm.student.fullpage-view
  "Full-page scrollable student detail view.
   Top-level toggle between Profile (all fields) and Communications.
   Profile view shows all sections simultaneously with sticky TOC rail."
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-callback]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.detail-view :refer [student-header recommendation-alert]]
            [components.crm.student.section-block :refer [section-block]]
            [components.crm.student.section-toc :refer [section-toc]]
            [components.crm.student.tabs.core :as sections]
            [components.crm.student.tabs.status :refer [status-tab]]
            [components.crm.student.tabs.school :refer [school-tab]]
            [components.crm.student.tabs.academics :refer [academics-tab]]
            [components.crm.student.tabs.testing :refer [testing-tab]]
            [components.crm.student.tabs.senior-year :refer [senior-year-tab]]
            [components.crm.student.tabs.admin :refer [admin-tab]]
            [components.crm.student.tabs.financial-aid :refer [financial-aid-tab]]
            [components.crm.student.tabs.postsecondary :refer [postsecondary-tab]]
            [components.crm.student.tabs.career :refer [career-tab]]
            [components.crm.student.tabs.family :refer [family-tab]]
            [components.crm.student.tabs.activities :refer [activities-tab]]
            [components.crm.student.tabs.community :refer [community-tab]]
            [components.crm.student.tabs.credentials :refer [credentials-tab]]
            [components.crm.student.tabs.online-presence :refer [online-presence-tab]]
            [components.crm.student.tabs.bryc-program :refer [bryc-program-tab]]
            [components.crm.student.communications :refer [log-communication-modal communications-section]]
            [components.crm.student.status :as status]
            [components.context.interface :as context]
            [store.crm.student.subs :as student-subs]))

(defn- view-key [initial-view initial-section]
  (case (or initial-view initial-section)
    "communications" :communications
    "attendance" :attendance
    "case-management" :case-management
    "involvement-awards" :involvement-awards
    :profile))

(defui obvious-placeholder [{:keys [title body]}]
  ($ :div {:class "rounded-md border border-dashed bg-muted/20 p-6"}
     ($ :h2 {:class "text-lg font-semibold"} title)
     ($ :p {:class "mt-2 max-w-2xl text-sm text-muted-foreground"} body)))

(defui fellow-attendance-view [{:keys [_student _field-values _api-client]}]
  ($ obvious-placeholder
     {:title "Attendance log placeholder"
      :body "Individual Fellow attendance will appear here once attendance log data is connected. Attendance can still be updated from the Attendance Dashboard roster flow."}))

(defui fellow-case-management-view [{:keys [_student _field-values _api-client]}]
  ($ obvious-placeholder
     {:title "Cases placeholder"
      :body "Open and closed Fellow cases will appear here. Use the term Cases for this surface; this is intentionally not labeled Interactions."}))

(defui fellow-involvement-awards-view [props]
  ($ :div {:class "space-y-6"}
     ($ activities-tab props)
     ($ obvious-placeholder
        {:title "Awards and honors placeholder"
         :body "Awards, honors, and non-BRYC involvement should be shown here when those fields are available. Existing senior-year honors remain available in the Senior Year profile section."})))

;; =============================================================================
;; Section renderer — maps section ID to its tab component
;; =============================================================================

(defn render-section
  "Render the content for a given section ID."
  [section-id props]
  (case section-id
    "status"          ($ status-tab props)
    "personal"        ($ (sections/personal-profile-component
                          (get (:field-values props) :bryc-status))
                         props)
    "school"          ($ school-tab props)
    "academics"       ($ academics-tab props)
    "testing"         ($ testing-tab props)
    "senior-year"     ($ senior-year-tab props)
    "family"          ($ family-tab props)
    "activities"      ($ activities-tab props)
    "financial-aid"   ($ financial-aid-tab props)
    "postsecondary"   ($ postsecondary-tab props)
    "career"          ($ career-tab props)
    "credentials"     ($ credentials-tab props)
    "health"          ($ admin-tab props)
    "community"       ($ community-tab props)
    "online-presence" ($ online-presence-tab props)
    "bryc-program"    ($ bryc-program-tab props)
    nil))

;; =============================================================================
;; Fullpage View
;; =============================================================================

(defui student-fullpage-view
  "Full-page student detail with top-level view toggle.
   Profile view: all field sections with sticky TOC.
   Communications view: full-width communications panel.
   Props:
   - student: student data map
   - initial-section: optional section ID to scroll to on mount"
  [{:keys [student initial-section initial-view]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        field-values (or (use-subscribe [::student-subs/field-values]) {})
        bryc-status (get field-values :bryc-status)
        all-visible (sections/visible-sections bryc-status)
        ;; Filter out communications — it gets its own top-level view
        visible (filterv #(not= "communications" (:id %)) all-visible)

        fellow? (status/status-in? sections/fellow-profile-statuses bryc-status)
        ;; Which view: from URL ?view= param. Fellow profiles get additional top-level views.
        active-view (if fellow?
                      (view-key initial-view initial-section)
                      (if (or (= initial-view "communications")
                              (= initial-section "communications"))
                        :communications
                        :profile))

        ;; Collapse state: set of expanded section IDs (all expanded by default)
        [expanded-ids set-expanded-ids!] (use-state (set (map :id visible)))
        all-collapsed? (empty? expanded-ids)

        ;; Active section for TOC highlighting
        [active-section set-active-section!] (use-state (or (when (not= initial-section "communications")
                                                              initial-section)
                                                            (first (map :id visible))))

        ;; Toggle a single section
        toggle-section (use-callback
                        (fn [section-id]
                          (set-expanded-ids!
                           (fn [ids]
                             (if (contains? ids section-id)
                               (disj ids section-id)
                               (conj ids section-id)))))
                        [])

        ;; Toggle all sections
        toggle-all (use-callback
                    (fn []
                      (set-expanded-ids!
                       (fn [ids]
                         (if (empty? ids)
                           (set (map :id visible))
                           #{}))))
                    [visible])

        ;; Click TOC to scroll to section
        scroll-to-section (use-callback
                           (fn [section-id]
                             ;; Ensure section is expanded before scrolling
                             (set-expanded-ids! (fn [ids] (conj ids section-id)))
                             (js/setTimeout
                              (fn []
                                (when-let [el (js/document.getElementById (str "section-" section-id))]
                                  (.scrollIntoView el #js {:behavior "smooth" :block "start"})))
                              50))
                           [])

        ;; Tab props shared by all sections
        tab-props {:student student :field-values field-values :api-client api-client}]

    ;; When the loaded BRYC status changes the profile flavor, make newly
    ;; visible sections expanded by default.
    (use-effect
     (fn []
       (set-expanded-ids! (fn [ids] (into ids (map :id visible))))
       js/undefined)
     [visible])

    ;; IntersectionObserver for scroll tracking (only when profile view is active)
    (use-effect
     (fn []
       (when (= active-view :profile)
         (let [observer (js/IntersectionObserver.
                         (fn [entries]
                           (doseq [entry entries]
                             (when (.-isIntersecting entry)
                               (let [^js target (.-target entry)]
                                 (set-active-section! (.. target -dataset -sectionId))))))
                         #js {:rootMargin "-15% 0px -75% 0px"
                              :threshold 0})]
           ;; Observe all section elements
           (doseq [{:keys [id]} visible]
             (when-let [el (js/document.getElementById (str "section-" id))]
               (.observe observer el)))
           ;; Cleanup
           (fn [] (.disconnect observer)))))
     [visible active-view])

    ;; Scroll to initial section on mount
    (use-effect
     (fn []
       (when (and initial-section (not= initial-section "communications"))
         (js/setTimeout
          (fn []
            (when-let [el (js/document.getElementById (str "section-" initial-section))]
              (.scrollIntoView el #js {:behavior "smooth" :block "start"})))
          200))
       js/undefined)
     [initial-section])

    ($ :div {:class "flex flex-col h-full min-h-0"}
       ;; Sticky header with tab nav
       ($ :div {:class "sticky top-0 z-20 border-b bg-card/95 backdrop-blur-sm"}
          ($ :div {:class "p-6"}
             ($ student-header {:student student
                                :field-values field-values
                                :active-tab active-view})))

       ;; Recommendation alert (profile view only)
       (when (= active-view :profile)
         ($ :div {:class "px-6 pt-4"}
            ($ recommendation-alert {:student student :api-client api-client})))

       ;; Body
       ($ :div {:class "flex-1 px-6 pb-6"}
          (case active-view
            ;; Profile: TOC + all field sections
            :profile
            ($ :div {:class "flex gap-6 pt-4"}
               ;; All field sections
               ($ :div {:class "flex-1 min-w-0"}
                  (for [{:keys [id label]} visible]
                    ($ section-block
                       {:key id
                        :id id
                        :title label
                        :collapsed? (not (contains? expanded-ids id))
                        :on-toggle #(toggle-section id)}
                       (render-section id tab-props))))

               ;; TOC rail (right side)
               ($ section-toc {:sections visible
                               :active-section-id active-section
                               :all-collapsed? all-collapsed?
                               :on-toggle-all toggle-all
                               :on-click scroll-to-section}))

            ;; Communications: read-only communication history (broadcasts + replies + logged).
            :communications
            ($ :div {:class "pt-4"}
               ($ communications-section {:student-id (:student/id student)
                                          :api-client api-client}))

            :attendance
            ($ :div {:class "pt-4"}
               ($ fellow-attendance-view tab-props))

            :case-management
            ($ :div {:class "pt-4"}
               ($ fellow-case-management-view tab-props))

            :involvement-awards
            ($ :div {:class "pt-4"}
               ($ fellow-involvement-awards-view tab-props))

            nil))

       ;; Modal always mounted so header button works from any view
       ($ log-communication-modal {:api-client api-client}))))
