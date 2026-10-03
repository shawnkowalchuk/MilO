# Research: PDF, email, Auto Backup, signing, export/import and reminders

> Gathered 2026-10-03 by a research agent reading live primary sources. **Not independently verified.** Treat as leads to confirm when the code is built and run on the phone.
> This file is evidence for the ADRs. It records what was true on that date and is not kept current.

## Summary

Research only; no files were created or modified. (The docs-pointer housekeeping in the relayed request is already recorded as done in docs/FINDINGS_LOG.md, so I left it alone.)

Headline results for MilO's reporting, sharing and data-safety work:

1. BIGGEST RISK - Auto Backup will silently stop. Cloud Auto Backup is capped at 25 MB per app and is all-or-nothing: over the cap the system calls onQuotaExceeded() and backs up nothing (the last good backup stays, getting staler). MilO stores raw GPS points every ~5 s; by my estimate that is roughly 25-60 MB per year, so a single Room database would cross the cap within the first year. Put raw points in a separate database file that is excluded from cloud backup (or lives in noBackupFilesDir) and keep the trips database tiny.

2. SECOND RISK - the signing key. The debug keystore is generated per Mac. A new Mac means a new key, the install fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE, Android Studio offers to uninstall ("Uninstalling will remove the application data!"), and Auto Backup restore is ALSO refused on signature mismatch. Use one dedicated keystore kept outside git for every build installed on the phone, and back it up. Manual export is the only safety net that does not depend on the key.

3. PDF: PdfDocument works in points (US Letter = 612 x 792), records pages and only serialises in writeTo(), is not thread safe, and should be run on one background thread. Do a measure/paginate pass first so "Page X of Y" and repeated headers are possible.

4. Email: ACTION_SEND (not ACTION_SENDTO) with EXTRA_EMAIL as String[], EXTRA_SUBJECT, EXTRA_TEXT, EXTRA_STREAM (FileProvider content URI), explicit FLAG_GRANT_READ_URI_PERMISSION + ClipData, setPackage("com.google.android.gm"), with a fallback. Android 17 docs warn the automatic URI grant for ACTION_SEND goes away in Android 18, so set the flag explicitly. No <queries> entry is needed just to start Gmail. The app can never know the email was sent (ACTION_SEND output is "nothing"); the encyclopedia's confirm-on-return design is correct.

5. Export/import: a versioned JSON export is safer across schema versions than copying the SQLite file; if the file is copied, use VACUUM INTO (or checkpoint/close) because Room uses WAL.

6. Reminder: no exact-alarm permission is needed. A daily WorkManager check that is state-based ("is the reminder day past and last month not submitted?") survives reboots by itself and is simpler than AlarmManager, which loses alarms on reboot and force-stop.

Version facts confirmed today: Android 17 (API 37) went stable 16 June 2026; WorkManager 2.12.0 (23 Sep 2026); Room 2.8.5 and Room 3.0.3 (androidx.room3) both stable 9 Sep 2026; androidx.core 1.19.1 (23 Sep 2026).

## Findings

### 1. Part A: PdfDocument page sizes are in PostScript points (1/72 inch); US Letter portrait is 612 x 792.

PageInfo.Builder Javadoc: pageWidth/pageHeight are 'in PostScript (1/72nd of an inch)'. 8.5 in x 72 = 612, 11 in x 72 = 792, so use PdfDocument.PageInfo.Builder(612, 792, pageNumber).create(). The printing guide confirms 'elements are specified in points, which is 1/72 of an inch'. All Paint text sizes, stroke widths and coordinates on the page canvas are therefore points, not px/dp/sp (a 10 pt body font is textSize = 10f).

- Confidence: high · Applies to: Part A - PDF report
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/pdf/PdfDocument.java

### 2. Part A: PdfDocument allows one open page at a time, is not thread safe, and must be written then closed.

Class Javadoc: 'pages are created one by one, i.e. you can have only a single page to which you are writing at any given time. This class is not thread safe.' startPage() and writeTo() throw IllegalStateException if the current page is not finished or the document is closed. Sequence: startPage -> draw on page.canvas -> finishPage -> ... -> writeTo(OutputStream) -> close(). The class uses CloseGuard and a finalizer, so wrap in try/finally and always close().

- Confidence: high · Applies to: Part A - PDF report
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/graphics/java/android/graphics/pdf/PdfDocument.java

### 3. Part A: Pages are only recorded while drawing; the actual PDF is produced in writeTo(). A finished page cannot be revisited, so pagination must be computed before drawing.

Native implementation (PdfDocument.cpp): startPage creates an SkPictureRecorder, finishPage turns it into an SkPicture kept in a vector, and write() creates SkPDF::MakeDocument and replays every picture; close() frees them. Consequences: (1) 'Page X of Y' needs a first pass that measures every row and assigns it to a page, then a second pass that draws; (2) all pages stay in native memory until close() (trivial for a text table, significant only if bitmaps are drawn); (3) writeTo is where the heavy work and I/O happen. Recommended structure: pure-Kotlin layout pass producing List<Page(rows)> (unit-testable without Android), then a draw pass that repeats the day header/column header on each page, draws rows, daily subtotal, month total, signature line, and the footer 'Page n of N'. Keep a day's subtotal with at least its last row (widow control) and never split a wrapped row across pages.

- Confidence: high · Applies to: Part A - PDF report
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/libs/hwui/jni/pdf/PdfDocument.cpp

### 4. Part A: If a content rect is set, the page canvas origin moves to the content rect; simplest is to leave it unset and apply margins yourself.

Native startPage records into SkRect::MakeWH(contentRect.width(), contentRect.height()) and beginPage is given the content rect, so drawing coordinates become relative to the content rect's top-left and are clipped to it. To avoid off-by-margin bugs, do not call setContentRect; define margin constants (for example 36-54 pt) and compute x/y from them. The printing guide warns: 'many printers are not able to print to the edge of a physical piece of paper. Make sure that you account for the unprintable edges of the page'.

