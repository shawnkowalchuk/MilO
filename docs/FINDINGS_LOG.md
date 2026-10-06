# Findings Log

> **What this is.** A running, dated journal of everything that happens to this codebase — every change, every decision, and every *finding* (a bug discovered, a gotcha, a thing that turned out not to work the way you assumed). It's the project's narrative memory. Where ARCHITECTURE and the ENCYCLOPEDIA describe the app *as it is now*, this log describes *how it got there and why*.
>
> **Why it matters.** Most "wait, why did we do it this way?" debt comes from lost context. This log is the answer to that question, written down at the moment you still remember it. In an AI-assisted workflow, **append an entry after every meaningful change** — it's part of the Definition of Done (STANDARDS §10), and it gives your assistant (and future-you) the real history instead of guesses.
>
> **How to use it.** Newest entries at the top. One entry per change/finding/decision. Keep them short — a few lines each. Tag the type so the log is scannable.
>
> **Status:** Living document · **Started:** 2026-10-03

---

## Entry types

- **`[CHANGE]`** — something was built, modified, or removed
- **`[FIX]`** — a bug was fixed
- **`[DECISION]`** — a choice was made (link an ADR if it's a big one)
- **`[FINDING]`** — something was discovered: a gotcha, a constraint, a surprise, a "don't do X because Y"
- **`[DEBT]`** — a corner was knowingly cut (what, why, cost to fix later)

---

## Log

### 2026-10-05

**`[FIX]` What the review of the screens found (work package 3)**
Two reviewers read the package before it was committed. Their findings and what was done about each. **Nothing here has run on a phone or an emulator either:** every fix is proven by the build and unit tests only, and the checks that must prove it on the phone are named.
- **The device checklist offered a command that takes the permissions away from every app on the phone.** `adb shell pm reset-permissions` was written with MilO's name after it, but the command takes no app name. Replaced by one `adb shell pm revoke` line per permission, each naming MilO, and a warning against the other one.
- **A screen did not notice the quick settings.** Setup, Home and the pairing screen read the phone again only when they resumed, and the quick settings panel covers MilO without pausing it: Location switched off there left Setup green. The screens now also read again when MilO's window gets the focus back. One shared effect does both, `CameToFrontEffect` in the design system, and Trips uses it too. Whether HyperOS's panel takes the focus the way stock Android's does is untested (device check 59).
- **Bluetooth that was still switching on read as "switched off", for good.** Switching it on takes the phone a second or two, and Back from the Bluetooth settings is quicker. The pairing screen now reads the paired devices once more when Android reports that Bluetooth has finished switching on or off (`platform/bluetooth/BluetoothSwitch.kt`, a receiver that is registered only while the pairing screen is on the back stack). Device check 70.
- **A consent dialog that failed by itself was shown as "closed without allowing".** From Android 13 the dialog has results other than "allowed", "closed" and "refused": Android gave up looking for the device, or failed inside. Those are now "Pairing failed", with `TruckPairing`'s reason; only results 0 and 1 are Shawn's own doing (`consentWasDeclined`).
- **The pairing screen's Back arrow could crash MilO.** A screen that is closing stays on the display for the length of the transition, and so does its arrow. A second tap closed Setup as well, and a third emptied the back stack, on which Navigation 3 throws; the process that dies is the one the trip service runs in. The arrow now closes the pairing screen only while it is on top, and Back never closes the last screen (`closeIfOnTop`, `closeTop` in `app/MiloNavigation.kt`).
- **Words that pointed at something the phone does not have.** The battery row told Shawn to choose "Unrestricted", which HyperOS does not offer; on a Xiaomi phone it now names Battery saver, "No restrictions" (a second sentence, `BATTERY_RESTRICTED_HYPEROS`). The truck row said "Pair it again" beside a button labelled "Change truck"; the button now says "Pair truck" whenever there is no pairing to change.
- **The device checklist could not be followed in order.** Check 66 expected "Everything MilO needs is set" two checks before the truck is paired. That expectation moved to check 68, and "Before the first check" now gives the order for a phone that has never been set up.
- **Statements this package had made false** were corrected: ADR-002 on `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (amendment 25), ARCHITECTURE on pairing having no screen, the encyclopedia on nothing reading the settings yet, and the three debts this package closed, which are now marked closed where they were logged.
- **Sending to the accountant** was recorded as deferred by decision. It is not Shawn's decision yet: see the entry below, now marked as waiting for him.
- **Tests:** 13 new, 439 in all.
- **Under `platform/bluetooth/`,** which another change has open: one new file, `BluetoothSwitch.kt`, and its test. No existing file there was touched, so the three stale comments named below are still stale.

**`[CHANGE]` The screens for the first test drive (work package 3)**
Until now MilO was one screen with one button. Everything below is new, and **none of it has run on a phone or an emulator**: this package was built and proven with the build and unit tests only (see the finding below).
- **Navigation** (`app/MiloNavigation.kt`, `app/MiloApp.kt`): Navigation 3 with a Material 3 bottom bar of four screens: Home, Trips, Setup, Log. The pairing screen is opened from Setup.
- **Truck pairing screen** (`feature/pairing/`): the phone's paired Bluetooth devices, a Pair button on each, Android's consent dialog, and the result in plain words. It asks for Nearby devices, and says what to do when Bluetooth or location is off, nothing is paired, or the dialog was closed. Picking another device changes the truck. It calls `TruckPairing` as it was; no existing file under `platform/bluetooth/` changed (the review above added one new file there).
- **Setup screen** (`feature/setup/`, rules in `platform/system/`): nine Android rows and, on a Xiaomi, Redmi or POCO phone, four HyperOS rows. Each row has its state and a button to the place to fix it. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is now declared, for the battery row.
- **Home**: a warning card that leads to Setup while a required row is not in order.
- **Event log screen** (`feature/eventlog/`): newest first, time to the second in local time, detail on a press, 200 entries at a time.
- **Trips screen** (`feature/trips/`): one month at a time, opening on the current one.
- **Design system**: `StatusRow` has four states and up to two buttons; new `MiloNavigationBar`, `ScreenTitle`, `SwitchRow` and `MiloIcons`; two new status colours.
- **Storage**: the confirmation dates of the checklist are four new keys in the settings file. `TripDao.observeStartedBetween` reads the trips of a span of time. **No table changed**: both exported schema files are byte for byte the same.
- **Dependencies added,** each checked against its live Maven metadata today: `navigation3-runtime` and `navigation3-ui` 1.2.0 (1.3.0 is an alpha), `lifecycle-viewmodel-navigation3` and `lifecycle-runtime-compose` 2.11.0 (2.12.0 is an alpha), `kotlinx-serialization-json` 1.11.0 (1.12.0 is a release candidate), and the Kotlin serialization plugin 2.4.20.
- **Tests:** 94 new, 426 in all.
- **Closes three debts** logged below: "A truck can only be paired over adb", "Permissions have to be granted by hand", and "`StatusRow` has two states".
- **Left stale on purpose:** three comments in `platform/bluetooth/` still say there is no pairing screen (`TruckPairing.kt`, twice, and `PairingStatus.kt`). Another change was open on that package, so it was not touched. One line was added to a test stand-in under `platform/trip/` (`FakeTripDao`), because the DAO gained a query.

**`[DECISION]` Points the documents did not settle, decided while building the screens**
All are Shawn's to change.
- **Which rows are required.** A row is required if an automatic trip can fail to start, or be cut short, without it. Recommended only: "Pause app activity if unused" (it matters after months without opening MilO) and the HyperOS "Other permissions" (they govern screens, and MilO starts a service). Everything else is required, including notifications (the "could not start this trip" warning is one) and Battery Saver being off. The home screen's warning follows the required rows only.
- **On the POCO the warning stays until two rows are confirmed by hand:** Battery saver "No restrictions" and the lock in recents. MilO cannot read either, so the only honest "OK" is Shawn's own word, stored with its date.
- **Autostart is believed as far as it can be read, and no further.** "Looks on" and "looks off" are shown as read. A confirmation by hand is offered only when the phone gives no reading at all, and it never overrides "looks off": the reading is the same question HyperOS itself asks before it starts an app.
- **A confirmed row is drawn as OK, with "You confirmed this on (date). MilO cannot check it."** The work order named four states for `StatusRow`; a fifth, "confirmed by you", was not added.
- **One battery row for Android's three settings.** "Restricted" (which blocks the trip service and was already in the preflight) and "not exempt" are two states of one row, with different buttons.
- **A row in order has no button,** except the truck row (the pairing screen is where the truck is changed) and the Autostart row (its reading is not certain).
- **Telling a permission dialog that never appeared from one that was refused.** Android stops showing a dialog after two refusals and does not say so. The usual test is used: if Android's "should the app explain?" answer is "no" both before and after, no dialog appeared, and the settings page is opened in its place. A first dialog dismissed without an answer is misread the same way; the settings page is harmless there.
- **Settings screens are opened from the application context, as a task of their own,** so the ViewModel can open them and no Composable calls Android. Every way of opening a screen is tried in order, down to Android's own page for MilO, and a fallback is written to the event log as an `ERROR` line: on this phone nobody would otherwise learn that a HyperOS screen is missing. No new event category.
- **The bottom bar keeps Home at the bottom of the back stack.** Back from Trips, Setup or Log leads to Home, and Back from Home leaves MilO. Each screen's ViewModel lives as long as the screen is on the back stack, so Trips opens on the current month every time.
- **The Trips screen.** The trip in progress is shown apart and is not in the total until it ends; its running distance comes from the trip controller, because the stored row holds 0 while a trip is open. Within a day the newest trip is first, like the days. A month whose only trips were discarded says how many are hidden. A trip belongs to the month of its start even when it is still recording at the turn of the month.
- **`kotlinx-serialization-json` is the declared serialization library,** as ADR-001 pins it, although the code only needs its core (the `@Serializable` annotation on the screen keys). Navigation 3 alone would bring core 1.7.3; this pins 1.11.0.
- **`lifecycle-runtime-compose` was added** for `LifecycleEventEffect`, which is how a screen re-reads the phone each time it comes to the front. It was already on the classpath through Compose; it is declared because the code uses it directly.
- **The event log's time is `2026-10-05 08:14:03`** in every language: sortable, 24-hour, with the date on every line.

**`[FINDING]` What work package 3 has never been run on**
Another change was using the emulators and the phone, so this package touched neither. What is proven: it compiles with warnings as errors, lint passes, and the unit tests cover the checklist's rules, the pairing screen's state, the month logic and the back stack. **What has never run anywhere:**
- **Every screen.** Not one Composable of this package has been drawn. Layout, wording that does not fit, a crash on opening a screen: all untested. The icons were written as path data by hand; a test proves each one parses, not that it looks right.
- **Navigation 3 at run time:** the bottom bar, Back, the saved back stack after Android has put MilO away.
- **Every permission dialog,** and what HyperOS shows in place of Android's own.
- **Android's consent dialog for the companion association,** which has never been shown by any build of MilO. This was already the biggest unknown of the triggers.
- **Every button that opens a settings screen.** The HyperOS component names come from the research, which found no test of them on this phone's HyperOS 2.
- **The Autostart reading.** The reflection call is the one the research describes; whether it answers on this phone is unknown.
- **`isPowerSaveMode` and `isAutoRevokeWhitelisted` on HyperOS:** whether they follow Xiaomi's own Battery saver mode and its "Pause app activity if unused" switch.
All of it is in `docs/DEVICE_TEST_CHECKLIST.md`, checks 52 to 79.

**`[FINDING]` An index on the trips' start time would help, and was not added**
The Trips screen asks for the trips that started in a span of time, and `trips` has no index on `startedAtMs`, so SQLite reads the whole table. At a few thousand rows a year that takes milliseconds. An index is a schema change, there is real data on the phone and no migration exists yet, so it was left out. Add it with the first migration.

**`[DECISION]` Shawn's requests after seeing the first build on the emulator**
The emulator build showed only a Start trip button, and Shawn named three things he needs: a way to pair to a vehicle, a Trips button to view the current and previous months' trips, and a way to send to the accountant for a specific month or a date range.
- **Pairing** was already in this package.
- **Trips by month is pulled forward into this package.** It had been deferred until after the first test drive to save time; he asked for it by name. It shows times and km only. Addresses, Business/Personal and editing still arrive in phase 2.
- **Sending to the accountant is not in this build, and that is not Shawn's decision yet.** He asked for it by name ("a way to send to a accountant. be able to pick specific month or date range"). The assistant left it in phase 3, because the report as briefed needs the phase 2 pieces first (Business/Personal and the from/to addresses), and widened its scope to a custom date range as well as a whole month. **Pending his confirmation:** nobody asked him whether waiting is acceptable. Until he answers, the Trips screen has no send, share or export of any kind. What he has to settle if he wants it sooner:
  - a reduced report now (times and km of every finished trip, no addresses, no Business/Personal), or the full report once phase 2 is in;
  - the file: the PDF of the brief, or a CSV first;
  - how it is sent: a Gmail draft to a stored accounts address (nothing stores one yet, and there is no settings screen), or Android's share sheet;
  - what a date-range report does to a month's Submitted status.
- **The Android Auto screen moves to after the first test drive.** Proposed by the assistant when Shawn said the build was taking very long; Google's rules make it unlikely to appear on the truck, and it is not needed to prove trip detection. Pending his objection.

**`[CHANGE]` Shawn's own R2-D2 clip is the trip-start sound in his builds**
Shawn dropped `r2d2.mp3` (4.5 seconds, 9 KB) into the project folder. It is copyrighted film audio, so it must not be committed, but it is his to use on his own phone. Android lets a build type override a resource: a file at `app/src/debug/res/raw/trip_start_chirp.mp3` replaces `main`'s `trip_start_chirp.wav` in debug builds, and debug is what gets installed on the phone. Both that folder and `/r2d2.mp3` are git-ignored. Checked: the built APK contains the mp3 and not the wav, and a trip started on the emulator played it with no player error. Not checked: how it sounds on the phone's speaker, and silent mode. This needed no code change, which is why it was done before the in-app sound picker; the picker is still planned with the settings screen. If MilO is ever built on another machine, the clip has to be copied there by hand or the build falls back to the synthesized chirp.

**`[CHANGE]` First install on the phone**
The placeholder build (the foundation, no trip engine yet) was installed on the phone over USB with adb, signed with the dedicated key, and opened without a crash. HyperOS accepted the install first time. Every later build can now update it in place, as long as it is signed with the same key. Not yet tried: a sync and Run from Android Studio.

**`[FINDING]` The phone is a POCO X5 Pro 5G on HyperOS 2.0, not a POCO X5 on HyperOS 1**
Read over adb: model 22101320G (`redwood_global`), Android 14 (API 34), HyperOS OS2.0.17.0.UMSMIXM, security patch 2025-11-01. That is the last build for this model, so the OS will not change under MilO. The companion device feature is present, Google Play services and Android Auto are installed, and "Install via USB" and "USB debugging (Security settings)" are both on. The design does not change, but the research notes' HyperOS 1 menu names may differ on this phone.

**`[CHANGE]` Builds for the phone are signed with a dedicated key**
Shawn created `~/keys/milo.jks` (PKCS12, alias `milo`) and stored its password in the macOS Keychain as `milo-keystore`. `app/build.gradle.kts` signs the debug and release builds with it, reading the password from the Keychain each time. Debug is signed too because Android Studio's Run button installs the debug build, and that is the build that holds the real trips. Checked on this Mac: the APK's signer is the new key; a missing Keychain entry stops the build with instructions; so does a missing keystore; with `CI=true` or `-Pmilo.signing.debugKey=true` the debug build gets the debug key instead.

**`[FIX]` What the review of the signing change found**
Two independent reviewers checked it before it merged. Three findings changed the code:
- **The configuration cache was keeping the password.** The first version said the password was "in no file". A plain-text search agreed, and was wrong: with Gradle's configuration cache on, every Gradle command saved an encrypted copy under the project's `.gradle` folder, and the key that decrypts it sits in `~/.gradle` behind a constant built into Gradle. A reviewer decrypted an entry to prove it. Anyone holding a backup of the home folder would have had the keystore, the copy and the key. The configuration cache is now off (`gradle.properties`). Measured cost: `spotlessCheck` takes about 1.2 to 1.6 seconds. The old cache entries were deleted.
- **An Android Studio sync would have been handed the password,** and Studio saves what a sync returns in its own cache file. The build now gives a sync a placeholder. Shown with a sync-style run that succeeds without touching the Keychain; not yet confirmed in Studio itself, so after the first real sync check that Run still installs a build signed by the dedicated key.
- **A missing keystore fell back to the debug key without stopping.** After a Mac migration that would build happily, fail to install, and leave Android Studio offering to uninstall the app, which deletes every trip. A missing keystore now stops the build everywhere except CI and the by-name opt-in.

What stays true and worth knowing:
- **The Keychain does not hide the password from other software on the Mac.** The entry was created with the `security` tool, so any program running as Shawn can read it the same way without a prompt. It keeps the password out of files, backups and git.
- **The password is in the Gradle daemon's memory while it runs.** That is unavoidable with Android's signing config, which takes the password as plain text.
- **To check that a re-entered password is right, build:** `./gradlew assembleDebug` fails with "keystore password was incorrect" on a mismatch. Do not use `keytool -list` by hand for this: it never looks at the Keychain, and for this kind of keystore it accepts an empty password.
- **The key was made twice.** The first keystore and Keychain entry never matched, because the hidden password prompts in the app's terminal pane did not receive what was typed.

---

**`[FIX]` Review of the recording core and the triggers (work package 2): what changed, and why**
Four reviewers read parts A and B before the first commit of either. Twenty findings, some of them the same defect seen twice. Every defect below has a unit test that fails on the old code, except where the fix is a document.
- **A truck that no reading can see lost every trip after four minutes** (major). The minute reading counted "not connected" against a trip that a link event had started, although no reading had ever shown that truck connected. On this phone a reading sees the hands-free and audio profiles only. Now such a reading is logged and changes nothing until a reading has shown the truck connected on its present link (`TripEvidence`). The same rule covers the reading a reconcile takes seconds after the link is made, which put a trip into its grace period at the start of a drive.
- **A mistyped address became the truck for good** (major). Adoption stored any lone association. It now needs the address to be among the phone's paired devices, and a stored truck whose association is gone gives way to a lone association for a paired device (`TruckPairingCheck`).
- **The companion callback could start the grace period of an open trip.** With an old hold-off set, releasing it also dropped "the truck is connected". With a trip open the belief now stands.
- **A fix kept a forgotten manual trip alive.** The first fix after a long silence was counted as movement before the overdue no-movement limit was looked at. The deadline is now dealt with first.
- **A trip nobody recorded stayed on the screen.** When Android destroyed the service and refused to start it again, the trip lived on in memory and joined the next drive, 14 hours later in the reviewer's test. Memory is now dropped, and the restart rules judge the stored trip at the next trigger.
- **After a failure in storage the screen and the service kept what was true before it.** Storage is now read again at once, one time.
- **The minute reading had nothing standing in for its timer.** A GPS fix now does, 70 seconds after the last reading. The pacing is a small pure class, `PollPacer`, on the elapsed-realtime clock.
- **On a phone without companion device support the pairing line said only "no truck is paired".** It now says why nothing can be adopted and lists the paired devices.
- **Documents that had drifted from the code:** what is not logged (ADR-002); what `DISCARDED` covers and how Android's own components reach the container (ARCHITECTURE); who handles an unreadable settings file and when the hold-off is read (APP_ENCYCLOPEDIA); what a profile drop does to the hold-off (both, see the decision below).
- **Files over the limit of STANDARDS section 3 were split:** `TruckPairingTest` into the pairing, the check and the stand-ins; the hold-off cases out of the restore test; the service's start intent into `TripServiceIntent`; the settings the rules run with into `TripRuleSettings`; the minute-reading tests into `TripControllerPollTest`. No file is over 300 lines.
- **Tests:** 25 new, 357 in all.

**`[DECISION]` ADR-002 amended again: points 18 to 24**
The reasons the code does not show. All are Shawn's to change, and point 18 changes a rule he was told had been decided.
- **Point 18 goes against "two readings in a row are a disconnect" as it was decided.** The rule assumed a reading can see the truck. Where it cannot, the rule ended every trip and missed the rest of the drive, which is the failure MilO exists to prevent. What is given up: on such a truck (and for the first seconds of any trip) a disconnect that is never reported is not caught by a reading. A trip then stays open until the link event arrives, End is pressed, or the process dies and the restart rules close it. "Seen once on this link" was chosen over the 15-second window after the link event that two reviewers suggested: the window fixed the first seconds, but left a truck without profiles ending its trip the first time MilO was opened during a drive.
- **The rule stops at the trip.** With no trip open, a reading of "not connected" still releases a hold-off at once, seen or not: released wrongly it costs an unwanted restart, never a trip.
- **A press of Start or End, and a process start, still go by the reading alone.** On a truck no reading can see, End sets no hold-off (nothing reads as connected, so nothing restarts either), and a process restarted in mid-drive ends the trip after the grace period. Not changed, because the first drive decides whether this truck is such a truck (device check 32). If it is, the reading itself has to change, not these rules.
- **A reading before the link event is not held against it.** The worker takes note of a trigger before it reads anything for it, also before the reading of a process start. Otherwise the reading of a process the link event itself started would be thrown away as "older than the link".
- **A profile that drops still releases the hold-off** (point 24). A reviewer showed End, then the only connected profile dropping and coming back on the same link, starting a second trip with Shawn still in the truck. The cure would be to doubt a reading, and the hold-off's first rule is that any reading of "not connected" releases it. The documents had claimed the opposite; they now say what happens. Device check 47 looks for it.
- **The fix that stands in for the minute timer is decided in the service,** not in the controller, because it belongs with the timer it stands in for and can use the clock that keeps counting while the phone sleeps. The timer is skipped when a fix stood in less than 30 seconds earlier.
- **Adoption can now replace a stored truck.** Only when the stored truck's own association is gone, so it cannot happen behind Shawn's back in ordinary use: MilO removes every other association when it pairs.
- **The notification tap still starts the service directly** (amendment 10). A reviewer pointed out that HyperOS may reject that in a dead app with Autostart off. Nothing is known until device check 19a; the change, if needed, is to open MilO and let the visible screen start the trip.
- **Not done:** a truck cannot be stored on a phone without companion device support until the pairing screen exists. Building a way round it for a case that may not exist was left; the pairing line now says so.

**`[FINDING]` The reviewed build on the emulator (Android 16, API 36.1)**
A short run after the fixes, to see that the Android-side changes hold together. Nothing here is the phone.
- **A manual trip, start to end:** Start trip, 22 positions 100 m apart at 5-second intervals, End trip. The service reached the foreground with the trigger read back from its start intent, 24 fixes were stored, the trip closed at 1991 m, and the service was destroyed. No `ERROR` or `CRASH` line. The minute timer ran once in that time; with no truck its reading agreed with what was believed, so it left no line, and the fix that stands in for a late timer had no reason to act.
- **Adoption refused, against Android's real companion device manager:** `cmd companiondevice associate` with an address that is not paired with the emulator, then MilO opened: "NO_TRUCK: no truck is paired. Android lists one association, for aa:bb:cc:dd:ee:f0, which was not adopted: …".
- **What this costs:** the emulator has no paired Bluetooth device, so nothing can be adopted on it any more, and the companion path that part B tried there (below) can now only be tried on the phone.
- **Not exercised:** anything with a truck; a late minute timer; a lost service; failing storage. Those are unit tests only.

**`[FINDING]` On Android 17 the trip-start sound of an automatic start would be silent**
From the review, and confirmed today on developer.android.com (Android 17, "Background audio hardening"). An app that targets API 37, as MilO does, may play audio in the background only from a foreground service with while-in-use capability, which a service started from the background (boot, a Bluetooth broadcast, the companion callback) does not have. Playback "fails silently without throwing an exception", so MilO's log would still say the chirp played. A trip started with the Start button or the notification would still chirp, which would hide the cause. The POCO X5 runs Android 14 and is not affected, and nothing was changed. If MilO ever runs on Android 17: play the chirp as the sound of a one-shot notification channel, which the system plays, and test with `adb shell cmd audio set-enable-hardening throw`. This goes against the reason the chirp left the notification channel in the first place (MIUI is reported to switch channel sounds off), so it needs a decision then.

**`[CHANGE]` The triggers: the truck starts and ends a trip (work package 2, part B)**
Everything that tells the trip controller about the truck. Until now a trip could only be started by hand.
- **`platform/bluetooth/`:** `TruckBluetoothReceiver` (the link and the two profile broadcasts; in the manifest, exported, and registered again by the trip service for the length of a trip), `TruckCompanionService` (all three shapes of companion callback, and a reconcile in `onCreate`), `TruckReconcileReceiver` (boot, update), `TruckSignals` (what each broadcast and callback means: plain functions), `Truck` and `PairedTruck` (which device is the truck), `BluetoothTruckConnection` and `ProfileProxy` (the real answer to "is the truck connected?", replacing the stand-in), `TruckPairing`, `PairingStatus`, `CompanionLink` and `PairedDevices` (pairing, as functions for a screen that does not exist yet).
- **Manifest:** `BLUETOOTH_CONNECT`, `RECEIVE_BOOT_COMPLETED`, the three companion permissions, the two `uses-feature` entries (not required), the companion service and the two receivers.
- **No dependency was added.**
- **Changed in the recording core:** `TruckConnectionSource` returns a reading (connected, not connected or unknown, with how it was reached) instead of a Boolean, and `TripEvidence`, `TripWorker` and `TripLogText` follow; `TripController.whenCaughtUp`; the Bluetooth permission in `TripPreflight`, with its line on the home screen; the trip service registers the receiver; a new event category, `PAIRING` (stored by name, no migration). Two changes of behaviour are in the decisions below: where the reconcile at app launch runs, and when a companion start plays the sound.
- **Tests:** 68 new, 332 in all. The decisions about broadcasts and callbacks, the reading made of two profiles' answers, pairing against a stand-in for Android, and the controller with a truck that cannot be read.

**`[DECISION]` Points ADR-002 did not settle, decided while building the triggers**
In ADR-002 as amendments 11 to 17. The reasons the code does not show:
- **"Unknown" is a third answer, and each kind of trigger treats it differently.** Taken for "not connected" everywhere, one Bluetooth profile that fails to answer twice would end a trip in mid-drive. Ignored everywhere, a grace period could never run out while the Bluetooth permission is missing, and the service would record for ever. So: a reconcile or a poll changes nothing; a timer or a button goes by what is believed (a timer only runs while the truck is believed gone, so it still closes its trip); a process start counts it as "not connected". The last one has a price: it releases a hold-off. That needs MilO to be restarted, with the truck unreadable, while Shawn sits in the truck after pressing End. It costs an unwanted restart, not a missed trip.
- **A profile broadcast is a reading, not a connect event,** although it names the truck. As a connect event it would start the trip one step sooner, but it would also count as "a new link" and release the hold-off whenever the truck's hands-free profile reconnects by itself.
- **A new profile proxy for every reading.** The first version kept the two proxies for the life of the process. A kept proxy whose Bluetooth service has gone away (Bluetooth restarted) answers "no devices" without an error, which reads as "the truck is not connected"; two of those in a row end a trip. A fresh one costs two binds a minute during a trip.
- **The receiver reads the settings on the main thread, for one second at most.** A broadcast arrives for every Bluetooth device, the start has to be asked for before `onReceive` returns, and the truck's address is in a file that can only be read asynchronously. This is the one place that goes against "no file is opened on the main thread at process start". After the first read the answer comes from memory. If the read takes longer than a second the broadcast becomes a reading of the truck: slower, never wrong.
- **A manifest broadcast is held open until the controller has caught up** (`goAsync`, 8 seconds at most). A trigger that needs a reading is handled after `onReceive` has returned, and a process with nothing else running can be frozen at that moment; HyperOS is reported to be quick about it. Without this, a profile broadcast could wake MilO and be lost before the truck was read.
- **The reconcile at app launch moved from `HomeViewModel` to `MainActivity.onStart`,** together with the pairing check. In the ViewModel it ran once per Activity, and Android keeps an Activity for days: opening MilO from the recent apps read nothing. Opening MilO is what Shawn will do when a trip has not started.
- **The sound of a companion start waits for the confirmation.** Seen on the emulator: the chirp played, and 15 seconds later the trip was discarded as a false start. The chirp means "connected to the truck", so it must not play for a start that is not yet known to be one. With a real truck the link broadcast confirms within a moment.
- **Every broadcast for another device is logged as "Ignored", with its address.** It proves the receiver was served, which is the question of the first weeks. It adds lines to a log that nothing trims (existing debt below).
- **The companion "disappeared" callback is trusted; Android 16's "Bluetooth connected" event is not.** A wrong disconnect is put right by the next reading inside the grace period. Android does not say which transport connected, so the event is treated like "appeared" and has to be confirmed.
- **An association is adopted when no truck is stored.** It is the only way to try automatic start on the phone before the pairing screen exists. In ordinary use it never happens.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is not declared,** against the list in the work order: STANDARDS §12 says to declare only what built code uses, nothing asks for the exemption yet, and lint flags the permission. One line to add with the permission checklist.
- **Five suppressions, each with its reason in the code:** `InlinedApi` once (the name of a broadcast extra that Android 12 does not send) and `DEPRECATION` four times (the calls that are the only ones Android 12 or 13 has).

**`[FINDING]` The triggers on an emulator (Android 16, API 36.1), with no truck and no Bluetooth device**
The association was made with `adb shell cmd companiondevice associate`, and Android's companion service was told that the device had connected with `cmd companiondevice simulate-device-event`. Read from a copy of the event log.
- **The companion path works end to end on stock Android.** With MilO's process killed, and again with MilO force-stopped: Android started the process, the "appeared" trigger asked for the trip service, the service reached the foreground from the background, a trip opened, and 15 seconds later the timer's reading discarded it as a false start. No grace period, and (after the fix above) no sound.
- **Pairing:** the lone association was adopted the next time MilO came to the front, and `dumpsys companiondevice` showed it observed. It survived an update and a reboot.
- **Reconcile:** "MilO was updated" three seconds after `adb install -r`, with no screen open; "phone booted" about 50 seconds after a reboot; "app opened" each time MilO came to the front; "companion service created" when Android rebound the service after the process had died.
- **Fail closed:** with Nearby devices taken away the reading was "could not be read (MilO is not allowed to use Bluetooth)", the companion trigger was refused by the preflight, the warning was posted, and the home screen named the permission.
- **The exported receiver:** the shell was refused when it tried to send the link broadcast ("not allowed to send broadcast"), and a made-up action aimed at the receiver was dropped without a line. During a manual trip the trip service's own receiver was registered for the four actions, and gone when the trip ended.
- **Surprise, as the research predicted for Android 15 and 16:** told a second time that the device had connected (it still counted as present), Android bound the companion service and sent no event at all. The reading in the service's `onCreate` is what covers this.
- **Surprise:** on Android 15 and later, `BOOT_COMPLETED` is also sent when an app leaves the stopped state, so a force-stopped MilO woken by the companion service logged "phone booted". The source is now worded for both on those versions. The phone runs Android 14, which does not do this.
- **A check that nearly misled:** pressing Home and opening MilO again within a second never stopped the Activity, so no reconcile ran. It needs a few seconds in the background.

**`[FINDING]` What has run nowhere, not even on the emulator**
To be read before the first drive. All of it is in `docs/DEVICE_TEST_CHECKLIST.md`.
- **The reading of the truck's connection that this phone uses.** Up to Android 16 it goes through the hands-free and audio profile proxies. The emulator runs API 36.1, which asks the link instead, and the only other emulator on the Mac (`Pixel_8_Pro`, API 36) has lost its folder and cannot start. If this reading says "not connected" for a connected truck, MilO cannot catch a lost disconnect on that truck. (As first built, every trip would also have ended about four minutes after it started; the review above changed that.)
- **Any Bluetooth broadcast.** The emulator image has no root shell and no second device, so nothing reached `TruckBluetoothReceiver` with a device in it: finding the truck by address, the link trigger, the profile triggers, the broadcast held open.
- **`associate` and the consent dialog,** which need the pairing screen. Why the request goes through the Activity (Android 12 and earlier tie the callback to it) is from the Android source as remembered, not re-read for this change and not runnable here.
- **The companion callbacks of Android 12 to 15,** among them the one this phone sends. The emulator sent Android 16's.
- **Everything about HyperOS:** whether Autostart gates the broadcast, the companion bind, or both; whether a swipe from recents is a force stop; whether the companion feature is reported at all.

**`[DEBT]` A truck can only be paired over adb**
There is no pairing screen. Until it exists the truck is paired by making the association over adb and letting MilO adopt it. Since the review above, another truck is paired the same way: remove the stored truck's association, make the new one, open MilO. On a phone that does not report companion device support there is nothing to adopt, and no truck can be stored at all. Cost to fix: none beyond building the screen, which calls `TruckPairing` as it is. No code tag: there is no line to hang it on.
**Closed 2026-10-05 (work package 3):** the pairing screen exists and changes the truck, and on a phone without companion device support it stores the truck without an association. It has not run on the phone yet.

### 2026-10-03

**`[CHANGE]` The recording core: a trip can be recorded (work package 2, part A)**
Everything that records a trip once one is started. A trip can only be started by hand so far: Bluetooth, the companion device and the boot receiver are part B.
- **`platform/trip/`:** `TripController` (the one entry point, `onTrigger`, and its inbox), `TripWorker` (one piece of work at a time: rules, storage, event log, service), `TripEvidence`, `TripLedger`, `TripServiceLink`, `TripService` (foreground, type location), `TripServiceStarter` (preflight, then `startForegroundService`), `LocationRecorder`, `TripNotifications`, `TripStartSound`.
- **`platform/system/TripPreflight`, `platform/car/AndroidAutoWatcher`, `platform/bluetooth/TruckConnectionSource`** with a stand-in that always answers "not connected". Part B replaces the stand-in in `AppContainer`.
- **`core/trip/`:** the three rules below, in `HoldOffRules`, `OpenTripRules` (split out of `TripStateMachine`, which had reached the file size limit), `LostDisconnectDetector` and `AndroidAutoHoldGuard`; `TripProgress` for the running distance.
- **Home screen:** the placeholder cards are gone. It shows the trip in progress, why the last start failed, and one Start trip / End trip button, through `HomeViewModel`.
- **Manifest:** fine, coarse and background location, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`, the service, and the `<queries>` entry `CarConnection` needs. No Bluetooth, companion or boot entry.
- **`tools/make_trip_start_chirp.py`** and its output, `res/raw/trip_start_chirp.wav`: 1.4 s of sine sweeps, warbles and beeps, made from nothing else. The script has no randomness, so it reproduces the committed file byte for byte.
- **Dependencies added,** each checked against its live Maven metadata today: `play-services-location` 21.4.0, `androidx.car.app:app` 1.7.0 (1.8.0 is still a release candidate), `lifecycle-viewmodel-compose` 2.11.0, and for tests `kotlinx-coroutines-test` 1.11.0.
- **Settings:** the hold-off is stored as the time End was pressed (`auto_start_held_off_since_ms`), replacing the boolean. No build has been on the phone, so nothing had to be migrated.

**`[DECISION]` ADR-002 amended: three rules decided before the code, and what was decided while writing it**
The three rules came from the orchestrating session and are in the ADR's text and Amendments (5 to 7). The reasons that the code does not show:
- **The hold-off has three releases because each of the first two can be missed.** This closes the finding below, "The hold-off can swallow a trip". The time limit is applied only at a reading of the truck: releasing starts a trip in the same step, and that must not rest on what was believed 12 hours earlier. End pressed again restarts the hold-off from that press; otherwise an old hold-off at its limit would let the press itself start a trip.
- **The companion callback is believed only when idle, and does not mark the truck as seen.** A trip it opens is stored as started by the truck with the truck not seen, which is also how a restart recognises it. Believed while a manual trip is running, a callback from a head unit that is merely in range would mark the truck seen, the next readings would show no truck, and the trip would end in the middle of a drive: in exactly the case the Start button exists for.
- **The count of "not connected" readings is kept out of the state machine.** Those rules take levels, and telling them the same thing twice changes nothing. A counter is the opposite, so it is a small class beside them.

Decided while building, because ADR-002 did not settle them. All are Shawn's to change.
- **A trip row is opened only with the service in the foreground,** and the controller enforces it: an event that would leave a trip open while the service is down is not acted on, and the service is started with the trigger in its intent. The other way round (open the row, then start the service) leaves a trip that claims to be recording when Android refuses the service.
- **A connect or a press of Start asks for the service on the trigger's own thread,** before the stored state has even been read. The price: a service started for nothing (the hold-off was set) that stops again a moment later.
- **The preflight is the same for every start,** including "Allow all the time" for a press of Start with the app open. The Bluetooth permission is not in it yet: the manifest does not declare it until part B.
- **A refused start is not retried.** The notification is the way back.
- **Android Auto's "connected" is believed for 12 hours at most once the truck has gone,** and asked for again every minute. A limit based on standing still was rejected: if Shawn's truck drops Bluetooth while Android Auto runs over the cable, a 30-minute stop would end the trip and nothing would start the next one.
- **Android Auto does not confirm a companion start.** Amendment 7 names what confirms it, and it was taken literally.
- **The trip's timers are coroutines and no wake lock is held** (see the debt below).
- **Event-log lines are dated when they are written.** A trigger that has to start the service is handled a second after it fired; dated by the trigger, "trip started" would sit above "service in the foreground". The trip row keeps the trigger's time as its start.
- **Not logged:** GPS fixes, and a once-a-minute reading that only confirms what was believed. Either would add a line every few seconds or every minute to a log that nothing trims yet.
- **A fix's time of day is worked out from the phone's clock and the fix's age,** not taken from the fix. Trips are cut by comparing fix times with event times, and both must come from one clock.
- **The trip-start sound asks for no audio focus and does not check for a call.** The brief asked for a notification-type sound on the phone's speaker, nothing more.
- **The notification icon is a third drawable outside the design tokens.** Android draws it; Compose tokens do not reach it.
- **One lint check is suppressed, in one place:** `UseKtx` on `Uri.parse` in `TripStartSound`. Lint offers an extension from `core-ktx`, which MilO does not declare; one call does not justify the dependency.
- **A new event category, `LOCATION`.** Stored by name, so no migration.

**`[FINDING]` The recording core on an emulator (Android 16), not on the phone**
The debug build ran on the Medium Phone emulator, headless, with its audio output off. Permissions were granted with `adb shell pm grant`, positions fed with `adb emu geo fix`, and the databases read on the Mac.
- **A manual trip, start to end.** Start trip, 15 positions 100 m apart at 5-second intervals, End trip. Stored: one `FINISHED` trip of 1397 m against 1401 m fed, with start and end coordinates, and 34 fixes under its id. The home screen and the notification (low-importance channel, running timer) followed the distance while it grew. After End trip the service and the notification were gone. `points.db` and its three side files exist (device check 9, on the emulator).
- **The sound path ran.** Android's audio log shows MilO's player with usage `NOTIFICATION`, started, routed to one device, 22 050 Hz mono, stopped after 1.6 s and released. Nothing was heard: the emulator had no audio output.
- **A failed start.** With no permission, with location only "while in use", and with location switched off, Start trip opened no trip, the home screen listed the reason, the log had it, and the high-importance notification appeared. A tap on it started a trip.
- **A crash in mid-trip with MilO in the background** (`am crash`): Android brought the process and the service back within a second, the same trip carried on with its distance worked out again from the stored fixes, and no second sound played.
- **The location permission revoked in mid-trip:** Android killed the process and restarted the service; `startForeground` threw `SecurityException`; the service logged it, posted the warning and stopped, without crashing. With the permission back, a tap on the warning carried the same trip on.
- **The app updated in mid-trip** (`adb install -r`): the service was not restarted. Opening MilO picked the same trip up through the service. That trip, interrupted three times, closed at 1397 m against 1401 m fed.
- **A bug the run found, fixed in this change:** after such a pick-up the home screen kept showing "MilO could not start this trip" beside the trip in progress. The failure was only cleared when a trip row was opened, not when one was carried on.
- **Start-up disk access, measured with StrictMode for one run** (the probe is not in the code): building the trip controller, the two Room databases and the settings store touches no file on the main thread. The only main-thread disk access at process start is in `buildCrashFileStore`, from the foundation (74 ms on the emulator, the first time the no-backup folder is looked up). Left alone here; it contradicts the `AppContainer` comment that says building the container opens no file.
- **Surprise:** with only "Allow all the time" revoked in mid-trip, the restarted service was still allowed into the foreground and kept receiving fixes. Android seems to carry the "while in use" allowance of the original start over to the restart. Do not rely on it.
- **Surprise:** the emulator reports location "not available" and "available" again around every single fix. Logged as it came, that was 60 of the first 80 lines. `LocationRecorder` now logs a loss only when it has lasted 10 seconds, and the return only after a logged loss.
- **Not exercised:** anything with a truck (grace period, the minute reading catching a lost disconnect, the hold-off, a companion start); Android Auto connected; the 30-minute no-movement end; a custom sound; a start refused by Android from the background with every permission in place; silent and vibrate; anything on HyperOS.

**`[DEBT]` The trip's timers can fire late while the phone sleeps**
The grace period, the 15-second confirmation and the once-a-minute reading are coroutine delays inside the service, as the research recommends (not WorkManager, not an alarm). A coroutine delay does not count time the phone spends asleep, and no wake lock is held. What limits the harm: the trip rules judge a late reading by the stored deadline, so a trip that runs out of grace is still cut at the right moment; and a GPS fix stands in for a late timer, for a deadline once it is 10 seconds overdue and (since the review of 2026-10-05) for the minute reading 70 seconds after the last one. What is left: with no fixes arriving (the phone indoors after the truck has gone), a trip can stay open, and the service running, past its grace period until the phone next wakes. And a disconnect that was never reported is cut when the second minute reading is taken, not at a stored deadline, so if those readings are late the walk away from the truck is counted. Device check 27 measures it with the one timer part A can test. Cost to fix if the phone shows it is needed: small, a partial wake lock for the length of a trip, plus the `WAKE_LOCK` permission that ADR-002 lists as not needed. Tagged `TODO(debt)` in `TripService.kt`. The cheap part of the wall-clock debt below is done: a timer's reading is dated no earlier than the deadline it was set for.

**`[DEBT]` Permissions have to be granted by hand**
MilO declares the location and notification permissions but never asks for them: that belongs to the permission checklist, which is not built. Until then a fresh install records nothing until the permissions are set in the phone's settings or with `adb`; the home screen says what is missing. Cost to fix: none beyond building the checklist. No code tag: there is no line of code to hang it on.
**Closed 2026-10-05 (work package 3):** the Setup screen asks for every permission. It has not run on the phone yet.

**`[CHANGE]` Pre-commit hook switched on; CI proven on GitHub**
Shawn installed gitleaks 8.30.1 and `core.hooksPath` now points at `.githooks`, so every commit runs the staged-secret scan and then `spotlessCheck`. The two commits made before the hook existed were scanned afterwards across all branches: no leaks. The CI workflow's first run, on the skeleton PR, passed. Android Studio is updated to 2026.2; the project has not been opened in it yet.

**`[FIX]` Review of the phase 1 foundation: what changed, and why**
The foundation below was reviewed before its first commit, and these defects were fixed in the same change. Each has a unit test that fails on the old code.
- **A trip was closed on a stale belief.** An overdue grace period was closed by whatever event came next, including a GPS fix. A fix handled one second before the timer's reading cut a drive in two at a false disconnect, and the two minutes in between belonged to neither trip. Now only an event that brings a fresh reading of the truck closes a trip on a timeout (ADR-002 amended, see the decision below).
- **Yesterday's trip could swallow today's.** A connected truck carried an open trip on however old it was. A late reconnect and a late restart now close the old trip and start a new one.
- **Start during the grace period was swallowed.** It now ends the waiting trip and starts a manual one.
- **A trip was cut by filtering every point on its time of day.** With the phone's clock ten minutes fast for the first part of a 6 km drive and corrected later, the trip measured 900 m. The cut is now by stored order: only the points at the end of the list that are later than the cut-off are dropped.
- **One bad fix inside the speed limit was counted out and back.** A fix 200 m away while the truck stood still added 400 m, enough on its own to turn a parked connection into a kept trip. `DistanceCalculator` has a fourth rule: a counted step that the next fix walks back is taken back.
- **The exit-record import marked its place once per batch.** A process that died in mid-import brought the whole batch in again at every start. The mark now moves after each record.
- **Gathering evidence could stop the app for good.** An unreadable settings file made the start-up import throw, which ended the process at every start; the only way out was clearing the app's data, trips included. Each step now runs on its own, and a failure is written to the event log as `ERROR` (or to a crash file if the log is what failed). This changes the "corrupt settings file" decision below: the file is still never reset, but it no longer crashes the start.
- **Storage was touched outside `data/`.** `CrashFileStore` moved from `platform/diagnostics/` to `data/crash/`, and the DataStore is built by `buildSettingsStore` in `data/settings/`, not in the `AppContainer`. The rule in STANDARDS §3 holds again as written.
- **Small ones:** `setTruck` refuses a blank name and `setLastProcessExitImportedAtMs` a negative time, as the documents already claimed; the `PointsDatabase` comment names all three side files; `docs/DEVICE_TEST_CHECKLIST.md` now exists, with the checks this package needs on the phone.
- **Tests that could not fail.** Four deliberate breakages of the jump filter passed the whole suite: timing the speed with the wall clock, re-anchoring after two rejected fixes, not requiring them to agree, and not clearing them on a good fix. `DistanceCalculatorOutlierTest` now fails on each of them.

**`[DECISION]` ADR-002 amended: when an open trip may not be carried on**
ADR-002 said both "Grace ends: the trip is closed" and "truck connected means carry on recording the same trip", and gave the second no time limit. The safe reading was implemented and written into the ADR. The two limits were chosen in review without Shawn and are his to change; both are constants in `core/trip/TripState.kt`.
- **`LATE_CHECK_TOLERANCE_MS`, 30 seconds.** A "connected" reading up to this long after the grace deadline still carries the same trip on, because the reading taken when the timer fires always arrives a little late. Too small, and a late timer at a false disconnect splits one drive and loses up to two minutes of it. Too large, and a stop barely longer than the grace period is joined to the next drive.
- **`RESTART_GAP_LIMIT_MS`, 30 minutes.** After a restart, a trip that was recording carries on only if its newest stored point is younger than this. Inside the limit the gap is counted as a straight line, which is real truck distance: the truck is wherever the phone next connects to it. Beyond it the trip's times would be wrong, and from phase 2 Business or Personal is decided by a trip's start time. The stricter choice, the grace period itself, would lose the driving in the gap every time a cleaner kills MilO for more than two minutes in mid-drive.
- **Start during the grace period ends the waiting trip.** The other way, keeping the trip and switching it to the manual rules, needs a new stored state. The cost of the way chosen: what was driven between the truck being found gone and the press is in neither trip.
- **The 30-minute no-movement rule also needs a fresh reading of the truck.** Same principle: "no truck connected" has to be a fact, not a memory.
- **Not covered:** a process that stays alive through a lost disconnect still sees one long trip. That needs the service to re-read the connection now and then (see the hold-off finding below).

**`[DEBT]` Deadlines and the trip cut-off are times of day**
The grace deadline, the no-movement limit and the moment a trip is cut at are wall-clock times, because they are stored and must survive a reboot. If the phone corrects its clock by more than the time left: a clock set back during the grace period makes the grace period that much longer, and lets the walk away from the truck be counted (408 m in the review's simulation); a clock set forward by 31 minutes can end a moving manual trip. A trip that started on a wrong clock keeps the wrong start time, which can be later than its end. Left because it is rare (a correction of minutes, inside a two-minute window) and the cure touches every event, the stored grace columns and the points: elapsed realtime carried beside each wall-clock time and used whenever the phone has not rebooted. Cost to fix: medium. A cheap part for the next work package: the controller's timer runs on elapsed time, so when it fires it can stamp its reading no earlier than the deadline it was set for. Tagged `TODO(debt)` in `TripState.kt`.

**`[FINDING]` What the distance calculation still gets wrong (simulated, not this phone)**
Kept visible so the first test drives are read against it. The raw points are stored, so every trip can be recalculated when the rules improve.
- **Turns are cut.** Distance is the straight line from where it was last counted, once that line is longer than twice the two fixes' stated error. On a simulated town route (a right-angle turn every 300 m, about 25 km/h) the result is short by 2 % with 5 m fixes (all of it lost to sampling every 5 s), 4 % with 10 m fixes and 11 % with 25 m fixes. `DistanceCalculatorTurnsTest` pins these. The review's rougher route with slow corners gave 2 %, 5 % and 9 %, and twice that with a turn every 150 m.
- **The end of a trip is short by up to the threshold:** 20 m, 40 m or 100 m for 5 m, 10 m or 25 m fixes.
- **Some bad fixes are still believed:** a wrong first fix less than about 700 m off, and one wrong fix straight after a GPS gap (a 60-second tunnel, then a fix 600 m to the side: 500 m too much). Rule 4 does not catch these, because the next fixes do not walk back to where counting stood.
- **Candidates for the cure,** to choose from real points: use the phone's own speed reading for "parked" in place of stated accuracy (ends the corner cutting); hold a fix that follows a gap until the next one agrees with it; and, when the location request is built, check whether the fused provider can be told not to hand over an old cached position first.

**`[CHANGE]` Phase 1 foundation: storage, the trip rules, crash and kill capture**
The first product code. The app still opens on the placeholder home screen: no permission, service, receiver or new screen was added, and nothing calls the trip rules outside their tests.
- **`app/`:** `MiloApplication` (registered in the manifest) creates one `AppContainer`, the manual dependency injection.
- **`data/`:** two Room 3 databases on the bundled SQLite driver, `milo.db` (trips, event log) and `points.db` (raw GPS points), with their schemas exported to `app/schemas/` and committed; a repository over each DAO; `SettingsStore` on DataStore Preferences; `CrashFileStore` for the crash files. The tables are in ARCHITECTURE §6.
- **`core/trip/`:** ADR-002's trip rules as a pure function (`TripStateMachine`), the point filter and distance calculation (`DistanceCalculator`, haversine), and `TripClosing`. No Android imports.
- **`platform/diagnostics/`:** an uncaught-exception handler that hands the crash to the crash file store, and a start-up step that copies crash files and the system's process-exit records into the event log.
- **Dependencies added,** each re-checked against its live Maven index today: Room 3.0.3 (runtime, KSP compiler, Gradle plugin), KSP 2.3.12, `sqlite-bundled` 2.7.1, DataStore Preferences 1.2.1, `kotlinx-coroutines-core` 1.11.0. Tests still need only JUnit: the rules are plain functions, and DataStore runs on the JVM once given a file.

Why, where the code does not say it:
- **The trip rules take levels, not edges.** An event says "the truck is connected", never "the truck just connected". The rules keep what they were last told and, after every event, make the trip agree with it. That is what makes a repeated, missing or reversed event harmless, and it is checked by a test that feeds 240 000 random events and verifies the state after each one.
- **Timers are not effects.** The rules expose "when must I look again" as a function of the state. A timer lost with a killed process is set again from the restored state, and when it fires the caller sends a fresh reading of the truck's connection, which is ADR-002's "read the profile state again before closing".
- **Crashes go to a file first.** A dying process can do one blocking file write. A database write needs threads that may be gone, and the database may be what crashed. The file is under `no_backup/` so a restore can never replay an old crash.
- **`MiloApplication` opens no database or settings file on the main thread.** The container creates them lazily, because the process will soon be started by trip triggers with seconds to begin recording.

**`[DECISION]` Points ADR-002 and the brief left open, settled while building the foundation**
Each took the safest reading. All are Shawn's to overrule.
- **A trip is cut where the truck was found gone.** ADR-002 says a trip closes "at the last recorded point, not at the end of the grace period", but recording has to continue during the grace period or a short Bluetooth drop would lose distance. So the grace period's start is stored (`graceStartedAtMs`, a column the brief did not list), and a trip that runs out of grace ends at its last point up to that moment. Later fixes stay stored and count only if the truck comes back.
- **A discarded trip is marked, not deleted.** `DISCARDED` is a status. The distance thresholds are untested on this phone, and a real trip deleted because of a wrong distance could not be got back.
- **The Start and End events carry a fresh reading of the truck's connection.** The hold-off after a manual end must not be set from a stale belief, or it would swallow the next real trip.
- **End with no trip open, truck connected, also sets the hold-off.** Otherwise the fresh reading would start a trip on the press of End.
- **A button press first brings an open trip up to date.** Start is not swallowed by a trip whose grace period has already run out, and End does not stretch a forgotten trip to the moment of the press.
- **Android Auto also cancels a running grace period and pauses the 30-minute no-movement rule.** ADR-002 only says it stops the grace period from starting.
- **A trip that was recording when the process died, and finds the truck gone at restart, starts its grace period then.** ADR-002 lists three restart cases and not this one.
- **The no-movement rule ends the trip where it last moved,** not 30 minutes later.
- **The 30-minute limit and the distance thresholds are constants, not settings.** Only the grace period and the minimum trip distance are in the settings store, as the brief says.
- **Crash and kill capture lives in a new `platform/diagnostics/`.** ADR-002's package list has no place for it. The crash files themselves are storage, so `CrashFileStore` is in `data/crash/`.
- **`points.db` sits in the standard databases folder,** not under `no_backup/`, so phase 4 can still include it in a phone-to-phone transfer while excluding it from cloud backup.
- **A corrupt settings file is not reset.** A silent reset would drop the truck pairing, and automatic start would stop with no trace. First written as "it crashes at start-up"; the review found that meant a crash at every start, so the start-up import now logs the failure and carries on (see the fix entry above). Each later reader of the settings must handle an unreadable file itself. Revisit when the permission checklist can show "no truck paired".

**`[FINDING]` The hold-off can swallow a trip when a disconnect is never seen**
ADR-002 holds automatic start off after a manual end "until the truck next disconnects". If MilO is dead at that disconnect and the broadcast is dropped, the hold-off is still stored at the next connect, and that trip does not start. The rules cannot fix this while staying purely level-based: the only evidence would be the connect event itself, and ADR-002 says events are never assumed to come in pairs. Implemented as written. Options for Shawn: let a connect *event* (not a reconcile) release the hold-off, or give the hold-off a time limit. A related gap: a lost disconnect during a trip leaves it open until something reads the connection again, so the service should re-read it periodically.

**`[FINDING]` "Count distance only after 10 m" is not enough for a parked truck**
Simulated: one parked hour of fixes scattered the way a stated accuracy of 5 m implies. The 10 m rule alone added 674 m of phantom distance (356 m once the out-and-back rule from the review was in place: still enough to be kept as a trip). So movement must also exceed twice the two fixes' own stated error; with that, the same hour adds 0 m, and so do 100 such hours. The price is paid on turns and at the end of a trip: see the finding on what the calculation still gets wrong, above. The numbers come from a simulation, not from this phone: tune `DistanceLimits` from the raw points of the first test drives. The phone's own speed reading is stored for that purpose and not used yet.

**`[FINDING]` First run of the Android-side code, on an emulator (Android 16), not on the phone**
The debug build was installed on the existing Medium Phone emulator image, started read-only so nothing was saved to it. An induced crash (`am crash`), a force stop and a background kill each showed up in the event log at the next start, once, with the stack trace or the system's description. Nothing has run on the POCO X5 yet, and `points.db` was never opened in this run because nothing writes a point. Two things learned:
- A force stop is reported as `USER_REQUESTED` with the description `[FORCE STOP] ...`, not as `USER_STOPPED`, which means the Android user profile was stopped. The description text is what tells kills apart, as the research said it would for Xiaomi's cleaners.
- Room keeps a `.lck` file beside `milo.db` as well as `-wal` and `-shm`. Phase 4's backup rules must account for it.

**`[FINDING]` A raw point can refer to its trip only by id**
ARCHITECTURE §6 carried this as an inference to check when the two databases were built. It holds: SQLite enforces a foreign key only between tables in the same database file. `tripId` is a plain indexed column. Nothing deletes a trip today; code that ever does must delete the trip's points itself.

**`[FINDING]` Nothing in the gate flags an unused import**
The Kotlin compiler does not warn about one, and ktlint (through Spotless, `android_studio` style) left three in place after a file was split. They were found by hand. Android Studio shows them greyed out.

**`[DEBT]` The event log is never trimmed**
Every start writes at least one entry and trip events will add more. The table shares `milo.db` with the trips, and that file must stay far below the 25 MB backup cap. Left out because nothing is backed up until phase 4 and the right retention period is not known yet. Cost to fix: small, a delete-older-than query and a place to call it. Must be done before backup is switched on. Tagged `TODO(debt)` in `EventLogRepository.kt`.

**`[DECISION]` Trip detection design (ADR-002)**
Every trigger (companion service, Bluetooth receiver, reconcile at boot, update and launch, manual buttons) calls one idempotent function on an app-wide `TripController`. Events are treated as hints: the controller re-reads the real connection state rather than trusting that connects and disconnects arrive in pairs. The trip rules are a pure Kotlin state machine in `core/trip/` so they can be tested without a phone. Two rules that are not in Shawn's brief and were added to make the design safe: ending a trip by hand while the truck is still connected holds off automatic start until the truck next disconnects, and a manual trip with no truck connected ends after 30 minutes without movement. An always-on service is the fallback if starting from a dead process proves unreliable on HyperOS; it is not built, and needs Shawn's agreement. See `docs/adr/ADR-002-trip-detection.md`.

**`[FINDING]` Android Auto will probably not list a sideloaded MilO on the truck**
The dedicated research run and its verifier agree on what Google documents: "Unknown sources" in Android Auto's developer settings does not apply to Car App Library apps, and on a real head unit such an app must come from a trusted store. A private Play internal-sharing link or internal test track counts and needs no review. Community reports conflict on whether a sideloaded build still appears in practice. The screen is small, so it is built in phase 1 and tested in the desktop head unit and then on Shawn's truck. If it does not appear there, the choices are a private Play install (a Play Console account, and every build delivered through Play instead of Android Studio), a media-app style workaround, or dropping the screen. `CarConnection`, which holds a trip open while Android Auto is connected, works either way. (`2026-10-03-android-auto-screen.md`)

**`[FINDING]` Installing from Android Studio on HyperOS has its own prerequisites**
Besides USB debugging, HyperOS needs "Install via USB" and "USB debugging (Security settings)" switched on. Turning those on normally requires a Xiaomi account signed in, a SIM inserted, and mobile data on with Wi-Fi off. Each install then shows a prompt on the phone with a short countdown; missing it fails with `INSTALL_FAILED_USER_RESTRICTED`. Not yet tried on this phone. (`2026-10-03-miui-dev-bluetooth-audio.md`)

**`[CHANGE]` `CLAUDE.md` brought in line with the Kotlin standards**
Edited by the assistant after Shawn chose the Kotlin rewrite: the last two doc pointers; the done-checklist (Kotlin warnings as errors, Lint, Spotless and unit tests instead of TypeScript strict; `Log`/`println` instead of `console.log`; phone / Android Auto instead of mobile / web); the platforms question (now "which surfaces"); the lockfile line (now the version catalog and the wrapper checksum); the secrets section (no `.env`, no sign-in: the only secrets are the signing keystore and its passwords); and the Renovate-or-Dependabot line (Dependabot). The rules themselves are unchanged.

**`[CHANGE]` Project skeleton: Gradle build, quality gates, design system, CI files**
The first code. `./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug` passes from the terminal on AGP 9.4.1, Gradle 9.8.0 and Kotlin 2.4.20, so the fallback in ADR-001 was not needed. Not done yet: a sync in Android Studio (still 2025.3, too old for this project), a CI run, and an install on the phone. What exists:
- **Build:** one `:app` module, versions in `gradle/libs.versions.toml`, the wrapper pinned by checksum, Java 17 pinned in `gradle/gradle-daemon-jvm.properties`.
- **Code:** `app/MainActivity.kt` and `app/MiloApp.kt`, a placeholder `feature/home/HomeScreen.kt`, and `core/util/DistanceFormat.kt` with a unit test. There is no `Application` class, no `AppContainer`, no navigation, no permission, and nothing in `data/` or `platform/`.
- **Design system** (`core/designsystem/`): tokens in `theme/` (`Color.kt`, `MiloSpacing.kt`, `Type.kt`, `Shape.kt`, applied by `MiloTheme.kt`) and three base components in `component/`: `PrimaryButton`, `SectionCard` and `StatusRow` (its icons are in `StatusIcons.kt`). It is documented in APP_ENCYCLOPEDIA under Design system.
- **Repo files:** `.github/workflows/ci.yml`, `.github/workflows/dependency-graph.yml`, `.github/dependabot.yml` and `.githooks/pre-commit`. They do nothing until the GitHub repo exists, `core.hooksPath` is set and gitleaks is installed.

Choices that are not obvious from the code:
- **Three lint checks are disabled:** `GradleDependency`, `AndroidGradlePluginVersion` and `OldTargetApi`. Each fires only because something newer was released. With lint warnings as errors, that would turn a green build red overnight.
- **Native libraries are packaged unstripped** (`keepDebugSymbols` in `app/build.gradle.kts`). AGP 9.4.1 looks for NDK 28.2.13676358 to strip the prebuilt `libandroidx.graphics.path.so`, and this Mac has only 27.1.12297006. Without the setting every build prints "Unable to strip the following libraries".
- **Backup is off in two places.** `allowBackup="false"` stops cloud backup but not phone-to-phone transfer on Android 12 and later, so `data_extraction_rules.xml` also excludes every storage area from both, including the device-protected ones. Phase 4 replaces it with real rules.
- **Gradle scripts follow the same warning rule as app code,** through `org.gradle.kotlin.dsl.allWarningsAsErrors` in `gradle.properties`. `allWarningsAsErrors` in the app module does not cover them, and a build script is where a deprecated AGP or Gradle call shows up first.
- **`MainActivity` lives in `app/`,** and so will the `Application` class. The root package is not one of the six in STANDARDS §3, so no class sits in it.

**`[DECISION]` Rules settled while reviewing the skeleton**
- **GitHub Actions are pinned to full commit SHAs,** with the release in a trailing comment. The first draft used tags such as `v7`, which an action's owner can move to different code. It also left Dependabot's minor-and-patch group for actions with nothing to match.
- **The dependency graph workflow stays, pending Shawn's objection.** It arrived with the skeleton without a recorded decision. It is kept because STANDARDS §12 promises Dependabot alerts, and for Gradle those need the full resolved dependency list (research K11, K19). It runs on push to `main` only and holds the only write permission in the workflows. Shawn must switch on the dependency graph and Dependabot alerts in the repo settings. To drop it, delete the file and its lines in ADR-001 and STANDARDS §12 and §14.
- **No `CHANGELOG.md`.** The template asked for one. This log is already the dated record.
- **The device test checklist will be `docs/DEVICE_TEST_CHECKLIST.md`,** written in phase 1 with the first behaviour that can only be tested on the phone.
- **The Android Auto screen has no ViewModel.** The first ARCHITECTURE diagram routed it through one. The flow rule names Composables only, and the research has the car screen read one app-wide trip state directly. The diagram is redrawn; the shape of that shared state is still ADR-002.

**`[FINDING]` `spotlessCheck` can pass on old rules after an `.editorconfig` edit**
Reproduced in a scratch copy: `max_line_length` was changed from 100 to 40, a limit the existing source files break. A plain `./gradlew spotlessCheck` re-ran the tasks and passed. So did a run with `--rerun-tasks --no-configuration-cache --no-build-cache` in the same Gradle daemon. Only a fresh JVM failed, as it should. A daemon that is already running keeps applying the rules it loaded first. The pre-commit hook runs the plain command, so it has the same blind spot. The rule, written in `.editorconfig` and STANDARDS §4: after any `.editorconfig` change run `./gradlew spotlessCheck --no-daemon --rerun-tasks --no-configuration-cache --no-build-cache`, and treat CI as the authority.

**`[FINDING]` Kotlin `extraWarnings` fails on Room 3's generated code**
The research (K4) recommended `extraWarnings` next to `allWarningsAsErrors`. Tested in a scratch copy: the skeleton's own code compiles clean with both. With Room 3.0.3 and KSP 2.3.12 added, the generated `_Impl.kt` files fail with "Redundant visibility modifier", because warnings are errors. So `extraWarnings` stays off. The research names `-Xwarning-level` as the way to lower a single diagnostic; that route is untested.

**`[FINDING]` AGP 9.4.1 triggers a Gradle deprecation notice that MilO cannot fix**
A build that configures the project ends with "Deprecated Gradle features were used in this build, making it incompatible with Gradle 10". `--warning-mode all` shows one cause: `Configuration.setVisible(boolean)`, called from AGP's own `BasePlugin` and `SourceSetManager`. Gradle says the method is "scheduled to be removed in Gradle 11". None of MilO's scripts call it, and only a newer AGP removes it. It matters when Dependabot proposes a new Gradle major: check that the pinned AGP supports it first.

**`[FINDING]` Nothing automated will prompt for a new `targetSdk`**
The `OldTargetApi` lint check is disabled, and Dependabot updates libraries and plugins, not the SDK numbers in `app/build.gradle.kts`. `compileSdk` gets forced now and then: a library update that needs a newer one fails the build. Nothing does the same for `targetSdk`. Checking for a new stable Android version is a manual step, best done in the cleanup pass before each phase hand-over (STANDARDS §16).

**`[DEBT]` `StatusRow` has two states; the permission checklist needs more**
`StatusRow` takes a Boolean: met or not met. The checklist also needs "looks on / looks off / unknown" for Autostart and "confirm you set this" for settings the app cannot read (APP_ENCYCLOPEDIA, Permission checklist). It was left at two states because nothing uses the others yet, and they need new icons, colours and strings, which is a deliberate design-system addition. Cost to fix: small. Replace the Boolean with a status type when the checklist is built in phase 1. Tagged `TODO(debt)` in `StatusRow.kt`.
**Closed 2026-10-05 (work package 3):** `StatusRow` takes a `RowStatus` with four states, and the tag is gone from the code.

**`[DECISION]` Stack and tooling fixed for native Kotlin (ADR-001)**
Kotlin 2.4.20 on AGP 9.4.1, Gradle 9.8.0 and JDK 17. One `:app` module. Compose with Material 3, Navigation 3, Room 3, DataStore, and manual dependency injection through an `AppContainer`. Quality gates: Kotlin warnings as errors, Android Lint with warnings as errors, Spotless driving ktlint, and a plain `.githooks/` pre-commit script (gitleaks, then `spotlessCheck`). Left out on purpose, each with its reason in the ADR: detekt, Robolectric, Hilt, Sentry, staging and prod, a Gradle lockfile, dependency verification. ENGINEERING_STANDARDS and ARCHITECTURE were rewritten to match, which finishes the rewrite the "Standards to be rewritten" entry below left open. Three points that are easy to miss:
- **Dependabot replaces the Renovate named in that entry below.** It is native to GitHub, free on private repos, and handles the version catalog and the Gradle wrapper.
- **No lockfile, although the original standards template asked for one.** Update bots cannot regenerate one for an Android project, so it would mean hand work on every update. Exact pins in `gradle/libs.versions.toml` stand in for it.
- **AGP 9.4.1 with Gradle 9.8.0 is one step past what JetBrains documents for Kotlin 2.4.20.** The first build is the real test. The fallback is AGP 9.3.3 with Gradle 9.7.1. (Result: it built with no fallback. See the skeleton entry above.)

Revisit detekt when 2.0.0 is stable. See `docs/adr/ADR-001-stack-selection.md`.

**`[FINDING]` The research facts that will bite hardest**
All from `docs/research/`. None is confirmed on the POCO X5 yet.
- **Background location ("Allow all the time") is mandatory for automatic start.** On Android 14 and later, a location foreground service started from the background without it throws `SecurityException`. (`2026-10-03-fgs-background-start.md`)
- **Triggers must be redundant and feed one idempotent start function.** CompanionDeviceManager and the Bluetooth ACL broadcast come from the same Bluetooth-stack event, and each has gaps: truck already connected at boot or after an update, phone not yet unlocked, app force-stopped. (`2026-10-03-cdm-presence.md`)
- **Trip logic must be level-triggered, and Bluetooth receivers must be exported.** Events can be dropped, duplicated or reversed, so every event means "re-read the real connection state". A non-exported receiver never gets the broadcast, and a trip never ends. (`2026-10-03-fgs-background-start.md`)
- **On HyperOS, Autostart gates every start of a dead app, and it is off by default.** Decompiled system code shows broadcasts dropped and service starts and binds rejected while it is off, which would block both the Bluetooth receiver and the CompanionDeviceManager path. It is the likely reason Shawn's current app misses trips. Not tested on this phone. (`2026-10-03-miui-background-limits.md`)
- **The Android Auto screen may not show up on the truck.** Google's docs say the "Unknown sources" option does not cover Car App Library apps. This contradicts the "Unknown sources" assumption in the Android Auto entry below. An independent check has since confirmed that reading of the docs; see the Android Auto finding at the top of this date. (`2026-10-03-android-auto-screen.md`)
- **Two quiet ways to lose data.** Auto Backup stops completely above 25 MB, which raw GPS points in the main database would reach within about a year. And the debug keystore is per Mac: a different key means an uninstall that deletes every trip and blocks the backup restore too. (`2026-10-03-pdf-email-backup.md`)

**`[DECISION]` Research notes live in `docs/research/` as dated evidence, not as living documents**
Each file is named by date and topic and records what its sources said on that day, with a confidence level on each finding. The header of each file says whether an independent verifier re-checked it; the Kotlin standards, location and car, and PDF/email/backup files were not. They are the evidence behind the ADRs and are not edited to keep up with the world. Current truth lives in the four companion docs and the ADRs. When a research fact is acted on, or proved wrong on the phone, the result goes in this log.

**`[DECISION]` Android Auto screen added to the brief (phase 1)**
Shawn asked mid-kickoff for a Car App Library screen (`androidx.car.app:app-projected`): tracking status, current trip km and duration, today's session count and total km, and Start Trip / End Trip as a manual override. Manual trips still respect the schedule. His reason: it makes it easier to see that the app is running. That is why it sits in phase 1 rather than later; the first test drives are when that visibility matters most. Because MilO is sideloaded, Android Auto will only list it with "Unknown sources" enabled in Android Auto's developer settings.

**`[DECISION]` A bare event log moves from phase 4 into phase 1**
Shawn's brief put all diagnostics in phase 4. Phase 1 is driven for several days to prove trip detection, and a missed start cannot be explained afterwards without a record of Bluetooth, Android Auto and service events. So the event storage and a plain list screen ship in phase 1; the polished screen and the failure notification stay in phase 4. Proposed by the assistant, pending Shawn's objection.

**`[DECISION]` Kickoff answers that change the original brief**
MilO is a personal mileage logger for one phone (native Kotlin, no backend, no accounts, no Play Store). The full required behaviour is in APP_ENCYCLOPEDIA. The points below differ from, or add to, what Shawn first wrote:
- **Trip boundaries are Bluetooth only.** Connect to disconnect plus the grace period. Offered "split into sessions on long stops"; Shawn chose not to.
- **Safety net:** a manual Start/Stop button, plus a "you seem to be driving but the truck isn't connected" alert during scheduled hours. Driving detection never starts a trip on its own.
- **Trip-start sound:** an R2-D2 style chirp once the app is connected to the truck and ready to track. Phone speaker, respects silent mode. The bundled sound is an original synthesized chirp, because the film recording is copyrighted; a setting lets Shawn pick his own audio file.
- **No money on the report.** Accounts works out the reimbursement, so the PDF shows km only. The rate-per-km setting and the amount owed are dropped.
- **No odometer readings.** Start and finish location and mileage are tracked automatically.
- **No purpose column and no purpose note.** PDF table columns are Start, End, From, To, km.
- **Signature is a blank line.**

**`[DECISION]` Standards to be rewritten for Kotlin; private GitHub repo with CI and Renovate; no Sentry**
The standards template is TypeScript-centric (strict tsc, ESLint, Prettier, Husky, Sentry, dev/staging/prod backends). None of that fits a native Kotlin app with no backend. Shawn chose: rewrite the standards for Kotlin and record it in ADR-001, keep the repo private on GitHub with checks on every PR and Renovate for dependency updates, and skip Sentry and staging/prod. Crashes go to the in-app event log instead. The rewrite itself is still to do.

**`[FINDING]` Target phone is a Xiaomi POCO X5 on Android 14 (HyperOS)**
Xiaomi's MIUI/HyperOS is known for stopping background apps unless Autostart and battery "No restrictions" are set per app. That is the leading suspect for why Shawn's current mileage app fails to start trips, but it is not yet confirmed on this phone. The permission checklist must cover the Xiaomi settings, not only the stock Android ones.

**`[FINDING]` Dev machine state at kickoff**
Mac mini (Apple silicon), Android Studio 2025.3, JDK Zulu 17.0.18, SDK platforms android-36 and android-36.1, build-tools 35.0.0 / 36.0.0 / 36.1.0, `adb` and `gh` installed, `gh` signed in as shawnkowalchuk. No phone was attached over adb. The folder was not yet a git repo.

**`[DECISION]` The four companion docs live in `docs/`, not the repo root**
The templates arrived in `DOCS/` while `CLAUDE.md` and the README pointed at the repo root and at `/docs/adr/`. macOS's filesystem is case-insensitive, so `DOCS/` and `docs/adr/` were the same folder anyway. Renamed to lowercase `docs/`, created `docs/adr/`, and rewrote every pointer in `CLAUDE.md`, `README.md`, STANDARDS and ARCHITECTURE to the `docs/…` path. `CLAUDE.md` stays in the root because that is where the assistant loads it from.

---

### _[next date]_

**`[TYPE]` Short title**
What happened, and the *why* that won't be obvious from the code or the commit. Link an ADR or an ENCYCLOPEDIA entry where relevant.

---

> **Tip:** when you write `// TODO(debt): ...` in code, drop a matching `[DEBT]` line here too. The code tag tells you *where*; this log tells you *why* and *what it'll cost*.
