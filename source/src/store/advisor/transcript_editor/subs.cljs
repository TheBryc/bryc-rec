(ns store.advisor.transcript-editor.subs
  "Transcript editor subscriptions"
  (:require [re-frame.core :as rf]))

;; Loading state
(rf/reg-sub
  ::loading?
  (fn [db _]
    (get-in db [:advisor :transcript-editor :loading?])))

;; Error message
(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:advisor :transcript-editor :error])))

;; Current student ID
(rf/reg-sub
  ::current-student-id
  (fn [db _]
    (get-in db [:advisor :transcript-editor :current-student-id])))

;; Student summary from transcript screen
(rf/reg-sub
  ::student
  (fn [db _]
    (get-in db [:advisor :transcript-editor :student])))

;; Transcript history (all uploads, including deleted)
(rf/reg-sub
  ::transcript-history
  (fn [db _]
    (get-in db [:advisor :transcript-editor :transcript-history])))

;; Current (active) transcript ID
(rf/reg-sub
  ::current-transcript-id
  (fn [db _]
    (get-in db [:advisor :transcript-editor :current-transcript-id])))

;; Original transcript data
(rf/reg-sub
  ::original-transcript
  (fn [db _]
    (get-in db [:advisor :transcript-editor :editing :original-transcript])))

;; Working copy of transcript (nil if no edits)
(rf/reg-sub
  ::working-transcript
  (fn [db _]
    (get-in db [:advisor :transcript-editor :editing :transcript])))

;; Current transcript (working copy OR original)
(rf/reg-sub
  ::transcript
  :<- [::working-transcript]
  :<- [::original-transcript]
  (fn [[working original] _]
    (or working original)))

;; Has changes?
(rf/reg-sub
  ::has-changes?
  :<- [::working-transcript]
  :<- [::original-transcript]
  (fn [[working original] _]
    (and working
         (not= working original))))

;; Saving state
(rf/reg-sub
  ::saving?
  (fn [db _]
    (get-in db [:advisor :transcript-editor :saving?])))

;; Save success indicator
(rf/reg-sub
  ::save-success?
  (fn [db _]
    (get-in db [:advisor :transcript-editor :save-success?])))

;; Upload modal open?
(rf/reg-sub
  ::upload-modal-open?
  (fn [db _]
    (get-in db [:advisor :transcript-editor :upload-modal-open?])))

;; Transcript ID (for save command)
(rf/reg-sub
  ::transcript-id
  (fn [db _]
    (get-in db [:advisor :transcript-editor :editing :transcript-id])))

;; Deleting state
(rf/reg-sub
  ::deleting?
  (fn [db _]
    (get-in db [:advisor :transcript-editor :deleting?])))

;; Delete confirmation dialog transcript ID
(rf/reg-sub
  ::delete-confirm-id
  (fn [db _]
    (get-in db [:advisor :transcript-editor :delete-confirm-id])))
