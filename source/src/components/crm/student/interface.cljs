(ns components.crm.student.interface
  "Public interface for CRM student components."
  (:require [components.crm.student.avatar :as avatar]
            [components.crm.student.editable-field :as editable-field]
            [components.crm.student.profile-layout :as profile-layout]
            [components.crm.student.detail-view :as detail-view]
            [components.crm.student.fullpage-view :as fullpage-view]
            [components.shared.formatting :as formatting]))

;; Avatar
(def student-avatar avatar/student-avatar)
(def get-initials avatar/get-initials)
(def get-color-for-name avatar/get-color-for-name)

;; Editable fields
(def editable-text editable-field/editable-text)
(def editable-textarea editable-field/editable-textarea)
(def editable-number editable-field/editable-number)
(def editable-select editable-field/editable-select)
(def editable-multi-select editable-field/editable-multi-select)
(def editable-tag-select editable-field/editable-tag-select)

;; Profile layout
(def profile-layout profile-layout/profile-layout)
(def profile-header profile-layout/profile-header)
(def sidebar-card profile-layout/sidebar-card)
(def field-row profile-layout/field-row)
(def content-section profile-layout/content-section)
(def format-phone formatting/format-phone)

;; Detail view
(def student-detail-view detail-view/student-detail-view)

;; Fullpage view (replaces tab-based detail view)
(def student-fullpage-view fullpage-view/student-fullpage-view)
