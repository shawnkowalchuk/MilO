# Device test checklist

> **What this is.** The checks that can only be done on the phone itself: the Xiaomi POCO X5, Android 14 (HyperOS). Unit tests and the emulator cannot show what HyperOS does to a background app. Run every check here before a phase is handed over (STANDARDS §11), and write what happened into FINDINGS_LOG, pass or fail.
>
> **How it grows.** A change that adds behaviour only the phone can prove adds its checks here, in the same change. A check is removed only when the behaviour it covers is removed.
>
> **Status:** Living document · **Started:** 2026-10-03 · Nothing below has been run on the phone yet. What was run on an emulator is in FINDINGS_LOG. The screens of work package 3 (checks 52 to 79) have run nowhere at all.

---

## Before the first check

- The debug build is installed from the Mac (`./gradlew installDebug`, or Android Studio). HyperOS needs "Install via USB" and "USB debugging (Security settings)" switched on first (FINDINGS_LOG, 2026-10-03).
- **Never uninstall to fix a problem.** From phase 1 on the build holds real trips (STANDARDS §13).
- **Set the phone up on MilO's Setup screen** (the bottom bar, third button). It asks for each permission and opens each setting; checks 54 to 66 go through it row by row, and they are the first time that screen runs anywhere. Should the screen fail, the permissions can still be granted by hand. In the phone's settings: Apps, MilO, Permissions: Location set to "Allow all the time" with "Use precise location" on, Nearby devices allowed, and Notifications allowed. Or from the Mac:

```
adb shell pm grant com.shawnkowalchuk.milo android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.shawnkowalchuk.milo android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.shawnkowalchuk.milo android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm grant com.shawnkowalchuk.milo android.permission.POST_NOTIFICATIONS
adb shell pm grant com.shawnkowalchuk.milo android.permission.BLUETOOTH_CONNECT
```

Without Nearby devices no trip starts at all, not even with the Start trip button: the home screen then says "Bluetooth permission is missing".

**The order on a phone that has never been set up.** The sections below are grouped by what they prove, not by the order to run them the first time: checks 10 onward need the permissions, and checks 31 onward need the HyperOS settings and the paired truck as well, and all of that is set in checks further down. So the first time through:

1. Checks 1 to 8, at any point: they need no permission. Check 9 follows check 10.
2. Checks 52 and 53: getting around.
3. Checks 54 to 66: the Setup screen, row by row. They end with one thing still not set: the truck.
4. Checks 67 and 68: pair the truck. Only now does Setup say "Everything MilO needs is set".
5. Checks 10 to 30: a trip started by hand. Keep the truck switched off or out of range, except in the checks that name it (17 and 29): now that it is paired, connecting to it starts a trip by itself.
6. Checks 31 to 51: the truck starts and ends a trip. Checks 36 to 43 switch Autostart off on purpose; switch it back on afterwards.
7. Checks 69 to 79, in any order.

## Reading the event log

**On the phone:** the Log screen (the bottom bar, last button) shows the log newest first, with each line's time to the second. A line that says "Tap for details" opens when pressed: that is where the state before and after each trigger is. The screen itself is new and untested (checks 73 to 75).

**On the Mac,** for searching, for the trips and the GPS fixes, or if the screen fails: the log is read from a copy of the database. This is how it was read on the emulator; on the phone it is itself untested.

```
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db     > milo.db
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db-wal > milo.db-wal
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db-shm > milo.db-shm
sqlite3 milo.db "SELECT datetime(atMs/1000,'unixepoch','localtime'), category, message FROM event_log ORDER BY atMs, id;"
```

Copy all three files: the newest entries can still be in the `-wal` file. Keep the copies out of the project folder. Android Studio's Database Inspector (App Inspection) shows the same table without copying.

The state before and after each trigger is in the `detail` column: add `, detail` to the query to see it.

The trips are in the same file, and the GPS fixes in `points.db` (copy it the same way, with its `-wal` and `-shm`):

```
sqlite3 milo.db "SELECT id, datetime(startedAtMs/1000,'unixepoch','localtime'), datetime(endedAtMs/1000,'unixepoch','localtime'), status, startedBy, round(distanceMetres) FROM trips;"
sqlite3 points.db "SELECT tripId, count(*), round(avg(accuracyMetres),1) FROM raw_points GROUP BY tripId;"
```

Gaps between fixes of trip N, longest first (a fix is expected every 5 seconds):

```
sqlite3 points.db "SELECT (elapsedRealtimeMs - lag(elapsedRealtimeMs) OVER (ORDER BY id))/1000.0 AS gap FROM raw_points WHERE tripId = N ORDER BY gap DESC LIMIT 5;"
```

---

## Phase 1 foundation: crash and kill capture

These are the evidence for the reliability requirement, so they have to work on HyperOS before trip detection is tested.

