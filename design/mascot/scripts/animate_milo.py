# animate_milo.py - the MilO mascot's reusable clips. Run it inside Blender after build_milo.py.
#
# Each clip is one action, starting on frame 0 at 24 frames a second, and is also stashed on its
# own muted NLA track so a glTF export writes every clip under its own name:
#
#   Idle         4 s, loops    standing, gently moving
#   Wave         2 s           the app opens
#   ThumbsUp     2 s           a trip was saved
#   Walk         1 s, loops    a trip is being recorded (walks on the spot)
#   Celebrate    3 s           a mileage goal was reached
#   Flex         4.5 s         shows off: both arms, one arm to each side, both again
#   Peace        2.75 s        holds up the peace sign with its left hand
#   RockOn       2.75 s        holds up the horns with its right hand, and nods to the beat
#   WaveUp       0.5 s         the right arm goes up, and stays up: it ends where WaveLoop begins
#   WaveLoop     0.5 s, loops  the arm held up, one wave of the hand; the website loops it
#   Gauge_Speed  2 s           the speed needle alone, 0 to 240: seek to speed / 240 of its length
#   Gauge_RPM    2 s           the RPM needle alone, 0 to 8: seek to rpm / 8 of its length
#
# Every clip but the three loops and WaveUp starts and ends in the same standing pose (NEUTRAL),
# so they can follow one another. The needles move in every clip: each clip keys the rig's two sliders
# ("speed_kmh", "rpm") and the needle bones follow through their drivers.
#
# A clip is a function of time that sets the whole pose; it is keyed on every frame.

import bpy
from math import radians, sin, cos, pi, exp
from mathutils import Vector, Matrix, Euler, Quaternion

FPS = 24
rig = bpy.data.objects["MilO_Rig"]
pb = rig.pose.bones
REST = {b.name: b.matrix_local.to_3x3() for b in rig.data.bones}

FINGER_BONES = ["finger_a.01", "finger_a.02", "finger_b.01", "finger_b.02", "finger_c.01", "finger_c.02"]
ROTATED = ["body", "eye_speedo", "eye_rpm"] + [
    "%s.%s" % (name, side)
    for side in ("L", "R")
    for name in ["foot", "upper_arm", "forearm", "hand", "thumb.01", "thumb.02"] + FINGER_BONES
]
MOVED = ["body", "foot.L", "foot.R"]
SCALED = ["body", "eye_speedo", "eye_rpm"]
IDLE_RPM = 0.9


# ---------------------------------------------------------------- small maths
def V(x, y, z):
    return Vector((x, y, z))


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def smooth(t):
    t = clamp(t)
    return t * t * (3 - 2 * t)


def bump(t, a, b):
    """0 outside a..b, a smooth hill up to 1 in the middle."""
    return sin(pi * clamp((t - a) / (b - a))) ** 2


def hop(t, a, b):
    """The height of a jump from a to b: 0 at both ends, 1 at the top."""
    u = clamp((t - a) / (b - a))
    return 4 * u * (1 - u)


def overshoot(t):
    """0 to 1 with a small overshoot before it settles."""
    t = clamp(t) - 1
    return t * t * (2.70158 * t + 1.70158) + 1


def spring(u):
    """0 to 1, ringing a little as it arrives."""
    return 0.0 if u <= 0 else 1 - exp(-6 * u) * cos(14 * u)


def mirror(v):
    return Vector((-v.x, v.y, v.z))


def turn(v, axis, degrees):
    return Matrix.Rotation(radians(degrees), 3, axis) @ v


def swing(a, b, w):
    """From direction a towards direction b by the shortest way; w may run a little past 1."""
    a, b = a.normalized(), b.normalized()
    axis, angle = a.rotation_difference(b).to_axis_angle()
    return Quaternion(axis, angle * w) @ a


def frame_of(along, palm):
    y = along.normalized()
    z = palm - y * palm.dot(y)
    if z.length < 1e-4:
        z = y.orthogonal()
    z.normalize()
    return Matrix((y.cross(z), y, z)).transposed()


