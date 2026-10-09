# studio_milo.py - a white backdrop, three lights and a camera for the MilO mascot, plus the
# reference picture standing beside it. Run it inside Blender. It only rebuilds its own two
# collections ("MilO_Studio", "MilO_Reference") and tucks Blender's default light and camera away.

import os
import bpy
import bmesh
from math import radians, sin, cos
from mathutils import Vector

def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


REFERENCE = os.path.normpath(os.path.join(here(), "..", "reference.webp"))


def collection(name):
    coll = bpy.data.collections.get(name)
    if coll is None:
        coll = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(coll)
    stores = {bpy.types.Mesh: bpy.data.meshes, bpy.types.Camera: bpy.data.cameras, bpy.types.Light: bpy.data.lights}
    for ob in list(coll.objects):
        data = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        for kind, store in stores.items():
            if isinstance(data, kind) and data.users == 0:
                store.remove(data)
    return coll


def aim(ob, target):
    ob.rotation_euler = (Vector(target) - ob.location).to_track_quat("-Z", "Y").to_euler()


def studio():
    scene = bpy.context.scene
    defaults = bpy.data.collections.get("Default_Scene")
    for name in ("Light", "Camera"):
        ob = bpy.data.objects.get(name)
        if ob and defaults and defaults not in ob.users_collection:
            for c in list(ob.users_collection):
                c.objects.unlink(ob)
            defaults.objects.link(ob)
            ob.hide_viewport = True
            ob.hide_render = True

    coll = collection("MilO_Studio")

    # backdrop: floor that curves up into a wall, so there is no horizon line
    bm = bmesh.new()
    profile = [(-14.0, 0.0), (5.0, 0.0)]
    profile += [(5.0 + 4.0 * sin(radians(a)), 4.0 - 4.0 * cos(radians(a))) for a in range(6, 91, 6)]
    profile += [(9.0, 16.0)]
    left = [bm.verts.new((-16.0, y, z)) for y, z in profile]
    right = [bm.verts.new((16.0, y, z)) for y, z in profile]
    for i in range(len(profile) - 1):
        bm.faces.new((left[i], right[i], right[i + 1], left[i + 1]))
    me = bpy.data.meshes.new("MilO_Backdrop")
    bm.to_mesh(me)
    bm.free()
    me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))
    paper = bpy.data.materials.get("MilO_Backdrop") or bpy.data.materials.new("MilO_Backdrop")
    try:
        paper.use_nodes = True
    except Exception:
        pass
    bsdf = next((n for n in paper.node_tree.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if bsdf:
        bsdf.inputs["Base Color"].default_value = (0.8, 0.8, 0.8, 1)
        bsdf.inputs["Roughness"].default_value = 0.9
    paper.diffuse_color = (0.8, 0.8, 0.8, 1)
    me.materials.append(paper)
    backdrop = bpy.data.objects.new("MilO_Backdrop", me)
    backdrop.hide_select = True
    coll.objects.link(backdrop)

    # The key is kept low: a high key (or a strong rim) lights the tops of the shoes more than the
    # front of the body, and the shoes then read as a lighter green than the body.
    # The last two lights shine on the backdrop only (light linking), so the paper can be white
    # without washing out the mascot; the mascot still casts its soft shadow on the floor.
    paper_only = bpy.data.collections.get("MilO_Backdrop_Only") or bpy.data.collections.new("MilO_Backdrop_Only")
    if backdrop.name not in paper_only.objects:
        paper_only.objects.link(backdrop)
    for name, loc, power, size, target, backdrop_only in (
        ("MilO_Light_Key", (-3.2, -4.8, 3.4), 430.0, 3.5, (0.0, 0.0, 1.0), False),
        ("MilO_Light_Fill", (4.2, -3.8, 1.8), 130.0, 5.0, (0.0, 0.0, 1.0), False),
        ("MilO_Light_Rim", (1.5, 3.2, 4.2), 110.0, 3.0, (0.0, 0.0, 1.4), False),
        ("MilO_Light_Backdrop", (0.0, 1.5, 5.0), 2600.0, 12.0, (0.0, 9.0, 3.5), True),
        ("MilO_Light_Floor", (0.0, -2.0, 9.0), 400.0, 14.0, (0.0, 0.5, 0.0), True),
    ):
        light = bpy.data.lights.new(name, "AREA")
        light.energy = power
        light.size = size
        ob = bpy.data.objects.new(name, light)
        ob.location = loc
        aim(ob, target)
        coll.objects.link(ob)
        if backdrop_only:
            ob.light_linking.receiver_collection = paper_only

    cam = bpy.data.cameras.new("MilO_Camera")
    cam.lens = 85.0
    cam_ob = bpy.data.objects.new("MilO_Camera", cam)
    cam_ob.location = (0.62, -5.7, 1.38)
    aim(cam_ob, (-0.03, 0.0, 1.0))
    coll.objects.link(cam_ob)
    scene.camera = cam_ob

    world = bpy.data.worlds.get("MilO_World") or bpy.data.worlds.new("MilO_World")
    try:
        world.use_nodes = True
    except Exception:
        pass
    background = next((n for n in world.node_tree.nodes if n.type == "BACKGROUND"), None)
    if background:
        background.inputs[0].default_value = (1.0, 1.0, 1.0, 1.0)
        background.inputs[1].default_value = 0.32
    scene.world = world

    scene.render.resolution_x = 1200
    scene.render.resolution_y = 1200
    scene.render.resolution_percentage = 100
    scene.view_settings.view_transform = "Standard"
    for prop, value in (("use_raytracing", True), ("taa_render_samples", 64)):
        try:
            setattr(scene.eevee, prop, value)
        except Exception:
            pass

    # the picture being copied, standing to one side at the same scale; it never renders
    refs = collection("MilO_Reference")
    if os.path.exists(REFERENCE) or bpy.data.images.get("MilO_Reference"):
        img = bpy.data.images.get("MilO_Reference")
        if img is None:
            img = bpy.data.images.load(REFERENCE)
            img.name = "MilO_Reference"
            img.pack()
        board = bpy.data.objects.new("MilO_Reference_Picture", None)
        board.empty_display_type = "IMAGE"
        board.data = img
        board.empty_display_size = 2.18
        board.rotation_euler = (radians(90), 0.0, 0.0)
        board.location = (-3.1, 0.6, 1.02)
        board.hide_render = True
        refs.objects.link(board)
    print("studio ready: camera", tuple(round(v, 2) for v in cam_ob.location))


studio()
