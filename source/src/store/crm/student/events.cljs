(ns store.crm.student.events
  "Student detail page events"
  (:require [re-frame.core :as rf]
            [store.crm.student.effects :as student-fx]
            [components.shared.formatting :refer [normalize-phone]]
            ["sonner" :refer [toast]]))

;; Track current student ID
(rf/reg-event-db
  ::set-student-id
  (fn [db [_ student-id]]
    (assoc-in db [:advisor :student-detail :current-student-id] student-id)))

;; =============================================================================
;; Screen Query - Fat Query Pattern
;; =============================================================================

;; Load entire screen data with one query
(rf/reg-event-fx
  ::load-screen
  (fn [{:keys [db]} [_ student-id api-client]]
    ;; Prevent concurrent fetches for the same student
    (when-not (and (get-in db [:advisor :student-detail :loading?])
                   (= student-id (get-in db [:advisor :student-detail :current-student-id])))
      (let [already-loaded? (and (= student-id (get-in db [:advisor :student-detail :current-student-id]))
                                 (get-in db [:advisor :students-by-id student-id]))]
        ;; If already loaded, refresh in background without flashing loading state
        {:db (-> db
                 (assoc-in [:advisor :student-detail :current-student-id] student-id)
                 (assoc-in [:advisor :student-detail :loading?] (not already-loaded?)))
         ::student-fx/fetch-student-detail-screen {:student-id student-id
                                                    :api-client api-client
                                                    :on-success [::load-screen-success]
                                                    :on-failure [::load-screen-failure]}}))))

(rf/reg-event-db
  ::load-screen-success
  (fn [db [_ {:keys [student contact-type field-options]}]]
    (let [expected-id (get-in db [:advisor :student-detail :current-student-id])
          actual-id (:student/id student)]
      ;; Only update if this is still the student we're viewing
      (if (= expected-id actual-id)
        (let [field-defs (:field-definitions contact-type)
              ;; Convert to map keyed by slug for easy lookup
              field-map (reduce (fn [m fd]
                                  (assoc m (:slug fd) fd))
                                {}
                                field-defs)]
          (-> db
              (assoc-in [:advisor :students-by-id actual-id] student)
              (assoc-in [:advisor :student-detail :contact-type] contact-type)
              (assoc-in [:crm :field-definitions] field-map)
              (assoc-in [:advisor :student-detail :field-options] field-options)
              (assoc-in [:advisor :student-detail :loading?] false)
              (assoc-in [:advisor :student-detail :error] nil)))
        db))))

(rf/reg-event-db
  ::load-screen-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :loading?] false)
        (assoc-in [:advisor :student-detail :error] error))))

(rf/reg-event-fx
  ::archive-student
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])]
      {:db (assoc-in db [:advisor :student-detail :archiving?] true)
       ::student-fx/archive-contact {:contact-id student-id
                                     :reason "Archived from student detail"
                                     :api-client api-client
                                     :on-success [::archive-student-success]
                                     :on-failure [::archive-student-failure]}})))

(rf/reg-event-fx
  ::archive-student-success
  (fn [{:keys [db]} [_ student-id api-client]]
    (toast.success "Student archived")
    {:db (assoc-in db [:advisor :student-detail :archiving?] false)
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::archive-student-failure
  (fn [db [_ error]]
    (toast.error (or (:anomalies/message error) "Failed to archive student"))
    (-> db
        (assoc-in [:advisor :student-detail :archiving?] false)
        (assoc-in [:advisor :student-detail :archive-error] error))))

;; Silent refresh after edit - uses screen query
(rf/reg-event-fx
  ::refresh-screen
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (assoc-in db [:advisor :student-detail :current-student-id] student-id)
     ::student-fx/fetch-student-detail-screen {:student-id student-id
                                                :api-client api-client
                                                :on-success [::refresh-screen-success]
                                                :on-failure [::load-screen-failure]}}))

(rf/reg-event-db
  ::refresh-screen-success
  (fn [db [_ {:keys [student contact-type field-options]}]]
    (let [expected-id (get-in db [:advisor :student-detail :current-student-id])
          actual-id (:student/id student)]
      ;; Only update if this is still the student we're viewing
      (if (= expected-id actual-id)
        (let [field-defs (:field-definitions contact-type)
              field-map (reduce (fn [m fd]
                                  (assoc m (:slug fd) fd))
                                {}
                                field-defs)]
          (-> db
              (assoc-in [:advisor :students-by-id actual-id] student)
              (assoc-in [:advisor :student-detail :contact-type] contact-type)
              (assoc-in [:crm :field-definitions] field-map)
              (assoc-in [:advisor :student-detail :field-options] field-options)))
        db))))

