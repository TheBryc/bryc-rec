(ns components.hub.interface
  "Public interface for hub layout component."
  (:require [components.hub.core :as core]
            [components.hub.views :as views]))

(def hub-layout core/hub-layout)

;; Hub page views
(def hub-dashboard views/hub-dashboard)
(def hub-advising views/hub-advising)
(def hub-fellows views/hub-fellows)
(def hub-students views/hub-students)
(def hub-duplicates views/hub-duplicates)
(def hub-student-detail views/hub-student-detail)
(def hub-meetings views/hub-meetings)
(def hub-meeting views/hub-meeting)
(def hub-transcript views/hub-transcript)
(def hub-recommendations views/hub-recommendations)
(def hub-guardian-detail views/hub-guardian-detail)
(def hub-staff views/hub-staff)
(def hub-staff-detail views/hub-staff-detail)
(def hub-messages views/hub-messages)
(def hub-message-templates views/hub-message-templates)
(def hub-message-compose views/hub-message-compose)
(def hub-message-broadcast views/hub-message-broadcast)
