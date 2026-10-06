# Architecture

> **What this is.** The *map* of the system — its shape, the stack, how data flows, and how the pieces connect. Read this before building anything that touches structure. Update it whenever the structure changes. It should always answer: *"if I drop a new developer (or AI assistant) in, what do they need to know to not break things?"*
>
> **Status:** Living document · **Last updated:** 2026-10-05 · **See also:** ENGINEERING_STANDARDS.md (the rules), APP_ENCYCLOPEDIA.md (how each feature works)

Built so far: the Gradle build with its quality gates, the design system in `core/designsystem/`, the CI files, the two databases, the settings store and the crash files in `data/`, the trip rules as pure Kotlin in `core/trip/`, crash and kill capture in `platform/diagnostics/`, the recording core in `platform/trip/` (the `TripController`, the foreground `TripService`, GPS recording, the two notifications, the trip-start sound and the watch on Android Auto), and the triggers in `platform/bluetooth/`: the Bluetooth receiver, the companion service, the boot and update receiver, the reading of the truck's connection, and pairing (`TruckPairing`, which the pairing screen calls). The phone UI has five screens behind a bottom navigation bar (`app/MiloNavigation.kt`): Home (the trip in progress, one Start trip / End trip button, and a warning while setup is incomplete), Trips (one month at a time), Setup (the permission checklist, in `feature/setup/` with its rules in `platform/system/`), the truck pairing screen opened from Setup, and Log (the event log). **The triggers have never met a real Bluetooth connection or this phone:** they are covered by unit tests, and in part by an emulator with no truck. **The screens other than Home have never been drawn anywhere:** they were built and proven with the build and unit tests only (`docs/DEVICE_TEST_CHECKLIST.md`, checks 52 to 79). The rest of this document describes the structure the code must follow and the constraints already known from research. Required behaviour is in APP_ENCYCLOPEDIA.md.

---

## 1. System overview

MilO is a native Kotlin Android app for one person on one phone: Shawn's Xiaomi POCO X5 Pro 5G (Android 14, HyperOS 2.0). It notices when the phone connects to the work truck over Bluetooth, records the drive with GPS in a foreground service, stores the trip on the phone, and produces a monthly PDF that Shawn sends to accounts through Gmail. There is no backend, no account and no server: every piece of data lives in on-device storage. The app is installed from Android Studio on a Mac mini and is never published to the Play Store. It has two UI surfaces, the phone UI and an Android Auto screen, over one shared set of logic.

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

The rule names Composables, and the Android Auto screen is not one. A Car App Library screen has its own lifecycle; the research has it collect one app-wide trip state and redraw when that changes, with no ViewModel in between (`docs/research/2026-10-03-android-auto-screen.md`, finding 26). That shared state is the app-wide `TripController` in `platform/trip/`, decided in ADR-002: its `activity` flow says what trip is in progress and why the last start failed. Phone screens reach it through their ViewModels; the car screen will read it directly.

The system also starts the app when no screen is open: a Bluetooth connect, a reboot, an app update. Those entry points deal with Bluetooth, the companion device and location, so they belong in `platform/`. How they drive trip start and trip end is set out in `docs/adr/ADR-002-trip-detection.md`: every entry point calls one function, `TripController.onTrigger`, and the trip rules are a pure Kotlin state machine in `core/trip/`. The entry points:

| Entry point | Class | What it tells the controller |
|---|---|---|
| The truck's link going up or down; its hands-free or audio profile changing state | `platform/bluetooth/TruckBluetoothReceiver` (in the manifest, and registered again by the trip service during a trip) | "Link connected" or "disconnected" for the classic link; "read the truck" for everything else |
| Android binding the companion service | `platform/bluetooth/TruckCompanionService` | "Appeared" (a start that must be confirmed), "disconnected", and "read the truck" each time the service is created |
| Boot, and an update of MilO | `platform/bluetooth/TruckReconcileReceiver` | "Read the truck" |
| Process start | `app/MiloApplication` | "Read the truck" |
| MilO coming to the front | `app/MainActivity.onStart` | "Read the truck" |
| Start and End | `feature/home/HomeViewModel` | The press |
| A truck picked on the pairing screen | `platform/bluetooth/TruckPairing`, called by `feature/pairing/PairingViewModel` | "Read the truck", once the truck is stored |

A broadcast or a callback names a device, and the receiver decides on the spot whether it is the truck (`platform/bluetooth/TruckSignals`, plain functions). That needs the truck's address, which is in the settings file, so `PairedTruck` reads it while `onReceive` waits, for one second at most. "Read the truck" is `TruckConnectionSource.read()`, which answers connected, not connected or unknown, with how it found out.

