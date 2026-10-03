# ADR-002: Trip detection and background start

Date: 2026-10-03
Status: Accepted (design). Amended 2026-10-03 after the review of the phase 1 foundation; the changes are listed under "Amendments" at the end. Nothing here is confirmed on the phone yet; the device test checklist in phase 1 is what proves or changes it.

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

Every trigger calls the same function on one app-wide `TripController`. The function is idempotent: calling it twice, or in the wrong order, leaves the same result. Triggers call it synchronously. Nothing goes through WorkManager, an alarm or a delayed job, because the start allowance is measured in seconds.

| Trigger | What it is | What it covers |
|---|---|---|
| Companion service | `CompanionDeviceService` callbacks for the associated truck, plus a check in its `onCreate` | Dead process on stock Android; the standing start exemption |
| Bluetooth receiver | Exported manifest receiver for ACL connect and disconnect and for the hands-free and audio profile state changes, filtered to the truck's address | The backup path, and the independent detector |
| Reconcile | `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, app launch, service restart | Every case where no event fires |
| Manual | Start and End on the phone and on the Android Auto screen | Bluetooth never connected at all |

The driving alert (phase 2) only notifies. `CarConnection` only holds a trip open. Neither starts a trip.

### What counts as "the truck is connected"

- A connect event that carries the truck's address is trusted as it stands and starts the trip at once. The profiles connect a few seconds after the link, so checking them first would miss the start window.
- A companion "appeared" callback also starts the service at once. The service then confirms within 15 seconds that the truck really is connected (profile state, or an ACL connect seen for the address). If not, it stops quietly and logs a false start.
- Reconcile has no event to trust, so it reads the profile state.
- A disconnect event starts the grace timer. When the timer ends, the profile state is read again before the trip is closed.
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
- **Manual end while the truck is still connected:** the trip ends and automatic start is held off until the truck next disconnects. Without this, the next reconcile would start a new trip straight away.
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
5. The grace timer runs inside the service.

### Permissions declared

`BLUETOOTH_CONNECT`, fine, coarse and background location, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`, `REQUEST_COMPANION_RUN_IN_BACKGROUND`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Not needed: `BLUETOOTH_SCAN`, exact alarms, internet.

Both companion permissions are declared on purpose. On Android 12 to 15, an app with an association but without `REQUEST_COMPANION_RUN_IN_BACKGROUND` is removed from the battery allowlist on every reinstall.

### Pairing

Onboarding lists the phone's paired devices, Shawn picks the truck, and MilO associates it using an address filter with single-device mode and no device profile. That combination is the only one that matches an already-paired device without a scan. MilO then starts observing presence. On every launch it checks the association still exists and re-arms observation.

### Evidence from day one

Because none of this is proven on the phone, phase 1 writes to the event log:
- every trigger, with its source, the state before and after, and whether the service started;
- every service start, stop and failure, with the exception;
- every process start, with the reason the last process died (which names Xiaomi's cleaner if one killed it);
- every grace timer start, cancel and expiry, and every Android Auto connection change.

### Where the code lives

- `core/trip/`: the state machine, the distance calculation and the point filter. Pure Kotlin.
- `platform/trip/`: `TripController`, `TripService`, location recording, notifications, the sound.
- `platform/bluetooth/`: the receiver, the companion service, association, the connection check.
- `platform/car/`: the Android Auto classes. They read the controller's state directly and have no ViewModel.
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
- Reverse geocoding of start and finish addresses arrives in phase 2 with the day view. Phase 1 stores coordinates.

## Amendments

**2026-10-03, review of the phase 1 foundation.** Four points, each written into the text above. The reasons and the numbers are in FINDINGS_LOG under the same date. The two limits are judgements made without Shawn, and his to change.

1. **Only a fresh reading of the truck closes a trip on a timeout.** The first code closed an overdue grace period on any event, including a GPS fix. A fix handled a moment before the timer's reading then cut one drive in two. This is what "read again before the trip is closed" always meant; it is now a rule of the state machine.
2. **A late reconnect does not revive a trip.** "Grace ends: the trip is closed" and "truck connected means carry on" contradicted each other when the timer was lost. The first now wins, with a 30-second tolerance for a timer that fires late.
3. **A restart long after the last recorded point closes the trip.** "Truck connected means carry on" had no time limit. The limit is 30 minutes.
4. **Start during the grace period is not swallowed.**