;; =============================================================================
;; Generic field saving (for click-to-edit pattern)
;; =============================================================================

;; Generic event to save any field directly to the API.
;; Args: [field-slug value api-client]
;; field-slug: string like 'unweighted-gpa', 'act-score', etc.
;; value: the value to save (will be sent as-is to API)
(rf/reg-event-fx
  ::save-field
  (fn [{:keys [db]} [_ field-slug value api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])]
      {:db (assoc-in db [:advisor :student-detail :field-saving field-slug] true)
       ::student-fx/set-contact-field {:contact-id student-id
                                       :field-slug field-slug
                                       :value value
                                       :api-client api-client
                                       :on-success [::save-field-success field-slug]
                                       :on-failure [::save-field-failure field-slug]}})))

(rf/reg-event-fx
  ::save-field-success
  (fn [{:keys [db]} [_ field-slug student-id _new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :field-saving field-slug] false)
             (update-in [:advisor :student-detail :field-errors] dissoc field-slug))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-field-failure
  (fn [db [_ field-slug error]]
    (let [;; Extract error message from anomaly
          error-msg (or (:anomalies/message error)
                        (:cognitect.anomalies/message error)
                        (:message error)
                        (str "Failed to save " field-slug))]
      (-> db
          (assoc-in [:advisor :student-detail :field-saving field-slug] false)
          (assoc-in [:advisor :student-detail :field-errors field-slug] error-msg)))))

;; Clear field error (called when user starts editing again)
(rf/reg-event-db
  ::clear-field-error
  (fn [db [_ field-slug]]
    (update-in db [:advisor :student-detail :field-errors] dissoc field-slug)))

;; =============================================================================
;; Encrypted field saving (for credential fields like passwords)
;; =============================================================================

;; Save encrypted field - uses advisor command that handles encryption server-side
;; Args: [field-slug value api-client]
(rf/reg-event-fx
  ::save-encrypted-field
  (fn [{:keys [db]} [_ field-slug value api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])]
      {:db (assoc-in db [:advisor :student-detail :field-saving field-slug] true)
       ::student-fx/set-encrypted-contact-field {:contact-id student-id
                                                  :field-slug field-slug
                                                  :value value
                                                  :api-client api-client
                                                  :on-success [::save-field-success field-slug]
                                                  :on-failure [::save-field-failure field-slug]}})))

;; Save field with explicit student-id (for use outside student detail page)
;; Args: [student-id field-slug value api-client & {:keys [on-success on-failure]}]
(rf/reg-event-fx
  ::save-field-for-student
  (fn [{:keys [db]} [_ student-id field-slug value api-client {:keys [on-success on-failure]}]]
    {:db (assoc-in db [:advisor :student-detail :field-saving field-slug] true)
     ::student-fx/set-contact-field {:contact-id student-id
                                     :field-slug field-slug
                                     :value value
                                     :api-client api-client
                                     :on-success [::save-field-for-student-success field-slug on-success]
                                     :on-failure [::save-field-for-student-failure field-slug on-failure]}}))

(rf/reg-event-fx
  ::save-field-for-student-success
  (fn [{:keys [db]} [_ field-slug on-success student-id _new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :field-saving field-slug] false)
             (update-in [:advisor :student-detail :field-errors] dissoc field-slug))
     ;; Always refresh student data, optionally call custom on-success
     :fx (cond-> [[:dispatch [::refresh-screen student-id api-client]]]
           on-success (conj [:dispatch on-success]))}))

(rf/reg-event-db
  ::save-field-for-student-failure
  (fn [db [_ field-slug on-failure error]]
    (when on-failure
      (rf/dispatch on-failure))
    (-> db
        (assoc-in [:advisor :student-detail :field-saving field-slug] false)
        (assoc-in [:advisor :student-detail :field-errors field-slug]
                  (or (:anomalies/message error)
                      (:cognitect.anomalies/message error)
                      "Save failed")))))

;; Guardian field-saving events removed: editing now lives entirely on the
;; guardian profile page (see store.crm.guardian.core/save-field). The
;; family tab's guardian cards are read-only navigable cards.

;; Color Profile Editing
(rf/reg-event-db
  ::start-edit-color-profile
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/color-profile])
          ;; Convert keyword to string for Select component (e.g., :super-green -> "super-green")
          string-value (when current-value (name current-value))]
      (-> db
          (assoc-in [:advisor :student-detail :editing :color-profile?] true)
          (assoc-in [:advisor :student-detail :editing :color-profile-value] string-value)))))

(rf/reg-event-db
  ::cancel-edit-color-profile
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :color-profile?] false)
        (assoc-in [:advisor :student-detail :editing :color-profile-value] nil))))

