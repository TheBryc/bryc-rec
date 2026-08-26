(ns components.shared.affordability-callout
  "The 'Important: Affordability & Financial Aid' callout shown at the top of the
   Colleges content. Extracted verbatim from components.student.recommendations-page
   so BOTH the student view AND the advisor authoring view (Edit + Student-preview)
   render the identical block. Always-on (present-by-data on the student page)."
  (:require [uix.core :as uix :refer [defui $]]))

(defui affordability-callout
  "The College Aid Pro + FAFSA affordability callout. Pure presentational; no props."
  [_]
  ($ :div {:class "space-y-4 mb-8"}
     ($ :div {:class "flex items-center"}
        ($ :div {:class "w-2 h-2 bg-gradient-to-r from-blue-400 to-purple-500 rounded-full mr-3 opacity-70"})
        ($ :h2 {:class "text-sm font-semibold uppercase tracking-wide text-gray-600"}
           "Important: Affordability & Financial Aid"))
     ($ :div {:class "bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm p-6 transition-all duration-300 ease-in-out hover:shadow-md"}
        ($ :div {:class "text-sm md:text-base text-gray-700 leading-relaxed space-y-4"}
           ($ :p "BRYC only recommends realistically affordable schools and programs. However, affordability depends on your family's unique financial situation.")
           ($ :p
              ($ :span {:class "font-semibold"} "That's why BRYC offers students free access to College Aid Pro")
              ", a platform where you can predict how much any college would cost your family specifically.")
           ($ :p
              "We urge you to request access to your free account at "
              ($ :a {:href "https://thebryc.org/request"
                     :target "_blank"
                     :rel "noopener noreferrer"
                     :class "font-semibold text-primary hover:underline underline-offset-2"}
                 "thebryc.org/request")
              ".")
           ($ :p
              ($ :span {:class "font-semibold"} "Above all, make sure to complete your FAFSA")
              ", otherwise you cannot receive TOPS funding, federal aid (Pell Grant, etc.), or scholarships from your colleges.")))))
