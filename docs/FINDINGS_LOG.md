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

**`[CHANGE]` Pre-commit hook switched on; CI proven on GitHub**
Shawn installed gitleaks 8.30.1 and `core.hooksPath` now points at `.githooks`, so every commit runs the staged-secret scan and then `spotlessCheck`. The two commits made before the hook existed were scanned afterwards across all branches: no leaks. The CI workflow's first run, on the skeleton PR, passed. Android Studio is updated to 2026.2; the project has not been opened in it yet.

**`[FIX]` Review of the phase 1 foundation: what changed, and why**
The foundation below was reviewed before its first commit, and these defects were fixed in the same change. Each has a unit test that fails on the old code.
- **A trip was closed on a stale belief.** An overdue grace period was closed by whatever event came next, including a GPS fix. A fix handled one second before the timer's reading cut a drive in two at a false disconnect, and the two minutes in between belonged to neither trip. Now only an event that brings a fresh reading of the truck closes a trip on a timeout (ADR-002 amended, see the decision below).
- **Yesterday's trip could swallow today's.** A connected truck carried an open trip on however old it was. A late reconnect and a late restart now close the old trip and start a new one.
- **Start during the grace period was swallowed.** It now ends the waiting trip and starts a manual one.
- **A trip was cut by filtering every point on its time of day.** With the phone's clock ten minutes fast for the first part of a 6 km drive and corrected later, the trip measured 900 m. The cut is now by stored order: only the points at the end of the list that are later than the cut-off are dropped.
- **One bad fix inside the speed limit was counted out and back.** A fix 200 m away while the truck stood still added 400 m, enough on its own to turn a parked connection into a kept trip. `DistanceCalculator` has a fourth rule: a counted step that the next fix walks back is taken back.
- **The exit-record import marked its place once per batch.** A process that died in mid-import brought the whole batch in again at every start. The mark now moves after each record.
- **Gathering evidence could stop the app for good.** An unreadable settings file made the start-up import throw, which ended the process at every start; the only way out was clearing the app's data, trips included. Each step now runs on its own, and a failure is written to the event log as `ERROR` (or to a crash file if the log is what failed). This changes the "corrupt settings file" decision below: the file is still never reset, but it no longer crashes the start.
- **Storage was touched outside `data/`.** `CrashFileStore` moved from `platform/diagnostics/` to `data/crash/`, and the DataStore is built by `buildSettingsStore` in `data/settings/`, not in the `AppContainer`. The rule in STANDARDS §3 holds again as written.
- **Small ones:** `setTruck` refuses a blank name and `setLastProcessExitImportedAtMs` a negative time, as the documents already claimed; the `PointsDatabase` comment names all three side files; `docs/DEVICE_TEST_CHECKLIST.md` now exists, with the checks this package needs on the phone.
- **Tests that could not fail.** Four deliberate breakages of the jump filter passed the whole suite: timing the speed with the wall clock, re-anchoring after two rejected fixes, not requiring them to agree, and not clearing them on a good fix. `DistanceCalculatorOutlierTest` now fails on each of them.

**`[DECISION]` ADR-002 amended: when an open trip may not be carried on**
ADR-002 said both "Grace ends: the trip is closed" and "truck connected means carry on recording the same trip", and gave the second no time limit. The safe reading was implemented and written into the ADR. The two limits were chosen in review without Shawn and are his to change; both are constants in `core/trip/TripState.kt`.
- **`LATE_CHECK_TOLERANCE_MS`, 30 seconds.** A "connected" reading up to this long after the grace deadline still carries the same trip on, because the reading taken when the timer fires always arrives a little late. Too small, and a late timer at a false disconnect splits one drive and loses up to two minutes of it. Too large, and a stop barely longer than the grace period is joined to the next drive.
- **`RESTART_GAP_LIMIT_MS`, 30 minutes.** After a restart, a trip that was recording carries on only if its newest stored point is younger than this. Inside the limit the gap is counted as a straight line, which is real truck distance: the truck is wherever the phone next connects to it. Beyond it the trip's times would be wrong, and from phase 2 Business or Personal is decided by a trip's start time. The stricter choice, the grace period itself, would lose the driving in the gap every time a cleaner kills MilO for more than two minutes in mid-drive.
- **Start during the grace period ends the waiting trip.** The other way, keeping the trip and switching it to the manual rules, needs a new stored state. The cost of the way chosen: what was driven between the truck being found gone and the press is in neither trip.
- **The 30-minute no-movement rule also needs a fresh reading of the truck.** Same principle: "no truck connected" has to be a fact, not a memory.
- **Not covered:** a process that stays alive through a lost disconnect still sees one long trip. That needs the service to re-read the connection now and then (see the hold-off finding below).

