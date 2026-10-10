#!/usr/bin/env python3
# build.py - makes the marketing kit's pictures again. Run it from anywhere:
#
#   python3 marketing/build.py              every picture below, from what is in the kit
#   python3 marketing/build.py play social  only those groups (play, hunt, social, mascot,
#                                           logo, other)
#   python3 marketing/build.py poses        draws the mascot's poses again in Blender first
#                                           (half a minute), then run it once more for the rest
#
# What it draws from:
#   marketing/screenshots/*.png   the app's own screens, taken on an emulator with made-up
#                                 trips (README, "The screenshots"). Take them again when the
#                                 app changes, under the same names, and run this.
#   marketing/mascot/see-through/ the mascot's poses, drawn by marketing/tools/render_poses.py
#   marketing/templates/kit.css   the look: colours, corners, the phone, the typeface
#   website/fonts/sora.ttf, the app's own mark and the website itself
#
# How: each picture is a small web page, written here and drawn at its exact size by headless
# Chrome. Pillow then cuts it to size and saves it. Nothing is fetched from the internet.
#
# It needs Google Chrome, Pillow, pngquant (brew install pngquant) and, for the logos' SVG
# files and the report's page, poppler (brew install poppler). "poses" needs Blender.
#
# THE WORDS ON THE PICTURES ARE CLAIMS. Each one below is true of the app as it is (README,
# "What the kit says, and what it does not"). Change the app, and read them again.

import concurrent.futures
import http.server
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time

from PIL import Image, ImageFilter

KIT = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(KIT)
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
CSS = "file://" + os.path.join(KIT, "templates", "kit.css")
SHOTS = os.path.join(KIT, "screenshots")
POSES_DIR = os.path.join(KIT, "mascot", "see-through")

LIME = "#c6f432"
PAGE = "#121316"

# The mascot's poses: the file's name, the clip and the frame of it that holds the pose
# (design/mascot/scripts/animate_milo.py; 24 frames a second), and what the pose is called.
POSES = (
    ("standing", "Idle", 0, "Standing"),
    ("waving", "Wave", 15, "Waving"),
    ("thumbs-up", "ThumbsUp", 24, "Thumbs-up"),
    ("celebrate", "Celebrate", 14, "Celebrate"),
    ("flex", "Flex", 16, "Flex"),
    ("peace", "Peace", 31, "Peace"),
    ("rock-on", "RockOn", 34, "Rock on"),
)

# The words used more than once. The headline is the website's.
HEADLINE = "Business mileage that logs itself."
FACTS = "Free · No account · No ads"
SITE = "milotriplog.top"
ANDROID = "For Android 14 or newer"

# The app's mark: the letter's outline is app/src/main/res/drawable/ic_launcher_foreground.xml's.
M_PATH = ("M41.89,64.95L41.89,43.05L48.67,43.05L53.67,55.37L54.24,55.37L59.2,43.05L66.11,43.05L66.11,64.95"
          "L61.23,64.95L61.23,46.26L61.94,46.32L56.15,60.51L51.45,60.51L45.64,46.32L46.4,46.26L46.4,64.95z")


def mark(size, square=LIME, letter=PAGE):
    """The mark as inline SVG: the rounded square and the M."""
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="%d" height="%d">'
            '<rect x="18" y="18" width="72" height="72" rx="22" fill="%s"/><path fill="%s" d="%s"/></svg>'
            % (size, size, square, letter, M_PATH))


def brand(size, name="MilO Trip Log", reversed_mark=False):
    """The mark and the name on one line, the name `size` pixels high."""
    icon = mark(round(size * 1.64), PAGE, LIME) if reversed_mark else mark(round(size * 1.64))
    return '<div class="brand" style="font-size:%dpx;gap:%dpx">%s<span>%s</span></div>' % (size, round(size * 0.45), icon, name)


def shot(name):
    return "file://" + os.path.join(SHOTS, name + ".png")


def pose(name, size=2048):
    return "file://" + os.path.join(POSES_DIR, "%s-%d.png" % (name, size))


def phone(name, width, left, top, extra=""):
    return ('<div class="phone" style="--pw:%dpx;left:%dpx;top:%dpx;%s"><img src="%s"></div>'
            % (width, left, top, extra, shot(name)))