| # | Do this | Expect in the event log after opening MilO again | Result |
|---|---|---|---|
| 1 | Open MilO. Run `adb shell am crash com.shawnkowalchuk.milo`. Open MilO again. | A `CRASH` entry dated at the crash, with a stack trace in its detail; a `PROCESS` entry "ended: CRASH"; a `PROCESS` entry "started". Each once. | not run |
| 2 | Open MilO, go to the home screen, swipe MilO away in recents. Open it again. | A `PROCESS` entry "ended: …" whose text in brackets says what stopped it. **Write the exact text here**: it is how a missed trip will be explained later. | not run |
| 3 | Open MilO, go home, press the recents clear-all button (the X). Open MilO again. | As check 2. The research expects the text to name a Xiaomi cleaner, such as `SwipeUpClean`. | not run |
| 4 | Open MilO, go home, run the Security app's cleaner ("Boost speed" or "Cleaner"). Open MilO again. | As check 2, with that cleaner's name. | not run |
| 5 | Settings, Apps, MilO, Force stop. Open MilO again. | A `PROCESS` entry "ended: USER_REQUESTED" (on the emulator the text was `[FORCE STOP] …`). | not run |
| 6 | Open MilO, leave the phone alone with the screen off for several hours, open MilO again. | Either no "ended" entry (it survived), or one that names what killed it. Write down which. | not run |
| 7 | After checks 1 to 6, open MilO twice more without doing anything else. | No entry from checks 1 to 6 appears a second time. Only two new "started" entries (and the "ended" entries for those two starts, if the process was killed in between). | not run |

Checks 2 to 4 and 6 are the ones no emulator can do. If the text in brackets is empty or does not tell the cleaners apart, say so in FINDINGS_LOG: the plan for diagnosing missed trips depends on it (`docs/research/2026-10-03-miui-background-limits.md`).

## Phase 1 foundation: storage

| # | Do this | Expect | Result |
|---|---|---|---|
| 8 | After any of the checks above: `adb shell run-as com.shawnkowalchuk.milo ls databases` | `milo.db` with `milo.db-wal`, `milo.db-shm` and `milo.db.lck` beside it. | not run |
| 9 | After the first recorded trip (check 10): the same listing. | `points.db` with `points.db-wal`, `points.db-shm` and `points.db.lck` beside it, and `raw_points` in a copy of `points.db` has one row per fix. Seen on the emulator; not on the phone. | not run |

---

## Phase 1 recording core: a trip started by hand

Every trip here starts with the Start trip button, away from the truck (or before the truck is paired with MilO, which is the next section). These checks prove the part every later trip depends on: the service, the GPS fixes, the notification, the sound and the closing of a trip. Each one names what to look for in the event log.

### Starting, recording, ending

| # | Do this | Expect | Result |
|---|---|---|---|
| 10 | Outdoors, open MilO and press Start trip. | The "Trip in progress" notification appears at once, with a running timer. The chirp plays once from the phone. The home screen shows 0.0 km and the start time, and the button says End trip. Log: `SERVICE` "Trip service in the foreground (Start button: MANUAL_START)", `TRIP` "Trip N started by MANUAL", `LOCATION` "Location fixes requested", then "First fix after … s, accuracy … m". **Write down the time to the first fix.** | not run |
| 11 | Drive a route whose length is known (a few km), with the screen on. Press End trip. | The trip is `FINISHED`. Its distance is within a few per cent of the known length. `raw_points` has a row about every 5 seconds, and the gap query shows no gap much over 5 seconds. Log: `TRIP` "Trip N: finished, … m; ended by MANUAL; … fixes stored, … used". The notification is gone. **Write down the distance against the known one, and the fix counts.** | not run |
| 12 | Start a trip, switch the screen off, put the phone in a pocket or the mount, and drive for at least 15 minutes. End the trip. | As check 11. The gap query is the point: **HyperOS must not thin out or stop the fixes with the screen off.** Write down the longest gap, and any `LOCATION` line "Location has not been available for 10 s". If there are gaps, note the battery saver setting for MilO. | not run |
| 13 | Start a trip, walk less than 300 m, press End trip. | The trip is `DISCARDED`, not deleted, with its distance stored. Log: "discarded: … m is under the minimum of 300 m". | not run |
| 14 | During a trip, watch the notification for a minute while moving. | The distance in the notification follows the home screen, a little behind (it is refreshed at most every 15 seconds). The timer counts up by itself. | not run |

### The trip-start sound

| # | Do this | Expect | Result |
|---|---|---|---|
| 15 | Start a trip with the phone's ringer on and notification volume up. | The chirp, once, from the phone's own speaker, at the notification volume. Log: `SERVICE` "Trip-start sound: playing the bundled chirp". | not run |
| 16 | The same on vibrate, then on silent, then with Do Not Disturb on. | No sound in any of the three. **This rests on stock Android behaviour and is unconfirmed on HyperOS: write down what each mode does.** | not run |
| 17 | The same with the phone connected to the truck's Bluetooth and music playing through the truck. | Write down where the chirp comes out (phone, truck or both) and whether the start of it is cut off. The phone's speaker was asked for; Android may decide otherwise. | not run |

### When the start fails