# ---------------------------------------------------------------- arm poses
# All written for the LEFT arm (+X is outwards); the right arm is the mirror image.
# "curl" is two angles for each of the three fingers; "thumb" is thumb.01's x, y, z and thumb.02's x.
# "spread" is one angle for each finger: how far it is turned sideways in the palm's plane, towards
# the thumb. The hand signs need it; every other pose leaves the fingers side by side.
def arm_pose(upper, upper_palm, fore, fore_palm, hand, hand_palm, curl, thumb, spread=(0.0, 0.0, 0.0)):
    return dict(upper=upper, upper_palm=upper_palm, fore=fore, fore_palm=fore_palm,
                hand=hand, hand_palm=hand_palm, curl=curl, thumb=thumb, spread=spread)


RELAXED = arm_pose(V(0.80, -0.05, -0.60), V(0.0, 0.5, -0.8), V(0.30, -0.22, -0.93), V(0.0, 1.0, 0.0),
                   V(0.16, -0.12, -0.98), V(-0.15, 1.0, 0.0), (14, 20, 20, 26, 28, 34), (12, 0, 0, 14))

_fingers = V(-0.22, -0.85, 0.46).normalized()
_thumb_side = (V(0, 0, 1) - _fingers * _fingers.z).normalized()
THUMBS_UP = arm_pose(V(0.80, -0.25, -0.55), V(-0.3, 0.0, -0.9), V(0.15, -0.70, 0.70), V(-1.0, 0.0, 0.0),
                     _fingers, _fingers.cross(_thumb_side),
                     (88, 94, 88, 94, 88, 94), (-5.7, 0, 36.8, 0))

CHEER = arm_pose(V(0.55, -0.08, 0.83), V(0.0, -1.0, 0.0), V(0.38, -0.05, 0.92), V(0.0, -1.0, 0.0),
                 V(0.34, -0.05, 0.94), V(0.0, -1.0, 0.0), (2, 2, 0, 0, 2, 2), (0, 0, 0, 0))

# A fist, and the thumb laid across it.
FIST = (86, 92, 88, 94, 86, 92)
THUMB_ACROSS = (112, 0, 50, 62)
# Flexing: the upper arm out and up, the forearm up, the fist beside the top of the head and
# clear of it, its fingers to the viewer. The arms are short and the hands large: a fist turned
# in towards the head, as a real arm is flexed, would be inside it.
BICEPS = arm_pose(V(0.86, -0.08, 0.50), V(0.30, -0.80, -0.50), V(0.26, -0.14, 0.955), V(-0.45, -0.88, 0.0),
                  V(0.06, -0.12, 0.99), V(-0.50, -0.86, -0.07), FIST, THUMB_ACROSS)
# The other arm of a one-arm flex: the upper arm out, the forearm down.
LOW = arm_pose(V(0.96, -0.05, -0.10), V(-0.10, 0.20, -0.97), V(0.22, -0.16, -0.96), V(-0.95, 0.20, -0.25),
               V(0.12, -0.14, -0.98), V(-0.97, 0.25, -0.15), FIST, THUMB_ACROSS)
# Where an arm passes between those two: out in front, so the fist goes round the body, not through it.
REACH = arm_pose(V(0.93, -0.30, 0.10), V(-0.10, 0.30, -0.95), V(0.70, -0.70, 0.05), V(-0.70, -0.70, -0.20),
                 V(0.55, -0.82, 0.0), V(-0.82, -0.55, -0.20), FIST, THUMB_ACROSS)
# And where it passes on its way up from hanging and back: straight out to the side, so that
# the fists go round the head and not across the dials.
OUT = arm_pose(V(0.98, -0.15, 0.0), V(0.0, 0.0, -1.0), V(0.97, -0.20, 0.10), V(0.0, 0.0, -1.0),
               V(0.95, -0.20, 0.20), V(0.0, 0.0, -1.0), FIST, THUMB_ACROSS)

