# Engineering Standards & Architecture

> **Purpose.** This is the single source of truth for *how* this app is built — the decisions made **once, upfront**, so they don't get re-litigated ad-hoc inside every feature. The technical debt on past projects came almost entirely from decisions that were never made explicitly. This document makes them explicit before line one of product code.
>
> **How to use it.** Read it before you start. Follow it while you build. When a rule stops making sense, *change the rule here first* — don't quietly break it in code. A standard that lives in your head isn't a standard; it's a memory that fades under deadline pressure.
>
> **This is one of four documents** that work together — see §17 for how they fit.
>
> **Status:** Living document · **Owner:** Shawn · **Last updated:** 2026-10-08

---

## 0. The one rule that prevents most debt

> **No decision gets made twice, and no decision gets made implicitly.**

Every time you face a "how should I do X?" question (where to put a file, how to name a thing, which library, how to handle errors), you either:
1. Find the answer already written here → follow it, or
2. Don't find it → **stop, decide, write it here, then code.**

That five-minute habit is the entire game. Ad-hoc development is just this loop skipped a few hundred times.

---

## 1. Project profile

| Field | Decision |
|---|---|
| App name | MilO (`com.shawnkowalchuk.milo`) |
| One-line purpose | Logs business mileage in Shawn's work truck automatically and produces a monthly PDF. |
| Platforms | Android only, native Kotlin, Android 14 and newer. One phone: Xiaomi POCO X5 Pro 5G, Android 14 (HyperOS 2.0). Phone UI plus an Android Auto screen. Never on the Play Store. |
| Primary user | Shawn only. No accounts. |
| Target launch milestone | Phase 1: a trip starts by itself every time the truck connects. |
| Repo URL | https://github.com/shawnkowalchuk/MilO (public since 2026-10-08, private before; no license, all rights reserved) |

---

## 2. Tech stack (locked decisions)

Pinned. Changing the stack mid-project is the most expensive form of debt there is; if you must, write an ADR (§15). Full pin list and reasons: `docs/adr/ADR-001-stack-selection.md`. Live versions: `gradle/libs.versions.toml`. A library enters the build only when code needs it.

| Layer | Choice | Notes / rationale |
|---|---|---|
| **Language** | **Kotlin 2.4.20, `allWarningsAsErrors`** | Non-negotiable. A compiler warning fails the build. |
| Build | AGP 9.4.1, Gradle 9.8.0, JDK 17, KSP 2.3.12 | One `:app` module. Kotlin is built into AGP 9: never apply `org.jetbrains.kotlin.android` or kapt. |
| SDK levels | minSdk 34, compileSdk 37, targetSdk 37 | Android 17 is the newest stable platform. The phone runs API 34 (Android 14), and MilO runs on nothing older: Shawn's decision of 2026-10-06 (FINDINGS_LOG, 2026-10-07). **No code is written for an older Android:** it could be tested nowhere. A branch on the Android version is only for something that differs from Android 14 upwards. |
| UI | Jetpack Compose, Material 3 (Compose BOM 2026.09.00) | One UI toolkit. No XML layouts. |
| Navigation | Navigation 3 1.2.0; kotlinx-serialization-json 1.11.0 for its back-stack keys | Navigation 2 is in maintenance mode. |
| State | ViewModel (lifecycle 2.11.0), coroutines 1.11.0 | A screen's state lives in its ViewModel. No global store. |
| Storage | Room 3.0.3 on bundled SQLite; DataStore Preferences 1.2.1 for settings | New app, so start on Room's current line. |
| Background work | WorkManager 2.12.0 | Only for jobs that can wait. Never to start a trip. Not in the build yet: no job has needed it. The monthly reminder and the daily "nothing recorded" check each use one inexact `AlarmManager` alarm a day instead (FINDINGS_LOG, 2026-10-06). |
| Location | play-services-location 21.4.0 | Fused location provider. |
| Android Auto | `androidx.car.app` 1.8.0-rc01 | The in-truck screen and `CarConnection`. **A release candidate: the one exception to "stable only",** taken on 2026-10-06 for its security fix and to be replaced by the stable 1.8.0 the day that is released (ADR-001, "Exception, 2026-10-06"). |
| Dependency injection | Manual: an `AppContainer` created by the `Application` class | **No Hilt.** Small app, and Hilt's plugin is tied to AGP majors. |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine | Plain JVM tests. **No Robolectric** (§11). |
| Backend, auth, hosting, analytics | **None** | One user. All data stays on the phone. |
| Error tracking | In-app event log (phase 1) | **No Sentry.** Crashes and system kills go to the event log. |

