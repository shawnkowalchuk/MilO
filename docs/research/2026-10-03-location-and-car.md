# Research: GPS recording, geocoding, CarConnection and Activity Recognition

> Gathered 2026-10-03 by a research agent reading live primary sources. **Not independently verified.** Treat as leads to confirm when the code is built and run on the phone.
> This file is evidence for the ADRs. It records what was true on that date and is not kept current.

## Summary

Research only; I created and modified nothing in the project. On the housekeeping request: the pointers were already fixed when I looked. CLAUDE.md and README.md point at docs/..., docs/adr/ exists, and FINDINGS_LOG records the move. Two bare mentions remain in CLAUDE.md's build-loop diagram (lines 68 and 70: "APP_ENCYCLOPEDIA.md" and "FINDINGS_LOG.md" without the docs/ prefix); I left them alone.

Six results change or sharpen what docs/APP_ENCYCLOPEDIA.md currently plans:

1. "A point every 5 seconds or 10 m" cannot be expressed in FusedLocationProvider. Interval and minimum distance combine as AND, not OR. Request a plain 5 s interval with minimum distance 0 and apply the 10 m rule in app code, which also keeps the raw points the encyclopedia wants stored.
2. The Android Auto screen is at risk. Google's testing page says the "Unknown sources" developer option does not apply to apps built with the Car App Library; on a real head unit they must come from a trusted source such as Google Play (internal app sharing or an internal test track counts). The encyclopedia and FINDINGS_LOG assume Unknown sources is enough.
3. A location foreground service started while the app is in the background throws SecurityException on Android 14+ unless "Allow all the time" location is granted. That permission is mandatory for a Bluetooth-triggered trip start, not optional. Installs from Android Studio/adb can hold it.
4. CarConnection only works while MilO's process is alive and something is observing it. It cannot wake a dead app, and no documented broadcast does so when Android Auto connects. It suits its planned role (hold the trip open after Bluetooth drops) but not as a start trigger.
5. Activity Recognition transitions do wake a dead app through a PendingIntent broadcast. Google gives no latency figure; third-party reports I could not open suggest about a minute or more. That fits the planned alert-only use and would be poor as a trip starter.
6. Geocoder needs network, takes no API key, and guarantees nothing about availability. Trips must store coordinates first and fill addresses later with a network-constrained retry job.

Versions confirmed on Google Maven and Maven Central today: play-services-location 21.4.0, androidx.car.app 1.7.0 stable (1.8.0-rc01 exists), work-runtime-ktx 2.12.0, lifecycle-service 2.11.0, kotlinx-coroutines-play-services 1.11.0.

Not confirmed from a source I could open: Activity Recognition latency numbers (Medium posts blocked), numeric odometer tolerances, and real-world CarConnection lag. Issue tracker pages required sign-in. HyperOS-specific behaviour is not covered by any primary source and needs testing on the POCO X5.

## Findings

### 1. A1. Interval and minimum distance combine as AND, so "every 5 s or 10 m" is not expressible in one LocationRequest.

setMinUpdateDistanceMeters: "If a derived location update is not at least the specified distance away from the previous location update delivered to the client, it will not be delivered." Locations are derived on the interval, then dropped if they moved less than the minimum distance from the last delivered one. With 5 s + 10 m you get at most one point per 5 s and none while stationary. That hides the stationary heartbeat and discards raw points the encyclopedia wants kept for recalculation. Default minimum distance is 0.

- Confidence: high · Applies to: Part A - LocationRequest settings
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder

### 2. A2. Documented defaults and meanings of the other LocationRequest.Builder settings.

Priority default is BALANCED_POWER_ACCURACY, so PRIORITY_HIGH_ACCURACY must be set explicitly. setIntervalMillis is a desired interval: updates may arrive faster (down to the min update interval) or slower if throttled. setMaxUpdateDelayMillis default 0 means no batching. setWaitForAccurateLocation default true; it only affects HIGH_ACCURACY and delays initial low-accuracy fixes briefly. setMaxUpdateAgeMillis default equals the interval, so the first delivery may be a cached location up to one interval old; 0 means fresh fixes only. setGranularity default follows the permission level.

- Confidence: high · Applies to: Part A - LocationRequest settings
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder

### 3. A3. play-services-location 21.4.0 is the latest release and it changed the implicit minimum update interval to half the requested interval.

