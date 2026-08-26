(ns store.comms.mass-send
  "Re-frame store for the mass-send wizard + delivery-report page."
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [clojure.string :as str]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

;; =============================================================================
;; Effects
;; =============================================================================

(rf/reg-fx
  ::start-broadcast-fx
  (fn [{:keys [api-client payload on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command
                             api-client
                             (merge {:command/name :communications/start-broadcast}
                                    payload)))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::fetch-runs
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :communications/broadcast-runs}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::fetch-run
  (fn [{:keys [api-client run-id on-success on-failure]}]
    (when (and api-client run-id)
      (go
        (let [response (<! (api/query api-client {:query/name :communications/broadcast-run
                                                   :run-id run-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

;; =============================================================================
;; Wizard draft state
;; =============================================================================

(def ^:private blank-audience
  {:type-slug "student"
   :predicates []
   :include-guardians? false
   :field-defs nil
   :fields-loading? false
   :preview nil
   :preview-loading? false
   :preview-error nil})

(rf/reg-event-db
  ::reset-wizard
  (fn [db _]
    (assoc-in db [:mass-send :wizard]
              {:step :audience
               :channel :email
               :sms-alert? false
               :title ""
               :recipient-ids #{}
               :external-recipients-text ""
               :subject ""
               ;; Email composes as HTML (rich editor or raw power mode, both
               ;; write :body-html); SMS uses the plain :body / :sms-body.
               :body-format :html
               :body ""
               :body-html ""
               :sms-body ""
               :attachments []
               :error nil
               :sending? false
               :audience blank-audience})))

(rf/reg-event-db
  ::set-step
  (fn [db [_ step]]
    (assoc-in db [:mass-send :wizard :step] step)))

(rf/reg-event-db
  ::set-channel
  (fn [db [_ channel]]
    (let [db* (assoc-in db [:mass-send :wizard :channel] channel)]
      (if (= channel :email)
        (assoc-in db* [:mass-send :wizard :body-format] :html)
        (-> db*
            (assoc-in [:mass-send :wizard :subject] "")
            (assoc-in [:mass-send :wizard :external-recipients-text] "")
            (assoc-in [:mass-send :wizard :sms-alert?] false)
            (assoc-in [:mass-send :wizard :sms-body] ""))))))

(rf/reg-event-db
  ::set-body
  (fn [db [_ body]]
    (assoc-in db [:mass-send :wizard :body] body)))

(rf/reg-event-db
  ::set-body-format
  (fn [db [_ body-format]]
    (assoc-in db [:mass-send :wizard :body-format] body-format)))

(rf/reg-event-db
  ::set-body-html
  (fn [db [_ body-html]]
    (assoc-in db [:mass-send :wizard :body-html] body-html)))

(rf/reg-event-db
  ::set-title
  (fn [db [_ title]]
    (assoc-in db [:mass-send :wizard :title] title)))

(rf/reg-event-db
  ::set-subject
  (fn [db [_ subject]]
    (assoc-in db [:mass-send :wizard :subject] subject)))

(rf/reg-event-db
  ::set-sms-alert?
  (fn [db [_ v]]
    (assoc-in db [:mass-send :wizard :sms-alert?] (boolean v))))

(rf/reg-event-db
  ::set-sms-body
  (fn [db [_ body]]
    (assoc-in db [:mass-send :wizard :sms-body] body)))

(rf/reg-event-db
  ::set-external-recipients-text
  (fn [db [_ text]]
    (assoc-in db [:mass-send :wizard :external-recipients-text] text)))

(rf/reg-event-db
  ::add-attachment
  (fn [db [_ attachment]]
    (update-in db [:mass-send :wizard :attachments] (fnil conj []) attachment)))

(rf/reg-event-db
  ::remove-attachment
  (fn [db [_ file-id]]
    (update-in db [:mass-send :wizard :attachments]
               (fn [as] (vec (remove #(= file-id (:file-id %)) as))))))

(rf/reg-event-db
  ::toggle-recipient
  (fn [db [_ contact-id]]
    (let [current (or (get-in db [:mass-send :wizard :recipient-ids]) #{})
          next (if (contains? current contact-id)
                 (disj current contact-id)
                 (conj current contact-id))]
      (assoc-in db [:mass-send :wizard :recipient-ids] next))))

(rf/reg-event-db
  ::clear-recipients
  (fn [db _]
    (assoc-in db [:mass-send :wizard :recipient-ids] #{})))

(rf/reg-event-db
  ::set-recipient-contacts
  ;; Cache the resolved contact records so the review step can render names.
  (fn [db [_ contacts]]
    (assoc-in db [:mass-send :wizard :recipient-contacts]
              (into {} (map (juxt :id identity)) contacts))))

;; =============================================================================
;; Audience builder — filter predicates + live preview
;; =============================================================================

(defonce ^:private preview-timer (atom nil))

(def empty-audience-preview
  {:recipients []
   :counts {:total 0
            :missing-sms 0
            :missing-email 0
            :opted-out 0}
   :empty? true})

(defn- clear-preview-timer! []
  (when-let [t @preview-timer]
    (js/clearTimeout t)
    (reset! preview-timer nil)))

(rf/reg-fx
  ::fetch-field-defs-fx
  (fn [{:keys [api-client type-slug on-success on-failure]}]
    (when api-client
      (go
        (let [resp (<! (api/query api-client {:query/name :crm/get-contact-type
                                              :type-slug type-slug}))]
          (if (anomaly? resp)
            (rf/dispatch (conj on-failure resp))
            (rf/dispatch (conj on-success
                               (get-in resp [:contact-type :field-definitions])))))))))

(rf/reg-fx
  ::fetch-preview-fx
  (fn [{:keys [api-client query on-success on-failure]}]
    (when api-client
      (go
        (let [resp (<! (api/query api-client
                                  (merge {:query/name :crm/build-audience}
                                         query)))]
          (if (anomaly? resp)
            (rf/dispatch (conj on-failure resp))
            (rf/dispatch (conj on-success resp))))))))

;; Debounce: clear any pending timer, schedule the real fetch ~400ms out.
(rf/reg-fx
  ::schedule-preview
  (fn [{:keys [api-client]}]
    (clear-preview-timer!)
    (reset! preview-timer
            (js/setTimeout #(rf/dispatch [::do-fetch-preview api-client]) 400))))

(rf/reg-fx
  ::clear-preview-timer
  (fn [_]
    (clear-preview-timer!)))

(rf/reg-event-fx
  ::load-field-defs
  (fn [{:keys [db]} [_ api-client]]
    (let [type-slug (get-in db [:mass-send :wizard :audience :type-slug])]
      (when (and api-client (seq type-slug))
        {:db (assoc-in db [:mass-send :wizard :audience :fields-loading?] true)
         ::fetch-field-defs-fx {:api-client api-client
                                :type-slug type-slug
                                :on-success [::field-defs-loaded]
                                :on-failure [::field-defs-failed]}}))))

(rf/reg-event-db
  ::field-defs-loaded
  (fn [db [_ defs]]
    (-> db
        (assoc-in [:mass-send :wizard :audience :fields-loading?] false)
        (assoc-in [:mass-send :wizard :audience :field-defs] (vec defs)))))

(rf/reg-event-db
  ::field-defs-failed
  (fn [db _]
    (assoc-in db [:mass-send :wizard :audience :fields-loading?] false)))

(rf/reg-event-fx
  ::set-audience-type
  (fn [{:keys [db]} [_ type-slug api-client]]
    {:db (update-in db [:mass-send :wizard :audience] merge
                    {:type-slug type-slug
                     :predicates []
                     :include-guardians? false
                     :field-defs nil
                     :preview nil
                     :preview-error nil})
     :dispatch-n [[::load-field-defs api-client]
                  [::request-preview api-client]]}))

(rf/reg-event-db
  ::add-predicate
  (fn [db _]
    (update-in db [:mass-send :wizard :audience :predicates]
               (fnil conj [])
               {:id (random-uuid) :slug nil :data-type nil :op nil :value nil})))

(rf/reg-event-db
  ::update-predicate
  (fn [db [_ id patch]]
    (update-in db [:mass-send :wizard :audience :predicates]
               (fn [ps]
                 (mapv (fn [p]
                         (if (= id (:id p))
                           ;; Changing the field resets op + value.
                           (if (contains? patch :slug)
                             (merge p patch {:op nil :value nil})
                             (merge p patch))
                           p))
                       ps)))))

(rf/reg-event-db
  ::remove-predicate
  (fn [db [_ id]]
    (update-in db [:mass-send :wizard :audience :predicates]
               (fn [ps] (vec (remove #(= id (:id %)) ps))))))

(rf/reg-event-db
  ::set-include-guardians
  (fn [db [_ v]]
    (assoc-in db [:mass-send :wizard :audience :include-guardians?] (boolean v))))

(defn- ->backend-predicates
  "Drop incomplete rows (no field/op or blank value) and shape for the
   :crm/build-audience query. `tags`/`status` carry their scope."
  [predicates]
  (->> predicates
       (keep (fn [{:keys [slug op value scope]}]
               (when (and slug op
                          (or (#{:is-true :is-false} op)
                              (and (some? value)
                                   (not (and (string? value)
                                             (str/blank? value)))
                                   (not (and (coll? value) (empty? value))))))
                 ;; :field must be a keyword — it indexes into the
                 ;; contact's keyword :field-values map server-side.
                 (cond-> {:field (keyword slug) :op op}
                   (not (#{:is-true :is-false} op)) (assoc :value value)
                   scope (assoc :scope scope)))))
       vec))

(defn previewable-predicates?
  [predicates]
  (boolean (seq (->backend-predicates predicates))))

(rf/reg-event-fx
  ::request-preview
  (fn [{:keys [db]} [_ api-client]]
    (let [predicates (get-in db [:mass-send :wizard :audience :predicates])]
      (if (previewable-predicates? predicates)
        {:db (assoc-in db [:mass-send :wizard :audience :preview-loading?] true)
         ::schedule-preview {:api-client api-client}}
        {:db (-> db
                 (assoc-in [:mass-send :wizard :audience :preview-loading?] false)
                 (assoc-in [:mass-send :wizard :audience :preview-error] nil)
                 (assoc-in [:mass-send :wizard :audience :preview] empty-audience-preview))
         ::clear-preview-timer nil}))))

(rf/reg-event-fx
  ::do-fetch-preview
  (fn [{:keys [db]} [_ api-client]]
    (let [{:keys [type-slug predicates include-guardians?]}
          (get-in db [:mass-send :wizard :audience])]
      (if (previewable-predicates? predicates)
        {::fetch-preview-fx
         {:api-client api-client
          :query (cond-> {:type-slug type-slug
                          :predicates (->backend-predicates predicates)}
                   include-guardians?
                   (assoc :traversal {:relationship-type-slug "guardian-of"
                                      :direction :target}))
          :on-success [::preview-loaded]
          :on-failure [::preview-error]}}
        {:db (-> db
                 (assoc-in [:mass-send :wizard :audience :preview-loading?] false)
                 (assoc-in [:mass-send :wizard :audience :preview-error] nil)
                 (assoc-in [:mass-send :wizard :audience :preview] empty-audience-preview))}))))

(rf/reg-event-db
  ::preview-loaded
  (fn [db [_ resp]]
    (-> db
        (assoc-in [:mass-send :wizard :audience :preview-loading?] false)
        (assoc-in [:mass-send :wizard :audience :preview-error] nil)
        (assoc-in [:mass-send :wizard :audience :preview]
                  {:recipients (vec (:recipients resp))
                   :counts (:counts resp)}))))

(rf/reg-event-db
  ::preview-error
  (fn [db [_ error]]
    (-> db
        (assoc-in [:mass-send :wizard :audience :preview-loading?] false)
        (assoc-in [:mass-send :wizard :audience :preview-error]
                  (or (:cognitect.anomalies/message error)
                      "Could not preview audience")))))

(defn- slim->contact
  "Adapt a build-audience recipient summary into the contact shape the
   review/chip UI expects (looks up :field-values then :first-name etc.).
   Keeps :type-slug so recipient names can link to the right detail page."
  [{:keys [contact-id display-name type-slug tokens]}]
  {:id contact-id
   :display-name display-name
   :type-slug type-slug
   :field-values (or tokens {})})

(rf/reg-event-fx
  ::resolve-and-lock
  ;; Pull the previewed recipients into the frozen recipient set (unioned
  ;; with any manually-added contacts), then advance the wizard.
  (fn [{:keys [db]} [_ next-step]]
    (let [recipients (get-in db [:mass-send :wizard :audience :preview :recipients])
          ids (set (map :contact-id recipients))
          contact-map (into {} (map (juxt :contact-id slim->contact)) recipients)]
      {:db (-> db
               (update-in [:mass-send :wizard :recipient-ids]
                          (fnil into #{}) ids)
               (update-in [:mass-send :wizard :recipient-contacts]
                          merge contact-map))
       :dispatch [::set-step next-step]})))

;; =============================================================================
;; Submit
;; =============================================================================

(defn html->text [s]
  (-> (or s "")
      (str/replace #"(?is)<style[^>]*>.*?</style>" "")
      (str/replace #"(?is)<script[^>]*>.*?</script>" "")
      (str/replace #"(?i)<br\s*/?>" "\n")
      (str/replace #"(?i)</p\s*>" "\n\n")
      (str/replace #"<[^>]+>" "")
      (str/replace "&nbsp;" " ")
      (str/replace "&amp;" "&")
      (str/replace "&lt;" "<")
      (str/replace "&gt;" ">")
      (str/replace "&quot;" "\"")
      (str/replace "&#39;" "'")
      (str/replace #"[ \t]+\n" "\n")
      (str/replace #"\n{3,}" "\n\n")
      str/trim))

(rf/reg-event-fx
  ::submit
  (fn [{:keys [db]} [_ api-client]]
    (let [w (get-in db [:mass-send :wizard])
          {:keys [channel title subject body-format body body-html recipient-ids
                  external-recipients-text attachments]} w
          email? (= channel :email)
          html? (and email? (= :html body-format))
          body-text (if html? (html->text body-html) body)
          external-recipients (->> (str/split (if email? (or external-recipients-text "") "") #"[,\n]")
                                   (map str/trim)
                                   (remove str/blank?)
                                   (mapv (fn [email] {:email email})))]
      (when (and (seq body-text)
                 (or (not html?) (seq body-html))
                 (or (not email?) (seq subject))
                 (or (seq recipient-ids) (seq external-recipients)))
        {:db (-> db
                 (assoc-in [:mass-send :wizard :sending?] true)
                 (assoc-in [:mass-send :wizard :error] nil))
         ::start-broadcast-fx
         {:api-client api-client
          :payload (cond-> {:title title
                            :channels [(or channel :email)]
                            :body-format (if html? :html :plain)
                            :body body-text
                            :recipient-ids (vec recipient-ids)}
                     email? (assoc :subject subject)
                     html? (assoc :body-html body-html)
                     (seq external-recipients) (assoc :external-recipients external-recipients)
                     (seq attachments) (assoc :attachments attachments))
          :on-success [::submit-success]
          :on-failure [::submit-failure]}}))))

(rf/reg-event-fx
  ::submit-success
  (fn [{:keys [db]} [_ result]]
    (let [run-id (:run-id result)]
      {:db (-> db
               (assoc-in [:mass-send :wizard :sending?] false)
               (assoc-in [:mass-send :wizard :last-run-id] run-id))
       :dispatch [::set-step :sent]})))

(rf/reg-event-db
  ::submit-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:mass-send :wizard :sending?] false)
        (assoc-in [:mass-send :wizard :error]
                  (or (:cognitect.anomalies/message error)
                      "Mass-send failed")))))

;; =============================================================================
;; Runs list + detail
;; =============================================================================

(rf/reg-event-fx
  ::load-runs
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:mass-send :runs-loading?] true)
     ::fetch-runs {:api-client api-client
                   :on-success [::load-runs-success]
                   :on-failure [::load-runs-failure]}}))

(rf/reg-event-db
  ::load-runs-success
  (fn [db [_ {:keys [runs]}]]
    (-> db
        (assoc-in [:mass-send :runs-loading?] false)
        (assoc-in [:mass-send :runs] (vec runs)))))

(rf/reg-event-db
  ::load-runs-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:mass-send :runs-loading?] false)
        (assoc-in [:mass-send :runs-error] error))))

(rf/reg-event-fx
  ::load-run
  (fn [{:keys [db]} [_ api-client run-id]]
    {:db (-> db
             (assoc-in [:mass-send :run-loading?] true)
             (assoc-in [:mass-send :current-run-id] run-id))
     ::fetch-run {:api-client api-client
                  :run-id run-id
                  :on-success [::load-run-success run-id]
                  :on-failure [::load-run-failure]}}))

(rf/reg-event-db
  ::load-run-success
  (fn [db [_ run-id run]]
    (-> db
        (assoc-in [:mass-send :run-loading?] false)
        (assoc-in [:mass-send :runs-by-id run-id] run))))

(rf/reg-event-db
  ::load-run-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:mass-send :run-loading?] false)
        (assoc-in [:mass-send :run-error] error))))

;; =============================================================================
;; Subs
;; =============================================================================

(rf/reg-sub
  ::wizard
  (fn [db _]
    (or (get-in db [:mass-send :wizard])
        {:step :audience :channel :email :recipient-ids #{} :body "" :subject ""})))

(rf/reg-sub
  ::recipient-contacts
  (fn [db _]
    (or (get-in db [:mass-send :wizard :recipient-contacts]) {})))

(rf/reg-sub
  ::audience
  (fn [db _]
    (or (get-in db [:mass-send :wizard :audience]) blank-audience)))

(rf/reg-sub
  ::audience-predicates
  (fn [db _]
    (or (get-in db [:mass-send :wizard :audience :predicates]) [])))

(rf/reg-sub
  ::audience-field-defs
  (fn [db _]
    (or (get-in db [:mass-send :wizard :audience :field-defs]) [])))

(rf/reg-sub
  ::audience-preview
  (fn [db _]
    (get-in db [:mass-send :wizard :audience :preview])))

(rf/reg-sub
  ::audience-preview-loading?
  (fn [db _]
    (boolean (get-in db [:mass-send :wizard :audience :preview-loading?]))))

(rf/reg-sub
  ::audience-preview-error
  (fn [db _]
    (get-in db [:mass-send :wizard :audience :preview-error])))

(rf/reg-sub
  ::runs
  (fn [db _]
    (or (get-in db [:mass-send :runs]) [])))

(rf/reg-sub
  ::runs-loading?
  (fn [db _]
    (boolean (get-in db [:mass-send :runs-loading?]))))

(rf/reg-sub
  ::run
  (fn [db [_ run-id]]
    (get-in db [:mass-send :runs-by-id run-id])))

(rf/reg-sub
  ::run-loading?
  (fn [db _]
    (boolean (get-in db [:mass-send :run-loading?]))))