| # | Do this | Expect | Result |
|---|---|---|---|
| 18 | Set MilO's location permission to "Allow only while using the app". Press Start trip. | No trip starts. The home screen shows "MilO could not start this trip" and says location is not set to "Allow all the time". A notification with the same title appears with sound or a pop-up. Log: `SERVICE` "Could not start recording for Start button: MANUAL_START: BACKGROUND_LOCATION_MISSING". No row is added to `trips`. | not run |
| 19 | Tap that notification. | A trip starts (a tap is allowed to start it even with the permission as it is). The warning disappears. Log: "Trip service in the foreground (tap on the could-not-start notification: MANUAL_START)". | not run |
| 19a | Post the notification again (check 18). Press Home and run `adb shell am kill com.shawnkowalchuk.milo`; check with `adb shell pidof com.shawnkowalchuk.milo` that nothing is printed. Tap the notification. Do it once with Autostart **off** for MilO and once with it **on**. | The same log line as check 19, after a `PROCESS` "started" line. **Unknown with Autostart off, and the point of the check:** the tap starts a service in a dead app, which the research expects HyperOS to reject. The notification then disappears, nothing starts and nothing is logged. Write down both results. If it fails, the tap has to open MilO instead (APP_ENCYCLOPEDIA, edge cases). | not run |
| 20 | Set the permission back to "Allow all the time". Switch the phone's location off. Press Start trip. | As check 18, with "Location is switched off on this phone." | not run |
| 21 | Switch notifications off for MilO. Repeat check 20. | The home screen still says why. Log detail: "Notifications are off for MilO, so the warning was not shown." Then switch location on and press Start trip: the trip records with no notification visible. Switch notifications back on afterwards. | not run |
| 22 | In HyperOS, set MilO's battery saver to the most restrictive setting. Press Start trip. | **Unknown, and the point of the check:** write down whether the home screen reports "Battery use for MilO is set to Restricted" (Android's own setting) or whether the HyperOS setting is invisible to that check and the trip starts. Set it back to "No restrictions" afterwards. | not run |

### Surviving the things HyperOS does

| # | Do this | Expect | Result |
|---|---|---|---|
| 23 | During a trip, swipe MilO away in recents. Wait a minute, then open MilO. | On stock Android the trip carries on: log `SERVICE` "MilO was removed from the recent apps", fixes without a gap. On HyperOS the swipe may kill the process. Then the log shows a `PROCESS` "ended" line with the reason; write it down, and whether the trip carried on (same trip id, `SERVICE` "Trip service in the foreground (restarted by Android)" or "(app opened: RECONCILE)") or was closed. | not run |
| 24 | During a trip, press the recents clear-all button. Wait a minute, open MilO. | As check 23. | not run |
| 25 | During a trip, lock MilO in recents, then run the Security app's cleaner. | The trip carries on. Write down the result with and without the lock. | not run |
| 26 | During a trip, press Home, then run `adb shell am crash com.shawnkowalchuk.milo`. Do not open MilO. | Within seconds, either the service is back by itself (log: "Trip service in the foreground (restarted by Android)", then "picked up the stored state", the same trip id, **no second chirp**), or the "could not start this trip" notification appears and a tap on it carries the same trip on. On the emulator the service came back by itself. Write down which. | not run |
| 27 | Start a trip and leave the phone lying still with the screen off for 35 minutes. | The trip ends by itself: `TRIP` "ended by NO_MOVEMENT", closed where it last moved. **Compare the time of that log line with the trip's start plus 30 minutes.** A line that is minutes late means the phone slept through the timer; write the delay down (FINDINGS_LOG, the timer debt). | not run |
| 28 | Start a trip, leave the phone alone for several hours with the screen off while driving or not, then end it. | The process survived (no `PROCESS` "ended" line during the trip), or the log names what killed it. | not run |

### Android Auto, as far as it can be tested without the screen

| # | Do this | Expect | Result |
|---|---|---|---|
| 29 | Start a trip by hand. Connect the phone to the truck's Android Auto (cable or wireless). Wait two minutes. Disconnect. | Log: `ANDROID_AUTO` "CarConnection reports type 2: Android Auto is connected" soon after connecting, and "type 0 … not connected" after disconnecting. **Write down how long each took.** This works whether or not MilO's own screen shows on the truck. | not run |
| 30 | Read the log of any trip. | At the start of each trip, one `ANDROID_AUTO` line with "type 0" (or 2): the watch is running. A `null` in place of the number means the Android Auto app did not answer. | not run |

---

## Phase 1 triggers: the truck starts and ends a trip

**Nothing in this section has run anywhere but in unit tests and on an emulator without a truck.** The emulator (Android 16) showed the companion service, the boot and update reconcile and the pairing check working on stock Android. It could not show a single Bluetooth broadcast, and it does not use the way of reading the truck's connection that this phone uses (FINDINGS_LOG, 2026-10-05). So these checks are the first time most of this code meets a real connection, and the first time any of it meets HyperOS.

### Pairing MilO with the truck

The truck must already be paired with the phone in the phone's Bluetooth settings.

**The way to pair is the pairing screen:** Setup, the row "Truck paired and watched", Pair truck. Checks 67 to 72 go through it. Write down the association number N from the `PAIRING` line "… ARMED: association N for … is observed" in the Log: check 44 needs it.

