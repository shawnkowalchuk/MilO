# export_milo.py - writes the mascot to a .glb file with every clip, then reads the file back and
# prints what is in it. Run it inside Blender after build_milo.py and animate_milo.py.
#
# The app does not carry this file: it plays pictures of the mascot (render_greeting.py). The
# model is kept for whatever comes later.
#
# Only the "MilO_Mascot" collection is exported: no camera, lights or backdrop. The model's own
# pose in the file is the rest pose (arms straight out); every clip stashed on the rig's NLA
# tracks becomes one named animation, sampled on every frame so that everything the rig works
# out for itself (legs following the feet, hoses following their bones, needles following the
# sliders) is written as plain bone movement.

import os
import json
import struct
import bpy

def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


MASCOT = os.path.normpath(os.path.join(here(), ".."))
OUT = os.path.join(MASCOT, "export", "milo_mascot.glb")


def export(path):
    """Writes the model to `path` with every clip."""
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    rig = bpy.data.objects["MilO_Rig"]
    shown = rig.animation_data.action
    rig.animation_data.action = None
    for b in rig.pose.bones:
        b.location = (0.0, 0.0, 0.0)
        b.rotation_euler = (0.0, 0.0, 0.0)
        b.scale = (1.0, 1.0, 1.0)
    for ob in bpy.context.view_layer.objects:
        try:
            ob.select_set(False)
        except RuntimeError:
            pass
    for ob in bpy.data.collections["MilO_Mascot"].objects:
        ob.select_set(True)
    bpy.context.view_layer.objects.active = rig
    os.makedirs(os.path.dirname(path), exist_ok=True)

    wanted = dict(
        filepath=path, export_format="GLB", use_selection=True, export_apply=True, export_yup=True,
        export_cameras=False, export_lights=False, export_extras=False, export_skins=True,
        export_animations=True, export_animation_mode="ACTIONS", export_force_sampling=True,
        export_optimize_animation_size=True, export_optimize_animation_keep_anim_armature=False,
        export_reset_pose_bones=True, export_anim_slide_to_zero=True, export_morph=False,
        # Off: the clips written are the ones stashed on the rig's tracks. On, the exporter
        # looks for every action in the file that moves a bone.
        export_anim_single_armature=False,
    )
    known = bpy.ops.export_scene.gltf.get_rna_type().properties.keys()
    bpy.ops.export_scene.gltf(**{k: v for k, v in wanted.items() if k in known})

    rig.animation_data.action = shown
    return path


def describe(path):
    """What the file holds: its size, where the bytes go, and each clip's name and length."""
    with open(path, "rb") as f:
        data = f.read()
    length = struct.unpack_from("<I", data, 12)[0]
    doc = json.loads(data[20:20 + length].decode("utf-8"))
    views, accessors = doc.get("bufferViews", []), doc.get("accessors", [])

    def size(accessor_ids):
        return sum(views[v]["byteLength"] for v in {accessors[a]["bufferView"] for a in accessor_ids})

    mesh_ids, points, triangles = set(), 0, 0
    for mesh in doc.get("meshes", []):
        for prim in mesh["primitives"]:
            mesh_ids |= set(prim["attributes"].values()) | {prim["indices"]}
            points += accessors[prim["attributes"]["POSITION"]]["count"]
            triangles += accessors[prim["indices"]]["count"] // 3
    anim_ids = {s[k] for a in doc.get("animations", []) for s in a["samplers"] for k in ("input", "output")}
    skin_ids = {s["inverseBindMatrices"] for s in doc.get("skins", []) if "inverseBindMatrices" in s}
    names = [n.get("name", "") for n in doc["nodes"]]

    print("file: %s  %.2f MB (%d bytes)" % (path, len(data) / 1048576, len(data)))
    print("  meshes %.0f KB | animation %.0f KB | skin %.0f KB | json %.0f KB"
          % (size(mesh_ids) / 1024, size(anim_ids) / 1024, size(skin_ids) / 1024, length / 1024))
    print("  %d points, %d triangles, %d draw calls, %d materials, %d joints, %d images"
          % (points, triangles, sum(len(m["primitives"]) for m in doc.get("meshes", [])),
             len(doc.get("materials", [])), sum(len(s["joints"]) for s in doc.get("skins", [])), len(doc.get("images", []))))
    for anim in doc.get("animations", []):
        seconds = max(accessors[s["input"]]["max"][0] for s in anim["samplers"])
        moved = sorted({names[c["target"]["node"]] for c in anim["channels"]})
        needles = [n for n in moved if n.startswith("needle_")]
        print("  clip %-12s %.2f s  %3d channels on %2d bones  needles: %s"
              % (anim["name"], seconds, len(anim["channels"]), len(moved), ", ".join(needles) or "none"))
    return doc


describe(export(OUT))
