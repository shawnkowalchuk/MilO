# Research: Installing from Android Studio on HyperOS, Bluetooth start on Xiaomi, and the trip-start sound

> Gathered 2026-10-03 by a research agent reading live sources. Every finding was then re-checked by an independent verifier; its verdict is shown on each finding.
> Much of this rests on decompiled system code and forum reports, not vendor documentation. Nothing was tested on Shawn's phone. This file is evidence for the ADRs and is not kept current.

## Summary

Research only; no project files were created or modified. All claims below come from pages opened on 2026-10-03. StackOverflow, XDA, Medium, the MacroDroid wiki and jianshu refused the fetch (403), so I used a StackOverflow mirror and vendor guides instead.

**Phone.** Both candidate models stop at Android 14: POCO X5 5G ends on HyperOS 1, POCO X5 Pro 5G on HyperOS 2. Android 15/16 changes (new presence API, audio-focus restriction) therefore do not apply on this phone.

**Part A (install).** Installing from Android Studio needs USB debugging plus two Xiaomi-only switches, "Install via USB" and "USB debugging (Security settings)". Turning them on normally needs a signed-in Xiaomi account, a SIM, and mobile data with Wi-Fi off. Each install shows an on-phone prompt with a short countdown; missing it gives INSTALL_FAILED_USER_RESTRICTED. Leave "Turn on MIUI optimisation" alone unless installs keep failing. I found no evidence that wireless debugging skips the install gate.

**Part B (trip start).** This is the weak spot.
- AOSP 12-14 does bind CompanionDeviceService when a classic Bluetooth device connects, starting the process if needed. I found no public report, for or against, that this works on MIUI/HyperOS, so it is unproven on this phone.
- ACL_CONNECTED/DISCONNECTED may legally be manifest-registered, but an ACL broadcast alone does not permit starting a foreground service from the background. That needs the companion permission or the battery-optimisation exemption.
- On Android 14 a location foreground service started from the background also needs "Allow all the time" location.
- MIUI/HyperOS adds its own gates: Autostart, Battery saver "No restrictions", lock in Recents, "Pause app activity if unused", and the global battery mode. I found no report that tests ACL delivery to a dead app with Autostart on versus off; the nearest evidence says background triggers fail until Autostart is on.
- Xiaomi acknowledged a Bluetooth-dropout bug on POCO X5 Pro 5G HyperOS 2 builds, so trip-end logic needs a grace period.
- Activity Recognition should be a backup trigger only; there is no recent Xiaomi-specific reliability data.

**Part C (the R2-D2 sound).** Play the file directly from the foreground service with MediaPlayer; do not use a notification-channel sound, which MIUI is reported to disable by default.
- Media usage goes only to the car over A2DP and is silent if the stereo is on radio.
- Alarm usage plays on the phone speaker and the Bluetooth media device together in AOSP's default routing, and ignores silent/vibrate. It is the most likely to be heard, but needs checking on the phone.
- A2DP can come up several seconds after ACL_CONNECTED, so a sound at the moment of connection will realistically come from the phone speaker.
- The app should not ship Star Wars audio; the owner picks their own file and the app copies it into private storage.

## Findings

### 1. Both candidate phones top out at Android 14, so API 35+ behaviour never applies on this device.

POCO X5 5G (codename moonstone): launched on MIUI 13 / Android 12, then MIUI 14 / Android 13, then HyperOS 1 / Android 14; latest global build listed is 1.0.26.0.UMPMIXM (Dec 2025). POCO X5 Pro 5G (redwood): launched on MIUI 14 / Android 12, moved to Android 13, HyperOS 1, then HyperOS 2, still on Android 14; latest global build listed is 2.0.17.0.UMSMIXM (Jan 2026). Consequence: the Android 16 ObservingDevicePresenceRequest API and the Android 15 audio-focus restriction are irrelevant here; the app must use the older startObservingDevicePresence(String) and onDeviceAppeared callbacks.