# The two hand signs: the hand beside the head, fingers up, the palm to the viewer. The mascot has
# three fingers: "a" is next to the thumb, "b" is the middle one, "c" the outer one.
# Peace: a and b up and apart, c curled, the thumb across it.
PEACE = arm_pose(V(0.93, -0.12, 0.30), V(0.0, -1.0, 0.0), V(0.56, -0.20, 0.80), V(0.0, -1.0, 0.0),
                 V(0.08, -0.10, 1.0), V(0.0, -1.0, 0.0), (0, 0, 0, 0, 84, 90), (115, 0, 62, 62),
                 spread=(14.0, -14.0, 0.0))
# The horns: a and c up, b curled under the thumb.
HORNS = arm_pose(V(0.93, -0.14, 0.32), V(0.0, -1.0, 0.0), V(0.54, -0.22, 0.81), V(0.0, -1.0, 0.0),
                 V(0.07, -0.12, 1.0), V(0.0, -1.0, 0.0), (0, 0, 86, 92, 0, 0), (112, 0, 66, 64),
                 spread=(8.0, 0.0, -8.0))


def blend_arms(a, b, w):
    out = {}
    for key in ("upper", "upper_palm", "fore", "fore_palm", "hand", "hand_palm"):
        out[key] = swing(a[key], b[key], w)
    out["curl"] = tuple(clamp(x + (y - x) * w, -10, 96) for x, y in zip(a["curl"], b["curl"]))
    out["thumb"] = tuple(x + (y - x) * w for x, y in zip(a["thumb"], b["thumb"]))
    out["spread"] = tuple(x + (y - x) * w for x, y in zip(a["spread"], b["spread"]))
    return out


def curved(a, by, b, w):
    """From arm a to arm b on a curve that is drawn towards the arm `by` on its way."""
    return blend_arms(blend_arms(a, by, w), blend_arms(by, b, w), w)


def with_fingers(arm, of):
    """One arm with the fingers and thumb of another, for a hand that closes before the arm is up."""
    out = dict(arm)
    for key in ("curl", "thumb", "spread"):
        out[key] = of[key]
    return out


def turned(arm, axis, upper, fore, hand):
    """The same arm with its three parts swung about a world axis by their own angles."""
    out = dict(arm)
    for key, degrees in (("upper", upper), ("fore", fore), ("hand", hand)):
        out[key] = turn(arm[key], axis, degrees)
        out[key + "_palm"] = turn(arm[key + "_palm"], axis, degrees)
    return out


# ---------------------------------------------------------------- setting the rig
def set_rotation(name, basis):
    pb[name].rotation_euler = basis.to_euler("XYZ", pb[name].rotation_euler)


def set_body(right=0.0, up=0.0, forward=0.0, lean=0.0, turn_left=0.0, tilt_left=0.0, stretch=0.0):
    """Moves, leans and stretches the body. Returns its orientation, for the arms that hang off it."""
    b = pb["body"]
    b.location = (-right, up, forward)
    e = Euler((radians(lean), radians(turn_left), radians(-tilt_left)), "XYZ")
    b.rotation_euler = e
    wide = (1.0 / (1.0 + stretch)) ** 0.5
    b.scale = (wide, 1.0 + stretch, wide)
    return REST["body"] @ e.to_matrix()