Release note dated 25 June 2026: "Changed IMPLICIT_MIN_UPDATE_INTERVAL to represent half of the requested interval." With a 5 s interval and no explicit setMinUpdateIntervalMillis, points can arrive every 2.5 s when another app (for example Maps navigation) is requesting faster fixes. Set the minimum update interval explicitly so point density is deterministic. Google Maven metadata lists 21.4.0 as the newest version, with no pre-release above it.

- Confidence: high · Applies to: Part A - dependency version
- Source: https://developers.google.com/android/guides/releases

### 4. A4. Batching should stay off (max update delay 0).

Non-batched requests "are guaranteed to receive only single locations that are monotonically increasing in time". Batched requests "may receive batches of locations, and batches may be out-of-order with respect to other received batches" and such locations "may not be evaluated against... minimum update interval or minimum update distance". Batching support also varies by hardware. For a live km readout and simple distance summation, immediate in-order delivery is the right trade.

- Confidence: high · Applies to: Part A - batching
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder

### 5. A5. Use a LocationCallback inside the foreground service, not a PendingIntent.

The PendingIntent overload "is suited for receiving location updates in the background, even when the receiving app may have been killed by the system... For foreground use cases prefer to listen for location updates via a listener or callback instead of a pending intent." A foreground service is the foreground case. A callback registration dies with the process, so the service must re-request updates when it restarts and trip state must be persisted. PendingIntent requests are removed on upgrade or force-stop. Both forms note "a wakelock may be held on the client's behalf while delivering locations", which covers delivery only. LocationCallback also reports LocationAvailability changes, worth writing to the event log.

- Confidence: high · Applies to: Part A - callback vs PendingIntent
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient

### 6. A6. With the screen off, full-rate updates continue only while a foreground service is running; otherwise location drops to a few fixes per hour.

Background apps "can receive location updates only a few times each hour"; starting a foreground service is a listed way to keep the foreground rate. The same page cautions that a foreground service started while the app is in the background cannot access location on Android 11+ unless ACCESS_BACKGROUND_LOCATION is granted.

- Confidence: high · Applies to: Part A - screen off
- Source: https://developer.android.com/about/versions/oreo/background-location-limits

### 7. A7. On Android 14+ a location-type foreground service cannot be created from the background without ACCESS_BACKGROUND_LOCATION; the system throws SecurityException.

Service-types page: "you cannot create a location foreground service while your app is in the background, unless you've been granted the ACCESS_BACKGROUND_LOCATION runtime permission." Restrictions page: for apps targeting Android 14+, creating the service without the permission currently held throws SecurityException, and checkSelfPermission returns GRANTED even in the background, so it does not predict this. A Bluetooth-triggered start with the app closed is exactly this case. The manifest also needs foregroundServiceType="location" and FOREGROUND_SERVICE_LOCATION, and location services must be on.

- Confidence: high · Applies to: Part A - trip start reliability
- Source: https://developer.android.com/develop/background-work/services/fgs/service-types

### 8. A8. Background location is a hard-restricted permission, but installs from Android Studio/adb can hold it.

Manifest.permission says ACCESS_BACKGROUND_LOCATION "cannot be held by an app until the installer on record allowlists the permission". In AOSP, PackageInstaller.SessionParams.installFlags defaults to INSTALL_ALL_WHITELIST_RESTRICTED_PERMISSIONS, and the shell install command only clears it when --restrict-permissions is passed. A normal Android Studio install therefore allows the user to grant "Allow all the time".

- Confidence: high · Applies to: Part A - sideloading and permissions
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/pm/PackageManagerShellCommand.java

### 9. A9. The current background-start exemption list names activity recognition transitions and battery-optimisation exemption, but no longer names Bluetooth broadcasts; AOSP still grants the Bluetooth exemption in code.

The developer page lists "Your app receives an event that's related to geofencing or activity recognition transition" and "The user turns off battery optimizations for your app"; there is no Bluetooth entry. AOSP main's Bluetooth stack still sends ACL connect/disconnect broadcasts with a temporary allowlist of type FOREGROUND_SERVICE_ALLOWED, reason REASON_BLUETOOTH_BROADCAST, default 20 s (Utils.getTempBroadcastOptions, used in RemoteDevices). So the backup ACL receiver should still be able to start the service within about 20 s on AOSP-like builds. Whether HyperOS preserves this is unverified; the battery-optimisation exemption already on the checklist is the documented fallback.

