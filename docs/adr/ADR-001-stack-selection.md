# ADR-001: Stack selection
Date: 2026-10-03
Status: Accepted

**Build verification: PASSED on 2026-10-03.** `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug` succeeds from the terminal on AGP 9.4.1, Gradle 9.8.0 and Kotlin 2.4.20, so the fallback was not needed. KSP 2.3.12, Room 3.0.3 with the bundled SQLite driver, the serialization plugin and Navigation 3 1.2.0 were compiled in a throwaway copy and also work. Still unproven: a sync in Android Studio (the installed 2025.3 is too old), a run on GitHub Actions, and an install on the phone.

## Context

Shawn's brief: a personal app that logs business mileage in his work truck automatically and produces a monthly PDF for accounts. One phone (Xiaomi POCO X5, Android 14, HyperOS). No backend, no accounts, never on the Play Store. It is installed from Android Studio on a Mac mini (Apple silicon). The mileage app he uses today often fails to start trips, so reliable automatic trip start is the number one requirement.

Why native Kotlin. The brief asked for it, and the features that carry the requirement are Android platform APIs:

- `CompanionDeviceManager` association and presence, so the system wakes the app when the truck connects.
- A `location` foreground service started from the background, with manifest receivers for Bluetooth and boot events.
- An Android Auto screen through the Car App Library.

There is one platform and one phone, so a cross-platform framework would add a layer between the app and those APIs and gain nothing.

Constraints that shaped the choices:

- The project rule is latest stable only: no alpha, beta or RC. *(One exception since 2026-10-06, for one library: see "Exception, 2026-10-06" below.)*
- The standards template was written for TypeScript (strict tsc, ESLint, Prettier, Husky, npm audit, Sentry, dev/staging/prod). Each rule needed a Kotlin equivalent or a stated reason for dropping it.
- The Mac has one JDK, Zulu 17.0.18, and `JAVA_HOME` is unset. No second JDK is being installed.
- Every version below was read from live indexes on 2026-10-03 and re-checked by an independent verifier. The evidence is `docs/research/2026-10-03-versions.md` (cited as V plus the finding number) and `docs/research/2026-10-03-kotlin-standards.md` (cited as K plus the finding number; that file was not independently verified).

## Decision

### Project shape

- One Gradle module, `:app`, at the repo root. `applicationId` and `namespace`: `com.shawnkowalchuk.milo`. App name: MilO.
- minSdk 34, compileSdk 37, targetSdk 37. Android 17 (API 37) is the newest stable platform, and the current AndroidX releases require compileSdk 37 (V27, V28). *(minSdk was 31 until 2026-10-07. Asked which Android versions MilO should run on, Shawn chose "Android 14 and newer" on 2026-10-06: the one phone runs Android 14, API 34, and code for Android 12 and 13 could be tested nowhere. That code is gone. See the findings log, 2026-10-07.)*
- Feature-first packages inside the one module: `app/`, `feature/<name>/`, `core/designsystem/`, `core/util/`, `data/`, `platform/`. The layout and its rules are in ENGINEERING_STANDARDS §3.
- Manual dependency injection: an `AppContainer` created by the `Application` class.
- Signing: the standard debug keystore for now. No signing config in the repo. *(Superseded on 2026-10-05: builds for the phone are signed with a dedicated key outside the repo. See ENGINEERING_STANDARDS §12 and the findings log.)*

### Toolchain

| Tool | Pin | Verified |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | V3, V4 |
| Gradle wrapper | 9.8.0, with `distributionSha256Sum` | V5, V6, K10 |
| JDK | 17 (Zulu 17.0.18, the one installed) | V4, V34 |
| Kotlin | 2.4.20 | V7 |
| Compose compiler plugin | 2.4.20 (same version as Kotlin) | V11 |
| kotlinx-serialization plugin | 2.4.20 (same version as Kotlin) | V24 |
| KSP | 2.3.12 | V10 |

AGP 9 compiles Kotlin itself. `org.jetbrains.kotlin.android` and kapt are not applied (V9). The Kotlin Gradle plugin 2.4.20 is put on the build classpath so the compiler is the pinned version, not the older one AGP bundles. KSP and the serialization plugin are applied when Room and Navigation 3 arrive.

### Libraries

A pin is a choice of library. Each one is added to the build only when code needs it.

