#!/usr/bin/env python3
# demo_data.py - writes the made-up trips the marketing kit's screenshots show, as a file the
# app's own "Import" (Settings, last tile) reads: marketing/demo/MilO-demo-export.json.
#
#   python3 marketing/tools/demo_data.py
#
# NOTHING IN IT IS ANYBODY'S. The driver ("Alex Driver", "Example Electric Ltd.", "2023
# Pickup") and the addresses are the invented ones of the website's sample report
# (website/screenshots/report-sample.png): street names that sound like a town's commercial
# streets, with numbers that were never looked up. The vehicle's Bluetooth address is from the
# range no maker is given ("locally administered"), and spells MILO.
#
# The file is in the form data/transfer/ExportFormat.kt describes (format 3), without GPS
# points. It holds eight weeks of an electrician's driving around Edmonton, up to the morning
# of Thursday 24 September 2026, the day the screenshots were taken on: three to five Business
# trips on a work day between 07:00 and 17:00, a Personal one on some evenings and weekends,
# a holiday, one Saturday call-out marked Business by hand, one trip added by hand and one
# edited, and August's report recorded as sent. The same run always writes the same file.

import json
import math
import os
import random
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

ZONE = ZoneInfo("America/Edmonton")
FIRST_DAY = date(2026, 8, 3)
TODAY = date(2026, 9, 24)
HOLIDAYS = {date(2026, 9, 7)}
VEHICLE = "02:4D:49:4C:4F:01"
OUT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "demo", "MilO-demo-export.json"))

ABOUT = (
    "MilO mileage log. Every closed trip (finished, discarded and deleted), every report "
    "recorded as sent, the settings that mean the same on another phone, and the raw GPS "
    "points if \"points\" is not null. Times are milliseconds since 1970-01-01 UTC, "
    "distances are metres. Not in this file: the event log, the companion device "
    "association with the truck, the sounds chosen from the phone, and the confirmations "
    "of the setup checklist."
)

# name: (address, latitude, longitude, the label a trip that ends there usually gets)
PLACES = {
    "shop": ("4500 Industrial Rd NW, Edmonton", 53.5722, -113.5820, None),
    "supplier": ("3300 Gateway Blvd NW, Edmonton", 53.4850, -113.4930, "Supplier"),
    "leduc": ("1200 Main St, Leduc", 53.2650, -113.5500, "Site visit"),
    "st-albert": ("77 Commerce Way, St. Albert", 53.6400, -113.6200, "Service call"),
    "spruce-grove": ("48 Harbour Rd, Spruce Grove", 53.5450, -113.9000, "Site visit"),
    "riverside": ("910 Riverside Dr SW, Edmonton", 53.4500, -113.5800, "Client meeting"),
    "sherwood-park": ("15 Prairie Lane, Sherwood Park", 53.5300, -113.3000, "Estimate"),
    "market": ("2600 Market St NW, Edmonton", 53.5600, -113.4800, "Inspection"),
    "fort": ("615 Station Rd, Fort Saskatchewan", 53.7100, -113.2100, "Site visit"),
    "plaza": ("88 Lakeview Plaza NW, Edmonton", 53.6000, -113.5300, None),
    "arena": ("5 Arena Way, St. Albert", 53.6300, -113.6300, None),
}
SITES = ["leduc", "st-albert", "spruce-grove", "riverside", "sherwood-park", "market", "fort"]


def road_km(a, b):
    """How far a trip between two of the places is. The places are invented, so this is only a
    figure that suits a tradesperson's day: four fifths of the straight line between the points."""
    _, lat1, lon1, _ = PLACES[a]
    _, lat2, lon2, _ = PLACES[b]
    p1, p2 = math.radians(lat1), math.radians(lat2)
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 6371.0 * 2 * math.asin(math.sqrt(h)) * 0.8


def ms(moment):
    return int(moment.timestamp() * 1000)