- Confidence: medium · Applies to: Part A - trip start reliability (overlaps Bluetooth topic)
- Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

### 10. A10. Battery Saver can switch location off when the screen is off, depending on the device's configured mode.

PowerManager defines LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF, ALL_DISABLED_WHEN_SCREEN_OFF, THROTTLE_REQUESTS_WHEN_SCREEN_OFF and FOREGROUND_ONLY. AOSP LocationProviderManager marks registrations inactive in the three screen-off modes when the device is non-interactive. The mode is device configuration, and I could not determine what HyperOS ships. AOSP BatterySaverPolicy relaxes it to FOREGROUND_ONLY while car projection is active ("ensure that navigation works"), which helps only when Android Auto is running. The app can read PowerManager.getLocationPowerSaveMode() and warn.

- Confidence: medium · Applies to: Part A - screen off
- Source: https://developer.android.com/reference/android/os/PowerManager

### 11. A11. Doze should not interrupt recording in a moving truck, and AOSP's stationary throttle skips high-accuracy requests.

Doze applies when the device is "unplugged and stationary for a period of time, with the screen off" and exits on movement, screen-on or charging. AOSP main's StationaryThrottlingLocationProvider replaces fixes with the last known location only when the device is both in doze and stationary, and its condition excludes requests with QUALITY_HIGH_ACCURACY. Doze also suspends network access and defers jobs, which matters for geocoding right after a trip ends. This is AOSP behaviour; HyperOS may add its own restrictions.

- Confidence: medium · Applies to: Part A - screen off
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/core/java/com/android/server/location/provider/StationaryThrottlingLocationProvider.java

### 12. A12. Location accuracy is a 68% radius, and phone GPS is typically about 5 m under open sky, so the planned 25 m cut-off is reasonable.

getAccuracy: "estimated horizontal accuracy radius in meters of this location at the 68th percentile confidence level". About a third of fixes fall outside their stated radius. GPS.gov: "GPS-enabled smartphones are typically accurate to within a 4.9 m (16 ft.) radius under open sky" and "their accuracy worsens near buildings, bridges, and trees". A 25 m threshold rejects Wi-Fi/cell fixes and badly degraded GNSS while keeping normal urban fixes. The threshold value itself is engineering judgement, not from a source.

- Confidence: high · Applies to: Part A - accuracy filtering
- Source: https://developer.android.com/reference/android/location/Location

### 13. A13. Stationary GPS noise systematically adds distance; the reported GNSS speed is the best single gate against it.

Ranacher et al., "Why GPS makes distances bigger than they are": measurement error causes systematic overestimation of trajectory length, growing with error variance and shrinking with segment length and error autocorrelation. Summing 5 s segments on an idling truck therefore accumulates phantom km. Android's getSpeed doc: speed "may be more accurate than would be obtained simply by calculating distance / time for sequential positions, such as if the Doppler measurements from GNSS satellites are taken into account". Standard mitigations (practice, not from one source): count a segment only when reported speed exceeds a small threshold; keep a fixed anchor point and add distance only once displacement exceeds max(10 m, a multiple of accuracy); apply the accuracy filter. Activity Recognition STILL is not useful here because the Transition API deliberately ignores stops inside IN_VEHICLE.

- Confidence: medium · Applies to: Part A - stationary drift
- Source: https://pmc.ncbi.nlm.nih.gov/articles/PMC4786863/

### 14. A14. Location.distanceTo uses the WGS84 ellipsoid (Vincenty inverse); haversine is a spherical approximation with up to 0.5% error.

Docs: "Distance is defined using the WGS84 ellipsoid." AOSP source comment: based on the NGS "Inverse Formula", with WGS84 axes. Wikipedia: haversine "cannot be guaranteed correct to better than 0.5%". On 5 s segments both are far more precise than GPS noise, so the choice does not affect real accuracy. distanceTo/distanceBetween needs no extra code but is an Android framework call, so plain JVM unit tests cannot call it without Robolectric; a pure-Kotlin function is testable anywhere.

- Confidence: high · Applies to: Part A - distance computation
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/location/Location.java

