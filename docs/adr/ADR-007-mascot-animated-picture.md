# ADR-007: The mascot is two pictures, drawn beforehand and read by Android
Date: 2026-10-09
Status: Accepted. Supersedes ADR-005.

## Context

ADR-005, the same day, put Google's 3D engine Filament into the app to draw the mascot's wave from its model. That evening the cost was measured on the 0.3.0 build:

- About 6 MB more for every phone that installs from Google Play: two native libraries, `libfilament-jni.so` (2.8 MB) and `libgltfio-jni.so` (3.0 MB) for a current phone's processor.
- The website's APK was 64 MB where it had been 39 MB. The bundle for Google Play was 25 MB where it had been 15 MB.
- It was the app's first native code of its own, with a safety catch built around it in case it ended the app.

Shawn, that evening: "i wonder is there a better way to add the animations to the app without a bloated bundle. it seems frivilous and bloat ware. maybe a transparent animation?"

He was shown the cost and four ways on: switch to a transparent animated picture before 0.3.0; ship 0.3.0 as it was and switch after; keep 3D and trim it; remove the greeting. He chose "Switch before 0.3.0". That is option 3 of ADR-005, which that record had called cheap to go back to.

A few minutes later he asked for a new greeting: "can we still modify the wave animation. i was thinking it would be cool if the mascot walked from the right side of the screen to the center at the bottom of the screen. walking just above the bottom navigation than turned and waved".

## Decision

**The mascot is drawn once, in Blender, and the app carries the result as two pictures.** Android's own decoder reads them (`ImageDecoder` and `AnimatedImageDrawable`, in Android since version 9). Filament and gltfio are out of the build, with everything that was there only for them: the stage that held the engine, the model in the app's assets and its test, and the safety catch with its stored count.

**The greeting is the one Shawn asked for:**

1. The mascot comes in from beyond the right edge of the screen and walks to the left, its feet just above the bottom bar, to the middle of the screen's width.
2. There it turns to face the viewer and waves once.
3. It fades away.

**The two pictures** (`app/src/main/res/drawable-nodpi/`), both WebP with a see-through background. A frame is 600 by 420 pixels and is shown for 42 ms, 24 frames a second:

| Picture | What it is | Frames | Who plays it | Size |
|---|---|---|---|---|
| `mascot_walk.webp` | a still picture, 2400 by 1260 pixels: the frames side by side, four across and three down | 12: one cycle of the walk, two steps | the app, by its own clock, cycle after cycle | 98 KB |
| `mascot_turn_and_wave.webp` | an animated picture | 60: the turn in 11, then the Wave clip | Android, once; it keeps its last frame | 782 KB |

Together 880 KB. They are drawn by the studio's camera under the studio's lights, by `design/mascot/scripts/render_greeting.py`, and packed by `pack_greeting.py`. The files are committed; the build does not run Blender.

**How the two become one greeting:**

- The walk's frames show the mascot walking on the spot. For every drawing of the screen the app works out which frame is due, draws that frame, and puts it a twelfth of the stride to the left of the frame before it: 36.9 dp for each cycle. That is how far the feet step back in the frames, so a foot that is on the ground stays where it is on the screen.
- **The step and the place come from one number.** A phone that draws too slowly leaves frames out. It cannot show the mascot in one place with the step of another, and the walk ends when the clock says so.
- The walk is a whole number of cycles. When its last frame has had its 42 ms, the second picture takes its place in the same drawing of the screen: its first frame is the frame the walk would have shown next. Every frame is the same size and from the same camera, so nothing jumps.
- The turn and wave is an animated picture because the mascot stands where he is in it: nothing has to follow its frames. Android says when it shows its last one.

**Why the walk is not an animated picture too.** It was, in the first build of this change. Android plays an animated picture by its own clock and does not say which frame is showing, so the app moved the mascot by another clock. On the emulator the picture fell behind: in five recordings the mascot reached the middle and walked on the spot there for 0.10 to 0.54 seconds, with his feet sliding, before he turned. Found by the check of this change, before it was merged.

