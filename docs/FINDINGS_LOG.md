# Findings Log

> **What this is.** A running, dated journal of everything that happens to this codebase — every change, every decision, and every *finding* (a bug discovered, a gotcha, a thing that turned out not to work the way you assumed). It's the project's narrative memory. Where ARCHITECTURE and the ENCYCLOPEDIA describe the app *as it is now*, this log describes *how it got there and why*.
>
> **Why it matters.** Most "wait, why did we do it this way?" debt comes from lost context. This log is the answer to that question, written down at the moment you still remember it. In an AI-assisted workflow, **append an entry after every meaningful change** — it's part of the Definition of Done (STANDARDS §10), and it gives your assistant (and future-you) the real history instead of guesses.
>
> **How to use it.** Newest entries at the top. One entry per change/finding/decision. Keep them short — a few lines each. Tag the type so the log is scannable.
>
> **Status:** Living document · **Started:** 2026-10-03

---

## Entry types

- **`[CHANGE]`** — something was built, modified, or removed
- **`[FIX]`** — a bug was fixed
- **`[DECISION]`** — a choice was made (link an ADR if it's a big one)
- **`[FINDING]`** — something was discovered: a gotcha, a constraint, a surprise, a "don't do X because Y"
- **`[DEBT]`** — a corner was knowingly cut (what, why, cost to fix later)

---

## Log

### 2026-10-03

**`[DECISION]` Trip detection design (ADR-002)**
Every trigger (companion service, Bluetooth receiver, reconcile at boot, update and launch, manual buttons) calls one idempotent function on an app-wide `TripController`. Events are treated as hints: the controller re-reads the real connection state rather than trusting that connects and disconnects arrive in pairs. The trip rules are a pure Kotlin state machine in `core/trip/` so they can be tested without a phone. Two rules that are not in Shawn's brief and were added to make the design safe: ending a trip by hand while the truck is still connected holds off automatic start until the truck next disconnects, and a manual trip with no truck connected ends after 30 minutes without movement. An always-on service is the fallback if starting from a dead process proves unreliable on HyperOS; it is not built, and needs Shawn's agreement. See `docs/adr/ADR-002-trip-detection.md`.

**`[FINDING]` Android Auto will probably not list a sideloaded MilO on the truck**
The dedicated research run and its verifier agree on what Google documents: "Unknown sources" in Android Auto's developer settings does not apply to Car App Library apps, and on a real head unit such an app must come from a trusted store. A private Play internal-sharing link or internal test track counts and needs no review. Community reports conflict on whether a sideloaded build still appears in practice. The screen is small, so it is built in phase 1 and tested in the desktop head unit and then on Shawn's truck. If it does not appear there, the choices are a private Play install (a Play Console account, and every build delivered through Play instead of Android Studio), a media-app style workaround, or dropping the screen. `CarConnection`, which holds a trip open while Android Auto is connected, works either way. (`2026-10-03-android-auto-screen.md`)

**`[FINDING]` Installing from Android Studio on HyperOS has its own prerequisites**
Besides USB debugging, HyperOS needs "Install via USB" and "USB debugging (Security settings)" switched on. Turning those on normally requires a Xiaomi account signed in, a SIM inserted, and mobile data on with Wi-Fi off. Each install then shows a prompt on the phone with a short countdown; missing it fails with `INSTALL_FAILED_USER_RESTRICTED`. Not yet tried on this phone. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**`[CHANGE]` `CLAUDE.md` brought in line with the Kotlin standards**
Edited by the assistant after Shawn chose the Kotlin rewrite: the last two doc pointers; the done-checklist (Kotlin warnings as errors, Lint, Spotless and unit tests instead of TypeScript strict; `Log`/`println` instead of `console.log`; phone / Android Auto instead of mobile / web); the platforms question (now "which surfaces"); the lockfile line (now the version catalog and the wrapper checksum); the secrets section (no `.env`, no sign-in: the only secrets are the signing keystore and its passwords); and the Renovate-or-Dependabot line (Dependabot). The rules themselves are unchanged.

**`[CHANGE]` Project skeleton: Gradle build, quality gates, design system, CI files**
The first code. `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug` passes from the terminal on AGP 9.4.1, Gradle 9.8.0 and Kotlin 2.4.20, so the fallback in ADR-001 was not needed. Not done yet: a sync in Android Studio (still 2025.3, too old for this project), a CI run, and an install on the phone. What exists:
- **Build:** one `:app` module, versions in `gradle/libs.versions.toml`, the wrapper pinned by checksum, Java 17 pinned in `gradle/gradle-daemon-jvm.properties`.
- **Code:** `app/MainActivity.kt` and `app/MiloApp.kt`, a placeholder `feature/home/HomeScreen.kt`, and `core/util/DistanceFormat.kt` with a unit test. There is no `Application` class, no `AppContainer`, no navigation, no permission, and nothing in `data/` or `platform/`.
- **Design system** (`core/designsystem/`): tokens in `theme/` (`Color.kt`, `MiloSpacing.kt`, `Type.kt`, `Shape.kt`, applied by `MiloTheme.kt`) and three base components in `component/`: `PrimaryButton`, `SectionCard` and `StatusRow` (its icons are in `StatusIcons.kt`). It is documented in APP_ENCYCLOPEDIA under Design system.
- **Repo files:** `.github/workflows/ci.yml`, `.github/workflows/dependency-graph.yml`, `.github/dependabot.yml` and `.githooks/pre-commit`. They do nothing until the GitHub repo exists, `core.hooksPath` is set and gitleaks is installed.

Choices that are not obvious from the code:
- **Three lint checks are disabled:** `GradleDependency`, `AndroidGradlePluginVersion` and `OldTargetApi`. Each fires only because something newer was released. With lint warnings as errors, that would turn a green build red overnight.
- **Native libraries are packaged unstripped** (`keepDebugSymbols` in `app/build.gradle.kts`). AGP 9.4.1 looks for NDK 28.2.13676358 to strip the prebuilt `libandroidx.graphics.path.so`, and this Mac has only 27.1.12297006. Without the setting every build prints "Unable to strip the following libraries".
- **Backup is off in two places.** `allowBackup="false"` stops cloud backup but not phone-to-phone transfer on Android 12 and later, so `data_extraction_rules.xml` also excludes every storage area from both, including the device-protected ones. Phase 4 replaces it with real rules.
- **Gradle scripts follow the same warning rule as app code,** through `org.gradle.kotlin.dsl.allWarningsAsErrors` in `gradle.properties`. `allWarningsAsErrors` in the app module does not cover them, and a build script is where a deprecated AGP or Gradle call shows up first.
- **`MainActivity` lives in `app/`,** and so will the `Application` class. The root package is not one of the six in STANDARDS §3, so no class sits in it.

**`[DECISION]` Rules settled while reviewing the skeleton**
- **GitHub Actions are pinned to full commit SHAs,** with the release in a trailing comment. The first draft used tags such as `v7`, which an action's owner can move to different code. It also left Dependabot's minor-and-patch group for actions with nothing to match.
- **The dependency graph workflow stays, pending Shawn's objection.** It arrived with the skeleton without a recorded decision. It is kept because STANDARDS §12 promises Dependabot alerts, and for Gradle those need the full resolved dependency list (research K11, K19). It runs on push to `main` only and holds the only write permission in the workflows. Shawn must switch on the dependency graph and Dependabot alerts in the repo settings. To drop it, delete the file and its lines in ADR-001 and STANDARDS §12 and §14.
- **No `CHANGELOG.md`.** The template asked for one. This log is already the dated record.
- **The device test checklist will be `docs/DEVICE_TEST_CHECKLIST.md`,** written in phase 1 with the first behaviour that can only be tested on the phone.
- **The Android Auto screen has no ViewModel.** The first ARCHITECTURE diagram routed it through one. The flow rule names Composables only, and the research has the car screen read one app-wide trip state directly. The diagram is redrawn; the shape of that shared state is still ADR-002.

**`[FINDING]` `spotlessCheck` can pass on old rules after an `.editorconfig` edit**
Reproduced in a scratch copy: `max_line_length` was changed from 100 to 40, a limit the existing source files break. A plain `./gradlew spotlessCheck` re-ran the tasks and passed. So did a run with `--rerun-tasks --no-configuration-cache --no-build-cache` in the same Gradle daemon. Only a fresh JVM failed, as it should. A daemon that is already running keeps applying the rules it loaded first. The pre-commit hook runs the plain command, so it has the same blind spot. The rule, written in `.editorconfig` and STANDARDS §4: after any `.editorconfig` change run `./gradlew spotlessCheck --no-daemon --rerun-tasks --no-configuration-cache --no-build-cache`, and treat CI as the authority.

**`[FINDING]` Kotlin `extraWarnings` fails on Room 3's generated code**
The research (K4) recommended `extraWarnings` next to `allWarningsAsErrors`. Tested in a scratch copy: the skeleton's own code compiles clean with both. With Room 3.0.3 and KSP 2.3.12 added, the generated `_Impl.kt` files fail with "Redundant visibility modifier", because warnings are errors. So `extraWarnings` stays off. The research names `-Xwarning-level` as the way to lower a single diagnostic; that route is untested.

**`[FINDING]` AGP 9.4.1 triggers a Gradle deprecation notice that MilO cannot fix**
A build that configures the project ends with "Deprecated Gradle features were used in this build, making it incompatible with Gradle 10". `--warning-mode all` shows one cause: `Configuration.setVisible(boolean)`, called from AGP's own `BasePlugin` and `SourceSetManager`. Gradle says the method is "scheduled to be removed in Gradle 11". None of MilO's scripts call it, and only a newer AGP removes it. It matters when Dependabot proposes a new Gradle major: check that the pinned AGP supports it first.

**`[FINDING]` Nothing automated will prompt for a new `targetSdk`**
The `OldTargetApi` lint check is disabled, and Dependabot updates libraries and plugins, not the SDK numbers in `app/build.gradle.kts`. `compileSdk` gets forced now and then: a library update that needs a newer one fails the build. Nothing does the same for `targetSdk`. Checking for a new stable Android version is a manual step, best done in the cleanup pass before each phase hand-over (STANDARDS §16).

**`[DEBT]` `StatusRow` has two states; the permission checklist needs more**
`StatusRow` takes a Boolean: met or not met. The checklist also needs "looks on / looks off / unknown" for Autostart and "confirm you set this" for settings the app cannot read (APP_ENCYCLOPEDIA, Permission checklist). It was left at two states because nothing uses the others yet, and they need new icons, colours and strings, which is a deliberate design-system addition. Cost to fix: small. Replace the Boolean with a status type when the checklist is built in phase 1. Tagged `TODO(debt)` in `StatusRow.kt`.

**`[DECISION]` Stack and tooling fixed for native Kotlin (ADR-001)**
Kotlin 2.4.20 on AGP 9.4.1, Gradle 9.8.0 and JDK 17. One `:app` module. Compose with Material 3, Navigation 3, Room 3, DataStore, and manual dependency injection through an `AppContainer`. Quality gates: Kotlin warnings as errors, Android Lint with warnings as errors, Spotless driving ktlint, and a plain `.githooks/` pre-commit script (gitleaks, then `spotlessCheck`). Left out on purpose, each with its reason in the ADR: detekt, Robolectric, Hilt, Sentry, staging and prod, a Gradle lockfile, dependency verification. ENGINEERING_STANDARDS and ARCHITECTURE were rewritten to match, which finishes the rewrite the "Standards to be rewritten" entry below left open. Three points that are easy to miss:
- **Dependabot replaces the Renovate named in that entry below.** It is native to GitHub, free on private repos, and handles the version catalog and the Gradle wrapper.
- **No lockfile, although the original standards template asked for one.** Update bots cannot regenerate one for an Android project, so it would mean hand work on every update. Exact pins in `gradle/libs.versions.toml` stand in for it.
- **AGP 9.4.1 with Gradle 9.8.0 is one step past what JetBrains documents for Kotlin 2.4.20.** The first build is the real test. The fallback is AGP 9.3.3 with Gradle 9.7.1. (Result: it built with no fallback. See the skeleton entry above.)

Revisit detekt when 2.0.0 is stable. See `docs/adr/ADR-001-stack-selection.md`.

**`[FINDING]` The research facts that will bite hardest**
All from `docs/research/`. None is confirmed on the POCO X5 yet.
- **Background location ("Allow all the time") is mandatory for automatic start.** On Android 14 and later, a location foreground service started from the background without it throws `SecurityException`. (`2026-10-03-fgs-background-start.md`)
- **Triggers must be redundant and feed one idempotent start function.** CompanionDeviceManager and the Bluetooth ACL broadcast come from the same Bluetooth-stack event, and each has gaps: truck already connected at boot or after an update, phone not yet unlocked, app force-stopped. (`2026-10-03-cdm-presence.md`)
- **Trip logic must be level-triggered, and Bluetooth receivers must be exported.** Events can be dropped, duplicated or reversed, so every event means "re-read the real connection state". A non-exported receiver never gets the broadcast, and a trip never ends. (`2026-10-03-fgs-background-start.md`)
- **On HyperOS, Autostart gates every start of a dead app, and it is off by default.** Decompiled system code shows broadcasts dropped and service starts and binds rejected while it is off, which would block both the Bluetooth receiver and the CompanionDeviceManager path. It is the likely reason Shawn's current app misses trips. Not tested on this phone. (`2026-10-03-miui-background-limits.md`)
- **The Android Auto screen may not show up on the truck.** Google's docs say the "Unknown sources" option does not cover Car App Library apps. This contradicts the "Unknown sources" assumption in the Android Auto entry below. An independent check has since confirmed that reading of the docs; see the Android Auto finding at the top of this date. (`2026-10-03-android-auto-screen.md`)
- **Two quiet ways to lose data.** Auto Backup stops completely above 25 MB, which raw GPS points in the main database would reach within about a year. And the debug keystore is per Mac: a different key means an uninstall that deletes every trip and blocks the backup restore too. (`2026-10-03-pdf-email-backup.md`)

**`[DECISION]` Research notes live in `docs/research/` as dated evidence, not as living documents**
Each file is named by date and topic and records what its sources said on that day, with a confidence level on each finding. The header of each file says whether an independent verifier re-checked it; the Kotlin standards, location and car, and PDF/email/backup files were not. They are the evidence behind the ADRs and are not edited to keep up with the world. Current truth lives in the four companion docs and the ADRs. When a research fact is acted on, or proved wrong on the phone, the result goes in this log.

**`[DECISION]` Android Auto screen added to the brief (phase 1)**
Shawn asked mid-kickoff for a Car App Library screen (`androidx.car.app:app-projected`): tracking status, current trip km and duration, today's session count and total km, and Start Trip / End Trip as a manual override. Manual trips still respect the schedule. His reason: it makes it easier to see that the app is running. That is why it sits in phase 1 rather than later; the first test drives are when that visibility matters most. Because MilO is sideloaded, Android Auto will only list it with "Unknown sources" enabled in Android Auto's developer settings.

**`[DECISION]` A bare event log moves from phase 4 into phase 1**
Shawn's brief put all diagnostics in phase 4. Phase 1 is driven for several days to prove trip detection, and a missed start cannot be explained afterwards without a record of Bluetooth, Android Auto and service events. So the event storage and a plain list screen ship in phase 1; the polished screen and the failure notification stay in phase 4. Proposed by the assistant, pending Shawn's objection.

**`[DECISION]` Kickoff answers that change the original brief**
MilO is a personal mileage logger for one phone (native Kotlin, no backend, no accounts, no Play Store). The full required behaviour is in APP_ENCYCLOPEDIA. The points below differ from, or add to, what Shawn first wrote:
- **Trip boundaries are Bluetooth only.** Connect to disconnect plus the grace period. Offered "split into sessions on long stops"; Shawn chose not to.
- **Safety net:** a manual Start/Stop button, plus a "you seem to be driving but the truck isn't connected" alert during scheduled hours. Driving detection never starts a trip on its own.
- **Trip-start sound:** an R2-D2 style chirp once the app is connected to the truck and ready to track. Phone speaker, respects silent mode. The bundled sound is an original synthesized chirp, because the film recording is copyrighted; a setting lets Shawn pick his own audio file.
- **No money on the report.** Accounts works out the reimbursement, so the PDF shows km only. The rate-per-km setting and the amount owed are dropped.
- **No odometer readings.** Start and finish location and mileage are tracked automatically.
- **No purpose column and no purpose note.** PDF table columns are Start, End, From, To, km.
- **Signature is a blank line.**

**`[DECISION]` Standards to be rewritten for Kotlin; private GitHub repo with CI and Renovate; no Sentry**
The standards template is TypeScript-centric (strict tsc, ESLint, Prettier, Husky, Sentry, dev/staging/prod backends). None of that fits a native Kotlin app with no backend. Shawn chose: rewrite the standards for Kotlin and record it in ADR-001, keep the repo private on GitHub with checks on every PR and Renovate for dependency updates, and skip Sentry and staging/prod. Crashes go to the in-app event log instead. The rewrite itself is still to do.

**`[FINDING]` Target phone is a Xiaomi POCO X5 on Android 14 (HyperOS)**
Xiaomi's MIUI/HyperOS is known for stopping background apps unless Autostart and battery "No restrictions" are set per app. That is the leading suspect for why Shawn's current mileage app fails to start trips, but it is not yet confirmed on this phone. The permission checklist must cover the Xiaomi settings, not only the stock Android ones.

**`[FINDING]` Dev machine state at kickoff**
Mac mini (Apple silicon), Android Studio 2025.3, JDK Zulu 17.0.18, SDK platforms android-36 and android-36.1, build-tools 35.0.0 / 36.0.0 / 36.1.0, `adb` and `gh` installed, `gh` signed in as shawnkowalchuk. No phone was attached over adb. The folder was not yet a git repo.

**`[DECISION]` The four companion docs live in `docs/`, not the repo root**
The templates arrived in `DOCS/` while `CLAUDE.md` and the README pointed at the repo root and at `/docs/adr/`. macOS's filesystem is case-insensitive, so `DOCS/` and `docs/adr/` were the same folder anyway. Renamed to lowercase `docs/`, created `docs/adr/`, and rewrote every pointer in `CLAUDE.md`, `README.md`, STANDARDS and ARCHITECTURE to the `docs/…` path. `CLAUDE.md` stays in the root because that is where the assistant loads it from.

---

### _[next date]_

**`[TYPE]` Short title**
What happened, and the *why* that won't be obvious from the code or the commit. Link an ADR or an ENCYCLOPEDIA entry where relevant.

---

> **Tip:** when you write `// TODO(debt): ...` in code, drop a matching `[DEBT]` line here too. The code tag tells you *where*; this log tells you *why* and *what it'll cost*.
