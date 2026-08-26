(ns app.core
  (:require ["@js-joda/timezone"]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [re-frame.core :as rf]
            [components.router.interface :as router]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [components.auth.interface :as auth]
            [components.form-wizard.core :as wizard-core]
            [components.dev-banner.interface :as dev-banner]
            ["/gen/shadcn/components/ui/sonner" :refer [Toaster]]
            [config.core :as config]
            [store.core :as store]
            [store.auth.effects]
            [store.auth.events]
            [store.auth.subs]
            [store.advisor.effects]
            [store.advisor.dashboard.events]
            [store.advisor.dashboard.subs]
            [store.crm.student.effects]
            [store.crm.student.events]
            [store.crm.student.subs]
            [store.advisor.recommendations.effects]
            [store.advisor.recommendations.events]
            [store.advisor.recommendations.subs]
            [store.advisor.meetings.effects]
            [store.advisor.meetings.events]
            [store.advisor.meetings.subs]
            [store.advisor.meeting-editor.effects]
            [store.advisor.meeting-editor.events]
            [store.advisor.meeting-editor.subs]
            [store.advisor.transcript-editor.effects]
            [store.advisor.transcript-editor.events]
            [store.advisor.transcript-editor.subs]))

(defui app []
  ($ :<>
     ($ dev-banner/development-banner)
     ($ router/router)
     ($ Toaster)))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  ;; Create the API client and session manager
  (let [api-client (api/->RemoteAPIClient {:base-url (config/api-base-url)})
        session-manager (wizard-core/create-remote-session-manager api-client)
        app-context {:api/client api-client
                     :session/manager session-manager
                     :dev/show-banner false
                     :router/navigate! router/navigate!}]
    (uix.dom/render-root
     ($ uix/strict-mode
        ($ context/app-provider {:context app-context}
           ($ app)))
     root)))

(defn ^:export init []
  ;; Initialize si-frame store
  (rf/dispatch-sync [::store/initialize])
  (router/start-router!)
  ;; Check authentication status on app startup using refactored auth
  (let [api-client (api/->RemoteAPIClient {:base-url (config/api-base-url)})]
    (auth/check-auth-status! api-client))
  (render)) 



(comment
  ""
  )