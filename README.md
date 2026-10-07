# MilO

MilO is Shawn's own Android app for one phone, a Xiaomi POCO X5 Pro 5G (Android 14, HyperOS 2.0). It records the business kilometres driven in the work truck and makes the monthly mileage report for the accountant, as a PDF and as a CSV file. It has no account, no server and no internet permission: every trip stays on the phone unless Shawn sends or saves a file himself. It is installed over USB from the Mac mini, and is not on the Play Store.

**What MilO does by itself**

- Starts a trip when the phone connects to the truck's Bluetooth, records the drive with GPS, and ends the trip two minutes after the truck disconnects, or once the truck has not moved for ten minutes even though it is still connected (both times can be changed in Settings).
- While the truck stays connected after such a stop, waits beside it and starts the next trip when it moves.
- Looks up the start and end address of each trip.
- Saves each trip as Business or Personal, by the work hours set in Settings.
- Reminds you once a day, from the 1st of the month, until last month's report is recorded as sent.
- Notifies you if the phone reports driving during the work hours while no trip is being recorded.
- Writes down what happened in its event log: every Bluetooth connect and disconnect, every Android Auto change it sees, every start and stop of the trip service, every crash.

**What you do**

- Set the phone up once (the Setup screen and the HyperOS settings below) and pair the truck once.
- Look over the trips, and correct one when it is wrong.
- Send the report: MilO opens the email app with the PDF attached, and you press send.
- Keep a backup (an export file, and a copy of the signing key).

**What is proven and what is not.** On 2026-10-06 the truck was paired and started three trips by itself, and both times it disconnected the trip ended after the two minutes (`docs/FINDINGS_LOG.md`, 2026-10-06 (evening)). That evening also showed that the truck can stay connected long after it is parked, so the last trip of the day never ended. The rule built for that the same evening, a trip ends when the truck has not moved for ten minutes and MilO then waits beside it, **has not run on the phone yet**; neither has waking a MilO that HyperOS had closed. That night the finished work was checked once more against the day's own records from the phone: the start and the stop that were seen came out the same. Everything else was built and tested with unit tests, and most of it was run on an emulator; the Android Auto screen has run nowhere. Each entry of `docs/APP_ENCYCLOPEDIA.md` starts with a status line that says what the phone has proven and what it has not, and `docs/DEVICE_TEST_CHECKLIST.md` lists what the phone still has to show.

---

## Install and run from Android Studio

**Not tried yet in Android Studio itself.** Every build so far was put on the phone from the terminal over USB. Opening the project in Android Studio, its first sync and its Run button are untried with this project, so the steps below are what is expected, not what was seen. The terminal way further down is the one that has worked.

### What the Mac needs

- Android Studio Rabbit 1 2026.2.1 (or Quail 4 2026.1.4). An older Android Studio cannot open this project.
- SDK platform 37, from Android Studio's SDK Manager. It is installed on the Mac mini.
- Nothing else. The project brings its own Gradle and asks for Java 17, which the Mac has.

### Open the project and let it sync

1. In Android Studio choose **File, Open** and pick the `MilO` folder itself (the one that holds `settings.gradle.kts`), not the `app` folder inside it.
2. If Android Studio asks whether to trust the project, say yes.
3. Wait for the first sync to finish. It reads the build files and fetches what is missing, which takes a few minutes and needs the internet. The bar at the bottom says when it is done.
4. Android Studio writes `local.properties`, the file that says where the Android SDK is. Git ignores that file on purpose.

### The signing key

Android only lets a new build replace the MilO on the phone if both are signed with the same key. MilO has one key of its own for that.

- The key is the file `~/keys/milo.jks`. Its password is in the macOS Keychain under the name `milo-keystore`. Neither is in the project folder or on GitHub.
- **The build finds both by itself.** You should not be asked for a password, and there is nothing to set up in Android Studio's signing dialogs.
- If the key file or the Keychain entry is missing, the build stops and says what to do. It never signs with another key in silence.
- **If Android Studio ever says the app on the phone has a different signature and offers to uninstall it, press Cancel.** Uninstalling deletes every trip. Ask for help with the key first.
- Not yet confirmed in Android Studio: that Run installs a build signed with this key. A sync is handed a placeholder in place of the password, so that Android Studio cannot save it. After the first Run, check that MilO was updated in place and your trips are still there.

