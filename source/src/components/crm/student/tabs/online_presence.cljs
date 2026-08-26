(ns components.crm.student.tabs.online-presence
  "Online presence section — social media profiles."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [components.crm.student.field-grid :refer [field-grid field-cell sub-header]]
            [components.crm.student.editable-field :refer [editable-text]]
            [components.shared.field-error :refer [field-error]]
            [store.crm.student.events :as student-events]
            [store.crm.student.subs :as student-subs]))

;; =============================================================================
;; Online Presence Tab
;; =============================================================================

(defui online-presence-tab
  "Online presence tab with social media profile fields.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  (let [saving? (use-subscribe [::student-subs/saving?])]
    ($ :<>
       ($ sub-header {:title "Social Media"})
       ($ field-grid
          ;; Instagram
          ($ field-cell {:label "Instagram"}
             ($ editable-text
                {:value (get field-values :instagram)
                 :placeholder "Add Instagram handle"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "instagram" % api-client])})
             ($ field-error {:field-slug "instagram"}))

          ;; Facebook
          ($ field-cell {:label "Facebook"}
             ($ editable-text
                {:value (get field-values :facebook)
                 :placeholder "Add Facebook profile"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "facebook" % api-client])})
             ($ field-error {:field-slug "facebook"}))

          ;; LinkedIn
          ($ field-cell {:label "LinkedIn"}
             ($ editable-text
                {:value (get field-values :linkedin)
                 :placeholder "Add LinkedIn profile"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "linkedin" % api-client])})
             ($ field-error {:field-slug "linkedin"}))

          ;; TikTok
          ($ field-cell {:label "TikTok"}
             ($ editable-text
                {:value (get field-values :tiktok)
                 :placeholder "Add TikTok handle"
                 :saving? saving?
                 :on-save #(rf/dispatch [::student-events/save-field "tiktok" % api-client])})
             ($ field-error {:field-slug "tiktok"}))))))