- Confidence: medium · Applies to: Part A - PDF report
- Source: https://developer.android.com/training/printing/custom-docs

### 5. Part A: Text measurement and wrapping use the normal Paint/TextPaint/StaticLayout APIs; the width argument is in canvas units, i.e. points on a PDF page.

Single-line cells (times, km): Paint.measureText for width, Paint.Align.RIGHT to right-align the km column, paint.fontMetrics (descent - ascent + leading) for row height. Wrapping cells (From/To street addresses): StaticLayout.Builder.obtain(text, 0, text.length, textPaint, columnWidth) ('width in pixels' = points here), then layout.height gives the wrapped cell height and layout.draw(canvas) after canvas.save()/translate(x, y)/restore(). Row height = max of the two address layouts plus padding. Paint.breakText is the low-level alternative ('Measure the text, stopping early if the measured width exceeds maxWidth'). Set Paint.SUBPIXEL_TEXT_FLAG and LINEAR_TEXT_FLAG on the report paints: Javadoc says LINEAR_TEXT disables font hinting and SUBPIXEL_TEXT makes 'glyph advances ... computed with subpixel accuracy', which keeps measured widths consistent with what is drawn at small point sizes (this last recommendation is my inference from the flag documentation, not a documented PDF requirement).

- Confidence: medium · Applies to: Part A - PDF report
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/text/StaticLayout.java

### 6. Part A: Draw table rules with an explicit stroke width; do not use stroke width 0.

Paint.setStrokeWidth Javadoc: 'Pass 0 to stroke in hairline mode. Hairlines always draws a single pixel independent of the canvas's matrix.' In a PDF a hairline is renderer-dependent and can print almost invisibly. Use Paint(style = STROKE, strokeWidth = 0.5f..0.75f pt) with canvas.drawLine for row rules and the signature line, and compute rule y positions from the same row-height numbers used by the layout pass.

- Confidence: medium · Applies to: Part A - PDF report
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/Paint.java

### 7. Part A: PdfDocument does not have to run on the main thread when drawing directly with Canvas, and Google recommends doing rendering and writing off the main thread.

The printing guide: 'Rendering a document for printing can be a resource-intensive operation. In order to avoid blocking the main user interface thread of your application, you should consider performing the page rendering and writing operations on a separate thread'. Because the class 'is not thread safe', confine the whole create-draw-write-close sequence to ONE background coroutine (for example withContext(Dispatchers.IO)) and never share the document across threads. Avoid the Javadoc sample's View.draw(page.canvas) approach: Views need to be measured/laid out and belong to the UI thread; MilO's table should be drawn with Canvas primitives only (Compose cannot draw into this canvas directly). Write to a temp file in cacheDir and rename when complete so a half-written PDF is never attached.

- Confidence: high · Applies to: Part A - PDF report
- Source: https://developer.android.com/training/printing/custom-docs

### 8. Part B: FileProvider setup is a non-exported provider with grantUriPermissions=true plus a paths XML; use <cache-path> for generated reports.

Manifest: <provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true"><meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/></provider>. res/xml/file_paths.xml: <paths><cache-path name="reports" path="reports/"/></paths>. Then FileProvider.getUriForFile(context, authority, File(cacheDir, "reports/Mileage-2026-09.pdf")). cache-path maps to getCacheDir(), which Auto Backup excludes automatically, so regenerable PDFs and CSVs never count against the 25 MB backup quota. Supported path tags: files-path, cache-path, external-path, external-files-path, external-cache-path, external-media-path (there is no tag for noBackupFilesDir). Latest stable androidx.core is 1.19.1 (23 Sep 2026).

- Confidence: high · Applies to: Part B - email
- Source: https://developer.android.com/training/secure-file-sharing/setup-sharing

### 9. Part B: Use ACTION_SEND for an email with one attachment; ACTION_SENDTO with mailto: is documented for the no-attachment case only.

Common Intents guide: 'ACTION_SENDTO (for no attachment) or ACTION_SEND (for one attachment) or ACTION_SEND_MULTIPLE (for multiple attachments)'; extras EXTRA_EMAIL (string array of To addresses), EXTRA_SUBJECT, EXTRA_TEXT, EXTRA_STREAM (Uri). The mailto: + ACTION_SENDTO form exists to make sure 'only email apps handle this', but it carries no attachment contract, and the framework only migrates EXTRA_STREAM into ClipData (which is what makes the URI permission grant work) for ACTION_SEND, ACTION_SEND_MULTIPLE and ACTION_CHOOSER, not for ACTION_SENDTO. So a SENDTO intent with EXTRA_STREAM would reach Gmail without read permission on the content URI.

- Confidence: high · Applies to: Part B - email
- Source: https://developer.android.com/guide/components/intents-common#Email

### 10. Part B: Gmail needs EXTRA_EMAIL as a String array, and the recommended Gmail-first intent is ACTION_SEND + setPackage with a fallback.

Sharing guide: 'Some email apps, such as Gmail, expect a String[] for extras like EXTRA_EMAIL and EXTRA_CC. Use putExtra(String, String[])'. Recommended intent: Intent(ACTION_SEND).apply { type = "application/pdf"; putExtra(EXTRA_EMAIL, arrayOf(accountsEmail)); putExtra(EXTRA_SUBJECT, subject); putExtra(EXTRA_TEXT, body); putExtra(EXTRA_STREAM, uri); clipData = ClipData.newRawUri("", uri); addFlags(FLAG_GRANT_READ_URI_PERMISSION); setPackage("com.google.android.gm") }, started from the Activity inside try/catch ActivityNotFoundException. Fallback when Gmail is missing or disabled: the same intent without setPackage, either with intent.selector = Intent(ACTION_SENDTO, Uri.parse("mailto:")) (resolves only email apps while still delivering ACTION_SEND with the attachment) or wrapped in Intent.createChooser. Do not hardcode Gmail's compose Activity class name; it is not a public contract. There is no official Google document of Gmail's intent handling (Gmail is closed source), so the pre-fill behaviour must be confirmed on the POCO during phase 3.

