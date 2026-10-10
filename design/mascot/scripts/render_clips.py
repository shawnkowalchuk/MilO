# render_clips.py - draws any of the mascot's clips as pictures with a see-through background:
# numbered PNG frames from the studio's camera and under the studio's lights, as the greeting's
# are drawn. Run it after the other scripts, with Blender WITHOUT its window:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_clips.py -- <pixels> <folder> <clip> [<clip> ...]
#
# <pixels> is how high the standing mascot is drawn. <clip> is a name from CLIPS in
# animate_milo.py: Wave, Flex, Peace, RockOn and so on. Each clip's frames go into a folder of
# its own inside <folder>, named after the clip. pack_clips.py makes the pictures out of them.
#
# Every clip named in one run is drawn on one size of picture, with the standing mascot on the
# same spot in each, so that one can be cut after another without a jump. The picture's size is
# measured, not set: it is as far as the mascot reaches in any frame of those clips, and MARGIN
# pixels more on every side, made up to the next multiple of WHOLE.
#
# The poses come from animate_milo.py's functions, as in render_greeting.py, not from the clips
# stored in the Blender file: so the frames are what the scripts say even if the file is behind.
#
# The dials' black faces are drawn without their shine, as in the greeting and by the same code
# (dull_the_dials in render_greeting.py, which its set_stage calls), so that the mascot looks
# the same in every picture.
#
# NOTHING IS SAVED. The script hides the backdrop, changes the camera's lens, takes that shine
# off and writes clips of its own, all in memory, and Blender closes without saving.

import os
import sys
import types
import bpy
from math import ceil, floor

# Clear pixels between the mascot and the picture's edge, where it reaches farthest.
MARGIN = 12
# The picture's sides are multiples of this many pixels; what is added is at the right and at
# the top. The website shows its pictures at half their size and steps through a strip of
# frames: with a side of 8 pixels' multiple a frame is a whole number of the screen's pixels
# there, also on a screen that is scaled by a quarter or a half more.
WHOLE = 8
# The name a clip is written under in memory, so that the stored clip of the same name is left alone.
DRAWN = "Drawn_"


def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


def library(name):
    """Another of the mascot's scripts, read for its functions without being run."""
    path = os.path.join(here(), name)
    scope = {"__name__": "__milo_lib__", "__file__": path}
    with open(path) as f:
        exec(compile(f.read(), path, "exec"), scope)
    return types.SimpleNamespace(**scope)


greeting = library("render_greeting.py")
clips = greeting.clips
rig = clips.rig
scene = bpy.context.scene


def write(name):
    """One clip as an action in memory, keyed on every frame. Returns how many frames it has:
    one more than its length, because the first and the last are both drawn."""
    found = [(seconds, pose_at) for clip, seconds, pose_at, _ in clips.CLIPS if clip == name]
    if not found:
        raise SystemExit("No clip is called %s. There are: %s" % (name, ", ".join(c[0] for c in clips.CLIPS)))
    seconds, pose_at = found[0]
    frames = round(seconds * clips.FPS)
    clips.reset_pose()
    clips.new_action(DRAWN + name, frames, False)
    for frame in range(frames + 1):
        pose_at(frame / clips.FPS)
        greeting.key(frame)
    return frames + 1


def play(name):
    """Makes one of the clips written here the rig's live clip."""
    action = bpy.data.actions[DRAWN + name]
    rig.animation_data.action = action
    if getattr(rig.animation_data, "action_slot", True) is None and action.slots:
        rig.animation_data.action_slot = action.slots[0]


def seen_in_pixels():
    """Where the camera draws the mascot as it is posed now: left, lowest, right and highest,
    in pixels from the picture's lower left corner."""
    width, height = scene.render.resolution_x, scene.render.resolution_y
    at = [greeting.seen(p) for p in greeting.mascot_points()]
    across, up = [a[0] * width for a in at], [a[1] * height for a in at]
    return min(across), min(up), max(across), max(up)


def reach(frames):
    """How far the mascot reaches in the clips, in pixels from where it stands: to the left and
    to the right of its axis, below and above its standing soles. The stage must be set, which
    leaves the mascot standing."""
    axis = greeting.seen((0, 0, 0))[0] * scene.render.resolution_x
    soles = seen_in_pixels()[1]
    left = low = right = high = 0.0
    for name, count in frames.items():
        play(name)
        for frame in range(count):
            scene.frame_set(frame)
            box = seen_in_pixels()
            left, low = min(left, box[0] - axis), min(low, box[1] - soles)
            right, high = max(right, box[2] - axis), max(high, box[3] - soles)
    return left, low, right, high


def set_picture(frames, pixels):
    """Sets the picture so that every frame of the clips fits in it with MARGIN to spare, and
    the standing mascot is `pixels` high. Returns the picture's size, and where the mascot
    stands in it: its axis from the left edge and its soles above the lower edge."""
    # A first stage only to measure on: the lens is right, the picture is larger than needed.
    # (The stage is set twice. The second time it finds the dials dulled already.)
    greeting.set_stage(3 * pixels, 3 * pixels, pixels)
    left, low, right, high = reach(frames)
    width = WHOLE * ceil((right - left + 2 * MARGIN) / WHOLE)
    height = WHOLE * ceil((high - low + 2 * MARGIN) / WHOLE)
    axis, soles = MARGIN - floor(left), MARGIN - floor(low)
    greeting.set_stage(width, height, pixels)
    greeting.slide_view("shift_x", lambda: axis / width - greeting.seen((0, 0, 0))[0])
    greeting.slide_view("shift_y", lambda: (soles - seen_in_pixels()[1]) / height)
    return width, height, axis, soles


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(asked) < 3 or not asked[0].isdigit():
        raise SystemExit("After '--': how high the standing mascot is in pixels, a folder, and one clip or more.")
    pixels, folder, names = int(asked[0]), os.path.abspath(asked[1]), asked[2:]
    shown = rig.animation_data.action
    frames = {name: write(name) for name in names}
    width, height, axis, soles = set_picture(frames, pixels)

    boxes = {}
    for name in names:
        play(name)
        boxes[name] = greeting.render_clip(os.path.join(folder, name), frames[name])

    for name in names:
        bpy.data.actions.remove(bpy.data.actions[DRAWN + name])
    rig.animation_data.action = shown

    every = [box for name in names for box in boxes[name]]
    left, top = min(b[0] for b in every), min(b[1] for b in every)
    right, bottom = max(b[2] for b in every), max(b[3] for b in every)
    standing = boxes[names[0]][0]
    print("picture: %d x %d pixels, %d frames a second" % (width, height, clips.FPS))
    print("the standing mascot: %d pixels high, from %d to %d across; its axis is %d pixels from the left edge, "
          "its soles %d above the lower edge" % (standing[3] - standing[1], standing[0], standing[2], axis, height - standing[3]))
    for name in names:
        print("%s: %d frames in %s" % (name, frames[name], os.path.join(folder, name)))
    print("the mascot, over every frame: left %d, top %d, right %d, bottom %d" % (left, top, right, bottom))
    if left <= 0 or top <= 0 or right >= width or bottom >= height:
        raise SystemExit("The mascot touches the picture's edge in some frame: make MARGIN larger.")


if __name__ != "__milo_lib__":
    main()