### 15. A15. Agreement with the odometer is limited by the odometer itself; I could not confirm numeric tolerances from a source I opened.

The page I opened confirms UN Regulation 39's 02 series (in force 26 September 2025) "sets new limits on the accuracy of distance values recorded and displayed by the odometer" but gives no number. Search snippets (not opened) cite ±4% in R39 and ±2.5% recommended by SAE J2976. My own estimate, not sourced: at 5 s sampling, chord-cutting on curves under-reads by well under 1% and noise over-reads by a similar amount at road speeds, so 1-3% agreement is a realistic expectation. Validate on a known route.

- Confidence: low · Applies to: Part A - error against odometer
- Source: https://www.ats-group.org/en/updates-to-unece-regulation-r39/

### 16. A16. Jump detection must use elapsed-realtime timestamps, not wall-clock time.

getTime: the system clock "is not monotonic; it can jump forwards or backwards unpredictably... so this time should not be used to order or compare locations. Prefer getElapsedRealtimeNanos()". Implied speed is distance divided by the elapsed-realtime difference. The FLP docs also warn the first delivery may be a cached past location, so check its age. Design notes (practice): compare against the last accepted point; if several consecutive points are rejected but agree with each other, accept the new cluster so one bad anchor cannot poison the rest of the trip; a gap after a tunnel with plausible implied speed is not a jump and should be kept.

- Confidence: high · Applies to: Part A - impossible jumps
- Source: https://developer.android.com/reference/android/location/Location

### 17. B1. The blocking Geocoder call is deprecated from API 33; the listener version exists only on API 33+.

getFromLocation(lat, lon, max) is "deprecated in API level 33... Use getFromLocation(double,double,int,GeocodeListener) instead to avoid blocking a thread". GeocodeListener: "Only one of the methods will ever be invoked per geocoding attempt. There are no guarantees on how long it will take for a method to be invoked, nor any guarantees on the format or availability of error information." onGeocode "may return an empty list"; onError's message may be null. With minSdk 31 both paths are needed; the POCO X5 on Android 14 only ever uses the listener path.

- Confidence: high · Applies to: Part B - API shape
- Source: https://developer.android.com/reference/android/location/Geocoder.GeocodeListener

### 18. B2. Geocoder needs network, takes no API key, and comes with no availability or accuracy guarantee.

Class docs: "Geocoding services may provide no guarantees on availability or accuracy... Do not use this API for any safety-critical or regulatory compliance purpose." The blocking variant: "This API may hit the network, and may block for excessive amounts of time." The API has no key parameter. AOSP shows Geocoder is a thin proxy to a device-configured geocode provider package (config_geocoderProviderPackageName); on Google phones that is Play services (my inference, not stated in the source). isPresent() true still gives "no guarantee that any individual geocoding attempt will succeed". For a mileage report, treat addresses as best-effort labels and keep coordinates as the record.

- Confidence: high · Applies to: Part B - network and key
- Source: https://developer.android.com/reference/android/location/Geocoder

### 19. B3. Geocoder failure modes: IOException or timeout on the blocking call, onError or an empty list on the listener, and the listener is not called on the main thread.

From AOSP main: the blocking wrapper waits 15 s then throws IOException(TimeoutException), and throws IOException with the provider's error string on failure. If no provider exists the system calls onError(null). Provider-side throwables are passed back as strings. The listener is invoked from a Binder callback stub with no executor, so it runs on a binder thread. No rate limit is documented. Widely reported errors such as "grpc failed" and "Service not Available" appeared in search results, but the issue tracker pages required sign-in and I could not read them. The 15 s figure is from main and may differ on the phone's Android 14 build.

- Confidence: medium · Applies to: Part B - failure modes
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/location/java/android/location/Geocoder.java

### 20. B4. Retry pattern for trips that end with no signal: save coordinates, leave the address empty, and let a network-constrained job fill it in.

Doze "suspends network access" and defers JobScheduler/WorkManager work to maintenance windows, so an immediate attempt at trip end can fail even with coverage. Design recommendation: store start/end latitude and longitude on the trip with nullable address fields; enqueue unique WorkManager work with a connected-network constraint and exponential backoff; wrap the listener call in a timeout because no response time is guaranteed; treat an empty list as "no address" after a capped number of attempts and fall back to coordinates; sweep for trips with missing addresses on app start. work-runtime-ktx 2.12.0 is the current stable on Google Maven.