def set_arm(side, arm, body):
    to_side = (lambda v: v) if side == "L" else mirror
    parent = body
    for bone, key in (("upper_arm", "upper"), ("forearm", "fore"), ("hand", "hand")):
        name = "%s.%s" % (bone, side)
        target = frame_of(to_side(arm[key]), to_side(arm[key + "_palm"]))
        above = rig.data.bones[name].parent.name
        set_rotation(name, (parent @ REST[above].inverted() @ REST[name]).inverted() @ target)
        parent = target
    flip = 1.0 if side == "L" else -1.0
    for i, (bone, angle) in enumerate(zip(FINGER_BONES, arm["curl"])):
        # only a finger's first bone turns sideways; one that is not spread gets a plain 0
        spread = arm["spread"][i // 2] if i % 2 == 0 else 0.0
        pb["%s.%s" % (bone, side)].rotation_euler = (radians(angle), 0.0, radians(flip * spread) if spread else 0.0)
    x1, y1, z1, x2 = arm["thumb"]
    pb["thumb.01." + side].rotation_euler = (radians(x1), radians(flip * y1), radians(flip * z1))
    pb["thumb.02." + side].rotation_euler = (radians(x2), 0.0, 0.0)


def set_foot(side, out=0.04, forward=0.0, up=0.0, toe_out=18.0, toe_up=0.0):
    """The foot turns about the point on the ground under its ankle, so a tipped shoe is lifted
    by what its toe (or heel) would otherwise sink."""
    flip = 1.0 if side == "L" else -1.0
    lift = (0.20 if toe_up > 0 else 0.42) * abs(sin(radians(toe_up)))
    foot = pb["foot." + side]
    foot.location = (-flip * out, forward, up + lift)
    foot.rotation_euler = (radians(toe_up), 0.0, radians(flip * toe_out))


def set_eyes(twist_speedo=0.0, twist_rpm=0.0, pop_speedo=0.0, pop_rpm=0.0):
    for name, twist, pop in (("eye_speedo", twist_speedo, pop_speedo), ("eye_rpm", twist_rpm, pop_rpm)):
        pb[name].rotation_euler = (0.0, radians(twist), 0.0)
        pb[name].scale = (1.0 + pop,) * 3


def set_gauges(speed=0.0, rpm=IDLE_RPM):
    rig["speed_kmh"] = clamp(speed, 0.0, 240.0)
    rig["rpm"] = clamp(rpm, 0.0, 8.0)


def neutral(t=0.0):
    body = set_body()
    set_arm("L", RELAXED, body)
    set_arm("R", RELAXED, body)
    set_foot("L")
    set_foot("R")
    set_eyes()
    set_gauges()


# ---------------------------------------------------------------- the clips
def idle(t):
    w = 2 * pi * t / 4.0
    breath = sin(2 * w)
    body = set_body(up=0.010 * breath, stretch=0.008 * breath, lean=0.6 * sin(2 * w),
                    turn_left=1.5 * sin(w), tilt_left=1.2 * sin(w))
    for side, sway in (("L", sin(w)), ("R", -sin(w))):
        arm = turned(RELAXED, "X", 3 * sway, 5 * sway, 6 * sway)
        arm["curl"] = tuple(c + 3 * breath for c in RELAXED["curl"])
        set_arm(side, arm, body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=2.5 * sin(w), twist_rpm=-2.5 * sin(2 * w), pop_speedo=0.01 * breath, pop_rpm=-0.01 * breath)
    # the engine ticks over: the RPM needle trembles, the speed needle breathes off its stop
    set_gauges(speed=3 - 3 * cos(w), rpm=IDLE_RPM + 0.10 * sin(4 * w) + 0.05 * sin(10 * w))


def waving(e, beat, late):
    """The whole pose of a wave at one moment: e is how far the arm is up (0 hanging, 1 up),
    beat swings the forearm from side to side (-1 to 1), and late does the same for the hand,
    which follows a little behind. Wave, WaveUp and WaveLoop differ only in when they ask."""
    body = set_body(up=0.015 * e * beat * beat, tilt_left=-3.0 * e, lean=-1.0 * e)
    # held well out to the side, so the hand never swings in front of the speedometer
    out = radians(26 + 18 * beat)
    wrist = radians(26 + 18 * beat + 16 * late)
    high = arm_pose(V(0.85, -0.10, 0.50), V(0.0, -1.0, 0.0), V(sin(out), -0.12, cos(out)), V(0.0, -1.0, 0.0),
                    V(sin(wrist), -0.12, cos(wrist)), V(0.0, -1.0, 0.0), (3, 3, 0, 0, 3, 3), (0, 0, 0, 0))
    set_arm("R", blend_arms(RELAXED, high, e), body)
    set_arm("L", turned(RELAXED, "X", -4 * e, -6 * e, -6 * e), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=5 * e + 2 * e * beat, twist_rpm=5 * e - 2 * e * beat, pop_speedo=0.03 * e, pop_rpm=0.03 * e)
    # a little rev with every wave
    set_gauges(speed=e * (34 + 12 * beat), rpm=IDLE_RPM + e * (2.4 + 1.3 * beat))


def wave(t):
    e = smooth(t / 0.40) * (1 - smooth((t - 1.50) / 0.50))
    beat = sin(2 * pi * 2.5 * (t - 0.40))
    late = sin(2 * pi * 2.5 * (t - 0.40) - 0.9)
    waving(e, beat, late)


# One wave of the hand in WaveUp and WaveLoop: half a second, 12 frames, so that the loop closes
# on a frame. (Wave's own is 0.4 seconds, 9.6 frames, and could not be looped frame by frame.)
WAVE_BEAT = 0.5


def wave_up(t):
    """From standing to WaveLoop's first pose in one beat. The arm stays up: it is the way into
    the loop, not a clip to play alone."""
    turn = 2 * pi * (t / WAVE_BEAT - 1)
    waving(smooth(t / WAVE_BEAT), sin(turn), sin(turn - 0.9))


def wave_loop(t):
    turn = 2 * pi * t / WAVE_BEAT
    waving(1.0, sin(turn), sin(turn - 0.9))


def thumbs_up(t):
    fall = 1 - smooth((t - 1.50) / 0.45)
    e = overshoot((t - 0.05) / 0.40) * fall
    stretch = -0.035 * bump(t, 0.0, 0.28) + 0.05 * bump(t, 0.22, 0.62)
    body = set_body(up=0.02 * bump(t, 0.25, 0.65), stretch=stretch, lean=-2.5 * e, tilt_left=-1.5 * e)
    set_arm("R", blend_arms(RELAXED, THUMBS_UP, e), body)
    set_arm("L", RELAXED, body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=-4 * e, twist_rpm=-4 * e, pop_speedo=0.05 * bump(t, 0.25, 0.70), pop_rpm=0.05 * bump(t, 0.30, 0.75))
    # both needles swing up to where the picture has them: 80 km/h and 3,000 RPM
    arrived = spring(t - 0.15) * fall
    set_gauges(speed=80 * arrived, rpm=IDLE_RPM + (3.0 - IDLE_RPM) * arrived)


def walk(t):
    p = t % 1.0

    def step(phase):
        phase %= 1.0
        if phase < 0.5:  # on the ground, sliding back
            s = phase / 0.5
            return 0.15 * (1 - 2 * s), 0.0, 18 * (1 - smooth(s / 0.25)) - 22 * smooth((s - 0.7) / 0.3)
        s = (phase - 0.5) / 0.5  # in the air, swinging forward
        return 0.15 * (2 * smooth(s) - 1), 0.12 * sin(pi * s), -22 * (1 - smooth(s / 0.4)) + 18 * smooth((s - 0.55) / 0.45)

    w = 2 * pi * p
    body = set_body(right=-0.02 * sin(w), up=-0.015 - 0.02 * cos(2 * w), lean=4 + 1.5 * cos(2 * w),
                    turn_left=-5 * cos(w), tilt_left=2.5 * sin(w))
    for side, phase, reach in (("L", p, -cos(w)), ("R", p + 0.5, cos(w))):
        forward, up, toe_up = step(phase)
        set_foot(side, out=0.03, forward=forward, up=up, toe_out=10.0, toe_up=toe_up)
        # each arm swings with the opposite leg, and bends more as it comes forward
        bend = 0.5 + 0.5 * reach
        arm = turned(RELAXED, "X", -26 * reach, -26 * reach - 22 * bend, -26 * reach - 30 * bend)
        arm["curl"] = (30, 35, 35, 40, 40, 45)
        set_arm(side, arm, body)
    set_eyes(twist_speedo=2 * sin(w), twist_rpm=2 * sin(w), pop_speedo=0.015 * cos(2 * w), pop_rpm=-0.015 * cos(2 * w))
    # cruising: both needles bob with every step
    set_gauges(speed=30 + 5 * sin(2 * w + 0.5), rpm=2.2 + 0.6 * sin(2 * w + 2.0) + 0.15 * sin(4 * w))


def celebrate(t):
    first, second = hop(t, 0.32, 0.88), hop(t, 1.10, 1.54)
    height = (0.42 * first + 0.24 * second
              - 0.11 * bump(t, 0.0, 0.34) - 0.07 * bump(t, 0.88, 1.12) - 0.06 * bump(t, 1.54, 1.80))
    stretch = (-0.10 * bump(t, 0.0, 0.34) + 0.07 * bump(t, 0.30, 0.55) - 0.09 * bump(t, 0.86, 1.12)
               + 0.05 * bump(t, 1.08, 1.30) - 0.07 * bump(t, 1.52, 1.80))
    arms_up = smooth((t - 0.22) / 0.25) * (1 - smooth((t - 2.25) / 0.60))
    party = smooth((t - 1.75) / 0.2) * (1 - smooth((t - 2.35) / 0.4))
    body = set_body(up=height, stretch=stretch, lean=6 * bump(t, 0.0, 0.34) - 4 * arms_up,
                    turn_left=6 * sin(2 * pi * 1.5 * (t - 1.8)) * party, tilt_left=3 * sin(2 * pi * 3 * t) * party)
    for side, phase in (("L", 0.0), ("R", pi)):
        shake = 9 * sin(2 * pi * 3 * t + phase) * arms_up
        set_arm(side, blend_arms(RELAXED, turned(CHEER, "Y", shake * 0.5, shake, shake * 1.4), arms_up), body)
        air = max(first, second)
        up = 0.9 * (0.42 * first + 0.24 * second)
        set_foot(side, out=0.04 + 0.05 * air, up=up, toe_up=-22 * air)
    set_eyes(twist_speedo=6 * sin(2 * pi * 2 * t) * arms_up, twist_rpm=-6 * sin(2 * pi * 2 * t) * arms_up,
             pop_speedo=0.07 * first + 0.04 * second, pop_rpm=0.07 * first + 0.04 * second)
    # flat out: both needles swing right round and tremble at the red line
    revs = smooth((t - 0.25) / 0.35) * (1 - smooth((t - 2.30) / 0.60))
    set_gauges(speed=revs * (225 + 15 * sin(2 * pi * 4 * t)), rpm=IDLE_RPM + revs * (6.6 + 0.5 * sin(2 * pi * 6 * t)))


def flex(t):
    """Four poses, each squeezed once or twice: both arms; the right arm up and the left down;
    the other way round; both arms again."""
    up = smooth((t - 0.08) / 0.44) * (1 - smooth((t - 3.98) / 0.48))
    # in the second pose the left arm is the low one, in the third the right; where they change
    # over the left arm leads, so the two are never straight out at the same moment
    left_low = smooth((t - 1.18) / 0.36) * (1 - smooth((t - 2.08) / 0.38))
    right_low = smooth((t - 2.20) / 0.38) * (1 - smooth((t - 3.12) / 0.36))
    squeeze = up * (bump(t, 0.50, 0.84) + bump(t, 0.84, 1.18) + bump(t, 1.56, 2.00) + bump(t, 2.60, 3.04)
                    + bump(t, 3.48, 3.74) + bump(t, 3.72, 3.98))
    crouch = bump(t, 0.0, 0.34)
    # It leans back to show off, but not while it is turned to its right: leaning back and
    # turned that way, the dials' glossy black faces mirror the studio's key light and go pale
    # grey. The pictures are drawn with those faces dulled (render_greeting.py); the model and
    # its .glb keep the gloss, so the clip keeps out of that angle.
    back = up * (1 - right_low)
    body = set_body(right=0.05 * (left_low - right_low), up=0.015 * squeeze - 0.035 * crouch,
                    stretch=-0.04 * crouch + 0.035 * squeeze, lean=-(3.5 + 1.5 * squeeze) * back + 2.0 * right_low,
                    turn_left=10 * left_low - 4 * right_low, tilt_left=7 * (left_low - right_low))
    high = turned(BICEPS, "Y", -4 * squeeze, -7 * squeeze, -12 * squeeze)
    for name, low in (("L", left_low), ("R", right_low)):
        set_arm(name, curved(RELAXED, OUT, curved(high, REACH, LOW, low), up), body)
    set_foot("L")
    set_foot("R")
    look = 5 * (right_low - left_low)
    set_eyes(twist_speedo=look, twist_rpm=look, pop_speedo=0.05 * squeeze, pop_rpm=0.05 * squeeze)
    # the engine is revved with every squeeze
    set_gauges(speed=up * 20 + 30 * squeeze, rpm=IDLE_RPM + up * 1.0 + 4.6 * squeeze)


def peace(t):
    fall = 1 - smooth((t - 2.15) / 0.52)
    # No overshoot on the way up, as ThumbsUp has: past its place the hand would be in the RPM
    # dial. It swings a little outwards as it arrives instead.
    e = smooth((t - 0.05) / 0.40) * fall
    arrive = bump(t, 0.30, 0.62)
    # the fingers make the sign while the arm is still on its way, and let go as it comes down
    sign = smooth((t - 0.10) / 0.30) * (1 - smooth((t - 2.18) / 0.34))
    held = smooth((t - 0.50) / 0.25) * (1 - smooth((t - 1.90) / 0.25))
    rock = sin(2 * pi * 1.25 * (t - 0.50)) * held
    stretch = -0.03 * bump(t, 0.0, 0.26) + 0.04 * bump(t, 0.20, 0.62)
    body = set_body(up=0.02 * bump(t, 0.24, 0.64), stretch=stretch, lean=-2.0 * e, tilt_left=3.5 * e + 1.0 * rock)
    shown = turned(PEACE, "Y", 0.0, 3 * rock + 4 * arrive, 6 * rock + 7 * arrive)
    set_arm("L", with_fingers(blend_arms(RELAXED, shown, e), blend_arms(RELAXED, PEACE, sign)), body)
    set_arm("R", turned(RELAXED, "X", -4 * e, -6 * e, -6 * e), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=4 * e, twist_rpm=4 * e, pop_speedo=0.04 * bump(t, 0.25, 0.70), pop_rpm=0.04 * bump(t, 0.30, 0.75))
    # an easy cruise: 50 km/h at 2,000 RPM
    arrived = spring(t - 0.15) * fall
    set_gauges(speed=50 * arrived, rpm=IDLE_RPM + (2.0 - IDLE_RPM) * arrived)


def rock_on(t):
    fall = 1 - smooth((t - 2.15) / 0.52)
    e = smooth((t - 0.05) / 0.36) * fall
    arrive = bump(t, 0.26, 0.56)
    sign = smooth((t - 0.08) / 0.28) * (1 - smooth((t - 2.18) / 0.34))
    # three nods to a beat of two a second, the hand pumping with them
    held = smooth((t - 0.42) / 0.12) * (1 - smooth((t - 1.92) / 0.20))
    nod = held * (0.5 - 0.5 * cos(2 * pi * 2.0 * (t - 0.42)))
    stretch = -0.03 * bump(t, 0.0, 0.24) + 0.03 * bump(t, 0.18, 0.50) - 0.025 * nod
    body = set_body(up=-0.02 * nod, forward=0.02 * nod, stretch=stretch, lean=-2.0 * e + 8.0 * nod, tilt_left=-3.0 * e)
    shown = turned(turned(HORNS, "X", 0.0, 7 * nod, 13 * nod), "Y", 0.0, 4 * arrive, 7 * arrive)
    set_arm("R", with_fingers(blend_arms(RELAXED, shown, e), blend_arms(RELAXED, HORNS, sign)), body)
    set_arm("L", turned(RELAXED, "X", -4 * e - 5 * nod, -6 * e - 8 * nod, -6 * e - 10 * nod), body)
    set_foot("L")
    set_foot("R")
    set_eyes(twist_speedo=-4 * e, twist_rpm=-4 * e, pop_speedo=0.05 * nod, pop_rpm=0.05 * nod)
    # every nod takes the engine to the red line
    loud = smooth((t - 0.20) / 0.25) * fall
    set_gauges(speed=loud * (110 + 30 * nod), rpm=IDLE_RPM + loud * (3.2 + 3.0 * nod))


CLIPS = [("Idle", 4.0, idle, True), ("Wave", 2.0, wave, False), ("ThumbsUp", 2.0, thumbs_up, False),
         ("Walk", 1.0, walk, True), ("Celebrate", 3.0, celebrate, False),
         ("Flex", 4.5, flex, False), ("Peace", 2.75, peace, False), ("RockOn", 2.75, rock_on, False),
         ("WaveUp", 0.5, wave_up, False), ("WaveLoop", 0.5, wave_loop, True)]
NEEDLE_CLIPS = [("Gauge_Speed", "speed_kmh", 240.0), ("Gauge_RPM", "rpm", 8.0)]
NEEDLE_SECONDS = 2.0


# ---------------------------------------------------------------- writing the actions
def reset_pose():
    for b in pb:
        b.location = (0.0, 0.0, 0.0)
        b.rotation_euler = (0.0, 0.0, 0.0)
        b.scale = (1.0, 1.0, 1.0)


def new_action(name, frames, loop):
    old = bpy.data.actions.get(name)
    if old:
        bpy.data.actions.remove(old)
    action = bpy.data.actions.new(name)
    rig.animation_data.action = action
    action.use_fake_user = True
    action.use_frame_range = True
    action.frame_start = 0
    action.frame_end = frames
    action.use_cyclic = loop
    return action


def key_pose(frame):
    for name in ROTATED:
        pb[name].keyframe_insert("rotation_euler", frame=frame)
    for name in MOVED:
        pb[name].keyframe_insert("location", frame=frame)
    for name in SCALED:
        pb[name].keyframe_insert("scale", frame=frame)
    rig.keyframe_insert('["speed_kmh"]', frame=frame)
    rig.keyframe_insert('["rpm"]', frame=frame)


def all_curves(action):
    try:
        return [fc for layer in action.layers for strip in layer.strips
                for bag in strip.channelbags for fc in bag.fcurves]
    except AttributeError:
        return list(action.fcurves)


def animate():
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    scene = bpy.context.scene
    scene.render.fps = FPS
    rig.animation_data_create()
    for track in list(rig.animation_data.nla_tracks):
        rig.animation_data.nla_tracks.remove(track)
    stale = bpy.data.actions.get("MilO_ThumbsUp")  # the single still pose the clips replace
    if stale:
        bpy.data.actions.remove(stale)

    for name, seconds, pose_at, loop in CLIPS:
        frames = round(seconds * FPS)
        reset_pose()
        new_action(name, frames, loop)
        for frame in range(frames + 1):
            pose_at(frame / FPS)
            key_pose(frame)

    # The two needle clips move one slider each and nothing else, so they can be laid over any
    # other clip and scrubbed to a live value.
    for name, prop, top in NEEDLE_CLIPS:
        frames = round(NEEDLE_SECONDS * FPS)
        action = new_action(name, frames, False)
        for frame, value in ((0, 0.0), (frames, top)):
            rig[prop] = value
            rig.keyframe_insert('["%s"]' % prop, frame=frame)
        for fc in all_curves(action):
            for point in fc.keyframe_points:
                point.interpolation = "LINEAR"

    for name in [c[0] for c in CLIPS] + [c[0] for c in NEEDLE_CLIPS]:
        track = rig.animation_data.nla_tracks.new()
        track.name = name
        strip = track.strips.new(name, 0, bpy.data.actions[name])
        if getattr(strip, "action_slot", True) is None and bpy.data.actions[name].slots:
            strip.action_slot = bpy.data.actions[name].slots[0]
        track.mute = True

    show("Idle")
    print("clips:", {a.name: "%d frames%s" % (a.frame_end, ", loops" if a.use_cyclic else "")
                     for a in bpy.data.actions})


def show(name, frame=0):
    """Makes one clip the rig's live action and the scene's frame range."""
    action = bpy.data.actions[name]
    reset_pose()
    neutral()
    rig.animation_data.action = action
    if getattr(rig.animation_data, "action_slot", True) is None and action.slots:
        rig.animation_data.action_slot = action.slots[0]
    scene = bpy.context.scene
    scene.frame_start = 0
    scene.frame_end = int(action.frame_end)
    scene.frame_set(frame)


if __name__ != "__milo_lib__":
    animate()
