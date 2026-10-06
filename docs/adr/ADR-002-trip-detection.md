# ADR-002: Trip detection and background start

Date: 2026-10-03
Status: Accepted (design). Amended 2026-10-03, twice (after the review of the phase 1 foundation, and when the recording core was built), and 2026-10-05, three times (when the triggers were built, after the review of the recording core and the triggers, and when the screens were built). The changes are listed under "Amendments" at the end. Nothing here is confirmed on the phone yet; the device test checklist in phase 1 is what proves or changes it.

## Context

MilO must start recording when the phone connects to the truck over classic Bluetooth, even when the app's process is dead, and end the trip after the truck disconnects. Shawn's current mileage app often misses trips, so reliable start is the top requirement. The target is one phone: a Xiaomi POCO X5 on Android 14 (HyperOS). The app targets API 37, but the behaviour that runs is Android 14's.

The research in `docs/research/` settled these facts. Each is one line here; the file named holds the evidence.

1. **Two separate permissions are needed to start from the background.** A background-start exemption lets the service start; "Allow all the time" location lets it read location. On Android 14, starting a location foreground service from the background without background location throws `SecurityException`. (`fgs-background-start`)
2. **Three exemptions can allow the start.** A companion device association with `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (documented, lasts as long as the association). The battery-optimisation exemption (documented). A 20-second allowance the Bluetooth stack attaches to its connect broadcast (real in AOSP 12 to 14, not on the documented list). (`fgs-background-start`, `miui-dev-bluetooth-audio`)
3. **CompanionDeviceManager and the ACL broadcast are one detector with two delivery paths.** Both come from the same Bluetooth-stack event. What the companion path adds is that the system binds MilO's service directly, which starts a dead process on stock Android. (`cdm-presence`)
4. **A second, independent detector exists.** The hands-free and audio profile broadcasts (`BluetoothHeadset` and `BluetoothA2dp` `ACTION_CONNECTION_STATE_CHANGED`) may also be manifest-registered and come from a different part of the Bluetooth stack. (`cdm-presence`)
5. **Events are hints, not truth.** Companion callbacks can arrive in reverse order on a cold start, a quick drop-and-reconnect can deliver only one of the two broadcasts, and no event fires at all when the truck is already connected at boot, after an app update, or after the process is restarted. (`cdm-presence`, `fgs-background-start`)
6. **Android 14 has no public "is this device connected" call.** The available check is the hands-free and audio profile state (`getProfileConnectionState`, then a profile proxy's `getConnectedDevices()`). (`cdm-presence`)
7. **Receivers for Bluetooth broadcasts must be exported.** The sender is the Bluetooth app, not the system. A non-exported receiver never hears the disconnect and the trip never ends. (`fgs-background-start`)
8. **HyperOS Autostart gates every start of a dead app.** Decompiled system code shows the connect broadcast dropped and service starts and binds rejected while Autostart is off, and it is off by default for a sideloaded app. Unconfirmed on this phone. (`miui-background-limits`)
9. **Xiaomi's cleaners kill idle apps, and force stop leaves the app dead until it is opened.** The exit reason the system records names the cleaner. (`miui-background-limits`)
10. **`CarConnection` only works while MilO's process is alive.** It cannot wake the app, so it can hold a trip open but never start one. (`location-and-car`, `android-auto-screen`)
11. **After a reboot nothing is delivered until the first unlock.** (`cdm-presence`)

## Decision

### One entry point, many triggers

Every trigger calls the same function on one app-wide `TripController`. The function is idempotent: calling it twice, or in the wrong order, leaves the same result. Triggers call it synchronously. Nothing goes through WorkManager, an alarm or a delayed job, because the start allowance is measured in seconds. A manifest receiver keeps its broadcast open until the controller has dealt with the trigger, so the process cannot be frozen between the two.

| Trigger | What it is | What it covers |
|---|---|---|
| Companion service | `CompanionDeviceService` callbacks for the associated truck, plus a check in its `onCreate` | Dead process on stock Android; the standing start exemption |
| Bluetooth receiver | Exported manifest receiver for ACL connect and disconnect and for the hands-free and audio profile state changes, filtered to the truck's address | The backup path, and the independent detector |
| Reconcile | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, process start, app launch (every time MilO comes to the front), service restart, a truck just paired | Every case where no event fires |
| Manual | Start and End on the phone and on the Android Auto screen | Bluetooth never connected at all |

The driving alert (phase 2) only notifies. `CarConnection` only holds a trip open. Neither starts a trip.

### What counts as "the truck is connected"

- A connect event that carries the truck's address is trusted as it stands and starts the trip at once. The profiles connect a few seconds after the link, so checking them first would miss the start window.
- A companion "appeared" callback also starts the service at once. The service then confirms within 15 seconds that the truck really is connected (profile state, or an ACL connect seen for the address). If not, it stops, and the trip it had just opened is marked discarded as a false start, with the reason in the event log. It does not go through the grace period. A reading of "not connected" taken before the 15 seconds are up proves nothing, because the profiles connect after the link.
- The companion callback is believed only when no trip is open. It never marks the truck as seen in a trip that is already running: believed wrongly there, it would end a manual trip on a disconnect that never happened. With a trip open it does one thing only: it releases a hold-off that is more than 60 seconds old, and what is believed about the truck stands.
- Reconcile has no event to trust, so it reads the truck's connection: the hands-free and audio profile state up to Android 16, the link itself (`BluetoothDevice.isConnected`) from Android 16 QPR2.
- Only the link (ACL) broadcast of the classic transport is a connect or disconnect event. A hands-free or audio profile changing state, and a low-energy link to the truck's address, are prompts to read the connection: a profile can reconnect on a link that never dropped, and a low-energy link says nothing certain about the hands-free connection.
- A reading can come back "unknown": MilO is not allowed to use Bluetooth, or Bluetooth did not answer. Unknown is never "connected". A reconcile or a once-a-minute reading that comes back unknown changes nothing. A timer or a button has to act, so it goes by what is already believed about the truck. At a process start nothing is believed yet, and unknown counts as "not connected".
- A disconnect event starts the grace timer. When the timer ends, the profile state is read again before the trip is closed.
- While a trip is open, the service reads the truck's connection again about once a minute, to catch a disconnect that was never reported. Two readings of "not connected" in a row are treated as a disconnect and start the grace period. One reading alone is not enough, because the profile state can lag behind the link. A reading of "connected" is always believed.
- A reading of "not connected" counts against a link-level connect event only once a reading has shown the truck connected on that link. Until then it proves nothing, whether it was prompted by the minute timer, by a reconcile or by a profile broadcast: the profiles connect seconds after the link, and a truck can be connected without either of the two profiles a reading looks at. Such a reading is written to the event log and changes nothing. A disconnect event is still trusted as it stands.
- Only something that brings a fresh reading of the truck closes a trip whose time has run out: that reading, a connect or disconnect event, or a press of Start or End (each press reads the connection first). A GPS fix or an Android Auto change that arrives after the deadline knows nothing new about the truck, so it closes nothing. The trip waits for the reading.

### Trip rules

The rules are a pure Kotlin state machine in `core/trip/`, with unit tests and no Android types, so they can be tested without a phone.

- **Idle, truck connects:** a trip starts.
- **Recording, truck disconnects:** if Android Auto is still connected the trip carries on; otherwise the grace period starts (default 2 minutes).
- **Grace, truck reconnects:** the same trip carries on.
- **Grace ends:** the trip is closed at the time of the last recorded point, not at the end of the grace period.
- **A reconnect seen after the grace period has ended does not revive the trip.** The trip is closed as above and the truck starts a new one. The reading taken when the timer fires always arrives a little after the deadline, so a "connected" reading up to 30 seconds late still carries the same trip on. Later than that, the timer was lost (the process was frozen or killed) and the trip ended when its grace ran out.
- **Connect while recording, or disconnect while idle:** nothing happens. Events are never assumed to come in pairs.
- **Manual start:** starts a trip when idle. If the truck connects during it, it then behaves like an automatic trip.
- **Manual start during the grace period, truck still gone:** the waiting trip ends where the truck was found gone, and a manual trip starts. Otherwise the press would be swallowed and nothing would be recording once the grace period ran out.
- **Manual end while the truck is still connected:** the trip ends and automatic start is held off. Without this, the next reconcile would start a new trip straight away. The hold-off is released by whichever comes first: (a) any reading that shows the truck disconnected; (b) a link-level connect event (the ACL connect broadcast, or the companion "appeared" callback) that arrives more than 60 seconds after End was pressed; (c) 12 hours passing. A missed trip is worse than an unwanted restart.
- **A manual trip with no truck connected** ends on End Trip, or after 30 minutes without movement, so a forgotten one cannot run all night. This guard applies only to manual trips. Bluetooth trips are never split on stops, as Shawn decided.
- **A finished trip under the minimum distance** (default 0.3 km) is discarded, and the discard is written to the event log.

### State survives the process

The open trip is a row in the database with its status. On any restart the controller reads it, together with a fresh reading of the connection. A trip is carried on only if it can still be the same drive:

- **The trip was in its grace period.** It is judged by its stored deadline, exactly as above: over means closed at the last point, whatever is connected now; otherwise truck connected carries the same trip on, and truck gone resumes the grace timer.
- **The trip was recording.** If its newest stored point is more than 30 minutes old, nobody watched the truck in between, and the trip is closed at that point. Otherwise truck connected means carry on recording the same trip, and truck gone starts the grace period now.

In every case where the old trip is closed and the truck is connected, a new trip starts in the same step. Without these two limits, a process killed in the afternoon and woken by the truck the next morning would join both days into one trip.

### The service

`TripService` is a foreground service of type `location`.

1. The trigger runs a preflight: fine and background location granted, Bluetooth permission granted, location switched on, app not background-restricted.
2. If preflight fails, the service is not started. MilO posts a "could not start this trip, tap to start" notification and writes the reason to the event log. A tap on a notification is itself allowed to start the service.
3. Otherwise it calls `startForegroundService`. The first statement in `onStartCommand` is `startForeground` with the location type, inside a catch for `ForegroundServiceStartNotAllowedException` and `SecurityException`. A failure is logged and falls back to the same notification.
4. Only then does it request location, open or resume the trip row, register its own exported receiver for disconnect, observe `CarConnection`, and play the trip-start sound.
5. The grace timer and the once-a-minute reading of the truck's connection run inside the service. A GPS fix stands in for either when its timer is late: for a deadline once it is 10 seconds overdue, and for the minute reading once 70 seconds have passed since the last one. An overdue deadline is dealt with before the fix that revealed it is counted.

Step 4 is kept by the controller, not by the service: an event that would leave a trip open is not acted on while the service is not in the foreground. The service is asked to start with the trigger in its start intent, and it hands the trigger back to the controller once `startForeground` has succeeded. If Android refuses, no trip row is opened.

### Permissions declared

`BLUETOOTH_CONNECT`, fine, coarse and background location, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Not needed: `BLUETOOTH_SCAN`, exact alarms, internet. One more permission is declared that trip detection does not use: `ACCESS_NETWORK_STATE`, for the address lookup (amendment 26).

All are in the manifest. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` was the last to be added, on 2026-10-05 with the permission checklist (the Setup screen): it only allows MilO to ask for the exemption, and the checklist's battery row is the one place that asks (STANDARDS §12: declare only what built code uses).

