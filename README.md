# Project Documentation System

**MilO** is a personal Android app (native Kotlin, one phone, no backend, no accounts, never on the Play Store). It logs business mileage in Shawn's work truck automatically and produces a monthly PDF for accounts. What it must do is in `docs/APP_ENCYCLOPEDIA.md`.

This repo runs on a small set of documents that act as the project's **memory and rulebook**. They exist to prevent the two things that quietly wreck a codebase over time:

1. **Decisions made implicitly** — re-litigated ad-hoc in every feature until nothing is consistent.
2. **Lost context** — forgetting what was already built, why it was done a certain way, or how it works (e.g. "did I already add badges? how do they get awarded?").

Keep these documents current and the codebase stays understandable — even months later, even with an AI assistant doing most of the typing.

---

## Building and installing

Needs Android Studio Rabbit 1 2026.2.1 (or Quail 4 2026.1.4), SDK platform 37 and JDK 17. Older Android Studio versions cannot open this project. Details are in `docs/adr/ADR-001-stack-selection.md`.

**One-time setup, per clone.** Turn on the pre-commit hook:

```bash
git config core.hooksPath .githooks
```

Install gitleaks, which the hook uses to scan for secrets. Without it the hook stops every commit and prints an install hint:

```bash
brew install gitleaks
```

**Build.** This is the same command CI runs: format check, lint, unit tests, debug APK.

```bash
./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug
```

A terminal build has to be told where the Android SDK is. That path lives in `local.properties`, which git ignores, so a new clone does not have it and the build stops with "SDK location not found". Before the first terminal build in a new clone, either open the project once in Android Studio, which writes the file, or set the path in the shell:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

**Signing.** Builds for the phone are signed with one dedicated key: `~/keys/milo.jks`, with its password in the macOS Keychain under the name `milo-keystore`. Neither is in this repo, and the build finds both by itself. If the keystore or the Keychain entry is missing, the build stops and says what to do; it does not quietly use another key, because an app signed with another key cannot update the one on the phone. GitHub's CI has no key and builds with the throwaway debug key instead. To do the same on another machine, add `-Pmilo.signing.debugKey=true`, and never install that build on the phone. Keep a backup of the keystore and its password off the Mac.

**Your own trip-start sound.** The repo ships an original synthesized chirp. To use your own clip instead, save it as `app/src/debug/res/raw/trip_start_chirp.mp3` (or `.wav` / `.ogg`; the name must be `trip_start_chirp`). Debug builds made on this machine then play it. That folder is git-ignored on purpose: a personal clip may be someone else's copyright, so it must stay off GitHub. Delete the file to go back to the bundled chirp.

**Install on the phone.** Connect the POCO X5 with USB debugging on. HyperOS also needs "Install via USB" and "USB debugging (Security settings)" switched on in Developer options, and asks for a confirmation on the phone at each install. Then press Run in Android Studio or run:

```bash
./gradlew installDebug
```

The build on the phone holds the real trip data. Never uninstall it to fix a problem: uninstalling deletes the trips.

After the first install, open MilO and go to **Setup**. The checklist there asks for every permission, opens each phone setting that has to be changed (including the HyperOS ones), and leads to the screen where the truck is picked from the phone's paired Bluetooth devices. Until every required row is in order, the home screen shows a warning and a trip may not start by itself. `docs/DEVICE_TEST_CHECKLIST.md` lists what to check on the phone.

---

## The documents

| File | What it is | You open it to answer… |
|---|---|---|
| **`CLAUDE.md`** | Operating instructions for the AI assistant — read automatically at the start of every session | *(the AI's standing orders — keeps it reading before writing and logging after)* |
| **`docs/ENGINEERING_STANDARDS.md`** | The **rules** — how the app is built | "How should I do X?" |
| **`docs/ARCHITECTURE.md`** | The **map** — the system's shape, stack, data flow, what the two UI surfaces share | "What's the structure? What connects to what?" |
| **`docs/APP_ENCYCLOPEDIA.md`** | The **reference** — every feature/capability and exactly how it works | "Have I built this before? How does it behave?" |
| **`docs/FINDINGS_LOG.md`** | The **journal** — a dated record of every change, decision, and gotcha | "What happened, and why did we do it this way?" |

ADRs (Architecture Decision Records) for big individual decisions live in `docs/adr/`. Research notes live in `docs/research/`: dated evidence behind the ADRs, not kept current.

---

## Read order

**Starting fresh on the project:** read `CLAUDE.md` → `docs/ENGINEERING_STANDARDS.md` → `docs/ARCHITECTURE.md`. Skim `docs/APP_ENCYCLOPEDIA.md` to see what exists.

**Working with the AI assistant:** you don't need to do anything special — `CLAUDE.md` tells it to read the rest first. `CLAUDE.md` stays in the repo root; the other four docs live in `docs/`, where it expects to find them.

---

## The core loop

Every meaningful change follows the same loop. This is what keeps the docs and the code from drifting apart:

```
READ   standards + architecture + the relevant encyclopedia entry
  ↓
ASK    phone UI, Android Auto screen, or both? reuse an existing pattern? already built?
  ↓
BUILD  the smallest correct version, matching existing patterns
  ↓
UPDATE the encyclopedia if behavior changed
  ↓
LOG    append to the findings log: what changed and why
```

---

## Where to look when you're stuck

- **"Did I already build this?"** → `docs/APP_ENCYCLOPEDIA.md`. Search before building anything — extend what exists, don't reinvent it.
- **"Why did we do it this way?"** → `docs/FINDINGS_LOG.md`, then `docs/adr/`.
- **"How am I supposed to do this?"** → `docs/ENGINEERING_STANDARDS.md`.
- **"What's the shape of the system / is this shared between the phone UI and the Android Auto screen?"** → `docs/ARCHITECTURE.md`.

---

## Day-1 setup

Before writing any product feature (full checklist in `docs/ENGINEERING_STANDARDS.md` §18):

1. Private GitHub repo + `.gitignore` (build output, `local.properties`, keystores)
2. Gradle project that builds: one `:app` module, Kotlin `allWarningsAsErrors`, Android Lint `warningsAsErrors`, Spotless + ktlint
3. Pre-commit hook in `.githooks/` (gitleaks staged scan, then `spotlessCheck`)
4. **Latest *stable* dependencies, pinned as exact versions in `gradle/libs.versions.toml`** — and turn on **Dependabot** so they never go stale
5. Secret-scanning (gitleaks) in CI
6. Package structure + design tokens + base component library
7. CI on every PR (`spotlessCheck lintDebug testDebugUnitTest assembleDebug`)
8. Android Studio updated and SDK platform 37 installed, so the debug build installs on the phone
9. **`CLAUDE.md` in the repo root and the other four docs in `docs/`**, plus `docs/adr/` with ADR-001 (stack choice)

Left out on purpose: Sentry, staging and prod environments, a lockfile, detekt, Robolectric, Hilt, Renovate. The reasons are in ADR-001.

---

## Two habits that matter most

These directly fix the recurring pain:

- **Keep dependencies fresh continuously.** Dependabot + pinned versions means small painless updates instead of a once-a-year migration wall. (STANDARDS §2.)
- **Write it down when you do it, not later.** Every feature gets an encyclopedia entry; every change gets a findings-log line. That's how "did I already build badges?" becomes a five-second search instead of an hour of code spelunking.
