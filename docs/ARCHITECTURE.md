# Architecture

> **What this is.** The *map* of the system — its shape, the stack, how data flows, and how the pieces connect. Read this before building anything that touches structure. Update it whenever the structure changes. It should always answer: *"if I drop a new developer (or AI assistant) in, what do they need to know to not break things?"*
>
> **Status:** Living document · **Last updated:** 2026-10-06 · **See also:** ENGINEERING_STANDARDS.md (the rules), APP_ENCYCLOPEDIA.md (how each feature works)

Built so far: the Gradle build with its quality gates, the design system in `core/designsystem/`, the CI files, the two databases, the settings store and the crash files in `data/`, the trip rules as pure Kotlin in `core/trip/`, crash and kill capture in `platform/diagnostics/`, the recording core in `platform/trip/` (the `TripController`, the foreground `TripService`, GPS recording, the three notifications, the trip-start sound and the watch on Android Auto), and the triggers in `platform/bluetooth/`: the Bluetooth receiver, the companion service, the boot and update receiver, the reading of the truck's connection, and pairing (`TruckPairing`, which the pairing screen calls); and the lookup of each trip's start and end address in `platform/address/`. The phone UI has eight screens (`app/MiloNavigation.kt`, with their keys in `app/MiloBackStack.kt`), four of them behind a bottom navigation bar: Home (the trip in progress, one Start trip / End trip button, today's finished trips, and a warning while setup is incomplete), Trips (one month at a time, where a trip can be deleted and restored, marked Business or Personal, and opened for an edit), Setup (the permission checklist, in `feature/setup/` with its rules in `platform/system/`) and Log (the event log); and four opened from another screen: the truck pairing screen, from Setup and from Settings; Settings (`feature/settings/`: the truck, who the report for the accountant is from and where it goes, the two numbers of the trip rules, the work schedule and what becomes of a trip outside it, the driving alert's switch, the trip-start sound), from Home and from the Report screen; the edit screen (`feature/tripedit/`: a finished trip's times, addresses and distance changed by hand, or a trip MilO missed typed in), from Trips; and the Report screen (`feature/report/`: the report for the accountant for a month or a date range, as a PDF handed to the email app and as a CSV file), from Trips. **The triggers have never met the truck.** No truck is paired on the phone yet, so there they have had nothing to act on; they are covered by unit tests, and in part by an emulator with no truck. **Of the screens of work package 3, only Setup has been used on the phone,** where Shawn also started a trip with Home's button (FINDINGS_LOG, 2026-10-05, "First results from the phone"). The pairing screen has been drawn only on an emulator that has no Bluetooth device to list (2026-10-06), and nothing records the Log screen being drawn anywhere; their checks are 52 to 79 of `docs/DEVICE_TEST_CHECKLIST.md`. Trips and the address lookup ran on an emulator, not on the phone (checks 80 to 86), and so did Settings, deleting and restoring a trip, and Home's list of today's trips (checks 101 to 127). The first piece of phase 2 is built as well (2026-10-06): the work schedule in `core/schedule/`, which sorts every trip into Business or Personal at the moment it is closed and never has a say in whether one starts, with its settings, the marking of a trip by hand and the Business and Personal figures on Trips and Home. It brought the main database to version 3. It ran on an emulator, the migration included, and never on the phone (checks 128 to 150). The second piece followed the same day: a finished trip can be edited and a missed trip added by hand, such trips are marked, and what MilO recorded of an edited trip is kept and can be put back. It brought the main database to version 4, and it too ran on an emulator, the migration included, and never on the phone (checks 153 to 171). The third piece is built as well (2026-10-06): the driving alert in `platform/driving/`, which posts a notification when the phone reports driving during the work hours with no trip being recorded and the paired truck not connected, and never starts a trip. Its rules run in unit tests and its registration with Play services ran on an emulator; the build as it stands has had no report of driving anywhere (checks DA-1 to DA-14). **Phase 3 followed the same day:** the report for the accountant, with its rules, its layout and its CSV as pure Kotlin in `core/report/`, the list of sent reports in `data/report/`, and the drawing of the PDF and the hand-over to the email app in `platform/report/`. Whether a month is submitted is worked out from that list. It brought the main database to version 5 (one new table), and it ran on an emulator, the migration included, and never on the phone; Gmail there had no account, so no email draft has ever been seen (checks 177 to 207). The Android Auto screen is built too, in `platform/car/`: a `CarAppService`, a session and one screen. **It has never run anywhere either,** and by Google's documentation a build installed from Android Studio is not expected to appear on a real truck (section 10; checks 88 to 100). The rest of this document describes the structure the code must follow and the constraints already known from research. Required behaviour is in APP_ENCYCLOPEDIA.md.

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
[ Trips screen ] --Edit trip, Add a missed trip--> [ edit screen ]   feature/tripedit
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

**How the driving alert stands beside the recording.** It is a second thing the system can start MilO for, and it is deliberately not in the table of entry points above: it never calls `TripController.onTrigger`.

```
 Play services (Activity Recognition)
        |  "entered a vehicle" / "left a vehicle", through a PendingIntent
[ DrivingReceiver ]        in the manifest, so a report can start the process
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
- **The report adds up what it prints.** A trip is rounded to a tenth of a kilometre once (`tenthsOfAKilometre`), and every subtotal and the total are sums of those tenths. The screens round a sum of metres once instead, so a month's figure on the Trips screen and the report's total can differ a little (APP_ENCYCLOPEDIA says by how much).
- **MilO sends nothing.** It has no INTERNET permission. The email is a draft in the email app; the PDF reaches that app through a `FileProvider` that can hand out the files of one folder, the reports in the cache, and nothing else of MilO's storage.
- **Android never says whether an email was sent.** So nothing is recorded when the email app is opened. What was handed over is kept in the settings file (`ReportHandOver`), the question is asked when the Report screen is next in front, and only "I sent it" writes a row. The row is all there is to "submitted": no status is stored on a month, it is worked out from the rows (`monthSubmission`), for the Trips screen's month card and the Report screen alike.
- **The trip engine knows nothing of any of this.** Nothing under `core/trip/`, `platform/trip/` or `platform/bluetooth/` was changed for it.

**How the shared objects are made.** `app/MiloApplication` is the first code to run in the process, however it was started. It creates one `app/AppContainer`, which builds the databases, the repositories, the settings store, the trip controller, the address lookup, the catch-up that sorts earlier trips into Business and Personal, the setup checklist, the opener of settings screens, the driving alert, the list of sent reports and the three objects that make and hand over a report (`ReportTexts`, `ReportDocuments`, `ReportHandOff`), and the two objects behind the Settings screen's sound card (`OwnTripSound`, which makes a picked audio file the trip-start sound, and a `TripStartSound` player for its Play button), each lazily, on first use, and owns the application-wide coroutine scope. A screen's ViewModel is given what it needs from the container in `app/MiloNavigation`, so a feature never imports the `app` package. The container decides nothing about storage: it calls the `build...` functions in `data/`, which own every file name and folder. Everything else is handed what it needs through its constructor. There is no Hilt and no global singleton: to see what a class depends on, read its constructor; to see what it is given, read `AppContainer`.

**How the phone screens are reached.** Navigation 3. `app/MiloApp` holds the back stack and frames every screen with the bottom bar; `app/MiloNavigation` shows the screen on top of the back stack and is the only code that knows more than one feature. The screens' keys and the few functions that change the back stack are in `app/MiloBackStack.kt`. `MiloApp` is also where a screen is left, by any way, and so where the question before unsaved work is thrown away is asked (`app/UnsavedWork.kt`).

```
[ MiloNavigationBar ]   Home | Trips | Setup | Log        (core/designsystem)
        |
   back stack:   [ Home ]                    Home alone, or
                 [ Home, Trips | Setup | Log ]   one bar screen on top of it, or
                 [ Home, Setup, Pairing ]        the pairing screen, opened from Setup, or
                 [ Home, Settings ]              Settings, opened from Home's cog, or
                 [ Home, Settings, Pairing ]     the pairing screen, opened from Settings, or
                 [ Home, Trips, TripEdit ]       the edit screen, opened from Trips, or
                 [ Home, Trips, Report ]         the Report screen, opened from Trips, or
                 [ Home, Trips, Report, Settings ]   Settings, opened from the Report screen
```

- Each screen is a `@Serializable` key (`HomeKey`, `TripsKey`, `SetupKey`, `LogKey`, `PairingKey`, `SettingsKey`, and the two keys that carry something: `TripEditKey`, the id of the trip to edit, or nothing for a trip that is being added, and `ReportKey`, the year and month the Trips screen was showing), because Navigation 3 saves the back stack with kotlinx.serialization when Android puts MilO away. Seen on an emulator on 2026-10-06: with Settings open and MilO's process killed in the background, opening MilO again showed Settings. The same was seen with the edit screen: it came back, for a stored trip and for an empty form alike, with the trip as it is stored. What had been typed and not saved was gone, because the form lives in the screen's ViewModel and not in the saved state.
- Home is always at the bottom. Pressing a button of the bar leaves Home alone, or Home with that screen on top; whatever was open above is closed. So Back from Trips, Setup or Log leads to Home, and Back from Home leaves MilO.
- The bar marks the bar screen the back stack is in (`topLevelOf`): Setup under the pairing screen opened from Setup, Home under Settings and under the pairing screen opened from Settings, Trips under the edit screen and under the Report screen. Settings opened from the Report screen is on top of Trips too, so Trips stays marked there, and Back from it leads back to the report.
- A screen that leads to another one (Home to Setup, Home to Settings, Setup and Settings to pairing, Trips to the edit screen and to the Report screen, the Report screen to Settings) is handed a plain function to call. Features never import each other. A screen opened on top of another is added once, however often its button is pressed before the screen has changed (`openOnTop`).
- A screen's ViewModel lives as long as the screen is on the back stack, and is created with what it needs from the `AppContainer`. Leaving Trips and coming back therefore opens the current month again.
- Back closes the screen on top and never the last one: Navigation 3 throws on an empty back stack, and the process that would die is the one the trip service runs in (`closeTop`). A screen's own Back arrow closes that screen only while it is on top (`closeIfOnTop`), because a screen that is closing stays on the display, arrow included, for the length of the transition.
- **Three ways lead out of a screen that was opened on top of another: Android's Back, the screen's own Back arrow, and a button of the bottom bar.** All three are carried out by `MiloApp`, and none by a feature. That is why "Leave without saving?" is asked there: a screen says whether it holds something typed and not saved (a plain function it is handed; today only the edit screen calls it, with the answer of the pure `holdsUnsavedWork`), and while it does, every way out puts the question first and is taken only after "Discard" (`UnsavedWork`, pure values and functions with a unit test; `leaveTop` in `MiloBackStack.kt` is where each way leads). A screen that asked for itself would cover its own arrow and nothing else. The question is saved with the screens, so turning the phone keeps it; after Android has ended MilO the form is gone, the screen says so, and nothing is asked.
- **What one screen has to tell another goes through `MiloNavigation`, as a plain value.** There is one such value: when the trip that was just saved on the edit screen starts. The edit screen hands it over as it closes, the Trips screen is given it, shows the month the trip is in (`monthOfSavedTrip`) and says that it has. The two features still know nothing of each other.
- **The keyboard takes room and moves nothing.** `MainActivity` is declared `adjustResize`, and since MilO draws edge to edge that means Android leaves the window where it is and reports the keyboard's height. `MiloApp` gives that height up at the bottom of every screen (`imePadding`, after marking the room of the bars as used, so that the keyboard's height is not added on top of the bottom bar it covers). Without the declaration Android slid the whole window up, and the form's text was drawn through the status bar.
- **A screen that shows something Android does not report changes of** (permissions, settings, the phone's paired devices, which month is the current one, which day is today) **reads it again every time it comes to the front,** with the shared `CameToFrontEffect` (`core/designsystem/component/`). It acts on two signs. The screen resumes: Navigation 3 gives each screen a lifecycle of its own, so that happens when the screen is entered and whenever MilO returns from a settings screen or a system dialog. Or MilO's window gets the focus back: the quick settings panel and the notification shade cover MilO without pausing it, so nothing resumes when they close. The pairing screen has a third sign of its own, Android's broadcast that Bluetooth has finished switching on or off (`platform/bluetooth/BluetoothSwitch.kt`), because that happens a second or two after Shawn is back.
- **How a time of day is written is read by the screen itself, like the language.** Whether the phone is set to 24 hours (Android's "Use 24-hour format") is asked by `rememberTwentyFourHourClock` in `core/designsystem/component/`, when a screen is drawn and again each time it resumes, and handed to the pure formatters in `core/util/TimeFormat.kt` and to the time picker. It is presentation and nothing else, so no ViewModel stands in between; it is the one setting of the phone that a Composable reads for itself, and no other may be added this way.

**The setup checklist is shared, like the trip state.** `platform/system/SetupChecklist` holds the rows of the checklist as one flow. The Setup screen shows the rows; the home screen only asks `needsAttention` of them. What the phone reports is read when a screen asks; Shawn's confirmations (the settings store) and the truck's pairing (`TruckPairing.status`) arrive by themselves. The five facts that stop a trip from being recorded are read by `TripPreflight.facts()`, which the trip service's starter also uses.

One exception, because Android gives no other way. The components Android creates itself have no constructor MilO can call: `TripService`, `TruckCompanionService`, `TruckBluetoothReceiver`, `TruckReconcileReceiver`, `DrivingReceiver` (where the phone's driving detection reports to) and `MiloCarAppService` (the Android Auto screen's service). Each fetches the container from the application object (`(application as MiloApplication).container`). `TripNotifications` names `MainActivity` as the screen a tap on the trip notification opens. Those are the only places where `platform/` imports `app/`, and no other class may reach for the container this way.

---

## 4. Surface split (phone UI / Android Auto screen)

MilO has one platform and two UI surfaces. Both show the same trips and drive the same trip logic.

| Concern | Phone UI | Android Auto screen | Shared? |
|---|---|---|---|
| UI layer | Jetpack Compose screens, Material 3 | Car App Library screen, projected from the phone | No — surface-specific |
| What it shows | Everything: trips, settings, checklist, event log, reports | Tracking status, current trip km and duration, today's session count and total km | — |
| Which trips count, and today's totals | Business first, Personal apart | Every finished trip in one figure, as before | **Yes — `isCounted`, `categoryTotals` and `todayTrips` in `data/trip/TripTotals.kt`, used by Home, Trips and the car screen** |
| Business or Personal | Shown, and changed by hand, on Trips; shown on Home | Not shown (the car screen was left unchanged on 2026-10-06) | **Yes — the rule is `core/schedule/`, the stored result is on the trip** |
| Trips added or edited by hand | Typed in and changed on the edit screen; marked on Trips | Neither shown nor changed. Counted in Today like any finished trip | **Yes — the rules are pure functions in `data/trip/`, and `isCounted` knows no difference** |
| Manual trip control | Start/Stop button | Start Trip / End Trip | **Yes — both drive the same trip logic** |
| Trip rules, distance, formatting | | | **Yes** |
| Storage access | | | **Yes — repositories in `data/`** |
| System services | | | **Yes — `platform/`** |
| Auth | | | None on either (section 7) |

**Rule:** everything that isn't presentation is shared. Two copies of trip logic *will* drift. (See STANDARDS §9.)

The shared trip logic is placed by ADR-002: the pure rules (state machine, distance, point filter) in `core/trip/`, and the `TripController`, the foreground service and location recording in `platform/trip/`. The Car App Library classes live in `platform/car/`: the `CarConnection` watcher, and the screen.

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
        |                    today's trips              (data/trip; counted by todayTrips)
        |                    SetupChecklist.rows        (platform/system)
        |                    the clock
        |
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

---

## 5. Folder structure

One Gradle module, `:app`. Packages under `com.shawnkowalchuk.milo`:

```
app/                 # MiloApplication, the AppContainer, MainActivity, the navigation host,
                     #   and the question before a screen is left with unsaved work
feature/<name>/      # one package per feature: its Composable screens, its ViewModel,
                     #   its feature-only logic. Today: home/, trips/, tripedit/, report/,
                     #   setup/, pairing/, eventlog/, settings/
core/designsystem/   # theme tokens (colour, spacing, typography, shape), the shared base
                     #   components, and in text/ the words two features must say alike
                     #   (what a trip is saved as). The only core package that knows
                     #   Android's resources
core/trip/           # the trip rules: state machine, point filter, distance, trip closing.
                     #   Pure Kotlin, no Android imports, unit tested (ADR-002)
core/schedule/       # the work schedule and the rule that makes a closed trip Business or
                     #   Personal, at its close and again when its times are changed by hand.
                     #   Pure Kotlin, no Android imports, unit tested. Never used by
                     #   core/trip/: the schedule has no say in whether a trip starts
core/report/         # the report for the accountant: a period, the report as plain values,
                     #   every word and figure of the PDF, where each stands and on which
                     #   page, the CSV text, the email's subject and the file's name. Pure
                     #   Kotlin, no Android imports, unit tested
core/util/           # pure Kotlin helpers with unit tests: formatting of distances, times
                     #   and lengths of time, and a month, a day or a run of days as a span
                     #   of stored time
data/                # the only layer that touches storage. The two Room databases, and one
                     #   sub-package per kind of data, each with its entity, DAO and repository:
                     #   trip/ (with the rule for which trips count, the totals by Business
                     #   and Personal, the changes Shawn can make to a closed trip, the rules
                     #   for editing a finished trip, restoring what was recorded and adding
                     #   a trip by hand, and the catch-up that sorts trips recorded before
                     #   there was a schedule),
                     #   point/, eventlog/, settings/ (DataStore, the values the Settings
                     #   screen offers, how the schedule is kept, the report's four settings
                     #   and the report that waits for "Did you send it?"), crash/ (crash
                     #   files), sound/ (MilO's copy of a chosen trip-start sound),
                     #   report/ (the list of sent reports, the rule for which trips a
                     #   report lists, the rule for "submitted", and the report files)
platform/            # the only layer that touches Android system services: Bluetooth,
                     #   companion device, location, driving detection, notifications, audio,
                     #   Android Auto
platform/trip/       # TripController, TripService, GPS recording, notifications (the driving
                     #   alert's too, because its tap starts the trip service), the sound
                     #   (the player, and the choosing of an audio file of Shawn's own). The
                     #   ledger sorts a trip into Business or Personal as it closes it
platform/address/    # the start and end address of each trip: the geocoder, the passes over
                     #   the trips that still lack one, the rule for giving up (and for
                     #   keeping away from an address typed by hand), the one-line form of
                     #   an address
platform/report/     # the report's two meetings with Android: drawing the PDF's pages, and
                     #   the requests that hand a file to the email app, a viewer or the
                     #   share sheet. Also where the report's words are read from resources
platform/bluetooth/  # the triggers: the Bluetooth receiver, the companion service, the boot and
                     #   update receiver; the reading "is the truck connected?"; pairing
platform/car/        # the Android Auto screen (its service, session, screen, and the pure
                     #   function that decides what it shows) and the watch on Android Auto's
                     #   connection
platform/driving/    # the driving alert: the request to the phone's driving detection, the receiver
                     #   its reports arrive at, and the pure rule that decides whether a report
                     #   becomes a notification. It never starts a trip, and is given no way to.
                     #   A failure in it is caught there and never reaches the recording
platform/system/     # what the phone's permissions and settings say: the preflight check
                     #   before the service is started, the setup checklist's facts, rules
                     #   and shared rows, and the opening of the phone's settings screens
platform/diagnostics/  # crash and kill capture into the event log
```

Outside the app module, `tools/` holds scripts that are run by hand and are not part of the build. Today there is one: the script that synthesises the trip-start sound.

The Android entry points (`MiloApplication` and `MainActivity`) live in `app/`. No class sits in the root package. Features never import from each other. Shared code moves to `core/` or `data/`. Kotlin files are PascalCase and named after their main class. No file over about 300 lines, no Composable over about 200. (STANDARDS §3.)

---

## 6. Data model

What phases 1 to 3 store so far is built and described here exactly. Later phases add to it, each change as a Room migration: the phone holds real trips from phase 1 on, so no table is ever dropped and rebuilt. The source of truth for the tables is the schema Room exports to `app/schemas/` at every build, one file per version; those files are committed.

**Migrations** are in `data/MiloMigrations.kt`, one step per version, handed to Room by `buildMiloDatabase`. A step only adds: a column, an index, or a whole new table. There is no destructive fallback: a version without a step makes the database fail to open, loudly. Room runs a step, checks the result against the tables the code declares and stores the new version number inside one transaction; if the step or the check fails, the whole transaction is rolled back and the file stays at its old version with every row (seen on an emulator, FINDINGS_LOG 2026-10-05). `MiloMigrationsTest` checks each step against the exported schema files: the statements it runs must be exactly what the newer file has more than the older one, and a new table must be made by the very statement Room would make it with. A database that is more than one version behind is taken through every step in order (version 1 to 2 to 3 was run on an emulator on 2026-10-06, later that day versions 1 and 2 each to version 4, and then versions 1, 2, 3 and 4 each to version 5). A migration runs the first time anything in MilO touches the database, which after an update can be before MilO is opened: Android starts an app that is not force-stopped to tell it that it was updated.

**An older build cannot open a newer database.** A build made for version 2 that finds version 3 has no step back and no destructive fallback, so it dies at its start with "A migration from 3 to 2 was required but not found", a build made for version 3 that finds version 4 dies the same way, with "from 4 to 3", and a build made for version 4 that finds version 5 with "from 5 to 4" (all three seen on an emulator, FINDINGS_LOG 2026-10-06). The file is left as it is, and the newer build installed again reads everything. Every build so far calls itself `versionCode` 1, so Android does not stop the older build from being installed. **So no build from before 2026-10-06's Report screen may be installed over this one** (section 9; `docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check").

Conventions that hold everywhere: times are wall-clock milliseconds since 1970 unless a column says otherwise; distances are metres (kilometres exist only on screen); a column that holds one of a fixed set of values stores the Kotlin enum's name as text, so a constant can be added without a migration but never renamed without one.

**A new constant only works going forward.** A build from before the constant existed cannot read a row that holds it: Room's generated code throws `IllegalArgumentException: Can't convert value to enum, unknown value` as soon as a query returns the row, and nothing catches that. The database version does not protect here, because it did not change, and every build so far calls itself `versionCode` 1, so Android installs an older one over a newer one without complaint. The first such constant is the trip status `DELETED` (2026-10-06). **So no build from before 2026-10-06 may be installed over this one while a trip is deleted:** restore every deleted trip first, or do not go back (seen on an emulator, FINDINGS_LOG 2026-10-06; `docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check"). No data is lost if it happens: the newer build reads everything again. Whoever adds a constant to a stored enum writes the same warning for it.

**The second such constant is the event log category `DRIVING` (2026-10-06).** A build from before the driving alert that reads a `DRIVING` line dies the same way: on its Log screen, as soon as the newest 200 lines include one, and the trip service runs in the same process. Such a line is written at the first start of the newer build ("Driving alert: watching for driving…", or why not) and cannot be removed from the app. **So no build from before the driving alert may be installed over this one at all** (`docs/DEVICE_TEST_CHECKLIST.md`, "Before the first check"). Not tried on an emulator: it rests on the case above, which was. No data is lost if it happens; the newer build reads everything again.

### Main database: `milo.db` (`data/MiloDatabase`, version 5)

Version 1 was phase 1 as first installed on the phone. Version 2 (2026-10-05) added the four address columns of `trips` and the index on `startedAtMs`. Version 3 (2026-10-06) added the four columns of `trips` that hold Business or Personal: `category`, `categorySetByHand`, `ranPastSchedule` and `ignoredOutsideSchedule`. The step from 2 to 3 adds the columns and sorts no trip: every trip stored before it has an empty `category`, and the catch-up at the same process start fills it in. Version 4 (2026-10-06) added the seven columns of `trips` for a trip that is added or edited by hand: the marks `addedByHand`, `editedByHand`, `startAddressByHand` and `endAddressByHand`, and `recordedStartedAtMs`, `recordedEndedAtMs` and `recordedDistanceMetres`, which keep what MilO recorded of a trip that has been edited. The step from 3 to 4 adds the columns and reads or rewrites no stored value: every trip stored before it has the four marks at 0 and the three "recorded" columns empty, which is how "never edited" is stored. Version 5 (2026-10-06) added the table `sent_reports`. The step from 4 to 5 is one `CREATE TABLE` statement that names neither `trips` nor `event_log`: no column of either table is added, changed or read, and the new table starts empty, so no month is submitted after it.

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

**`sent_reports`** (`data/report/SentReport`), since version 5. One row for every report Shawn has said he sent. No index: the table grows by a dozen rows a year.

| Column | Type | Meaning |
|---|---|---|
| `id` | integer, key | Assigned by the database |
| `kind` | text | `MONTH` or `RANGE` (`SentReportKind`). `MONTH`: a report for a whole calendar month, the only kind that marks a month as submitted. `RANGE`: a report for a run of days Shawn chose, which marks no month, whatever days it covers |
| `firstDay` | integer | The first day of the report's period, as days since 1970-01-01. A calendar day, not a moment in time, so no time zone can move it. In SQLite: `date(firstDay * 86400, 'unixepoch')` |
| `lastDay` | integer | The last day of the period, on the same terms. It is inside the period. For a `MONTH` the two are the first and the last day of that month |
| `sentAtMs` | integer | When the email app was opened with the report: the day it counts as sent, however much later Shawn answered the question |
| `tripCount` | integer | How many Business trips the report listed |
| `distanceMetres` | real | The total the report printed, in metres. Always a whole number of hundreds, because the report adds up kilometres rounded to a tenth |
| `revision` | integer | 0 for the first report sent for this `kind`, `firstDay` and `lastDay`; 1 for the first that replaced it, and so on. Given inside the insert's transaction (`insertAsNextRevision`): one more than the highest number those three have so far |

**A row is written when Shawn answers "I sent it", and at no other moment.** Android cannot tell an app whether an email was sent (section 10), so MilO takes his word, and nothing is written when the email app is merely opened. A row is never updated and never deleted: the DAO has an insert and reads, nothing else. It keeps the figures the report had when it was sent, which later edits of the trips do not reach.

**Whether a month is submitted is not stored anywhere.** It is worked out from this table by `monthSubmission` in `data/report/Submission.kt`: a month is submitted once it has a `MONTH` row, by the first of them, and the newest one is named as its latest revision. A `RANGE` row never counts, not even one that runs from a month's first day to its last. The Trips screen's month card and the Report screen both ask that one function. The same file says which revision the next report for a period is (`nextRevision`), the rule the insert uses, and what one more report for a period would do (`sentEffect`: mark the month, be listed as a revision of a month that keeps its first date, or be listed as a date range), which "Did you send it?" says before it is answered.

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
| `custom_sound_uri` | text | none | Where MilO's own copy of the audio file Shawn chose is (a `file:` address inside `no_backup/trip_sound/`); absent means the bundled chirp |
| `custom_sound_name` | text | none | Since 2026-10-06. What the file he picked was called, for the Settings screen. Written and cleared together with the key above; absent when the phone gave no name |
| `schedule_<day>_tracked`, `schedule_<day>_start_minute`, `schedule_<day>_end_minute`, for each `<day>` from `monday` to `sunday` | boolean, integer, integer (21 keys) | Monday to Friday tracked, Saturday and Sunday not; 480 and 990 on every day | Since 2026-10-06. The work schedule: whether trips on that day can be Business, and the day's start and end as minutes since local midnight (08:00 and 16:30). An end is always after its start. All 21 are written together (`setSchedule`); the keys are spelled out in `data/settings/ScheduleStorage.kt`. A day whose stored times make no sense, which no setter can write, reads with the default hours |
| `ignore_trips_outside_schedule` | boolean | false | Since 2026-10-06. What becomes of a trip that starts outside the schedule: false saves it as Personal, true has it stored as discarded when it closes |
| `driving_alert_enabled` | boolean | true | Since 2026-10-06. Whether the driving alert is switched on. Read by the alert at every report of driving and whenever it looks at what to ask of the phone, and by the setup checklist for the Physical activity row |
| `last_driving_alert_at_ms` | integer | none | Since 2026-10-06. When the driving alert was last posted. Absent before the first alert. Written by the alert each time it posts one, and read at every report of driving: no second alert is posted within 30 minutes of it, also after a restart of the process |
| `report_name`, `report_company`, `report_vehicle` | text each | none | Since 2026-10-06. Who the report for the accountant is from: Shawn's name, his company, a description of the vehicle. Each is stored without spaces around it and is 80 characters at most; absent means not set. A report cannot be made without the name; the other two are left off the report when they are not set |
| `accountant_email` | text | none | Since 2026-10-06. The address the email app is opened with. Only something that is one email address is ever stored (`isEmailAddress` in `data/settings/ReportDetails.kt`); absent means not set, and then no report can be sent |
| `report_handed_over_kind`, `report_handed_over_first_day`, `report_handed_over_last_day`, `report_handed_over_trip_count`, `report_handed_over_tenths`, `report_handed_over_at_ms` | text, integer, integer, integer, integer, integer (6 keys) | none | Since 2026-10-06. The report that was handed to the email app and that Shawn has not answered "Did you send it?" for yet (`ReportHandOver`): `MONTH` or `RANGE`, the period's first and last day as days since 1970-01-01, its number of trips, its total in tenths of a kilometre, and when the email app was opened. Written together before the email app is opened and removed together when he answers, or when the email app did not open. A file that holds only some of them, or values that make no report, reads as "nothing is waiting". The keys are spelled out in `data/settings/ReportHandOver.kt` |
| `auto_start_held_off_since_ms` | integer | none | ADR-002's hold-off: the time a trip was ended by hand with the truck still connected. Absent means not held off. The time is kept because two of the three things that release the hold-off are measured from it |
| `last_process_exit_imported_at_ms` | integer | 0 | The newest process-exit record already copied into the event log |
| `confirmed_xiaomi_autostart_at_ms`, `confirmed_xiaomi_battery_saver_at_ms`, `confirmed_xiaomi_other_permissions_at_ms`, `confirmed_xiaomi_recents_lock_at_ms` | integer each | none | When Shawn confirmed a step of the setup checklist that MilO cannot read. Absent means not confirmed. The key of each step is fixed on `ConfirmedStep` in `data/settings/MiloSettings.kt` |

The time of the last driving alert, the hold-off, the exit-record marker, the four confirmations and the report that waits for its answer are not settings Shawn chooses. They are small pieces of state that must outlive the process.

The store is built by `buildSettingsStore` in the same package, with no corruption handler: an unreadable file makes every read throw, and is never replaced by empty settings (that would drop the truck pairing without a trace).

### Crash files: `no_backup/crashes/` (`data/crash/CrashFileStore`)

One small text file per uncaught exception, named `crash-<time>.txt`: the time, the thread, a one-line summary and the stack trace. Written while the process dies, copied into `event_log` at the next start and then deleted. At most 20 wait at once. The folder is under the app's no-backup files, so Android never backs it up or transfers it.

### The chosen trip-start sound: `no_backup/trip_sound/` (`data/sound/OwnSoundStore`)

At most one file, `own_trip_start_sound_<number>`: MilO's copy of the audio file Shawn chose on the Settings screen, 10 MB at most. A new choice is written beside the old copy under a new number, and the old one is removed only after the settings name the new one, so the sound in use is never touched by a choice that fails. The folder is under the app's no-backup files: a sound must never be what pushes the app over the 25 MB backup limit (below). Phase 4's backup rules will restore the settings without the file; the trip service then plays the bundled chirp and logs why.

### The report files: `cache/reports/` (`data/report/ReportFileStore`)

The PDF and the CSV of a report, such as `Mileage-2026-10-Shawn-Kowalchuk.pdf` (a date range: `Mileage-2026-10-05-to-2026-10-18-…`; a report that replaces one already sent: `…-rev1.pdf`; a CSV is never named as a revision). A file is written under another name (`….part`) and given its own only when all of it is on the disk, so a half-written report can never be attached. A report made again for the same period replaces the file. Files older than seven days are removed when the Report screen is opened.

They are in the app's cache because each can be made again from the trips at any time. Android never backs the cache up, and **empties it by itself when the phone runs short of room** (seen on an emulator: the file was gone seconds after it was made, FINDINGS_LOG 2026-10-06), so a file is looked for before it is opened and made again if it is gone. This is the one folder MilO's file provider can hand a file out of (`res/xml/report_file_paths.xml`; section 8).

### Not built yet

| Stored data | Key fields | Phase |
|---|---|---|
| More settings | reminder day | 4 |

### Two database files

Raw GPS points live in their own database file, to be kept out of cloud backup. Android Auto Backup is capped at 25 MB per app and is all-or-nothing: over the cap nothing is backed up, and no error is shown. By the research estimate, points recorded every few seconds would cross the cap within about a year and take the small, valuable trip data down with them. Backup is switched off entirely until phase 4 writes the backup rules. Source for the split and the cap: `docs/research/2026-10-03-pdf-email-backup.md`.

Both files are in the app's standard databases folder. Room keeps three more files beside a database, seen on the emulator for `milo.db`: `-wal`, `-shm` and `.lck`. For the points database that means `points.db-wal`, `points.db-shm` and `points.db.lck`, and phase 4's backup rules must name the points database together with them.

**A raw point refers to its trip by id only.** SQLite cannot enforce a foreign key across database files, so `tripId` is a plain indexed column. Nothing removes a trip's row: a short trip is marked `DISCARDED`, and a trip Shawn deletes on the Trips screen is marked `DELETED`, both with their points kept. Any later code that does remove a row must delete its points itself, through `RawPointRepository`. A trip that was added by hand has no points at all, and an edit leaves a trip's points as they are: they are what was recorded, whatever the row says now.

**Repositories are the only way in.** `TripRepository`, `RawPointRepository`, `EventLogRepository`, `SentReportRepository`, `SettingsStore`, `CrashFileStore`, `OwnSoundStore` and `ReportFileStore` are the API the rest of the app uses (`TripCategoryCatchUp`, which lives in `data/trip/` too, goes through the first, the third and the fourth like everything else); the DAOs, the DataStore and the files are not touched, or even located, from outside `data/`. Every write of the trip rules to a trip is safe to repeat, and one made to a trip that is no longer open changes nothing. The write that closes a trip also stores its category and the two flags that go with it. Six writes are made to a closed trip. The address lookup's (`recordAddressLookup`) touches the address columns of a `FINISHED` trip and nothing else. It is the one write that is **not** safe to repeat: every call moves the time of the last attempt, and a call that leaves an address missing counts one more attempt, so it is made once for each lookup and never wrapped in a retry. Shawn's own changes of status (`correct`, above) touch the status and nothing else, and a repeated one changes nothing. The catch-up's (`sortUnsorted`) touches `category` and `ranPastSchedule` of a closed trip that has no category and is not set by hand, and a repeated one changes nothing. Shawn's own choice of category (`setCategoryByHand`, above) touches `category` and `categorySetByHand` of a `FINISHED` trip, and a repeated one changes nothing. The edit screen's two (`editByHand` and `restoreRecorded`, above) touch sixteen columns of a `FINISHED` trip, each inside a transaction with the read it is based on, and a repeated one changes nothing. One write makes a closed trip that was never open: `addByHand`, an insert, which is **not** safe to repeat (a second call adds a second trip); the edit screen lets one press through at a time and leaves when it has been stored. One write makes a row in another table: `SentReportRepository.recordSent`, an insert into `sent_reports`, which is **not** safe to repeat either (a second call records a second report, as a revision of the first); the Report screen forgets the report that waited for its answer before it makes the call, and lets one answer through at a time.

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
| Android's file picker (Storage Access Framework) | Choosing an audio file as the trip-start sound | `feature/settings/SettingsScreen` opens it; `platform/trip/PickedAudio` reads the file | Needs no permission: a pick lets MilO read that one file for a short while, and MilO copies it at once. Ran on an emulator; HyperOS's own picker has never been seen |
| MediaPlayer (system) | The trip-start sound, at a trip start and from the Settings screen's Play button; and the check that a chosen file can be played | `platform/trip/TripStartSound` | Notification-type audio: silent on silent, vibrate and Do Not Disturb, and in Bedtime mode (the phone, 2026-10-05: nothing was heard until Bedtime mode was off) |
| Geocoder (system) | Start and end addresses | `platform/address/GeocoderAddressLookup` | Built; ran on an emulator. Needs network, takes no key, has no availability guarantee. A failed lookup is tried again at the next occasion, four times at most. The one place a position leaves the phone |
| ConnectivityManager (system) | Whether the phone is online, asked before the geocoder is | `platform/address/NetworkStatus` | Needs `ACCESS_NETWORK_STATE`. MilO has no INTERNET permission and opens no connection itself |
| Android Auto (Car App Library) | The in-truck screen; `CarConnection` keeps a trip open | `platform/car/` | Both are built. The screen has never run anywhere, and may not be listed on the truck at all: distribution risk (section 10) |
| Activity recognition (Play services) | The driving alert: "entered a vehicle" and "left a vehicle", delivered through a `PendingIntent` to a manifest receiver | `platform/driving/` | Built (2026-10-06). Alert only: it never starts a trip. Part of the location library already in the build, so no new dependency. Needs the Physical activity permission (`ACTIVITY_RECOGNITION`). On an emulator Play services accepted the request; the build as it stands has had no report delivered anywhere. Whether a report can start MilO's process on HyperOS is untested (section 10) |
| Gmail, or any other email app | Sending the report for the accountant | `platform/report/ReportHandOff` builds the request; `feature/report/ReportScreen` starts it on MilO's activity | Built. A "send this file" request with the accountant's address, the subject, a few lines of text and the PDF, handed to the app that takes a `mailto:` address: Gmail first, then any email app. MilO sends nothing and has no INTERNET permission; Shawn taps send. The app cannot learn whether it was sent. On an emulator the request reached Gmail's compose screen with a read permission for the one file; Gmail had no account there, so **no draft has ever been seen**, on any device |
| A PDF viewer; Android's share sheet | Looking at the PDF before it is sent; saving or sharing the CSV | `platform/report/ReportHandOff` | Built. Whatever app the phone shows a PDF with (Google Drive's viewer on the emulator), and Android's own share sheet for the CSV. A phone without such an app is told so on the screen |
| FileProvider (`androidx.core`) | Letting those apps read the one file they are handed | The `<provider>` in the manifest, `res/xml/report_file_paths.xml` | Not exported. It can hand out the files of `cache/reports/` and nothing else, and only to an app MilO has just handed a file's address to, with a read permission that ends when that app's screen closes. The permission is given explicitly: Android 18 stops giving it by itself |
| The phone's settings screens | The buttons of the setup checklist and the pairing screen | `platform/system/SystemScreens` | Android's own screens, and three HyperOS ones known only from other apps' source. Each is tried inside a try/catch and falls back on Android's page for MilO. Shawn went through Setup on the phone on 2026-10-05 and reported no problem; whether each button opened the right screen was not written down (device checks 54 to 66) |
| HyperOS Autostart app-op | The "looks on / looks off" reading on the setup checklist | `platform/system/SetupFacts` | A hidden Android method reached by reflection with MIUI's app-op 10008. Advisory only; any failure reads as "unknown". On the phone it answered and followed the switch (2026-10-05) |
| Android Auto Backup | Off-phone copy of the main database | Manifest backup rules | Off until phase 4. 25 MB cap (section 6) |
| GitHub | Private repo, CI, Dependabot updates, and Dependabot alerts fed by the dependency graph workflow | `.github/` | Development only |