- Confidence: medium · Applies to: Device identification (X5 5G data from https://miuirom.org/phones/poco-x5-5g)
- Source: https://miuirom.org/phones/poco-x5-pro-5g
- Verifier: **confirmed**. Independent firmware tracker agrees: every POCO X5 5G (moonstone) build is HyperOS 1.0.x on Android 14 (latest global OS1.0.26.0.UMPMIXM, dated 2025-12-26 there); POCO X5 Pro 5G (redwood) is on HyperOS 2 / Android 14 (global OS2.0.17.0.UMSMIXM Jan 2026, Indonesia OS2.0.15.0 Apr 2026) and is reported as getting no Android 15. Caveat: if the phone was never updated it may still be on Android 12 or 13 (MIUI 13/14), where the CompanionDeviceService callback signature differs (onDeviceAppeared(String) on API 31-32, AssociationInfo overload from API 33). Check Settings > About phone before building. (https://xmfirmwareupdater.com/hyperos/moonstone/)

### 2. Developer options path: Settings > About phone > tap 'OS version' (HyperOS) or 'MIUI version' (MIUI) seven times; the menu then appears at Settings > Additional settings > Developer options.

Two independent guides agree on the path. One says 7 taps, the other 8-10. USB debugging is the toggle inside Developer options and shows a confirmation warning.

- Confidence: high · Applies to: Part A, MIUI 13/14 and HyperOS 1/2
- Source: https://capgo.app/blog/how-to-enable-developer-options-on-android/
- Verifier: **confirmed**. Path and location confirmed by two other guides. Variation to expect: some HyperOS builds label the entry 'My device' and put the tile one level deeper (My device / About phone > Detailed info and specs > OS version). Tap count is quoted as 7 or 8-10; keep tapping until the 'You are now a developer' toast. (https://miuirom.org/updates/usb-debugging)

### 3. Two Xiaomi-only switches are required in Developer options: 'Install via USB' and 'USB debugging (Security settings)'. Without 'Install via USB', adb install fails with INSTALL_FAILED_USER_RESTRICTED.

The guide describes a Xiaomi security layer that intercepts adb installs on both MIUI and HyperOS. Enabling 'USB debugging (Security settings)' shows three consecutive 5-second warnings that must each be accepted. The same guide gives the success output as 'Performing Streamed Install / Success'.

- Confidence: medium · Applies to: Part A
- Source: https://coldfusion-example.blogspot.com/2026/02/fix-install-via-usb-device-is.html
- Verifier: **confirmed**. The two switches and the error are confirmed independently. The 'three consecutive 5-second warnings' detail is not independently verified; other sources describe 10-second (or longer) countdown screens, so treat the timing as variable. (https://xiaomiui.net/how-to-enable-usb-debugging-on-xiaomi-devices-2505/)

### 4. Enabling 'Install via USB' normally requires a signed-in Xiaomi (Mi) account, a physical SIM, and mobile data with Wi-Fi turned off; on Wi-Fi the toggle fails with 'The device is temporarily restricted'.

A February 2026 guide lists the order: sign in to the Xiaomi account, insert SIM, enable mobile data, turn Wi-Fi off, then enable USB debugging, Install via USB and USB debugging (Security settings). It says the setting persists after the SIM is removed. The author cautions that the exact checks vary by model and MIUI/HyperOS version. A second guide adds: no VPN active, and sign out/in of the Mi account if the toggle loops on a network error.

- Confidence: medium · Applies to: Part A
- Source: https://note.com/gioro/n/nd5603e8418ed?hl=en
- Verifier: **confirmed**. A second guide independently says the switches may need a signed-in Xiaomi account and an inserted SIM, and a Repeato article (seen via search snippet only; page would not load) gives the same Wi-Fi-off / mobile-data-on fix for 'temporarily restricted'. Exact checks vary by model and build, as the researcher noted. (https://capgo.app/blog/how-to-enable-developer-options-on-android/)

### 5. Each adb install shows an on-phone 'Install via USB' confirmation with a short countdown; if it is not accepted in time the install fails with 'INSTALL_FAILED_USER_RESTRICTED: Install canceled by user'.

A StackOverflow answer (16 votes, read via mirror) says the device gives about 7 seconds to confirm. Another answer (8 votes) says that if you once denied it, the per-app switch is under Settings > Permissions > 'Install via USB'. A HyperOS screenshot in the DriveQuant guide confirms an 'Install via USB' entry exists under Settings > Apps > Permissions. A Flutter issue shows the exact error string on a Redmi Note 7 Pro. Practical point: keep the phone unlocked and in hand for the first Run from Android Studio.

- Confidence: medium · Applies to: Part A
- Source: https://qastack.com.de/programming/47239251/install-failed-user-restricted-android-studio-using-redmi-4-device
- Verifier: **confirmed**. Per-install prompt and failure when it is not accepted are confirmed (Mi 9 / MIUI 12 report; the 'remember' checkbox does not stop it reappearing). The per-app 'Install via USB' list under Settings > Apps > Permissions is visible in the DriveQuant HyperOS screenshot, which I read directly. The 7-second figure is not independently verified; StackOverflow could not be opened. (https://forum.cocosengine.org/t/xiaomi-devices-how-to-disable-install-via-usb-dialog-on-adb-install-my-apk/51250)

### 6. 'Turn on MIUI optimisation' does not need changing as a first step; turning it off is a last-resort fix with side effects.

The top StackOverflow answer (340 votes, MIUI 9 era) says to disable MIUI Optimization and restart. A 2026 guide calls that a last resort and warns it may reset permissions and reboot the phone. On HyperOS the toggle is hidden and renamed 'Turn on system optimisation'; it appears after tapping 'Reset to default values' several times. A xiaomi.eu thread (Xiaomi 13 Ultra, HyperOS 1) reports the toggle re-enabling itself. A permission reset would wipe the Autostart, battery and location grants this app depends on.

- Confidence: medium · Applies to: Part A
- Source: https://xiaomi.eu/community/threads/cant-disable-system-miui-optimizations.75093/
- Verifier: **confirmed**. Independent articles confirm that turning it off resets all app permissions to denied. Detail fix: in the xiaomi.eu thread the HyperOS toggle is worded 'Enable System optimizations', appears after tapping 'Reset to default values', and re-enables itself; a xiaomi.eu staff member says disabling it is not supported on their ROM. That thread is about the xiaomi.eu custom ROM on a Xiaomi 13 Ultra, not a stock global POCO build. (https://www.patchworkoftips.com/turn-off-miui-optimizations/10127/)

### 7. Wireless debugging is supported (Android 11+) but I found no evidence that it bypasses Xiaomi's 'Install via USB' gate or the on-phone prompt.

Android's documentation covers pairing by QR code or pairing code from Android Studio on the same Wi-Fi network and mentions no difference in install behaviour. One Shizuku guide claims wireless debugging has no Mi-account requirement, but that is about enabling debugging, not about adb install. The Xiaomi install check is described as sitting between adb and the package manager, which suggests it applies regardless of transport. Treat wireless debugging as a convenience, not a workaround.

- Confidence: low · Applies to: Part A (Shizuku claim from https://shizukudownload.com/guides/xiaomi/)
- Source: https://developer.android.com/tools/adb
- Verifier: **confirmed**. I also found no report of a bypass. Supporting evidence that Xiaomi's adb gates are transport-independent: Shizuku's official setup guide says that on MIUI wireless debugging still needs 'USB debugging (Security options)' enabled in Developer options. Whether the per-install prompt appears over Wi-Fi is still untested; plan as if it does. (https://shizuku.rikka.app/guide/setup/)

### 8. In AOSP 12-14, companion device presence works for classic Bluetooth: the system binds CompanionDeviceService when the associated device connects, starting the app process if needed.

The Android 12 and 14 Javadoc for startObservingDevicePresence says that for Bluetooth classic devices it is triggered when the device connects/disconnects, and requires REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE. The Android 12 server code uses a Bluetooth connection callback and binds the service on demand. The service Javadoc says the binding raises the process priority. In Android 12 the service is kept bound for up to 10 minutes after disappearance (DEVICE_DISAPPEARED_UNBIND_TIMEOUT_MS). This is stock Android behaviour only.

- Confidence: high · Applies to: Part B, AOSP 12-14 (Javadoc: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/java/android/companion/CompanionDeviceManager.java)
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/companion/java/com/android/server/companion/CompanionDeviceManagerService.java
- Verifier: **confirmed**. Verified in the Android 14 Javadoc ('For Bluetooth classic devices this is triggered when the device connects/disconnects', permission REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE, DeviceNotAssociatedException if not associated) and the Android 12 server (DEVICE_DISAPPEARED_UNBIND_TIMEOUT_MS = 10 min, on-demand bind, REQUEST_COMPANION_RUN_IN_BACKGROUND adds the app to the power allowlist). Android's Bluetooth background guide separately recommends CompanionDeviceService plus the REQUEST_COMPANION_* permissions for background starts. Stock Android only. (https://developer.android.com/develop/connectivity/bluetooth/ble/background)

### 9. I found no public report, positive or negative, on CompanionDeviceService presence callbacks for classic Bluetooth on MIUI or HyperOS; it must be treated as unproven on this phone.

Several searches of GitHub, Codeberg and forums returned nothing specific. Nearest signals: Gadgetbridge users on HyperOS 1.2 to 3.0 (Android 14-16) reported that bands paired as companion devices did not auto-reconnect; the root cause identified there was socket timing, not the presence API. Separately, a Xiaomi 14 Ultra on a xiaomi.eu ROM needed the system package com.android.companiondevicemanager reinstalled before watch pairing stopped crashing, which shows association depends on that system app being intact.

- Confidence: low · Applies to: Part B (second case: https://xiaomi.eu/community/threads/fixed-the-problem.73343/)
- Source: https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/4598
- Verifier: **confirmed**. My own searches also found nothing specific. Both cited neighbours check out (Gadgetbridge issue: HyperOS 1.2-3.0, root cause socket timing; xiaomi.eu: Xiaomi 14 Ultra on xiaomi.eu ROM fixed by reinstalling com.android.companiondevicemanager). Open risk the researcher did not state: Xiaomi's developer FAQ says Autostart gates starting an app via system broadcasts; whether it also gates the system binding a CompanionDeviceService in a dead app is unknown and must be tested with Autostart on and off. (https://xiaomi.eu/community/threads/fixed-the-problem.73343/)

### 10. ACTION_ACL_CONNECTED and ACTION_ACL_DISCONNECTED are on Android's implicit-broadcast exception list, so a manifest receiver for them is allowed for apps targeting API 26+.

The list also includes BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED and BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED, which can also be manifest-registered. The ACL broadcast requires the BLUETOOTH_CONNECT runtime permission on Android 12+.

- Confidence: high · Applies to: Part B
- Source: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions
- Verifier: **confirmed**. Confirmed on the exceptions page, and in AOSP 14 source the ACL broadcast is sent with receiver permission BLUETOOTH_CONNECT and FLAG_RECEIVER_INCLUDE_BACKGROUND, so manifest receivers are targeted. (https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/btservice/RemoteDevices.java)

### 11. Receiving an ACL broadcast does not by itself permit starting a foreground service from the background on Android 12+; a separate exemption is needed.

The documented exemptions include: declaring REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND (preferred) or REQUEST_COMPANION_RUN_IN_BACKGROUND with a companion association; the user turning off battery optimisation for the app; activity-recognition transition and geofence events; BOOT_COMPLETED. Bluetooth ACL broadcasts are not on the list. So the manifest-receiver path only works if the app also holds a companion association with that permission, or the battery-optimisation exemption.

- Confidence: high · Applies to: Part B, Android 12-14
- Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- Verifier: **refuted**. Wrong for stock Android 12-14. The Bluetooth stack sends ACL_CONNECTED / ACL_DISCONNECTED (and A2DP broadcasts) with BroadcastOptions.setTemporaryAppAllowlist(20 000 ms default, TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED, REASON_BLUETOOTH_BROADCAST). PowerExemptionManager documents that type as 'plus allow foreground service start from background', and the broadcast queue applies it to manifest receivers. So the receiver may call startForegroundService for about 20 seconds with no companion association and no battery-optimisation exemption. Caveats: the current public exemption list does not mention it (so treat it as implementation behaviour that MIUI could alter, and keep the companion path as a second route); the service must be started straight from onReceive; and on Android 14 a location-type service still needs ACCESS_BACKGROUND_LOCATION. (https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/Utils.java)

### 12. On Android 14 a 'location' foreground service cannot be created while the app is in the background unless ACCESS_BACKGROUND_LOCATION ('Allow all the time') is granted; otherwise startForeground throws SecurityException.

The service also needs the FOREGROUND_SERVICE_LOCATION manifest permission, location services switched on, and fine or coarse location granted. Because every trip start here happens with no visible activity, 'Allow all the time' is mandatory, not optional.

- Confidence: high · Applies to: Part B, Android 14 (HyperOS)
- Source: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Verifier: **confirmed**. Confirmed on a second Android page: while-in-use permissions are not held in the background, so creating a location service there throws SecurityException unless ACCESS_BACKGROUND_LOCATION is granted. This applies even when a background-start exemption exists. (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

### 13. 'Allow all the time' cannot be granted from the permission dialog on Android 11+; the user is sent to a settings page and must pick it there.

Android requires asking for foreground location first, then background separately; the label text comes from getBackgroundPermissionOptionLabel(). On MIUI/HyperOS a tracking-app vendor gives the path as Settings > Apps > [app] > Permissions > Location > Allow all the time. If only approximate location is granted in the foreground, background is approximate too. I found no source showing a MIUI-specific variation of this dialog.

- Confidence: medium · Applies to: Part B (MIUI path from https://docs.sportstracklive.com/android-battery-saving/xiaomi)
- Source: https://developer.android.com/develop/sensors-and-location/location/permissions/background
- Verifier: **confirmed**. Confirmed in Android's documentation (dialog has no 'Allow all the time' on API 30+; label from getBackgroundPermissionOptionLabel(); approximate-only carries into background). The Xiaomi path Settings > Apps > [app] > Permissions > Location > Allow all the time matches the vendor page I opened. No MIUI-specific variation found by me either. (https://developer.android.com/develop/sensors-and-location/location/permissions/background)

### 14. Apps in Android's 'stopped state' receive no broadcasts; MIUI/HyperOS is reported to tear down a foreground service along with its task when the app is swiped from Recents.

Android: an app is stopped when installed but never launched, or when the user force-stops it; the system excludes stopped apps from all broadcasts. A 2026 pull request, verified on a Xiaomi phone running HyperOS 3, states that swiping the app out of Recents destroys the foreground service with its task. Its fix was android:stopWithTask="false", handling onTaskRemoved, and scheduling a restart. That is one project on a newer HyperOS than this phone, so confirm on the POCO.

- Confidence: medium · Applies to: Part B (stopped state: https://developer.android.com/about/versions/android-3.1#launchcontrols)
- Source: https://github.com/Osasuwu/like-current-song/pull/86
- Verifier: **confirmed**. Both parts confirmed (PR #86 is dated 19 Sep 2026, verified on HyperOS 3). Stronger than stated: Chinese developer reports say the MIUI Recents swipe is a real Force Stop, not just a process kill. A force-stop puts the app in the stopped state, so manifest broadcasts stop until the user opens the app again, and in stock Android a force-stop also clears the app's alarms, so the AlarmManager restart in that PR is not a dependable fix. Locking the app in Recents is the practical defence. (https://www.v2ex.com/t/670173)

### 15. I found no test report of ACL_CONNECTED delivery to a dead app on MIUI/HyperOS with Autostart on versus off; the nearest evidence says background triggers fail until Autostart is enabled.

A January 2026 note (Redmi Note 15 Pro 5G, HyperOS 2.0.204.0) reports the Automate app's triggers stopped working after force-close or restart until 'Autostart in background' was enabled at Settings > Apps > Autostart in background. A tracking-app vendor states that with Autostart off, MIUI/HyperOS kills the app shortly after screen-off. An older tracking-SDK issue (Redmi 5 Plus, Android 8.1) reports background callbacks not firing after the user terminated the app, while Samsung worked. Expect Autostart off to block the wake-up, and Autostart on to allow it, but only an on-device test will settle it.

- Confidence: low · Applies to: Part B (also https://github.com/transistorsoft/background-geolocation-lt/issues/68)
- Source: https://note.com/moba_gadgets_x/n/n15c0758c1f2f?hl=en
- Verifier: **confirmed**. No direct test found by me either, but there is a first-party source the researcher missed: Xiaomi's developer adaptation FAQ states that autostart is user-controlled, off by default, and 'includes boot start and start via receiving system broadcasts' (one app launching another is not restricted). That directly supports 'Autostart off blocks the ACL wake-up'. Two corrections: the note.com post is about Automate losing notification access after force-close or reboot, not triggers in general; and users report the Global ROM is laxer than the China ROM (FCM delivered without autostart), so the on-device test is still required. (https://dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/)

### 16. HyperOS menu paths for keeping a trip-detection app alive, from a telematics vendor's screenshots: Background autostart, Battery saver 'No restrictions', 'Pause app activity if unused' off, and battery mode Balanced or Performance.

Screenshots dated April 2024 show: (1) Settings > Apps > Manage apps > [app] > turn off 'Pause app activity if unused'. (2) Same screen > Battery saver > 'No restrictions'; the other options are 'Battery saver (recommended)', 'Restrict background apps: Close apps after 10 minutes of background activity', and 'Restrict background activity'. (3) Settings > Apps > Permissions > Background autostart > enable the app. (4) Settings > Battery > Current mode > Balanced or Performance, not Battery saver or Ultra battery saver.

- Confidence: high · Applies to: Part B, HyperOS 1
- Source: https://www.drivequant.com/hubfs/Tuto%20Smartphones%20-%20EN/Xiaomi/Xiaomi%20-%20HyperOS%20-%20EN.pdf
- Verifier: **confirmed**. I read the PDF screenshots directly and all four paths and option labels match, including 'Restrict background apps: Close apps after 10 minutes of background activity'. A second vendor gives the same Settings > Apps > Manage apps > [app] > Battery saver > No restrictions path. Extra detail: the Manage apps screen also has a 'Background autostart' shortcut at the top. The screenshots show 'Wed, 17 Apr' with no year. (https://docs.sportstracklive.com/android-battery-saving/xiaomi)

### 17. MIUI 13/14 equivalents and extra switches: Autostart, lock in Recents, and the 'Other permissions' page.

dontkillmyapp rates Xiaomi at its most aggressive level and lists: MIUI 14 Settings > Apps > [app] > App permissions > Background autostart; older builds Security app > Permissions > Autostart; Security > Battery > App battery saver > [app] > No restriction; lock the app by dragging its Recents card down or long-pressing for the padlock. Other vendors add Settings > Apps > Manage apps > [app] > Other permissions > 'Start in background', 'Show on Lock screen' and 'Display pop-up windows while running in the background'. No source measured multi-hour screen-off survival of a location service on a POCO X5 specifically.

- Confidence: medium · Applies to: Part B, MIUI 13/14 (extra switches: https://x-gps.app/en/help/tracker/xiaomi/settings/)
- Source: https://dontkillmyapp.com/xiaomi
- Verifier: **confirmed**. dontkillmyapp paths match as quoted. The x-gps page words its path 'Settings > Installed apps > [app] > Other permissions' with 'Show on Lock screen' and 'Start in background'; a third vendor lists 'Display pop-up windows' and 'Open new windows while running in background'. Labels move between versions, so the in-app checklist should tell the user to search Settings for 'autostart'. (https://tiktask.ai/blog/keep-automation-alive-xiaomi-infinix-tecno-checklist)

### 18. The standard battery-optimisation exemption is a documented way to allow background foreground-service starts, but its relationship to MIUI's own 'Battery saver: No restrictions' is not confirmed, and some users report the setting reverting.

Android lists 'user disables battery optimizations for the app' as an exemption. A xiaomi.eu thread (HyperOS 1 and 2 on the EU ROM) reports battery optimisation being forced back on after reboots or updates for at least one app, attributed there to a blacklist in the China ROM base. I found no source describing how MIUI presents the standard exemption dialog. The app should re-check the exemption on every launch rather than assume it sticks.

- Confidence: low · Applies to: Part B
- Source: https://xiaomi.eu/community/threads/disable-battery-optimization-for-an-app-permanently.74932/
- Verifier: **confirmed**. Android's exemption list does include it. The revert report is weaker than it sounds: it concerns the xiaomi.eu ROM (China base) and mainly AdGuard, attributed to a China-ROM blacklist, so it says little about a stock global POCO build. Also, because Bluetooth broadcasts already carry a foreground-service start allowance on stock Android (see the refuted finding), this exemption is a backup rather than a prerequisite. (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)

### 19. There is no supported way to read the Autostart state; the one known library uses hidden MIUI APIs and does not mention HyperOS.

XomaDev/MIUI-Autostart exposes Autostart.getSafeState(context). Its README lists testing on MIUI 10, 11, 12 and 14 (13 reportedly works) and says nothing about HyperOS; it warns Play may reject apps using it, which does not matter for a sideloaded app. Under this project's dependency rules it would need a maintenance check before being added. The alternative is a manual checklist screen with a deep link to the setting.

- Confidence: medium · Applies to: Part B
- Source: https://github.com/XomaDev/MIUI-Autostart
- Verifier: **confirmed**. Confirmed (README: MIUI 10, 11, 12, 14 tested, 13 untested, latest v1.3, no HyperOS; dontkillmyapp points to the same library). Missing from the finding: Xiaomi's own developer FAQ documents the deep-link actions, 'miui.intent.action.OP_AUTO_START' for the autostart list and 'miui.intent.action.APP_PERM_EDITOR' with extra 'extra_pkgname' for the per-app permission editor. The widely copied component com.miui.securitycenter / com.miui.permcenter.autostart.AutoStartManagementActivity is community-sourced and I found no confirmation it resolves on HyperOS, so resolve the intent first and fall back to the standard app-details screen. (https://dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/)

### 20. Activity Recognition transitions (IN_VEHICLE) are delivered by PendingIntent with device-dependent latency; I found no recent evidence on their reliability on MIUI/HyperOS.

Google's documentation says only that the latency of event detection might vary by device. Transition events are themselves an exemption for starting a foreground service from the background. The only Xiaomi-specific report found is old (Redmi 6A, Android 8.1): the activity stayed 'still' while driving and tracking stopped; there was no maintainer diagnosis. A tracking-SDK FAQ says that without motion detection, 200-1000 m of movement is needed before tracking starts. Treat IN_VEHICLE as a backup trigger, not the primary one.

- Confidence: low · Applies to: Part B (Xiaomi report: https://github.com/transistorsoft/flutter_background_geolocation/issues/423)
- Source: https://developer.android.com/develop/sensors-and-location/location/transitions
- Verifier: **confirmed**. Documentation wording confirmed; I found no recent Xiaomi reliability data either. Omission: on Android 10+ the app must also hold the runtime permission android.permission.ACTIVITY_RECOGNITION or Play services returns no results; the transitions page only shows the older com.google.android.gms permission. (https://developer.android.com/about/versions/10/privacy/changes)

### 21. Xiaomi acknowledged a Bluetooth disconnection bug on POCO X5 Pro 5G HyperOS 2 builds OS2.0.1.0.UMSRUXM, OS2.0.3.0.UMSMIXM and OS2.0.3.0.UMSEUXM.

Reported May 2025 from Xiaomi's weekly bug report: ongoing Bluetooth disconnections during normal use, under analysis, no fix build named. Later builds exist (global 2.0.17.0, January 2026) but I found no statement that they fix it. For this app, a brief dropout would look like ACL_DISCONNECTED followed by ACL_CONNECTED mid-trip, so ending a trip instantly on disconnect would split trips.

- Confidence: medium · Applies to: Part B, POCO X5 Pro 5G only
- Source: https://ximitime.com/the-next-hyperos-2-updates-will-fix-bluetooth-connectivity-issues-48114/
- Verifier: **confirmed**. A second outlet lists the same three builds and says Xiaomi acknowledged the fault and was preparing an OTA fix. I found no statement naming the build that fixed it. Applies to the X5 Pro 5G only. (https://tecnobits.com/en/Practical-guide-on-how-to-fix-Bluetooth-problems-on-Xiaomi-phones/)

### 22. With Android Auto, Bluetooth still connects to the car (for calls and the wireless handshake) but media audio travels over USB or Wi-Fi, not A2DP. Xiaomi-specific Android Auto failures are reported mostly on China-ROM phones.

So the ACL connection trigger should still fire in an Android Auto truck. A xiaomi.eu thread reports Android Auto not starting or freezing after HyperOS on several models (Redmi K60 Pro, Xiaomi 12, Poco X6 Pro); one user traced a MIUI 14 problem to App lock, another fixed it by clearing the app's data. None of the reports is about a POCO X5.

- Confidence: medium · Applies to: Part B and C (Xiaomi reports: https://xiaomi.eu/community/threads/android-auto-on-hyperos.70508/)
- Source: https://9to5google.com/2022/05/28/android-auto-bluetooth/
- Verifier: **confirmed**. Independent articles confirm wired Android Auto keeps Bluetooth Hands-Free Profile for calls while media goes over the cable. The xiaomi.eu thread matches (Redmi K60 Pro, Xiaomi 12, Xiaomi 14 / 14 Pro China, Poco X6 Pro; App lock and clear-data fixes; no POCO X5 report). (https://www.howtogeek.com/your-wired-android-auto-connection-secretly-still-uses-bluetooth-for-one-important-thing/)

### 23. A 2-second clip fits SoundPool's limits, but MediaPlayer is the simpler fit for a one-shot sound from a cold-started service.

SoundPool pre-decodes to PCM with a 1 MB cap per sound (about 5.6 s at 44.1 kHz stereo; longer clips are truncated), gives low-latency playback, and loads from a resource, path or file descriptor; there is no Uri overload. MediaPlayer additionally offers setPreferredDevice (API 28+), which lets the app force a specific output such as the phone speaker. Both accept AudioAttributes.

- Confidence: high · Applies to: Part C (setPreferredDevice: https://learn.microsoft.com/en-us/dotnet/api/android.media.mediaplayer.setpreferreddevice?view=net-android-34.0)
- Source: https://learn.microsoft.com/en-us/dotnet/api/android.media.soundpool?view=net-android-34.0
- Verifier: **confirmed**. AOSP SoundPool source confirms the 1 MB per-sound limit (about 5.6 s at 44.1 kHz stereo, truncated beyond) and exactly four load() overloads with no Uri version. setPreferredDevice is API 28. Caveat: the platform notes the preferred device is not guaranteed to be the one actually used, and one driving app documents forced phone-speaker routing as glitchy on some phones. (https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/media/java/android/media/SoundPool.java)

### 24. Each AudioAttributes usage maps to a volume stream: MEDIA, GAME, ASSISTANT and NAVIGATION_GUIDANCE to music; ALARM to alarm; NOTIFICATION and NOTIFICATION_EVENT to notification; ASSISTANCE_SONIFICATION to system.

This is from AOSP 14's AudioAttributes source. It decides which volume slider controls the sound and which mute rules apply (next two findings).

- Confidence: high · Applies to: Part C, AOSP 14
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/media/java/android/media/AudioAttributes.java
- Verifier: **confirmed**. Checked against the GitHub mirror of the Android 14 source; mapping matches. (https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/media/java/android/media/AudioAttributes.java)

### 25. Silent and vibrate modes mute only the ring, notification and system streams on phones; media and alarm streams keep playing.

AOSP's default 'ringer mode affected streams' on voice-capable devices are STREAM_RING, STREAM_NOTIFICATION, STREAM_SYSTEM and STREAM_SYSTEM_ENFORCED; STREAM_MUSIC is added only on tablets. So a sound played with notification or sonification usage is lost when the phone is on silent or vibrate; media and alarm usage are not. MIUI could change this default; verify on the phone.

- Confidence: medium · Applies to: Part C
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/packages/SettingsProvider/src/com/android/providers/settings/DatabaseHelper.java
- Verifier: **confirmed**. Mirror copy of DatabaseHelper shows the same default (ring, notification, system, system-enforced; music added only when the device is not voice-capable). Stock Android only; not verified on MIUI. (https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/packages/SettingsProvider/src/com/android/providers/settings/DatabaseHelper.java)

### 26. Do Not Disturb mutes notification usages whenever it is on; media and alarm usages are muted only if the user's DND rules disallow them or in Total silence.

From AOSP 14 ZenModeHelper: notifications are muted when zen is on; alarms when priority mode disallows alarms; media (which includes navigation guidance and assistant) when priority mode disallows media; system sounds in alarms-only mode or when priority mode disallows system; everything in total silence.

- Confidence: high · Applies to: Part C, AOSP 14
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/services/core/java/com/android/server/notification/ZenModeHelper.java
- Verifier: **confirmed**. Mirror copy of ZenModeHelper.applyRestrictions matches line for line (muteNotifications = zenOn; muteAlarms and muteMedia only in priority mode when disallowed; muteEverything in total silence). (https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android14-release/services/core/java/com/android/server/notification/ZenModeHelper.java)

### 27. In AOSP's default routing, media usage goes to the Bluetooth A2DP device when one is connected, while alarm-type sounds play on the phone speaker and the media device together.

Engine.cpp: the media strategy picks the last-connected removable media device (A2DP) unless Bluetooth media is force-disabled. The sonification strategy (alarms, ringtones) selects the speaker and carries the comment that its second device is the same as the device used by the media strategy. Notifications use the 'respectful' strategy, which follows media routing when not in a call. A2DP is not used during a call. Xiaomi's vendor audio policy may differ.

- Confidence: medium · Applies to: Part C, AOSP 14 default engine
- Source: https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android14-release/services/audiopolicy/enginedefault/src/Engine.cpp
- Verifier: **confirmed**. Engine.cpp matches, and a driving app's documentation independently describes alarm-style audio as playing through all available devices at once (Bluetooth plus phone speaker) on most phones. Not verified on Xiaomi's audio policy. (https://book.highwayradar.com/features/sounds/)

### 28. Audio sent over A2DP is only heard if the car stereo's source is set to Bluetooth; on radio, a media-usage sound will be silent.

Google Maps Help instructs users to set the car's audio source to Bluetooth to hear guidance over the car speakers, and to turn off 'Play voice over Bluetooth' to hear it from the phone speaker instead. A sound that must be heard regardless of stereo source therefore has to include the phone speaker.

- Confidence: medium · Applies to: Part C
- Source: https://support.google.com/maps/answer/11523237?hl=en&co=GENIE.Platform%3DAndroid
- Verifier: **confirmed**. Google Maps Help says exactly this (set the car's source to Bluetooth; 'Play voice over Bluetooth' toggle). No contrary source found. (https://support.google.com/maps/answer/11523237?hl=en&co=GENIE.Platform%3DAndroid)

### 29. While Android Auto is projecting, ordinary app audio is sent to the car automatically; how non-media usages (alarm, notification) are routed during projection is not documented.

Android's car media guidance says an app needs no special logic: it plays audio as it would on phone speakers and Android Auto sends it to the car's system. That covers media-type playback. I found nothing authoritative for alarm or notification usage during projection, so that case needs an on-device test if the truck uses Android Auto.

- Confidence: low · Applies to: Part C
- Source: https://developer.android.com/training/cars/media/enable-playback
- Verifier: **confirmed**. Android's car media page confirms the media statement and says nothing on alarm or notification usages. One field data point: a driving app reports media-stream audio goes to the car under Android Auto, and that forcing the phone speaker can jump between phone and car on some devices. (https://book.highwayradar.com/features/sounds/)

### 30. Requesting AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK makes the system lower other apps' volume automatically for the duration of the sound.

Automatic ducking is system-handled on Android 8+ with no callback needed; it does not duck speech content. Focus should be abandoned when the clip finishes. Android 15 restricts focus requests to the top app or an app running a foreground service; MilO would be in a foreground service, and the phone is Android 14 or older anyway.

- Confidence: high · Applies to: Part C
- Source: https://developer.android.com/media/optimize/audio-focus
- Verifier: **confirmed**. Confirmed, including the speech-content exception and the Android 15 rule (top app or running a foreground service; no service type is specified). (https://developer.android.com/about/versions/15/behavior-changes-15)

### 31. ACL_CONNECTED signals only the low-level link; the A2DP profile can come up seconds later, so a sound played at that instant will usually come from the phone speaker.

The ACL broadcast is documented as a low-level connection. AOSP 14's Bluetooth PhonePolicy waits 6000 ms after one profile connects before it tries the remaining profiles on that device. A2DP readiness is signalled by BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED. I could not open a source on head units clipping the first moments of a new A2DP stream; that remains unverified.

- Confidence: medium · Applies to: Part C (ACL doc: https://learn.microsoft.com/en-us/dotnet/api/android.bluetooth.bluetoothdevice.actionaclconnected?view=net-android-34.0)
- Source: https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/btservice/PhonePolicy.java
- Verifier: **confirmed**. PhonePolicy shows the 6000 ms connect-other-profiles delay. The part the researcher could not verify is supported by long-running user reports (search snippets from XDA and MacRumors threads; the pages themselves blocked fetching): the first one to two seconds of a newly started A2DP stream are often lost on car stereos, so a 2-second clip sent to A2DP can be mostly swallowed. (https://xdaforums.com/t/bluetooth-a2dp-problems-delayed-cut-off-notifications-during-navigation.1492483/)

### 32. A user-picked audio file can be kept via ACTION_OPEN_DOCUMENT plus takePersistableUriPermission, but access is lost if the file is moved or deleted.

Without taking the persistable grant, access ends at device restart. With it, access survives restarts, but the documentation states the app does not retain access if the document is moved or deleted. Copying the picked file into app-private storage at pick time removes that dependency for a service that starts with no UI.

- Confidence: high · Applies to: Part C
- Source: https://developer.android.com/training/data-storage/shared/documents-files
- Verifier: **confirmed**. Matches Android's documentation (grant lasts until restart unless persisted; access lost on move or delete). No independent second source needed; this is the primary reference. (https://developer.android.com/training/data-storage/shared/documents-files)

### 33. MIUI is reported to disable sound on app-created notification channels by default, and one HyperOS 2 build crashes when the user tries to change a channel's sound.

Threema's FAQ (MIUI 10 and 11): when an app creates a channel only vibration is enabled; sound, heads-up and light are off by default and the app cannot change them. A xiaomi.eu report (Xiaomi 11 Lite 5G NE 'Lisa', HyperOS 2.0.6.0): Settings crashes when changing the default sound for an app's notification. Both are for other versions than this phone's, but they are reason enough not to rely on a channel sound for the trip-start confirmation.

- Confidence: medium · Applies to: Part C (HyperOS 2 bug: https://xiaomi.eu/community/threads/lisa-hyperos-2-0-6-0-bug-cant-set-custom-notification-sound-for-apps.75662/)
- Source: https://threema.com/en/faq/notification-channels-xiaomi
- Verifier: **confirmed**. Threema FAQ confirmed (MIUI 10: only vibration on; MIUI 11: sound, floating, lock-screen and more off by default; the app cannot change them). Additional independent report on MIUI 13: per-app notification sounds replaced by the system default sound. Conclusion stands: do not rely on a channel sound; play the clip directly. (https://xiaomi.eu/community/threads/miui-overrides-notification-sound-of-apps-to-the-default-notification-sound.65104/)

## What the verifier said the research missed

- Stock Android 12-14 already lets a manifest receiver start a foreground service from ACL_CONNECTED: the Bluetooth stack attaches a roughly 20-second 'foreground service start allowed' temp allowlist (REASON_BLUETOOTH_BROADCAST) to its broadcasts. The receiver must call startForegroundService directly in onReceive. It is absent from the current public exemption list, so keep the companion-device route as a second path, but neither it nor the battery exemption is a prerequisite. Source: https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/android14-release/android/app/src/com/android/bluetooth/Utils.java
- Xiaomi's own developer FAQ states what Autostart gates: boot start and starting an app via received system broadcasts, off by default (app-to-app launches are not restricted). It also gives the official deep-link actions: 'miui.intent.action.OP_AUTO_START' (autostart list) and 'miui.intent.action.APP_PERM_EDITOR' with extra 'extra_pkgname' (per-app permission editor). The researcher supplied no component names; the community one (com.miui.securitycenter / com.miui.permcenter.autostart.AutoStartManagementActivity) is unverified on HyperOS, so resolve before launching and fall back to the standard app-details screen. Source: https://dev.mi.com/docs/appsmarket/technical_docs/adaptation_FAQ/
- On MIUI a Recents swipe is reported to be a true force-stop. That puts the app in Android's stopped state: no manifest broadcasts until the user opens the app again, and stock Android also clears its alarms, so a restart alarm from onTaskRemoved cannot be relied on. The same stopped state applies to a freshly sideloaded app until it is opened once. The setup checklist needs 'lock the app in Recents' and 'open the app after every install'. Sources: https://www.v2ex.com/t/670173 and https://developer.android.com/about/versions/android-3.1#launchcontrols
- Car stereos commonly drop the first one to two seconds of a newly started A2DP stream, which is most of a 2-second clip. For the requested R2-D2-style sound, either play on the phone speaker (MediaPlayer.setPreferredDevice with the built-in speaker, API 28+, not guaranteed) or use alarm usage (phone speaker plus Bluetooth together, follows the alarm volume slider, allowed by default Do Not Disturb), or wait for the A2DP connected broadcast and add lead-in silence. Sources: https://book.highwayradar.com/features/sounds/ and https://xdaforums.com/t/bluetooth-a2dp-problems-delayed-cut-off-notifications-during-navigation.1492483/ (search snippet)
- The user asked for the sound 'when connecting and running', which is two moments: at Bluetooth connect, A2DP is not up so the sound lands on the phone speaker; a few seconds later, when tracking is running, a media-usage sound may go to the car instead. Pick one explicit route for both so they behave the same. Also, the real R2-D2 effects are copyrighted Lucasfilm recordings: do not commit one to the project; ship an original default chirp and let the user pick their own file, copied into app-private storage at pick time.
- Activity Recognition needs the runtime permission android.permission.ACTIVITY_RECOGNITION on Android 10+; without it Play services delivers no transitions. Google's transitions page shows only the older gms manifest permission. Source: https://developer.android.com/about/versions/10/privacy/changes
- On Android 13+ the app must request POST_NOTIFICATIONS; if denied the foreground service still runs but its notification is hidden from the drawer and shows only in Task Manager, so the driver gets no visible trip indicator. Source: https://developer.android.com/develop/ui/views/notifications/notification-permission
- Xiaomi's adb security gates are not USB-only: Shizuku's official guide says MIUI needs 'USB debugging (Security options)' even for wireless debugging. Do the Mi account / SIM / mobile-data setup once regardless of transport. Source: https://shizuku.rikka.app/guide/setup/
- Autostart strictness is reported to differ between China and Global ROMs (Global users report push delivery without Autostart). The POCO X5 runs a Global-family ROM, so the with/without-Autostart test for ACL_CONNECTED and for CompanionDeviceService binding has to be run on the actual phone; no source settles it. Source: https://www.v2ex.com/t/670173

## Recommendations

- Install setup, in this order: sign in to a Xiaomi account, insert a SIM, turn mobile data on and Wi-Fi off, disable any VPN; then in Settings > Additional settings > Developer options enable USB debugging, Install via USB, and USB debugging (Security settings). Keep the phone unlocked and in hand for the first Run so the install prompt can be accepted within its countdown.
- Leave 'Turn on MIUI optimisation' on. Only try turning it off if installs keep failing after the three switches are set, and if you do, re-check every MilO permission afterwards because it may reset them.
- Use USB for the first install. Wireless debugging is fine afterwards for convenience, but do not plan on it avoiding the Xiaomi account/SIM step or the install prompt.
- Register three independent trip-start triggers and make the start idempotent: (1) companion device presence with REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE and REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND, using the pre-Android-16 startObservingDevicePresence(String) API; (2) the manifest ACL_CONNECTED/ACL_DISCONNECTED receiver; (3) Activity Recognition IN_VEHICLE as a slow backup.
- Make 'Allow all the time' location, the battery-optimisation exemption, BLUETOOTH_CONNECT, notification and activity-recognition permissions hard requirements in onboarding. On Android 14 the location service cannot start from the background without 'Allow all the time'.
- Build a 'Trip start health' screen that re-checks on every launch: background location, battery-optimisation exemption, companion association still present, notification permission. Add a manual MIUI/HyperOS checklist the app cannot verify itself: Background autostart on, Battery saver 'No restrictions', 'Pause app activity if unused' off, app locked in Recents, battery mode Balanced or Performance.
- Set android:stopWithTask="false" on the tracking service and handle onTaskRemoved, since HyperOS is reported to kill the service when the app is swiped from Recents.
- Do not end a trip immediately on ACL_DISCONNECTED. Use a grace period (for example 2-3 minutes, cancelled by a reconnect) because of the acknowledged POCO X5 Pro Bluetooth dropout bug.
- Log every trigger with a timestamp to Room (which trigger fired, whether the service start succeeded, any exception). This is the only way to find out which path actually works on this phone.
- Before trusting the design, run an on-device test matrix: app swiped from Recents; force-stopped; after reboot without opening the app; Autostart on versus off; screen off for 3+ hours with the service running. Record which triggers fire in each case.
- Play the confirmation sound directly from the foreground service with MediaPlayer. Keep the service's notification channel silent so MIUI's channel-sound behaviour is irrelevant and there is no double sound.
- Default the sound to alarm usage so it plays on the phone speaker and the car together and ignores silent/vibrate, and offer a setting to switch to media usage (car only, follows media volume). Verify the actual routing on the phone, since Xiaomi's audio policy may differ from AOSP.
- Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK before playing and abandon it when the clip ends, so music ducks briefly. Skip the sound if a phone call is active.
- For the 'connecting' sound, play immediately and accept that it will come from the phone speaker. For the 'running' sound, wait for A2DP to report connected (with a timeout of roughly 8-10 seconds, then fall back to the phone speaker) if the owner wants it through the truck.
- Let the owner pick their own audio file with the system file picker and copy it into app-private storage at pick time. Ship a neutral built-in default beep; do not bundle Star Wars audio in the project.
- Do not add the MIUI-Autostart library without first confirming it is maintained and works on HyperOS; it uses hidden MIUI APIs and its README does not mention HyperOS.

## Questions raised for the owner

- Which exact phone is it: POCO X5 5G or POCO X5 Pro 5G? Please read out Settings > About phone: the MIUI or HyperOS version string and the Android version.
- Is a Xiaomi (Mi) account signed in on the phone, and does it have an active SIM with mobile data? Both are normally needed once to switch on 'Install via USB'.
- Is the phone on the stock global ROM, or has it been modified (unlocked bootloader, xiaomi.eu or other custom ROM)?
- Does the work truck use Android Auto (wired or wireless), or plain Bluetooth only?
- What is the truck stereo usually playing when you get in: radio, or Bluetooth audio from the phone?
- Where should the R2-D2 sound be heard: phone speaker, truck speakers, or both?
- Should the sound still play when the phone is on silent, vibrate or Do Not Disturb?
- 'When connecting and running': do you want two sounds (one when Bluetooth connects, one when GPS recording actually starts) or a single sound? Do you also want a sound when the trip ends?
- Do you already have your own R2-D2 audio file on the phone to pick (MP3, WAV or OGG)? The app would not ship one.
- Is it acceptable for music or a podcast to dip in volume for the two seconds the sound plays?
- Do you habitually swipe apps away in Recents or use 'Clear all', and do you use Battery saver or Ultra battery saver mode? Both affect whether a trip can start.
- Which mileage app fails to start trips today, and are Autostart and Battery saver 'No restrictions' already set for it? That tells us whether those settings alone are enough on this phone.
