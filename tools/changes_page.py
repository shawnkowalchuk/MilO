#!/usr/bin/env python3
"""Checks MilO's list of changes and writes the website's What's new page from it
(docs/APP_ENCYCLOPEDIA.md, "Version and What's new").

The list is app/src/main/assets/changelog.json: built into the app for its What's new screen,
and turned into website/changes.html here, so the two never say different things. The page is
committed like every other file of the website (plain files, no build: ADR-003), and CI runs
this script with --check, which fails when the list breaks a rule below or the page is not the
one the list makes.

After editing the list:

    python3 tools/changes_page.py           # checks the list, then writes the page
    python3 tools/changes_page.py --check   # checks the list and the page, writes nothing

The rules, beyond the form the app reads (data/changelog/Changelog.kt):
- versions are three numbers ("0.1.0"), each named once, newest first;
- only the newest version may be without a date (not released yet), and dates go back in time
  down the list;
- the newest version is the build's versionName in app/build.gradle.kts, so the app always
  finds its own version in the list.

It needs nothing but the Python 3 standard library, and it writes the same bytes every time.
"""

import datetime
import html
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = ROOT / "app/src/main/assets/changelog.json"
GRADLE = ROOT / "app/build.gradle.kts"
PAGE = ROOT / "website/changes.html"

# The four kinds of change, with the word the app's tag and the page's tag show.
KINDS = {"new": "New", "improved": "Improved", "fixed": "Fixed", "security": "Security"}

VERSION = re.compile(r"^\d+\.\d+\.\d+$")
VERSION_NAME = re.compile(r'^\s*versionName\s*=\s*"([^"]+)"', re.MULTILINE)


def problems_in(changelog, version_name):
    """Every rule the list breaks, as sentences. An empty list means it is fine."""
    if not isinstance(changelog, dict) or set(changelog) != {"releases"}:
        return ['The file must hold one key, "releases"']
    releases = changelog["releases"]
    if not isinstance(releases, list) or not releases:
        return ['"releases" must list at least one version']
    found = []
    for index, release in enumerate(releases):
        found += problems_in_release(index, release)
    if found:
        return found
    versions = [tuple(int(part) for part in r["version"].split(".")) for r in releases]
    if any(newer <= older for newer, older in zip(versions, versions[1:])):
        found.append("Versions must be newest first, each named once")
    if any(r["date"] is None for r in releases[1:]):
        found.append("Only the newest version can be without a date")
    dates = [r["date"] for r in releases if r["date"] is not None]
    if any(newer < older for newer, older in zip(dates, dates[1:])):
        found.append("Dates must go back in time down the list")
    if releases[0]["version"] != version_name:
        found.append(
            f'The newest version, {releases[0]["version"]}, is not the build\'s versionName, '
            f"{version_name} (app/build.gradle.kts)"
        )
    return found


def problems_in_release(index, release):
    where = f"Version number {index + 1}"
    if not isinstance(release, dict) or set(release) != {"version", "date", "changes"}:
        return [f'{where} must have exactly "version", "date" and "changes"']
    found = []
    version, date, changes = release["version"], release["date"], release["changes"]
    if not isinstance(version, str) or not VERSION.match(version):
        found.append(f'{where}: "{version}" is not three numbers like "0.1.0"')
    else:
        where = version
    if date is not None:
        try:
            datetime.date.fromisoformat(date)
        except (TypeError, ValueError):
            found.append(f'{where}: the date "{date}" is not a day like "2026-10-09"')
    if not isinstance(changes, list) or not changes:
        return found + [f"{where} must list at least one change"]
    for change in changes:
        if not isinstance(change, dict) or not {"kind", "title"} <= set(change) <= {
            "kind",
            "title",
            "body",
        }:
            found.append(f'{where}: a change must have "kind" and "title", and may have "body"')
            continue
        if change["kind"] not in KINDS:
            found.append(f'{where}: "{change["kind"]}" is not one of {", ".join(KINDS)}')
        for key in ("title", "body"):
            text = change.get(key, "-")
            if not isinstance(text, str) or not text.strip():
                found.append(f'{where}: a change\'s "{key}" must be some words')
    return found


def page(changelog):
    """The whole page, in the website's own frame (website/compare.html has the same)."""
    sections = "\n".join(release_section(release) for release in changelog["releases"])
    return PAGE_TEMPLATE.replace("{releases}", sections)