**How a trip is recorded.** Three objects, each with one job:

```
 any thread                    one worker coroutine                 main thread
[ a trigger ] -> TripController.onTrigger -> inbox -> TripWorker    [ TripService ]
                        |                               |  asks the trip rules (core/trip)
                        |                               |  writes storage and the event log
                        |  recording must begin         |  then tells the service and the screens
                        +--> start the service ---------+------> startForeground, then hands the
                             (preflight first)                    trigger back to the controller
```

- **`TripController`** takes everything through one inbox, worked off by one coroutine, so two triggers on different threads cannot interleave. It decides nothing itself: `TripStateMachine` does. A manifest receiver keeps its broadcast open until the inbox has been worked off as far as its trigger (`whenCaughtUp`), so Android cannot freeze the process in between.
- **The service comes first.** A trip is opened or carried on only while `TripService` is in the foreground. An event that would leave a trip open while the service is down is not acted on; the service is asked to start with the trigger in its intent, and hands it back once `startForeground` has succeeded. If Android refuses, no trip row exists, and the "could not start this trip" notification is posted.
- **`TripService`** holds what only makes sense during a trip: the GPS fixes, the timers (grace period, confirmation, the once-a-minute reading of the truck), the watch on Android Auto, the notification and the sound. It reports to the controller and does what it is told. It decides nothing.
- **A trip that nothing records is not kept in memory.** If the service is lost and Android will not start it again, or storage fails in mid-event, the worker drops what it holds. The open trip row is the truth, and the next look at storage picks it up or closes it.
- **Every GPS fix goes through the controller's inbox too**, so fixes are stored in arrival order under the trip that is open at that moment.

**How the shared objects are made.** `app/MiloApplication` is the first code to run in the process, however it was started. It creates one `app/AppContainer`, which builds the databases, the repositories, the settings store, the trip controller, the setup checklist and the opener of settings screens (each lazily, on first use) and owns the application-wide coroutine scope. A screen's ViewModel is given what it needs from the container in `app/MiloNavigation`, so a feature never imports the `app` package. The container decides nothing about storage: it calls the `build...` functions in `data/`, which own every file name and folder. Everything else is handed what it needs through its constructor. There is no Hilt and no global singleton: to see what a class depends on, read its constructor; to see what it is given, read `AppContainer`.

**How the phone screens are reached.** Navigation 3. `app/MiloApp` holds the back stack and frames every screen with the bottom bar; `app/MiloNavigation` shows the screen on top of the back stack and is the only code that knows more than one feature.

```
[ MiloNavigationBar ]   Home | Trips | Setup | Log        (core/designsystem)
        |
   back stack:   [ Home ]                    Home alone, or
                 [ Home, Trips | Setup | Log ]   one bar screen on top of it, or
                 [ Home, Setup, Pairing ]        the pairing screen, opened from Setup
```

