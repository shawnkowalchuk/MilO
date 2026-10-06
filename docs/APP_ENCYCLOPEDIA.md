# App Encyclopedia

> **What this is.** The complete *reference* for every capability the app has and exactly how it works — the "how does this actually behave?" book. When you (or your AI assistant) forget how a feature works, or before you change one, you read its entry here first. This is the single biggest defence against "I changed X and didn't realise it broke Y."
>
> **How to maintain it.** One entry per feature/capability, using the template below. **Update the entry in the same change that alters the feature** — a stale encyclopedia is worse than none, because it lies confidently. If behaviour changed and this didn't, the Definition of Done (STANDARDS §10) wasn't met.
>
> **Status:** Living document · **Last updated:** 2026-10-03 · **See also:** ARCHITECTURE.md (the map), FINDINGS_LOG.md (history of changes)

---

## How to read an entry

Each capability is documented with: what it does, who can use it, how it works step by step, where the code lives, what it depends on, edge cases, and which platforms it's on. Copy the template at the bottom for every new feature.

**Most entries are still `Planned`.** They record the founder's brief of 2026-10-03 plus the answers given at kickoff, so they describe required behaviour, not code. An entry marked `In progress` has a "How it works today" and a "Where the code lives" section. Those two sections describe only what exists; everything under "Required behaviour" that they do not mention is not built.

**What exists after the phase 1 foundation:** storage, the trip rules as tested pure code, and crash and kill capture. **Nothing starts or records a trip yet.** The app still opens on the placeholder home screen.

**The app in one line:** MilO is a personal Android app (native Kotlin, one phone, no backend, no accounts, no Play Store) that automatically logs business mileage in Shawn's work truck and produces a monthly PDF to email to accounts.

**Target phone:** Xiaomi POCO X5, Android 14 (HyperOS). Installed from Android Studio on a Mac mini.

**Build phases** (stop after each so Shawn can test on the phone):
1. Truck pairing, trip detection, GPS logging, trip-start sound, manual Start/Stop, Android Auto screen, permission checklist, basic trip list, bare event log. Shawn drives with this for a few days before phase 2.
2. Schedule settings, Business/Personal, driving alert, day and month views, editing, manual trips.
3. Monthly PDF, email, submitted status, CSV export.
4. Diagnostics, reminders, backups, polish.

---

## Index

