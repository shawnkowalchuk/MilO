# Device test checklist

> **What this is.** The checks that can only be done on the phone itself: the Xiaomi POCO X5, Android 14 (HyperOS). Unit tests and the emulator cannot show what HyperOS does to a background app. Run every check here before a phase is handed over (STANDARDS §11), and write what happened into FINDINGS_LOG, pass or fail.
>
> **How it grows.** A change that adds behaviour only the phone can prove adds its checks here, in the same change. A check is removed only when the behaviour it covers is removed.
>
> **Status:** Living document · **Started:** 2026-10-03 · Nothing below has been run on the phone yet.

---

## Before the first check

- The debug build is installed from the Mac (`./gradlew installDebug`, or Android Studio). HyperOS needs "Install via USB" and "USB debugging (Security settings)" switched on first (FINDINGS_LOG, 2026-10-03).
- **Never uninstall to fix a problem.** From phase 1 on the build holds real trips (STANDARDS §13).

## Reading the event log before it has a screen

The event log screen is not built yet. Until it is, the log is read on the Mac from a copy of the database. This is how it was read on the emulator; on the phone it is itself untested.

```
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db     > milo.db
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db-wal > milo.db-wal
adb exec-out run-as com.shawnkowalchuk.milo cat databases/milo.db-shm > milo.db-shm
sqlite3 milo.db "SELECT datetime(atMs/1000,'unixepoch','localtime'), category, message FROM event_log ORDER BY atMs, id;"
```

Copy all three files: the newest entries can still be in the `-wal` file. Keep the copies out of the project folder. Android Studio's Database Inspector (App Inspection) shows the same table without copying.

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
| 9 | **Cannot be run yet.** The raw points database has never been opened at runtime, because nothing records a GPS fix. With the first recorded trip (the next work package): the same listing shows `points.db` and its three side files, and `raw_points` in a copy of `points.db` has one row per fix. | | waiting for trip recording |

---

## Later work packages

Bluetooth-triggered start with and without HyperOS Autostart, the companion device wake-up, the grace period, Android Auto on the truck and the trip-start sound each add their checks here when they are built.