- Each screen is a `@Serializable` key (`HomeKey`, `TripsKey`, `SetupKey`, `LogKey`, `PairingKey`), because Navigation 3 saves the back stack with kotlinx.serialization when Android puts MilO away.
- Home is always at the bottom. Pressing a button of the bar leaves Home alone, or Home with that screen on top; whatever was open above is closed. So Back from Trips, Setup or Log leads to Home, and Back from Home leaves MilO.
- A screen that leads to another one (Home to Setup, Setup to pairing) is handed a plain function to call. Features never import each other.
- A screen's ViewModel lives as long as the screen is on the back stack, and is created with what it needs from the `AppContainer`. Leaving Trips and coming back therefore opens the current month again.
- Back closes the screen on top and never the last one: Navigation 3 throws on an empty back stack, and the process that would die is the one the trip service runs in (`closeTop`). A screen's own Back arrow closes that screen only while it is on top (`closeIfOnTop`), because a screen that is closing stays on the display, arrow included, for the length of the transition.
- **A screen that shows something Android does not report changes of** (permissions, settings, the phone's paired devices, which month is the current one) **reads it again every time it comes to the front,** with the shared `CameToFrontEffect` (`core/designsystem/component/`). It acts on two signs. The screen resumes: Navigation 3 gives each screen a lifecycle of its own, so that happens when the screen is entered and whenever MilO returns from a settings screen or a system dialog. Or MilO's window gets the focus back: the quick settings panel and the notification shade cover MilO without pausing it, so nothing resumes when they close. The pairing screen has a third sign of its own, Android's broadcast that Bluetooth has finished switching on or off (`platform/bluetooth/BluetoothSwitch.kt`), because that happens a second or two after Shawn is back.

**The setup checklist is shared, like the trip state.** `platform/system/SetupChecklist` holds the rows of the checklist as one flow. The Setup screen shows the rows; the home screen only asks `needsAttention` of them. What the phone reports is read when a screen asks; Shawn's confirmations (the settings store) and the truck's pairing (`TruckPairing.status`) arrive by themselves. The five facts that stop a trip from being recorded are read by `TripPreflight.facts()`, which the trip service's starter also uses.

One exception, because Android gives no other way. The components Android creates itself have no constructor MilO can call: `TripService`, `TruckCompanionService`, `TruckBluetoothReceiver` and `TruckReconcileReceiver`. Each fetches the container from the application object (`(application as MiloApplication).container`). `TripNotifications` names `MainActivity` as the screen a tap on the trip notification opens. Those are the only places where `platform/` imports `app/`, and no other class may reach for the container this way.

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

The shared trip logic is placed by ADR-002: the pure rules (state machine, distance, point filter) in `core/trip/`, and the `TripController`, the foreground service and location recording in `platform/trip/`. The Car App Library classes live in `platform/car/`; so far that is the `CarConnection` watcher, and the screen is not built.

---

## 5. Folder structure

One Gradle module, `:app`. Packages under `com.shawnkowalchuk.milo`:

```
app/                 # MiloApplication, the AppContainer, MainActivity, the navigation host
feature/<name>/      # one package per feature: its Composable screens, its ViewModel,
                     #   its feature-only logic. Today: home/, trips/, setup/, pairing/,
                     #   eventlog/
core/designsystem/   # theme tokens (colour, spacing, typography, shape) and the shared
                     #   base components
core/trip/           # the trip rules: state machine, point filter, distance, trip closing.
                     #   Pure Kotlin, no Android imports, unit tested (ADR-002)
core/util/           # pure Kotlin helpers with unit tests: formatting of distances and
                     #   times, and a month as a span of stored time
data/                # the only layer that touches storage. The two Room databases, and one
                     #   sub-package per kind of data, each with its entity, DAO and repository:
                     #   trip/, point/, eventlog/, settings/ (DataStore), crash/ (crash files)
platform/            # the only layer that touches Android system services: Bluetooth,
                     #   companion device, location, notifications, audio, Android Auto
platform/trip/       # TripController, TripService, GPS recording, notifications, the sound
platform/bluetooth/  # the triggers: the Bluetooth receiver, the companion service, the boot and
                     #   update receiver; the reading "is the truck connected?"; pairing
platform/car/        # so far only the watch on Android Auto's connection
platform/system/     # what the phone's permissions and settings say: the preflight check
                     #   before the service is started, the setup checklist's facts, rules
                     #   and shared rows, and the opening of the phone's settings screens
platform/diagnostics/  # crash and kill capture into the event log
```

Outside the app module, `tools/` holds scripts that are run by hand and are not part of the build. Today there is one: the script that synthesises the trip-start sound.

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
| `status` | text | `OPEN`, `FINISHED`, or `DISCARDED`: under the minimum distance, or a companion start that was never confirmed (a false start, whatever its distance). The row is kept, not deleted. Which of the two it was is in the event log only, in the trip's "discarded" line |
| `startedBy` | text | `TRUCK` or `MANUAL` |
| `truckSeen` | integer 0/1 | Whether the truck was seen connected, by something that can be trusted, at any point during the trip. A manual trip the truck never joined ends by different rules. A `TRUCK` trip with 0 here was opened by the companion callback alone and is still waiting to be confirmed |
| `graceStartedAtMs` | integer, null | When the truck was found gone. Null while something holds the trip open |
| `graceDeadlineMs` | integer, null | When the grace period runs out. Set and cleared together with the column above |
| `distanceMetres` | real | Written when the trip closes; 0 while open |
| `startLatitude`, `startLongitude`, `endLatitude`, `endLongitude` | real, null | Written when the trip closes; null if no usable GPS fix was recorded |

At most one trip is `OPEN`. The repository enforces it: starting a trip while one is open returns the open one. The Trips screen reads the trips that started in a span of time (`observeTripsStartedBetween`). There is no index on `startedAtMs`, so that reads the whole table, which at a few thousand rows a year takes milliseconds; the index is to be added with the first migration. Not here yet, and added by the phase that builds each: Business or Personal, addresses, the manual or edited flag.

**`event_log`** (`data/eventlog/EventLogEntry`), indexed on `atMs`.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `atMs` | integer | When the event happened. A crash or a kill is written at the next start but dated when it happened, so the log is ordered by this column |
| `category` | text | `PROCESS`, `CRASH`, `ERROR` (a failure that was caught), `TRIGGER` (a trip trigger, with the state before and after in `detail`), `SERVICE`, `GRACE`, `ANDROID_AUTO`, `TRIP`, `LOCATION`, `PAIRING` |
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
| `truck_address`, `truck_name`, `truck_association_id` | text, text, integer | none | The paired truck and its companion device association. Written and cleared together, by `platform/bluetooth/TruckPairing`. The address is in capitals, the only form Android's Bluetooth classes accept. The id is absent on Android 12 and on a phone without companion device support |
| `grace_period_seconds` | integer | 120 | How long a trip waits after the truck disconnects |
| `minimum_trip_distance_metres` | integer | 300 | A shorter trip is discarded |
| `sound_enabled` | boolean | true | Whether the trip-start sound plays |
| `custom_sound_uri` | text | none | The audio file Shawn chose; absent means the bundled chirp |
| `auto_start_held_off_since_ms` | integer | none | ADR-002's hold-off: the time a trip was ended by hand with the truck still connected. Absent means not held off. The time is kept because two of the three things that release the hold-off are measured from it |
| `last_process_exit_imported_at_ms` | integer | 0 | The newest process-exit record already copied into the event log |
| `confirmed_xiaomi_autostart_at_ms`, `confirmed_xiaomi_battery_saver_at_ms`, `confirmed_xiaomi_other_permissions_at_ms`, `confirmed_xiaomi_recents_lock_at_ms` | integer each | none | When Shawn confirmed a step of the setup checklist that MilO cannot read. Absent means not confirmed. The key of each step is fixed on `ConfirmedStep` in `data/settings/MiloSettings.kt` |

The hold-off, the exit-record marker and the four confirmations are not settings Shawn chooses. They are small pieces of state that must outlive the process.

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
| Bluetooth (system) | Detecting the truck connecting and disconnecting; reading whether it is connected; hearing Bluetooth itself being switched on or off, for the pairing screen's list | `platform/bluetooth/` | Built, never run against a real connection. Receivers must be exported (section 10) |
| CompanionDeviceManager (system) | Association with the truck; the system wakes the app on presence | `platform/bluetooth/` | Built; ran on an emulator (Android 16). Behaviour on HyperOS is untested |
| Fused location (Play services) | GPS fixes during a trip | `platform/trip/LocationRecorder` | Built. A fix every 5 seconds, no cached first position |
| Geocoder (system) | Start and end addresses | `platform/` | Needs network and has no availability guarantee, so a failed lookup is retried later |
| Android Auto (Car App Library) | The in-truck screen; `CarConnection` keeps a trip open | `platform/car/` | `CarConnection` is built; the screen is not. Distribution risk (section 10) |
| Activity recognition (Play services) | The phase 2 driving alert | `platform/` | Alert only. It never starts a trip |
| Gmail | Sending the monthly PDF | `platform/` | An intent opens the draft and Shawn taps send. The app cannot learn whether it was sent |
| The phone's settings screens | The buttons of the setup checklist and the pairing screen | `platform/system/SystemScreens` | Android's own screens, and three HyperOS ones known only from other apps' source. Each is tried inside a try/catch and falls back on Android's page for MilO. Never run on the phone |
| HyperOS Autostart app-op | The "looks on / looks off" reading on the setup checklist | `platform/system/SetupFacts` | A hidden Android method reached by reflection with MIUI's app-op 10008. Advisory only; any failure reads as "unknown" |
| Android Auto Backup | Off-phone copy of the main database | Manifest backup rules | Off until phase 4. 25 MB cap (section 6) |
| GitHub | Private repo, CI, Dependabot updates, and Dependabot alerts fed by the dependency graph workflow | `.github/` | Development only |

No Sentry, no analytics, no API keys.

---

## 9. Environments & deployment

| Environment | Backend project | Used for | URL / build channel |
|---|---|---|---|
| The phone | None | Everything: development, testing and Shawn's real trips | Debug build signed with the dedicated key in `~/keys/milo.jks`, installed from Android Studio or with `./gradlew installDebug` |

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

- Android has no public "is this device connected" before Android 16 QPR2 (API 36.1). On this phone the reading asks the hands-free and the audio profile which devices they are connected to, through a profile proxy each. A truck connected on neither profile reads as "not connected" for as long as it is connected. So a reading of "not connected" is held against a trip only once some reading has shown the truck connected on its present link; until then only a disconnect event ends the trip. (`2026-10-03-cdm-presence.md`; ADR-002, amendment 18)
- A reading can be "unknown" (no Bluetooth permission, no answer). Unknown is never "connected", and it must never be turned into "not connected" for a trip that is recording. (ADR-002, amendment 11)
- A request to associate with the truck must go through an Activity: Android shows its consent dialog on it, and up to Android 12 the request fails from any other context. (FINDINGS_LOG, 2026-10-05)

**This phone**
- HyperOS background limits: starting a dead app is gated by Autostart, which is off by default for a sideloaded app. With it off, decompiled system code shows manifest broadcasts dropped and service starts and binds rejected, which would block both the Bluetooth receiver and the CompanionDeviceManager path. Battery saver "No restrictions" is a second, separate gate. This rests on decompiled code and forum reports, so the with and without Autostart test on the POCO X5 decides it. (`2026-10-03-miui-background-limits.md`, `2026-10-03-cdm-presence.md`)
- What MilO can know about the HyperOS settings is limited. Autostart can be read only through an unofficial app-op check that is known to say "on" wrongly; the per-app Battery saver profile, the "Other permissions" switches and the lock in recents cannot be read at all, so the setup checklist takes Shawn's word for them, with the date. Whether a HyperOS settings screen exists cannot be asked beforehand either (an app cannot see another app's screens without declaring it), so each is opened inside a try/catch with a fallback. (`2026-10-03-miui-background-limits.md`, findings 27 to 33)
- Installing from Android Studio needs two Xiaomi-only developer switches ("Install via USB" and "USB debugging (Security settings)") and a confirmation on the phone at each install. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**Android Auto**
- Distribution risk: Google's documentation says the "Unknown sources" developer option does not apply to Car App Library apps, which must come from a trusted store to show on a real head unit. A sideloaded MilO should appear in the desktop head-unit emulator but may not appear in the truck. **Unverified on the truck and still being researched;** community reports conflict. (`2026-10-03-android-auto-screen.md`, `2026-10-03-location-and-car.md`)
- `CarConnection` only reports while the app is already running. It can hold a trip open but cannot start one. (`2026-10-03-location-and-car.md`)

