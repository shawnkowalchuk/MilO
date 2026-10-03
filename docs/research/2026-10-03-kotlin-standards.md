# Research: Translating the engineering standards to Kotlin and Android

> Gathered 2026-10-03 by a research agent reading live primary sources. **Not independently verified.** Treat as leads to confirm when the code is built and run on the phone.
> This file is evidence for the ADRs. It records what was true on that date and is not kept current.

## Summary

Research only: I created and modified no files. On the relayed request ("fix the pointers"): a read-only grep shows CLAUDE.md and README.md already point at docs/... and docs/adr/, and the folder on disk is lowercase docs/ (macOS is case-insensitive, so DOCS/ resolves to the same directory). Two bare references remain in CLAUDE.md's build-loop block (lines 68 and 70: "APP_ENCYCLOPEDIA.md", "FINDINGS_LOG.md" without docs/). The project is not yet a git repository, so hooks, CI and Dependabot are all prospective.

Toolchain confirmed live on 2026-10-03: Kotlin 2.4.20, AGP 9.4.1 (min Gradle 9.6.0, JDK 17, max API 37), Gradle 9.8.0. AGP 9 compiles Kotlin itself, which breaks older quality plugins.

Translation of the TypeScript standards, with a verdict for a one-phone personal app:

| TypeScript standard | Kotlin/Android equivalent | Verdict |
|---|---|---|
| strict tsc | `allWarningsAsErrors` + `extraWarnings` in `kotlin { compilerOptions {} }` | Worth it |
| (no TS equivalent) | Explicit API mode | Overhead (meant for library authors) |
| ESLint | Android Lint with `warningsAsErrors` (Compose checks included) | Worth it |
| ESLint extras | detekt | Skip for now: no stable release works with AGP 9 / Kotlin 2.4; 2.0 is still alpha |
| Prettier | Spotless 8.10.3 running ktlint 1.8.0 | Worth it |
| Husky + lint-staged | Committed `.githooks/pre-commit` via `core.hooksPath` (gitleaks staged scan + `spotlessCheck`) | Worth it; lefthook and pre-commit are overhead |
| package.json exact pins | `gradle/libs.versions.toml` with plain version strings | Worth it |
| Lockfile | Gradle dependency locking | Marginal: bots probably cannot regenerate it for Android |
| (supply-chain extra) | Gradle dependency verification | Overhead |
| .nvmrc / reproducible toolchain | Wrapper `distributionSha256Sum` | Worth it (one line, Dependabot maintains it) |
| Renovate or Dependabot | Dependabot | Better fit: native, free on private repos, handles catalog, wrapper and checksum |
| npm audit | `gradle/actions/dependency-submission` + Dependabot alerts | Worth it if on GitHub; OSV-Scanner and OWASP dependency-check are overhead |
| gitleaks in CI | Same, plus local hook | Worth it; GitHub's own secret scanning is not free on private repos |
| CI on every PR | One GitHub Actions job | Worth it if on GitHub, but GitHub Free cannot block merges on private repos |
| Feature-first folders | `feature/`, `data/`, `core/` packages in one module | Worth it; isolation is by convention only |
| DI framework | Manual DI (AppContainer) rather than Hilt | Manual preferred at this size |
| Sentry | Uncaught-exception handler + `ApplicationExitInfo` (+ `ApplicationStartInfo` on API 35+) | Very worth it; directly serves reliable trip start |
| dev/staging/prod | Debug build with `applicationIdSuffix` + a deliberate signing-key choice | Worth it |

## Findings

### 1. Current stable toolchain is Kotlin 2.4.20, AGP 9.4.1 and Gradle 9.8.0; AGP 9 compiles Kotlin itself, so compiler options move to a top-level kotlin { compilerOptions { } } block.