- Confidence: medium · Applies to: Part B - retry
- Source: https://developer.android.com/training/monitoring-device-state/doze-standby

### 21. C1. CarConnection detects Android Auto by querying a content provider hosted by the Android Auto app and re-querying on a broadcast; wired and wireless are not distinguished.

androidx source: CarConnectionTypeLiveData queries content://androidx.car.app.connection for column "CarConnectionState" and, while active, registers a context receiver for androidx.car.app.connection.action.CAR_CONNECTION_UPDATED (exported on API 33+). A null cursor, missing column or empty result is treated as NOT_CONNECTED. Values: 0 not connected, 1 native, 2 projection. Both USB and wireless Android Auto report 2.

- Confidence: high · Applies to: Part C - mechanism
- Source: https://raw.githubusercontent.com/androidx/androidx/androidx-main/car/app/app/src/main/java/androidx/car/app/connection/CarConnectionTypeLiveData.java

### 22. C2. The required <queries> entry ships inside the library's own manifest, so it merges automatically.

androidx.car.app:app's AndroidManifest.xml contains <queries><provider android:name="androidx.car.app.connection.provider" android:authorities="androidx.car.app.connection"/></queries>. Depending on the library is enough. If the query were reimplemented without the library, this entry would have to be added by hand or the query returns null on Android 11+.

- Confidence: high · Applies to: Part C - manifest
- Source: https://raw.githubusercontent.com/androidx/androidx/androidx-main/car/app/app/src/main/AndroidManifest.xml

### 23. C3. Car App Library 1.7.0 is the current stable; targeting Android 14+ requires at least 1.3.0-beta01 for CarConnection.

Release page (9 September 2026): stable 1.7.0, release candidate 1.8.0-rc01, alpha 1.9.0-alpha02. Connection API page: "For apps that use the CarConnection API and target Android 14 (API level 34) and higher, use Car App Library version 1.3.0-beta01 or later. Earlier versions of the library throw an exception on devices running Android 14 or later." CarConnection lives in androidx.car.app:app, which app-projected already pulls in.

- Confidence: high · Applies to: Part C - dependency version
- Source: https://developer.android.com/jetpack/androidx/releases/car-app

### 24. C4. CarConnection can be observed from a Service with no Activity, but it only updates while it has an active observer.

The constructor takes any Context and is @MainThread. The receiver is registered in onActive and unregistered in onInactive, and the query is asynchronous, so reading getValue() once without observing returns null or a stale value. From a service use observeForever (removing the observer in onDestroy) or a LifecycleService. The trip service must already be observing before Bluetooth drops for the "still on Android Auto" check to be valid at the end of the grace period. The reference docs only describe the Activity pattern.

- Confidence: high · Applies to: Part C - use from a Service
- Source: https://raw.githubusercontent.com/androidx/androidx/androidx-main/car/app/app/src/main/java/androidx/car/app/connection/CarConnection.java

### 25. C5. Nothing documented wakes a dead app when Android Auto connects.

The car-connection broadcast is not on the implicit-broadcast exemption list, so a manifest receiver would not get it; CarConnection only uses a context-registered receiver. Android Auto no longer changes UI mode: "On devices running Android 12 or higher, Android Auto doesn't change the UI mode of the device when running, so don't rely on it." The exemption list does include BluetoothDevice ACL connected/disconnected and the headset/A2DP connection-state broadcasts. I could not inspect how the closed-source Android Auto app sends its broadcast, hence medium.

- Confidence: medium · Applies to: Part C - waking a dead app
- Source: https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions

### 26. C6. On a native Android Automotive OS head unit, CarConnection reports NATIVE only to apps running on the car itself; a phone merely paired to it reports NOT_CONNECTED.

The constructor picks AutomotiveCarConnectionTypeLiveData (always CONNECTION_TYPE_NATIVE = 1) when the device running the app has FEATURE_AUTOMOTIVE. MilO runs on the phone, which does not. Next to a Google built-in truck, the phone reports 2 only if it is actually projecting Android Auto to that unit, otherwise 0. CarConnection is therefore not a general "in the truck" signal.

- Confidence: high · Applies to: Part C - Automotive OS value
- Source: https://raw.githubusercontent.com/androidx/androidx/androidx-main/car/app/app/src/main/java/androidx/car/app/connection/CarConnection.java

