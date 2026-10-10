# render_greeting.py - draws the app's greeting as pictures: the mascot walking toward the left,
# and the mascot turning to face the viewer and waving. Run it after the other scripts, with
# Blender WITHOUT its window, and name two folders for the frames:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_greeting.py -- <folder for the walk> <folder for the turn and wave>
#
# Each folder gets numbered PNG frames with a see-through background, all of one size, from the
# studio's camera and under the studio's lights. pack_greeting.py then makes the two animated
# pictures the app carries out of them. What this script prints at the end (the picture's size,
# the frames, the stride, how far the mascot reaches ahead, where its soles are) is what the
# app's code is told, in core/designsystem/component/MascotWalk.kt.
#
#   the walk           every second frame of the clip "Walk", so one cycle is WALK_FRAMES frames,
#                      with the mascot turned WALK_ANGLE degrees so that it walks to the left
#   the turn and wave  from the walk's first pose and angle to the standing pose, facing the
#                      viewer, in TURN_FRAMES frames with a small hop; then the clip "Wave"
#
# The turn and wave's first frame is the frame the walk would show next, so the one follows the
# other without a jump; its last frame is the standing pose.
#
# NOTHING IS SAVED. The script hides the backdrop, turns the mascot, changes the camera's lens
# and writes two clips of its own, all in memory, and Blender closes without saving. It changes
# no collection and no stored clip.

import os
import sys
import types
import bpy
import numpy
from math import radians, sin, pi
from bpy_extras.object_utils import world_to_camera_view
from mathutils import Vector

# ---------------------------------------------------------------- what is drawn
# The app draws the standing mascot MASCOT_DP high, whatever the phone's density. The picture
# has two and a half pixels to the dp: a current phone has 2.6 to 3, and at three the two
# pictures together were over a megabyte without looking better on a phone.
PX_PER_DP = 2.5
MASCOT_DP = 150
# The picture. The mascot's axis, about which it turns, is the picture's middle: so it is wide
# enough for the waving hand on one side, and as wide on the other. High enough for the hop.
PICTURE_DP = (240, 168)
# How far above the picture's lower edge the soles are while the mascot walks.
FEET_DP = 2

# Degrees the mascot is turned away from the viewer while it walks. Far enough that it clearly
# walks to the left, not so far that its face is lost: the body is a slab, and from about 60
# degrees on the M and the dials are seen from the side.
WALK_ANGLE = 55.0
# One cycle of "Walk" is 24 frames. Every second one is drawn, so the mascot takes its steps
# twice as fast as the clip does on its own: its stride is short, and at the clip's own speed
# the walk to the middle of a phone would take eight seconds.
WALK_FRAMES = 12
TURN_FRAMES = 11
# How high the mascot hops while it turns, so that its soles do not grind round on the ground.
HOP = 0.06
WAVE_FRAMES = 48
FPS = 24


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


clips = library("animate_milo.py")
rig = clips.rig
pb = clips.pb
scene = bpy.context.scene
camera = scene.camera


# ---------------------------------------------------------------- the two clips, in memory
def pose_now():
    """Everything a clip keys, as it stands."""
    return dict(
        rotation={name: tuple(pb[name].rotation_euler) for name in clips.ROTATED},
        location={name: tuple(pb[name].location) for name in clips.MOVED},
        scale={name: tuple(pb[name].scale) for name in clips.SCALED},
        gauges=(rig["speed_kmh"], rig["rpm"]),
    )


def pose_between(a, b, w):
    """Sets the pose that lies the part w of the way from a to b."""
    def mix(x, y):
        return tuple(p + (q - p) * w for p, q in zip(x, y))

    for name in clips.ROTATED:
        pb[name].rotation_euler = mix(a["rotation"][name], b["rotation"][name])
    for name in clips.MOVED:
        pb[name].location = mix(a["location"][name], b["location"][name])
    for name in clips.SCALED:
        pb[name].scale = mix(a["scale"][name], b["scale"][name])
    rig["speed_kmh"], rig["rpm"] = mix(a["gauges"], b["gauges"])


def key(frame, angle=0.0, lifted=0.0):
    """Keys the pose as it stands, with the whole mascot turned and lifted."""
    clips.key_pose(frame)
    rig.rotation_euler = (0.0, 0.0, -radians(angle))
    rig.location = (0.0, 0.0, lifted)
    rig.keyframe_insert("rotation_euler", frame=frame)
    rig.keyframe_insert("location", frame=frame)


def write_walk():
    """One cycle. The frame after the last is the first again."""
    clips.reset_pose()
    clips.new_action("Greeting_Walk", WALK_FRAMES - 1, True)
    for frame in range(WALK_FRAMES):
        clips.walk(frame / WALK_FRAMES)
        key(frame, angle=WALK_ANGLE)
    return WALK_FRAMES