AGP 9.4.0 (September 2026) needs Gradle 9.6.0+, JDK 17, and supports up to API 37; Google Maven lists 9.4.1 as the newest non-prerelease. Gradle current is 9.8.0 (services.gradle.org/versions/current). Kotlin 2.4.20 was released 2026-09-07 (2.4.21-RC and 2.5.0-Beta1 are prereleases; api.github.com/repos/JetBrains/kotlin/releases). With built-in Kotlin the org.jetbrains.kotlin.android plugin must be removed and android.kotlinOptions is replaced by kotlin.compilerOptions; kapt is replaced by KSP or com.android.legacy-kapt (developer.android.com/build/migrate-to-built-in-kotlin). Opt-out flags android.builtInKotlin=false and android.newDsl=false still exist in 9.x but are slated for removal in AGP 10.

- Confidence: high · Applies to: baseline
- Source: https://developer.android.com/build/releases/gradle-plugin

### 2. detekt has no stable release that works with AGP 9 built-in Kotlin and Kotlin 2.4; only 2.0.0 alphas do. Skip it for now.

Latest stable is 1.23.8 (2025-02-21), built for Kotlin 2.0.21 / AGP 8.8.1 / Gradle 8.12.1. Issue #8865 (1.23.8 cannot read Kotlin 2.3 metadata, floods false positives) was closed as not planned on 2025-11-18. AGP 9 built-in Kotlin support (issue #8320) landed only on the 2.0.0 milestone. 2.0.0-alpha.6 (2026-08-04, plugin id dev.detekt) is built against Kotlin 2.4.10 / Gradle 9.6.1 / AGP 9.3.1; there is no stable 2.0.0 and no published date. Using 1.23.8 would require the AGP opt-out flags (per secondary reports); using the alpha breaks the project's no-prerelease rule. Verdict: overhead today; revisit when 2.0.0 stable ships.

- Confidence: high · Applies to: static analysis (ESLint equivalent)
- Source: https://detekt.dev/docs/introduction/compatibility/

### 3. Android Lint with warningsAsErrors is the maintained ESLint equivalent and comes free with AGP; turn off its version-nag check so it does not fight Dependabot.

lint { warningsAsErrors = true } treats all warnings as errors; abortOnError defaults to true; a baseline file exists but a new project should not need one (Lint.kt in AOSP tools/base, studio-main). Run with ./gradlew lintDebug. Compose ships its own lint checks that run inside Android Lint (developer.android.com/develop/ui/compose/tooling/lint). The GradleDependency check (severity Warning, applies to Gradle and TOML files) fires whenever a newer library version exists, so with warningsAsErrors it would break the build on every upstream release; add it to lint.disable. Similar checks such as AndroidGradlePluginVersion probably need the same treatment (not verified). Verdict: worth doing.

- Confidence: high · Applies to: static analysis (ESLint equivalent)
- Source: https://developer.android.com/studio/write/lint

### 4. The strict-tsc equivalent is allWarningsAsErrors plus extraWarnings in the Kotlin compiler options; explicit API mode is for library authors and is overhead for an app.

Kotlin Gradle options: allWarningsAsErrors (default false), extraWarnings (extra declaration/expression/type checks, default false), progressiveMode. Individual diagnostics can be tuned with -Xwarning-level=NAME:(error|warning|disabled), so one noisy warning can be downgraded without dropping -Werror. -Xreturn-value-checker=check|full is available for ignored return values. Explicit API mode (kotlin { explicitApi() }) forces visibility modifiers and explicit types on public declarations and is documented as recommended for library authors (kotlinlang.org/docs/api-guidelines-simplicity.html). 'tsc --noEmit' maps to ./gradlew compileDebugKotlin. Verdict: allWarningsAsErrors + extraWarnings worth doing; explicitApi overhead.

- Confidence: high · Applies to: static analysis (strict tsc equivalent)
- Source: https://kotlinlang.org/docs/gradle-compiler-options.html

### 5. Spotless running ktlint is the simplest maintained Prettier equivalent; it is independent of AGP internals.