(rf/reg-event-db
  ::set-color-profile-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :color-profile-value] value)))

(rf/reg-event-fx
  ::save-color-profile
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value (get-in db [:advisor :student-detail :editing :color-profile-value])]
      {:db (assoc-in db [:advisor :student-detail :editing :saving?] true)
       ::student-fx/set-contact-field {:contact-id student-id
                                       :field-slug "color-profile"
                                       :value (keyword value)
                                       :api-client api-client
                                       :on-success [::save-color-profile-success]
                                       :on-failure [::save-color-profile-failure]}})))

(rf/reg-event-fx
  ::save-color-profile-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/color-profile] new-value)
             (assoc-in [:advisor :student-detail :editing :color-profile?] false)
             (assoc-in [:advisor :student-detail :editing :color-profile-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-color-profile-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save color profile"))))

;; Involvement Score Editing
(rf/reg-event-db
  ::start-edit-involvement-score
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/involvement-score])]
      (-> db
          (assoc-in [:advisor :student-detail :editing :involvement-score?] true)
          (assoc-in [:advisor :student-detail :editing :involvement-score-value] (str current-value))))))

(rf/reg-event-db
  ::cancel-edit-involvement-score
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :involvement-score?] false)
        (assoc-in [:advisor :student-detail :editing :involvement-score-value] nil))))

(rf/reg-event-db
  ::set-involvement-score-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :involvement-score-value] value)))

(rf/reg-event-fx
  ::save-involvement-score
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value-str (get-in db [:advisor :student-detail :editing :involvement-score-value])
          value (js/parseInt value-str 10)]
      (if (or (js/isNaN value) (< value 1) (> value 5))
        ;; Validation error
        {:db (assoc-in db [:advisor :student-detail :editing :error] "Score must be between 1 and 5")}
        ;; Valid, proceed with save
        {:db (-> db
                 (assoc-in [:advisor :student-detail :editing :saving?] true)
                 (assoc-in [:advisor :student-detail :editing :error] nil))
         ::student-fx/set-contact-field {:contact-id student-id
                                         :field-slug "involvement-score"
                                         :value value
                                         :api-client api-client
                                         :on-success [::save-involvement-score-success]
                                         :on-failure [::save-involvement-score-failure]}}))))

(rf/reg-event-fx
  ::save-involvement-score-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/involvement-score] new-value)
             (assoc-in [:advisor :student-detail :editing :involvement-score?] false)
             (assoc-in [:advisor :student-detail :editing :involvement-score-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-involvement-score-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save involvement score"))))

;; ACT Score Editing
(rf/reg-event-db
  ::start-edit-act-score
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/act-score])]
      (-> db
          (assoc-in [:advisor :student-detail :editing :act-score?] true)
          (assoc-in [:advisor :student-detail :editing :act-score-value] (str current-value))))))

(rf/reg-event-db
  ::cancel-edit-act-score
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :act-score?] false)
        (assoc-in [:advisor :student-detail :editing :act-score-value] nil))))

(rf/reg-event-db
  ::set-act-score-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :act-score-value] value)))

(rf/reg-event-fx
  ::save-act-score
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value-str (get-in db [:advisor :student-detail :editing :act-score-value])
          value (js/parseInt value-str 10)]
      (if (or (js/isNaN value) (< value 0) (> value 36))
        ;; Validation error
        {:db (assoc-in db [:advisor :student-detail :editing :error] "ACT score must be between 0 and 36")}
        ;; Valid, proceed with save
        {:db (-> db
                 (assoc-in [:advisor :student-detail :editing :saving?] true)
                 (assoc-in [:advisor :student-detail :editing :error] nil))
         ::student-fx/set-contact-field {:contact-id student-id
                                         :field-slug "act-score"
                                         :value value
                                         :api-client api-client
                                         :on-success [::save-act-score-success]
                                         :on-failure [::save-act-score-failure]}}))))

(rf/reg-event-fx
  ::save-act-score-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/act-score] new-value)
             (assoc-in [:advisor :student-detail :editing :act-score?] false)
             (assoc-in [:advisor :student-detail :editing :act-score-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-act-score-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save ACT score"))))

;; Unweighted GPA Editing
(rf/reg-event-db
  ::start-edit-unweighted-gpa
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/unweighted-cumulative-gpa])]
      (-> db
          (assoc-in [:advisor :student-detail :editing :unweighted-gpa?] true)
          (assoc-in [:advisor :student-detail :editing :unweighted-gpa-value] (if current-value (str current-value) ""))))))

(rf/reg-event-db
  ::cancel-edit-unweighted-gpa
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :unweighted-gpa?] false)
        (assoc-in [:advisor :student-detail :editing :unweighted-gpa-value] nil))))