**Dependency rule:** every new dependency is a liability you maintain forever. Before adding one, ask: *can I write this in <50 lines myself?* If yes, write it. If no, pick the most-maintained option.

### Dependency currency policy

The goal is **current *and* stable**: the latest stable release (no alpha, beta, RC or milestone), proven by a passing build.

- **Pin exact versions** in `gradle/libs.versions.toml` as plain strings: no ranges, no dynamic versions. Plugins are applied through the catalog. The Gradle wrapper is pinned with `distributionSha256Sum`.
- **No Gradle lockfile and no dependency verification metadata.** Update bots cannot regenerate them for Android, so both mean hand work on every update.
- **Dependabot** finds updates: weekly, minor and patch grouped, majors separate. **Dependabot, not Renovate:** Dependabot is native to GitHub, free on private repos, and handles the catalog and the Gradle wrapper.
- **Update cadence:** merge update PRs weekly. A year of skipped upgrades is a migration project.
- **Never upgrade a major version blind.** Read the breaking-changes, do it in its own branch, let CI + the device checklist (§11) confirm. One major per branch.
- **A pre-release is taken only by Shawn's decision, written into ADR-001 with its reason and its end.** There is one: the Car App Library's 1.8.0-rc01 (2026-10-06). While a library is on a pre-release, Dependabot offers further pre-releases of it, so such a pin comes with an `ignore` rule in `.github/dependabot.yml` that hides that library's pre-releases. **The rule names ranges of pre-releases only, never "everything above" a version:** Dependabot's own pull request for the stable release does not touch that file, so the rule must do no harm if it is still there afterwards. It is taken out with the pin, as tidying.

---

## 3. Project structure

Use a **feature-first** layout, not a type-first one. Group by *what it does*, not *what kind of file it is*, so features are easy to find, change, and delete cleanly.

```
com.shawnkowalchuk.milo   # one Gradle module, ':app'
  app/                    # MiloApplication, the AppContainer, MainActivity, navigation host
  feature/<name>/         # one package per feature: its screens, its ViewModel, its own logic
  core/designsystem/      # theme tokens, the shared base components, and the words two
                          #   features must say alike. The only core package that may use
                          #   Android's resources
  core/trip/              # the trip rules (ADR-002): pure Kotlin with unit tests
  core/schedule/          # the work schedule and Business or Personal: pure Kotlin with unit
                          #   tests. It sorts a trip that has closed; it never starts one
  core/report/            # the report for the accountant: its rules, its layout and its CSV.
                          #   Pure Kotlin with unit tests; the drawing is in platform/
  core/util/              # pure Kotlin helpers with unit tests (time, formatting)
  data/                   # Room, DAOs, repositories, DataStore, crash files: the only layer that
                          #   touches storage
  platform/               # the only layer that touches Android system services: Bluetooth,
                          #   companion device, location, driving detection, notifications, alarms,
                          #   audio, Android Auto
```

Outside the app module, `website/` is the public website (ADR-003): plain HTML and CSS, Firebase Hosting's public folder (`firebase.json`). It shares no code with the app; its colours, corners and font are copied from the design system and said so in `website/styles.css`.

**Rules:**
- **Flow:** Composable → ViewModel → repository or platform class. Composables never call system services or DAOs directly.
- **The Android entry points live in `app/`:** `MiloApplication` and `MainActivity`. No class sits in the root package.
- Features never import from each other. Shared code moves to `core/` or `data/`. This keeps features deletable. Kotlin cannot enforce it inside one module; review does.
- **No file over ~300 lines, no Composable over ~200.** When a file gets fat, split it. Checked in review.

---

## 4. Coding standards

- **Naming:** `PascalCase` for classes and Composables, `camelCase` for functions and variables, `UPPER_SNAKE_CASE` for constants. **Kotlin files are `PascalCase`, named after their main class.**
- **Kotlin:** warnings are errors. No `@Suppress` and no disabled lint check without a comment explaining why. No explicit-API mode; that is for libraries.
- **Functions do one thing.** If you need "and" to describe it, split it.
- **No magic numbers/strings.** Name them as constants. UI values come from design tokens (§8).
- **Comments explain *why*, not *what*.**
- **Errors are handled, never swallowed.** No empty `catch {}`.

