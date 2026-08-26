(ns components.crm.student.section-block
  "Lightweight collapsible section wrapper.
   Replaces the heavy Card-based content-section for the fullpage view."
  (:require [uix.core :as uix :refer [defui $]]
            ["lucide-react" :refer [ChevronDown]]))

(defui section-block
  "Collapsible section with uppercase header.
   Props:
   - id: section id string (used for scroll targeting)
   - title: section display title
   - collapsed?: boolean
   - on-toggle: callback fn
   - children: section content"
  [{:keys [id title collapsed? on-toggle children]}]
  ($ :section {:id (str "section-" id)
               :data-section-id id
               :class "scroll-mt-[11rem] mb-8"}
     ;; Header
     ($ :button {:class "flex items-center gap-2 w-full text-left group mb-3"
                 :on-click on-toggle}
        ($ :h2 {:class "text-xs font-bold uppercase tracking-widest text-muted-foreground group-hover:text-foreground transition-colors"}
           title)
        ($ :div {:class "flex-1 h-px bg-border ml-3"})
        ($ ChevronDown {:class (str "h-3.5 w-3.5 text-muted-foreground transition-transform duration-200 "
                                    (when collapsed? "-rotate-90"))}))

     ;; Content with animated collapse
     ($ :div {:class "section-content"
              :data-collapsed (str (boolean collapsed?))}
        ($ :div children))))