**Decided with it. Shawn was not asked these; each is a number in one place:**

- **The mascot stands 150 dp high.** The picture has 2.5 pixels to the dp.
- **It walks turned 55 degrees from the viewer.** Further round, its face is lost: the body is a slab, and the M and the dials are then seen from the side.
- **It takes its steps twice as fast as the Walk clip.** Every second frame of the clip is drawn.
- **It turns with a small hop,** in under half a second, so that its soles do not grind round on the ground.
- **While it walks it moves with its frames,** 24 times a second, and the dials' black faces are drawn without their shine. Both are said under Consequences.
- **It takes nothing from the screen under it.** No press, no Back and no screen reader meets it, and the screen is not dimmed. So there is no "tap to skip" any more: there is nothing to skip past.
- **When MilO greets has not changed** (APP_ENCYCLOPEDIA, The greeting).

## Consequences

**What was measured, on release builds of 0.3.0 made the same way:**

| | With Filament | With the pictures |
|---|---|---|
| The website's APK | 64 MB | 39.9 MB |
| The bundle for Google Play | 25 MB | 15.5 MB |
| The app's own native libraries | Filament and gltfio, 5.8 MB for a current phone | none |
| What the mascot adds | the two libraries and a 1.16 MB model | 0.88 MB of pictures |

The native libraries left in the APK are AndroidX's, as before the mascot: the database's (1.3 MB for a current phone) and two of 10 KB each.

**What it costs:**

- **The mascot cannot react to anything.** A picture plays as it was drawn. Needles that follow the truck's real speed would need the 3D engine back.
- **Every further clip is another picture of about this size:** about 13 KB for each frame, so a third of a megabyte for each second.
- **The walk's sheet takes about 12 MB of the phone's graphics memory** while the greeting is on the screen (2400 by 1260 pixels, four bytes each). It is let go when the greeting is over.
- **The look is fixed when the pictures are drawn.** Another size, angle or pace means drawing them again in Blender (a minute and a half) and committing the new files.
- **The walk cannot be made quicker without the feet sliding.** The Walk clip's stride is short: 37 dp for two steps at this size. To the middle of a phone 393 dp wide that is 8 cycles and 4 seconds, already at twice the clip's speed. The whole greeting is then about 7 seconds. A shorter walk needs a longer stride in the clip itself (`animate_milo.py`), which is the mascot's design and Shawn's to change.
- **The walking mascot moves 24 times a second,** with his frames, and not with every drawing of the screen. That is what keeps his feet still; a phone's screen could move him more smoothly, and his feet would slide by 3 dp between two frames.

**What is better than with the 3D engine:** the mascot looks as Blender draws it. The dials' rims are chrome; the engine drew them dark.

**One thing is not as the Blender file has it: the dials' black faces are drawn without their shine.** They are glossy in the model. As the mascot came round to the viewer, the big dial mirrored the studio's key light: its whole face was pale grey for three frames, an eighth of a second, and black again. It read as a flicker at the moment he looks at you. `render_greeting.py` takes the shine off those two faces while it draws, and saves nothing. The faces are a shade blacker in every frame; the marks, the rims and the glass are as they were.

**The safety catch is gone.** It was there because native code that fails ends the app with nothing to catch. Android's picture decoder fails with an error the app can catch: the greeting is then left out for that start, and the Log gets one line.

**One trap in Android itself, found on the first day:** an animated picture that is stopped must keep its listeners. Android tells them of the stop a moment later, from a list it reads only then; with the list cleared in between, it ended the app. `MascotPictures.close` says so.

**It has run on an emulator only** (Android 16, without a graphics chip), not on the phone. Device checks MG-1 to MG-9.

**Going back to 3D stays possible.** The model, its skeleton and its clips are still in `design/mascot/`, and `export/milo_mascot.glb` is still written from them. It would cost what ADR-005 measured.
