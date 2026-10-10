# render_dials.py - draws what the website's moving needles are made of. On the website the
# standing mascot's two needles keep moving, and the stylesheet turns them: each needle is a
# small picture of its blade, turned about the middle of its dial, over a picture of the dial
# without a blade. Run it after render_clips.py, with Blender WITHOUT its window, and give it
# the SAME height, folder and clips as render_clips.py was given for the website, so that it
# measures the same picture:
#
#   blender -b design/mascot/milo_mascot.blend --python design/mascot/scripts/render_dials.py -- <pixels> <folder> <clip> [<clip> ...]
#
# It writes three files into <folder>/Dials/, and pack_clips.py makes the website's dials.webp
# out of them:
#
#   faces.png    the standing mascot without the blades of its needles. Their caps, the dials'
#                marks and the glass over them are as in every other picture
#   blades.png   the two blades alone, both pointing straight up (120 km/h and 4,000 RPM), on a
#                see-through background, with the caps cut out of them
#   dials.json   where each needle turns in the picture, and how far its blade reaches
#
# A blade is drawn without the glass over it, which would otherwise be drawn round it as a
# haze; pack_clips.py darkens the blade by what the glass takes.
#
# NOTHING IS SAVED. The script takes the needles apart, hides the rest of the mascot and writes
# a clip of its own, all in memory, and Blender closes without saving.

import json
import os
import sys
import types
import bpy
import bmesh
from mathutils import Vector

# Each needle: its name on the website, its object, and the bone it turns with.
NEEDLES = (("speed", "Eye_Speedo_Needle", "needle_speedo"), ("rpm", "Eye_RPM_Needle", "needle_rpm"))
# A needle is one object of two materials: its red blade is the first, its black cap the second.
BLADE = 0
# Where the sliders stand when both blades point straight up.
UP = (120.0, 4.0)


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


drawing = library("render_clips.py")
greeting = drawing.greeting
clips = drawing.clips
rig = drawing.rig
scene = bpy.context.scene


def part(ob, blade):
    """A copy of a needle's mesh with its blade alone, or with everything but its blade."""
    mesh = ob.data.copy()
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if (f.material_index == BLADE) != blade], context="FACES")
    bm.to_mesh(mesh)
    bm.free()
    return mesh


def stand(speed, rpm):
    """The standing pose with the needles where they are told. It is keyed as a clip of one
    frame, as the other pictures' poses are, so that the needles follow their sliders."""
    clips.reset_pose()
    clips.neutral()
    clips.set_gauges(speed, rpm)
    clips.new_action(drawing.DRAWN + "Dials", 0, False)
    greeting.key(0)
    scene.frame_set(0)


def draw(path):
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def where(ob, bone):
    """Where a needle turns in the picture, in pixels from its upper left corner, and how far
    from there its blade reaches. Measured on the blade as it is posed: it lies a little in
    front of the dial, and the camera does not look at the dial squarely."""
    width, height = scene.render.resolution_x, scene.render.resolution_y
    posed = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    points = [posed.matrix_world @ v.co for v in posed.to_mesh().vertices]
    posed.to_mesh_clear()
    axis = rig.matrix_world @ rig.pose.bones[bone].head
    depth = sum(p.y for p in points) / len(points)

    def pixel(point):
        across, up = greeting.seen(point)
        return Vector((across * width, (1 - up) * height))

    pivot = pixel(Vector((axis.x, depth, axis.z)))
    return pivot, max((pixel(p) - pivot).length for p in points)


def main():
    asked = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if len(asked) < 3 or not asked[0].isdigit():
        raise SystemExit("After '--': the height, the folder and the clips that render_clips.py was given for the website.")
    pixels, folder, names = int(asked[0]), os.path.join(os.path.abspath(asked[1]), "Dials"), asked[2:]
    os.makedirs(folder, exist_ok=True)
    width, height, _, _ = drawing.set_picture({name: drawing.write(name) for name in names}, pixels)

    mascot = bpy.data.collections["MilO_Mascot"]
    needles = [(name, bpy.data.objects[ob], bone) for name, ob, bone in NEEDLES]
    blades = {ob: part(ob, True) for _, ob, _ in needles}
    caps = {ob: part(ob, False) for _, ob, _ in needles}

    # The standing mascot without the blades.
    for _, ob, _ in needles:
        ob.data = caps[ob]
    stand(0.0, clips.IDLE_RPM)
    draw(os.path.join(folder, "faces"))

    # The blades alone, straight up. Each cap is there as a hole: it lies over its blade.
    for ob in mascot.objects:
        if ob.type == "MESH":
            ob.hide_render = True
    for _, ob, _ in needles:
        cap = ob.copy()
        cap.data = caps[ob]
        cap.is_holdout = True
        cap.hide_render = False
        mascot.objects.link(cap)
        ob.data = blades[ob]
        ob.hide_render = False
    stand(*UP)
    draw(os.path.join(folder, "blades"))

    found = {"picture": [width, height], "needles": {}}
    for name, ob, bone in needles:
        pivot, reach = where(ob, bone)
        found["needles"][name] = {"turns_at": [round(pivot.x, 2), round(pivot.y, 2)], "reaches": round(reach, 2)}
        print("%s needle: turns at %.2f, %.2f pixels from the picture's upper left corner; its blade reaches %.2f pixels"
              % (name, pivot.x, pivot.y, reach))
    with open(os.path.join(folder, "dials.json"), "w") as f:
        json.dump(found, f, indent=2)
    print("picture: %d x %d pixels; faces.png, blades.png and dials.json in %s" % (width, height, folder))


if __name__ != "__milo_lib__":
    main()
