# render_poses.py - draws the mascot's poses as still pictures with a see-through background,
# for the marketing kit. Run it with Blender WITHOUT its window:
#
#   blender -b design/mascot/milo_mascot.blend --python marketing/tools/render_poses.py -- <pixels> <folder> <name>=<clip>:<frame> [...]
#
# <pixels> is how high every picture is. <name> is the file's name, <clip> a clip of
# design/mascot/scripts/animate_milo.py and <frame> the frame of it that holds the pose
# (24 frames a second). marketing/build.py says which poses the kit has; this is its line:
#
#   ... -- 2048 /tmp/milo-poses standing=Idle:0 waving=Wave:15 thumbs-up=ThumbsUp:24 celebrate=Celebrate:14 flex=Flex:16 peace=Peace:31 rock-on=RockOn:34
#
# All the poses named in one run are drawn on one size of picture with the mascot standing on
# the same spot, so that one can take another's place without a jump. The picture is exactly
# <pixels> high: the mascot is drawn as large as the pose that reaches highest allows, with
# MARGIN pixels to spare.
#
# It is design/mascot/scripts/render_clips.py's camera, lights and dulled dials, and that
# script's own functions: the mascot looks here as it does in the app and on the website. It
# changes none of the mascot's scripts and SAVES NOTHING in the Blender file.

import os
import sys
import types
from math import ceil, floor

import bpy

MARGIN = 24
WHOLE = 8
# The size the poses are measured at before the real size is known.
TRIAL = 1000


def repository():
    return os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def library(path):
    """One of the mascot's scripts, read for its functions without being run."""
    scope = {"__name__": "__milo_lib__", "__file__": path}
    with open(path) as f:
        exec(compile(f.read(), path, "exec"), scope)
    return types.SimpleNamespace(**scope)


drawing = library(os.path.join(repository(), "design", "mascot", "scripts", "render_clips.py"))
greeting = drawing.greeting
scene = bpy.context.scene


def reach(poses):
    """How far the mascot reaches in the poses, in pixels from where it stands, on the stage as
    it is set: left and right of its axis, below and above its standing soles."""
    axis = greeting.seen((0, 0, 0))[0] * scene.render.resolution_x
    soles = drawing.seen_in_pixels()[1]
    left = low = right = high = 0.0
    for _, clip, frame in poses:
        drawing.play(clip)
        scene.frame_set(frame)
        box = drawing.seen_in_pixels()
        left, low = min(left, box[0] - axis), min(low, box[1] - soles)
        right, high = max(right, box[2] - axis), max(high, box[3] - soles)
    return left, low, right, high


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(asked) < 3 or not asked[0].isdigit():
        raise SystemExit("After '--': the pictures' height in pixels, a folder, and name=Clip:frame once or more.")
    height, folder = int(asked[0]), os.path.abspath(asked[1])
    poses = []
    for word in asked[2:]:
        name, _, rest = word.partition("=")
        clip, _, frame = rest.partition(":")
        poses.append((name, clip, int(frame)))

    shown = drawing.rig.animation_data.action
    for clip in sorted({clip for _, clip, _ in poses}):
        drawing.write(clip)

    # Measured on a large trial stage, then drawn at the size that makes the picture as high
    # as asked.
    greeting.set_stage(3 * TRIAL, 3 * TRIAL, TRIAL)
    left, low, right, high = reach(poses)
    scale = (height - 2 * MARGIN) / (high - low)
    pixels = TRIAL * scale
    width = WHOLE * ceil(((right - left) * scale + 2 * MARGIN) / WHOLE)
    axis = MARGIN - floor(left * scale) + (width - ceil((right - left) * scale + 2 * MARGIN)) // 2
    soles = MARGIN - floor(low * scale)
    greeting.set_stage(width, height, pixels)
    greeting.slide_view("shift_x", lambda: axis / width - greeting.seen((0, 0, 0))[0])
    greeting.slide_view("shift_y", lambda: (soles - drawing.seen_in_pixels()[1]) / height)

    os.makedirs(folder, exist_ok=True)
    for name, clip, frame in poses:
        drawing.play(clip)
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, name)
        bpy.ops.render.render(write_still=True)

    for clip in {clip for _, clip, _ in poses}:
        bpy.data.actions.remove(bpy.data.actions[drawing.DRAWN + clip])
    drawing.rig.animation_data.action = shown
    print("pictures: %d x %d pixels; the standing mascot is %.0f pixels high, its axis %d from the left edge, "
          "its soles %d above the lower edge" % (width, height, pixels, axis, soles))
    for name, clip, frame in poses:
        print("%s.png: frame %d of %s" % (name, frame, clip))


main()
