(ns components.terms.core
  (:require [uix.core :as uix :refer [defui $]]))

(defui ^:private section [{:keys [title children]}]
  ($ :section {:class "mt-8"}
     ($ :h2 {:class "text-xl font-semibold tracking-tight mb-3"} title)
     children))

(defui main [_]
  ($ :div {:class "min-h-screen bg-background text-foreground"}
     ($ :div {:class "max-w-3xl mx-auto px-6 py-12"}
        ($ :header {:class "border-b pb-6 mb-8"}
           ($ :h1 {:class "text-3xl font-bold tracking-tight"} "BRYC Terms and Conditions")
           ($ :p {:class "text-sm text-muted-foreground mt-2"} "Last updated: May 4, 2026"))

        ($ :p {:class "leading-relaxed"}
           "These Terms and Conditions govern your use of BRYC's college and career advising services. "
           "By using the service or providing your information to BRYC, you agree to these terms. "
           "If you do not agree, please do not use the service.")

        ($ section {:title "The service"}
           ($ :p {:class "leading-relaxed"}
              "BRYC provides college and career advising to high school students, including personalized "
              "recommendations, communications with advisors, and related tools."))

        ($ section {:title "Eligibility and minors"}
           ($ :p {:class "leading-relaxed"}
              "BRYC is intended for high school students. Many students are minors. By providing information "
              "or accepting messages on behalf of a student under 18, a parent or guardian agrees to these "
              "terms on the student's behalf."))

        ($ section {:title "Your responsibilities"}
           ($ :ul {:class "list-disc pl-6 space-y-2"}
              ($ :li nil "Provide accurate information when completing intake or communicating with advisors.")
              ($ :li nil "Use the service only for lawful purposes and only as intended.")
              ($ :li nil "Keep any login credentials confidential and let us know promptly if you suspect unauthorized access.")))

        ($ section {:title "Text messaging (SMS)"}
           ($ :p {:class "mb-3"}
              "Text messages from BRYC are optional. If you opt in, you agree to receive text messages from "
              "BRYC related to advising services, including conversations with your advisor and reminders "
              "about deadlines, applications, and meetings. You do not need to consent to text messages to "
              "receive BRYC advising services, and providing a phone number does not, by itself, create SMS "
              "consent.")
           ($ :p {:class "mb-3"}
              "Reply " ($ :span {:class "font-mono"} "STOP") " to any message to unsubscribe. Reply "
              ($ :span {:class "font-mono"} "HELP") " for help. Message and data rates may apply. Message "
              "frequency varies.")
           ($ :p {:class "leading-relaxed"}
              "How we handle the information collected through text messaging is described in our "
              ($ :a {:href "/privacy" :class "underline"} "Privacy Policy") "."))

        ($ section {:title "Recommendations and advice"}
           ($ :p {:class "leading-relaxed"}
              "Recommendations, suggestions, and other information provided through BRYC are for guidance "
              "only. They are not guarantees of admission, financial aid, scholarships, or any other outcome. "
              "Decisions about applications, programs, and finances remain yours."))

        ($ section {:title "Intellectual property"}
           ($ :p {:class "leading-relaxed"}
              "BRYC and its content, design, and software are owned by BRYC and protected by applicable "
              "intellectual-property laws. You retain ownership of the information you submit. By submitting "
              "information, you grant BRYC permission to use it to provide the service to you."))

        ($ section {:title "Service provided as-is"}
           ($ :p {:class "leading-relaxed"}
              "BRYC is provided on an \"as-is\" and \"as-available\" basis without warranties of any kind, "
              "to the fullest extent permitted by law."))

        ($ section {:title "Limitation of liability"}
           ($ :p {:class "leading-relaxed"}
              "To the fullest extent permitted by law, BRYC and its team will not be liable for indirect, "
              "incidental, or consequential damages arising from your use of the service."))

        ($ section {:title "Termination"}
           ($ :p {:class "leading-relaxed"}
              "You may stop using the service at any time. We may suspend or end access if these terms are "
              "violated or if needed to protect the service or others."))

        ($ section {:title "Changes to these terms"}
           ($ :p {:class "leading-relaxed"}
              "We may update these terms from time to time. When we do, the new \"Last updated\" date will "
              "appear at the top of this page. Continued use of the service after an update means you accept "
              "the updated terms."))

        ($ section {:title "Contact"}
           ($ :p {:class "leading-relaxed"}
              "Questions about these terms? Email "
              ($ :a {:href "mailto:cameron@obney.ai" :class "underline"} "cameron@obney.ai") ".")))))
