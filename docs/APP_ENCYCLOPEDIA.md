# App Encyclopedia

> **What this is.** The complete *reference* for every capability the app has and exactly how it works — the "how does this actually behave?" book. When you (or your AI assistant) forget how a feature works, or before you change one, you read its entry here first. This is the single biggest defence against "I changed X and didn't realise it broke Y."
>
> **How to maintain it.** One entry per feature/capability, using the template below. **Update the entry in the same change that alters the feature** — a stale encyclopedia is worse than none, because it lies confidently. If behaviour changed and this didn't, the Definition of Done (STANDARDS §10) wasn't met.
>
> **Status:** Living document · **Last updated:** 2026-10-05 · **See also:** ARCHITECTURE.md (the map), FINDINGS_LOG.md (history of changes)

---

## How to read an entry

Each capability is documented with: what it does, who can use it, how it works step by step, where the code lives, what it depends on, edge cases, and which platforms it's on. Copy the template at the bottom for every new feature.

**Most entries are still `Planned`.** They record the founder's brief of 2026-10-03 plus the answers given at kickoff, so they describe required behaviour, not code. An entry marked `In progress` has a "How it works today" and a "Where the code lives" section. Those two sections describe only what exists; everything under "Required behaviour" that they do not mention is not built.

**What exists after the screens (work package 3):** storage, the trip rules as tested pure code, crash and kill capture, everything that records a trip once one is started (the trip controller, the foreground service, GPS recording, the notifications, the trip-start sound, the watch on Android Auto), everything that starts and ends one by itself (the Bluetooth receiver, the companion service, the boot and update receiver, the reading of the truck's connection, pairing with the truck), and five screens behind a bottom navigation bar: Home, Trips (one month at a time), Setup (the permission checklist, with the truck pairing screen opened from it) and Log (the event log). **None of the automatic part has met a real Bluetooth connection or the POCO X5 yet, and the screens of work package 3 have never been drawn anywhere:** they were built and proven with the build and unit tests only. What the phone has to prove is in `docs/DEVICE_TEST_CHECKLIST.md`.

**The app in one line:** MilO is a personal Android app (native Kotlin, one phone, no backend, no accounts, no Play Store) that automatically logs business mileage in Shawn's work truck and produces a monthly PDF to email to accounts.

**Target phone:** Xiaomi POCO X5 Pro 5G (global ROM), Android 14, HyperOS 2.0. Installed from Android Studio on a Mac mini.

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
- [Permission checklist](#permission-checklist) — phase 1 (the Setup screen)
- [Schedule and Business/Personal](#schedule-and-businesspersonal) — phase 2
- [Trip log: home, day and month views](#trip-log-home-day-and-month-views) — phases 1 and 2
- [Monthly PDF and submission](#monthly-pdf-and-submission) — phase 3
- [Diagnostics and reminders](#diagnostics-and-reminders) — phases 1 and 4
- [Backup, export and import](#backup-export-and-import) — phase 4
- [Settings](#settings) — grows each phase
- [Design system](#design-system) — live, grows as screens need it

---

## Truck pairing and trip detection

**Status:** In progress (phase 1): built, and not yet run against a real truck or on the phone; the pairing screen has never been drawn · **Platforms:** Android · **Last updated:** 2026-10-05

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
- **The pairing screen** (`feature/pairing/`) is opened from the Setup screen's truck row. It shows three cards. "Your truck": the stored truck and, in plain words, whether Android is watching for it, followed by how the attempt just made went. "Before you can pick the truck", only when something is in the way. "Paired with this phone": the phone's paired Bluetooth devices, named, each with its own button (Pair; Pair again on the stored truck; Use this one on another device once a truck is stored).
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
- Everything that can prompt a trip calls one function, `TripController.onTrigger(trigger, source)`. The callers are the Bluetooth receiver, the companion service, the reconcile receiver, `MiloApplication` (a reconcile at every process start), `MainActivity` (a reconcile every time MilO comes to the front), `HomeViewModel` (the Start and End buttons), `TruckPairing` (a truck was just stored) and the trip service (its timers).
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
- **Android's consent dialog has never been shown by any build of MilO,** and the pairing screen has never been drawn. Device checks 67 to 72 are the first time.
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
- If storage fails in the middle of an event, the controller logs the failure, forgets what it held in memory, and reads the state from storage again at once: a service that came up for a trip which could not be stored is stopped, and a trip that is still open in storage carries on. The fix or the event that failed is lost. If the hold-off could not be stored after End, the truck starts a new trip straight away. If storage fails that second time too, nothing more is tried until the next trigger.

---

## GPS recording and distance

**Status:** In progress (phase 1): fixes are recorded during a trip, and the trip's distance and its start and end coordinates are stored when it closes. Addresses (reverse geocoding) are not built · **Platforms:** Android · **Last updated:** 2026-10-03

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

**Where the code lives**
- `platform/trip/LocationRecorder.kt` (the request and the callback); `platform/trip/TripLedger.kt` (stores each fix, closes the trip).
- `core/trip/`: `DistanceCalculator.kt` (the four rules and the thresholds), `Haversine.kt`, `TrackPoint.kt`, `TripProgress.kt` (the running distance and the time of the last movement), `TripClosing.kt` (minimum distance, end time and positions).
- `data/point/`: `RawPoint.kt` (the table), `RawPointDao.kt`, `RawPointRepository.kt`; the database is `data/PointsDatabase.kt`.
- Tests: `app/src/test/.../core/trip/DistanceCalculatorTest.kt`, `DistanceCalculatorParkedTest.kt`, `DistanceCalculatorOutlierTest.kt`, `DistanceCalculatorTurnsTest.kt`, `HaversineTest.kt`, `TripProgressTest.kt`, `TripClosingTest.kt`.

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
- A trip under the minimum distance is marked `DISCARDED` and kept with its points, not deleted. If the distance was wrong, the trip can still be recovered.
- After a reboot in the middle of a trip the elapsed-realtime clock restarts, so the distance covered across the reboot cannot be checked and is not counted.
- The phone's own speed reading is stored but not used yet. It is the likely next refinement: for the parked-truck rule in place of stated accuracy (which would end the corner cutting), and to reject a leap from a fix that says it is standing still.
- If the phone's clock was wrong at the start of a trip and corrected during it, the distance is still right (points are cut by stored order, not by time), but the trip's stored start time is the wrong one and can even be later than its end.
- A trip can end with no signal, so geocoding must retry later.

---

## Trip-start sound

**Status:** In progress (phase 1): the sound plays at every trip start; the setting to choose another file is not built · **Platforms:** Android · **Last updated:** 2026-10-05

**What it does**
Plays a short R2-D2 style droid chirp once, when the app has connected to the truck and is ready to track the trip. It is Shawn's audible confirmation that detection worked.

**Required behaviour**
- Plays once per trip start, at the moment recording has really begun. No sound on trip end.
- Comes from the phone speaker and behaves like a notification sound: silent when the phone is on vibrate or Do Not Disturb.
- Played by the trip service itself, not attached to a notification channel, because MIUI is reported to switch channel sounds off by default.
- The sound committed to the repository is an original synthesized droid-style chirp. The film recording is copyrighted, so it is never committed.
- **Shawn's own clip replaces it in builds made on his Mac.** A file placed at `app/src/debug/res/raw/trip_start_chirp.<ext>` overrides the bundled chirp in debug builds, which are the builds installed on the phone. That folder is git-ignored, so the clip stays on his Mac and his phone and never reaches GitHub. Since 2026-10-05 it holds his R2-D2 clip (4.5 seconds). CI and any other machine build with the synthesized chirp. The event log says "playing the bundled chirp" either way, because it is the sound built into that build.
- A setting to choose any audio file on the phone, without rebuilding, is still planned with the settings screen.

**How it works today**
1. When the controller has opened a trip row, with the service already in the foreground, it tells the service that a trip has just started. That happens once per trip: not when a trip is picked up after a restart, and not when the grace period is cancelled. One exception: a trip opened by the companion "appeared" callback alone is not yet known to be a trip. Its sound plays when the truck's connection is confirmed, and a false start makes no sound at all (`TripTransition.tripReallyBegan`).
2. The service takes down any "could not start this trip" notification, reads the settings, and if the sound is switched on calls `TripStartSound.play`.
3. The sound is played with `MediaPlayer` and notification-type audio attributes, so the phone plays it at the notification volume and mutes it on silent, on vibrate and in Do Not Disturb. The player asks for the phone's built-in speaker as its preferred output.
4. If the settings hold a custom sound, that is played. If it cannot be opened, or fails while preparing or playing, the bundled chirp is played instead and the event log says why.
5. The bundled chirp is `res/raw/trip_start_chirp.wav`: 1.4 seconds of sine sweeps, warbles and short beeps, generated by `tools/make_trip_start_chirp.py`. The script is committed and produces the same file every time; the build does not run it.

**Where the code lives**
`platform/trip/TripStartSound.kt`; the call is in `platform/trip/TripService.kt`. The script is `tools/make_trip_start_chirp.py`. The two settings (`sound_enabled`, `custom_sound_uri`) are in `data/settings/SettingsStore.kt`.

**Edge cases & gotchas**
- Nothing can set the custom sound yet: the picker arrives with the settings screen. The research recommends copying the chosen file into the app's own storage at that moment, because access to the original is lost when it is moved or deleted.
- The phone's speaker is a preference, not a guarantee: Android may route the sound elsewhere. Where it comes out with the truck connected is a check on the phone.
- The sound does not lower music that is playing and does not check for a phone call. The research suggests both; neither was asked for.
- Never heard on the POCO X5 yet, and whether HyperOS mutes notification-type audio on vibrate the way stock Android does is unconfirmed.
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
- A manually started trip still respects the schedule: it is classified Business or Personal by its start time, the same as an automatic one.

**How it works today**
The home screen has one button. With no trip open it says "Start trip" and sends the `MANUAL_START` trigger; with a trip open it says "End trip" and sends `MANUAL_END`. Both go through `HomeViewModel` to `TripController.onTrigger`, the same function every other trigger calls, so a manual trip is recorded, closed and logged exactly like an automatic one. Each press reads the truck's connection first; if it cannot be read, the press goes by what was last believed. A manual trip the truck never joins ends on End trip, or after 30 minutes without movement.

**Where the code lives**
`feature/home/HomeScreen.kt` and `HomeViewModel.kt`. The rules for a manual trip are in `core/trip/` (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection)).

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
- The rule "do not end the trip while Android Auto is connected" does not depend on this screen and works either way. That rule is built: `platform/car/AndroidAutoWatcher.kt` watches `CarConnection` during a trip (see [Truck pairing and trip detection](#truck-pairing-and-trip-detection), rule 13). The screen itself is not built.

---

## Permission checklist

**Status:** In progress (phase 1): built as the Setup screen; never run on a phone or an emulator · **Platforms:** Android · **Last updated:** 2026-10-05

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
- **Nothing here has run on a phone.** Whether the Autostart reading answers on this phone, whether each HyperOS button finds its screen on HyperOS 2, and whether `isPowerSaveMode` and the "pause if unused" reading follow Xiaomi's own switches are device checks 54 to 66.
- Whether HyperOS's quick settings panel takes the focus from MilO's window, as stock Android's does, is untested. If it does not, a setting changed there shows only after MilO is left and opened again (device check 59).
- Whether Android's "Restricted" can be reached on HyperOS at all, and whether Battery saver "No restrictions" is what lifts it, is unknown (device checks 22 and 61). The sentence names the one battery setting the HyperOS app page has.
- **Two rows of the brief are not there.** Physical activity belongs to the driving alert (phase 2), and nothing uses it yet. The companion background permissions are granted at install and cannot be taken away, so they have no row; what can go wrong with the companion association is the truck row.
- The Autostart reading is known to say "on" for a switch that is off on some phones, and always says "on" while MIUI optimisation is disabled. "Looks on" therefore proves nothing; the with and without Autostart drive (device checks 36 to 43) does.
- A confirmation by hand never overrides an Autostart that reads as off. If the reading were wrong in that direction on this phone, the warning could not be cleared; that would be a finding to act on.
- The confirmations are Shawn's word, and HyperOS is reported to reset these settings after updates and reboots. A confirmation does not expire. The date is shown so that an old one can be doubted.
- On the POCO the home screen's warning stays until the Battery saver row and the recents lock are confirmed, even with every permission granted.
- Android's battery exemption and HyperOS's "No restrictions" are treated as two settings. The research could not settle whether either sets the other.
- If the settings file cannot be read, the rows are still shown, without the confirmations, and an `ERROR` line is logged.
- If Android refuses one of the readings, an `ERROR` line with the exception is logged and the rows stay as they were. Should that happen at the very first reading, Setup stays on "Reading the phone's settings…"; the Log says why.
- The settings screens open as a task of their own. Back returns to MilO.

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

**Status:** In progress: the home screen shows the trip in progress, and the Trips screen lists one month at a time (times and km only). Never run on a phone. The rest is phase 2 · **Platforms:** Android · **Last updated:** 2026-10-05

**What it does**
Shows what was recorded and lets Shawn correct it.

**Required behaviour**
- Home screen: current trip status, plus today's sessions (count, km each, total km, total drive time).
- Day view: each session with start and end time, start and end address, km, and Business/Personal.
- Month view: sessions and business km per day, plus month totals.
- Shawn can edit any trip, delete trips, and manually add a missed trip. Manually added or edited trips are marked as such.
- There is no purpose or note field. Dropped at kickoff.

**How it works today**
*Home* (`feature/home/HomeScreen.kt`). While the setup checklist needs attention, a card "Setup needs attention" with a button to Setup (see [Permission checklist](#permission-checklist)). Then one card, "Current trip": with no trip open, "No trip in progress"; with one, the distance so far, "Trip in progress" (or that it is waiting for the truck to reconnect) and the time it started. It reads `TripController.activity` through `HomeViewModel` and keeps no copy. Below the card, if the last start failed, a second card lists why. Then the Start trip / End trip button. Today's sessions are not built.

*Trips* (`feature/trips/`), the second button of the bottom bar. Shawn asked for it by name on 2026-10-05: "a trip button to view previous and current months trips".
1. It opens on the current month, every time it is entered. At the top, one card: the month's name and year, the total km, the number of trips, and two buttons, Previous month and Next month. Next month is greyed out on the current month: the screen never goes past it. Back is one month at a time, without limit.
2. Under it a switch, "Show discarded trips", off by default.
3. Then the trips, one card per day, newest day first and newest trip first within a day. Each trip shows its start time, its end time and its km.
4. **What is counted.** The total and the number of trips are of the finished trips only. A trip discarded as too short (or as a false start) is left out of the list and the totals; a line says how many are hidden. With the switch on they are listed in their day, marked "Discarded … Not counted" with their km greyed, and the totals do not change. This is how a real trip that was wrongly discarded can be spotted during testing.
5. **A trip in progress** is shown in a card of its own above the days, "In progress", with its start time and its running distance, and is not in the total until it ends. The running distance comes from the trip controller, because the stored row holds 0 while a trip is open; if the controller is not recording that trip, no figure is shown.
6. **Which day and month a trip belongs to.** The day and the month it started in, in the phone's time zone. A trip that runs past midnight is listed once, under the day it started.
7. An empty month says "No trips in (month and year)."
8. **Reading.** Only the month on screen is read: `TripRepository.observeTripsStartedBetween` takes a span of time, and `monthSpan` in `core/util/TimeSpan.kt` gives the span of a month in a time zone (from local midnight on the 1st up to, not including, local midnight on the next 1st, so a month in which the clocks change is an hour longer or shorter). The list follows the database: a trip that closes while the screen is open appears by itself. Which month is the current one is worked out again each time the screen comes to the front.
9. The sums and the grouping are pure functions in `feature/trips/TripMonth.kt` (`monthSummary`, `stepMonth`, `canStepForward`).

**Where the code lives**
- `feature/home/HomeScreen.kt` and `HomeViewModel.kt`.
- `feature/trips/`: `TripsScreen.kt` and `TripRows.kt` (the screen), `TripsViewModel.kt`, `TripMonth.kt` (what is counted and how it is grouped).
- `core/util/TimeSpan.kt` (a month as a span of time; the day and month of a stored time) and `TimeFormat.kt` (the month heading, the day heading, the time of day).
- `data/trip/TripDao.kt` and `TripRepository.kt` (the query by span of time).
- Tests: `app/src/test/.../feature/trips/TripMonthTest.kt`, `app/src/test/.../core/util/TimeSpanTest.kt` and `TimeFormatTest.kt`.

**Edge cases & gotchas**
- **The Trips screen has never been drawn.** Device checks 76 to 79.
- **The Trips screen has no send, share or export.** Sending a month or a date range to the accountant is [Monthly PDF and submission](#monthly-pdf-and-submission), which is not built.
- Addresses, Business/Personal, the day view, editing, deleting and adding a trip are phase 2. Until then every finished trip counts, whatever the time of day.
- The `trips` table has no index on the start time, so the query reads the whole table. At a few thousand rows a year that takes milliseconds. The index is a schema change and waits for the first migration (FINDINGS_LOG, 2026-10-05).
- A trip still recording at the turn of the month is shown in the month it started in, not in the new current month.
- The total is added up in metres and rounded once. The km of the single trips, each rounded to one decimal, can therefore add up to 0.1 more or less than the total shown.
- If the phone's time zone changes, the days and months are worked out again in the new zone the next time the screen comes to the front. A trip near midnight can then move to the neighbouring day.
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

**Status:** In progress (phase 1): the event log is stored; crashes and process kills are captured into it at every start, trip recording writes its evidence to it, and the Log screen shows it (never run on a phone). The rest is phase 4 · **Platforms:** Android · **Last updated:** 2026-10-05

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
   - `TRIP`: a trip starting; the truck being seen in it; a trip finishing or being discarded, with the distance, the reason and the fix counts; the hold-off being set and released, with the reason.
   - `GRACE`: the grace period starting and being cancelled. Its running out is the `TRIP` line "ended by GRACE_EXPIRED".
   - `SERVICE`: the service reaching the foreground and for which trigger; a start that failed and why, with the exception; the service being destroyed; a service lost in mid-trip, and the trip being dropped from memory when Android will not start it again; MilO being removed from the recent apps; the trip-start sound playing or failing.
   - `ANDROID_AUTO`: every change `CarConnection` reports, with the raw value, and the state before and after.
   - `LOCATION`: fixes being requested and stopped, the time to the first fix and its accuracy, and location being lost for 10 seconds or more and coming back. A shorter loss is not logged: the emulator reports one around every fix.
   - `ERROR`: a failure inside the controller, with its stack trace; an unreadable settings file.
   - `PAIRING`: the truck being paired or adopted, a pairing that failed and why, and every check of the pairing with its result. While no truck is armed, the line also lists the phone's paired devices with their addresses, names an association that was not adopted, and says if the phone has no companion device support.

7. **The Log screen** (`feature/eventlog/`), the last button of the bottom bar: the stored log, newest first.
   - Each line shows its time as `2026-10-05 08:14:03` (to the second, 24-hour, in the phone's own time zone, the same form in every language), its category by its stored name (`CRASH` and `ERROR` in red) and its message.
   - A line that has a detail says "Tap for details". Pressing it opens the detail under the line: the state before and after a trigger, a stack trace. Pressing again closes it. One line is open at a time.
   - **It is never read whole.** The newest 200 entries are read, through the index on the time. "Show older entries" at the bottom adds 200 more each time. The list is drawn lazily, so only the lines on screen are laid out. It opens as fast with ten thousand entries as with ten.
   - It follows the log: a line written while the screen is open appears at the top.
   - A setup button that could not open its HyperOS screen writes an `ERROR` line here (see [Permission checklist](#permission-checklist)).

Checked on an emulator (Android 16) on 2026-10-03: an induced crash, a force stop and a background kill each appeared in the log at the next start, once. On 2026-10-05 the same emulator showed the trigger lines of the companion service, the boot and update reconcile and the pairing check (FINDINGS_LOG). Not yet run on the POCO X5. The Log screen has not been run anywhere.

**Where the code lives**
- `feature/eventlog/`: `EventLogScreen.kt`, `EventLogViewModel.kt`. The time format is `formatLogTime` in `core/util/TimeFormat.kt`.
- `platform/diagnostics/`: `CrashHandler.kt`, `ProcessExitReader.kt`, `ProcessExit.kt`, `StartupDiagnostics.kt`.
- `data/crash/`: `CrashFileStore.kt` (the crash files and where they are kept).
- `data/eventlog/`: `EventLogEntry.kt` (the table and the categories), `EventLogDao.kt`, `EventLogRepository.kt`.
- `app/MiloApplication.kt` starts both; `app/AppContainer.kt` builds them.
- The trip lines are written by `platform/trip/TripWorker.kt` and worded in `platform/trip/TripLogText.kt` and `TripLedger.kt`. The source of a Bluetooth or companion trigger is worded in `platform/bluetooth/TruckSignals.kt`, and the pairing lines in `platform/bluetooth/TruckPairing.kt` and `TruckPairingCheck.kt`.
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

**Status:** In progress (grows each phase): the phase 1 values are stored; there is no settings screen yet · **Platforms:** Android · **Last updated:** 2026-10-05

**Required behaviour**
- Truck: the paired device, with a way to change it.
- Trip: disconnect grace period (default 2 minutes), minimum trip distance (default 0.3 km), trip-start sound (bundled chirp or a chosen audio file).
- Schedule: tracked days and hours, and whether out-of-schedule trips are saved as Personal or ignored.
- Report: name, company, vehicle description, accounts email. There is no rate-per-km setting.
- Reminder: day of the month.

**How it works today**
`SettingsStore` in `data/settings/` keeps the settings in a DataStore Preferences file and is the only way to read or write them. `settings` is a flow of `MiloSettings` that delivers the current values and every change; `current()` reads them once. A value never written reads as its default. Each setter refuses a value that cannot be right: a negative duration, distance or time, and a blank address, name or URI. "No truck name" and "no custom sound" are passed as null. If the file cannot be read, every read throws: the store never replaces it with empty settings.

Stored now: the truck's Bluetooth address, name and companion association id; the grace period (default 120 s); the minimum trip distance (default 300 m); the trip-start sound on or off (default on) and an optional custom sound; and values that are not Shawn's to choose but must outlive the process: the hold-off after a manual end, stored as the time End was pressed; how far the process-exit records have been imported; and the time at which he confirmed each step of the setup checklist that MilO cannot read (`ConfirmedStep`: HyperOS Autostart, Battery saver, Other permissions, the recents lock). A step that is not stored is not confirmed. Schedule, report and reminder settings are not stored yet.

Read today: the trip controller reads the grace period and the minimum trip distance at every trigger, so a changed value applies from the next event. It reads the stored hold-off only when it picks the state up from storage (after a process start, or after it dropped what it held in memory); from then on it keeps the hold-off in memory and writes every change; the trip service reads the two sound settings at each trip start. Besides the hold-off, the truck's three values are written, by `TruckPairing` (from the pairing screen, or when it adopts an association), and the confirmations, by the setup checklist. The truck's values are read by the Bluetooth receiver and the companion service at every event (to tell whether it is about the truck) and by every reading of the truck's connection. Nothing else is written yet, because there is no settings screen: the truck is changed on the pairing screen, which is reached from Setup.

**Where the code lives**
`data/settings/SettingsStore.kt` and `MiloSettings.kt` (the values and their defaults). Test: `app/src/test/.../data/settings/SettingsStoreTest.kt`.

---

## Design system

**Status:** Live · **Surfaces:** Phone · **Last updated:** 2026-10-05

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
  - `ScreenTitle`: the heading every screen starts with. Given an `onBack`, it shows a back arrow before the title; only a screen opened from another one (pairing) has it.
  - `SwitchRow`: a labelled on/off switch. The whole row is the target.
  - `MiloIcons`: the icons a screen may use: Home, Trips, Setup, Log, Back.
  - `CameToFrontEffect`: draws nothing. It calls the screen's ViewModel when the screen is first shown and every time Shawn is looking at it again, on either of two signs: the screen resumes (it is entered, or MilO returns from a settings screen, another app or one of Android's dialogs), or MilO's window gets the focus back (the quick settings panel or the notification shade closing, which resumes nothing). Returning from a settings screen gives both signs, so what it calls must be safe to call twice. The rule for the second sign is a plain function with a unit test (`focusRegained`); the effect itself cannot be tested off the phone. Added on 2026-10-05, after the review of the screens.
- **Patterns the screens share,** to be followed by the next ones:
  - A screen starts with `ScreenTitle` and pads its content with `MiloTheme.spacing.medium`. A screen of fixed content is a scrolling `Column` of `SectionCard`s; a screen of stored rows (Log, Trips) is a `LazyColumn`.
  - While a screen's first read is under way it shows one line of text ("Reading…"), and an empty list says so in a sentence. Neither is a component.
  - A problem and the way to fix it is a `StatusRow` with a button, on Home, Setup and the pairing screen alike.
  - A screen that shows something Android reports no changes of (a permission, a setting, the paired devices, which month is the current one) reads it again through `CameToFrontEffect`, never through a lifecycle effect of its own.
  - A ViewModel is handed plain functions for navigation; a feature never imports another feature.

**Edge cases & gotchas**
- No colour, dp or sp value may be written outside `core/designsystem/`. The only exceptions are the two launcher-icon drawables, which the launcher reads before Compose exists, and `drawable/ic_stat_trip.xml`, the notification icon, which Android draws itself outside Compose.
- Notifications are not Compose and cannot use these components. Their text is in `strings.xml`.
- The icons are built from path data in `MiloIcons.kt` and `StatusIcons.kt`, not taken from the material-icons library, which is frozen. A unit test proves that each one parses. **None of the icons added on 2026-10-05 has been seen on a screen:** device check 52.
- The bottom bar is always visible, on the pairing screen too.
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
