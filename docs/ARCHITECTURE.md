# Architecture

> **What this is.** The *map* of the system — its shape, the stack, how data flows, and how the pieces connect. Read this before building anything that touches structure. Update it whenever the structure changes. It should always answer: *"if I drop a new developer (or AI assistant) in, what do they need to know to not break things?"*
>
> **Status:** Living document · **Last updated:** 2026-10-10 · **See also:** ENGINEERING_STANDARDS.md (the rules), APP_ENCYCLOPEDIA.md (how each feature works)

Built so far: the Gradle build with its quality gates, the design system in `core/designsystem/`, the CI files, the two databases, the settings store and the crash files in `data/`, the trip rules as pure Kotlin in `core/trip/`, crash and kill capture in `platform/diagnostics/`, the recording core in `platform/trip/` (the `TripController`, the foreground `TripService`, GPS recording, the three notifications, the connect and trip-start sounds and the watch on Android Auto), and the triggers in `platform/bluetooth/`: the Bluetooth receiver, the companion service, the boot and update receiver, the reading of the truck's connection, and pairing (`TruckPairing`, which the pairing screen calls); and the lookup of each trip's start and end address in `platform/address/`. The phone UI has eight screens (`app/MiloNavigation.kt`, with their keys in `app/MiloBackStack.kt`), four of them behind a bottom navigation bar: Home (laid out since 2026-10-06 as the owner's design draws it: a tile that starts a trip, today's and the month's Business kilometres, the truck's connection, last month's report while it is not sent, the last trip, and while a trip is recorded its kilometres, the button that ends it and today's finished trips; with a warning while setup is incomplete), Trips (one month at a time, where a trip can be deleted and restored, marked Business or Personal, and opened for an edit), Setup (the permission checklist, in `feature/setup/` with its rules in `platform/system/`) and Log (the event log); and four opened from another screen: the truck pairing screen, from Setup and from Settings; Settings (`feature/settings/`: the truck, who the report for the accountant is from and where it goes, the three numbers of the trip rules, the work schedule and what becomes of a trip outside it, the driving alert's switch, the connect sound and the trip-start sound), from Home and from the Report screen; the edit screen (`feature/tripedit/`: a finished trip's times, addresses and distance changed by hand, or a trip MilO missed typed in), from Trips; and the Report screen (`feature/report/`: the report for the accountant for a month or a date range, as a PDF handed to the email app and as a CSV file), from Trips. **The triggers met the truck on 2026-10-06:** it was paired on the phone that morning, and three trips started and ended by themselves (FINDINGS_LOG, 2026-10-06 (evening)). **That evening the trip rules gained the parked rule** (ADR-002, amendments 28 to 30): a trip that is being recorded is closed where the truck last moved once it has stood still for a limit Shawn sets, even with Bluetooth still connected, and while the truck stays connected, by Bluetooth or by Android Auto, MilO waits beside it and starts the next trip when it moves. It added no table and no column (the main database is still version 5): the wait is kept in the settings file. It is covered by unit tests; its close ran on an emulator, and the wait ran there only in a throwaway build with the reading of the truck replaced. Nothing of it has run on the phone (checks 265 to 290). The finished work was verified the same night, before the second day in the truck: the start from a connect and the end on a disconnect were found unchanged, and one rule of the watch on a parked truck was changed (amendment 30). **Of the screens of work package 3, only Setup has been used on the phone,** where Shawn also started a trip with Home's button (FINDINGS_LOG, 2026-10-05, "First results from the phone"). The pairing screen has been drawn only on an emulator that has no Bluetooth device to list (2026-10-06), and nothing records the Log screen being drawn on the phone (it was drawn on an emulator with phase 4, part A); their checks are 52 to 79 of `docs/DEVICE_TEST_CHECKLIST.md`. Trips and the address lookup ran on an emulator, not on the phone (checks 80 to 86), and so did Settings, deleting and restoring a trip, and Home's list of today's trips (checks 101 to 127). The first piece of phase 2 is built as well (2026-10-06): the work schedule in `core/schedule/`, which sorts every trip into Business or Personal at the moment it is closed and never has a say in whether one starts, with its settings, the marking of a trip by hand and the Business and Personal figures on Trips and Home. It brought the main database to version 3. It ran on an emulator, the migration included. On the phone the conversion of the database from version 1 to version 3 ran without error on 2026-10-06, and the catch-up sorted the two test trips (FINDINGS_LOG, "The phone's own database converted cleanly"); the rest of it has not been seen there (checks 128 to 150). The second piece followed the same day: a finished trip can be edited and a missed trip added by hand, such trips are marked, and what MilO recorded of an edited trip is kept and can be put back. It brought the main database to version 4, and it too ran on an emulator, the migration included, and never on the phone (checks 153 to 171). The third piece is built as well (2026-10-06): the driving alert in `platform/driving/`, which posts a notification when the phone reports driving during the work hours with no trip being recorded and the paired truck not connected, and never starts a trip. Its rules run in unit tests and its registration with Play services ran on an emulator; the build as it stands has had no report of driving anywhere (checks DA-1 to DA-14). **Phase 3 followed the same day:** the report for the accountant, with its rules, its layout and its CSV as pure Kotlin in `core/report/`, the list of sent reports in `data/report/`, and the drawing of the PDF and the hand-over to the email app in `platform/report/`. Whether a month is submitted is worked out from that list. It brought the main database to version 5 (one new table), and it ran on an emulator, the migration included, and never on the phone; Gmail there had no account, so no email draft has ever been seen (checks 177 to 207). **Part A of phase 4 followed the same day:** the monthly reminder in `platform/reminder/` (a notification, decided by a pure function each time MilO looks, with one inexact alarm a day to make it look), the event log shared as a text file, filtered by category and trimmed at every process start, one rule for every total (`sumOfTenths` in `core/util/`, for the report and for every screen), a sent report that can be removed from the list, and two warnings around a month that is sent. It changed no table: the main database is still version 5. It ran on an emulator, over a database written by the build before it, and never on the phone (checks 208 to 231). **Part B followed the same day:** Android's backup switched on by rules of MilO's own, with a backup agent that only brings each database into one file, writes down a refused backup and writes down a restore (`platform/transfer/`); and "Export all data" and "Import data" on the Settings screen, one versioned JSON document written and read without ever being held in memory (`data/transfer/`). After a restore or an import the truck is paired only if Android on this phone holds its association. It changed no table either: the main database is still version 5. It ran on an emulator with Android's test transport, and never on the phone, where a restore cannot be tried at all without removing MilO (checks 232 to 252). **The wrap-up of the same day** added one more thing that is started with the process: an app-wide watch on Android Auto in `platform/car/` (`AndroidAutoLog`), which writes a line to the event log for each change of Android Auto's connection while no trip is being recorded, and can do nothing else. Until then Android Auto was watched only by the trip service, during a trip. It changed no table and nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/`. It ran on an emulator beside a stand-in for the Android Auto app, and never on the phone (checks 260 to 264). **The evening of the same day brought two more things Shawn had decided,** beside the parked rule above (FINDINGS_LOG, 2026-10-06 (evening, decisions)). The daily "nothing recorded" check in `platform/nothingrecorded/`: on a work day, if no trip has been started by a time he sets (noon out of the box), MilO posts one notification, whose tap opens Home; it is built like the monthly reminder, with a second inexact alarm a day, and can touch no trip. It changed no table, no permission and nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/`, and it ran on an emulator and never on the phone (checks NR-1 to NR-13; NR-14 is the library's). Its review added two things that have run in unit tests only: it looks a second time before it says "no trip", for a trip that is just starting, and it follows a change of the work schedule (NR-15, NR-16). And the Car App Library moved from 1.7.0 to the release candidate 1.8.0-rc01 for its security fix, the one exception to the stable-only rule (ADR-001); no code of MilO's changed for it. The Android Auto screen is built too, in `platform/car/`: a `CarAppService`, a session and one screen. **It has never run anywhere either,** and by Google's documentation a build installed from Android Studio is not expected to appear on a real truck (section 10; checks 88 to 100). **MilO has a clock of its own** (ADR-006; section 3, "How MilO tells the time"; built in the night of 2026-10-07 and changed on 2026-10-09 after its verification): Shawn sets the phone's date a day ahead for a few seconds, and until then every part of MilO took that date for real. One object now tells the time for all of MilO, and nothing else reads the phone's clock. It changed no table, no permission and nothing under `core/trip/`. It has run in unit tests only, never on the phone or an emulator (checks CJ-1 to CJ-13). The rest of this document describes the structure the code must follow and the constraints already known from research. Required behaviour is in APP_ENCYCLOPEDIA.md.

---

## 1. System overview

MilO is a native Kotlin Android app for one person on one phone: Shawn's Xiaomi POCO X5 Pro 5G (Android 14, HyperOS 2.0). It notices when the phone connects to the work truck over Bluetooth, records the drive with GPS in a foreground service, stores the trip on the phone, and produces a monthly PDF that Shawn sends to accounts through Gmail. There is no backend, no account and no server: every piece of data lives in on-device storage. The app is installed over USB from a Mac mini (from Android Studio by the brief; so far with adb from the terminal). Since 2026-10-08 anyone can download it as an APK from GitHub Releases, and since 2026-10-09 it is being prepared for Google Play as a second channel (ADR-004). It has two UI surfaces, the phone UI and an Android Auto screen, over one shared set of logic.

Reliable automatic trip start is the number one requirement. Most of section 10 exists because of it.

**Beside the app, a website** (since 2026-10-08, ADR-003): `milotriplog.top`, "MilO Trip Log", a landing page, a comparison, What's new and a privacy policy as plain files in `website/`, served by Firebase Hosting and deployed by `.github/workflows/website.yml` on a merge to `main`. It shares no code with the app, and the app does not talk to it or to Firebase.

---

## 2. The stack (and why)

| Layer | Technology | Why this one |
|---|---|---|
| App framework | Native Android, one Gradle module `:app` | The app depends on CompanionDeviceManager, foreground services and Android Auto, which are platform APIs |
| Android versions | Android 14 (API 34) and newer; built against API 37 | The one phone runs Android 14. Nothing older could be tested, so no code is written for it (FINDINGS_LOG, 2026-10-07) |
| Language | Kotlin 2.4.20, warnings are errors | Compile-time safety |
| Styling / design system | Jetpack Compose with Material 3; tokens in `core/designsystem/`. One look, always dark: the owner's "Bento" design (2026-10-06), set in its typeface, Sora, which the app carries as one font file | One UI toolkit, one set of tokens |
| Navigation/routing | Navigation 3 | Navigation 2 is in maintenance mode |
| Screen state | ViewModels, coroutines, Flow | No global store |
| Server state | None | There is no server |
| Backend / BaaS | None | One user, one phone |
| Database | Room 3 on bundled SQLite; DataStore Preferences for settings | On-device storage only |
| Auth | None | See section 7 |
| File/media storage | App-private storage on the phone | The PDF, the CSV and an export file leave only when Shawn sends or saves them; Android's own backup takes the main database and the settings (section 6) |
| Background work | A `location` foreground service during a trip, and while MilO waits beside a truck that is connected and parked. Two inexact alarms a day from Android's `AlarmManager`: one for the monthly reminder, one for the daily "nothing recorded" check. WorkManager is in the planned stack and is not in the build | Nothing that can wait is ever used to start a trip (section 10). No job has needed WorkManager so far: the address lookup, the reminder and the daily check all run on occasions MilO has anyway |
| Location | Fused location provider (Play services) | |
| Android Auto | Car App Library (`androidx.car.app`), on a release candidate since 2026-10-06 | The in-truck screen, and `CarConnection`. The release candidate is the one exception to "stable only", for a security fix (ADR-001) |
| Dependency injection | Manual: an `AppContainer` created by the `Application` class | No Hilt |
| Hosting / build | No hosting. Gradle 9.8.0 with AGP 9.4.1; CI on GitHub Actions. The release build goes through R8 since 2026-10-10 (ADR-008); the debug build does not | R8 removes unused code, rewrites the rest and renames it. MilO's own names are kept, so its event log stays readable (section 10, "Build") |
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

The rule names Composables, and the Android Auto screen is not one. A Car App Library screen has its own lifecycle; the research has it collect one app-wide trip state and redraw when that changes, with no ViewModel in between (`docs/research/2026-10-03-android-auto-screen.md`, finding 26). That shared state is the app-wide `TripController` in `platform/trip/`, decided in ADR-002: its `activity` flow says what trip is in progress, why the last start failed, and what the trip rules believe about the truck's connection. Phone screens reach it through their ViewModels; the car screen (`platform/car/TripStatusScreen`) reads it directly.

The system also starts the app when no screen is open: a Bluetooth connect, a reboot, an app update. Those entry points deal with Bluetooth, the companion device and location, so they belong in `platform/`. How they drive trip start and trip end is set out in `docs/adr/ADR-002-trip-detection.md`: every entry point calls one function, `TripController.onTrigger`, and the trip rules are a pure Kotlin state machine in `core/trip/`. The entry points:

| Entry point | Class | What it tells the controller |
|---|---|---|
| The truck's link going up or down; its hands-free or audio profile changing state | `platform/bluetooth/TruckBluetoothReceiver` (in the manifest, and registered again by the trip service during a trip) | "Link connected" or "disconnected" for the classic link; "read the truck" for everything else |
| Android binding the companion service | `platform/bluetooth/TruckCompanionService` | "Appeared" (a start that must be confirmed), "disconnected", and "read the truck" each time the service is created |
| Boot, and an update of MilO | `platform/bluetooth/TruckReconcileReceiver` | "Read the truck" |
| Process start | `app/MiloApplication` | "Read the truck" |
| MilO coming to the front | `app/MainActivity.onStart` | "Read the truck" |
| Start and End | `feature/home/HomeViewModel` | The press |
| Start trip and End trip on the car's display | `platform/car/TripStatusScreen` | The press, as the same two triggers |
| Start trip and End trip on the home-screen widget (since 2026-10-07) | Start: a `PendingIntent` for `TripService` built in `platform/widget/HomeWidgetViews`, as the tap on the "could not start" notification is. End: `platform/widget/HomeWidgetActionReceiver` | The press, as the same two triggers (ADR-002, amendment 35) |
| A truck picked on the pairing screen | `platform/bluetooth/TruckPairing`, called by `feature/pairing/PairingViewModel` | "Read the truck", once the truck is stored |

