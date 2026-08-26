(ns components.crm.student.tabs.core
  "Section routing and configuration for the student CRM detail view.
   Defines available sections and dispatches rendering to the correct component."
  (:require [uix.core :as uix :refer [defui $]]
            [components.crm.student.tabs.profile :refer [profile-tab]]
            [components.crm.student.tabs.profile-advisee :refer [advisee-profile-tab]]
            [components.crm.student.tabs.profile-fellow :refer [fellow-profile-tab]]
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
            [components.crm.student.communications :refer [communications-section]]
            [components.crm.student.status :as status]))

;; =============================================================================
;; Section Configuration
;; =============================================================================

(def fellow-profile-statuses #{:fellow :vulnerable-fellow})
(def advisee-profile-statuses #{:advisee})

(defn personal-profile-component [bryc-status]
  (cond
    (status/status-in? fellow-profile-statuses bryc-status) fellow-profile-tab
    (status/status-in? advisee-profile-statuses bryc-status) advisee-profile-tab
    :else profile-tab))

(def legacy-sections
  "Original CRM sections used for non-Fellow profile flavors."
  [{:id "communications"   :label "Communications"}
   {:id "status"           :label "Status"}
   {:id "personal"         :label "Personal"}
   {:id "school"           :label "School"}
   {:id "academics"        :label "Academics"}
   {:id "senior-year"      :label "Senior Year"}
   {:id "financial-aid"    :label "Financial Aid"}
   {:id "activities"       :label "Activities"}
   {:id "credentials"      :label "Credentials"}
   {:id "health"           :label "Health"}
   {:id "family"           :label "Family"}
   {:id "postsecondary"    :label "Postsecondary"    :requires #{:alumni :college-fellow}}
   {:id "career"           :label "Career"           :requires #{:fellow :alumni :college-fellow}}
   {:id "community"        :label "Community"        :requires #{:fellow :alumni :college-fellow}}
   {:id "online-presence"  :label "Online Presence"  :requires #{:fellow :alumni :college-fellow}}
   {:id "bryc-program"     :label "BRYC Program"     :requires #{:fellow :alumni :college-fellow}}])

(def advisee-sections
  "Advisee profile flavor. School is nested under Academics, so it is not a
   standalone section here."
  [{:id "status"           :label "Status"}
   {:id "personal"         :label "Personal"}
   {:id "academics"        :label "Academics"}
   {:id "senior-year"      :label "Senior Year"}
   {:id "financial-aid"    :label "Financial Aid"}
   {:id "activities"       :label "Activities"}
   {:id "credentials"      :label "Credentials"}
   {:id "family"           :label "Family"}
   {:id "communications"   :label "Communications"}])

(def fellow-sections
  "PDF feedback profile flavor used only for Fellow and Vulnerable Fellow."
  [{:id "personal"         :label "Personal"}
   {:id "bryc-program"     :label "BRYC Program"}
   {:id "academics"        :label "Academics"}
   {:id "testing"          :label "Testing"}
   {:id "senior-year"      :label "Senior Year"}
   {:id "health"           :label "Health & Safety"}
   {:id "communications"   :label "Communications"}])

(def sections legacy-sections)

(defn visible-sections
  "Filter sections to those visible for the given BRYC status."
  [bryc-status]
  (let [base-sections (cond
                        (status/status-in? fellow-profile-statuses bryc-status) fellow-sections
                        (status/status-in? advisee-profile-statuses bryc-status) advisee-sections
                        :else legacy-sections)]
    (filterv (fn [{:keys [requires]}]
               (or (nil? requires)
                   (status/status-in? requires bryc-status)))
             base-sections)))

(def section-ids
  "Set of valid section IDs."
(set (map :id (concat legacy-sections fellow-sections advisee-sections))))

(def default-section-id
  "Default section shown when none is selected."
  "communications")

;; =============================================================================
;; Section Content Dispatcher
;; =============================================================================

(defui section-content
  "Renders the content for the currently active section.
   Props:
   - active-section: string section ID
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [active-section student field-values api-client]}]
  (let [props {:student student :field-values field-values :api-client api-client}]
    (case active-section
      "personal"         ($ (personal-profile-component (get field-values :bryc-status)) props)
      "academics"        ($ academics-tab props)
      "testing"          ($ testing-tab props)
      "senior-year"      ($ senior-year-tab props)
      "family"           ($ family-tab props)
      "activities"       ($ activities-tab props)
      "financial-aid"    ($ financial-aid-tab props)
      "postsecondary"    ($ postsecondary-tab props)
      "career"           ($ career-tab props)
      "credentials"      ($ credentials-tab props)
      "health"           ($ admin-tab props)
      "communications"   ($ communications-section {:student-id (:student/id student) :api-client api-client})
      "community"        ($ community-tab props)
      "online-presence"  ($ online-presence-tab props)
      "bryc-program"     ($ bryc-program-tab props)
      ($ :div {:class "p-8 text-center text-muted-foreground"}
         ($ :p "This section is coming soon.")))))