### Run it on the phone

1. Connect the phone to the Mac with the USB cable and unlock it. If the phone asks whether to allow USB debugging from this computer, allow it.
2. In Android Studio's toolbar, pick the phone in the device list. The configuration beside it is `app`.
3. Press **Run** (the green triangle). Android Studio builds MilO, installs it over the one on the phone and opens it.
4. Keep the phone in your hand until MilO is open: HyperOS asks on the phone before it installs (below).

### What to expect on HyperOS

- **Developer options are already set on your phone.** HyperOS needs three switches for an install from the Mac: "USB debugging", "Install via USB" and "USB debugging (Security settings)". Read from the phone on 2026-10-05, the two Xiaomi ones were on. Nothing to change.
- If they are ever switched off again (*from the research notes for HyperOS, check on your phone*): Settings, About phone, tap "OS version" seven times; then Settings, Additional settings, Developer options. Switching "Install via USB" on is reported to need a signed-in Xiaomi account, a SIM in the phone, and mobile data on with Wi-Fi off.
- **The "Install via USB" prompt.** At each install the phone is reported to show a prompt with a countdown of a few seconds. Press Install before it runs out. If you miss it, Android Studio reports `INSTALL_FAILED_USER_RESTRICTED`; press Run again. (*From the research notes for HyperOS, check on your phone.* The installs so far went through the first time; whether the prompt showed was not written down.)
- **MilO may start by itself right after an install.** Seen on your phone on 2026-10-06: HyperOS started MilO straight after the update, with Background autostart on.
- **An update can take a moment before MilO opens.** If the new build stores trips in a newer form, it converts what the phone holds first. Your trips are kept.

---

## First-time setup in the app

Open MilO and press **Setup** in the bottom bar: its third button, a box with a tick (the bar shows icons and no words). The screen lists everything MilO needs, each row with its state and a button that leads to the place to set it. The tile at its top says how many of the rows are ready. Until every required row is in order, the Home screen shows "Setup needs attention" and a trip may not start by itself.

On 2026-10-05 you went through this screen once, everything except pairing the truck. The Physical activity row was added after that.

### The rows, in order

A row that is to be fixed (a red mark) stands at the top of its group until it is in order; the tables give the order with nothing to fix.

"Android" (MilO reads the state of all ten by itself):

| # | Row | What to do |
|---|---|---|
| 1 | Precise location | Press Allow. Choose "While using the app", with Precise on. |
| 2 | Location: Allow all the time | Press Allow. Android opens its own location page for MilO: choose "Allow all the time". The button is there once row 1 is done. |
| 3 | Notifications | Press Allow. |
| 4 | Nearby devices (Bluetooth) | Press Allow. Without it no trip starts at all, not even with the Start trip button. |
| 5 | Location switched on | Location must be on for the whole phone. |
| 6 | Truck paired and watched | Leave this for last: see "Pair the truck" below. |
| 7 | Battery use: unrestricted | Press Open settings and allow MilO to run without battery optimisation. |
| 8 | Not paused when unused | Recommended. Switch off "Pause app activity if unused" for MilO. |
| 9 | Battery Saver off | The phone's Battery Saver must be off while you work. |
| 10 | Physical activity | Recommended. Only the driving alert needs it. |

"This Xiaomi phone (HyperOS)" (four rows; what each setting is for is in the next section):

| # | Row | What to do |
|---|---|---|
| 11 | Background autostart | Press Open settings, find MilO in the list and switch it on. The row then says "Looks on". |
| 12 | Battery saver: No restrictions | Press Open settings, choose "No restrictions", come back and press "I have set this". |
| 13 | Other permissions | Recommended. Press Open settings, switch the three on, come back and press "I have set this". |
| 14 | Locked in recent apps | No button: it is a gesture. Do it, then press "I have set this". |

A row you confirmed says "You confirmed this on (date). MilO cannot check it." If a button opens MilO's App info page in place of the page its row names, the setting is reported to be one tap further in from there (*from the research notes for HyperOS, check on your phone*); the Log screen then has an `ERROR` line that says which screen could not be opened. Whether each button opens the right HyperOS screen on your phone was not written down.

### Pair the truck

Do this at the truck, with every other row in order.

