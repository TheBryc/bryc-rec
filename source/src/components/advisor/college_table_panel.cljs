(ns components.advisor.college-table-panel
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.recommendations.events :as events]
            [store.advisor.recommendations.subs :as subs]
            [components.advisor.institution-table :refer [institution-table]]
            [components.advisor.coming-soon-button :as csb]
            [components.advisor.authoring.selection :as sel]
            [components.shared.str-groups :as str-groups]
            ["/gen/shadcn/components/ui/accordion" :as accordion]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]))

(defn str-group-color
  "Accent color for a :str-badge display group's count badge."
  [str-key]
  (case str-key
    "open"   "bg-purple-600"
    "safety" "bg-green-600"
    "target" "bg-blue-600"
    "reach"  "bg-orange-600"
    "bg-gray-600"))

(def ^:private str-group->add-bucket
  "Which selection bucket a '+ Add School' under a given :str-badge group targets. STR is
   an engine fact, not an advisor choice, so a custom school added under 'Open Admission'
   or 'Safety' lands in the :safety bucket; 'Target'/'Reach' map to their own buckets."
  {"open" :safety "safety" :safety "target" :target "reach" :reach})

(defui college-table-panel
  "Issue 0062 — the dense editor's Colleges tab groups schools by :str-badge (Open
   Admission → Safety → Target → Reach), the SAME grouping the student viewer uses, so
   advisor + student read identical categories. The pool is flattened across its
   :safety/:target/:reach buckets and re-grouped by each school's OWN :str-badge; selection
   persistence stays bucketed (toggle/reorder resolve the real bucket per card)."
  [{:keys [api-client]}]
  (let [pool (use-subscribe [::subs/pool])
        selection (use-subscribe [::subs/selection])
        flat (sel/flatten-pool-institutions pool)
        groups (str-groups/group-institutions-by-str flat)]
    ($ :div {:class "space-y-4"}
       ($ accordion/Accordion {:type "multiple"
                               :default-value (to-array (map first groups))
                               :class "space-y-4"}
          (for [[str-key insts] groups]
            (let [selected-count (count (filter #(sel/institution-selected? selection (:id %)) insts))]
              ($ accordion/AccordionItem {:key str-key :value str-key :class "border-b-0"}
                 ($ :div {:class "flex items-center justify-between"}
                    ($ accordion/AccordionTrigger {:class "px-2 hover:no-underline flex-1"}
                       ($ :div {:class "flex items-center gap-2"}
                          ($ badge/Badge {:variant "default" :class (str "text-xs " (str-group-color str-key))}
                             (str selected-count))
                          ($ :span {:class "text-sm font-medium"}
                             (get str-groups/str-group-labels str-key str-key))))
                    ;; DISABLED pending scope agreement — 'Coming soon' hover
                    ;; (Cameron: revert the add-custom workaround). The span stops
                    ;; propagation so a click can't toggle the accordion group.
                    ($ :span {:class "mr-2"
                              :on-click (fn [e] (.stopPropagation e))}
                       ($ csb/coming-soon-button {:label "+ Add School"})))
                 ($ accordion/AccordionContent {:class "px-1 pb-2"}
                    (when (seq insts)
                      ($ institution-table
                         {:institutions insts
                          :selection selection
                          :api-client api-client}))))))))))
