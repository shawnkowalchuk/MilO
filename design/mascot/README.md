# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. It is a 3D model here. The app carries no model: it shows two pictures of it, drawn here once (`docs/adr/ADR-007-mascot-animated-picture.md`).

Nothing in this folder is part of the app's build. The two pictures the app carries are written from here into `app/src/main/res/drawable-nodpi/` and committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the `.glb` file and prints what is in it |
| `scripts/render_greeting.py` | Draws the frames of the app's greeting: the walk, and the turn and wave |
| `scripts/pack_greeting.py` | Packs those frames into the app's two pictures |
| `export/milo_mascot.glb` | The model with every clip (1.69 MB), for whatever comes later. Nothing uses it today |
| `renders/milo_thumbs_up.png` | The mascot as Blender draws it, in the pose of the reference picture |
| `reference.webp` | The picture the mascot was built from |

**The scripts are the source, the `.blend` is their result.** To change the mascot, change a script and run the first four again, in the order above, rather than editing the Blender file by hand: what is done by hand is gone at the next run. Each of them only rebuilds its own collections (`MilO_Mascot`, `MilO_Studio`, `MilO_Reference`). After a change the app's pictures have to be drawn again too (below).

```
blender design/mascot/milo_mascot.blend --python design/mascot/scripts/build_milo.py --python design/mascot/scripts/studio_milo.py --python design/mascot/scripts/animate_milo.py --python design/mascot/scripts/export_milo.py
```

That command opens Blender's window and leaves the file to be saved by hand. The two greeting scripts are run differently, without the window, and are not part of it.

## The clips

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the two loops begins and ends in the same standing pose, so one can follow another. The needles move in every clip.

| Clip | Length | What happens |
|---|---|---|
| `Idle` | 4 s, loops | Stands, breathes, sways a little |
| `Wave` | 2 s | The right arm goes up, the open hand waves |
| `ThumbsUp` | 2 s | The pose of the reference picture, and back |
| `Walk` | 1 s, loops | Walks on the spot. The greeting plays it at twice this speed |
| `Celebrate` | 3 s | Crouches, jumps twice with both arms up |
| `Gauge_Speed` | 2 s | The speed needle alone, from 0 to 240 |
| `Gauge_RPM` | 2 s | The RPM needle alone, from 0 to 8 |

The two gauge clips move one bone each and nothing else, so they can be laid over any other clip and set to a live value: speed ÷ 240 of the way through the first, RPM ÷ 8 of the way through the second.

## The app's pictures

The app's greeting is two pictures (WebP with a see-through background; a frame is 600 by 420 pixels, 24 frames a second):

| Picture | What it shows | Size |
|---|---|---|
| `app/src/main/res/drawable-nodpi/mascot_walk.webp` | One cycle of the walk toward the left, 12 frames. A still picture: the frames lie side by side, four across and three down. The app draws the frame it wants, because it moves the mascot with every step | 98 KB |
| `app/src/main/res/drawable-nodpi/mascot_turn_and_wave.webp` | From the walk's first frame: the turn to the viewer in 11 frames, then the Wave clip. An animated picture of 60 frames, which Android plays once | 782 KB |

They are made in two steps. The first needs the Mac's Blender. The second needs `cwebp` and `webpmux` (`brew install webp`) and `ffmpeg` (`brew install ffmpeg`). The two folders are for the frames and can be anywhere outside the repository:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_greeting.py -- /tmp/milo-greeting/walk /tmp/milo-greeting/wave
python3 design/mascot/scripts/pack_greeting.py /tmp/milo-greeting/walk /tmp/milo-greeting/wave
```

- **The first step draws the frames** (a minute and a half), from the studio's camera and under its lights, with the backdrop hidden so that the background is see-through. It saves nothing in the Blender file. At its end it prints the picture's size, how far the mascot reaches ahead while it walks, where its soles are, and its stride.
- **The second step packs them** into the two pictures and prints what each weighs.
- **The app's code has to be told what the first step printed:** `MascotPicture` in `core/designsystem/component/MascotWalk.kt` holds those numbers. `MascotPicturesTest` fails if the pictures' size, the sheet's rows, the animated picture's frames or how often it plays are not what stands there. The stride, the reach and the soles are not in the files, and no test can check them: a wrong stride shows as feet that slide.
- **What can be changed at the top of `render_greeting.py`:** how high the mascot is drawn, how far it is turned while it walks, how fast it steps, and how long the turn takes.
- **The dials' black faces are drawn without their shine** (`dull_the_dials` in `render_greeting.py`; nothing is saved). They are glossy in the model, and as the mascot came round to the viewer the big dial mirrored the key light: pale grey for three frames, like a flicker.

Seen from the studio's camera, which looks down on the ground a little, the foot nearer the viewer stands about 11 dp lower in the picture than the other, and a planted foot rises about 3 dp while it steps back. That is the view: the mascot walks a little toward the viewer. From left to right a planted foot stays where it is.

To give the app another clip, draw it as one more picture the same way and add it to `MascotPictures` in `core/designsystem/component/Mascot.kt`: an animated picture if the mascot stands where it is, a sheet of frames if the app has to move it in step. Each second of a clip is about a third of a megabyte.

## What to know before changing the skeleton

- The character faces Blender's −Y and stands on z = 0, about 2 units tall. `.L` is its own left.
- **The needles are driven by two sliders on the skeleton** (`speed_kmh`, `rpm`). A clip keys the sliders, never the needle bones.
- **Arms and legs are hoses.** Their four bones on each side are bendy bones that carry no mesh; rows of short `hose.*` bones follow them and carry it. A `.glb` file cannot hold a bendy bone, and this is what keeps the hoses round in the export.
- **`Walk` walks on the spot,** with a short stride: each foot goes 0.30 of a unit back while it is planted, on a mascot 2 units high. The greeting moves the picture by that much, so a longer or shorter stride here changes how long the walk across the screen takes.
- **The feet lead the legs.** Move a foot and its leg follows; lower the body and the knees bend.
- The "M" is the app's own mark: its outline is read from `app/src/main/res/drawable/ic_launcher_foreground.xml`, and the numbers on the dials are set in the app's `sora.ttf`.
