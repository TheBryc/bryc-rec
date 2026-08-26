(ns components.crm.student.section-toc
  "Sticky table-of-contents rail for the fullpage student view.
   Highlights the active section based on scroll position."
  (:require [uix.core :refer [defui $]]
            ["lucide-react" :refer [ChevronsUpDown]]))

(defui section-toc
  "Sticky TOC rail with section links and collapse-all toggle.
   Props:
   - sections: vector of {:id :label} section definitions
   - active-section-id: currently visible section id
   - all-collapsed?: boolean
   - on-toggle-all: callback for collapse/expand all
   - on-click: callback (section-id) for navigation"
  [{:keys [sections active-section-id all-collapsed? on-toggle-all on-click]}]
  ($ :nav {:class "w-40 shrink-0 sticky top-[10rem] self-start hidden lg:block"}
     ;; Collapse/Expand All toggle
     ($ :button {:class "flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground mb-4 transition-colors"
                 :on-click on-toggle-all}
        ($ ChevronsUpDown {:class "h-3 w-3"})
        (if all-collapsed? "Expand All" "Collapse All"))

     ;; Section links
     ($ :ul {:class "space-y-0.5"}
        (for [{:keys [id label]} sections]
          ($ :li {:key id}
             ($ :button
                {:class (str "w-full text-left px-2.5 py-1 text-xs rounded transition-all duration-150 "
                             (if (= id active-section-id)
                               "text-foreground font-semibold bg-accent border-l-2 border-foreground"
                               "text-muted-foreground hover:text-foreground hover:bg-accent/50"))
                 :on-click #(on-click id)}
                label))))))