1. Pair the phone with the truck in the phone's own Bluetooth settings, if it is not paired already.
2. In MilO: Setup, the row "Truck paired and watched", **Pair truck**.
3. Under "Paired with this phone", find the truck and press **Pair**.
4. Android shows a dialog of its own and asks you to allow it. Choose **Allow**.
5. The screen says "Paired with (the truck's name)" and "Paired, and Android is watching for it. A trip starts by itself when it connects." On Setup the truck's row now has its tick, and the warning on Home is gone.

Bluetooth and Location must both be on for this, and the screen says so if one is not. **This step has never been done:** Android's dialog has not been shown by any build of MilO, on any device. If the screen fails, `docs/DEVICE_TEST_CHECKLIST.md` ("Pairing MilO with the truck") has a second way from the Mac.

---

## Phone settings for the POCO X5 Pro (HyperOS 2.0)

HyperOS stops apps in the background harder than plain Android does. These are the settings that keep it from putting MilO to sleep. Why they matter comes from research into HyperOS, not from tests on your phone: the drives that prove them are checks 31 to 43 of `docs/DEVICE_TEST_CHECKLIST.md`.

**Every menu path below is from the research notes for HyperOS, and each is marked so.** The FINDINGS_LOG records what was read back from your phone (a permission granted, a switch on), never the path that was taken to set it, so none of the paths counts as confirmed. Menu names differ between HyperOS versions; if a path does not match, search the phone's Settings for the name of the switch. The research is in `docs/research/2026-10-03-miui-background-limits.md` and `docs/research/2026-10-03-miui-dev-bluetooth-audio.md`.

### 1. Background autostart: on

- **Where:** Settings, Apps, Permissions, Background autostart, MilO on. Or: Settings, Apps, Manage apps, MilO, Autostart. *(From the research notes for HyperOS, check on your phone.)*
- **Why:** it is the switch that lets HyperOS start a closed app. With it off, HyperOS is reported to drop the truck's Bluetooth signal and refuse to start MilO for it. It is off by default for an app you install yourself. This is the most important one.
- **MilO's Setup:** reads it, through an unofficial check, as "Looks on" or "Looks off". On your phone the reading answered and followed the switch (2026-10-05).
- **On your phone so far:** read as on, on 2026-10-05.

### 2. Battery saver for MilO: "No restrictions"

- **Where:** Settings, Apps, Manage apps, MilO, Battery saver, No restrictions. *(From the research notes for HyperOS, check on your phone.)*
- **Why:** it is the one choice of the four that is reported to keep Xiaomi's own freezer and automatic clean-ups away from an app.
- **MilO's Setup:** cannot read it. The row waits for "I have set this".
- **On your phone so far:** not recorded.

### 3. Battery optimisation exemption (Android's own)

- **Where:** MilO's Setup, the row "Battery use: unrestricted", Open settings, then allow. The research found no HyperOS menu path for it, so use the row's button.
- **Why:** it lets Android start a recording while MilO is in the background, and keeps Android's own battery saving off MilO. It is treated as a separate setting from number 2: set both.
- **MilO's Setup:** reads it.
- **On your phone so far:** granted, read from the phone on 2026-10-05.

### 4. "Pause app activity if unused": off

- **Where:** Settings, Apps, Manage apps, MilO, switch off "Pause app activity if unused". *(From the research notes for HyperOS, check on your phone.)*
- **Why:** with it on, Android takes an app's permissions away and stops its background work after months without being opened.
- **MilO's Setup:** reads it (the row "Not paused when unused", recommended). Whether HyperOS has this switch, and whether the row follows it there, is not known.
- **On your phone so far:** not recorded.

### 5. "Other permissions": three switches on

- **Where:** Settings, Apps, Manage apps, MilO, Other permissions. Switch on "Show on Lock screen", "Start in background" (also seen as "Display pop-up windows while running in the background" or "Open new windows while running in the background") and "Permanent notification". *(From the research notes for HyperOS, check on your phone.)*
- **Why:** they decide whether an app may show a screen from the background and keep its permanent notification. The research says a recording does not depend on them, and that apps of MilO's kind ask for them all the same; switching them on does no harm. The names on HyperOS 2.0 are not confirmed.
- **MilO's Setup:** cannot read them. One recommended row, which waits for "I have set this".
- **On your phone so far:** not recorded.

### 6. MilO locked in the recent apps

