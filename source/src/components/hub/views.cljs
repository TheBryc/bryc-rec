(ns components.hub.views
  "Hub page views that wrap content components with hub-layout."
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/button" :as button]
            [components.shared.loading-states :refer [loading-spinner]]
            [components.hub.core :refer [hub-layout]]
            [components.advisor.interface :as advisor]
            [components.advisor.authoring.view :as authoring]
            [components.fellows.interface :as fellows]
            [components.crm.student.interface :as crm-student]
            [components.crm.duplicates :refer [duplicate-review-page]]
            [components.crm.student.detail-view :refer [student-header]]
            [components.crm.student.communications :refer [log-communication-modal]]
            [components.crm.guardian.profile :refer [guardian-profile]]
            [components.crm.staff :as staff]
            [components.comms.broadcast-dashboard :refer [broadcast-dashboard]]
            [components.comms.templates.library :refer [templates-library]]
            [components.comms.mass-send.wizard :refer [mass-send-wizard]]
            [components.comms.mass-send.delivery-report :refer [delivery-report]]
            [components.context.interface :as context]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]
            [store.crm.guardian.core :as guardian-store]))

;; Dashboard page (coming soon placeholder)
(defui hub-dashboard [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-dashboard}
     ($ :div {:class "flex flex-col items-center justify-center h-64 text-center"}
        ($ :div {:class "rounded-full bg-muted p-4 mb-4"}
           ($ :svg {:class "w-8 h-8 text-muted-foreground" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "1.5"}
              ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d "M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z"})))
        ($ :h2 {:class "text-xl font-semibold mb-2"} "Dashboard")
        ($ :p {:class "text-muted-foreground"} "Coming soon"))))

;; Advising page (dashboard with student list)
(defui hub-advising [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-advising}
     ($ advisor/advisor-content {:student-route-name :hub-student-detail
                                 :current-match current-match})))

;; Fellows page (fellows dashboard with PM management)
(defui hub-fellows [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-fellows}
     ($ fellows/fellows-content {:student-route-name :hub-student-detail
                                  :current-match current-match})))

;; Students page (coming soon placeholder)
(defui hub-students [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-students}
     ($ :div {:class "flex flex-col items-center justify-center h-64 text-center"}
        ($ :div {:class "rounded-full bg-muted p-4 mb-4"}
           ($ :svg {:class "w-8 h-8 text-muted-foreground" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "1.5"}
              ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d "M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z"})))
        ($ :h2 {:class "text-xl font-semibold mb-2"} "Students")
        ($ :p {:class "text-muted-foreground"} "Coming soon"))))

(defui hub-duplicates [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-duplicates}
     ($ duplicate-review-page)))

;; Student detail page - uses new CRM two-column layout
;; Uses screen query pattern - one query returns all screen data
(defui hub-student-detail [{:keys [current-match]}]
  (let [student-id (get-in current-match [:query-params :student-id])
        student (use-subscribe [::student-subs/student])
        loading? (use-subscribe [::student-subs/loading?])

        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)]

    ;; Load entire screen data with one query
    (use-effect
      (fn []
        (when student-id
          (rf/dispatch [::student-events/set-student-id (parse-uuid student-id)])
          (rf/dispatch [::student-events/load-screen (parse-uuid student-id) api-client]))
        js/undefined)
      [student-id api-client])

    ($ hub-layout {:active-route :hub-student-detail}
       (cond
         loading?
         ($ loading-spinner {:text "Loading student..."})

         student
         ($ crm-student/student-fullpage-view {:student student
                                                    :initial-section (get-in current-match [:query-params :section])
                                                    :initial-view (get-in current-match [:query-params :view])})

         :else
         ($ :div {:class "flex items-center justify-center h-64"}
            ($ :div {:class "text-center"}
               ($ :p {:class "text-muted-foreground mb-4"} "Student not found")
               ($ button/Button {:on-click #(navigate! :hub-advising)}
                  "Back to Dashboard")))))))