class Log:
    def __init__(self):
        self.trips = []
        self.random = random.Random(20260924)

    def drive(self, start, origin, destination, category, label=None, **changed):
        """One finished trip that the vehicle started. Returns when it ended."""
        km = round(road_km(origin, destination) + self.random.uniform(-0.2, 0.6), 1)
        minutes = max(6, round(km / self.random.uniform(40, 58) * 60 + self.random.uniform(2, 6)))
        end = start + timedelta(minutes=minutes, seconds=self.random.randrange(60))
        trip = {
            "id": 0,
            "startedAtMs": ms(start),
            "endedAtMs": ms(end),
            "status": "FINISHED",
            "startedBy": "TRUCK",
            "truckSeen": True,
            # A whole number of tenths of a kilometre, so that every way of adding up agrees.
            "distanceMetres": round(km * 10) * 100.0,
            "startLatitude": PLACES[origin][1],
            "startLongitude": PLACES[origin][2],
            "endLatitude": PLACES[destination][1],
            "endLongitude": PLACES[destination][2],
            "startAddress": PLACES[origin][0],
            "endAddress": PLACES[destination][0],
            "addressAttempts": 1,
            "addressLastAttemptAtMs": ms(end) + 4000,
            "category": category,
            "categorySetByHand": False,
            "ranPastSchedule": False,
            "ignoredOutsideSchedule": False,
            "addedByHand": False,
            "editedByHand": False,
            "startAddressByHand": False,
            "endAddressByHand": False,
            "recordedStartedAtMs": None,
            "recordedEndedAtMs": None,
            "recordedDistanceMetres": None,
            "vehicleAddress": VEHICLE,
            "label": label,
        }
        trip.update(changed)
        self.trips.append(trip)
        return end

    def at(self, day, hour, minute):
        return datetime.combine(day, time(hour, minute, self.random.randrange(60)), ZONE)

    def today(self, day):
        """The morning of the day the screenshots show: two trips, over before 9:41."""
        there = self.drive(self.at(day, 7, 28), "shop", "st-albert", "BUSINESS", "Service call")
        self.drive(there + timedelta(minutes=58), "st-albert", "supplier", "BUSINESS", "Supplier")

    def work_day(self, day):
        """Out from the shop, two or three stops, the supplier on some days, and back."""
        pick = self.random
        stops = pick.sample(SITES, pick.choice([2, 2, 2, 3, 3]))
        if pick.random() < 0.55:
            stops.insert(pick.randrange(1, len(stops) + 1), "supplier")
        now = self.at(day, 7, pick.randrange(12, 50))
        here = "shop"
        for stop in stops + ["shop"]:
            last = stop == "shop"
            # The way back starts before the work hours end, so that it is still Business.
            if not last and now.time() > time(14, 40):
                continue
            label = PLACES[stop][3] if pick.random() < 0.9 else None
            now = self.drive(now, here, stop, "BUSINESS", label)
            here = stop
            now += timedelta(minutes=pick.randrange(35, 105))
        if pick.random() < 0.3:
            evening = self.at(day, 18, pick.randrange(5, 40))
            back = self.drive(evening, "shop", "arena", "PERSONAL")
            self.drive(back + timedelta(minutes=pick.randrange(70, 100)), "arena", "shop", "PERSONAL")

    def weekend(self, day):
        pick = self.random
        if day == date(2026, 9, 12):
            # A Saturday call-out: outside the work hours, so MilO saved it as Personal, and
            # the driver marked both ways Business on the Trips screen.
            out = self.drive(self.at(day, 9, 12), "shop", "riverside", "BUSINESS", "Emergency call", categorySetByHand=True)
            self.drive(out + timedelta(minutes=96), "riverside", "shop", "BUSINESS", categorySetByHand=True)
        elif pick.random() < 0.6:
            out = self.drive(self.at(day, pick.randrange(10, 14), pick.randrange(60)), "shop", "plaza", "PERSONAL")
            self.drive(out + timedelta(minutes=pick.randrange(40, 80)), "plaza", "shop", "PERSONAL")

    def by_hand(self):
        """The two trips with an asterisk on the report: one typed in afterwards, one edited."""
        first =[t for t in self.trips if datetime.fromtimestamp(t["startedAtMs"] / 1000, ZONE).date() == date(2026, 9, 15)][0]
        first.update(
            editedByHand=True,
            recordedStartedAtMs=first["startedAtMs"],
            recordedEndedAtMs=first["endedAtMs"],
            recordedDistanceMetres=first["distanceMetres"],
            distanceMetres=first["distanceMetres"] + 1200.0,
        )
        august = [t for t in self.trips if datetime.fromtimestamp(t["startedAtMs"] / 1000, ZONE).date() == date(2026, 8, 19)][-1]
        august.update(startedBy="MANUAL", truckSeen=False, addedByHand=True, startAddressByHand=True, endAddressByHand=True,
                      startLatitude=None, startLongitude=None, endLatitude=None, endLongitude=None,
                      addressAttempts=0, addressLastAttemptAtMs=None)


