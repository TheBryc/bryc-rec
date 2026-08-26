(ns components.admin.core
  (:require [uix.core :as uix :refer [defui $ use-effect use-memo]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/data-table" :refer [DataTable]]
            ["/gen/shadcn/components/ui/data-table-column-header" :refer [DataTableColumnHeader]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/tabs" :as tabs]
            ["/gen/shadcn/components/ui/alert" :as alert]
            [components.context.interface :as context]
            [store.auth.subs :as auth-subs]
            [store.admin.events :as admin-events]
            [store.admin.subs :as admin-subs]))

(defn- generations->js [generations]
  (clj->js generations :keyword-fn #(if (namespace %)
                                      (str (namespace %) "/" (name %))
                                      (name %))))

(defn- format-duration [minutes]
  (cond
    (< minutes 1) "< 1 min"
    (< minutes 60) (str (int minutes) " min")
    :else (let [hours (int (/ minutes 60))
                mins (int (mod minutes 60))]
            (str hours "h " mins "m"))))

(defn- format-started-at [s]
  (try
    (let [d (js/Date. s)]
      (.toLocaleString d js/undefined #js {:month "short" :day "numeric"
                                           :hour "2-digit" :minute "2-digit"}))
    (catch :default _ s)))

(defn- make-in-progress-columns [retrying on-retry]
  #js [#js {:accessorKey "student-name"
            :size 200
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Student"}))
            :cell (fn [props]
                    (let [name (.. props -row (getValue "student-name"))]
                      ($ :div {:class "font-medium"} (or name "Unknown"))))}

       #js {:accessorKey "started-at"
            :size 180
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Started At"}))
            :cell (fn [props]
                    ($ :span {:class "text-muted-foreground text-sm"}
                       (format-started-at (.. props -row (getValue "started-at")))))}

       #js {:accessorKey "duration-minutes"
            :size 120
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Duration"}))
            :cell (fn [props]
                    (let [minutes (.. props -row (getValue "duration-minutes"))]
                      ($ :span {:class "text-sm"} (format-duration minutes))))}

       #js {:accessorKey "flagged?"
            :size 120
            :header "Status"
            :cell (fn [props]
                    (let [flagged (.. props -row (getValue "flagged?"))]
                      (if flagged
                        ($ badge/Badge {:variant "destructive"} "Stuck")
                        ($ badge/Badge {:variant "secondary"} "Running"))))
            :enableSorting false}

       #js {:id "actions"
            :size 100
            :header ""
            :cell (fn [props]
                    (let [student-id (-> (.. props -row -original) (unchecked-get "student-id"))
                          is-retrying (contains? retrying (str student-id))]
                      ($ button/Button
                         {:variant "outline"
                          :size "sm"
                          :disabled is-retrying
                          :onClick (fn [e]
                                     (.stopPropagation e)
                                     (on-retry student-id))}
                         (if is-retrying "Retrying..." "Retry"))))
            :enableSorting false}])

(defn- make-failed-columns [retrying on-retry]
  #js [#js {:accessorKey "student-name"
            :size 180
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Student"}))
            :cell (fn [props]
                    (let [name (.. props -row (getValue "student-name"))]
                      ($ :div {:class "font-medium"} (or name "Unknown"))))}

       #js {:accessorKey "started-at"
            :size 160
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Started At"}))
            :cell (fn [props]
                    ($ :span {:class "text-muted-foreground text-sm"}
                       (format-started-at (.. props -row (getValue "started-at")))))}

       #js {:accessorKey "duration-minutes"
            :size 100
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Duration"}))
            :cell (fn [props]
                    (let [minutes (.. props -row (getValue "duration-minutes"))]
                      ($ :span {:class "text-sm"} (format-duration minutes))))}

       #js {:accessorKey "error-message"
            :size 250
            :header "Error"
            :cell (fn [props]
                    (let [msg (.. props -row (getValue "error-message"))]
                      ($ :span {:class "text-sm text-destructive truncate block max-w-[250px]"
                                :title msg}
                         msg)))
            :enableSorting false}

       #js {:id "actions"
            :size 100
            :header ""
            :cell (fn [props]
                    (let [student-id (-> (.. props -row -original) (unchecked-get "student-id"))
                          is-retrying (contains? retrying (str student-id))]
                      ($ button/Button
                         {:variant "outline"
                          :size "sm"
                          :disabled is-retrying
                          :onClick (fn [e]
                                     (.stopPropagation e)
                                     (on-retry student-id))}
                         (if is-retrying "Retrying..." "Retry"))))
            :enableSorting false}])

(defn- make-completed-columns []
  #js [#js {:accessorKey "student-name"
            :size 200
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Student"}))
            :cell (fn [props]
                    (let [name (.. props -row (getValue "student-name"))]
                      ($ :div {:class "font-medium"} (or name "Unknown"))))}

       #js {:accessorKey "started-at"
            :size 180
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Started At"}))
            :cell (fn [props]
                    ($ :span {:class "text-muted-foreground text-sm"}
                       (format-started-at (.. props -row (getValue "started-at")))))}

       #js {:accessorKey "duration-minutes"
            :size 120
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Duration"}))
            :cell (fn [props]
                    (let [minutes (.. props -row (getValue "duration-minutes"))]
                      ($ :span {:class "text-sm"} (format-duration minutes))))}

       #js {:accessorKey "completed-at"
            :size 180
            :header (fn [props]
                      ($ DataTableColumnHeader
                         {:column (.-column props)
                          :title "Completed At"}))
            :cell (fn [props]
                    ($ :span {:class "text-muted-foreground text-sm"}
                       (format-started-at (.. props -row (getValue "completed-at")))))}])

