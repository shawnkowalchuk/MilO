# build_milo.py - rebuilds the MilO mascot (model + rig) from scratch inside the "MilO_Mascot"
# collection. Run it inside Blender. It only ever touches objects in that collection.
#
# The character faces -Y, stands on z = 0 and is about 2 units tall. ".L" is the character's own
# left (+X). Everything is built in a neutral rest pose: arms straight out, hands open, palms down.

import os
import bpy
import bmesh
from math import radians, sin, cos, pi
from mathutils import Vector, Matrix

COLL = "MilO_Mascot"
def here():
    """This script's folder: from its own path, or beside the .blend when it is run from
    Blender's text editor."""
    path = globals().get("__file__", "")
    return os.path.dirname(os.path.abspath(path)) if os.path.isfile(path) else bpy.path.abspath("//scripts")


# The app's own typeface, for the numbers on the dials.
SORA = os.path.normpath(os.path.join(here(), "..", "..", "..", "app", "src", "main", "res", "font", "sora.ttf"))

# ---------------------------------------------------------------- dimensions
BODY_W, BODY_D, BODY_H = 1.06, 0.44, 1.00
BODY_Z0 = 0.72
BODY_C = Vector((0.0, 0.0, BODY_Z0 + BODY_H / 2))
FRONT_Y = -BODY_D / 2
BODY_R = 0.085

GAUGE_Y = -0.30  # world y of both dial faces
GAUGE_BACK_Y = 0.19
SPEEDO = dict(key="Speedo", bone="speedo", cx=-0.19, cz=1.69, R=0.33, Rb=0.262, wr=0.024, hz=0.030)
TACH = dict(key="RPM", bone="rpm", cx=0.38, cz=1.57, R=0.225, Rb=0.178, wr=0.017, hz=0.022)

# The letter M of Sora at weight 700, as the app's own mark draws it
# (app/src/main/res/drawable/ic_launcher_foreground.xml). Where the font's outline crosses itself
# at the two inner corners, the crossing point is used, so the shape is one simple outline.
M_OUTLINE = [
    (41.89, 64.95), (41.89, 43.05), (48.67, 43.05), (53.67, 55.37), (54.24, 55.37), (59.2, 43.05),
    (66.11, 43.05), (66.11, 64.95), (61.23, 64.95), (61.23, 48.06), (56.15, 60.51), (51.45, 60.51),
    (46.4, 48.176), (46.4, 64.95),
]
M_HEIGHT = 0.38
M_CX, M_CZ = 0.0, 1.07
M_DEPTH = 0.04
M_CHAMFER = 0.012

LEG_X, HIP_Z, KNEE_Z, ANKLE_Z = 0.27, 0.74, 0.59, 0.44
LEG_R0, LEG_R1 = 0.066, 0.078
SOLE_T = 0.07
SHOE_HW, SHOE_HL, SHOE_H, SHOE_CY = 0.232, 0.33, 0.33, -0.125
SHOE_TAPER = 0.10

ARM_Z, SHOULDER_X, ELBOW_X, WRIST_X = 1.17, 0.50, 0.78, 1.04
ARM_R = 0.05
PALM_C = Vector((1.245, 0.0, ARM_Z))
PALM_SIZE = (0.27, 0.34, 0.20)
PALM_R = 0.088
CUFF_X, CUFF_R, CUFF_r = WRIST_X + 0.045, 0.112, 0.058
FINGER_X = 1.355
FINGER_R = 0.062
FINGERS = [("finger_a", -0.113, 1.0), ("finger_b", 0.0, 1.08), ("finger_c", 0.113, 0.94)]
FINGER_L1, FINGER_L2 = 0.095, 0.085
THUMB_0 = Vector((1.20, -0.135, ARM_Z - 0.005))
THUMB_DIR = Vector((0.5, -0.866, 0.0)).normalized()
THUMB_R = 0.066
THUMB_L1, THUMB_L2 = 0.11, 0.10

HOSE_BONES = ("upper_arm", "forearm", "thigh", "shin")
HOSE_STEPS = 5


# ---------------------------------------------------------------- small helpers
def arc(a0, a1, n):
    return [radians(a0 + (a1 - a0) * i / n) for i in range(n + 1)]


def srgb(h):
    h = h.lstrip("#")

    def lin(c):
        c /= 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    return (lin(int(h[0:2], 16)), lin(int(h[2:4], 16)), lin(int(h[4:6], 16)), 1.0)


def z_to(direction):
    return Vector((0, 0, 1)).rotation_difference(Vector(direction).normalized()).to_matrix().to_4x4()