### Automated enforcement (set up before writing features)
- [x] **Kotlin `allWarningsAsErrors`** for app and test code. `org.gradle.kotlin.dsl.allWarningsAsErrors` in `gradle.properties` does the same for the Gradle Kotlin scripts.
- [x] **Android Lint** with `warningsAsErrors` and `abortOnError`
- [x] **Spotless 8.10.3 driving ktlint 1.8.0** over Kotlin sources and Gradle Kotlin scripts
- [x] **Pre-commit hook** — a committed plain script in `.githooks/` (no Husky, no Node): gitleaks staged scan, then `spotlessCheck`. Fails loudly with an install hint when gitleaks is missing. The script is written; switching it on (`core.hooksPath`, gitleaks installed) is a §18 item.

**After any change to `.editorconfig`,** check formatting with a fresh JVM: `./gradlew spotlessCheck --no-daemon --rerun-tasks --no-configuration-cache --no-build-cache`. A Gradle daemon that is already running keeps applying the old rules, so a plain `spotlessCheck`, and the pre-commit hook, can pass when they should fail. CI starts a fresh JVM, so CI is the authority for that change.

**No detekt.** No stable release works with AGP 9 and Kotlin 2.4. Revisit when detekt 2.0.0 is stable.

---

## 5. Git & version control discipline

- **`main` always builds and installs.** Never commit directly to it.
- **Branch per change:** `feat/trip-detection`, `fix/grace-timer`, `chore/upgrade-agp`.
- **Conventional Commits:** `feat:`, `fix:`, `chore:`, `refactor:`, `docs:`, `test:`.
- **Small, frequent commits.** One logical change each.
- **PRs even when solo** — open against `main`, let CI run, self-review the diff.
- **`.gitignore` set up before the first commit** — build output, `local.properties`, IDE files, keystores, OS junk.

---

## 6. AI-assisted development guardrails

This section matters most for you. AI assistance is how the last apps got built — and **unscoped AI assistance is exactly how the technical debt accumulated.**

- **Point the AI at the docs first.** It reads this file + ARCHITECTURE.md + the relevant APP_ENCYCLOPEDIA.md entry before any change.
- **Scope every AI change to one feature/file at a time.**
- **You review every line before it merges.** AI output is a draft, not a commit.
- **The AI must ask, not assume, on two things:** (1) phone UI, Android Auto screen, or both? (§9), and (2) a *new* UI pattern, or an existing one? (§8).
- **Never let AI invent a new pattern when one exists.** Consistency beats cleverness.
- **Automated PR review** is not set up. If added, it is a backstop, not a substitute for yours.
- **Refactors are separate, explicit tasks.** Never mixed into a feature diff.
- **After the change ships, append to FINDINGS_LOG.md** (§17).

---

## 7. Architecture principles

- **Separate the layers:** Composable ↔ ViewModel ↔ repository or platform class (§3).
- **One source of truth per piece of state.** The database is the truth. A ViewModel observes it and keeps no copy "just in case."
- **Composables are dumb by default.** Push logic into the ViewModel or `core/util/`.
- **Side effects live at the edges** (`data/`, `platform/`), not in Composables.
- **Build for deletion.** The best architecture lets you rip a feature out cleanly when it doesn't work.

---

## 8. UI consistency & the design system

**Once a UI pattern is decided, every other piece of UI follows it.** No exceptions, no one-offs. Inconsistent UI is debt you can *see*.

- **Establish the system once, up front:** design tokens (colour, spacing, typography, shape) live in `core/designsystem/`. Every UI value references a token — **never** a hard-coded colour or `dp` value in a screen.
- **Build a shared component library** in `core/designsystem/`: one canonical version of each base component. New screens *compose* these.
- **The first time you solve an interaction, that solution becomes the standard.** How errors surface, how loading and empty states look, how a list row behaves — decide it once.
- **Before building any UI, check:** does a component/pattern for this already exist? If yes, reuse it. If no, that is a deliberate addition to the shared library (document it), not a quiet one-off.
- A quick way to audit: if two screens that do similar things look or behave differently, one of them is wrong.