### 27. C7. CarConnection reliability and timing are undocumented; Google's own guidance treats it as a signal that "might not always be accurate" when combined for detection.

The parked-apps page uses CONNECTION_TYPE_PROJECTION plus display ID to detect Android Auto use and cautions the method "might not always be accurate". The value depends on the Android Auto app answering the provider query; any failure reads as not connected. I found no primary source for how long after plug-in or unplug the value changes, and could not search the issue tracker (sign-in required). Use it only to hold a trip open, and guard against a stuck "connected" value so a trip cannot run forever.

- Confidence: low · Applies to: Part C - reliability
- Source: https://developer.android.com/training/cars/parked/auto

### 28. C8. Google's docs say a sideloaded Car App Library app will not run on a real head unit even with Unknown sources enabled.

Testing page: "To test your app in real vehicles, you must install it from a trusted source such as Google Play... You can use Internal App Sharing or an Internal Test Track." And: "Android Auto has a developer option that lets you run apps that aren't installed from a trusted source. This setting applies to media, messaging notifications, and parked apps but doesn't apply to apps built using the Android for Cars App Library." This contradicts the Android Auto screen entry in docs/APP_ENCYCLOPEDIA.md and the matching FINDINGS_LOG decision. The Desktop Head Unit emulator is unaffected. CarConnection itself does not depend on this. Actual behaviour on the truck should be tested before building the screen.

- Confidence: high · Applies to: Part C - Android Auto screen (phase 1 plan)
- Source: https://developer.android.com/training/cars/testing

### 29. C9. Wireless Android Auto starts from a Bluetooth connection; whether wired Android Auto always keeps Bluetooth connected is not confirmed by a primary source.

Google's help page: "The first time you connect wirelessly, you will need to pair your phone and car via Bluetooth" and "Once your phone is paired with your car via Bluetooth, Android Auto should start within a few seconds." So wireless sessions are preceded by the Bluetooth connect MilO already listens for. For USB sessions, search results (articles I did not open) say calls still use Bluetooth hands-free, but the help page does not state it. This decides whether the CarConnection hold-open rule ever matters on this truck.

- Confidence: medium · Applies to: Part C - wired vs wireless
- Source: https://support.google.com/androidauto/answer/6348029

### 30. D1. The Transition API needs the runtime permission android.permission.ACTIVITY_RECOGNITION on Android 10+; the developer guide page is stale on this.

ActivityRecognitionClient reference: "For Android 10 (API level 29) and later: android.permission.ACTIVITY_RECOGNITION permission; For Android 9 (API level 28) and earlier: com.google.android.gms.permission.ACTIVITY_RECOGNITION". Manifest.permission lists ACTIVITY_RECOGNITION as protection level dangerous, so it is a runtime prompt (shown as "Physical activity"). The transitions guide still mentions only the old gms permission. With minSdk 31 only the android.permission one is needed.

- Confidence: high · Applies to: Part D - permissions
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient

### 31. D2. Transition events are delivered only through a PendingIntent, which must be mutable on Android 12+ and explicit when targeting Android 14+.

Google's platform sample builds the PendingIntent with FLAG_MUTABLE on S and above, and the geofencing guide states "Starting on Android S+ the pending intent has to be mutable" (Play services fills in the result extras). Android 14 behaviour change: "If an app creates a mutable pending intent with an intent that doesn't specify a component or package, the system throws an exception." So use PendingIntent.getBroadcast with an Intent naming MilO's own receiver class. Note the sample itself uses an implicit action intent, which would throw when targeting 34+.

- Confidence: high · Applies to: Part D - PendingIntent setup
- Source: https://github.com/android/platform-samples/blob/main/samples/location/src/main/java/com/example/platform/location/useractivityrecog/UserActivityTransitionManager.kt

### 32. D3. The PendingIntent does wake a dead app, provided the receiver is declared in the manifest and the app has not been force-stopped.

Google's overview: a PendingIntent "removes the need to have a service constantly running in the background for activity detection purposes". The codelab registers its receiver in an Activity's onStart, which only works while that Activity is alive and is the wrong pattern for MilO. The equivalent location API states PendingIntent requests are removed when the app is upgraded, removed or force-quit; I assume the same for activity recognition. On HyperOS, starting a dead app's receiver likely also needs Autostart enabled (dontkillmyapp lists it; not verified for this API).