(rf/reg-event-db
  ::set-unweighted-gpa-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :unweighted-gpa-value] value)))

(rf/reg-event-fx
  ::save-unweighted-gpa
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value-str (get-in db [:advisor :student-detail :editing :unweighted-gpa-value])
          value (js/parseFloat value-str)]
      (if (or (js/isNaN value) (< value 0) (> value 4.0))
        ;; Validation error
        {:db (assoc-in db [:advisor :student-detail :editing :error] "GPA must be between 0.0 and 4.0")}
        ;; Valid, proceed with save
        {:db (-> db
                 (assoc-in [:advisor :student-detail :editing :saving?] true)
                 (assoc-in [:advisor :student-detail :editing :error] nil))
         ::student-fx/set-contact-field {:contact-id student-id
                                         :field-slug "unweighted-gpa"
                                         :value value
                                         :api-client api-client
                                         :on-success [::save-unweighted-gpa-success]
                                         :on-failure [::save-unweighted-gpa-failure]}}))))

(rf/reg-event-fx
  ::save-unweighted-gpa-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/unweighted-cumulative-gpa] new-value)
             (assoc-in [:advisor :student-detail :editing :unweighted-gpa?] false)
             (assoc-in [:advisor :student-detail :editing :unweighted-gpa-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-unweighted-gpa-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save unweighted GPA"))))

;; Weighted Core GPA Editing
(rf/reg-event-db
  ::start-edit-weighted-core-gpa
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/weighted-core-gpa])]
      (-> db
          (assoc-in [:advisor :student-detail :editing :weighted-core-gpa?] true)
          (assoc-in [:advisor :student-detail :editing :weighted-core-gpa-value] (if current-value (str current-value) ""))))))

(rf/reg-event-db
  ::cancel-edit-weighted-core-gpa
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :weighted-core-gpa?] false)
        (assoc-in [:advisor :student-detail :editing :weighted-core-gpa-value] nil))))

(rf/reg-event-db
  ::set-weighted-core-gpa-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :weighted-core-gpa-value] value)))

(rf/reg-event-fx
  ::save-weighted-core-gpa
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value-str (get-in db [:advisor :student-detail :editing :weighted-core-gpa-value])
          value (js/parseFloat value-str)]
      (if (or (js/isNaN value) (< value 0) (> value 5.0))
        ;; Validation error
        {:db (assoc-in db [:advisor :student-detail :editing :error] "GPA must be between 0.0 and 5.0")}
        ;; Valid, proceed with save
        {:db (-> db
                 (assoc-in [:advisor :student-detail :editing :saving?] true)
                 (assoc-in [:advisor :student-detail :editing :error] nil))
         ::student-fx/set-contact-field {:contact-id student-id
                                         :field-slug "weighted-core-gpa"
                                         :value value
                                         :api-client api-client
                                         :on-success [::save-weighted-core-gpa-success]
                                         :on-failure [::save-weighted-core-gpa-failure]}}))))

(rf/reg-event-fx
  ::save-weighted-core-gpa-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/weighted-core-gpa] new-value)
             (assoc-in [:advisor :student-detail :editing :weighted-core-gpa?] false)
             (assoc-in [:advisor :student-detail :editing :weighted-core-gpa-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-weighted-core-gpa-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save weighted core GPA"))))

;; Recommendation Regeneration (Async)
;; The command starts background generation - status is tracked on the server
(rf/reg-event-fx
  ::regenerate-recommendations
  (fn [{:keys [db]} [_ student-id api-client]]
    ;; Clear any previous error and trigger the command
    {:db (assoc-in db [:advisor :student-detail :error] nil)
     ::student-fx/regenerate-recommendations {:student-id student-id
                                              :api-client api-client
                                              :on-success [::regenerate-recommendations-started student-id api-client]
                                              :on-failure [::regenerate-recommendations-failure]}}))

(rf/reg-event-fx
  ::regenerate-recommendations-started
  (fn [_ctx [_ student-id api-client]]
    ;; Command succeeded - generation has started in background
    ;; Refresh to get updated recommendation-status with generation-in-progress? = true
    {:dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-fx
  ::regenerate-recommendations-failure
  (fn [_ [_ error]]
    (toast.error (or (:anomalies/message error)
                     "Failed to start recommendation generation"))
    {}))

;; TOPS Award Editing
(rf/reg-event-db
  ::start-edit-tops-award
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/tops-award])]
      (-> db
          (assoc-in [:advisor :student-detail :editing :tops-award?] true)
          (assoc-in [:advisor :student-detail :editing :tops-award-value] current-value)))))

