# AWAREX 1.0 — Single Release Acceptance Gate

AWAREX 1.0 is the next user-facing APK. No intermediate APKs are to be delivered from this branch.

## Product mission
Notice what matters before the user knows to ask.

## Required end-to-end capabilities

- Capture real evidence from manual entry, Android notifications, and Android share intents.
- Reject obvious notification/status noise before it pollutes user-facing memory.
- Canonicalize duplicate observations without losing raw provenance.
- Detect commitments and completion evidence in English and common Egyptian-Arabic phrasing.
- Track open loops durably across process death and app restart.
- Detect overdue commitments from time, not only from new input.
- Extract amounts/currencies and detect meaningful numeric/value changes across related evidence.
- Build evolving situations from related evidence rather than presenting isolated notifications.
- Surface ranked insights with reason, confidence, and supporting evidence.
- Separate Now, World, Memory, and Settings surfaces.
- Provide local-only core intelligence when cloud AI is unavailable.
- Provide proactive Android notifications for genuinely overdue/high-priority items while avoiding repeat spam.
- Allow the user to dismiss/snooze an insight and persist that preference.
- Preserve package identity, user data, database state, and the permanent signing identity.

## Reliability gates

- Unit tests pass.
- Android lint passes.
- Debug APK builds.
- Permanent signed release APK builds.
- APK signature verification passes.
- Existing 0.2.x/0.3.0 data opens without destructive migration.
- No uninstall/reset is required.
- No sample/demo evidence is presented as real user evidence.

## Release rule

Do not bump or distribute 0.x builds from this branch. The first distributed APK from this branch must be AWAREX 1.0.0 after the complete gate above is green.