- **Where:** open MilO, open the recent apps, press and hold MilO's card, tap the padlock. *(From the research notes for HyperOS, check on your phone.)*
- **Why:** a locked app is left alone by the clear-all button and by Xiaomi's cleaners. It is not protected from a swipe on its own card, and not from every automatic clean-up.
- **MilO's Setup:** cannot read it, and has no button for it. The row waits for "I have set this".
- **On your phone so far:** not recorded.

### 7. Location: "Allow all the time"

- **Where:** MilO's Setup, the row "Location: Allow all the time", Allow. By hand: Settings, Apps, Manage apps, MilO, Permissions, Location, "Allow all the time", with "Use precise location" on. *(From the research notes for HyperOS, check on your phone.)* That it is granted was read from the phone; the path was not written down.
- **Why:** a trip that starts by itself starts while MilO is closed. Android 14 refuses to start a location recording from the background without it, and there is no way round that.
- **MilO's Setup:** reads it.
- **On your phone so far:** granted, read from the phone on 2026-10-05.

### What MilO's Setup can read, in one place

| Setting | Setup reads it? |
|---|---|
| Background autostart | Yes, through an unofficial check ("Looks on" / "Looks off") |
| Battery saver "No restrictions" | No. You confirm it |
| Battery optimisation exemption | Yes |
| "Pause app activity if unused" | Yes (unproven on HyperOS) |
| "Other permissions" | No. You confirm it |
| Locked in recent apps | No. You confirm it |
| Location "Allow all the time" | Yes |
| Precise location, Notifications, Nearby devices, Physical activity | Yes |
| Location switched on, the phone's Battery Saver | Yes (whether the row sees HyperOS's own Battery saver and Ultra battery saver modes is not known) |

What you confirmed is your word, with its date. HyperOS is reported to put some of these settings back after a system update or a restart. **After a system update, go through Setup again.**

### Habits

- **Do not force stop MilO.** A force-stopped app is told nothing by Android until you open it again: no trip starts.
- **Do not swipe MilO away** in the recent apps. While it waits for the truck, a swipe ends it even when it is locked, and only Background autostart brings it back.
- **Avoid the clear-all button** (the X in the recent apps) and the Security app's Cleaner and Boost speed on work days. Clear-all is reported to end apps that are not locked, a recording included.
- **Keep Battery saver mode off on work days.** Settings, Battery, Current mode: Balanced or Performance, not Battery saver or Ultra battery saver. *(From the research notes for HyperOS, check on your phone.)*
- **Keep the alarm volume up: the trip-start sound follows it.** Since 2026-10-07 the sound is played like an alarm, so that it is heard when the phone is on silent, on vibrate, in Do Not Disturb or in Bedtime mode. (On 2026-10-05 Bedtime mode silenced it on your phone; the trip was recorded all the same.) A Do Not Disturb that is set to silence alarms too still silences it. *(Not yet tried on your phone: device checks NL-70 to NL-74.)*
- **After the phone restarts, unlock it once.** Android holds Bluetooth events back until the first unlock.
- **Do not press "Back up now" in the phone's Google settings during a trip.** On an emulator a backup that was asked for from the Mac ended MilO in the middle of a recording. Whether the phone's own button does the same is not known.

Two more settings from the same research notes, which MilO's Setup does not show: Settings, Battery, the cog or "Additional features": set "Clear cache when device is locked" and "Turn off mobile data when device is locked" to Never. *(From the research notes for HyperOS, check on your phone.)*

---

## Everyday use

This is what is built. A start by the truck and an end by its disconnect were seen on 2026-10-06; an end because the truck stood still, and what follows it, have not been seen on the phone yet.

**When a trip starts.** The truck's Bluetooth connects, and within seconds:

- the trip-start sound plays once from the phone, at the alarm volume;
- a notification "Trip in progress" appears and stays, with the kilometres so far;
- Home turns its lime tile into the trip: "Recording", the kilometres so far, the minutes it has run and "since (time)", and the button End trip. Its small Truck tile says "Connected".

If Home is open at that moment, its truck tile first says "Connecting…" and then "Connected", for about four seconds, and only then does the lime tile change. The trip is being recorded all that time. Built on 2026-10-07 and seen on an emulator only.

**When a trip ends.** In one of two ways, whichever comes first.

