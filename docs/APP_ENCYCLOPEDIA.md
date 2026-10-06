# App Encyclopedia

> **What this is.** The complete *reference* for every capability the app has and exactly how it works — the "how does this actually behave?" book. When you (or your AI assistant) forget how a feature works, or before you change one, you read its entry here first. This is the single biggest defence against "I changed X and didn't realise it broke Y."
>
> **How to maintain it.** One entry per feature/capability, using the template below. **Update the entry in the same change that alters the feature** — a stale encyclopedia is worse than none, because it lies confidently. If behaviour changed and this didn't, the Definition of Done (STANDARDS §10) wasn't met.
>
> **Status:** Living document · **Last updated:** 2026-10-06 · **See also:** ARCHITECTURE.md (the map), FINDINGS_LOG.md (history of changes)

---

## How to read an entry

Each capability is documented with: what it does, who can use it, how it works step by step, where the code lives, what it depends on, edge cases, and which platforms it's on. Copy the template at the bottom for every new feature.

**Most entries are still `Planned`.** They record the founder's brief of 2026-10-03 plus the answers given at kickoff, so they describe required behaviour, not code. An entry marked `In progress` has a "How it works today" and a "Where the code lives" section. Those two sections describe only what exists; everything under "Required behaviour" that they do not mention is not built.

