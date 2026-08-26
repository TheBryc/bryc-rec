(ns components.crm.student.field-grid
  "Dense field layout components for the fullpage student view.
   Provides a single-column field layout and compact field cells."
  (:require [uix.core :as uix :refer [defui $ use-state]]))

(def bryc-colors
  {:pink "#ed125f"
   :medium-gray "#676868"
   :light-gray "#f2f3f4"
   :black "#000000"})

(defui field-grid
  "Single-column layout for field groups.
   Props:
   - children: grid children (field-cell components)"
  [{:keys [children]}]
  ($ :div {:class "grid grid-cols-1 gap-y-3"}
     children))

(defui field-cell
  "Compact field display with label above value.
   Props:
   - label: field label string
   - span: accepted for compatibility; ignored in the single-column layout
   - children: field value / editable component"
  [{:keys [label children]}]
  ($ :div
     ($ :label {:class "text-xs font-medium text-muted-foreground uppercase tracking-wide"} label)
     ($ :div {:class "mt-0.5"} children)))

(defui sub-header
  "Lightweight sub-section divider within a section block.
   Props:
   - title: sub-section title"
  [{:keys [title]}]
  ($ :div {:class "pt-5 pb-2 first:pt-0"}
     ($ :h3 {:class "text-sm font-semibold text-foreground"} title)))

(defui nested-tabs
  "Small tab bar for section-local profile organization.
   Props:
   - tabs: vector of {:id string :label string :render fn}
   - default-id: optional string tab id"
  [{:keys [tabs default-id]}]
  (let [[active-id set-active-id!] (use-state (or default-id (:id (first tabs))))
        active-tab (or (first (filter #(= active-id (:id %)) tabs))
                       (first tabs))]
    ($ :div {:class "space-y-5"}
       ($ :div {:class "flex flex-wrap gap-1 rounded-md p-1"
                :style {:backgroundColor (:light-gray bryc-colors)}}
          (for [{:keys [id label]} tabs]
            ($ :button
               {:key id
                :type "button"
                :class "rounded px-3 py-1.5 text-sm font-medium transition-colors"
                :style (if (= id (:id active-tab))
                         {:backgroundColor "#ffffff"
                          :color (:pink bryc-colors)
                          :boxShadow "0 1px 2px rgba(0,0,0,0.08)"}
                         {:color (:medium-gray bryc-colors)})
                :on-click #(set-active-id! id)}
               label)))
       (when-let [render (:render active-tab)]
         (render)))))
