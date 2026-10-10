# ADR-007: The mascot is two animated pictures, played by Android
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

**The mascot is drawn once, in Blender, and the app carries the result as two animated pictures.** Android's own decoder plays them (`ImageDecoder` and `AnimatedImageDrawable`, in Android since version 9). Filament and gltfio are out of the build, with everything that was there only for them: the stage that held the engine, the model in the app's assets and its test, and the safety catch with its stored count.

**The greeting is the one Shawn asked for:**

1. The mascot comes in from beyond the right edge of the screen and walks to the left, its feet just above the bottom bar, to the middle of the screen's width.
2. There it turns to face the viewer and waves once.
3. It fades away.

**The two pictures** (`app/src/main/res/drawable-nodpi/`), both animated WebP with a see-through background, 600 by 420 pixels, 24 frames a second:

| Picture | Frames | Plays | Size |
|---|---|---|---|
| `mascot_walk.webp` | 12: one cycle of the walk, two steps | starts again for ever; the code stops it | 100 KB |
| `mascot_turn_and_wave.webp` | 60: the turn in 11, then the Wave clip | once, and keeps its last frame | 773 KB |

Together 873 KB. They are drawn by the studio's camera under the studio's lights, by `design/mascot/scripts/render_greeting.py`, and packed by `pack_greeting.py`. The files are committed; the build does not run Blender.

**How the two become one greeting:**

- The walk's picture shows the mascot walking on the spot. The app moves the picture to the left by exactly as much as the mascot's feet step back in it: 36.9 dp for each cycle. So a foot that is on the ground stays where it is on the screen.
- The walk is a whole number of cycles. Where the walk's picture starts again, the second picture takes its place: its first frame is the frame the walk would have shown next. Both pictures are the same size and were drawn by the same camera, so nothing jumps.
- The app is told by Android when each picture shows its last frame. It cannot see which frame is showing in between.

**Decided with it. Shawn was not asked these; each is a number in one place:**

- **The mascot stands 150 dp high.** The picture has 2.5 pixels to the dp.
- **It walks turned 55 degrees from the viewer.** Further round, its face is lost: the body is a slab, and the M and the dials are then seen from the side.
- **It takes its steps twice as fast as the Walk clip.** Every second frame of the clip is drawn.
- **It turns with a small hop,** in under half a second, so that its soles do not grind round on the ground.
- **It takes nothing from the screen under it.** No press, no Back and no screen reader meets it, and the screen is not dimmed. So there is no "tap to skip" any more: there is nothing to skip past.
- **When MilO greets has not changed** (APP_ENCYCLOPEDIA, The greeting).

## Consequences

**What was measured, on release builds of 0.3.0 made the same way:**

| | With Filament | With the pictures |
|---|---|---|
| The website's APK | 64 MB | 39.9 MB |
| The bundle for Google Play | 25 MB | 15.5 MB |
| The app's own native libraries | Filament and gltfio, 5.8 MB for a current phone | none |
| What the mascot adds | the two libraries and a 1.16 MB model | 0.87 MB of pictures |

The native libraries left in the APK are AndroidX's, as before the mascot: the database's (1.3 MB for a current phone) and two of 10 KB each.

**What it costs:**

- **The mascot cannot react to anything.** A picture plays as it was drawn. Needles that follow the truck's real speed would need the 3D engine back.
- **Every further clip is another picture of about this size:** about 13 KB for each frame, so a third of a megabyte for each second.
- **The look is fixed when the pictures are drawn.** Another size, angle or pace means drawing them again in Blender (a minute and a half) and committing the new files.
- **The walk cannot be made quicker without the feet sliding.** The Walk clip's stride is short: 37 dp for two steps at this size. To the middle of a phone 393 dp wide that is 8 cycles and 4 seconds, already at twice the clip's speed. The whole greeting is then about 7 seconds. A shorter walk needs a longer stride in the clip itself (`animate_milo.py`), which is the mascot's design and Shawn's to change.
- **Android keeps the pictures' time, not the app.** The app moves the walking mascot by the clock and trusts the picture to keep up. On a phone that cannot draw fast enough the picture falls behind, and the mascot then marks time in the middle until the picture has finished its last step. Seen on the emulator, which draws without a graphics chip: 0.15 to 0.3 seconds of it.

**What is better than with the 3D engine:** the mascot looks as Blender draws it. The dials' rims are chrome and the glass over them shines; the engine drew the rims dark.

**The safety catch is gone.** It was there because native code that fails ends the app with nothing to catch. Android's picture decoder fails with an error the app can catch: the greeting is then left out for that start, and the Log gets one line.

**One trap in Android itself, found on the first day:** a picture that is stopped must keep its listeners. Android tells them of the stop a moment later, from a list it reads only then; with the list cleared in between, it ended the app. `MascotPictures.close` says so.

**It has run on an emulator only** (Android 16, without a graphics chip), not on the phone. Device checks MG-1 to MG-9.

**Going back to 3D stays possible.** The model, its skeleton and its clips are still in `design/mascot/`, and `export/milo_mascot.glb` is still written from them. It would cost what ADR-005 measured.