| Library | Pin | Verified |
|---|---|---|
| Compose BOM (Material 3 1.4.0) | 2026.09.00 | V12 |
| activity-compose | 1.13.0 | V13 |
| lifecycle | 2.11.0 | V14 |
| core-ktx | 1.19.1 | V16 |
| Navigation 3, with lifecycle-viewmodel-navigation3 2.11.0 | 1.2.0 | V15 |
| Room 3 (group `androidx.room3`, KSP compiler) | 3.0.3 | V17 |
| `androidx.sqlite:sqlite-bundled` | 2.7.1 | V17 |
| DataStore Preferences | 1.2.1 | V19 |
| WorkManager | 2.12.0 | V20 |
| play-services-location | 21.4.0 | V21 |
| `androidx.car.app` (`app` and `app-projected`) | 1.7.0 at the start. **1.8.0-rc01 since 2026-10-06**, a release candidate: the one exception to "stable only" (below) | V22 for 1.7.0; the live Maven metadata of 2026-10-06 for 1.8.0-rc01 |
| kotlinx-coroutines | 1.11.0 | V23 |
| kotlinx-serialization-json | 1.11.0 | V24 |
| JUnit | 4.13.2 | V25 |
| kotlinx-coroutines-test | 1.11.0 | V25 |
| Turbine | 1.2.1 | V25 |

Room 3 over Room 2.8.5: this is a new app with nothing to migrate, and Room 3 is the line that gets new work (V17, V18). Navigation 3 over Navigation 2: the Navigation 2 release page marks it as maintenance mode (V15).

### Tooling

