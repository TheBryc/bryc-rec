# BRYC Rec Live — student recommendation viewer

One static page (`index.html`, zero dependencies) that renders a student's live
recommendation from the BRYC app in the prototype's visual style.

## How it works
- The student's link carries the SAME share token the app already mints:
  `https://<your-host>/index.html?token=<token>`
- The page POSTs the app's own `student/recommendations` query (transit+json,
  decoder ported from bryc-scraper) and renders the **resolved** pool — so every
  counselor edit and override shows up automatically. It stores nothing and
  writes nothing.

## Publish
Any static host works. GitHub Pages:
```bash
cd bryc-rec-live
git init && git add . && git commit -m "rec viewer"
gh repo create bryc-rec-live --public --source=. --push
# then enable Pages (main branch, / root) in the repo settings
```

## Links for students
Counselor shares from the app as usual, then swap the domain in front of
`?token=...` — or bulk-generate tokens for a roster via the API
(`student-ops/generate-student-view-link`) into a sheet.

## Notes
- Logos render in a fixed 48px contain-fit box → consistent size regardless of
  source resolution/aspect.
- Handles both salary modes: PSEO earnings chart (LA publics) and LWC
  occupation-wage chart (out-of-state), plus What's Cool, careers + PUMS,
  About-This-School tiles, and the costs waterfall (net = COA − TOPS − Pell).
