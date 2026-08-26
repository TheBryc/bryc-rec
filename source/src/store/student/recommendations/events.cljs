(ns store.student.recommendations.events
  "Re-frame events for student view of recommendations"
  (:require [re-frame.core :as rf]
            [store.student.recommendations.effects :as effects]))

(rf/reg-event-fx
  ::load-recommendations
  (fn [{:keys [db]} [_ token api-client]]
    {:db (assoc-in db [:student :recommendations :loading?] true)
     ::effects/load-recommendations {:token token
                                     :api-client api-client
                                     :on-success [::load-success]
                                     :on-failure [::load-failure]}}))

(defn load-success-db
  "PURE re-frame event-db handler: write the Student-view query result onto the
   student recommendations store. Carries the 0059 :advisor identity map through so
   the ::advisor sub can render the advisor card. Extracted (named) so it's tested
   directly (data->data)."
  [db [_ {:keys [pool resolved advisor advisor-message pool-id]}]]
  (-> db
      (assoc-in [:student :recommendations :loading?] false)
      (assoc-in [:student :recommendations :pool] pool)
      (assoc-in [:student :recommendations :resolved] resolved)
      (assoc-in [:student :recommendations :advisor] advisor)
      (assoc-in [:student :recommendations :advisor-message] advisor-message)
      (assoc-in [:student :recommendations :pool-id] pool-id)
      (assoc-in [:student :recommendations :error] nil)))

(rf/reg-event-db ::load-success load-success-db)

(rf/reg-event-db
  ::load-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:student :recommendations :loading?] false)
        (assoc-in [:student :recommendations :error] error))))

;; ---------------------------------------------------------------------------
;; In-page Student preview (0051) — seed this student store from the ADVISOR
;; store's CURRENT resolved selection + advisor message so the dense advisor
;; editor can render the REAL redesigned `components.student.recommendations-page`
;; inline (no share-link round-trip). The advisor `:resolved` is ALREADY the
;; selection applied to the pool (the same :resolved the student query returns),
;; so NO default-selection trim is applied — that trim is only for the demo
;; harness, which seeds a raw saved pool. Writes ONLY under
;; [:student :recommendations …]; the sibling advisor store path is untouched, so
;; seeding the preview never corrupts the advisor's editing state.
;; ---------------------------------------------------------------------------

(defn seed-preview-db
  "PURE re-frame event-db handler: seed the student recommendations store from a
   given advisor resolved pool + advisor message (+ optional 0059 :advisor identity
   map). Tested directly (data->data)."
  [db [_ {:keys [resolved advisor advisor-message]}]]
  (-> db
      (assoc-in [:student :recommendations :resolved] resolved)
      (assoc-in [:student :recommendations :advisor] advisor)
      (assoc-in [:student :recommendations :advisor-message] advisor-message)
      (assoc-in [:student :recommendations :loading?] false)
      (assoc-in [:student :recommendations :error] nil)))

(rf/reg-event-db ::seed-preview seed-preview-db)
