(ns components.short-link.core
  (:require [uix.core :as uix :refer [defui $]]
            [cljs.core.async :refer [go <!]]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(defui redirect-page [{:keys [current-match]}]
  (let [code (get-in current-match [:path-params :code])
        ctx (context/use-context)
        api-client (:api/client ctx)
        [state set-state!] (uix/use-state :loading)]

    (uix/use-effect
      (fn []
        (when (and api-client code)
          (go
            (let [result (<! (api/query api-client {:query/name :link/resolve-short-link
                                                    :code code}))]
              (if (anomaly? result)
                (set-state! :not-found)
                (let [target-url (:target-url result)
                      url (js/URL. target-url)
                      path (str (.-pathname url) (.-search url))]
                  (set! js/window.location.href path))))))
        js/undefined)
      [api-client code])

    (case state
      :loading ($ :div {:class "flex items-center justify-center min-h-screen"}
                 ($ :p {:class "text-lg text-muted-foreground"} "Redirecting..."))
      :not-found ($ :div {:class "flex items-center justify-center min-h-screen"}
                   ($ :div {:class "text-center"}
                     ($ :h1 {:class "text-2xl font-bold mb-2"} "Link Not Found")
                     ($ :p {:class "text-muted-foreground"} "This link may have expired or is invalid."))))))
