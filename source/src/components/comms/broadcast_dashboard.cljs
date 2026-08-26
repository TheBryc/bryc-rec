(ns components.comms.broadcast-dashboard
  (:require [uix.core :as uix :refer [defui $ use-effect use-state]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.context.interface :as context]
            [store.comms.mass-send :as ms]
            ["/gen/shadcn/components/ui/button" :as button]
            ["lucide-react" :refer [CheckCircle2 ChevronRight FileText Mail]]))

(defn- fmt-date [iso]
  (if (seq iso)
    (try
      (.toLocaleDateString (js/Date. iso) "en-US" #js {:month "short" :day "numeric" :year "numeric"})
      (catch :default _ iso))
    "-"))

(defn- normalize-keyword [v]
  (cond
    (keyword? v) v
    (string? v) (keyword v)
    :else v))

(defn- status-label [run]
  (case (normalize-keyword (:status run))
    :scheduled "Scheduled"
    :canceled "Canceled"
    :completed "Sent"
    :running "Sending"
    :failed "Failed"
    (str/capitalize (name (or (normalize-keyword (:status run)) :draft)))))

(defn- status-class [run]
  (case (normalize-keyword (:status run))
    :scheduled "bg-amber-50 text-amber-800 ring-amber-600/20"
    :completed "bg-emerald-50 text-emerald-700 ring-emerald-600/20"
    :running "bg-blue-50 text-blue-700 ring-blue-600/20"
    :canceled "bg-gray-100 text-gray-700 ring-gray-500/20"
    :failed "bg-red-50 text-red-700 ring-red-600/20"
    "bg-gray-100 text-gray-700 ring-gray-500/20"))

(defn- channels-label [run]
  (let [channels (or (:channels run) [(:channel run)])]
    (str/join " + "
              (map #(case (normalize-keyword %)
                      :email "Email"
                      :sms "SMS"
                      :sms-alert "SMS alert"
                      (name (or % :unknown)))
                   channels))))

(defn- title [run]
  (or (:title run) (:subject run) "Untitled broadcast"))

(defn- run-id [run]
  (or (:run-id run) (:broadcast-id run)))

(defn- run-date [run]
  (or (:scheduled-at run) (:started-at run) (:created-at run)))

(defn- recipient-count [run]
  (+ (count (or (:recipient-ids run) []))
     (count (or (:external-recipients run) []))))

