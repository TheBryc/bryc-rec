(ns components.hub.core
  "Hub layout component with sidebar navigation for staff UI."
  (:require [uix.core :as uix :refer [defui $ use-state]]
            ["/gen/shadcn/components/ui/sidebar" :as sidebar]
            ["/gen/shadcn/components/ui/collapsible" :as collapsible]
            [components.context.interface :as context]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.advisor.dashboard.events :as dashboard-events]
            [store.auth.subs :as auth-subs]))

;; Icon paths (Heroicons outline style)
(def nav-icons
  {:dashboard "M3.75 6A2.25 2.25 0 016 3.75h2.25A2.25 2.25 0 0110.5 6v2.25a2.25 2.25 0 01-2.25 2.25H6a2.25 2.25 0 01-2.25-2.25V6zM3.75 15.75A2.25 2.25 0 016 13.5h2.25a2.25 2.25 0 012.25 2.25V18a2.25 2.25 0 01-2.25 2.25H6A2.25 2.25 0 013.75 18v-2.25zM13.5 6a2.25 2.25 0 012.25-2.25H18A2.25 2.25 0 0120.25 6v2.25A2.25 2.25 0 0118 10.5h-2.25a2.25 2.25 0 01-2.25-2.25V6zM13.5 15.75a2.25 2.25 0 012.25-2.25H18a2.25 2.25 0 012.25 2.25V18A2.25 2.25 0 0118 20.25h-2.25A2.25 2.25 0 0113.5 18v-2.25z"
   :operations "M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z"
   :advising "M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
   :people "M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
   :students "M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
   :messages "M21.75 6.75v10.5A2.25 2.25 0 0119.5 19.5h-15a2.25 2.25 0 01-2.25-2.25V6.75m19.5 0A2.25 2.25 0 0019.5 4.5h-15a2.25 2.25 0 00-2.25 2.25m19.5 0v.243a2.25 2.25 0 01-1.07 1.916l-7.5 4.615a2.25 2.25 0 01-2.36 0L3.32 8.91a2.25 2.25 0 01-1.07-1.916V6.75"
   :fellows "M4.26 10.147a60.438 60.438 0 0 0-.491 6.347A48.62 48.62 0 0 1 12 20.904a48.62 48.62 0 0 1 8.232-4.41 60.46 60.46 0 0 0-.491-6.347m-15.482 0a50.636 50.636 0 0 0-2.658-.813A59.906 59.906 0 0 1 12 3.493a59.903 59.903 0 0 1 10.399 5.84c-.896.248-1.783.52-2.658.814m-15.482 0A50.717 50.717 0 0 1 12 13.489a50.702 50.702 0 0 1 7.74-3.342M6.75 15a.75.75 0 1 0 0-1.5.75.75 0 0 0 0 1.5Zm0 0v-3.675A55.378 55.378 0 0 1 12 8.443m-7.007 11.55A5.981 5.981 0 0 0 6.75 15.75v-1.5"
   :chevron-right "M9 5l7 7-7 7"
   :logout "M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"})

;; Simple navigation item component
(defui nav-item [{:keys [label icon-path active? on-click]}]
  ($ sidebar/SidebarMenuItem
     ($ sidebar/SidebarMenuButton
        {:isActive active?
         :on-click on-click}
        ($ :svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "2"}
           ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d icon-path}))
        ($ :span label))))

;; Collapsible navigation group component
(defui nav-group [{:keys [label icon-path items active-route navigate! default-open]}]
  (let [[open? set-open!] (use-state (or default-open false))]
    ($ collapsible/Collapsible {:open open? :onOpenChange set-open! :class "group/collapsible"}
       ($ sidebar/SidebarMenuItem
          ($ collapsible/CollapsibleTrigger {:asChild true}
             ($ sidebar/SidebarMenuButton
                ($ :svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "2"}
                   ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d icon-path}))
                ($ :span label)
                ($ :svg {:class (str "ml-auto w-4 h-4 transition-transform duration-200 "
                                     (if open? "rotate-90" ""))
                         :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "2"}
                   ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d (:chevron-right nav-icons)}))))
          ($ collapsible/CollapsibleContent
             ($ sidebar/SidebarMenuSub
                (for [{:keys [route-name route-params label]} items]
                  ($ sidebar/SidebarMenuSubItem {:key (str route-name)}
                     ($ sidebar/SidebarMenuSubButton
                        {:isActive (= active-route route-name)
                         :on-click #(navigate! route-name (or route-params {}))}
                        ($ :span label))))))))))