def canvas(width, height, body, kind=""):
    return '<div class="canvas %s" style="--w:%dpx;--h:%dpx">%s</div>' % (kind, width, height, body)


# ------------------------------------------------------------------ drawing a page
class Job:
    """One picture: where it goes, its size, its page, and how it is saved."""

    def __init__(self, out, width, height, body, kind="", clear=False, quant=False, jpeg=False, scale=1):
        self.out, self.width, self.height = os.path.join(KIT, out), width, height
        self.page = canvas(width, height, body, kind + (" clear" if clear else ""))
        self.clear, self.quant, self.jpeg, self.scale = clear, quant, jpeg, scale


def draw(job, folder):
    """Draws one page with headless Chrome and saves the picture."""
    name = os.path.basename(job.out).rsplit(".", 1)[0]
    page = os.path.join(folder, name + "-" + str(abs(hash(job.out))) + ".html")
    raw = page[:-5] + ".png"
    with open(page, "w", encoding="utf-8") as f:
        f.write('<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="%s"></head>'
                "<body>%s</body></html>" % (CSS, job.page))
    # A headless window narrower than about 500 pixels is not honoured: small pictures are
    # drawn in a larger window and cut out of its corner.
    window = "%d,%d" % (max(job.width, 600), max(job.height, 600))
    command = [CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
               "--force-device-scale-factor=%d" % job.scale, "--virtual-time-budget=3000",
               "--user-data-dir=" + os.path.join(folder, "chrome-" + str(abs(hash(job.out)))),
               "--screenshot=" + raw, "--window-size=" + window]
    if job.clear:
        command.append("--default-background-color=00000000")
    chrome(command + ["file://" + page], raw)
    picture = Image.open(raw).crop((0, 0, job.width * job.scale, job.height * job.scale))
    os.makedirs(os.path.dirname(job.out), exist_ok=True)
    if job.jpeg:
        picture.convert("RGB").save(job.out, "JPEG", quality=90, subsampling=0, optimize=True)
    else:
        picture = picture if job.clear else picture.convert("RGB")
        picture.save(job.out, "PNG", optimize=True)
        if job.quant:
            quantise(job.out)
    return job.out


def chrome(command, made):
    """Runs headless Chrome until the file it is asked for is written, then ends it. Chrome
    writes the picture within a second or two and then, on this Mac, does not go by itself."""
    running = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    last, waited = -1, 0.0
    try:
        while waited < 90 and running.poll() is None:
            time.sleep(0.25)
            waited += 0.25
            size = os.path.getsize(made) if os.path.exists(made) else 0
            if size > 0 and size == last:
                break
            last = size
    finally:
        if running.poll() is None:
            running.terminate()
            try:
                running.wait(5)
            except subprocess.TimeoutExpired:
                running.kill()
    if not os.path.exists(made) or os.path.getsize(made) == 0:
        raise SystemExit("Chrome did not write %s" % made)


def quantise(path):
    """Makes a PNG smaller with pngquant: a palette of the picture's own colours, see-through
    parts kept. Looked at beside the full picture at full size, nothing can be told apart."""
    subprocess.run(["pngquant", "--quality=85-100", "--speed", "1", "--force", "--skip-if-larger",
                    "--output", path, path], check=False)


def draw_all(jobs):
    with tempfile.TemporaryDirectory() as folder:
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            for out in pool.map(lambda job: draw(job, folder), jobs):
                print(os.path.relpath(out, ROOT))


# ------------------------------------------------------------------ Google Play
def play_shot(number, name, headline, sub, picture, kind="", width=820, top=500, left=None, more=""):
    left = (1080 - width) // 2 if left is None else left
    body = ('<div class="abs" style="left:76px;right:76px;top:96px">'
            '<h1 class="h" style="font-size:82px">%s</h1>'
            '<p class="sub" style="font-size:37px;margin-top:28px">%s</p></div>%s%s'
            % (headline, sub, phone(picture, width, left, top), more))
    return Job("google-play/screenshot-%d-%s.png" % (number, name), 1080, 1920, body, kind)