- Confidence: medium · Applies to: Part B - email
- Source: https://developer.android.com/training/sharing/send

### 11. Part B: A selector and setPackage cannot be combined on the same Intent.

Intent.setSelector Javadoc: 'You can not use both a selector and setPackage(String) on the same base Intent', and the method throws IllegalArgumentException("Can't set selector when package name is already set"). So the Gmail-targeted intent uses setPackage only, and the fallback intent uses the mailto selector only.

- Confidence: high · Applies to: Part B - email
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/content/Intent.java

### 12. Part B: Set FLAG_GRANT_READ_URI_PERMISSION explicitly; Android 17 documents that the automatic grant for ACTION_SEND will be removed in Android 18.

Android 17 behaviour changes (all apps): 'Currently, if an app launches an intent with a URI that has the action ACTION_SEND, ACTION_SEND_MULTIPLE, or ACTION_IMAGE_CAPTURE, the system automatically grants the read and write URI permissions to the target app. Starting in Android 18, the system will no longer automatically grant these permissions.' Mitigation: intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION). Detection: StrictMode.VmPolicy.Builder().detectImplicitUriPermissionGrant() or logcat 'Please set the grant explicitly in the app'. Current AOSP code does the implicit step in Intent.migrateExtraStreamToClipData (creates ClipData from EXTRA_STREAM and adds the flag, logging a counter when the flag was missing). Setting both clipData and the flag yourself is future-proof.

- Confidence: high · Applies to: Part B - email
- Source: https://developer.android.com/about/versions/17/behavior-changes-all

### 13. Part B: No <queries> entry is required to start Gmail; it is required only if the app wants to ask whether Gmail is installed.

Package visibility docs: an app can start another app's activity with an implicit or explicit intent regardless of whether that app is visible to it. On API 30+ the calls that are filtered are the PackageManager queries (getPackageInfo, resolveActivity, queryIntentActivities). So the reliable pattern is 'just startActivity and catch ActivityNotFoundException'. If MilO wants to show Gmail status on a diagnostics/checklist screen, add <queries><package android:name="com.google.android.gm"/></queries> (and, for the fallback, <intent><action android:name="android.intent.action.SENDTO"/><data android:scheme="mailto"/></intent>). The common-intents sample's resolveActivity() check would return null without such an entry even when Gmail is installed.

- Confidence: high · Applies to: Part B - email
- Source: https://developer.android.com/training/package-visibility/automatic

### 14. Part B: The app can never learn whether the email was actually sent.

Intent Javadoc for ACTION_SEND and ACTION_SENDTO both end with 'Output: nothing.' The only feedback Android offers is the chooser callback (Intent.createChooser with an IntentSender -> EXTRA_CHOOSER_RESULT / chosen ComponentName), which reports which app was picked, not whether the user pressed Send, saved a draft or backed out. The encyclopedia's rule (ask Shawn to confirm before marking a month Submitted) is therefore the correct design: record 'compose opened at <time>' when startActivity succeeds, and show the 'Did you send it?' confirmation when MilO's screen resumes. Certainty would require sending through the Gmail API with OAuth, which contradicts the no-accounts brief.

- Confidence: high · Applies to: Part B - email
- Source: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/content/Intent.java

### 15. Part B: The read permission granted through the intent lasts only while Gmail's activity stack is alive, so do not delete the PDF right after launching compose.

FileProvider Javadoc: 'Permissions granted in an Intent remain in effect while the stack of the receiving Activity is active. When the stack finishes, the permissions are automatically removed.' The secure-file-sharing guide also warns against Context.grantUriPermission() because it persists until explicitly revoked. Keep generated reports in cacheDir/reports and prune old ones on a later app start (for example older than 7 days) rather than immediately after startActivity.

- Confidence: high · Applies to: Part B - email
- Source: https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/core/core/src/main/java/androidx/core/content/FileProvider.java

### 16. Part B: The open-source mail app Gmail descends from reads exactly these extras, which supports the ACTION_SEND design (indicative only).

AOSP UnifiedEmail ComposeActivity.initFromExtras reads intent.getStringArrayExtra(Intent.EXTRA_EMAIL) (array form), EXTRA_CC, EXTRA_BCC, getStringExtra(EXTRA_SUBJECT) and EXTRA_TEXT for the body, parses a mailto: data URI, and initAttachmentsFromIntent reads EXTRA_STREAM (a single Uri unless the action is ACTION_SEND_MULTIPLE). Present-day Gmail is closed source, so this is supporting evidence, not proof.

- Confidence: low · Applies to: Part B - email
- Source: https://raw.githubusercontent.com/LineageOS/android_packages_apps_UnifiedEmail/cm-14.1/src/com/android/mail/compose/ComposeActivity.java

### 17. Part C: The Auto Backup cloud quota is 25 MB per app and exceeding it means nothing is backed up at all.

Auto Backup guide: 'Backup data is stored in a private folder in the user's Google Drive account, limited to 25 MB per app ... Only the most recent backup is stored.' and 'Caution: If the amount of data is over 25 MB, the system calls onQuotaExceeded() and doesn't back up data to the cloud. The system periodically checks whether the amount of data later falls under the 25 MB threshold and continues Auto Backup when it does.' BackupAgent Javadoc: 'The ongoing backup operation is halted and rolled back: any data that had been stored by a previous backup operation is still intact. Typically the quota-exceeded state will be detected before any data is actually transmitted over the network.' AOSP PerformFullTransportBackupTask does a preflight (agent.doMeasureFullBackup -> transport.checkFullBackupSize(totalSize)); on TRANSPORT_QUOTA_EXCEEDED it calls agent.doQuotaExceeded and logs 'Transport quota exceeded for package'. Net effect: no error is shown to the user, the cloud copy just stops advancing. The size measured is the total of all included files (database, -wal, shared prefs, files/, external files).