Five more things can start MilO's process and are deliberately not in this table, because none of them calls `TripController.onTrigger`: a report from the phone's driving detection (`DrivingReceiver`), the monthly reminder's daily alarm (`ReminderReceiver`), the daily alarm of the "nothing recorded" check (`NothingRecordedReceiver`), since 2026-10-07 the home screen asking for the widget to be drawn (`HomeWidgetProvider`, which only draws), and since 2026-10-07 (night) Android saying that the phone's clock was set (`ClockChangeReceiver`, which only has MilO's clock look). Each is described below. A process they start runs `MiloApplication` like any other, so the reconcile at process start happens then too. **Android's backup is one more, and of another kind:** when MilO is not running, Android starts a process for the backup or the restore in which `MiloApplication` is not used at all and the container does not exist ("How backup, export and import stand beside everything", below).

A broadcast or a callback names a device, and the receiver decides on the spot whether it is the truck (`platform/bluetooth/TruckSignals`, plain functions). That needs the truck's address, which is in the settings file, so `PairedTruck` reads it while `onReceive` waits, for one second at most. "Read the truck" is `TruckConnectionSource.read()`, which answers connected, not connected or unknown, with how it found out.

**How MilO tells the time** (since 2026-10-07 (night), ADR-006). One object, and one rule that everything else follows:

```
 the phone's wall clock ----+                          THE ONLY READER of the phone's clock
 time since boot -----------+--> [ TrustedClock ]      core/clock, pure
 the boot's number ---------+        |   ^
 the anchor's file  <--read, write---+   |  made first, by MiloApplication (platform/clock: miloClock)
   no_backup/clock/anchor  (data/clock)  |
                                         v
                          AppContainer.clock  =  TrustedClock.now
                                         |
        +--------------+-----------------+----------------+-------------------+
        |              |                 |                |                   |
  trip controller   the daily check   the event log's   the screens'      the widget, the
  and the service   and the reminder  every writer      ViewModels        Android Auto screen
        |
  [ ClockWatch ] <-- TIME_SET (ClockChangeReceiver), and the clock's own news
        |   one line in the event log for a change MilO did not follow
        +-> MonthlyReminder.arm, NothingRecordedCheck.arm   once the clocks agree again
              each asks Android for its daily alarm (askForDailyAlarm):
                the clocks agree   -> at a time of day     (RTC, RTC_WAKEUP)
                they disagree      -> after the time left, counted from boot
                                      (ELAPSED_REALTIME, ELAPSED_REALTIME_WAKEUP)
                agree, on probation-> after 2 minutes, counted from boot, to ask again;
                                      no notification is shown until then
```

- **Nothing in MilO reads the phone's wall clock but `platform/clock/PhoneClock.kt`,** which hands it to `TrustedClock` as one of its two inputs. No `System.currentTimeMillis()`, no `LocalDate.now()`, no `Instant.now()`, no `Calendar` anywhere else: a class that needs the time takes a `clock: () -> Long` in its constructor and is handed `container.clock`. `WallClockReadersTest` reads the source tree and fails on a second reader. The time ZONE is not guarded: `ZoneId.systemDefault()` is read wherever a day is worked out.
- **The rule is pure.** `TrustedClock` is handed the two clocks as functions, so every case runs in a unit test. While its time and the phone's are within 2 minutes it follows the phone, so its time is exactly the phone's. Further apart it keeps its own time and follows only after the difference has been seen to hold for 10 minutes. The two clocks are read as one moment: the time since boot is read again after the phone's clock, and a pair it moved under is read again.
- **It is made before the container.** `MiloApplication.onCreate` calls `miloClock(this)` first, which reads the anchor's file, and hands the clock to `AppContainer`. A process that Android starts while the phone's date is ahead therefore has the right time from its first reading.
- **An anchor taken with nothing to check it against is on probation** (since 2026-10-09). A process that finds no usable anchor (no file, a file of another boot, a phone that gives no boot number) takes the phone's clock as it is, and that can be inside a jump. Until the phone's clock has agreed with such an anchor at a reading 2 minutes or more after it was taken, a phone's clock found more than 2 minutes behind MilO's time is taken at once. The mark is in the anchor (`ClockAnchor.onProbationSinceMs`) and in its file, so the next process of the same boot knows. `ClockWatch` looks every 30 seconds while it lasts and MilO's process is awake. **While it lasts, MilO shows no notification:** `MonthlyReminder` and `NothingRecordedCheck` are handed `clockOnProbation`, hold back a notification that would be due, and ask for their alarm 2 minutes ahead, counted from boot, so that a MilO that Android has frozen is woken to confirm its clock and look again.
- **It is the one object that is not the container's.** `miloClock` keeps one clock for the process and gives it to whoever asks. That is for Android's backup agent, whose process has no `MiloApplication` and no container, and which dates its notes with the same clock.
- **The screens get it through a composition local,** `LocalMiloClock` (`core/designsystem/component/`), for the two places where a Composable shows a day by itself: the header's date and the Log screen's "today". `MiloApp` provides `container.clock` once. Everything else on a screen gets its time from a ViewModel, which was handed the clock.
- **The daily alarms are Android's, and Android goes by the phone's clock.** `MonthlyReminder` and `NothingRecordedCheck` are handed a second function, `phoneClockAgrees`. While it answers yes they ask for their alarm at a time of day (`setFor`). While it answers no they ask for the same alarm after a wait that Android counts from boot (`setAfter`, since 2026-10-09): the time that is really left, which no date moves. Both requests carry the same `PendingIntent`, so each takes the other's place and there is never more than one alarm. Which way it is, is decided in one function for both, `askForDailyAlarm` (`platform/clock/DailyAlarmAsked.kt`). `ClockWatch` hears of every change (the `TIME_SET` broadcast, and what the clock itself notices at any reading), writes the one line when a change is over, and calls both `arm` functions when the clocks agree, and when it sets aside a clock that was set back (Android delivers no alarm for that, so nothing else would ask). While they disagree it has the clock read every 30 seconds. If no MilO process is there when they agree again, the alarm that counts from boot remains.
- **Nothing in `platform/clock/` can touch a trip.** `ClockWatch` is handed the clock, the two `arm` functions, the event log and the crash files. A failure in it is caught there, as in the monthly reminder.
- **What Android dates itself stays on the phone's clock:** the records of ended processes (`StartupDiagnostics` writes one that is dated ahead at the time it reads it, and never moves its mark past the present), a notification's running time, a file's date.

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
- **A truck that is connected and parked is a third thing the service can be asked for** (since 2026-10-06 (evening); ADR-002, amendment 28). The trip rules close a trip whose truck has stood still (`core/trip/ParkedRules`), and if the truck is still connected they do not go idle: the state holds `Parked`, and `TripServiceLink` tells the service `watchParked` in place of `record` or `stop`. The service stays in the foreground, asks for a fix every 30 seconds in place of every 5 (`FixRate`), and shows a notification that says the truck is parked. Its fixes still go through the inbox, but with no trip open they are stored nowhere: `platform/trip/TripParking` feeds them to `core/trip/ParkedWatch`, which is the distance calculation run over the parked place and those fixes, so "moved" means beside a parked truck exactly what it means for a trip's kilometres. When it has moved, the rules start a trip, and `TripParking` stores the parked place and the fixes that showed the movement as that trip's first points. The wait itself (when it began, and the place) is kept in the settings file, like the hold-off, so that a restart finds it; `platform/trip/PickedUp.kt` reads it back with the open trip, and keeps it if the truck cannot be read at that moment. Android Auto is watched through the wait as through a trip, and holds it when the truck's Bluetooth drops. The wait lasts three days at most (`WAITING_LIMIT_MS`); after that the service stops and nothing watches the truck. The review of the same evening changed five things in this, set out in ADR-002, amendment 29. The verification of that night changed one more (amendment 30): `ParkedWatch` drops a first fix that nothing bears out within two minutes, so that it cannot date a trip, but keeps it in mind as a sighting, and the next usable fix that also shows the truck away from the parked place starts the trip. Before, a truck driven off while usable fixes came more than two minutes apart was never seen to move.

**How addresses are found.** `platform/address/TripAddresses` stands beside the recording, not inside it:

```
[ TripController.activity ] --read--> [ TripAddresses ] --asks--> AddressLookup (Geocoder)
[ a process start, the Trips screen ] --catchUp()--^   |
                                                       +--writes--> the address columns of a
                                                                    FINISHED trip, the event log
```

- It reads the controller's published state and the stored trips, and writes only a finished trip's four address columns, through `TripRepository`. The trip rules, the controller and the service know nothing of it, so a lookup cannot delay the end of a trip and a failed one cannot change a trip. An address that is Shawn's own (typed or emptied on the edit screen, or of a trip he added by hand) is never asked about and never written to.
- A pass over the trips that are due is asked for with `catchUp`: by `MiloApplication` at process start, by the Trips screen's ViewModel each time the screen comes to the front, and by the class itself when the controller stops showing a trip as in progress. The request at process start is handed to `TripController.whenCaughtUp`, so the pass follows the reconcile: a trip the restart rules close at start is never published as in progress, and only that pass looks it up. This is the one place where the two meet, and the controller still knows nothing of addresses: it runs a callback when its inbox has been worked off. Requests go through a channel that keeps only the newest and one coroutine works them off, so passes never overlap.
- What is due, how the attempts are spaced and when a trip is given up on are pure functions (`AddressRetry.kt`), shared with the Trips screen so that what the screen says and what the lookup does cannot disagree. Turning a geocoder's answer into one line is another (`AddressLine.kt`).
- The geocoder is behind the interface `AddressLookup`, and the network check is a plain function handed in, so the whole class runs in unit tests on stand-ins.
- There is no scheduled job. WorkManager is in the planned stack for work that can wait (section 2), but it is not in the build yet, and the work order for this package put it out of scope; a lookup that failed waits for the next of the occasions above.

**How a trip becomes Business or Personal.** The work schedule stands after the recording, never before it:

```
[ trip rules ] --EndTrip--> [ TripLedger.close ] --one UPDATE--> the trip row: closed,
   core/trip                    platform/trip                     AND its category and flags
                                     |  asks, at this moment only
                              fileTrip()   core/schedule, pure
                                     ^  the schedule and "ignore", as stored now
[ a process start ] --catchUp()--> [ TripCategoryCatchUp ] --> closed trips with no category
                                        data/trip                 (recorded before version 3)
[ Trips screen ] --mark--> TripRepository.setCategoryByHand --> a FINISHED trip, set by hand
[ edit screen ] --save, restore, add--> refileTrip()   core/schedule, pure   (below)
```

- **The schedule is never asked whether a trip starts, carries on or ends.** Nothing under `core/trip/` or `platform/bluetooth/` knows it exists, and the trip service does not either. The controller's settings reader (`TripRuleSettings`) reads it with the grace period and the minimum distance at every trigger, and hands it to exactly one place: the ledger's `close`.
- **A trip is sorted in the write that closes it.** `fileTrip` (pure, in `core/schedule/`) takes the trip's stored start, its end as the closing rules worked it out, the schedule and the phone's time zone, and answers Business or Personal, whether a Business trip ran past its day's hours, and whether the trip is to be ignored. `TripRepository.closeTrip` stores that together with the end, the distance and the positions, so no closed trip is left without having been sorted. If the settings cannot be read there is nothing to sort by, and the trip is closed unsorted.
- **"Ignore them" is a status, written once.** A trip that the trip rules kept, that turned out Personal, with "ignore" chosen, is stored as `DISCARDED` with `ignoredOutsideSchedule` set, and with everything a kept trip has. That is the whole of the feature: the trip was started and recorded like any other, and "Count this trip" on the Trips screen puts it back.
- **Trips from before version 3** are sorted by `data/trip/TripCategoryCatchUp`, asked for by `MiloApplication` at process start through `TripController.whenCaughtUp`, after the reconcile, in the pattern of the address catch-up (a conflated channel, one coroutine, one pass at a time). It writes only the category and the ran-past flag of a closed trip that has no category and was not set by hand, and the update itself carries those three conditions. It never changes a status.
- **MilO sorts a trip once.** A trip with a category is not touched by the catch-up, and no change of the schedule reaches a stored trip. Every later write is Shawn's own: `setCategoryByHand`, which also marks the row as set by hand, and a save of the edit screen. There a category he picks is his; a category set by hand stays whatever he does to the times; and otherwise a trip whose **start** he changed is sorted again by the schedule as it is at that moment, because only the start decides. "Ran past schedule" is worked out again whenever a time changed. The rule is `refileTrip` in `core/schedule/TripRefiling.kt`, and the form shows what it will store before Save is pressed.
- The rule is `classifyTrip` and is told nothing about what started the trip, so a trip started by hand follows it like one the truck started.

**How a trip is changed or added by hand.** The edit screen stands after the recording too, and beside the lookup:

```
[ Trips screen ] --Edit, Add missed trip---------> [ edit screen ]   feature/tripedit
                                                         |  TripEditViewModel holds the form
                                                         |  until Save is pressed
                                               [ TripEditing ]  checks the form (pure), then
                                                         |      writes, then one line in the log
        +------------------------+-----------------------+
        |                        |                       |
 editByHand               restoreRecorded            addByHand       TripRepository
        |                        |                       |
 editedTrip()             restoredTrip()          tripAddedByHand()  data/trip, pure
        +-----------+------------+                       |
                    |                                    |
     TripDao.rewriteFinished: read the row,          TripDao.insert:
     ask the rule, write it back, in one             a FINISHED row that
     transaction, only if it is FINISHED             was never open
```

- **Only a finished trip.** The write matches on the status, like the changes of status, so a trip that is being recorded, discarded or deleted is never written to, whatever a screen showed. Nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/` knows of any of this: the trip rules own the open trip, and these writes never touch one.
- **The rules are pure functions.** `editedTrip`, `restoredTrip` and `tripAddedByHand` (`data/trip/`) each take the stored row and answer the row to store. The DAO only carries the answer out, and reads the row inside the same transaction, so what the rule saw is what is replaced.
- **A save writes only what Shawn changed.** The form is opened on the trip as it is stored and keeps every untouched field "as stored", to the millisecond and the metre. So a trip that is opened and saved is not changed, and an address the lookup finds while the form is open is not overwritten by a field he never touched.
- **What MilO recorded is kept on the row.** The first edit copies the trip's start, end and distance into three columns that are never written again; "Restore recorded values" copies them back. Nothing is worked out again from the GPS points (section 6 says why).
- **The form is checked at the moment of the save,** by a pure function (`formProblems` in `feature/tripedit/`), against the clock and the trip that is being recorded as they are then: the end after the start, nothing in the future, no overlap with the recording, and a distance that is a number, not negative and not absurd. Each thing it finds belongs to a part of the form (`FormPart`), and the screen says it there.
- **The form has one date, the day the trip started, and a yes or no for "ended the next day"** (`TripForm.endDayOffset`). The end is never moved to the next day by a rule. A typed time in the hour that happens twice when the clocks go back is read on the side of the change the stored time is on.
- **An address that is Shawn's own is kept away from the lookup** by a mark on the row for each end. The rule that decides what is due (`platform/address/AddressRetry.kt`), the query that finds the trips and the update that stores an answer all carry it.
- **Every change leaves one line in the event log** that names each value before and after (`data/trip/TripEditLogText.kt`).

**The truck's odometer (since 2026-10-07)** is worked out, never stored: `core/odometer/` takes the readings Shawn typed (kept in the settings file, `data/settings/OdometerStorage.kt`) and the trips that moved the truck (`data/trip/TruckTrips.kt`, `movesOdometer`), and gives the figure at any moment. The Settings tile (`OdometerViewModel`), Home's tile (since 2026-10-09) and the report (`ReportSources` → `mileageReport` → `MileageReport.odometer`) all ask it, from every finished trip, so they cannot disagree; Settings and Home through one function, `vehicleOdometers` (`data/trip/VehicleOdometers.kt`), because a feature never imports another. **Since 2026-10-09 those two tiles also count the trip being recorded, as it is driven:** each follows `TripController.activity`, whose `tripSoFar()` (`platform/trip/TripActivity.kt`) hands the open trip over as plain values (`TripSoFar`: its id, start, metres so far, whether a paired vehicle has been seen in it, and which), and `drivenTrips` (`data/trip/TruckTrips.kt`) adds it to the finished trips on the finished trip's own terms, unless storage already holds it as ended. The data layer still knows nothing of the trip controller, and the report still asks from finished trips alone. It touches no trip and no trip rule. Since 2026-10-07 (evening) a reading carries the unit it was typed in, and the figure is asked for in the unit chosen in Settings. **Since 2026-10-08 there is one per paired vehicle:** a reading names its vehicle ("time:km|address"), the vehicle's trips are those that name it (and for the first vehicle also those that name none: `ofVehicle`, `drivenIn`), and `MileageReport.odometers` holds one `VehicleOdometer` each.

**The unit distances are shown in (since 2026-10-07, evening)** is one stored setting (`distance_unit`). `data/settings/ShownUnit` holds it in memory for the life of the process, started by `MiloApplication`, and is the one place the surfaces read it from: the phone's ViewModels for Home and Trips, the Android Auto screen, the home-screen widget and the trip's notification. Each follows it as a flow, so a change in Settings shows everywhere at once; the trip service writes its notification again when it changes, and touches nothing else. **The edit screen is the one that does not follow it:** a form keeps its unit for as long as it is open, so it reads the unit from the settings file when it opens, in the read that gives it the work schedule (`TripEditing.formSettings`). It falls back on `ShownUnit` only if that file cannot be read. Storage is not involved: trips, points, the export file and the backup stay in metres. What a sent report printed is stored with its unit (section 6).

**How the driving alert stands beside the recording.** It is a second thing the system can start MilO for, and it is deliberately not in the table of entry points above: it never calls `TripController.onTrigger`.

```
 Play services (Activity Recognition)
        |  "entered a vehicle" / "left a vehicle", through a PendingIntent
[ DrivingReceiver ]        in the manifest, so a report can start the process
        |   a fresh "entered a vehicle" --> TripController.onVehicleEntered (since 2026-10-07)
        |
[ DrivingAlert ] --waits--> TripController.whenCaughtUp      (the first look at storage is done)
        |        --reads--> is a trip open? when did the last one end? (the stored rows)
        |                   the truck's connection (TruckConnectionSource)
        |                   the switch, the work schedule, whether a truck is paired and the
        |                   time of the last alert (SettingsStore)
        |
  judgeDriving()           pure: show, withdraw or leave
        |
[ TripNotifications ]      the "Driving alert" notification, and nothing else
        |
  SettingsStore            the time of the alert, written when one is posted
        :
        :  only a tap by Shawn
        v
[ TripService ] <--- the same intent as the "could not start this trip" notification:
                     MANUAL_START, handed to the controller once the service is in the foreground
```

- **Nothing in `platform/driving/` can start a trip.** `DrivingAlert` is handed five narrow things by the container: the two questions "is a trip open in storage?" and "when did the last trip end?", the flow the controller publishes, the controller's `whenCaughtUp`, and two functions that post and cancel the notification. It holds neither the controller nor the trip repository. Its one write to storage is the time of an alert, in the settings file. A unit test runs it beside a real controller and checks that no report ever asks for the trip service.
- **Beside a parked truck a report turns GPS on, and starts nothing** (since 2026-10-07, ADR-002 amendment 32). After the first hour of a wait beside a parked, connected truck GPS is off, and the receiver hands a fresh "entered a vehicle" (`enteredVehicleAtMs`) to `TripController.onVehicleEntered`, before the alert judges the report. The worker remembers its time and gives the service a later "GPS until" (`core/trip/ParkedGps.kt`); the fixes then decide by the watch's own rule. It is not a trigger: it reads no truck, changes no trip state and cannot ask for the service. The controller asks the alert, through a function the container passes it, whether the phone's reports are coming at all (`DrivingAlert.reportsComing`); without them GPS stays on for the whole wait.
- **The trip starts through an existing path.** The notification's tap is a `PendingIntent` for the trip service carrying `MANUAL_START`, built by the same function as the "could not start this trip" notification's and told apart from it by its request number. `TripService`, the controller, the trip rules and the Bluetooth triggers were not changed for the alert.
- **The decision is one pure function,** `judgeDriving`, which asks the work schedule through `classifyTrip`: the alert is shown only when a trip that started now would be Business. The schedule still has no say in whether a trip starts. The same function holds the other conditions: a truck is paired, no trip ended in the last 5 minutes, no alert was posted in the last 30. Both times come from storage (the `endedAtMs` of the newest closed trip, and `last_driving_alert_at_ms` in the settings file), so a restart of the process forgets neither.
- **A failure in it cannot end a recording.** The alert's coroutines run in the application scope, in the process of the trip service. Each piece of its work is run by `DrivingFailures.keptApart`, which catches every exception, writes one `ERROR` line and falls back on a crash file if the log is what failed, as `TripAddresses` and `TripCategoryCatchUp` do.
- **The request to Play services is renewed at every process start** (`MiloApplication`), which covers a reboot and an update of MilO: both start a new process, the first through the boot and update receiver. It is looked at again when MilO comes to the front and when the Settings switch is pressed (`DrivingAlert.arm`).
- **A trip that starts being recorded withdraws the alert:** `DrivingAlert` follows `TripController.activity` for that one purpose.

**How a report for the accountant is made and sent.** It stands after everything else: it reads finished trips and writes none.

```
[ Report screen ]  feature/report        ReportViewModel keeps no copy of anything stored
        |  reads, and again whenever one of them changes
        +--> the trips that started in the period   TripRepository (the query the Trips screen uses)
        +--> the settings: name, company, vehicle, the accountant's address   SettingsStore
        +--> the list of sent reports               SentReportRepository
        |
   selectForReport()     data/report, pure: counted, Business, started in the period
        |
   MileageReport         core/report, pure: the sender, the period, the trips by day,
        |                every figure rounded once and added up as printed
        +--> printedReport() -> layoutReport()   core/report, pure: every word, then where
        |          |                             each one stands and on which page
        |    ReportDocuments.createPdf   platform/report: draws the pages (PdfDocument),
        |          |                     writes the file through ReportFileStore (data/report)
        +--> reportCsv()  ->  ReportDocuments.createCsv
        |
   ReportHandOff         platform/report: builds the request for the email app, a PDF
        |                viewer or the share sheet, with MilO's file provider's address
        |                of the one file and a permission to read it
   the screen starts it on MilO's own activity; MilO sends nothing itself
        |
   "Did you send it?"    asked when Shawn is back; "I sent it" -> ReportRecords ->
                         one row in sent_reports, and a line in the event log
```

- **Two passes make the PDF, and only the second knows Android.** `PdfDocument` records pages as they are drawn and cannot go back to one, so "Page 2 of 5", a heading repeated on the next page and a row kept whole have to be decided before anything is drawn. `layoutReport` (pure) decides them from a `TextMeasure`, which answers how wide a text is; the drawing pass hands it the paints it then draws with, and a unit test hands it plain arithmetic. The drawing pass (`ReportPdf.kt`) only carries the result out.
- **One value, two files.** The PDF and the CSV are both made from the one `MileageReport`, and the screen's summary from the same selection, so the three cannot show different trips or figures. The CSV's copy differs in one thing: it carries no revision (`ReportSources.reportFor`). A CSV is the trips as they are now and replaces no report that was sent, so its file name and its share title never announce one.
- **The report adds up what it prints, and so does every screen.** A trip is rounded to a tenth once (`tenthsOf`), and every subtotal and total is a sum of those tenths. The rule is one pure function, `sumOfTenths` in `core/util/DistanceFormat.kt`. **Since 2026-10-07 (evening) both take the unit the figure is shown in** (`DistanceUnit`: kilometres, or miles when Shawn chooses them in Settings): a tenth of a kilometre or a tenth of a mile, always worked out from the stored metres, never from a figure that was already rounded in the other unit. In kilometres the arithmetic is the one it always was. The report's days and total go through it (`core/report/MileageReport.kt`), and since 2026-10-06 so do the totals of `data/trip/TripTotals.kt` (`categoryTotals` and `todayTrips`), which the Trips screen's month card and day headings, Home's two figures and the Android Auto screen's Today row are made of. No screen adds up metres of its own, so a month reads the same on the phone as on its report. Before that the screens rounded a sum of metres once, and differed from the report by up to about a kilometre a month.
- **MilO sends nothing.** It has no INTERNET permission. The email is a draft in the email app; the PDF reaches that app through a `FileProvider` that can hand out the files of one folder, the reports in the cache, and nothing else of MilO's storage.
- **Android never says whether an email was sent.** So nothing is recorded when the email app is opened. What was handed over is kept in the settings file (`ReportHandOver`), the question is asked when the Report screen is next in front, and only "I sent it" writes a row. The row is all there is to "submitted": no status is stored on a month, it is worked out from the rows (`monthSubmission`), for the Trips screen's month card, the Report screen and the monthly reminder alike. That is also why a report can be taken back by deleting its row and nothing else (`SentReportRepository.remove`, behind a question on the Report screen), and why "has this month changed since it was sent?" needs no stored state: the row's own count and total are held against the month as it is now (`changedSinceSent`).
- **The trip engine knows nothing of any of this.** Nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/` was changed for it.

**How the monthly reminder stands beside everything.** It is a third thing the system can start MilO for, after a trip trigger and a report of driving, and like the driving alert it never calls `TripController.onTrigger`.

```
 Android's alarm service: one inexact alarm, asked for at 09:00 of the next day
        |  through a PendingIntent
[ ReminderReceiver ]       in the manifest, so the alarm can start the process
        |
[ MonthlyReminder ] <----- a process start (MiloApplication, after TripController.whenCaughtUp)
        |          <----- MilO coming to the front (MainActivity.onStart)
        |          <----- the reminder's Settings tile; a report recorded as sent
        |  reads: the switch, the day and "shown for which month, on which day" (SettingsStore)
        |         the list of sent reports (SentReportRepository)
        |         last month's trips (TripRepository, the query the Trips screen uses)
  judgeReminder()          pure: show, withdraw or leave
        |
[ ReminderNotification ]   "Mileage report for September 2026", and nothing else
        :
        :  only a tap by Shawn
        v
[ MainActivity ] --the month--> MiloApp --showReport--> back stack [ Home, Trips, Report ]
```

- **It is decided by what is true when MilO looks,** not by an alarm that fires on a date: the reminder is switched on, this month's reminder day has come, last month has no report for the whole month, it has a trip its report would list, and no reminder was shown for it yet today. The same question on every occasion, so a phone that was off on the 1st needs no special case. The decision and the two dates around it (`reminderStartsOn`, `monthToRemindOf`) are pure functions in `platform/reminder/ReminderRules.kt`; "has a trip its report would list" is `selectForReport` and "has a report" is `monthSubmission`, so the reminder cannot disagree with the Report screen.
- **One inexact alarm a day, and no WorkManager.** `AlarmManager.set` with type `RTC` needs no permission; Android may deliver it up to an hour late and does not wake a sleeping phone for it. Android forgets alarms at a reboot and at a force stop, so the alarm is asked for again at every process start (a reboot and an update of MilO both start one, through the boot and update receiver), and each alarm that arrives asks for the next.
- **Nothing in `platform/reminder/` can touch a trip.** `MonthlyReminder` is handed the settings store, two reads (every sent report; the trips that started in a span of time), two functions that post and cancel the notification, and, since 2026-10-07 (night), the question whether MilO's clock is the phone's right now, which it asks before it asks Android for its alarm: the answer decides on which of the phone's two clocks the alarm is asked for ("How MilO tells the time", above). Its one write is "shown for this month, on this day", in the settings file.
- **A failure in it cannot end a recording.** Its coroutines run in the application scope, in the process of the trip service; each piece of its work is caught, written as one `ERROR` line, and falls back on a crash file if the log is what failed, as in the driving alert.
- **The tap is the one place a notification leads into the screens.** The notification's `PendingIntent` starts `MainActivity` with an action of MilO's own and the month as two numbers; an activity that is already there is told through `onNewIntent`. `MainActivity` reads the month (`reportMonthToOpen`, in the reminder's package) and hands it to `MiloApp`, which opens the Report screen through the same gate as every other way out of a screen: if the edit screen holds unsaved work, "Leave without saving?" comes first. A request that Android replays (an activity rebuilt after a rotation, or started again from the recent apps) is not carried out a second time.

**How the "nothing recorded" check stands beside everything.** It is a fourth thing the system can start MilO for, and like the driving alert and the monthly reminder it never calls `TripController.onTrigger`. It is the reminder's pattern over again, with one more thing read: the trips of today.

```
 Android's alarm service: one inexact alarm, asked for at the moment the next day is checked from
        |  through a PendingIntent
[ NothingRecordedReceiver ]   in the manifest, so the alarm can start the process
        |
[ NothingRecordedCheck ] <--- a process start (MiloApplication)
        |             <--- MilO coming to the front (MainActivity.onStart)
        |             <--- the check's Settings tile; the end of an import
        |             <--- the stored work schedule changing (the check watches it itself)
[ NothingRecordedLook ]      one look at a time
        |  waits:  TripController.whenCaughtUp, five seconds at most
        |  reads:  the switch, the time, the work schedule and "shown on which day" (SettingsStore)
        |          the trips that started today, and the open trip (TripRepository)
  judgeNothingRecorded()     pure: show, withdraw or leave
        |  if "show": waits 2.5 s for a trip that may be starting, has the controller catch up
        |             again, reads and judges once more, and shows only if it is still "show"
        |
[ NothingRecordedNotification ]   "No trip recorded today", and nothing else
        :                    <--- TripController.activity: a trip that begins takes it away
        :  only a tap by Shawn
        v
[ MainActivity ] --"Home is asked for"--> MiloApp --showTopLevel--> back stack [ Home ]
```

- **It is decided by what is true when MilO looks,** like the reminder: the check is switched on, the schedule tracks today, the moment from which today is checked has come (the time Shawn set, or the day's own start if that is later), no trip counts as recorded today, and today's notification has not been shown. The decision and the three things around it (`checkedFrom`, `countsAsRecordedToday`, `nextNothingRecordedLookMs`) are pure functions in `platform/nothingrecorded/NothingRecordedRules.kt`.
- **"A trip was recorded today" is read from storage, and says nothing about its outcome.** Every row MilO opened today counts, whatever its status became (open, finished, discarded, deleted), and so does a trip that is open now, whenever it began. A trip added by hand never counts: it was typed in because MilO missed it. An edited trip goes by the start MilO recorded.
- **The schedule is read for one thing more, and still decides nothing about a trip.** The check asks it which days are work days and when each begins. It joins the driving alert as a reader of the schedule that only ever notifies (APP_ENCYCLOPEDIA, Schedule and Business/Personal).
- **A second inexact alarm a day, and still no WorkManager.** `AlarmManager.set`, as for the reminder, with no permission. Its type is `RTC_WAKEUP` where the reminder's is `RTC`: this notification is worth most while the work day is still going on, so a sleeping phone is woken for the moment the look takes. Asked for again at every process start, by each alarm that arrives, and whenever the stored work schedule changes; taken back while the check is switched off.
- **It follows the work schedule by itself.** The alarm's moment is the later of the time set and the next day's start, so it is worked out from the schedule. `NothingRecordedCheck` watches the schedule in the settings store's own flow for the life of the process; a change, on the Settings screen or by an import, has it ask for the alarm again and look. Nothing in `feature/settings/` knows of it, and no other setting is watched: the check's own switch and time come from its card, which tells it.
- **It waits for the trip controller, but not for ever.** A look can be the first thing a new process does. It waits until the controller has dealt with what it was handed (`whenCaughtUp`), so that a trip the last process left open is closed before the trips are read. After five seconds it looks all the same and says so in its line: this check exists for the day something in MilO has stopped working, and must not depend on all of it working.
- **"Caught up" is not "the trip that is starting is stored", so "no trip" is said only by a second look.** The controller opens a trip only when the trip service is in the foreground ("the service comes first", above). Between asking Android for the service and the service reporting in, it holds no trip row, publishes none and has an empty inbox, so `whenCaughtUp` answers at once. A look in that gap (MilO opened beside a connected truck; a process the truck's connect started) would read "no trip today" a moment before the trip begins. Nothing the check is handed says that a start is pending, and `platform/trip/` was not to change for it. So `NothingRecordedLook` does not post on a first "show": it waits `TRIP_START_WAIT_MS` (2.5 s), asks `whenCaughtUp` again (the service may have reported in, with its trip still in the inbox), reads and judges again, and posts only if the answer is still "show". The wait is inside the lock that keeps looks apart, so a look that arrives during it finds the day already stored. A controller that did not answer the first time is not waited for again: five seconds and the wait together stay under the eight a manifest broadcast is held open for. **What is left:** a service that takes longer than 2.5 s to come up; the notification is then posted and taken away as the trip begins.
- **Nothing in `platform/nothingrecorded/` can touch a trip.** `NothingRecordedCheck` is handed the settings store, two reads of the stored trips, the flow the controller publishes, the controller's `whenCaughtUp`, and two functions that post and cancel the notification, and passes them on to `NothingRecordedLook`. Since 2026-10-07 (night) it is also handed the question whether MilO's clock is the phone's right now, which it asks before it asks Android for its alarm: the answer decides on which of the phone's two clocks the alarm is asked for. Neither holds the controller or the trip repository. Its one write is the day the notification was shown, in the settings file.
- **A failure in it cannot end a recording.** Each piece of its work is run by `NothingRecordedFailures.keptApart`, which catches every exception, writes one `ERROR` line and falls back on a crash file if the log is what failed, as in the driving alert.
- **The tap is the second place a notification leads into the screens.** Its `PendingIntent` starts `MainActivity` with an action of MilO's own; `MainActivity` reads it (`homeAskedFor`, in the check's package) and tells `MiloApp`, which shows Home through the same gate as a press of the bar's Home button: unsaved work on the edit screen is asked about first.

**How the watch on Android Auto outside trips stands beside the recording.** It is not an entry point: it starts no process and never calls `TripController.onTrigger`.

```
 Android Auto (the app on the phone)
        |  answers the Car App Library's question, and announces each change,
        |  to a process that is observing it
[ AndroidAutoWatcher ]  x 2    platform/car, the same class twice:
        |                      one of the trip service's, for the length of a trip
        |                          -> TripController.onAndroidAuto -> the trip rules, and a line
        |                      one of AndroidAutoLog's, for the life of the process
        v
[ AndroidAutoLog ] --asks--> is a trip being recorded?   TripController.activity, read only
        |
  judgeAndroidAutoReport()     pure: a line, or silence
        |
  the event log, category ANDROID_AUTO, and nothing else
```

- **Two watches, and each change written by one of them.** During a trip the trip service's watch tells the trip controller, which tells the trip rules and writes the line. The app-wide watch hears the same change and stays silent, because the controller publishes a trip. Outside a trip only the app-wide watch exists, and it writes the line. In the instant a trip starts or ends the two can overlap or leave a gap of milliseconds.
- **It cannot touch a trip.** `AndroidAutoLog` is handed the source of its reports, the main thread, one question ("is a trip being recorded?", answered from what the controller publishes), the event log, the crash file store, a clock and the application scope. It holds neither the controller nor the trip repository. Android Auto still reaches the trip rules through the trip service alone, and still cannot start a trip (section 10).
- **Started with the process, after the reconcile.** `MiloApplication` asks for it through `TripController.whenCaughtUp`, like the catch-ups and the reminder, so that a process started by a trip trigger does the trigger's work first. The watch itself begins on the main thread, which the Car App Library requires.
- **It sees only what happens while the process is alive,** and it is told of a change only when Android Auto announces one. A process start's first reading is written as such, so a connection that was made while MilO was not running shows as "connected" without a time.
- **A failure in it does not end the process.** Its own work is caught piece by piece and written as one `ERROR` line, with a crash file as the way out if the log is what failed; if neither can be written the failure is counted and said with the next line. That last step is where it differs from the address lookup and the driving alert, which let such a failure out.

**How backup, export and import stand beside everything.** Three ways for Shawn's data to leave or to arrive, and one place where what arrived is put right. None of them calls `TripController.onTrigger`, and nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/` was changed for them.

```
 Android's backup                    res/xml/data_extraction_rules.xml decides what is copied
        |  calls, in a process where MiloApplication and the container may not exist
[ MiloBackupAgent ]  platform/transfer
        |  before the copy:  settleDatabasesForBackup()   data/transfer: each database's log
        |                    moved into its main file, with SQLite itself, not through Room
        |  too large / restored / collected:  a small file in no_backup/backup_notes/
        v
[ BackupAftermath ] <--- every process start (MiloApplication, before the start-up diagnostics)
        |  each note becomes a line of the event log
        |  after a restore:  truckOnArrival()  pure: is the truck paired HERE?
        |                    forgetOtherInstallation()  the association's number, the
        |                    confirmations of the checklist, a sound whose file is not here
        +--> TruckPairing.check   which reports "paired before, pair it again"

 [ Settings screen, last tile ]  feature/settings: DataViewModel
        |  a file made or picked with Android's file picker
[ DataTransfer ]  platform/transfer    one thing at a time; its state outlives the screen
        |
        +-- export --> DataExport.writeTo --> writeExport()   data/transfer, pure: one JSON
        |                 reads every closed trip, every sent     document, written straight
        |                 report, the settings that travel and,   through, the points a page
        |                 page by page, the raw points            at a time
        |
        +-- import --> IncomingImport   MilO's own copy of the picked file
                       readExport()     pure: the whole file checked, nothing touched
                       the question     "Replace everything on this phone?"
                       [ ImportRun ]    a safety copy of what the phone holds, then
                            |           a note that it begins  no_backup/import/begun.txt
                            |           trips + sent reports   one transaction, milo.db,
                            |                                  with a line of the event log
                            |           the settings           one write, truckOnArrival()
                            |           the raw points         one transaction, points.db
                            |           the note taken away
                            +--> the pairing check, the address lookup, the sorting into
                                 Business and Personal, the driving alert, the reminder

[ ImportResume ] <--- every process start (MiloApplication, after the start-up diagnostics)
        |  no note:                   nothing (a copy of a file nobody answered is removed)
        |  a note, the line missing:  nothing was replaced; note, copy, safety copy removed
        |  a note, the line there:    ImportRun's steps from "the settings" on, again, from
        |                             MilO's copy of the file; or, without a usable copy,
        +--> DataTransfer.status      the raw points of the replaced trips removed
```

- **Android's code copies the files; MilO's agent only stands around the copy.** The manifest names the agent with `fullBackupOnly`, so Android backs up by copying files, by the rules file, which has exclude lines only (section 6 says why that matters for the write-ahead log).
- **The agent can reach nothing of the app.** Android runs it in a process it starts for the purpose when MilO is not running, where the `Application` object is a plain one; and, seen on an emulator, inside MilO's own process when MilO is alive. It therefore builds the two things it needs itself (the note store and, as a last resort, the crash file store), opens the databases with SQLite and not through Room, and leaves what it has to tell as a file, the way a crash does. It never touches the container.
- **What a restore brought is put right at the next ordinary start, by what is true then.** `BackupAftermath` does not trust the restored settings about the truck: it asks Android which associations it holds on this phone, and one pure function, `truckOnArrival`, decides. The same function decides for an import. Its rule: the association on this phone decides, and a truck is never stored as paired because a file says it was.
- **An export is written, and an import is read, one value at a time.** A year of raw points is some tens of megabytes as text (about a hundred since a trip stores a point every 2 seconds, 2026-10-09) and several times that as objects. The writer fetches the points a page at a time and writes each as it comes; the reader (`JsonScanner`) takes the document apart by its brackets and quotation marks alone and hands each trip, report and point to the JSON library singly. The trips, the sent reports and the settings of a file are held (a few thousand small values); the points never are.
- **The file's form is its own, not the tables'.** `ExportFormat.kt` fixes the document and its version number; `ExportMapping.kt` stands between it and the entities, with every stored constant spelled out. A unit test holds the document against the column lists Room exports to `app/schemas`, so a column that is added to a table and not to the document fails the build.
- **An import is a sequence of steps that each can stop it, and only the first of them can be undone by doing nothing.** Check the whole file; ask; no trip in progress (asked three times, the last inside the transaction); a safety copy; then the main database in one transaction. Up to there a failure leaves the phone as it was. The settings and the points follow, each atomic by itself: the two databases are two files and cannot share a transaction. If the points fail, the points of the replaced trips are removed, so that the state is "trips whole, no tracks" and never "another trip's track".
- **An import outlives the process that began it.** Nothing makes the two transactions one step, and the second takes seconds. So the first leaves a witness in its own database: a line of the event log, written in the transaction that replaces the trips, whose time and text a note in `no_backup/import/` holds from just before. A process start that finds the note asks the log for the line. Without it nothing was replaced, and nothing is touched. With it, `ImportResume` takes the steps after the trips again (each can be taken twice), or removes the raw points up to the note's trip id if MilO's copy of the file cannot be used. It never writes to the trips. A start that is itself ended over this is counted in the note, and after two the import is left as it is and said, so that it can never end every process that follows.
- **A raw point without a stored trip does not stop an export.** The writer passes over a point whose trip is not among the closed trips of the file, and `DataExport` counts those points beforehand (one grouped query), so the file still says exactly what it holds. Otherwise two databases that had come apart could not be exported, and could not be imported over either, since every import begins with a safety copy.
- **A trip that starts during an import is safe by its id.** The tables number upwards from the highest id they ever held. The transaction that replaces the trips answers with the highest id a trip had before or has now; the points transaction removes points up to that id only. A trip the truck starts after the first transaction has a higher id, and its fixes are not touched.
- **`DataTransfer` holds the state, not the screen.** It runs in the application scope and publishes where it stands as one flow. Leaving Settings stops nothing; a failure is caught there, written to the log (`TransferFailures`, with the crash file as the way out if the log is what failed) and said on the card.
- **The only SQL that can empty a table is in `data/transfer/TransferDaos.kt`,** two DAOs that nothing but `DataExport` and `DataImport` is handed.

**How the shared objects are made.** `app/MiloApplication` is the first code to run in the process, however it was started. It makes MilO's clock first ("How MilO tells the time", above), and then creates one `app/AppContainer`, which is handed that clock and passes it on as `container.clock`. The container builds the databases, the repositories, the settings store, the trip controller, the address lookup, the catch-up that sorts earlier trips into Business and Personal, the setup checklist, the opener of settings screens, the driving alert, the list of sent reports, the writer of the event log's text file, and the two objects behind the Settings screen's two sound tiles (`OwnTripSound`, which makes a picked audio file the connect sound or the trip-start sound, and a `TripSoundPlayer` for their Play buttons), each lazily, on first use, and owns the application-wide coroutine scope. The three objects that make and hand over a report (`ReportTexts`, `ReportDocuments`, `ReportHandOff`) and the monthly reminder are built by `app/ReportObjects`, a part of the container that it creates and through which they are reached (`container.reports`); it is a class of its own only because `AppContainer` is at its size limit, and it follows the same rules. `app/TransferObjects` (`container.transfer`) is a second such part, for `BackupAftermath` and `DataTransfer`; what those two are made of is put together in their own package (`platform/transfer/TransferBuilding.kt`), from a handful of things the container hands over. `app/CarObjects` (`container.car`) is a third, for the one long-lived object around Android Auto, the watch that writes its connection changes to the event log outside trips (`buildAndroidAutoLog` in `platform/car/` puts it together). `app/CheckObjects` (`container.checks`) is a fourth, for the daily "nothing recorded" check; the ViewModel of its Settings tile is made there too, as the one for backup, export and import is made in `TransferObjects`. `app/ClockObjects` (`container.clocks`) is another, for `ClockWatch` and for the question "is MilO's time the phone's right now?". A screen's ViewModel is given what it needs from the container in `app/MiloNavigation`, so a feature never imports the `app` package. The container decides nothing about storage: it calls the `build...` functions in `data/`, which own every file name and folder. Everything else is handed what it needs through its constructor. There is no Hilt, and one object only that is kept for the whole process outside the container: MilO's clock (`miloClock`), which the backup agent's process needs too. To see what a class depends on, read its constructor; to see what it is given, read `AppContainer`.

**How the phone screens are reached.** Navigation 3. `app/MiloApp` holds the back stack and frames every screen with the bottom bar; `app/MiloNavigation` shows the screen on top of the back stack and is the only code that knows more than one feature. The screens' keys and the few functions that change the back stack are in `app/MiloBackStack.kt`. `MiloApp` is also where a screen is left, by any way, and so where the question before unsaved work is thrown away is asked (`app/UnsavedWork.kt`).

```
[ MiloNavigationBar ]   Home | Trips | Settings | Log     (core/designsystem; four icons, no words)
        |
   back stack:   [ Home ]                    Home alone, or
                 [ Home, Trips | Settings | Log ]   one bar screen on top of it, or
                 [ Home, Settings, Setup ]       Setup, opened from the tile at the top of Settings, or
                 [ Home, Settings, Setup, Pairing ]   the pairing screen, opened from Setup, or
                 [ Home, Settings, Pairing ]     the pairing screen, opened from Settings, or
                 [ Home, Setup ]                 Setup, opened from Home's warning, or
                 [ Home, Setup, Pairing ]        the pairing screen, opened from that Setup, or
                 [ Home, Pairing ]               the pairing screen, opened from Home's truck tile
                                                 while no truck is paired, or
                 [ Home, Report ]                the Report screen, opened from Home's tile for a
                                                 report that has not been sent, or
                 [ Home, Trips, TripEdit ]       the edit screen, opened from Trips, or
                 [ Home, Trips, Report ]         the Report screen, opened from Trips, or
                 [ Home, Trips, Report, Settings ]   Settings, opened from the Report screen
```

Until 2026-10-07 the bar's third screen was Setup, and Settings was opened with a button at the end of Home's top line. Shawn asked for "the bottom button Setup to now be Settings", for the button on Home to go, and chose to reach Setup from a tile at the top of Settings.

- **Before every screen, once: the first start** (since 2026-10-08). While `first_run_stage` is not set, `MiloApp` draws the page that says what MilO does (`feature/onboarding/`) in place of the bottom bar's frame and the back stack's screens. OK stores `setup` and opens Setup on top of Home; while the stage is `setup`, `MiloNavigation` hands Setup a Done action, and Done stores `done` and shows Settings as the bar's button does. The back stack is the same one throughout, so nothing about it changed (APP_ENCYCLOPEDIA, First-start onboarding).
- **Once after an update: What's new** (since 2026-10-08). `MiloApp` watches `WhatsNewNoticeViewModel`, and when the first start is over and `whats_new_seen_version` is not the version installed, it puts `WhatsNewKey` on top of the back stack and stores the version. The same screen is opened by the Version tile at the end of Settings. Its list is `app/src/main/assets/changelog.json`, read through `data/changelog/` (APP_ENCYCLOPEDIA, Version and What's new).
- **On a fresh start: the greeting** (since 2026-10-09). `MainActivity` says that the activity is newly built (`greetingAsked`, set beside `homeAsked` and for the same reason: an activity Android builds again is not a fresh start). `MiloApp` asks `greeting()` in `feature/greeting/` what that comes to, and while the answer is "showing" it draws `MascotGreeting` over the screen, and hands it the height of the bottom bar as the `Scaffold` reports it: the mascot walks along the bar's top edge. The greeting is a drawing and takes no press from the screen under it. The rule drops the greeting during the first start, when Home is not the screen on top (What's new, a report a notification opened), and after a tapped notification (APP_ENCYCLOPEDIA, The greeting).
- Each screen is a `@Serializable` key (`HomeKey`, `TripsKey`, `SettingsKey`, `LogKey`, `SetupKey`, `PairingKey`, and the two keys that carry something: `TripEditKey`, the id of the trip to edit, or nothing for a trip that is being added, and `ReportKey`, the year and month the Trips screen was showing), because Navigation 3 saves the back stack with kotlinx.serialization when Android puts MilO away. Seen on an emulator on 2026-10-06: with Settings open and MilO's process killed in the background, opening MilO again showed Settings. The same was seen with the edit screen: it came back, for a stored trip and for an empty form alike, with the trip as it is stored. What had been typed and not saved was gone, because the form lives in the screen's ViewModel and not in the saved state.
- Home is always at the bottom. Pressing a button of the bar leaves Home alone, or Home with that screen on top; whatever was open above is closed. So Back from Trips, Settings or Log leads to Home, and Back from Home leaves MilO.
- The bar marks the bar screen the back stack is in (`topLevelOf`): the screen directly on top of Home, if it is one of the bar's, and Home otherwise. So Settings stays marked under Setup and the pairing screen opened from it; Home under Setup opened from Home's warning, under the pairing screen opened from Home's own truck tile, and under the Report screen opened from Home's own report tile; Trips under the edit screen and under the Report screen opened from Trips. Settings opened from the Report screen is a bar screen opened on top of another one: Trips stays marked there, Settings shows the way back at its top, and Back leads back to the report. It is the only screen that is sometimes the bar's and sometimes opened on top; `MiloNavigation` tells the two apart by its place on the back stack.
- A screen that leads to another one (Home to Setup, to Trips, to pairing and to the Report screen; Settings to Setup and to pairing; Setup to pairing; Trips to the edit screen and to the Report screen; the Report screen to Settings) is handed a plain function to call. Settings shows the Setup feature's tile in a slot the app fills (`SetupLinkTile`), as it shows the tiles of the daily check and of backup and export. Features never import each other. A screen opened on top of another is added once, however often its button is pressed before the screen has changed (`openOnTop`).
- **Two screens are also opened from outside the screens.** The Report screen, by a tap on the monthly reminder: `showReport` in `MiloBackStack.kt` makes the back stack what it is when the screen is opened from Trips, Home, Trips and the report on top, whatever was open before, so Back leads where it always leads from a report. And Home, by a tap on the daily check's notification (since 2026-10-06): `showTopLevel`, exactly what a press of the bar's Home button does, so whatever was open on top is closed.
- A screen's ViewModel lives as long as the screen is on the back stack, and is created with what it needs from the `AppContainer`. Leaving Trips and coming back therefore opens the current month again.
- Back closes the screen on top and never the last one: Navigation 3 throws on an empty back stack, and the process that would die is the one the trip service runs in (`closeTop`). A screen's own Back arrow closes that screen only while it is on top (`closeIfOnTop`), because a screen that is closing stays on the display, arrow included, for the length of the transition.
- **Every screen starts with the same top line** (`AppHeader`, since 2026-10-07): the app's mark, "MilO" and today's date at the start, the screen's name at the end. On a screen opened on top of another, an arrowhead stands before the name, and the two are the screen's own Back arrow.
- **Three ways lead out of a screen that was opened on top of another: Android's Back, the screen's own Back arrow, and a button of the bottom bar.** All three are carried out by `MiloApp`, and none by a feature. That is why "Leave without saving?" is asked there: a screen says whether it holds something typed and not saved (a plain function it is handed; today only the edit screen calls it, with the answer of the pure `holdsUnsavedWork`), and while it does, every way out puts the question first and is taken only after "Discard" (`UnsavedWork`, pure values and functions with a unit test; `leaveTop` in `MiloBackStack.kt` is where each way leads). A screen that asked for itself would cover its own arrow and nothing else. The question is saved with the screens, so turning the phone keeps it; after Android has ended MilO the form is gone, the screen says so, and nothing is asked.
- **What one screen has to tell another goes through `MiloNavigation`, as a plain value.** There is one such value: when the trip that was just saved on the edit screen starts. The edit screen hands it over as it closes, the Trips screen is given it, shows the month the trip is in (`monthOfSavedTrip`) and says that it has. The two features still know nothing of each other.
- **The keyboard takes room and moves nothing.** `MainActivity` is declared `adjustResize`, and since MilO draws edge to edge that means Android leaves the window where it is and reports the keyboard's height. `MiloApp` gives that height up at the bottom of every screen (`imePadding`, after marking the room of the bars as used, so that the keyboard's height is not added on top of the bottom bar it covers). Without the declaration Android slid the whole window up, and the form's text was drawn through the status bar.
- **One look, and it is dark from the first frame on** (2026-10-06, the owner's "Bento" design). `MiloTheme` in `core/designsystem/theme/` no longer asks whether the phone is set to light or dark. Three places have to agree for that, and each says so in a comment: the theme's page colour (`Color.kt`); the window's background in `res/values/colors.xml`, which Android paints before Compose exists, so that a launch does not flash white (a unit test keeps it equal to the page colour); and `MiloSystemBarStyle`, which `MainActivity` hands to `enableEdgeToEdge`, so that the icons of Android's own two bars are light whatever the phone is set to. There is no `values-night` folder any more: one window theme is enough. The bottom bar is drawn by the design system as a floating bar and leaves the room Android's own bottom bar needs by itself, as Material's bar did.
- **The typeface is a file the app carries, and it is named in one place** (2026-10-06). The screens are set in Sora, the design's typeface: `res/font/sora.ttf`, a variable font, which is one file that holds every weight. `Type.kt` in `core/designsystem/theme/` names it once, as a family of four weights drawn from that one file, and every text style is built on that family. No screen and no component names a font. The one exception is decided: the Log's time and tag are set in the phone's own monospace (no screen uses those two styles yet). The font is someone else's work, carried as a file and not fetched as a library, so its licence is in the repository: `licenses/Sora-OFL.txt`, the SIL Open Font License 1.1. The notifications do not use it: Android draws a notification itself. The PDF does since 2026-10-07: `platform/report/ReportPdf.kt` reads the same file (`Resources.getFont`) and tells each paint its weight on the font's axis (`setFontVariationSettings`), as `Type.kt` does for the screens. A character Sora does not have, such as the narrow space Android puts before "a.m.", is drawn from the phone's own font, as on the screens.
- **Home's tiles are worked out, not stored** (2026-10-06, Home as the design draws it). `HomeViewModel` keeps no state of its own. It reads five things: the trip controller's `TripActivity`, the setup checklist's rows, the stored trips of this month and of last month, the stored settings (which truck is paired, the hold-off after End, the reminder's switch and day), and the list of sent reports; and it is handed the address lookup's start of a trip in progress. Pure functions in `feature/home/` turn them into what the screen draws, in one step (`homeUi`), so that every tile of one frame is made from the same trip state. Nothing is counted by a rule of Home's own: today and the month go through `todayTrips` and `categoryTotals`, and the report tile through the monthly reminder's `judgeReminder`, fed by `reminderMoment` in `platform/reminder/`, which the notification uses too. The truck's tile is one enum of states and one function that chooses among them (`TruckState.kt`): a new state is one constant and one line. The two states beside a parked truck were added that way (`TripActivity.parked`, the parked rule's word).
- **Home plays the truck's arrival in the screen, not in the trip engine** (2026-10-07). The design's "Connecting…" is no state of the truck: Android reports a connection only once it is made. So it is played by Home, for a fixed time, over what the view model publishes. `ShownArrival` (`feature/home/`) is a holder of plain values: what was seen, which stage is showing, and when that stage runs out. `homeShown` is a flow that feeds it three things, each new `HomeUi`, whether the screen is resumed, and each press of Start, and sends on what to draw (`HomeShown`); it waits for a stage to run out with a coroutine delay and nothing else. `HomeScreen` collects it for as long as it is in the composition. Leaving the screen, or the screen no longer being resumed, starts the holder afresh, which is why only what happens in front of the owner is ever played. The view model, the trip controller and everything under `core/trip/`, `platform/trip/` and `platform/bluetooth/` know nothing of it, and a trip is recorded from its first second whatever Home shows. The movement inside the tile belongs to the design system (`TruckLinkMotion.kt`): Compose's `InfiniteTransition`, its values read while drawing only, composed only while the drawing is in view and its screen is resumed.
- **A screen that shows something Android does not report changes of** (permissions, settings, the phone's paired devices, which month is the current one, which day is today) **reads it again every time it comes to the front,** with the shared `CameToFrontEffect` (`core/designsystem/component/`). It acts on two signs. The screen resumes: Navigation 3 gives each screen a lifecycle of its own, so that happens when the screen is entered and whenever MilO returns from a settings screen or a system dialog. Or MilO's window gets the focus back: the quick settings panel and the notification shade cover MilO without pausing it, so nothing resumes when they close. The pairing screen has a third sign of its own, Android's broadcast that Bluetooth has finished switching on or off (`platform/bluetooth/BluetoothSwitch.kt`), because that happens a second or two after Shawn is back.
- **How a time of day is written is read by the screen itself, like the language.** Whether the phone is set to 24 hours (Android's "Use 24-hour format") is asked by `rememberTwentyFourHourClock` in `core/designsystem/component/`, when a screen is drawn and again each time it resumes, and handed to the pure formatters in `core/util/TimeFormat.kt` and to the time picker. It is presentation and nothing else, so no ViewModel stands in between; it is the one setting of the phone that a Composable reads for itself, and no other may be added this way.

**The setup checklist is shared, like the trip state.** `platform/system/SetupChecklist` holds the rows of the checklist as one flow. The Setup screen shows the rows; the home screen only asks `needsAttention` of them. What the phone reports is read when a screen asks; Shawn's confirmations (the settings store) and the truck's pairing (`TruckPairing.status`) arrive by themselves. The five facts that stop a trip from being recorded are read by `TripPreflight.facts()`, which the trip service's starter also uses.

One exception, because Android gives no other way. The components Android creates itself have no constructor MilO can call: `TripService`, `TruckCompanionService`, `TruckBluetoothReceiver`, `TruckReconcileReceiver`, `DrivingReceiver` (where the phone's driving detection reports to), `ReminderReceiver` (where the monthly reminder's daily alarm arrives), `NothingRecordedReceiver` (where the daily check's alarm arrives), `HomeWidgetProvider` and `HomeWidgetActionReceiver` (the home-screen widget's, since 2026-10-07) and `MiloCarAppService` (the Android Auto screen's service). Each fetches the container from the application object (`(application as MiloApplication).container`). `TripNotifications` names `MainActivity` as the screen a tap on the trip notification opens. (The reminder's notification opens it too, and is handed the class by `ReportObjects`, so `platform/reminder/` imports `app/` only for its receiver. The daily check's notification is handed it by `CheckObjects` in the same way.) Those are the only places where `platform/` imports `app/`, and no other class may reach for the container this way. **`MiloBackupAgent` is created by Android too, and is the one such component that must not fetch the container:** in the process Android starts for a backup or a restore the application object is not a `MiloApplication` (above).

---

## 4. Surface split (phone UI / Android Auto screen)

MilO has one platform and two UI surfaces. Both show the same trips and drive the same trip logic.

| Concern | Phone UI | Android Auto screen | Shared? |
|---|---|---|---|
| UI layer | Jetpack Compose screens, Material 3 | Car App Library screen, projected from the phone | No — surface-specific |
| What it shows | Everything: trips, settings, checklist, event log, reports, the monthly reminder's notification and the daily check's ("No trip recorded today") | Tracking status, current trip km and duration, and one line of Home's figures: today's and the month's Business km and the month's dollars (since 2026-10-09; today's session count and total km before) | — |
| Which trips count, and today's totals | Business first, Personal apart | Business only, today's and the month's, in the Business row (since 2026-10-09; every finished trip in one figure before) | **Yes — `isCounted`, `categoryTotals` and `todayTrips` in `data/trip/TripTotals.kt`, used by Home, Trips and the car screen. Every total on either surface is added up by `sumOfTenths` (`core/util/`), the rule of the report for the accountant** |
| Business or Personal | Shown, and changed by hand, on Trips; shown on Home | The Business totals only, since 2026-10-09; a trip's own category is not shown | **Yes — the rule is `core/schedule/`, the stored result is on the trip** |
| Trips added or edited by hand | Typed in and changed on the edit screen; marked on Trips | Neither shown nor changed. Counted in the Business row like any finished Business trip | **Yes — the rules are pure functions in `data/trip/`, and `isCounted` knows no difference** |
| Manual trip control | Start/Stop button, and since 2026-10-07 the home-screen widget's Start trip / End trip | Start Trip / End Trip | **Yes — every one drives the same trip logic** |
| The month's Business dollars (since 2026-10-09) | On Home's month tile, beside the Business share; on the widget since 2026-10-07 | In the Business row, after the month's km | **Yes — `allowanceCents` in `core/allowance/`, at the one rate in the settings, always from kilometres (`categoryTotals(..., KILOMETRES)`)** |
| Home-screen widget (since 2026-10-07) | The status line, the open trip's km and running time, the one button, and this month's and this year's Business km priced at the rate set in Settings | None: Android Auto has no widgets | **Yes — the status, the trip's figures and the button are `carScreenContent`'s, the car screen's own; the pricing is `core/allowance/`** |
| Kilometres or miles (since 2026-10-07, evening) | Chosen in Settings; every screen writes distances in it | The two rows write their distance in it; a change is drawn at once | **Yes — one stored setting, held in memory by `ShownUnit` (`data/settings/`), and one rule, `tenthsOf` and `sumOfTenths` with a `DistanceUnit` (`core/util/`). The words for a unit are chosen in `core/designsystem/text/DistanceWords.kt` for both** |
| Trip rules, distance, formatting | | | **Yes** |
| Storage access | | | **Yes — repositories in `data/`** |
| System services | | | **Yes — `platform/`** |
| Auth | | | None on either (section 7) |

**Rule:** everything that isn't presentation is shared. Two copies of trip logic *will* drift. (See STANDARDS §9.)

The shared trip logic is placed by ADR-002: the pure rules (state machine, distance, point filter) in `core/trip/`, and the `TripController`, the foreground service and location recording in `platform/trip/`. The Car App Library classes live in `platform/car/`: the `CarConnection` watcher, the screen, and the app-wide watch that writes Android Auto's connection changes to the event log outside trips.

**How the car screen is put together.**

```
 Android Auto (the app on the phone)
        |  binds, when MilO is opened on the car's display
[ MiloCarAppService ]    checks who is connecting
        |
[ MiloCarSession ]       one per visit; writes its life to the event log
        |
[ TripStatusScreen ]     while the car shows it, follows:
        |                    TripController.activity    (platform/trip)
        |                    the month's trips          (data/trip; counted by todayTrips, categoryTotals)
        |                    the settings, read only    (the rate)
        |                    SetupChecklist.rows        (platform/system)
        |                    the clock
        |
  businessFigures()      pure: today, the month and its dollars, as Home has them
  carScreenContent()     pure: decides what is shown
        |
  one pane: Status / This trip / Today, and one button
        |
  the button  ->  TripController.onTrigger
```

- **The service runs in MilO's one process,** the same one as the trip service. Being shown on a car does not make an app "in front" for location, so the car screen records nothing: it shows what the trip service records.
- **The screen holds no state of its own** beyond what it last drew. Android Auto creates and destroys sessions as it likes, and each new one builds itself from the shared objects.
- **`carScreenContent` is the only place that decides what is shown.** Its result is already rounded to what is printed, so "has anything changed?" is a plain comparison, and the screen redraws only then.
- **Three fixed row titles and no second screen.** A car counts a redraw as a harmless refresh only while the header's title, the number of rows and every row's title stay the same; anything else uses up one of five steps, after which the car closes the app. This is why the changing words are under the titles and never in them.


**How the home-screen widget is put together** (since 2026-10-07; a phone surface, but drawn from the car screen's own decisions).

```
 the home screen app  --- asks for a drawing (added, a reboot, every 3 hours) --->  [ HomeWidgetProvider ]
        |                                                                                  |
        |                       while MilO's process runs:                       HomeWidget.refresh()
        |                       [ HomeWidget ] follows                                     |
        |                           TripController.activity          (platform/trip)       |
        |                           this year's trips                (data/trip)            |
        |                           the month in force (looked at hourly)                  |
        |                                  |                                               |
        |                      homeWidgetContent()  pure: carScreenContent() + the dollars (core/allowance)
        |                                  |
        |                      homeWidgetViews()    RemoteViews: status, km, running clock, dollars, one button
        |
  Start  ->  PendingIntent for TripService (MANUAL_START)       End  ->  HomeWidgetActionReceiver -> TripController.onTrigger(MANUAL_END)
```

- **It decides nothing about a trip** and is handed no way to write one. Its two buttons send the triggers the app's buttons send, under sources of their own in the event log.
- **Its running figures are drawn at most every 15 seconds,** as on the car; everything else at once. The running time is a `Chronometer`, which the home screen advances by itself.
- **Its switch is a component state.** Off, `HomeWidget.applySwitch` draws every widget as switched off and then disables `HomeWidgetProvider`, which takes the widget out of the phone's list. The stored switch is applied again at every process start, after the reconcile, so a restore of the settings file takes effect.
---

## 5. Folder structure

One Gradle module, `:app`. Packages under `com.shawnkowalchuk.milo`:

```
app/                 # MiloApplication, the AppContainer (with ReportObjects, TransferObjects,
                     #   CarObjects, CheckObjects, WidgetObjects, WhatsNewObjects and
                     #   ClockObjects, the parts of it around the report, around backup,
                     #   export and import, around Android Auto, around the daily "nothing
                     #   recorded" check, around the home-screen widget, around the version
                     #   with its list of changes, and around MilO's clock), MainActivity,
                     #   the navigation
                     #   host, and the question before a screen is left with unsaved work
feature/<name>/      # one package per feature: its Composable screens, its ViewModel,
                     #   its feature-only logic. Today: home/, trips/, tripedit/, report/,
                     #   setup/, pairing/, eventlog/, settings/, onboarding/ (the first
                     #   start's page, since 2026-10-08), whatsnew/ (the Version tile and
                     #   the What's new screen, since 2026-10-08)
core/designsystem/   # theme tokens (colour, spacing, typography, shape), the shared base
                     #   components, and in text/ the words two features must say alike
                     #   (what a trip is saved as, where it went, whether a month's
                     #   report was sent). The only core package that knows Android's
                     #   resources. One file of text/ knows a type of platform/address
                     #   (what is known about one end of a trip); nothing else in core
                     #   imports from platform
core/trip/           # the trip rules: state machine, point filter, distance, trip closing,
                     #   and the parked rule with its watch on a parked truck.
                     #   Pure Kotlin, no Android imports, unit tested (ADR-002)
core/schedule/       # the work schedule and the rule that makes a closed trip Business or
                     #   Personal, at its close and again when its times are changed by hand.
                     #   Pure Kotlin, no Android imports, unit tested. Never used by
                     #   core/trip/: the schedule has no say in whether a trip starts
core/report/         # the report for the accountant: a period, the report as plain values,
                     #   every word and figure of the PDF, where each stands and on which
                     #   page, the CSV text, the email's subject and the file's name. Pure
                     #   Kotlin, no Android imports, unit tested
core/clock/          # MilO's own clock (ADR-006): the rule by which it follows the phone's
                     #   clock or keeps its own time, the anchor it works from, and what it
                     #   tells about a change. Pure Kotlin, no Android imports, unit tested.
                     #   It never reads a clock itself: the two it compares are handed in
core/allowance/      # Business kilometres priced at one rate per kilometre, for the
                     #   home-screen widget's reference figure: the rate out of the box and
                     #   its limits, reading and writing a rate, whole dollars. Pure Kotlin,
                     #   no Android imports, unit tested
core/util/           # pure Kotlin helpers with unit tests: formatting of distances, times
                     #   and lengths of time, a month, a day or a run of days as a span of
                     #   stored time, the unit a distance is shown in (DistanceUnit:
                     #   kilometres or miles), and the one rule by which trips are added up
                     #   in either (tenthsOf, sumOfTenths), for the report and every screen
data/                # the only layer that touches storage. The two Room databases, and one
                     #   sub-package per kind of data, each with its entity, DAO and repository:
                     #   trip/ (with the rule for which trips count, the totals by Business
                     #   and Personal, the changes Shawn can make to a closed trip, the rules
                     #   for editing a finished trip, restoring what was recorded and adding
                     #   a trip by hand, and the catch-up that sorts trips recorded before
                     #   there was a schedule),
                     #   point/, eventlog/ (with the trimming of old lines, and the log as
                     #   a text file), settings/ (DataStore, the values the Settings
                     #   screen offers, how the schedule is kept, the report's four settings,
                     #   the report that waits for "Did you send it?", the monthly
                     #   reminder's values, and the daily check's), crash/ (crash files),
                     #   clock/ (the file the anchor of MilO's clock is kept in),
                     #   sound/ (MilO's copies of the chosen sounds), changelog/ (the list
                     #   of versions and their changes built into the app, read from its
                     #   assets: `app/src/main/assets/changelog.json`),
                     #   report/ (the list of sent reports, the rule for which trips a
                     #   report lists, the rules for "submitted", for what removing a report
                     #   does and for "changed since it was sent", and the files that are
                     #   handed to other apps),
                     #   transfer/ (the export file: its form and version, the mapping
                     #   between it and the tables, what an import refuses, the writing
                     #   and the reading of it without holding it in memory; the only SQL
                     #   that replaces whole tables; MilO's copy of a picked file, the
                     #   note that an import has begun, the safety copies, the backup
                     #   agent's notes, and the step that brings a database into one file
                     #   before a backup)
platform/            # the only layer that touches Android system services: Bluetooth,
                     #   companion device, location, driving detection, notifications, alarms,
                     #   audio, Android Auto
platform/trip/       # TripController, TripService, GPS recording, notifications (the driving
                     #   alert's too, because its tap starts the trip service), the sound
                     #   (the player, and the choosing of an audio file of Shawn's own). The
                     #   ledger sorts a trip into Business or Personal as it closes it.
                     #   TripParking keeps the wait beside a parked truck
platform/address/    # the start and end address of each trip: the geocoder, the passes over
                     #   the trips that still lack one, the rule for giving up (and for
                     #   keeping away from an address typed by hand), the one-line form of
                     #   an address
platform/report/     # the report's two meetings with Android: drawing the PDF's pages, and
                     #   the requests that hand a file to the email app, a viewer or the
                     #   share sheet (the event log's text file goes the same way). Also
                     #   where the report's words are read from resources
platform/bluetooth/  # the triggers: the Bluetooth receiver, the companion service, the boot and
                     #   update receiver; the reading "is the truck connected?"; pairing
platform/car/        # the Android Auto screen (its service, session, screen, and the pure
                     #   function that decides what it shows), the watch on Android Auto's
                     #   connection, and the app-wide use of that watch which writes each
                     #   change to the event log while no trip is being recorded (with the
                     #   pure rule for "a line or silence"). That one can do nothing else,
                     #   and a failure in it is caught there
platform/driving/    # the driving alert: the request to the phone's driving detection, the receiver
                     #   its reports arrive at, and the pure rule that decides whether a report
                     #   becomes a notification. It never starts a trip, and is given no way to.
                     #   A failure in it is caught there and never reaches the recording
platform/reminder/   # the monthly reminder: the pure rule that decides whether it is shown,
                     #   the daily inexact alarm and the receiver it arrives at, and the
                     #   notification whose tap opens the Report screen. It reads sent
                     #   reports and trips and writes neither. A failure in it is caught
                     #   there and never reaches the recording
platform/nothingrecorded/  # the daily "nothing recorded" check: the pure rule that decides
                     #   whether MilO says that a work day has no trip, the daily inexact
                     #   alarm and the receiver it arrives at, and the notification whose
                     #   tap opens Home. It reads the settings and the stored trips and
                     #   writes no trip. A failure in it is caught there and never reaches
                     #   the recording
platform/transfer/   # Android's backup and the manual export and import: the backup agent,
                     #   the first start after a backup or a restore, the pure rule for
                     #   what becomes of the truck's pairing when settings arrive from
                     #   elsewhere, the export and the import with their steps and their
                     #   state, the start after an import that the process was ended in
                     #   the middle of, and the picked file through the content resolver.
                     #   Nothing in it can start, end or change a trip that is being
                     #   recorded, and
                     #   a failure in it is caught there
platform/widget/     # the home-screen widget: the provider Android knows it by, the receiver
                     #   of its End button, the pure function that decides what it shows
                     #   (from the car screen's), its drawing as RemoteViews, and the object
                     #   that keeps it in step with the trip and applies its switch. It
                     #   reads the trip and the trips and writes neither; its buttons reach
                     #   the trip controller as every other button does
platform/clock/      # the Android side of MilO's clock: the one place that reads the phone's
                     #   wall clock, the phone's time since boot and its boot number, the
                     #   one clock of the process; the receiver for "the phone's clock was
                     #   set"; and the watch that writes a change to the event log and has
                     #   the two daily alarms asked for again. It can touch no trip
platform/system/     # what the phone's permissions and settings say: the preflight check
                     #   before the service is started, the setup checklist's facts, rules
                     #   and shared rows, the opening of the phone's settings screens, the
                     #   version installed, and which app installed MilO (InstallSource,
                     #   2026-10-09: Google Play's copy shows no Buy me a coffee tile)
platform/diagnostics/  # crash and kill capture into the event log, and its trimming at start
```

Outside the app module, `website/` is the website (ADR-003), under the name "MilO Trip Log": `index.html`, `compare.html`, `changes.html` (What's new, written by `tools/changes_page.py` from the app's list of changes; never edited by hand), `privacy.html`, `404.html`, `styles.css`, `mark.svg` (the app's mark, from its launcher icon), `screenshots/` (the phone's screenshots and the sample report page, which the README shows too; until 2026-10-08 in `docs/screenshots/`), `fonts/` (a copy of the app's `sora.ttf` and its licence) and, since 2026-10-09, `mascot/` (the mascot's three pictures for the front page's lime tile, drawn by `design/mascot/`: him standing; his two dials without their needles and the needles alone, which the stylesheet turns all the time; and the frames of his wave, which it steps through for as long as a pointer is on him; still no script). `firebase.json` makes it Firebase Hosting's public folder, with three short addresses (`/privacy`, `/compare`, `/changes`, as rewrites; no `cleanUrls`, which would send every `.html` address through a redirect, Google's verification file among them), a Content-Security-Policy that allows nothing but the site's own files, and cache times; `.firebaserc` names the Firebase project, `milotriplog`. `tools/` holds scripts that are run by hand and are not part of the build. Today there are two: the script that synthesises the two built-in sounds, the connect sound's chirp and the trip-start sound's chime; and `changes_page.py` (since 2026-10-08), which checks the list of changes and writes the website's What's new page from it, and which CI and the website's deploy run with `--check`. `licenses/` holds the licence of what the app carries that is someone else's work and is not a library. Today there is one: the font's, `Sora-OFL.txt`.

Also outside the app module, `design/mascot/` is the mascot as Blender builds it (ADR-007): `milo_mascot.blend`, four Python scripts that write it from nothing (`scripts/`), the picture it was built from, a picture of the result, and `export/milo_mascot.glb`, the model with all twelve clips, which nothing uses today. None of it is part of the build, and its `README.md` says how it is used. Two more scripts make what the app carries: `render_greeting.py` draws the greeting's frames in Blender, and `pack_greeting.py` packs them into the two pictures in `app/src/main/res/drawable-nodpi/` (a sheet of the walk's frames, and the turn and wave as an animated picture). Those are committed, as the built-in sounds are (`tools/make_trip_start_chirp.py`). Two more again draw any other clip the same way (since 2026-10-09): `render_clips.py` and `pack_clips.py`, which made the three pictures kept in `design/mascot/renders/` for later (flexing, the peace sign, the horns) and, with `render_dials.py` for the dials and their needles (since 2026-10-10), the website's three in `website/mascot/`, committed too.

The Android entry points (`MiloApplication` and `MainActivity`) live in `app/`. No class sits in the root package. Features never import from each other. Shared code moves to `core/` or `data/`. Kotlin files are PascalCase and named after their main class. No file over about 300 lines, no Composable over about 200. (STANDARDS §3.)

---

## 6. Data model

What phases 1 to 3 store so far is built and described here exactly. Later phases add to it, each change as a Room migration: the phone holds real trips from phase 1 on, so no table is ever dropped and rebuilt. The source of truth for the tables is the schema Room exports to `app/schemas/` at every build, one file per version; those files are committed.

**Migrations** are in `data/MiloMigrations.kt`, one step per version, handed to Room by `buildMiloDatabase`. A step only adds: a column, an index, or a whole new table. There is no destructive fallback: a version without a step makes the database fail to open, loudly. Room runs a step, checks the result against the tables the code declares and stores the new version number inside one transaction; if the step or the check fails, the whole transaction is rolled back and the file stays at its old version with every row (seen on an emulator, FINDINGS_LOG 2026-10-05). `MiloMigrationsTest` checks each step against the exported schema files: the statements it runs must be exactly what the newer file has more than the older one, and a new table must be made by the very statement Room would make it with. A database that is more than one version behind is taken through every step in order (version 1 to 2 to 3 was run on an emulator on 2026-10-06, later that day versions 1 and 2 each to version 4, and then versions 1, 2, 3 and 4 each to version 5). A migration runs the first time anything in MilO touches the database, which after an update can be before MilO is opened: Android starts an app that is not force-stopped to tell it that it was updated.

**An older build cannot open a newer database.** A build made for version 2 that finds version 3 has no step back and no destructive fallback, so it dies at its start with "A migration from 3 to 2 was required but not found", a build made for version 3 that finds version 4 dies the same way, with "from 4 to 3", and a build made for version 4 that finds version 5 with "from 5 to 4" (all three seen on an emulator, FINDINGS_LOG 2026-10-06). The file is left as it is, and the newer build installed again reads everything. Every build so far calls itself `versionCode` 1, so Android does not stop the older build from being installed. **So no build from before 2026-10-06's Report screen may be installed over this one** (section 9; `docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check").

Conventions that hold everywhere: times are wall-clock milliseconds since 1970 unless a column says otherwise; distances are metres (kilometres exist only on screen); a column that holds one of a fixed set of values stores the Kotlin enum's name as text, so a constant can be added without a migration but never renamed without one.

**A new constant only works going forward.** A build from before the constant existed cannot read a row that holds it: Room's generated code throws `IllegalArgumentException: Can't convert value to enum, unknown value` as soon as a query returns the row, and nothing catches that. The database version does not protect here, because it did not change, and every build so far calls itself `versionCode` 1, so Android installs an older one over a newer one without complaint. The first such constant is the trip status `DELETED` (2026-10-06). **So no build from before 2026-10-06 may be installed over this one while a trip is deleted:** restore every deleted trip first, or do not go back (seen on an emulator, FINDINGS_LOG 2026-10-06; `docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check"). No data is lost if it happens: the newer build reads everything again. Whoever adds a constant to a stored enum writes the same warning for it.

**The second such constant is the event log category `DRIVING` (2026-10-06).** A build from before the driving alert that reads a `DRIVING` line dies the same way: on its Log screen, as soon as the newest 200 lines include one, and the trip service runs in the same process. Such a line is written at the first start of the newer build ("Driving alert: watching for driving…", or why not) and cannot be removed from the app. **So no build from before the driving alert may be installed over this one at all** (`docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check"). Not tried on an emulator: it rests on the case above, which was. No data is lost if it happens; the newer build reads everything again.

### Main database: `milo.db` (`data/MiloDatabase`, version 7)

Version 1 was phase 1 as first installed on the phone. Version 2 (2026-10-05) added the four address columns of `trips` and the index on `startedAtMs`. Version 3 (2026-10-06) added the four columns of `trips` that hold Business or Personal: `category`, `categorySetByHand`, `ranPastSchedule` and `ignoredOutsideSchedule`. The step from 2 to 3 adds the columns and sorts no trip: every trip stored before it has an empty `category`, and the catch-up at the same process start fills it in. Version 4 (2026-10-06) added the seven columns of `trips` for a trip that is added or edited by hand: the marks `addedByHand`, `editedByHand`, `startAddressByHand` and `endAddressByHand`, and `recordedStartedAtMs`, `recordedEndedAtMs` and `recordedDistanceMetres`, which keep what MilO recorded of a trip that has been edited. The step from 3 to 4 adds the columns and reads or rewrites no stored value: every trip stored before it has the four marks at 0 and the three "recorded" columns empty, which is how "never edited" is stored. Version 5 (2026-10-06) added the table `sent_reports`. The step from 4 to 5 is one `CREATE TABLE` statement that names neither `trips` nor `event_log`: no column of either table is added, changed or read, and the new table starts empty, so no month is submitted after it. **Part A of phase 4 (2026-10-06) changed no table and no version:** it added queries only (a delete of one row of `sent_reports`, a delete of old rows of `event_log`, and reads), so `5.json` is byte for byte what it was and no migration runs. **Part B changed none either:** it added two DAOs of queries (`data/transfer/TransferDaos.kt`), for reading every row and for replacing the trips, the sent reports and the raw points; both schema files are byte for byte what they were. **Version 6 (2026-10-07, evening)** added one column to `sent_reports`, `distanceUnit`, the unit a sent report was printed in. The step from 5 to 6 is one `ALTER TABLE … ADD COLUMN` with the default `'KILOMETRES'`, which is true of every row written before it; it names no other table and reads no stored figure. On an emulator it ran over a database of `main`'s build that held a sent report. **Version 7 (2026-10-08)** added two columns to `trips`, `vehicleAddress` and `label`, both text that may be empty: the step from 6 to 7 is two `ALTER TABLE … ADD COLUMN` with no default, and every trip stored before it has neither. The vehicle of those trips is filled in once, at the next process start, from the truck's address in the settings file, which a migration cannot read (`data/trip/TripVehicleCatchUp.kt`). Its schema file was written by hand and checked against Room's own export in CI (FINDINGS_LOG, 2026-10-08).

**`trips`** (`data/trip/Trip`): one row per trip, open or closed, indexed on `startedAtMs`.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `startedAtMs` | integer | When the trip started: the moment MilO opened it, or, after an edit or for a trip added by hand, the time Shawn chose, on the whole minute |
| `endedAtMs` | integer, null while open | When it ended: the time of its last recorded point, or the time Shawn chose. The driving alert reads the latest one of all closed trips, to keep quiet for 5 minutes after a trip (a read only; no index, the table is small) |
| `status` | text | `OPEN`, `FINISHED`, `DISCARDED` or `DELETED`. `DISCARDED`: under the minimum distance, or a companion start that was never confirmed (a false start, whatever its distance), or, since version 3, a trip that started outside the work schedule while such trips were set to be ignored. The row is kept, not deleted. Which of the first two it was is in the event log only, in the trip's "discarded" line; the third is marked by `ignoredOutsideSchedule`. `DELETED` (since 2026-10-06, a new value of the same text column, so no migration; a build from before that date crashes on such a row, see the conventions above): a finished trip that Shawn deleted on the Trips screen. The row is kept here too |
| `startedBy` | text | `TRUCK` or `MANUAL`. A trip added by hand has `MANUAL`: nothing started it, and it was Shawn and not the truck. `addedByHand` is what tells it from a trip started with the button |
| `truckSeen` | integer 0/1 | Whether the truck was seen connected, by something that can be trusted, at any point during the trip. A manual trip the truck never joined ends by different rules. A `TRUCK` trip with 0 here was opened by the companion callback alone and is still waiting to be confirmed |
| `graceStartedAtMs` | integer, null | When the truck was found gone. Null while something holds the trip open |
| `graceDeadlineMs` | integer, null | When the grace period runs out. Set and cleared together with the column above |
| `distanceMetres` | real | Written when the trip closes; 0 while open. Or the distance Shawn typed |
| `startLatitude`, `startLongitude`, `endLatitude`, `endLongitude` | real, null | Written when the trip closes; null if no usable GPS fix was recorded, and always null on a trip added by hand. Never changed by an edit: they stay where the truck was |
| `startAddress` | text, null | Since version 2. Where the trip started, as one line such as "12 Shop Rd, Edmonton". Null until the geocoder has been asked and has found one. Written only for a `FINISHED` trip. The address lookup never replaces one that is set; Shawn can, on the edit screen (`startAddressByHand`) |
| `endAddress` | text, null | Since version 2. Where it ended, on the same terms |
| `addressAttempts` | integer, default 0 | Since version 2. How many lookups have left an address of this trip missing. At 4 (`MAX_ADDRESS_ATTEMPTS`) the trip is not asked about again. Set back to 0, with the column below emptied, when "Restore recorded values" hands a typed address back to the lookup |
| `addressLastAttemptAtMs` | integer, null | Since version 2. When the addresses were last looked up, successfully or not; null if never. The next attempt after a failed one waits 2 minutes, 1 hour, then 1 day from this time |
| `category` | text, null | Since version 3. `BUSINESS` or `PERSONAL` (`core/schedule/TripCategory`). Null means not sorted: while the trip is open, and for a closed trip recorded before version 3 until the catch-up has reached it, or closed while the settings could not be read. Written when the trip closes, from the schedule as stored then; by the catch-up, only while it is null; by Shawn's own choice on the Trips screen or the edit screen; and, for a trip that is not set by hand, again from the schedule when he changes its start (`refileTrip`). A trip added by hand gets it the same way: his choice, or else the schedule |
| `categorySetByHand` | integer 0/1, default 0 | Since version 3. 1 once Shawn has chosen the category himself, on the Trips screen or in the edit form. Nothing but another choice of his changes the category of such a trip, a change of its times included |
| `ranPastSchedule` | integer 0/1, default 0 | Since version 3. 1 if the schedule made this a Business trip and it ended after the end time of the day it started on. Written with `category` by the close and by the catch-up, and never by a choice by hand: it is kept when the category is changed, and shown only while the trip is Business. Worked out again, from the schedule as it is then, whenever a save of the edit screen or a restore changes the trip's start or end |
| `ignoredOutsideSchedule` | integer 0/1, default 0 | Since version 3. 1 if the trip was stored as `DISCARDED` for one reason only: the trip rules had kept it, it turned out Personal, and trips outside the schedule were set to be ignored. Written when the trip closes and never again. It says why a discarded trip was discarded, and means nothing once the trip is counted after all |
| `addedByHand` | integer 0/1, default 0 | Since version 4. 1 for a trip Shawn typed in on the edit screen because MilO missed it. Such a row was never `OPEN`, has no positions and no raw points, and both its addresses are his. Written once, by the insert. The report for the accountant marks such a trip with an asterisk |
| `editedByHand` | integer 0/1, default 0 | Since version 4. 1 while a time, an address or the distance of a recorded trip is Shawn's and not what MilO recorded. Set by a save of the edit screen that changed one of them; taken away only by "Restore recorded values". Never set on a trip that was added by hand, and never by a change of Business or Personal alone. The report for the accountant marks such a trip with an asterisk |
| `startAddressByHand` | integer 0/1, default 0 | Since version 4. 1 once Shawn has typed, changed or emptied `startAddress` himself, and on every trip added by hand. The address lookup then neither asks about that address nor writes to it, empty or not. Set back to 0 by "Restore recorded values", which also removes the typed address |
| `endAddressByHand` | integer 0/1, default 0 | Since version 4. The same for `endAddress` |
| `recordedStartedAtMs` | integer, null | Since version 4. What `startedAtMs` was when the trip was first edited, which is what MilO recorded. Null on a trip that has never been edited, and on a trip added by hand. Written once, by the first save that marks the trip as edited, and never changed afterwards, not by a later edit and not by a restore |
| `recordedEndedAtMs` | integer, null | Since version 4. The same for `endedAtMs` |
| `recordedDistanceMetres` | real, null | Since version 4. The same for `distanceMetres` |
| `vehicleAddress` | text, null | Since version 7. The Bluetooth address of the paired vehicle the trip was in: the one a connect named, or a reading found, while it was open (`TripLedger.noteVehicle`). Null for a trip started with the button with no vehicle connected, and for a trip added by hand; such a trip counts for the first vehicle (`drivenIn`) |
| `label` | text, null | Since version 7. Shawn's label for the trip ("Work"), at most 40 characters, or null; printed on the report as its purpose. Written only for a trip that is not open (`TripDao.setLabel`) |

At most one trip is `OPEN`. The repository enforces it: starting a trip while one is open returns the open one. The Trips screen reads the trips that started in a span of time (`observeTripsStartedBetween`), through the index on `startedAtMs`; the home screen and the Android Auto screen read today's the same way, and the Report screen the trips of its month or date range. The report reads `trips` and never writes to it. The address lookup reads the finished trips that still lack an address it may fill in (`findTripsLackingAddress`: an address that is missing and not Shawn's own) and writes the four address columns (`recordAddressLookup`, which leaves an address of his own alone even where it is empty). The catch-up reads the closed trips that have no category and were not set by hand (`findUnsortedTrips`) and writes the category and the ran-past flag of exactly those (`sortUnsorted`).

**A closed trip's status can be changed by hand, in three ways and no other** (`data/trip/TripCorrection`, carried out by `TripRepository.correct`):

| Change | From | To |
|---|---|---|
| Delete | `FINISHED` | `DELETED` |
| Restore | `DELETED` | `FINISHED` |
| Count this trip | `DISCARDED` | `FINISHED` |

Each is one `UPDATE` that sets the status and matches on the status it starts from. Nothing else on the row is written, and no raw point is touched, which is why every one of them can be undone and why Restore gives back exactly the trip that was deleted. A trip in any other status, an `OPEN` one above all, is not matched and not changed. None of the three leads to `OPEN` or starts from it, so the trip rules and these changes never write to the same row.

**A finished trip's category can be changed by hand** (`TripRepository.setCategoryByHand`): one `UPDATE` that sets `category` to the chosen one and `categorySetByHand` to 1, and matches a `FINISHED` trip that does not already have that category. The status is not written, so a kept trip that is marked Personal stays kept, whatever the setting for trips outside the schedule. A trip in any other status is not matched. What a row of the Trips screen offers comes from the same rule (`categoriesOffered` in `data/trip/TripCategories.kt`).

**A finished trip's times, distance and addresses can be changed by hand, and what was recorded put back** (`TripRepository.editByHand` and `restoreRecorded`, from the edit screen). Both go through one update, `TripDao.writeByHand`, which matches a `FINISHED` trip and writes sixteen columns and no other: `startedAtMs`, `endedAtMs`, `distanceMetres`, the two addresses and their two marks, `addressAttempts` and `addressLastAttemptAtMs`, `category`, `categorySetByHand`, `ranPastSchedule`, `editedByHand` and the three "recorded" columns. The status, what started the trip, the positions, the grace columns, `ignoredOutsideSchedule` and `addedByHand` are never written by it, and no raw point is touched. It is only ever called by `TripDao.rewriteFinished`, which reads the row, asks a pure rule what it is to become and writes that, inside one transaction; a row that would not change is not written at all.

| Change | Rule (`data/trip/TripEdit.kt`) | What it writes |
|---|---|---|
| A save of the edit screen | `editedTrip` | Only what the form changed. If a time, an address or the distance is then different from what was stored: `editedByHand` 1, and the three "recorded" columns filled from the row as it was, unless they are filled already. A changed or emptied address gets its mark. `category` and the two flags beside it by `refileTrip` |
| Restore recorded values | `restoredTrip` | The start, end and distance from the three "recorded" columns; an address that is marked as Shawn's own emptied, both marks 0, and if one was emptied `addressAttempts` 0 and `addressLastAttemptAtMs` empty, so that the lookup starts afresh; `editedByHand` 0; `category` and the two flags by `refileTrip`. Refused for a trip that is not edited, or was added by hand |

**Why the recorded figures are columns, and not worked out again from the raw points.** The start of a trip is the moment MilO opened it, which is before its first fix. Its end is its last fix before a cut-off that only the trip rules knew (the moment the truck was found gone), and fixes recorded after that are stored too. So neither time can be read back from `raw_points`. The distance could be, given the recorded end, but only while `points.db` is there, and that file is kept out of the backup on purpose (below): after a restore on another phone it is empty, and a recalculation would put 0 km on a real trip. It would also change the figure once the distance thresholds are tuned. Three columns on the row depend on neither.

**A trip can be added by hand** (`TripRepository.addByHand`, the rule is `tripAddedByHand` in `data/trip/TripAddedByHand.kt`): one insert of a `FINISHED` row with `startedBy` `MANUAL`, `truckSeen` 0, no positions, the times, distance and addresses as typed, `addedByHand` 1 and both address marks 1. It was never open, so "at most one trip is `OPEN`" is not touched, and it is counted, marked Business or Personal, edited, deleted and restored like any finished trip. The minimum trip distance and "Ignore them" are not applied to it: both are for trips MilO records by itself.

**Which trips count** is one rule in one place: `isCounted` in `data/trip/TripTotals.kt`, a `FINISHED` trip and nothing else. The month's totals on the Trips screen, today's totals on the home screen and on the Android Auto screen, and the report for the accountant with its CSV go by it (`selectForReport` in `data/report/ReportSelection.kt`, which lists the counted trips that are saved as Business and started in the report's period). `OPEN`, `DISCARDED` and `DELETED` trips are in no total, and only a `FINISHED` trip has its addresses looked up. A trip that was added or edited by hand counts like any other, with the figures it has now. **Counted trips are added up by category** in the same file (`categoryTotals`): Business, Personal, and apart from both any counted trip that is not sorted. Business and Personal are never added into one figure on the phone; the Android Auto screen still shows every counted trip of today in one.

**`event_log`** (`data/eventlog/EventLogEntry`), indexed on `atMs`.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `atMs` | integer | When the event happened. A crash or a kill is written at the next start but dated when it happened, so the log is ordered by this column |
| `category` | text | `PROCESS`, `CRASH`, `ERROR` (a failure that was caught), `TRIGGER` (a trip trigger, with the state before and after in `detail`), `SERVICE`, `GRACE`, `ANDROID_AUTO`, `TRIP`, `LOCATION`, `PAIRING`, `ADDRESS` (a lookup of a trip's addresses; added with version 2, which needed no change to the table), `DRIVING` (the driving alert: what is asked of the phone's driving detection, each of its reports, and what was done about it; added on 2026-10-06 with no change to the table or the version, see the conventions above), `REPORT` (a report for the accountant made, handed to the email app, and what Shawn answered when asked whether he sent it; added with version 5, which no earlier build can open, so the warning above about a new constant is covered by the rule about versions) |
| `message` | text | One short line |
| `detail` | text, null | Anything longer, such as a stack trace |

**Rows of `event_log` are removed in one way: the trimming at process start** (since 2026-10-06; `EventLogRepository.trim`, called by `StartupDiagnostics`). A row goes when it is older than 90 days **and** is not among the newest 1,000 rows (`trimBeforeMs`). The second condition keeps a start with a wrong clock from emptying the table. Nothing else deletes from it. The Log screen can read the lines of a few categories at a time (`observeNewestOf`, since 2026-10-07 for a list of categories: each of the screen's chips stands for one or more; no index on `category`, the table is bounded now), and the whole table is read oldest first in pieces of a few hundred rows, keyed by time and id, when the log is written to a text file (`readAfter`).

**`sent_reports`** (`data/report/SentReport`), since version 5. One row for every report Shawn has said he sent. No index: the table grows by a dozen rows a year.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `kind` | text | `MONTH` or `RANGE` (`SentReportKind`). `MONTH`: a report for a whole calendar month, the only kind that marks a month as submitted. `RANGE`: a report for a run of days Shawn chose, which marks no month, whatever days it covers |
| `firstDay` | integer | The first day of the report's period, as days since 1970-01-01. A calendar day, not a moment in time, so no time zone can move it. In SQLite: `date(firstDay * 86400, 'unixepoch')` |
| `lastDay` | integer | The last day of the period, on the same terms. It is inside the period. For a `MONTH` the two are the first and the last day of that month |
| `sentAtMs` | integer | When the email app was opened with the report: the day it counts as sent, however much later Shawn answered the question |
| `tripCount` | integer | How many Business trips the report listed |
| `distanceMetres` | real | The total the report printed, in metres: a whole number of tenths of `distanceUnit`, because the report adds up figures rounded to a tenth. For a report in kilometres a whole number of hundreds, as before. Read it back with `printedTenths`, never by rounding it in the other unit |
| `revision` | integer | 0 for the first report sent for this `kind`, `firstDay` and `lastDay`; 1 for the first that replaced it, and so on. Given inside the insert's transaction (`insertAsNextRevision`): one more than the highest number those three have so far |
| `distanceUnit` | text, default `KILOMETRES` | Since version 6. `KILOMETRES` or `MILES` (`DistanceUnit`): the unit the report was printed in. The row is listed in this unit for good, and "changed since the report was sent" adds the month's trips up in this unit to hold them against the row (`changedSinceSent`). So the unit MilO is set to later changes nothing about a sent report |

**A row is written when Shawn answers "I sent it", and at no other moment.** Android cannot tell an app whether an email was sent (section 10), so MilO takes his word, and nothing is written when the email app is merely opened. A row is never updated. It keeps the figures the report had when it was sent, which later edits of the trips do not reach. **Since 2026-10-06 a row can be deleted, by Shawn and by nothing else:** "Remove" on the Report screen, behind a question, for a report that was recorded by mistake (`SentReportRepository.remove`: the read, the delete and a look at what is left for the period, in one transaction). No other row is written by it: the reports that stay keep their `revision`, so the numbers of a period can have a gap, and the next report takes one more than the highest that is left (`nextRevision`).

**Whether a month is submitted is not stored anywhere.** It is worked out from this table by `monthSubmission` in `data/report/Submission.kt`: a month is submitted once it has a `MONTH` row, by the one with the lowest revision number (the original, or the earliest revision left if the original was removed), and the newest one is named as its latest revision. A month whose only row is deleted is not submitted, with nothing else to undo. A `RANGE` row never counts, not even one that runs from a month's first day to its last. The Trips screen's month card and the Report screen both ask that one function. The same file says which revision the next report for a period is (`nextRevision`), the rule the insert uses, and what one more report for a period would do (`sentEffect`: mark the month, be listed as a revision of a month that keeps its first date, or be listed as a date range), which "Did you send it?" says before it is answered. Since phase 4 it also says what deleting a row would do (`removalEffect`), which the question before a removal says, and whether a submitted month still has the trips of its newest report (`changedSinceSent`: the row's `tripCount` and `distanceMetres` against the month's Business trips now; it sees a changed count or total, and not an edited time or address).

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
| `truck_address`, `truck_name`, `truck_association_id` | text, text, integer | none | The paired truck and its companion device association. Written and cleared together, by `platform/bluetooth/TruckPairing`. The address is in capitals, the only form Android's Bluetooth classes accept. The id is absent on a phone without companion device support, and for a truck that came with a restore or an import and has no association on this phone yet Since 2026-10-08 the first of the paired vehicles: removing it moves the next one here |
| `more_vehicles` | set of texts | none | Since 2026-10-08. The paired vehicles beside the first, one entry each, `address<TAB>pairedAtMs<TAB>associationId<TAB>name` (the id and the name empty when there is none), read in the order they were paired (`VehicleStorage.kt`). An export does not carry it; a restore takes the association ids off |
| `trip_vehicles_filled` | boolean | false | Since 2026-10-08. Not a setting: true once the trips and the odometer readings from before several vehicles have been given the first vehicle's address (`TripVehicleCatchUp`). An import clears it, so the pass runs again over the imported trips |
| `grace_period_seconds` | integer | 120 | How long a trip waits after the truck disconnects |
| `parked_limit_seconds` | integer | 600 | Since 2026-10-06 (evening). How long a trip may go without real movement before it is closed where it last moved, even with the truck still connected. A stored number that is not positive reads as the default. Its key, and the five below, are in `data/settings/ParkedStorage.kt` |
| `parked_since_ms`, `parked_place_at_ms`, `parked_place_latitude`, `parked_place_longitude`, `parked_place_accuracy_metres` | integer, integer, decimal, decimal, decimal | none | Since 2026-10-06 (evening). The wait beside a parked truck (ADR-002, amendment 28): when it began, and where the truck stands, which is the end position of the trip that was closed there, with the time it stopped and the accuracy of that fix; for a trip that never moved, which has no end position, it is that trip's newest usable fix. Absent means MilO is not waiting. The place is written in one piece and can be missing as a whole (that trip had no usable fix). Written by `platform/trip/TripParking` when a wait begins and removed when it ends |
| `minimum_trip_distance_metres` | integer | 300 | A shorter trip is discarded |
| `distance_unit` | text | none (kilometres) | Since 2026-10-07 (evening). The unit every distance is shown in: `km` or `mi`. Absent until Shawn chooses one in Settings, and read as kilometres; a word MilO does not write is read as kilometres too. It changes nothing that is stored. Its key is in `data/settings/UnitStorage.kt`. Not in an export file: a gap, on the same terms as the widget's rate |
| `sound_enabled` | boolean | true | Whether the connect sound plays. Until 2026-10-08 it was the only sound and was called the trip-start sound; its three keys kept their names |
| `custom_sound_uri` | text | none | Where MilO's own copy of the audio file Shawn chose for the connect sound is (a `file:` address inside `no_backup/trip_sound/`); absent means the bundled chirp |
| `custom_sound_name` | text | none | Since 2026-10-06. What the file he picked was called, for the Settings screen. Written and cleared together with the key above; absent when the phone gave no name |
| `driving_off_sound_enabled`, `driving_off_sound_uri`, `driving_off_sound_name` | boolean, text, text | true, none, none | Since 2026-10-08. The same three for the trip-start sound, which plays when the truck drives off; absent address means the bundled chime. The keys are in `SettingsStore`; `TripSound` in `data/settings/SoundListStorage.kt` says which three keys belong to which sound |
| `own_sounds` | set of texts | none | Since 2026-10-07. The sounds of Shawn's own he has added, one entry each, `address<TAB>name` (the name empty when the phone gave none); both sounds choose from it since 2026-10-08. A sound in use is always read as on the list, also one chosen before there was a list. Kept in `data/settings/SoundListStorage.kt` |
| `schedule_<day>_tracked`, `schedule_<day>_start_minute`, `schedule_<day>_end_minute`, for each `<day>` from `monday` to `sunday` | boolean, integer, integer (21 keys) | Monday to Friday tracked, Saturday and Sunday not; 480 and 990 on every day | Since 2026-10-06. The work schedule: whether trips on that day can be Business, and the day's start and end as minutes since local midnight (08:00 and 16:30). An end is always after its start. All 21 are written together (`setSchedule`); the keys are spelled out in `data/settings/ScheduleStorage.kt`. A day whose stored times make no sense, which no setter can write, reads with the default hours |
| `ignore_trips_outside_schedule` | boolean | false | Since 2026-10-06. What becomes of a trip that starts outside the schedule: false saves it as Personal, true has it stored as discarded when it closes |
| `driving_alert_enabled` | boolean | true | Since 2026-10-06. Whether the driving alert is switched on. Read by the alert at every report of driving and whenever it looks at what to ask of the phone, and by the setup checklist for the Physical activity row |
| `last_driving_alert_at_ms` | integer | none | Since 2026-10-06. When the driving alert was last posted. Absent before the first alert. Written by the alert each time it posts one, and read at every report of driving: no second alert is posted within 30 minutes of it, also after a restart of the process |
| `report_name`, `report_company`, `report_vehicle` | text each | none | Since 2026-10-06. Who the report for the accountant is from: Shawn's name, his company, a description of the vehicle. Each is stored without spaces around it and is 80 characters at most; absent means not set. A report cannot be made without the name; the other two are left off the report when they are not set |
| `accountant_email` | text | none | Since 2026-10-06. The address the email app is opened with. Only something that is one email address is ever stored (`isEmailAddress` in `data/settings/ReportDetails.kt`); absent means not set, and then no report can be sent |
| `report_handed_over_kind`, `report_handed_over_first_day`, `report_handed_over_last_day`, `report_handed_over_trip_count`, `report_handed_over_tenths`, `report_handed_over_at_ms`, and since 2026-10-07 (evening) `report_handed_over_unit` | text, integer, integer, integer, integer, integer, text (6 keys, and the unit for a report in miles) | none | Since 2026-10-06. The total is in tenths of the unit the report was printed in; the unit key is `mi` for a report in miles and absent for one in kilometres, so a report that was waiting before there was a choice reads as kilometres. The report that was handed to the email app and that Shawn has not answered "Did you send it?" for yet (`ReportHandOver`): `MONTH` or `RANGE`, the period's first and last day as days since 1970-01-01, its number of trips, its total in tenths of the report's unit, and when the email app was opened. Written together before the email app is opened and removed together when he answers, or when the email app did not open. A file that holds only some of them, or values that make no report, reads as "nothing is waiting". The keys are spelled out in `data/settings/ReportHandOver.kt` |
| `reminder_enabled` | boolean | true | Since 2026-10-06 (phase 4). Whether the monthly reminder to send last month's report is switched on |
| `reminder_day_of_month` | integer | 1 | Since 2026-10-06 (phase 4). The day of the month from which the reminder is shown, 1 to 31. In a shorter month its last day stands in (`reminderStartsOn`). A stored number outside 1 to 31, which no setter can write, reads as 1 |
| `reminder_shown_for_month_first_day`, `reminder_shown_on_day` | integer, integer | none | Since 2026-10-06 (phase 4). The month the reminder was last shown for (the first day of that month) and the day it was shown on, both as days since 1970-01-01 (`ReminderShown`). Written together each time the reminder is shown, and read at every look: no second reminder is shown for that month on that day, also after a restart of the process. A file that holds only one of them reads as "never shown". The keys are spelled out in `data/settings/ReminderStorage.kt` |
| `nothing_recorded_enabled` | boolean | true | Since 2026-10-06 (evening). Whether the daily "nothing recorded" check is switched on |
| `nothing_recorded_minute_of_day` | integer | 720 | Since 2026-10-06 (evening). The time of day from which a work day is checked, as whole minutes since local midnight (12:00). A day whose work hours start later is checked from their start. A stored number that is no minute of a day, which no setter can write, reads as 720 |
| `home_widget_enabled` | boolean | true | Since 2026-10-07. Whether the home-screen widget is offered (its switch in Settings). Off: every widget on the home screen is drawn as switched off and the widget's provider component is disabled, at the press and again at every process start (`HomeWidget.applySwitch`). Not in an export file |
| `home_widget_cents_per_km` | int | 70 (absent) | Since 2026-10-07 (evening). The rate the widget prices the Business kilometres at, in cents a kilometre, set on the widget's tile in Settings; 1 to 500. A stored value outside that is read as 70. The widget follows it and is drawn again at once. Not in an export file: a gap, with the parked limit and the odometer readings (`TransferStorage.kt`, `TODO(debt)`) |
| `first_run_stage` | string | absent | Since 2026-10-08. How far the first start has got: absent until OK on the page that says what MilO does, then `setup` (Setup has its Done button), then `done`. A value a build does not know is read as `done`. Not in an export file: it is this phone's |
| `whats_new_seen_version` | string | absent | Since 2026-10-08. The versionName whose list of changes this phone was last shown. What's new opens by itself once when the first start is over and this is not the version installed; the first start's OK stores it, so a fresh install never sees it. Not in an export file: it is this phone's |
| `nothing_recorded_shown_on_day` | integer | none | Since 2026-10-06 (evening). The day the check's notification was last shown, as days since 1970-01-01. Written each time it is shown, and read at every look: no second one is shown on that day, also after a restart of the process. The three keys are spelled out in `data/settings/NothingRecordedStorage.kt`, and are read as one value, `MiloSettings.nothingRecorded` |
| `last_export_at_ms`, `last_export_with_points` | integer, boolean | none | Since 2026-10-06 (phase 4, part B). When "Export all data" last wrote a file, and whether the raw GPS points were in it (`LastExport`). Written together after an export that succeeded; absent before the first. The Settings screen shows it. The keys are spelled out in `data/settings/TransferStorage.kt` |
| `auto_start_held_off_since_ms` | integer | none | ADR-002's hold-off: the time a trip was ended by hand with the truck still connected. Absent means not held off. The time is kept because two of the three things that release the hold-off are measured from it |
| `last_process_exit_imported_at_ms` | integer | 0 | The newest process-exit record already copied into the event log |
| `confirmed_xiaomi_autostart_at_ms`, `confirmed_xiaomi_battery_saver_at_ms`, `confirmed_xiaomi_other_permissions_at_ms`, `confirmed_xiaomi_recents_lock_at_ms` | integer each | none | When Shawn confirmed a step of the setup checklist that MilO cannot read. Absent means not confirmed. The key of each step is fixed on `ConfirmedStep` in `data/settings/MiloSettings.kt` |

The time of the last driving alert, the record of the last reminder, the day of the daily check's last notification, the date of the last export, the hold-off, the wait beside a parked truck, the exit-record marker, the four confirmations and the report that waits for its answer are not settings Shawn chooses. They are small pieces of state that must outlive the process.

**What of this file travels, and what is only true of one phone** (since phase 4, part B; `data/settings/TransferStorage.kt`). Android's backup takes the whole file. An export file carries only the settings that mean the same anywhere, as the type `TransferredSettings`: the truck's address and name (since 2026-10-08 the first vehicle's only, a gap), the grace period and the minimum distance, the connect sound's switch (not the trip-start sound's, a gap like the parked limit's), the schedule and the choice for trips outside it, the driving alert's switch, the report's four, and the reminder's switch and day. **The daily check's switch and time are not among them, although they mean the same anywhere:** adding them changes the form of the export file, which needs a new format version (FINDINGS_LOG, 2026-10-06, `[DEBT]`). An import therefore leaves those two as this phone has them. **The parked limit does not travel in an export either,** although it means the same anywhere: it was added after the file's form was fixed, and adding it needs a new format version (`[DEBT]`, FINDINGS_LOG, 2026-10-06 (evening)); an import leaves the phone's own value. **Nor do the odometer readings, the widget's rate and, since 2026-10-07 (evening), the unit distances are shown in** (`[DEBT]`, FINDINGS_LOG, 2026-10-07 and 2026-10-07 (evening)). **The export file is format 2 since that evening,** for one thing only: a sent report now says which unit it was printed in. That is a record, not a setting. A file of format 1 is still read, with its sent reports in kilometres. An import writes exactly the settings that travel, in one write, and removes the seven keys of a report that waits for its answer (the six and the unit). After a restore, `forgetOtherInstallation` removes `truck_association_id` (unless Android on this phone holds an association for the truck), the four `confirmed_xiaomi_…` keys, `custom_sound_uri` with `custom_sound_name` if the file is not on this phone, and the five keys of a wait beside a parked truck, which is true of one moment on one phone. No other key is touched by either.

The store is built by `buildSettingsStore` in the same package, with no corruption handler: an unreadable file makes every read throw, and is never replaced by empty settings (that would drop the truck pairing without a trace).

### Crash files: `no_backup/crashes/` (`data/crash/CrashFileStore`)

One small text file per uncaught exception, named `crash-<time>.txt`: the time, the thread, a one-line summary and the stack trace. Written while the process dies, copied into `event_log` at the next start and then deleted. At most 20 wait at once. The folder is under the app's no-backup files, so Android never backs it up or transfers it.

### The clock's anchor: `no_backup/clock/anchor` (`data/clock/ClockAnchorStore`)

One line of five words: the layout (1), the number of the phone's boot, a time of day, the time since boot at which that time of day was true, and the probation mark, which is the word `proven` or the time since boot at which the anchor's probation began. MilO's clock works from it ("How MilO tells the time", section 3). It is read when the process starts, before anything asks the time, and written when the anchor is first set, when it is proven, when a change of the phone's clock is followed, when a date taken at a start is given up, and otherwise at most every five minutes; always whole, into `anchor.new`, which then takes the file's name in one step. A file that is missing, does not hold an anchor, or holds a line with no line end (a line that was cut short) reads as none, and the phone's clock is then taken as it is, on probation. The folder is under the app's no-backup files and is not in an export: an anchor is true of one boot of one phone.
### The chosen sounds: `no_backup/trip_sound/` (`data/sound/OwnSoundStore`)

One file for each sound on the list (`own_sounds`), `own_trip_start_sound_<number>`: MilO's copy of an audio file Shawn added on the Settings screen, 10 MB at most. Both sounds play these copies, and one copy can be both (since 2026-10-08). A new file is written beside the others under a new number, and a copy is removed only after it is off the list, so a sound in use is never touched by a choice that fails. The folder is under the app's no-backup files: a sound must never be what pushes the app over the 25 MB backup limit (below). Android's backup restores the settings without the files; the first start after a restore removes the list and the keys that name a copy for either sound (`forgetOtherInstallation`), so the Settings screen and the trip service agree that the bundled sounds are in use.

### The files handed to other apps: `cache/reports/` (`data/report/ReportFileStore`)

The PDF and the CSV of a report, such as `Mileage-2026-10-Shawn-Kowalchuk.pdf` (a date range: `Mileage-2026-10-05-to-2026-10-18-…`; a report that replaces one already sent: `…-rev1.pdf`; a CSV is never named as a revision). A file is written under another name (`….part`) and given its own only when all of it is on the disk, so a half-written report can never be attached. A report made again for the same period replaces the file. Files older than seven days are removed when the Report screen is opened.

They are in the app's cache because each can be made again from the trips at any time. Android never backs the cache up, and **empties it by itself when the phone runs short of room** (seen on an emulator: the file was gone seconds after it was made, FINDINGS_LOG 2026-10-06), so a file is looked for before it is opened and made again if it is gone. This is the one folder MilO's file provider can hand a file out of (`res/xml/report_file_paths.xml`; section 8).

Since 2026-10-06 the same folder holds **the event log as a text file,** `MilO-log-2026-10-06.txt`, when Shawn shares it from the Log screen (`data/eventlog/EventLogFiles`): one file for a day, replaced by a second share on that day, written piece by piece under the same "complete or not there" rule (`writeInPieces`), and removed after seven days with the report files. It is the same one folder the file provider hands out; nothing was added to `res/xml/report_file_paths.xml`.

### What backup, export and import keep: three folders under `no_backup/`

All three are in the folder Android never backs up or transfers (since 2026-10-06, phase 4, part B).

- **`no_backup/backup_notes/`** (`data/transfer/BackupNoteStore`): small text files the backup agent leaves for the next ordinary start: `QUOTA_EXCEEDED-<time>.txt`, `RESTORED-<time>.txt`, `NOT_SETTLED-<time>.txt`, and one `COLLECTED.txt` that each backup replaces. Each is written into the event log at the next start and then removed. At most 20 wait at once; a restore's is written whatever else waits.
- **`no_backup/import/incoming.json`** (`IncomingImport`): MilO's own copy of the file picked for an import, from the pick until the import is over or declined; 512 MB at most. One left behind by a process that ended is removed at the next start, unless the note below is there: then it is what the import is finished from.
- **`no_backup/import/begun.txt`** (`IncomingImport`, `ImportBegun`): the note that an import is about to replace the trips, written whole (under another name, then renamed) just before the transaction and removed after the import's last step. Five lines: the highest trip id the import knows, the time of its safety copy, the time and the text of the line the event log gets in that transaction, and how many process starts have tried to finish it. It is there only while an import runs, or after a process was ended in the middle of one.
- **`no_backup/safety_copies/MilO-before-import-<time>.json`** (`SafetyCopies`): a whole export, raw points included, written just before an import replaces anything; complete or not there, like a report file. An import of a picked file keeps the newest three. Putting one of them back removes that one once the phone holds it again and no other, and a start that finishes an import removes none. They are not in the cache, which Android empties by itself, and not in the backed-up files, where one of them alone could put MilO over the 25 MB.

### Two database files

Raw GPS points live in their own database file, to be kept out of cloud backup. Android Auto Backup is capped at 25 MB per app and is all-or-nothing: over the cap nothing is backed up, and no error is shown. By the research estimate, points recorded every few seconds would cross the cap within about a year and take the small, valuable trip data down with them. Since 2026-10-09 a trip stores a point every 2 seconds, not every 5: by Shawn's first week of driving (9,521 points in five days), about 83 MB a year in `points.db` where it was about 33 MB, and about 104 MB a year in an export where it was about 42 MB. Source for the split and the cap: `docs/research/2026-10-03-pdf-email-backup.md`.

Both files are in the app's standard databases folder. Room keeps three more files beside a database, seen on the emulator for `milo.db`: `-wal`, `-shm` and `.lck`.

**What Android's backup takes** (since 2026-10-06, phase 4, part B; `res/xml/data_extraction_rules.xml`, which has exclude lines only, so that everything not named is taken):

| File | Cloud backup | Transfer to another phone |
|---|---|---|
| `databases/milo.db` and `milo.db-wal` | taken | taken |
| `files/datastore/settings.preferences_pb` | taken | taken |
| `databases/points.db` and `points.db-wal` | **left out** | taken |
| `-shm` and `.lck` beside each database, `files/profileInstalled` | left out | left out |
| `no_backup/` (crash files, the chosen sound, the three folders above), the cache | never taken by Android | never taken by Android |

- **The main database is not named in the rules at all,** and that is the point. Room writes a change into the `-wal` file first and moves it into `milo.db` later; a rule that included `milo.db` by name would leave the log behind. On an emulator the `milo.db` of an install with three recorded trips was 4,096 bytes and all of it was in a `-wal` file of 716,912.
- **Beyond the rules, the backup agent moves each log into its main file before the files are collected** (`settleDatabasesForBackup`: `PRAGMA wal_checkpoint(TRUNCATE)` on a connection of its own, not Room's), so the copy is one file that is whole by itself. It waits up to two seconds for whatever else has the database open, and writes down a database it could not settle.
- **The points database is excluded with all four of its files.** A `-wal` file restored beside a new, empty `points.db` would be read as that database's own log.
- **Android applies the rules a second time when it restores,** by the kind of restore it takes it to be: a backup made on the same phone is restored by the cloud rules, whatever it holds (seen on an emulator: a transfer-mode backup held `points.db`, the restore at install left it out, and a restore through the transport in transfer mode brought it).
- **After a cloud restore `points.db` is not there,** and Room makes an empty one when it is first needed. No figure depends on it (above, "Why the recorded figures are columns").
- **A restored `milo.db` is opened by `buildMiloDatabase` like any other file:** an older version is migrated step by step, a newer one fails to open and is left as it is (both seen on an emulator, FINDINGS_LOG 2026-10-06). There is no destructive fallback.

**A raw point refers to its trip by id only.** SQLite cannot enforce a foreign key across database files, so `tripId` is a plain indexed column. Nothing removes a trip's row: a short trip is marked `DISCARDED`, and a trip Shawn deletes on the Trips screen is marked `DELETED`, both with their points kept. Any later code that does remove a row must delete its points itself, through `RawPointRepository`. A trip that was added by hand has no points at all, and an edit leaves a trip's points as they are: they are what was recorded, whatever the row says now.

**Repositories are the only way in.** `TripRepository`, `RawPointRepository`, `EventLogRepository`, `SentReportRepository`, `SettingsStore`, `CrashFileStore`, `OwnSoundStore`, `ReportFileStore`, `EventLogFiles` and, for backup, export and import, `DataExport`, `DataImport`, `IncomingImport`, `SafetyCopies` and `BackupNoteStore` are the API the rest of the app uses (`TripCategoryCatchUp`, which lives in `data/trip/` too, goes through the first, the third and the fourth like everything else); the DAOs, the DataStore and the files are not touched, or even located, from outside `data/`. Every write of the trip rules to a trip is safe to repeat, and one made to a trip that is no longer open changes nothing. The write that closes a trip also stores its category and the two flags that go with it. Six writes are made to a closed trip. The address lookup's (`recordAddressLookup`) touches the address columns of a `FINISHED` trip and nothing else. It is the one write that is **not** safe to repeat: every call moves the time of the last attempt, and a call that leaves an address missing counts one more attempt, so it is made once for each lookup and never wrapped in a retry. Shawn's own changes of status (`correct`, above) touch the status and nothing else, and a repeated one changes nothing. The catch-up's (`sortUnsorted`) touches `category` and `ranPastSchedule` of a closed trip that has no category and is not set by hand, and a repeated one changes nothing. Shawn's own choice of category (`setCategoryByHand`, above) touches `category` and `categorySetByHand` of a `FINISHED` trip, and a repeated one changes nothing. The edit screen's two (`editByHand` and `restoreRecorded`, above) touch sixteen columns of a `FINISHED` trip, each inside a transaction with the read it is based on, and a repeated one changes nothing. One write makes a closed trip that was never open: `addByHand`, an insert, which is **not** safe to repeat (a second call adds a second trip); the edit screen lets one press through at a time and leaves when it has been stored. One write makes a row in another table: `SentReportRepository.recordSent`, an insert into `sent_reports`, which is **not** safe to repeat either (a second call records a second report, as a revision of the first); the Report screen forgets the report that waited for its answer before it makes the call, and lets one answer through at a time. Since 2026-10-06 (phase 4) two writes take rows away, and each is safe to repeat: `SentReportRepository.remove`, a delete of one row of `sent_reports` by its id, which a second call finds gone; and `EventLogRepository.trim`, a delete of the rows of `event_log` that are too old to keep, after which a second call finds nothing left to remove. And one more write goes to the settings file from outside the screens: `setReminderShown`, by the monthly reminder, each time it posts its notification. **Since phase 4, part B, one thing replaces whole tables, and it is an import and nothing else:** `DataImport.replaceMain` deletes and inserts every row of `trips` and `sent_reports` in one transaction that first looks for a trip in progress and then changes nothing; `replacePoints` does the same for the raw points of the trips that were replaced. Both are safe to repeat with the same file. `event_log` is not touched by an import.

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
| Fused location (Play services) | GPS fixes during a trip, and at a lower rate while MilO waits beside a parked truck | `platform/trip/LocationRecorder` | Built. A fix every 2 seconds during a trip (5 until 2026-10-09) and every 30 beside a parked truck, 5 for ten minutes after a report of getting into a vehicle; no cached first position. The lower rate has run only in a throwaway build on an emulator |
| Android's file picker (Storage Access Framework) | Choosing an audio file for the connect sound or the trip-start sound; and, since phase 4, part B, making the file an export is written to and picking the file an import reads | `feature/settings/SettingsScreen` and `DataCard` open it; `platform/trip/PickedAudio` and `platform/transfer/PickedDocuments` read and write the file | Needs no permission: a pick lets MilO use that one file for a short while. A picked sound and a picked import file are copied at once; an export is written straight into the file that was made, and removed again if the writing fails. Ran on an emulator; HyperOS's own picker has never been seen |
| MediaPlayer (system) | The connect sound and the trip-start sound, during a trip and from the Settings screen's Play buttons; and the check that a chosen file can be played | `platform/trip/TripSoundPlayer` | Alarm-type audio since 2026-10-07 (Shawn's decision, "Sound: Always play"): it follows the alarm volume and is not muted by silent, vibrate, or a Do Not Disturb or Bedtime mode that lets alarms through. Until then it was notification-type audio, and Bedtime mode silenced it on the phone (2026-10-05). Not yet heard on the phone as an alarm (device checks NL-70 to NL-74) |
| Geocoder (system) | Start and end addresses | `platform/address/GeocoderAddressLookup` | Built; ran on an emulator. Needs network, takes no key, has no availability guarantee. A failed lookup is tried again at the next occasion, four times at most. The one place a position leaves the phone |
| ConnectivityManager (system) | Whether the phone is online, asked before the geocoder is | `platform/address/NetworkStatus` | Needs `ACCESS_NETWORK_STATE`. MilO has no INTERNET permission and opens no connection itself |
| Android Auto (Car App Library) | The in-truck screen; `CarConnection` keeps a trip open, and a wait beside the parked truck; and, outside a trip, each change `CarConnection` reports is written to the event log | `platform/car/` | Version 1.8.0-rc01 since 2026-10-06, a release candidate taken for its security fix (ADR-001, "Exception, 2026-10-06"). All three are built. The screen has never run anywhere, and may not be listed on the truck at all: distribution risk (section 10). `CarConnection` has reported a connection only on an emulator, from a stand-in for the Android Auto app (FINDINGS_LOG, 2026-10-06); what the real app reports on the phone has not been seen |
| Activity recognition (Play services) | The driving alert: "entered a vehicle" and "left a vehicle", delivered through a `PendingIntent` to a manifest receiver | `platform/driving/` | Built (2026-10-06). Alert only: it never starts a trip. Part of the location library already in the build, so no new dependency. Needs the Physical activity permission (`ACTIVITY_RECOGNITION`). On an emulator Play services accepted the request; the build as it stands has had no report delivered anywhere. Whether a report can start MilO's process on HyperOS is untested (section 10) |
| Gmail, or any other email app | Sending the report for the accountant | `platform/report/ReportHandOff` builds the request; `feature/report/ReportScreen` starts it on MilO's activity | Built. A "send this file" request with the accountant's address, the subject, a few lines of text and the PDF, handed to the app that takes a `mailto:` address: Gmail first, then any email app. MilO sends nothing and has no INTERNET permission; Shawn taps send. The app cannot learn whether it was sent. On an emulator the request reached Gmail's compose screen with a read permission for the one file; Gmail had no account there, so **no draft has ever been seen**, on any device |
| A PDF viewer; Android's share sheet | Looking at the PDF before it is sent; saving or sharing the CSV; and, since phase 4, sharing the event log as a text file from the Log screen | `platform/report/ReportHandOff` | Built. Whatever app the phone shows a PDF with (Google Drive's viewer on the emulator), and Android's own share sheet for the CSV. A phone without such an app is told so on the screen |
| FileProvider (`androidx.core`) | Letting those apps read the one file they are handed | The `<provider>` in the manifest, `res/xml/report_file_paths.xml` | Not exported. It can hand out the files of `cache/reports/` (the report files and the shared event log) and nothing else, and only to an app MilO has just handed a file's address to, with a read permission that ends when that app's screen closes. The permission is given explicitly: Android 18 stops giving it by itself |
| The phone's settings screens | The buttons of the setup checklist and the pairing screen | `platform/system/SystemScreens` | Android's own screens, and three HyperOS ones known only from other apps' source. Each is tried inside a try/catch and falls back on Android's page for MilO. Shawn went through Setup on the phone on 2026-10-05 and reported no problem; whether each button opened the right screen was not written down (device checks 54 to 66) |
| HyperOS Autostart app-op | The "looks on / looks off" reading on the setup checklist | `platform/system/SetupFacts` | A hidden Android method reached by reflection with MIUI's app-op 10008. Advisory only; any failure reads as "unknown". On the phone it answered and followed the switch (2026-10-05) |
| AlarmManager (system) | Having MilO look at the monthly reminder once a day, and ask once a day whether a trip has been recorded | `platform/reminder/ReminderAlarm`, delivered to `ReminderReceiver`; `platform/nothingrecorded/NothingRecordedAlarm`, delivered to `NothingRecordedReceiver` | **The daily check's** (2026-10-06, evening): one **inexact** alarm (`set`, type `RTC_WAKEUP`) for the moment the next day is checked from, noon out of the box; no permission; taken back while the check is switched off. On an emulator it started a MilO whose process had been ended, and was asked for again after a restart of the emulator; whether HyperOS lets it through is untested (section 10). **The reminder's:** built (2026-10-06). One **inexact** alarm (`set`, type `RTC`) for 09:00 of the next day: no permission, no exact-alarm permission declared, up to about an hour late, and not delivered to a sleeping phone until it wakes. Forgotten by Android at a reboot and a force stop, so asked for again at every process start. On an emulator it started a MilO whose process had been killed; whether HyperOS lets it do that with Autostart off is untested (section 10) |
| AppWidgetManager (system) | The home-screen widget: drawing it, asking the home screen to add it, and its provider being disabled while its switch is off | `platform/widget/` | Built (2026-10-07), never run. The home screen app draws the widget; what HyperOS's does with a disabled provider's widget is not known (device check W-9) |
| Android's backup (Auto Backup, and the transfer to a new phone) | An off-phone copy of the main database and the settings; the raw points too when a phone is moved to another | The `<application>` element of the manifest, `res/xml/data_extraction_rules.xml`, `platform/transfer/MiloBackupAgent` | Switched on (2026-10-06). 25 MB cap for the cloud, all or nothing (section 6). Android decides when, and restores only into a build signed with the same key. Ran on an emulator with Android's test transport (`bmgr`): backed up, removed, installed, restored. **Never on the phone**, and Google's own transports (the cloud, phone to phone) have not been used at all |
| GitHub | Public repo (since 2026-10-08), CI, Dependabot updates, and Dependabot alerts fed by the dependency graph workflow; the website's deploys | `.github/` | Development only |
| Google Play (since 2026-10-09, ADR-004) | A second channel for the same app, beside GitHub Releases; Play App Signing with MilO's own key | The Play Console, by hand; `platform/system/InstallSource` tells a copy from Google Play apart | Prepared, nothing uploaded. The app has no Play SDK and talks to no Google Play service |
| Firebase Hosting (Google) | The website, `milotriplog.top` (since 2026-10-08, ADR-003). Not used by the app | `website/`, `firebase.json`, `.firebaserc`, `.github/workflows/website.yml` | Static files only: no Firebase SDK, functions, database or analytics. Deployed with the deploy key in GitHub's Actions secrets (`FIREBASE_SERVICE_ACCOUNT_MILOTRIPLOG`). The domain is connected in the Firebase console, with the records it gives entered at the registrar; Firebase provides the certificate |

No Sentry, no analytics, no API keys. The website's deploy key is the one secret outside the Mac (STANDARDS §12); once MilO is uploaded to Google Play, Google holds a copy of the signing key as well (ADR-004).

---

## 9. Environments & deployment

| Environment | Backend project | Used for | URL / build channel |
|---|---|---|---|
| The phone | None | Everything: development, testing and Shawn's real trips | Since 2026-10-09 the release build: the APK of each GitHub release, installed over the one before with adb (README, "Making a release"). Until then the debug build, from Android Studio or `./gradlew installDebug`. Both are signed with the dedicated key in `~/keys/milo.jks`, so either installs over the other. Only the release build goes through R8 (ADR-008) |
| GitHub Releases | None | The APK anyone can download | `assembleRelease`, through R8 since 2026-10-10, signed with the same key, uploaded by hand with its mapping file (README, "Making a release") |
| Google Play (ADR-004) | None (Play Console app "MilO Trip Log") | The same version for the Play Store; 0.3.0 was in Google's review on 2026-10-10 | `bundleRelease`, from the same run of R8 as the APK, the same key, Play App Signing with MilO's own key (README, "Publishing on Google Play"). The bundle carries R8's mapping file and R8's own figures, the "R8 metadata" the Play Console asked for |
| The website | Firebase project `milotriplog` (Hosting only) | The public page and the privacy policy | `https://milotriplog.top`, and Firebase's own `https://milotriplog.web.app`; live on every merge that changes `website/` |

There is one environment because there is no backend to separate (STANDARDS §13). The build on the phone holds real trip data, so an uninstall is data loss.

Build and release path: a pull request runs CI (gitleaks, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`). **CI builds and tests the debug build, which R8 does not touch; the release build is proven by installing it and looking it over before it is published** (ADR-008; README, "Making a release"). After merge the build is installed on the phone, and the device test checklist (`docs/DEVICE_TEST_CHECKLIST.md`, which grows with each work package) is run there. A phase's behaviour counts as proven only once its checks have run on the phone, but the next phase does not wait for them: on 2026-10-05 Shawn decided that the phases follow one another without a stop in between (FINDINGS_LOG, 2026-10-06; STANDARDS §11). An older build is never installed over a newer one without reading section 6's two rules first: a build from before a database version cannot open that database, and a build from before a stored enum constant crashes on a row that holds it. Until 2026-10-09 not merging red was a rule Shawn followed by hand (GitHub Free cannot block a merge in a private repo, and the repo was private until 2026-10-08). **Since 2026-10-09 GitHub keeps it:** a branch protection rule on `main` requires the CI check before any merge, for Shawn too (STANDARDS §14).

---

## 10. Known constraints & decisions

Load-bearing facts from the research in `docs/research/`. Those files are dated evidence and are not kept current. Almost nothing below has been tested on this phone yet; the two items that have been (the Autostart reading, the four permissions) say so. The detailed trip-detection design is in `docs/adr/ADR-002-trip-detection.md`. Stack decisions are in ADR-001.

**Automatic trip start**
- Background location ("Allow all the time") is mandatory for automatic start. On Android 14 and later, a `location` foreground service started from the background without it throws `SecurityException`. No background-start exemption replaces it. (`2026-10-03-fgs-background-start.md`)
- Triggers must be redundant and must all feed one idempotent start function. CompanionDeviceManager presence and the Bluetooth ACL broadcast come from the same event in the Bluetooth stack, so they are two delivery paths, not two detectors, and each has gaps. (`2026-10-03-cdm-presence.md`)
- No event arrives when the truck is already connected at boot, after an app update or after the process restarts. The Bluetooth broadcast also never reaches a force-stopped app, or a phone not yet unlocked after a reboot. The app must check the real connection state at those moments. (`2026-10-03-fgs-background-start.md`)
- Trip logic must be level-triggered, not edge-triggered. Connect and disconnect events can be dropped, duplicated or delivered in reverse order, so each event is a prompt to re-read the real state, never the state itself. (`2026-10-03-fgs-background-start.md`, `2026-10-03-cdm-presence.md`)
- Receivers for Bluetooth broadcasts must be exported: `android:exported="true"` in the manifest, `RECEIVER_EXPORTED` when registered in code. The sender is the Bluetooth process, not the system. A non-exported receiver never gets the broadcast, and a trip never ends. (`2026-10-03-fgs-background-start.md`)
- The start must run directly from the trigger, with no WorkManager or delayed hop. The start allowance that comes with a Bluetooth or boot broadcast lasts about 20 seconds. (`2026-10-03-fgs-background-start.md`)

- Android has no public "is this device connected" before Android 16 QPR2 (API 36.1). On this phone the reading asks the hands-free and the audio profile which devices they are connected to, through a profile proxy each. A truck connected on neither profile reads as "not connected" for as long as it is connected. So a reading of "not connected" is held against a trip only once some reading has shown the truck connected on its present link; until then only a disconnect event ends the trip. (`2026-10-03-cdm-presence.md`; ADR-002, amendment 18)
- A reading can be "unknown" (no Bluetooth permission, no answer). Unknown is never "connected", and it must never be turned into "not connected" for a trip that is recording. (ADR-002, amendment 11)
- A request to associate with the truck goes through the pairing screen's Activity: Android shows its consent dialog on it. Android 12 refused the request from any other context. MilO no longer runs on Android 12, and the call was left as it is: it is the one that paired the truck on the phone. (FINDINGS_LOG, 2026-10-05 and 2026-10-07)

**This phone**
- HyperOS background limits: starting a dead app is gated by Autostart, which is off by default for a sideloaded app. With it off, decompiled system code shows manifest broadcasts dropped and service starts and binds rejected, which would block both the Bluetooth receiver and the CompanionDeviceManager path. Battery saver "No restrictions" is a second, separate gate. This rests on decompiled code and forum reports, so the with and without Autostart test on the POCO X5 decides it. (`2026-10-03-miui-background-limits.md`, `2026-10-03-cdm-presence.md`)
- What MilO can know about the HyperOS settings is limited. Autostart can be read only through an unofficial app-op check that is known to say "on" wrongly (on this phone it answered "allow" on 2026-10-05, and the same record showed it rejecting nine minutes earlier, so here it follows the switch); the per-app Battery saver profile, the "Other permissions" switches and the lock in recents cannot be read at all, so the setup checklist takes Shawn's word for them, with the date. Whether a HyperOS settings screen exists cannot be asked beforehand either (an app cannot see another app's screens without declaring it), so each is opened inside a try/catch with a fallback. (`2026-10-03-miui-background-limits.md`, findings 27 to 33)
- **The phone's date is set one day ahead by hand for a few seconds, several times on some evenings,** and put back by automatic time: Shawn does it to get lives in a game, and will go on doing it (the phone's own time log, 2026-10-05 to 2026-10-07; ADR-006). The time zone does not change and the time since boot is not affected. MilO therefore keeps its own clock, and nothing in it may read the phone's (section 3, "How MilO tells the time"). Whether HyperOS starts MilO for Android's `TIME_SET` broadcast has not been seen; since 2026-10-09 the daily alarms do not depend on it.
- Installing from Android Studio needs two Xiaomi-only developer switches ("Install via USB" and "USB debugging (Security settings)") and a confirmation on the phone at each install. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**Android Auto**
- Distribution risk: Google's documentation says the "Unknown sources" developer option does not apply to Car App Library apps, which must come from a trusted store to show on a real head unit. A sideloaded MilO should appear in the desktop head-unit emulator but is not expected to appear in the truck. The screen was built knowing this. **Unverified on the truck;** community reports conflict. If it does not appear, the choices are a private Google Play install, a media-app style workaround, or dropping the screen (APP_ENCYCLOPEDIA, Android Auto screen). (`2026-10-03-android-auto-screen.md`, `2026-10-03-location-and-car.md`) **Since 2026-10-09 the way out is Google Play (ADR-004):** a copy from there comes from a trusted store, once Google's review of the car screen accepts it under IoT.
- The car screen's service is exported with no permission, because Android has none for Android Auto's binding on a phone. What protects it is the Car App Library's host check, set to the library's own list of Android Auto's signing certificates in every build type. If Google changes that certificate, MilO's screen is refused until the library is updated. (`2026-10-03-android-auto-screen.md`, findings 15 and 16)
- That host check is the library's own code, so it is only as sound as the pinned library. **Since 2026-10-06 the pinned library is 1.8.0-rc01, a release candidate,** taken by Shawn's decision as the one exception to the stable-only rule: its release notes say it includes a security fix, without naming it, and tell every lower version to update. Until then MilO was on 1.7.0 and the fix was knowingly missing. Compared with 1.7.0 by taking both apart, the host check differs in one thing: 1.7.0 also lets in any host that holds the permission `android.car.permission.TEMPLATE_RENDERER`, on any device; 1.8.0-rc01 does that only on a car that runs Android itself, so on a phone only the hosts on the list are let in. Google does not say that this is the fix. **The stable 1.8.0 is to be taken the day it is released,** and a rule in `.github/dependabot.yml` keeps Dependabot from offering another pre-release of this library in the meantime. The rule hides pre-releases only, so it hides nothing stable if it is still there afterwards. (ADR-001, "Exception, 2026-10-06"; `2026-10-03-android-auto-screen.md`, finding 3)
- The screen needs Car API level 7 (the library's current `Header` class). The level is a property of the Android Auto app on the phone, and which level which version speaks is not documented. The session writes the level it was given to the event log.
- Android Auto, not MilO, decides how long a car session lives, and being shown on the car is not "in front" for location or for starting the trip service. A trip started from the car's button therefore needs what an automatic start needs: "Allow all the time", and battery use unrestricted or the companion association. (`2026-10-03-android-auto-screen.md`, findings 23 to 25)
- `CarConnection` only reports while the app is already running. It can hold a trip open but cannot start one. (`2026-10-03-location-and-car.md`) Since the wrap-up MilO observes it for the whole life of its process, for the event log only; a change while MilO is not running is still never seen, and nothing but the trip service's own watch tells the trip rules.

**Driving alert**
- Activity Recognition says "in a vehicle", never which one, and takes about a minute or more to notice. This is why its reports only ever lead to a notification, and why a trip started from the notification misses the beginning of the drive. (`2026-10-03-location-and-car.md`, findings 30 to 36)
- Reports arrive only through a `PendingIntent`, which must be mutable (Play services writes the report into it) and, on Android 14 and later, must name its target class. Without the Physical activity permission Play services reports nothing. (findings 30 and 31)
- Google does not say whether a request outlives a reboot or an update, so MilO makes it again at every process start. Nothing promises that a report is fresh either, so a report of entering a vehicle that is more than 5 minutes old is not acted on. (finding 36 for the request; the limit is a precaution and the 5 minutes a judgement, FINDINGS_LOG 2026-10-06; whether this phone hands over old reports is device check DA-12)
- The phone reports leaving a vehicle only when it detects another activity, and slow traffic is reported as a mix of driving, cycling and standing still (finding 34, low confidence). So one drive can bring several "left" and "entered" reports, which is why no second alert is posted within 30 minutes of the last and none within 5 minutes of a trip's end. (FINDINGS_LOG 2026-10-06; device checks DA-10 and DA-14)
- The delivery names MilO's receiver directly. ADR-002 noted that this might get past HyperOS's Autostart gate where the Bluetooth broadcast does not; the research itself thinks a dead app's receiver likely needs Autostart here too (`2026-10-03-location-and-car.md`, finding 32). **Untested either way.** If it gets past, the alert is a way of waking MilO that does not depend on Autostart; if it does not, the alert works only while MilO's process is alive. (ADR-002, amendment 27; device checks DA-8 and DA-9)

**Data safety**
- Auto Backup cap: 25 MB per app, all-or-nothing, silent when exceeded. This is why raw GPS points live in a separate database file (section 6). (`2026-10-03-pdf-email-backup.md`)
- **Android's backup does not always end the app first.** The documentation says that it does. On an emulator (Android 16), a backup asked for with `bmgr backupnow` while MilO was alive ran the backup agent inside MilO's own process and ended that process afterwards; with MilO not running, Android started a process for it. So the copy can be taken while Room has the database open, which is why the agent moves the log into the main file first and does not rely on the process being gone. Whether the scheduled backup behaves the same was not seen. (FINDINGS_LOG, 2026-10-06)
- **Android ends MilO's process when a backup has been collected,** also a MilO that was alive in the background. Its schedule passes over an app with a foreground service, so a recording is not ended; `bmgr backupnow` does not look.
- **A restore cannot be tried on the phone.** It happens at install, and installing afresh means removing MilO and its trips. It is first met when the phone is replaced. The export file is the copy that can be checked beforehand, on a computer.
- **The points reach a new phone only by a restore that Android treats as a transfer.** The rules are applied again at restore time (section 6).
- Signing-key risk: every build for the phone is signed with one dedicated key (`~/keys/milo.jks`, password in the macOS Keychain). A build signed with any other key cannot update the installed app. The only way forward would be an uninstall, which deletes every trip, and Auto Backup then refuses to restore. So the keystore and its password must be backed up off the Mac, and a manual export is the only copy of the trips that does not depend on the key. (`2026-10-03-pdf-email-backup.md`)

**Addresses**
- Android's geocoder needs a network and promises neither an answer nor a right one; on this phone it is Google Play services. An address is a label, and the stored coordinates stay the record. (`2026-10-03-location-and-car.md`, findings 17 to 20)
- The research's retry design was a WorkManager job with a network constraint. This build has none (WorkManager is not a dependency yet, and this package was to add none): the lookup runs at process start, when a trip ends and when the Trips screen comes to the front, and checks the network itself. A trip that ends offline therefore waits for one of those occasions. (FINDINGS_LOG, 2026-10-05)
- When a trip ends by itself the trip service stops, and HyperOS may freeze or kill the process before the geocoder answers. The trip is then caught up at the next occasion. Not measured on the phone.

**Recording**
- A fused location request combines interval and distance as AND, so "every 5 seconds or 10 m" cannot be asked for. The request was a fix every 5 seconds, and is one every 2 seconds since 2026-10-09 (ADR-002, amendment 37); the 10 m rule is applied in MilO's own distance calculation. (`2026-10-03-location-and-car.md`)
- The timers of a trip run as coroutines inside the service, as the research recommends, and no wake lock is held. A coroutine timer does not count time the phone spends asleep, so a timer can fire late when the phone sleeps between GPS fixes. Two things limit the harm: a GPS fix stands in for a late timer (for a deadline once it is 10 seconds overdue, for the minute reading 70 seconds after the last one), and the trip rules judge a late reading by the stored deadline, not by when the timer fired. With no fixes arriving, nothing stands in. Not measured on the phone yet (DEVICE_TEST_CHECKLIST).
- Recording needs four permissions: precise location, "Allow all the time", Nearby devices (Bluetooth) and notifications. The Setup screen asks for each; Shawn granted all four through it on the phone on 2026-10-05 (FINDINGS_LOG, "First results from the phone").

**The report**
- Android's `PdfDocument` is the only PDF code: no third-party library (ADR-001). It works in points (US Letter is 612 by 792), records a page as it is drawn and cannot return to one, and is not thread safe. So the layout is decided first, in pure Kotlin, and a PDF is made on one background thread at a time. (`2026-10-03-pdf-email-backup.md`, findings 1 to 7)
- Android gives an app no way to learn whether an email was sent: the request to an email app has no result. MilO therefore asks, and records only what Shawn says. (`2026-10-03-pdf-email-backup.md`, finding 14)
- The email app is chosen by a selector for a `mailto:` address, and the request itself carries no type. Both were learned on an emulator: with Gmail's package named on the request, Android asked "Gmail or Chat?", and with a type on the request, no email app was found at all (FINDINGS_LOG, 2026-10-06). What Gmail then does with the address, the subject and the attachment is Gmail's own, undocumented, and unseen until it is tried on the phone.
- The report files are in the cache, which Android empties when storage is short. A draft that waits in the email app while that happens may lose its attachment; the file can always be made again.

**The monthly reminder**
- Exact alarms need a permission that Android 14 no longer grants by itself, and a reminder to send a report gains nothing from the minute. An inexact alarm needs none and is delivered within about an hour. (`2026-10-03-pdf-email-backup.md`, finding 33)
- Android cancels an app's alarms at a reboot and when the app is force-stopped; an update of the app keeps them. So an alarm-based reminder has to ask again at boot and at app start. MilO asks at every process start, which covers both, and after each alarm. (finding 34)
- The research recommended a daily WorkManager job with a state-based decision (finding 35). The decision is built as recommended; the job is an alarm instead, because WorkManager is not in the build and this work was to add no dependency. What WorkManager would have added is that the job survives a reboot by itself.
- **Whether the alarm reaches MilO on HyperOS is untested.** The research notes that HyperOS can stop apps without Autostart, which would clear their alarms until the app next runs (finding 34, marked unverified). The reminder is therefore also looked at on every occasion MilO runs anyway, and would then appear when MilO is next opened or woken. (Device checks 217 to 219)
- The reminder needs the notification permission, like every notification of MilO's; without it the log says that nobody saw it. (finding 36)

**The "nothing recorded" check**
- It can only speak if MilO runs. If HyperOS never starts MilO, for the alarm or for anything else, no notification comes: the check then shows the moment MilO is next opened, which is too late to be of use that day. Whether HyperOS delivers an alarm to a MilO it has put to sleep, with Autostart on and with it off, is the open question it shares with the monthly reminder (above), and is **untested**. (Device checks NR-9 to NR-11)
- The alarm is `RTC_WAKEUP`, so Android wakes a sleeping phone for it, and it is inexact: Android may deliver it late, by up to an hour for an alarm a day ahead (the window `dumpsys alarm` showed on an emulator), and later still on a phone that is in its deepest sleep, lying still. A phone in a moving truck is not in that sleep.
- "No trip today" is not "detection is broken". On a work day on which the truck is not driven before the set time, the notification is a false alarm, by design, and its text says so. It cannot tell the two apart: it asks only whether a trip was started. That holds also while MilO waits beside a truck that is connected and parked, which is the one time MilO does know that the truck has not moved: the check does not ask the wait, so the notification comes then too (read from the code; the two together have run nowhere).
- It counts what MilO stored, so it is silent on the day a trip was started and recorded nothing useful: a trip discarded for being under the minimum distance counts as "a trip was started".
- A notification is posted about three seconds after the look that finds it due: the look waits 2.5 s for a trip that may be starting, and looks again. The 2.5 s are a judgement, not a measurement. On an emulator Android has brought MilO's process and its trip service back within a second after a crash (FINDINGS_LOG, 2026-10-03); **how long the phone takes to bring the service to the foreground, from a cold process above all, has not been measured**, and a service slower than the wait still gets the notification, with its sound, a moment before the trip takes it away. (Device check NR-15)
- The wait is held open by a broadcast only when the daily alarm prompted the look, and by the screen when MilO was opened. A look at a process start that a reboot or an update caused has nothing holding the process once the controller has caught up; if the phone puts MilO to sleep inside those 2.5 s, the look finishes the next time MilO runs, with a second look that is fresh. Stock Android waits longer than that before it freezes a process; what HyperOS does is **untested**.
- Android removes an app's notifications when the app has just been installed or updated. Seen on a loaded emulator on 2026-10-06: a notification posted five seconds after `adb install` was removed by Android a second later, while MilO had already stored that the day's notification was shown. On the phone this can only happen when an update is installed after the day's check time on a work day without a trip; the Log still has the "shown" line. (FINDINGS_LOG, 2026-10-06 (evening, decisions); device check NR-13)

**Build**
- **The release build goes through R8** (since 2026-10-10, ADR-008): `isMinifyEnabled` and `isShrinkResources` on the release build type in `app/build.gradle.kts`, with Android's `proguard-android-optimize.txt`, the rules each library ships inside itself, and MilO's own three in `app/proguard-rules.pro`. The debug build is as before. What the build writes beside the APK, in `app/build/outputs/mapping/release/`: `mapping.txt` (what everything was called), `configuration.txt` (every rule that applied, with where it came from), `usage.txt` (what was removed).
- **MilO's own classes, methods and fields keep their names, and its code keeps its shape:** R8 removes what MilO does not use and otherwise leaves `com.shawnkowalchuk.milo` as written. That is for the event log, which prints MilO's class names and stack traces. It also keeps stable what is stored by name: the screens of the navigation back stack are saved under their class names, and Navigation 3 looks the class up again by that name after Android has ended MilO in the background. Every exception class keeps its name too, the libraries' included. The libraries are renamed and rewritten.
- **A stack trace from the release build names the class and the method, not the file and the line.** R8 puts the id of the build's mapping file where the file name was (`r8-map-id-…`) and a number of its own where the line was, in MilO's lines as in the libraries'. The real ones come back with that release's `mapping.txt` (README, "Reading a stack trace from a release"). The id in the trace is the `pg_map_id` in the first lines of the mapping file, so a trace says which file it needs.
- **Code that is reached by its name at run time is what R8 can break,** because R8 cannot see that it is used. In MilO that is: the classes the manifest names (kept by rules the build makes from the manifest); the serializers of the `@Serializable` classes, which Navigation 3 finds by reflection (kept by kotlinx.serialization's own rules); the database classes Room finds by name (Room's rules); the Car App Library's templates, which are sent to Android Auto under their class and field names (the library's rules); and one hidden Android method MilO calls for the HyperOS Autostart reading, which is Android's code and not in the app. Stored enum names (in the databases, the settings and the export) are text inside the code and are not changed by renaming. On 2026-10-10 the first three ran in the minified build on an emulator. The Android Auto screen cannot be shown there: for it, the library's own serializer was run on a template like MilO's, there and back. The Autostart reading runs only on a Xiaomi phone (FINDINGS_LOG, 2026-10-10; device checks R8-1 to R8-10).
- The Car App Library is pinned to a release candidate, 1.8.0-rc01, the only pre-release in the build, by Shawn's decision of 2026-10-06 (ADR-001, "Exception, 2026-10-06"). `gradle/libs.versions.toml` and `.github/dependabot.yml` each say what is to be undone when 1.8.0 is stable. The Dependabot rule that goes with the pin hides this library's pre-releases only; what it does was read from Dependabot's source and has not been seen running.
- AGP 9.4.1 with Gradle 9.8.0 and Kotlin 2.4.20 is one step past what JetBrains documents. The set builds from the terminal on this Mac (2026-10-03) with no fallback. Gradle prints one deprecation notice, caused by AGP's own code (FINDINGS_LOG, 2026-10-03). The fallback and the dev-machine requirements are in ADR-001. (`2026-10-03-versions.md`)