Spotless Gradle plugin 8.10.3 was released 2026-09-25 (id com.diffplug.spotless; needs JRE 17+, Gradle 7.3+). Its defaults are ktlint 1.8.0 and ktfmt 0.64 (plugin-gradle/CHANGES.md). It cannot auto-detect Android source sets, so set target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt") and add kotlinGradle { target("*.gradle.kts"); ktlint() }. Tasks are spotlessCheck and spotlessApply; ./gradlew spotlessInstallGitPrePushHook installs a pre-push hook. ktlint style is set via .editorconfig or editorConfigOverride (ktlint_code_style). Alternative: ktlint-gradle 14.2.0 (2026-03-12) added AGP 9 built-in Kotlin support in 14.1.0. Pick one, not both. Verdict: worth doing.

- Confidence: high · Applies to: formatting (Prettier equivalent)
- Source: https://raw.githubusercontent.com/diffplug/spotless/main/plugin-gradle/README.md

### 6. ktlint's latest stable (1.8.0) is built on Kotlin 2.2.21, so it may not parse syntax introduced in Kotlin 2.3/2.4; ktfmt is the fallback.

ktlint 1.8.0 was published 2025-11-14 and its version catalog pins kotlin = 2.2.21. ktlint master is on Kotlin 2.4.20, but the only newer release is 2.0.0-ALPHA-4 (2026-08-21), which moves Maven coordinates to io.github.ktlint and states the project is no longer maintained by Pinterest. ktfmt 0.64 (2026-06-24) is actively released and is format-only. I found no report of 1.8.0 failing on Kotlin 2.4 code; the parsing risk is my inference from the embedded compiler version. If ktlint chokes on a new construct, switching the Spotless step to ktfmt is a one-line change.

- Confidence: medium · Applies to: formatting (Prettier equivalent)
- Source: https://raw.githubusercontent.com/pinterest/ktlint/1.8.0/gradle/libs.versions.toml

### 7. A version catalog with plain version strings is the exact-pin equivalent of package.json, but it is not a lockfile.

gradle/libs.versions.toml is picked up automatically; sections are [versions], [libraries], [plugins], [bundles]; plugins are applied with alias(libs.plugins.x). A plain string such as "3.0.5" is a required version (no ^ or ~ unless you write a range or +). Gradle states that catalogs declare requested versions but do not enforce them: conflict resolution can still pick a different transitive version. Android's own guide recommends the same file and layout (developer.android.com/build/migrate-to-catalogs). Verdict: worth doing.

- Confidence: high · Applies to: version pinning
- Source: https://docs.gradle.org/current/userguide/version_catalogs.html

### 8. Gradle dependency locking is the true lockfile equivalent, but update bots probably cannot regenerate it for an Android project, so it means manual work on every update.

dependencyLocking { lockAllConfigurations() } plus ./gradlew dependencies --write-locks writes gradle.lockfile per project; buildscript configurations are not covered by default (buildscript-gradle.lockfile is separate). Gradle says locking is most valuable with dynamic versions. Renovate regenerates lockfiles by running ./gradlew and its docs say it does not support Android projects that need extra configuration such as the Android SDK. Dependabot's lockfile_updater.rb also runs Gradle with --write-locks, and its Gradle Dockerfile installs JDK 17/21 and Gradle but no Android SDK; I did not find a statement that Android lockfile updates fail, so that part is inference. Verdict: marginal for this app; exact pins with no dynamic versions already give repeatable builds. If skipped, record it as a deliberate deviation from 'commit the lockfile'.

- Confidence: medium · Applies to: lockfile
- Source: https://docs.gradle.org/current/userguide/dependency_locking.html

### 9. Gradle dependency verification is real supply-chain protection but high-maintenance; it is overhead for a one-phone personal app.

./gradlew --write-verification-metadata sha256 (or pgp,sha256) help writes gradle/verification-metadata.xml and verification turns on when the file exists. Android's official guide recommends signatures with trusted keys and trusting *-sources.jar / *-javadoc.jar so Android Studio sync works (developer.android.com/build/dependency-verification). Every dependency change needs the metadata regenerated and reviewed. Dependabot's Gradle file fetcher lists build files, the catalog, gradle.lockfile and wrapper files but not verification-metadata.xml, so each Dependabot PR would fail until fixed by hand. Renovate can update it only when allowed to execute ./gradlew, and not for Android projects. Community reports (not a primary source) also describe macOS-generated metadata missing the Linux aapt2 artifact that CI needs. Verdict: overhead; it works against the main goal of keeping updates painless.

