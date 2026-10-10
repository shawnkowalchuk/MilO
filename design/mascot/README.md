# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. It is a 3D model here. The app carries no model: it shows two pictures of it, drawn here once (`docs/adr/ADR-007-mascot-animated-picture.md`). The website shows three more, drawn the same way.

Nothing in this folder is part of the app's build. The two pictures the app carries are written from here into `app/src/main/res/drawable-nodpi/`, the website's three into `website/mascot/`, and all five are committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the `.glb` file and prints what is in it |
| `scripts/render_greeting.py` | Draws the frames of the app's greeting: the walk, and the turn and wave |
| `scripts/pack_greeting.py` | Packs those frames into the app's two pictures |
| `scripts/render_clips.py` | Draws the frames of any clip, at any size |
| `scripts/render_dials.py` | Draws the two dials without their needles, and the needles' blades alone, for the website |
| `scripts/pack_clips.py` | Packs those frames into the pictures in `renders/`, and into the website's three |
| `export/milo_mascot.glb` | The model with every clip (2.28 MB), for whatever comes later. Nothing uses it today |
| `renders/milo_thumbs_up.png` | The mascot as Blender draws it, in the pose of the reference picture |
| `renders/milo_flex.webp`, `milo_peace.webp`, `milo_rock_on.webp` | Three clips as animated pictures with a see-through background, for whatever comes later (below) |
| `renders/milo_flex.png`, `milo_peace.png`, `milo_rock_on.png` | A still of the pose each of them holds |
| `reference.webp` | The picture the mascot was built from |

**The scripts are the source, the `.blend` is their result.** To change the mascot, change a script and run the first four again, in the order above, rather than editing the Blender file by hand: what is done by hand is gone at the next run. Each of them only rebuilds its own collections (`MilO_Mascot`, `MilO_Studio`, `MilO_Reference`). After a change to the model, or to a clip that a picture is drawn from, that picture has to be drawn again too (below).

Without Blender's window, which is how it was last run (2026-10-09). `-b` is "no window", and the last part saves the file, which nothing else does:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/build_milo.py --python design/mascot/scripts/studio_milo.py --python design/mascot/scripts/animate_milo.py --python design/mascot/scripts/export_milo.py --python-expr "import bpy; bpy.ops.wm.save_mainfile(compress=True)"
```

It takes a few seconds. Blender keeps the file it replaced beside the new one as `milo_mascot.blend1`; git ignores it, and it can be deleted. On this Mac `blender` is `/Applications/Blender.app/Contents/MacOS/Blender`.

The same command without `-b` and without its last part opens Blender's window and leaves the file to be saved by hand. The scripts that draw pictures are run differently and are not part of it.

**Run twice, it writes the same clips and the same `.glb`, byte for byte.** That is the check that a change to a script left the other clips alone: build once from the scripts as they were and once from the changed ones, and compare. The Blender file that was committed before the evening of 2026-10-09 was not quite what its scripts write: about 850 of its 29,600 key values were off by less than a millionth, the mark of a file built in several sittings. Nothing that can be seen.

## The clips

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the three loops and `WaveUp` begins and ends in the same standing pose, so one can follow another. The needles move in every clip.

`Flex`, `Peace` and `RockOn` were added on 2026-10-09 for pictures to come ("future content ideas or use on the website"); nothing plays them yet. `WaveUp` and `WaveLoop` were added on 2026-10-10 for the website, where he waves for as long as a pointer is on him.

| Clip | Length | What happens |
|---|---|---|
| `Idle` | 4 s, loops | Stands, breathes, sways a little |
| `Wave` | 2 s | The right arm goes up, the open hand waves |
| `ThumbsUp` | 2 s | The pose of the reference picture, and back |
| `Walk` | 1 s, loops | Walks on the spot. The greeting plays it at twice this speed |
| `Celebrate` | 3 s | Crouches, jumps twice with both arms up |
| `Flex` | 4.5 s | Shows off in four poses and squeezes each: both fists up beside the head; the right arm up and the left down; the other way round; both again. The RPM needle jumps with every squeeze |
| `Peace` | 2.75 s | The left hand goes up beside the head and holds the peace sign for a second and a half: two fingers up and apart, the third curled under the thumb, the palm to the viewer |
| `RockOn` | 2.75 s | The right hand goes up and holds the horns: the two outer fingers up, the middle one curled under the thumb. Three nods to a beat, the RPM needle at the red line with each |
| `WaveUp` | 0.5 s | The right arm goes up and stays up: its last frame is `WaveLoop`'s first. The way into the loop, not a clip to play alone; nothing brings the arm down again but `Wave`'s own ending |
| `WaveLoop` | 0.5 s, loops | The arm held up, one wave of the hand. It is `Wave`'s wave at two a second instead of two and a half, so that one wave is 12 frames and the loop closes on a frame |
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

**Two runs do not give the same bytes.** Drawn twice from the same file on the same Mac, every frame differs from its twin in a few dozen pixels, by 3 of 255 at most, and the packed pictures differ by a few bytes in 800,000. So the app's pictures cannot be checked by drawing them again and comparing the files: compare the frames, and expect that much. A change that is not meant to touch them leaves the committed pictures alone.

## Any other clip as a picture

`render_clips.py` draws any clip by its name, the standing mascot as many pixels high as it is told. It is the greeting's camera, lights, dulled dials and 24 frames a second, on a see-through background, so the mascot looks the same in every picture:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- 400 /tmp/milo-poses Flex Peace RockOn
python3 design/mascot/scripts/pack_clips.py poses /tmp/milo-poses
```

