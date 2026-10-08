# ADR-003: A website on Firebase Hosting, deployed from GitHub with a deploy key
Date: 2026-10-08
Status: Accepted

## Context

Shawn bought the domain `milotriplog.top` and created a Firebase project for MilO ("i created a web project in firebase for another project reactimate. i want to do the same for milo"). His other project, Reactimate, is a web app on Firebase Hosting at `reactimate.top`, deployed by a GitHub Actions job on every push to `main` with a Firebase service-account key kept as a GitHub secret.

MilO is not a web app. It is an Android app with no backend, no account and no internet permission, and its privacy promise rests on that (ENGINEERING_STANDARDS §12). The repository and its APK download are public since 2026-10-08.

Three constraints from the standing rules:

- No secret is ever committed, and a new secret gets an ADR first (CLAUDE.md, ENGINEERING_STANDARDS §12). Until now the only secret was the signing keystore and its password, on the Mac.
- Every version is pinned and kept current; every GitHub Action is pinned to a commit SHA (§14).
- No one-off styling: the site should look like the app (§8).

## Decision

Asked on 2026-10-08, Shawn chose:

- **Content: "Landing + privacy page".** One page that says what MilO does, with the screenshots, a "Download the latest APK" button that opens the newest GitHub Release, the install steps and a privacy summary; and a privacy policy page. A not-found page besides.
- **Deploy: "Auto on merge, like Reactimate".** `.github/workflows/website.yml` deploys `website/` to the live site whenever a change to it (or to `firebase.json`, `.firebaserc` or the workflow) reaches `main`, and on demand from the Actions tab.
- **Tech: "Plain HTML and CSS".** Hand-written pages in `website/`, no build step, no npm, no JavaScript at all.
- **The Firebase project** is `milotriplog` (`.firebaserc`): its display name is "MilOtriplog", and the console's Project settings showed the ID `milotriplog` (Shawn's screenshot, 2026-10-08). It is on the free Spark plan, which includes Hosting and a custom domain.

And so:

- **The app is not changed and gets no Firebase.** No Firebase SDK, no analytics, no internet permission. The website is a separate thing that happens to live in the same repository.
- **The site lives in `website/`,** which is Firebase Hosting's public folder (`firebase.json`). The screenshots moved there from `docs/screenshots/`, so the README and the site show one copy. The font is a copy of the app's `sora.ttf`, with its licence beside it (`website/fonts/Sora-OFL.txt`), as the SIL Open Font License asks.
- **The site loads nothing from anywhere else** and runs no script: a Content-Security-Policy that allows only the site's own images, styles and font says so to the browser. It sets no cookies and has no analytics.
- **The deploy key** is a Google Cloud service-account key with Firebase Hosting rights. It lives only in the repository's GitHub Actions secrets, as `FIREBASE_SERVICE_ACCOUNT_MILOTRIPLOG`, which is the name `firebase init hosting:github` gives it. That command, run once on any computer that has a copy of `firebase.json` and `.firebaserc` (Shawn's Windows PC, on 2026-10-08), makes a service account for GitHub with the Hosting rights it needs and no more, and stores the key in GitHub; the key is never downloaded into the repository (`.gitignore` names the usual file names as a backstop). The workflow fails with instructions if the secret is missing.
- **Pins:** the deploy action is `FirebaseExtended/action-hosting-deploy` v0.11.0, by commit SHA, which Dependabot keeps current with the other actions. The Firebase command-line tool it runs is pinned to 15.33.0 in the workflow, where Dependabot cannot see it: raised by hand (`[DEBT]`).
- **The domain** is connected in the Firebase console (Hosting, Add custom domain), with the records it gives entered at the registrar of `milotriplog.top`. Firebase provides the HTTPS certificate.

## Consequences

- Better: a public page and a privacy policy at a real address, deployed by a merge like Reactimate's, with nothing to remember on the Mac.
- Worse: a second secret, and the first one outside the Mac. Whoever holds it can replace the website (not the app, not the repository, not the releases). It is revoked by deleting the service account's key in the Google Cloud console and is replaced by running `firebase init hosting:github` again.
- Worse: a third workflow, and Google sees the site's visitors as any web host does (the privacy page says so).
- The privacy page makes promises about the app. A change to what the app keeps or sends must change `website/privacy.html` in the same pull request.
- Locked into: Firebase Hosting for the site, which is easy to leave: the pages are plain files.