No Sentry, no analytics, no API keys.

---

## 9. Environments & deployment

| Environment | Backend project | Used for | URL / build channel |
|---|---|---|---|
| The phone | None | Everything: development, testing and Shawn's real trips | Debug build signed with the dedicated key in `~/keys/milo.jks`, installed from Android Studio or with `./gradlew installDebug` |

There is one environment because there is no backend to separate (STANDARDS §13). The build on the phone holds real trip data, so an uninstall is data loss.

Build and release path: a pull request runs CI (gitleaks, then `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug`). After merge the build is installed on the phone, and the device test checklist (`docs/DEVICE_TEST_CHECKLIST.md`, which grows with each work package) is run there. A phase's behaviour counts as proven only once its checks have run on the phone, but the next phase does not wait for them: on 2026-10-05 Shawn decided that the phases follow one another without a stop in between (FINDINGS_LOG, 2026-10-06; STANDARDS §11). An older build is never installed over a newer one without reading section 6's two rules first: a build from before a database version cannot open that database, and a build from before a stored enum constant crashes on a row that holds it. GitHub Free cannot block a merge on a red build in a private repo, so not merging red is a rule Shawn follows by hand.

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
- A request to associate with the truck must go through an Activity: Android shows its consent dialog on it, and up to Android 12 the request fails from any other context. (FINDINGS_LOG, 2026-10-05)

