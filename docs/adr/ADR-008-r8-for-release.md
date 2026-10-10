# ADR-008: The release build goes through R8, and MilO's own names stay readable
Date: 2026-10-10
Status: Accepted (built, and run on an emulator; not yet on the phone, and not yet read by the Play Console)

## Context

MilO's release build, the one on the phone, on GitHub and on Google Play, was the code exactly as the compiler wrote it: every class of every library in full, under its own name. R8 had never been switched on. R8 is the tool in Android's build that does three things to an app's code before it is packed: it removes what nothing uses ("shrinking"), it rewrites what is left to be smaller and faster ("optimisation"), and it gives classes, methods and fields short meaningless names ("obfuscation").

On 2026-10-10 the Play Console flagged the bundle of 0.3.0:

> "DEX code optimization is below our threshold. Improve your percentages in the following categories: Obfuscation (1%). Percentages under 25% in any category of your app may impact your visibility and publishing capabilities on Google Play. Fix by Feb 2027."

The same page said "No R8 metadata included. Use R8 to get the best performance" and "Total uncompressed DEX size 32.8 MB". (DEX is the form an app's code has on Android.) Shawn: "we should fix this".

Two things stood against simply switching it on.

- **This is the build that records Shawn's real trips.** A fault that exists only in the build R8 has rewritten would lose drives, and nothing finds it but running that build. The unit tests and the debug build do not go through R8.
- **MilO writes its own diagnostics.** A failure's stack trace goes into the event log, a crash file stores the exception's class, and a log line prints a class name ("handling Trigger"). Shawn reads that Log on his phone and sends it on. Names like `a.b.c` there would be unreadable.

## Decision

1. **The release build goes through R8: shrinking, optimisation, obfuscation, and the removal of resources nothing uses.** The debug build does not. One build of the app for both channels, as before (ADR-004): the APK for GitHub and the bundle for Google Play come from the same run of R8.
2. **MilO's own names stay as written.** `app/proguard-rules.pro` keeps the name of every class, method and field under `com.shawnkowalchuk.milo`, and nothing else of them: what MilO does not use is still removed. The libraries (AndroidX, Compose, Kotlin, Play services, Room, the Car App Library), which are about two thirds of the code that is left, are renamed and rewritten as usual.
3. **MilO's own code keeps its shape too.** The rule is `-keepnames`, which also stops R8 from folding one of MilO's methods into another or merging two of its classes. So a stack trace has one line for each call, as in the debug build, and the code that records a trip runs as it was written. The other choice was measured: letting R8 rewrite MilO's code as well took 0.5 MB more off the APK and folded away 35 % of MilO's methods (3,768 of 10,709).
4. **An exception keeps its name, whoever made it.** One more rule keeps the class name of every exception, the libraries' included, because the first line of every stack trace is that name. It costs next to nothing: about 60 classes of the libraries keep their names for it.
5. **Each rule in the file says why it is there.** Android's own rules (`proguard-android-optimize.txt`) and the rules each library ships inside itself do the rest. No blanket `-keep`, and no `-dontwarn` for something not understood. R8 gave no warning.
6. **Every release keeps its mapping file.** R8 writes `mapping.txt`: for every renamed thing, what it was called, and for every line of every stack trace, the real file and line. The bundle for Google Play carries it inside, so the Play Console has it without an upload. For GitHub it is attached to the release, packed, beside the APK (README, "Making a release"). It is never committed: it is 53 MB, and it is build output.

## Consequences

**Better**

- **Google's figure.** R8 writes its own scores into the bundle, which is what the Console asked for ("R8 metadata"). For this build: 57.2 % of classes, methods and fields free to be renamed, 56.7 % free to be rewritten, 93.5 % free to be removed. Counted from the app itself: 60 % of the classes that are left have a new name (3,914 of 6,499), 59 % of the fields, 36 % of the methods. All are above Google's 25 %. **How the Console counts is not published,** and its figure for this build has not been seen: it is read after the first upload.
- **The download.** The APK on GitHub goes from 39.9 MB to 12.1 MB. The code in it goes from 32.8 MB to 5.3 MB. The bundle goes from 15.5 MB to 10.1 MB, of which 3.9 MB is the mapping file, which Google keeps and does not send to phones; what a phone downloads from Google Play is, by the bundle's own contents, about 4 MB where it was about 13 MB.
- **The Console's second warning ends too:** the bundle now holds the file that turns a renamed stack trace back.

**Worse**

- **A fault can now exist in the release build alone.** R8 removes what it cannot see being used, and code that is reached by name at run time (through reflection) is what it cannot see. The libraries' own rules cover their cases, and everything MilO does was run on an emulator in the minified build (FINDINGS_LOG, 2026-10-10). But from now on a green CI says nothing about the release build: CI builds and tests the debug build. **So a release is installed on the phone and looked over before it is published** (README, "Making a release"), and a change that leans on names or reflection is tried in the release build, not only in the debug one.
- **A stack trace from a release no longer says where in the file.** R8 replaces the file name in every line of a trace with the id of the build's mapping file, and the line number with a number of its own, for MilO's classes as much as for the libraries'. The setting that used to keep both (`-keepattributes SourceFile,LineNumberTable`) now only lets them be found again. So a line of a trace reads `at com.shawnkowalchuk.milo.data.trip.TripRepository.setCategoryByHand(r8-map-id-5288…:124)`: the class and the method are plain, the file and the line are not. With that release's `mapping.txt` and one command the trace comes back whole, `TripRepository.kt:229` (README, "Reading a stack trace from a release"; the example is from a throwaway build with a failure put in on purpose). R8 has no supported switch for this.
- **A library's lines in a trace are short names** (`at mz1.k(…)`) until the trace is turned back the same way.
- **Without a release's mapping file, its stack traces stay half readable for good.** R8 gave the same result, byte for byte, each of the three times it was run on the same code (2026-10-10), so a lost file could be made again from the release's tag with the same versions of everything. That is a rescue, not a plan.
- **A release build takes longer to make:** 34 to 40 seconds on the Mac where it took 28, with everything made afresh.

**Locked in**

- Nothing. Taking the three lines out of `app/build.gradle.kts` gives the old build back. A release without R8 would again be flagged by Google Play.

**Left open, and Shawn's to decide if it comes up**

- **If the Console still shows a low figure for obfuscation,** the next step is to let R8 rename MilO's own methods and fields and keep only its class names. Measured: R8's score for renaming goes from 57 % to 88 %, and the methods with a new name from 36 % to 64 %. The price: a trace from MilO's own code then names the class and nothing more until it is turned back with the mapping file. That trades the Log's readability for Google's figure, so it is not taken in advance.

## How it was decided

Four ways of keeping MilO's names were built and measured on 2026-10-10 (FINDINGS_LOG has the table): names of everything and the code's shape (taken); names of everything, shape not kept; class names only; nothing kept. The first was taken because the names are kept for the sake of the Log, and a Log whose traces miss a third of MilO's calls serves that purpose badly; and because the code least proven under R8, MilO's own, is then the code R8 changes least.
