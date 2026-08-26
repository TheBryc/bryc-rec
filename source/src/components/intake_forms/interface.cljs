(ns components.intake-forms.interface
  "Intake form configuration and field mappings.

   Field options are fetched from CRM contact type metadata at runtime."
  (:require [components.intake-forms.core :as core]))

(def config core/intake-config)