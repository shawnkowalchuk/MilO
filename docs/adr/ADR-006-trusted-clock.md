# ADR-006: MilO keeps its own clock

Date: 2026-10-07
Status: Accepted. Built and unit tested. It ran on a stock-Android emulator on 2026-10-09 (FINDINGS_LOG, "MilO's clock ran on an emulator"); what was decided after that run (C and D below) has run in unit tests only. Nothing of it has run on the phone (device checks CJ-1 to CJ-15).
Changed: 2026-10-09, after two independent verifications of the build: decisions 2 and 4 below, and the section "Decided on 2026-10-09, after the verification". Changed again the same day, after the run on an emulator: the section "Decided on 2026-10-09, after the emulator run".
Number: written as ADR-003. It is ADR-006 since 2026-10-09: by then ADR-003 was the website, ADR-004 Google Play and ADR-005 the mascot's renderer. For a few hours that day it was merged as a second ADR-005, which was a mistake of the merge and is put right here.

## Context

On the evening of 2026-10-07 MilO showed "No trip recorded today" after a full day of trips. The phone's own time log explained it. The date had been set one day ahead by hand, and automatic time had put it back 5 to 16 seconds later. That happened five times that evening, and once on each of the two evenings before.

Shawn, asked about it on 2026-10-07: "i do that to get lives in candy crush". He will keep doing it. So MilO has to work on a phone whose date jumps a day ahead for a few seconds, several times an evening.

What is known about a jump, from the phone's time log:

- The clock goes ahead by 24 hours, a few milliseconds short. It comes back when automatic time is switched on again.
- The time zone does not change.
- The clock that counts from the phone's boot is not affected. It cannot be set.
- Android sends the broadcast `TIME_SET` for each change.

Until this decision MilO asked the phone for the time in some forty places, and nothing listened for a change of the clock. An investigation on 2026-10-07 proved, with unit tests and on an emulator, what one jump did:

- The two daily alarms fired at once. "No trip recorded today" was shown for tomorrow. Tomorrow was stored as "already notified", so the real warning was used up. Both alarms were asked for again for the day after tomorrow. On the last evening of a month the monthly reminder announced a month that had not ended.
- Beside a parked truck, in the first hour of a wait, one GPS fix inside the jump made MilO think the hour was over. GPS went off, and on the emulator the next drive was recorded by nothing.
- After more than 48 hours beside the truck (a weekend), one reading inside the jump "reached" the 72-hour limit, and MilO stopped watching for good.
- During a drive, the first GPS fix inside the jump made the parked rule think the truck had stood for 24 hours. The trip was ended at speed: two records for one drive, or the rest of a hand-started drive lost.
- A trip that started inside the jump was dated tomorrow: the wrong day, weekday, month, and Business or Personal. It ended before it started.
- The hold-off after End lapsed at once. A grace period ended at once.
- Lines written to the event log in those seconds were dated tomorrow.

Finished trips that were already stored were never changed by a jump. The distance calculation was not disturbed: it already ran on the clock that counts from boot.

## Decision

**MilO keeps its own time, in one place, and every part of MilO asks that place.** The trip rules, the daily checks and the screens are not changed. They are handed a time that cannot jump.

1. **How it tells the time.** MilO's clock remembers an anchor: a time of day, and what the clock that counts from boot read at that moment. "Now" is the anchor's time plus what has been counted since. At every reading it compares that with the phone's clock.
   - **They agree to within 2 minutes** (`FOLLOW_AT_ONCE_MS`): the phone's clock becomes the new anchor, and is the answer. That is what a network time correction looks like. So while nobody touches the clock, MilO's time is exactly the phone's.
   - **They differ by more:** the phone is not believed. The answer is MilO's own time. Only if the phone's clock keeps the same difference for 10 minutes (`HOLD_MS`) is it taken as the new anchor.
   - **"Keeps it for 10 minutes" means seen to keep it.** While MilO does not believe the phone it looks at the clock again every 30 seconds. If more than 2 minutes pass between two looks (`SEEN_AGAIN_WITHIN_MS`), the 10 minutes start again. Without this, two looks that both fell into a jump would pass for one change that never went away: on 2026-10-07 the jumps came 5 to 10 minutes apart, so the first and the third were 12 minutes apart.
   - It is a rule about how long a change has held. It never asks who made the change, and it does not read the "automatic time" setting.