---

## 9. Feature kickoff — answer before you build

Before writing a feature, answer these. In an AI-assisted flow, **the assistant asks these and waits for your answer rather than assuming.**

1. **Surface scope — which UI surface does this feature ship on?** MilO has two. Explicitly decide: **phone UI, Android Auto screen, or both?** Never default to one or assume both.
2. **Does this reuse an existing UI pattern (§8), or introduce a new one?** If new, is that deliberate?
3. **Where does it live** in the feature-first structure (§3)?
4. **What's the smallest version** that delivers the value? (Ship that first.)
5. **Does it need an ADR** (a non-obvious decision/tradeoff)?

> **Two-surface note:** everything except presentation is shared. Only the screen differs; logic both surfaces need lives outside the feature packages. Two copies of the same logic *will* drift.

---

## 10. Definition of Done

Code isn't "done" until **all** are true:

- [ ] Kotlin compiles with no warnings
- [ ] `spotlessCheck`, `lintDebug` and `testDebugUnitTest` pass
- [ ] No leftover debug logging (`println`, throwaway `Log.d`), no commented-out code
- [ ] Errors handled; a failure that affects a trip is written to the event log
- [ ] No secrets or keys in the diff
- [ ] UI uses existing tokens/components — no one-off styles (§8)
- [ ] Surface scope matches what was decided at kickoff (§9)
- [ ] Self-reviewed the full PR diff
- [ ] Works on the POCO X5 itself, not just the emulator. Work whose device checks have not run yet is merged all the same (§11), and the documents then say that it is unproven on the phone
- [ ] FINDINGS_LOG.md updated; APP_ENCYCLOPEDIA.md updated if behavior changed

---

## 11. Testing strategy (pragmatic, solo-founder version)

- **Unit test `core/util/`** — pure functions (distance, time, formatting), cheap, high value.
- **Unit test the trip rules** — grace period, minimum distance, point filtering, Business or Personal by schedule. Written as pure Kotlin so they run as plain JVM tests.
- **No Robolectric.** Its current release needs Java 21 to simulate API 36 and 37, and the Mac has only JDK 17. Behaviour that needs Android is tested on the phone.
- **Skip trivial UI tests.**
- **Device test checklist** — `docs/DEVICE_TEST_CHECKLIST.md`, run on the POCO X5. A phase's behaviour counts as proven only when its checks have run there. **The checks do not hold up the next phase:** on 2026-10-05 Shawn replaced "stop after each phase" with "carry on through the phases without a stop in between", so the next phase starts when the last one is merged and the checks are run beside the later work (FINDINGS_LOG, 2026-10-06). If a check fails, that fix comes before anything built on top of it. Bluetooth triggers, background starts and HyperOS limits cannot be tested anywhere else. Every change that adds behaviour only the phone can prove adds its checks to that file in the same change.
- Tools: JUnit 4, kotlinx-coroutines-test, Turbine (`./gradlew testDebugUnitTest`).

The bar: *would a bug here lose a trip, or put a wrong number on the report accounts reads?* If yes, test it.

---

## 12. Security baseline

### Secrets & passwords — never hardcoded, always prompted or injected

**No password, API key, token, or secret is ever written into source code, a script, or anything committed to git.** Absolute. A secret committed once lives in git history forever, even after you delete it.

- **This app has no secrets.** No backend, no accounts, no API keys, so no `.env` and nothing to inject.
- **The one secret is the signing keystore and its password.** The keystore is `~/keys/milo.jks`, outside the repo. Its password is kept in the macOS Keychain under the name `milo-keystore`, and the build reads it from there each time (`app/build.gradle.kts`); it is in no file in the repo and is never typed into a script. Three things keep it from spreading: Gradle's configuration cache is switched off, because it would save a copy in the project's `.gradle` folder; an Android Studio sync is given a placeholder, because Studio saves what a sync returns; and nothing prints it. What the Keychain does not do is hide it from other programs running as Shawn, which can read the entry without a prompt. Both the keystore and the password must be backed up off the Mac: without them MilO can only be reinstalled by wiping its trips. **A missing keystore or a missing Keychain entry stops the build**, with instructions. The only places a build without the key is allowed are GitHub's CI and a run with `-Pmilo.signing.debugKey=true`; there the debug build gets the throwaway debug key, the release build is left unsigned, and neither may ever be installed over the real one.
- **The website's deploy key** (since 2026-10-08, ADR-003): a Google Cloud service-account key that can deploy to Firebase Hosting, for `milotriplog.top`. It is in GitHub's Actions secrets as `FIREBASE_SERVICE_ACCOUNT_MILOTRIPLOG` and nowhere else: made and stored there by `firebase init hosting:github` on the Mac, never downloaded into the repository (`.gitignore` names the usual key file names as a backstop). The deploy workflow fails with instructions if it is missing. It can replace the website and nothing else; it is revoked in the Google Cloud console. The app knows nothing of it.
- **If a secret is ever added,** it is prompted for or injected at runtime, never written into a script, and a missing one fails loudly at startup.
- **Secret-scanning (gitleaks)** in the pre-commit hook and in CI.