def play():
    wave = '<img class="mascot" src="%s" style="height:740px;right:-30px;bottom:-14px">' % pose("waving")
    jobs = [
        play_shot(1, "home", "Business mileage that logs itself.",
                  "Today, this month, and what it comes to.", "home", "lime", top=430),
        play_shot(2, "starts-by-itself", "It starts when your phone connects to your work vehicle.",
                  "By Bluetooth. You do not open the app.", "home-vehicle-connecting", top=520),
        play_shot(3, "recorded", "The drive is recorded with GPS.",
                  "Even with MilO closed.", "home-recording", top=430),
        play_shot(4, "business-or-personal", "Business or Personal, by your work hours.",
                  "You can change any trip yourself.", "trips-trip-actions", top=430),
        play_shot(5, "labels", "Say what each trip was for.",
                  "The report prints the label as the trip’s purpose.", "trip-label", top=430),
        Job("google-play/screenshot-6-report.png", 1080, 1920,
            '<div class="abs" style="left:76px;right:76px;top:96px">'
            '<h1 class="h" style="font-size:82px">The month’s report for your accountant.</h1>'
            '<p class="sub" style="font-size:37px;margin-top:28px">A PDF and a CSV file, made for you.</p></div>'
            + phone("report", 660, 44, 440)
            + '<img class="paper" src="%s" style="width:600px;left:446px;top:1096px;transform:rotate(3deg)">' % report_page(1),
            "lime"),
        play_shot(7, "private", "Your trips stay on your phone.",
                  "No account. No server.", "welcome", top=430),
        play_shot(8, "mascot", "And MilO waves hello.",
                  "He walks in when you open the app.", "home-mascot-wave", width=640, top=400, left=60, more=wave),
    ]
    feature = ('<div class="abs col" style="left:56px;top:0;bottom:0;width:560px;justify-content:center;gap:26px">'
               + brand(30, reversed_mark=True)
               + '<h1 class="h" style="font-size:60px">%s</h1></div>' % HEADLINE
               + '<img class="mascot" src="%s" style="height:490px;right:10px;bottom:0">' % pose("waving"))
    jobs.append(Job("google-play/feature-graphic-1024x500.png", 1024, 500, feature, "lime"))
    draw_all(jobs)
    shutil.copyfile(os.path.join(ROOT, "website", "icon-512.png"), os.path.join(KIT, "google-play", "icon-512.png"))
    print("marketing/google-play/icon-512.png")


def report_page(number):
    return "file://" + os.path.join(KIT, "screenshots", "report-page-%d.png" % number)


# ------------------------------------------------------------------ Product Hunt
def hunt_text(headline, sub, width=560, size=62):
    return ('<div class="abs col" style="left:70px;top:0;bottom:0;width:%dpx;justify-content:center">'
            '<h1 class="h" style="font-size:%dpx">%s</h1>'
            '<p class="sub" style="font-size:27px;margin-top:26px">%s</p></div>' % (width, size, headline, sub))