2. **Across a start of MilO.** The anchor is kept in a small file, with the number of the phone's boot. A MilO that Android starts while the date is ahead (the daily alarm does exactly that) reads the anchor first and has the right time from its first reading. After a restart of the phone, or with no anchor file, the phone's clock is taken as it is, **and that anchor is on probation** (decided on 2026-10-09, below; while it is, MilO shows no notification, C below). The file is in the folder that Android's backup never takes, and it is not in an export.
3. **Nothing else reads the phone's clock.** Every object that needs the time is handed MilO's clock. A unit test reads the source code and fails if a second reader appears. The time zone still comes from the phone.
4. **The daily alarms.** Android's alarm service goes by the phone's clock, so it still delivers both daily alarms the moment the date goes ahead. That cannot be prevented with the kind of alarm MilO uses. The look they trigger now sees the real day and decides rightly. While MilO's clock and the phone's disagree, MilO does not ask Android for an alarm at a time of day: asked for the real next noon while the phone reads tomorrow evening, the alarm would be delivered at once, again and again. **It asks for the same alarm on the clock that counts from boot** (decided on 2026-10-09, below). MilO listens for `TIME_SET`, and when the clocks agree again it asks for both alarms in the normal way and has both checks look.
5. **What the build before left behind.** A stored "notification shown" day that lies after today counts as not shown, and is taken out of the settings. The same holds for the monthly reminder's memory. A stored mark of the last imported process record that lies in the future is not trusted either. Lines already dated tomorrow stay as they are.
6. **One line in the event log** for each change that MilO saw and did not follow at once, written when it is over: "The phone's date was set 24 h 0 min ahead. MilO saw it back 9 s later and kept its own time." The line is written by the MilO process that saw the change begin. A change that no MilO process saw, or whose process was ended before the date came back, leaves no line.

The rule is a pure class, `core/clock/TrustedClock`. The Android side is `platform/clock/`. The numbers above are constants in `TrustedClock.kt`.

## Decided on 2026-10-09, after the verification

Two independent verifiers examined the build on 2026-10-09. They found no blocker: with nobody touching the clock, MilO's time was the phone's at every reading, and no injected jump changed a trip. They found two risks. Both were decided the same day.

**A. The daily alarms while the clocks disagree.**

- **What was found.** After the date went ahead, MilO asked Android for no alarm and relied on `TIME_SET` to ask again when the date came back. A MilO process that Android had frozen or ended by then, on a phone that does not deliver `TIME_SET` to it, had no daily alarm until MilO next started. A day on which nothing else starts MilO is the day the daily check is for.
- **Decided.** While the clocks disagree, MilO asks for the same alarm on the clock that counts from boot, due after the time that is really left: `ELAPSED_REALTIME_WAKEUP` for the daily check (its normal alarm is `RTC_WAKEUP`) and `ELAPSED_REALTIME` for the monthly reminder (normally `RTC`). It is inexact like the normal one and needs no permission. It is the same request to Android, so the normal request that follows when the clocks agree takes its place.
- **Why it cannot loop.** Setting the date does not move the clock that counts from boot. An alarm on it cannot go off at the jump.
- **What it costs.** Nothing that was seen. One more kind of line in the event log, for an alarm that was asked for in this way.

**B. An anchor on probation.**

- **What was found.** A MilO process that starts with no usable anchor takes the phone's clock as it is. That is the first start after an install or an update, the first MilO process after a restart of the phone, and a start with an anchor file that is missing or cannot be read. If such a start fell into the seconds the date is ahead, MilO believed tomorrow, stored it, and then refused today for ten watched minutes, in later processes too: in practice until ten minutes into the next trip. The build's own `TIME_SET` receiver makes such a start likely: Android may start MilO for the change of the date itself. Android's rules allow it (`TIME_SET` is one of the broadcasts it still delivers to an app that is not running); whether HyperOS does it has not been seen (CJ-8).
- **Decided.** An anchor that was taken with nothing to check it against is on probation. It leaves probation when the phone's clock agrees with it, to within 2 minutes, at a reading at least 2 minutes (`PROBATION_MS`) after it was taken. That is longer than any of his jumps. The mark is stored with the anchor, so a later process of the same boot knows.
- **While an anchor is on probation,** a phone's clock that is found more than 2 minutes BEHIND MilO's time is taken at once, as a new anchor that is on probation itself. His trick goes ahead and then back, so a clock that goes back says that the anchor was taken inside a jump. A phone's clock that goes AHEAD is set aside as always.
- **When that happens** the Log gets one line, "MilO started while the phone's date was set 24 h 0 min ahead, and took the phone's time when it came back.", and both daily alarms are asked for again and both checks look again, as after any change that came back. While its anchor is on probation MilO looks at the clock every 30 seconds by itself, so a MilO that is awake does not depend on being told. A MilO that Android has frozen does not look; it is woken by the alarm of C below.
- **What it costs.**
  - **For the seconds until the phone's clock comes back, such a MilO goes by the date,** as every MilO did before this ADR. What it stamps in those seconds stays stamped: log lines, and in the worst case a trip that started in exactly those seconds. As first built, "No trip recorded today" could be shown in those seconds, with its sound, and it was seen on an emulator. Since C below it is not shown.
  - **During probation a clock that is really set back by more than 2 minutes is followed at once,** not after ten minutes. That is what MilO did before it kept a clock.
  - **A date left ahead for 2 minutes or more after such a start is believed,** and its way back is then held for ten watched minutes.
  - **One gap is known and left open.** MilO starts with no anchor inside one jump, no MilO process reads the clock until a later jump, and a reading falls into that one. The two readings agree and are more than 2 minutes apart, so the anchor passes for proven with tomorrow's date. It needs Android to start MilO for both jumps and for neither return. It is pinned by a unit test and logged as debt. Since C below an alarm wakes MilO two minutes after the first start, which is a reading between the two jumps; the gap is left only where Android does not deliver that alarm.

