(ns store.core
  "Si-frame store initialization and core events"
  (:require [re-frame.core :as rf]))

(defn init-db
  "Initialize the application database with default state"
  []
  {:auth {:status :loading  ;; :loading | true | false
          :user nil}       ;; {:email "..."}
   :advisor {:students-by-id {}  ;; Normalized cache: {student-id -> student}
             :dashboard {:students []
                        :loading? false
                        :error nil
                        :last-fetch nil
                        :search-query ""
                        :current-page 1
                        :page-size 10}
             :student-detail {:current-student-id nil
                             :loading? false
                             :error nil
                             :last-fetch nil
                             :editing {:color-profile? false
                                      :involvement-score? false
                                      :color-profile-value nil
                                      :involvement-score-value nil
                                      :saving? false
                                      :error nil}}
             :recommendations {:loading? true
                              :error nil
                              :student-id nil
                              :pool nil          ;; Immutable pool from backend
                              :pool-id nil
                              :selection nil     ;; UI format (central text-edits)
                              :saved-selection nil  ;; Last saved state
                              :edit-mode? false
                              :saving? false
                              :active-tab "colleges"
                              :card-state {:expanded #{}    ;; Set of expanded card paths
                                          :editing #{}      ;; Set of cards being edited
                                          :edit-buffers {}}}}
   :form-wizard {:screen :email-capture  ;; :email-capture | :form | :success
                 :email nil
                 :session-id nil
                 :session-token nil
                 :session-error nil
                 :resume-link-sent? false
                 :is-processing? false
                 :validation-error nil
                 :current-group 0
                 :answers {}
                 :validation-errors {}
                 :saved-fields #{}
                 :show-validation? false
                 :field-progress 0.0
                 :submission-state nil}})

(rf/reg-event-db
  ::initialize
  (fn [_ _]
    (init-db)))