### Other baselines
- **Location data stays on the phone.** It leaves only in files Shawn sends or saves himself (PDF, CSV, and since 2026-10-06 the export file, which holds every trip's positions and, unless he switches them off, every raw GPS point), in his own Android backup (switched on since 2026-10-06: the trips with their positions and addresses go to his Google account, the raw points only to a phone he moves to), and as coordinates given to Android's Geocoder for an address lookup. There is no server. **MilO itself still sends nothing:** it has no INTERNET permission and no storage permission; a file is written where Android's own file picker put it, and the backup is Android's.
- **No personal data in logs that leave the device** (Logcat, CI output, crash text): no coordinates, addresses or names. The in-app event log stays on the phone unless Shawn shares it himself: since 2026-10-06 the Log screen can hand it to Android's share sheet as a text file. That file is his to send, and the screen says before the press what it holds: the truck's and the other paired devices' names and Bluetooth addresses, and every address he typed or replaced on the edit screen (an edit's line names the address before and after). It holds no GPS position, and that must stay so: nothing written to the event log may contain one.
- **Validate all input** — what Shawn types and any imported file. An import checks the whole file before it touches anything, refuses what it does not know (a newer format, an unknown part, a value that cannot be), and takes nothing about the truck's pairing on the file's word (`data/transfer/ExportChecks.kt`, `platform/transfer/TruckArrival.kt`).
- **Least privilege** — declare only the permissions a built feature uses.
- **Dependency scanning** — Dependabot alerts on the GitHub repo. `.github/workflows/dependency-graph.yml` sends GitHub the full list of libraries the build resolves, on every push to `main`. It holds the only write permission in the workflows (`contents: write`, which GitHub's submission API requires). Shawn must switch on the dependency graph and Dependabot alerts in the repo settings, or it has no effect.

---

## 13. Environments & configuration

- **One environment.** There is no backend, so `dev` / `staging` / `prod` have nothing to separate. The only build is the debug build installed on Shawn's phone from Android Studio.
- **That build holds the real trips.** Never uninstall to fix a problem, and never lower `versionCode` (that forces an uninstall). Uninstalling deletes the data.
- **No environment variables and no `.env.example`.** User settings live in DataStore.

---

## 14. CI/CD & releases

- **CI on every PR** (GitHub Actions, one job): gitleaks scan, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug` on JDK 17. Red build = no merge.
- **A second workflow runs on push to `main` only.** It submits the dependency graph for Dependabot alerts (§12). It checks nothing and blocks nothing.
- **A third workflow, `website.yml`, deploys the website** (since 2026-10-08, ADR-003): on push to `main` when `website/`, `firebase.json`, `.firebaserc` or the workflow changed, and by hand from the Actions tab. It uploads `website/` to Firebase Hosting's live site with the deploy key (§12). Plain files, no build; it checks nothing. The Firebase command-line tool it runs is pinned in the workflow, where Dependabot cannot see it, and is raised by hand.
- **That rule is kept by hand.** GitHub Free could not block merges while the repo was private. Since it is public (2026-10-08), branch protection can require the CI check before a merge; it is not switched on yet, so for now only Shawn stops a red build from merging.
- **CI cannot test a Bluetooth-triggered start.** The device checklist (§11) does.
- **Reproducible builds** — the Gradle wrapper is committed and checksum-pinned; every version is an exact pin. A GitHub Action is pinned to a full commit SHA with its release in a trailing comment (`# v7.0.1`), because a tag can be moved to different code.
- **Tag releases** (`v1.2.0`). There is no `CHANGELOG.md`: FINDINGS_LOG.md is the dated record of what changed. **Since 2026-10-08 a release is also a GitHub Release with the APK attached,** for anyone to download: built with `assembleRelease` on the Mac, signed with the phone's own key, and uploaded by hand (Shawn's choice over a CI build, so the key never leaves the Mac). Every release raises `versionCode` by one and sets `versionName`; neither is ever lowered. The steps are in README, "Making a release". There is no release pipeline.

