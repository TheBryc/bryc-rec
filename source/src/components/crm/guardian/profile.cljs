(ns components.crm.guardian.profile
  "Guardian profile page. Mirrors the student profile pattern:
   - Sticky header with Profile / Communications view toggle
   - Profile view: collapsible section-blocks with sticky TOC rail
   - Communications view: full-width comms timeline
   Reuses section-block, section-toc, student-avatar, communications-section,
   and editable primitives without modification."
  (:require [uix.core :as uix :refer [defui $ use-state use-callback use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            [components.crm.student.field-grid :refer [field-grid field-cell]]
            [components.crm.student.editable-field :refer [editable-text]]
            [components.crm.student.avatar :refer [student-avatar]]
            [components.crm.student.section-block :refer [section-block]]
            [components.crm.student.section-toc :refer [section-toc]]
            [components.crm.student.communications :refer [log-communication-modal communications-section]]
            [components.shared.formatting :refer [format-phone normalize-phone validate-email validate-phone]]
            [components.context.interface :as context]
            [store.crm.student.events :as student-events]
            [store.crm.guardian.core :as guardian]
            ["/gen/shadcn/components/ui/button" :as button]
            ["lucide-react" :refer [MessageSquare]]))

(def ^:private sections
  [{:id "guardian-contact"          :label "Contact"}
   {:id "guardian-address"          :label "Address"}
   {:id "guardian-linked-students"  :label "Linked Students"}])

;; =============================================================================
;; Header (mirrors student-header shape)
;; =============================================================================

(defui ^:private guardian-header
  [{:keys [guardian guardian-id active-view on-set-view]}]
  (let [fv (:field-values guardian)
        full-name (str/trim (str (:first-name fv) " " (:last-name fv)))
        display (if (str/blank? full-name) "Unnamed guardian" full-name)
        tab-btn (fn [view label]
                  ($ :button
                     {:class (str "px-3 py-1.5 rounded-md text-sm font-medium transition-colors "
                                  (if (= active-view view)
                                    "bg-background text-foreground shadow-sm"
                                    "text-muted-foreground hover:text-foreground"))
                      :on-click #(on-set-view view)}
                     label))]
    ($ :div
       ($ :div {:class "flex flex-col md:flex-row items-start gap-4 md:gap-6 md:items-center mb-4"}
          ($ :div {:class "flex items-center gap-4 flex-1 min-w-0"}
             ($ student-avatar {:name display :size :lg})
             ($ :div {:class "min-w-0"}
                ($ :h1 {:class "text-2xl font-semibold truncate"} display)
                ($ :div {:class "flex items-center gap-2 text-sm text-muted-foreground"}
                   ($ :span "Guardian")
                   (when-let [email (:email fv)]
                     ($ :<>
                        ($ :span "·")
                        ($ :span email))))))
          ($ button/Button
             {:variant "outline" :size "sm"
              :on-click #(rf/dispatch [::student-events/open-log-communication-modal
                                       {:contact-id guardian-id}])}
             ($ MessageSquare {:class "h-4 w-4 mr-2"})
             "Log Communication"))
       ($ :div {:class "inline-flex items-center gap-1 bg-muted/50 rounded-lg p-1"}
          (tab-btn :profile "Profile")
          (tab-btn :communications "Communications")))))

;; =============================================================================
;; Section content
;; =============================================================================

(defui ^:private contact-content
  [{:keys [guardian api-client]}]
  (let [fv (:field-values guardian)]
    ($ field-grid
       ($ field-cell {:label "First Name"}
          ($ editable-text
             {:value (:first-name fv)
              :placeholder "Add first name"
              :on-save #(rf/dispatch [::guardian/save-field "first-name" % api-client])}))
       ($ field-cell {:label "Last Name"}
          ($ editable-text
             {:value (:last-name fv)
              :placeholder "Add last name"
              :on-save #(rf/dispatch [::guardian/save-field "last-name" % api-client])}))
       ($ field-cell {:label "Email"}
          ($ editable-text
             {:value (:email fv)
              :placeholder "Add email"
              :validate validate-email
              :on-save #(rf/dispatch [::guardian/save-field "email" % api-client])}))
       ($ field-cell {:label "Phone"}
          ($ editable-text
             {:value (format-phone (:phone fv))
              :placeholder "Add phone"
              :validate validate-phone
              :on-save #(rf/dispatch [::guardian/save-field "phone" (normalize-phone %) api-client])})))))

(defui ^:private address-content
  [{:keys [guardian api-client]}]
  (let [fv (:field-values guardian)]
    ($ field-grid
       ($ field-cell {:label "Mailing Address" :span :full}
          ($ editable-text
             {:value (:mailing-address fv)
              :placeholder "Add mailing address"
              :on-save #(rf/dispatch [::guardian/save-field "mailing-address" % api-client])})))))

(defui ^:private linked-student-row
  [{:keys [student]}]
  (let [name (:display-name student)
        href (str "/hub/students/view?student-id="
                  (js/encodeURIComponent (str (:id student))))]
    ($ :a {:href href
           :class "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors hover:bg-muted/40"}
       ($ student-avatar {:name name :size :sm})
       ($ :div {:class "min-w-0 flex-1"}
          ($ :p {:class "text-sm font-medium truncate"} name)
          ($ :p {:class "text-xs text-muted-foreground truncate"}
             (str/join " · "
                       (filter some?
                               [(:relationship-type student)
                                (:grade student)]))))
       (when (:is-primary student)
         ($ :div {:class "flex items-center gap-1.5 shrink-0"}
            ($ :span {:aria-hidden true
                      :class "inline-block w-2 h-2 rounded-full bg-amber-500"})
            ($ :span {:class "text-xs text-amber-700 font-medium"} "Primary"))))))

(defui ^:private linked-students-content
  [{:keys [linked-students]}]
  (if (seq linked-students)
    ($ :div {:class "rounded-lg border bg-background divide-y"}
       (for [s linked-students]
         ($ linked-student-row {:key (str (:id s)) :student s})))
    ($ :p {:class "text-sm text-muted-foreground"}
       "No linked students.")))

;; =============================================================================
;; Page
;; =============================================================================

(defui guardian-profile
  [{:keys [_initial-view]}]
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        guardian        (use-subscribe [::guardian/guardian])
        linked-students (use-subscribe [::guardian/linked-students])
        field-options   (use-subscribe [::guardian/field-options])
        loading?        (use-subscribe [::guardian/loading?])
        guardian-id     (use-subscribe [::guardian/current-guardian-id])

        [active-view set-active-view!] (use-state :profile)

        ;; Collapse state — all expanded by default
        [expanded-ids set-expanded-ids!] (use-state (set (map :id sections)))
        all-collapsed? (empty? expanded-ids)

        [active-section set-active-section!] (use-state (-> sections first :id))

        toggle-section
        (use-callback
         (fn [section-id]
           (set-expanded-ids!
            (fn [ids]
              (if (contains? ids section-id)
                (disj ids section-id)
                (conj ids section-id)))))
         [])

        toggle-all
        (use-callback
         (fn []
           (set-expanded-ids!
            (fn [ids]
              (if (empty? ids)
                (set (map :id sections))
                #{}))))
         [])

        scroll-to-section
        (use-callback
         (fn [section-id]
           (set-expanded-ids! (fn [ids] (conj ids section-id)))
           (js/setTimeout
            (fn []
              (when-let [el (js/document.getElementById (str "section-" section-id))]
                (.scrollIntoView el #js {:behavior "smooth" :block "start"})))
            50))
         [])

        tab-props {:guardian guardian :linked-students linked-students
                   :field-options field-options :api-client api-client}]

    ;; IntersectionObserver for TOC active-section tracking (profile view only)
    (use-effect
     (fn []
       (when (= active-view :profile)
         (let [observer (js/IntersectionObserver.
                         (fn [entries]
                           (doseq [entry entries]
                             (when (.-isIntersecting entry)
                               (let [^js target (.-target entry)]
                                 (set-active-section! (.. target -dataset -sectionId))))))
                         #js {:rootMargin "-15% 0px -75% 0px"
                              :threshold 0})]
           (doseq [{:keys [id]} sections]
             (when-let [el (js/document.getElementById (str "section-" id))]
               (.observe observer el)))
           (fn [] (.disconnect observer)))))
     [active-view])

    (cond
      (and loading? (nil? guardian))
      ($ :div {:class "px-6 py-12 text-center text-muted-foreground"} "Loading…")

      (nil? guardian)
      ($ :div {:class "px-6 py-12 text-center text-muted-foreground"}
         "Guardian not found.")

      :else
      ($ :div {:class "flex flex-col h-full min-h-0"}
         ;; Sticky header
         ($ :div {:class "sticky top-0 z-20 border-b bg-card/95 backdrop-blur-sm"}
            ($ :div {:class "p-6"}
               ($ guardian-header {:guardian guardian
                                   :guardian-id guardian-id
                                   :active-view active-view
                                   :on-set-view set-active-view!})))

         ;; Body
         ($ :div {:class "flex-1 px-6 pb-6"}
            (case active-view
              :profile
              ($ :div {:class "flex gap-6 pt-4"}
                 ($ :div {:class "flex-1 min-w-0"}
                    ($ section-block
                       {:id "guardian-contact" :title "Contact"
                        :collapsed? (not (contains? expanded-ids "guardian-contact"))
                        :on-toggle #(toggle-section "guardian-contact")}
                       ($ contact-content tab-props))
                    ($ section-block
                       {:id "guardian-address" :title "Address"
                        :collapsed? (not (contains? expanded-ids "guardian-address"))
                        :on-toggle #(toggle-section "guardian-address")}
                       ($ address-content tab-props))
                    ($ section-block
                       {:id "guardian-linked-students" :title "Linked Students"
                        :collapsed? (not (contains? expanded-ids "guardian-linked-students"))
                        :on-toggle #(toggle-section "guardian-linked-students")}
                       ($ linked-students-content tab-props)))
                 ($ section-toc {:sections sections
                                 :active-section-id active-section
                                 :all-collapsed? all-collapsed?
                                 :on-toggle-all toggle-all
                                 :on-click scroll-to-section}))

              :communications
              ($ :div {:class "pt-4"}
                 ($ communications-section
                    {:student-id guardian-id
                     :api-client api-client}))

              nil))

         ;; Modal always mounted so the header button works from any view
         ($ log-communication-modal {:api-client api-client})))))