**`[DEBT]` Deadlines and the trip cut-off are times of day**
The grace deadline, the no-movement limit and the moment a trip is cut at are wall-clock times, because they are stored and must survive a reboot. If the phone corrects its clock by more than the time left: a clock set back during the grace period makes the grace period that much longer, and lets the walk away from the truck be counted (408 m in the review's simulation); a clock set forward by 31 minutes can end a moving manual trip. A trip that started on a wrong clock keeps the wrong start time, which can be later than its end. Left because it is rare (a correction of minutes, inside a two-minute window) and the cure touches every event, the stored grace columns and the points: elapsed realtime carried beside each wall-clock time and used whenever the phone has not rebooted. Cost to fix: medium. A cheap part for the next work package: the controller's timer runs on elapsed time, so when it fires it can stamp its reading no earlier than the deadline it was set for. Tagged `TODO(debt)` in `TripState.kt`.

**`[FINDING]` What the distance calculation still gets wrong (simulated, not this phone)**
Kept visible so the first test drives are read against it. The raw points are stored, so every trip can be recalculated when the rules improve.
- **Turns are cut.** Distance is the straight line from where it was last counted, once that line is longer than twice the two fixes' stated error. On a simulated town route (a right-angle turn every 300 m, about 25 km/h) the result is short by 2 % with 5 m fixes (all of it lost to sampling every 5 s), 4 % with 10 m fixes and 11 % with 25 m fixes. `DistanceCalculatorTurnsTest` pins these. The review's rougher route with slow corners gave 2 %, 5 % and 9 %, and twice that with a turn every 150 m.
- **The end of a trip is short by up to the threshold:** 20 m, 40 m or 100 m for 5 m, 10 m or 25 m fixes.
- **Some bad fixes are still believed:** a wrong first fix less than about 700 m off, and one wrong fix straight after a GPS gap (a 60-second tunnel, then a fix 600 m to the side: 500 m too much). Rule 4 does not catch these, because the next fixes do not walk back to where counting stood.
- **Candidates for the cure,** to choose from real points: use the phone's own speed reading for "parked" in place of stated accuracy (ends the corner cutting); hold a fix that follows a gap until the next one agrees with it; and, when the location request is built, check whether the fused provider can be told not to hand over an old cached position first.

**`[CHANGE]` Phase 1 foundation: storage, the trip rules, crash and kill capture**
The first product code. The app still opens on the placeholder home screen: no permission, service, receiver or new screen was added, and nothing calls the trip rules outside their tests.
- **`app/`:** `MiloApplication` (registered in the manifest) creates one `AppContainer`, the manual dependency injection.
- **`data/`:** two Room 3 databases on the bundled SQLite driver, `milo.db` (trips, event log) and `points.db` (raw GPS points), with their schemas exported to `app/schemas/` and committed; a repository over each DAO; `SettingsStore` on DataStore Preferences; `CrashFileStore` for the crash files. The tables are in ARCHITECTURE §6.
- **`core/trip/`:** ADR-002's trip rules as a pure function (`TripStateMachine`), the point filter and distance calculation (`DistanceCalculator`, haversine), and `TripClosing`. No Android imports.
- **`platform/diagnostics/`:** an uncaught-exception handler that hands the crash to the crash file store, and a start-up step that copies crash files and the system's process-exit records into the event log.
- **Dependencies added,** each re-checked against its live Maven index today: Room 3.0.3 (runtime, KSP compiler, Gradle plugin), KSP 2.3.12, `sqlite-bundled` 2.7.1, DataStore Preferences 1.2.1, `kotlinx-coroutines-core` 1.11.0. Tests still need only JUnit: the rules are plain functions, and DataStore runs on the JVM once given a file.

Why, where the code does not say it:
- **The trip rules take levels, not edges.** An event says "the truck is connected", never "the truck just connected". The rules keep what they were last told and, after every event, make the trip agree with it. That is what makes a repeated, missing or reversed event harmless, and it is checked by a test that feeds 240 000 random events and verifies the state after each one.
- **Timers are not effects.** The rules expose "when must I look again" as a function of the state. A timer lost with a killed process is set again from the restored state, and when it fires the caller sends a fresh reading of the truck's connection, which is ADR-002's "read the profile state again before closing".
- **Crashes go to a file first.** A dying process can do one blocking file write. A database write needs threads that may be gone, and the database may be what crashed. The file is under `no_backup/` so a restore can never replay an old crash.
- **`MiloApplication` opens no database or settings file on the main thread.** The container creates them lazily, because the process will soon be started by trip triggers with seconds to begin recording.

**`[DECISION]` Points ADR-002 and the brief left open, settled while building the foundation**
Each took the safest reading. All are Shawn's to overrule.
- **A trip is cut where the truck was found gone.** ADR-002 says a trip closes "at the last recorded point, not at the end of the grace period", but recording has to continue during the grace period or a short Bluetooth drop would lose distance. So the grace period's start is stored (`graceStartedAtMs`, a column the brief did not list), and a trip that runs out of grace ends at its last point up to that moment. Later fixes stay stored and count only if the truck comes back.
- **A discarded trip is marked, not deleted.** `DISCARDED` is a status. The distance thresholds are untested on this phone, and a real trip deleted because of a wrong distance could not be got back.
- **The Start and End events carry a fresh reading of the truck's connection.** The hold-off after a manual end must not be set from a stale belief, or it would swallow the next real trip.
- **End with no trip open, truck connected, also sets the hold-off.** Otherwise the fresh reading would start a trip on the press of End.
- **A button press first brings an open trip up to date.** Start is not swallowed by a trip whose grace period has already run out, and End does not stretch a forgotten trip to the moment of the press.
- **Android Auto also cancels a running grace period and pauses the 30-minute no-movement rule.** ADR-002 only says it stops the grace period from starting.
- **A trip that was recording when the process died, and finds the truck gone at restart, starts its grace period then.** ADR-002 lists three restart cases and not this one.
- **The no-movement rule ends the trip where it last moved,** not 30 minutes later.
- **The 30-minute limit and the distance thresholds are constants, not settings.** Only the grace period and the minimum trip distance are in the settings store, as the brief says.
- **Crash and kill capture lives in a new `platform/diagnostics/`.** ADR-002's package list has no place for it. The crash files themselves are storage, so `CrashFileStore` is in `data/crash/`.
- **`points.db` sits in the standard databases folder,** not under `no_backup/`, so phase 4 can still include it in a phone-to-phone transfer while excluding it from cloud backup.
- **A corrupt settings file is not reset.** A silent reset would drop the truck pairing, and automatic start would stop with no trace. First written as "it crashes at start-up"; the review found that meant a crash at every start, so the start-up import now logs the failure and carries on (see the fix entry above). Each later reader of the settings must handle an unreadable file itself. Revisit when the permission checklist can show "no truck paired".

**`[FINDING]` The hold-off can swallow a trip when a disconnect is never seen**
ADR-002 holds automatic start off after a manual end "until the truck next disconnects". If MilO is dead at that disconnect and the broadcast is dropped, the hold-off is still stored at the next connect, and that trip does not start. The rules cannot fix this while staying purely level-based: the only evidence would be the connect event itself, and ADR-002 says events are never assumed to come in pairs. Implemented as written. Options for Shawn: let a connect *event* (not a reconcile) release the hold-off, or give the hold-off a time limit. A related gap: a lost disconnect during a trip leaves it open until something reads the connection again, so the service should re-read it periodically.

**`[FINDING]` "Count distance only after 10 m" is not enough for a parked truck**
Simulated: one parked hour of fixes scattered the way a stated accuracy of 5 m implies. The 10 m rule alone added 674 m of phantom distance (356 m once the out-and-back rule from the review was in place: still enough to be kept as a trip). So movement must also exceed twice the two fixes' own stated error; with that, the same hour adds 0 m, and so do 100 such hours. The price is paid on turns and at the end of a trip: see the finding on what the calculation still gets wrong, above. The numbers come from a simulation, not from this phone: tune `DistanceLimits` from the raw points of the first test drives. The phone's own speed reading is stored for that purpose and not used yet.

**`[FINDING]` First run of the Android-side code, on an emulator (Android 16), not on the phone**
The debug build was installed on the existing Medium Phone emulator image, started read-only so nothing was saved to it. An induced crash (`am crash`), a force stop and a background kill each showed up in the event log at the next start, once, with the stack trace or the system's description. Nothing has run on the POCO X5 yet, and `points.db` was never opened in this run because nothing writes a point. Two things learned:
- A force stop is reported as `USER_REQUESTED` with the description `[FORCE STOP] ...`, not as `USER_STOPPED`, which means the Android user profile was stopped. The description text is what tells kills apart, as the research said it would for Xiaomi's cleaners.
- Room keeps a `.lck` file beside `milo.db` as well as `-wal` and `-shm`. Phase 4's backup rules must account for it.

**`[FINDING]` A raw point can refer to its trip only by id**
ARCHITECTURE §6 carried this as an inference to check when the two databases were built. It holds: SQLite enforces a foreign key only between tables in the same database file. `tripId` is a plain indexed column. Nothing deletes a trip today; code that ever does must delete the trip's points itself.

**`[FINDING]` Nothing in the gate flags an unused import**
The Kotlin compiler does not warn about one, and ktlint (through Spotless, `android_studio` style) left three in place after a file was split. They were found by hand. Android Studio shows them greyed out.

**`[DEBT]` The event log is never trimmed**
Every start writes at least one entry and trip events will add more. The table shares `milo.db` with the trips, and that file must stay far below the 25 MB backup cap. Left out because nothing is backed up until phase 4 and the right retention period is not known yet. Cost to fix: small, a delete-older-than query and a place to call it. Must be done before backup is switched on. Tagged `TODO(debt)` in `EventLogRepository.kt`.

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