- Confidence: high · Applies to: Part C - Auto Backup
- Source: https://developer.android.com/identity/data/autobackup

### 18. Part C: MilO's raw GPS points will push a single database past 25 MB, probably within the first year, so the points must be kept out of the cloud backup set.

My estimate (not from a source): the encyclopedia specifies a point about every 5 s and 'Store the raw points'. 2 h of driving per working day is about 1,440 points/day, about 380,000 points/year; at roughly 60-80 bytes per row including the trip index that is about 25-30 MB/year, and proportionally more for longer driving days. Once over 25 MB the whole app stops backing up, including the small, valuable trips table. Options: (a) a second Room database file for raw points that is excluded from <cloud-backup> (or created under noBackupFilesDir), keeping trips/settings/event log in the backed-up database; (b) store each trip's points as one compact delta-encoded BLOB; (c) prune or down-sample points after N months. Option (a) is the least code and makes the backed-up database stay in the low single-digit MB for years. Override onQuotaExceeded in a small BackupAgent subclass, or check sizes at app start, and write a line to the in-app event log so a stalled backup is visible.

- Confidence: medium · Applies to: Part C - Auto Backup (design)
- Source: https://developer.android.com/identity/data/autobackup

### 19. Part C: On API 31+ the rules live in android:dataExtractionRules with separate <cloud-backup> and <device-transfer> sections; any <include> switches off the include-everything default.

Syntax: <data-extraction-rules><cloud-backup [disableIfNoEncryptionCapabilities="true|false"]><include domain="file|database|sharedpref|external|root|device_file|device_database|device_sharedpref|device_root" path="..."/><exclude .../></cloud-backup><device-transfer>...</device-transfer></data-extraction-rules>, referenced by <application android:dataExtractionRules="@xml/data_extraction_rules">. 'If you specify an <include> element, the system no longer includes any files by default and backs up only the files specified.' With minSdk 31 the legacy android:fullBackupContent file is ignored on every device MilO can run on ('the android:fullBackupContent attribute that points to the old config is ignored on devices running Android 12 or higher'), so only data_extraction_rules.xml is needed. Default-included: shared preferences, getFilesDir(), getDatabasePath() files, getExternalFilesDir(); always excluded: getCacheDir(), getCodeCacheDir(), getNoBackupFilesDir().

- Confidence: high · Applies to: Part C - Auto Backup
- Source: https://developer.android.com/identity/data/autobackup

### 20. Part C: To exclude one database, exclude the file AND its -wal and -shm sidecars, or (simpler) create that database under noBackupFilesDir.

Rule form: <cloud-backup><exclude domain="database" path="points.db"/><exclude domain="database" path="points.db-wal"/><exclude domain="database" path="points.db-shm"/></cloud-backup>, using only <exclude> elements so everything else stays included by default. Paths are literal file or folder names (no wildcards documented). Why the sidecars matter: SQLite says 'The WAL file is part of the persistent state of the database and should be kept with the database if the database is copied or moved' and lists 'Copying a database file without also copying its journal' and mispairing journals among ways to corrupt a database; a restored orphan -wal next to a freshly created database is exactly that mispairing. The alternative that needs no rules at all is to open the points database at File(context.noBackupFilesDir, "points.db").absolutePath, since that directory is excluded from Auto Backup by the platform. Android 12+ D2D transfer has 'a much larger data size limit of 2 GB', so the points database can still be included in <device-transfer> to follow Shawn to a new phone via cable/Wi-Fi transfer (note: for targetSdk 31+ allowBackup=false does not disable D2D on some devices, and D2D rules are only controlled by the <device-transfer> section).

- Confidence: medium · Applies to: Part C - Auto Backup
- Source: https://www.sqlite.org/wal.html

### 21. Part C: Room uses WAL by default, and Auto Backup copes with it as long as the rules do not single out only the main .db file.

Room JournalMode.AUTOMATIC resolves to WRITE_AHEAD_LOGGING unless ActivityManager.isLowRamDevice, so MilO's databases will have -wal and -shm files, and recent commits can live only in the -wal until a checkpoint (SQLite auto-checkpoints at about 1000 pages or when the last connection closes cleanly; after a process kill 'the WAL file might be retained on disk'). Auto Backup's default includes the whole databases directory, sidecars included, and 'During Auto Backup, the system shuts down the app to make sure it is no longer writing to the file system'; the agent then runs in a restricted mode where content providers are not initialised and the base Application class is used, so Room is not opened during the copy. A consistent db + wal pair is therefore captured and SQLite replays the WAL on first open after restore. The failure mode to avoid is an <include domain="database" path="milo.db"/> rule that names only the main file: the -wal is then left out and the restore loses every un-checkpointed commit. (The consistency conclusion is my reasoning from the two documents; no Android page states it in one sentence.)

- Confidence: medium · Applies to: Part C - Auto Backup
- Source: https://raw.githubusercontent.com/androidx/androidx/androidx-main/room3/room3-runtime/src/androidMain/kotlin/androidx/room3/RoomDatabase.android.kt

### 22. Part C: A scheduled backup will not kill a trip in progress, but a manual bmgr backupnow can; force-stopped apps are not backed up.

