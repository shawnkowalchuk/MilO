# MilO's own rules for R8, the tool that shrinks the release build (docs/adr/ADR-008-r8-for-release.md).
#
# R8 does three things to the release build, and to that build only: it removes code nothing
# uses, it rewrites what is left to be smaller and faster, and it gives classes, methods and
# fields short meaningless names ("obfuscation"). The debug build is not touched.
#
# These rules come on top of two other sets, which are not repeated here:
#   - Android's own, "proguard-android-optimize.txt", named in app/build.gradle.kts;
#   - the rules each library ships inside itself: Room, DataStore, kotlinx.serialization,
#     the Car App Library, Play services, Compose, coroutines. The build writes all of them
#     out, with where each came from, to app/build/outputs/mapping/release/configuration.txt.
# The classes the manifest names (the activity, the services, the receivers, the backup agent)
# are kept by rules the build makes from the manifest itself.
#
# Every rule below says why it is here. Do not add a rule without a reason written next to it,
# and never a blanket "-keep class **" or a "-dontwarn" for something that is not understood:
# either would hide the very fault R8 exists to report.

# --- What a stack trace needs --------------------------------------------------------------
# MilO writes its own diagnostics: a failure's stack trace goes into the event log, and a crash
# into a crash file. Without these two attributes a trace would carry no place in the code at
# all. With them, R8 still replaces the file name with the id of this build's mapping file
# ("r8-map-id-...") and the line with a number of its own, and writes the real file and line of
# each into mapping.txt. So the names in a trace can be read as they are, and the file and line
# need that release's mapping.txt (README, "Reading a stack trace from a release").
-keepattributes SourceFile,LineNumberTable

# --- MilO's own names stay as written ------------------------------------------------------
# The event log prints class names ("handling Trigger"), the crash file stores the exception's
# class, and every stack trace names MilO's classes and methods. Shawn reads that log on his
# phone and sends it on, so those names must be the ones in the source code.
#
# "-keepnames" keeps a name only: code that nothing uses is still removed. It also leaves
# MilO's own classes and methods standing as they were written (R8 does not fold one of them
# into another), so a trace has one line for each call, as in the debug build. The libraries,
# which are about two thirds of the code that remains, are renamed and rewritten as usual.
#
# It also keeps what is stored or handed over by name from changing between releases: the
# screens on the navigation back stack are saved under their class names (Navigation 3 looks
# the class up again by that name when Android brings MilO back).
-keepnames class com.shawnkowalchuk.milo.** { *; }

# --- An exception keeps its name, whoever made it -------------------------------------------
# The first line of a stack trace is the exception's class and its message, and the crash file
# stores that class as its summary. A library's exception with a short meaningless name
# ("xf: Unexpected JSON token") could not be read without the mapping file; with this rule it
# says what it is ("kotlinx.serialization.json.internal.JsonDecodingException: ..."). The name
# only: an exception class nothing uses is still removed.
-keepnames class * extends java.lang.Throwable
