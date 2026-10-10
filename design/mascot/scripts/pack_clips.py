# pack_clips.py - makes pictures out of the frames render_clips.py drew. Run it with plain
# Python, not inside Blender. It has two uses:
#
#   python3 design/mascot/scripts/pack_clips.py poses <folder>
#
# <folder> holds the frames of Flex, Peace and RockOn. For each it writes into
# design/mascot/renders/ an animated picture that plays once and keeps its last frame
# (milo_flex.webp, milo_peace.webp, milo_rock_on.webp) and a still of the pose it holds
# (milo_flex.png and so on). Nothing uses them yet: they are there for whatever comes.
#
#   python3 design/mascot/scripts/pack_clips.py website <folder>
#
# <folder> holds the frames of WaveUp and WaveLoop, and Dials, which render_dials.py drew. It
# writes the website's three pictures into website/mascot/:
#
#   standing.webp  the mascot standing
#   waving.webp    every second frame of the arm going up and then of one wave of the hand, one
#                  under the other in ONE still picture
#   dials.webp     the two dials' faces without their needles, and under them the two needles'
#                  blades, pointing straight up
#
# The website runs no script, and a page without one cannot start an animated picture again
# from its first frame; so the stylesheet lays the strip of waving.webp over the standing picture
# and shows it one frame at a time, the raise once and the wave again and again. And it turns
# the two blades of dials.webp over the two faces all the time (website/styles.css, "The
# mascot"). What this prints at the end, the sizes, the numbers of frames and where the dials
# lie, is what the stylesheet and index.html are told.
#
# A page without a script cannot tell either whether the strip has arrived, so it cannot take
# the standing picture away for it: the standing mascot stays where it is, under the strip.
# Each frame of the strip therefore covers it: wherever the standing mascot is and the waving
# one is not (his hanging arm, most of all), the frame is filled with the lime of the tile he
# stands on. That lime is read from --accent in website/styles.css. CHANGE THAT COLOUR, OR
# STAND HIM ON ANOTHER, AND waving.webp HAS TO BE PACKED AGAIN.
#
# A VISITOR'S BROWSER KEEPS THESE PICTURES FOR A WEEK AND THE STYLESHEET FOR AN HOUR
# (firebase.json). A picture that changes in a way the stylesheet counts on, another number of
# frames or another place for the mascot in it, therefore gets ANOTHER NAME, or for a week some
# visitors see the new rules step through the old picture. That is why the strip is waving.webp
# and no longer wave.webp, which had 24 frames; and why the mascot must stand where he stood in
# standing.webp (README, "The website's pictures").
#
# All of them are committed. It needs what pack_greeting.py needs, and uses its functions:
# cwebp and webpmux (Homebrew: "brew install webp") and ffmpeg ("brew install ffmpeg").

import json
import os
import re
import shutil
import struct
import sys
import tempfile

from pack_greeting import pack_animated, frames_in, run, here

MASCOT = os.path.normpath(os.path.join(here(), ".."))
RENDERS = os.path.join(MASCOT, "renders")
SITE = os.path.normpath(os.path.join(MASCOT, "..", "..", "website"))
WEBSITE = os.path.join(SITE, "mascot")

# The clip's folder, the picture's name, and the frame that is its still: the first squeeze of
# both arms; the peace sign held upright; the horns between two nods.
POSES = (("Flex", "milo_flex", 16), ("Peace", "milo_peace", 31), ("RockOn", "milo_rock_on", 34))

# The website's wave: every second frame of the two clips, so twelve a second. Of the raise,
# WaveUp, the first frame is left out: it is the standing pose, which is on the page already.
# Its last frame is left out too, because it is the first of the loop, WaveLoop; and the loop's
# last is its first again.
CLIP_FPS = 24
WAVE_EVERY = 2
RAISE = ("WaveUp", (2, 4, 6, 8, 10))
LOOP = ("WaveLoop", (0, 2, 4, 6, 8, 10))
# The standing picture is always on the page, and finer than the app's pictures (60 and 50).
# The strip is coarser: it is on the page for two seconds, moving, and 24 frames weigh.
STANDING_CWEBP = ["-q", "80", "-alpha_q", "90", "-sharp_yuv", "-af", "-m", "6"]
STRIP_CWEBP = ["-q", "45", "-alpha_q", "30", "-sharp_yuv", "-af", "-m", "6"]
# How many pixels further than the standing mascot's outline the lime under a frame reaches, so
# that no rim of the standing picture shows beside it, also when the page draws both smaller.
SPREAD = 3

