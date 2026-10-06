#!/usr/bin/env python3
"""Synthesises MilO's trip-start sound: res/raw/trip_start_chirp.wav.

The sound is an original droid-style chirp, built here from nothing but sine waves: sweeps,
warbles and short beeps. It is not copied from, sampled from or derived from any recording.
The film sound it is meant to remind Shawn of is copyrighted and is not in this project
(docs/APP_ENCYCLOPEDIA.md, "Trip-start sound").

The output is committed, so the build does not run this script. Run it again only to change the
sound:

    python3 tools/make_trip_start_chirp.py

It needs nothing but the Python 3 standard library, and it produces the same bytes every time
(no randomness), so an unchanged script gives an unchanged file.
"""

import math
import struct
import wave
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/trip_start_chirp.wav"

# 22.05 kHz mono is plenty for tones below 4 kHz on a phone speaker, and keeps the file small.
SAMPLE_RATE = 22_050

# Well below full scale: the phone speaker distorts a full-scale sine, and this plays at the
# notification volume Shawn has set.
PEAK = 0.55

# Fade each note in and out over this long, or its edges click.
EDGE_SECONDS = 0.006


def note(seconds, start_hz, end_hz, warble_hz=0.0, warble_depth_hz=0.0):
    """One note: a sine that glides from start_hz to end_hz, optionally wobbling on the way.

    The phase is accumulated sample by sample, so the pitch can change without a click.
    A little second harmonic is mixed in to make it less of a pure whistle.
    """
    count = int(seconds * SAMPLE_RATE)
    edge = int(EDGE_SECONDS * SAMPLE_RATE)
    samples = []
    phase = 0.0
    for index in range(count):
        position = index / count
        # A glide that is even in pitch (exponential in frequency) sounds like a slide whistle.
        frequency = start_hz * (end_hz / start_hz) ** position
        frequency += warble_depth_hz * math.sin(2 * math.pi * warble_hz * index / SAMPLE_RATE)
        phase += 2 * math.pi * frequency / SAMPLE_RATE
        value = math.sin(phase) + 0.18 * math.sin(2 * phase)
        if index < edge:
            value *= 0.5 - 0.5 * math.cos(math.pi * index / edge)
        elif index >= count - edge:
            value *= 0.5 - 0.5 * math.cos(math.pi * (count - index) / edge)
        samples.append(value / 1.18)
    return samples


def silence(seconds):
    return [0.0] * int(seconds * SAMPLE_RATE)


def chirp():
    """The phrase: a rising hello, some chatter, a warbled thought and a questioning whistle."""
    return (
        silence(0.03)
        + note(0.11, 900, 2600)
        + silence(0.025)
        + note(0.05, 2100, 2100)
        + silence(0.02)
        + note(0.05, 1500, 1500)
        + silence(0.02)
        + note(0.07, 2500, 1700)
        + silence(0.035)
        + note(0.20, 1800, 2000, warble_hz=28, warble_depth_hz=420)
        + silence(0.03)
        + note(0.06, 1250, 1250)
        + silence(0.02)
        + note(0.06, 2300, 2300)
        + silence(0.02)
        + note(0.13, 3000, 1100)
        + silence(0.04)
        + note(0.09, 1400, 1900, warble_hz=45, warble_depth_hz=250)
        + silence(0.03)
        + note(0.26, 1300, 3300, warble_hz=11, warble_depth_hz=60)
        + silence(0.05)
    )


def main():
    samples = chirp()
    frames = b"".join(struct.pack("<h", round(PEAK * 32767 * value)) for value in samples)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        output.writeframes(frames)
    print(f"{OUTPUT.name}: {len(samples) / SAMPLE_RATE:.2f} s, {OUTPUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