**Over adb,** if the pairing screen fails. This skips Android's consent dialog. MilO adopts an association made this way when it has no truck stored, or when the stored truck's own association is gone.

1. Grant Nearby devices (above) and open MilO once.
2. Read the event log. The newest `PAIRING` line ends with the phone's paired devices and their addresses: `… NO_TRUCK: no truck is paired. Android lists no association at all. Paired with the phone: <name> (AA:BB:CC:DD:EE:FF), …`. Take the truck's address from it. Stop and write it down if:
   - the line says `this phone has no companion device support`. HyperOS does not report the feature and MilO can adopt nothing over adb. Use the pairing screen, which stores the truck without companion support; the Bluetooth receiver is then the only automatic trigger;
   - the list is `PERMISSION_MISSING` or `BLUETOOTH_OFF`. Grant Nearby devices or switch Bluetooth on, open MilO again, and read the new line.
3. Make the association from the Mac, with that address:

```
adb shell cmd companiondevice associate 0 com.shawnkowalchuk.milo AA:BB:CC:DD:EE:FF
adb shell cmd companiondevice list 0
```

   **Compare the address the second command prints with the one in the `PAIRING` line before going on.** MilO adopts an association only for a device that is paired with the phone, so a mistyped address is not stored as the truck. The next `PAIRING` line then says `…, which was not adopted`, and nothing starts by itself until the association is put right (see the way back, below).
4. Send MilO to the background, wait a few seconds, and open it again. Expect two `PAIRING` lines: "No truck was stored and Android lists one association. Adopted it: Truck(address=…, name=…, associationId=N)" and "Truck pairing checked (app opened): ARMED: association N for … is observed". **Write down N**: check 44 needs it.
5. `adb shell dumpsys companiondevice | grep milo` shows `mNotifyOnDeviceNearby=true`.

**The way back from a wrong association, and the way to pair another truck.** The pairing screen can change the truck (check 71). Over adb, remove the association and make the right one, then send MilO to the background and open it again:

```
adb shell cmd companiondevice disassociate 0 com.shawnkowalchuk.milo <wrong or old address>
adb shell cmd companiondevice associate 0 com.shawnkowalchuk.milo <the truck's address>
```

If a truck was already stored, expect `PAIRING` "Android lists no association for the stored truck (…) and one for another paired device. Adopted it in its place: Truck(…)", then "… ARMED". A stored truck that still has its association is never replaced. **Never clear MilO's data to change the truck or to get round a pairing problem: that deletes the trips.**

### A first drive

Set up as HyperOS should be: Autostart on for MilO, battery saver "No restrictions", MilO locked in recents. The Setup screen says "Everything MilO needs is set" when that is so and the truck is paired (checks 54 to 68). Open MilO once, then press Home.

| # | Do this | Expect in the event log | Result |
|---|---|---|---|
| 31 | Get in and start the truck. Do not touch the phone. | The chirp within a few seconds, and the "Trip in progress" notification. `TRIGGER` "Bluetooth receiver: ACL connected for the truck (address): TRUCK_LINK_CONNECTED: asked Android for the trip service", or the same from "companion service: device appeared (…): TRUCK_APPEARED". Then `SERVICE` "Trip service in the foreground (…)" and `TRIP` "Trip N started by TRUCK". Seconds later, `TRIGGER` lines "…hands-free profile connected for the truck…: the truck is connected (the hands-free profile lists the truck)". **Write down which line came first, how many seconds after the truck's screen showed the phone, and which of the four kinds appeared at all: ACL, companion, hands-free, audio.** In the first seconds, before a profile has connected, there may be lines ending "No reading has shown the truck connected since its link was made, so this proves nothing" (from "companion service created" or "app opened"). They are expected and harmless. There must be no `GRACE` line at the start of the drive. | not run |
| 32 | Five minutes into the drive, read the log. | **No "minute check" line.** A reading that agrees with what is believed is not logged, so silence is the good result. A line every minute, "minute check: the truck reads as not connected (neither the hands-free nor the audio profile lists it). No reading has shown the truck connected since its link was made, so this proves nothing", means MilO cannot see this truck's connection through the two profiles. The trip is not ended by it, but on this truck a disconnect that is never reported would not be caught, and a restart of MilO in mid-drive would end the trip. **Write the lines down: this decides whether the connection check works for this truck.** A line "…One reading alone proves nothing" followed a minute later by a `GRACE` line while the truck is connected means the profiles dropped while the link stayed up; write that down too. A line "…could not be read (…)" is also to be written down, with its reason. A source of "minute check (prompted by a GPS fix: the timer was late)" on any of these lines means the phone slept through the minute timer. | not run |
| 33 | Park, switch the truck off, walk away with the phone. | `TRIGGER` "…ACL disconnected for the truck…: the truck is not connected", from "Bluetooth receiver" and from "Bluetooth receiver (trip service)", and "companion service: device disappeared". `GRACE` "Trip N: grace period started (120 s)". Two minutes later `TRIGGER` "timer: the truck is not connected (neither the hands-free nor the audio profile lists it)" and `TRIP` "Trip N: finished, … m; ended by GRACE_EXPIRED". The notification goes. The trip's end time is when the truck went, not two minutes later. **Write down which of the three disconnect lines appeared.** | not run |
| 34 | During a drive, stop, switch the truck off and on again within a minute. | If the truck drops Bluetooth: `GRACE` "grace period started", then on reconnect "grace period cancelled", and the same trip number carries on. No second chirp. | not run |
| 35 | Drive with Android Auto on the cable (if the truck has it), and read the log after. | Whether the hands-free line of check 31 still appears, and whether any "minute check" line does. Android Auto sends audio over the cable, so the audio profile may never connect; the hands-free profile should. | not run |

