# App Encyclopedia

> **What this is.** The complete *reference* for every capability the app has and exactly how it works — the "how does this actually behave?" book. When you (or your AI assistant) forget how a feature works, or before you change one, you read its entry here first. This is the single biggest defence against "I changed X and didn't realise it broke Y."
>
> **How to maintain it.** One entry per feature/capability, using the template below. **Update the entry in the same change that alters the feature** — a stale encyclopedia is worse than none, because it lies confidently. If behaviour changed and this didn't, the Definition of Done (STANDARDS §10) wasn't met.
>
> **Status:** Living document · **Last updated:** 2026-10-03 · **See also:** ARCHITECTURE.md (the map), FINDINGS_LOG.md (history of changes)

---

## How to read an entry

Each capability is documented with: what it does, who can use it, how it works step by step, where the code lives, what it depends on, edge cases, and which platforms it's on. Copy the template at the bottom for every new feature.

**Every entry below is `Planned`.** Nothing is built yet. These entries record the founder's brief of 2026-10-03 plus the answers given at kickoff, so they describe required behaviour, not code. "How it works" and "Where the code lives" get filled in when each phase is built.

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

**Status:** Planned (phase 1) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Starts a trip automatically when the phone connects to the truck over Bluetooth and ends it after the truck disconnects. This is the most important capability in the app: the mileage app Shawn uses today often fails to start trips even when the phone is clearly connected, so **reliability is the number one requirement**.

**Required behaviour**
- Onboarding: Shawn picks the truck from the phone's paired Bluetooth devices. The app associates it with `CompanionDeviceManager` and observes device presence, so the system wakes the app on connect and disconnect even if it has been closed.
- Backup trigger: a manifest-registered receiver for `BluetoothDevice.ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED`, filtered to the truck's address.
- On connect: start a foreground service (type `location`) with an ongoing "Trip in progress" notification and begin GPS recording.
- On disconnect: wait a grace period (default 2 minutes, configurable) before ending the trip. Reconnecting inside the grace period continues the same trip.
- If Android Auto is still connected (`androidx.car.app` `CarConnection`), do not end the trip. Shawn sometimes uses Android Auto over a USB cable.
- **A trip runs from Bluetooth connect to disconnect plus grace, and nothing else splits it.** Decided at kickoff: a long stop with the engine running stays inside one trip. Stops are not cut into separate sessions.

**Depends on**
Bluetooth permissions, companion device association, the foreground service, and on this phone the HyperOS background settings (see [Permission checklist](#permission-checklist)). Background location ("Allow all the time") is mandatory, not optional: on Android 14 a trip started while the app is in the background fails without it.

**Edge cases & gotchas**
- Phone reboots or the app is updated while the truck is already connected: no connect broadcast fires, so the existing connection has to be detected another way.
- With HyperOS Autostart off and MilO not running, decompiled system code shows the connect broadcast is dropped and the system's attempts to start MilO's services are rejected. Autostart is off by default for a sideloaded app. This is unconfirmed on Shawn's phone until tested, but Autostart is treated as mandatory. See `docs/research/2026-10-03-miui-background-limits.md`.
- After a reboot, Android holds Bluetooth events until the phone has been unlocked once, so a trip begun before the first unlock starts recording at unlock.
- Swiping MilO away in recents, the clear-all button and Xiaomi's cleaners can kill it while it waits for the truck. Force stop leaves it unable to start until Shawn opens it again.

---

## GPS recording and distance

**Status:** Planned (phase 1) · **Platforms:** Android · **Last updated:** 2026-10-03

**What it does**
Records the truck's position during a trip and turns it into a distance in km, with start and finish addresses.

**Required behaviour**
- Record a GPS fix every 5 seconds using `FusedLocationProviderClient`, and count distance only after 10 m of real movement. Shawn's brief said "every 5 seconds or 10 m"; the location API cannot express "or" (its interval and distance settings combine as "and"), so the 10 m rule is applied in MilO's own distance calculation.
- Discard points with accuracy worse than 25 m, and impossible jumps (implied speed over 180 km/h).
- Store the raw points so distance can be recalculated later.
- Discard trips under 0.3 km (configurable), for example moving the truck around the yard.
- Reverse-geocode the start and end points to street addresses and store them with the trip. Start and finish locations and mileage are tracked automatically; there is no odometer entry anywhere in the app.

**Edge cases & gotchas**
- A stationary truck with Bluetooth connected still produces GPS jitter, which must not add phantom km.
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

**Status:** Planned (bare event log in phase 1, the rest in phase 4) · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- A bare event log (the stored events and a plain list screen) ships in phase 1, because without it a missed trip start during the test drives cannot be diagnosed. Phase 4 finishes it.
- Event log screen: every Bluetooth connect and disconnect, Android Auto change, and service start and stop, with a timestamp. Crashes are written here too, because the app has no crash-reporting service.
- If the truck connects during scheduled hours and a trip fails to start, notify Shawn.
- A reminder notification on the 1st of each month (day configurable) to submit the previous month.

---

## Backup, export and import

**Status:** Planned (phase 4) · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- The main database (trips, submission status, settings, event log) is included in Android Auto Backup. Raw GPS points live in a second database file that stays out of cloud backup, because Auto Backup is capped at 25 MB and backs up nothing at all once that is exceeded.
- Until phase 4 backup is switched off entirely (`allowBackup="false"` plus `data_extraction_rules.xml`), so a half-configured backup cannot restore stale data.
- Manual export and import of all data to a file.

---

## Settings

**Status:** Planned (grows each phase) · **Platforms:** Android · **Last updated:** 2026-10-03

**Required behaviour**
- Truck: the paired device, with a way to change it.
- Trip: disconnect grace period (default 2 minutes), minimum trip distance (default 0.3 km), trip-start sound (bundled chirp or a chosen audio file).
- Schedule: tracked days and hours, and whether out-of-schedule trips are saved as Personal or ignored.
- Report: name, company, vehicle description, accounts email. There is no rate-per-km setting.
- Reminder: day of the month.

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