Manifest docs for android:backupInForeground: default false 'means that the OS avoids backing up the app while it's running in the foreground, such as a music app that is actively playing music using a service in the startForeground() state'. AOSP UserBackupManagerService confirms: headBusy = (privFlags & PRIVATE_FLAG_BACKUP_IN_FOREGROUND) == 0 && isAppForeground(uid), and the package is deferred ('Full backup time but ... is busy; deferring'). Leave backupInForeground at its default. The scheduled-job check does not exist on the bmgr backupnow path, and backup ends with unbindAgent(..., allowKill = true), so never run a manual backup test during a recording trip. Separately, BackupEligibilityRules.appIsStopped culls packages with FLAG_STOPPED (force-stopped, cleared, or just installed and never launched) from the full-backup queue. Scheduled backups also need: backup enabled on the device, 24 h since the last one, device idle, Wi-Fi (unless mobile-data backup is on); 'a device might never back up'.

- Confidence: high · Applies to: Part C - Auto Backup
- Source: https://developer.android.com/guide/topics/manifest/application-element

### 23. Part C: Backup and restore work for a debug build installed by Android Studio or adb; restore happens at install time, but only if the signing certificate matches and the backup is not from a newer versionCode.

Auto Backup guide: 'Data is restored whenever the app is installed, whether from the Play Store, during device setup ..., or by running adb install. The restore operation occurs after the APK is installed but before the app is available to be launched by the user.' The official test script reinstalls with 'adb install-multiple -t' (-t = test-only APKs, which is what Android Studio's Run produces). AOSP eligibility rules have no debuggable or installer check for cloud or D2D backup (debuggable only matters for the separate 'adb backup' path). Restore gates in PerformUnifiedRestoreTask: 'Signature mismatch restoring <pkg>' -> package skipped; and 'Source version X > installed version Y' -> skipped unless android:restoreAnyVersion="true". On a new phone, a dataset chosen in the setup wizard becomes the 'ancestral dataset' and a later adb/Android Studio install can restore from it; 'If the user didn't go through the device setup wizard, then the device can restore only from its own backups.' Cloud backups are end-to-end encrypted with the lock-screen PIN/pattern/password on Android 9+. Side effect to expect: the first Run on a wiped or new phone may silently restore old data before first launch.

- Confidence: high · Applies to: Part C - Auto Backup
- Source: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/services/backup/java/com/android/server/backup/restore/PerformUnifiedRestoreTask.java

### 24. Part C: Testing with bmgr - documented commands and what success/failure look like.

Cloud path: adb shell bmgr enable true; adb shell bmgr list transports; adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService; adb shell bmgr backupnow <package> (success prints 'Package <package> with result: Success'); then adb shell pm uninstall --user 0 <package> and reinstall (adb install -t, or Run from Android Studio) to trigger restore-at-install. Offline path: adb shell bmgr transport com.android.localtransport/.LocalTransport plus adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true', then backupnow/uninstall/reinstall, then switch the transport back to the GMS one. D2D path (Android 12+): settings put secure backup_enable_d2d_test_mode 1; bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport; bmgr init ...D2dTransport; backupnow; reinstall; then set test mode back to 0. Diagnostics: adb logcat tags BackupManagerService / PFTBT / Backup, and adb shell dumpsys backup. Known messages: 'Full backup not currently possible -- key/value backup not yet run?' (fix: adb shell bmgr run, retry); 'Transport rejected backup of <PACKAGE>, skipping' / 'Transport quota exceeded for package' (over 25 MB); 'Try to backup for an uninitialized backup account' (no backup account set). Prerequisite for cloud: a Google account set as backup account in Settings > Google > Backup. The bmgr tool page notes 'bmgr restore does not work for encrypted backups', so test restore by reinstalling, not with bmgr restore. The testing page's phrase '2 MB for cloud backup' is a typo for 25 MB.

- Confidence: high · Applies to: Part C - Auto Backup
- Source: https://developer.android.com/identity/data/testingbackup

### 25. Part C: Android Studio itself can take and restore a backup file of a debug build on a connected device.

Android Studio Narwhal 3 Feature Drop release notes: 'Deploy a debug version of your app to a connected device', then Run > Backup App Data (or the Running Devices toolbar, or Device Explorer > Processes) and choose Device to Device, Cloud, or Cloud (Unencrypted); the file is kept under Project > Android > Backup Files and can be restored via Run > Edit Configurations > Restore options > 'Restore app state' (optionally 'Only restore on fresh apk installation'). It honours the app's backup rules, so it is also a quick way to verify data_extraction_rules.xml. The dev machine has Android Studio 2025.3, which is newer than Narwhal 3 FD. Useful as a pre-flight snapshot before risky changes; it writes MilO data (trip addresses) to the Mac, so keep those files out of git.

- Confidence: medium · Applies to: Part C - Auto Backup / Part D
- Source: https://developer.android.com/studio/releases/past-releases/as-narwhal-3-feature-drop-release-notes

### 26. Part D: The debug keystore is generated per machine at ~/.android/debug.keystore and is not a durable identity.

App-signing guide: 'The first time you run or debug your project in Android Studio, the IDE automatically creates the debug keystore and certificate in $HOME/.android/debug.keystore'; the certificate lasts 30 years; 'the debug certificate is created by the build tools and is insecure by design'; deleting the file makes Android Studio regenerate a NEW key. A new or reinstalled Mac, a deleted ~/.android folder, or a CI runner therefore signs with a different key. Gradle task :app:signingReport shows which keystore a variant uses.

- Confidence: high · Applies to: Part D - signing
- Source: https://developer.android.com/studio/publish/app-signing

### 27. Part D: If the signing key changes, the app cannot be updated in place; the only way forward is uninstalling, which deletes all app data, and Auto Backup restore is refused too.

PackageManager: INSTALL_FAILED_UPDATE_INCOMPATIBLE is returned 'if a previously installed package of the same name has a different signature than the new package (and the old package's data was not removed)'. Android Studio then shows: 'Installation failed since the device already has an application with the same package but a different signature. In order to proceed, you have to uninstall the existing application. WARNING: Uninstalling will remove the application data! Do you want to uninstall the existing application?' The same prompt appears for a versionCode downgrade ('the device already has a newer version of this application'), so versionCode must never go down. After such an uninstall, the Auto Backup copy cannot come back either, because restore checks signatures ('Signature mismatch restoring'). A lost keystore therefore defeats both the in-place data and the cloud backup at once; only a manual export file survives, because it is not tied to the signature.