(defn- attention? [run]
  (or (#{:failed :canceled} (normalize-keyword (:status run)))
      (pos? (or (:failed run) 0))))

(defn- tab-match? [active-tab run]
  (case active-tab
    :scheduled (= :scheduled (normalize-keyword (:status run)))
    :sent (= :completed (normalize-keyword (:status run)))
    :attention (attention? run)
    true))

(defn- tab-count [runs tab]
  (count (filter #(tab-match? tab %) runs)))

(defn- date-label [run]
  (case (normalize-keyword (:status run))
    :scheduled "Scheduled"
    :completed "Sent"
    :running "Started"
    "Date"))

(defn- delivery-label [run]
  (let [sent (or (:sent run) 0)
        total (or (:total run) 0)
        failed (or (:failed run) 0)
        skipped (or (:skipped run) 0)
        secondary (cond-> []
                    (pos? failed) (conj (str failed " failed"))
                    (pos? skipped) (conj (str skipped " skipped")))]
    {:primary (str sent "/" total " sent")
     :secondary (str/join " · " secondary)}))

(def tabs
  [{:id :all :label "All"}
   {:id :scheduled :label "Scheduled"}
   {:id :sent :label "Sent"}
   {:id :attention :label "Needs attention"}])

(defui ^:private status-pill [{:keys [run]}]
  ($ :span {:class (str "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset "
                        (status-class run))}
     (status-label run)))

(defui ^:private channel-badge [{:keys [label]}]
  ($ :span {:class "inline-flex items-center rounded-full bg-gray-950/5 px-2 py-0.5 text-xs font-medium text-gray-700"}
     label))

(defui ^:private tab-button [{:keys [tab active? count on-select]}]
  ($ :button
     {:type "button"
      :on-click #(on-select (:id tab))
      :class (str "inline-flex h-8 items-center gap-2 rounded-md px-3 text-sm font-medium transition-colors "
                  (if active?
                    "bg-gray-950 text-white"
                    "text-muted-foreground hover:bg-gray-950/5 hover:text-foreground"))}
     (:label tab)
     ($ :span {:class (str "rounded-full px-1.5 py-0.5 text-xs tabular-nums "
                           (if active?
                             "bg-white/15 text-white"
                             "bg-gray-950/5 text-muted-foreground"))}
        count)))

(defui ^:private empty-row [{:keys [message]}]
  ($ :tr
     ($ :td {:colSpan 6 :class "px-3 py-12 text-center text-sm text-muted-foreground"}
        message)))

(defui ^:private broadcast-row [{:keys [run on-open]}]
  (let [delivery (delivery-label run)]
    ($ :tr
       {:key (str (run-id run))
        :on-click #(on-open run)
        :class "group cursor-pointer hover:bg-gray-950/2"}
       ($ :td {:class "py-4 pr-3 align-top"}
          ($ :div {:class "min-w-0"}
             ($ :p {:class "truncate text-sm font-medium tracking-tight text-foreground"}
                (title run))
             ($ :div {:class "mt-1 flex min-w-0 items-center gap-2"}
                ($ channel-badge {:label (channels-label run)})
                (when-let [audience (:audience-summary run)]
                  ($ :span {:class "truncate text-sm text-muted-foreground"}
                     audience)))))
       ($ :td {:class "px-3 py-4 align-top"}
          ($ status-pill {:run run}))
       ($ :td {:class "px-3 py-4 align-top"}
          ($ :p {:class "text-sm text-foreground"} (fmt-date (run-date run)))
          ($ :p {:class "mt-0.5 text-xs text-muted-foreground"} (date-label run)))
       ($ :td {:class "px-3 py-4 text-right align-top text-sm tabular-nums text-foreground"}
          (recipient-count run))
       ($ :td {:class "px-3 py-4 text-right align-top"}
          ($ :p {:class "text-sm tabular-nums text-foreground"} (:primary delivery))
          (when (seq (:secondary delivery))
            ($ :p {:class "mt-0.5 text-xs tabular-nums text-red-600"} (:secondary delivery))))
       ($ :td {:class "py-4 pl-3 text-right align-top"}
          ($ ChevronRight {:class "ml-auto size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"})))))

(defui ^:private broadcast-table [{:keys [runs on-open empty-message]}]
  ($ :div {:class "-mx-4 -my-2 w-full max-w-full overflow-x-auto whitespace-nowrap sm:-mx-6 lg:-mx-8"
           :style #js {:contain "paint"}}
     ($ :div {:class "inline-block min-w-full px-4 py-2 align-middle sm:px-6 lg:px-8"}
        ($ :table {:class "w-full min-w-[56rem] table-fixed"}
           ($ :colgroup
              ($ :col {:class "w-[44%]"})
              ($ :col {:class "w-[11%]"})
              ($ :col {:class "w-[16%]"})
              ($ :col {:class "w-[9%]"})
              ($ :col {:class "w-[16%]"})
              ($ :col {:class "w-[4%]"}))
           ($ :thead
              ($ :tr {:class "border-b border-gray-950/10"}
                 ($ :th {:scope "col" :class "whitespace-nowrap py-3 pr-3 text-left text-xs font-medium text-muted-foreground"} "Broadcast")
                 ($ :th {:scope "col" :class "whitespace-nowrap px-3 py-3 text-left text-xs font-medium text-muted-foreground"} "Status")
                 ($ :th {:scope "col" :class "whitespace-nowrap px-3 py-3 text-left text-xs font-medium text-muted-foreground"} "Date")
                 ($ :th {:scope "col" :class "whitespace-nowrap px-3 py-3 text-right text-xs font-medium text-muted-foreground"} "Recipients")
                 ($ :th {:scope "col" :class "whitespace-nowrap px-3 py-3 text-right text-xs font-medium text-muted-foreground"} "Delivery")
                 ($ :th {:scope "col" :class "whitespace-nowrap py-3 pl-3 text-right text-xs font-medium text-muted-foreground"}
                    ($ :span {:class "sr-only"} "Open"))))
           ($ :tbody {:class "divide-y divide-gray-950/5"}
              (if (empty? runs)
                ($ empty-row {:message empty-message})
                (for [run runs]
                  ($ broadcast-row
                     {:key (str (run-id run))
                      :run run
                      :on-open on-open}))))))))

(defui broadcast-dashboard []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)
        [active-tab set-active-tab!] (use-state :all)
        runs (use-subscribe [::ms/runs])
        loading? (use-subscribe [::ms/runs-loading?])
        visible-runs (filterv #(tab-match? active-tab %) runs)
        tab-counts (into {} (map (fn [{:keys [id]}] [id (tab-count runs id)]) tabs))]
    (use-effect
      (fn []
        (rf/dispatch [::ms/load-runs api-client])
        js/undefined)
      [api-client])
    ($ :div {:class "flex w-full min-w-0 max-w-none flex-col overflow-hidden"}
       ($ :div {:class "sticky top-0 z-10 -mt-6 border-b border-gray-950/5 bg-background/95 pt-6 pb-8 backdrop-blur"}
          ($ :div {:class "flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"}
             ($ :div
                ($ :h1 {:class "text-xl font-semibold tracking-tight text-foreground sm:text-lg"} "Communication")
                ($ :p {:class "mt-1 text-base/7 text-muted-foreground sm:text-sm/6"}
                   "Broadcast emails, urgent SMS alerts, and scheduled sends"))
             ($ :div {:class "flex shrink-0 items-center gap-2"}
                ($ button/Button
                   {:variant "outline" :size "sm" :class "h-8 gap-1.5"
                    :on-click #(navigate! :hub-message-templates)}
                   ($ FileText {:class "size-3.5"})
                   "Templates")
                ($ button/Button
                   {:size "sm" :class "h-8 gap-1.5"
                    :on-click #(navigate! :hub-message-compose)}
                   ($ Mail {:class "size-3.5"})
                   "New broadcast")))
          ($ :div {:class "mt-6 flex min-h-8 min-w-0 max-w-full items-center gap-1 overflow-x-auto"}
             (for [tab tabs]
               ($ tab-button
                  {:key (name (:id tab))
                   :tab tab
                   :active? (= active-tab (:id tab))
                   :count (get tab-counts (:id tab) 0)
                   :on-select set-active-tab!}))))
       (cond
         loading?
         ($ :div {:class "mt-6"}
            ($ broadcast-table
               {:runs []
                :empty-message "Loading broadcasts…"
                :on-open identity}))

         (empty? runs)
         ($ :div {:class "px-4 py-12 text-center"}
            ($ CheckCircle2 {:class "mx-auto size-8 text-muted-foreground"})
            ($ :p {:class "mt-3 text-sm text-muted-foreground"} "No broadcasts yet.")
            ($ button/Button
               {:size "sm" :class "mt-4 h-8 gap-1.5"
                :on-click #(navigate! :hub-message-compose)}
               ($ Mail {:class "size-3.5"})
               "New broadcast"))

         :else
         ($ :div {:class "mt-6"}
            ($ broadcast-table
               {:runs visible-runs
                :empty-message (case active-tab
                                 :scheduled "No scheduled broadcasts."
                                 :sent "No sent broadcasts."
                                 :attention "No broadcasts need attention."
                                 "No broadcasts.")
                :on-open (fn [run]
                           (navigate! :hub-message-broadcast
                                      {:run-id (str (run-id run))}))}))))))