**Data safety**
- Auto Backup cap: 25 MB per app, all-or-nothing, silent when exceeded. This is why raw GPS points live in a separate database file (section 6). (`2026-10-03-pdf-email-backup.md`)
- Signing-key risk: every build for the phone is signed with one dedicated key (`~/keys/milo.jks`, password in the macOS Keychain). A build signed with any other key cannot update the installed app. The only way forward would be an uninstall, which deletes every trip, and Auto Backup then refuses to restore. So the keystore and its password must be backed up off the Mac, and a manual export is the only copy of the trips that does not depend on the key. (`2026-10-03-pdf-email-backup.md`)

**Recording**
- A fused location request combines interval and distance as AND, so "every 5 seconds or 10 m" cannot be asked for. The request is a fix every 5 seconds, and the 10 m rule is applied in MilO's own distance calculation. (`2026-10-03-location-and-car.md`)
- The timers of a trip run as coroutines inside the service, as the research recommends, and no wake lock is held. A coroutine timer does not count time the phone spends asleep, so a timer can fire late when the phone sleeps between GPS fixes. Two things limit the harm: a GPS fix stands in for a late timer (for a deadline once it is 10 seconds overdue, for the minute reading 70 seconds after the last one), and the trip rules judge a late reading by the stored deadline, not by when the timer fired. With no fixes arriving, nothing stands in. Not measured on the phone yet (DEVICE_TEST_CHECKLIST).
- Recording needs four permissions: precise location, "Allow all the time", Nearby devices (Bluetooth) and notifications. The Setup screen asks for each; none of its dialogs has been seen on the phone yet.

**Build**
- AGP 9.4.1 with Gradle 9.8.0 and Kotlin 2.4.20 is one step past what JetBrains documents. The set builds from the terminal on this Mac (2026-10-03) with no fallback. Gradle prints one deprecation notice, caused by AGP's own code (FINDINGS_LOG, 2026-10-03). The fallback and the dev-machine requirements are in ADR-001. (`2026-10-03-versions.md`)