- [Truck pairing and trip detection](#truck-pairing-and-trip-detection) — phase 1
- [GPS recording and distance](#gps-recording-and-distance) — phase 1
- [Trip-start sound](#trip-start-sound) — phase 1
- [Safety net: manual button and driving alert](#safety-net-manual-button-and-driving-alert) — phases 1 and 2
- [Android Auto screen](#android-auto-screen) — phase 1
- [Permission checklist](#permission-checklist) — phase 1
- [Schedule and Business/Personal](#schedule-and-businesspersonal) — phase 2
- [Trip log: home, day and month views](#trip-log-home-day-and-month-views) — phases 1 and 2
- [Monthly PDF and submission](#monthly-pdf-and-submission) — phase 3
- [Diagnostics and reminders](#diagnostics-and-reminders) — phases 1 and 4
- [Backup, export and import](#backup-export-and-import) — phase 4
- [Settings](#settings) — grows each phase
- [Design system](#design-system) — live, grows as screens need it

---

## Truck pairing and trip detection

**Status:** In progress (phase 1): the trip rules and their storage exist and are tested; nothing runs them yet · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Starts a trip automatically when the phone connects to the truck over Bluetooth and ends it after the truck disconnects. This is the most important capability in the app: the mileage app Shawn uses today often fails to start trips even when the phone is clearly connected, so **reliability is the number one requirement**.

**Required behaviour**
- Onboarding: Shawn picks the truck from the phone's paired Bluetooth devices. The app associates it with `CompanionDeviceManager` and observes device presence, so the system wakes the app on connect and disconnect even if it has been closed.
- Backup trigger: a manifest-registered receiver for `BluetoothDevice.ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED`, filtered to the truck's address.
- On connect: start a foreground service (type `location`) with an ongoing "Trip in progress" notification and begin GPS recording.
- On disconnect: wait a grace period (default 2 minutes, configurable) before ending the trip. Reconnecting inside the grace period continues the same trip.
- If Android Auto is still connected (`androidx.car.app` `CarConnection`), do not end the trip. Shawn sometimes uses Android Auto over a USB cable.
- **A trip runs from Bluetooth connect to disconnect plus grace, and nothing else splits it.** Decided at kickoff: a long stop with the engine running stays inside one trip. Stops are not cut into separate sessions.

**How it works today**
Pairing, the Bluetooth triggers, the service and the notification are not built. What exists is the decision-making they will call, designed in `docs/adr/ADR-002-trip-detection.md`:
1. `TripStateMachine.step(state, event, rules)` takes the current state and one event and returns the new state plus a list of effects (start a trip, mark the truck seen, start or cancel the grace period, end the trip, set the hold-off). It touches nothing itself: the caller carries the effects out.
2. Events state what is true, not what just happened: "the truck is connected", "Android Auto is not connected", "Start was pressed and the truck is not connected", "End was pressed", "the truck moved". The same event twice changes nothing, so dropped, repeated and reversed Bluetooth events cannot corrupt a trip.
3. After every event the rules settle the trip against what is known:
   - idle and the truck connected: a trip starts, unless the hold-off is set;
   - a trip open and the truck or Android Auto connected: it carries on, and a running grace period is cancelled;
   - neither connected: the grace period starts (once; a repeated disconnect does not move the deadline), and when it runs out the trip ends;
   - a manual trip the truck never joined: it ends after 30 minutes without movement.
4. Every event carries the time, but a trip whose time has run out is closed only by an event that brings a fresh reading of the truck: a connect or disconnect, the timer's own reading, or a press of Start or End. A GPS fix or an Android Auto change closes nothing, because what the rules believe about the truck may be out of date. `TripStateMachine.nextCheckAtMs` says when a timer is needed. When it fires, or when that time is already past, the caller reads the truck's connection and sends it as an event, which is how "check again before closing" is done.
5. A reconnect continues the same trip only inside the grace period, plus 30 seconds to allow for a timer that fires late. After that the trip is over, whatever connects: it is closed where the truck was found gone, and a connected truck starts a new trip in the same step.
6. After a process restart, `TripStateMachine.restore` rebuilds the state from the open trip row, the stored hold-off, the time of the trip's newest stored point and a fresh reading of both connections, then applies the same rules. A trip that was recording, and whose newest point is more than 30 minutes old, is closed at that point first: nobody watched the truck in between.
7. Start pressed while a trip is waiting out its grace period, with the truck still gone, ends that trip where the truck was found gone and starts a manual trip.
8. An ended trip is cut at the moment the truck was found gone (or the last movement, for the no-movement rule). `TripClosing.close` drops the stored points recorded after that moment and works out the end time, the distance and the start and end positions from the rest, and marks a trip under the minimum distance `DISCARDED`.
9. `TripRepository` stores the trip. Each write matches one effect and is safe to repeat. Starting a trip while one is open returns the open one.

**Where the code lives**
- `core/trip/`: `TripStateMachine.kt` (the rules), `TripState.kt`, `TripEvent.kt`, `TripEffect.kt`, `TripClosing.kt`, `TripStatus.kt`, `TripStartCause.kt`.
- `data/trip/`: `Trip.kt` (the table), `TripDao.kt`, `TripRepository.kt`. The hold-off is in `data/settings/SettingsStore.kt`.
- Tests: `app/src/test/.../core/trip/TripStateMachine*Test.kt` (the rules in ADR-002's order, how a grace period ends, the buttons, awkward orderings, random sequences, restarts) and `TripClosingTest.kt`.

**Depends on**
Bluetooth permissions, companion device association, the foreground service, and on this phone the HyperOS background settings (see [Permission checklist](#permission-checklist)). Background location ("Allow all the time") is mandatory, not optional: on Android 14 a trip started while the app is in the background fails without it.

**Edge cases & gotchas**
- Phone reboots or the app is updated while the truck is already connected: no connect broadcast fires, so the existing connection has to be detected another way.
- With HyperOS Autostart off and MilO not running, decompiled system code shows the connect broadcast is dropped and the system's attempts to start MilO's services are rejected. Autostart is off by default for a sideloaded app. This is unconfirmed on Shawn's phone until tested, but Autostart is treated as mandatory. See `docs/research/2026-10-03-miui-background-limits.md`.
- After a reboot, Android holds Bluetooth events until the phone has been unlocked once, so a trip begun before the first unlock starts recording at unlock.
- Swiping MilO away in recents, the clear-all button and Xiaomi's cleaners can kill it while it waits for the truck. Force stop leaves it unable to start until Shawn opens it again.
- Ending a trip by hand while the truck is still connected holds automatic start off until the truck is next seen disconnected. Pressing End with no trip open and the truck connected does the same. **Open risk:** if that disconnect is never seen (the app was dead and the broadcast was dropped), the hold-off is still set at the next connect and that trip does not start by itself. Not yet decided; see FINDINGS_LOG 2026-10-03.
- A lost disconnect cannot be noticed by the rules alone: the trip stays open until something reads the connection again. The service will have to re-read it from time to time.
- The process killed (or frozen) while a trip is open: the next morning's connect does not join yesterday's trip. A trip in grace is closed by its stored deadline, restart or not. After a restart, a trip that was recording is closed at its newest point if that is more than 30 minutes old; up to 30 minutes it carries on, and the gap is counted as a straight line. A restart also closes a trip that stored no point for 30 minutes for any other reason, such as location switched off. A process that stays alive through a lost disconnect is not covered: see the line above.
- The 30 seconds and the 30 minutes are constants in `TripState.kt` (`LATE_CHECK_TOLERANCE_MS`, `RESTART_GAP_LIMIT_MS`). They were chosen in review without Shawn and are his to change (ADR-002, Amendments).
- Start pressed during the grace period: what was driven between the truck being found gone and the press belongs to neither trip.
- The grace deadline, the 30-minute limit and the moment a trip is cut at are times of day. If the phone corrects its clock by more than the time left, a grace period lasts longer or shorter than set, and a clock set back during the grace period lets the walk away from the truck be counted. Rare; logged as `[DEBT]` in FINDINGS_LOG.
- Android Auto holds a trip open in every case: it stops the grace period from starting, cancels one that is running, and pauses the no-movement rule for a manual trip. It never starts a trip.
- What the phone records during the grace period (Shawn walking away from the truck) is stored but is not part of the trip if the truck stays away. If the truck comes back, it is.
- The no-movement rule ends a manual trip where it last moved, not 30 minutes later.
- A companion "appeared" start that is not confirmed within 15 seconds (ADR-002) has no rule of its own yet. Today it would end through the grace period and be discarded for its distance. To be settled when the service is built.

---

## GPS recording and distance

**Status:** In progress (phase 1): the point filter, the distance calculation and the storage for raw points exist and are tested; nothing records a fix yet · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Records the truck's position during a trip and turns it into a distance in km, with start and finish addresses.

**Required behaviour**
- Record a GPS fix every 5 seconds using `FusedLocationProviderClient`, and count distance only after 10 m of real movement. Shawn's brief said "every 5 seconds or 10 m"; the location API cannot express "or" (its interval and distance settings combine as "and"), so the 10 m rule is applied in MilO's own distance calculation.
- Discard points with accuracy worse than 25 m, and impossible jumps (implied speed over 180 km/h).
- Store the raw points so distance can be recalculated later.
- Discard trips under 0.3 km (configurable), for example moving the truck around the yard.
- Reverse-geocode the start and end points to street addresses and store them with the trip. Start and finish locations and mileage are tracked automatically; there is no odometer entry anywhere in the app.

**How it works today**
No fix is requested or recorded yet. What exists is the calculation over stored points, `DistanceCalculator` in `core/trip/`. It takes the fixes in the order recorded and applies four rules to each:
1. **Accuracy.** A fix with no accuracy, or an accuracy radius over 25 m, is not used.
2. **Impossible jumps.** A fix that would need more than 180 km/h to reach from the last good fix is not used. The time between them comes from the phone's elapsed-realtime clock, which never jumps. A long gap (a tunnel) is not a jump, so the distance across it is counted as a straight line. If three rejected fixes in a row agree with each other, they are believed instead and counting restarts from them: this is what stops one bad fix, such as a stale first position, from poisoning the rest of the trip. The leap itself is never counted.
3. **Only real movement counts.** Distance is added only once the truck is at least 10 m from where distance was last counted, and at least twice the two fixes' own stated error: 20 m with 5 m fixes, 40 m with 10 m fixes, 100 m with 25 m fixes. What is added is the straight line from where distance was last counted. On a straight road that loses nothing; through a turn it cuts the corner.
4. **Out and back is not a drive.** If the fix after a counted step is nearer to where counting stood before the step than to where the step went, the step was a bad fix and is taken back. This is what keeps one bad fix 200 m away, while the truck stands at a light or sits parked, from adding 400 m.

Distance between two fixes is the haversine great-circle distance, in pure Kotlin so it runs in unit tests. It differs from Android's own ellipsoid calculation by at most about 0.5 %, far less than GPS error over such short steps.

Every fix is stored in `raw_points` whether or not these rules use it, with both clocks, its accuracy and its reported speed, so a trip can be recalculated later with better rules. The thresholds are named constants gathered in `DistanceLimits`.

**Where the code lives**
- `core/trip/`: `DistanceCalculator.kt` (the four rules and the thresholds), `Haversine.kt`, `TrackPoint.kt`, `TripClosing.kt` (minimum distance, end time and positions).
- `data/point/`: `RawPoint.kt` (the table), `RawPointDao.kt`, `RawPointRepository.kt`; the database is `data/PointsDatabase.kt`.
- Tests: `app/src/test/.../core/trip/DistanceCalculatorTest.kt`, `DistanceCalculatorParkedTest.kt`, `DistanceCalculatorOutlierTest.kt`, `DistanceCalculatorTurnsTest.kt`, `HaversineTest.kt`.

**Edge cases & gotchas**
- A stationary truck with Bluetooth connected still produces GPS jitter, which must not add phantom km.
- The 10 m rule alone does not achieve that. In a simulated hour of parked fixes scattered as a stated accuracy of 5 m implies, 10 m alone added about 360 m, enough to be kept as a trip; with the stated-error rule the same hour adds nothing. So the brief's "10 m" is a floor, not the whole rule. The thresholds are to be tuned from the raw points of the first test drives.
- **The stated-error rule costs distance on turns.** All numbers are simulations, not this phone. On a town route with a right-angle turn every 300 m at about 25 km/h, a fix every 5 seconds alone misses about 2 %. The calculation is short by the same 2 % with 5 m fixes, about 4 % with 10 m fixes and about 11 % with 25 m fixes. A truck circling inside a space smaller than the threshold (a yard, a small roundabout) with fixes of 8 m or worse measures nothing.
- **The end of a trip is short by up to the threshold:** up to 20 m with 5 m fixes, 40 m with 10 m, 100 m with 25 m, because the last stretch never crosses it. A real 350 m trip recorded with poor fixes can therefore come out under the 300 m minimum and be discarded (it is kept as `DISCARDED`, see below).
- **Bad fixes that are still believed** (simulated in review, not seen on this phone). A wrong first fix less than about 700 m from the truth stays as the trip's start position and adds up to its own distance from the truth. One wrong fix straight after a GPS gap is believed if the truck could have got there in the time of the gap, and the way back to the road is counted as well: after a 60-second tunnel, a fix 600 m to the side added 500 m to a 5.3 km drive, and one 2 km to the side added about 1 km. Two bad fixes in a row that agree with each other look like a real there-and-back. Not fixed, because the right cure depends on what this phone's bad fixes look like; the likely one is to hold a fix that follows a gap until the next fix agrees with it.
- Rule 4 has a price: pulling forward and reversing within one fix (shunting in the yard) is not counted.
- A trip under the minimum distance is marked `DISCARDED` and kept with its points, not deleted. If the distance was wrong, the trip can still be recovered.
- After a reboot in the middle of a trip the elapsed-realtime clock restarts, so the distance covered across the reboot cannot be checked and is not counted.
- The phone's own speed reading is stored but not used yet. It is the likely next refinement: for the parked-truck rule in place of stated accuracy (which would end the corner cutting), and to reject a leap from a fix that says it is standing still.
- If the phone's clock was wrong at the start of a trip and corrected during it, the distance is still right (points are cut by stored order, not by time), but the trip's stored start time is the wrong one and can even be later than its end.
- A trip can end with no signal, so geocoding must retry later.

---

## Trip-start sound

**Status:** Planned (phase 1) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Plays a short R2-D2 style droid chirp once, when the app has connected to the truck and is ready to track the trip. It is Shawn's audible confirmation that detection worked.

**Required behaviour**
- Plays once per trip start, at the moment recording has really begun. No sound on trip end.
- Comes from the phone speaker and behaves like a notification sound: silent when the phone is on vibrate or Do Not Disturb.
- Played by the trip service itself, not attached to a notification channel, because MIUI is reported to switch channel sounds off by default.
- The bundled sound is an original synthesized droid-style chirp. The film recording itself is copyrighted, so it is not shipped in the app. A setting lets Shawn choose any audio file on the phone instead.

---

## Safety net: manual button and driving alert

**Status:** Planned (button in phase 1, alert in phase 2) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Catches the case where Bluetooth never connects at all.

**Required behaviour**
- A Start/Stop button on the home screen starts or ends a trip by hand.
- During scheduled hours, if the phone's driving detection says Shawn is in a moving vehicle and the truck is not connected, notify him with a tap-to-start action. Needs the Physical activity permission.
- Driving detection never starts a trip by itself, because it would also log rides in other vehicles.
- A manually started trip still respects the schedule: it is classified Business or Personal by its start time, the same as an automatic one.

---

## Android Auto screen

**Status:** Planned (phase 1) · **Platforms:** Android Auto (projected from the phone) · **Last updated:** 2026-10-03

**What it does**
Shows MilO on the truck's Android Auto display, built with the Car App Library (`androidx.car.app:app-projected`). Shawn asked for it so he can see more easily that the app is running, and override it without touching the phone. It is in phase 1 because that visibility matters most during the first test drives.

**Required behaviour**
- Shows tracking status, the current trip's km and duration, and today's session count and total km.
- Start Trip and End Trip buttons act as a manual override. They drive the same trip logic as the phone's Start/Stop button, and a trip started here still respects the schedule (the schedule itself arrives in phase 2).

**Edge cases & gotchas**
- **At risk.** Google's documentation says Android Auto's "Unknown sources" setting does not apply to Car App Library apps: on a real head unit they must be installed from a trusted store such as Google Play (a private internal-sharing link counts). A build installed from Android Studio is expected to show in the desktop head-unit emulator but may not appear in the truck. Community reports conflict, so it gets tested on Shawn's truck in phase 1. If it does not appear, the choices are a private Play internal-sharing install, a media-app style workaround, or dropping the screen. See `docs/research/2026-10-03-android-auto-screen.md`.
- The rule "do not end the trip while Android Auto is connected" does not depend on this screen and works either way.

---

## Permission checklist

**Status:** Planned (phase 1) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
One screen showing every requirement as green or red, each with a button that takes Shawn to the place to fix it.

**Required behaviour**
- Items: precise location, background location ("Allow all the time"), notifications, Bluetooth connect, companion background permissions, battery optimisation exemption, Physical activity (for the driving alert).
- Xiaomi items for this phone: Background autostart, Battery saver "No restrictions", "Pause app activity if unused" off, the "Other permissions" switches (Show on Lock screen, Start in background, Permanent notification), and MilO locked in recents. Each has a button that opens the right HyperOS screen.
- The screen is honest about what it can verify. Autostart can only be read through an unofficial check, so it shows as "looks on", "looks off" or "unknown". The Battery saver profile and the recents lock cannot be read at all, so they are "confirm you set this" steps.

---

## Schedule and Business/Personal

**Status:** Planned (phase 2) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Decides whether a trip is Business or Personal from when it started.

**Required behaviour**
- Settings: choose which days to track and a start and end time for each day. Default Monday to Friday, 08:00 to 16:30. An option applies the same hours to all days.
- A trip that starts inside the schedule is saved as Business. A trip that starts outside it is saved as Personal, with a setting to ignore such trips entirely instead.
- Shawn can switch any trip between Business and Personal.
- If a Business trip is still running at the end time, keep recording until the trip ends and flag it "ran past schedule".

---

## Trip log: home, day and month views

**Status:** Planned (basic list in phase 1, the rest in phase 2) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Shows what was recorded and lets Shawn correct it.

**Required behaviour**
- Home screen: current trip status, plus today's sessions (count, km each, total km, total drive time).
- Day view: each session with start and end time, start and end address, km, and Business/Personal.
- Month view: sessions and business km per day, plus month totals.
- Shawn can edit any trip, delete trips, and manually add a missed trip. Manually added or edited trips are marked as such.
- There is no purpose or note field. Dropped at kickoff.

---

## Monthly PDF and submission

**Status:** Planned (phase 3) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Produces the monthly reimbursement report and hands it to Gmail.

**Required behaviour**
- Shawn picks any month, not only the current one, and generates a PDF of that month's Business trips. Built with Android's own `PdfDocument`, no third-party PDF library.
- Header: name, company, vehicle, month, generated date.
- One table per day with columns **Start, End, From, To, km**, then a daily subtotal. A month total km at the end.
- **No rate and no amount owed.** Accounts works the money out, so the report shows km only.
- No purpose column and no odometer readings.
- A blank signature line.
- Manual or edited trips carry an asterisk, with a legend.
- Send button opens Gmail with the accounts address, the subject "Mileage – [Month Year] – [Name]", and the PDF attached. Shawn taps send himself.
- After sending, the month is marked Submitted with the date. Each month shows submitted or not submitted. Resubmitting a month warns first, is allowed, and is labelled a revision.
- CSV export for any month.

**Edge cases & gotchas**
- Android cannot tell the app whether the email was really sent, so marking a month Submitted needs Shawn's confirmation.

---

## Diagnostics and reminders

**Status:** In progress (phase 1): the event log is stored, and crashes and process kills are captured into it at every start. There is no screen to read it on yet, and no trip events are written. The rest is phase 4 · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- A bare event log (the stored events and a plain list screen) ships in phase 1, because without it a missed trip start during the test drives cannot be diagnosed. Phase 4 finishes it.
- Event log screen: every Bluetooth connect and disconnect, Android Auto change, and service start and stop, with a timestamp. Crashes are written here too, because the app has no crash-reporting service.
- If the truck connects during scheduled hours and a trip fails to start, notify Shawn.
- A reminder notification on the 1st of each month (day configurable) to submit the previous month.

**How it works today**
1. **A crash.** `MiloApplication` installs `CrashHandler` as the process's uncaught-exception handler. When any thread throws, the handler writes one small text file (time, thread, exception, stack trace) to `no_backup/crashes/`, forces it to disk, and then passes the exception on to Android's own handler, which ends the process as usual.
2. **The next start, however it happens.** `MiloApplication.onCreate` launches `StartupDiagnostics.record()` on a background thread. It:
   - copies every waiting crash file into the event log as a `CRASH` entry, dated when the crash happened, and deletes the file;
   - asks Android for its record of why earlier MilO processes ended (`ActivityManager.getHistoricalProcessExitReasons`), keeps the ones newer than the last one already imported, and writes each as a `PROCESS` entry with the reason and the system's description text. On this phone the description is where a Xiaomi cleaner names itself;
   - writes a `PROCESS` entry for the new process.
3. Each record is written to the log and then marked done, one record at a time. A process that dies half-way imports at most that one record again. A duplicate line is possible; a lost one is not.
4. **Gathering evidence never stops the app.** Each of the three steps runs on its own. A step that throws is written to the log as an `ERROR` entry with its stack trace, and the next step still runs. If the event log itself cannot be written, the failure goes to a crash file (so it reaches the log at a later start) and the remaining steps are skipped, since all of them need the log.
5. `EventLogRepository` is the only way to write or read the log. Entries carry a time, a category, a one-line message and an optional detail.

Checked on an emulator (Android 16) on 2026-10-03: an induced crash, a force stop and a background kill each appeared in the log at the next start, once. Not yet run on the POCO X5.

**Where the code lives**
- `platform/diagnostics/`: `CrashHandler.kt`, `ProcessExitReader.kt`, `ProcessExit.kt`, `StartupDiagnostics.kt`.
- `data/crash/`: `CrashFileStore.kt` (the crash files and where they are kept).
- `data/eventlog/`: `EventLogEntry.kt` (the table and the categories), `EventLogDao.kt`, `EventLogRepository.kt`.
- `app/MiloApplication.kt` starts both; `app/AppContainer.kt` builds them.
- Tests: `app/src/test/.../platform/diagnostics/` and `app/src/test/.../data/crash/`.
- What can only be checked on the phone is in `docs/DEVICE_TEST_CHECKLIST.md`.

**Edge cases & gotchas**
- At most 20 crash files wait at once, so an app that crashes at every start cannot fill the phone. The first 20 are kept.
- Android keeps only a small number of exit records per app. If MilO is not started for a long time while being killed repeatedly, the oldest records are gone before they are imported.
- A crash produces two entries: the `CRASH` entry with the stack trace, and the system's `PROCESS` entry saying the process ended by crashing.
- Nothing trims the event log yet. It must be trimmed before phase 4 switches backup on, because it shares the backed-up database with the trips (`[DEBT]` in FINDINGS_LOG).
- An unreadable settings file is never reset: a silent reset would lose the truck pairing without a trace. It also no longer crashes the start (that was a crash at every start, with no way out but clearing the app's data, trips included). The start-up import logs an `ERROR` entry and carries on, and the exit records are not imported while the file stays unreadable. Whatever reads the settings next (pairing, the permission checklist, the trip controller) has to deal with the failure itself; none of them is built yet.
- If neither the event log nor the crash folder can be written, start-up does crash: there is nowhere left to record the failure.

---

## Backup, export and import

**Status:** Planned (phase 4) · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- The main database (trips, submission status, settings, event log) is included in Android Auto Backup. Raw GPS points live in a second database file that stays out of cloud backup, because Auto Backup is capped at 25 MB and backs up nothing at all once that is exceeded.
- Until phase 4 backup is switched off entirely (`allowBackup="false"` plus `data_extraction_rules.xml`), so a half-configured backup cannot restore stale data.
- Manual export and import of all data to a file.

**What exists today**
The two database files exist: `milo.db` (trips and the event log) and `points.db` (raw GPS points), both in the app's databases folder. Crash files wait in `no_backup/crashes/`, which Android never backs up. Backup is still off for everything. The file names, and the side files Room keeps beside each, are in ARCHITECTURE §6 for the phase 4 backup rules. Submission status is not stored yet, and settings are in a DataStore file, not in the database.

---

## Settings

**Status:** In progress (grows each phase): the phase 1 values are stored; there is no settings screen yet · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- Truck: the paired device, with a way to change it.
- Trip: disconnect grace period (default 2 minutes), minimum trip distance (default 0.3 km), trip-start sound (bundled chirp or a chosen audio file).
- Schedule: tracked days and hours, and whether out-of-schedule trips are saved as Personal or ignored.
- Report: name, company, vehicle description, accounts email. There is no rate-per-km setting.
- Reminder: day of the month.

**How it works today**
`SettingsStore` in `data/settings/` keeps the settings in a DataStore Preferences file and is the only way to read or write them. `settings` is a flow of `MiloSettings` that delivers the current values and every change; `current()` reads them once. A value never written reads as its default. Each setter refuses a value that cannot be right: a negative duration, distance or time, and a blank address, name or URI. "No truck name" and "no custom sound" are passed as null. If the file cannot be read, every read throws: the store never replaces it with empty settings.

Stored now: the truck's Bluetooth address, name and companion association id; the grace period (default 120 s); the minimum trip distance (default 300 m); the trip-start sound on or off (default on) and an optional custom sound; and two values that are not Shawn's to choose but must outlive the process (the hold-off after a manual end, and how far the process-exit records have been imported). Schedule, report and reminder settings are not stored yet. Nothing reads these values yet except the crash and kill capture.

**Where the code lives**
`data/settings/SettingsStore.kt` and `MiloSettings.kt` (the values and their defaults). Test: `app/src/test/.../data/settings/SettingsStoreTest.kt`.

---

## Design system

**Status:** Live · **Surfaces:** Phone · **Last updated:** 2026-10-03

**What it does**
The one place colours, spacing, type and shapes are defined, and the shared components every phone screen is composed from. Check here before building any UI: reuse what exists, and add to it deliberately when something is missing (STANDARDS §8).

**What exists**
- Tokens in `core/designsystem/theme/`: `Color.kt` (light and dark schemes plus the ok/problem status colours), `MiloSpacing.kt` (4, 8, 16, 24, 32 dp steps), `Type.kt`, `Shape.kt`. `MiloTheme` applies them and exposes `MiloTheme.spacing` and `MiloTheme.statusColors`.
- Components in `core/designsystem/component/`: `PrimaryButton` (the main action on a screen), `SectionCard` (a titled group of content), `StatusRow` (a label with a met / not-met indicator and an optional action button; its icons are in `StatusIcons.kt`).

**Edge cases & gotchas**
- No colour, dp or sp value may be written outside `core/designsystem/`. The only exceptions are the two launcher-icon drawables, which the launcher reads before Compose exists.
- `StatusRow` has two states today. The permission checklist needs more ("looks on", "looks off", "unknown", "confirm you set this"). Logged as `[DEBT]`; fix it when the checklist is built.
- The Android Auto screen cannot use these components. It is drawn by the car from Car App Library templates.

---

## Entry template (copy this for each new capability)

```
## [Feature name]

**Status:** Planned / In progress / Live · **Surfaces:** Phone / Android Auto / Both · **Last updated:** [date]

**What it does**
[one or two sentences]

**How it works (step by step)**
1. [trace the real path: which screen → which ViewModel → which repository or platform class → what happens]

**Where the code lives**
[the feature folder / key files]

**Depends on**
[services, other features, external libraries]

**Edge cases & gotchas**
- [the non-obvious behaviors that will bite later]

**Related**
[links to related entries]
```