def hunt():
    row = ('<div style="display:flex;justify-content:space-between;align-items:center;padding:20px 28px;'
           'border-top:1px solid var(--outline);font-size:26px"><span>%s</span><span style="%s">%s</span></div>')
    forgot = "color:var(--text-secondary);font-weight:500"
    log = "".join(row % line for line in (
        ("Monday", "font-weight:600", "23.5 km"), ("Tuesday", forgot, "?"), ("Wednesday", forgot, "?"),
        ("Thursday", "font-weight:600", "16.9 km"), ("Friday", forgot, "?")))
    problem = (hunt_text("A mileage log is easy to forget.",
                         "The date, the times, both addresses, the distance and what it was for. "
                         "For every business drive, all year.", 600)
               + '<div class="tile abs" style="left:760px;top:150px;width:430px;padding:26px 0 8px">'
               '<div style="padding:0 28px 18px;font-size:22px;color:var(--text-secondary)">Mileage log, by hand</div>%s</div>' % log)
    fact = '<div class="tile %s" style="padding:0 34px;line-height:108px;font-size:36px;font-weight:700">%s</div>'
    facts = (hunt_text("Free, and your trips stay on your phone.",
                       "MilO has no account and no server. Your trips leave the phone only in files you send or save yourself, "
                       "in your own Android backup, and as positions handed to Android’s own address lookup.", 600, 56)
             + '<div class="abs col" style="left:760px;top:143px;width:440px;gap:14px">'
             + fact % ("accent", "Free") + fact % ("", "No account") + fact % ("", "No ads")
             + fact % ("", "No subscription") + "</div>")
    jobs = [
        Job("product-hunt/gallery-1-problem.png", 1270, 760, problem),
        Job("product-hunt/gallery-2-starts-by-itself.png", 1270, 760,
            hunt_text("Connect to the truck. The trip starts.",
                      "MilO starts a trip when your phone connects to a work vehicle’s Bluetooth, and records the drive "
                      "with GPS, even with the app closed.")
            + phone("home-vehicle-connecting", 430, 740, 70)),
        Job("product-hunt/gallery-3-business-or-personal.png", 1270, 760,
            hunt_text("Business or Personal, by your work hours.",
                      "Trips in your work hours are saved as Business, the others as Personal. "
                      "You can change any trip yourself, and label what it was for.")
            + phone("trips-trip-actions", 430, 740, 70)),
        Job("product-hunt/gallery-4-monthly-report.png", 1270, 760,
            hunt_text("The month’s report, made for you.",
                      "A PDF and a CSV file for your accountant: every Business trip with its times, "
                      "addresses and distance, and the purpose you gave it.", 590, 58)
            + '<img class="paper" src="%s" style="width:500px;left:712px;top:62px;transform:rotate(2.5deg)">' % report_page(1),
            "lime"),
        Job("product-hunt/gallery-5-private-and-free.png", 1270, 760, facts),
        Job("product-hunt/gallery-6-mascot.png", 1270, 760,
            hunt_text("And MilO waves hello.",
                      "The mascot walks in, turns and waves when you open the app.<br><br>%s<br>%s" % (ANDROID, SITE), 520, 64)
            + '<img class="mascot" src="%s" style="height:690px;right:36px;bottom:24px">' % pose("waving"),
            "lime"),
        Job("product-hunt/thumbnail-240.png", 240, 240,
            '<div class="center" style="width:240px;height:240px">%s</div>' % mark(240).replace('rx="22"', 'rx="0"')),
    ]
    draw_all(jobs)