def write_turn_and_wave():
    """Starts on the walk's first pose, which is the frame the walk would show next."""
    clips.reset_pose()
    clips.walk(0.0)
    walking = pose_now()
    clips.neutral()
    standing = pose_now()
    clips.new_action("Greeting_TurnAndWave", TURN_FRAMES + WAVE_FRAMES, False)
    for frame in range(TURN_FRAMES):
        along = frame / TURN_FRAMES
        w = clips.smooth(along)
        pose_between(walking, standing, w)
        key(frame, angle=WALK_ANGLE * (1 - w), lifted=HOP * sin(pi * along))
    for frame in range(WAVE_FRAMES + 1):
        clips.wave(frame / FPS)
        key(TURN_FRAMES + frame)
    return TURN_FRAMES + WAVE_FRAMES + 1


# ---------------------------------------------------------------- the picture
def mascot_points():
    """Every point of the mascot's surface as it is posed now, in the scene."""
    graph = bpy.context.evaluated_depsgraph_get()
    points = []
    for ob in bpy.data.collections["MilO_Mascot"].objects:
        if ob.type != "MESH":
            continue
        posed = ob.evaluated_get(graph)
        mesh = posed.to_mesh()
        points += [posed.matrix_world @ v.co for v in mesh.vertices]
        posed.to_mesh_clear()
    return points


def seen(point):
    """Where the camera draws a point of the scene: across and up, each from 0 to 1."""
    at = world_to_camera_view(scene, camera, Vector(point))
    return at.x, at.y


def set_stage():
    """A see-through background, and the picture's size. The camera stays where the studio put
    it: only its lens and the part of its view that is kept change, so that the standing mascot
    is MASCOT_DP high and the axis it turns about is the picture's middle."""
    width, height = (round(side * PX_PER_DP) for side in PICTURE_DP)
    render = scene.render
    render.film_transparent = True
    render.resolution_x, render.resolution_y, render.resolution_percentage = width, height, 100
    render.image_settings.file_format = "PNG"
    render.image_settings.color_mode = "RGBA"
    render.image_settings.color_depth = "8"
    render.use_file_extension = True
    # The paper would be drawn behind the mascot, and the picture it was built from stands
    # beside it. Neither is in the app's pictures.
    bpy.data.objects["MilO_Backdrop"].hide_render = True
    for ob in bpy.data.collections["MilO_Reference"].objects:
        ob.hide_render = True

    clips.reset_pose()
    clips.neutral()
    rig.animation_data.action = None
    rig.rotation_euler = (0.0, 0.0, 0.0)
    rig.location = (0.0, 0.0, 0.0)
    bpy.context.view_layer.update()
    lens = camera.data
    lens.shift_x = lens.shift_y = 0.0
    ups = [seen(p)[1] for p in mascot_points()]
    lens.lens *= MASCOT_DP * PX_PER_DP / ((max(ups) - min(ups)) * height)
    slide_view("shift_x", lambda: 0.5 - seen((0, 0, 0))[0])
    return width, height


def slide_view(shift, off):
    """Moves the part of the camera's view that is kept, across or up, until off() is nothing.
    What a shift of the lens does to the picture is measured, not assumed."""
    lens = camera.data
    setattr(lens, shift, 0.0)
    before = off()
    setattr(lens, shift, 0.1)
    after = off()
    setattr(lens, shift, 0.1 * before / (before - after))


def stand_on_the_line(frames):
    """Moves the camera's view up or down until the lowest sole of the clip that is live is
    FEET_DP above the picture's lower edge. Measured on the walk, because the mascot stands on
    that line for longest: seen from the studio's camera, which looks down on the ground, the
    foot nearer the viewer is lower in the picture, and both are higher once it has turned."""
    def lowest():
        low = 1.0
        for frame in range(frames):
            scene.frame_set(frame)
            low = min(low, min(seen(p)[1] for p in mascot_points()))
        return low

    slide_view("shift_y", lambda: FEET_DP / PICTURE_DP[1] - lowest())


def render_clip(folder, frames):
    """Draws every frame of the clip that is live, and returns where the mascot is in each:
    (left, top, right, bottom) in pixels from the picture's upper left corner."""
    os.makedirs(folder, exist_ok=True)
    boxes = []
    for frame in range(frames):
        scene.frame_set(frame)
        scene.render.filepath = os.path.join(folder, "%04d" % frame)
        bpy.ops.render.render(write_still=True)
        boxes.append(box_of(scene.render.filepath + ".png"))
    return boxes


def alpha_of(path):
    """A frame's see-through channel, top row first."""
    image = bpy.data.images.load(path)
    width, height = image.size
    pixels = numpy.empty(width * height * 4, dtype=numpy.float32)
    image.pixels.foreach_get(pixels)
    bpy.data.images.remove(image)
    return pixels.reshape(height, width, 4)[::-1, :, 3]


def box_of(path):
    alpha = alpha_of(path)
    rows = numpy.nonzero(alpha.max(axis=1) > 0)[0]
    columns = numpy.nonzero(alpha.max(axis=0) > 0)[0]
    return int(columns[0]), int(rows[0]), int(columns[-1]) + 1, int(rows[-1]) + 1