- Confidence: medium · Applies to: dependency verification
- Source: https://docs.gradle.org/current/userguide/dependency_verification.html

### 10. Pinning the Gradle distribution checksum is one line and Dependabot keeps it current.

Add distributionSha256Sum=<hash> to gradle/wrapper/gradle-wrapper.properties (or pass --gradle-distribution-sha256-sum to the wrapper task); Gradle fails the build on mismatch. For 9.8.0 the distribution checksum is bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c (services.gradle.org/versions/current). Upgrade with ./gradlew :wrapper --gradle-version X, run twice to refresh all wrapper files. Commit gradle-wrapper.jar, the properties file, gradlew and gradlew.bat. setup-gradle validates the wrapper JAR in CI by default. Dependabot's wrapper updater treats distributionUrl and distributionSha256Sum as managed keys (properties_reconciler.rb). Verdict: worth doing.

- Confidence: high · Applies to: Gradle wrapper
- Source: https://docs.gradle.org/current/userguide/gradle_wrapper.html

### 11. Dependabot supports the Gradle version catalog, gradle.lockfile and the Gradle wrapper, natively and at no cost on private repos.

GitHub's docs source lists: build.gradle / build.gradle.kts, gradle/libs.versions.toml (standard location only), gradle.lockfile; and for the wrapper it runs Gradle and updates gradle-wrapper.properties, gradlew, gradlew.bat and gradle-wrapper.jar. Security updates for Gradle rely on dependency-submission data. Dependabot runs on standard GitHub-hosted runners are free and do not use the Actions quota (docs.github.com billing page). Config is a committed .github/dependabot.yml with package-ecosystem gradle and github-actions; options include schedule.interval, groups, cooldown and open-pull-requests-limit (default 5). I could not confirm whether the wrapper task succeeds on an Android project inside Dependabot's SDK-less image; if it fails, the wrapper bump is a two-command manual job.

- Confidence: high · Applies to: dependency updates
- Source: https://raw.githubusercontent.com/github/docs/main/data/reusables/dependabot/supported-package-managers.md

### 12. Renovate also supports catalogs and the wrapper, but since v43 it will not execute ./gradlew unless a self-hosted admin allows it, and it documents no support for Android projects that need the SDK.

