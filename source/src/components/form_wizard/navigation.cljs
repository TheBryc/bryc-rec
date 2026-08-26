(ns components.form-wizard.navigation
  "Navigation and progress components for form wizard"
  (:require [uix.core :refer [defui $]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/card" :as card]
            ["marked" :as marked]))

;; Markdown rendering component
(defui markdown-content [{:keys [content class-name]}]
  (let [html (marked/parse content)]
    ($ :div {:class (str "prose prose-sm max-w-none " (or class-name ""))
             :dangerouslySetInnerHTML {:__html html}})))

;; Step indicator component
(defui step-indicator [{:keys [current-step total-steps field-progress]}]
  ($ :div {:class "flex items-center justify-between mb-4"}
     ($ :div {:class "flex items-center space-x-2"}
        ($ :div {:class "flex items-center justify-center w-8 h-8 bg-gradient-to-r from-blue-500 to-purple-600 rounded-full text-white text-sm font-bold"}
           (inc current-step))
        ($ :span {:class "text-gray-500 text-sm"} (str "of " total-steps " steps")))
     ($ :div {:class "text-sm text-gray-500 bg-white/60 px-3 py-1 rounded-full"}
        (str (Math/round (* field-progress 100)) "% complete"))))

;; Form header with progress and navigation
(defui form-header [{:keys [current-group-data field-progress current-step total-steps on-prev on-next on-submit]}]
  ($ card/CardHeader {:class "bg-gradient-to-r from-blue-50 to-purple-50 pb-6"}
     ($ step-indicator {:current-step current-step
                       :total-steps total-steps
                       :field-progress field-progress})

     ($ card/CardTitle {:class "text-2xl font-bold text-gray-800 mb-2"}
        (when current-group-data (:title current-group-data)))
     (if (and current-group-data (:description current-group-data))
       ($ markdown-content {:content (:description current-group-data)
                           :class-name "text-gray-600 mb-4"})
       ($ :p {:class "text-gray-600 mb-4"}
          "Fill out the information below to continue your journey with BRYC."))

     ; Top navigation buttons (only show starting from step 2)
     (when (> current-step 0)
       ($ :div {:class "flex justify-between items-center mt-4 pt-4 border-t border-gray-200/50"}
          ; Previous button
          ($ button/Button
             {:variant "outline"
              :onClick on-prev
              :class "flex items-center space-x-2 px-4 py-2 border-gray-300 text-gray-700 hover:bg-gray-50"}
             ($ :span "←")
             ($ :span "Previous"))

          ; Next/Submit button
          (if (= current-step (dec total-steps))
            ($ button/Button
               {:onClick on-submit
                :class "flex items-center space-x-2 bg-gradient-to-r from-green-500 to-green-600 hover:from-green-600 hover:to-green-700 px-6 py-2 text-white font-semibold rounded-lg shadow-lg"}
               ($ :span "🚀")
               ($ :span "Submit & Continue"))
            ($ button/Button
               {:onClick on-next
                :class "flex items-center space-x-2 bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 px-4 py-2 text-white font-semibold rounded-lg shadow-lg"}
               ($ :span "Next")
               ($ :span "→")))))))

;; Bottom wizard navigation
(defui wizard-navigation [{:keys [current-group total-groups on-prev on-next on-submit show-validation-message]}]
  ($ :div {:class "flex justify-between items-center mt-8 pt-6 border-t border-gray-200"}
     ; Previous button
     (if (= current-group 0)
       ($ :div {:class "w-32"}) ; Spacer
       ($ button/Button
          {:variant "outline"
           :onClick on-prev
           :class "flex items-center space-x-2 px-6 py-3 border-gray-300 text-gray-700 hover:bg-gray-50"}
          ($ :span "←")
          ($ :span "Previous")))

     ; Encouraging message or validation message
     ($ :div {:class "text-center"}
        (if show-validation-message
          ($ :p {:class "text-sm text-amber-600 font-medium flex items-center justify-center space-x-2"}
             ($ :span "📝")
             ($ :span "Please fill out the required fields above to continue."))
          (if (= current-group (dec total-groups))
            ($ :p {:class "text-sm text-gray-600 font-medium"} "🎉 Almost there! Let's finish strong.")
            ($ :p {:class "text-sm text-gray-600"} "You're doing great! Keep going."))))

     ; Next/Submit button
     (if (= current-group (dec total-groups))
       ($ button/Button
          {:onClick on-submit
           :class "flex items-center space-x-2 bg-gradient-to-r from-green-500 to-green-600 hover:from-green-600 hover:to-green-700 px-8 py-3 text-white font-semibold rounded-lg shadow-lg"}
          ($ :span "🚀")
          ($ :span "Submit & Continue"))
       ($ button/Button
          {:onClick on-next
           :class "flex items-center space-x-2 bg-gradient-to-r from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700 px-6 py-3 text-white font-semibold rounded-lg shadow-lg"}
          ($ :span "Next")
          ($ :span "→")))))

;; Fixed progress bar at bottom of screen
(defui progress-bar [{:keys [current-step total-steps field-progress]}]
  ($ :div {:class "fixed bottom-0 z-50 bg-white shadow-xl border-t border-gray-200"
           :style {:left "0"
                   :right "0"
                   :width "100vw"}}
     ($ :div {:class "max-w-3xl mx-auto px-6 py-4"}
        ($ :div {:class "flex items-center justify-between mb-2"}
           ($ :div {:class "flex items-center space-x-4"}
              ($ :div {:class "text-sm font-medium text-gray-900"}
                 "Your Progress")
              ($ :div {:class "text-sm text-gray-600"}
                 (str "Step " (inc current-step) " of " total-steps)))
           ($ :div {:class "text-sm font-bold text-blue-600"}
              (str (Math/round (* field-progress 100)) "% Complete")))
        ($ :div {:class "relative h-3 bg-gray-200 rounded-full overflow-hidden"}
           ($ :div {:class "h-full bg-gradient-to-r from-blue-500 to-green-500 transition-all duration-500 ease-out"
                    :style {:width (str (* field-progress 100) "%")}})
           ; Progress milestones
           (when (>= field-progress 0.25)
             ($ :div {:class "absolute left-1/4 top-0 bottom-0 w-0.5 bg-white/60"}))
           (when (>= field-progress 0.5)
             ($ :div {:class "absolute left-1/2 top-0 bottom-0 w-0.5 bg-white/60"}))
           (when (>= field-progress 0.75)
             ($ :div {:class "absolute left-3/4 top-0 bottom-0 w-0.5 bg-white/60"}))))))
