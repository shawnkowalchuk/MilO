#!/usr/bin/env python3
# make_video.py - makes the promo video and the mascot's opening as clips, from the screen
# recordings in marketing/video/source/. Run it from anywhere (a minute or two):
#
#   python3 marketing/tools/make_video.py
#
# It writes into marketing/video/:
#   promo-vertical-1080x1920.mp4, promo-horizontal-1920x1080.mp4    the promo, in two shapes
#   opening-vertical-1080x1920.mp4, opening-horizontal-1920x1080.mp4  its first scene alone
#   opening.gif                                                      the same, small, as a GIF
#
# THE SCREEN IN EVERY SCENE IS A REAL RECORDING of the app on an emulator, with the made-up
# trips of the screenshots (README, "The video"). This script adds what is round it: the page,
# the phone it is shown in, the words, and in the opening a camera that moves in on the mascot.
# The words are drawn by Chrome from marketing/templates/kit.css, as the pictures' are
# (marketing/build.py). The only sound is the app's own two: the connect sound when the trip
# starts by itself, and the trip-start sound when the drive is under way.
#
# THE WORDS ARE CLAIMS, as in build.py: each is true of the app as it is.
#
# It needs what build.py needs, and ffmpeg (brew install ffmpeg).

import os
import subprocess
import sys
import tempfile

from PIL import Image, ImageDraw, ImageSequence

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import build  # noqa: E402  (the kit's own build.py: its pages and its Chrome)

KIT, ROOT = build.KIT, build.ROOT
SOURCE = os.path.join(KIT, "video", "source")
OUT = os.path.join(KIT, "video")
FPS = 30
FADE = 0.3  # seconds one scene takes to give way to the next
SCREEN = (1080, 2400)  # the emulator's screen, which every recording is
PAGE, TILE, OUTLINE = (18, 19, 22), (30, 32, 37), (58, 62, 70)

# The scenes, in order: the recording, how long the scene is, and its words. Each recording
# in marketing/video/source/ is cut to its scene already, at 30 frames a second (README, "The
# video", says from what and how fast).
SCENES = (
    dict(name="opening", clip="opening.mp4", seconds=7.1,
         headline="MilO waves hello.", sub="When you open the app, the mascot walks in."),
    dict(name="starts", clip="starts-by-itself.mp4", seconds=5.4,
         headline="A trip starts by itself", sub="when your phone connects to your work vehicle’s Bluetooth."),
    dict(name="recorded", clip="recording-time-lapse.mp4", seconds=4.2,
         headline="Recorded with GPS", sub="even with the app closed. Shown here sped up."),
    dict(name="trips", clip="trips.mp4", seconds=4.3,
         headline="Business or Personal", sub="sorted by your work hours."),
    dict(name="report", clip="report.mp4", seconds=5.1,
         headline="The month’s report", sub="for your accountant, as a PDF and a CSV file."),
)
END_SECONDS = 4.4

# The opening's camera. Times are seconds into the scene. In the recording the mascot comes in
# at 1.0, stops in the middle at 4.1, has waved by 6.6 and is gone at 6.9.
ZOOM_IN, ZOOM_HELD, ZOOM_OUT, ZOOM_OVER = 2.4, 3.9, 6.3, 7.0
# The point of the screen the camera moves in on: where the mascot stands, in the recording's pixels.
FOCUS = (540, 1740)

# The two shapes. phone: the phone's width; at: its upper left corner; band: the part of the
# picture the words have to themselves, which the phone passes behind when the camera moves
# in; zoom: how far it moves in, and where the mascot is then.
SHAPES = {
    "vertical": dict(size=(1080, 1920), phone=720, at=(180, 344), band=(0, 0, 1080, 322), zoom=1.5, target=(540, 1140)),
    "horizontal": dict(size=(1920, 1080), phone=463, at=(1208, 40), band=(0, 0, 960, 1080), zoom=2.15, target=(1440, 560)),
}

# The app's two sounds, and when in the promo each is heard: the scene, and seconds into it.
SOUNDS = (("trip_start_chirp.wav", "starts", 3.0), ("trip_go_chime.wav", "recorded", 0.5))


