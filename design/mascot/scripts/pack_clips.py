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
# <folder> holds the frames of Wave. It writes the website's two pictures into website/mascot/:
# standing.webp, the mascot standing, and wave.webp, every second frame of the wave, one under
# the other in ONE still picture. The website runs no script, and a page without one cannot
# start an animated picture again from its first frame; so the stylesheet lays that strip over
# the standing picture and shows it one frame at a time (website/styles.css, "The mascot").
# What this prints at the end, the frame's size and how many there are, is what the stylesheet
# and index.html are told.
#
# A page without a script cannot tell either whether the strip has arrived, so it cannot take
# the standing picture away for it: the standing mascot stays where it is, under the strip.
# Each frame of the strip therefore covers it: wherever the standing mascot is and the waving
# one is not (his hanging arm, most of all), the frame is filled with the lime of the tile he
# stands on. That lime is read from --accent in website/styles.css. CHANGE THAT COLOUR, OR
# STAND HIM ON ANOTHER, AND wave.webp HAS TO BE PACKED AGAIN.
#
# All of them are committed. It needs what pack_greeting.py needs, and uses its functions:
# cwebp and webpmux (Homebrew: "brew install webp") and ffmpeg ("brew install ffmpeg").

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

# The website's wave: every second frame of the clip, so twelve a second. The clip's last frame
# is the standing pose again and is left out: the page shows the standing picture after it.
CLIP_FPS = 24
WAVE_EVERY = 2
WAVE_FRAMES = 24
# The standing picture is always on the page, and finer than the app's pictures (60 and 50).
# The strip is coarser: it is on the page for two seconds, moving, and 24 frames weigh.
STANDING_CWEBP = ["-q", "80", "-alpha_q", "90", "-sharp_yuv", "-af", "-m", "6"]
STRIP_CWEBP = ["-q", "45", "-alpha_q", "30", "-sharp_yuv", "-af", "-m", "6"]
# How many pixels further than the standing mascot's outline the lime under a frame reaches, so
# that no rim of the standing picture shows beside it, also when the page draws both smaller.
SPREAD = 3


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


def website(folder):
    frames = os.path.join(folder, "Wave")
    names = frames_in(frames)[::WAVE_EVERY][:WAVE_FRAMES]
    if len(names) != WAVE_FRAMES:
        raise SystemExit("The wave needs %d frames, every %d. of the clip; %s has too few." % (WAVE_FRAMES, WAVE_EVERY, frames))
    os.makedirs(WEBSITE, exist_ok=True)

    standing = os.path.join(WEBSITE, "standing.webp")
    run(["cwebp", "-quiet", *STANDING_CWEBP, os.path.join(frames, names[0]), "-o", standing])

    # One ffmpeg run: the standing mascot's shape, SPREAD pixels larger, filled with the tile's
    # lime; each frame laid over a copy of that; the results one under the other.
    width, height = size_of(os.path.join(frames, names[0]))
    lime, count = tile_colour(), len(names)
    graph = "[0:v]format=rgba,alphaextract%s[shape];" % (",dilation" * SPREAD)
    graph += "color=c=0x%s:s=%dx%d,format=rgba[lime];" % (lime, width, height)
    graph += "[lime][shape]alphamerge,split=%d%s;" % (count, "".join("[under%d]" % i for i in range(count)))
    graph += "".join("[under%d][%d:v]overlay=format=rgb[frame%d];" % (i, i, i) for i in range(count))
    graph += "%sxstack=inputs=%d:grid=1x%d" % ("".join("[frame%d]" % i for i in range(count)), count, count)
    wave = os.path.join(WEBSITE, "wave.webp")
    with tempfile.TemporaryDirectory() as made:
        strip = os.path.join(made, "strip.png")
        laid = ["ffmpeg", "-v", "error", "-y"]
        for name in names:
            laid += ["-i", os.path.join(frames, name)]
        run(laid + ["-filter_complex", graph, "-frames:v", "1", "-pix_fmt", "rgba", strip])
        run(["cwebp", "-quiet", *STRIP_CWEBP, strip, "-o", wave])

    print("standing.webp: %d x %d pixels, %d bytes" % (width, height, os.path.getsize(standing)))
    print("wave.webp: %d frames of %d x %d pixels, one under the other (%d x %d), on the tile's #%s where the "
          "standing mascot would show, %d bytes" % (count, width, height, width, height * count, lime, os.path.getsize(wave)))
    print("on the page: %g x %g, half of that, and %d frames in %g seconds"
          % (width / 2, height / 2, len(names), len(names) * WAVE_EVERY / CLIP_FPS))


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