(rf/reg-event-db
  ::cancel-edit-tops-award
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :tops-award?] false)
        (assoc-in [:advisor :student-detail :editing :tops-award-value] nil))))

(rf/reg-event-db
  ::set-tops-award-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :tops-award-value] value)))

(rf/reg-event-fx
  ::save-tops-award
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value (get-in db [:advisor :student-detail :editing :tops-award-value])]
      {:db (-> db
               (assoc-in [:advisor :student-detail :editing :saving?] true)
               (assoc-in [:advisor :student-detail :editing :error] nil))
       ::student-fx/set-contact-field {:contact-id student-id
                                       :field-slug "tops-award"
                                       :value (or value "None")
                                       :api-client api-client
                                       :on-success [::save-tops-award-success]
                                       :on-failure [::save-tops-award-failure]}})))

(rf/reg-event-fx
  ::save-tops-award-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/tops-award] new-value)
             (assoc-in [:advisor :student-detail :editing :tops-award?] false)
             (assoc-in [:advisor :student-detail :editing :tops-award-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-tops-award-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save TOPS award"))))

;; WorkKeys Score Editing
(rf/reg-event-db
  ::start-edit-work-keys-score
  (fn [db _]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          current-value (get-in db [:advisor :students-by-id student-id :student/work-keys-score])
          ;; Convert keyword to string for Select component (e.g., :bronze -> "bronze")
          string-value (when current-value (name current-value))]
      (-> db
          (assoc-in [:advisor :student-detail :editing :work-keys-score?] true)
          (assoc-in [:advisor :student-detail :editing :work-keys-score-value] string-value)))))

(rf/reg-event-db
  ::cancel-edit-work-keys-score
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :editing :work-keys-score?] false)
        (assoc-in [:advisor :student-detail :editing :work-keys-score-value] nil))))

(rf/reg-event-db
  ::set-work-keys-score-value
  (fn [db [_ value]]
    (assoc-in db [:advisor :student-detail :editing :work-keys-score-value] value)))

(rf/reg-event-fx
  ::save-work-keys-score
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          value (get-in db [:advisor :student-detail :editing :work-keys-score-value])]
      {:db (-> db
               (assoc-in [:advisor :student-detail :editing :saving?] true)
               (assoc-in [:advisor :student-detail :editing :error] nil))
       ::student-fx/set-contact-field {:contact-id student-id
                                       :field-slug "work-keys-score"
                                       :value (keyword value)
                                       :api-client api-client
                                       :on-success [::save-work-keys-score-success]
                                       :on-failure [::save-work-keys-score-failure]}})))

(rf/reg-event-fx
  ::save-work-keys-score-success
  (fn [{:keys [db]} [_ student-id new-value api-client]]
    {:db (-> db
             (assoc-in [:advisor :students-by-id student-id :student/work-keys-score] new-value)
             (assoc-in [:advisor :student-detail :editing :work-keys-score?] false)
             (assoc-in [:advisor :student-detail :editing :work-keys-score-value] nil)
             (assoc-in [:advisor :student-detail :editing :saving?] false)
             (assoc-in [:advisor :student-detail :editing :error] nil))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::save-work-keys-score-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:advisor :student-detail :editing :saving?] false)
        (assoc-in [:advisor :student-detail :editing :error] "Failed to save WorkKeys score"))))

;; =============================================================================
;; Communication Events
;; =============================================================================

(rf/reg-event-fx
  ::fetch-communications
  (fn [{:keys [db]} [_ contact-id api-client]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :communications-loading?] true)
             (assoc-in [:advisor :student-detail :communications-error] nil))
     ::student-fx/fetch-communications {:contact-id contact-id
                                        :api-client api-client
                                        :on-success [::fetch-communications-success]
                                        :on-failure [::fetch-communications-failure]}}))

(rf/reg-event-db
  ::fetch-communications-success
  (fn [db [_ response]]
    (-> db
        (assoc-in [:advisor :student-detail :communications] (:communications response))
        (assoc-in [:advisor :student-detail :communications-total] (:total response))
        (assoc-in [:advisor :student-detail :communications-loading?] false)
        (assoc-in [:advisor :student-detail :communications-error] nil))))

(rf/reg-event-db
  ::fetch-communications-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :communications-loading?] false)
        (assoc-in [:advisor :student-detail :communications-error]
                  (or (:cognitect.anomalies/message error)
                      (:anomalies/message error)
                      "Could not load communications")))))