**What exists after the screens (work package 3):** storage, the trip rules as tested pure code, crash and kill capture, everything that records a trip once one is started (the trip controller, the foreground service, GPS recording, the notifications, the trip-start sound, the watch on Android Auto), everything that starts and ends one by itself (the Bluetooth receiver, the companion service, the boot and update receiver, the reading of the truck's connection, pairing with the truck), and five screens behind a bottom navigation bar: Home, Trips (one month at a time), Setup (the permission checklist, with the truck pairing screen opened from it) and Log (the event log). **None of the automatic part has met the truck yet:** no truck is paired on the phone. The screens of work package 3 were built and proven with the build and unit tests only, and have been seen since as follows. On the phone (2026-10-05, FINDINGS_LOG, "First results from the phone"): Shawn went through Setup, everything except pairing the truck, and started a trip with Home's button. On an emulator: Trips (2026-10-05) and the pairing screen, with no Bluetooth device to list (2026-10-06). Nothing records the Log screen being drawn anywhere. No check of `docs/DEVICE_TEST_CHECKLIST.md` has been run as it is written; what the phone has to prove is there.

**Added by work package 4:** each finished trip gets a start and an end address, looked up with the phone's own geocoder and shown on the Trips screen as "from → to" (see [GPS recording and distance](#gps-recording-and-distance) and [Trip log](#trip-log-home-day-and-month-views)). It was pulled forward from phase 2 on its own; the day view, Business/Personal and editing are still phase 2. Run on an emulator, not on the phone.

**Added on 2026-10-05, the last piece of phase 1:** the [Android Auto screen](#android-auto-screen). It has never been drawn anywhere, not on the truck and not in Google's desktop emulator of a car display, and Google's documentation says a build installed from Android Studio is not expected to appear on a real truck at all. Device checks 88 to 100.

**Added on 2026-10-06, the rest of phase 1:** trips can be deleted and restored on the Trips screen, and a trip MilO discarded can be counted after all; the home screen lists today's finished trips; and there is a [Settings](#settings) screen, opened from Home, for the truck, the grace period, the minimum trip distance and the trip-start sound, including a sound of Shawn's own choosing. Drawn and used on an emulator, never on the phone. Device checks 101 to 127.

**Added on 2026-10-06, the first piece of phase 2:** the [work schedule and Business / Personal](#schedule-and-businesspersonal). Settings has the seven days with their hours and the choice for trips outside them; every trip is saved as Business or Personal when it ends, by when it started; any finished trip can be marked the other way on the Trips screen; and the month, each day and Home's Today show Business first and Personal apart. The main database is now version 3. Run on an emulator, the migration included, never on the phone. Device checks 128 to 152.

**The app in one line:** MilO is a personal Android app (native Kotlin, one phone, no backend, no accounts, no Play Store) that automatically logs business mileage in Shawn's work truck and produces a monthly PDF to email to accounts.

**Target phone:** Xiaomi POCO X5 Pro 5G (global ROM), Android 14, HyperOS 2.0. Installed from Android Studio on a Mac mini.

**Build phases.** The brief said to stop after each phase so Shawn can test on the phone. On 2026-10-05 he replaced that: finish phase 1, then carry on through everything else in the brief without stopping between phases (FINDINGS_LOG, 2026-10-06). The phases still say in which order things are built, and each still has its device checks.
1. Truck pairing, trip detection, GPS logging, trip-start sound, manual Start/Stop, Android Auto screen, permission checklist, basic trip list, bare event log. **Built.** The brief had Shawn drive with this for a few days before phase 2; the drives now go on beside the later phases.
2. Schedule settings, Business/Personal, driving alert, day and month views, editing, manual trips. **Built so far:** the schedule settings and Business/Personal, with the figures of each day and month (2026-10-06).
3. Monthly PDF, email, submitted status, CSV export.
4. Diagnostics, reminders, backups, polish.

---

## Index

- [Truck pairing and trip detection](#truck-pairing-and-trip-detection) — phase 1
- [GPS recording and distance](#gps-recording-and-distance) — phase 1
- [Trip-start sound](#trip-start-sound) — phase 1
- [Safety net: manual button and driving alert](#safety-net-manual-button-and-driving-alert) — phases 1 and 2
- [Android Auto screen](#android-auto-screen) — phase 1
- [Permission checklist](#permission-checklist) — phase 1 (the Setup screen)
- [Schedule and Business/Personal](#schedule-and-businesspersonal) — phase 2, built
- [Trip log: home, day and month views](#trip-log-home-day-and-month-views) — phases 1 and 2
- [Monthly PDF and submission](#monthly-pdf-and-submission) — phase 3
- [Diagnostics and reminders](#diagnostics-and-reminders) — phases 1 and 4
- [Backup, export and import](#backup-export-and-import) — phase 4
- [Settings](#settings) — the phase 1 screen is built; grows each phase
- [Design system](#design-system) — live, grows as screens need it

---

## Truck pairing and trip detection

**Status:** In progress (phase 1): built, and not yet run against a real truck. On the phone only a trip started with the button has been recorded (2026-10-05); no truck is paired there. The pairing screen has been drawn only on an emulator with no Bluetooth device to list (2026-10-06) · **Platforms:** Android · **Last updated:** 2026-10-06

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
Designed in `docs/adr/ADR-002-trip-detection.md`. First pairing, then what tells MilO about the truck, then the path a trigger takes, then the rules.

*Pairing*
- **The pairing screen** (`feature/pairing/`) is opened from the Setup screen's truck row, and from the Settings screen's "Change truck" (or "Pair truck") button. Its Back arrow leads back to whichever of the two it was opened from. It shows three cards. "Your truck": the stored truck and, in plain words, whether Android is watching for it, followed by how the attempt just made went. "Before you can pick the truck", only when something is in the way. "Paired with this phone": the phone's paired Bluetooth devices, named, each with its own button (Pair; Pair again on the stored truck; Use this one on another device once a truck is stored).
- **What can be in the way,** one at a time and in this order: Nearby devices is not allowed (the card's button asks for it with Android's dialog); the phone has no Bluetooth; Bluetooth is switched off (a button to the Bluetooth settings); location is switched off (a button to the Location settings; the devices are listed but their buttons are greyed out, because Android needs location on to make the association); nothing is paired with the phone (the Bluetooth settings again). The screen reads the phone again every time it comes to the front, which includes the quick settings panel closing over it (see [Design system](#design-system), `CameToFrontEffect`). It reads once more when Android reports that Bluetooth has finished switching on or off (`bluetoothSwitchChanges`): switching on takes the phone a second or two, and Shawn is back on the screen sooner than that.
- **Picking a device** calls `TruckPairing.associate` with the screen's Activity. Android hands back its consent dialog, which the screen shows once (the ViewModel remembers which one it showed, so a rotation does not show it twice), and the dialog's result goes to `TruckPairing.onConsentResult`. The screen then says one of: "Paired with (name)" and, above it, that Android is watching for the truck; "Not paired. Android's dialog was closed without allowing. Nothing was changed."; or "Pairing failed", with `TruckPairing`'s own reason word for word and what to try. "Not paired" is shown only when Shawn himself closed the dialog (result 0) or pressed "Don't allow" (result 1). From Android 13 the dialog can also end because Android gave up looking for the device or failed inside; every such result is "Pairing failed" (`consentWasDeclined` in `PairingUiState.kt`).
- **Changing the truck** is picking another device. `TruckPairing` stores the new truck and removes every other association, the old truck's among them.
- What the screen shows is decided by one pure function, `pairingUiState`, from the phone's device list, whether location is on, the stored truck, `TruckPairing.status` and `TruckPairing.progress`. `progress` belongs to the whole app and outlives the screen, so the result of an attempt made on an earlier visit is not shown.
- `TruckPairing` is what the screen calls: `pairedDevices()` lists the phone's paired Bluetooth devices; `associate(device, activity)` asks Android for a companion device association with the chosen one (an address filter, single-device mode, no device profile: the one combination that finds an already-paired device without a scan); Android answers with a consent dialog for the screen to launch, delivered through `progress`; `onConsentResult` takes the dialog's result.
- When the association exists, the truck's address (in capitals), name and association id are stored in the settings, every other association MilO holds is removed, and Android is asked to observe the truck: from then on it binds MilO's companion service whenever the truck connects. The trip controller is then asked to read the truck, because Android does not always report a truck that is already connected.
- `check()` runs at every process start and every time MilO comes to the front. It compares the stored truck with the associations Android lists and asks Android again to observe (asking twice changes nothing). The result is one of: armed, no truck, association missing (it was removed in the phone's settings), not supported (a truck is stored, but the phone has no companion device support), failed. It is logged as a `PAIRING` line each time. While no truck is armed the line also lists the phone's paired devices, and says so if the phone has no companion device support.
- An association with no truck stored for it is adopted as the truck, if it is the only one and its device is paired with the phone. That is how a truck can still be paired over adb if the screen fails: the association is made over adb (`docs/DEVICE_TEST_CHECKLIST.md`). An association for an address that is not among the phone's paired devices is turned down, and the `PAIRING` line says so.
- The same adoption replaces a stored truck whose own association is gone. Over adb it is the way back from a wrong truck, and the way to pair another one if the screen fails: remove the association, make the right one, open MilO. A stored truck that still has its association is never replaced by adoption.

*What tells MilO about the truck*
- **The Bluetooth receiver** (`TruckBluetoothReceiver`, in the manifest and exported) hears four broadcasts for every Bluetooth device: the link going up, the link going down, and the hands-free and the audio profile changing state. It looks the truck up in the settings and compares addresses. For another device it writes one "Ignored" line to the event log and does nothing else. For the truck: the classic link going up is a "link connected" trigger and going down a "disconnected" trigger, both trusted as they stand; a profile reaching "connected" or "disconnected", and a low-energy link to the truck's address, are prompts to read the truck's connection.
- **The same receiver, registered by the trip service** for as long as a trip is recorded, so that a disconnect is heard even if the manifest receiver is not served. During a trip each broadcast therefore arrives twice, and the event log shows both.
- **The companion service** (`TruckCompanionService`). Android binds it when the truck connects, starting MilO's process if it has to. "Appeared" is a trigger that starts a trip which must be confirmed (rule 12); "disappeared" is a "disconnected" trigger. Android has had three shapes of this callback (Android 12; 13 to 15; 16 and later) and newer versions also send the older ones, so each shape acts only on the versions for which it is the newest. Every time the service is created it also prompts a reading, because Android rebinds it after MilO's process died without repeating "appeared".
- **The reconcile receiver** (`TruckReconcileReceiver`): the phone has booted, or MilO was updated. Both prompt a reading.
- **Process start and MilO coming to the front** prompt a reading too (`MiloApplication`, `MainActivity.onStart`).
- A manifest receiver keeps its broadcast open until the controller has dealt with the trigger (8 seconds at most), so the process is not frozen between `onReceive` returning and the truck being read.

*Reading the truck* (`TruckConnectionSource`, implemented by `BluetoothTruckConnection`)
- The answer is connected, not connected or unknown, with a few words on how it was reached. Those words are part of the event-log line of the trigger that asked.
- On this phone (and up to Android 16) there is no way to ask whether a device is connected. MilO asks the hands-free profile and the audio profile which devices they are connected to, each through a proxy fetched for that one reading and handed back after it. One profile listing the truck is enough. "Not connected" needs both to say so.
- From Android 16 QPR2 it asks the link itself (`BluetoothDevice.isConnected`).
- Unknown: the Bluetooth permission is missing, the settings cannot be read, or a profile gave no answer within 3 seconds while the phone says some device is connected on it. Unknown is never taken for connected. A reconcile or a once-a-minute reading that comes back unknown changes nothing; a timer or a button goes by what was already believed; a process start counts it as "not connected".
- No truck paired, no Bluetooth on the phone, or Bluetooth switched off: not connected.

*The path of a trigger*
- Everything that can prompt a trip calls one function, `TripController.onTrigger(trigger, source)`. The callers are the Bluetooth receiver, the companion service, the reconcile receiver, `MiloApplication` (a reconcile at every process start), `MainActivity` (a reconcile every time MilO comes to the front), `HomeViewModel` (the Start and End buttons), the Android Auto screen (its one button, `platform/car/TripStatusScreen`), `TruckPairing` (a truck was just stored) and the trip service (its timers).
- The controller puts the trigger in an inbox. One coroutine (`TripWorker`) works the inbox off in order, so triggers on different threads cannot interleave.
- For each trigger the worker reads the truck's connection if the trigger calls for it (`TripEvidence`), asks the trip rules what should happen, and then: writes a line to the event log with the source and the state before and after; writes each effect to storage (`TripLedger`) and logs it; and only then tells the service and the screens.
- **The service comes first.** If the outcome is an open trip and the trip service is not in the foreground, nothing is stored. The controller runs the preflight (precise location, "Allow all the time", location switched on, battery not "Restricted", Nearby devices allowed) and asks Android for the service, with the trigger in the start intent. The service's first statement is `startForeground`; once that has succeeded it hands the trigger back, and the trip is opened. A connect or a press of Start asks for the service straight from the thread the trigger fired on, without waiting for the worker, because Android's allowance for such a start lasts seconds.
- **If the start fails** at the preflight or in Android, no trip row is opened. The reason goes to the event log, the home screen shows it, and a high-importance notification says "MilO could not start this trip. Tap to start". The tap starts the service directly.
- **If the service is lost in mid-trip** (Android destroyed it while the process lives), the worker asks for it again. If Android refuses, the worker drops the trip it holds in memory: the home screen stops showing a trip in progress and shows why. The trip row stays open in storage, and the next trigger picks it up through the restart rules below, which close it if it has gone stale.
- When the last trip closes, the worker tells the service to stop. A service that was started for nothing (the hold-off was set, or the event was a duplicate) is stopped the same way.
- After the process was killed, the first trigger makes the worker read the open trip and its stored fixes and apply the restart rules below. `START_STICKY` has Android create the service again by itself, which counts as a reconcile. The service has `stopWithTask="false"`, so swiping MilO out of the recent apps does not end a trip.

*The rules*
1. `TripStateMachine.step(state, event, rules)` takes the current state and one event and returns the new state plus a list of effects (start a trip, mark the truck seen, start or cancel the grace period, end the trip, set or release the hold-off). It touches nothing itself: the controller carries the effects out.
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
10. **The hold-off ends** on whichever of three things comes first: a reading that shows the truck disconnected; a link-level connect event (the ACL connect broadcast or the companion "appeared" callback) more than 60 seconds after End was pressed; or 12 hours, applied at the next reading of the truck. The time of the End press is stored with the hold-off.
11. **A lost disconnect is caught by a reading once a minute.** While a trip is open the service prompts the controller every 60 seconds to read the truck's connection. "Connected" is always believed. "Not connected", when the rules believe the truck connected, is believed only the second time in a row; the first is logged and otherwise ignored. Any other trigger in between starts the count again. If the minute timer is late (the phone slept), a GPS fix that arrives 70 seconds or more after the last reading prompts it, and a timer that then fires within 30 seconds of that reading is skipped (`PollPacer`), so the readings stay about a minute apart.

    **A reading of "not connected" counts only once a reading has seen the truck.** A trip that rests on a link-level connect event is not doubted by a reading until some reading has shown the truck connected on that link. This holds for the minute reading and for a reconcile alike (MilO opened, the companion service created, a profile broadcast). Such a reading is logged with "No reading has shown the truck connected since its link was made, so this proves nothing" and changes nothing. A new link event starts this again. A disconnect event is always trusted.

12. **The companion "appeared" callback starts a trip that must be confirmed.** When idle it opens a trip at once, with the truck not yet marked as seen and a deadline 15 seconds on. A link-level connect event or a reading of "connected" confirms it, and it is an ordinary truck trip from then on. At the deadline the controller reads the truck; if it is still not connected (or cannot be read) the trip ends as a false start: no grace period, and it is discarded whatever distance the phone covered. When a trip is already open the callback changes nothing about the trip; all it can do there is release a hold-off that is more than 60 seconds old. The trip-start sound waits for the confirmation.
13. **Android Auto holds a trip open, but not for ever.** The service watches `CarConnection` from the moment recording begins and reports every change. While Android Auto is believed connected, the service makes the library ask again once a minute. Once Android Auto alone has held a trip open for 12 hours it is taken for a stuck value and no longer believed, until it has been seen to report "not connected".

**Where the code lives**
- `core/trip/`: `TripStateMachine.kt` (the rules), `OpenTripRules.kt` (what an open trip should be doing), `HoldOffRules.kt` (when the hold-off ends), `TripState.kt`, `TripEvent.kt`, `TripEffect.kt`, `TripClosing.kt`, `TripStatus.kt`, `TripStartCause.kt`, `LostDisconnectDetector.kt` (two readings in a row), `PollPacer.kt` (when the minute reading is due, by timer or by GPS fix), `AndroidAutoHoldGuard.kt` (the 12 hours).
- `platform/trip/`: `TripController.kt` (the entry point and the inbox), `TripWorker.kt` (one trigger at a time: rules, storage, log, service), `TripEvidence.kt` (turns a trigger into what the rules are told, and decides which readings count), `TripRuleSettings.kt` (the settings the rules run with), `TripLedger.kt` (carries the effects out in storage), `TripServiceLink.kt` (what the service was last told), `TripTrigger.kt` (the triggers, and the two interfaces between controller and service), `TripActivity.kt` (what the screens show), `TripLogText.kt` (the wording of the event-log lines), `TripService.kt`, `TripServiceIntent.kt` (how a trigger rides in the service's start intent), `TripServiceStarter.kt`.
- `platform/system/TripPreflight.kt`: the check before the service is started.
- `feature/pairing/`: `PairingScreen.kt` and `PairingCards.kt` (the screen), `PairingViewModel.kt`, `PairingUiState.kt` (what the screen shows and the function that decides it). The buttons to the phone's settings go through `platform/system/SystemScreens.kt`.
- `platform/bluetooth/`: `TruckBluetoothReceiver.kt` (the four broadcasts, in the manifest and in the trip service), `TruckCompanionService.kt` (the three shapes of companion callback), `TruckReconcileReceiver.kt` (boot and update), `TruckSignals.kt` (what each broadcast and callback means, and whether it is about the truck: plain functions), `Truck.kt` (the stored truck, and `PairedTruck`, which looks it up for a trigger), `TruckConnectionSource.kt` (the question "is the truck connected right now?" and the reading it returns), `BluetoothTruckConnection.kt` and `ProfileProxy.kt` (the answer), `TruckPairing.kt` and `PairingStatus.kt` (pairing), `TruckPairingCheck.kt` (the check that it is still armed, and adoption), `CompanionLink.kt` (everything asked of Android's companion device manager, with the differences between Android versions), `PairedDevices.kt` (the phone's paired devices), `BluetoothSwitch.kt` (says when the phone's Bluetooth has finished switching on or off, for the pairing screen).
- `platform/car/AndroidAutoWatcher.kt`: the watch on `CarConnection`.
- `data/trip/`: `Trip.kt` (the table), `TripDao.kt`, `TripRepository.kt`. The hold-off is in `data/settings/SettingsStore.kt`.
- `app/AppContainer.kt` builds the controller, the reading and the pairing; `app/MiloApplication.kt` sends the reconcile and the pairing check at process start, and `app/MainActivity.kt` both again each time MilO comes to the front. The manifest declares the two services, the two receivers and the permissions they need.
- Tests: `app/src/test/.../core/trip/TripStateMachine*Test.kt` (the rules in ADR-002's order, how a grace period ends, the buttons, the hold-off, the companion start, awkward orderings, random sequences, restarts), `TripClosingTest.kt`, `LostDisconnectDetectorTest.kt`, `PollPacerTest.kt`, `AndroidAutoHoldGuardTest.kt`; `app/src/test/.../platform/trip/TripController*Test.kt`, which run the controller against stand-ins for storage, the truck and the service (`TripControllerReadingTest.kt` for the triggers and for a truck that cannot be read, `TripControllerPollTest.kt` for which readings of "not connected" count, `TripControllerRestartTest.kt` for a lost process, a lost service and failing storage); `app/src/test/.../platform/bluetooth/`: `TruckSignalsTest.kt`, `TruckTest.kt`, `BluetoothTruckConnectionTest.kt` (what is made of the two profiles' answers), `TruckPairingTest.kt` and `TruckPairingCheckTest.kt` (against a stand-in for Android's companion device manager, in `TruckPairingFakes.kt`) and `BluetoothSwitchTest.kt`; and `app/src/test/.../feature/pairing/PairingUiStateTest.kt` (what the pairing screen shows for each thing the phone and `TruckPairing` can report, and which results of the consent dialog count as declined). The receivers, the companion service, the calls into Android's Bluetooth and the screen itself have no unit tests: there is no Robolectric (STANDARDS §11).
- What only the phone can prove is in `docs/DEVICE_TEST_CHECKLIST.md`.

**Depends on**
Bluetooth permissions, companion device association, the foreground service, and on this phone the HyperOS background settings (see [Permission checklist](#permission-checklist)). Background location ("Allow all the time") is mandatory, not optional: on Android 14 a trip started while the app is in the background fails without it.

**Edge cases & gotchas**
- Phone reboots or the app is updated while the truck is already connected: no connect broadcast fires, so the existing connection has to be detected another way.
- With HyperOS Autostart off and MilO not running, decompiled system code shows the connect broadcast is dropped and the system's attempts to start MilO's services are rejected. Autostart is off by default for a sideloaded app. This is unconfirmed on Shawn's phone until tested, but Autostart is treated as mandatory. See `docs/research/2026-10-03-miui-background-limits.md`.
- After a reboot, Android holds Bluetooth events until the phone has been unlocked once, so a trip begun before the first unlock starts recording at unlock.
- Swiping MilO away in recents, the clear-all button and Xiaomi's cleaners can kill it while it waits for the truck. Force stop leaves it unable to start until Shawn opens it again.
- Ending a trip by hand while the truck is still connected holds automatic start off. Pressing End with no trip open and the truck connected does the same, and pressing End again restarts the hold-off from that press. It ends as rule 10 says. What is left of the old risk: if the disconnect and the next connect event are both missed, the trip after that starts only when something reads the truck as connected 12 hours or more after the End press.
- A connect event within 60 seconds of the End press does not release the hold-off, even if the truck really did drop and reconnect in that minute. The disconnect itself releases it if it is seen.
- A lost disconnect is noticed between one and two minutes late (rule 11), and the grace period starts then. The trip is cut at that later moment, so up to two minutes of walking away from the truck can be counted. With the phone asleep and no GPS fixes arriving, the readings come later still, and so does the cut.
- **The once-a-minute reading is only as good as the check behind it, and on this phone the check has never run.** It looks at the hands-free and the audio profile. A truck that is connected on neither reads as "not connected" for the whole drive. The second half of rule 11 keeps that from ending the trip: the readings are logged every minute and change nothing. The price on such a truck: a disconnect that is never reported is not caught by a reading, so the trip then stays open until End is pressed or the process dies (the restart rules close it). And a process that restarts in mid-drive finds the truck "not connected" and ends the trip after the grace period. Device check 32 is the test. The reading through the two profiles is the one piece of the automatic part that ran nowhere, not even on the emulator, whose Android version asks the link instead.
- In the first moments of any trip a link event started, before a reading has shown the truck, a lost disconnect is not caught either (rule 11). On an ordinary truck that is the few seconds until a profile connects.
- A truck's connection that cannot be read ("unknown") never ends a trip that is recording, and never starts one. The price: while every reading is unknown, a disconnect that was never reported is not noticed either. Each unknown reading is a line in the event log.
- At a process start an unknown reading counts as "not connected". A trip that was recording then starts its grace period (a later reading can still cancel it), and a hold-off is released as if the truck had been seen gone.
- A broadcast for the truck is recognised by its address alone. If the settings cannot be read within a second, the receiver cannot tell whose broadcast it is: it then asks for a reading of the truck in place of trusting the event, so a link connect does not start the trip at once.
- The receiver writes a line for every Bluetooth device that connects or disconnects (earbuds, a watch), with its address, marked "Ignored". They prove that the broadcast arrived, which is what the first weeks on the phone are about; they also add to a log that nothing trims yet.
- The companion "disappeared" callback is trusted as a disconnect. Should Android send one while the truck is still connected, the trip goes into its grace period and the next reading (within a minute) cancels it. With no trip open it releases a hold-off, and the truck then starts a trip at the next reading.
- A hands-free or audio profile that connects while the hold-off is set does not release it and does not start a trip: it is not a new link. A profile that drops is another matter. Its broadcast prompts a reading, and if it was the only profile connected (the usual case with Android Auto on a cable, where only hands-free is up), the reading says "not connected". That releases the hold-off, and the profile coming back starts a trip, although the link never dropped and Shawn never left the truck. Left that way on purpose: any reading of "not connected" releases the hold-off, because a missed trip is worse than an unwanted restart (ADR-002, amendment 24). Device check 47 shows whether this truck does it.
- On Android 15 and 16, asking Android to observe a truck that is already connected binds the companion service but sends no "appeared". Seen on the emulator. The reading taken each time the companion service is created covers it; this phone runs Android 14, which does send it.
- Over adb, another truck is paired the way the first is: remove the stored truck's association, make one for the new truck, and open MilO; the lone association is adopted in place of the stored truck. While the stored truck still has its association, adoption replaces nothing. The pairing screen has no such limit.
- On a phone that does not report companion device support there is no association to adopt, and the `PAIRING` line says so. Only the pairing screen can store a truck there: it stores it without an association, so that the Bluetooth receiver knows which device to listen for. Whether HyperOS reports the feature is not known until tried.
- **Android's consent dialog has never been shown by any build of MilO.** The pairing screen has been drawn only on an emulator that has no Bluetooth device to list (2026-10-06, opened from Settings), so no device has ever been picked on it. Device checks 67 to 72 are the first time.
- The pairing screen cannot remove the truck without pairing another. Nothing asked for that.
- Changing the truck while a trip is being recorded is not prevented. The trip controller is told that the truck changed and reads the new truck's connection, and the trip rules go by the new truck from then on. What that does to a trip the old truck was holding open has not been tried.
- If Android never answers a request to pair, the screen stays on "Waiting for Android" and no device can be picked. Leaving the pairing screen and opening it again lets Shawn pick again.
- The pairing screen's Back arrow closes that screen and nothing else, however often it is tapped while the screen slides away (`closeIfOnTop`). Before, a second tap closed Setup too and a third crashed MilO.
- The receiver that hears Bluetooth being switched on or off is registered only while the pairing screen is on the back stack. It has never received a broadcast: device check 70.
- A failed pairing is shown with `TruckPairing`'s reason as it is written for the event log, in English and with the exception's name where there is one. It is exact, not pretty.
- Pairing a truck again makes a second association on some Android versions. MilO keeps the one with the highest id and removes the others.
- On Android 12 the association is not reported through a callback, only through the result of the consent dialog. If Android has not listed it by the time MilO looks, pairing fails with "lists no association" and has to be repeated. Never seen; this phone runs Android 14.
- The timers of a trip (grace period, confirmation, the minute) are coroutines in the service and can fire late while the phone sleeps. A GPS fix stands in for a late timer: for a deadline once it is 10 seconds overdue, for the minute reading 70 seconds after the last one. The overdue deadline is dealt with before that fix is counted, so the first fix after a long silence closes a forgotten manual trip where it last moved; it does not count as new movement. With no fixes arriving (indoors) a trip can stay open, and the service running, past its grace period until the phone next wakes. A trip that runs out of grace is still cut at the right moment; only its closing is late. A disconnect that was never reported is different: the trip is cut when the second reading is taken, so late readings mean a late cut.
- The process killed (or frozen) while a trip is open: the next morning's connect does not join yesterday's trip. A trip in grace is closed by its stored deadline, restart or not. After a restart, a trip that was recording is closed at its newest point if that is more than 30 minutes old; up to 30 minutes it carries on, and the gap is counted as a straight line. A restart also closes a trip that stored no point for 30 minutes for any other reason, such as location switched off. A process that stays alive through a lost disconnect relies on the once-a-minute reading (rule 11).
- The 30 seconds and the 30 minutes are constants in `TripState.kt` (`LATE_CHECK_TOLERANCE_MS`, `RESTART_GAP_LIMIT_MS`). They were chosen in review without Shawn and are his to change (ADR-002, Amendments).
- Start pressed during the grace period: what was driven between the truck being found gone and the press belongs to neither trip.
- The grace deadline, the 30-minute limit and the moment a trip is cut at are times of day. If the phone corrects its clock by more than the time left, a grace period lasts longer or shorter than set, and a clock set back during the grace period lets the walk away from the truck be counted. Rare; logged as `[DEBT]` in FINDINGS_LOG.
- Android Auto holds a trip open in every case: it stops the grace period from starting, cancels one that is running, and pauses the no-movement rule for a manual trip. It never starts a trip.
- What the phone records during the grace period (Shawn walking away from the truck) is stored but is not part of the trip if the truck stays away. If the truck comes back, it is.
- The no-movement rule ends a manual trip where it last moved, not 30 minutes later.
- A companion start is not confirmed by Android Auto being connected, only by the truck's own connection (ADR-002, amendment 7, taken literally).
- If the process dies in the 15 seconds a companion start waits to be confirmed, the deadline is worked out again from the stored trip (started by the truck, truck never seen) and the same rule applies at the restart.
- If Android refuses the service, the trigger is not retried. The notification is the way back: a tap starts a manual trip, or picks up the trip a killed process left open.
- The tap starts the trip service directly. On stock Android that is allowed even from a dead process. On HyperOS with Autostart off, the research expects a service start in a dead app to be rejected; the tap would then do nothing and leave no line in the log. Untested: device check 19a. If it fails there, the tap has to open MilO and let the visible screen start the trip.
- The preflight asks for "Allow all the time" and for Nearby devices even for a press of Start with the app open. Until both are granted, no trip can be recorded at all.
- An unreadable settings file does not stop trips: the controller logs it once and runs with the defaults (2-minute grace, 300 m minimum, no hold-off).
- The grace period and the minimum distance are Shawn's to set on the [Settings](#settings) screen. Nothing tells the trip controller about a change: it reads both at every trigger, so the stored value is the one in force from the next event on. A grace period that is already running keeps the deadline it was started with.
- If storage fails in the middle of an event, the controller logs the failure, forgets what it held in memory, and reads the state from storage again at once: a service that came up for a trip which could not be stored is stopped, and a trip that is still open in storage carries on. The fix or the event that failed is lost. If the hold-off could not be stored after End, the truck starts a new trip straight away. If storage fails that second time too, nothing more is tried until the next trigger.

---

## GPS recording and distance

**Status:** In progress (phase 1): fixes are recorded during a trip, and the trip's distance and its start and end coordinates are stored when it closes. The start and end addresses are looked up afterwards and stored with the trip (run on an emulator, never on the phone) · **Platforms:** Android · **Last updated:** 2026-10-06

**What it does**
Records the truck's position during a trip and turns it into a distance in km, with start and finish addresses.

**Required behaviour**
- Record a GPS fix every 5 seconds using `FusedLocationProviderClient`, and count distance only after 10 m of real movement. Shawn's brief said "every 5 seconds or 10 m"; the location API cannot express "or" (its interval and distance settings combine as "and"), so the 10 m rule is applied in MilO's own distance calculation.
- Discard points with accuracy worse than 25 m, and impossible jumps (implied speed over 180 km/h).
- Store the raw points so distance can be recalculated later.
- Discard trips under 0.3 km (configurable), for example moving the truck around the yard.
- Reverse-geocode the start and end points to street addresses and store them with the trip. Start and finish locations and mileage are tracked automatically; there is no odometer entry anywhere in the app.

**How it works today**
*Recording.* When the controller tells the trip service that a trip is open, the service starts `LocationRecorder`, which asks the fused location provider for a fix every 5 seconds at high accuracy: no minimum distance, never faster than 5 seconds, no batching, and no cached position (the first fix must be a new one). Each fix arrives in a `LocationCallback` on the main thread and is handed to the controller, which stores it in `raw_points` under the open trip and adds it to the running distance (`TripProgress`). The home screen and the notification show that running distance. The recorder also writes to the event log when fixes are requested and stopped, how long the first fix took and how accurate it was, and when location has been unavailable for 10 seconds and when it comes back. The fixes stop when the trip closes.

*Closing.* When the trip rules end a trip, the controller reads the trip's stored fixes back, cuts off those recorded after the truck was found gone, and works out the distance, the end time and the start and end positions from the rest (`TripClosing`). It stores them on the trip and marks the trip `FINISHED`, or `DISCARDED` if it is under the minimum distance or was a false start. The event log gets the outcome, the distance and how many fixes were stored, used and rejected.

*The calculation,* `DistanceCalculator` in `core/trip/`, is the same for the running distance and for the final one. It takes the fixes in the order recorded and applies four rules to each:
1. **Accuracy.** A fix with no accuracy, or an accuracy radius over 25 m, is not used.
2. **Impossible jumps.** A fix that would need more than 180 km/h to reach from the last good fix is not used. The time between them comes from the phone's elapsed-realtime clock, which never jumps. A long gap (a tunnel) is not a jump, so the distance across it is counted as a straight line. If three rejected fixes in a row agree with each other, they are believed instead and counting restarts from them: this is what stops one bad fix, such as a stale first position, from poisoning the rest of the trip. The leap itself is never counted.
3. **Only real movement counts.** Distance is added only once the truck is at least 10 m from where distance was last counted, and at least twice the two fixes' own stated error: 20 m with 5 m fixes, 40 m with 10 m fixes, 100 m with 25 m fixes. What is added is the straight line from where distance was last counted. On a straight road that loses nothing; through a turn it cuts the corner.
4. **Out and back is not a drive.** If the fix after a counted step is nearer to where counting stood before the step than to where the step went, the step was a bad fix and is taken back. This is what keeps one bad fix 200 m away, while the truck stands at a light or sits parked, from adding 400 m.

Distance between two fixes is the haversine great-circle distance, in pure Kotlin so it runs in unit tests. It differs from Android's own ellipsoid calculation by at most about 0.5 %, far less than GPS error over such short steps.

Every fix is stored in `raw_points` whether or not these rules use it, with both clocks, its accuracy and its reported speed, so a trip can be recalculated later with better rules. The thresholds are named constants gathered in `DistanceLimits`.

*Addresses.* `TripAddresses` in `platform/address/` turns the stored start and end positions of a trip into two addresses.
1. **It only watches.** It reads the trip controller's published state and the stored trips, and writes nothing but the four address columns of a finished trip. Ending a trip never waits for it, and a failure in it leaves the trip as it was.
2. **When it looks.** One pass over the trips that are due is made when a trip stops being in progress, at every process start, and each time the Trips screen comes to the front. The pass at process start is asked for through the trip controller (`TripController.whenCaughtUp`), so it runs after the controller has dealt with what the last process left behind: a trip the restart rules close there was never seen in progress by the lookup, and is looked up by that pass. Passes run one at a time; a request made during a pass leads to one more pass after it. There is no scheduled job (WorkManager is not in the build yet): a trip whose lookup failed waits for the next of those occasions.
3. **Which trips are due** (`isDueForLookup` in `AddressRetry.kt`): a `FINISHED` trip that lacks an address for an end whose position is stored, has not used up its attempts, and whose last attempt is long enough ago. A `DISCARDED` trip is never looked up, and neither is a `DELETED` one. A deleted trip that is restored, or a discarded one that Shawn counts after all, is a finished trip again: it is due like any other, and the Trips screen asks for a pass at once. Trips recorded before addresses existed are due like any other, and are caught up by the first pass.
4. **The lookup itself** is Android's `Geocoder` (`GeocoderAddressLookup`, behind the interface `AddressLookup`): no key, a network connection, up to five candidates asked for, 20 seconds waited at most. On Android 13 and later the answer arrives on a listener; on Android 12 the blocking call runs on a background thread, which is interrupted when the 20 seconds are up (`onBlockingThread`; that form has never been run, the phone is Android 14). This is the one place where a position leaves the phone.
5. **One line per address** (`addressLine` in `AddressLine.kt`), in this order of preference: the street and the town, with the house number in front when the geocoder gives one ("10103 104 Avenue Northwest, Edmonton"); the street alone; the town alone (the district, county or province stands in where the geocoder names no town); and last the geocoder's own line with the country and the postal code taken out. A country or a postal code is never shown. Of several candidates the one that says the most is used, and the geocoder's first when two say as much. A house number does not count as saying more: the geocoder lists its best match first, and a numbered candidate further down can be the building next door or one on another street.
6. **No network is not an attempt.** Before a pass asks anything, `NetworkStatus` asks Android whether the phone has a working internet connection. Without one nothing is asked and nothing is counted. The log gets one line, and another only when what is waiting has changed: a trip that starts and ends without a network gets one while its start is waiting and one, "Waiting: 1 finished trip.", once it has ended.
7. **Giving up.** A lookup that leaves an address missing (the geocoder knows none there, or it failed) is counted on the trip. After the first the trip waits at least 2 minutes, after the second 1 hour, after the third 1 day; after the fourth (`MAX_ADDRESS_ATTEMPTS`) it is not asked about again. An address that was found is kept, so a trip can end up with one of the two. A lookup that fails outright also ends the pass: the trips waiting behind it would fail the same way and each lose an attempt.
8. **A trip in progress.** Its row has no position until it closes, so the lookup reads the position of the trip's first usable fix from the trip controller (`CurrentTrip.startLatitude`), asks for its address once, and keeps the answer in memory for the Trips screen (`TripAddresses.openTripStart`). When the trip closes with that same start position, the remembered address is stored without asking again.
9. **The event log,** category `ADDRESS`: one line per lookup of a trip ("Trip 3: start address found; end address none (the geocoder knows no address there). Failed attempt 1 of 4; the next one is at least 2 min away."), one per lookup for a trip in progress, and one when a pass was put off for lack of a network. The lines name no address and no position.

**Where the code lives**
- `platform/address/`: `TripAddresses.kt` (the passes), `AddressRetry.kt` (what is due, when to give up, what a row says about each end), `AddressLine.kt` (the one line), `AddressLookup.kt` and `GeocoderAddressLookup.kt` (the geocoder), `NetworkStatus.kt`, `AddressLogText.kt` (the log lines). `app/MiloApplication.kt` asks for the pass at process start, through `TripController.whenCaughtUp`.
- `data/trip/`: the four address columns on `Trip`, `findTripsLackingAddress` and `recordAddressLookup` on `TripRepository`. The migration that added the columns is `data/MiloMigrations.kt`.
- `platform/trip/LocationRecorder.kt` (the request and the callback); `platform/trip/TripLedger.kt` (stores each fix, closes the trip).
- `core/trip/`: `DistanceCalculator.kt` (the four rules and the thresholds), `Haversine.kt`, `TrackPoint.kt`, `TripProgress.kt` (the running distance and the time of the last movement), `TripClosing.kt` (minimum distance, end time and positions).
- `data/point/`: `RawPoint.kt` (the table), `RawPointDao.kt`, `RawPointRepository.kt`; the database is `data/PointsDatabase.kt`.
- Tests: `app/src/test/.../core/trip/DistanceCalculatorTest.kt`, `DistanceCalculatorParkedTest.kt`, `DistanceCalculatorOutlierTest.kt`, `DistanceCalculatorTurnsTest.kt`, `HaversineTest.kt`, `TripProgressTest.kt`, `TripClosingTest.kt`; for the addresses `app/src/test/.../platform/address/AddressLineTest.kt`, `AddressRetryTest.kt`, `TripAddressesTest.kt`, `TripAddressesWithControllerTest.kt` (the lookup beside a real trip controller), `GeocoderAddressLookupTest.kt` (the time limit around Android 12's blocking call) and `app/src/test/.../data/MiloMigrationsTest.kt`.

**Edge cases & gotchas**
- The distance shown during a trip can be a little higher than the stored one: what is recorded during the grace period counts while the trip is open, and is cut off when it closes.
- A fix's time of day is worked out from the phone's clock and the fix's age, not taken from the fix. The trip rules compare fix times with times read from the phone's clock, and the satellite time inside a fix can differ from it.
- A fix that arrives after the trip has closed is not stored.
- If the location permission is taken away during a trip, Android ends the app; the trip is picked up or closed at the next start.
- A stationary truck with Bluetooth connected still produces GPS jitter, which must not add phantom km.
- The 10 m rule alone does not achieve that. In a simulated hour of parked fixes scattered as a stated accuracy of 5 m implies, 10 m alone added about 360 m, enough to be kept as a trip; with the stated-error rule the same hour adds nothing. So the brief's "10 m" is a floor, not the whole rule. The thresholds are to be tuned from the raw points of the first test drives.
- **The stated-error rule costs distance on turns.** All numbers are simulations, not this phone. On a town route with a right-angle turn every 300 m at about 25 km/h, a fix every 5 seconds alone misses about 2 %. The calculation is short by the same 2 % with 5 m fixes, about 4 % with 10 m fixes and about 11 % with 25 m fixes. A truck circling inside a space smaller than the threshold (a yard, a small roundabout) with fixes of 8 m or worse measures nothing.
- **The end of a trip is short by up to the threshold:** up to 20 m with 5 m fixes, 40 m with 10 m, 100 m with 25 m, because the last stretch never crosses it. A real 350 m trip recorded with poor fixes can therefore come out under the 300 m minimum and be discarded (it is kept as `DISCARDED`, see below).
- **Bad fixes that are still believed** (simulated in review, not seen on this phone). A wrong first fix less than about 700 m from the truth stays as the trip's start position and adds up to its own distance from the truth. One wrong fix straight after a GPS gap is believed if the truck could have got there in the time of the gap, and the way back to the road is counted as well: after a 60-second tunnel, a fix 600 m to the side added 500 m to a 5.3 km drive, and one 2 km to the side added about 1 km. Two bad fixes in a row that agree with each other look like a real there-and-back. Not fixed, because the right cure depends on what this phone's bad fixes look like; the likely one is to hold a fix that follows a gap until the next fix agrees with it.
- Rule 4 has a price: pulling forward and reversing within one fix (shunting in the yard) is not counted.
- A trip under the minimum distance is marked `DISCARDED` and kept with its points, not deleted. If the distance was wrong, the trip is recovered on the Trips screen with "Count this trip" (see [Trip log](#trip-log-home-day-and-month-views)). It is then counted with the distance that was measured; nothing recalculates it.
- After a reboot in the middle of a trip the elapsed-realtime clock restarts, so the distance covered across the reboot cannot be checked and is not counted.
- The phone's own speed reading is stored but not used yet. It is the likely next refinement: for the parked-truck rule in place of stated accuracy (which would end the corner cutting), and to reject a leap from a fix that says it is standing still.
- If the phone's clock was wrong at the start of a trip and corrected during it, the distance is still right (points are cut by stored order, not by time), but the trip's stored start time is the wrong one and can even be later than its end.
- **An address can arrive late.** A trip that ends with no signal, or whose lookup is cut short because the phone puts MilO to sleep once the trip service has stopped, gets its address at the next occasion: the next trip ending, MilO being started, or the Trips screen being opened. Until then the Trips screen says it is still being looked up. The same goes for a trip the restart rules close later than at process start (after the trip service could not be started, or together with the start of a new trip): the lookup never saw it in progress, so it waits for the next occasion, which for a new trip is its first GPS fix.
- **A trip MilO has given up on stays without that address.** Nothing asks again, and there is no way yet to enter one by hand (`[DEBT]` in FINDINGS_LOG).
- **An address is what the geocoder says, not a measurement.** It names the nearest address it knows, which at a large yard or a rural site can be a neighbour's, an "Unnamed Road", or nothing. The stored coordinates remain the record. An address once stored is never replaced by a later lookup.
- The house number is written before the street, as in Canada, whatever the phone's language.
- The same place can be worded differently on two trips if the first fixes fell on different sides of a lot.
- The start address shown for a trip in progress is held in memory. If the process is restarted in mid-trip it is looked up again.
- A trip discarded as a false start may still have had its start looked up in its first seconds, for the screen. Nothing is stored for it.

---

## Trip-start sound

**Status:** In progress (phase 1): the sound plays at every trip start, and the Settings screen switches it on and off, plays it, and lets Shawn choose an audio file of his own. Choosing a file ran on an emulator, never on the phone · **Platforms:** Android · **Last updated:** 2026-10-06

**What it does**
Plays a short R2-D2 style droid chirp once, when the app has connected to the truck and is ready to track the trip. It is Shawn's audible confirmation that detection worked.

**Required behaviour**
- Plays once per trip start, at the moment recording has really begun. No sound on trip end.
- Comes from the phone speaker and behaves like a notification sound: silent when the phone is on vibrate or Do Not Disturb.
- Played by the trip service itself, not attached to a notification channel, because MIUI is reported to switch channel sounds off by default.
- The sound committed to the repository is an original synthesized droid-style chirp. The film recording is copyrighted, so it is never committed.
- **Shawn's own clip replaces it in builds made on his Mac.** A file placed at `app/src/debug/res/raw/trip_start_chirp.<ext>` overrides the bundled chirp in debug builds, which are the builds installed on the phone. That folder is git-ignored, so the clip stays on his Mac and his phone and never reaches GitHub. Since 2026-10-05 it holds his R2-D2 clip (4.5 seconds). CI and any other machine build with the synthesized chirp. The event log says "playing the bundled chirp" either way, because it is the sound built into that build.
- A setting to choose any audio file on the phone, without rebuilding. Built on 2026-10-06: see "Choosing a sound" below.

**How it works today**
1. When the controller has opened a trip row, with the service already in the foreground, it tells the service that a trip has just started. That happens once per trip: not when a trip is picked up after a restart, and not when the grace period is cancelled. One exception: a trip opened by the companion "appeared" callback alone is not yet known to be a trip. Its sound plays when the truck's connection is confirmed, and a false start makes no sound at all (`TripTransition.tripReallyBegan`).
2. The service takes down any "could not start this trip" notification, reads the settings, and if the sound is switched on calls `TripStartSound.play`.
3. The sound is played with `MediaPlayer` and notification-type audio attributes, so the phone plays it at the notification volume and mutes it on silent, on vibrate and in Do Not Disturb. The player asks for the phone's built-in speaker as its preferred output.
4. If the settings hold a custom sound, that is played. It is always MilO's own copy of the file Shawn chose, never the file he picked. If it cannot be opened, or fails while preparing or playing, the bundled chirp is played instead and the event log says why.
5. The bundled chirp is `res/raw/trip_start_chirp.wav`: 1.4 seconds of sine sweeps, warbles and short beeps, generated by `tools/make_trip_start_chirp.py`. The script is committed and produces the same file every time; the build does not run it.
6. One sound at a time: a sound that is still playing is cut off when the next one starts. A trip start never meets that; it is there for the Play button below.

*Choosing a sound* (the Settings screen, card "Trip-start sound"; see [Settings](#settings))
1. **A switch,** "Play a sound when a trip starts". Off means no sound at any trip start. It does not change which sound is chosen.
2. **"Sound in use"** says "the built-in sound", or "your own sound" with the name of the file that was picked.
3. **Play** plays the sound a trip start would play now, through the same player, whether or not the switch is on. Pressing it again starts the sound again from the beginning.
4. **"Use my own sound"** opens Android's own file picker, set to audio files. The file Shawn picks is then, in this order:
   - copied into MilO's private storage (`no_backup/trip_sound/`), under a new name, beside the copy in use if there is one. A file over 10 MB, an empty file and a file that cannot be read are refused here. "Cannot be read" includes whatever the app that holds the file answers with: the file is opened by that app's code, and an exception it throws (it no longer knows the file, MilO may no longer read it) is a refusal like any other, never a crash;
   - handed to Android's player to prepare. A file the player cannot prepare (not audio, or damaged) is refused, and its copy is thrown away;
   - written into the settings, as the place of the copy and the name of the picked file;
   - and only then is the copy that was in use before removed. There is one copy at most, however often a sound is chosen.
5. **A refused file changes nothing.** The settings and the copy in use are exactly as before, so the sound that was playing until now still plays. The card says why in one sentence ("MilO could not copy that file", "That file is too large for a trip-start sound (more than 10 MB)", "That file cannot be played as a sound", each ending "The sound was not changed."), and the event log gets an `ERROR` line with what Android or the file system said.
6. **"Use the built-in sound"** is offered while a sound of Shawn's own is in use. It clears the two settings and removes the copy.
7. Closing the file picker without choosing changes nothing and says nothing.
8. A change that has begun is finished even if Shawn leaves the Settings screen in the middle of it.

**Where the code lives**
- `platform/trip/TripStartSound.kt` (the player, and `playbackProblem`, the check that a file can be played); the call at a trip start is in `platform/trip/TripService.kt`.
- `platform/trip/OwnTripSound.kt` (choosing a file and going back, step by step) and `PickedAudio.kt` (reading the picked file through Android's content resolver).
- `data/sound/OwnSoundStore.kt` (MilO's copy: where it is kept, the size limit, one copy at most).
- `feature/settings/` (the card, the file picker, the words for a refusal). `app/AppContainer.kt` builds `ownTripSound` and `soundPreview`, the player behind the Play button.
- The script is `tools/make_trip_start_chirp.py`. The three settings (`sound_enabled`, `custom_sound_uri`, `custom_sound_name`) are in `data/settings/SettingsStore.kt`.
- Tests: `app/src/test/.../platform/trip/OwnTripSoundTest.kt` (a file that cannot be read, cannot be played or is too large changes nothing, and neither does one whose app answers with an exception of its own; a new choice replaces the copy before it; going back) and `app/src/test/.../data/sound/OwnSoundStoreTest.kt` (the copies, against a real folder). The player itself and the file picker have no unit tests: they need Android.

**Edge cases & gotchas**
- **Choosing a sound has run on an emulator only** (FINDINGS_LOG, 2026-10-06): a WAV file was picked, copied, played by the Play button and at the next trip start, and replaced by a second pick; a text file with an audio name was refused. On the phone it is device checks 117 to 123. What HyperOS's own file picker looks like, and whether it offers audio files from the places Shawn keeps them, is not known.
- **The copy is not in any backup,** on purpose: it can be megabytes, and Android's backup takes nothing at all from an app that is over its 25 MB limit. When phase 4 switches backup on and the settings are restored on another phone, the settings will name a copy that is not there. A trip start then plays the bundled chirp and the event log says why, but the Settings screen still says "your own sound" until the sound is chosen again or "Use the built-in sound" is pressed.
- "Can be played" means that Android's player can prepare the file. A file with a video track and no sound in it would pass and play nothing.
- A long file is played to its end at every trip start. Nothing limits the length, only the size (10 MB).
- **Play is silent when a trip start would be:** on silent, on vibrate, in Do Not Disturb and in Bedtime mode. The card says so. The event log still gets its line ("Settings screen, Play pressed. Trip-start sound: playing …").
- Play uses a player of its own, not the trip service's. If a trip starts while a sound from Play is still playing, the two overlap.
- In a build made on Shawn's Mac "the built-in sound" is his R2-D2 clip, because that build has it in place of the chirp (above).
- The phone's speaker is a preference, not a guarantee: Android may route the sound elsewhere. Where it comes out with the truck connected is a check on the phone.
- The sound does not lower music that is playing and does not check for a phone call. The research suggests both; neither was asked for.
- **Heard on the POCO X5 once, at a trip started with the button** (2026-10-05, FINDINGS_LOG, "First results from the phone"): Shawn's R2-D2 clip played. Before that he heard nothing, although the log showed the sound being played without error: the phone was in Bedtime mode. Whether HyperOS mutes notification-type audio on vibrate the way stock Android does is unconfirmed (device check 16). A sound chosen on the Settings screen has never been heard on the phone.
- **On Android 17 the chirp of an automatic start would be silent.** MilO targets API 37, and Android 17 lets such an app play audio in the background only from a foreground service that was started while the app was visible or by the user. A service started by a Bluetooth broadcast, the companion callback or boot is not one, and playback then fails without an error: the log would still say "playing the bundled chirp". The POCO X5 runs Android 14 and is not affected. If MilO ever runs on Android 17, the chirp has to become the sound of a notification, which the system plays (FINDINGS_LOG, 2026-10-05).
- If the process dies in the 15 seconds a companion start waits to be confirmed, and the truck is confirmed after the restart, that trip makes no sound.

---

## Safety net: manual button and driving alert

**Status:** In progress: the button works; the alert is phase 2 · **Platforms:** Android · **Last updated:** 2026-10-05

**What it does**
Catches the case where Bluetooth never connects at all.

**Required behaviour**
- A Start/Stop button on the home screen starts or ends a trip by hand.
- During scheduled hours, if the phone's driving detection says Shawn is in a moving vehicle and the truck is not connected, notify him with a tap-to-start action. Needs the Physical activity permission.
- Driving detection never starts a trip by itself, because it would also log rides in other vehicles.
- A manually started trip still respects the schedule: it is classified Business or Personal by its start time, the same as an automatic one. (Built on 2026-10-06: the rule that sorts a trip is not told what started it. See [Schedule and Business/Personal](#schedule-and-businesspersonal).)

**How it works today**
The home screen has one button. With no trip open it says "Start trip" and sends the `MANUAL_START` trigger; with a trip open it says "End trip" and sends `MANUAL_END`. Both go through `HomeViewModel` to `TripController.onTrigger`, the same function every other trigger calls, so a manual trip is recorded, closed and logged exactly like an automatic one. Each press reads the truck's connection first; if it cannot be read, the press goes by what was last believed. A manual trip the truck never joins ends on End trip, or after 30 minutes without movement.

The [Android Auto screen](#android-auto-screen) has the same button, under the same words, and sends the same two triggers. A trip started there is the same kind of manual trip. In the event log the two are told apart by the source: "Start button" and "End button" on the phone, "Android Auto Start button" and "Android Auto End button" on the car.

**Where the code lives**
`feature/home/HomeScreen.kt` and `HomeViewModel.kt`; on the car, `platform/car/TripStatusScreen.kt` and `CarAction` in `platform/car/CarScreenContent.kt`. The rules for a manual trip are in `core/trip/` (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection)).

---

## Android Auto screen

**Status:** In progress (phase 1): built, and **never run anywhere**. Not on the truck, not in Google's desktop emulator of a car display (the Desktop Head Unit, which is not installed on the Mac), not on an emulator. It is proven by the build and by unit tests of everything it decides · **Platforms:** Android Auto (projected from the phone) · **Last updated:** 2026-10-06

**What it does**
Shows MilO on the truck's Android Auto display, built with the Car App Library (`androidx.car.app`). Shawn asked for it so he can see more easily that the app is running, and override it without touching the phone. It is in phase 1 because that visibility matters most during the first test drives.

**Required behaviour**
- Shows tracking status, the current trip's km and duration, and today's session count and total km.
- Start Trip and End Trip buttons act as a manual override. They drive the same trip logic as the phone's Start/Stop button, and a trip started here still respects the schedule (it is sorted into Business or Personal when it ends, like any other; see [Schedule and Business/Personal](#schedule-and-businesspersonal)).

**The known risk, before anything else**
Google's documentation says two things. Android Auto's "Unknown sources" developer setting covers media apps, messaging notifications and parked apps, and "doesn't apply to apps built using the Android for Cars App Library". And to test an app in a real vehicle, "you must install it from a trusted source such as Google Play". MilO is installed from Android Studio. So by Google's own words this screen is expected to show in the desktop emulator of a car display and **not on the truck**. Reports from other people conflict, and one unanswered report says sideloaded apps do not show on a POCO X5 Pro. Shawn tests it on the truck (device checks 88 to 100). If MilO is not listed there, there are three choices:
1. **A private Google Play install.** Play's "internal app sharing" or an internal test track delivers MilO to the phone with no review and no public listing, and Android Auto then treats it as trusted. It needs a Play Console account (25 US dollars once, with identity verification). Play signs its copy with its own key, so that copy and a build from Android Studio cannot update each other: changing over means uninstalling, which deletes the trips unless they are exported first. It also ends "never on the Play Store".
2. **A media-app style entry.** "Unknown sources" does allow a sideloaded media app on a real car. MilO would appear as a media app, with the status as its "now playing" text and Start and End as its buttons. A workaround, and not the screen that was asked for.
3. **Dropping the screen.** Everything else works without it: the rule "do not end the trip while Android Auto is connected" does not depend on it, and the phone's notification still shows the trip.
Unofficial tools that pretend an app came from Play exist. They are untrusted software from strangers and the reports on them are mixed, so they are not one of the choices. Source for all of this: `docs/research/2026-10-03-android-auto-screen.md`, findings 8 to 12.

**How it works today**
1. **One screen, never another.** A car allows an app five steps and then closes it. A redraw is a harmless "refresh", and not a step, only while the header's title, the number of rows and every row's title stay exactly the same. So the screen is one pane with the header "MilO" and always three rows, titled **Status**, **This trip** and **Today**. Whatever changes is written under a title, never into it, and nothing is ever opened on top of this screen.
2. **Status** is one line. The first of these that applies is shown:

   | The line | When |
   |---|---|
   | Truck disconnected. Waiting for it to reconnect | A trip is open and in its grace period |
   | Recording. Truck not connected | A trip is open and the trip rules believe the truck's Bluetooth is not connected: a trip started by hand that the truck has not joined, or one that Android Auto alone is holding open |
   | Recording | A trip is open |
   | Could not start: (why) | No trip, and the last attempt to start one was refused. The reason is the first thing the preflight found (location permission, "Allow all the time", location switched off, battery set to Restricted, Nearby devices), or "Android did not allow it" |
   | Not recording. MilO's setup is incomplete | No trip, and a required row of the setup checklist is not in order (the home screen's rule, `needsAttention`) |
   | Not recording. Truck not connected | No trip, and the trip rules believe the truck is not connected |
   | Not recording | No trip, and nothing above applies: ended by hand with the truck still connected, or MilO has only just started |

3. **This trip** shows the kilometres to one decimal and the time since the trip started in whole minutes ("12.4 km · 23 min", or "12.4 km · 1 h 5 min"), and "No trip in progress" when there is none.
4. **Today** shows the finished trips that started today and their total ("3 trips · 41.2 km"), by the one rule the Trips screen and the phone's home screen count by (`todayTrips` and `isCounted` in `data/trip/TripTotals.kt`): a discarded or a deleted trip is not counted, and the trip in progress is not counted until it ends. A trip deleted on the phone leaves the car's total at once, and a restored one comes back. **The row counts every finished trip, Business and Personal together.** The phone's Today card has shown the two apart since 2026-10-06; the car's row was left as it was on purpose, so its figures are the phone's Business and Personal added up. "No trips yet" before the first; "Not available" until storage has answered, or if it could not be read.
5. **One button.** "Start trip" while no trip is open, "End trip" while one is, the grace period included. It sends `MANUAL_START` or `MANUAL_END` to `TripController.onTrigger`, exactly as the phone's button does. It works while the truck is moving: it is an ordinary button, not one of the library's parked-only ones. A press does what the button said when it was drawn.
6. **Where it reads from.** The screen has no ViewModel and decides nothing. It follows four things: `TripController.activity` (the trip, why the last start failed, and what the trip rules believe about the truck), the trips that started today (`TripRepository`), the shared setup checklist, and the clock. One pure function, `carScreenContent`, turns them into what is shown.
7. **When it redraws.** Only while the car is showing the screen, and only when what is printed has changed: kilometres are compared after rounding to one decimal and the time after rounding down to whole minutes. The running figures of a trip are redrawn at most every 15 seconds. Everything else (the status, the button, a trip starting or ending, today's totals) is drawn at once.
8. **A start that is refused** changes the Status line to "Could not start: (why)", which stays until a trip does start. If it happens while the screen is showing, the car also shows a short message, "MilO could not start this trip". The reason is in the event log twice: the trip controller's own `SERVICE` line ("Could not start recording for Android Auto Start button: MANUAL_START: BACKGROUND_LOCATION_MISSING"), and an `ANDROID_AUTO` line saying that the car screen said so. The phone gets its "could not start this trip" notification as for any other start.
9. **Who may connect.** Android Auto reaches the screen through a service that has to be open to other apps. What it lets in is checked by the fingerprint of the app's signing certificate against the list the Car App Library ships: Android Auto itself, and Google's host for cars that run Android themselves. Any other app is turned away. The same list is used in every build.
10. **What it writes to the event log,** all as `ANDROID_AUTO` lines: "car app service created, a host is connecting"; "session created by (the host's package name), Car API level (the level agreed)"; "shown on the car's display"; "no longer shown"; "session destroyed"; "car app service destroyed"; and "said that the trip could not start". Each press of the button is the trip controller's own `TRIGGER` line, with "Android Auto Start button" or "Android Auto End button" as its source and the state before and after in its detail.
11. **If the screen's own code fails,** the failure is written to the log as an `ERROR` line and MilO closes itself on the car's display. An exception left alone would end the whole process, and that is the process recording the trip.

**Where the code lives**
- `platform/car/MiloCarAppService.kt`: the service Android Auto binds, and the check of who may connect.
- `platform/car/MiloCarSession.kt`: one visit of Android Auto. Builds the screen and writes the session's lines to the log.
- `platform/car/TripStatusScreen.kt`: the screen. Follows the four sources, redraws, builds the pane.
- `platform/car/CarScreenContent.kt`: `carScreenContent` (what is shown, from the controller's state and today's trips), `CarStatus`, `CarAction`, and `differsOnlyInTripFigures` (which changes wait for the 15 seconds). Pure functions.
- `data/trip/TripTotals.kt`: `todayTrips` (what counts as today), shared with the phone's home screen since 2026-10-06.
- `platform/trip/TripActivity.kt`: `truckConnected`, added for this screen.
- `core/util/TimeSpan.kt`: `daySpan`, today as a span of stored time. `core/util/DurationFormat.kt`: `wholeHoursAndMinutes`, the time since a trip started.
- The manifest declares the service (category `androidx.car.app.category.IOT`), the car app descriptor `res/xml/automotive_app_desc.xml` (a "template" app) and the lowest Car API level the screen works with, 7. The words are in `strings.xml` under "The Android Auto screen".
- Tests: `app/src/test/.../platform/car/CarScreenContentTest.kt` (every state: no trip, recording, the grace period, truck not connected, setup incomplete, each kind of refused start; the figures; the button), `app/src/test/.../data/trip/TripTotalsTest.kt` (what counts as today), `CarScreenRefreshTest.kt` (what is redrawn at once and what waits), `MiloCarSessionTest.kt` (the session's log line), `app/src/test/.../platform/trip/TripActivityTest.kt` (what is reported about the truck) and `app/src/test/.../core/util/TimeSpanTest.kt` (a day). The service, the session and the screen themselves have no tests: they need a car, and there is no Robolectric (STANDARDS §11).

**Depends on**
`androidx.car.app:app` and `app-projected` 1.7.0, and `androidx.lifecycle:lifecycle-runtime`. The Android Auto app on the phone, which is what draws the screen and decides the Car API level. The trip controller, the trip repository and the setup checklist.

**Edge cases & gotchas**
- **Nothing here has been run.** Whether the pane looks right, whether the car accepts it, whether the button reacts, whether the lines appear in the log: all unknown until device checks 88 to 100.
- **"Truck not connected" is what the trip rules believe, not a fresh look at the truck.** While no trip is open nothing reads the truck's connection unless a Bluetooth event arrives or MilO is opened on the phone. If MilO missed the truck connecting, the car says "Not recording. Truck not connected" while the truck is connected. That is the moment the button is for: a press of Start trip reads the truck first. Opening MilO on the car's display does not make the trip controller read the truck, the way opening it on the phone does; whether it should is Shawn's to decide (FINDINGS_LOG, 2026-10-05).
- For up to 15 seconds after the companion service alone has started a trip, the line is "Recording. Truck not connected": the truck is not confirmed yet.
- **The lowest Car API level is 7.** The header is built with the library's current `Header` class, which needs level 7. The level belongs to the Android Auto app on the phone, not to the truck, and Google does not publish which level which version speaks. An Android Auto too old for level 7 would not open MilO at all; the log would then show "car app service created" with no "session created" after it. A host that failed the certificate check leaves the same trace; the library's own Logcat lines (their tags begin with `CarApp`) tell the two apart.
- If Google changes Android Auto's signing certificate, the list in a pinned library goes out of date and MilO is turned away until the library is updated.
- **The library is one release behind a security fix.** The release notes of 1.8.0-rc01 say it includes one, without naming it, and tell every lower version to update. It is a release candidate, so 1.7.0 stays until 1.8.0 is stable, and 1.8.0 is then to be taken at once. It matters here because the check of who may connect (9 above) is the library's code, and the service behind it is open to every app on the phone (FINDINGS_LOG, 2026-10-05, `[DEBT]`).
- **A refused start that repeats exactly** (the same reason again) shows no second message: the Status line already says it and does not change.
- A Status line that says "Could not start" stays after the cause was put right, until the next trip starts. The phone's home screen does the same.
- **Android Auto decides how long the screen lives.** Google's own reference host drops an app about three minutes after the driver leaves it; what Android Auto does on this phone is not documented, and the log lines are how it will be learned. Nothing depends on the session: the trip is recorded by the trip service whether or not the screen exists.
- **Being shown on the car does not count as being in front for location.** A trip started from the car's button is started like one started by the truck: it needs "Allow all the time", and battery use unrestricted or the companion association. Without them the start is refused, and the screen says so.
- Opening MilO on the car when its process is dead starts the process, which reads the truck as every process start does. Whether HyperOS lets Android Auto start a dead MilO with Autostart off is unknown (device check 99).
- The setup checklist is read each time the car shows the screen. A setting changed while the screen stays up shows only after one of MilO's phone screens has read it again.
- The button has no icon and there is one button, not two. Google's design guide prefers an icon on every button and frowns on a button that changes its label; one button was the brief.
- The category `IOT` is the least wrong of the library's categories (none is for logging trips). It decides which rules Google would review the app against, and nothing is reviewed for an app that is not on Play.
- `todayTrips` was in `platform/car/` while the car screen was its only user. Since the home screen's Today card exists it is in `data/trip/TripTotals.kt`, and both surfaces call that one function.
- The rule "do not end the trip while Android Auto is connected" does not depend on this screen and works either way: `platform/car/AndroidAutoWatcher.kt` watches `CarConnection` during a trip (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection), rule 13).

---

## Permission checklist

**Status:** In progress (phase 1): built as the Setup screen. Shawn went through it on the phone on 2026-10-05, everything except pairing the truck; the device checks for it have not been run as written · **Platforms:** Android · **Last updated:** 2026-10-06

**What it does**
One screen, Setup, showing every requirement with its state, each with a button that takes Shawn to the place to fix it. The home screen warns while a required one is not in order.

**Required behaviour**
- Items: precise location, background location ("Allow all the time"), notifications, Bluetooth connect, companion background permissions, battery optimisation exemption, Physical activity (for the driving alert).
- Xiaomi items for this phone: Background autostart, Battery saver "No restrictions", "Pause app activity if unused" off, the "Other permissions" switches (Show on Lock screen, Start in background, Permanent notification), and MilO locked in recents. Each has a button that opens the right HyperOS screen.
- The screen is honest about what it can verify. Autostart can only be read through an unofficial check, so it shows as "looks on", "looks off" or "unknown". The Battery saver profile and the recents lock cannot be read at all, so they are "confirm you set this" steps.

**How it works today**
1. **The rows.** `setupRows` in `platform/system/SetupRules.kt` is a pure function: given what the phone reports (`SetupFacts`), the state of the truck's pairing and Shawn's confirmations, it returns every row with its state (OK, problem, unknown, needs confirmation), which sentence to show and what its button does. `SetupReader` reads the facts from Android. The five facts that stop a trip from being recorded are read by `TripPreflight.facts()`, the same code that checks them before the trip service is started, so the checklist and the preflight cannot disagree.
2. **The nine Android rows,** on every phone:

   | Row | In order when | Its button |
   |---|---|---|
   | Precise location | The precise location permission is granted | Android's permission dialog |
   | Location: Allow all the time | The background location permission is granted | Asks for it, which makes Android open its own location page. No button until precise location is granted: Android ignores the request before that, and the row says so |
   | Notifications | MilO's notifications are shown | The permission dialog (Android 13 and later), or the notification settings |
   | Nearby devices (Bluetooth) | The Bluetooth permission is granted | The permission dialog |
   | Location switched on | Location is on for the whole phone | The phone's Location settings |
   | Truck paired and watched | The pairing check says "armed" | MilO's pairing screen. The button is always there: "Pair truck" while nothing is paired or Android has dropped the association (the sentence then says "pair it again"), "Change truck" otherwise |
   | Battery use: unrestricted | MilO is exempt from battery optimisation, and not "Restricted" | Android's dialog that asks for the exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared for it); for "Restricted", MilO's page in the settings. On a Xiaomi phone that page is HyperOS's "App info", which has no "Unrestricted", so the sentence names its Battery saver choice "No restrictions" there |
   | Not paused when unused | "Pause app activity if unused" is off for MilO | The page with that switch |
   | Battery Saver off | The phone-wide Battery Saver is off | The Battery Saver settings |

3. **The four HyperOS rows,** only on a Xiaomi, Redmi or POCO phone (`isXiaomiFamily`, from the manufacturer and the brand the phone reports):
   - **Background autostart.** Read through the unofficial check the research describes: `AppOpsManager.checkOpNoThrow` with MIUI's app-op 10008, reached by reflection. Mode 0 is shown as "Looks on", any other mode as "Looks off", and a call that fails as "MilO could not read this setting". Each sentence says that it is not a certainty. Only when there is no reading does the row offer "I have set this".
   - **Battery saver: No restrictions,** **Other permissions** (Show on Lock screen, starting or opening windows in the background, Permanent notification, as one row) and **Locked in recent apps.** MilO cannot read any of them. Each shows an empty ring and the instruction until Shawn presses "I have set this"; then it is green and says "You confirmed this on (date). MilO cannot check it." "Not set any more" takes the confirmation back. The dates are in the settings store.
   - Their buttons open the HyperOS screens the research found: the Autostart list (`com.miui.securitycenter`'s `AutoStartManagementActivity`, then the action `miui.intent.action.OP_AUTO_START`), MilO's Battery saver choices (`com.miui.powerkeeper`'s `HiddenAppsConfigActivity`), and MilO's permission editor (`miui.intent.action.APP_PERM_EDITOR`). The recents lock has no button: it is a gesture on MilO's card.
4. **Required and recommended.** A row is required if an automatic trip can fail to start, or be cut short, without it. Two rows are only recommended: "Not paused when unused" and "Other permissions". Their sentences begin with "Recommended".
5. **Opening a settings screen** (`platform/system/SystemScreens.kt`). Each screen has a list of ways to open it, best first, and the last is always Android's own page for MilO. They are tried in order inside a try/catch, so a screen this build of HyperOS does not have is never a crash. When a fallback was used, or nothing opened, an `ERROR` line in the event log says which screen and why.
6. **Asking for a permission.** The button shows Android's dialog. After two refusals Android stops showing it and answers "refused" at once; MilO then opens the settings page that holds the permission, so the button always leads somewhere (`androidDidNotAsk`).
7. **Re-reading.** Android sends no event when a permission or a setting changes. The Setup and Home screens ask the shared `SetupChecklist` to read the phone again every time they come to the front: coming back from a settings screen, one of Android's dialogs closing, and the quick settings panel closing over MilO. The first two resume the screen; the panel does not, so MilO's window getting the focus back counts as well (`CameToFrontEffect`, see [Design system](#design-system)). The truck row and the confirmations update by themselves.
8. **The home screen's warning.** While any required row is not OK, Home shows a card "Setup needs attention" with a button to Setup. The rule is `needsAttention`, and the Setup screen's count at the top uses the same one. No truck paired is one of the required rows.

**Where the code lives**
- `feature/setup/`: `SetupScreen.kt`, `SetupViewModel.kt`, `SetupTexts.kt` (which words each row shows).
- `platform/system/`: `SetupRow.kt` (the rows, their states and what a button can do), `SetupRules.kt` (`setupRows`, `needsAttention`), `SetupFacts.kt` (what is read from the phone, `SetupReader`, the Autostart reading, `isXiaomiFamily`), `SetupChecklist.kt` (the rows as a flow, shared by Setup and Home), `SystemScreen.kt` (the ways of opening each settings screen, as plain values), `SystemScreens.kt` (opens them), `PermissionAsk.kt` (`androidDidNotAsk`), `TripPreflight.kt` (the five facts shared with the trip service).
- `data/settings/`: the confirmation dates (`ConfirmedStep`, `SettingsStore.setConfirmedAtMs`).
- The warning card is in `feature/home/HomeScreen.kt`.
- Tests: `app/src/test/.../platform/system/` (`SetupRulesTest.kt`, `SetupAttentionTest.kt`, `SetupFactsTest.kt`, `SetupChecklistTest.kt`, `SystemScreenTest.kt`, `PermissionAskTest.kt`, `TripPreflightTest.kt`) and `app/src/test/.../feature/setup/SetupTextsTest.kt` (the truck button's words, and the battery sentence for each kind of phone).

**Depends on**
`TruckPairing.status` (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection)), the settings store, and the HyperOS findings in `docs/research/2026-10-03-miui-background-limits.md` (4, 16 to 20 and 27 to 33).

**Edge cases & gotchas**
- **Used on the phone once, and not yet checked row by row.** On 2026-10-05 Shawn went through Setup on the POCO, everything except pairing the truck. Read back over adb afterwards (FINDINGS_LOG, "First results from the phone"): all four runtime permissions granted, "Allow all the time" among them; MilO on Android's battery-optimisation exemption list; and the Autostart reading answering "allow", where the same record showed it rejecting nine minutes earlier, so on HyperOS 2.0 the reading works and follows the switch. Still unknown: whether each HyperOS button opened the right screen (he reported no problem), and whether `isPowerSaveMode` and the "pause if unused" reading follow Xiaomi's own switches. Device checks 54 to 66 go through it row by row and have not been run.
- Whether HyperOS's quick settings panel takes the focus from MilO's window, as stock Android's does, is untested. If it does not, a setting changed there shows only after MilO is left and opened again (device check 59).
- Whether Android's "Restricted" can be reached on HyperOS at all, and whether Battery saver "No restrictions" is what lifts it, is unknown (device checks 22 and 61). The sentence names the one battery setting the HyperOS app page has.
- **Two rows of the brief are not there.** Physical activity belongs to the driving alert (phase 2), and nothing uses it yet. The companion background permissions are granted at install and cannot be taken away, so they have no row; what can go wrong with the companion association is the truck row.
- The Autostart reading is known to say "on" for a switch that is off on some phones, and always says "on" while MIUI optimisation is disabled. On this phone it has been seen to follow the switch once (above). "Looks on" still proves nothing about whether HyperOS lets MilO start; the with and without Autostart drive (device checks 36 to 43) does.
- A confirmation by hand never overrides an Autostart that reads as off. If the reading were wrong in that direction on this phone, the warning could not be cleared; that would be a finding to act on.
- The confirmations are Shawn's word, and HyperOS is reported to reset these settings after updates and reboots. A confirmation does not expire. The date is shown so that an old one can be doubted.
- On the POCO the home screen's warning stays until the Battery saver row and the recents lock are confirmed, even with every permission granted.
- Android's battery exemption and HyperOS's "No restrictions" are treated as two settings. The research could not settle whether either sets the other.
- If the settings file cannot be read, the rows are still shown, without the confirmations, and an `ERROR` line is logged.
- If Android refuses one of the readings, an `ERROR` line with the exception is logged and the rows stay as they were. Should that happen at the very first reading, Setup stays on "Reading the phone's settings…"; the Log says why.
- The settings screens open as a task of their own. Back returns to MilO.

---

## Schedule and Business/Personal

**Status:** In progress (phase 2): built on 2026-10-06. Ran on an emulator, never on the phone. Device checks 128 to 152 · **Platforms:** Android (phone) · **Last updated:** 2026-10-06

**What it does**
Decides whether a trip is Business or Personal from when it started, and lets Shawn overrule it.

**Required behaviour**
- Settings: choose which days to track and a start and end time for each day. Default Monday to Friday, 08:00 to 16:30. An option applies the same hours to all days.
- A trip that starts inside the schedule is saved as Business. A trip that starts outside it is saved as Personal, with a setting to ignore such trips entirely instead.
- Shawn can switch any trip between Business and Personal.
- If a Business trip is still running at the end time, keep recording until the trip ends and flag it "ran past schedule".

**How it works today**
1. **The schedule** is seven days, each with a switch ("tracked") and a start and an end time. It is kept with the other settings. A day's end must be later than its start, both on a whole minute: there are no overnight spans, and the latest end is 23:59. Out of the box: Monday to Friday, 08:00 to 16:30; Saturday and Sunday are off and carry the same hours for the day one of them is switched on. The screen is described under [Settings](#settings).
2. **The rule** (`classifyTrip` in `core/schedule/TripClassification.kt`, a pure function). A trip is **Business** when its **start**, read as a day and a time of day in the phone's time zone, falls on a tracked day at or after that day's start time and before its end time. Otherwise it is **Personal**. On the stroke of the start time is Business; on the stroke of the end time is Personal. Only the start decides, and what started the trip (the truck, or a Start button on the phone or the car) makes no difference.
3. **Ran past schedule.** A Business trip that ends after the end time *of the day it started on* is flagged. The end time is worked out as a point in time, so a trip that runs past midnight is judged against the day it began, and a day on which the clocks change is as long as it really was. Ending exactly at the end time is not past it. A Personal trip is never flagged.
4. **When it happens: when a trip is finalised, and at no other moment.** The trip rules close the trip as they always did; in the same write that closes it, the category and the flag are stored (`TripLedger.close` in `platform/trip/`, `TripRepository.closeTrip`). The schedule that is used is the one stored at that moment, so a change on the Settings screen applies to trips that end after it. **The schedule never decides whether a trip starts, carries on or ends.** Nothing in the trip rules, the Bluetooth triggers or the trip service reads it. A truck that connects at 07:55 for a drive at 08:05 is one trip that started at 07:55, recorded whole, saved as Personal, and Shawn can mark it Business.
5. **"Ignore them".** With that chosen for trips outside the schedule, a trip is still started, recorded and measured exactly as usual. When it is finalised and turns out Personal, it is stored as **discarded**, with its times, distance, positions and GPS points, and marked as ignored for that reason. It is then out of the list and the totals, and can be found on the Trips screen under "Show deleted and discarded trips", where it says "Left out: it started outside your work hours, and Settings says to ignore such trips. Not counted." and has the button "Count this trip". Counted, it is an ordinary Personal trip. A trip the trip rules had already discarded (too short, or a start that was never confirmed) is not marked as ignored: it says what it always said.
6. **By hand.** On the Trips screen a finished trip can be marked as Business or as Personal (see [Trip log](#trip-log-home-day-and-month-views)). That writes the category and that Shawn set it, and nothing else. A trip set by hand is never changed by MilO again. With "Ignore them" on, marking a kept trip Personal does not discard it: the setting is applied once, when a trip is finalised, and this trip has now been dealt with by hand.
7. **Trips recorded before the schedule existed** have no category after the update (the migration leaves them "not sorted"). At every process start a catch-up (`TripCategoryCatchUp` in `data/trip/`) looks for closed trips without a category that were not set by hand, and sorts each by the schedule **as it is at that moment** and the phone's time zone then. It writes the category and the flag and nothing else: it never discards a trip, whatever "Ignore them" says, and it sorts deleted and discarded trips too, so that they have their category when they are restored or counted. After its first pass it finds nothing. The first pass after the update runs before Shawn has seen the Settings screen, so it goes by Monday to Friday, 08:00 to 16:30.
8. **A later change of the schedule changes no stored trip,** sorted by the schedule or set by hand. What a trip was saved as is a fact about the day it was driven.
9. **A trip that is not sorted** (possible only between the update and the first pass, or while the settings file cannot be read) is shown as "Not marked Business or Personal yet", is counted in a line of its own, and can be marked by hand.
10. **The event log.** The `TRIP` line that records the end of a trip now ends with what the schedule made of it and what it went by: "…; saved as Business: it started on a Tue at 10:14:05; that day's hours are 08:00 to 16:30 (America/Edmonton)", "…; saved as Business, ran past schedule: …", "…; saved as Personal: it started on a Sat at 10:02:40; Sat is not a tracked day (America/Edmonton)". An ignored trip's line starts "Trip 12: discarded: it started outside the work schedule, and trips outside it are set to be ignored (12340 m). It can be counted on the Trips screen" and, like the line of a trip that was too short, ends with what the trip is if it is counted after all ("…; if it is counted, it is Personal: it started on a Sat at 10:02:40; Sat is not a tracked day (America/Edmonton)"). The catch-up writes one `TRIP` line for a pass that sorted something ("Business or Personal set for 5 trips recorded before they were sorted (process start), by the work schedule as it is now: 3 Business (1 ran past schedule), 2 Personal. No trip was discarded or otherwise changed."), with each trip and its reason in the detail. Every marking by hand is one `TRIP` line, and a refused or failed one is a line too.

**Where the code lives**
- `core/schedule/`: `WorkSchedule.kt` (the seven days, what hours a day may have, the defaults, the copy), `TripClassification.kt` (`classifyTrip`, and `fileTrip`, which adds the "ignore" decision at the moment a trip is finalised), `TripCategory.kt` (Business, Personal). Pure Kotlin: no file in the package imports anything of Android, its string resources included.
- `core/designsystem/text/TripCategoryWords.kt` (`categoryWordsRes`): which words a row uses for what a trip is saved as. It chooses a string resource, so it stands with the design system and not beside the rule (moved there by the review of 2026-10-06).
- `data/settings/`: `ScheduleStorage.kt` (how the schedule is kept in the settings file), `SettingsStore.kt` (`setSchedule`, `setIgnoreTripsOutsideSchedule`), `MiloSettings.kt`.
- `data/trip/`: `Trip.kt` (the four columns), `TripDao.kt` and `TripRepository.kt` (`closeTrip`, `findUnsortedTrips`, `sortUnsorted`, `setCategoryByHand`), `TripCategoryCatchUp.kt` (the catch-up and its rule `needsSorting`), `TripCategories.kt` (what a trip may be marked as; when "ran past schedule" is said), `TripTotals.kt` (`categoryTotals`), `TripCategoryLogText.kt` (the wording of the log lines). `data/MiloMigrations.kt` has the step from version 2 to 3.
- `platform/trip/`: `TripLedger.kt` (sorts a trip as it closes it), `TripRuleSettings.kt` (reads the schedule with the other settings), `TripLogText.kt`.
- `feature/settings/ScheduleCards.kt` and `feature/trips/` (the buttons, the rows, the totals).
- Tests: `app/src/test/.../core/schedule/` (`TripClassificationTest`: the edges of a day, an untracked day, across midnight, both clock changes, the time zone; `TripFilingTest`; `WorkScheduleTest`), `core/designsystem/text/TripCategoryWordsTest.kt`, `data/trip/TripCategoryCatchUpTest.kt` and `TripCategorySelectionTest.kt` (which trips the catch-up may touch, compared with the query), `TripCategoryTotalsTest.kt`, `data/settings/ScheduleStorageTest.kt`, `platform/trip/TripControllerScheduleTest.kt` and `TripControllerIgnoreTest.kt` (the trip controller on stand-ins: sorted at the close, recorded in full whatever the schedule says), `feature/trips/TripMarkingTest.kt` and `TripMonthCategoryTest.kt`, `data/MiloMigrationsTest.kt`.

**Depends on**
The settings store, the trips table (database version 3), and the phone's time zone and clock.

**Edge cases & gotchas**
- **Never run on the phone.** What ran on an emulator on 2026-10-06 is in FINDINGS_LOG under that date.
- **The start decides, to the millisecond.** A trip that starts a minute before the day's start time is Personal however long it runs. The cure is the button: mark it Business.
- **The trip's start is when MilO opened the trip,** which for a trip the truck starts is when the phone connected to it, not when the truck began to move.
- **The time zone is the phone's at the moment the trip is sorted,** not where the trip was driven. A trip is sorted when it ends, so this only shows if the phone changes zone during a trip. For the catch-up it is the zone at the first start after the update. **What it costs:** a work drive from Alberta into the Pacific time zone is read an hour earlier than it began, so one that started in the first hour of the work day is saved as Personal, and with "Ignore them" on it is left out. In winter a drive into Saskatchewan is read an hour later, and one that started in the last hour of the day goes the same way. Nothing on the trip's row says why; its line in the Log names the zone it was read in. It is put right by hand: Mark as Business, after Count this trip if it was left out. The cure is to store the zone with the trip when it starts and to sort by that: a new column, and a change to how a trip is started, which the work on the schedule was not to touch. A reviewer reproduced the case on an emulator on 2026-10-06; it was left as it is, for Shawn to decide.
- **The first catch-up uses the default hours.** If Shawn's real hours differ, trips recorded before the update can be sorted wrongly, and changing the schedule afterwards does not sort them again. They are marked by hand.
- **"Ran past schedule" is stored once, when the schedule sorts the trip,** and is shown only while the trip is Business. A trip that was Personal by the schedule and is marked Business by hand never shows it, even if it ended after the day's end time: the flag is not worked out again.
- **With "Ignore them" on, a Personal trip looks like a trip that vanished:** it is recorded, then it is not in the list. The Trips screen's line "N deleted or discarded trips are not shown" is the only sign, and the Log says why.
- **An ignored trip gets no addresses** until it is counted: only a finished trip is looked up.
- **A setting that cannot be read leaves a closing trip unsorted** (it is not sorted by hours that are not Shawn's), and the catch-up logs an `ERROR` line at each start until the settings can be read again.
- A day's hours cannot run past midnight. Night work that crosses midnight would need two days' hours, and the trip is still judged by its start alone.
- There is no report yet: what Business means for the monthly PDF is [Monthly PDF and submission](#monthly-pdf-and-submission), phase 3. `categoryTotals` and the `category` column are what it will read.

**Related**
[Settings](#settings), [Trip log](#trip-log-home-day-and-month-views), [Diagnostics and reminders](#diagnostics-and-reminders).

---

## Trip log: home, day and month views

**Status:** In progress: the home screen shows the trip in progress and today's finished trips, and the Trips screen lists one month at a time (times, from and to addresses, Business or Personal, km), where a trip can be deleted and restored, a discarded trip counted after all, and a finished trip marked Business or Personal. Each day and each month shows its Business figures first and Personal apart. All of it ran on an emulator, never on the phone. Editing and adding a trip are still to come in phase 2 · **Platforms:** Android · **Last updated:** 2026-10-06

**What it does**
Shows what was recorded and lets Shawn correct it.

**Required behaviour**
- Home screen: current trip status, plus today's sessions (count, km each, total km, total drive time).
- Day view: each session with start and end time, start and end address, km, and Business/Personal.
- Month view: sessions and business km per day, plus month totals.
- Shawn can edit any trip, delete trips, and manually add a missed trip. Manually added or edited trips are marked as such.
- There is no purpose or note field. Dropped at kickoff.

**How it works today**
*Home* (`feature/home/`). Beside the title, a cog that opens [Settings](#settings). While the setup checklist needs attention, a card "Setup needs attention" with a button to Setup (see [Permission checklist](#permission-checklist)). Then one card, "Current trip": with no trip open, "No trip in progress"; with one, the distance so far, "Trip in progress" (or that it is waiting for the truck to reconnect) and the time it started. It reads `TripController.activity` through `HomeViewModel` and keeps no copy. Below the card, if the last start failed, a second card lists why. Then the Start trip / End trip button, and under it the card "Today":
1. **What it shows.** Business first (since 2026-10-06): the word "Business", the km of today's Business trips in large figures, and under it how many Business trips and their drive time ("3 trips · 1 h 12 min driving"). Then, in a quieter line of its own and never added to the Business figure, "Personal: 5.0 km · 1 trip" (it is there with zeros on a day without a Personal trip). Then every finished trip on a line of its own with its start time, its end time, what it is saved as ("Business", "Business · ran past schedule" or "Personal") and its km, newest first. Before the first trip of the day: "No finished trips yet today."
2. **What is counted** is decided by `todayTrips` in `data/trip/TripTotals.kt`, the function the [Android Auto screen](#android-auto-screen) uses: finished trips that started today. A discarded or a deleted trip is not there, and so a trip left out by "Ignore them" is not there either. The trip in progress is not there: it has the card above, and while one is open the Today card says "The trip in progress is added when it ends." The split into Business and Personal is `TodayTrips.totals`, worked out by the same `categoryTotals` rule the Trips screen uses.
3. **Drive time** is the time from start to end of each finished Business trip, added up and rounded down to whole minutes once. A stop with the engine running is inside a trip, so it is in the drive time. Personal trips have no drive time on the card.
4. **It follows storage.** A trip that ends, or is deleted, restored or counted on the Trips screen, changes the card by itself. Only today's trips are read (`daySpan` in `core/util/TimeSpan.kt`).
5. **Which day is today** is worked out when the screen comes to the front (`CameToFrontEffect`), in the phone's time zone. A trip belongs to the day it started on.
6. The button stands above the card so that it does not move down the screen as the day fills up.

*Trips* (`feature/trips/`), the second button of the bottom bar. Shawn asked for it by name on 2026-10-05: "a trip button to view previous and current months trips".
1. It opens on the current month, every time it is entered. At the top, one card: the month's name and year; then Business first, the word "Business", the km of the month's Business trips in large figures and their number; then, apart and quieter, "Personal: 22.0 km · 3 trips"; and two buttons, Previous month and Next month. Next month is greyed out on the current month: the screen never goes past it. Back is one month at a time, without limit. Business and Personal are never added into one figure. (A third line, "Not marked Business or Personal yet: …", appears only while such a trip exists; see [Schedule and Business/Personal](#schedule-and-businesspersonal).)
2. Under it a switch, "Show deleted and discarded trips", off by default. (Until 2026-10-06 it was "Show discarded trips".)
3. Then the trips, one card per day, newest day first and newest trip first within a day. **Under the day's date, its heading has a second line: the day's number of sessions and its Business km** ("3 trips · 42.1 km business"). The sessions are the day's finished trips, Business and Personal together; the km are of the Business ones only. Each trip shows its start time, its end time and its km, under the times where it went, and under that **what it is saved as: "Business", "Business · ran past schedule" or "Personal"**. Where it went: "10103 104 Avenue Northwest, Edmonton → 9321 Jasper Avenue, Edmonton". Shawn asked for this: "I just want to track from start address to end one for each trip." There is no map.
   - **A missing address is said in words,** never as coordinates and never as a gap. While neither address is known: "Looking up the addresses…". When MilO has given up on both: "No address found for this trip." With one known, the other side of the arrow says "looking up the address…" or "no address found".
   - The words are chosen by plain functions in `TripPlaceTexts.kt` from what the trip's row says (`startPlace` and `endPlace` in `platform/address/AddressRetry.kt`): an address, still being looked up, or not found.
   - Each time the screen comes to the front it asks for the missing addresses to be looked up (see [GPS recording and distance](#gps-recording-and-distance)). An address that arrives while the screen is open appears by itself.
4. **What is counted.** The totals and the numbers of trips are of the finished trips only (`isCounted` in `data/trip/TripTotals.kt`), added up by what each is saved as (`categoryTotals`, same file). A trip discarded as too short (or as a false start), a trip left out by "Ignore them" and a trip Shawn deleted are left out of the list and the totals; a line says how many are hidden ("2 deleted or discarded trips are not shown."). With the switch on they are listed in their day with their km greyed and with what they would be saved as, and neither the totals nor a day's heading change:
   - a discarded trip is marked "Discarded: too short, or a start that was never confirmed. Not counted.", with no line about addresses (a discarded trip is never looked up);
   - a trip left out by "Ignore them" is a discarded trip too, and is marked "Left out: it started outside your work hours, and Settings says to ignore such trips. Not counted." Its km are the full distance that was recorded;
   - a deleted trip is marked "Deleted by you. Not counted.", with the addresses it already had when it was deleted. An address it lacked is "no address found", never "looking up": nothing is looked up for a trip while it is deleted.
5. **A trip in progress** is shown in a card of its own above the days, "In progress", with its start time and its running distance, and is not in the total until it ends. The running distance comes from the trip controller, because the stored row holds 0 while a trip is open; if the controller is not recording that trip, no figure is shown. Under it, where the trip started: "Where it started is not known yet." until the first usable GPS fix, then "Looking up where it started…", then "From (address)", or "No address found for where it started."
6. **Which day and month a trip belongs to.** The day and the month it started in, in the phone's time zone. A trip that runs past midnight is listed once, under the day it started.
7. An empty month says "No trips in (month and year)."

*Marking a trip Business or Personal.* The brief: "I can switch any trip between Business and Personal."
1. Tapping a finished trip shows its buttons under it. The first is "Mark as Personal" on a Business trip and "Mark as Business" on a Personal one (a trip that is not sorted yet shows both); "Delete trip" is the last.
2. The press is made at once, with no question: the other button undoes it. The buttons are put away with the press. Left open, the same spot would at once hold the opposite button, and a double tap would undo the first.
3. **What it does.** The trip's category is written, with the note that Shawn set it by hand (`TripRepository.setCategoryByHand`), and nothing else on the row changes: it stays a finished, counted trip with its times, distance and addresses. Its row, its day's heading, the month's two totals and Home's Today follow at once.
4. **Set by hand is final for MilO.** The catch-up never touches such a trip, and no change of the schedule does. Only another press changes it.
5. **"Ran past schedule" follows the category.** It is shown only on a Business trip. A Business trip that ran past the schedule and is marked Personal stops showing it; marked Business again, it shows it again.
6. **With "Ignore them" on, marking a kept trip Personal does not discard it.** The marking writes the category and never the status.
7. **Only a finished trip can be marked.** A deleted or a discarded trip is restored or counted first, and the trip in progress has no category yet. The rule is in storage too: the update matches a finished trip that does not already have the category (`categoriesOffered` in `data/trip/TripCategories.kt` is the same rule for the screen, so it cannot offer what storage would refuse).
8. **The event log** gets one `TRIP` line per press: "Trip 12: marked Personal by hand on the Trips screen (12340 m, was Business). Nothing else about it changed, and the work schedule no longer decides what it is". A press that changed nothing gets one too ("Trip 12: mark as Personal refused on the Trips screen: it is Personal already. Nothing changed"), and a failure of storage an `ERROR` line; the screen then shows "That trip could not be changed. The Log screen says why."

*Deleting, restoring and counting a trip.* Shawn asked on 2026-10-05: "can we delete trips?"
1. **Delete.** Above the first day a line says "Tap a trip to mark it Business or Personal, or to delete it." Tapping a finished trip shows its buttons under it, "Delete trip" the last of them; tapping the trip again puts them away. One trip at a time shows its buttons. If a button would come up below the edge of the screen, as it does for the lowest trip in view, the list moves up until the trip and its buttons can all be seen. Only a tap moves the list: a button that comes back into view because the list was scrolled stays where it is. A deleted or a discarded trip is not something to tap, and a screen reader does not offer it as one; its own button (below) is. The button asks first: "Delete this trip?", with the trip's times and km and how to get it back, and the buttons Cancel and Delete. Cancel, Back and a tap beside the question change nothing.
2. **What Delete does.** The trip's status becomes `DELETED`, and nothing else on its row changes: its times, distance, positions and addresses stay, and so do its raw GPS points. It leaves the list, the month's total and count, the home screen's Today and the Android Auto screen's Today at once.
3. **Restore.** With the switch on, a deleted trip has a button "Restore". It makes the trip a finished trip again, exactly as it was. No question is asked: nothing is taken away, and Delete undoes it.
4. **Count this trip.** With the switch on, a discarded trip has a button "Count this trip". It makes the trip an ordinary finished trip, with the distance that was measured. It is there because the distance thresholds are untested on the phone and a real trip can have been discarded wrongly. The button is on every discarded trip, a false start included: on a truck whose connection no reading can confirm, a real drive ends up as one. A counted trip is deleted like any other, which is also how counting is undone.
5. **A trip that is still being recorded cannot be deleted.** Its card has no button and says so. The rule is also in storage: every one of the three changes is one update that matches on the status it starts from (`TripCorrection` in `data/trip/`: finished to deleted, deleted to finished, discarded to finished), so a press that arrives for a trip in any other state, an open one above all, changes nothing. Which button a row shows is taken from the same three lines (`correctionOffered`: the change that starts from the row's status), so the screen cannot offer a change that storage would refuse.
6. **Addresses.** A deleted or discarded trip is never looked up. When a trip becomes a finished one again, by Restore or by Count this trip, the screen asks for a lookup pass at once, and its missing addresses arrive like any other trip's.
7. **The event log** gets one `TRIP` line for every press that reaches storage: "Trip 12: deleted on the Trips screen (12340 m, was finished). It is no longer counted. Its row and its GPS points are kept, and Restore puts it back", "Trip 12: restored on the Trips screen (12340 m, was deleted). It is counted again", "Trip 9: counted on the Trips screen (250 m, was discarded). It is now a finished trip". A press that changed nothing gets one too ("Trip 12: delete refused on the Trips screen: it is still being recorded. Nothing changed"), and a failure of storage an `ERROR` line with the exception. After either of those the screen shows "That trip could not be changed. The Log screen says why." until the next change.
8. **Reading.** Only the month on screen is read: `TripRepository.observeTripsStartedBetween` takes a span of time, and `monthSpan` in `core/util/TimeSpan.kt` gives the span of a month in a time zone (from local midnight on the 1st up to, not including, local midnight on the next 1st, so a month in which the clocks change is an hour longer or shorter). The list follows the database: a trip that closes while the screen is open appears by itself. Which month is the current one is worked out again each time the screen comes to the front.
9. The sums and the grouping are pure functions in `feature/trips/TripMonth.kt` (`monthSummary`, `stepMonth`, `canStepForward`). Which words a row uses for what a trip is saved as, for why it is left out, and on its buttons is chosen in `TripNoteTexts.kt`.

**Where the code lives**
- `feature/home/`: `HomeScreen.kt`, `TodayCard.kt` (the Today card) and `HomeViewModel.kt`.
- `feature/trips/`: `TripsScreen.kt`, `MonthCard.kt`, `TripRows.kt` and `DeleteQuestion.kt` (the screen), `TripsViewModel.kt`, `TripMonth.kt` (what is counted, how it is grouped, each day's heading, and the kinds of row, each with the stored status it stands for), `TripPlaceTexts.kt` (the words for where a trip went), `TripNoteTexts.kt` (the words for what a trip is saved as, why it is left out, and its buttons), `TripCorrections.kt` (makes a delete, a restore, a "count this trip" or a marking as Business or Personal, and writes its line to the event log).
- `data/trip/`: `TripCorrection.kt` (the three changes as changes of status, and which one a trip of each status is offered), `TripCategories.kt` (what a trip may be marked as, and when "ran past schedule" is said), `TripTotals.kt` (`isCounted`, `categoryTotals`, `todayTrips`), `TripDao.kt` and `TripRepository.kt` (the query by span of time; `correct`, the one update behind all three changes; `setCategoryByHand`).
- `core/designsystem/text/TripCategoryWords.kt`: which words say Business or Personal, shared by Home and Trips so that a trip reads the same on both.
- `core/util/TimeSpan.kt` (a month and a day as a span of time; the day and month of a stored time), `TimeFormat.kt` (the month heading, the day heading, the time of day) and `DurationFormat.kt` (hours and minutes). `core/designsystem/component/TwentyFourHourClock.kt`: whether the phone is set to 24 hours, which every time of day on Home and Trips is written by.
- Tests: `app/src/test/.../feature/trips/TripMonthTest.kt`, `TripMonthLeftOutTest.kt` (the month once trips can be deleted, and that a row's kind stands for the status its trip has), `TripPlaceTextsTest.kt` and `TripCorrectionsTest.kt` (what is stored after each change, every change refused from every status but its own, the log lines; against the stand-in trips table, through the repository the screen uses); `app/src/test/.../data/trip/TripCorrectionTest.kt` (where each change starts and leads, and which one each status is offered) and `TripTotalsTest.kt` (what counts, today's totals and drive time) and `TripCategoryTotalsTest.kt` (the totals with Business, Personal and unsorted trips mixed; what a trip may be marked as); `app/src/test/.../feature/trips/TripMonthCategoryTest.kt` (the month's two totals, a day's heading, what a row says) and `TripMarkingTest.kt` (what a marking stores, what is refused, the log lines); `app/src/test/.../core/util/TimeSpanTest.kt`, `TimeFormatTest.kt` and `DurationFormatTest.kt`.

**Edge cases & gotchas**
- **The Trips screen has never been drawn on the phone.** Device checks 76 to 86. It was drawn on an emulator on 2026-10-05, with addresses (FINDINGS_LOG). Deleting, restoring, counting and the home screen's Today card were used on an emulator on 2026-10-06, and are device checks 101 to 110 and 125 on the phone.
- **A deleted trip is never destroyed,** and nothing in MilO can destroy one: there is no "empty the bin". The row and its raw points stay on the phone for good, like a discarded trip's.
- "Count this trip" counts the distance as measured. If the trip was discarded because GPS was poor and the distance came out too short, the counted trip carries that too-short distance; nothing recalculates it from the raw points, and there is no editing yet (phase 2).
- Restore and Count this trip ask no question, Delete does. A trip counted by mistake is put right by deleting it; it then shows as "Deleted by you", not as "Discarded" again.
- Which finished trip shows its Delete button, and whether the switch is on, belong to the screen: leaving Trips and coming back starts with the switch off and no button showing.
- The home screen's Today is worked out when Home comes to the front. Left open across midnight, it keeps showing the day it was opened on until Home is left and opened again, or MilO is. The Android Auto screen looks at the clock every 10 seconds and does not have this.
- Today's totals are added up in metres and rounded once, like a month's, so the km of the single trips can add up to 0.1 more or less than the total shown.
- **The Trips screen has no send, share or export.** Sending a month or a date range to the accountant is [Monthly PDF and submission](#monthly-pdf-and-submission), which is not built.
- Editing and adding a trip are still to come in phase 2; Business/Personal and the figures of each day were built on 2026-10-06. There is no separate day view: a day is its card on the Trips screen, with its heading. An address cannot be corrected by hand until editing exists. (Deleting was phase 2 in the brief and was built on 2026-10-06, when Shawn asked for it.)
- **Every finished trip is listed and counted, Business or Personal.** What keeps a Personal trip off the report is its category, not its absence. Only "Ignore them" takes a trip out of the list, and then as a discarded trip that can be counted.
- A day's heading counts sessions of both kinds and adds up Business km only, as the brief asks. A day of Personal trips only therefore reads "2 trips · 0.0 km business".
- The Android Auto screen's Today row still adds Business and Personal together (see [Android Auto screen](#android-auto-screen)).
- Marking a trip has never been tried with a screen reader, like the other buttons of a row (device check 127).
- The query by span of time is read through an index on the start time, added by the first migration (database version 2).
- "From → to" wraps onto a second line when the two addresses are long; a trip's row is then taller than its neighbours. A finished trip's row is never lower than 48 dp, Material's smallest target for a finger, which matters for one with a single line under its times ("Looking up the addresses…").
- The delete, restore and count buttons of a row have never been used with a screen reader (device check 127).
- A trip still recording at the turn of the month is shown in the month it started in, not in the new current month.
- The total is added up in metres and rounded once. The km of the single trips, each rounded to one decimal, can therefore add up to 0.1 more or less than the total shown.
- If the phone's time zone changes, the days and months are worked out again in the new zone the next time the screen comes to the front. A trip near midnight can then move to the neighbouring day.
- **A trip's times have 24 hours or AM and PM as the phone itself is set** ("Use 24-hour format" in Android's date and time settings; until that switch has been touched, Android goes by the language). "08:14 – 08:39" on a phone set to 24 hours, "8:14 a.m. – 8:39 a.m." on one that is not, on Home, on Trips and in the question before a delete alike. Until the review of 2026-10-06 the language alone decided. The switch is read when a screen is drawn and again each time it comes back, so a change shows without a restart (seen on an emulator, device check 152). The Log's times are always 24 hours, to the second.
- A trip whose stored start is later than its end (the clock was corrected during it, see [GPS recording and distance](#gps-recording-and-distance)) is listed as stored.

---

## Monthly PDF and submission

**Status:** Planned (phase 3), **not built: nothing in the app creates or sends a report.** Shawn asked for it again on 2026-10-05. Leaving it in phase 3 is the assistant's proposal and waits for his confirmation (FINDINGS_LOG, 2026-10-05) · **Platforms:** Android · **Last updated:** 2026-10-05

**What it does**
Produces the monthly reimbursement report and hands it to Gmail.

**Required behaviour**
- Shawn picks a specific month or a custom date range, not only the current month, and generates a PDF of the Business trips in it. The date range was added on 2026-10-05 ("be able to pick specific month or date range"). Built with Android's own `PdfDocument`, no third-party PDF library.
- Header: name, company, vehicle, month, generated date.
- One table per day with columns **Start, End, From, To, km**, then a daily subtotal. A month total km at the end.
- **No rate and no amount owed.** Accounts works the money out, so the report shows km only.
- No purpose column and no odometer readings.
- A blank signature line.
- Manual or edited trips carry an asterisk, with a legend.
- Send button opens Gmail with the accounts address, the subject "Mileage – [Month Year] – [Name]", and the PDF attached. Shawn taps send himself.
- After sending, the month is marked Submitted with the date. Each month shows submitted or not submitted. Resubmitting a month warns first, is allowed, and is labelled a revision.
- CSV export for any month or date range.
- **Open, to settle at phase 3 kickoff:** how a date-range report counts toward a month's Submitted status (for example a range that covers half a month, or spans two).

**Edge cases & gotchas**
- Android cannot tell the app whether the email was really sent, so marking a month Submitted needs Shawn's confirmation.
- Already built, for the report to use and not rebuild: `TripRepository.observeTripsStartedBetween` reads the trips of any span of time, a month or a custom range alike, and `monthSpan` in `core/util/TimeSpan.kt` turns a month into such a span (see [Trip log](#trip-log-home-day-and-month-views)).

---

## Diagnostics and reminders

**Status:** In progress (phase 1): the event log is stored; crashes and process kills are captured into it at every start, trip recording writes its evidence to it, and the Log screen shows it (nothing records that screen being drawn anywhere). The rest is phase 4 · **Platforms:** Android · **Last updated:** 2026-10-06

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
6. **Trip recording writes the evidence ADR-002 asks for.** Everything goes through the trip controller's inbox, so the lines are in the order things happened:
   - `TRIGGER`: every trigger, with its source in the message and the state before and after in the detail. The source says which receiver or service heard it and names the device, for example "Bluetooth receiver: ACL connected for the truck (AA:BB:…)". A trigger that read the truck's connection adds how the answer was reached, in brackets. Also: a Bluetooth broadcast or companion callback for a device that is not the truck, marked "Ignored"; a trigger that had to start the service first ("asked Android for the trip service"); a reading that came back unknown; a once-a-minute reading that was not believed; and a reading of "not connected" taken before any reading had shown the truck connected. The lines are dated when they are written, so they read in the order things happened. Two things are left out because they arrive all day and usually change nothing: GPS fixes, and a once-a-minute reading that only confirms what was believed.
   - `TRIP`: a trip starting; the truck being seen in it; a trip finishing or being discarded, with the distance, the reason and the fix counts; the hold-off being set and released, with the reason. Since 2026-10-06 the line for a trip that closes also says what the work schedule made of it and what it went by ("…; saved as Business: it started on a Tue at 10:14:05; that day's hours are 08:00 to 16:30 (America/Edmonton)"), and a trip left out by "Ignore them" says so in place of "finished" (see [Schedule and Business/Personal](#schedule-and-businesspersonal)). Also every trip deleted, restored or counted by hand on the Trips screen, every trip marked Business or Personal there, and every such press that was refused (see [Trip log](#trip-log-home-day-and-month-views)). Those lines are written by the screen, not through the trip controller's inbox. And one line for a pass of the catch-up that sorted trips recorded earlier, with each trip in its detail; that one is written by the catch-up itself.
   - `GRACE`: the grace period starting and being cancelled. Its running out is the `TRIP` line "ended by GRACE_EXPIRED".
   - `SERVICE`: the service reaching the foreground and for which trigger; a start that failed and why, with the exception; the service being destroyed; a service lost in mid-trip, and the trip being dropped from memory when Android will not start it again; MilO being removed from the recent apps; the trip-start sound playing or failing. Also, from the Settings screen: the sound being changed to a file of Shawn's or back to the bundled chirp, and each press of Play ("Settings screen, Play pressed. Trip-start sound: playing the chosen sound").
   - `ANDROID_AUTO`: every change `CarConnection` reports, with the raw value, and the state before and after. Also the life of the Android Auto screen: its service created and destroyed, each session created (with the host's package name and the Car API level agreed) and destroyed, the screen shown and no longer shown, and each time it told the driver that a trip could not start (see [Android Auto screen](#android-auto-screen)). A press of the car's button is a `TRIGGER` line, like the phone's.
   - `LOCATION`: fixes being requested and stopped, the time to the first fix and its accuracy, and location being lost for 10 seconds or more and coming back. A shorter loss is not logged: the emulator reports one around every fix.
   - `ERROR`: a failure inside the controller, with its stack trace; an unreadable settings file; a failure of the Android Auto screen (its own code, today's trips unreadable, or a call the car answered with an error); an audio file that was refused as the trip-start sound, with what Android said about it; a change on the Settings or the Trips screen that storage refused; a catch-up of Business and Personal that could not be made ("Sorting earlier trips into Business and Personal failed. They stay unsorted").
   - `PAIRING`: the truck being paired or adopted, a pairing that failed and why, and every check of the pairing with its result. While no truck is armed, the line also lists the phone's paired devices with their addresses, names an association that was not adopted, and says if the phone has no companion device support.
   - `ADDRESS`: every lookup of a trip's start and end address with what happened to each end and, if it failed, which attempt of four it was; a lookup for a trip in progress; a pass put off because the phone had no network. These are not written through the trip controller's inbox, so they are dated when written and can sit minutes or days after the trip they name. They name no address and no position.

7. **The Log screen** (`feature/eventlog/`), the last button of the bottom bar: the stored log, newest first.
   - Each line shows its time as `2026-10-05 08:14:03` (to the second, 24-hour, in the phone's own time zone, the same form in every language), its category by its stored name (`CRASH` and `ERROR` in red) and its message.
   - A line that has a detail says "Tap for details". Pressing it opens the detail under the line: the state before and after a trigger, a stack trace. Pressing again closes it. One line is open at a time.
   - **It is never read whole.** The newest 200 entries are read, through the index on the time. "Show older entries" at the bottom adds 200 more each time. The list is drawn lazily, so only the lines on screen are laid out. It opens as fast with ten thousand entries as with ten.
   - It follows the log: a line written while the screen is open appears at the top.
   - A setup button that could not open its HyperOS screen writes an `ERROR` line here (see [Permission checklist](#permission-checklist)).

Checked on an emulator (Android 16) on 2026-10-03: an induced crash, a force stop and a background kill each appeared in the log at the next start, once. On 2026-10-05 the same emulator showed the trigger lines of the companion service, the boot and update reconcile and the pairing check (FINDINGS_LOG). On the POCO X5 the log has been written and read back over adb once (2026-10-05: a start refused with its reasons, the trip and sound lines of a trip started with the button, no crash and no error line); an induced crash and a kill have not been tried there (device checks 1 to 7). Nothing records the Log screen being drawn anywhere.

**Where the code lives**
- `feature/eventlog/`: `EventLogScreen.kt`, `EventLogViewModel.kt`. The time format is `formatLogTime` in `core/util/TimeFormat.kt`.
- `platform/diagnostics/`: `CrashHandler.kt`, `ProcessExitReader.kt`, `ProcessExit.kt`, `StartupDiagnostics.kt`.
- `data/crash/`: `CrashFileStore.kt` (the crash files and where they are kept).
- `data/eventlog/`: `EventLogEntry.kt` (the table and the categories), `EventLogDao.kt`, `EventLogRepository.kt`.
- `app/MiloApplication.kt` starts both; `app/AppContainer.kt` builds them.
- The trip lines are written by `platform/trip/TripWorker.kt` and worded in `platform/trip/TripLogText.kt` and `TripLedger.kt`. The lines for a trip changed by hand are written and worded in `feature/trips/TripCorrections.kt`, and the lines about the choice of sound in `platform/trip/OwnTripSound.kt`. What the schedule made of a trip is worded in `data/trip/TripCategoryLogText.kt`, for the closing line and for the catch-up's line, which `data/trip/TripCategoryCatchUp.kt` writes. The address lines are written by `platform/address/TripAddresses.kt` and worded in `AddressLogText.kt`. The source of a Bluetooth or companion trigger is worded in `platform/bluetooth/TruckSignals.kt`, and the pairing lines in `platform/bluetooth/TruckPairing.kt` and `TruckPairingCheck.kt`.
- Tests: `app/src/test/.../platform/diagnostics/`, `app/src/test/.../data/crash/` and `app/src/test/.../feature/eventlog/EventLogPageTest.kt`.
- What can only be checked on the phone is in `docs/DEVICE_TEST_CHECKLIST.md`.

**Edge cases & gotchas**
- At most 20 crash files wait at once, so an app that crashes at every start cannot fill the phone. The first 20 are kept.
- Android keeps only a small number of exit records per app. If MilO is not started for a long time while being killed repeatedly, the oldest records are gone before they are imported.
- A crash produces two entries: the `CRASH` entry with the stack trace, and the system's `PROCESS` entry saying the process ended by crashing.
- Nothing trims the event log yet. It must be trimmed before phase 4 switches backup on, because it shares the backed-up database with the trips (`[DEBT]` in FINDINGS_LOG). A day of driving now adds a few dozen lines per trip.
- The lines are in English and are not translated, on the Log screen too: the log is evidence.
- The Log screen has no search and no filter by category, and the log cannot be copied or exported from it. For that, the database is read on the Mac (`docs/DEVICE_TEST_CHECKLIST.md`).
- Entries are ordered by the time they describe. A crash or a kill is written at the next start but dated when it happened, so it appears further down than the lines around its writing.
- During the hour that is repeated when the clocks go back, two lines an hour apart show the same time. Their order is still right.
- If the event log cannot be written while the trip controller is handling a failure, the process crashes on purpose, which leaves a crash file. A failure must not vanish.
- An unreadable settings file is never reset: a silent reset would lose the truck pairing without a trace. It also no longer crashes the start (that was a crash at every start, with no way out but clearing the app's data, trips included). The start-up import logs an `ERROR` entry and carries on, and the exit records are not imported while the file stays unreadable. Whatever reads the settings next has to deal with the failure itself, and each one that exists does. The trip controller logs it once and runs with the defaults. The pairing check reports `FAILED` with the reason, in its `PAIRING` line. The setup checklist logs an `ERROR` line and shows its rows without the confirmations. The Bluetooth receiver cannot tell whose broadcast it is, so it asks for a reading of the truck in place of trusting the event; that reading comes back unknown for the same reason. The companion service acts on its callback all the same, because Android only calls it for MilO's own association.
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

**Status:** In progress (grows each phase): the phase 1 settings and the work schedule are stored and have a screen. The screen ran on an emulator, never on the phone · **Platforms:** Android · **Last updated:** 2026-10-06

**Required behaviour**
- Truck: the paired device, with a way to change it.
- Trip: disconnect grace period (default 2 minutes), minimum trip distance (default 0.3 km), trip-start sound (bundled chirp or a chosen audio file).
- Schedule: tracked days and hours, and whether out-of-schedule trips are saved as Personal or ignored.
- Report: name, company, vehicle description, accounts email. There is no rate-per-km setting.
- Reminder: day of the month.

**How it works today**
*The screen* (`feature/settings/`), opened with the cog beside the title of the home screen. It is not in the bottom bar; while it is open the bar keeps Home marked, and its Back arrow (or Back) leads to Home. Five cards: Truck, Trips, Work schedule, Trips outside the work hours, Trip-start sound. Report and reminder settings arrive with the phases that build them.
1. **Truck.** The truck's name as the phone gave it ("The truck" if it has none), and a button "Change truck" that opens the pairing screen (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection)). With no truck stored: "No truck is paired", and the button says "Pair truck". Back from the pairing screen leads back to Settings. The card shows the name only; whether Android is still watching for the truck is the Setup screen's and the pairing screen's to say.
2. **Trips.** At the top: "A change applies from the next trip. A trip that is being recorded may already use it." Then two numbers, each between a minus and a plus button:
   - **Wait after the truck disconnects** (the grace period): 0.5 to 10 minutes in half minutes, default 2. Shown as "2 min" or "2.5 min".
   - **Shortest trip that counts** (the minimum trip distance): 0.1 to 2.0 km in tenths, default 0.3. Shown as "0.3 km".
   A button is greyed out at its end of the range. Each press is stored at once; there is no Save. The two buttons do not move while the value between them changes (see `StepperRow` under [Design system](#design-system)), so a thumb can stay where it is and press again.
3. **Work schedule** (since 2026-10-06; what it is for is under [Schedule and Business/Personal](#schedule-and-businesspersonal)). Two sentences on what the schedule does and when a change applies, then the seven days, Monday first:
   - Each day has a switch with its name. A day that is switched off shows nothing else; its hours are kept, and come back with the switch.
   - A day that is switched on shows two buttons side by side, "Start: 08:00" and "End: 16:30" on a phone set to 24 hours, "Start: 8:00 a.m." and "End: 4:30 p.m." on one that is not (the phone's own "Use 24-hour format" switch decides, as for a trip's times). Each opens the **time picker**: Android's clock dial under a title such as "Monday: start", with Cancel and OK. Nothing changes until OK; Cancel, Back and a tap beside the dial change nothing. The dial has AM and PM exactly when the times on the screen do: both go by the phone's switch.
   - **An end must be later than its start.** A time that would break that is not stored, and a red line says so **directly under that day's two times**: "A day's end must be later than its start. Nothing was changed. To move a whole day later, change its end first." If the line would come up below the edge of the screen, the screen moves up until all of it can be read; it does so for every time that is picked and refused, a second one for the same day included. It stays until the next press. (Until the review of 2026-10-06 the line stood once, at the top of the card, which for Friday is some 1,700 px above the button that was pressed.)
   - **"Use these hours on every tracked day"** is a button under a tracked day, shown only while another tracked day has other hours. Out of the box, with five equal days, there is none. Pressing it gives that day's start and end to every tracked day at once and asks no question; the buttons then disappear, because all tracked days agree. Days that are off are not switched on and keep their own hours.
4. **Trips outside the work hours.** One choice of two, each with a line saying what it means: **"Save as Personal"** (the default) and **"Ignore them"**. With "Ignore them" a trip is still recorded, then left out of the list and the totals; the card says where to find it ("on the Trips screen, switch on "Show deleted and discarded trips" to see one and count it").
5. **Trip-start sound.** The switch, "Sound in use", Play, "Use my own sound" and "Use the built-in sound": see [Trip-start sound](#trip-start-sound).
6. **When a change takes effect.** Nothing tells the trip engine. The trip controller reads the grace period and the minimum distance at every trigger, and the trip service reads the sound settings at every trip start, so the stored value is the one in force from the next event on, with no restart. For a trip that is being recorded this means: a grace period that starts after the change uses the new length, one that is already running keeps its deadline, and the minimum distance that counts is the one stored when the trip ends. The schedule and the choice for trips outside it are read the same way and used at one moment only, the end of a trip: a trip is sorted by what is stored when it ends. No trip that is already saved changes when the schedule does.
7. **If the settings file cannot be read,** the screen says so in place of the cards ("MilO cannot read its settings, so they cannot be shown or changed here. Trips are still recorded, with the usual values."), and an `ERROR` line goes to the event log. If a change cannot be stored, the screen says "That change could not be saved. The Log screen says why."
8. What the screen shows is decided by one pure function, `settingsUiState`, from the stored settings. The ranges and the stepping are `SteppedChoice` in `data/settings/SettingChoices.kt`; what hours a day may have, and the copy, are `WorkSchedule` in `core/schedule/`.

*The store.* `SettingsStore` in `data/settings/` keeps the settings in a DataStore Preferences file and is the only way to read or write them. `settings` is a flow of `MiloSettings` that delivers the current values and every change; `current()` reads them once. A value never written reads as its default. Each setter refuses a value that cannot be right: a negative duration, distance or time, and a blank address, name or URI. The schedule's setter needs no check of its own: a `WorkSchedule` cannot hold a day that ends before it starts. "No truck name" and "no sound name" are passed as null. If the file cannot be read, every read throws: the store never replaces it with empty settings.

Stored now: the truck's Bluetooth address, name and companion association id; the grace period (default 120 s); the minimum trip distance (default 300 m); the trip-start sound on or off (default on); an optional custom sound, as the place of MilO's copy of the file and the name of the file that was picked; and values that are not Shawn's to choose but must outlive the process: the hold-off after a manual end, stored as the time End was pressed; how far the process-exit records have been imported; and the time at which he confirmed each step of the setup checklist that MilO cannot read (`ConfirmedStep`: HyperOS Autostart, Battery saver, Other permissions, the recents lock). A step that is not stored is not confirmed. Since 2026-10-06 also the work schedule (for each day of the week whether it is tracked, and its start and end as minutes since midnight; default Monday to Friday, 08:00 to 16:30) and whether trips outside it are ignored (default no: they are saved as Personal). A day whose stored times make no sense, which no setter can write, reads with the default hours. Report and reminder settings are not stored yet.

Who reads and writes: the trip controller reads the grace period and the minimum trip distance at every trigger. It reads the stored hold-off only when it picks the state up from storage (after a process start, or after it dropped what it held in memory); from then on it keeps the hold-off in memory and writes every change. The trip service reads the sound settings at each trip start. The Settings screen writes the grace period, the minimum distance, the sound switch, the schedule and the choice for trips outside it, and `OwnTripSound` writes the custom sound. The schedule and that choice are read by the trip controller with the other settings at every trigger and used only when a trip closes, and by the catch-up at process start. The truck's three values are written by `TruckPairing` (from the pairing screen, or when it adopts an association), and the confirmations by the setup checklist. The truck's values are read by the Bluetooth receiver and the companion service at every event (to tell whether it is about the truck) and by every reading of the truck's connection.

**Where the code lives**
- `feature/settings/`: `SettingsScreen.kt`, `SettingsCards.kt` (the truck, the two numbers, the sound and the file picker) and `ScheduleCards.kt` (the schedule, the time picker, the choice for trips outside the hours), `SettingsViewModel.kt`, `SettingsUiState.kt` (what the screen shows and the function that decides it).
- `data/settings/`: `SettingsStore.kt`, `MiloSettings.kt` (the values and their defaults), `SettingChoices.kt` (the values the screen offers), `ScheduleStorage.kt` (the schedule's keys in the settings file).
- `core/schedule/WorkSchedule.kt`: the schedule itself and its rules.
- `core/util/DurationFormat.kt`: `formatMinutes`, the "2.5" of "2.5 min". `core/util/TimeFormat.kt`: `formatClockTime` (a day's start and end) and `formatDayOfWeek`. `core/designsystem/component/TwentyFourHourClock.kt`: `rememberTwentyFourHourClock`, the phone's own 24-hour switch, which the two times and the time picker's dial go by.
- `app/MiloNavigation.kt` opens the screen from Home and the pairing screen from it.
- Tests: `app/src/test/.../data/settings/SettingsStoreTest.kt`, `ScheduleStorageTest.kt` (the schedule read back, its key names, a file with nonsense in it) and `SettingChoicesTest.kt` (the ranges, the steps, what each value looks like on the screen), `app/src/test/.../core/schedule/WorkScheduleTest.kt` (what hours a day may have, the copy), `app/src/test/.../feature/settings/SettingsUiStateTest.kt` (with which day a refusal stands under), `app/src/test/.../core/util/DurationFormatTest.kt` and `TimeFormatTest.kt` (with the phone's 24-hour switch against the language's own habit), and `app/src/test/.../app/MiloNavigationTest.kt` for the way in and out.

**Edge cases & gotchas**
- **Never run on the phone.** Device checks 111 to 124 and 126, and for the schedule 130 to 135, 151 and 152. On an emulator on 2026-10-06 the screen was drawn, the two numbers were stepped, a trip recorded straight afterwards was discarded by the new minimum without a restart, and the screen came back after Android had killed MilO in the background (FINDINGS_LOG).
- The ranges (0.5 to 10 minutes, 0.1 to 2 km) were chosen while building and are Shawn's to change: two constants in `SettingChoices.kt`.
- The store accepts values the screen does not offer (a grace period of 0, for one). Such a value is shown as it is, and the first press of minus or plus brings it to the nearest value the screen does offer.
- There is no minimum distance of zero. With none, a start that never left the yard would count as a trip.
- A longer grace period keeps a trip open, and the phone recording, for longer after Shawn leaves the truck. The trip is still cut where the truck was found gone, so the walk is not counted.
- Changing a number is not written to the event log: each press would be a line. A trip's own lines show the values it ran with ("… is under the minimum of 300 m").
- The screen has no "reset to defaults". The defaults are 2 minutes and 0.3 km, Monday to Friday 08:00 to 16:30, and "Save as Personal".
- **To move a day's hours past its present end, the end is changed first** (and to move them before its present start, the start first): each time is checked against the other as it stands. The red line says so.
- **The copy asks no question** and replaces the hours of every other tracked day in one press. It is undone by setting the days again, which is why the button is only shown while it would change something.
- A change of the schedule is not written to the event log, like the two numbers. Each trip's closing line says which hours it was judged by.
- The time picker cannot choose seconds, and the schedule stores whole minutes. The latest end a day can have is 23:59; a trip that starts in the last minute of such a day is Personal.
- The day names follow the phone's language. **Whether a time has 24 hours or AM and PM follows the phone's own "Use 24-hour format" switch:** the dial, the two times beside it, and every trip's times on Home and Trips, so that they agree with each other and with the phone's clock. Until the review of 2026-10-06 the language alone decided, and on a phone set to 24 hours in English the dial had AM and PM, where one missed tap stores a time that is valid and wrong. Where the phone's switch and the language's own habit disagree, the time is written plainly, "16:30" or "4:30 PM", and not in the language's own short form. Seen on an emulator, never on the phone (device check 152).
- **"That change could not be saved" still stands at the top of the screen,** above the first card, whichever card the press was in. For a press far down the screen it is out of view, the fault the schedule's own red line had until the review of 2026-10-06. It needs a settings file that cannot be written, and was left for a change of its own.
- **The time picker is Material 3's, which that library still marks as experimental** in the release the build pins (1.4.0). It is used through one component (`TimeDialog`), so a change to it in a later release is one file's repair; the weekly update PR would fail to compile, not misbehave.

---

## Design system

**Status:** Live · **Surfaces:** Phone · **Last updated:** 2026-10-06

**What it does**
The one place colours, spacing, type and shapes are defined, and the shared components every phone screen is composed from. Check here before building any UI: reuse what exists, and add to it deliberately when something is missing (STANDARDS §8).

**What exists**
- Tokens in `core/designsystem/theme/`: `Color.kt` (light and dark schemes plus the four status colours), `MiloSpacing.kt` (4, 8, 16, 24, 32 dp steps), `Type.kt`, `Shape.kt`. `MiloTheme` applies them and exposes `MiloTheme.spacing` and `MiloTheme.statusColors`.
- **Status colours** (`MiloTheme.statusColors`): `ok` (green), `problem` (red, the scheme's error colour), `attention` (amber: Shawn has to do or confirm something MilO cannot check) and `unknown` (grey, the colour of secondary text: MilO could not find out). `attention` and `unknown` were added on 2026-10-05 for the setup checklist.
- Components in `core/designsystem/component/`:
  - `PrimaryButton`: the main action on a screen.
  - `SectionCard`: a titled group of content.
  - `StatusRow`: one requirement and its state. An indicator, a label, an optional second line, and up to two text buttons on a line of their own under the text. **Four states** (`RowStatus`), each with its own shape as well as its own colour: `OK` (green tick), `PROBLEM` (red warning), `UNKNOWN` (grey question mark), `NEEDS_CONFIRMATION` (amber empty ring). The icons are in `StatusIcons.kt`. A setting that Shawn has confirmed by hand is drawn as `OK`, and its second line says that it is his word.
  - `MiloNavigationBar`: the bottom bar, Material 3's navigation bar with one entry per top-level screen, each with an icon and its name. Added on 2026-10-05, with navigation.
  - `ScreenTitle`: the heading every screen starts with. Given an `onBack`, it shows a back arrow before the title; only a screen opened from another one (pairing, Settings) has it. Given an `action` (`ScreenTitleAction`: an icon, what a screen reader says for it, and what it does), it shows one icon button at the end of the line. **Added on 2026-10-06** for the way from Home to Settings, which has no place in the bottom bar. It is for the way to another screen, not for an action on the screen itself.
  - `SwitchRow`: a labelled on/off switch. The whole row is the target.
  - `StepperRow`: a setting that is a number chosen in steps. Its name, an optional second line saying what it is for, and the value between a minus and a plus button (`StepperButton`: what a screen reader says for it, what it does, and whether it is greyed out). **Added on 2026-10-06** for the grace period and the minimum trip distance. Buttons and not a slider, because the values are few and exact, and a slider is hard to set to one of them with a thumb. The value stands in the middle of a place of fixed width (five times its text size, so it grows with the phone's font size), which keeps both buttons where they are while the value changes: with a place as wide as its text, the minus button moved 14 dp between "2 min" and "1.5 min", and every other press of a thumb held still missed it. A value wider than the place is still shown whole, and only then do the buttons move. A screen reader says the new value after each press.
  - `FigureRow`: a line or two of text with one figure at its end, such as a trip and its km. The figure can be greyed, for one that is in no total, or left out. **Added on 2026-10-06**, taken out of the Trips screen's rows when the home screen's Today card needed the same row: every list of trips is made of it.
  - `ChoiceRow`: one of a few choices of which exactly one is in force: a radio button, its name, and an optional quieter line saying what choosing it means. The whole row is the target, 48 dp at least, like `SwitchRow`. **Added on 2026-10-06** for "Save as Personal" and "Ignore them": a switch says on or off, and this setting has two sides that both need a name. The rows of one choice stand in a column with `Modifier.selectableGroup()`, so that a screen reader announces them as one group.
  - `TimeDialog`: asks for a time of day with Android's usual clock dial, under a title that says what the time is for, with a button that takes the time and one that does not. Nothing is chosen until the first is pressed; pressing beside it, or Back, changes nothing. It is told whether to show AM and PM. **Added on 2026-10-06** for the start and the end of a work day. It wraps Material 3's time picker, which is still marked experimental in the pinned Material release; the opt-in is in this one file and nowhere else.
  - `ConfirmDialog`: the question before something is taken away. A title, a text that says what will happen and how it is undone, a button that names the action ("Delete", never "OK") and a button that does not do it. Pressing beside it, or Back, does nothing. **Added on 2026-10-06** for deleting a trip; the first dialog of MilO's own.
  - `MiloIcons`: the icons a screen may use: Home, Trips, Setup, Log, Back, Settings (a cog), Add and Remove (the plus and the minus of a `StepperRow`).
  - `rememberTwentyFourHourClock`: draws nothing. It answers whether the phone is set to write times with 24 hours ("Use 24-hour format" in Android's date and time settings; until that switch has been touched, Android answers by the phone's language), and reads it again each time the screen resumes, because the switch is changed outside MilO and Android tells a screen nothing about it. A screen calls it once and hands the answer to `formatTimeOfDay` or `formatClockTime` (`core/util/TimeFormat.kt`) and to `TimeDialog`. **Added on 2026-10-06** by the review of the work schedule. It is the only place a phone screen reads that setting, and the only setting a Composable reads for itself.
  - `CameToFrontEffect`: draws nothing. It calls the screen's ViewModel when the screen is first shown and every time Shawn is looking at it again, on either of two signs: the screen resumes (it is entered, or MilO returns from a settings screen, another app or one of Android's dialogs), or MilO's window gets the focus back (the quick settings panel or the notification shade closing, which resumes nothing). Returning from a settings screen gives both signs, so what it calls must be safe to call twice. The rule for the second sign is a plain function with a unit test (`focusRegained`); the effect itself cannot be tested off the phone. Added on 2026-10-05, after the review of the screens.
- **Words two features must say alike** are in `core/designsystem/text/`. Today one file, `TripCategoryWords.kt` (`categoryWordsRes`): which words say what a closed trip is saved as, used by Home and by Trips. It is here and not beside the rule in `core/schedule/`, because it chooses a string resource and the rules packages import nothing of Android (**moved here on 2026-10-06** by the review). It is the only file of the design system that knows a type from outside it (`TripCategory`).
- **Patterns the screens share,** to be followed by the next ones:
  - A screen starts with `ScreenTitle` and pads its content with `MiloTheme.spacing.medium`. A screen of fixed content is a scrolling `Column` of `SectionCard`s; a screen of stored rows (Log, Trips) is a `LazyColumn`.
  - While a screen's first read is under way it shows one line of text ("Reading…"), and an empty list says so in a sentence. Neither is a component.
  - A problem and the way to fix it is a `StatusRow` with a button, on Home, Setup and the pairing screen alike. A press that did not work (a refused sound file, a change that could not be stored, a trip that could not be changed) is a `StatusRow` in the red state without a button, shown until the next press. It stands beside what was pressed: in the sound's card, under the day whose time was refused.
  - A setting is stored at the moment it is changed. No screen has a Save button. A setting that is picked in a dialog (a time of day) is stored when the dialog's own button is pressed.
  - A time of day is written by `formatTimeOfDay` or `formatClockTime`, with the answer of `rememberTwentyFourHourClock`: 24 hours or AM and PM as the phone is set, never as a screen decides for itself.
  - A setting that is on or off is a `SwitchRow`; one that is a number in steps is a `StepperRow`; one that is a choice between named things is a column of `ChoiceRow`s; a time of day is a text button that shows the time and opens a `TimeDialog`.
  - A total that must not be read as part of another stands in a line of its own, quieter than the figure above it (Personal under Business, on Home and on Trips).
  - Something that can be undone is done at once (Restore, Count this trip, a setting); only what takes something away asks first, with a `ConfirmDialog`.
  - A secondary action is a text button at the end of its line, on a line of its own under the text it belongs to.
  - A row that is pressed as a whole is at least 48 dp high, Material's smallest target for a finger. `SwitchRow` sees to that itself; a row that a screen makes pressable (a finished trip on Trips) uses Material's own `minimumInteractiveComponentSize()`, so no dp value is written in the screen. A row that cannot be pressed is not given a click handler that is switched off: a screen reader would call it "disabled".
  - Something that appears because of a tap appears where it can be seen: if it would be below the edge of a list, the list moves (`BringIntoViewRequester`; the Delete button on Trips, and the refusal of a day's hours on Settings). Only a tap moves the screen: what is drawn again because the phone was turned stays where it is.
  - A screen that shows something Android reports no changes of (a permission, a setting, the paired devices, which month is the current one) reads it again through `CameToFrontEffect`, never through a lifecycle effect of its own.
  - A ViewModel is handed plain functions for navigation; a feature never imports another feature.

**Edge cases & gotchas**
- No colour, dp or sp value may be written outside `core/designsystem/`. The only exceptions are the two launcher-icon drawables, which the launcher reads before Compose exists, and `drawable/ic_stat_trip.xml`, the notification icon, which Android draws itself outside Compose.
- Notifications are not Compose and cannot use these components. Their text is in `strings.xml`.
- The icons are built from path data in `MiloIcons.kt` and `StatusIcons.kt`, not taken from the material-icons library, which is frozen. A unit test proves that each one parses. **How the icons added on 2026-10-05 look on the phone has not been reported:** Shawn has used the bottom bar there (2026-10-05), and device check 52, which asks about each icon, has not been run. The cog, the plus and the minus were seen on an emulator on 2026-10-06.
- A card is only as wide inside as its widest line. A button that is to sit at the end of a card must be in a row that fills the width, or it lands in the middle of a card with short lines (seen on the emulator with the Settings screen's truck card).
- The bottom bar is always visible, on the pairing screen and the Settings screen too.
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
