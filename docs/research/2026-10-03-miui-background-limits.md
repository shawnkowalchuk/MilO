# Research: Xiaomi MIUI and HyperOS background limits on the POCO X5

> Gathered 2026-10-03 by a research agent reading live sources. Every finding was then re-checked by an independent verifier; its verdict is shown on each finding.
> Much of this rests on decompiled system code and forum reports, not vendor documentation. Nothing was tested on Shawn's phone. This file is evidence for the ADRs and is not kept current.

## Summary

Scope: what MIUI 13/14 and HyperOS do to a background-started app on a POCO X5, how to switch each thing off, how to deep-link and detect it. Research only; no project files were touched.

Headline: on Xiaomi the dead-process start path MilO depends on is gated by one hidden MIUI app-op, OP_AUTO_START (10008), shown to the user as Autostart / Background autostart. Decompiled HyperOS (Android 14) system code shows that when the app's process is not running and this op is not allowed, the system (a) drops implicit manifest broadcasts such as ACL_CONNECTED with the log line "process is not permitted to auto start", (b) rejects start/bind of the app's services ("MIUILOG- Reject service"), and (c) refuses sticky-service restarts ("MIUILOG- Reject RestartService"). Autostart is off by default for third-party apps, so a freshly sideloaded MilO will not start from the truck's Bluetooth until the owner turns it on. This is very likely why the owner's current mileage app misses trips.

Second layer: the per-app Battery saver profile (PowerKeeper). Only "No restrictions" exempts the app from Xiaomi's freezer and automatic kills; the padlock in recents only protects against the user-facing cleaners. On non-China (international) ROMs, which is what POCO phones ship, the cleaners use a plain process kill rather than a true force-stop, and swiping a card away spares a process that has a running foreground service; the clear-all X button kills unlocked apps including their foreground services.

Versions: POCO X5 5G (moonstone) launched on MIUI 13 / Android 12, went to MIUI 14 / Android 13, and tops out at HyperOS 1.0.x on Android 14 (global OS1.0.26.0.UMPMIXM, Dec 2025). POCO X5 Pro 5G (redwood) launched on MIUI 14 / Android 12 and tops out at HyperOS 2.0.x, still Android 14 (global OS2.0.17.0.UMSMIXM, Jan 2026); no Android 15 for either.

Deep links: the Autostart list activity (com.miui.securitycenter / com.miui.permcenter.autostart.AutoStartManagementActivity) still works through HyperOS 3; the PowerKeeper per-app page (com.miui.powerkeeper / .ui.HiddenAppsConfigActivity) is reported removed only in HyperOS 3, which neither POCO X5 can run. Always wrap in try/catch and fall back to the standard app-details screen.

Detection: Autostart can be read with reflection on the AOSP-greylisted AppOpsManager.checkOpNoThrow(10008, uid, pkg), but it reports "allowed" when MIUI optimization is off and has some false-positive reports, so treat it as advisory. "No restrictions" has no API; PowerManager.isIgnoringBatteryOptimizations is a usable proxy on recent builds. The only proof is an end-to-end self-test.

Confidence note: the mechanism findings come from decompiled miui-services.jar of an unspecified HyperOS/Android 14 device (2024) plus a 2026 reverse-engineering report on China HyperOS; they match the 2018-era log strings and forum reports, but must be confirmed on the owner's phone with logcat. MileIQ's and Komoot's support pages and XDA threads returned HTTP 403 and were not read.

The relayed user request (an R2-D2 sound when connecting and running) is covered by one finding, one recommendation and one owner question.

## Findings

### 1. POCO X5 5G (moonstone) shipped with MIUI 13 on Android 12 and its newest build is HyperOS 1.0.x on Android 14; no HyperOS 2 build is listed.

Global ROM list: first build V13.0.2.0.SMPMIXM (MIUI 13, Android 12, 2022-12-17); MIUI 14 / Android 13 from V14.0.2.0.TMPMIXM (2023-04-12) to V14.0.6.0.TMPMIXM (2023-11-06); HyperOS 1 / Android 14 from OS1.0.3.0.UMPMIXM (2024-01-29) to OS1.0.26.0.UMPMIXM (2025-12-24), the newest listed. EEA newest is OS1.0.25.0.UMPEUXM (2026-01-04). So 'Android 14 or older' on this model means HyperOS 1 (A14), MIUI 14 (A13) or MIUI 13 (A12). Trackers may lag; the phone's own About screen is authoritative.