;; Modal state
;; Optional :contact-id arg lets pages other than the student detail open the
;; modal against a different contact (e.g. the guardian profile page).
;; Defaults to the current student detail's contact-id.
(rf/reg-event-db
  ::open-log-communication-modal
  (fn [db [_ & [{:keys [communication contact-id]}]]]
    (let [editing? (some? communication)
          contact-id (or contact-id
                         (get-in db [:advisor :student-detail :current-student-id]))
          ;; Convert a js/Date to datetime-local input format (YYYY-MM-DDTHH:MM)
          date->local (fn [d]
                        (let [pad #(.. (str %) (padStart 2 "0"))]
                          (str (.getFullYear d) "-" (pad (inc (.getMonth d))) "-" (pad (.getDate d))
                               "T" (pad (.getHours d)) ":" (pad (.getMinutes d)))))
          now-local (date->local (js/Date.))
          ;; For editing, convert stored ISO instant back to local datetime-local format
          existing-occurred-at (when-let [oa (:occurred-at communication)]
                                 (date->local (js/Date. oa)))
          form (if editing?
                 {:communication-type (:communication-type communication)
                  :direction (:direction communication)
                  :subject (or (:subject communication) "")
                  :content (or (:content communication) "")
                  :duration-minutes (:duration-minutes communication)
                  :outcome (or (:outcome communication) "")
                  :next-steps (or (:next-steps communication) "")
                  :call-link (or (:call-link communication) "")
                  :occurred-at (or existing-occurred-at now-local)}
                 {:communication-type :email
                  :direction :outbound
                  :subject ""
                  :content ""
                  :outcome ""
                  :next-steps ""
                  :call-link ""
                  :occurred-at now-local})]
      (-> db
          (assoc-in [:advisor :student-detail :communication-modal :open?] true)
          (assoc-in [:advisor :student-detail :communication-modal :mode] (if editing? :edit :create))
          (assoc-in [:advisor :student-detail :communication-modal :contact-id] contact-id)
          (assoc-in [:advisor :student-detail :communication-modal :communication-id]
                    (when editing? (:id communication)))
          (assoc-in [:advisor :student-detail :communication-modal :form] form)))))

(rf/reg-event-db
  ::close-log-communication-modal
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :communication-modal :open?] false)
        (assoc-in [:advisor :student-detail :communication-modal :form] nil)
        (assoc-in [:advisor :student-detail :communication-modal :mode] nil)
        (assoc-in [:advisor :student-detail :communication-modal :communication-id] nil)
        (assoc-in [:advisor :student-detail :communication-modal :error] nil))))

(rf/reg-event-db
  ::set-communication-form-field
  (fn [db [_ field value]]
    (assoc-in db [:advisor :student-detail :communication-modal :form field] value)))

(rf/reg-event-fx
  ::log-communication
  (fn [{:keys [db]} [_ api-client]]
    (let [modal (get-in db [:advisor :student-detail :communication-modal])
          ;; Prefer the contact-id stored when the modal was opened so this
          ;; works for both the student page and the guardian page.
          contact-id (or (:contact-id modal)
                         (get-in db [:advisor :student-detail :current-student-id]))
          form (:form modal)
          mode (:mode modal)
          editing? (= mode :edit)]
      {:db (assoc-in db [:advisor :student-detail :communication-modal :saving?] true)
       (if editing?
         ::student-fx/update-communication
         ::student-fx/log-communication)
       (if editing?
         {:contact-id contact-id
          :communication-id (:communication-id modal)
          :changes (let [ct (:communication-type form)
                         is-call? (#{:voice-call :video-call} ct)]
                     (cond-> {:communication-type ct
                              :direction (:direction form)}
                       (not is-call?) (assoc :subject (:subject form)
                                             :content (:content form))
                       is-call? (assoc :content (:content form)
                                       :duration-minutes (:duration-minutes form)
                                       :outcome (:outcome form)
                                       :next-steps (:next-steps form))
                       (= ct :video-call) (assoc :call-link (:call-link form))))
          :api-client api-client
          :on-success [::log-communication-success contact-id api-client]
          :on-failure [::log-communication-failure]}
         {:contact-id contact-id
          :communication-type (:communication-type form)
          :direction (:direction form)
          :subject (:subject form)
          :content (:content form)
          :duration-minutes (:duration-minutes form)
          :outcome (:outcome form)
          :next-steps (:next-steps form)
          :call-link (:call-link form)
          :occurred-at (:occurred-at form)
          :api-client api-client
          :on-success [::log-communication-success contact-id api-client]
          :on-failure [::log-communication-failure]})})))

(rf/reg-event-fx
  ::log-communication-success
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :communication-modal :open?] false)
             (assoc-in [:advisor :student-detail :communication-modal :saving?] false)
             (assoc-in [:advisor :student-detail :communication-modal :form] nil))
     :dispatch [::fetch-communications student-id api-client]}))