;; Main hub layout component
(defui hub-layout [{:keys [active-route children]}]
  (let [ctx (context/use-context)
        navigate! (:router/navigate! ctx)
        api-client (:api/client ctx)
        user (use-subscribe [::auth-subs/user])]

    ($ sidebar/SidebarProvider
       ;; Sidebar
       ($ sidebar/Sidebar {:collapsible "icon"}
          ;; Sidebar Header with Branding
          ($ sidebar/SidebarHeader {:class "p-4 border-b border-sidebar-border"}
             ($ :div {:class "flex items-center gap-3"}
                ($ :div {:class "w-8 h-8 rounded bg-primary flex items-center justify-center"}
                   ($ :span {:class "text-primary-foreground font-bold text-sm"} "B"))
                ($ :div {:class "group-data-[collapsible=icon]:hidden"}
                   ($ :p {:class "text-xs font-medium uppercase tracking-wider text-muted-foreground"} "BRYC")
                   ($ :p {:class "text-sm font-semibold"} "Hub"))))

          ;; Sidebar Content
          ($ sidebar/SidebarContent
             ($ sidebar/SidebarGroup
                ($ sidebar/SidebarGroupContent
                   ($ sidebar/SidebarMenu
                      ;; Dashboard (top-level)
                      ($ nav-item {:label "Dashboard"
                                   :icon-path (:dashboard nav-icons)
                                   :active? (= active-route :hub-dashboard)
                                   :on-click #(navigate! :hub-dashboard)})

                      ;; Communication (top-level)
                      ($ nav-item {:label "Communication"
                                   :icon-path (:messages nav-icons)
                                   :active? (= active-route :hub-messages)
                                   :on-click #(navigate! :hub-messages)})

                      ;; Operations group (collapsible)
                      ($ nav-group {:label "Operations"
                                    :icon-path (:operations nav-icons)
                                    :default-open true
                                    :active-route active-route
                                    :navigate! navigate!
                                    :items [{:route-name :hub-advising
                                             :label "Advising"}
                                            {:route-name :hub-fellows
                                             :label "Fellows"}]})

                      ;; People group (collapsible)
                      ($ nav-group {:label "People"
                                    :icon-path (:people nav-icons)
                                    :default-open false
                                    :active-route active-route
                                    :navigate! navigate!
                                    :items [{:route-name :hub-students
                                             :label "Students"}
                                            {:route-name :hub-staff
                                             :label "Staff"}
                                            {:route-name :hub-duplicates
                                             :label "Duplicate review"}]})))))

          ;; Sidebar Footer with Sign Out
          ($ sidebar/SidebarFooter {:class "border-t border-sidebar-border p-2"}
             ($ sidebar/SidebarMenu
                ($ sidebar/SidebarMenuItem
                   ($ sidebar/SidebarMenuButton
                      {:on-click #(rf/dispatch [::dashboard-events/logout api-client navigate!])}
                      ($ :svg {:class "w-4 h-4" :fill "none" :viewBox "0 0 24 24" :stroke "currentColor" :strokeWidth "2"}
                         ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :d (:logout nav-icons)}))
                      ($ :span "Sign Out"))))))

       ;; Main Content Area
       ($ sidebar/SidebarInset
          ;; Top Header
          ($ :header {:class "flex h-14 items-center gap-4 border-b bg-background px-6"}
             ($ sidebar/SidebarTrigger)
             ($ :div {:class "flex-1"}))

          ;; Page Content
          ($ :main {:class "flex-1 p-6"}
             children)))))