### Who can wake MilO: Autostart off and on

The question the whole design hangs on. For each row, bring MilO into the state named, then connect to the truck (get in and start it) and wait a minute without touching the phone. Do the whole table twice: once with Autostart **off** for MilO, once with it **on**. Afterwards open MilO and read the log.

A row passes if a trip started by itself: `PROCESS` "Process … started" (where the process was dead), a `TRIGGER` connect line, `SERVICE` "Trip service in the foreground", `TRIP` "started by TRUCK". **For every row write down which trigger line came first: "Bluetooth receiver", "companion service", or none.** If none, the last line of the row's expectation says what opening MilO should then do.

| # | State of MilO before the truck connects | Expect | Autostart off | Autostart on |
|---|---|---|---|---|
| 36 | In the background, process alive (opened a minute ago, Home pressed). | A trip starts. The control row. | not run | not run |
| 37 | Process killed, not force-stopped: press Home, then `adb shell am kill com.shawnkowalchuk.milo`. Check with `adb shell pidof com.shawnkowalchuk.milo` that nothing is printed. | Stock Android starts the process for the broadcast and for the companion service. With Autostart off the research expects neither on HyperOS. If none: opening MilO logs "app opened: the truck is connected (…)" and the trip starts then. | not run | not run |
| 38 | Swiped away in recents (no trip open). | As 37, unless the swipe is a force stop on this phone: the `PROCESS` "ended" line says which. Then it is row 41. | not run | not run |
| 39 | Phone rebooted with the truck already running, **left locked**. Wait two minutes, then unlock. | Nothing before the unlock. After it: `TRIGGER` "phone booted: the truck is connected (…)" or a companion "device appeared" line, and a trip starts. **Write down the seconds from unlock to "Trip service in the foreground"**, and which of the two lines it was. | not run | not run |
| 40 | Phone rebooted and unlocked, MilO not opened, truck off. Then start the truck. | As 37: the process is dead after a reboot. | not run | not run |
| 41 | Force-stopped: Settings, Apps, MilO, Force stop. | The Bluetooth receiver stays silent on every Android (a stopped app gets no broadcasts). On stock Android the companion service still starts MilO; whether HyperOS allows it is unknown. If none: opening MilO starts the trip, and from then on the receiver works again. | not run | not run |
| 42 | Reinstalled from the Mac (`./gradlew installDebug`) with the truck off, and **not opened** afterwards. Then start the truck. | The log of the install itself: `TRIGGER` "MilO was updated: the truck is not connected (…)" and `PAIRING` "… ARMED" (the association survives an update). Then a trip starts as in 37. If nothing happens until MilO is opened, "open MilO after every install" becomes a rule. | not run | not run |
| 43 | Reinstalled in the middle of a drive. Do not open MilO. | The same trip carries on (same number, no second chirp): `TRIGGER` "MilO was updated: the truck is connected (…)" or "companion service created: the truck is connected (…)", then `SERVICE` "Trip service in the foreground". On the emulator the update reconcile ran within three seconds of the install. | not run | not run |

### The companion service without the truck

These can be done at the desk, and they answer the Autostart question for the companion path alone. N is the association number from the pairing steps. On Android 14 the command sends the same "appeared" callback the truck would.

| # | Do this | Expect in the event log | Result |
|---|---|---|---|
| 44 | Truck off or out of range. Kill MilO as in row 37. `adb shell cmd companiondevice simulate-device-appeared N`. Wait 20 seconds. | `PROCESS` "started"; `TRIGGER` "companion service: device appeared (…): TRUCK_APPEARED: asked Android for the trip service"; `SERVICE` "Trip service in the foreground"; `TRIP` "Trip N started by TRUCK"; **no chirp and no "Trip-start sound" line**; 15 seconds later `TRIGGER` "timer: the truck is not connected (…)" and `TRIP` "discarded as a false start: the truck's connection was never confirmed". No `GRACE` line. About a minute after the command Android takes the device away again by itself: "companion service: device disappeared". Seen on the emulator; not on the phone. | not run |
| 45 | The same with Autostart off, then with MilO force-stopped, then swiped away. | Whether the first line appears at all. This is the cleanest test of whether HyperOS lets Android bind the companion service of a dead app. | not run |
| 46 | `adb shell cmd companiondevice disassociate 0 com.shawnkowalchuk.milo <address>`, then open MilO. | `PAIRING` "… ASSOCIATION_MISSING: the truck (…) has no association any more and has to be paired again. Android lists no association at all. Paired with the phone: …". Make the association again as in the pairing steps and open MilO: "ARMED" again (the association has a new number). | not run |

