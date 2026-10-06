# Architecture

> **What this is.** The *map* of the system — its shape, the stack, how data flows, and how the pieces connect. Read this before building anything that touches structure. Update it whenever the structure changes. It should always answer: *"if I drop a new developer (or AI assistant) in, what do they need to know to not break things?"*
>
> **Status:** Living document · **Last updated:** 2026-10-03 · **See also:** ENGINEERING_STANDARDS.md (the rules), APP_ENCYCLOPEDIA.md (how each feature works)

The project skeleton and the phase 1 foundation are built: the Gradle build with its quality gates, the design system in `core/designsystem/`, a placeholder home screen, the CI files, the two databases, the settings store and the crash files in `data/`, the trip rules as pure Kotlin in `core/trip/`, and crash and kill capture in `platform/diagnostics/`. **Nothing starts or records a trip yet:** there is no permission, service, receiver or trip screen, and nothing calls the trip rules outside their tests. The rest of this document describes the structure the code must follow and the constraints already known from research. Required behaviour is in APP_ENCYCLOPEDIA.md.

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
        crash files                 fused location
                                    Geocoder
                                    Gmail intent
```

**The hard rule:** Composable → ViewModel → repository or platform class. Composables never call system services or DAOs directly. `data/` is the only layer that touches storage. `platform/` is the only layer that touches Android system services. This keeps the trip rules testable on the JVM and stops the two UI surfaces from growing separate logic.

The rule names Composables, and the Android Auto screen is not one. A Car App Library screen has its own lifecycle; the research has it collect one app-wide trip state and redraw when that changes, with no ViewModel in between (`docs/research/2026-10-03-android-auto-screen.md`, finding 26). That shared state is the app-wide `TripController` in `platform/trip/`, decided in ADR-002. Phone screens reach it through their ViewModels; the car screen reads it directly.

The system also starts the app when no screen is open: a Bluetooth connect, a reboot, an app update. Those entry points deal with Bluetooth, the companion device and location, so they belong in `platform/`. How they drive trip start and trip end is set out in `docs/adr/ADR-002-trip-detection.md`: every entry point calls one idempotent function on the `TripController`, and the trip rules are a pure Kotlin state machine in `core/trip/`. The state machine exists; the `TripController` and the entry points do not yet.

**How the shared objects are made.** `app/MiloApplication` is the first code to run in the process, however it was started. It creates one `app/AppContainer`, which builds the databases, the repositories and the settings store (each lazily, on first use) and owns the application-wide coroutine scope. The container decides nothing about storage: it calls the `build...` functions in `data/`, which own every file name and folder. Everything else is handed what it needs through its constructor. There is no Hilt and no global singleton: to see what a class depends on, read its constructor; to see what it is given, read `AppContainer`.

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
app/                 # MiloApplication, the AppContainer, MainActivity, the navigation host
feature/<name>/      # one package per feature: its Composable screens, its ViewModel,
                     #   its feature-only logic
core/designsystem/   # theme tokens (colour, spacing, typography, shape) and the shared
                     #   base components
core/trip/           # the trip rules: state machine, point filter, distance, trip closing.
                     #   Pure Kotlin, no Android imports, unit tested (ADR-002)
core/util/           # pure Kotlin helpers with unit tests (time, formatting)
data/                # the only layer that touches storage. The two Room databases, and one
                     #   sub-package per kind of data, each with its entity, DAO and repository:
                     #   trip/, point/, eventlog/, settings/ (DataStore), crash/ (crash files)
platform/            # the only layer that touches Android system services: Bluetooth,
                     #   companion device, location, notifications, audio, Android Auto
platform/diagnostics/  # crash and kill capture into the event log
```

The other `platform/` sub-packages named in ADR-002 (`trip/`, `bluetooth/`, `car/`, `system/`) arrive with the code that fills them.

The Android entry points (`MiloApplication` and `MainActivity`) live in `app/`. No class sits in the root package. Features never import from each other. Shared code moves to `core/` or `data/`. Kotlin files are PascalCase and named after their main class. No file over about 300 lines, no Composable over about 200. (STANDARDS §3.)

---

## 6. Data model

What phase 1 stores is built and described here exactly. Later phases add to it, each change as a Room migration: the phone holds real trips from phase 1 on, so no table is ever dropped and rebuilt. The source of truth for the tables is the schema Room exports to `app/schemas/` at every build; those files are committed.

Conventions that hold everywhere: times are wall-clock milliseconds since 1970 unless a column says otherwise; distances are metres (kilometres exist only on screen); a column that holds one of a fixed set of values stores the Kotlin enum's name as text, so a constant can be added freely but never renamed without a migration.

### Main database: `milo.db` (`data/MiloDatabase`, version 1)

**`trips`** (`data/trip/Trip`): one row per trip, open or closed.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `startedAtMs` | integer | When the trip started |
| `endedAtMs` | integer, null while open | When it ended: the time of its last recorded point |
| `status` | text | `OPEN`, `FINISHED`, or `DISCARDED` (under the minimum distance; the row is kept, not deleted) |
| `startedBy` | text | `TRUCK` or `MANUAL` |
| `truckSeen` | integer 0/1 | Whether the truck was connected at any point during the trip. A manual trip the truck never joined ends by different rules |
| `graceStartedAtMs` | integer, null | When the truck was found gone. Null while something holds the trip open |
| `graceDeadlineMs` | integer, null | When the grace period runs out. Set and cleared together with the column above |
| `distanceMetres` | real | Written when the trip closes; 0 while open |
| `startLatitude`, `startLongitude`, `endLatitude`, `endLongitude` | real, null | Written when the trip closes; null if no usable GPS fix was recorded |