- Confidence: medium · Applies to: Part D - waking a dead app
- Source: https://developers.google.com/location-context/activity-recognition

### 33. D4. An activity recognition transition event is a documented exemption that allows starting a foreground service from the background.

Exemption list: "Your app receives an event that's related to geofencing or activity recognition transition." A location-type service would still need background location (finding A7). For MilO's alert-only design this is moot: the alert is a notification, and starting the trip from the user's tap on it is itself exempt ("The user performs an action on a UI element related to your app... notification").

- Confidence: high · Applies to: Part D - foreground service start
- Source: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

### 34. D5. Google publishes no latency figure for IN_VEHICLE detection; third-party reports suggest roughly a minute, sometimes several.

The only official statement: "The latency of event detection might vary by device." Search snippets from a Life360 engineering post and others (Medium blocked my fetches, so not opened) report about one minute typical, longer with the phone in a cup holder, and slow traffic being reported as a mix of IN_VEHICLE, ON_BICYCLE and STILL. Google's 2018 announcement quotes Intuit and Life360 on improved reliability and battery, without numbers. IN_VEHICLE fires for any vehicle, including as a passenger, and the EXIT event arrives only when another activity is detected. Acceptable for an alert; as a trip trigger it would lose the first part of every trip.

- Confidence: low · Applies to: Part D - latency and reliability
- Source: https://developer.android.com/develop/sensors-and-location/location/transitions

### 35. D6. Battery cost is low by design.

Reference: the Transition API "improves accuracy, consumes less power". The underlying detection reads "short bursts of sensor data" and "only makes use of low power sensors"; "activity reporting may stop when the device is 'STILL' for an extended period" and resumes on movement, on devices with a significant-motion sensor. No measured figure is published.

- Confidence: medium · Applies to: Part D - battery
- Source: https://developers.google.com/android/reference/com/google/android/gms/location/ActivityRecognitionClient

### 36. D7. Whether transition registrations survive a reboot is not documented; treat them like geofences and re-register.

The geofencing guide, served by the same Play services location process, says registrations survive Play services upgrades and crashes but must be re-registered after reboot, reinstall, or cleared data. Nothing equivalent is written for activity transitions. Re-registering on boot, on app update and on every app start is cheap and safe: a new request with the same PendingIntent replaces the old one.

- Confidence: medium · Applies to: Part D - registration lifetime
- Source: https://developer.android.com/develop/sensors-and-location/location/geofencing

### 37. E1. Xiaomi-specific background settings are documented only by a community source and not for HyperOS on Android 14 specifically.

dontkillmyapp lists: Background autostart permission, battery saver "No restrictions", locking the app in recents, and disabling MIUI optimisations in developer settings. The page says nothing specific about GPS being cut with the screen off or about HyperOS. Every AOSP-derived behaviour in this report (Bluetooth allowlist, battery-saver location mode, doze throttling) needs confirming on the POCO X5.

- Confidence: low · Applies to: Target phone (POCO X5, HyperOS)
- Source: https://dontkillmyapp.com/xiaomi

## Recommendations