### The rules around End trip and a lost disconnect

| # | Do this | Expect in the event log | Result |
|---|---|---|---|
| 47 | During a trip, with the truck running, press End trip. Stay in the truck five minutes. Do it once on Bluetooth alone and once with Android Auto on the cable (if the truck has it). | `TRIP` "Trip N: finished…; ended by MANUAL" and "Automatic start held off: ended with the truck connected". No new trip, although the truck is still connected and MilO is opened or reads the truck again. **One way a new trip can still start, to be written down if it happens:** a `TRIGGER` line "…hands-free profile disconnected for the truck…: the truck is not connected", then `TRIP` "Automatic start no longer held off: TRUCK_SEEN_DISCONNECTED", then a profile "connected" line and "Trip N+1 started by TRUCK". The truck dropped and remade its only profile on a link that stayed up. MilO releases the hold-off on any reading of "not connected" on purpose (ADR-002, amendment 24); if this truck does it, that choice has to be looked at again. | not run |
| 48 | Then switch the truck off. | A disconnect line, and `TRIP` "Automatic start no longer held off: TRUCK_SEEN_DISCONNECTED". The next start of the truck starts a trip. | not run |
| 49 | Only if rows 37 showed that Autostart off keeps MilO from being woken. Press End trip with the truck running, switch Autostart off, kill MilO (row 37), switch the truck off, wait two minutes, switch Autostart on and start the truck. | The disconnect was not seen. The connect is a new link more than 60 seconds after End: `TRIP` "Automatic start no longer held off: NEW_LINK" and a trip starts. (If the disconnect got through after all, the reason is `TRUCK_SEEN_DISCONNECTED`.) | not run |
| 50 | Start a trip by hand away from the truck, then get in and start the truck. | `TRIP` "Trip N: the truck is connected". From then on it ends like a truck trip (check 33), not after 30 minutes without movement. | not run |
| 51 | Take Nearby devices away from MilO in the phone's settings, open MilO and press Start trip. Give it back afterwards. | The home screen: "Bluetooth permission is missing". `TRIGGER` "…the truck's connection could not be read (MilO is not allowed to use Bluetooth)". No trip. Seen on the emulator. | not run |

### What these checks cannot show

- **A disconnect that is never reported** cannot be made to happen on purpose. The once-a-minute reading that catches it (two readings in a row, counted only once a reading has shown the truck connected) is covered by unit tests; on the phone it shows only as the "minute check" lines of check 32.
- **A minute timer that is late** shows only when a reading is logged at all: its source then reads "minute check (prompted by a GPS fix: the timer was late)". Check 27 is the direct measure of whether timers stall on this phone.
- **A trip service that Android destroys in mid-trip and refuses to restart** is covered by unit tests only. Should it happen, the log shows `SERVICE` "The trip service stopped while a trip is open…", "Could not start recording for trip service stopped…" and "The open trip is not being recorded. The next trigger picks it up".
- **The 12-hour end of the hold-off** is covered by unit tests only.
- **Android's consent dialog** and the pairing through an Activity are checks 67 to 72, below.
- **Android 12, 13, 15 and 16 behave differently** in the companion service (three shapes of callback) and in how the truck is read. This phone runs Android 14 only. The other branches are compiled and reviewed, and one of them (Android 16) ran on the emulator.

## Phase 1 screens: navigation, Setup, pairing, the log and the trips

**Nothing in this section has run anywhere.** Work package 3 was built while the phone and the emulators were in use by another change, so it was proven with the build and unit tests only (FINDINGS_LOG, 2026-10-05). These checks are the first time any of these screens is drawn. For every check, a crash or a screen that cannot be read is itself the result: write it down, with the newest `CRASH` line of the Log.

### Getting around

| # | Do this | Expect | Result |
|---|---|---|---|
| 52 | Open MilO. Press each button of the bottom bar in turn. | Four buttons: Home, Trips, Setup, Log, each with an icon and its name, the one showing highlighted. Each opens its screen. **Look at the four icons** (a house, a calendar page, a ticked list, a bulleted list) and the four states of the Setup rows (tick, warning, question mark, empty ring): they were drawn from numbers and never seen. | not run |
| 53 | From Trips press Back. From Setup open the truck row, then press Back twice. Open the truck row again and tap the arrow beside the title twice, as fast as you can. From Home press Back. Then open Log, press Home on the phone, wait a minute and open MilO from the recent apps. | Back from Trips, Setup or Log leads to Home. Back from the pairing screen leads to Setup, and the bar shows Setup the whole time. The arrow tapped twice also ends on Setup, not on Home, and MilO does not crash. Back from Home leaves MilO. MilO comes back on the Log screen. Rotating the phone on any screen keeps the screen. | not run |

### The Setup screen