Both companion permissions are declared on purpose. On Android 12 to 15, an app with an association but without `REQUEST_COMPANION_RUN_IN_BACKGROUND` is removed from the battery allowlist on every reinstall.

### Pairing

Onboarding lists the phone's paired devices, Shawn picks the truck, and MilO associates it using an address filter with single-device mode and no device profile. That combination is the only one that matches an already-paired device without a scan. MilO then starts observing presence. At every process start and every time it is opened, it checks the association still exists and re-arms observation.

The request to associate goes through the pairing screen's Activity: Android shows its consent dialog on top of it, and up to Android 12 the request fails from any other context. On a phone without companion device support the truck is stored anyway, so the Bluetooth receiver knows which device to listen for. Only the pairing screen can do that: without an association there is nothing to adopt (amendment 19).

### Evidence from day one

Because none of this is proven on the phone, phase 1 writes to the event log:
- every trigger, with its source, the state before and after, and whether the service started. Two things are left out, because they arrive all day and usually change nothing: a GPS fix, and a once-a-minute reading that only confirms what is already believed. Either is logged when it does change something;
- every service start, stop and failure, with the exception;
- every process start, with the reason the last process died (which names Xiaomi's cleaner if one killed it);
- every grace timer start, cancel and expiry, and every Android Auto connection change.

### Where the code lives

- `core/trip/`: the state machine, the distance calculation and the point filter. Pure Kotlin.
- `platform/trip/`: `TripController`, `TripService`, location recording, notifications, the sound.
- `platform/bluetooth/`: the receiver, the companion service, association, the connection check.
- `platform/car/`: the Android Auto classes. The screen reads the controller's state directly and has no ViewModel. The `CarConnection` watcher is here too.
- `platform/system/`: permission and Xiaomi settings checks.
- `data/`: the databases, repositories and settings.

Phone screens reach the controller through their ViewModels.

## Consequences

**Better**
- No single trigger is a point of failure, and a missed trip leaves a record of which trigger did or did not fire.
- The trip rules can be tested exhaustively without a phone.
- A killed process picks its trip back up.

**Worse**
- Autostart on HyperOS is a hard prerequisite that MilO can only partly verify. If it is off, every automatic path fails together. The permission checklist is the only defence.
- More code than a single receiver: three trigger classes, a reconcile step and a state machine.
- `minSdk` is 31, so the companion and notification code needs branches for Android 12 and 13 that this phone will never run. Raising `minSdk` to 34 would delete them. That is Shawn's call; the brief says 31.

**Deferred, decided by the phase 1 test drives**
- **An always-on service.** If starting from a dead process proves unreliable even with Autostart on, the fallback is a small permanent foreground service that keeps the process alive and listens in code. It costs a permanent notification and some battery, so it needs Shawn's agreement and is not built now.
- **Activity recognition as an extra wake path.** Its delivery uses an explicit target, which may get past the Autostart gate. Untested.

**Open, and assumed for now**
- A trip that begins before the first unlock after a reboot starts recording at unlock.
- Reverse geocoding of start and finish addresses was to arrive in phase 2 with the day view. It was built on 2026-10-05, on its own (amendment 26); the day view is still phase 2.

## Amendments

**2026-10-03, review of the phase 1 foundation.** Four points, each written into the text above. The reasons and the numbers are in FINDINGS_LOG under the same date. The two limits are judgements made without Shawn, and his to change.

1. **Only a fresh reading of the truck closes a trip on a timeout.** The first code closed an overdue grace period on any event, including a GPS fix. A fix handled a moment before the timer's reading then cut one drive in two. This is what "read again before the trip is closed" always meant; it is now a rule of the state machine.
2. **A late reconnect does not revive a trip.** "Grace ends: the trip is closed" and "truck connected means carry on" contradicted each other when the timer was lost. The first now wins, with a 30-second tolerance for a timer that fires late.
3. **A restart long after the last recorded point closes the trip.** "Truck connected means carry on" had no time limit. The limit is 30 minutes.
4. **Start during the grace period is not swallowed.**

**2026-10-03, the recording core (work package 2, part A).** Points 5 to 7 were decided by the orchestrating session before the code was written and are written into the text above. Points 8 to 10 were decided while building, because the text above did not settle them. All are Shawn's to change; the reasons and the numbers are in FINDINGS_LOG under the same date.

5. **What releases the hold-off.** "Until the truck next disconnects" swallowed the next trip whenever that disconnect was never seen (the app was dead). Now three things release it, whichever comes first: a reading that shows the truck disconnected; a link-level connect event more than 60 seconds after End was pressed; 12 hours. A new link can only form after the old one dropped, so the connect event proves the disconnect nobody saw. The 60 seconds ignore late duplicates of the connect event from the connection that was already up. The 12 hours are applied at the next reading of the truck, so that the trip they allow starts on a fresh reading. Pressing End again with the truck connected restarts the hold-off from that press.
6. **Catching a lost disconnect.** While a trip is open the service reads the truck's connection about once a minute. Two readings of "not connected" in a row start the grace period.
7. **A false start from the companion callback.** A trip opened by the "appeared" callback alone is discarded if the truck's connection is not confirmed within 15 seconds, by a link-level connect event for the truck or by the profile state. The service stops at once: no grace period.
8. **Android Auto's "connected" is not believed for ever.** The value comes from the Android Auto app and nothing guarantees it ever goes back. Two guards: while Android Auto is believed connected the service makes the library ask again once a minute; and once Android Auto alone (the truck gone) has held a trip open for 12 hours it is taken for stuck and no longer believed, until it has been seen to report "not connected". 12 hours, so that a real working day on Android Auto with Bluetooth down is never cut.
9. **The preflight is the same for every start.** Background location is required even for a press of Start with the app open, which Android itself would allow without it. A phone on which only manual trips work should say so at once. The Bluetooth permission joins the preflight with the Bluetooth code.
10. **A tap on the "could not start" notification starts the service directly,** with no preflight: the tap is one of the moments Android allows the start. If it still fails, the same notification is posted again.

**2026-10-05, the triggers (work package 2, part B).** Decided while building the Bluetooth receiver, the companion service, pairing and the connection check, because the text above did not settle them. Points 11 to 13 are written into the text above. All are Shawn's to change; the reasons are in FINDINGS_LOG under the same date.

11. **A reading of the truck can be "unknown", and unknown is never "connected".** Without the Bluetooth permission, or when a Bluetooth profile does not answer, the phone cannot say. What a trigger does with it: a reconcile or a once-a-minute reading changes nothing (a trip does not start on it, and a trip that is recording is not put into its grace period by it); a timer or a button goes by what is already believed, so a grace period still runs out and a companion start that nothing confirmed is still a false start; a process start, where nothing is believed yet, counts it as "not connected".
12. **Only the classic link broadcast is a connect or disconnect event.** The hands-free and audio profile broadcasts are the independent detector, and they prompt a reading. Trusted as link events, a profile that reconnects on a link that never dropped would release the hold-off 60 seconds after End was pressed.
13. **A manifest broadcast is kept open until the controller has dealt with it,** for 8 seconds at most. A trigger that needs a reading first is handled a moment after `onReceive` returns, and a process with nothing else running may be frozen in between.
14. **The companion "disappeared" callback is trusted as a disconnect,** like the link broadcast. If it is wrong, the next reading inside the grace period cancels it. Android 16's "Bluetooth connected" presence event is still treated as "appeared" and has to be confirmed: Android does not say which transport connected.
15. **The reconcile at app launch runs every time MilO comes to the front,** not once per Activity. Android keeps an Activity for days, and opening MilO is what Shawn does when a trip did not start.
16. **A lone association with no truck stored is adopted as the truck.** In ordinary use the two are made together. It is what lets automatic start be tried on the phone before the pairing screen exists, with the association made over adb.
17. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` waits for the permission checklist,** the only code that will ask for the exemption. (Done: see 25.)

**2026-10-05, the review of the recording core and the triggers (work package 2).** Four reviewers read the code. Points 18 to 23 change the text above; point 24 only makes it exact. Point 18 changes a rule the orchestrating session had decided (amendment 6), so it is Shawn's to confirm first. The reasons are in FINDINGS_LOG under the same date.

18. **A reading of "not connected" proves nothing until a reading has shown the truck connected on its present link.** Amendment 6 counted every such reading of the minute timer. On Android 14 a reading sees only the hands-free and the audio profile. With a truck whose link is up but which is on neither profile, the first two readings ended every trip four minutes after it started, and nothing started the next one, because the link never dropped. The same reading taken by a reconcile seconds after the link was made (the profiles follow the link) put a trip into its grace period at once. Now neither counts until a reading has seen the truck. The price: on such a truck, and in the first moments of any trip, a disconnect that is never reported is not caught by a reading. A disconnect event still ends the trip. Device check 32 shows whether this truck is affected.
19. **Only a device paired with the phone is adopted, and an adopted truck can be replaced the same way.** Amendment 16 adopted any lone association. One mistyped digit in the adb command was then stored as the truck for good. Now the association's address must be in the phone's list of paired devices. And when the stored truck's own association is gone, a lone association for a paired device is adopted in its place: the way back from a wrong truck, and the way to pair another one, while there is no pairing screen.
20. **A GPS fix stands in for the minute timer too,** as it already did for a deadline, and the readings stay about a minute apart whichever of the two prompts them: two readings seconds apart would be worth no more than one.
21. **An overdue deadline is judged before the fix that reveals it is counted.** Counted first, the first fix after a long silence moved the no-movement limit of a forgotten manual trip on, and the trip stayed open.
22. **The companion callback with a trip open leaves what is believed about the truck alone.** It released an old hold-off by way of "the truck is no longer known to be connected", which started the open trip's grace period.
23. **A trip nothing is recording is not kept in memory.** When the trip service is lost and Android refuses to start it again, the controller drops what it holds, and the next trigger picks the stored trip up through the restart rules, which close it if it has gone stale. After a failure in storage the controller reads storage again at once, for the same reason.
24. **A profile that drops can release the hold-off.** Amendment 12 keeps a profile that reconnects from counting as a new link. It does not stop the release by a reading: when the only connected profile drops, the reading it prompts says "not connected", which releases the hold-off as decided ("any reading that shows the truck disconnected"), and the profile's return then starts a trip. Unchanged, because a missed trip is worse than an unwanted restart; device check 47 shows whether it happens with this truck.

**2026-10-05, the screens (work package 3).** One point, written into the text above.

25. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared.** The permission checklist exists as the Setup screen, and its battery row asks for the exemption with Android's own dialog. This closes point 17.

**2026-10-05, the addresses (work package 4).** One point, written into the text above.

26. **Addresses are looked up beside the recording, never inside it, and `ACCESS_NETWORK_STATE` is declared for them.** Reverse geocoding was pulled forward from phase 2. `platform/address/TripAddresses` reads the controller's published state and writes only the address columns of a finished trip, so nothing in this record changes: no trigger, rule or effect knows about addresses. The one addition to the controller's side is that `CurrentTrip` carries the position of the trip's first usable fix. The lookup's pass at process start is asked for through the controller's existing `whenCaughtUp`, so that it follows the reconcile and finds a trip the restart rules have just closed; the controller only runs the callback. The permission lets the lookup ask whether the phone is online before it asks the geocoder; it is granted at install, and MilO still needs no internet permission (APP_ENCYCLOPEDIA, "GPS recording and distance"; FINDINGS_LOG, 2026-10-05).
