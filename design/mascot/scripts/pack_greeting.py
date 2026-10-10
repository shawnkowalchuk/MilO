# pack_greeting.py - makes the two pictures the app's greeting shows, out of the frames
# render_greeting.py drew. Run it with plain Python, not inside Blender, and name the two folders:
#
#   python3 design/mascot/scripts/pack_greeting.py <folder of the walk> <folder of the turn and wave>
#
# It writes two files into app/src/main/res/drawable-nodpi/, both WebP with a see-through
# background, and both are committed: the app's build runs none of this.
#
#   mascot_walk.webp           a still picture: the walk's frames side by side on one sheet,
#                              WALK_COLUMNS across, row after row. The app draws the frame it
#                              wants, because it moves the mascot with every step.
#   mascot_turn_and_wave.webp  an animated picture that plays once and keeps its last frame.
#                              Android plays it.
#
# MascotPicturesTest holds both to what the app's code expects.
#
# It needs cwebp and webpmux (Homebrew: "brew install webp") and ffmpeg ("brew install ffmpeg").
# ffmpeg lays the walk's frames on the sheet. Each frame of the animated picture is packed by
# cwebp and the frames are joined by webpmux; img2webp would do both in one step, but it cannot
# be told to store the see-through channel more coarsely, and that channel is two fifths of
# every frame.

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
# The walk's sheet: this many frames in a row. MascotPicture.WALK_COLUMNS in the app says the same.
WALK_COLUMNS = 4
# Colour at 60 of 100 and the see-through channel at 50, the lowest at which the edges, the M and
# the dials looked like the frames on a phone. -sharp_yuv keeps the green from bleeding into the
# white gloves; without -exact, cwebp gives the see-through pixels beside an edge that edge's
# colour, so no dark or light fringe is drawn over the app's dark page or its lime tile.
CWEBP = ["-q", "60", "-alpha_q", "50", "-sharp_yuv", "-af", "-m", "6"]


def frames_in(folder):
    frames = sorted(name for name in os.listdir(folder) if name.endswith(".png"))
    if not frames:
        raise SystemExit("No frames in %s" % folder)
    return frames


def run(command):
    """Runs a tool. What it says is shown only if it fails."""
    done = subprocess.run(command, capture_output=True, text=True)
    if done.returncode != 0:
        raise SystemExit("%s failed:\n%s" % (command[0], done.stderr))


def pack_sheet(folder, out):
    """The walk: one still picture with every frame on it, WALK_COLUMNS across, row after row.
    The frames touch; each has a see-through margin of its own, so none shows in its neighbour."""
    frames = frames_in(folder)
    rows, over = divmod(len(frames), WALK_COLUMNS)
    if over:
        raise SystemExit("%d frames do not fill rows of %d" % (len(frames), WALK_COLUMNS))
    with tempfile.TemporaryDirectory() as packed:
        sheet = os.path.join(packed, "sheet.png")
        laid = ["ffmpeg", "-v", "error", "-y"]
        for name in frames:
            laid += ["-i", os.path.join(folder, name)]
        laid += ["-filter_complex", "xstack=inputs=%d:grid=%dx%d" % (len(frames), WALK_COLUMNS, rows),
                 "-frames:v", "1", "-pix_fmt", "rgba", sheet]
        run(laid)
        run(["cwebp", "-quiet", *CWEBP, sheet, "-o", out])
    return "%d frames on one sheet, %d across and %d down" % (len(frames), WALK_COLUMNS, rows)


def pack_animated(folder, out):
    """The turn and wave: one animated picture, each frame shown for FRAME_MS, played once.
    Every frame is the whole picture and replaces the one before it, so none depends on another."""
    frames = frames_in(folder)
    with tempfile.TemporaryDirectory() as packed:
        join = ["webpmux"]
        for name in frames:
            frame = os.path.join(packed, name[:-4] + ".webp")
            run(["cwebp", "-quiet", *CWEBP, os.path.join(folder, name), "-o", frame])
            join += ["-frame", frame, "+%d+0+0+0-b" % FRAME_MS]
        # "-loop 1" is once; 0 would be for ever.
        run(join + ["-loop", "1", "-bgcolor", "0,0,0,0", "-o", out])
    return "%d frames of %d ms, plays once" % (len(frames), FRAME_MS)


def main():
    if len(sys.argv) != 3:
        raise SystemExit("Name two folders: the walk's frames, then the turn and wave's.")
    for tool, formula in (("cwebp", "webp"), ("webpmux", "webp"), ("ffmpeg", "ffmpeg")):
        if shutil.which(tool) is None:
            raise SystemExit("%s is not installed. On a Mac: brew install %s" % (tool, formula))
    os.makedirs(APP_PICTURES, exist_ok=True)
    total = 0
    for folder, name, pack in zip(sys.argv[1:], ("mascot_walk.webp", "mascot_turn_and_wave.webp"),
                                  (pack_sheet, pack_animated)):
        out = os.path.join(APP_PICTURES, name)
        said = pack(folder, out)
        total += os.path.getsize(out)
        print("%s: %s, %d bytes" % (name, said, os.path.getsize(out)))
    print("together: %d bytes" % total)


# pack_clips.py reads this file for its functions and must not set the greeting's pictures going.
if __name__ == "__main__":
    main()