- Confidence: high · Applies to: Part D - signing
- Source: https://raw.githubusercontent.com/JetBrains/android/master/android/resources/messages/AndroidBundle.properties

### 28. Part D: Simplest safe arrangement - one dedicated keystore outside the repo, used for every build that goes on the phone, with credentials in a git-ignored properties file.

Create once: keytool -genkey -v -keystore milo.jks -keyalg RSA -keysize 2048 -validity 10000 -alias milo (documented form; Google advises at least 25 years validity, and 10,000 days is about 27 years). Keep milo.jks outside the working tree (for example ~/keys/milo/). Follow the documented keystore.properties pattern (storeFile, storePassword, keyAlias, keyPassword) loaded in build.gradle.kts, with keystore.properties in .gitignore ('Be sure to keep the keystore.properties file secure. This may include removing it from your source control system'). Assign that signingConfig to BOTH the debug and release build types so whatever variant is installed has the same certificate. Guard rails: if keystore.properties is missing and the build is not on CI, fail the build with a clear message instead of silently falling back to the machine's debug key (otherwise a new Mac leads straight to the uninstall prompt); on CI (GitHub Actions) fall back to the ephemeral debug key because CI artifacts are never installed on the phone. Do this before the first install that will hold real trips; switching keys later costs an export, uninstall and import. Back up the .jks and its passwords somewhere other than the Mac ('Keep the keystore file containing your private key in a safe, secure place'). Never commit the keystore or passwords, which also matches CLAUDE.md's secrets rule.

- Confidence: high · Applies to: Part D - signing
- Source: https://developer.android.com/studio/publish/app-signing

### 29. Part E: Copying the Room database file is only safe from a consistent, single-file snapshot; VACUUM INTO is the cleanest way to get one.

SQLite: a file copy is safe only 'as long as there are no transactions in progress while the copy is taking place', and the WAL 'should be kept with the database if the database is copied'. Three workable methods: (1) VACUUM INTO '<cacheDir>/export.db' - 'an alternative to the backup API for generating backup copies of a live database', 'a consistent snapshot of the original database', target must not exist or be empty, cannot run inside an open transaction; added in SQLite 3.27.0 (2019-02-07) and API 31 ships SQLite 3.32, so it works on minSdk 31 with either the framework or bundled driver. (2) PRAGMA wal_checkpoint(TRUNCATE) and verify the first result column (busy) is 0 before copying the main file - with Room's reader pool it can return busy=1. (3) close() the RoomDatabase (last connection closing checkpoints and deletes the WAL), copy, reopen. With Room 2.8.x use db.openHelper.writableDatabase; with Room 3.0.x (androidx.room3, no SupportSQLite types) use db.useWriterConnection { it.usePrepared("...") { stmt -> stmt.step() } } outside any transaction block. Then stream the single snapshot file to the SAF Uri.

- Confidence: high · Applies to: Part E - export/import
- Source: https://www.sqlite.org/lang_vacuum.html

### 30. Part E: Importing a database FILE is fragile across schema versions; a JSON export with its own format version is the safer portable format.

Importing a file means closing Room, replacing the database, and deleting the old -wal/-shm (SQLite: 'Overwriting a database file with another without also deleting any hot journal associated with the original database' corrupts). Room then treats the file by its stored version: older version -> every Migration from that version must still exist or Room throws IllegalStateException; fallbackToDestructiveMigration 'permanently deletes all data'; a file from a NEWER app version is a downgrade, which either crashes or (with fallbackToDestructiveMigrationOnDowngrade) wipes. There is also no chance to validate rows before they become the live database. A JSON document with { formatVersion, exportedAt, appVersionCode, schemaVersion, settings, trips[], points[]/per-trip } is decoupled from the table layout: the importer can reject an unknown formatVersion with a clear message, validate everything first, and insert inside one transaction, and it is human-readable and diffable. Cost: an explicit mapper that must be updated (and round-trip tested) whenever the schema changes. Recommendation: JSON as the canonical export/import (kotlinx.serialization, which is not signature- or Room-version-dependent), optionally plus a raw VACUUM INTO snapshot labelled 'same app version only' for forensic use. Raw GPS points can make the JSON large; write it streamed, and consider a ZIP with points in a separate entry or an 'include GPS points' toggle.

- Confidence: medium · Applies to: Part E - export/import
- Source: https://developer.android.com/training/data-storage/room/migrating-db-versions

### 31. Part E: The Storage Access Framework needs no permissions; ACTION_CREATE_DOCUMENT cannot overwrite, and the I/O belongs on a background thread.

Export: ACTION_CREATE_DOCUMENT with CATEGORY_OPENABLE, a MIME type and EXTRA_TITLE (for example milo-export-2026-10-03.json); in Compose use rememberLauncherForActivityResult with ActivityResultContracts.CreateDocument(mime). Import: ACTION_OPEN_DOCUMENT / ActivityResultContracts.OpenDocument. 'this mechanism doesn't require any system permissions'. 'ACTION_CREATE_DOCUMENT cannot overwrite an existing file. If your app tries to save a file with the same name, the system appends a number in parentheses'. Read/write through contentResolver.openOutputStream(uri) / openInputStream(uri) or openFileDescriptor; the guide notes 'You should complete this operation on a background thread, not the UI thread.' The picker lets Shawn save straight into Google Drive or Downloads, which gives an off-phone copy that is independent of the signing key and the 25 MB quota. No persistable permission is needed for one-shot export/import.

- Confidence: high · Applies to: Part E - export/import
- Source: https://developer.android.com/training/data-storage/shared/documents-files

