(ns components.shared.terms-to-know
  "The 'Terms to Know' glossary block shown once at the bottom of the
   recommendations tab content. FIXED text — deterministic, present-by-data,
   NOT advisor-editable, and driven by NO LLM. Shared verbatim by BOTH the
   student recommendations page AND the advisor authoring view so the glossary
   reads identically. Styled to the teal aesthetic (#05a09c eyebrow dot,
   #2a6465 headwords, #313335 prose) used by the other rec_demo blocks."
  (:require [uix.core :as uix :refer [defui $]]))

(def ^:private terms
  "The five fixed glossary entries, in display order. Static definitions —
   changing them is a deliberate copy edit, never data- or advisor-driven."
  [{:term "Median"
    :def  "The middle value: half of people earn more, half earn less. A better gut-check than an average."}
   {:term "Living wage"
    :def  "The income a single adult needs to cover basic costs (housing, food, transportation) in a given area — here, Baton Rouge."}
   {:term "Net cost"
    :def  "What you actually pay after grants and scholarships — not the sticker price."}
   {:term "TOPS"
    :def  "Louisiana's state scholarship that helps cover tuition for eligible in-state students (TOPS-Tech for 2-year/technical programs)."}
   {:term "Pell Grant"
    :def  "A federal grant for students with financial need — money you don't repay."}])

(defui terms-to-know
  "The fixed 'Terms to Know' glossary. Pure presentational and input-independent:
   the same fixed definitions render regardless of any props passed."
  [_]
  ($ :div {:class "space-y-4 mt-10"}
     ($ :div {:class "flex items-center"}
        ($ :div {:class "w-2 h-2 bg-[#05a09c] rounded-full mr-3"})
        ($ :h3 {:class "text-sm font-semibold uppercase tracking-wide text-[#2a6465] font-head"}
           "Terms to Know"))
     ($ :div {:class "bg-white/60 backdrop-blur-sm rounded-2xl shadow-sm p-6"}
        ($ :dl {:class "space-y-4"}
           (for [{:keys [term def]} terms]
             ($ :div {:key term
                      :class "text-sm md:text-base text-[#313335] leading-relaxed"}
                ($ :dt {:class "font-semibold text-[#2a6465] font-head inline"} term)
                ($ :dd {:class "inline"} (str " — " def))))))))