At most one trip is `OPEN`. The repository enforces it: starting a trip while one is open returns the open one. Not here yet, and added by the phase that builds each: Business or Personal, addresses, the manual or edited flag.

**`event_log`** (`data/eventlog/EventLogEntry`), indexed on `atMs`.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `atMs` | integer | When the event happened. A crash or a kill is written at the next start but dated when it happened, so the log is ordered by this column |
| `category` | text | `PROCESS`, `CRASH`, `ERROR` (a failure that was caught), `TRIGGER`, `SERVICE`, `GRACE`, `ANDROID_AUTO`, `TRIP`. Only the first three are written so far |
| `message` | text | One short line |
| `detail` | text, null | Anything longer, such as a stack trace |

### Raw points database: `points.db` (`data/PointsDatabase`, version 1)

**`raw_points`** (`data/point/RawPoint`), indexed on `tripId`. Every fix is stored, including the ones the distance calculation rejects.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned in the order fixes arrive; the order points are read back in |
| `tripId` | integer | The trip the fix belongs to. Not a foreign key (see below) |
| `wallClockMs` | integer | Time of day of the fix |
| `elapsedRealtimeMs` | integer | Time since the phone booted. It never jumps, so it is the clock used for the speed between two fixes |
| `latitude`, `longitude` | real | Degrees |
| `accuracyMetres` | real, null | The phone's 68 % accuracy radius; null if it gave none |
| `speedMetresPerSecond` | real, null | The phone's own speed reading; null if it gave none. Stored for later tuning, not used yet |

### Settings: DataStore Preferences file `settings` (`data/settings/SettingsStore`)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `truck_address`, `truck_name`, `truck_association_id` | text, text, integer | none | The paired truck and its companion device association. Written and cleared together |
| `grace_period_seconds` | integer | 120 | How long a trip waits after the truck disconnects |
| `minimum_trip_distance_metres` | integer | 300 | A shorter trip is discarded |
| `sound_enabled` | boolean | true | Whether the trip-start sound plays |
| `custom_sound_uri` | text | none | The audio file Shawn chose; absent means the bundled chirp |
| `auto_start_held_off` | boolean | false | ADR-002's hold-off: set when a trip is ended by hand with the truck still connected, cleared when the truck is next seen disconnected |
| `last_process_exit_imported_at_ms` | integer | 0 | The newest process-exit record already copied into the event log |

The last two are not settings Shawn chooses. They are small pieces of state that must outlive the process.

The store is built by `buildSettingsStore` in the same package, with no corruption handler: an unreadable file makes every read throw, and is never replaced by empty settings (that would drop the truck pairing without a trace).

### Crash files: `no_backup/crashes/` (`data/crash/CrashFileStore`)

One small text file per uncaught exception, named `crash-<time>.txt`: the time, the thread, a one-line summary and the stack trace. Written while the process dies, copied into `event_log` at the next start and then deleted. At most 20 wait at once. The folder is under the app's no-backup files, so Android never backs it up or transfers it.

### Not built yet

| Stored data | Key fields | Phase |
|---|---|---|
| Monthly submission status | month, submitted date, whether it is a revision | 3 |
| More settings | schedule, report header fields, reminder day | 2 to 4 |

### Two database files

Raw GPS points live in their own database file, to be kept out of cloud backup. Android Auto Backup is capped at 25 MB per app and is all-or-nothing: over the cap nothing is backed up, and no error is shown. By the research estimate, points recorded every few seconds would cross the cap within about a year and take the small, valuable trip data down with them. Backup is switched off entirely until phase 4 writes the backup rules. Source for the split and the cap: `docs/research/2026-10-03-pdf-email-backup.md`.

Both files are in the app's standard databases folder. Room keeps three more files beside a database, seen on the emulator for `milo.db`: `-wal`, `-shm` and `.lck`. For the points database that means `points.db-wal`, `points.db-shm` and `points.db.lck`, and phase 4's backup rules must name the points database together with them.

**A raw point refers to its trip by id only.** SQLite cannot enforce a foreign key across database files, so `tripId` is a plain indexed column. Nothing deletes a trip today (a short trip is marked `DISCARDED`). Any later code that does delete one must delete its points itself, through `RawPointRepository`.

**Repositories are the only way in.** `TripRepository`, `RawPointRepository`, `EventLogRepository`, `SettingsStore` and `CrashFileStore` are the API the rest of the app uses; the DAOs, the DataStore and the files are not touched, or even located, from outside `data/`. Every write to a trip is safe to repeat, and a write to a trip that is no longer open changes nothing.

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

Build and release path: a pull request runs CI (gitleaks, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`). After merge the build is installed on the phone, and the device test checklist (`docs/DEVICE_TEST_CHECKLIST.md`, which grows with each work package) is run before a phase is handed over. GitHub Free cannot block a merge on a red build in a private repo, so not merging red is a rule Shawn follows by hand.

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
