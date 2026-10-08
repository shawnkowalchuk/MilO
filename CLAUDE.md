# CLAUDE.md — Operating Instructions for the AI Assistant

> **Read this file first, at the start of every session, before doing anything else.** It is the standing order for how this project is built. It exists to stop two mistakes from recurring: (1) dependencies rotting until they're too painful to update, and (2) rebuilding or breaking things because nobody remembered they already existed. Follow it exactly. When in doubt, ask — don't assume.

---

## Your prime directive

Keep the code and the documentation in lockstep, and never let the assistant's convenience create debt the founder pays for later. Speed is fine; sprawl is not. Every shortcut must be visible.

---

## Start-of-session protocol (do this every time)

Before responding to any build request, read, in this order:
1. **`docs/ENGINEERING_STANDARDS.md`** — the rules for *how* to build.
2. **`docs/ARCHITECTURE.md`** — the shape of the system and what's shared between the phone UI and the Android Auto screen.
3. The relevant entry in **`docs/APP_ENCYCLOPEDIA.md`** — how the feature you're about to touch already works.
4. The top of **`docs/FINDINGS_LOG.md`** — what happened recently and why.

If you skip this, you will make a decision that's already been made, and that's the whole problem we're solving.

---

## Before you build ANY feature — answer these, ask if unsure

1. **"Has this already been built?"** Search `docs/APP_ENCYCLOPEDIA.md` first. If a capability for this exists, **extend it — do not rebuild it.**
   - *Concrete example:* before adding "award a badge when a user does X," check the encyclopedia for an existing badge capability. If badges already exist, plug into that system. Don't create a second, parallel way to do badges. This is the exact failure mode — half-remembered features getting reinvented — that this whole setup prevents.
2. **"Which surfaces?"** MilO has two: the phone UI and the Android Auto screen. **Ask explicitly: phone only, Android Auto only, or both?** Never default to one or assume both. Then put all non-UI logic (trip rules, distance, schedule, data access) in the shared `core/`, `data/` and `platform/` packages so the two surfaces can't drift.
3. **"Does the UI already have a pattern for this?"** If yes, reuse the existing component/pattern exactly. If no, you're adding to the design system deliberately — flag it and say so. Never create a one-off style. (STANDARDS §8.)
4. **"Where does it live?"** Place it in the feature-first structure (STANDARDS §3). One feature, one folder.
5. **"What's the smallest version that delivers the value?"** Build that first.

**If any of these is unclear, stop and ask the founder before writing code.** A 30-second question is cheaper than a rebuild.

---

## Dependencies — keep them current so they never become a wall

The recurring pain: deps go stale, then updating them is a nightmare. Prevent it from day one.

- **At project start:** install the **latest *stable*** version of every dependency — never a beta/RC/alpha/next. Pin exact versions in `gradle/libs.versions.toml` (no ranges, no dynamic `+` versions). Commit the Gradle wrapper with its checksum.
- **Dependabot is set up from day one** (chosen over Renovate in ADR-001) so updates arrive as small PRs continuously and CI catches breakage immediately. Stale-dependency pain is just a year of skipped small updates collapsed into one migration — automation prevents the collapse.
- **Every time you add a dependency:** confirm it's the current stable release and that it's actively maintained. If you're about to add something abandoned or redundant with an existing dep, say so instead.
- **Never upgrade a major version silently or in a mixed diff.** One major upgrade per branch, with its breaking-changes read and the smoke test run.
- **If you notice anything is out of date, flag it** — don't quietly build on top of stale packages.

---

## Secrets & passwords — never hardcoded

- Never write a password, API key, token, or secret into code, a script, or anything committed to git. Ever. (It lives in git history forever.)
- MilO has no backend, no API keys and no sign-in. The app's only secrets are the signing keystore and its passwords. They live outside the repository and are **prompted for or read at build time** from a git-ignored file, never committed.
- The website (`website/`, milotriplog.top) has one secret of its own since 2026-10-08: the Firebase Hosting deploy key, kept only in GitHub's Actions secrets (ADR-003). It is never downloaded into the repository. The website is not one of the app's two surfaces; the app gets no Firebase.
- If a secret is ever added (an API key, a token), it follows the same rule and gets an ADR first.
- If a required secret is missing, fail loudly. (STANDARDS §12.)

---

## The build loop (every change, no exceptions)

```
READ standards + architecture + the encyclopedia entry
   ↓
ASK the kickoff questions above; wait for answers if unclear
   ↓
BUILD the smallest correct version, following existing patterns
   ↓
UPDATE docs/APP_ENCYCLOPEDIA.md if behavior was added or changed
   ↓
APPEND to docs/FINDINGS_LOG.md: what changed, and the WHY that isn't obvious from the code
```

The last two steps are not optional. They are why next month's session won't repeat this month's mistakes.

---

## Before you tell the founder you're done

Confirm all of these — say which ones you did:

- [ ] Kotlin compiles with warnings as errors; Android Lint, Spotless and unit tests pass
- [ ] No secrets, no stray `Log`/`println` debugging, no commented-out code in the diff
- [ ] UI reuses existing tokens/components — no one-off styling
- [ ] Surface scope matches what was decided (phone / Android Auto / both)
- [ ] Any new dependency is the current stable version, pinned
- [ ] **`docs/APP_ENCYCLOPEDIA.md` updated** if behavior changed
- [ ] **`docs/FINDINGS_LOG.md` appended** with what changed and why
- [ ] If a corner was knowingly cut, it's logged as `[DEBT]` in the findings log and tagged `// TODO(debt):` in code

If you cannot check one, say so plainly rather than claiming it's done.

---

## When the founder asks "did I already build / decide this?"

Don't guess. Search, in order:
- **A feature or capability** → `docs/APP_ENCYCLOPEDIA.md`
- **Why something was done a certain way** → `docs/FINDINGS_LOG.md`, then `docs/adr/`
- **How something is supposed to be done** → `docs/ENGINEERING_STANDARDS.md`
- **The shape of the system / what's shared between the phone UI and the Android Auto screen** → `docs/ARCHITECTURE.md`

Answer from the documents, not from memory of the conversation.

---

## How to behave, in short

Be the assistant that reads before it writes, asks before it assumes, reuses before it rebuilds, keeps dependencies fresh, and writes down what it did. That single set of habits is the entire difference between a codebase that stays clean and one that turns into the last one.