- Confidence: high · Applies to: POCO X5 5G (moonstone)
- Source: https://xiaomirom.com/en/rom/poco-x5-5g-moonstone-global-fastboot-recovery-rom/
- Verifier: **confirmed**. A second tracker agrees: launch MIUI 13 / Android 12; newest Global OS1.0.26.0.UMPMIXM (Dec 2025), EEA OS1.0.25.0.UMPEUXM (Jan 2026), India 1.0.22.0, no OS2 build. No build newer than Jan 2026 was found on either tracker as of Oct 2026. (https://miuirom.org/phones/poco-x5-5g)

### 2. POCO X5 Pro 5G (redwood) shipped with MIUI 14 on Android 12 and its newest build is HyperOS 2.0.x, still on Android 14; it will not get Android 15.

Timeline: launch Feb 2023 MIUI 14 / Android 12; April 2023 MIUI 14 / Android 13; Jan 2024 HyperOS 1 / Android 14; April 2025 HyperOS 2 / Android 14. Newest global build OS2.0.17.0.UMSMIXM (2026-01-19); EEA 2.0.14.0.UMSEUXM; India 2.0.15.0.UMSINXM. Support window listed as until February 2026. A separate article (Jan 2025) states there will be no Android 15 for this model.

- Confidence: high · Applies to: POCO X5 Pro 5G (redwood)
- Source: https://miuirom.org/phones/poco-x5-pro-5g
- Verifier: **confirmed**. Newest builds confirmed: Global OS2.0.17.0.UMSMIXM (recovery 2026-01-19), EEA 2.0.14.0, India 2.0.15.0; Indonesia is reported as 2.0.13.0 by one search result and 2.0.15.0 by miuirom.org. HyperOS 2 on this model is Android 14 based; no Android 15. (https://hyperosinsider.com/poco-x5-pro-hyperos-2-update-status-is-revealed/)

### 3. The X5 Pro 5G reached end of support on 2026-02-06; the X5 5G is listed as supported until February 2027.

abit.ee (2026-02-19, citing Xiaomi's EOL list on mi.com) says the POCO X5 Pro 5G 'officially reached EOL on February 6, 2026'. miuirom.org lists the X5 5G as 'Active until February 2027, except TR'. One secondary article (gagadget, July 2026) says both hit EOL in Feb 2026, so the X5 5G date is not fully consistent across sources. Xiaomi's own trust.mi.com list could not be parsed. Practical meaning: the OS on this phone is effectively frozen, so behaviour verified once should stay stable.

- Confidence: medium · Applies to: both models
- Source: https://abit.ee/en/smartphones/xiaomi-redmi-poco-hyperos-updates-eol-support-android-smartphones-en
- Verifier: **uncertain**. X5 Pro EOL on 2026-02-06 is confirmed. The X5 5G date is contradicted: nokiapoweruser (17 Jul 2026) says Xiaomi added both 'POCO X5' and 'POCO X5 Pro 5G' to its EOL list; gagadget (3 Jul 2026), ximitime and nokiamob all say both ended in Q1/Feb 2026; only miuirom.org says Feb 2027, and phonelifespan.tech shows the two models swapped. No moonstone build newer than Dec 2025/Jan 2026 exists on two trackers, which fits support having already ended. Treat both phones as frozen; read the exact build from About phone. (https://nokiapoweruser.com/xiaomi-10-more-devices-end-of-life-eol-hyperos-updates-stop/)

### 4. Autostart is a hidden MIUI app-op, OP_AUTO_START = 10008, stored per package; mode 0 means allowed.

Decompiled android.miui.AppOpsUtils: getApplicationAutoStart(Context, String) simply returns AppOpsManager.checkOpNoThrow(10008, uid, packageName); setApplicationAutoStart writes mode 0 when on and mode 2 when off. Telegram's XiaomiUtilities lists the same constant along with OP_BOOT_COMPLETED 10007, OP_SHOW_WHEN_LOCKED 10020, OP_BACKGROUND_START_ACTIVITY 10021 and OP_SERVICE_FOREGROUND 10023. Because the 'off' value can be 1 or 2, a check must test 'equals 0', not 'equals 1'.

- Confidence: medium · Applies to: MIUI and HyperOS (decompiled framework, older MIUI build)
- Source: https://raw.githubusercontent.com/shivatejapeddi/miuiframework/cd456214274c046663aefce4d282bea0151f1f89/sources/android/miui/AppOpsUtils.java
- Verifier: **confirmed**. Confirmed in three places: AppOpsUtils (checkOpNoThrow(10008); set writes 0 or 2), Telegram constants (10007, 10008, 10020, 10021, 10022, 10023), and system-side ProcessManagerService.isAllowAutoStart which returns checkOpNoThrow(10008) == 0. The MIUI-Autostart library treats 1 as disabled, so 'equals 0' is the only safe test. (https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/ProcessManagerService.java)

### 5. With Autostart off and the process dead, implicit manifest broadcasts (which includes BluetoothDevice.ACTION_ACL_CONNECTED / ACL_DISCONNECTED) are dropped.

BroadcastQueueModernStubImpl.checkApplicationAutoStart runs when the receiver's process is not warm. For a non-system app, if the intent has no explicit component, the package is not running and op 10008 is not allowed, it logs "Unable to launch app <pkg>/<uid> for broadcast <intent>: process is not permitted to  auto start" and skips the receiver. ACL_CONNECTED is sent as an implicit broadcast, so it falls under this gate. The same log string from the older BroadcastQueueInjector class appears in a 2018 analysis, so the gate has existed across MIUI generations. Logcat filter to use on the phone: "not permitted to".

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile; same log seen on older MIUI
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/BroadcastQueueModernStubImpl.java
- Verifier: **confirmed**. A second, older decompile (BroadcastQueueImpl, different repo) has the same rule in cleaner form: allowed if the intent has a component, or the package is running, or op 10008 is 0; otherwise it logs 'Unable to launch app ... process is not permitted to  auto start'. For a non-system app, setPackage() alone does not bypass it; only an explicit component does. Android's own docs list ACL_CONNECTED/ACL_DISCONNECTED as implicit broadcasts that manifest receivers may still get, so on this phone the MIUI gate is the thing that blocks them. (https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/BroadcastQueueImpl.java)

### 6. In the same code, broadcasts carrying an explicit component skip the autostart test, FCM is always let through on international ROMs, and BOOT_COMPLETED is checked against a separate op on international ROMs.

The autostart branch is only entered when r.intent.getComponent() == null. On Build.IS_INTERNATIONAL_BUILD the action com.google.android.c2dm.intent.RECEIVE returns true immediately. BOOT_COMPLETED and LOCKED_BOOT_COMPLETED are on an 'international special action' list that is allowed when AppOpsUtils.getApplicationSpecialBroadcast returns 0, otherwise they need Autostart. Implication to test, not yet proven: PendingIntent callbacks from Play services (activity-transition, fused location) that target MilO's receiver explicitly may wake a dead process even where ACL_CONNECTED does not. The decompile has some mangled control flow, so treat this as a lead.

- Confidence: low · Applies to: HyperOS 1 / Android 14 decompile, international ROM
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/BroadcastQueueModernStubImpl.java
- Verifier: **confirmed**. True as a reading of the HyperOS 1 decompile, with two limits. (1) The older BroadcastQueueImpl decompile has the explicit-component and c2dm bypasses but no BOOT_COMPLETED special-op list, so on MIUI 13/14 assume BOOT_COMPLETED needs Autostart. (2) Explicit broadcasts still pass through WakePathChecker and SmartPower's interception, so 'a Play services PendingIntent wakes a dead app with Autostart off' stays unproven. Keep it as low confidence and do not design around it. (https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/BroadcastQueueImpl.java)

### 7. Starting or binding a service in a non-running third-party package is also gated by Autostart, which is the path a CompanionDeviceService bind would take.

AutoStartManagerServiceStubImpl.isAllowStartService: for a non-system, non-preloaded package that is not running it calls noteOpNoThrow(10008); if the result is not 0 it logs "MIUILOG- Reject service :<intent> userId : .. uid : .." and returns false. ActiveServiceManagementImpl.canBindService delegates to the same method, so bindService is covered too. Whether binds originating from system_server (as CompanionDeviceManager's are) are exempted could not be determined: the caller lives in services.jar, which was not available; a helper that records 'is caller system' exists, hinting at an exemption. Must be tested on the phone with Autostart off: look for "MIUILOG- Reject service" when the truck connects.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/AutoStartManagerServiceStubImpl.java
- Verifier: **uncertain**. The gate itself is confirmed in two decompiles (isAllowStartService checks op 10008, logs 'MIUILOG- Reject service', no caller-uid exemption inside the method). Whether a CompanionDeviceManager bind reaches it is not proven: in the MIUI 12 decompile the gate is called only from specific system managers (notification listeners, sync, jobs, text services, chooser) and the companion manager has no hook; the HyperOS call site of canBindService is not public. Real-world evidence leans toward 'not exempt': two independent projects logged 'MIUILOG- Reject service' plus 'Unable to bind notification listener service' on HyperOS (one on a Redmi Note 13) when the system itself tried to bind with Autostart off, and one notes that turning Autostart on was not enough until the app was fully closed and reopened. Build as if Autostart is mandatory and test on the phone. (https://github.com/eyupgok/prism-hub/blob/HEAD/CLAUDE.md)

### 8. After MIUI kills the process, a sticky foreground service is not restarted unless Autostart is on.

ActiveServiceManagementImpl.canRestartServiceLocked calls AutoStartManagerServiceStub.canRestartServiceLocked, which checks op 10008 and logs "MIUILOG- Reject RestartService service :<component> uid : .." when it is not allowed. So START_STICKY alone does not bring a recording service back on this phone.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ActiveServiceManagementImpl.java
- Verifier: **confirmed**. Confirmed in the HyperOS decompile (canRestartServiceLocked returns false unless op 10008 is 0, logging 'MIUILOG- Reject RestartService service'). The MIUI 12 decompile has the same hook (ActiveServices calls ActiveServicesInjector.canRestartServiceLocked), so this holds across MIUI 13/14 and HyperOS. A second Whetstone check follows it and can also deny the restart. (https://raw.githubusercontent.com/Fklearn/XiaomiFramework/master/mi2s_10_miui12/src/main/java/com/android/server/am/ActiveServices.java)

### 9. JobScheduler / WorkManager jobs are not cancelled by the autostart gate on international ROMs, but are on China ROMs.

JobServiceContextImpl.checkIfCancelJob returns false (do not cancel) when Build.IS_INTERNATIONAL_BUILD is true; otherwise it cancels the job if isAllowStartService fails. A 2018 report on MIUI 10 / Android 8.1 shows a JobScheduler-bound service rejected with "MIUILOG- Reject service" until the app's autostart was enabled, so older or China builds did gate jobs. On the owner's POCO ROM a periodic WorkManager job is therefore a plausible safety net, but it is still subject to Doze and to the per-app Battery saver profile.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile; MIUI 10 report for the older behaviour
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/job/JobServiceContextImpl.java
- Verifier: **confirmed**. JobServiceContextImpl.checkIfCancelJob read verbatim: returns false when Build.IS_INTERNATIONAL_BUILD. The ACRA report is dated 21 Feb 2019, not 2018 (Xiaomi Note 3, MIUI 10 / Android 8.1, JobSenderService rejected until autostart was enabled). Caveat: this only shows the job is not cancelled; the bind to the job service may still meet the same unresolved bind gate as finding 7, so treat a periodic job as a best-effort net, not a guarantee. (https://github.com/ACRA/acra/issues/732)

### 10. Autostart is off by default for third-party apps, so a sideloaded app does not start from the background until the owner enables it.

A 2026 write-up on Android battery killers states the Xiaomi autostart permission 'is off by default for third-party apps'. DriveQuant's HyperOS guide screenshot shows the Background autostart list with '5 apps can start in the background' and '32 apps aren't allowed'. Xiaomi keeps an internal preloaded whitelist (PreloadedAppPolicy) that a debug build installed from Android Studio cannot be on. Separately, standard Android keeps a force-stopped app in the stopped state until the user launches it, so MilO must be opened once after each install.

- Confidence: medium · Applies to: MIUI 13/14, HyperOS
- Source: https://bglocation.dev/blog/surviving-android-battery-killers-capacitor
- Verifier: **confirmed**. SportsTrackLive's Xiaomi page states Autostart is off by default and is the most common cause of lost tracks. The DriveQuant PDF was opened: its Background autostart screen shows '5 apps can start in the background' and '32 apps aren't allowed to start in the background'. (https://docs.sportstracklive.com/android-battery-saving/xiaomi)

### 11. Turning MIUI optimization off bypasses the broadcast autostart gate, and makes autostart checks read as 'allowed'.

checkApplicationAutoStart returns true at the top when AppOpsUtils.isXOptMode() is true; isXOptMode is the inverse of system property persist.sys.miui_optimization. The maintainer of the MIUI-Autostart library independently reports that the autostart state only reads correctly with MIUI optimization ON, and that Autostart 'mainly has an effect if MIUI Optimizations is turned on'. This is a last-resort lever: it has side effects (changes permission handling, background sync and animation behaviour) and the toggle is hidden or absent on some HyperOS builds.

- Confidence: medium · Applies to: MIUI 12–14, HyperOS 1
- Source: https://github.com/XomaDev/MIUI-Autostart/issues/15
- Verifier: **confirmed**. Both decompiles return true at the top of checkApplicationAutoStart when AppOpsUtils.isXOptMode() is true. Issue 15 comments (read via the GitHub API) confirm a MIUI 13.0.7 user got the right value only with MIUI optimization on, and the maintainer's 'mainly has an affect if MIUI Optimizations is turned on'. Issue 14 shows a wrong reading even with optimization on, so the relationship is not perfectly clean. (https://api.github.com/repos/XomaDev/MIUI-Autostart/issues/15/comments)

### 12. On international ROMs MIUI's cleaners do a plain process kill, not a true force-stop; on China ROMs apps without Autostart are force-stopped.

ProcessCleanerBase.killOnce picks a kill level: 104 = forceStopPackage only if isForceStopEnable() and the caller asked for it; otherwise 102 (killBackgroundProcesses) for LockScreenClean or 103 (plain kill). isForceStopEnable returns false when Build.IS_INTERNATIONAL_BUILD, or the app is a system app, or op 10008 is allowed; the one exception is policy 13 (AutoIdleKill), which always force-stops. POCO ROM variants are non-China builds; confirm with 'adb shell getprop ro.product.mod_device' (should end in _global). Consequence: after a swipe or cleaner kill on this phone the app is dead but not 'stopped', so it can be woken again provided Autostart is on.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile
- Source: https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/ProcessCleanerBase.java
- Verifier: **confirmed**. Read directly: isForceStopEnable returns true for policy 13, otherwise false when IS_INTERNATIONAL_BUILD, system app, autostart allowed, or the package is in list 42. killOnce picks 104 only if that is true and the caller asked for it, else 102 for policy 3, else 103. One exception to keep in mind: policy 13 (AutoIdleKill) force-stops even on international ROMs, which would put the app into the stopped state. (https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/ProcessManagerService.java)

### 13. Swiping a single card away spares a process with a running foreground service on international ROMs; the clear-all X button kills everything that is not locked, foreground services included.

ProcessSceneCleaner.handleSwipeKill skips a process when Build.IS_INTERNATIONAL_BUILD and proc.mServices.hasForegroundServices(); otherwise it kills it. handleKillAll (policy 1 OneKeyClean, the X button) walks every running process and kills it unless whitelisted. A xiaomi.eu forum user reports the same thing from experience: closing all apps with the X button terminates foreground services. So while MilO is idle (no service running) a swipe kills it; while it is recording a swipe does not, but clear-all does unless the card is locked.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile, international ROM
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessSceneCleaner.java
- Verifier: **confirmed**. handleSwipeKill and handleKillAll read directly; a xiaomi.eu user (28 Nov 2024) reports the X button ending foreground services. Two additions: the recents padlock is not consulted for a single-card swipe (policy 7 has no isLockedApplication check), and 'important process' protection only applies to packages on Xiaomi's own dynamic whitelist, so a third-party foreground service gets no extra protection from clear-all. (https://xiaomi.eu/community/threads/miui-14-limit-background-activity-even-after-customization.69748/)

### 14. The padlock in recents protects against user-facing cleaners only, not against PowerKeeper's automatic kills.

ProcessManagerService.isInWhiteList includes isLockedApplication for policies 1, 3, 4, 5, 6, 14, 16 and 22 (OneKeyClean, LockScreenClean, GameClean, OptimizationClean, GarbageClean, AutoSleepClean, AutoSystemAbnormalClean, ScreenOffCPUCheckKill). It returns false for policies 10–13, 15 and 17 (UserDefined, AutoPowerKill, AutoThermalKill, AutoIdleKill, AutoLockOffClean, AutoLockOffCleanByPriority). Locked packages are stored as JSON in the MIUI system setting 'locked_apps'. Kill reason strings are passed to killLocked, so they should appear as the description in ApplicationExitInfo and in logcat.

- Confidence: medium · Applies to: HyperOS 1 / Android 14 decompile
- Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessManagerService.java
- Verifier: **confirmed**. isInWhiteList switch read directly: isLockedApplication is checked for policies 1, 3, 4, 5, 6, 14, 16, 22 and the method returns false for 8-13, 15, 17. Policy names verified against miui.process.ProcessConfig. Locked apps are stored as JSON under MiuiSettings.System 'locked_apps'. Kills go through killLocked(reason, 13, true), so they show up as ApplicationExitInfo REASON_OTHER (13) with the MIUI policy name as the description. (https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessPolicy.java)

### 15. A real Force stop puts the app in Android's stopped state: it receives nothing until the user opens it again.

Android's documentation says an app enters the stopped state when the user force-stops it and should only leave it through direct or indirect user action (launching it, a widget, the share sheet). Android 15 additionally cancels the app's pending intents on entering the state; the phone here is Android 14 at most. For MilO: never use Force stop, and after any force-stop or fresh install the owner must open the app once.

- Confidence: high · Applies to: all Android versions
- Source: https://developer.android.com/about/versions/15/behavior-changes-all
- Verifier: **confirmed**. Android's page confirms the stopped state and that the pending-intent cancellation is new in Android 15, so it does not apply to this phone. (https://developer.android.com/about/versions/15/behavior-changes-all)

### 16. The per-app Battery saver screen has four profiles, and only 'No restrictions' exempts the app from Xiaomi's freezer; it also puts the app on Android's Doze whitelist.

UI choice to stored value: Battery saver (recommended) = miuiAuto; No restrictions = noRestrict; Restrict background apps = restrictBg (closes after 10 minutes in background by default); Restrict background activity = noBg. The value fans out to about ten PowerKeeper controllers (kill-process, background location, frozen-app, sensor, DeviceIdle, app-standby and others). Selecting No restrictions was observed to add the package to Settings.System MILLET_NO_RESTRICT_APP (consulted by the Greezer freezer) and to the DeviceIdle user whitelist. The Doze whitelist alone did not stop Xiaomi's freezer. The same report notes PowerKeeper's Play-services network limiter defaults to off on international builds. Tested on China HyperOS, Android 16, in 2026; older builds may differ.

- Confidence: medium · Applies to: HyperOS (tested on China builds, Android 16); mechanism expected on HyperOS 1/2
- Source: https://github.com/dingwen07/hyperos-fcm-fix/blob/4cd193f9b2ed4d23f2aa070d94d806dede3810b9/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md
- Verifier: **uncertain**. The four profiles and their wording are confirmed on a global HyperOS 1 screenshot (No restrictions; Battery saver (recommended); Restrict background apps, 'Close apps after 10 minutes of background activity'; Restrict background activity). The internal effects (freezer exemption, automatic Doze whitelisting) come from one investigation on China HyperOS / Android 16 and are contradicted by Nova-Android's code comments; a xiaomi.eu HyperOS 2 thread also treats 'battery optimization disabled' and 'No restrictions' as two separate settings that each reset. Do not assume one implies the other on HyperOS 1/2 or MIUI 13/14: set both and check both. (https://xiaomi.eu/community/threads/disable-battery-optimization-for-an-app-permanently.74932/)

### 17. Menu path for No restrictions on HyperOS: Settings > Apps > Manage apps > [app] > Battery saver > No restrictions; on the same App info page turn off 'Pause app activity if unused'.

DriveQuant (a trip-detection SDK vendor) publishes a screenshot walkthrough for HyperOS: Settings > Apps > Manage apps > app > uncheck 'Pause app activity if unused' > Battery saver > 'No restrictions' (described on screen as 'Battery saver doesn't restrict app's activity'). MIUI 12–14 use the same App info > Battery saver path; older alternatives are Security > Battery > App battery saver > app > No restriction.

- Confidence: high · Applies to: HyperOS 1/2; MIUI 12–14 equivalent
- Source: https://www.drivequant.com/hubfs/Tuto%20Smartphones%20-%20EN/Xiaomi/Xiaomi%20-%20HyperOS%20-%20EN.pdf
- Verifier: **confirmed**. AdGuard's MIUI 13+/HyperOS instructions give the identical path, and every screen of the DriveQuant PDF was viewed and matches. (https://adguard.com/kb/adguard-for-android/solving-problems/background-work/)

### 18. Menu path for Autostart: HyperOS is Settings > Apps > Permissions > Background autostart; MIUI is Security app > Permissions > Autostart, or the Autostart toggle on the app's App info page.

Xiaomi's own FAQ gives: Settings > Apps > Permissions > Background autostart, then switch it on for the app. dontkillmyapp gives for MIUI 14: Settings > Apps > Your app > App permissions > Background autostart, and for older MIUI: Security app > Permissions > Auto-start. The HyperOS Manage apps screen also has a 'Background autostart' shortcut at the top.

- Confidence: high · Applies to: HyperOS 1/2; MIUI 13/14 via dontkillmyapp
- Source: https://www.mi.com/global/support/faq/details/KA-507611/
- Verifier: **confirmed**. AdGuard documents both routes for MIUI 13+/HyperOS: Settings > Apps > Permissions > Background autostart, or Settings > Apps > Manage apps > [app] > Autostart. Xiaomi's FAQ wording was confirmed through search (the page itself returns 403). The label is 'Autostart' on MIUI and 'Background autostart' on HyperOS; it is the same switch. (https://adguard.com/kb/adguard-for-android/solving-problems/background-work/)

### 19. Locking the app: open recents, long-press the card and tap the padlock (older MIUI: drag the card down); second route is Security app > Boost speed > settings cog > Lock apps.

dontkillmyapp documents both gestures and the Boost speed route, and notes that an app locked in Boost speed 'may be spared by Ultra battery saver'. Dragging down again or tapping the padlock again unlocks it.

- Confidence: high · Applies to: MIUI 12–14, HyperOS
- Source: https://dontkillmyapp.com/xiaomi
- Verifier: **confirmed**. SportsTrackLive describes the same gesture (swipe down or long-press until the padlock appears, then tap it). An XDA thread also gives the Boost speed > Lock apps route. (https://docs.sportstracklive.com/android-battery-saving/xiaomi)

### 20. 'Show on Lock screen', 'Display pop-up windows while running in the background' and 'Start in background' live under App info > Other permissions and are separate MIUI app-ops; they govern activities, not service starts.

Telegram maps them to OP_SHOW_WHEN_LOCKED 10020 and OP_BACKGROUND_START_ACTIVITY 10021 and checks them with the same reflection call. They matter only if MilO ever opens a screen from the background or over the lock screen; starting a foreground service does not need them. Tracker vendors still tell users to switch them on (X-GPS: Other permissions > 'Show on the Lock screen' and 'Start in background'), and Tasker's FAQ asks for Auto Start plus 'Display on Lock Screen'. Enabling them is harmless.

- Confidence: medium · Applies to: MIUI 12–14, HyperOS 1/2
- Source: https://raw.githubusercontent.com/DrKLO/Telegram/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/src/main/java/org/telegram/messenger/XiaomiUtilities.java
- Verifier: **confirmed**. X-GPS's Xiaomi page and SportsTrackLive both place them under Other permissions; a second open-source app checks ops 10020 and 10021 by the same reflection. That app also checks a third one the finding leaves out: 'Permanent notification' (op 10023, OP_SERVICE_FOREGROUND). It is worth switching on for an app whose whole job is a foreground-service notification. (https://x-gps.app/en/help/tracker/xiaomi/settings/)

### 21. MIUI optimization is a developer option: Settings > Additional settings > Developer options > 'Turn on MIUI optimization' (HyperOS: 'Turn on system optimization'), often hidden until 'Reset to default values' is tapped.

Developer options are unlocked by tapping the MIUI/OS version in About phone repeatedly. On MIUI 13+ and HyperOS the toggle is frequently hidden; the documented way to reveal it is to tap 'Reset to default values' near the bottom of Developer options (some guides say several times). On some HyperOS devices it is not available at all. Reported side effects of turning it off: unpredictable app behaviour, changed permission handling, background sync changes.

- Confidence: medium · Applies to: MIUI 12–14, HyperOS
- Source: https://appuals.com/enable-or-disable-miui-optimization/
- Verifier: **confirmed**. Other guides agree: unlock Developer options by tapping the OS version, open Additional settings > Developer options, tap 'Reset to default values' several times to reveal the toggle; on HyperOS it is named 'Turn on system optimization'. (https://droidwin.com/fix-miui-optimization-missing-in-developer-options-in-xiaomi/)

### 22. System-wide battery modes: Battery saver reduces background activity and Ultra battery saver restricts almost everything; both are set under Settings > Battery > Current mode.

Xiaomi: Battery saver 'can reduce background activity of Apps, stop sync, and minimize system animations'. Ultra battery saver 'restricts most power consuming features. Only calls, messages, and network connectivity are not affected'; path Settings > Battery > Current mode > Ultra battery saver, also a Control centre tile. DriveQuant's guide tells users to keep Current mode on Balanced or Performance. Neither saver mode should be on during a work shift.

- Confidence: high · Applies to: MIUI 14, HyperOS
- Source: https://www.mi.com/global/support/article/KA-36287/
- Verifier: **confirmed**. The DriveQuant HyperOS screenshot shows Settings > Battery > Current mode with four choices: Performance mode, Balanced, Battery saver, Ultra battery saver. Xiaomi's Ultra battery saver wording was confirmed via search. (https://www.drivequant.com/hubfs/Tuto%20Smartphones%20-%20EN/Xiaomi/Xiaomi%20-%20HyperOS%20-%20EN.pdf)

### 23. Two lock-screen battery features should be set to Never: 'Turn off mobile data when device is locked' and 'Clear cache when device is locked'.

Both are under Settings > Battery > gear icon / Additional features (also Security > Battery > Additional features). Xiaomi describes the first as disabling mobile data after lock; the second clears memory after the screen locks, which corresponds to the LockScreenClean / AutoLockOffClean kill reasons in the system code. MilO has no backend so mobile data is not critical, but assisted GPS and Play services benefit from it. A 'Sleep mode' item under Battery > Scenarios is mentioned by third-party guides but could not be confirmed from a source that was opened.

- Confidence: high · Applies to: MIUI 14, HyperOS
- Source: https://www.mi.com/uk/support/faq/details/KA-170677/
- Verifier: **confirmed**. Xiaomi's FAQs give Settings > Battery > Additional features > 'Turn off mobile data when device is locked' (also Security app > Battery > Additional features), with a Never option; DroidWin gives Security app > Battery > settings icon > 'Clear cache when device is locked' > Never. The 'Sleep mode' item the researcher could not confirm does exist per an XDA guide: Battery > settings icon > Scenarios > Sleep mode (seen only in search results; the page blocks fetching). Add 'turn Sleep mode off if present' to the checklist. (https://www.mi.com/global/support/faq/details/KA-503354/)

### 24. Memory extension is at Settings > Additional settings > Memory extension; no evidence was found that it causes or prevents app kills.

Xiaomi describes it as ZRAM-based extra RAM. Forum guides sometimes suggest turning it off, but no source that was opened ties it to background kills. Leave it as it is unless testing shows low-memory kills.

- Confidence: low · Applies to: MIUI 13/14, HyperOS
- Source: https://www.mi.com/uk/support/article/KA-12358/
- Verifier: **confirmed**. Path confirmed by an independent guide. No source tying it to background kills was found by me either. (https://www.nextpit.com/how-tos/hyperos-memory-extension-feature)

### 25. Xiaomi's automatic app backup kills all running apps, and settings can silently revert, so both need watching.

Tasker's official FAQ: 'On Xiaomi devices disable automatic backup of apps because that process kills all running apps'. A Redmi Note 4 / MIUI 10 report shows the per-app background setting reverting from 'No restrictions' to 'Battery saver' after each reboot, and a 2025 xiaomi.eu thread reports battery-optimization exemptions being re-enabled on HyperOS 2 for some apps after reboots. MilO should therefore re-check its own state at every start rather than assuming setup is permanent.

- Confidence: medium · Applies to: MIUI 10 through HyperOS 2
- Source: https://tasker.joaoapps.com/userguide/en/faqs/faq-problem.html
- Verifier: **confirmed**. Tasker's FAQ wording confirmed. Reverts are corroborated by two xiaomi.eu threads: per-app 'No restrictions' reverting to MIUI battery saver after reboot (Oct-Dec 2020) and battery-optimization exemptions re-enabling after reboots and updates on HyperOS 1 and 2. The decompile also has explicit backup handling in the cleaners, which fits backup being a kill trigger. (https://xiaomi.eu/community/threads/battery-saver-settings-for-individual-apps-reset-after-reboot.58130/)

### 26. A location-type foreground service started while the app is in the background needs 'Allow all the time' location; having battery optimization turned off is itself one of Android's exemptions for starting a foreground service from the background.

Android docs: with only while-in-use location, creating a location foreground service from the background throws SecurityException. Listed exemptions from the background-start restriction include: the user turned off battery optimizations for the app, the app uses Companion Device Manager with REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND or REQUEST_COMPANION_RUN_IN_BACKGROUND, and geofencing or activity-recognition transition events. Since 'No restrictions' appears to add the app to the Doze whitelist, that single Xiaomi setting helps on both the Xiaomi and the AOSP side.

- Confidence: high · Applies to: Android 12–14
- Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Verifier: **confirmed**. Both points are in Android's docs. One precision that matters on this phone: the SecurityException is the Android 14 (target API 34) behaviour. On Android 12/13 the service starts but gets no location, with only a logcat line ('Foreground service started from background can not have location/camera/microphone access'). The closing remark that 'No restrictions' also covers the Android exemption rests on the unproven part of finding 16; request the battery-optimization exemption explicitly. (https://developer.android.com/develop/background-work/services/fgs/service-types)

### 27. Deep link to the Autostart list: explicit component com.miui.securitycenter / com.miui.permcenter.autostart.AutoStartManagementActivity; it still works on HyperOS, including HyperOS 3.

Used unchanged by KeyMapper (code added Sept 2026), by the AutoStarter library, and by Nova-Android, whose source comments say it was verified on HyperOS 3 (OS3.0.4) and add the action 'miui.intent.action.OP_AUTO_START' as a second candidate. It opens the global list, not the app's own row, so the UI must tell the owner to find MilO and switch it on. No extras are needed.

- Confidence: medium · Applies to: MIUI 10 through HyperOS 3
- Source: https://raw.githubusercontent.com/keymapperorg/KeyMapper/7ac384a9dbd6214eb7f5b39ade93a0402ef22469/base/src/main/java/io/github/sds100/keymapper/base/expertmode/xiaomi/XiaomiOptimizationUseCase.kt
- Verifier: **confirmed**. The same component is used unchanged by several unrelated, currently maintained apps (voice, phylax, Bada, Lazy-Claw, DPIP), some with action miui.intent.action.OP_AUTO_START as a second candidate. The HyperOS 3 statement rests on Nova's comment alone (Redmi Pad 2 Pro, OS3.0.4) and does not matter for this phone. No source documents a test on a POCO X5 specifically. (https://github.com/makesnosense/voice/blob/HEAD/mobile/android/app/src/main/java/org/voicepopuli/voice/nativepermissions/XiaomiPermissions.kt)

### 28. Deep link to the per-app Battery saver chooser: com.miui.powerkeeper / com.miui.powerkeeper.ui.HiddenAppsConfigActivity with string extras package_name and package_label; reported removed in HyperOS 3 only.

KeyMapper and Nova-Android both use exactly these extras. Nova's comments: on HyperOS 3 this PowerKeeper page is gone, and the standard android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS intent is intercepted by Security Center's PowerDetailActivity, which opens the Battery saver page for the app; last-resort screen is com.miui.securitycenter / com.miui.powercenter.PowerSettings. Neither POCO X5 can run HyperOS 3, so the PowerKeeper activity should be present, but no source confirmed it specifically on HyperOS 1 or 2. Note one open-source list uses the extra key 'packageName' instead; 'package_name' is the form used by the maintained apps.

- Confidence: medium · Applies to: MIUI through HyperOS 2; changed in HyperOS 3
- Source: https://raw.githubusercontent.com/confeden/Nova-Android/ac46079f7d5f799f3a7672524db808a260ad2431/app/src/main/java/com/example/nova/VendorBackgroundSettingsHelper.kt
- Verifier: **confirmed**. Component and both extra keys match in four more independent codebases (ProtonVPN-Next, Lazy-Claw, whitelist-bypass, DPIP). One of them (SmartIsland) pairs the class with package com.miui.securitycenter, which looks like a bug; use com.miui.powerkeeper. Still no source that states a test on HyperOS 1 or 2 for this exact activity, and 'removed in HyperOS 3' is Nova's comment only, so keep the try/catch fallback. (https://github.com/SMH01-MOD-NEXT/ProtonVPN-Next/blob/HEAD/app/src/main/java/ru/protonmod/next/utils/system/SystemUtils.kt)

### 29. Deep link to the per-app MIUI permission editor (Other permissions): action miui.intent.action.APP_PERM_EDITOR with extra extra_pkgname.

Telegram builds it as Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter") with extras extra_package_uid (own uid) and extra_pkgname (own package). On classic MIUI this editor contains the Autostart toggle; Nova's comments say that on HyperOS 3 it no longer does. Use it for 'Show on Lock screen' and the pop-up window permission, not as the only route to Autostart.

- Confidence: medium · Applies to: MIUI 10–14, HyperOS 1/2
- Source: https://raw.githubusercontent.com/DrKLO/Telegram/f2908b14133bbffbf7ab04f641ecb5bfaf533242/TMessagesProj/src/main/java/org/telegram/messenger/XiaomiUtilities.java
- Verifier: **confirmed**. A second app builds it exactly as Telegram does (setPackage com.miui.securitycenter, extras extra_package_uid and extra_pkgname). A third names the target class com.miui.permcenter.permissions.PermissionsEditorActivity. (https://github.com/makesnosense/voice/blob/HEAD/mobile/android/app/src/main/java/org/voicepopuli/voice/nativepermissions/XiaomiPermissions.kt)

### 30. Safe fallback pattern: start the vendor activity inside try/catch for ActivityNotFoundException and SecurityException, then fall back to Settings.ACTION_APPLICATION_DETAILS_SETTINGS; do not pre-check with resolveActivity.

KeyMapper's comment: 'Some Xiaomi ROMs remove or rename these activities, so fall back to the app's own details settings screen'. DPIP's helper explains why not to probe first: on Android 11+ package visibility hides these components unless a <queries> entry is declared for each vendor package, so a resolve check reports 'unavailable' on exactly the devices that have the screen. On MIUI/HyperOS the app-details screen is Xiaomi's App info page, which contains Autostart, Battery saver and Other permissions, so the fallback still lands one tap away.

- Confidence: high · Applies to: all MIUI / HyperOS versions
- Source: https://raw.githubusercontent.com/ExpTechTW/DPIP/fcd37d293db0088c9db2e025162a6609238e1982/android/app/src/main/kotlin/com/exptech/dpip/BackgroundExecutionChannel.kt
- Verifier: **confirmed**. Android's package-visibility guide says the same: startActivity needs no <queries> entry, and the recommended approach is to fire the intent and catch ActivityNotFoundException rather than resolve first. (https://developer.android.com/training/package-visibility/use-cases)

### 31. Autostart can be read without any bypass library: reflect AppOpsManager.checkOpNoThrow(int, int, String) with op 10008 and compare to MODE_ALLOWED.

In AOSP android14-release that method is annotated @UnsupportedAppUsage with no max target SDK, i.e. on the 'unsupported' list that reflection may still reach. Telegram ships exactly this call for ops 10020 and 10021. A contributor on the MIUI-Autostart tracker posted (2026-09-29) a Kotlin version for op 10008 after testing on a Redmi 13C, HyperOS 2.0.206 / Android 15, where two other reflection ideas had failed with ClassNotFoundException / NoSuchMethodException. Catch ReflectiveOperationException and SecurityException and report 'unknown'.

- Confidence: medium · Applies to: MIUI 10–14, HyperOS 1/2
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/core/java/android/app/AppOpsManager.java
- Verifier: **confirmed**. Checked the AOSP source for android12-release, android13-release and android14-release: the method is @UnsupportedAppUsage with no maxTargetSdk in all three. The 2026-09-29 comment and its Kotlin code were read through the GitHub API. A second app ships the same call for op 10008. Note the only documented device test is a Redmi 13C on HyperOS 2.0.206 / Android 15, not this phone, and that app (like Telegram) returns 'granted' on any exception; MilO should return 'unknown' instead. (https://api.github.com/repos/XomaDev/MIUI-Autostart/issues/24/comments)

### 32. Autostart detection is not fully reliable: there are reports of 'enabled' while the switch is off, and it always reads enabled when MIUI optimization is off.

MIUI-Autostart issue 14 (Redmi Note 9, MIUI 12.5.4, Android 11): getAutoStartState returned ENABLED with autostart off, with a video; issue 15 (POCO, and MIUI 13.0.7): 'always returns true' until MIUI optimization was switched on. The library also maps only value 1 to DISABLED and defaults unknown results to true, while the framework writes 2 for off. The library depends on LSPosed HiddenApiBypass, which Google Play began rejecting in Sept 2025 as affected by Android Runtime updates; it has had no code push since 2025-09-23. The judemanutd AutoStarter library is at 1.1.0 and only opens the screen. Neither is worth adding as a dependency.

- Confidence: medium · Applies to: MIUI 12–14, HyperOS
- Source: https://github.com/XomaDev/MIUI-Autostart/issues/24
- Verifier: **confirmed**. Issue 14 and 15 threads read in full, library source read (0 = ENABLED, 1 = DISABLED, anything else falls back to true by default), last code push 2025-09-23, Play rejection report dated 2025-09-13. In issue 14 the wrong reading happened with MIUI optimization on, so the check can be wrong in both states. (https://api.github.com/repos/XomaDev/MIUI-Autostart/issues/14/comments)

### 33. There is no supported way to read the per-app Battery saver profile; PowerManager.isIgnoringBatteryOptimizations is the best available proxy.

PowerKeeper's table content://com.miui.powerkeeper.configure/userTable requires miui.permission.powerkeeper.HIDDEN_MODE_PROVIDER (signature or privileged), so even adb shell is refused. The same investigation observed that choosing No restrictions adds the app to the DeviceIdle user whitelist, and that an app already exempt in AOSP is auto-promoted to noRestrict the first time its Battery saver page is opened. So: false means definitely not 'No restrictions'; true means probably fine. An opposite claim exists in Nova-Android's comments (the vendor setting does not set the Android flag), so verify on the phone with 'adb shell dumpsys deviceidle whitelist'. An unofficial mod uses reflection on miui.process.ProcessManager.getAppBatterySaverPolicy (0 = no restrictions); untested.

- Confidence: medium · Applies to: HyperOS; older MIUI uncertain
- Source: https://github.com/dingwen07/hyperos-fcm-fix/blob/4cd193f9b2ed4d23f2aa070d94d806dede3810b9/docs/xiaomi-hyperos-gms-fcm-greezer-investigation.md
- Verifier: **uncertain**. The first half holds (the PowerKeeper provider needs a signature/system permission). The proxy is not established: the only source linking 'No restrictions' to the Doze whitelist tested China HyperOS on Android 16, Nova-Android's comments say the vendor setting does not set the Android flag, and xiaomi.eu users describe them as separate settings. Treat isIgnoringBatteryOptimizations as a check of the Android exemption only, show Battery saver as a manual 'confirm you set this' step, and settle it on the phone with 'adb shell dumpsys deviceidle whitelist' before and after choosing No restrictions. The getAppBatterySaverPolicy reflection exists in one mod but that method is absent from the older framework decompile I checked. (https://raw.githubusercontent.com/confeden/Nova-Android/ac46079f7d5f799f3a7672524db808a260ad2431/app/src/main/java/com/example/nova/VendorBackgroundSettingsHelper.kt)

### 34. Real report, MIUI 14: a work location-monitoring app stopped updating in the background even with lock and autostart set; the advice that followed was Background autostart, Show on lock screen, and Battery saver = No restrictions, plus not using the X button.

xiaomi.eu thread (Aug 2023 onward). Suggested fix list: long-press the app icon > App info > enable 'background autostart'; Permissions > 'show on lock screen'; Battery saver > 'No restrictions'. Another user fixed a similar case by fully uninstalling and reinstalling apps that had been copied over during phone migration. A Nov 2024 reply notes that closing apps with the X button terminates foreground services.

- Confidence: medium · Applies to: MIUI 14 (Android 12/13)
- Source: https://xiaomi.eu/community/threads/miui-14-limit-background-activity-even-after-customization.69748/
- Verifier: **confirmed**. Thread read: original post 5 Aug 2023, moderator's fix list the same day, reinstall-after-migration tip 24 Oct 2023, X-button warning 28 Nov 2024. The X-button behaviour matches the decompiled clear-all routine. (https://xiaomi.eu/community/threads/miui-14-limit-background-activity-even-after-customization.69748/)

### 35. Real report, HyperOS 2: Home Assistant companion sensors stopped updating after OS updates and were fixed only by later OS updates.

Community thread covering Xiaomi 13T/14/15 Ultra, POCO X6 Pro, X7 Pro, F7 Pro on HyperOS 2.0.1.0 to 2.0.104.0: battery (and for one user alarm) sensors stopped updating; app-side toggling did not help; users reported it fixed in later builds (for example 2.0.108.0), then broken again on others. The app developer's position: wait for the manufacturer to fix the bug it introduced. Relevant if the owner's phone is an X5 Pro on HyperOS 2: some failures are OS regressions that no setting cures.

- Confidence: medium · Applies to: HyperOS 2
- Source: https://community.home-assistant.io/t/ha-companion-app-doesnt-update-battery-sensor-anymore-hyperos-2/839201
- Verifier: **confirmed**. Thread read; devices and versions match (broken on 2.0.1.0 through 2.0.10.0, fixed on some later builds such as 2.0.108.0, new alarm-sensor problem on 2.0.104.0). The developer attributes it to the OS. No POCO X5 appears in the thread. (https://community.home-assistant.io/t/ha-companion-app-doesnt-update-battery-sensor-anymore-hyperos-2/839201)

### 36. Mainstream mileage apps' own help pages give no Xiaomi-specific steps, which fits the owner's experience of missed trips.

Driversnote's Android auto-tracking article lists only Location 'Allow all the time', precise location and Physical activity. TripLog's MagicTrip article lists only the generic Android battery-optimization exemption and avoiding Battery Saver mode. Neither mentions Autostart, the Xiaomi Battery saver profile or locking. By contrast, tracker vendors that do document Xiaomi (DriveQuant, SportsTrackLive, X-GPS) all require Autostart plus No restrictions, and usually the padlock. MileIQ's article could not be read (HTTP 403).

- Confidence: medium · Applies to: MIUI / HyperOS generally
- Source: https://driversnote.helpscoutdocs.com/article/462-how-to-settings-permissions-for-auto-tracking-android
- Verifier: **confirmed**. Driversnote has a second article the researcher did not cite ('Android Battery Saving Settings', updated 11 Mar 2026): it covers Samsung, Huawei, Pixel, Oppo, Nokia, OnePlus and Vivo but not Xiaomi, and sends other users to dontkillmyapp.com. TripLog's MagicTrip article has only the generic battery-optimization step. MileIQ's article returned 403 for me as well. (https://driversnote.helpscoutdocs.com/article/137-android-battery-saving-settings)

### 37. For the requested R2-D2 sound: custom notification-channel sounds are unreliable on MIUI/HyperOS, so the sound should be played by the service itself.

xiaomi.eu reports: on HyperOS 2.0.6.0 the per-app notification sound picker crashes (NullPointerException in RingtonePreference), and on MIUI 12.5/13 custom notification sounds reverted after reboot, with .ogg files surviving better than .mp3. The MIUI 14 thread above also mentions apps falling back to the standard notification sound. Playing a bundled clip directly from the already-running foreground service avoids all of this. Nothing found suggests MIUI blocks audio played by a foreground service.

- Confidence: low · Applies to: MIUI 12.5–14, HyperOS 1/2
- Source: https://xiaomi.eu/community/threads/some-apps-doesnt-play-sound.65287/
- Verifier: **confirmed**. The recommendation stands, but the cited thread only covers MIUI 12.5/13 (sounds forgotten after reboot, .ogg surviving better than .mp3, March-May 2022). The HyperOS picker crash is in other xiaomi.eu threads, one of them on redwood itself, the POCO X5 Pro: HyperOS 2.0.10.0, 14 Jun 2025, NullPointerException in RingtonePreference, staff reply 'wait for next release'; a similar report exists for HyperOS 1.0.17.0. These are xiaomi.eu builds, so the stock ROM may differ. Android's docs add that the 'top app or foreground service' rule for audio focus only starts with apps targeting Android 15, so on this phone a foreground service can play the clip either way. (https://xiaomi.eu/community/threads/redwood-hyperos-2-0-10-0-bug-cant-set-custom-notification-sound-for-apps.75692/)

## What the verifier said the research missed

- System-started binds are rejected in practice when Autostart is off. Two unrelated projects on HyperOS logged 'AutoStartManagerServiceStubImpl: MIUILOG- Reject service' together with 'Unable to bind notification listener service' when the system tried to bind their listener (one on a Redmi Note 13). One notes that switching Autostart on did not help until the app was fully closed and reopened, because Android had stopped retrying. For MilO: treat Autostart as a hard prerequisite for the companion-device path, and after the owner enables it, re-arm presence observation (stop then start observing) or have him reopen the app. Sources: https://github.com/eyupgok/prism-hub/blob/HEAD/CLAUDE.md and https://github.com/Yeypayeyy/catet-/blob/main/android/app/src/main/java/dev/frlagee/catet/BootReceiver.kt
- The app can find out which MIUI cleaner killed it. The cleaners call killLocked(reason, 13, true), so ApplicationExitInfo reports REASON_OTHER (13) with the MIUI policy name as the description (OneKeyClean, SwipeUpClean, LockScreenClean, AutoPowerKill, AutoIdleKill and so on). Reading getHistoricalProcessExitReasons at startup and logging it turns 'missed trip' into a named cause. Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessCleanerBase.java
- On the Android 12/13 builds this phone may run (MIUI 13/14), a location foreground service started from the background without 'Allow all the time' does not crash; it runs with no location and only a logcat line. The exception is Android 14 behaviour for apps targeting API 34. MilO must check ACCESS_BACKGROUND_LOCATION itself before recording, or it will log empty trips silently. Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- 'Pause app activity if unused' is standard Android hibernation and is detectable: PackageManagerCompat.getUnusedAppRestrictionsStatus() reads it and IntentCompat.createManageUnusedAppRestrictionsIntent() opens the right screen. On Android 12+ hibernation resets runtime permissions and stops background jobs and alarms, which would undo the whole setup after a few months of the app not being opened. This is one checklist item MilO can verify for real. Source: https://developer.android.com/topic/performance/app-hibernation
- A third MIUI 'Other permissions' switch is missing from the findings: 'Permanent notification' (op 10023, OP_SERVICE_FOREGROUND). Another app checks it alongside 10020 and 10021 with the same reflection call. Add it to the owner checklist and to MilO's self-check. Source: https://github.com/makesnosense/voice/blob/HEAD/mobile/android/app/src/main/java/org/voicepopuli/voice/nativepermissions/XiaomiPermissions.kt
- The older (MIUI 13/14-era) broadcast gate has no BOOT_COMPLETED special case: a boot receiver only runs if Autostart is on. The separate op the researcher describes exists only in the HyperOS 1 decompile. Source: https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/BroadcastQueueImpl.java
- Policy 13 (AutoIdleKill) force-stops even on international ROMs. If that fires, MilO is in the stopped state and nothing wakes it until the owner opens it. A daily 'last seen alive' check on app open, or a visible 'not armed' state, is the only defence. Source: https://raw.githubusercontent.com/chrmhoffmann/miui-services/fba6b27f43ee39c1a5d5a62d1dcfb45246eddb27/sources/com/android/server/am/ProcessManagerService.java
- Sound design facts for the R2-D2 cue that nobody checked: audio played with a notification usage is muted when the ringer is on silent or vibrate, while media and alarm usages are not. Media-usage audio also follows the phone's media route, which once the truck's Bluetooth audio profile is up means the truck speakers (or silence if the head unit is on another source). The first chirp at connect time will usually come out of the phone speaker because the audio profile connects after the link. The owner asked for a sound 'when connecting and running', so decide the usage on purpose and make the two cues distinct. Sources: https://community.home-assistant.io/t/tts-via-notification-stream-plays-when-ringer-is-silenced/907776 and https://developer.android.com/media/optimize/audio-focus
- The R2-D2 voice is Lucasfilm/Disney property. For a private sideloaded build that is the owner's call, but a clip lifted from the films should not be committed to a public repository or shipped to others; a synthesized sound-alike avoids the issue. (My own note, not from a source opened in this check.)
- An adb-only lever exists for a phone the developer installs to over USB: KeyMapper's Xiaomi helper runs 'dumpsys deviceidle whitelist +<package>' and 'cmd appops set <package> RUN_IN_BACKGROUND allow' / 'RUN_ANY_IN_BACKGROUND allow'. These set the Android-side exemptions without any UI. They do not set Autostart or the Battery saver profile, and MIUI may require 'USB debugging (Security settings)' for them. Source: https://raw.githubusercontent.com/keymapperorg/KeyMapper/7ac384a9dbd6214eb7f5b39ade93a0402ef22469/base/src/main/java/io/github/sds100/keymapper/base/expertmode/xiaomi/XiaomiOptimizationUseCase.kt
- Recents lock state cannot be confirmed by any documented method. The framework has miui.process.ProcessManager.isLockedApplication(package, userId) with no permission check on the server side in the HyperOS 1 decompile, and the list lives in the system setting 'locked_apps'. Both are untested leads and the class is likely blocked for third-party reflection, so plan for the padlock to be a manual, owner-confirmed step. Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessManagerService.java
- A single-card swipe ignores the padlock in the system code (policy 7 does not check locked apps) and only spares a process that has a running foreground service. So while MilO is idle and waiting for the truck, swiping it away kills it even if locked; only Autostart brings it back. The owner should be told not to swipe MilO away at all. Source: https://raw.githubusercontent.com/raghavt20/miui-services/d9e4df4bed682f316f555858588e2130611ad768/sources/com/android/server/am/ProcessSceneCleaner.java
- Limits of this check: mi.com support pages, two Gizmochina articles, the XDA notification guide, MileIQ's help article and Notebookcheck all returned 403 and were confirmed only through search-result text. The web search quota ran out before I could look for a second source on whether 'No restrictions' sets Android's battery-optimization flag.

## Recommendations

- OWNER CHECKLIST 1 of 12: Read Settings > About phone and note the model name, the MIUI / OS version string and the Android version. This decides which menu names below apply (MIUI 13/14 say 'Autostart'; HyperOS says 'Background autostart').
- OWNER CHECKLIST 2 of 12: Install MilO from Android Studio and open it once. Grant Location as 'Allow all the time' with precise location on, plus Nearby devices, Physical activity and Notifications. An app that has never been opened, or was force-stopped, receives nothing.
- OWNER CHECKLIST 3 of 12: Turn Autostart on. HyperOS: Settings > Apps > Permissions > Background autostart > MilO on. MIUI 13/14: Settings > Apps > Manage apps > MilO > Autostart on (or Security app > Permissions > Autostart). This is the single most important switch.
- OWNER CHECKLIST 4 of 12: Settings > Apps > Manage apps > MilO > Battery saver > choose 'No restrictions'. On the same page switch off 'Pause app activity if unused'.
- OWNER CHECKLIST 5 of 12: Same page > Other permissions: allow 'Show on Lock screen', 'Display pop-up windows while running in the background' (HyperOS may call it 'Open new windows while running in the background'), 'Start in background' and 'Permanent notification' where they appear.
- OWNER CHECKLIST 6 of 12: Lock MilO. Open MilO, open recents, long-press its card and tap the padlock (older MIUI: drag the card down). Also Security app > Boost speed > settings cog > Lock apps > MilO.
- OWNER CHECKLIST 7 of 12: Settings > Battery > Current mode: keep Balanced or Performance. Do not use Battery saver or Ultra battery saver on work days.
- OWNER CHECKLIST 8 of 12: Settings > Battery > gear icon / Additional features: set 'Turn off mobile data when device is locked' to Never and 'Clear cache when device is locked' to Never. If a Sleep mode / Scenarios item exists there, turn it off.
- OWNER CHECKLIST 9 of 12: Habits: never press Force stop on MilO, never swipe its card away, avoid the clear-all X button and the Security app's Cleaner / Boost speed during a shift, and turn off Xiaomi's automatic app backup.
- OWNER CHECKLIST 10 of 12: Prove it. Reboot the phone, unlock it, do not open MilO, then start the truck. A trip must start within about a minute. Repeat once after swiping MilO away while idle, and once after pressing the clear-all X.
- OWNER CHECKLIST 11 of 12: If a test fails, connect to Android Studio and capture logcat filtered on 'MIUILOG- Reject' and 'not permitted to'. Only if Autostart is confirmed on and starts are still rejected, try Developer options > turn off 'Turn on MIUI optimization' (tap 'Reset to default values' to reveal it); expect side effects and re-grant permissions afterwards.
- OWNER CHECKLIST 12 of 12: Repeat steps 3 to 6 after any system update, after uninstalling and reinstalling MilO, or whenever MilO shows its setup warning. Updating in place from Android Studio keeps the settings; uninstalling loses them.
- Build a first-run 'Xiaomi setup' screen that walks the checklist with one button per item, each using the deep links found (AutoStartManagementActivity; HiddenAppsConfigActivity with package_name and package_label; APP_PERM_EDITOR with extra_pkgname), every launch wrapped in try/catch for ActivityNotFoundException and SecurityException with fallback to ACTION_APPLICATION_DETAILS_SETTINGS. Show it only when Build.MANUFACTURER is Xiaomi or the ro.miui.ui.version.name / ro.mi.os.version.name properties are set.
- Show status next to each item but label it honestly: Autostart from checkOpNoThrow(10008) as 'looks on / looks off / unknown'; battery from isIgnoringBatteryOptimizations as 'not done / probably done'. Re-evaluate at every app start and every service start, and raise a notification if either flips to off.
- Add a built-in self-test and a kill diary: on every process start, read ActivityManager.getHistoricalProcessExitReasons and store the reason and description (expect strings such as SwipeUpClean, OneKeyClean, LockScreenClean, AutoPowerKill, AutoIdleKill), and record which trigger woke the app (companion presence, ACL broadcast, activity transition, boot). This turns 'it missed a trip' into a diagnosable fact.
- Do not depend on a single wake path. Companion presence and the ACL receiver are both behind the Autostart gate. Test whether an explicit-component PendingIntent from Play services (activity transition) wakes a dead process with Autostart off; if it does, keep it as an independent fallback trigger. Also keep a periodic WorkManager check, which the international ROM does not cancel.
- Consider, as an owner-approved fallback, a permanent lightweight foreground service that keeps the process alive and registers the Bluetooth receiver in code. The system code spares processes with a foreground service on swipe-away, and a live process is not subject to the autostart gate at all. Cost: a permanent notification and some battery.
- Do not add the XomaDev MIUI-Autostart or judemanutd AutoStarter libraries. The first relies on a hidden-API bypass that Google flags as breaking on newer Android Runtime and has known false positives; the second is old and only opens one screen. The needed code is about thirty lines and should live in one shared, platform-agnostic-free Android module.
- For the R2-D2 sound, play a bundled .ogg clip from the foreground service (SoundPool or MediaPlayer) at connect and at recording start, rather than attaching it to a notification channel, and make it a setting the owner can switch off. Decide the audio stream deliberately, because media audio will route to the truck's speakers once Bluetooth audio is connected.
- Before relying on any decompiled-code finding, confirm it on the owner's phone: run 'adb shell getprop ro.product.mod_device' (expect a value ending in _global), 'adb shell dumpsys deviceidle whitelist' after choosing No restrictions, and the logcat filters above with Autostart off and then on.

## Questions raised for the owner

- Which exact phone is it, and what does Settings > About phone show for the MIUI / OS version (for example OS1.0.26.0.UMPMIXM or V14.0.6.0.TMPMIXM) and the Android version? The letters in that string also tell us the region of the ROM.
- For your current mileage app, are Autostart and Battery saver 'No restrictions' switched on today? If they are off, that very likely explains the missed trips; if they are on and it still fails, we need to plan for a deeper problem.
- Do you regularly use the clear-all X button in recents, the Security app's Cleaner / Boost speed, or Battery saver / Ultra battery saver during the work day? Are you willing to stop, or should MilO be designed to survive them?
- Would you accept a permanent MilO notification (an always-running lightweight service) if testing shows that starting from a fully closed app is not reliable on this phone?
- If the normal settings are not enough, are you willing to turn off 'MIUI optimization' in Developer options, knowing it can change how other apps and permissions behave?
- For the R2-D2 sound: should it come out of the truck's speakers or the phone's speaker, should it stay silent when the phone is on silent or Do Not Disturb, and should it play once at connect and once when recording starts, or differently?
- Do you have an R2-D2 sound file you are entitled to use? The film sounds are Disney / Lucasfilm copyright, so you would need to supply the clip yourself, or we can use a similar droid-style chirp instead.
