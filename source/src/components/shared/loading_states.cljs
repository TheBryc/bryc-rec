(ns components.shared.loading-states
  "Shared loading/error/empty state wrappers."
  (:require [uix.core :as uix :refer [defui $]]
            ["/gen/shadcn/components/ui/alert" :as alert]))

(defui loading-spinner
  "Centered loading message."
  [{:keys [text]}]
  ($ :div {:class "flex items-center justify-center h-64"}
     ($ :div {:class "text-muted-foreground"} (or text "Loading..."))))

(defui error-banner
  "Destructive alert banner for errors."
  [{:keys [error]}]
  ($ alert/Alert {:variant "destructive" :class "max-w-2xl mx-auto"}
     ($ :div (str error))))

(defui empty-state
  "Centered empty state card with title and message."
  [{:keys [title message]}]
  ($ :div {:class "flex-1 flex items-center justify-center"}
     ($ :div {:class "max-w-md text-center"}
        ($ :div {:class "bg-card border rounded-lg p-8 space-y-4"}
           ($ :h3 {:class "text-xl font-semibold"} title)
           ($ :p {:class "text-sm text-muted-foreground"} message)))))

(defui async-content
  "Wraps loading/error/ready states. Renders children when ready.

   Props:
   - loading?     — boolean
   - error        — string or nil
   - loading-text — optional loading message
   - children     — content to render in ready state"
  [{:keys [loading? error loading-text children]}]
  (cond
    loading?
    ($ loading-spinner {:text loading-text})

    error
    ($ error-banner {:error error})

    :else
    children))
