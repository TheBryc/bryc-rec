(ns components.router.core
  (:require [reitit.frontend :as rf]
            [reitit.frontend.easy :as rfe]
            [pushy.core :as pushy]
            [uix.core :as uix :refer [defui $]]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.auth.interface :as auth]
            [components.intake.interface :as intake]
            [components.hub.interface :as hub]
            [components.student.interface :as student]
            [components.rec-demo.interface :as rec-demo]
            [components.admin.interface :as admin]
            [components.senior-survey.core :as senior-survey]
            [components.short-link.core :as short-link]
            [components.gallery.interface :as gallery]
            [components.context.interface :as context]
            [store.auth.subs :as auth-subs]))

(def routes
  [["/" {:name :home :view hub/hub-dashboard :redirect "/hub"}]
   ["/auth"
    ["" {:name :auth :view auth/main}]
    ["/*path" {:name :auth-sub :view auth/main}]]
   ["/intake" {:name :intake :view intake/main}]
   ["/senior-intake" {:name :senior-intake :view senior-survey/main}]
   ["/student"
    ["/recommendations" {:name :student-recommendations :view student/recommendations-page}]]
   ;; Hub routes (staff UI) - using query params for IDs
   ["/hub"
    ["" {:name :hub-dashboard :view hub/hub-dashboard}]
    ["/advising" {:name :hub-advising :view hub/hub-advising}]
    ["/fellows" {:name :hub-fellows :view hub/hub-fellows}]
    ["/students"
     ["" {:name :hub-students :view hub/hub-students}]
     ["/view" {:name :hub-student-detail :view hub/hub-student-detail}]
     ["/meetings" {:name :hub-meetings :view hub/hub-meetings}]
     ["/meeting" {:name :hub-meeting :view hub/hub-meeting}]
     ["/transcript" {:name :hub-transcript :view hub/hub-transcript}]
     ["/recommendations" {:name :hub-recommendations :view hub/hub-recommendations}]]
    ["/duplicates" {:name :hub-duplicates :view hub/hub-duplicates}]
    ["/guardians"
     ["/view" {:name :hub-guardian-detail :view hub/hub-guardian-detail}]]
    ["/staff"
     ["" {:name :hub-staff :view hub/hub-staff}]
     ["/view" {:name :hub-staff-detail :view hub/hub-staff-detail}]]
    ["/messages"
     ["" {:name :hub-messages :view hub/hub-messages}]
     ["/templates" {:name :hub-message-templates :view hub/hub-message-templates}]
     ["/compose" {:name :hub-message-compose :view hub/hub-message-compose}]
     ["/broadcasts/:run-id" {:name :hub-message-broadcast :view hub/hub-message-broadcast}]]]
   ["/admin"
    ["/recommendations" {:name :admin-recommendations :view admin/main}]]
   ["/dev/gallery" {:name :dev-gallery :view gallery/gallery-page}]
   ;; Rec UI redesign PROTOTYPE — unauthenticated demo page for the dev team
   ["/rec-demo" {:name :rec-demo :view rec-demo/rec-demo-page}]
   ["/r/:code" {:name :short-link-redirect :view short-link/redirect-page}]])

(defonce router (rf/router routes))
(defonce match (atom nil))

(defonce history
  (pushy/pushy 
    #(reset! match %)
    #(rf/match-by-path router %)))

;; Protected routes that require authentication
(def protected-routes
  #{:hub-dashboard :hub-advising :hub-fellows :hub-students :hub-duplicates :hub-student-detail :hub-meetings :hub-meeting :hub-transcript :hub-recommendations :hub-guardian-detail :hub-staff :hub-staff-detail :hub-messages :hub-message-templates :hub-message-compose :hub-message-broadcast :admin-recommendations})

(defui router-outlet []
  (let [[current-match set-current-match!] (uix/use-state @match)
        auth-status (use-subscribe [::auth-subs/status])
        ctx (context/use-context)
        navigate! (:router/navigate! ctx)]

    ;; Watch the match atom and update component state when it changes
    (uix/use-effect
      (fn []
        (let [watch-key (gensym "router-watch")]
          (add-watch match watch-key
                    (fn [_ _ _ new-val]
                      (set-current-match! new-val)))
          ;; Cleanup function
          (fn []
            (remove-watch match watch-key))))
      [])

    (let [view (get-in current-match [:data :view])
          redirect (get-in current-match [:data :redirect])
          route-name (get-in current-match [:data :name])]
      (cond
        redirect (do (js/setTimeout #(set! js/window.location.pathname redirect) 0) nil)

        ;; Don't render anything while checking auth status for protected routes
        (and (protected-routes route-name) (= auth-status :loading))
        nil

        ;; Check authentication for protected routes
        (and (protected-routes route-name) (not= auth-status true))
        (do (js/setTimeout #(navigate! :auth) 0) nil)

        view ($ view {:current-match current-match})
        :else nil))))

(defn start-router! []
  (pushy/start! history))

(defn- template-param-keys
  "Keys that appear as path parameters (e.g. :run-id) in a reitit route
   template like \"/hub/messages/broadcasts/:run-id\". Note: reitit's
   match-by-name stuffs *all* supplied params into the match's :path-params,
   so we derive the true path params from the template instead."
  [template]
  (->> (re-seq #":([^/]+)" (or template ""))
       (map (comp keyword second))
       set))

(defn navigate!
  ([route-name]
   (navigate! route-name {}))
  ([route-name params]
   (let [route-match (or (when (seq params)
                           (rf/match-by-name router route-name params))
                         (rf/match-by-name router route-name))
         path (:path route-match)
         path-param-keys (template-param-keys (:template route-match))
         query-params (apply dissoc params path-param-keys)
         query-string (when (seq query-params)
                        (->> query-params
                             (map (fn [[k v]] (str (name k) "=" (js/encodeURIComponent v))))
                             (str/join "&")
                             (str "?")))]
     (when path
       (pushy/set-token! history (str path query-string))))))