## Decided on 2026-10-09, after the emulator run

The build with A and B ran on a stock-Android emulator the same day: 18 jumps of 12 to 20 seconds, made through Settings. No trip, wait, hold-off or stored point was changed. One finding was major and one small one was worth a change. The whole list is in FINDINGS_LOG, "MilO's clock ran on an emulator".

**C. A clock on probation shows no notification, and is woken two minutes on.**

- **What was found.** The emulator was restarted, and the trick done before any MilO process had run. Android started MilO in the second the date went ahead. MilO took tomorrow, and three seconds later showed "No trip recorded today", with its sound, after a day with six trips. It was taken away when the date came back, 12 seconds later. That was the cost written down under B; it was now seen.
- **Decided.** While MilO's anchor is on probation, a "No trip recorded today" or a monthly reminder that would be due is not shown and not stored. The Log says why. The rules that decide whether one is due are not changed.
- **And so that a true one is not lost:** while its clock agrees with the phone's and is on probation, MilO asks for both daily alarms 2 minutes ahead (`PROBATION_MS`), counted from boot, and not for the next day. When the alarm comes, that reading confirms the clock, or sees the date back and starts two more minutes. Then the alarm is asked for in the normal way and a notification that is truly due is shown.
- **Why it cannot loop.** If the phone's clock goes ahead of an anchor on probation and stays there, MilO asks for the alarm of A, which counts to the real time, and not for two minutes again.
- **What it also closes.** A MilO that Android started inside a jump and froze again kept the alarm of a wrong day until something else read the time, and its anchor stayed unconfirmed for as long. Seen on the emulator: a MilO that Android started and left alone was frozen within seconds, and the 30-second look of B does not run in a frozen process.
- **What it costs.** After every restart of the phone, and once after the update that brings this clock, the phone is woken once more, two minutes after MilO's first start, and the Log has a few more lines. A notification that is due at that start comes two to four minutes later. On a phone that gives no boot number this would be so at every start of MilO.
- **Who decided.** It was decided while the finding was being fixed, not by Shawn, and is reported to him to confirm or to have taken out.

**D. A clock that is set back: the watch has both alarms asked for.**

- **What was found.** A date set ahead makes Android deliver both alarms, and each then asks to count from boot (A). A clock set back delivers nothing, so nothing asked: both alarms waited for a time of day by a clock MilO did not believe.
- **Decided.** When MilO sets a backward change aside, it has both alarms asked for once. They count from boot by themselves. Not for a step ahead: Android's own delivery does it there, and it would add lines to every trick.

## Consequences

**Better.** A jump of the phone's date no longer reaches a trip, a wait, the hold-off, the grace period, the daily check, the monthly reminder, the event log's dates or any screen. The fix can be seen working: a jump that MilO sees leaves its line in the Log.

**What it costs.**

- **A date that is really changed by hand is followed ten minutes late,** and only by a MilO that has watched it for ten minutes in a row: during a trip, a wait beside the truck, or with MilO open. Until then MilO's times differ from the phone's.
- **The moment a real change is followed, MilO's time jumps once,** by the size of the change. A trip that is open at that moment is hit as trips were before this decision. A date held ahead for longer than ten minutes is believed, and so is its way back, ten minutes late.
- **The first start after this update, and the first MilO process after a restart of the phone, take the phone's clock as it is.** If the date is ahead in that moment, MilO goes by it until the phone's clock comes back, which is seconds. What it costs is under B above.
- **While the clocks disagree, the daily alarms count from boot.** If MilO's process is gone when the date comes back, that alarm remains and comes at the real time. Nothing is missing any more (A above).
- **After a restart of the phone, a notification that is due at MilO's first start comes two to four minutes later,** and the phone is woken once more (C above).
- **A date that is left ahead is followed only by a MilO that watches it.** Seen on the emulator: idle in the background MilO was frozen and had not followed after 10.5 minutes; opened, it followed ten minutes later. The alarms counted from boot all the while.
- **What Android stamps itself still goes by the phone's clock.** The record of a process that died inside a jump is written at the time MilO read it, with a note. The trip notification's running time is worked out by Android: posted inside a jump, it reads a day long until its next update.
- A few more lines in the event log on an evening of jumps: each daily alarm that arrives, and each asking again.

**Locked in.** Everything in MilO that needs the time takes it from one place. Code that reads the phone's clock itself fails a unit test.

## Considered and not taken

- **Measuring each duration on the clock that counts from boot, rule by rule.** The trip rules, the wait, the hold-off and the grace period would each have been changed, and each stored time would have needed a second form. It is some forty places, and one missed place is a lost trip. A day ("is it Thursday?") cannot be measured on that clock at all.
- **Android's network clock.** Android can say what the network thinks the time is. It cannot always say it: with no network time yet, it has no answer, and MilO must tell the time in a truck with no signal.
- **Asking him not to do it.** He will keep doing it. A mileage log that depends on its owner's habits on another app is not reliable.