def ease(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def camera(t):
    """How far the opening's camera has moved in at t seconds into the scene: 0 to 1."""
    if t < ZOOM_HELD:
        return ease((t - ZOOM_IN) / (ZOOM_HELD - ZOOM_IN))
    return 1 - ease((t - ZOOM_OUT) / (ZOOM_OVER - ZOOM_OUT))


def rounded(size, box, radius):
    """A mask, white inside the rounded box, with smooth edges (drawn at twice the size)."""
    k = 2
    mask = Image.new("L", (size[0] * k, size[1] * k), 0)
    ImageDraw.Draw(mask).rounded_rectangle([v * k for v in box], radius=radius * k, fill=255)
    return mask.resize(size, Image.LANCZOS)


def with_phone(canvas, shape, frame, moved_in=0.0):
    """Draws the phone with `frame` on its screen onto the canvas, the camera moved in by 0 to 1."""
    z = 1 + (shape["zoom"] - 1) * moved_in
    width = shape["phone"] * z
    pad = width * 0.026
    scale = (width - 2 * pad) / SCREEN[0]
    base = (shape["phone"] - 2 * shape["phone"] * 0.026) / SCREEN[0]
    rest = (shape["at"][0] + shape["phone"] * 0.026 + FOCUS[0] * base, shape["at"][1] + shape["phone"] * 0.026 + FOCUS[1] * base)
    spot = (rest[0] + (shape["target"][0] - rest[0]) * moved_in, rest[1] + (shape["target"][1] - rest[1]) * moved_in)
    left, top = spot[0] - FOCUS[0] * scale, spot[1] - FOCUS[1] * scale
    right, bottom = left + SCREEN[0] * scale, top + SCREEN[1] * scale
    body = (left - pad, top - pad, right + pad, bottom + pad)
    size = canvas.size
    canvas.paste(Image.new("RGB", size, OUTLINE), (0, 0), rounded(size, body, width * 0.125))
    line = width * 0.005
    canvas.paste(Image.new("RGB", size, TILE), (0, 0), rounded(size, [body[0] + line, body[1] + line, body[2] - line, body[3] - line], width * 0.125 - line))
    shown = frame.resize((round(right - left), round(bottom - top)), Image.LANCZOS)
    layer = Image.new("RGB", size, PAGE)
    layer.paste(shown, (round(left), round(top)))
    canvas.paste(layer, (0, 0), rounded(size, (left, top, right, bottom), width * 0.1))


def recording(scene):
    """The scene's frames from its recording, one for each frame of the video."""
    count = round(scene["seconds"] * FPS)
    command = ["ffmpeg", "-v", "error", "-i", os.path.join(SOURCE, scene["clip"]), "-vf", "fps=%d" % FPS,
               "-frames:v", str(count), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    reading = subprocess.Popen(command, stdout=subprocess.PIPE)
    wanted, last = SCREEN[0] * SCREEN[1] * 3, None
    for _ in range(count):
        data = reading.stdout.read(wanted)
        if len(data) == wanted:
            last = Image.frombuffer("RGB", SCREEN, data, "raw", "RGB", 0, 1)
        if last is None:
            raise SystemExit("%s gave no picture" % scene["clip"])
        yield last
    reading.stdout.close()
    reading.wait()


def writer(path, size):
    """An ffmpeg that takes the video's frames one after the other and writes them nearly lossless."""
    command = ["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % size, "-r", str(FPS),
               "-i", "-", "-c:v", "libx264", "-preset", "veryfast", "-crf", "10", "-pix_fmt", "yuv420p", path]
    return subprocess.Popen(command, stdin=subprocess.PIPE)


def words(scene, shape_name, folder):
    """The scene's words as a see-through picture the size of the video, drawn by Chrome."""
    width, height = SHAPES[shape_name]["size"]
    if shape_name == "vertical":
        body = ('<div class="abs" style="left:70px;right:70px;top:84px;text-align:center">'
                '<h1 class="h" style="font-size:72px">%s</h1><p class="sub" style="font-size:35px;margin-top:18px">%s</p></div>'
                % (scene["headline"], scene["sub"]))
    else:
        body = ('<div class="abs col" style="left:120px;top:0;bottom:0;width:800px;justify-content:center">%s'
                '<h1 class="h" style="font-size:102px;margin-top:60px">%s</h1><p class="sub" style="font-size:43px;margin-top:30px">%s</p></div>'
                % (build.brand(40), scene["headline"], scene["sub"]))
    job = build.Job(os.path.join(folder, "words-%s-%s.png" % (scene["name"], shape_name)), width, height, body, clear=True)
    return Image.open(build.draw(job, folder)).convert("RGBA")


def paper(shape_name):
    """The report's first page as a sheet of paper, turned a little, for the report's scene."""
    sheet = Image.open(os.path.join(KIT, "screenshots", "report-page-1.png")).convert("RGBA")
    width = 600 if shape_name == "vertical" else 470
    sheet = sheet.resize((width, round(sheet.height * width / sheet.width)), Image.LANCZOS)
    sheet.putalpha(rounded(sheet.size, (0, 0, sheet.width, sheet.height), 10))
    return sheet.rotate(-3, expand=True, resample=Image.BICUBIC)


def render_scene(scene, shape_name, path, folder):
    shape = SHAPES[shape_name]
    caption = words(scene, shape_name, folder)
    sheet = paper(shape_name) if scene["name"] == "report" else None
    out = writer(path, shape["size"])
    for number, frame in enumerate(recording(scene)):
        t = number / FPS
        canvas = Image.new("RGB", shape["size"], PAGE)
        with_phone(canvas, shape, frame, camera(t) if scene["name"] == "opening" else 0.0)
        ImageDraw.Draw(canvas).rectangle(shape["band"], fill=PAGE)
        canvas.paste(caption, (0, 0), caption)
        if sheet is not None:
            # The page comes up over the phone in the scene's last second and a half.
            up = ease((t - (scene["seconds"] - 1.7)) / 0.5)
            if up > 0:
                x = shape["size"][0] - sheet.width - (40 if shape_name == "vertical" else 30)
                rest = shape["size"][1] - sheet.height - (150 if shape_name == "vertical" else 60)
                canvas.paste(sheet, (x, round(shape["size"][1] + (rest - shape["size"][1]) * up)), sheet)
        out.stdin.write(canvas.tobytes())
    out.stdin.close()
    out.wait()


def render_end(shape_name, path, folder):
    """The end card: the mark, the headline, the three facts and the address, on the lime, and
    the mascot, who waves once."""
    width, height = SHAPES[shape_name]["size"]
    if shape_name == "vertical":
        body = ('<div class="abs col" style="left:80px;right:80px;top:200px;align-items:center;text-align:center">%s'
                '<h1 class="h" style="font-size:104px;margin-top:56px">%s</h1>'
                '<div style="font-size:42px;font-weight:600;margin-top:44px">%s</div>'
                '<div class="pill" style="font-size:44px;padding:20px 44px;margin-top:44px">%s</div></div>'
                % (build.brand(46, reversed_mark=True), build.HEADLINE, build.FACTS, build.SITE))
        tall, right, top = 760, 100, 1070
    else:
        body = ('<div class="abs col" style="left:130px;top:0;bottom:0;width:900px;justify-content:center;align-items:flex-start">%s'
                '<h1 class="h" style="font-size:98px;margin-top:50px">%s</h1>'
                '<div style="font-size:42px;font-weight:600;margin-top:40px">%s</div>'
                '<div class="pill" style="font-size:42px;padding:18px 42px;margin-top:40px">%s</div></div>'
                % (build.brand(44, reversed_mark=True), build.HEADLINE, build.FACTS, build.SITE))
        tall, right, top = 800, 10, 170
    job = build.Job(os.path.join(folder, "end-%s.png" % shape_name), width, height, body, "lime")
    card = Image.open(build.draw(job, folder)).convert("RGB")
    waving = [frame.convert("RGBA") for frame in ImageSequence.Iterator(Image.open(os.path.join(KIT, "mascot", "animated", "wave.webp")))]
    size = (round(waving[0].width * tall / waving[0].height), tall)
    waving = [frame.resize(size, Image.LANCZOS) for frame in waving]
    where = (width - right - size[0], top)
    out = writer(path, (width, height))
    for number in range(round(END_SECONDS * FPS)):
        # The wave is 24 frames a second and starts half a second in. He stands before and after.
        at = min(len(waving) - 1, max(0, round((number / FPS - 0.5) * 24)))
        canvas = card.copy()
        canvas.paste(waving[at], where, waving[at])
        out.stdin.write(canvas.tobytes())
    out.stdin.close()
    out.wait()


def sound_track(path, starts, seconds):
    """The promo's sound: silence, and the app's two sounds where their scenes are."""
    command = ["ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-t", "%.3f" % seconds, "-i", "anullsrc=r=44100:cl=stereo"]
    graph, mixed = "", "[0:a]"
    for number, (name, scene, into) in enumerate(SOUNDS, start=1):
        command += ["-i", os.path.join(ROOT, "app", "src", "main", "res", "raw", name)]
        delay = round((starts[scene] + into) * 1000)
        graph += "[%d:a]aresample=44100,volume=0.8,adelay=%d|%d[s%d];" % (number, delay, delay, number)
        mixed += "[s%d]" % number
    graph += "%samix=inputs=%d:duration=first:normalize=0[a]" % (mixed, len(SOUNDS) + 1)
    subprocess.run(command + ["-filter_complex", graph, "-map", "[a]", "-c:a", "aac", "-b:a", "128k", path], check=True)


def join(parts, lengths, sound, path):
    """The scenes one after the other, each giving way to the next in FADE seconds, with the sound."""
    command = ["ffmpeg", "-v", "error", "-y"]
    for part in parts:
        command += ["-i", part]
    graph, last, offset = "", "[0:v]", 0.0
    for number in range(1, len(parts)):
        offset += lengths[number - 1] - FADE
        graph += "%s[%d:v]xfade=transition=fade:duration=%.2f:offset=%.3f[v%d];" % (last, number, FADE, offset, number)
        last = "[v%d]" % number
    command += ["-i", sound, "-filter_complex", graph.rstrip(";"), "-map", last, "-map", "%d:a" % len(parts),
                "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p", "-r", str(FPS),
                "-c:a", "copy", "-movflags", "+faststart", "-shortest", path]
    subprocess.run(command, check=True)


def opening_alone(part, path):
    """The first scene alone, silent, going dark at its end."""
    seconds = SCENES[0]["seconds"]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", part, "-vf", "fade=t=out:st=%.2f:d=0.3:color=0x121316" % (seconds - 0.3),
                    "-an", "-c:v", "libx264", "-preset", "slow", "-crf", "19", "-pix_fmt", "yuv420p", "-r", str(FPS),
                    "-movflags", "+faststart", path], check=True)