Start with the permissions taken away: Settings, Apps, MilO, Permissions, each set to "Don't allow". Or from the Mac, one line per permission, each naming MilO:

```
adb shell pm revoke com.shawnkowalchuk.milo android.permission.ACCESS_BACKGROUND_LOCATION
adb shell pm revoke com.shawnkowalchuk.milo android.permission.ACCESS_FINE_LOCATION
adb shell pm revoke com.shawnkowalchuk.milo android.permission.ACCESS_COARSE_LOCATION
adb shell pm revoke com.shawnkowalchuk.milo android.permission.POST_NOTIFICATIONS
adb shell pm revoke com.shawnkowalchuk.milo android.permission.BLUETOOTH_CONNECT
```

Android closes MilO when a permission is taken away, so do this with no trip open. **Never use `adb shell pm reset-permissions`.** That command takes no app name: it takes the permissions away from every app on the phone.

| # | Do this | Expect | Result |
|---|---|---|---|
| 54 | Open Setup. | "Reading the phone's settings…" for a moment at most, then a line saying how many things are not set, the card "Permissions and phone settings" with nine rows, and the card "This Xiaomi phone (HyperOS)" with four. Home shows the card "Setup needs attention". | not run |
| 55 | Precise location: press Allow. Choose "While using the app" with Precise on. | Android's own dialog. The row turns green without leaving the screen. The row below, "Allow all the time", now has an Allow button; before, it said to allow precise location first. **If you choose Approximate, the row stays red, and Allow asks again.** | not run |
| 56 | Location, Allow all the time: press Allow. | Android (or HyperOS) opens its own location page for MilO, as the row says. Choose "Allow all the time" and press Back: the row is green. **Write down what the page looks like on HyperOS.** | not run |
| 57 | Notifications and Nearby devices: press Allow on each. | A dialog each; both rows turn green. | not run |
| 58 | Take Nearby devices away again, press Allow and refuse, twice. Press Allow a third time. | The third press opens MilO's page in the phone's settings in place of a dialog, because Android no longer shows one. Allow it there and come back: green. | not run |
| 59 | With Setup open, pull down the quick settings and switch Location off. Close them. Then press the row's button. | The row "Location switched on" is red as soon as the quick settings close: MilO reads the phone again when its window gets the focus back. **That rests on HyperOS's panel taking the focus the way stock Android's does, which nobody has tried. If the row stays green, write that down, then leave MilO and come back: that must turn it red.** The button opens the phone's Location settings. Switch it on and come back: green. | not run |
| 60 | Battery use: press Open settings. | Android's dialog "Let app always run in background?", or HyperOS's own page in its place. Allow. The row is green on return. **Write down which of the two appeared.** Then, on the Mac, `adb shell dumpsys deviceidle whitelist` lists `com.shawnkowalchuk.milo`. | not run |
| 61 | In the phone's settings set MilO's battery use to the most restrictive choice. Open Setup. | Unknown, and the same question as check 22: either the row "Battery use: unrestricted" turns red and says that MilO is restricted in the background, naming Battery saver "No restrictions" as the way out, or the HyperOS setting is invisible to it. Write down which, and whether its button (MilO's App info page) leads to that setting. Set it back. | not run |
| 62 | Not paused when unused: press Open settings. | MilO's page in the phone's settings, with the switch "Pause app activity if unused". Switch it off, come back: green. **Write down whether HyperOS has that switch and whether the row follows it.** This row is recommended: it never raises the home screen's warning. | not run |
| 63 | Switch the phone's Battery saver on (Settings, Battery). Open Setup, then Home. | The row "Battery Saver off" is red and Home shows the warning. Switch it off: both clear. **Write down whether the row sees HyperOS's Battery saver, and its Ultra battery saver.** | not run |

### The HyperOS rows

| # | Do this | Expect | Result |
|---|---|---|---|
| 64 | With Background autostart **off** for MilO, open Setup. Press the row's Open settings. Switch it on. Come back. Then switch it off again and come back. | "Looks off" with a red warning, then "Looks on" with a green tick, then "Looks off" again. **This is the unofficial reading meeting the phone for the first time: write down exactly what the row said each time.** If it says "MilO could not read this setting", write that down too: the row then waits for "I have set this". Write down which screen the button opened: the list of apps allowed to autostart, or MilO's App info page. | not run |
| 65 | Battery saver: No restrictions. Press Open settings. Choose "No restrictions". Come back and press "I have set this". Press "Not set any more", then confirm again. | The button opens MilO's Battery saver choices, or MilO's App info page (then: Battery saver, No restrictions). The row turns green and says "You confirmed this on (today's date)". "Not set any more" brings the empty ring back. **Write down which screen opened.** | not run |
| 66 | Other permissions: press Open settings. Locked in recent apps: follow the row and confirm. | Other permissions opens MilO's HyperOS permission page, or App info. **Write down which, and the names of the switches HyperOS 2 has there**: the row names them from the research. With both required rows confirmed, Autostart looking on and everything above green, one thing is still not set, the truck: Setup says "1 thing MilO needs is not set" and Home still shows the warning. Both clear in check 68. | not run |

After checks 60 to 66, read the Log. For every button that could not open its own screen there is an `ERROR` line "Opened another screen in place of XIAOMI_…" (or of another name), with the reason in its detail. **Write each one down:** it says which of the researched HyperOS screens this phone does not have.

### The pairing screen

| # | Do this | Expect | Result |
|---|---|---|---|
| 67 | Setup, the truck row, Pair truck. | "No truck is paired", and the card "Paired with this phone" listing the phone's Bluetooth devices by name, each with a Pair button. | not run |
| 68 | Press Pair on the truck. Choose Allow in Android's dialog. | **Android's consent dialog, shown by MilO for the first time.** Then "Paired with (the truck's name)" and, under the truck's name, "Paired, and Android is watching for it." Log: `PAIRING` "Paired with the truck: …" and "Truck pairing checked (paired with the truck): ARMED: association N for … is observed". On the Mac, `adb shell dumpsys companiondevice \| grep milo` shows `mNotifyOnDeviceNearby=true`. Setup's truck row is green and its button says Change truck. With checks 54 to 66 done, Setup now says "Everything MilO needs is set" and the warning on Home is gone. **Write down N, and the exact words of Android's dialog.** | not run |
| 69 | Press Pair again on the truck and close Android's dialog without allowing (Back, or Don't allow). | "Not paired. Android's dialog was closed without allowing. Nothing was changed." The truck above is still paired and watched. If no dialog appears and the pairing just succeeds again, write that down: Android then reuses the association. If the screen says "Pairing failed" in place of "Not paired", Android's dialog ended by itself (it gave up looking for the truck, or failed inside): write down the reason shown. | not run |
| 70 | Take Nearby devices away and open the pairing screen. Allow it. Then switch Bluetooth off and come back. Press the button, switch Bluetooth on and press Back at once. Then switch Location off in the quick settings, with the pairing screen open. | In turn: a card saying MilO is not allowed to use Nearby devices, with an Allow button that brings the list back; "Bluetooth is switched off" with a button to the Bluetooth settings; the list of devices, which appears by itself within a few seconds even if Bluetooth was still switching on when you came back; "Location is switched off", with the devices listed but their buttons greyed out, as soon as the quick settings close. **Write down if the list did not appear by itself.** | not run |
| 71 | Press "Use this one" on another device (earbuds will do). Allow. Then press "Use this one" on the truck and allow. | After the first: the other device is the truck, and `adb shell cmd companiondevice list 0` shows one association for MilO, the new one. After the second: the truck is the truck again, with a new association number, and still only one association. Log: two "Paired with the truck" lines. **Write down the new N.** | not run |
| 72 | `adb shell cmd companiondevice disassociate 0 com.shawnkowalchuk.milo <address>`, then open MilO and go to Setup. | The truck row is red: "(name) was paired, but Android no longer watches for it. Pair it again.", and its button says Pair truck. Home shows the warning. On the pairing screen, Pair again brings back "watching for it". | not run |

