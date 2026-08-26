(ns components.shared.field-error
  "Shared field-error component for displaying CRM field validation errors."
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.uix :refer [use-subscribe]]
            [store.crm.student.subs :as student-subs]))

(defui field-error [{:keys [field-slug]}]
  (let [error (use-subscribe [::student-subs/field-error field-slug])]
    (when error
      ($ :p {:class "text-xs text-destructive mt-1"} error))))