- **The first step draws the frames** (five minutes for these three), each clip into a folder of its own. All the clips named in one run get one size of picture, with the mascot standing on the same spot in each, so that one can be cut after another without a jump. The size is measured: as far as the mascot reaches in any frame, 12 pixels more on every side, and made up to a multiple of 8. For these three it is 592 by 448 pixels. It saves nothing in the Blender file.
- **The second step packs them** into `renders/`: an animated WebP of each that plays once and keeps its last frame, and a PNG still of the pose it holds. It needs what `pack_greeting.py` needs.

| Picture | Frames | Size | Its still |
|---|---|---|---|
| `renders/milo_flex.webp` | 109 | 1.48 MB | `milo_flex.png`: both fists up, the first squeeze |
| `renders/milo_peace.webp` | 67 | 0.96 MB | `milo_peace.png`: the peace sign, upright |
| `renders/milo_rock_on.webp` | 67 | 0.95 MB | `milo_rock_on.png`: the horns, between two nods |

For another size, give another number; for another clip, give its name and add a line to `POSES` in `pack_clips.py`.

## The website's pictures

The website's front page shows the mascot in its lime tile. His needles keep moving, and he waves for as long as a pointer is on him (APP_ENCYCLOPEDIA, Website). Three pictures in `website/mascot/`, drawn 560 pixels high so that the page can show him 280 high on a sharp screen:

```
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- 560 /tmp/milo-web Wave WaveUp WaveLoop
blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_dials.py -- 560 /tmp/milo-web Wave WaveUp WaveLoop
python3 design/mascot/scripts/pack_clips.py website /tmp/milo-web
```

The second step is given the same height and clips as the first, so that it measures the same picture. `Wave` is named although the page no longer shows it: see "where he stands in the picture" below.

| Picture | What it is | Size |
|---|---|---|
| `website/mascot/standing.webp` | The first frame of `WaveUp`: the mascot standing, on a see-through background. 720 by 608 pixels | 31 KB |
| `website/mascot/dials.webp` | Four squares of 128 pixels: the two dials' faces without a needle, cut round, and under them the two needles' blades, pointing straight up | 8 KB |
| `website/mascot/waving.webp` | 11 frames one under the other in one still picture of 720 by 6,688 pixels: five of `WaveUp`, then six of `WaveLoop`, every second frame of each. Not see-through where the standing mascot is (below) | 228 KB |