### The Log screen

| # | Do this | Expect | Result |
|---|---|---|---|
| 73 | Open Log after the checks above. | The newest line on top. Every line has a date and a time with seconds (`2026-10-05 08:14:03`) in the phone's own time, a category and a message. **Compare the time of the newest line with the phone's clock.** `CRASH` and `ERROR` are in red. | not run |
| 74 | Press a `TRIGGER` line that says "Tap for details". Press it again. Rotate the phone with a line open. | The detail (the state before and after) opens under the line, and closes. It stays open through the rotation. A line without "Tap for details" does nothing. | not run |
| 75 | With Log open, connect a Bluetooth device (earbuds). Then scroll to the very bottom. | A new "Ignored" line appears at the top by itself. With more than 200 lines in the log, the bottom has "Show older entries", and pressing it adds 200 more. Scrolling stays smooth. **After a week of driving, write down how long the screen takes to open.** | not run |

### The Trips screen

| # | Do this | Expect | Result |
|---|---|---|---|
| 76 | Open Trips after the trips of checks 10 to 13. | The current month's name and year, the total km and "N trips", then one card per day, newest day first, each trip with its start time, end time and km. The total is the sum of the trips listed. The discarded trip of check 13 is not listed; a line says "1 trip was discarded and is not shown". | not run |
| 77 | Switch "Show discarded trips" on. | The discarded trip appears in its day, marked "Discarded … Not counted", its km greyed. The total and the number of trips do not change. | not run |
| 78 | Press Previous month, then Next month. | A month without trips says "No trips in (month and year)". Next month is greyed out on the current month, and works from an earlier one. Leaving Trips and coming back shows the current month again. | not run |
| 79 | Start a trip by hand, open Trips, walk or drive a little, end the trip. If it can be arranged: start a trip before midnight and end it after. | While recording: a card "In progress" at the top with the start time and a growing km figure, and the total unchanged. After End trip it moves into its day and the total grows. The trip over midnight is listed once, under the day it started. | not run |

## Later work packages

The Android Auto screen on the truck adds its checks here when it is built.
