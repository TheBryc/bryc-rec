(ns components.crm.student.tabs.overview
  "Overview section — stat cards only, at-a-glance metrics."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.uix :refer [use-subscribe]]
            [components.shared.stat-card :refer [stat-card]]
            ["lucide-react" :refer [GraduationCap Target BookOpen]]
            [store.crm.student.subs :as student-subs]))

(defui overview-tab
  "Overview — stat cards for key metrics at a glance.
   Props:
   - student: student data map
   - field-values: CRM field-values map
   - api-client: API client for saving fields"
  [{:keys [student field-values api-client]}]
  ($ :div {:class "grid grid-cols-3 gap-4"}
     ($ stat-card {:label "GPA"
                   :value (:student/self-reported-gpa student)
                   :icon GraduationCap})
     ($ stat-card {:label "ACT"
                   :value (:student/act-score student)
                   :icon Target})
     ($ stat-card {:label "Graduation"
                   :value (:student/graduation-year student)
                   :icon BookOpen})))