def sole_seen(side, frame):
    """How far across the picture the camera sees the middle of a shoe's sole in a frame of the
    clip that is live, in pixels. The sole and not the foot's bone: what is nearer to the camera
    moves further in the picture, and the sole is what stands on the ground."""
    scene.frame_set(frame)
    posed = bpy.data.objects["Shoe." + side].evaluated_get(bpy.context.evaluated_depsgraph_get())
    points = [posed.matrix_world @ v.co for v in posed.to_mesh().vertices]
    posed.to_mesh_clear()
    ground = min(p.z for p in points)
    sole = [p for p in points if p.z < ground + 0.01]
    return seen(sum(sole, Vector()) / len(sole))[0] * PICTURE_DP[0] * PX_PER_DP


def stride_by_camera():
    """How far the two planted feet move back in the picture in one cycle of the walk, which
    must be live: the nearer foot's part and the farther foot's. Each is measured while its foot
    is flat on the ground, and a foot is planted for half a cycle, at an even pace."""
    half = WALK_FRAMES // 2
    whole_step = half / (FLAT[1] - FLAT[0])
    return tuple(
        (sole_seen(side, start + FLAT[1]) - sole_seen(side, start + FLAT[0])) * whole_step
        for side, start in (("L", 0), ("R", half))
    )


def near_foot_by_frames(folder):
    """The check on that arithmetic, measured in the drawn frames. While the nearer foot is
    flat on the ground and the other one is in the air, the lowest rows of the picture show its
    sole and nothing else, and the middle of the sole can be followed from frame to frame. The
    farther foot cannot be followed this way: the nearer one, in the air, is drawn across it."""
    def sole(frame):
        alpha = alpha_of(os.path.join(folder, "%04d.png" % frame))
        lowest = numpy.nonzero(alpha.max(axis=1) > 0.5)[0][-1]
        columns = numpy.nonzero(alpha[lowest - 6] > 0.5)[0]
        return (columns[0] + columns[-1] + 1) / 2

    return sole(FLAT[1]) - sole(FLAT[0])


# The frames of the walk between which the left foot, the nearer one, is flat on the ground:
# from a quarter to seven tenths of the half cycle it is planted for (walk() in animate_milo.py).
# The right foot does the same half a cycle later.
FLAT = (WALK_FRAMES // 6, WALK_FRAMES // 3)


def main():
    folders = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(folders) != 2:
        raise SystemExit("Name two folders after '--': one for the walk, one for the turn and wave.")
    walk_folder, wave_folder = (os.path.abspath(folder) for folder in folders)
    shown = rig.animation_data.action
    width, height = set_stage()

    walk_frames = write_walk()
    stand_on_the_line(walk_frames)
    near, far = stride_by_camera()
    walk_boxes = render_clip(walk_folder, walk_frames)
    wave_boxes = render_clip(wave_folder, write_turn_and_wave())
    near_flat_drawn = near_foot_by_frames(walk_folder)

    for action in (bpy.data.actions["Greeting_Walk"], bpy.data.actions["Greeting_TurnAndWave"]):
        bpy.data.actions.remove(action)
    rig.animation_data.action = shown

    boxes = walk_boxes + wave_boxes
    left, top = min(b[0] for b in boxes), min(b[1] for b in boxes)
    right, bottom = max(b[2] for b in boxes), max(b[3] for b in boxes)
    standing = wave_boxes[-1]
    print("picture: %d x %d pixels, %g to the dp: %d x %d dp" % (width, height, PX_PER_DP, *PICTURE_DP))
    print("walk: %d frames in %s" % (len(walk_boxes), walk_folder))
    print("turn and wave: %d frames (%d of them the turn) in %s" % (len(wave_boxes), TURN_FRAMES, wave_folder))
    print("the mascot, over every frame: left %d, top %d, right %d, bottom %d" % (left, top, right, bottom))
    print("  walking: left %d, right %d; the picture's middle is %d, so it reaches %d pixels (%.1f dp) ahead"
          % (min(b[0] for b in walk_boxes), max(b[2] for b in walk_boxes), width // 2,
             width // 2 - min(b[0] for b in walk_boxes), (width // 2 - min(b[0] for b in walk_boxes)) / PX_PER_DP))
    print("  soles above the lower edge: %d pixels while walking, %d standing at the end"
          % (height - max(b[3] for b in walk_boxes), height - standing[3]))
    print("  standing at the end: %d pixels high (%.1f dp)" % (standing[3] - standing[1], (standing[3] - standing[1]) / PX_PER_DP))
    print("stride for one cycle: %.1f pixels (%.2f dp): the nearer foot %.1f, the farther foot %.1f"
          % (near + far, (near + far) / PX_PER_DP, near, far))
    print("  the check, the nearer foot from frame %d to frame %d: %.1f pixels in the drawn frames, %.1f by the camera"
          % (*FLAT, near_flat_drawn, near * (FLAT[1] - FLAT[0]) / (WALK_FRAMES // 2)))
    if left <= 0 or top <= 0 or right >= width or bottom >= height:
        raise SystemExit("The mascot touches the picture's edge in some frame: make PICTURE_DP larger.")


if __name__ != "__milo_lib__":
    main()