- **The truck disconnects.** Home and the notification say "Trip in progress. Waiting for the truck to reconnect" for two minutes; reconnecting in that time continues the same trip. Then the trip ends and the notification goes.
- **The truck has not moved for ten minutes,** connected or not. The trip ends where and when the truck stopped, not ten minutes later. A stop of ten minutes or more therefore cuts a drive into two trips; the ten minutes can be set from 5 to 30 in Settings.

Either way there is no sound, and the trip is the "Last trip" on Home and a row on the Trips screen, with its times, from and to addresses, Business or Personal, and kilometres. A trip under 0.3 km is discarded and can be counted after all on Trips.

**"Truck connected and parked".** Your truck can stay connected to the phone long after it is switched off. When a trip has ended because the truck stood still and the truck is still connected (by Bluetooth, or by Android Auto on the cable), the notification stays and says "Truck connected and parked", "A trip starts when the truck moves", and the truck's tile on Home says the same. Nothing is being recorded. MilO looks at the phone's position every 30 seconds, and when the truck drives off a new trip starts by itself within about a minute, from where the truck was parked, **without the trip-start sound**. When the truck finally disconnects, the notification goes. You can press Start trip at any time. While MilO waits there is no End trip button: the lime tile on Home reads Start trip.

**If you end a trip yourself with End trip while the truck is still connected,** MilO does not wait. It starts nothing by itself until the truck has disconnected and connected again, or until twelve hours have passed and MilO is opened, or you press Start trip. If the truck stays connected all that time, as yours can, nothing tells you that the next drive is not being recorded: open MilO before you drive off, and press Start trip if the lime tile on Home reads Start trip. After a trip you started that way, a stop is waited out as before.

After three days of standing MilO stops watching, to spare the battery, and the truck's tile on Home says "Truck connected. MilO has stopped watching it". Open MilO or press Start trip before you drive off then.

**If a trip did not start.** Press **Start trip** on Home; **End trip** ends it. If MilO tried and could not start, it posts "MilO could not start this trip. Tap to start". If the phone notices driving during the work hours with no trip being recorded and the truck not connected, it posts "You seem to be driving": tap it to start one (this needs the Physical activity row of Setup and a paired truck). A trip that was missed altogether is typed in on Trips with "Add missed trip".

**If MilO has stopped noticing the truck.** Once a day MilO asks itself whether a trip has been started. On a work day (a day that is switched on in the work schedule) that has none by 12:00 noon, it posts one notification: "No trip recorded today". If the truck has not been driven that day, there is nothing to do. If it has, tap the notification: MilO opens on Home, which says "Setup needs attention" if something it needs is switched off. The time and the switch are in Settings, on the card "Daily check"; a trip you add by hand does not count. Built on 2026-10-06 and seen on an emulator only: whether this phone lets MilO wake up for it at noon is one of the things still to be tried.

**Where things are.**

| What | Where |
|---|---|
| Start trip and End trip, the trip in progress, today's and the month's Business kilometres, whether the truck is connected, the last trip | **Home**, the first button of the bottom bar (a house) |
| Every trip, a month at a time: press a day to see its trips, and a trip to edit it, mark it Business or Personal, or delete it; add a missed trip | **Trips**, the second button (a list) |
| The report for the accountant | Trips, the dark pill on the month's lime tile (**Not submitted** or **Submitted**) |
| Your name, company, vehicle, the accountant's email address, the work hours, the three trip numbers (how long to wait for the truck to reconnect, how long it may stand still, the shortest trip that counts), the driving alert, the daily check, the reminder, the sound, export and import | **Settings**, the square button with three sliders at the top of Home |
| The permissions and phone settings, and the truck | **Setup**, the third button (a box with a tick) |
| What MilO did and when | **Log**, the last button (a sheet of paper) |

**The report.** Set your name and the accountant's address in Settings first. On the Report screen, "Create PDF" and "Open PDF" let you look at it; **Send to accountant** opens the email app with the address, the subject and the PDF filled in. MilO sends nothing itself: press send there. Back in MilO it asks "Did you send it?"; "I sent it" marks the month as submitted. "Export CSV" makes the same trips as a spreadsheet file. No email draft has been seen yet, on any device: the emulator had no email account.