# ------------------------------------------------------------------ social
def social():
    url = '<div class="abs" style="left:%dpx;bottom:%dpx;font-size:%dpx;font-weight:600">%s</div>'
    jobs = []
    # Link pictures, 1200 x 630: what a link to the website shows when it is shared.
    jobs.append(Job("social/link-1200x630-a-phone.png", 1200, 630,
                    '<div class="tile accent abs col" style="left:40px;top:40px;width:720px;height:550px;padding:44px;justify-content:space-between">'
                    + brand(34, reversed_mark=True)
                    + '<div><h1 class="h" style="font-size:66px">%s</h1>'
                    '<p class="sub" style="font-size:27px;margin-top:22px">A free Android app. It starts a trip when your phone connects to your work vehicle.</p></div>'
                    '<div style="font-size:26px;font-weight:700">%s</div></div>' % (HEADLINE, SITE)
                    + phone("home", 370, 795, 40)))
    jobs.append(Job("social/link-1200x630-b-mascot.png", 1200, 630,
                    '<div class="abs col" style="left:64px;top:0;bottom:0;width:590px;justify-content:center;gap:30px">'
                    + brand(34, reversed_mark=True)
                    + '<h1 class="h" style="font-size:62px">%s</h1><div style="font-size:28px;font-weight:600">%s<br>%s</div></div>'
                    % (HEADLINE, FACTS, SITE)
                    + '<img class="mascot" src="%s" style="height:610px;right:4px;bottom:4px">' % pose("waving"), "lime"))
    step = ('<div class="tile %s col" style="flex:1;padding:30px;justify-content:space-between;height:250px">'
            '<div style="font-size:22px;font-weight:600;opacity:.7">%s</div><div class="h" style="font-size:36px">%s</div></div>')
    jobs.append(Job("social/link-1200x630-c-three-steps.png", 1200, 630,
                    '<div class="abs" style="left:56px;top:52px">%s</div>' % brand(34)
                    + '<h1 class="h abs" style="left:56px;top:140px;font-size:58px">%s</h1>' % HEADLINE
                    + '<div class="abs" style="left:56px;right:56px;top:264px;display:flex;gap:16px">'
                    + step % ("accent", "1", "It starts by itself")
                    + step % ("", "2", "Business or Personal")
                    + step % ("", "3", "The month’s report") + "</div>"
                    + '<div class="abs" style="left:56px;bottom:44px;font-size:26px;color:var(--text-secondary)">%s · %s</div>' % (FACTS, SITE)))

    # Squares, 1080 x 1080: one idea each.
    def square(number, name, body, kind=""):
        return Job("social/square-1080-%d-%s.png" % (number, name), 1080, 1080, body, kind)

    def words(headline, sub, size=84, top=96):
        return ('<div class="abs" style="left:76px;right:76px;top:%dpx"><h1 class="h" style="font-size:%dpx">%s</h1>'
                '<p class="sub" style="font-size:36px;margin-top:26px">%s</p></div>' % (top, size, headline, sub))

    foot = '<div class="abs" style="left:76px;bottom:64px">%s</div>'
    jobs += [
        square(1, "headline", words(HEADLINE, FACTS, 104, 110) + foot % brand(34, SITE, True)
               + '<img class="mascot" src="%s" style="height:620px;right:-10px;bottom:6px">' % pose("thumbs-up"), "lime"),
        square(2, "starts-by-itself", words("Connect to the truck. The trip starts.",
                                           "No button. MilO does not have to be open.", 80)
               + phone("home-vehicle-connecting", 560, 260, 384)),
        square(3, "business-or-personal", words("Business or Personal, by your work hours.",
                                               "You can change any trip yourself.", 78)
               + phone("trips-trip-actions", 560, 260, 384)),
        square(4, "monthly-report", words("The month’s report for your accountant.", "A PDF and a CSV file, made for you.", 78)
               + '<img class="paper" src="%s" style="width:700px;left:190px;top:520px;transform:rotate(-2deg)">' % report_page(1), "lime"),
        square(5, "free", '<div class="abs col" style="left:76px;top:90px;gap:6px">'
               + "".join('<div class="h" style="font-size:108px">%s</div>' % line for line in ("Free.", "No account.", "No ads."))
               + '<div class="h accent-text" style="font-size:108px">No subscription.</div></div>'
               + foot % brand(34, SITE)),
        square(6, "private", words("Your trips stay on your phone.", "MilO has no account and no server.", 96, 110)
               + foot % brand(34, SITE, True)
               + '<img class="mascot" src="%s" style="height:600px;right:20px;bottom:6px">' % pose("peace"), "lime"),
    ]

    # Stories, 1080 x 1920. The top 250 and the bottom 340 pixels are left to the app that shows them.
    jobs += [
        Job("social/story-1080x1920-1-mascot.png", 1080, 1920,
            '<div class="abs" style="left:80px;right:60px;top:280px">%s<h1 class="h" style="font-size:98px;margin-top:40px">%s</h1>'
            '<p class="sub" style="font-size:40px;margin-top:30px">%s</p></div>' % (brand(40, reversed_mark=True), HEADLINE, FACTS)
            + '<img class="mascot" src="%s" style="height:850px;left:176px;bottom:440px">' % pose("waving")
            + '<div class="abs" style="left:0;right:0;bottom:360px;text-align:center;font-size:40px;font-weight:700">%s</div>' % SITE, "lime"),
        Job("social/story-1080x1920-2-starts-by-itself.png", 1080, 1920,
            '<div class="abs" style="left:80px;right:80px;top:270px">%s<h1 class="h" style="font-size:84px;margin-top:40px">Connect to the truck. The trip starts.</h1>'
            '<p class="sub" style="font-size:38px;margin-top:28px">Recorded with GPS, even with MilO closed.</p></div>' % brand(36, SITE)
            + phone("home-recording", 620, 230, 790)),
        Job("social/story-1080x1920-3-monthly-report.png", 1080, 1920,
            '<div class="abs" style="left:80px;right:80px;top:270px">%s<h1 class="h" style="font-size:84px;margin-top:40px">The month’s report for your accountant.</h1>'
            '<p class="sub" style="font-size:38px;margin-top:28px">A PDF and a CSV file, made for you.</p></div>' % brand(36, SITE, True)
            + '<img class="paper" src="%s" style="width:610px;left:235px;top:770px;transform:rotate(-2deg)">' % report_page(1), "lime"),
    ]

    # The banner, 1500 x 500: the middle is kept clear of a profile picture at the lower left.
    jobs.append(Job("social/banner-1500x500.png", 1500, 500,
                    '<div class="abs col" style="left:420px;top:0;bottom:0;width:580px;justify-content:center;gap:20px">'
                    + brand(30, reversed_mark=True)
                    + '<h1 class="h" style="font-size:60px">%s</h1><div style="font-size:25px;font-weight:600;white-space:nowrap">%s · %s</div></div>'
                    % (HEADLINE, FACTS, SITE)
                    + '<img class="mascot" src="%s" style="height:480px;right:20px;bottom:4px">' % pose("waving"), "lime"))
    draw_all(jobs)