**This phone**
- HyperOS background limits: starting a dead app is gated by Autostart, which is off by default for a sideloaded app. With it off, decompiled system code shows manifest broadcasts dropped and service starts and binds rejected, which would block both the Bluetooth receiver and the CompanionDeviceManager path. Battery saver "No restrictions" is a second, separate gate. This rests on decompiled code and forum reports, so the with and without Autostart test on the POCO X5 decides it. (`2026-10-03-miui-background-limits.md`, `2026-10-03-cdm-presence.md`)
- What MilO can know about the HyperOS settings is limited. Autostart can be read only through an unofficial app-op check that is known to say "on" wrongly (on this phone it answered "allow" on 2026-10-05, and the same record showed it rejecting nine minutes earlier, so here it follows the switch); the per-app Battery saver profile, the "Other permissions" switches and the lock in recents cannot be read at all, so the setup checklist takes Shawn's word for them, with the date. Whether a HyperOS settings screen exists cannot be asked beforehand either (an app cannot see another app's screens without declaring it), so each is opened inside a try/catch with a fallback. (`2026-10-03-miui-background-limits.md`, findings 27 to 33)
- Installing from Android Studio needs two Xiaomi-only developer switches ("Install via USB" and "USB debugging (Security settings)") and a confirmation on the phone at each install. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**Android Auto**
- Distribution risk: Google's documentation says the "Unknown sources" developer option does not apply to Car App Library apps, which must come from a trusted store to show on a real head unit. A sideloaded MilO should appear in the desktop head-unit emulator but is not expected to appear in the truck. The screen was built knowing this. **Unverified on the truck;** community reports conflict. If it does not appear, the choices are a private Google Play install, a media-app style workaround, or dropping the screen (APP_ENCYCLOPEDIA, Android Auto screen). (`2026-10-03-android-auto-screen.md`, `2026-10-03-location-and-car.md`)
- The car screen's service is exported with no permission, because Android has none for Android Auto's binding on a phone. What protects it is the Car App Library's host check, set to the library's own list of Android Auto's signing certificates in every build type. If Google changes that certificate, MilO's screen is refused until the library is updated. (`2026-10-03-android-auto-screen.md`, findings 15 and 16)
- That host check is the library's own code, so it is only as sound as the pinned library, and the pinned library, 1.7.0, is one release behind a security fix. The release notes of 1.8.0-rc01 say it includes one, without naming it, and tell every lower version to update. A release candidate is not taken (the stable-only rule), so the fix is knowingly missing. **1.8.0 is to be taken the day it is stable.** (`2026-10-03-android-auto-screen.md`, finding 3; FINDINGS_LOG, 2026-10-05, `[DEBT]`)
- The screen needs Car API level 7 (the library's current `Header` class). The level is a property of the Android Auto app on the phone, and which level which version speaks is not documented. The session writes the level it was given to the event log.
- Android Auto, not MilO, decides how long a car session lives, and being shown on the car is not "in front" for location or for starting the trip service. A trip started from the car's button therefore needs what an automatic start needs: "Allow all the time", and battery use unrestricted or the companion association. (`2026-10-03-android-auto-screen.md`, findings 23 to 25)
- `CarConnection` only reports while the app is already running. It can hold a trip open but cannot start one. (`2026-10-03-location-and-car.md`)

**Driving alert**
- Activity Recognition says "in a vehicle", never which one, and takes about a minute or more to notice. This is why its reports only ever lead to a notification, and why a trip started from the notification misses the beginning of the drive. (`2026-10-03-location-and-car.md`, findings 30 to 36)
- Reports arrive only through a `PendingIntent`, which must be mutable (Play services writes the report into it) and, on Android 14 and later, must name its target class. Without the Physical activity permission Play services reports nothing. (findings 30 and 31)
- Google does not say whether a request outlives a reboot or an update, so MilO makes it again at every process start. Nothing promises that a report is fresh either, so a report of entering a vehicle that is more than 5 minutes old is not acted on. (finding 36 for the request; the limit is a precaution and the 5 minutes a judgement, FINDINGS_LOG 2026-10-06; whether this phone hands over old reports is device check DA-12)
- The phone reports leaving a vehicle only when it detects another activity, and slow traffic is reported as a mix of driving, cycling and standing still (finding 34, low confidence). So one drive can bring several "left" and "entered" reports, which is why no second alert is posted within 30 minutes of the last and none within 5 minutes of a trip's end. (FINDINGS_LOG 2026-10-06; device checks DA-10 and DA-14)
- The delivery names MilO's receiver directly. ADR-002 noted that this might get past HyperOS's Autostart gate where the Bluetooth broadcast does not; the research itself thinks a dead app's receiver likely needs Autostart here too (`2026-10-03-location-and-car.md`, finding 32). **Untested either way.** If it gets past, the alert is a way of waking MilO that does not depend on Autostart; if it does not, the alert works only while MilO's process is alive. (ADR-002, amendment 27; device checks DA-8 and DA-9)

**Data safety**
- Auto Backup cap: 25 MB per app, all-or-nothing, silent when exceeded. This is why raw GPS points live in a separate database file (section 6). (`2026-10-03-pdf-email-backup.md`)
- Signing-key risk: every build for the phone is signed with one dedicated key (`~/keys/milo.jks`, password in the macOS Keychain). A build signed with any other key cannot update the installed app. The only way forward would be an uninstall, which deletes every trip, and Auto Backup then refuses to restore. So the keystore and its password must be backed up off the Mac, and a manual export is the only copy of the trips that does not depend on the key. (`2026-10-03-pdf-email-backup.md`)

**Addresses**
- Android's geocoder needs a network and promises neither an answer nor a right one; on this phone it is Google Play services. An address is a label, and the stored coordinates stay the record. (`2026-10-03-location-and-car.md`, findings 17 to 20)
- The research's retry design was a WorkManager job with a network constraint. This build has none (WorkManager is not a dependency yet, and this package was to add none): the lookup runs at process start, when a trip ends and when the Trips screen comes to the front, and checks the network itself. A trip that ends offline therefore waits for one of those occasions. (FINDINGS_LOG, 2026-10-05)
- When a trip ends by itself the trip service stops, and HyperOS may freeze or kill the process before the geocoder answers. The trip is then caught up at the next occasion. Not measured on the phone.

**Recording**
- A fused location request combines interval and distance as AND, so "every 5 seconds or 10 m" cannot be asked for. The request is a fix every 5 seconds, and the 10 m rule is applied in MilO's own distance calculation. (`2026-10-03-location-and-car.md`)
- The timers of a trip run as coroutines inside the service, as the research recommends, and no wake lock is held. A coroutine timer does not count time the phone spends asleep, so a timer can fire late when the phone sleeps between GPS fixes. Two things limit the harm: a GPS fix stands in for a late timer (for a deadline once it is 10 seconds overdue, for the minute reading 70 seconds after the last one), and the trip rules judge a late reading by the stored deadline, not by when the timer fired. With no fixes arriving, nothing stands in. Not measured on the phone yet (DEVICE_TEST_CHECKLIST).
- Recording needs four permissions: precise location, "Allow all the time", Nearby devices (Bluetooth) and notifications. The Setup screen asks for each; Shawn granted all four through it on the phone on 2026-10-05 (FINDINGS_LOG, "First results from the phone").

**The report**
- Android's `PdfDocument` is the only PDF code: no third-party library (ADR-001). It works in points (US Letter is 612 by 792), records a page as it is drawn and cannot return to one, and is not thread safe. So the layout is decided first, in pure Kotlin, and a PDF is made on one background thread at a time. (`2026-10-03-pdf-email-backup.md`, findings 1 to 7)
- Android gives an app no way to learn whether an email was sent: the request to an email app has no result. MilO therefore asks, and records only what Shawn says. (`2026-10-03-pdf-email-backup.md`, finding 14)
- The email app is chosen by a selector for a `mailto:` address, and the request itself carries no type. Both were learned on an emulator: with Gmail's package named on the request, Android asked "Gmail or Chat?", and with a type on the request, no email app was found at all (FINDINGS_LOG, 2026-10-06). What Gmail then does with the address, the subject and the attachment is Gmail's own, undocumented, and unseen until it is tried on the phone.
- The report files are in the cache, which Android empties when storage is short. A draft that waits in the email app while that happens may lose its attachment; the file can always be made again.

**Build**
- AGP 9.4.1 with Gradle 9.8.0 and Kotlin 2.4.20 is one step past what JetBrains documents. The set builds from the terminal on this Mac (2026-10-03) with no fallback. Gradle prints one deprecation notice, caused by AGP's own code (FINDINGS_LOG, 2026-10-03). The fallback and the dev-machine requirements are in ADR-001. (`2026-10-03-versions.md`)
