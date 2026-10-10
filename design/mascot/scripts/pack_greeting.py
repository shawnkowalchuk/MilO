# pack_greeting.py - makes the two animated pictures the app's greeting plays, out of the frames
# render_greeting.py drew. Run it with plain Python, not inside Blender, and name the two folders:
#
#   python3 design/mascot/scripts/pack_greeting.py <folder of the walk> <folder of the turn and wave>
#
# It writes app/src/main/res/drawable-nodpi/mascot_walk.webp, which starts again for ever, and
# mascot_turn_and_wave.webp, which plays once and keeps its last frame. Both are animated WebP
# with a see-through background, and both are committed: the app's build runs none of this.
# MascotPicturesTest holds them to what the app's code expects.
#
# It needs cwebp and webpmux (Homebrew: "brew install webp"). Each frame is packed by cwebp and
# the frames are joined by webpmux. img2webp would do both in one step, but it cannot be told
# to store the see-through channel more coarsely, and that channel is two fifths of every frame.

import os
import shutil
import subprocess
import sys
import tempfile

def here():
    """This script's folder."""
    return os.path.dirname(os.path.abspath(__file__))


APP_PICTURES = os.path.normpath(os.path.join(here(), "..", "..", "..", "app", "src", "main", "res", "drawable-nodpi"))
# 24 frames a second, in whole milliseconds, which is all the file can say.
FRAME_MS = 42
# Colour at 60 of 100 and the see-through channel at 50, the lowest at which the edges, the M and
# the dials looked like the frames on a phone. -sharp_yuv keeps the green from bleeding into the
# white gloves; without -exact, cwebp gives the see-through pixels beside an edge that edge's
# colour, so no dark or light fringe is drawn over the app's dark page or its lime tile.
CWEBP = ["-q", "60", "-alpha_q", "50", "-sharp_yuv", "-af", "-m", "6"]
# How often each plays, as the file says it: 0 is for ever, 1 is once.
PICTURES = (("mascot_walk.webp", 0), ("mascot_turn_and_wave.webp", 1))


def pack(folder, out, plays):
    """One animated picture from a folder's frames, each shown for FRAME_MS. Every frame is the
    whole picture and replaces the one before it, so none depends on another."""
    frames = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
    if not frames:
        raise SystemExit("No frames in %s" % folder)
    with tempfile.TemporaryDirectory() as packed:
        join = ["webpmux"]
        for name in frames:
            frame = os.path.join(packed, name[:-4] + ".webp")
            subprocess.run(["cwebp", "-quiet", *CWEBP, os.path.join(folder, name), "-o", frame], check=True)
            join += ["-frame", frame, "+%d+0+0+0-b" % FRAME_MS]
        join += ["-loop", str(plays), "-bgcolor", "0,0,0,0", "-o", out]
        # webpmux says what it saved on the line for errors; it is shown only if it fails.
        joined = subprocess.run(join, capture_output=True, text=True)
        if joined.returncode != 0:
            raise SystemExit("webpmux could not write %s:\n%s" % (out, joined.stderr))
    return len(frames)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Name two folders: the walk's frames, then the turn and wave's.")
    for tool in ("cwebp", "webpmux"):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install webp" % tool)
    os.makedirs(APP_PICTURES, exist_ok=True)
    total = 0
    for folder, (name, plays) in zip(sys.argv[1:], PICTURES):
        out = os.path.join(APP_PICTURES, name)
        frames = pack(folder, out, plays)
        total += os.path.getsize(out)
        print("%s: %d frames of %d ms, %s, %d bytes"
              % (name, frames, FRAME_MS, "plays once" if plays else "starts again for ever", os.path.getsize(out)))
    print("together: %d bytes" % total)


main()
