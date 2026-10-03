# Architecture

> **What this is.** The *map* of the system — its shape, the stack, how data flows, and how the pieces connect. Read this before building anything that touches structure. Update it whenever the structure changes. It should always answer: *"if I drop a new developer (or AI assistant) in, what do they need to know to not break things?"*
>
> **Status:** Living document · **Last updated:** 2026-10-03 · **See also:** ENGINEERING_STANDARDS.md (the rules), APP_ENCYCLOPEDIA.md (how each feature works)

The project skeleton is built: the Gradle build with its quality gates, the design system in `core/designsystem/`, a placeholder home screen and the CI files. No trip feature exists yet. The rest of this document describes the structure the code must follow and the constraints already known from research. Required behaviour is in APP_ENCYCLOPEDIA.md.

---

## 1. System overview

MilO is a native Kotlin Android app for one person on one phone: Shawn's Xiaomi POCO X5 (Android 14, HyperOS). It notices when the phone connects to the work truck over Bluetooth, records the drive with GPS in a foreground service, stores the trip on the phone, and produces a monthly PDF that Shawn sends to accounts through Gmail. There is no backend, no account and no server: every piece of data lives in on-device storage. The app is installed from Android Studio on a Mac mini and is never published to the Play Store. It has two UI surfaces, the phone UI and an Android Auto screen, over one shared set of logic.

Reliable automatic trip start is the number one requirement. Most of section 10 exists because of it.

---

## 2. The stack (and why)

| Layer | Technology | Why this one |
|---|---|---|
| App framework | Native Android, one Gradle module `:app` | The app depends on CompanionDeviceManager, foreground services and Android Auto, which are platform APIs |
| Language | Kotlin 2.4.20, warnings are errors | Compile-time safety |
| Styling / design system | Jetpack Compose with Material 3; tokens in `core/designsystem/` | One UI toolkit, one set of tokens |
| Navigation/routing | Navigation 3 | Navigation 2 is in maintenance mode |
| Screen state | ViewModels, coroutines, Flow | No global store |
| Server state | None | There is no server |
| Backend / BaaS | None | One user, one phone |
| Database | Room 3 on bundled SQLite; DataStore Preferences for settings | On-device storage only |
| Auth | None | See section 7 |
| File/media storage | App-private storage on the phone | The PDF and CSV leave only when Shawn sends them |
| Background work | A `location` foreground service during a trip; WorkManager for jobs that can wait | WorkManager is never used to start a trip (section 10) |
| Location | Fused location provider (Play services) | |
| Android Auto | Car App Library (`androidx.car.app`) | The in-truck screen, and `CarConnection` |
| Dependency injection | Manual: an `AppContainer` created by the `Application` class | No Hilt |
| Hosting / build | No hosting. Gradle 9.8.0 with AGP 9.4.1; CI on GitHub Actions | |
| Error tracking | In-app event log (phase 1) | No Sentry |
| Analytics | None | |

Versions, sources and the reason for each omission are in `docs/adr/ADR-001-stack-selection.md`. Live pins are in `gradle/libs.versions.toml`.

---

## 3. High-level architecture

```
[ Composable screens ]            [ Android Auto screen ]
      phone UI                        Car App Library
          |                                 |
   [ ViewModels ]                           |  no ViewModel: it reads the shared
          |                                 |  trip state directly (ADR-002)
          +---------------+-----------------+
                          |
           +--------------+-------------+
           |                            |
   [ repositories ]            [ platform classes ]
        data/                       platform/
           |                            |
        Room                        Bluetooth
        DataStore                   CompanionDeviceManager
                                    fused location
                                    Geocoder
                                    Gmail intent
```

**The hard rule:** Composable → ViewModel → repository or platform class. Composables never call system services or DAOs directly. `data/` is the only layer that touches storage. `platform/` is the only layer that touches Android system services. This keeps the trip rules testable on the JVM and stops the two UI surfaces from growing separate logic.

The rule names Composables, and the Android Auto screen is not one. A Car App Library screen has its own lifecycle; the research has it collect one app-wide trip state and redraw when that changes, with no ViewModel in between (`docs/research/2026-10-03-android-auto-screen.md`, finding 26). That shared state is the app-wide `TripController` in `platform/trip/`, decided in ADR-002. Phone screens reach it through their ViewModels; the car screen reads it directly.

The system also starts the app when no screen is open: a Bluetooth connect, a reboot, an app update. Those entry points deal with Bluetooth, the companion device and location, so they belong in `platform/`. How they drive trip start and trip end is set out in `docs/adr/ADR-002-trip-detection.md`: every entry point calls one idempotent function on the `TripController`, and the trip rules are a pure Kotlin state machine in `core/trip/`.

---

## 4. Surface split (phone UI / Android Auto screen)

MilO has one platform and two UI surfaces. Both show the same trips and drive the same trip logic.

