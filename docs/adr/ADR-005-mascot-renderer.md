# ADR-005: The mascot is a 3D model, drawn by Filament
Date: 2026-10-09
Status: Accepted

## Context

MilO has a mascot since 2026-10-09: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. It was built in Blender from a picture Shawn brought, as a model with a skeleton and seven clips (Idle, Wave, ThumbsUp, Walk, Celebrate, and one for each gauge needle), and it exports to one `.glb` file (`design/mascot/`).

Shawn wants it in the app. Asked which clips, he was sure of one: "the wave one when the app starts up". Asked where, he chose "Greeting over Home": on a fresh start the mascot appears large over a dimmed Home, waves for two seconds and fades away. Phone only; Android Auto shows Google's templates and can draw neither a model nor an animation.

Until now the app drew everything with Jetpack Compose and carried no native code of its own and no 3D drawing. Three ways to show the wave were put to Shawn, with their cost:

1. **Filament**, Google's own renderer, with its glTF loader gltfio. It plays the `.glb` as it is, so every other clip, and needles that follow the real speed, stay possible.
2. **SceneView**, a community library around the same Filament. Less code to write. Its release 4.53.0 pins an older Filament (1.72.1) and brings seven more libraries along, one of them for networking (Fuel 2.3.1), into an app with no internet permission.
3. **No engine: the wave rendered in Blender as an animated picture,** played by Android itself. No dependency, about half a megabyte, and it looks as Blender draws it. It cannot react to anything, and every further clip is another file. While the wave is the only use, this is where ENGINEERING_STANDARDS §2 points ("can I write this in <50 lines myself?").

## Decision

**Filament, taken directly** (Shawn: "Filament 3D", having asked for the dependency before the options were laid out). Two artifacts of one version, pinned in `gradle/libs.versions.toml`:

- `com.google.android.filament:filament-android`, the renderer;
- `com.google.android.filament:gltfio-android`, which reads a `.glb` and plays the clips in it.

Version 1.77.3, the newest on Maven Central on 2026-10-09 (released the day before; Filament makes no pre-releases there). **Not taken: `filament-utils-android`,** a layer of conveniences (a ready-made viewer with a camera that follows the finger, a loader for light maps) that MilO's one small view does not need.

How it is used:

- **One component draws the mascot,** `Mascot` in `core/designsystem/component/`, with `MascotStage` holding everything of Filament's. Nothing else in the app touches Filament. The engine is built when the mascot appears and given back when it leaves: it does not live while MilO shows its ordinary screens.
- **The app carries its own copy of the model with only the clips its code plays** (`app/src/main/assets/mascot/milo_wave.glb`, 1.16 MB, the Wave clip). It is written by `design/mascot/scripts/export_milo.py` and committed; the build does not run Blender. `MascotModelTest` holds the file to what the code expects.
- **A see-through background** in a `TextureView`, so the mascot lies over the screen behind it.
- **Light without a light map.** Two lights as far away as the sun and one even light from all sides. Nothing is mirrored, so the gauges' rims, chrome in Blender, are drawn dark.

## Consequences

**What it costs, measured on 2026-10-09 on a debug build:**

- The APK that holds all four processor types grows from 46.6 MB to 70.1 MB. The native libraries in it go from about 1.2 MB to 7.0 MB for `arm64-v8a` (5.5 MB for `armeabi-v7a`, 7.5 MB each for `x86` and `x86_64`), and Android stores them in the APK uncompressed. **This is the file the website's download button gives.** Before the measurement Shawn was told "+13 MB", from the size of the library files as Maven holds them, which are compressed; the true figure is about 23.5 MB.
- From Google Play a phone gets the libraries for its own processor only: about 6 MB more on a current phone.
- Leaving out the two `x86` kinds, which only emulators and a few Chromebooks use, would take about 13 MB off the website's APK. Not done here: it is a build decision of its own, and the emulator on Shawn's PC needs them.

**Native code that fails ends the app, and nothing in Kotlin can catch it.** Seen on the first day: on the emulator's software graphics (SwiftShader, OpenGL ES 3.0) Filament could not link its shader for a model with a skeleton ("Vertex shader active uniforms exceed GL_MAX_VERTEX_UNIFORM_VECTORS (256)") and the process died. Phones have graphics chips with far higher limits, and on the PC's own graphics card the same build ran. But MilO greets when it is opened, so a phone on which the drawing failed would lose MilO at every start. Hence **the greeting's safety catch** (`feature/greeting/GreetingViewModel`): every greeting is counted before the drawing begins and the count is cleared when the mascot has been seen; after two in a row that never showed it, MilO stops greeting on that phone. The app can die of its greeting twice and not a third time.

**The drawing has run on an emulator only,** on the PC's graphics card through the emulator's translator. It has not run on a phone (device checks MG-1 to MG-9).

**Filament is released about once a week.** Dependabot's weekly pull request, which groups minor and patch updates, will carry one of its versions nearly every time. Each needs the greeting looked at on the phone, not only a green build: a renderer can change what is drawn without changing a line that compiles.

**The app now has a second way of drawing.** Everything else is Compose. A further use of the mascot (the other clips are in `design/mascot/export/milo_mascot.glb`, 1.69 MB with all seven) goes through `Mascot` and adds its clip to the app's copy of the model; it does not start a second engine or a second component.

**Going back is cheap.** The dependency is two lines, the drawing three files, and option 3 above would replace them with a picture file and no library.