def small_moving(part):
    """The vertical opening as a GIF, small enough to post where no video is taken."""
    gif = os.path.join(OUT, "opening.gif")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", part, "-vf",
                    "fps=15,scale=360:640:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle",
                    "-loop", "0", gif], check=True)
    return [gif]


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as folder:
        for shape_name in SHAPES:
            width, height = SHAPES[shape_name]["size"]
            parts, lengths = [], []
            for scene in SCENES:
                part = os.path.join(folder, "%s-%s.mp4" % (scene["name"], shape_name))
                render_scene(scene, shape_name, part, folder)
                parts.append(part)
                lengths.append(scene["seconds"])
                print("scene %s, %s" % (scene["name"], shape_name))
            end = os.path.join(folder, "end-%s.mp4" % shape_name)
            render_end(shape_name, end, folder)
            parts.append(end)
            lengths.append(END_SECONDS)
            starts, at = {}, 0.0
            for scene, length in zip(SCENES, lengths):
                starts[scene["name"]] = at
                at += length - FADE
            total = sum(lengths) - FADE * (len(lengths) - 1)
            sound = os.path.join(folder, "sound-%s.m4a" % shape_name)
            sound_track(sound, starts, total)
            promo = os.path.join(OUT, "promo-%s-%dx%d.mp4" % (shape_name, width, height))
            join(parts, lengths, sound, promo)
            alone = os.path.join(OUT, "opening-%s-%dx%d.mp4" % (shape_name, width, height))
            opening_alone(parts[0], alone)
            made = [promo, alone]
            if shape_name == "vertical":
                made += small_moving(parts[0])
            for path in made:
                print("%s: %.1f MB" % (os.path.relpath(path, ROOT), os.path.getsize(path) / 1e6))
            print("the promo is %.1f seconds" % total)


if __name__ == "__main__":
    main()