;; Shared student page wrapper — loads student data and renders consistent header
;; for sub-pages (meetings, transcript, recommendations).
(defui student-page-wrapper
  "Wraps sub-pages with consistent student header + tab nav.
   Props:
   - current-match: route match
   - active-tab: keyword for which tab to highlight
   - children: page content"
  [{:keys [current-match active-tab children]}]
  (let [student-id (get-in current-match [:query-params :student-id])
        student (use-subscribe [::student-subs/student])
        field-values (or (use-subscribe [::student-subs/field-values]) {})
        loading? (use-subscribe [::student-subs/loading?])
        ctx (context/use-context)
        api-client (:api/client ctx)]

    ;; Ensure student data is loaded
    (use-effect
      (fn []
        (when student-id
          (rf/dispatch [::student-events/set-student-id (parse-uuid student-id)])
          (rf/dispatch [::student-events/load-screen (parse-uuid student-id) api-client]))
        js/undefined)
      [student-id api-client])

    ($ :<>
       ;; Student header with tab nav
       (when (and student (not loading?))
         ($ :div {:class "border-b bg-card p-6"}
            ($ student-header {:student student
                               :field-values field-values
                               :active-tab active-tab})))
       ;; Page content
       children
       ;; Log communication modal — always mounted so header button works
       ($ log-communication-modal {:api-client api-client}))))

;; Meetings page wrapper
(defui hub-meetings [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-student-detail}
     ($ student-page-wrapper {:current-match current-match :active-tab :meetings}
        ($ advisor/meetings-page {:current-match current-match}))))

;; Meeting page wrapper
(defui hub-meeting [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-student-detail}
     ($ student-page-wrapper {:current-match current-match :active-tab :meetings}
        ($ advisor/meeting-page {:current-match current-match}))))

;; Transcript editor wrapper
(defui hub-transcript [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-student-detail}
     ($ student-page-wrapper {:current-match current-match :active-tab :transcript}
        ($ advisor/transcript-editor {:current-match current-match}))))

;; Recommendations page wrapper.
;; Cutover flag (issue 0016 — S1; global-ON in 0022 — S6; REVERTED to DEFAULT OFF in
;; 0041): the advisor EDIT experience is the pre-existing DENSE recommendations-page.
;; The redesigned rec_demo authoring read-through is RETAINED behind the flag — a build
;; with the flag ON (or ?authoring=on) renders it (student-preview authoring view).
(defui hub-recommendations [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-student-detail}
     ($ student-page-wrapper {:current-match current-match :active-tab :recommendations}
        (if (authoring/cutover-enabled?)
          ($ authoring/authoring-recommendations-page {:current-match current-match})
          ($ advisor/recommendations-page {:current-match current-match})))))

;; Guardian profile page
(defui hub-guardian-detail [{:keys [current-match]}]
  (let [guardian-id (get-in current-match [:query-params :guardian-id])
        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        loading? (use-subscribe [::guardian-store/loading?])
        guardian (use-subscribe [::guardian-store/guardian])]
    (use-effect
     (fn []
       (when guardian-id
         (rf/dispatch [::guardian-store/set-guardian-id (parse-uuid guardian-id)])
         (rf/dispatch [::guardian-store/load-screen (parse-uuid guardian-id) api-client]))
       js/undefined)
     [guardian-id api-client])
    ($ hub-layout {:active-route :hub-students}
       (cond
         (and loading? (nil? guardian))
         ($ loading-spinner {:text "Loading guardian..."})

         :else
         ($ guardian-profile {:api-client api-client})))))

(defui hub-staff [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-staff}
     ($ staff/staff-list {:current-match current-match})))

(defui hub-staff-detail [{:keys [current-match]}]
  ($ hub-layout {:active-route :hub-staff}
     ($ staff/staff-detail {:current-match current-match})))

;; Broadcast (Communication) landing page
(defui hub-messages [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-messages}
     ($ broadcast-dashboard)))

(defui hub-message-compose [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-message-compose}
     ($ mass-send-wizard)))

(defui hub-message-templates [{:keys [_current-match]}]
  ($ hub-layout {:active-route :hub-message-templates}
     ($ templates-library)))

;; Delivery report (single broadcast)
(defui hub-message-broadcast [{:keys [current-match]}]
  (let [run-id (some-> (or (get-in current-match [:path-params :run-id])
                           (get-in current-match [:query-params :run-id]))
                       parse-uuid)]
    ($ hub-layout {:active-route :hub-message-broadcast}
       ($ delivery-report {:run-id run-id}))))