| Concern | Phone UI | Android Auto screen | Shared? |
|---|---|---|---|
| UI layer | Jetpack Compose screens, Material 3 | Car App Library screen, projected from the phone | No — surface-specific |
| What it shows | Everything: trips, settings, checklist, event log, reports | Tracking status, current trip km and duration, today's session count and total km | — |
| Manual trip control | Start/Stop button | Start Trip / End Trip | **Yes — both drive the same trip logic** |
| Trip rules, distance, formatting | | | **Yes** |
| Storage access | | | **Yes — repositories in `data/`** |
| System services | | | **Yes — `platform/`** |
| Auth | | | None on either (section 7) |

**Rule:** everything that isn't presentation is shared. Two copies of trip logic *will* drift. (See STANDARDS §9.)

The shared trip logic is placed by ADR-002: the pure rules (state machine, distance, point filter) in `core/trip/`, and the `TripController`, the foreground service and location recording in `platform/trip/`. The Car App Library classes live in `platform/car/`.

---

## 5. Folder structure

One Gradle module, `:app`. Packages under `com.shawnkowalchuk.milo`:

```
app/                 # navigation host and the AppContainer
feature/<name>/      # one package per feature: its Composable screens, its ViewModel,
                     #   its feature-only logic
core/designsystem/   # theme tokens (colour, spacing, typography, shape) and the shared
                     #   base components
core/util/           # pure Kotlin helpers with unit tests (distance, time, formatting)
data/                # Room database, DAOs, repositories, DataStore:
                     #   the only layer that touches storage
platform/            # the only layer that touches Android system services: Bluetooth,
                     #   companion device, location, notifications, audio, Android Auto
```

The Android entry points (`MainActivity`, and the `Application` class when it arrives) live in `app/`. No class sits in the root package. Features never import from each other. Shared code moves to `core/` or `data/`. Kotlin files are PascalCase and named after their main class. No file over about 300 lines, no Composable over about 200. (STANDARDS §3.)

---

## 6. Data model (overview)

What the app will store, at the level of shape only. Nothing here is built. Entity names, tables and columns are not decided: they are settled in the phase that builds each one, and the fields below are read off the required behaviour in APP_ENCYCLOPEDIA.md.

| Stored data | Key fields | Relationships |
|---|---|---|
| Trips | start and end time, start and end location and address, distance in km, Business or Personal, manual or edited flag | Each trip has many raw GPS points |
| Raw GPS points | trip id, time, latitude, longitude, accuracy | Each belongs to one trip. **Stored in a separate database file** |
| Event log | time, event type, detail | None |
| Monthly submission status | month, submitted date, whether it is a revision | Covers the Business trips of one month |
| Settings (DataStore, not a table) | truck device, grace period, minimum trip distance, trip-start sound, schedule, report header fields, reminder day | None |

**Two database files.** Raw GPS points live in their own database file, kept out of cloud backup. Android Auto Backup is capped at 25 MB per app and is all-or-nothing: over the cap nothing is backed up, and no error is shown. By the research estimate, points recorded every few seconds would cross the cap within about a year and take the small, valuable trip data down with them. Backup is switched off entirely until phase 4 writes the backup rules. Source for the split and the cap: `docs/research/2026-10-03-pdf-email-backup.md`.

One consequence is an inference, not a research finding: a raw point can refer to its trip only by id, because a SQLite foreign key cannot point into another database file. If that holds, deleting a trip has to delete its points in code. It is checked when the two databases are built.

---

## 7. Authentication & authorization flow

**There is none.** MilO has one user, no accounts, no sign-in, no roles and no server to authenticate to. Nothing in the app stores a credential or a token. Whoever holds the unlocked phone can use the app; the phone's screen lock is the only access control.

Android runtime permissions (location, Bluetooth, notifications) are not authentication. They are covered by the Permission checklist entry in APP_ENCYCLOPEDIA.md.

---

## 8. External integrations

None of these is a service of our own. Each is a system or Google component already on the phone, except GitHub, which the app never talks to.

| Service | Used for | Where it's wired | Notes |
|---|---|---|---|
| Bluetooth (system) | Detecting the truck connecting and disconnecting | `platform/` | Receivers must be exported (section 10) |
| CompanionDeviceManager (system) | Association with the truck; the system wakes the app on presence | `platform/` | Behaviour on HyperOS is untested |
| Fused location (Play services) | GPS fixes during a trip | `platform/` | |
| Geocoder (system) | Start and end addresses | `platform/` | Needs network and has no availability guarantee, so a failed lookup is retried later |
| Android Auto (Car App Library) | The in-truck screen; `CarConnection` keeps a trip open | `platform/` | Distribution risk (section 10) |
| Activity recognition (Play services) | The phase 2 driving alert | `platform/` | Alert only. It never starts a trip |
| Gmail | Sending the monthly PDF | `platform/` | An intent opens the draft and Shawn taps send. The app cannot learn whether it was sent |
| Android Auto Backup | Off-phone copy of the main database | Manifest backup rules | Off until phase 4. 25 MB cap (section 6) |
| GitHub | Private repo, CI, Dependabot updates, and Dependabot alerts fed by the dependency graph workflow | `.github/` | Development only |