### 32. Part E/C: Room has two stable lines today - 2.8.5 and 3.0.3 (new androidx.room3 package) - and the choice changes how checkpoint/VACUUM statements are issued.

Release pages show Room 2.8.5 stable (9 Sep 2026) and Room 3.0.3 stable (9 Sep 2026; 3.1.0-alpha01 in alpha). Room 3 moves to androidx.room3:room3-runtime, removes all SupportSQLite types, is KSP-only and coroutine-first (useWriterConnection / immediateTransaction; PooledConnection.usePrepared(sql) { SQLiteStatement -> }), with BundledSQLiteDriver recommended and a room3-sqlite-wrapper compatibility artifact. CLAUDE.md's 'latest stable' rule points at 3.0.3, but whichever line the architecture research picks, the export code must use that line's API. With BundledSQLiteDriver the SQLite version no longer depends on the phone's Android version (framework SQLite is 3.32 on API 31-33, 3.39/3.42 on 34, 3.44 on 35, 3.50 on 36.1/37).

- Confidence: high · Applies to: Part E - export/import
- Source: https://developer.android.com/jetpack/androidx/releases/room3

### 33. Part F: Exact alarms need a permission that is denied by default on the target phone (Android 14), and a monthly reminder does not need exact timing.

Android 12: targeting 31+ requires SCHEDULE_EXACT_ALARM for setExact / setExactAndAllowWhileIdle / setAlarmClock, else SecurityException. Android 13: USE_EXACT_ALARM added (install-time, not revocable, 'Limited use cases', intended for alarm-clock/calendar apps under Play policy). Android 14: SCHEDULE_EXACT_ALARM 'is no longer being pre-granted to most newly installed apps targeting Android 13 and higher (will be set to denied by default)' - the user must grant 'Alarms & reminders' via Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, the app must check canScheduleExactAlarms() and listen for ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED; apps on the battery-optimisation allowlist are exempt. No further exact-alarm changes are listed in the Android 15, 16 or 17 behaviour-change pages I opened for 17 (15/16 not re-checked page by page). Google's own table says for 'User-specified action after specific time' use inexact set(), and for scheduled background work use WorkManager. Inexact alarms need no permission: set()/setAndAllowWhileIdle() never fire early and on Android 12+ fire 'within one hour of the supplied trigger time, unless any battery-saving restrictions are in effect'; setWindow has a 10-minute minimum window. One-hour accuracy is ample for 'submit last month's mileage'.

- Confidence: high · Applies to: Part F - reminder
- Source: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms

### 34. Part F: AlarmManager alarms are lost on reboot and on force-stop (but survive an app update), so an alarm-based reminder needs re-arming code.

Docs: 'By default, all alarms are canceled when a device shuts down'; re-arm from a RECEIVE_BOOT_COMPLETED receiver. AOSP AlarmManagerService: ACTION_PACKAGE_RESTARTED (force-stop) removes the package's alarms, whereas ACTION_PACKAGE_REMOVED with EXTRA_REPLACING returns early ('This package is being updated; don't kill its alarms'). Android 15+: entering the stopped state also cancels all pending intents, and 'When the user's actions remove the app from the stopped state, the ACTION_BOOT_COMPLETED broadcast is delivered to the app providing an opportunity to re-register any pending intents.' An alarm design therefore needs: schedule on settings change, re-arm on BOOT_COMPLETED, on app start, after each firing (next month), and ideally on time/timezone change. Unverified but likely relevant: Android Studio's Run force-stops the app before reinstalling, and Xiaomi HyperOS can stop apps without Autostart, both of which would clear alarms until the app next runs.

- Confidence: high · Applies to: Part F - reminder
- Source: https://developer.android.com/develop/background-work/services/alarms/schedule

### 35. Part F: WorkManager persists work across reboots by itself, cannot express 'monthly on day N' directly, and is the simpler fit if the reminder is made state-based.

Docs: 'Scheduled work is stored in an internally managed SQLite database and WorkManager takes care of ensuring that this work persists and is rescheduled across device reboots.' Periodic minimum is 15 minutes and timing is inexact ('The exact time that the worker is going to be executed also depends on the constraints ... and on system optimizations'); there is no calendar-month period. Two patterns: (1) recommended - one unique PeriodicWorkRequest (enqueueUniquePeriodicWork, policy KEEP/UPDATE) running about daily whose worker asks 'is today on or after the reminder day (clamped to the month's last day for 29-31), is the previous month not marked Submitted, and have I not already notified for it?' and posts the notification if so. This self-heals after reboots, a phone that was off on the 1st, a changed reminder day, and time-zone changes, with no date arithmetic for scheduling and no extra receiver. Since WorkManager 2.9.0, setNextScheduleTimeOverride gives 'Precise scheduling of periodic workers ... without drift' (use with ExistingPeriodicWorkPolicy.UPDATE) to aim the daily run at a chosen local hour so the notification does not arrive at 3 am. (2) a OneTimeWorkRequest with setInitialDelay to the next reminder date that re-enqueues itself - works but has the same re-arm fragility as alarms. Latest stable WorkManager is 2.12.0 (23 Sep 2026, minSdk 24). WorkManager initialises through a ContentProvider, which is not started in backup 'restricted mode', so it does not interfere with Auto Backup.

- Confidence: high · Applies to: Part F - reminder
- Source: https://developer.android.com/develop/background-work/background-tasks/persistent

### 36. Part F: The reminder needs the POST_NOTIFICATIONS runtime permission on Android 13+, which MilO must request anyway.

On Android 13+ notifications are off by default for a new install; the app declares android.permission.POST_NOTIFICATIONS and requests it at a sensible moment. If denied, foreground-service notices are still visible in the Task Manager but not in the notification drawer, and ordinary notifications such as the reminder are not shown at all. Call NotificationManagerCompat.areNotificationsEnabled() in the worker and record a line in the event log when the reminder could not be shown. Use a dedicated notification channel for reminders, separate from the ongoing 'Trip in progress' channel, so Shawn can tune them independently.

