# ADR-004: Google Play as a second channel, with the phone's own key
Date: 2026-10-09
Status: Accepted (the app's changes are made; nothing has been uploaded to Google Play yet)

## Context

Until now MilO was "never on the Play Store" (ENGINEERING_STANDARDS §1). Since 2026-10-08 anyone can download it as an APK from GitHub Releases, signed on the Mac with the phone's own key, `~/keys/milo.jks`; the first release, 0.1.0, was published on 2026-10-09.

On 2026-10-09 Shawn asked: "i may want to add this app to the play store eventually please walk me through the steps". He then created the app in the Play Console of his organisation account (2795748 Alberta Ltd., which also publishes GOpher Forms and SeaWingman) as "MilO Trip Log", package `com.shawnkowalchuk.milo`, English (Canada), free. Asked whether to make the changes Google Play would want, he answered: "yes start working on the changes and i want the android auto screen".

What Google Play asks of an app like MilO, as found on 2026-10-09 (from Google's pages where they could be read, otherwise from developers' reports of reviews; the Play Console's own policy pages are the authority at upload):

- **Background location** ("Allow all the time") needs a declaration in the Console with a short video, and a disclosure in the app itself, shown before the request and answered by a press: what location is collected, why, that it is collected "even when the app is closed or not in use", and whether it is shared.
- **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** is allowed only for a short list of kinds of app whose core function the battery optimisation breaks (chat, task automation, a connection to a device, and a few more). A mileage logger is not clearly on it. The list of every app's setting can be opened without the permission.
- **A link that lets people pay the developer outside Google Play's billing** is against the payments policy. Donations are allowed only to a tax-exempt organisation. Apps have been refused and removed for a donation link (WireGuard, 2019; AnkiDroid, 2026). The Buy me a coffee tile (2026-10-08) is such a link.
- **Android Auto:** the Car App Library is open to navigation, point-of-interest, IoT, VoIP and weather apps, and Google reviews a car screen against its quality rules before it reaches cars. MilO's screen (a trip's status with Start and End) is declared as IoT (`androidx.car.app.category.IOT`), which is the closest of them and still not a natural fit.
- **An app from a trusted store** is what Android Auto needs to show a Car App Library screen in a car (ARCHITECTURE, "Distribution risk"). A sideloaded MilO is not expected to appear in the truck; a MilO from Google Play can.

## Decision

1. **Google Play becomes a second channel beside GitHub Releases.** The same code, the same version name and `versionCode` sequence, built on the Mac: an APK for GitHub (`assembleRelease`), an app bundle for Google Play (`bundleRelease`). There is one build of the app, not one per store: no build flavours.
2. **Play App Signing with MilO's own key.** At the first upload the Console asks which key signs the app. Shawn uploads `milo.jks` with the tool the Console offers, rather than letting Google make a new key. Then the copy from Google Play, the APK from GitHub and the phone's MilO are all signed alike, and each installs over the others with the trips kept. A key made by Google would never update the phone's MilO without an export, an uninstall and an import. **This choice cannot be undone** once a build is uploaded. The upload key is the same `milo.jks`, which already signs the release build.
3. **Automatic protection is off** (Shawn was told to press "Turn off" when creating the app). It adds a check to the copy on Google Play that sends people from any other source to Google Play, which would get in the way of the GitHub APK.
4. **The app's changes,** in the one build for both channels:
   - **A location disclosure before "Allow all the time"** (`feature/setup/LocationDisclosure.kt`): Setup's background-location button first shows what location MilO collects, why, that it does so even when the app is closed or not in use, that it stays on the phone, and that the two ends of a trip go to the phone's address lookup; Android is asked only after "Continue". It is shown at every press, so it always comes before the request.
   - **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is removed.** Setup's battery row opens Android's list of every app's battery optimisation, and its sentence says to show all apps, choose MilO and "Don't optimise". The exemption Shawn already gave stays: removing the permission takes nothing away from an app on the list.
   - **The Buy me a coffee tile is not shown in a copy installed from Google Play** (`platform/system/InstallSource.kt`: Android's installer of record is `com.android.vending`). The APK from GitHub keeps it, and the website keeps its button. If Google Play still objects to the address being in the app at all, the tile is removed from the app.
5. **The Android Auto screen stays,** as Shawn chose, under IoT. If Google's review refuses it, the choices are to argue the case, to find a category that fits, or to publish on Google Play without the screen; none is taken in advance.
6. **No ads.** The Console's ads question is answered "No". Shawn asked whether to answer "Yes" now and add AdMob later: the answer describes the app as it is, can be changed at any time, and is changed before an update with ads is published. Ads would bring the internet permission and end the privacy promise that nothing leaves the phone, so they would get an ADR of their own.

## Consequences

- **Better:** a MilO from Google Play can show its Android Auto screen in the truck, which the sideloaded one cannot; updates arrive from Google Play; the app can be found.
- **Worse: Google holds a copy of the signing key.** "The key never leaves the Mac" (2026-10-08) stops being true at the first upload; the key's password still does not leave the Keychain. Google keeps the key safe as Play App Signing does for every app, and has a copy should the Mac's be lost.
- **Worse:** Google's review can now change what MilO may do, at each update. The disclosure, the declarations, the data safety form and the privacy policy must follow every change to what the app collects; a change to `website/privacy.html` already goes with such a change (ADR-003), and the Console's forms now too.
- **Worse:** the battery row takes more taps: a list to search instead of a dialog for MilO alone.
- **Locked in:** the package name, and the signing key once uploaded.
- **Unknown until tried:** whether the review accepts the screen under IoT; whether HyperOS opens the battery list or MilO's App info (the fallback); how the review reads the address lookup and Android's backup for the data safety form. Device checks GP-1 to GP-8.