# ------------------------------------------------------------------ the mascot
def poses():
    """Draws the poses again in Blender, 2048 pixels high, and makes the 1024 pixel ones."""
    names = ["%s=%s:%d" % (name, clip, frame) for name, clip, frame, _ in POSES]
    with tempfile.TemporaryDirectory() as folder:
        subprocess.run([BLENDER, "-b", os.path.join(ROOT, "design", "mascot", "milo_mascot.blend"), "--python",
                        os.path.join(KIT, "tools", "render_poses.py"), "--", "2048", folder] + names, check=True)
        os.makedirs(POSES_DIR, exist_ok=True)
        for name, _, _, _ in POSES:
            large = Image.open(os.path.join(folder, name + ".png")).convert("RGBA")
            for height in (2048, 1024):
                out = os.path.join(POSES_DIR, "%s-%d.png" % (name, height))
                size = (round(large.width * height / large.height / 2) * 2, height)
                (large if height == 2048 else large.resize(size, Image.LANCZOS)).save(out, "PNG")
                quantise(out)
                print(os.path.relpath(out, ROOT))


def sticker(name, folder, edge=22):
    """The pose with a white rim round it, as a sticker is cut. Returns the picture's path."""
    picture = Image.open(os.path.join(POSES_DIR, name + "-1024.png")).convert("RGBA")
    room = Image.new("RGBA", (picture.width + 4 * edge, picture.height + 4 * edge), (0, 0, 0, 0))
    room.alpha_composite(picture, (2 * edge, 2 * edge))
    # The rim: the pose's shape, spread by a blur and cut hard again, then softened at its edge.
    shape = room.getchannel("A").filter(ImageFilter.GaussianBlur(edge)).point(lambda v: 255 if v > 10 else 0)
    shape = shape.filter(ImageFilter.GaussianBlur(1.5))
    rim = Image.new("RGBA", room.size, (255, 255, 255, 0))
    rim.putalpha(shape)
    rim.alpha_composite(room)
    out = os.path.join(folder, "sticker-%s.png" % name)
    rim.save(out)
    return out