(rf/reg-event-db
  ::log-communication-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :communication-modal :saving?] false)
        (assoc-in [:advisor :student-detail :communication-modal :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to log communication")))))

;; Delete communication
(rf/reg-event-fx
  ::delete-communication
  (fn [{:keys [db]} [_ communication-id api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])]
      {:db (assoc-in db [:advisor :student-detail :deleting-communication-id] communication-id)
       ::student-fx/delete-communication
       {:contact-id student-id
        :communication-id communication-id
        :api-client api-client
        :on-success [::delete-communication-success student-id api-client]
        :on-failure [::delete-communication-failure]}})))

(rf/reg-event-fx
  ::delete-communication-success
  (fn [{:keys [db]} [_ student-id api-client]]
    {:db (assoc-in db [:advisor :student-detail :deleting-communication-id] nil)
     :dispatch [::fetch-communications student-id api-client]}))

(rf/reg-event-db
  ::delete-communication-failure
  (fn [db [_ _error]]
    (assoc-in db [:advisor :student-detail :deleting-communication-id] nil)))

;; =============================================================================
;; Transcript Analysis Events
;; =============================================================================

(rf/reg-event-fx
  ::apply-transcript-analysis
  (fn [{:keys [db]} [_ {:keys [student-id meeting-id approved-updates rejected-updates on-success on-error]}]]
    (let [api-client (get-in db [:context :api/client])]
      {:db (assoc-in db [:advisor :student-detail :transcript-analysis :applying?] true)
       ::student-fx/apply-transcript-analysis {:student-id student-id
                                                :meeting-id meeting-id
                                                :approved-updates approved-updates
                                                :rejected-updates rejected-updates
                                                :api-client api-client
                                                :on-success [::apply-transcript-analysis-success student-id on-success]
                                                :on-failure [::apply-transcript-analysis-failure on-error]}})))

(rf/reg-event-fx
  ::apply-transcript-analysis-success
  (fn [{:keys [db]} [_ student-id on-success]]
    (when on-success (on-success))
    {:db (-> db
             (assoc-in [:advisor :student-detail :transcript-analysis :applying?] false)
             (assoc-in [:advisor :student-detail :transcript-analysis :error] nil))
     :dispatch [::refresh-screen student-id (get-in db [:context :api/client])]}))

(rf/reg-event-db
  ::apply-transcript-analysis-failure
  (fn [db [_ on-error error]]
    (when on-error (on-error))
    (-> db
        (assoc-in [:advisor :student-detail :transcript-analysis :applying?] false)
        (assoc-in [:advisor :student-detail :transcript-analysis :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to apply transcript analysis")))))

;; =============================================================================
;; Add Guardian sheet — open/close + save flows
;; =============================================================================

(rf/reg-event-db
  ::open-add-guardian-sheet
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :add-guardian-sheet :open?] true)
        (assoc-in [:advisor :student-detail :add-guardian-sheet :mode] :find)
        (assoc-in [:advisor :student-detail :add-guardian-sheet :form] {})
        (assoc-in [:advisor :student-detail :add-guardian-sheet :found] nil)
        (assoc-in [:advisor :student-detail :add-guardian-sheet :error] nil))))

(rf/reg-event-db
  ::close-add-guardian-sheet
  (fn [db _]
    (assoc-in db [:advisor :student-detail :add-guardian-sheet :open?] false)))

(rf/reg-event-db
  ::set-add-guardian-form-field
  (fn [db [_ field-key value]]
    (assoc-in db [:advisor :student-detail :add-guardian-sheet :form field-key] value)))

(rf/reg-event-db
  ::set-add-guardian-found
  (fn [db [_ contact]]
    (assoc-in db [:advisor :student-detail :add-guardian-sheet :found] contact)))

(rf/reg-event-fx
  ::link-found-guardian
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          found (get-in db [:advisor :student-detail :add-guardian-sheet :found])
          relationship-type (get-in db [:advisor :student-detail :add-guardian-sheet :form :relationship-type])
          guardian-id (:id found)]
      (when (and guardian-id student-id)
        {:db (assoc-in db [:advisor :student-detail :add-guardian-sheet :saving?] true)
         ::student-fx/create-relationship
         {:type-slug "guardian-of"
          :source-contact-id guardian-id
          :target-contact-id student-id
          :properties (cond-> {} relationship-type (assoc :relationship-type relationship-type))
          :is-primary false
          :api-client api-client
          :on-success [::add-guardian-success student-id api-client]
          :on-failure [::add-guardian-failure]}}))))

