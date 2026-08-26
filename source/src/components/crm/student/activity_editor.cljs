(ns components.crm.student.activity-editor
  "Editable component for extracurricular activities."
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/dialog" :as dialog]
            ["/gen/shadcn/components/ui/input" :refer [Input]]
            ["/gen/shadcn/components/ui/label" :refer [Label]]
            ["/gen/shadcn/components/ui/checkbox" :refer [Checkbox]]
            ["lucide-react" :refer [Plus Pencil Trash2]]))

(def grade-options
  [{:value "9th" :label "9th Grade"}
   {:value "10th" :label "10th Grade"}
   {:value "11th" :label "11th Grade"}
   {:value "12th" :label "12th Grade"}])

(def category-options
  [{:value "School-Based Activity" :label "School-Based Activity"}
   {:value "Community-Based Activity" :label "Community-Based Activity"}
   {:value "Work Experience" :label "Work Experience"}
   {:value "Home & Family" :label "Home & Family"}])

(defui activity-form
  "Form for editing a single activity.
   Props:
   - activity: activity map to edit (nil for new)
   - on-save: (fn [activity]) called when save is clicked
   - on-cancel: (fn []) called when cancel is clicked
   - saving?: boolean indicating save in progress"
  [{:keys [activity on-save on-cancel saving?]}]
  (let [[form-data set-form-data!] (use-state (or activity
                                                   {:activity ""
                                                    :role ""
                                                    :grades-involved #{}
                                                    :category ""}))

        update-field (fn [field value]
                       (set-form-data! (fn [fd] (assoc fd field value))))

        toggle-grade (fn [grade]
                       (set-form-data!
                         (fn [fd]
                           (let [current (or (:grades-involved fd) #{})]
                             (assoc fd :grades-involved
                                    (if (contains? current grade)
                                      (disj current grade)
                                      (conj current grade)))))))

        valid? (and (seq (str/trim (:activity form-data ""))))]

    ($ :div {:class "space-y-4"}
       ;; Activity name
       ($ :div {:class "space-y-2"}
          ($ Label {:htmlFor "activity-name"} "Activity Name")
          ($ Input
             {:id "activity-name"
              :value (:activity form-data "")
              :on-change #(update-field :activity (.. % -target -value))
              :placeholder "Enter activity name..."
              :disabled saving?}))

       ;; Role
       ($ :div {:class "space-y-2"}
          ($ Label {:htmlFor "activity-role"} "Role (optional)")
          ($ Input
             {:id "activity-role"
              :value (:role form-data "")
              :on-change #(update-field :role (.. % -target -value))
              :placeholder "e.g., President, Member, Volunteer..."
              :disabled saving?}))

       ;; Grades involved
       ($ :div {:class "space-y-2"}
          ($ Label "Grades Involved")
          ($ :div {:class "flex flex-wrap gap-4"}
             (for [{:keys [value label]} grade-options]
               (let [checked? (contains? (or (:grades-involved form-data) #{}) value)]
                 ($ :div {:key value :class "flex items-center space-x-2"}
                    ($ Checkbox
                       {:id (str "grade-" value)
                        :checked checked?
                        :onCheckedChange #(toggle-grade value)
                        :disabled saving?})
                    ($ Label
                       {:htmlFor (str "grade-" value)
                        :class "text-sm font-normal cursor-pointer"}
                       label))))))

       ;; Category (optional)
       ($ :div {:class "space-y-2"}
          ($ Label {:htmlFor "activity-category"} "Category (optional)")
          ($ :select
             {:id "activity-category"
              :value (:category form-data "")
              :on-change #(update-field :category (.. % -target -value))
              :disabled saving?
              :class "flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"}
             ($ :option {:value ""} "Select category...")
             (for [{:keys [value label]} category-options]
               ($ :option {:key value :value value} label))))

       ;; Buttons
       ($ :div {:class "flex justify-end gap-2 pt-4"}
          ($ button/Button
             {:variant "outline"
              :on-click on-cancel
              :disabled saving?}
             "Cancel")
          ($ button/Button
             {:on-click #(when valid? (on-save form-data))
              :disabled (or (not valid?) saving?)}
             (if saving? "Saving..." "Save"))))))

(defui activity-card
  "Display card for a single activity with edit/delete buttons.
   Props:
   - activity: activity map
   - on-edit: (fn []) called when edit is clicked
   - on-delete: (fn []) called when delete is clicked
   - disabled?: boolean to disable actions"
  [{:keys [activity on-edit on-delete disabled?]}]
  (let [grade-order (fn [g] (case g "9th" 9 "10th" 10 "11th" 11 "12th" 12 99))
        grades-str (when (seq (:grades-involved activity))
                     (str "Grades " (str/join ", " (sort-by grade-order (:grades-involved activity)))))]
    ($ :div {:class "p-3 bg-muted/50 rounded-lg group relative"}
       ($ :div {:class "flex items-start justify-between gap-2"}
          ($ :div {:class "flex-1"}
             ($ :p {:class "font-medium"} (:activity activity))
             (when (or (:role activity) grades-str (:category activity))
               ($ :p {:class "text-sm text-muted-foreground mt-1"}
                  (str/join " • " (filter some? [(:role activity) grades-str (:category activity)])))))

          ;; Action buttons (visible on hover)
          ($ :div {:class "flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity"}
             ($ button/Button
                {:variant "ghost"
                 :size "icon"
                 :class "h-8 w-8"
                 :on-click on-edit
                 :disabled disabled?}
                ($ Pencil {:class "h-4 w-4"}))
             ($ button/Button
                {:variant "ghost"
                 :size "icon"
                 :class "h-8 w-8 text-destructive hover:text-destructive"
                 :on-click on-delete
                 :disabled disabled?}
                ($ Trash2 {:class "h-4 w-4"})))))))

(defui editable-activities
  "Editable activities list with add/edit/delete functionality.
   Props:
   - value: vector of activity maps
   - on-save: (fn [activities]) called when activities change
   - saving?: boolean indicating save in progress"
  [{:keys [value on-save saving?]}]
  (let [[dialog-open? set-dialog-open!] (use-state false)
        [editing-index set-editing-index!] (use-state nil)
        [local-saving? set-local-saving!] (use-state false)

        activities (or value [])

        open-add-dialog (fn []
                          (set-editing-index! nil)
                          (set-dialog-open! true))

        open-edit-dialog (fn [idx]
                           (set-editing-index! idx)
                           (set-dialog-open! true))

        handle-save (fn [activity-data]
                      (set-local-saving! true)
                      (let [new-activities (if (some? editing-index)
                                             ;; Editing existing
                                             (assoc (vec activities) editing-index activity-data)
                                             ;; Adding new
                                             (conj (vec activities) activity-data))]
                        (on-save new-activities)
                        (set-dialog-open! false)
                        (set-local-saving! false)))

        handle-delete (fn [idx]
                        (when (js/confirm "Are you sure you want to delete this activity?")
                          (let [new-activities (vec (concat (subvec (vec activities) 0 idx)
                                                            (subvec (vec activities) (inc idx))))]
                            (on-save new-activities))))

        handle-cancel (fn []
                        (set-dialog-open! false)
                        (set-editing-index! nil))]

    ($ :div {:class "space-y-3"}
       ;; Activity list
       (if (seq activities)
         (for [[idx activity] (map-indexed vector activities)]
           ($ activity-card
              {:key idx
               :activity activity
               :on-edit #(open-edit-dialog idx)
               :on-delete #(handle-delete idx)
               :disabled? (or saving? local-saving?)}))
         ($ :p {:class "text-sm text-muted-foreground italic"} "No activities added yet."))

       ;; Add button
       ($ button/Button
          {:variant "outline"
           :size "sm"
           :on-click open-add-dialog
           :disabled (or saving? local-saving?)
           :class "mt-2"}
          ($ Plus {:class "h-4 w-4 mr-2"})
          "Add Activity")

       ;; Edit/Add dialog
       ($ dialog/Dialog {:open dialog-open? :onOpenChange set-dialog-open!}
          ($ dialog/DialogContent {:class "sm:max-w-md"}
             ($ dialog/DialogHeader
                ($ dialog/DialogTitle
                   (if (some? editing-index) "Edit Activity" "Add Activity")))
             ($ activity-form
                {:activity (when (some? editing-index) (get activities editing-index))
                 :on-save handle-save
                 :on-cancel handle-cancel
                 :saving? local-saving?}))))))