def month_total(trips, year, month):
    business = [t for t in trips if t["category"] == "BUSINESS"
                and (lambda d: (d.year, d.month) == (year, month))(datetime.fromtimestamp(t["startedAtMs"] / 1000, ZONE))]
    return len(business), round(sum(t["distanceMetres"] for t in business), 1)


def main():
    log = Log()
    day = FIRST_DAY
    while day <= TODAY:
        if day.weekday() >= 5:
            log.weekend(day)
        elif day == TODAY:
            log.today(day)
        elif day not in HOLIDAYS:
            log.work_day(day)
        day += timedelta(days=1)
    log.by_hand()
    trips = sorted(log.trips, key=lambda t: t["startedAtMs"])
    for number, trip in enumerate(trips, start=1):
        trip["id"] = number

    count, metres = month_total(trips, 2026, 8)
    sent = [{
        "id": 1, "kind": "MONTH", "firstDay": "2026-08-01", "lastDay": "2026-08-31",
        "sentAtMs": ms(datetime(2026, 9, 1, 8, 12, 40, tzinfo=ZONE)),
        "tripCount": count, "distanceMetres": metres, "revision": 0, "distanceUnit": "KILOMETRES",
    }]
    days = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
    settings = {
        "truck": {"address": VEHICLE, "name": "Work truck"},
        "gracePeriodSeconds": 120,
        "minimumTripDistanceMetres": 300,
        "soundEnabled": True,
        "schedule": [{"day": d, "tracked": i < 5, "startMinute": 7 * 60, "endMinute": 17 * 60} for i, d in enumerate(days)],
        "ignoreTripsOutsideSchedule": False,
        "drivingAlertEnabled": True,
        "reportName": "Alex Driver",
        "reportCompany": "Example Electric Ltd.",
        "reportVehicle": "2023 Pickup",
        "accountantEmail": "accounts@example.com",
        "reminderEnabled": True,
        "reminderDay": 1,
    }
    written = datetime(2026, 9, 24, 9, 25, 0, tzinfo=ZONE)
    compact = dict(separators=(",", ":"), ensure_ascii=False)
    lines = ["{"]
    lines.append('"format": "milo-export",')
    lines.append('"formatVersion": 3,')
    lines.append('"exportedAtMs": %d,' % ms(written))
    lines.append('"exportedAt": %s,' % json.dumps(written.isoformat()))
    lines.append('"appVersion": "0.3.1",')
    lines.append('"databaseVersion": 8,')
    lines.append('"about": %s,' % json.dumps(ABOUT))
    lines.append('"contents": %s,' % json.dumps({"trips": len(trips), "sentReports": len(sent), "points": None, "settings": True}, **compact))
    lines.append('"settings": %s,' % json.dumps(settings, **compact))
    lines.append('"sentReports": [')
    lines.append(",\n".join(json.dumps(r, **compact) for r in sent))
    lines.append("],")
    lines.append('"trips": [')
    lines.append(",\n".join(json.dumps(t, **compact) for t in trips))
    lines.append("],")
    lines.append('"pointColumns": ["tripId","wallClockMs","elapsedRealtimeMs","latitude","longitude","accuracyMetres","speedMetresPerSecond"],')
    lines.append('"points": null')
    lines.append("}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    september = month_total(trips, 2026, 9)
    today = [t for t in trips if datetime.fromtimestamp(t["startedAtMs"] / 1000, ZONE).date() == TODAY]
    print("%s: %d trips, %d bytes" % (os.path.relpath(OUT), len(trips), os.path.getsize(OUT)))
    print("August: %d Business trips, %.1f km. September so far: %d Business trips, %.1f km"
          % (count, metres / 1000, september[0], september[1] / 1000))
    print("Today: %d trips, %.1f km" % (len(today), sum(t["distanceMetres"] for t in today) / 1000))


main()