(rf/reg-event-fx
  ::create-and-link-guardian
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          form (get-in db [:advisor :student-detail :add-guardian-sheet :form])
          field-values (cond-> {}
                         (:first-name form) (assoc :first-name (:first-name form))
                         (:last-name form) (assoc :last-name (:last-name form))
                         (:email form) (assoc :email (:email form))
                         (:phone form) (assoc :phone (normalize-phone (:phone form)))
                         (:mailing-address form) (assoc :mailing-address (:mailing-address form)))]
      (when (and student-id (or (:first-name form) (:email form)))
        {:db (assoc-in db [:advisor :student-detail :add-guardian-sheet :saving?] true)
         ::student-fx/create-contact
         {:type-slug "guardian"
          :field-values field-values
          :api-client api-client
          :on-success [::create-and-link-guardian-contact-success api-client]
          :on-failure [::add-guardian-failure]}}))))

(rf/reg-event-fx
  ::create-and-link-guardian-contact-success
  (fn [{:keys [db]} [_ api-client response]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          guardian-id (:contact-id response)
          relationship-type (get-in db [:advisor :student-detail :add-guardian-sheet :form :relationship-type])]
      {::student-fx/create-relationship
       {:type-slug "guardian-of"
        :source-contact-id guardian-id
        :target-contact-id student-id
        :properties (cond-> {} relationship-type (assoc :relationship-type relationship-type))
        :is-primary false
        :api-client api-client
        :on-success [::add-guardian-success student-id api-client]
        :on-failure [::add-guardian-failure]}})))

(rf/reg-event-fx
  ::add-guardian-success
  (fn [{:keys [db]} [_ student-id api-client & _]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :add-guardian-sheet :open?] false)
             (assoc-in [:advisor :student-detail :add-guardian-sheet :saving?] false)
             (assoc-in [:advisor :student-detail :add-guardian-sheet :found] nil)
             (assoc-in [:advisor :student-detail :add-guardian-sheet :form] {}))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::add-guardian-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :add-guardian-sheet :saving?] false)
        (assoc-in [:advisor :student-detail :add-guardian-sheet :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to add guardian")))))

;; =============================================================================
;; Add Sibling sheet — find an existing student by email and link via sibling-of
;; =============================================================================

(rf/reg-event-db
  ::open-add-sibling-sheet
  (fn [db _]
    (-> db
        (assoc-in [:advisor :student-detail :add-sibling-sheet :open?] true)
        (assoc-in [:advisor :student-detail :add-sibling-sheet :form] {})
        (assoc-in [:advisor :student-detail :add-sibling-sheet :found] nil)
        (assoc-in [:advisor :student-detail :add-sibling-sheet :error] nil))))

(rf/reg-event-db
  ::close-add-sibling-sheet
  (fn [db _]
    (assoc-in db [:advisor :student-detail :add-sibling-sheet :open?] false)))

(rf/reg-event-db
  ::set-add-sibling-form-field
  (fn [db [_ field-key value]]
    (assoc-in db [:advisor :student-detail :add-sibling-sheet :form field-key] value)))

(rf/reg-event-db
  ::set-add-sibling-found
  (fn [db [_ contact]]
    (assoc-in db [:advisor :student-detail :add-sibling-sheet :found] contact)))


(rf/reg-event-fx
  ::link-found-sibling
  (fn [{:keys [db]} [_ api-client]]
    (let [student-id (get-in db [:advisor :student-detail :current-student-id])
          found (get-in db [:advisor :student-detail :add-sibling-sheet :found])
          other-id (:id found)]
      (when (and student-id other-id)
        {:db (assoc-in db [:advisor :student-detail :add-sibling-sheet :saving?] true)
         ::student-fx/create-relationship
         {:type-slug "sibling-of"
          :source-contact-id student-id
          :target-contact-id other-id
          :api-client api-client
          :on-success [::add-sibling-success student-id api-client]
          :on-failure [::add-sibling-failure]}}))))

(rf/reg-event-fx
  ::add-sibling-success
  (fn [{:keys [db]} [_ student-id api-client & _]]
    {:db (-> db
             (assoc-in [:advisor :student-detail :add-sibling-sheet :open?] false)
             (assoc-in [:advisor :student-detail :add-sibling-sheet :saving?] false)
             (assoc-in [:advisor :student-detail :add-sibling-sheet :found] nil)
             (assoc-in [:advisor :student-detail :add-sibling-sheet :form] {}))
     :dispatch [::refresh-screen student-id api-client]}))

(rf/reg-event-db
  ::add-sibling-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:advisor :student-detail :add-sibling-sheet :saving?] false)
        (assoc-in [:advisor :student-detail :add-sibling-sheet :error]
                  (or (:cognitect.anomalies/message error)
                      "Failed to link sibling")))))