def release_section(release):
    version = release["version"]
    if release["date"] is None:
        when = "Not released yet"
    else:
        day = datetime.date.fromisoformat(release["date"])
        when = f"{day.day} {day:%B %Y}"
    lines = "\n".join(change_line(change) for change in release["changes"])
    anchor = "v" + version.replace(".", "-")
    return f"""      <section class="tile release" id="{anchor}">
        <h2>Version {version} <span class="secondary">· {when}</span></h2>
        <ul class="changes">
{lines}
        </ul>
      </section>"""


def change_line(change):
    kind = change["kind"]
    body = change.get("body")
    said = f'\n              <p class="secondary">{html.escape(body)}</p>' if body else ""
    return f"""          <li>
            <span class="tag tag-{kind}">{KINDS[kind]}</span>
            <div>
              <strong>{html.escape(change["title"])}</strong>{said}
            </div>
          </li>"""


PAGE_TEMPLATE = """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <!-- Written by tools/changes_page.py from app/src/main/assets/changelog.json. Edit the list, not this page. -->
  <title>What's new · MilO Trip Log</title>
  <meta name="description" content="Every version of MilO Trip Log, the free automatic mileage tracker for Android, and what changed in each.">
  <link rel="canonical" href="https://milotriplog.top/changes">
  <link rel="icon" href="/mark.svg" type="image/svg+xml">
  <link rel="stylesheet" href="/styles.css">
  <meta name="theme-color" content="#121316">
</head>
<body>
  <div class="page">
    <header class="top">
      <a class="brand" href="/"><img src="/mark.svg" alt="" width="36" height="36">MilO Trip Log</a>
      <nav aria-label="Main">
        <a href="/#install">Install</a>
        <a href="/compare">Compare</a>
        <a href="/changes" aria-current="page">What's new</a>
        <a href="/privacy">Privacy</a>
        <a href="https://github.com/shawnkowalchuk/MilO">GitHub</a>
      </nav>
    </header>

    <main>
      <section class="tile">
        <h1 class="page-title">What's new</h1>
        <p>Every version of MilO and what changed in it, newest first. The same list is in the app, under Settings, Version.</p>
        <p class="secondary">The newest released version is the <a href="https://github.com/shawnkowalchuk/MilO/releases/latest">latest download on GitHub</a>. To update, install it over the one you have: your trips stay.</p>
      </section>

{releases}
    </main>

    <footer>
      <p>© 2026 Shawn Kowalchuk. All rights reserved.</p>
      <p><a href="/privacy">Privacy</a> · <a href="https://github.com/shawnkowalchuk/MilO">Source on GitHub</a> · The typeface is Sora, under the <a href="/fonts/Sora-OFL.txt">SIL Open Font License 1.1</a>.</p>
      <p>MilO Trip Log is an independent project, not affiliated with any other app or company named Milo.</p>
    </footer>
  </div>
</body>
</html>
"""


def main(argv):
    check_only = argv[1:] == ["--check"]
    if argv[1:] not in ([], ["--check"]):
        print("Usage: python3 tools/changes_page.py [--check]", file=sys.stderr)
        return 2
    try:
        changelog = json.loads(CHANGELOG.read_text(encoding="utf-8"))
    except ValueError as broken:
        print(f"{CHANGELOG.relative_to(ROOT)} is not valid JSON: {broken}", file=sys.stderr)
        return 1
    found = VERSION_NAME.search(GRADLE.read_text(encoding="utf-8"))
    if found is None:
        print(f"No versionName in {GRADLE.relative_to(ROOT)}", file=sys.stderr)
        return 1
    problems = problems_in(changelog, found.group(1))
    if problems:
        print(f"{CHANGELOG.relative_to(ROOT)} breaks a rule:", file=sys.stderr)
        for problem in problems:
            print(f"  - {problem}", file=sys.stderr)
        return 1
    wanted = page(changelog)
    if check_only:
        if not PAGE.exists() or PAGE.read_text(encoding="utf-8") != wanted:
            print(
                f"{PAGE.relative_to(ROOT)} is not the page the list makes. "
                "Run: python3 tools/changes_page.py",
                file=sys.stderr,
            )
            return 1
        print("The list of changes and the website's page agree.")
        return 0
    PAGE.write_text(wanted, encoding="utf-8")
    print(f"Wrote {PAGE.relative_to(ROOT)}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
