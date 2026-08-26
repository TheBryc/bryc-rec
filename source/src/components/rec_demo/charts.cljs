(ns components.rec-demo.charts
  "Recharts-based charts for the rec-redesign PROTOTYPE (v2).

   PROTOTYPE ONLY. Data is hardcoded from the SLU BSN exemplar — see
   components.rec-demo.data. Recharts (^2.15.4) is the app's charting library.

   v2 branding (Build Plan §1): earnings bars in BOLD TEAL (#05a09c); the two
   cost-of-living bars in NEUTRAL GRAY (#676868) so earnings vs. cost-of-living
   read apart at a glance. Per-bar color comes from each datum's :fill."
  (:require [uix.core :as uix :refer [defui $]]
            [clojure.string :as str]
            ["recharts" :refer [ResponsiveContainer BarChart Bar
                                XAxis YAxis CartesianGrid Tooltip
                                Cell LabelList]]))

;; ============================================================================
;; Palette — TEAL lead (Senior & Advising team), per the team color system.
;; ============================================================================

(def color-earnings "#05a09c")   ;; bold teal — earnings bars
(def color-col      "#676868")   ;; neutral gray — cost-of-living bars (deliberately non-teal)
(def color-net      "#2a6465")   ;; dark teal — highlighted "your net cost" bar
(def color-brand    color-earnings) ;; default bar fill alias
(def color-grid  "#e5e7eb")
(def color-axis  "#676868")
(def color-label "#313335")

;; ============================================================================
;; Helpers
;; ============================================================================

(defn usd [n]
  (str "$" (.toLocaleString (js/Math.round n) "en-US")))

(defn ^js multiline-x-tick
  "Custom XAxis tick that renders names containing newlines across <tspan>s,
   so long category labels wrap like the reference charts."
  [^js props]
  (let [x (.-x props)
        y (.-y props)
        value (.. props -payload -value)
        lines (str/split (str value) #"\n")]
    ($ :g {:transform (str "translate(" x "," y ")")}
       (for [[i line] (map-indexed vector lines)]
         ($ :text {:key i
                   :x 0
                   :y 0
                   :dy (+ 16 (* i 15))
                   :textAnchor "middle"
                   :fill color-axis
                   :fontSize 12}
            line)))))

;; ============================================================================
;; Chart container chrome (title + boxed legend + source caption)
;; ============================================================================

(defui chart-frame
  "legend-items: optional vector of {:color :label} rows rendered in a bordered box.
   source is optional — citations are NOT shown in the prototype (Build Plan §3.3)."
  [{:keys [title legend-items source children]}]
  ($ :div {:class "bg-white rounded-2xl shadow-sm ring-1 ring-[#bcdfe5] p-5 md:p-6"}
     (when title
       ($ :h4 {:class "text-base md:text-lg font-bold text-[#2a6465] text-center mb-3 font-head"}
          title))
     (when (seq legend-items)
       ($ :div {:class "flex justify-center mb-2"}
          ($ :div {:class "inline-flex flex-col gap-1 border border-slate-200 rounded-md px-3 py-2"}
             (for [[i {:keys [color label]}] (map-indexed vector legend-items)]
               ($ :div {:key i :class "flex items-center gap-2"}
                  ($ :span {:class "inline-block w-3.5 h-3.5 rounded-sm shrink-0"
                            :style {:background-color color}})
                  ($ :span {:class "text-xs md:text-sm text-slate-600"} label))))))
     children
     (when source
       ($ :p {:class "text-xs italic text-slate-500 mt-3 leading-relaxed"} source))))

;; ============================================================================
;; Chart 1 — Salary vs. Cost of Living (vertical bars)
;; Earnings bars teal, cost-of-living bars gray (per-bar :fill from data).
;; ============================================================================

(defui salary-vs-col-chart
  [{:keys [data title legend-items source y-max]}]
  ($ chart-frame
     {:title title :legend-items legend-items :source source}
     ($ :div {:class "w-full" :style {:height 360}}
        ($ ResponsiveContainer {:width "100%" :height "100%"}
           ($ BarChart {:data (clj->js data)
                        :margin #js {:top 30 :right 16 :left 8 :bottom 48}}
              ($ CartesianGrid {:strokeDasharray "4 4" :vertical false :stroke color-grid})
              ($ XAxis {:dataKey "name"
                        :tickLine false
                        :axisLine #js {:stroke "#d1d5db"}
                        :interval 0
                        :tick multiline-x-tick
                        :height 60})
              ($ YAxis {:domain #js [0 (or y-max 100000)]
                        :tickFormatter usd
                        :tickLine false
                        :axisLine false
                        :fontSize 13
                        :stroke color-axis
                        :width 64})
              ($ Tooltip {:formatter (fn [v] (usd v))
                          :cursor #js {:fill "rgba(5,160,156,0.07)"}})
              ($ Bar {:dataKey "value" :radius #js [4 4 0 0] :isAnimationActive false}
                 (for [[i d] (map-indexed vector data)]
                   ($ Cell {:key i :fill (:fill d color-earnings)}))
                 ($ LabelList {:dataKey "label"
                               :position "top"
                               :fill color-label
                               :fontSize 14
                               :fontWeight "bold"})))))))

;; ============================================================================
;; Chart 2 — Cost step-down waterfall (What It Costs You)
;; Solid descending running-total bars; final "net cost" bar in dark teal.
;; ============================================================================

(defui financial-aid-waterfall
  [{:keys [data title legend-items source y-max]}]
  ($ chart-frame
     {:title title :legend-items legend-items :source source}
     ($ :div {:class "w-full" :style {:height 360}}
        ($ ResponsiveContainer {:width "100%" :height "100%"}
           ($ BarChart {:data (clj->js data)
                        :margin #js {:top 30 :right 16 :left 8 :bottom 48}}
              ($ CartesianGrid {:strokeDasharray "4 4" :vertical false :stroke color-grid})
              ($ XAxis {:dataKey "name"
                        :tickLine false
                        :axisLine #js {:stroke "#d1d5db"}
                        :interval 0
                        :tick multiline-x-tick
                        :height 60})
              ($ YAxis {:domain #js [0 (or y-max 25000)]
                        :tickFormatter usd
                        :tickLine false
                        :axisLine false
                        :fontSize 13
                        :stroke color-axis
                        :width 64})
              ($ Tooltip {:formatter (fn [v] (usd v))
                          :cursor #js {:fill "rgba(5,160,156,0.07)"}})
              ($ Bar {:dataKey "value" :radius #js [4 4 0 0] :isAnimationActive false}
                 (for [[i d] (map-indexed vector data)]
                   ($ Cell {:key i :fill (:fill d color-earnings)}))
                 ($ LabelList {:dataKey "label"
                               :position "top"
                               :fill color-label
                               :fontSize 14
                               :fontWeight "bold"})))))))
