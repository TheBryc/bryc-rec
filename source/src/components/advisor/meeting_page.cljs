(ns components.advisor.meeting-page
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.advisor.intake-meeting-page :refer [intake-meeting-page]]
            [components.advisor.freeform-meeting-page :refer [freeform-meeting-page]]
            [components.context.interface :as context]
            [store.advisor.meetings.events :as meetings-events]
            [store.advisor.meetings.subs :as meetings-subs]))

(defui meeting-page [{:keys [current-match]}]
  (let [student-id (get-in current-match [:query-params :student-id])
        meeting-id (get-in current-match [:query-params :meeting-id])

        ;; Subscribe to state - all data comes from editor screen query
        meeting (use-subscribe [::meetings-subs/current-meeting])
        student (use-subscribe [::meetings-subs/student])

        ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        api-client (:api/client ctx)]

    ;; Load editor screen data (fat query pattern - student, meeting, and meetings in one query)
    (use-effect
      (fn []
        (when (and student-id meeting-id)
          (rf/dispatch [::meetings-events/load-editor-screen
                        (parse-uuid student-id)
                        (parse-uuid meeting-id)
                        api-client]))
        js/undefined)
      [student-id meeting-id api-client])

    ;; Note: We no longer need to redirect on empty meetings since new meetings
    ;; are optimistically added to the cache when created. If meeting is not found,
    ;; it will simply render nothing until the fetch completes.

    (when meeting
      (if (= (:meeting/type meeting) :intake-interview)
        ($ intake-meeting-page {:student-name (if student (:student/name student) "Loading...")
                               :student-id student-id
                               :meeting-id (parse-uuid meeting-id)
                               :meeting meeting
                               :meeting-notes (:meeting/notes meeting)
                               :meeting-status (:meeting/status meeting)
                               :meeting-transcript (:meeting/transcript meeting)
                               :meeting-analysis-status (:meeting/analysis-status meeting)
                               :meeting-analysis-result (:meeting/analysis-result meeting)
                               :on-complete (fn [notes]
                                             (js/console.log "Meeting completed with notes:" notes)
                                             (navigate! :hub-meetings {:student-id student-id}))
                               :on-back #(navigate! :hub-meetings {:student-id student-id})})
        ($ freeform-meeting-page {:student-name (if student (:student/name student) "Loading...")
                                 :student-id student-id
                                 :meeting-id (parse-uuid meeting-id)
                                 :meeting meeting
                                 :meeting-notes (:meeting/notes meeting)
                                 :meeting-status (:meeting/status meeting)
                                 :meeting-transcript (:meeting/transcript meeting)
                                 :meeting-analysis-status (:meeting/analysis-status meeting)
                                 :meeting-analysis-result (:meeting/analysis-result meeting)
                                 :on-complete (fn [notes]
                                               (js/console.log "Meeting completed with notes:" notes)
                                               (navigate! :hub-meetings {:student-id student-id}))
                                 :on-back #(navigate! :hub-meetings {:student-id student-id})})))))
