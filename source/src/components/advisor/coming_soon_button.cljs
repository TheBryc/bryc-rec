(ns components.advisor.coming-soon-button
  "A DISABLED add-button with a 'Coming soon' hover tooltip — the add-custom flow
   is turned off pending scope agreement (Cameron: revert the workaround, disable
   the button with an on-hover that says 'Coming soon'). Replaces every
   ::open-add-*-modal button across the five record surfaces; the modals and
   store events remain defined for a future phase, just unwired.

   The button sits inside a SPAN tooltip trigger — a disabled button emits no
   pointer events, so Radix tooltips never fire on a bare disabled trigger."
  (:require [uix.core :refer [$ defui]]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/tooltip" :as tooltip]))

(defui coming-soon-button
  "Props: :label (button text) · :class (optional extra layout classes) ·
   :default-open? (test seam — a CLOSED Radix tooltip portals nothing, so the
   SSR content pin renders it open; live it opens on hover)."
  [{:keys [label class default-open?]}]
  ($ tooltip/TooltipProvider {:delayDuration 150}
     ($ tooltip/Tooltip {:defaultOpen (boolean default-open?)}
        ($ tooltip/TooltipTrigger {:asChild true}
           ($ :span {:class "inline-block cursor-not-allowed"}
              ($ button/Button
                 {:variant "outline" :size "sm" :disabled true
                  :class (str "text-xs pointer-events-none " (or class ""))}
                 label)))
        ($ tooltip/TooltipContent "Coming soon"))))
