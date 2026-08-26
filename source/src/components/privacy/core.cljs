(ns components.privacy.core
  (:require [uix.core :as uix :refer [defui $]]))

(defui ^:private section [{:keys [title children]}]
  ($ :section {:class "mt-8"}
     ($ :h2 {:class "text-xl font-semibold tracking-tight mb-3"} title)
     children))

(defui main [_]
  ($ :div {:class "min-h-screen bg-background text-foreground"}
     ($ :div {:class "max-w-3xl mx-auto px-6 py-12"}
        ($ :header {:class "border-b pb-6 mb-8"}
           ($ :h1 {:class "text-3xl font-bold tracking-tight"} "BRYC Privacy Policy")
           ($ :p {:class "text-sm text-muted-foreground mt-2"} "Last updated: May 4, 2026"))

        ($ :p {:class "leading-relaxed"}
           "BRYC provides college and career advising services to high school students. "
           "This policy describes what information we collect about students and parents or guardians, "
           "how we use it, how we share it, and the choices you have.")

        ($ section {:title "Information we collect"}
           ($ :p {:class "mb-3"} "We collect the following categories of information, primarily through our intake form and through ongoing advising:")
           ($ :ul {:class "list-disc pl-6 space-y-2"}
              ($ :li nil
                 ($ :span {:class "font-medium"} "Contact information") " — names, email addresses, phone numbers, and mailing area for the student and, where provided, a parent or guardian.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "Demographic information") " — birthdate, gender, pronouns, race or ethnicity, primary language, and family circumstances students choose to share.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "Academic information") " — school, graduation year, grades, test scores, and transcripts the student uploads.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "Goals and preferences") " — career interests, preferred schools and programs, essays, and other open-ended responses.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "Account information for outside services") " — when a student chooses to share login details for school portals or testing services, we store those credentials in a protected form so only authorized advising activity can use them.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "Communications") " — records of messages exchanged between students, parents or guardians, and BRYC advisors, including text messages and emails.")))

        ($ section {:title "How we use information"}
           ($ :p {:class "leading-relaxed"}
              "We use the information we collect to deliver advising services, generate personalized college, "
              "scholarship, and career recommendations, communicate with students and families, and operate, "
              "secure, and improve the service."))

        ($ section {:title "Text messaging (SMS)"}
           ($ :p {:class "mb-3"} "We use text messaging in three ways:")
           ($ :ul {:class "list-disc pl-6 space-y-2 mb-4"}
              ($ :li nil "One-to-one conversations between students and their BRYC advisor.")
              ($ :li nil "Reminders and notifications about deadlines, applications, and meetings.")
              ($ :li nil "The same kinds of messages sent to a parent or guardian when that parent or guardian has opted in or requested SMS communication."))
           ($ :p {:class "mb-3"}
              ($ :span {:class "font-medium"} "Consent. ")
              "Students may choose whether to opt in to text messages from BRYC. SMS consent is optional "
              "and is not required to receive BRYC advising services. Providing a phone number does not, by "
              "itself, create consent to receive text messages.")
           ($ :p {:class "mb-3"}
              ($ :span {:class "font-medium"} "Opt out. ")
              "Reply " ($ :span {:class "font-mono"} "STOP") " to any message to unsubscribe. Reply "
              ($ :span {:class "font-mono"} "HELP") " for help. Message and data rates may apply. Message frequency varies.")
           ($ :p {:class "leading-relaxed"}
              ($ :span {:class "font-medium"} "Mobile data is not shared for marketing. ")
              "No mobile information will be shared with third parties or affiliates for marketing or "
              "promotional purposes. All other categories exclude text messaging originator opt-in data and "
              "consent; this information will not be shared with any third parties."))

        ($ section {:title "How we share information"}
           ($ :p {:class "mb-3"} "We share information only in these limited categories:")
           ($ :ul {:class "list-disc pl-6 space-y-2 mb-3"}
              ($ :li nil
                 ($ :span {:class "font-medium"} "Service providers that help us operate BRYC") " — companies that host the service, send emails and text messages, store uploaded files, and provide security and analytics on our behalf. They are only permitted to use the information to provide their services to BRYC.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "AI and recommendation providers") " — to generate personalized college, scholarship, and career recommendations, we send relevant student profile information to providers that operate large language models. These providers process the information to return recommendations and are not permitted to use it to train their general models or for their own purposes.")
              ($ :li nil
                 ($ :span {:class "font-medium"} "When required by law") " — to comply with legal obligations or to protect rights, safety, and security."))
           ($ :p {:class "leading-relaxed"}
              "We do not sell personal information, and we do not share mobile phone numbers or text-message "
              "content for marketing."))

        ($ section {:title "How we protect information"}
           ($ :p {:class "leading-relaxed"}
              "We protect information using encryption in transit and at rest, restricted staff access, and "
              "additional protection for sensitive items such as outside-service credentials."))

        ($ section {:title "How long we keep information"}
           ($ :p {:class "leading-relaxed"}
              "We keep information for as long as needed to provide advising services and as required by "
              "law. You may request deletion using the contact information below."))

        ($ section {:title "Students under 18, parents, and guardians"}
           ($ :p {:class "leading-relaxed"}
              "Many BRYC students are minors. Parents and guardians may request to access, correct, or "
              "delete their student's information."))

        ($ section {:title "Your choices and rights"}
           ($ :p {:class "leading-relaxed"}
              "You may request access to, correction of, or deletion of your information, and you may opt "
              "out of text messages at any time by replying " ($ :span {:class "font-mono"} "STOP") "."))

        ($ section {:title "Contact"}
           ($ :p {:class "leading-relaxed"}
              "Questions or requests about this policy? Email "
              ($ :a {:href "mailto:cameron@obney.ai" :class "underline"} "cameron@obney.ai") "."))

        ($ section {:title "Changes to this policy"}
           ($ :p {:class "leading-relaxed"}
              "When we update this policy, the new \"Last updated\" date will appear at the top of the page.")))))