---

## 15. Architecture Decision Records (ADRs)

Record meaningful decisions (stack choices, tradeoffs, "we did it this weird way because…") in `docs/adr/`. Future-you won't remember *why*, and lost context is its own debt.

```
# ADR-NNN: [Short title]
Date: YYYY-MM-DD
Status: Accepted | Superseded by ADR-XXX

## Context
What's the situation/problem? What constraints apply?

## Decision
What did we decide to do?

## Consequences
What gets better, what gets worse, what we're now locked into.
```

---

## 16. Managing debt that *does* slip through

Some debt is fine — taking it on deliberately to ship is valid. The rule is: **make it visible.**

- Log knowingly-cut corners in `docs/FINDINGS_LOG.md`: what, why, cost-to-fix-later.
- Tag in-code shortcuts searchably: `// TODO(debt): ...`.
- **One cleanup pass per phase:** prune dead code, clear TODOs, update deps before the phase is called finished.

---

## 17. Companion documents (the doc system)

These four documents are the project's memory. The assistant reads the map *before* changing code and records what it did *after*, so the codebase never drifts away from what's written.

| Document | What it is | When it's read | When it's updated |
|---|---|---|---|
| **ENGINEERING_STANDARDS.md** (this file) | The *rules* — how the app is built | Before starting; when unsure how to do something | When a rule changes |
| **ARCHITECTURE.md** | The *map* — the system's shape, stack and data flow | Before building anything that touches structure | When structure changes |
| **APP_ENCYCLOPEDIA.md** | The *reference* — every feature and exactly how it works | Before changing a feature | Whenever a feature is added or its behavior changes |
| **FINDINGS_LOG.md** | The *journal* — a dated record of every change, decision, and gotcha | When you need to know what happened and why | After every meaningful change — always |

**The loop, every change:** read STANDARDS + ARCHITECTURE + the relevant ENCYCLOPEDIA entry → build → update ENCYCLOPEDIA if behavior changed → append to FINDINGS_LOG.

---

## 18. Day-1 setup checklist

Do these *before* writing a single product feature. Left out on purpose (reasons in ADR-001): Sentry, staging and prod, a lockfile, detekt, Robolectric, Hilt, Renovate.

- [x] GitHub repo created (private; public since 2026-10-08), `.gitignore` in place (build output, `local.properties`, keystores)
- [x] Gradle project builds: one `:app` module, AGP 9.4.1, Kotlin 2.4.20, wrapper 9.8.0 with `distributionSha256Sum`
- [x] Quality gates on: Kotlin `allWarningsAsErrors`, Android Lint `warningsAsErrors` + `abortOnError`, Spotless + ktlint
- [x] `.githooks/pre-commit` (gitleaks, then `spotlessCheck`); `core.hooksPath` set; gitleaks installed
- [x] Latest **stable** deps pinned as exact versions in `gradle/libs.versions.toml`
- [ ] Dependabot enabled (weekly; minor and patch grouped, majors separate) — *config is in the first PR; it takes effect when that merges*
- [x] Package structure (§3) scaffolded; `AppContainer` created by the `Application` class
- [x] Design tokens + base component library started in `core/designsystem/` (§8)
- [x] CI pipeline on PRs (gitleaks, then `spotlessCheck lintDebug testDebugUnitTest assembleDebug`)
- [x] Android Studio updated, SDK platform 37 installed, debug build installs on the POCO X5 — *installed over USB with adb on 2026-10-05; a sync and Run from Android Studio itself are still untried*
- [x] All four docs created in the repo: STANDARDS, ARCHITECTURE, APP_ENCYCLOPEDIA, FINDINGS_LOG
- [x] First ADR written: "ADR-001: Stack selection"

---

*When this document and the code disagree, the document wins — or the document changes. Never let them silently drift apart.*