- **Nothing here is an animated picture.** The website runs no script (ADR-003, and its privacy page says so), and a page without one cannot start an animated picture again from its first frame. So the stylesheet does the moving (`website/styles.css`, "The mascot"), as the app steps through the sheet of its walk.
- **The needles are turned, not drawn frame by frame.** Only the needles move while he stands, and a needle only turns. So `dials.webp` holds each dial's face without a blade, which the page lays over the standing picture's dial, and the blade alone, which the page lays over that and turns about the middle of the dial. It weighs 8 KB; a strip of the two dials drawn frame by frame was reckoned at 150 KB or more and was not made. And the needles move as smoothly as the screen can show. How they move, an engine ticking over and revved now and then, is written in the stylesheet, not here.
- **How a blade is drawn alone** (`render_dials.py`): the needle is one object, a red blade and a black cap. In memory it is taken apart; the standing mascot is drawn with the caps only, and then the blades alone with everything else hidden and the caps as holes, because a cap lies over its blade. The blade is drawn without the glass over the dial, which would be drawn round it as a haze, and `pack_clips.py` darkens it by what the glass takes (a twentieth). The camera looks at the dials almost squarely, about 6 degrees off, so a blade turned flat on the page lies where a blade drawn at that angle would, to within half a pixel by the arithmetic. Turned to where the standing picture has its own needle, it covers it.
- **The wave is a raise and a loop in one strip.** The stylesheet steps through the first five frames once and then through the other six again and again, twelve a second. The raise's first frame is left out, because it is the standing pose; its last is the loop's first.
- **Each frame of the strip covers the standing mascot,** needles and all. A page without a script cannot tell whether the strip has arrived, so it cannot take the standing picture away for it: with the first version, on a slow line, the mascot was gone for two and a half seconds at the first rollover. So the standing picture stays, and each frame is filled with the lime of the tile wherever the standing mascot is and the waving one is not: his hanging right arm, most of all. `pack_clips.py` reads that lime from `--accent` in `website/styles.css`. **Change that colour, or stand him on another, and `waving.webp` has to be packed again,** or a shape in the old lime waves with him. WebP cannot store this lime exactly: the strip's is one step of 255 off in red and in blue, which cannot be seen.
- **The page is told the sizes and the places.** `pack_clips.py` prints them: the frame's size, how many frames the raise and the loop have, and for each dial where its square lies in the picture and where in the square the needle turns, in hundredths. `index.html` holds half the frame's size as the picture's width and height; `styles.css` holds the rest. Draw him another size, change a clip's length, or change the clips he is drawn from, and those change with it.
- **The sides are multiples of 8 pixels on purpose, and the dials' squares lie on multiples of 4,** so that half and a quarter of them are whole numbers of the screen's pixels, also on a screen scaled by a quarter or a half more, as Windows laptops are. Otherwise he would shiver by a part of a pixel from frame to frame, and a dial's face would lie a part of a pixel off the dial under it.
- **A picture that changes gets another name.** A visitor's browser keeps these pictures for a week and the stylesheet for an hour (`firebase.json`). When a picture changes in a way the stylesheet counts on, some visitors would for a week see the new rules step through the old picture. So the strip is `waving.webp` since it has 11 frames; it was `wave.webp` while it had 24.
- **Where he stands in the picture must not change either,** for the same reason: the dials' faces are laid over `standing.webp`, old or new. The picture's size and the mascot's place in it are measured from the clips named, as far as he reaches in any of them. `Wave`'s hand reaches 2 pixels further than `WaveLoop`'s, and the first `standing.webp` was drawn from `Wave`; so `Wave` is still named, its frames are drawn and not used, and he stands where he stood: his middle 434 pixels from the left of 720, his soles 11 above the lower edge.

## What to know before changing the skeleton

- The character faces Blender's −Y and stands on z = 0, about 2 units tall. `.L` is its own left.
- **The needles are driven by two sliders on the skeleton** (`speed_kmh`, `rpm`). A clip keys the sliders, never the needle bones.
- **Arms and legs are hoses.** Their four bones on each side are bendy bones that carry no mesh; rows of short `hose.*` bones follow them and carry it. A `.glb` file cannot hold a bendy bone, and this is what keeps the hoses round in the export.
- **`Walk` walks on the spot,** with a short stride: each foot goes 0.30 of a unit back while it is planted, on a mascot 2 units high. The greeting moves the picture by that much, so a longer or shorter stride here changes how long the walk across the screen takes.
- **The feet lead the legs.** Move a foot and its leg follows; lower the body and the knees bend.
- The "M" is the app's own mark: its outline is read from `app/src/main/res/drawable/ic_launcher_foreground.xml`, and the numbers on the dials are set in the app's `sora.ttf`.

## What to know before writing a clip

- **A hand has a thumb and three fingers:** `finger_a` next to the thumb, `finger_b` the middle and longest, `finger_c` the outer one. A pose curls each finger at its two joints and may spread it sideways (`spread` in `arm_pose`); the thumb folds across the palm with `(112, 0, 50, 62)` or near it. The peace sign is a and b up and apart; the horns are a and c up.
- **The hands are large and the arms short.** A fist beside the head is clear of it only with the forearm leaning a little outwards and the fingers to the viewer; curled in towards the head, as a real arm flexes, it is inside the head. A hand held up beside the head has about a finger's width to spare.
- **No overshoot towards the head.** `ThumbsUp` swings a little past its pose and back. A hand beside the head that does so goes through a dial: `Peace` and `RockOn` swing outwards as they arrive instead.
- **An arm goes from one pose to another by the shortest way,** which between "forearm up" and "forearm down" can be through the body. `curved()` draws the way towards a third pose: `Flex` takes its fists round the outside with it.
- **Turned about 10 degrees to its right and leaning back, the dials go pale.** Their glossy black faces then mirror the studio's key light into the camera, the same shine the greeting found in its turn. The pictures are drawn with the faces dulled, but the model and its `.glb` keep the gloss, so `Flex` does not lean back in the pose in which it is turned that way.
- **Look at the frames.** `render_clips.py` at 250 pixels and a sheet of every second frame shows a hand in a dial, or a pop, that the numbers do not.