- Confidence: high · Applies to: Part F - reminder
- Source: https://developer.android.com/develop/ui/views/notifications/notification-permission

### 37. Platform baseline: Android 17 (API level 37) is the latest stable release, so 'targetSdk = latest stable' means 37.

Android Developers Blog 'Android 17 is here', posted 16 June 2026: 'Today we're releasing Android 17 and making it available on most supported Pixel devices', source pushed to AOSP. The developer.android.com Android 17 pages already list QPR betas. FINDINGS_LOG records only SDK platforms android-36 and android-36.1 on the Mac, so the API 37 platform (and an AGP/Android Studio version that supports compileSdk 37) must be installed before targeting it. Relevance to this topic: the Android 17 page is where the ACTION_SEND implicit-URI-grant deprecation is documented; I found no Android 17 changes to AlarmManager, Auto Backup, SAF or notifications in the two behaviour-change pages.

- Confidence: high · Applies to: All parts
- Source: https://android-developers.googleblog.com/2026/06/Android-17.html

## Recommendations

- Split storage by backup value: keep trips, settings, submission status and the event log in the main Room database (cloud-backed), and put raw GPS points in a second database file that is excluded from <cloud-backup> (exclude the .db, .db-wal and .db-shm, or simply create it under noBackupFilesDir) but included in <device-transfer>. This keeps the backed-up set far below the 25 MB all-or-nothing cap.
- Write res/xml/data_extraction_rules.xml with exclude-only rules (no <include> elements) and leave android:backupInForeground at its default (false) so a scheduled backup never kills a recording trip. Do not add a legacy fullBackupContent file; minSdk 31 never reads it.
- Make backup health visible: subclass BackupAgent only to override onQuotaExceeded (write to the in-app event log), and show 'backed-up data size vs 25 MB' and 'last manual export date' on the diagnostics screen.
- Before the first install that will hold real trips, create a dedicated signing keystore outside the repo (keytool -genkey -v -keystore milo.jks -keyalg RSA -keysize 2048 -validity 10000 -alias milo), reference it through a git-ignored keystore.properties, and use the same signingConfig for debug and release. Fail the local build loudly if the properties file is missing; let CI fall back to the ephemeral debug key because CI builds are never installed.
- Never decrease versionCode, and treat Android Studio's 'uninstall the existing application?' prompt as a stop sign: export data first.
- Treat the manual export as the primary disaster-recovery path (it is the only one independent of the signing key, the Google account and the quota). Implement it as a versioned JSON document via ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT, validated fully before a single-transaction import. Offer a raw VACUUM INTO snapshot only as an optional 'same app version' extra.
- Generate the PDF on one background coroutine with a two-pass design: a pure-Kotlin layout/pagination pass (unit-testable) and a Canvas draw pass. Page 612 x 792 pt, own margin constants, no setContentRect, StaticLayout for wrapped From/To addresses, explicit 0.5-0.75 pt rules, 'Page n of N' footer, write to cacheDir/reports via temp file then rename.
- Open Gmail with ACTION_SEND + type application/pdf + EXTRA_EMAIL as arrayOf(address) + EXTRA_SUBJECT + EXTRA_TEXT + EXTRA_STREAM + ClipData + FLAG_GRANT_READ_URI_PERMISSION + setPackage("com.google.android.gm"), inside try/catch ActivityNotFoundException with a fallback to the same intent using a mailto selector or a chooser. Add <queries><package android:name="com.google.android.gm"/></queries> only if the checklist screen should report whether Gmail is installed.
- Enable StrictMode.VmPolicy detectImplicitUriPermissionGrant() in debug builds to prove the explicit URI grant is in place ahead of the Android 18 removal.
- Keep the encyclopedia's rule: on return from Gmail, ask 'Did you send it?' before marking the month Submitted; store 'compose opened' time separately from 'submitted' date.
- Implement the monthly reminder as a unique daily WorkManager periodic job with state-based logic (reminder day reached, previous month not submitted, not yet notified), aimed at a daytime hour with setNextScheduleTimeOverride, and run the same check on app start. Do not declare SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM. Clamp reminder days 29-31 to the last day of shorter months.
- During phase 4, run the documented bmgr cycle once on the POCO (bmgr backupnow, uninstall, reinstall with the same key, verify data) while no trip is recording, and also take an Android Studio 'Backup App Data' file as a second proof that the rules file does what is intended.
- Verify Gmail's actual pre-fill behaviour (recipient, subject, body, attachment) on the POCO in phase 3; Google publishes no contract for it, so this is the one item in this topic that only a device test can confirm.

## Questions raised for the owner

- About how many hours a working day is the truck driven, and for how long do you want the raw GPS breadcrumb points kept (forever, or is trip distance plus start/end address enough after a few months)? This decides how the 25 MB cloud-backup limit is handled.
- On the POCO X5, is Google backup switched on (Settings > Google > Backup) with your Google account, and does the phone have a screen lock? Without both, Android Auto Backup never runs and manual export becomes the only backup.
- Where do you want the signing keystore and its passwords backed up outside the Mac (password manager, iCloud Drive, USB stick)? If that file is lost, the app can only be reinstalled by wiping its data.
- Where should manual export files go and how often (for example Google Drive via the file picker, monthly right after submitting)? Should the app nudge you when the last export is older than some number of days?
- Should a manual export include the raw GPS points (larger file, lets distance be recalculated after a restore) or only trips and settings?
- Is there more than one Gmail account on the phone? MilO can pre-fill the recipient, subject and attachment but cannot choose the From account; Gmail uses its own default.
- For the monthly reminder: what time of day should it arrive, and should it repeat daily until the month is marked Submitted or appear only once?
