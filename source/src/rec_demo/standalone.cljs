(ns rec-demo.standalone
  "Standalone entry point for the rec-redesign PROTOTYPE.

   Mounts ONLY components.rec-demo.core/rec-demo-page — no router, no API client,
   no other app code. This is what gets built for the public GitHub Pages demo, so
   the bundle contains the prototype + open-source libs only (nothing proprietary)."
  (:require [uix.core :refer [$]]
            [uix.dom :as dom]
            [components.rec-demo.core :as core]))

(defonce root
  (dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  (dom/render-root ($ core/rec-demo-page {}) root))

(defn init []
  (render))