**The log, and how to send it.** The Log screen lists the newest lines first. Each day's lines are one tile, with the day above it (today's has none). The chips under the title show one kind of line at a time (Trips, Bluetooth, Android Auto, Errors); "Tap for details" opens what a line holds. **The square button beside the title shares the whole log:** it asks first, saying what the file holds, then makes a text file of every line and opens Android's share sheet: pick your email app and send it to whoever is helping you. The file names the truck and the phone's other Bluetooth devices with their addresses, and every address you typed on the edit screen. It holds no GPS position. Lines older than 90 days are removed, except the newest 1,000.

MilO only sees Android Auto connect or disconnect while MilO itself is running; a connection that came and went while it was closed is not in the log.

---

## Backups

There are three things to keep. The first two are yours to do.

**1. An export file.** Settings, the last tile ("Backup, export and import"), **Export**, with "Include the GPS points" left on. Android's file picker asks where to put `MilO-export-(date).json`: choose Downloads or Drive, then copy the file off the phone. Make one after each month's report, and before each update of MilO. It is the one copy that depends on nothing else: not the signing key, not a Google account, not this phone. "Import data" on the same card puts such a file back, and replaces everything the phone holds.

**2. The signing key and its password.** Copy `~/keys/milo.jks` to somewhere that is not the Mac, and keep its password with it (it is in the macOS Keychain under `milo-keystore`; a password manager is a good place for the copy). Never put the password in a file inside the project. Without the key and its password, a new build cannot replace the MilO on the phone: the only way forward would be to uninstall, which deletes the trips, and Android's backup would then refuse to restore them.

**3. Android's own backup.** If Google's backup is switched on for the phone, Android copies MilO's trips, sent reports, settings and event log to your Google account by itself, and gives them back when MilO is installed afresh with the same signing key. The raw GPS points are not in it. **It has never been seen to happen on your phone**, and a restore cannot be tried there without removing MilO. When Android has collected a backup, the Log says so in a `PROCESS` line ("Android collected MilO's data for a cloud backup …"). Until that line has been seen, count on the export file only.

**Never uninstall MilO to see whether a restore works.**

---

## Updating the app

1. Make an export first (above). If the MilO on the phone does not have that card yet: the last install the FINDINGS_LOG records, on the morning of 2026-10-06, is of a build from before the export existed. The trips the phone held then were copied to `~/MilO-backups/` on the Mac before that install, and check 232 of `docs/DEVICE_TEST_CHECKLIST.md` says how to take such a copy again.
2. Get the new code. In Android Studio: Git, Pull. Or in a terminal in the `MilO` folder:

   ```bash
   git pull
   ```

3. Connect the phone and press **Run**, as for the first install. The new build replaces the old one in place and keeps every trip and setting.

**Updates only go forward.** Never install an older build over a newer one. A newer build may store trips in a form an older one cannot read, and the older build then stops at every start. Android does not prevent it, because every build so far calls itself version 0.1.0. If it happens, no trip is lost: install the newer build again.

**Never uninstall MilO to fix a problem,** and never clear its data. Both delete every trip. If MilO misbehaves: open the Log, share it, and install a fixed build over the one on the phone.

After an update, open MilO once and look at Setup.

---

## For developers

### Building from the terminal

Needs Android Studio Rabbit 1 2026.2.1 (or Quail 4 2026.1.4) for the IDE, SDK platform 37 and JDK 17. Details are in `docs/adr/ADR-001-stack-selection.md`.

The build. This is the command CI runs: format check, lint, unit tests, debug APK.

```bash
./gradlew spotlessCheck lintDebug testDebugUnitTest assembleDebug
```

A terminal build has to be told where the Android SDK is. That path is in `local.properties`, which git ignores, so a new clone stops with "SDK location not found". Either open the project once in Android Studio, which writes the file, or set the path in the shell:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

Install on the connected phone (USB debugging on, the HyperOS switches as above). This is how every build reached the phone so far:

```bash
./gradlew installDebug
```

**Signing.** Builds for the phone are signed with `~/keys/milo.jks`, whose password the build reads from the macOS Keychain entry `milo-keystore` each time. A missing keystore or entry stops the build with instructions. GitHub's CI has no key and signs the debug build with the throwaway debug key. To do the same on another machine:

```bash
./gradlew assembleDebug -Pmilo.signing.debugKey=true
```

Never install that build on the phone: it cannot update the real one.