Renovate updates *.gradle(.kts), gradle.properties, *.versions.toml anywhere, *.lockfile and gradle/verification-metadata.xml. Its docs list as unsupported: Android projects that require extra configuration to run (e.g. setting the Android SDK). Catalog version bumps use a JavaScript parser and do not run Gradle. Wrapper, lockfile and verification updates need allowedUnsafeExecutions to include gradleWrapper, a global self-hosted option; gradle-wrapper/artifacts.ts returns null (no update) when execution is not allowed. Renovate 43 made this off by default (discussion #40790). The Mend-hosted app is free for private repos (1 concurrent job, runs every 4 hours).

- Confidence: high · Applies to: dependency updates
- Source: https://raw.githubusercontent.com/renovatebot/renovate/main/docs/usage/java.md

### 13. Whether the Mend-hosted Renovate app enables Gradle wrapper execution is unconfirmed; secondary reports say it does not.

The Mend-hosted config page does not mention allowedUnsafeExecutions or gradleWrapper. Several third-party repositories' PR notes say the hosted app does not enable it and repos cannot override it, which would mean no wrapper jar/script/checksum updates from hosted Renovate. I could not confirm this from Renovate or Mend documentation. Either way Dependabot is the better fit here: nothing to install, no third-party app with access to a private repo, and it covers catalog, wrapper, checksum and Actions versions.

- Confidence: low · Applies to: dependency updates
- Source: https://docs.renovatebot.com/mend-hosted/hosted-apps-config/

### 14. A plain git hook via core.hooksPath is the simplest Husky replacement; lefthook and pre-commit add a tool to install for no gain on a solo repo.

Git reads hooks from $GIT_DIR/hooks unless core.hooksPath points elsewhere, so a committed .githooks/pre-commit script works after a one-time 'git config core.hooksPath .githooks' per clone; a non-zero exit aborts the commit and --no-verify bypasses it. lefthook 2.1.16 (2026-10-01) is a single Go binary (brew install lefthook, lefthook.yml, 'lefthook install' per clone). pre-commit 4.6.2 needs Python. Neither needs Node. Suggested hook content: gitleaks staged scan, then ./gradlew spotlessCheck; leave Android Lint and unit tests to pre-push or CI because they are slow (my judgment, not sourced). With detekt skipped there is no detekt step. Verdict: plain hook worth doing; lefthook/pre-commit overhead.

- Confidence: high · Applies to: pre-commit hooks
- Source: https://git-scm.com/docs/githooks

### 15. The current gitleaks command for a pre-commit scan is 'gitleaks git --pre-commit --redact --staged --verbose'.

gitleaks 8.30.1 (2026-03-21) is the latest release; install with brew install gitleaks. The detect and protect commands were deprecated in v8.19.0 in favour of git, dir and stdin. The project's own .pre-commit-hooks.yaml uses exactly the command above. The hook should fail loudly if the gitleaks binary is missing. For this app the realistic secrets are a release keystore, its passwords and local.properties.

- Confidence: high · Applies to: secret scanning
- Source: https://raw.githubusercontent.com/gitleaks/gitleaks/master/.pre-commit-hooks.yaml

### 16. GitHub's own secret scanning and push protection are free only on public repos, so gitleaks is the free option for a private repo.

GitHub's security-features page lists dependency graph and Dependabot alerts/updates as available on all plans, while secret scanning, push protection and code scanning are on by default only for public repositories and need paid Secret Protection / Code Security for private ones. gitleaks-action v3.0.0 (2026-05-30, Node 24 runtime) needs no licence for personal accounts (organisations need a free key), wants GITHUB_TOKEN, and needs checkout with fetch-depth: 0. Verdict: local hook worth doing; CI gitleaks is a cheap backstop.

- Confidence: medium · Applies to: secret scanning
- Source: https://docs.github.com/en/code-security/getting-started/github-security-features

### 17. A minimal Android CI job needs four actions and no Android SDK setup step; current majors are checkout v7, setup-java v6, gradle/actions v6, gitleaks-action v3.

Latest releases (api.github.com releases, read 2026-10-03): actions/checkout v7.0.1, actions/setup-java v6.0.1, gradle/actions v6.4.0, gitleaks/gitleaks-action v3.0.0, actions/upload-artifact v7.0.1. The ubuntu-24.04 runner image already has Java 17 as default, Android platforms android-36 and android-37.0, and build-tools 36/37. setup-gradle@v6 validates the wrapper JAR by default; its default 'enhanced' cache is proprietary and only a free preview on private repos, so set cache-provider: basic (MIT, built on actions/cache). Steps: checkout (fetch-depth: 0) -> gitleaks-action -> setup-java (temurin 17) -> setup-gradle -> ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug, with permissions: contents: read. The official READMEs still show checkout@v6 / setup-java@v5 in examples; I did not find documented breaking changes in the newer majors beyond ESM migration and dropping legacy Adopt distributions.

- Confidence: medium · Applies to: CI
- Source: https://github.com/gradle/actions/blob/main/docs/setup-gradle.md

### 18. On GitHub Free, a private repo gets 2,000 Actions minutes a month but no protected branches, so 'red build = no merge' cannot be enforced.

GitHub's plans page lists protected branches, required reviewers and code owners for private repos under GitHub Pro, not Free. Free personal accounts get 2,000 minutes/month and 500 MB artifact storage for private repos; Linux runners are the cheapest. CI is still worth having because it is what makes Dependabot PRs safe to merge, but on Free it is advisory. CI also cannot test the thing that matters most here (Bluetooth-triggered start on the real phone).

- Confidence: medium · Applies to: CI
- Source: https://docs.github.com/en/get-started/learning-about-github/githubs-plans

### 19. The npm-audit equivalent is the Gradle dependency-submission action feeding Dependabot alerts; OSV-Scanner and OWASP dependency-check are overhead here.

gradle/actions/dependency-submission@v6 resolves all configurations (without building) and submits the full graph, including transitives, to GitHub's dependency graph; it needs permissions: contents: write and is normally run on push to main. GitHub then raises Dependabot alerts. Dependabot cannot open a fix PR for a purely transitive dependency. OSV-Scanner 2.6.0 reads gradle.lockfile, buildscript-gradle.lockfile and gradle/verification-metadata.xml but not libs.versions.toml, so it only helps if locking is adopted. OWASP dependency-check-gradle 13.0.0 works but is a heavy plugin to carry. The app has no backend, so exposure is small; the submission workflow is cheap enough to keep. I did not confirm whether the submission action resolves an Android project without extra setup (the runner image does have the SDK).

- Confidence: medium · Applies to: vulnerability scanning
- Source: https://github.com/gradle/actions/blob/main/docs/dependency-submission.md

### 20. A single module with feature-first packages matches Google's guidance for small apps; Kotlin cannot enforce feature boundaries inside one module.

Google says modularization does not always make sense and that going too fine-grained adds overhead (developer.android.com/topic/modularization). Its architecture recommendations say small apps can keep data-layer types in a data package and UI types in a ui package, that UI code must not touch data sources directly, and name GPS location providers and Bluetooth data providers as data sources that belong behind repositories. Kotlin has private (file-level for top-level declarations), protected, internal (whole module) and public, with no package-private level (kotlinlang.org/docs/visibility-modifiers.html), so 'features do not import each other' is a convention. Proposed layout under the app package: MiloApp.kt, AppContainer.kt, MainActivity.kt, navigation/, feature/{trips, tracking, vehicle, export, diagnostics, settings}/, data/{db (Room database, entities, DAOs, migrations), repositories}, core/{designsystem, bluetooth, location, util}. The foreground service, Bluetooth and boot receivers and the grace-period logic live in feature/tracking; Room sits in data/ because tracking and trips both use it. Verdict: worth doing.

- Confidence: medium · Applies to: project structure
- Source: https://developer.android.com/topic/architecture/recommendations

### 21. Two naming and size rules in the standards need translating: Kotlin files are PascalCase, and nothing stable currently enforces the 300-line limit.

The Android Kotlin style guide says a file with one top-level class is named after it (MyClass.kt), other files use PascalCase, packages are all lowercase with no underscores, constants are UPPER_SNAKE_CASE, and the column limit is 100. That replaces the kebab-case filename rule. The file and function size limits were detekt rules; with detekt out, the limit is a review convention or a short wc -l check in the git hook. I did not find a file-length rule in ktlint's standard set (not exhaustively checked).

- Confidence: medium · Applies to: project structure
- Source: https://developer.android.com/kotlin/style-guide

### 22. Manual DI is the better fit at this size; Google allows either in simple apps, and Hilt's Gradle plugin is tied to AGP major versions.

Google's recommendation: use Hilt or manual DI in simple apps; use Hilt if the project is complex enough, e.g. multiple screens with ViewModels, WorkManager, or ViewModels scoped to the nav back stack. MilO will have a few ViewModel screens, so by the letter it sits on the line. The manual pattern is an AppContainer held by the Application class (developer.android.com/training/dependency-injection/manual). Application.onCreate is documented to run before any activity, service or receiver is created, so a receiver or service started in a cold process can reach the container. Hilt is at 2.60.1; since Dagger 2.59 its Gradle plugin requires AGP 9 and Gradle 9.1+, which is the kind of coupling that turns into an upgrade wall. Manual DI needs no extra plugin or annotation processor beyond Room's KSP. Verdict: manual DI; Hilt is overhead unless the app grows (injected WorkManager workers, many screens).

- Confidence: medium · Applies to: dependency injection
- Source: https://developer.android.com/training/dependency-injection/manual

### 23. A custom uncaught-exception handler must save the previous default handler and call it after logging, or the process will not die and be recorded properly.

Android's RuntimeInit installs KillApplicationHandler as the default uncaught-exception handler; it reports the crash to ActivityManager (handleApplicationCrash) and then calls Process.killProcess and System.exit(10). The Sentry replacement is: in Application.onCreate, read Thread.getDefaultUncaughtExceptionHandler(), install a handler that writes thread name, stack trace and timestamp synchronously (plain file append, since Room and coroutines may not complete while the process is dying), then delegates to the saved handler. On next start, import that file into the app's event log. Keep location coordinates out of crash text (standards: no PII in logs). Verdict: very worth doing.

- Confidence: high · Applies to: crash capture
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/os/RuntimeInit.java

### 24. ApplicationExitInfo tells the next process why the last one died, including system kills that no exception handler sees.

ActivityManager.getHistoricalProcessExitReasons(null, 0, n) returns records most-recent-first from a system ring buffer (only recent ones are kept). Reason codes: EXIT_SELF 1, SIGNALED 2, LOW_MEMORY 3 (not reported on all devices; check isLowMemoryKillReportSupported, otherwise it appears as SIGNALED with SIGKILL), CRASH 4, CRASH_NATIVE 5, ANR 6, INITIALIZATION_FAILURE 7, PERMISSION_CHANGE 8, EXCESSIVE_RESOURCE_USAGE 9, USER_REQUESTED 10 (force stop, or removed from Recents), USER_STOPPED 11, DEPENDENCY_DIED 12, OTHER 13, FREEZER 14, PACKAGE_STATE_CHANGE 15, PACKAGE_UPDATED 16. getTraceInputStream gives ANR traces and, from API 31, native tombstones. setProcessStateSummary(bytes, max 128, no PII) lets the app tag state such as 'trip active' so the next process knows what the dead one was doing; calls may be throttled. The class has been available since API 30 (from memory, not re-confirmed today), so minSdk 31 is covered. Log each new record once (dedupe on timestamp and pid).

- Confidence: high · Applies to: crash capture
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ApplicationExitInfo.java

### 25. ApplicationStartInfo can record why each process start happened (broadcast, service, boot), which speaks directly to the trip-start requirement.

ActivityManager.getHistoricalProcessStartReasons(maxNum) returns recent start records from a ring buffer. Start reasons include ALARM 0, BOOT_COMPLETE 2, BROADCAST 3, JOB 5, LAUNCHER 6, SERVICE 10, START_ACTIVITY 11; there is also wasForceStopped(). Logging this at startup would show whether the Bluetooth connection broadcast actually started the process and whether the app had been force-stopped. The source marks it as a flagged API; I believe it shipped in API 35 (Android 15) but could not load the reference page to confirm the level, so it needs an SDK_INT guard above minSdk 31.

- Confidence: medium · Applies to: crash capture
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ApplicationStartInfo.java

### 26. dev/staging/prod has no meaning without a backend; the useful equivalent is a debug build that installs beside the daily-driver build, plus a deliberate signing-key choice.

Setting applicationIdSuffix = ".debug" on the debug build type gives it a different application ID, so a development build and the build the owner relies on for real trips can coexist on the one phone with separate databases. Android only installs an update if it is signed with the same certificate as the installed app; the debug keystore is auto-generated per machine at $HOME/.android/debug.keystore (developer.android.com/studio/publish/app-signing). A new Mac or a CI-built APK would therefore not update in place, and uninstalling loses the trip database unless it was exported. If a dedicated keystore is used, its passwords belong in an untracked keystore.properties, which is the only real secret this app has. Verdict: suffix worth doing; the keystore choice is the owner's.

- Confidence: high · Applies to: environments and config
- Source: https://developer.android.com/build/build-variants

## Recommendations

- Rewrite the tooling rows of docs/ENGINEERING_STANDARDS.md (sections 2, 4, 10, 12, 13, 14, 18) with the Kotlin/Android equivalents in the summary table, and record each 'skip' (detekt, lockfile, dependency verification, staging/prod, Sentry) as a deliberate decision in an ADR or [DEBT] entry rather than leaving the TypeScript wording.
- Quality gate: Kotlin allWarningsAsErrors + extraWarnings, Android Lint with warningsAsErrors = true and GradleDependency disabled, and Spotless 8.10.3 with ktlint 1.8.0 over src/**/*.kt and *.gradle.kts. Do not enable explicit API mode.
- Do not add detekt now. Put a dated note in FINDINGS_LOG to revisit when detekt 2.0.0 stable (plugin id dev.detekt) is released; until then the file-size limits are a review convention.
- If ktlint 1.8.0 fails to parse Kotlin 2.4 code, switch the Spotless step to ktfmt 0.64 rather than adopting the ktlint 2.0 alpha.
- Pin everything in gradle/libs.versions.toml with plain version strings, apply plugins through alias(libs.plugins.x), and add distributionSha256Sum to gradle-wrapper.properties. Skip dependencyLocking and verification-metadata.xml at the start.
- Use Dependabot, not Renovate: one .github/dependabot.yml with weekly gradle and github-actions entries, grouped so minor/patch updates arrive as one PR and majors arrive separately (matches the one-major-per-branch rule).
- Commit a .githooks/pre-commit script (gitleaks git --pre-commit --staged --redact, then ./gradlew spotlessCheck) and document the one-time 'git config core.hooksPath .githooks' and 'brew install gitleaks' in the README. Keep lint and unit tests out of pre-commit.
- CI (only if the repo is on GitHub): one ubuntu-24.04 job running checkout@v7 (fetch-depth 0), gitleaks-action@v3, setup-java@v6 (temurin 17), setup-gradle@v6 with cache-provider: basic, then ./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug. Add a second small workflow on push to main with gradle/actions/dependency-submission@v6 and enable Dependabot alerts.
- Structure: single :app module with feature/, data/ and core/ packages as outlined in the findings; PascalCase Kotlin file names; features never import each other; shared code moves to core/ or data/.
- Use manual DI: an AppContainer created in the Application class, reached from the Bluetooth receiver, the foreground service and ViewModel factories. Revisit Hilt only if WorkManager workers with injected dependencies or many more screens appear.
- Build crash and kill capture into the first milestone, since it is the evidence for the trip-start requirement: a chained uncaught-exception handler writing to a file, ApplicationExitInfo import at startup, setProcessStateSummary tagging trip state, and ApplicationStartInfo logging on API 35+.
- Add applicationIdSuffix ".debug" to the debug build type so experimental builds never overwrite the build holding real trip data.
- Minor doc fix for whoever owns the pointer change: CLAUDE.md lines 68 and 70 (the build-loop block) still say APP_ENCYCLOPEDIA.md and FINDINGS_LOG.md without the docs/ prefix; every other pointer in CLAUDE.md and README.md already uses docs/.

## Questions raised for the owner

- Will this repo be pushed to GitHub as a private repo, and are you on GitHub Free or Pro? Dependabot, CI and vulnerability alerts all depend on it being on GitHub, and only Pro can block merges on a failing build for a private repo.
- Are you comfortable skipping the Gradle lockfile and dependency verification (keeping exact pins plus the wrapper checksum), given they would likely need manual regeneration on every dependency update? The alternative is stricter supply-chain checks at the cost of more friction per update.
- Should dev-only tooling be allowed a written exception to the no-prerelease rule (which would permit detekt 2.0.0-alpha.6), or do we go without detekt until 2.0.0 stable? I recommend going without.
- Which signing key should the build you rely on daily use: the auto-generated debug keystore on this Mac (simplest, but a new Mac means uninstall and losing on-phone data unless exported), or a dedicated keystore that you back up?
- Do you want a separate '.debug' build installed beside your daily build on the phone, so testing never touches real trip data? It means two MilO icons and two sets of permissions to grant.