- **Version pinning:** exact versions as plain strings in `gradle/libs.versions.toml`. No ranges, no dynamic versions. Plugins are applied through the catalog (K7).
- **Compiler gate:** Kotlin `allWarningsAsErrors` for app and test code (K4), and `org.gradle.kotlin.dsl.allWarningsAsErrors` in `gradle.properties` for the Gradle Kotlin scripts.
- **Lint:** Android Lint with `warningsAsErrors` and `abortOnError`. The checks that fire only because a newer version exists are disabled, so a green build cannot turn red overnight; Dependabot owns version updates (K3).
- **Format:** Spotless 8.10.3 driving ktlint 1.8.0 over Kotlin sources and Gradle Kotlin scripts (V30, V32, K5).
- **Pre-commit hook:** a committed plain script in `.githooks/`, enabled with `git config core.hooksPath .githooks`. It runs a gitleaks staged scan, then `spotlessCheck`. It fails loudly with an install hint when gitleaks is missing (K14, K15).
- **Repo and CI:** a private GitHub repo. GitHub Actions runs on every pull request: gitleaks, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug` (K17). Each action is pinned to a full commit SHA with its release in a trailing comment, because a tag can be moved to different code.
- **Dependency graph:** a second workflow, `.github/workflows/dependency-graph.yml`, runs on push to `main` only. It sends GitHub the full list of libraries the build resolves, so Dependabot alerts cover the indirect ones too (K11, K19). It holds the only write permission in the workflows (`contents: write`, which the submission API requires). Shawn must switch on the dependency graph and Dependabot alerts in the repo settings. It was added with the skeleton and was not one of the kickoff decisions: proposed by the assistant, pending Shawn's objection.
- **Dependency updates:** Dependabot, weekly, minor and patch grouped, majors separate (K11).
- **Tests:** plain JVM unit tests of pure Kotlin code. Behaviour that needs Android is tested on the phone with a written device checklist (`docs/DEVICE_TEST_CHECKLIST.md`, written in phase 1).

### Deliberate omissions

| Not used | Reason |
|---|---|
| detekt | No stable release works with AGP 9 and Kotlin 2.4. 1.23.8 is built for Kotlin 2.0.21; 2.0.0 is still alpha (V29, K2). Revisit when 2.0.0 is stable. |
| Robolectric | 4.17 needs Java 21 to simulate API 36 and 37, and only JDK 17 is installed (V26). |
| Hilt | Manual DI is enough at this size, and Hilt's Gradle plugin is tied to AGP majors (K22). |
| Renovate | Dependabot is native to GitHub, free on private repos, and handles the version catalog and the Gradle wrapper (K11, K12). This replaces the Renovate choice in an earlier FINDINGS_LOG entry of the same date. |
| Gradle lockfile | Update bots cannot regenerate it for an Android project, so it would mean hand work on every update (K8). The original standards template asked for a committed lockfile. |
| Dependency verification metadata | Same reason: every Dependabot PR would fail until fixed by hand (K9). |
| Sentry | There is no backend and one user. Crashes and system kills are captured into the in-app event log, built in phase 1. |
| Staging and prod environments | There is no backend to separate. One environment: the debug build on the phone. |
| Explicit-API mode | It is meant for library authors (K4). |
| Kotlin `extraWarnings` | K4 recommended it next to `allWarningsAsErrors`. Room 3.0.3's generated code fails under the pair with "Redundant visibility modifier" (scratch build, 2026-10-03; see FINDINGS_LOG). |
| Husky, lint-staged, Node | A plain git hook does the same job with nothing to install but gitleaks (K14). |
| `org.jetbrains.kotlin.android`, kapt | AGP 9 has Kotlin built in, and the kapt plugin does not work with it. Room 3 needs KSP anyway (V9, V17). |
| Signing config in the repo | The debug keystore is used for now. A dedicated keystore, if created, stays outside git. |
| Pre-release versions | AGP 9.5 alphas, Kotlin 2.4.21-RC, Gradle 9.9 milestones, car app 1.8.0-rc01 and the others listed in the versions research were seen and excluded. *(Car app 1.8.0-rc01 was taken after all on 2026-10-06, as the one exception below. Every other pre-release stays excluded.)* |

## Exception, 2026-10-06: one pre-release, for a security fix

**Decided by Shawn on 2026-10-06** (FINDINGS_LOG, "Four open decisions settled by Shawn"). It is the only exception to the stable-only rule, and it covers one library.

- **What.** `androidx.car.app` (`app` and `app-projected`, which move together) is pinned to **1.8.0-rc01**, a release candidate, in place of 1.7.0, the newest stable release.
- **Why.** The release notes of 1.8.0-rc01 (2026-08-26) say "This release includes a security fix. If you are using a lower version, please update to use this version." They do not name the fix. MilO's Android Auto service is exported with no permission, because Android offers none for Android Auto's binding on a phone; the check that turns other apps away is the library's own code (`HostValidator`). MilO's one process holds the trips and has location "all the time". Until this decision the fix was knowingly missing (FINDINGS_LOG, 2026-10-05, `[DEBT]`).
- **A stable release was looked for first.** The live Maven metadata of `androidx.car.app:app` was read on 2026-10-06: its versions end `…, 1.7.0, 1.8.0-alpha01, 1.8.0-alpha02, 1.8.0-alpha03, 1.8.0-beta01, 1.8.0-rc01, 1.9.0-alpha01, 1.9.0-alpha02`. There is no stable 1.8.0, and 1.8.0-rc01 is the only release candidate of it. Had a stable 1.8.0 existed, it would have been taken and no exception needed.
- **It ends as soon as it can.** The version **returns to the stable line the day 1.8.0 is stable.** That change is one line in `gradle/libs.versions.toml` (`carApp`, tagged `TODO(debt)`). One rule in `.github/dependabot.yml` goes with it (below); the rule is harmless if it is left in.
- **It does not widen.** No other library may be moved to a pre-release under this exception, and this library may not be moved to another one (a later release candidate, or a 1.9.0 alpha) without Shawn being asked again.

**How the exception is kept to what was decided.** Dependabot treats a library that is pinned to a pre-release as one that wants pre-releases, and proposes the highest version there is. Read from its source on 2026-10-06 (`dependabot-core`, the Gradle version finder and the grouping rule): with 1.8.0-rc01 pinned and nothing else changed, the weekly pull request of minor updates would have carried `1.9.0-alpha02`, and once 1.8.0 was stable Dependabot would still have proposed the newest 1.9.0 alpha in its place. So `.github/dependabot.yml` has one `ignore` rule, for `androidx.car.app:*`, of two ranges: `(1.8.0-rc01,1.8.0)`, a later release candidate of 1.8.0, and `[1.9.0-alpha01,1.9.0)`, every pre-release of 1.9.0. That leaves Dependabot the stable 1.8.0 to propose, and it will propose it as a pull request of its own, because the step from 1.8.0-rc01 to 1.8.0 changes none of the three numbers the grouping goes by. **The rule hides pre-releases only, so it is safe to forget.** Dependabot's pull request for 1.8.0 changes the version catalog and not this rule, and it is merged when CI is green; a rule that outlives the pin then hides nothing that would be offered, because Dependabot offers no pre-release for a library on a stable version, and 1.8.1, 1.9.0 and every later release lie outside both ranges. (The rule was first written as "everything above 1.8.0", which would have hidden every later release, security fixes included, for as long as nobody remembered to remove it. The review of this change replaced it: FINDINGS_LOG, 2026-10-06, evening.) It is still to be taken out when 1.8.0 is taken, as tidying. **Not covered:** a pre-release of a version after 1.9.0, which does not exist today; if one is offered while the pin is still the release candidate, it is not to be merged. All of this was read from Dependabot's code (`dependabot-core`, the Gradle version finder and the Maven range parser it shares), not seen happening: the first weekly run after this change is the proof.

**What the update changed, as far as it could be read.**

- **In MilO's code: nothing.** The gate (`spotlessCheck lintDebug testDebugUnitTest assembleDebug`) passes with 1.8.0-rc01 and no change to a Kotlin file, the manifest or a resource. The release notes of 1.8.0-alpha01 to rc01 name nothing that `platform/car/` uses: they add a media category, new templates, row images and one deprecation in the hardware API (`Mileage`), which MilO does not touch.
- **In the library** (the two AARs taken apart and compared, 1.7.0 against 1.8.0-rc01): the classes the Android Auto screen is built from (`Pane`, `PaneTemplate`, `Header`, their builders, the row constraints) and the classes behind `CarConnection` hold the same code in both; `Row` and `Action` gained members (an image at a row's end, a progress bar, a media action) and lost none. The library's lowest Android version moved from 5.0 to 6.0 (MilO's is higher). Its manifest declares no new permission, its list of Android Auto's signing certificates is unchanged, and the highest Car API level is still 8. One library it brings along moved: Guava, from 31.1 to 32.0.1.
- **The one change found in the check of who may connect.** In 1.7.0, `HostValidator` lets in a host that holds the permission `android.car.permission.TEMPLATE_RENDERER`, on any device, beside the hosts on the list. In 1.8.0-rc01 it does so only on a car that runs Android itself (`android.hardware.type.automotive`); on a phone, only the hosts on the list are let in. Google does not say that this is the security fix. It is the only difference found in that class, and it is in the very check MilO's exported service depends on.

**What is not proven.** A release candidate is not a release: Google may still change it before 1.8.0. The Android Auto screen has never run anywhere, with either version (APP_ENCYCLOPEDIA, Android Auto screen). `CarConnection` answered on an emulator with 1.8.0-rc01 as it did with 1.7.0 ("type 0", beside a stub of the Android Auto app); what it reports on the phone beside the real Android Auto has not been seen with either.

## Consequences

**What must change on the Mac before the project opens in Android Studio**

- Android Studio must be updated to Rabbit 1 2026.2.1 or Quail 4 2026.1.4. The installed Panda 1 2025.3.1 supports AGP only up to 9.0 and is below the minimum Studio version for API 37, so it cannot open an AGP 9.4 project or target API 37 (V1, V2). This blocks IDE sync and Run. Command-line Gradle builds do not depend on the Studio version.
- SDK platform 37 (`platforms;android-37.0`) is required. It was missing at kickoff, when only android-36 and android-36.1 were present (V33), and is installed now (checked 2026-10-03). The Mac has no `sdkmanager` command, so a platform comes from Studio's SDK Manager or from AGP's own download. Build-tools 36.0.0 is already installed and is enough for AGP 9.4.
- Rabbit 1 reportedly bundles Java 25 (V35, unverified), while the terminal uses Zulu 17. The build pins the Gradle daemon to Java 17 in `gradle/gradle-daemon-jvm.properties` so the terminal, Android Studio and CI run the same JDK.

**The untested combination**

- JetBrains documents Kotlin 2.4.20's Gradle plugin up to AGP 9.3.1 and Gradle 9.7.0. AGP 9.4.1 with Gradle 9.8.0 is one step beyond that (V8). Gradle's own matrix covers Kotlin 2.4.20-RC2 and AGP 9.5 alphas, but nobody had run this exact set when it was chosen. The first sync and build is the real test. (Result: the terminal build passed first time; see the verification line at the top.)
- Fallback if it fails to sync or build: AGP 9.3.3 with Gradle 9.7.1, keeping Kotlin 2.4.20, with the exact failure recorded in FINDINGS_LOG and in the verification line at the top of this ADR. Both still support API 37. The fallback is itself a patch level above JetBrains' table (V8).
- ktlint 1.8.0 parses with an embedded Kotlin 2.2.21 compiler, so it may reject syntax newer than Kotlin 2.2 (V30, K6). If that happens it needs a new decision; the research points at ktfmt.

**What gets worse**

- No Robolectric means nothing that touches Android classes is unit tested. Trip rules have to be written as pure Kotlin to be testable at all, and everything else rests on the device checklist.
- No detekt means the file-size limits and similar rules are review conventions, not checks.
- No lockfile means only declared versions are pinned. Gradle can still resolve a different transitive version (K7).
- GitHub Free cannot block merges on a private repo (K18). "Red build, no merge" is a rule Shawn follows by hand. CI also cannot test a Bluetooth-triggered start on the real phone.
- The debug keystore belongs to this Mac. A build signed on another machine cannot update the installed app, and the uninstall that follows deletes the trip data. A dedicated keystore is a later decision; the risk is recorded in ARCHITECTURE §10.

**What we are now locked into**

- Compose with Material 3, Navigation 3 and Room 3. Room 3 is coroutine-only and KSP-only (V17).
- Navigation 3 pulls in kotlinx-serialization, which is why the serialization plugin is in the toolchain.
- WorkManager 2.12.0 depends on Room 2.7.0 at runtime, so both Room lines are on the classpath. Imports must come from `androidx.room3` only.
- This ADR records the starting pins. Dependabot will move them weekly; `gradle/libs.versions.toml` is the live source.
- Since 2026-10-06, one pin is a pre-release, and one Dependabot rule exists because of it ("Exception, 2026-10-06"). Both are to be undone together when the Car App Library's 1.8.0 is stable; the rule is written so that forgetting it costs nothing.