# The dials. Each needle gets a square of the picture, CELL pixels wide, with the point the
# needle turns about near its middle; the square's corner lies on a multiple of 4 pixels, so
# that the page can show it at a quarter of its size on whole pixels. dials.webp holds four
# such squares: the two faces without a blade above, the two blades below.
CELL = 128
# A face is cut round: it covers the standing picture's own needle and no more. It reaches this
# many pixels past the blade's tip and fades out over the last FADE of them.
PAST_THE_TIP = 4
FADE = 3
# What the glass over a dial takes from the red of a blade. The blade is drawn without the
# glass; in the standing picture the same red, seen through it, is about this much darker.
THROUGH_GLASS = 0.95
DIALS_CWEBP = ["-q", "85", "-alpha_q", "100", "-sharp_yuv", "-af", "-m", "6"]
NEEDLES = ("speed", "rpm")


def size_of(frame):
    """A PNG frame's width and height in pixels, which stand at the head of the file."""
    with open(frame, "rb") as f:
        return struct.unpack(">II", f.read(24)[16:])


def tile_colour():
    """The lime of the tile the mascot stands on, as six hex digits, from the website's stylesheet."""
    with open(os.path.join(SITE, "styles.css")) as f:
        found = re.search(r"--accent:\s*#([0-9a-fA-F]{6})\s*;", f.read())
    if not found:
        raise SystemExit("website/styles.css no longer says --accent: #rrggbb; the wave needs the tile's colour.")
    return found.group(1)


def poses(folder):
    os.makedirs(RENDERS, exist_ok=True)
    for clip, name, still in POSES:
        frames = os.path.join(folder, clip)
        out = os.path.join(RENDERS, name + ".webp")
        said = pack_animated(frames, out)
        shutil.copyfile(os.path.join(frames, "%04d.png" % still), os.path.join(RENDERS, name + ".png"))
        print("%s.webp: %s, %d bytes; %s.png is frame %d, %d bytes"
              % (name, said, os.path.getsize(out), name, still, os.path.getsize(os.path.join(RENDERS, name + ".png"))))


def strip(frames, out):
    """The frames one under the other in one still picture, each laid over the standing mascot's
    shape filled with the tile's lime. The first of the frames named is the standing one, and is
    not in the strip itself."""
    standing, shown = frames[0], frames[1:]
    width, height = size_of(standing)
    lime, count = tile_colour(), len(shown)
    # One ffmpeg run: the standing mascot's shape, SPREAD pixels larger, filled with the lime;
    # each frame laid over a copy of that; the results one under the other.
    graph = "[0:v]format=rgba,alphaextract%s[shape];" % (",dilation" * SPREAD)
    graph += "color=c=0x%s:s=%dx%d,format=rgba[lime];" % (lime, width, height)
    graph += "[lime][shape]alphamerge,split=%d%s;" % (count, "".join("[under%d]" % i for i in range(count)))
    graph += "".join("[under%d][%d:v]overlay=format=rgb[frame%d];" % (i, i + 1, i) for i in range(count))
    graph += "%sxstack=inputs=%d:grid=1x%d" % ("".join("[frame%d]" % i for i in range(count)), count, count)
    with tempfile.TemporaryDirectory() as made:
        laid = os.path.join(made, "strip.png")
        command = ["ffmpeg", "-v", "error", "-y"]
        for frame in frames:
            command += ["-i", frame]
        run(command + ["-filter_complex", graph, "-frames:v", "1", "-pix_fmt", "rgba", laid])
        run(["cwebp", "-quiet", *STRIP_CWEBP, laid, "-o", out])
    return lime


