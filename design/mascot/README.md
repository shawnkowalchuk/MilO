# The mascot

MilO's mascot: a green block with a speedometer and a rev counter for eyes, the app's "M" for a mouth, hose arms and legs, white gloves and green shoes. Built in Blender 5.2 on 2026-10-09 from the picture in `reference.webp`. Why it is a 3D model, and how the app draws it, is in `docs/adr/ADR-005-mascot-renderer.md`.

Nothing in this folder is part of the app's build. The one file the app carries is written from here into `app/src/main/assets/mascot/` and committed.

| File | What it is |
|---|---|
| `milo_mascot.blend` | The Blender file: model, skeleton, clips, and a small studio (camera, lights, white backdrop) |
| `scripts/build_milo.py` | Builds the model and its skeleton from nothing |
| `scripts/studio_milo.py` | Builds the camera, the lights and the backdrop, and stands the reference picture beside the model |
| `scripts/animate_milo.py` | Writes the clips |
| `scripts/export_milo.py` | Writes the two `.glb` files and prints what is in each |
| `export/milo_mascot.glb` | The model with every clip (1.69 MB), for the website and for whatever comes later |
| `renders/milo_thumbs_up.png` | The mascot as Blender draws it, in the pose of the reference picture |
| `reference.webp` | The picture the mascot was built from |

**The scripts are the source, the `.blend` is their result.** To change the mascot, change a script and run all four again, in the order above, rather than editing the Blender file by hand: what is done by hand is gone at the next run. Each script only rebuilds its own collections (`MilO_Mascot`, `MilO_Studio`, `MilO_Reference`).

```
blender design/mascot/milo_mascot.blend --python design/mascot/scripts/build_milo.py --python design/mascot/scripts/studio_milo.py --python design/mascot/scripts/animate_milo.py --python design/mascot/scripts/export_milo.py
```

## The clips

Each is one action, at 24 frames a second, starting on frame 0. Every clip but the two loops begins and ends in the same standing pose, so one can follow another. The needles move in every clip.

| Clip | Length | What happens |
|---|---|---|
| `Idle` | 4 s, loops | Stands, breathes, sways a little |
| `Wave` | 2 s | The right arm goes up, the open hand waves |
| `ThumbsUp` | 2 s | The pose of the reference picture, and back |
| `Walk` | 1 s, loops | Walks on the spot |
| `Celebrate` | 3 s | Crouches, jumps twice with both arms up |
| `Gauge_Speed` | 2 s | The speed needle alone, from 0 to 240 |
| `Gauge_RPM` | 2 s | The RPM needle alone, from 0 to 8 |

The two gauge clips move one bone each and nothing else, so they can be laid over any other clip and set to a live value: speed ÷ 240 of the way through the first, RPM ÷ 8 of the way through the second.

## The app's copy

`app/src/main/assets/mascot/milo_wave.glb` (1.16 MB) is the same model with only the clips the app's code plays, named in `APP_CLIPS` in `export_milo.py`. To give the app another clip: add its name there, run the scripts, add it to `MascotClip` in `core/designsystem/component/Mascot.kt`, and commit the new file. `MascotModelTest` fails while the two lists differ.

## What to know before changing the skeleton

- The character faces Blender's −Y and stands on z = 0, about 2 units tall. `.L` is its own left.
- **The needles are driven by two sliders on the skeleton** (`speed_kmh`, `rpm`). A clip keys the sliders, never the needle bones.
- **Arms and legs are hoses.** Their four bones on each side are bendy bones that carry no mesh; rows of short `hose.*` bones follow them and carry it. A `.glb` file cannot hold a bendy bone, and this is what keeps the hoses round in the export.
- **The feet lead the legs.** Move a foot and its leg follows; lower the body and the knees bend.
- The "M" is the app's own mark: its outline is read from `app/src/main/res/drawable/ic_launcher_foreground.xml`, and the numbers on the dials are set in the app's `sora.ttf`.
