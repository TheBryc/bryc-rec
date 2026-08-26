(ns components.advisor.interface
  (:require [components.advisor.core :as core]
            [components.advisor.transcript-editor :as transcript-editor-ns]
            [components.advisor.recommendations-page :as recommendations-page-ns]
            [components.advisor.scholarship-card :as scholarship-card-ns]
            [components.advisor.meetings-page :as meetings-page-ns]
            [components.advisor.meeting-page :as meeting-page-ns]))

(def main core/main)
(def advisor-content core/advisor-content)
(def transcript-editor transcript-editor-ns/transcript-editor)
(def recommendations-page recommendations-page-ns/recommendations-page)
(def scholarship-card scholarship-card-ns/scholarship-card)
(def meetings-page meetings-page-ns/meetings-page)
(def meeting-page meeting-page-ns/meeting-page)