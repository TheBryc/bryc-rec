(ns components.shared.stat-card
  (:require [uix.core :as uix :refer [defui $]]))

(defui stat-card
  "Stat card with monospace value display.
   Props:
   - label: string label above the value
   - value: string/number to display prominently
   - description: optional subtitle below value
   - icon: optional Lucide icon component"
  [{:keys [label value description icon]}]
  (let [display-val (if (and value (not= value "")) (str value) "\u2014")]
    ($ :div {:class "p-4 border rounded-lg shadow-sm hover:border-foreground/20 transition-colors min-h-[5.5rem] flex flex-col justify-between"}
       ($ :div {:class "flex items-center gap-2 text-muted-foreground mb-2"}
          (when icon ($ icon {:class "h-4 w-4 shrink-0"}))
          ($ :span {:class "text-xs font-medium uppercase tracking-wider"} label))
       ($ :p {:class "font-mono text-2xl font-medium truncate"} display-val)
       (when description
         ($ :p {:class "text-xs text-muted-foreground mt-1"} description)))))