def mascot():
    jobs = []
    for name, _, _, _ in POSES:
        picture = '<img class="mascot" src="%s" style="height:930px;left:%dpx;top:90px">' % (pose(name), (1080 - 930 * 1776 // 2048) // 2 - 12)
        jobs.append(Job("mascot/on-lime/%s-1080.jpg" % name, 1080, 1080, picture, "lime", jpeg=True))
        jobs.append(Job("mascot/on-dark/%s-1080.jpg" % name, 1080, 1080, picture, jpeg=True))
    with tempfile.TemporaryDirectory() as folder:
        cells = "".join(
            '<div class="col" style="align-items:center;gap:6px"><img src="file://%s" style="height:520px;display:block">'
            '<div style="font-size:30px;font-weight:600;color:var(--text-secondary)">%s</div></div>' % (sticker(name, folder), title)
            for name, _, _, title in POSES)
        jobs.append(Job("mascot/sticker-sheet.png", 2400, 1400,
                        '<div class="abs" style="left:70px;top:56px">%s</div>' % brand(40)
                        + '<div class="abs" style="left:40px;right:40px;top:170px;display:flex;flex-wrap:wrap;justify-content:center;gap:34px 30px">%s</div>' % cells,
                        quant=True))
        draw_all(jobs)


# ------------------------------------------------------------------ the logo
def lockup(text_colour, square, letter, size=200):
    """The mark and the name. `size` is the mark's side; the picture is cut to what is drawn."""
    return ('<div class="brand" style="font-size:%dpx;gap:%dpx;color:%s;padding:%dpx">%s<span>MilO Trip Log</span></div>'
            % (round(size * 0.6), round(size * 0.28), text_colour, round(size * 0.3), mark(size, square, letter)))


def logo():
    folder = os.path.join(KIT, "logo")
    os.makedirs(folder, exist_ok=True)
    # The mark itself is the website's file, and its twin for a lime ground.
    shutil.copyfile(os.path.join(ROOT, "website", "mark.svg"), os.path.join(folder, "mark.svg"))
    with open(os.path.join(folder, "mark-on-lime.svg"), "w") as f:
        # Without the picture's own size, as the website's file is: the first of the two
        # "width, height" pairs. The second is the square's, and must stay.
        f.write(mark(72, PAGE, LIME).replace(' width="72" height="72"', "", 1) + "\n")
    variants = (
        # name, the canvas's kind, see-through, the words' colour, the square, the letter
        ("lockup-on-dark", "", False, "#f2f3f5", LIME, PAGE),
        ("lockup-on-lime", "lime", False, PAGE, PAGE, LIME),
        ("lockup-see-through-light-words", "", True, "#f2f3f5", LIME, PAGE),
        ("lockup-see-through-dark-words", "", True, PAGE, LIME, PAGE),
    )
    with tempfile.TemporaryDirectory() as scratch:
        for width in (512, 2048):
            # The lockup is 4 to 1: 1.3 of the mark's side high with its margins, and the name beside it.
            side = width / 6.4
            for name, kind, clear, words, square, letter in variants:
                job = Job("logo/%s-%d.png" % (name, width), width, round(width / 4),
                          '<div class="center" style="width:100%%;height:100%%">%s</div>' % lockup(words, square, letter, round(side)).replace("padding:%dpx" % round(side * 0.3), "padding:0"),
                          kind, clear=clear, quant=True)
                print(os.path.relpath(draw(job, scratch), ROOT))
            for name, square, letter in (("mark", LIME, PAGE), ("mark-on-lime", PAGE, LIME)):
                job = Job("logo/%s-%d.png" % (name, width), width, width, mark(width, square, letter), clear=True, quant=True)
                print(os.path.relpath(draw(job, scratch), ROOT))
        for name, kind, clear, words, square, letter in variants:
            vector(name, kind, clear, words, square, letter, scratch)


def vector(name, kind, clear, words, square, letter, scratch):
    """The lockup as an SVG file whose letters are outlines, so that it needs no font: Chrome
    prints the page to a PDF, which carries the letters' shapes, and poppler turns that into SVG."""
    width, height = 1280, 320
    ground = "transparent" if clear else (LIME if kind == "lime" else PAGE)
    page = os.path.join(scratch, name + ".html")
    with open(page, "w", encoding="utf-8") as f:
        f.write('<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="%s">'
                "<style>@page{size:%dpx %dpx;margin:0} html,body{width:%dpx;height:%dpx}</style></head><body>"
                '<div class="canvas center" style="--w:%dpx;--h:%dpx;background:%s;-webkit-print-color-adjust:exact">%s</div></body></html>'
                % (CSS, width, height, width, height, width, height, ground,
                   lockup(words, square, letter, 200).replace("padding:60px", "padding:0")))
    pdf = os.path.join(scratch, name + ".pdf")
    chrome([CHROME, "--headless=new", "--disable-gpu", "--allow-file-access-from-files", "--no-pdf-header-footer",
            "--virtual-time-budget=3000", "--user-data-dir=" + os.path.join(scratch, "chrome-pdf-" + name),
            "--print-to-pdf=" + pdf, "file://" + page], pdf)
    out = os.path.join(KIT, "logo", name + ".svg")
    subprocess.run(["pdftocairo", "-svg", "-f", "1", "-l", "1", pdf, out], check=True)
    print(os.path.relpath(out, ROOT))


# ------------------------------------------------------------------ other
def other():
    folder = os.path.join(KIT, "other")
    os.makedirs(folder, exist_ok=True)
    # The Android Auto screen, drawn here after a picture of the real one, with the made-up
    # figures of the other pictures. It is a drawing, not a screenshot, and in the kit's
    # typeface, where the car uses its own.
    line = ('<div style="display:flex;gap:26px;align-items:flex-start"><div style="width:36px;padding-top:8px">%s</div>'
            '<div><div style="font-size:27px;font-weight:500;color:#f2f3f5">%s</div>'
            '<div style="font-size:21px;color:#c3c7cf;margin-top:6px">%s</div></div></div>')
    route = ('<svg viewBox="0 0 36 36" width="36" height="36"><path d="M7 27 L28 9" stroke="%s" stroke-width="4" stroke-linecap="round"/>'
             '<circle cx="7" cy="27" r="5" fill="%s"/><circle cx="28" cy="9" r="5" fill="%s"/></svg>' % (LIME, LIME, LIME))
    bars = ('<svg viewBox="0 0 36 36" width="36" height="36"><rect x="4" y="20" width="7" height="12" fill="%s"/>'
            '<rect x="14.5" y="12" width="7" height="20" fill="%s"/><rect x="25" y="4" width="7" height="28" fill="%s"/></svg>' % (LIME, LIME, LIME))
    car = ('<div class="abs" style="left:0;top:0;width:800px;height:480px;background:#000"></div>'
           '<div class="abs" style="left:0;top:0;width:800px;height:400px;background:#22252a;border-radius:0 0 26px 26px">'
           '<div class="abs" style="left:20px;top:16px;display:flex;align-items:center;gap:18px">'
           '<div style="width:48px;height:48px;border-radius:50%%;background:%s;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:18px;color:%s">M</div>'
           '<span style="font-size:29px;color:#c3c7cf;font-weight:400">MilO</span></div>'
           '<div class="abs col" style="left:92px;top:96px;gap:36px">%s%s</div>'
           '<div class="abs center" style="left:210px;top:312px;width:380px;height:64px;border-radius:32px;background:%s;color:#1b2400;font-size:22px;font-weight:600">Start trip</div></div>'
           '<div class="abs center" style="left:374px;bottom:14px;width:52px;height:52px;border-radius:50%%;background:%s;color:%s;font-weight:700;font-size:19px">M</div>'
           '<div class="abs" style="right:24px;bottom:18px;font-size:22px;color:#f2f3f5">9:41</div>'
           % (LIME, PAGE,
              line % (route, "This trip", "Truck parked. A trip starts when it moves"),
              line % (bars, "Business", "Today 22.0 km · September 1192.1 km, $834"),
              LIME, LIME, PAGE))
    jobs = [Job("other/android-auto-screen-800x480.png", 800, 480, car, "", quant=True)]
    draw_all(jobs)
    website(os.path.join(folder, "website-1440x900.png"))


def website(out):
    """The website's front page as this repository has it, 1440 by 900 pixels. The pages name
    their files from the site's root, so the folder is served to Chrome for a moment."""
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kwargs):
            super().__init__(*args, directory=os.path.join(ROOT, "website"), **kwargs)

        def log_message(self, *args):
            pass

    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Quiet)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with tempfile.TemporaryDirectory() as scratch:
            raw = os.path.join(scratch, "site.png")
            chrome([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    "--virtual-time-budget=4000", "--user-data-dir=" + os.path.join(scratch, "chrome"),
                    "--screenshot=" + raw, "--window-size=1440,900", "http://127.0.0.1:%d/" % server.server_address[1]], raw)
            Image.open(raw).convert("RGB").crop((0, 0, 1440, 900)).save(out, "PNG", optimize=True)
            quantise(out)
    finally:
        server.shutdown()
    print(os.path.relpath(out, ROOT))


GROUPS = {"play": play, "hunt": hunt, "social": social, "mascot": mascot, "logo": logo, "other": other}


def main():
    asked = sys.argv[1:]
    if asked == ["poses"]:
        poses()
        return
    unknown = [name for name in asked if name not in GROUPS]
    if unknown:
        raise SystemExit("No such group: %s. There are: %s, and poses." % (", ".join(unknown), ", ".join(GROUPS)))
    for name in asked or list(GROUPS):
        GROUPS[name]()


if __name__ == "__main__":
    main()