def reset_collection(name):
    coll = bpy.data.collections.get(name)
    if coll is None:
        coll = bpy.data.collections.new(name)
        bpy.context.scene.collection.children.link(coll)
    removers = {
        bpy.types.Mesh: bpy.data.meshes, bpy.types.Armature: bpy.data.armatures,
        bpy.types.Camera: bpy.data.cameras, bpy.types.Light: bpy.data.lights,
    }
    for ob in list(coll.objects):
        data = ob.data
        bpy.data.objects.remove(ob, do_unlink=True)
        if data is not None and data.users == 0:
            for kind, store in removers.items():
                if isinstance(data, kind):
                    store.remove(data)
    return coll


# ---------------------------------------------------------------- materials
def principled(name, color, rough=0.4, metal=0.0, coat=0.0, emit=0.0):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    bsdf = next((n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"), None)
    if bsdf is None:
        nt.nodes.clear()
        bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
        out = nt.nodes.new("ShaderNodeOutputMaterial")
        nt.links.new(bsdf.outputs[0], out.inputs[0])

    def put(key, val):
        if key in bsdf.inputs:
            bsdf.inputs[key].default_value = val

    put("Base Color", color)
    put("Roughness", rough)
    put("Metallic", metal)
    put("Coat Weight", coat)
    put("Coat Roughness", 0.06)
    put("Emission Color", color)
    put("Emission Strength", emit)
    m.diffuse_color = color
    m.roughness = rough
    m.metallic = metal
    return m


def glass_material(name):
    # A plain see-through gloss on the standard shader, so a glTF export carries it unchanged.
    m = principled(name, (0.02, 0.02, 0.02, 1.0), rough=0.03)
    bsdf = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Alpha"].default_value = 0.14
    m.diffuse_color = (1.0, 1.0, 1.0, 0.14)
    try:
        m.surface_render_method = "BLENDED"
    except Exception:
        pass
    return m


def make_materials():
    return {
        "green": principled("MilO_Green", srgb("#34BA16"), rough=0.22, coat=0.4),
        "black": principled("MilO_Black_Rubber", (0.012, 0.012, 0.013, 1), rough=0.42),
        "gloss": principled("MilO_Black_Gloss", (0.004, 0.004, 0.005, 1), rough=0.12, coat=0.5),
        "white": principled("MilO_White_Glove", (0.78, 0.78, 0.78, 1), rough=0.32, coat=0.2),
        "chrome": principled("MilO_Chrome", (0.88, 0.89, 0.9, 1), rough=0.1, metal=1.0),
        "mark": principled("MilO_Dial_White", (0.95, 0.95, 0.95, 1), rough=0.5, emit=0.6),
        "dial_green": principled("MilO_Dial_Green", srgb("#35D21C"), rough=0.5, emit=0.8),
        "red": principled("MilO_Red", srgb("#F01E12"), rough=0.35, emit=0.5),
        "glass": glass_material("MilO_Glass"),
    }


# ---------------------------------------------------------------- mesh building blocks
def lathe(bm, profile, segs=32, xf=None, closed=False, mi=0):
    """Revolve a profile of (radius, height) points around Z. A radius of 0 makes a pole."""
    rings, new_verts = [], []
    for rho, z in profile:
        if abs(rho) < 1e-9:
            ring = [bm.verts.new((0.0, 0.0, z))]
        else:
            ring = [bm.verts.new((rho * cos(2 * pi * i / segs), rho * sin(2 * pi * i / segs), z))
                    for i in range(segs)]
        rings.append(ring)
        new_verts += ring
    n = len(rings)
    pairs = [(i, i + 1) for i in range(n - 1)] + ([(n - 1, 0)] if closed else [])
    for a, b in pairs:
        ra, rb = rings[a], rings[b]
        if len(ra) == 1 and len(rb) == 1:
            continue
        for i in range(segs):
            j = (i + 1) % segs
            if len(ra) == 1:
                f = bm.faces.new((ra[0], rb[i], rb[j]))
            elif len(rb) == 1:
                f = bm.faces.new((ra[i], rb[0], ra[j]))
            else:
                f = bm.faces.new((ra[i], rb[i], rb[j], ra[j]))
            f.material_index = mi
    if xf is not None:
        bmesh.ops.transform(bm, matrix=xf, verts=new_verts)
    return new_verts


def capsule(bm, p0, p1, r, segs=18, hemi=5, mi=0):
    p0, p1 = Vector(p0), Vector(p1)
    length = (p1 - p0).length
    prof = [(r * cos(a), r * sin(a)) for a in arc(-90, 0, hemi)]
    prof += [(r * cos(a), length + r * sin(a)) for a in arc(0, 90, hemi)]
    prof[0], prof[-1] = (0.0, -r), (0.0, length + r)
    return lathe(bm, prof, segs, Matrix.Translation(p0) @ z_to(p1 - p0), mi=mi)


def tube(bm, p0, p1, r0, r1, rings=16, segs=20, mi=0):
    p0, p1 = Vector(p0), Vector(p1)
    length = (p1 - p0).length
    prof = [(0.0, 0.0)]
    prof += [(r0 + (r1 - r0) * i / rings, length * i / rings) for i in range(rings + 1)]
    prof += [(0.0, length)]
    return lathe(bm, prof, segs, Matrix.Translation(p0) @ z_to(p1 - p0), mi=mi)


def torus(bm, center, axis, big_r, small_r, segs=36, msegs=14, mi=0):
    prof = [(big_r + small_r * cos(2 * pi * i / msegs), small_r * sin(2 * pi * i / msegs))
            for i in range(msegs)]
    return lathe(bm, prof, segs, Matrix.Translation(Vector(center)) @ z_to(axis), closed=True, mi=mi)


def rounded_box(bm, center, size, radius, segs=5):
    before = set(bm.verts)
    made = bmesh.ops.create_cube(bm, size=1.0)["verts"]
    bmesh.ops.scale(bm, vec=Vector(size), verts=made)
    edges = list({e for v in made for e in v.link_edges})
    bmesh.ops.bevel(bm, geom=edges, offset=radius, offset_type="OFFSET", segments=segs,
                    profile=0.5, affect="EDGES", clamp_overlap=True)
    mine = [v for v in bm.verts if v not in before]
    bmesh.ops.translate(bm, vec=Vector(center), verts=mine)
    return mine


def finish(coll, name, bm, mats, sharp=40, recalc=True):
    if recalc:
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces[:])
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    for m in mats:
        me.materials.append(m)
    me.polygons.foreach_set("use_smooth", [True] * len(me.polygons))
    if sharp:
        me.set_sharp_from_angle(angle=radians(sharp))
    me.update()
    ob = bpy.data.objects.new(name, me)
    coll.objects.link(ob)
    return ob


def text_mesh(body, size, font, embolden=0.0):
    """The letters as a flat mesh in the XY plane, centred on its own bounding box."""
    cu = bpy.data.curves.new("tmp_text", "FONT")
    cu.body = body
    cu.size = size
    cu.font = font
    cu.offset = embolden
    cu.resolution_u = 6
    ob = bpy.data.objects.new("tmp_text", cu)
    bpy.context.scene.collection.objects.link(ob)
    bpy.context.view_layer.update()
    dg = bpy.context.evaluated_depsgraph_get()
    me = bpy.data.meshes.new_from_object(ob.evaluated_get(dg))
    bpy.data.objects.remove(ob, do_unlink=True)
    bpy.data.curves.remove(cu)
    xs = [v.co.x for v in me.vertices]
    ys = [v.co.y for v in me.vertices]
    me.transform(Matrix.Translation((-(min(xs) + max(xs)) / 2, -(min(ys) + max(ys)) / 2, 0.0)))
    return me


def merge(bm, me, xf, mi):
    me.transform(xf)
    n0 = len(bm.faces)
    bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    for f in bm.faces[n0:]:
        f.material_index = mi
    bpy.data.meshes.remove(me)


def offset_outline(pts, d):
    n = len(pts)
    area = sum(pts[i].x * pts[(i + 1) % n].y - pts[(i + 1) % n].x * pts[i].y for i in range(n)) / 2
    sign = 1.0 if area > 0 else -1.0
    out = []
    for i in range(n):
        p0, p1, p2 = pts[i - 1], pts[i], pts[(i + 1) % n]
        e1, e2 = (p1 - p0).normalized(), (p2 - p1).normalized()
        n1 = Vector((e1.y, -e1.x)) * sign
        n2 = Vector((e2.y, -e2.x)) * sign
        out.append(p1 + (n1 + n2) / max(1.0 + n1.dot(n2), 0.25) * d)
    return out


# ---------------------------------------------------------------- body
def build_body(coll, mats):
    bm = bmesh.new()
    rounded_box(bm, BODY_C, (BODY_W, BODY_D, BODY_H), BODY_R, 8)
    body = finish(coll, "Body", bm, [mats["green"], mats["gloss"]])

    # The M is cut into the front: a chamfered rim, then straight walls down to a flat floor.
    s = M_HEIGHT / 21.9
    pts = [Vector(((x - 54.0) * s, -(y - 54.0) * s)) for x, y in M_OUTLINE]
    lead = 0.02
    layers = [
        (offset_outline(pts, M_CHAMFER + lead), FRONT_Y - lead),
        (pts, FRONT_Y + M_CHAMFER),
        (pts, FRONT_Y + M_DEPTH),
    ]
    bm = bmesh.new()
    rings = [[bm.verts.new((p.x + M_CX, y, p.y + M_CZ)) for p in outline] for outline, y in layers]
    n = len(pts)
    caps = [bm.faces.new(rings[0]), bm.faces.new(rings[-1])]
    for a, b in zip(rings[:-1], rings[1:]):
        for i in range(n):
            j = (i + 1) % n
            bm.faces.new((a[i], b[i], b[j], a[j]))
    # Triangles only: the rim's faces are not flat, and the exact solver returns nothing for those.
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    cutter = finish(coll, "tmp_M_cutter", bm, [], sharp=None)

    uncut = len(body.data.vertices)
    cut = None
    for solver in ("EXACT", "MANIFOLD", "FLOAT"):
        mod = body.modifiers.new("cut", "BOOLEAN")
        mod.operation = "DIFFERENCE"
        mod.object = cutter
        try:
            mod.solver = solver
        except TypeError:
            body.modifiers.remove(mod)
            continue
        bpy.context.view_layer.update()
        dg = bpy.context.evaluated_depsgraph_get()
        result = bpy.data.meshes.new_from_object(body.evaluated_get(dg))
        body.modifiers.remove(mod)
        if len(result.vertices) > uncut:
            cut = result
            print("M cut with the %s solver" % solver)
            break
        bpy.data.meshes.remove(result)
    if cut is None:
        raise RuntimeError("The M could not be cut into the body with any solver")
    old = body.data
    body.data = cut
    bpy.data.meshes.remove(old)
    cut.name = body.name
    cutter_mesh = cutter.data
    bpy.data.objects.remove(cutter, do_unlink=True)
    bpy.data.meshes.remove(cutter_mesh)

    # Walls and floor of the letter are glossy black; the chamfered rim stays green.
    half_w = M_HEIGHT * (24.22 / 21.9) / 2 + 0.03
    half_h = M_HEIGHT / 2 + 0.03
    limit = FRONT_Y + M_CHAMFER * 0.9
    black = 0
    for p in cut.polygons:
        c = p.center
        inside = abs(c.x - M_CX) < half_w and abs(c.z - M_CZ) < half_h and c.y < FRONT_Y + M_DEPTH + 0.01
        if inside and min(cut.vertices[i].co.y for i in p.vertices) > limit:
            p.material_index = 1
            black += 1
    cut.polygons.foreach_set("use_smooth", [True] * len(cut.polygons))
    cut.set_sharp_from_angle(angle=radians(35))
    cut.update()
    return body, black


# ---------------------------------------------------------------- gauges (the eyes)
def gauge_frame(g):
    # Gauge-local x is world x, local y is world z (up on the dial), local z points at the viewer.
    return Matrix.Translation((g["cx"], GAUGE_Y, g["cz"])) @ Matrix.Rotation(radians(90), 4, "X")


def dial_angle(g, value):
    """Angle on the dial, counter-clockwise from 3 o'clock, for a speed or an engine speed."""
    return radians(210.0 - (value if g is SPEEDO else value * 30.0))


def build_gauge(coll, mats, g, font):
    G = gauge_frame(g)
    R, Rb, wr, hz = g["R"], g["Rb"], g["wr"], g["hz"]
    Rv = Rb - wr
    key = g["key"]
    zb = -(GAUGE_BACK_Y - GAUGE_Y)

    # housing (green) and bezel (chrome)
    bm = bmesh.new()
    e = R * 0.2
    prof = [(0.0, zb)]
    prof += [(R - e + e * sin(a), zb + e - e * cos(a)) for a in arc(0, 90, 6)]
    prof += [(R - e + e * cos(a), -e + e * sin(a)) for a in arc(0, 90, 6)]
    prof += [(0.0, 0.0)]
    lathe(bm, prof, 64, G, mi=0)
    ring = [(Rb + wr * cos(2 * pi * i / 16), 0.01 + hz * sin(2 * pi * i / 16)) for i in range(16)]
    lathe(bm, ring, 64, G, closed=True, mi=1)
    housing = finish(coll, "Eye_%s_Housing" % key, bm, [mats["green"], mats["chrome"]])

    # dial: black face, green band, red zone, ticks, numbers
    bm = bmesh.new()
    lathe(bm, [(0.0, 0.004), (Rb, 0.004)], 64, mi=0)

    def band(r0, r1, v0, v1, z, mi, steps=48):
        a0, a1 = dial_angle(g, v0), dial_angle(g, v1)
        inner, outer = [], []
        for i in range(steps + 1):
            a = a0 + (a1 - a0) * i / steps
            inner.append(bm.verts.new((r0 * cos(a), r0 * sin(a), z)))
            outer.append(bm.verts.new((r1 * cos(a), r1 * sin(a), z)))
        for i in range(steps):
            bm.faces.new((inner[i], inner[i + 1], outer[i + 1], outer[i])).material_index = mi

    def tick(value, r0, r1, w, z=0.0075):
        a = dial_angle(g, value)
        er, et = Vector((cos(a), sin(a), 0.0)), Vector((-sin(a), cos(a), 0.0))
        up = Vector((0.0, 0.0, z))
        quad = [er * r0 - et * w / 2, er * r0 + et * w / 2, er * r1 + et * w / 2, er * r1 - et * w / 2]
        bm.faces.new([bm.verts.new(q + up) for q in quad]).material_index = 1

    def label(words, size, pos, embolden=0.0):
        merge(bm, text_mesh(words, size, font, embolden), Matrix.Translation((pos[0], pos[1], 0.0075)), 1)

    if g is SPEEDO:
        band(0.925 * Rv, 0.965 * Rv, 0, 240, 0.006, 2)
        for v in range(0, 241, 10):
            major = v % 20 == 0
            tick(v, (0.75 if major else 0.82) * Rv, 0.90 * Rv, 0.007 if major else 0.004)
        for v in range(0, 241, 40):
            a = dial_angle(g, v)
            label(str(v), 0.046, (0.57 * Rv * cos(a), 0.57 * Rv * sin(a)), 0.0012)
        label("km/h", 0.042, (0.0, -0.46 * Rv), 0.001)
    else:
        band(0.925 * Rv, 0.965 * Rv, 0, 6.5, 0.006, 2)
        band(0.86 * Rv, 0.965 * Rv, 6.5, 8, 0.006, 3, steps=12)
        for i in range(0, 17):
            major = i % 2 == 0
            tick(i / 2, (0.75 if major else 0.82) * Rv, 0.90 * Rv, 0.006 if major else 0.0035)
        for v in range(0, 9):
            a = dial_angle(g, v)
            label(str(v), 0.038, (0.60 * Rv * cos(a), 0.60 * Rv * sin(a)), 0.001)
        label("RPM", 0.03, (0.0, -0.40 * Rv), 0.001)
        label("x1000", 0.02, (0.0, -0.62 * Rv), 0.0006)
    bmesh.ops.transform(bm, matrix=G, verts=bm.verts[:])
    dial = finish(coll, "Eye_%s_Dial" % key, bm, [mats["gloss"], mats["mark"], mats["dial_green"], mats["red"]],
                  sharp=None, recalc=False)

    # needle (points at 12 o'clock at rest) and its centre cap
    bm = bmesh.new()
    length, hw, tail = 0.82 * Rv, 0.046 * Rv, 0.17 * Rv
    z0, z1 = 0.018, 0.026
    flat = [(-hw, -tail), (hw, -tail), (hw * 0.25, length), (-hw * 0.25, length)]
    lo = [bm.verts.new((x, y, z0)) for x, y in flat]
    hi = [bm.verts.new((x, y, z1)) for x, y in flat]
    bm.faces.new(lo)
    bm.faces.new(hi)
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((lo[i], lo[j], hi[j], hi[i]))
    rc, capz, caph = 0.15 * Rv, 0.016, 0.125 * Rv
    cap = [(0.0, capz), (rc, capz)]
    cap += [(rc * cos(a), capz + caph * 0.4 + caph * 0.6 * sin(a)) for a in arc(0, 90, 5)]
    lathe(bm, cap, 32, mi=1)
    bmesh.ops.transform(bm, matrix=G, verts=bm.verts[:])
    needle = finish(coll, "Eye_%s_Needle" % key, bm, [mats["red"], mats["gloss"]], sharp=50)

    # glass: a shallow dome over the dial
    bm = bmesh.new()
    rg, ze, zc = Rv + 0.006, 0.9 * hz, 0.9 * hz + 0.14 * Rv
    prof = [(rg * i / 8, ze + (zc - ze) * (1 - (i / 8) ** 2)) for i in range(0, 9)]
    lathe(bm, prof, 64, G)
    glass = finish(coll, "Eye_%s_Glass" % key, bm, [mats["glass"]], sharp=None, recalc=False)
    glass.visible_shadow = False
    return housing, dial, needle, glass


# ---------------------------------------------------------------- limbs, hands, shoes (left side)
def build_leg_bm():
    bm = bmesh.new()
    x = LEG_X
    tube(bm, (x, 0, HIP_Z + 0.07), (x, 0, ANKLE_Z - 0.08), LEG_R0, LEG_R1, rings=18)
    return bm


def build_arm_bm():
    bm = bmesh.new()
    tube(bm, (SHOULDER_X - 0.06, 0, ARM_Z), (WRIST_X + 0.03, 0, ARM_Z), ARM_R, ARM_R, rings=20)
    return bm


def build_shoe_bm():
    bm = bmesh.new()
    # upper: half an egg, larger at the toe
    # A ball cut below its middle, so the widest part sits a little above the sole.
    low = sin(radians(-24))
    dome = [(0.0, -0.18), (cos(radians(-24)), -0.18)]
    dome += [(cos(a), (sin(a) - low) / (1 - low)) for a in arc(-24, 90, 14)]
    dome[-1] = (0.0, 1.0)
    for v in lathe(bm, dome, 40, mi=0):
        x, y, z = v.co
        k = 1.0 - SHOE_TAPER * y
        v.co = Vector((LEG_X + x * SHOE_HW * k, SHOE_CY + y * SHOE_HL, SOLE_T + z * SHOE_H))
    # sole
    e, ez = 0.06, 0.3
    sole = [(0.0, 0.0)]
    sole += [(1 - e + e * sin(a), ez - ez * cos(a)) for a in arc(0, 90, 4)]
    sole += [(1 - e + e * cos(a), 1 - ez + ez * sin(a)) for a in arc(0, 90, 4)]
    sole += [(0.0, 1.0)]
    for v in lathe(bm, sole, 40, mi=1):
        x, y, z = v.co
        k = 1.0 - SHOE_TAPER * y
        v.co = Vector((LEG_X + x * (SHOE_HW - 0.004) * k, SHOE_CY + y * (SHOE_HL - 0.004), z * SOLE_T))
    # the rolled collar round the ankle
    torus(bm, (LEG_X, 0, ANKLE_Z - 0.01), (0, 0, 1), 0.10, 0.055, mi=0)
    return bm


HAND_PARTS = {1: "hand"}


def build_hand_bm():
    bm = bmesh.new()
    layer = bm.verts.layers.int.new("part")

    def tag(pid, bone):
        HAND_PARTS[pid] = bone
        for v in bm.verts:
            if v[layer] == 0:
                v[layer] = pid

    torus(bm, (CUFF_X, 0, ARM_Z), (1, 0, 0), CUFF_R, CUFF_r)
    rounded_box(bm, PALM_C, PALM_SIZE, PALM_R, 5)
    tag(1, "hand")
    pid = 2
    for name, y, k in FINGERS:
        a = Vector((FINGER_X, y, ARM_Z))
        b = a + Vector((FINGER_L1 * k, 0, 0))
        c = b + Vector((FINGER_L2 * k, 0, 0))
        capsule(bm, a, b, FINGER_R)
        tag(pid, name + ".01")
        capsule(bm, b, c, FINGER_R * 0.96)
        tag(pid + 1, name + ".02")
        pid += 2
    t1 = THUMB_0 + THUMB_DIR * THUMB_L1
    t2 = t1 + THUMB_DIR * THUMB_L2
    capsule(bm, THUMB_0, t1, THUMB_R)
    tag(pid, "thumb.01")
    capsule(bm, t1, t2, THUMB_R * 0.96)
    tag(pid + 1, "thumb.02")
    return bm


def mirrored(coll, ob, name):
    me = ob.data.copy()
    me.name = name
    me.transform(Matrix.Scale(-1.0, 4, (1, 0, 0)))
    me.flip_normals()
    me.update()
    twin = bpy.data.objects.new(name, me)
    coll.objects.link(twin)
    return twin


# ---------------------------------------------------------------- rig
def build_rig(coll):
    data = bpy.data.armatures.new("MilO_Rig")
    arm = bpy.data.objects.new("MilO_Rig", data)
    coll.objects.link(arm)
    for o in bpy.context.view_layer.objects:
        try:
            o.select_set(False)
        except RuntimeError:
            pass
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = data.edit_bones

    def add(name, head, tail, parent=None, connect=False, z=None, deform=True):
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
        if z is not None:
            b.align_roll(Vector(z))
        b.use_deform = deform
        return b

    add("root", (0, 0, 0), (0, 0.45, 0), z=(0, 0, 1), deform=False)
    add("body", (0, 0, BODY_Z0), (0, 0, BODY_Z0 + 0.5), "root", z=(0, -1, 0))
    for g in (SPEEDO, TACH):
        # Both point out of the dial, so turning one about its own length (local Y) spins it.
        add("eye_" + g["bone"], (g["cx"], 0.0, g["cz"]), (g["cx"], -0.2, g["cz"]), "body", z=(0, 0, 1))
        add("needle_" + g["bone"], (g["cx"], GAUGE_Y, g["cz"]), (g["cx"], GAUGE_Y - 0.1, g["cz"]),
            "eye_" + g["bone"], z=(0, 0, 1))

    for side, sx in ((".L", 1.0), (".R", -1.0)):
        def P(x, y, z, sx=sx):
            return (sx * x, y, z)

        down = (0, 0, -1)
        add("thigh" + side, P(LEG_X, 0, HIP_Z), P(LEG_X, -0.015, KNEE_Z), "body", z=(0, -1, 0))
        add("shin" + side, P(LEG_X, -0.015, KNEE_Z), P(LEG_X, 0, ANKLE_Z), "thigh" + side, True, z=(0, -1, 0))
        add("foot" + side, P(LEG_X, 0, 0), P(LEG_X, -0.35, 0), "root", z=(0, 0, 1))
        add("ankle_ik" + side, P(LEG_X, 0, ANKLE_Z), P(LEG_X, 0.12, ANKLE_Z), "foot" + side,
            z=(0, 0, 1), deform=False)

        add("upper_arm" + side, P(SHOULDER_X, 0, ARM_Z), P(ELBOW_X, 0, ARM_Z), "body", z=down)
        add("forearm" + side, P(ELBOW_X, 0, ARM_Z), P(WRIST_X, 0, ARM_Z), "upper_arm" + side, True, z=down)
        add("hand" + side, P(WRIST_X, 0, ARM_Z), P(WRIST_X + 0.2, 0, ARM_Z), "forearm" + side, True, z=down)
        for name, y, k in FINGERS:
            a = Vector((FINGER_X, y, ARM_Z))
            b = a + Vector((FINGER_L1 * k, 0, 0))
            c = b + Vector((FINGER_L2 * k, 0, 0))
            add(name + ".01" + side, P(*a), P(*b), "hand" + side, z=down)
            add(name + ".02" + side, P(*b), P(*c), name + ".01" + side, True, z=down)
        t1 = THUMB_0 + THUMB_DIR * THUMB_L1
        t2 = t1 + THUMB_DIR * THUMB_L2
        add("thumb.01" + side, P(*THUMB_0), P(*t1), "hand" + side, z=down)
        add("thumb.02" + side, P(*t1), P(*t2), "thumb.01" + side, True, z=down)

    # Arms and legs are hoses. Each control bone bends as a curve (a bendy bone), and a row of
    # short ordinary bones follows that curve and carries the mesh. Bendy bones alone do not
    # survive a glTF export; the short bones do, and the limb looks the same.
    hoses = []
    for control in [b.name for b in eb if b.name.split(".")[0] in HOSE_BONES]:
        c = eb[control]
        c.bbone_segments = 12
        c.use_deform = False
        base, side = control.rsplit(".", 1)
        step = (c.tail - c.head) / HOSE_STEPS
        # the lower bone of each limb gets one more, at its far end
        count = HOSE_STEPS + (1 if base in ("forearm", "shin") else 0)
        for i in range(count):
            h = eb.new("hose.%s.%d.%s" % (base, i, side))
            h.head = c.head + step * i
            h.tail = h.head + step
            h.roll = c.roll
            h.parent = c
            h.use_deform = True
            hoses.append((h.name, control, i / HOSE_STEPS))
    bpy.ops.object.mode_set(mode="OBJECT")

    for pb in arm.pose.bones:
        pb.rotation_mode = "XYZ"
    tucked = data.collections.new("Hose (automatic)")
    for name, control, along in hoses:
        follow = arm.pose.bones[name].constraints.new("COPY_TRANSFORMS")
        follow.target = arm
        follow.subtarget = control
        follow.head_tail = along
        follow.use_bbone_shape = True
        tucked.assign(data.bones[name])
    tucked.is_visible = False
    for side in (".L", ".R"):
        ik = arm.pose.bones["shin" + side].constraints.new("IK")
        ik.target = arm
        ik.subtarget = "ankle_ik" + side
        ik.chain_count = 2
        ik.use_stretch = True
        for name in ("thigh", "shin"):
            arm.pose.bones[name + side].ik_stretch = 0.25

    # Two sliders on the rig drive the needles.
    arm["speed_kmh"] = 80.0
    arm["rpm"] = 3.0
    arm.id_properties_ui("speed_kmh").update(min=0.0, max=240.0, soft_min=0.0, soft_max=240.0,
                                             description="Where the speedometer needle points")
    arm.id_properties_ui("rpm").update(min=0.0, max=8.0, soft_min=0.0, soft_max=8.0,
                                       description="Where the RPM needle points, in thousands")
    for bone, prop, expr in (("needle_speedo", "speed_kmh", "radians(120 - v)"),
                             ("needle_rpm", "rpm", "radians(120 - v * 30)")):
        fc = arm.driver_add('pose.bones["%s"].rotation_euler' % bone, 1)
        drv = fc.driver
        drv.type = "SCRIPTED"
        var = drv.variables.new()
        var.name = "v"
        var.type = "SINGLE_PROP"
        var.targets[0].id = arm
        var.targets[0].data_path = '["%s"]' % prop
        drv.expression = expr
    arm.show_in_front = True
    return arm


def bind(ob, arm):
    ob.parent = arm
    mod = ob.modifiers.new("Armature", "ARMATURE")
    mod.object = arm


def rigid(ob, arm, bone):
    ob.vertex_groups.new(name=bone).add(list(range(len(ob.data.vertices))), 1.0, "REPLACE")
    bind(ob, arm)


def skin_parts(ob, arm, side):
    me = ob.data
    vals = [0] * len(me.vertices)
    me.attributes["part"].data.foreach_get("value", vals)
    groups = {}
    for i, pid in enumerate(vals):
        groups.setdefault(pid, []).append(i)
    for pid, idx in groups.items():
        ob.vertex_groups.new(name=HAND_PARTS[pid] + side).add(idx, 1.0, "REPLACE")
    me.attributes.remove(me.attributes["part"])
    bind(ob, arm)


def skin_hose(ob, arm, upper, lower, side, along):
    """A hose mesh: each ring leans on the two short bones it lies between.
    `along` gives a vertex's distance down the limb from its first joint."""
    bones = arm.data.bones
    chain, reach = [], 0.0
    for base in (upper, lower):
        length = bones["%s.%s" % (base, side)].length
        count = sum(1 for b in bones if b.name.startswith("hose.%s." % base) and b.name.endswith("." + side))
        chain += [("hose.%s.%d.%s" % (base, i, side), reach + length * i / HOSE_STEPS) for i in range(count)]
        reach += length
    groups = [ob.vertex_groups.new(name=name) for name, _ in chain]
    marks = [mark for _, mark in chain]
    for v in ob.data.vertices:
        d = along(v.co)
        j = max(0, min(len(marks) - 2, sum(1 for mark in marks if mark <= d) - 1))
        f = max(0.0, min(1.0, (d - marks[j]) / (marks[j + 1] - marks[j])))
        if f < 1.0:
            groups[j].add([v.index], 1.0 - f, "REPLACE")
        if f > 0.0:
            groups[j + 1].add([v.index], f, "REPLACE")
    bind(ob, arm)


# ---------------------------------------------------------------- build
def build():
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    coll = reset_collection(COLL)
    mats = make_materials()
    font = bpy.data.fonts.load(SORA, check_existing=True)

    body, black_faces = build_body(coll, mats)
    eyes = {g["bone"]: build_gauge(coll, mats, g, font) for g in (SPEEDO, TACH)}

    leg_l = finish(coll, "Leg.L", build_leg_bm(), [mats["black"]])
    arm_l = finish(coll, "Arm.L", build_arm_bm(), [mats["black"]])
    shoe_l = finish(coll, "Shoe.L", build_shoe_bm(), [mats["green"], mats["black"]], sharp=50)
    hand_l = finish(coll, "Hand.L", build_hand_bm(), [mats["white"]], sharp=60)
    leg_r, arm_r = mirrored(coll, leg_l, "Leg.R"), mirrored(coll, arm_l, "Arm.R")
    shoe_r, hand_r = mirrored(coll, shoe_l, "Shoe.R"), mirrored(coll, hand_l, "Hand.R")

    rig = build_rig(coll)
    rigid(body, rig, "body")
    # The big flat faces set the shading, so the front stays flat right up to its rounded edges.
    flat = body.modifiers.new("Flat faces", "WEIGHTED_NORMAL")
    flat.keep_sharp = True
    flat.weight = 100
    for name, (housing, dial, needle, glass) in eyes.items():
        for ob in (housing, dial, glass):
            rigid(ob, rig, "eye_" + name)
        rigid(needle, rig, "needle_" + name)
    for side, leg, arm, shoe, hand in ((".L", leg_l, arm_l, shoe_l, hand_l), (".R", leg_r, arm_r, shoe_r, hand_r)):
        skin_hose(leg, rig, "thigh", "shin", side[1:], lambda co: HIP_Z - co.z)
        skin_hose(arm, rig, "upper_arm", "forearm", side[1:], lambda co: abs(co.x) - SHOULDER_X)
        rigid(shoe, rig, "foot" + side)
        skin_parts(hand, rig, side)

    bpy.context.view_layer.update()
    print("built %d objects, %d bones, M faces painted black: %d"
          % (len(coll.objects), len(rig.data.bones), black_faces))
    return rig


build()