- Build the location request as: PRIORITY_HIGH_ACCURACY, interval 5000 ms, explicit setMinUpdateIntervalMillis (suggest 5000 so density is predictable), setMinUpdateDistanceMeters(0), setMaxUpdateDelayMillis(0), setWaitForAccurateLocation(true), setMaxUpdateAgeMillis(0). Reword the encyclopedia's "every 5 seconds or 10 m" to "a fix every 5 s; distance counted only after 10 m of real movement".
- Receive fixes with a LocationCallback inside the location foreground service. Write each raw fix to Room as it arrives (latitude, longitude, accuracy, speed, speed accuracy, elapsed-realtime timestamp, accepted/rejected flag). Return START_STICKY, persist the open trip, and re-request updates when the service restarts. Log LocationAvailability changes and time-to-first-fix to the event log.
- Compute distance in one pure, unit-tested function over stored points: drop fixes with accuracy over 25 m; drop jumps using elapsed-realtime time difference (the planned 180 km/h limit is fine) with a re-anchor rule after several mutually consistent rejects; keep an anchor point and add a segment only when displacement exceeds max(10 m, a multiple of accuracy) or reported GNSS speed is above about 1 m/s. Tune the thresholds from the first test drives using the stored raw points.
- Use Location.distanceBetween (WGS84) for segment length, or a pure-Kotlin haversine if the distance function must run in plain JVM unit tests. The difference (up to 0.5%) is below GPS noise; pick one and record it in the encyclopedia.
- Treat "Allow all the time" location as a hard prerequisite for automatic trip start, not just a checklist item: before relying on a Bluetooth trigger, verify it is granted and show a blocking warning if not. Wrap startForeground in a catch for SecurityException and ForegroundServiceStartNotAllowedException and write the failure to the event log and a notification.
- Keep the battery-optimisation exemption on the checklist as the documented guarantee for starting the service from the background, and add a Battery Saver warning (PowerManager.isPowerSaveMode / getLocationPowerSaveMode) because Battery Saver can stop location with the screen off.
- Geocoding: save coordinates on the trip immediately with nullable address fields; resolve addresses in unique WorkManager work (2.12.0) constrained to a connected network with exponential backoff and a capped attempt count; wrap the API 33+ listener in a coroutine with a timeout and hop off the binder thread; fall back to coordinates on the report when no address is found; check Geocoder.isPresent() once and log it.
- Use CarConnection from androidx.car.app:app 1.7.0 only as planned: to hold a trip open after Bluetooth drops. Observe it with observeForever from the trip service for the whole trip, log every change to the event log, treat null as not connected, and add a guard so a stuck "projection" value cannot keep a trip open indefinitely. Do not design any trip start around it.
- Before building the Android Auto screen, run a one-hour spike: install a minimal Car App Library app from Android Studio and check whether the truck's head unit lists it with Unknown sources on. If it does not, the choices are a private Play internal-testing upload, the Desktop Head Unit only, or dropping the screen. Update the encyclopedia entry and FINDINGS_LOG with the result either way.
- For the phase 2 driving alert: request android.permission.ACTIVITY_RECOGNITION; register IN_VEHICLE ENTER and EXIT with PendingIntent.getBroadcast using an explicit Intent to a manifest-declared receiver and FLAG_MUTABLE | FLAG_UPDATE_CURRENT; re-register on boot, on app update and on app start; filter by schedule and truck-connected state inside the receiver; start the trip only from the user's tap on the notification.
- Pin the versions confirmed today: com.google.android.gms:play-services-location:21.4.0, androidx.car.app:app and app-projected 1.7.0, androidx.work:work-runtime-ktx:2.12.0, androidx.lifecycle:lifecycle-service:2.11.0 (only if LifecycleService is used), org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0 (only if Task.await is wanted).
- Test on the POCO X5 early and record the results as findings: service start from a Bluetooth connect with the app swiped away and the screen off; a 30-minute screen-off drive checking for gaps in the stored fixes; a 30-minute engine-on idle checking phantom km; and a trip ending in airplane mode to exercise the geocoding retry.

## Questions raised for the owner

- Google's documentation says a sideloaded app with an Android Auto screen will not appear on a real head unit, even with "Unknown sources" on. If a quick test on your truck confirms that, which do you prefer: (a) create a Google Play developer account (one-time fee) and upload MilO privately to an internal test track, never public; (b) drop the Android Auto screen and rely on the phone notification and trip-start chirp; or (c) defer the screen to a later phase?
- When you plug the phone in by USB for Android Auto, does the truck's Bluetooth stay connected to the phone (for example, do calls still show as going through the truck)? And is the truck's screen a regular head unit running Android Auto from your phone, or a "Google built-in" system?
- Do you normally have Battery Saver on during work days, and is the phone usually charging while you drive? Battery Saver can stop GPS when the screen is off.
- How close does MilO's km need to be to the truck's odometer for Accounts to accept it? A realistic target is within about 1-3%, and the odometer itself has a tolerance of a few percent.
- MilO will only ever run on your POCO X5 (Android 14). Are you fine with the app refusing to install on anything older than Android 13 or 14? It removes extra code paths; the cost is that it would not install on an older spare phone.
- When no street address can be found for a trip's start or end (no signal for a long time, or a rural spot with no address), what should the PDF show: the nearest town, the GPS coordinates, or a blank you fill in by editing the trip?
