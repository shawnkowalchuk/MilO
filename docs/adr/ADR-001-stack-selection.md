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

- The project rule is latest stable only: no alpha, beta or RC.
- The standards template was written for TypeScript (strict tsc, ESLint, Prettier, Husky, npm audit, Sentry, dev/staging/prod). Each rule needed a Kotlin equivalent or a stated reason for dropping it.
- The Mac has one JDK, Zulu 17.0.18, and `JAVA_HOME` is unset. No second JDK is being installed.
- Every version below was read from live indexes on 2026-10-03 and re-checked by an independent verifier. The evidence is `docs/research/2026-10-03-versions.md` (cited as V plus the finding number) and `docs/research/2026-10-03-kotlin-standards.md` (cited as K plus the finding number; that file was not independently verified).

## Decision

### Project shape

- One Gradle module, `:app`, at the repo root. `applicationId` and `namespace`: `com.shawnkowalchuk.milo`. App name: MilO.
- minSdk 31, compileSdk 37, targetSdk 37. Android 17 (API 37) is the newest stable platform, and the current AndroidX releases require compileSdk 37 (V27, V28).
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
| `androidx.car.app` (`app` and `app-projected`) | 1.7.0 | V22 |
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
| Pre-release versions | AGP 9.5 alphas, Kotlin 2.4.21-RC, Gradle 9.9 milestones, car app 1.8.0-rc01 and the others listed in the versions research were seen and excluded. |

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