def dials(folder, out):
    """dials.webp, and for each needle where its square lies in the picture and where in the
    square it turns, both as the stylesheet wants them: in hundredths of the whole."""
    with open(os.path.join(folder, "dials.json")) as f:
        found = json.load(f)
    width, height = found["picture"]
    graph = "[0:v]format=rgba,split=%d%s;[1:v]format=rgba,split=%d%s;" % (
        len(NEEDLES), "".join("[faces%d]" % i for i in range(len(NEEDLES))),
        len(NEEDLES), "".join("[blades%d]" % i for i in range(len(NEEDLES))))
    told = []
    for i, name in enumerate(NEEDLES):
        (x, y), reach = found["needles"][name]["turns_at"], found["needles"][name]["reaches"]
        left, top = (4 * round((at - CELL / 2) / 4) for at in (x, y))
        round_to = reach + PAST_THE_TIP
        if min(x - left, y - top, left + CELL - x, top + CELL - y) < round_to:
            raise SystemExit("The %s needle's face does not fit a square of %d pixels: make CELL larger." % (name, CELL))
        graph += ("[faces%d]crop=%d:%d:%d:%d,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':"
                  "a='alpha(X,Y)*clip((%.2f-hypot(X-%.2f,Y-%.2f))/%d,0,1)'[face%d];"
                  % (i, CELL, CELL, left, top, round_to, x - left, y - top, FADE, i))
        graph += ("[blades%d]crop=%d:%d:%d:%d,colorchannelmixer=rr=%.2f:gg=%.2f:bb=%.2f[blade%d];"
                  % (i, CELL, CELL, left, top, THROUGH_GLASS, THROUGH_GLASS, THROUGH_GLASS, i))
        told.append((name, 100 * left / width, 100 * top / height, 100 * (x - left) / CELL, 100 * (y - top) / CELL))
    graph += "%s%sxstack=inputs=%d:grid=%dx2" % (
        "".join("[face%d]" % i for i in range(len(NEEDLES))), "".join("[blade%d]" % i for i in range(len(NEEDLES))),
        2 * len(NEEDLES), len(NEEDLES))
    with tempfile.TemporaryDirectory() as made:
        laid = os.path.join(made, "dials.png")
        run(["ffmpeg", "-v", "error", "-y", "-i", os.path.join(folder, "faces.png"), "-i", os.path.join(folder, "blades.png"),
             "-filter_complex", graph, "-frames:v", "1", "-pix_fmt", "rgba", laid])
        run(["cwebp", "-quiet", *DIALS_CWEBP, laid, "-o", out])
    return 100 * CELL / width, 100 * CELL / height, told


def website(folder):
    frames = [os.path.join(folder, RAISE[0], "0000.png")]
    for clip, numbers in (RAISE, LOOP):
        frames += [os.path.join(folder, clip, "%04d.png" % number) for number in numbers]
    missing = [frame for frame in frames if not os.path.exists(frame)]
    if missing:
        raise SystemExit("Not drawn: %s. Draw %s and %s with render_clips.py first." % (missing[0], RAISE[0], LOOP[0]))
    os.makedirs(WEBSITE, exist_ok=True)

    standing = os.path.join(WEBSITE, "standing.webp")
    run(["cwebp", "-quiet", *STANDING_CWEBP, frames[0], "-o", standing])
    wave = os.path.join(WEBSITE, "waving.webp")
    lime = strip(frames, wave)
    turning = os.path.join(WEBSITE, "dials.webp")
    wide, high, told = dials(os.path.join(folder, "Dials"), turning)

    width, height = size_of(frames[0])
    up, round_and_round = len(RAISE[1]), len(LOOP[1])
    print("standing.webp: %d x %d pixels, %d bytes" % (width, height, os.path.getsize(standing)))
    print("waving.webp: %d frames of %d x %d pixels, one under the other (%d x %d), on the tile's #%s where the "
          "standing mascot would show, %d bytes" % (up + round_and_round, width, height, width, height * (up + round_and_round),
                                                    lime, os.path.getsize(wave)))
    print("  the raise is the first %d frames, %g seconds; the loop the other %d, %g seconds"
          % (up, up * WAVE_EVERY / CLIP_FPS, round_and_round, round_and_round * WAVE_EVERY / CLIP_FPS))
    print("dials.webp: %d x %d pixels, squares of %d: the faces above, the blades below, %d bytes"
          % (CELL * len(NEEDLES), CELL * 2, CELL, os.path.getsize(turning)))
    print("  a square is %.4f%% of the picture's width and %.4f%% of its height" % (wide, high))
    for name, left, top, turns_x, turns_y in told:
        print("  %s: its square lies %.4f%% from the left and %.4f%% from the top; the needle turns at %.2f%% %.2f%% of the square"
              % (name, left, top, turns_x, turns_y))
    print("on the page: %g x %g, half of the picture" % (width / 2, height / 2))


def main():
    uses = {"poses": poses, "website": website}
    if len(sys.argv) != 3 or sys.argv[1] not in uses:
        raise SystemExit("Say 'poses' or 'website', then the folder render_clips.py drew into.")
    for tool, formula in (("cwebp", "webp"), ("webpmux", "webp"), ("ffmpeg", "ffmpeg")):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install %s" % (tool, formula))
    uses[sys.argv[1]](os.path.abspath(sys.argv[2]))


if __name__ == "__main__":
    main()