(defui main [{:keys [_current-match]}]
  (let [user (use-subscribe [::auth-subs/user])
        generations (use-subscribe [::admin-subs/generations])
        completed (use-subscribe [::admin-subs/completed])
        failed (use-subscribe [::admin-subs/failed])
        stats (use-subscribe [::admin-subs/stats])
        loading? (use-subscribe [::admin-subs/loading?])
        error (use-subscribe [::admin-subs/error])
        retrying (use-subscribe [::admin-subs/retrying])

        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)

        user-email (:email user)
        is-admin? (and user-email (str/ends-with? user-email "@obney.ai"))
        has-in-progress? (boolean (seq generations))]

    ;; Redirect non-admin users
    (use-effect
      (fn []
        (when (and (some? user-email) (not is-admin?))
          (js/setTimeout #(navigate! :hub-dashboard) 0))
        js/undefined)
      [user-email is-admin? navigate!])

    ;; Load data on mount
    (use-effect
      (fn []
        (when (and api-client is-admin?)
          (rf/dispatch [::admin-events/load-generations api-client]))
        js/undefined)
      [api-client is-admin?])

    ;; Auto-refresh every 30s when in-progress generations exist
    (use-effect
      (fn []
        (if (and api-client is-admin? has-in-progress?)
          (let [interval-id (js/setInterval
                             #(rf/dispatch [::admin-events/load-generations api-client])
                             30000)]
            (fn [] (js/clearInterval interval-id)))
          js/undefined))
      [api-client is-admin? has-in-progress?])

    (let [on-retry (uix/use-callback
                     (fn [student-id]
                       (rf/dispatch [::admin-events/retry-generation student-id api-client]))
                     [api-client])

          generations-js (use-memo #(generations->js (or generations [])) [generations])
          failed-js (use-memo #(generations->js (or failed [])) [failed])
          completed-js (use-memo #(generations->js (or completed [])) [completed])

          in-progress-columns (use-memo
                                #(make-in-progress-columns retrying on-retry)
                                [retrying on-retry])
          failed-columns (use-memo
                           #(make-failed-columns retrying on-retry)
                           [retrying on-retry])
          completed-columns (use-memo
                              #(make-completed-columns)
                              [])]

      (if (not is-admin?)
        nil
        ($ :div {:class "p-8 max-w-5xl mx-auto"}
           ;; Header
           ($ :div {:class "flex items-center justify-between mb-6"}
              ($ :div
                 ($ :h1 {:class "text-2xl font-bold"} "Recommendation Generations")
                 ($ :p {:class "text-muted-foreground text-sm mt-1"}
                    "Monitor recommendation generations. Generations running over 10 minutes are flagged as stuck."))
              ($ :div {:class "flex items-center gap-3"}
                 (when has-in-progress?
                   ($ :span {:class "text-xs text-muted-foreground"} "Auto-refreshing..."))
                 ($ button/Button
                    {:variant "outline"
                     :size "sm"
                     :onClick #(rf/dispatch [::admin-events/load-generations api-client])}
                    "Refresh")))

           ;; Error banner
           (when error
             ($ alert/Alert {:variant "destructive" :class "mb-4"}
                ($ alert/AlertDescription
                   (or (:cognitect.anomalies/message error)
                       "Failed to load recommendation generations."))))

           ;; Stats row
           (when stats
             ($ :div {:class "flex gap-3 mb-6"}
                ($ badge/Badge {:variant "secondary"}
                   (str (:running-count stats) " Running"))
                (when (pos? (:stuck-count stats 0))
                  ($ badge/Badge {:variant "destructive"}
                     (str (:stuck-count stats) " Stuck")))
                ($ badge/Badge {:variant "outline"}
                   (str (:completed-today stats) " Completed today"))
                (when (pos? (:failed-today stats 0))
                  ($ badge/Badge {:variant "destructive" :class "border-destructive"}
                     (str (:failed-today stats) " Failed today")))))

           ;; Tabbed content
           ($ tabs/Tabs {:defaultValue "in-progress"}
              ($ tabs/TabsList {:class "mb-4"}
                 ($ tabs/TabsTrigger {:value "in-progress"}
                    (str "In Progress"
                         (when (pos? (count (or generations [])))
                           (str " (" (count generations) ")"))))
                 ($ tabs/TabsTrigger {:value "failed"}
                    (str "Recent Failures"
                         (when (pos? (count (or failed [])))
                           (str " (" (count failed) ")"))))
                 ($ tabs/TabsTrigger {:value "completed"}
                    (str "Recent Completions"
                         (when (pos? (count (or completed [])))
                           (str " (" (count completed) ")")))))

              ($ tabs/TabsContent {:value "in-progress"}
                 ($ DataTable
                    {:columns in-progress-columns
                     :data generations-js
                     :loading? (boolean loading?)
                     :emptyMessage "No in-progress generations"
                     :pageSize 20
                     :showPagination true}))

              ($ tabs/TabsContent {:value "failed"}
                 ($ DataTable
                    {:columns failed-columns
                     :data failed-js
                     :loading? (boolean loading?)
                     :emptyMessage "No recent failures"
                     :pageSize 20
                     :showPagination true}))

              ($ tabs/TabsContent {:value "completed"}
                 ($ DataTable
                    {:columns completed-columns
                     :data completed-js
                     :loading? (boolean loading?)
                     :emptyMessage "No recent completions"
                     :pageSize 20
                     :showPagination true}))))))))