No Sentry, no analytics, no API keys.

---

## 9. Environments & deployment

| Environment | Backend project | Used for | URL / build channel |
|---|---|---|---|
| The phone | None | Everything: development, testing and Shawn's real trips | Debug build, standard debug keystore, installed from Android Studio or with `./gradlew installDebug` |

There is one environment because there is no backend to separate (STANDARDS §13). The build on the phone holds real trip data, so an uninstall is data loss.

Build and release path: a pull request runs CI (gitleaks, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`). After merge the build is installed on the phone, and the device test checklist (`docs/DEVICE_TEST_CHECKLIST.md`, written in phase 1) is run before a phase is handed over. GitHub Free cannot block a merge on a red build in a private repo, so not merging red is a rule Shawn follows by hand.

---

## 10. Known constraints & decisions

Load-bearing facts from the research in `docs/research/`. Those files are dated evidence and are not kept current. Nothing below has been tested on this phone yet. The detailed trip-detection design is in `docs/adr/ADR-002-trip-detection.md`. Stack decisions are in ADR-001.

**Automatic trip start**
- Background location ("Allow all the time") is mandatory for automatic start. On Android 14 and later, a `location` foreground service started from the background without it throws `SecurityException`. No background-start exemption replaces it. (`2026-10-03-fgs-background-start.md`)
- Triggers must be redundant and must all feed one idempotent start function. CompanionDeviceManager presence and the Bluetooth ACL broadcast come from the same event in the Bluetooth stack, so they are two delivery paths, not two detectors, and each has gaps. (`2026-10-03-cdm-presence.md`)
- No event arrives when the truck is already connected at boot, after an app update or after the process restarts. The Bluetooth broadcast also never reaches a force-stopped app, or a phone not yet unlocked after a reboot. The app must check the real connection state at those moments. (`2026-10-03-fgs-background-start.md`)
- Trip logic must be level-triggered, not edge-triggered. Connect and disconnect events can be dropped, duplicated or delivered in reverse order, so each event is a prompt to re-read the real state, never the state itself. (`2026-10-03-fgs-background-start.md`, `2026-10-03-cdm-presence.md`)
- Receivers for Bluetooth broadcasts must be exported: `android:exported="true"` in the manifest, `RECEIVER_EXPORTED` when registered in code. The sender is the Bluetooth process, not the system. A non-exported receiver never gets the broadcast, and a trip never ends. (`2026-10-03-fgs-background-start.md`)
- The start must run directly from the trigger, with no WorkManager or delayed hop. The start allowance that comes with a Bluetooth or boot broadcast lasts about 20 seconds. (`2026-10-03-fgs-background-start.md`)

**This phone**
- HyperOS background limits: starting a dead app is gated by Autostart, which is off by default for a sideloaded app. With it off, decompiled system code shows manifest broadcasts dropped and service starts and binds rejected, which would block both the Bluetooth receiver and the CompanionDeviceManager path. Battery saver "No restrictions" is a second, separate gate. This rests on decompiled code and forum reports, so the with and without Autostart test on the POCO X5 decides it. (`2026-10-03-miui-background-limits.md`, `2026-10-03-cdm-presence.md`)
- Installing from Android Studio needs two Xiaomi-only developer switches ("Install via USB" and "USB debugging (Security settings)") and a confirmation on the phone at each install. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**Android Auto**
- Distribution risk: Google's documentation says the "Unknown sources" developer option does not apply to Car App Library apps, which must come from a trusted store to show on a real head unit. A sideloaded MilO should appear in the desktop head-unit emulator but may not appear in the truck. **Unverified on the truck and still being researched;** community reports conflict. (`2026-10-03-android-auto-screen.md`, `2026-10-03-location-and-car.md`)
- `CarConnection` only reports while the app is already running. It can hold a trip open but cannot start one. (`2026-10-03-location-and-car.md`)

**Data safety**
- Auto Backup cap: 25 MB per app, all-or-nothing, silent when exceeded. This is why raw GPS points live in a separate database file (section 6). (`2026-10-03-pdf-email-backup.md`)
- Signing-key risk: the debug keystore is generated per Mac. A build signed with a different key cannot update the installed app. The only way forward is an uninstall, which deletes every trip, and Auto Backup then refuses to restore. A manual export is the only copy that does not depend on the key. (`2026-10-03-pdf-email-backup.md`)

**Recording**
- A fused location request combines interval and distance as AND, so "every 5 seconds or 10 m" cannot be asked for. The request is a fix every 5 seconds, and the 10 m rule is applied in MilO's own distance calculation. (`2026-10-03-location-and-car.md`)

**Build**
- AGP 9.4.1 with Gradle 9.8.0 and Kotlin 2.4.20 is one step past what JetBrains documents. The set builds from the terminal on this Mac (2026-10-03) with no fallback. Gradle prints one deprecation notice, caused by AGP's own code (FINDINGS_LOG, 2026-10-03). The fallback and the dev-machine requirements are in ADR-001. (`2026-10-03-versions.md`)