**Your own trip-start sound.** The repo ships an original synthesized chirp. A file saved as `app/src/debug/res/raw/trip_start_chirp.mp3` (or `.wav` / `.ogg`; the name must be `trip_start_chirp`) replaces it in debug builds made on this machine. That folder is git-ignored on purpose: a personal clip may be someone else's copyright and must stay off GitHub. Delete the file to go back to the bundled chirp. (Settings in the app can also choose any audio file on the phone, without a rebuild.)

**The typeface.** The screens are set in Sora, which the app carries as `app/src/main/res/font/sora.ttf`. Its licence, the SIL Open Font License 1.1, is `licenses/Sora-OFL.txt`, and stays in the repository for as long as the font does.

### The pre-commit hook

One-time setup, per clone. Turn the hook on:

```bash
git config core.hooksPath .githooks
```

Install gitleaks, which the hook uses to scan staged changes for secrets. Without it the hook stops every commit and prints an install hint:

```bash
brew install gitleaks
```

The hook runs the gitleaks scan and then `spotlessCheck`. Lint, unit tests and the build are left to CI.

### The documentation system

The repo runs on a small set of documents that are its memory and rulebook. They exist to prevent two things: decisions made implicitly and re-argued in every feature, and lost context about what was built and why.

| File | What it is | You open it to answer |
|---|---|---|
| `CLAUDE.md` | Operating instructions for the AI assistant, read at the start of every session | (its standing orders: read before writing, log after) |
| `docs/ENGINEERING_STANDARDS.md` | The rules: how the app is built | "How should I do X?" |
| `docs/ARCHITECTURE.md` | The map: the system's shape, stack, data flow, what the two UI surfaces share | "What connects to what?" |
| `docs/APP_ENCYCLOPEDIA.md` | The reference: every capability and how it works, each with a status line | "Have I built this? How does it behave? Is it proven on the phone?" |
| `docs/FINDINGS_LOG.md` | The journal: a dated record of every change, decision and finding | "What happened, and why this way?" |
| `docs/DEVICE_TEST_CHECKLIST.md` | The checks only the phone can run | "What is still unproven?" |

ADRs for big individual decisions are in `docs/adr/`. Research notes are in `docs/research/`: dated evidence behind the ADRs, not kept current.

**Read order.** Starting fresh: `CLAUDE.md`, then `docs/ENGINEERING_STANDARDS.md`, then `docs/ARCHITECTURE.md`; skim `docs/APP_ENCYCLOPEDIA.md` to see what exists. With the AI assistant nothing special is needed: `CLAUDE.md` stays in the repo root and tells it to read the other four in `docs/` first.

**The loop, every change:**

```
READ   standards + architecture + the relevant encyclopedia entry
  ↓
ASK    phone UI, Android Auto screen, or both? reuse an existing pattern? already built?
  ↓
BUILD  the smallest correct version, matching existing patterns
  ↓
UPDATE the encyclopedia if behaviour changed
  ↓
LOG    append to the findings log: what changed and why
```

**Where to look when stuck.**

- "Did I already build this?": `docs/APP_ENCYCLOPEDIA.md`. Search before building; extend what exists.
- "Why did we do it this way?": `docs/FINDINGS_LOG.md`, then `docs/adr/`.
- "How am I supposed to do this?": `docs/ENGINEERING_STANDARDS.md`.
- "What is the shape of the system, and is this shared between the phone UI and the Android Auto screen?": `docs/ARCHITECTURE.md`.

**What was set up on day 1** (the full checklist is `docs/ENGINEERING_STANDARDS.md` §18): a private GitHub repo with a `.gitignore` for build output, `local.properties` and keystores; one `:app` module with Kotlin `allWarningsAsErrors`, Android Lint `warningsAsErrors`, Spotless and ktlint; the pre-commit hook; the latest stable dependencies pinned as exact versions in `gradle/libs.versions.toml`, with Dependabot so they never go stale; gitleaks in CI; the package structure, design tokens and base components; CI on every PR; and the documents above with ADR-001. Left out on purpose, with the reasons in ADR-001: Sentry, staging and prod environments, a lockfile, detekt, Robolectric, Hilt, Renovate.

**Two habits that matter most.** Keep dependencies fresh continuously: Dependabot and pinned versions mean small updates in place of a once-a-year migration (STANDARDS §2). And write it down when you do it, not later: every feature gets an encyclopedia entry, every change a findings-log line.
