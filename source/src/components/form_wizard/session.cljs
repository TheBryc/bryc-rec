(ns components.form-wizard.session
  "Session management for form wizard - protocols and implementations"
  (:require [components.api.interface :as api]
            [cljs.core.async :refer [go <!]]
            [anomalies :as anom :refer [anomaly?]]))

;; Session management protocol
(defprotocol SessionManager
  "Protocol for managing form sessions with email-based persistence"
  (create-session [this args] "Create a new session. Args: {:email string :form-id string}. Returns: {:session-id string :token string}")
  (send-resume-link [this args] "Send resume link via email. Args: {:email string :form-id string}. Returns: promise with {:status keyword}")
  (validate-session-token [this args] "Validate JWT token. Args: {:token string}. Returns: {:session-id string :email string :form-id string} or nil")
  (load-session-state [this args] "Load session state. Args: {:session-id string}. Returns: answers map or nil")
  (save-session-state [this args] "Save current form state. Args: {:session-id string :answers map}. Returns: nil"))

;; Local implementation for development/testing
(deftype LocalSessionManager [state-atom]
  SessionManager
  (create-session [_ {:keys [email form-id]}]
    (let [session-id (str (random-uuid))
          token (str "jwt-" session-id)]
      (swap! state-atom assoc session-id {:email email :form-id form-id :answers {} :created-at (js/Date.)})
      {:session-id session-id :token token}))

  (send-resume-link [_ {:keys [email form-id]}]
    ; Simulate email sending
    (js/console.log "Sending resume link to" email "for form" form-id)
    (js/Promise.resolve {:status :sent}))

  (validate-session-token [_ {:keys [token]}]
    (if-let [session-id (second (re-find #"jwt-(.+)" token))]
      (if-let [session-data (get @state-atom session-id)]
        ;; Check if session is expired (older than 24 hours for testing)
        (let [created-at (:created-at session-data)
              now (js/Date.)
              hours-old (/ (- (.getTime now) (.getTime created-at)) (* 1000 60 60))]
          (if (> hours-old 24)
            {:error :expired
             :message "Your link has expired. Please request a new link."}
            {:session-id session-id :email (:email session-data) :form-id (:form-id session-data)}))
        {:error :failed
         :message "Invalid session token. Please start a new form."})
      {:error :failed
       :message "Invalid session token format. Please start a new form."}))

  (load-session-state [_ {:keys [session-id]}]
    (get-in @state-atom [session-id :answers]))

  (save-session-state [_ {:keys [session-id answers]}]
    (swap! state-atom assoc-in [session-id :answers] answers)
    (swap! state-atom assoc-in [session-id :updated-at] (js/Date.))))

;; Remote implementation that uses backend API
;; Note: Intake commands now write directly to CRM contacts
(deftype RemoteSessionManager [api-client survey-type]
  SessionManager
  (create-session [_ {:keys [email]}]
    ;; Start survey creates CRM contact and returns survey-id + contact-id
    (go
      (let [cmd (cond-> {:command/name :intake/start-survey
                         :email email}
                  survey-type (assoc :survey-type survey-type))
            response (<! (api/command api-client cmd))
            _ (js/console.log "[create-session] raw response:" (pr-str response))]
        (if (anomaly? response)
          response  ; Return the anomaly to the caller
          {:session-id (:survey-id response)
           :contact-id (:contact-id response)  ; CRM contact for this survey
           :token nil  ; Token not needed for create
           :email email}))))

  (send-resume-link [_ {:keys [email]}]
    ;; Send survey link command
    (go
      (let [cmd (cond-> {:command/name :intake/send-survey-link
                         :email email}
                  survey-type (assoc :survey-type survey-type))
            response (<! (api/command api-client cmd))]
        (if (anomaly? response)
          response  ; Return the anomaly to the caller
          {:status :sent}))))

  (validate-session-token [_ {:keys [token]}]
    ;; Resume survey loads answers from CRM contact
    (go
      (let [response (<! (api/command api-client {:command/name :intake/resume-survey
                                                  :jwt token}))]
        (if (anomaly? response)
          response  ; Return the anomaly to the caller
          (let [{:keys [survey-id contact-id email answers survey-type]} response]
            {:session-id survey-id
             :contact-id contact-id  ; CRM contact for this survey
             :email email
             :form-id (if (= survey-type :senior) "senior-intake" "intake")
             :answers answers})))))

  (load-session-state [_ {:keys [session-id]}]
    ;; Not needed - answers are returned by validate-session-token
    ;; Return nil or empty map to indicate no separate loading needed
    (go {}))

  (save-session-state [_ {:keys [session-id answers email survey-type]}]
    ;; Save answers directly to CRM contact
    (go
      (let [cmd (cond-> {:command/name :intake/save-answers
                          :survey-id session-id
                          :email email
                          :answers answers}
                  survey-type (assoc :survey-type survey-type))
            response (<! (api/command api-client cmd))]
        (if (anomaly? response)
          response  ; Return the anomaly to the caller
          nil)))))

;; Create default local session manager
(defonce local-session-state (atom {}))
(defonce default-local-session-manager (LocalSessionManager. local-session-state))

;; Helper to create RemoteSessionManager with API client
(defn create-remote-session-manager
  "Create a RemoteSessionManager with the given API client and optional survey-type"
  ([api-client] (RemoteSessionManager. api-client nil))
  ([api-client survey-type] (RemoteSessionManager. api-client survey-type)))

;; REPL testing utilities
(defonce reset-callbacks (atom []))
(defonce resume-callbacks (atom []))

(defn register-reset-callback!
  "Register a callback for resetting to email screen (internal use)"
  [callback]
  (swap! reset-callbacks conj callback))

(defn register-resume-callback!
  "Register a callback for resuming session (internal use)"
  [callback]
  (swap! resume-callbacks conj callback))

(defn reset-to-email-screen!
  "Reset wizard to email capture screen for testing resume functionality"
  []
  (doseq [callback @reset-callbacks]
    (callback)))

(defn clear-all-sessions!
  "Clear all stored sessions"
  []
  (reset! local-session-state {}))

(defn list-sessions
  "List all current sessions"
  []
  @local-session-state)

(defn create-test-session-with-data
  "Create a test session with some sample data for resume testing"
  [email]
  (let [session-result (create-session default-local-session-manager {:email email :form-id "bryc-intake-form"})
        sample-answers {:intake.answered/student-first-name "John"
                        :intake.answered/student-last-name "Doe"
                        :intake.answered/role "Student"}]
    (save-session-state default-local-session-manager
                       {:session-id (:session-id session-result)
                        :answers sample-answers})
    session-result))

(defn resume-existing-session!
  "Resume an existing session by email (finds existing session data)"
  [email]
  (let [existing-sessions @local-session-state
        session-id (->> existing-sessions
                        (filter (fn [[_sid sdata]] (= (:email sdata) email)))
                        first
                        first)]
    (if session-id
      (let [token (str "jwt-" session-id)]
        (js/console.log "Resuming existing session for:" email)
        (js/console.log "Found session with data:" (get-in existing-sessions [session-id :answers]))
        ; Trigger resume callbacks with the token
        (doseq [callback @resume-callbacks]
          (callback token)))
      (js/console.log "No existing session found for email:" email))))

(defn simulate-resume-link!
  "Simulate clicking a resume link - creates session with data and triggers resume"
  [email]
  (let [session-result (create-test-session-with-data email)
        token (:token session-result)]
    (js/console.log "Simulating resume link click...")
    (js/console.log "Session created with sample data for:" email)
    (js/console.log "Token:" token)
    ; Trigger resume callbacks with the token
    (doseq [callback @resume-callbacks]
      (callback token))))

(defn create-expired-session!
  "Create an expired session for testing error handling"
  [email]
  (let [session-id (str (random-uuid))
        token (str "jwt-" session-id)
        ; Create session that's 25 hours old (expired)
        expired-date (js/Date. (- (.getTime (js/Date.)) (* 25 60 60 1000)))]
    (swap! local-session-state assoc session-id
           {:email email
            :form-id "bryc-intake-form"
            :answers {:intake.answered/student-first-name "John"
                      :intake.answered/student-last-name "Doe"}
            :created-at expired-date
            :updated-at expired-date})
    (js/console.log "Created expired session for:" email "with token:" token)
    {:session-id session-id :token token}))

(defn simulate-expired-resume-link!
  "Simulate clicking an expired resume link for testing error handling"
  [email]
  (let [session-result (create-expired-session! email)
        token (:token session-result)]
    (js/console.log "Simulating expired resume link click...")
    (js/console.log "Token:" token)
    ; Trigger resume callbacks with the expired token
    (doseq [callback @resume-callbacks]
      (callback token))))
