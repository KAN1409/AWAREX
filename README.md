# AWAREX

**Notice what matters before you know to ask.**

AWAREX is a clean-room Android intelligence system built from scratch.

## Product loop

Observe → Remember → Connect → Discover → Prioritize → Surface → Explain with evidence.

## Clean-room rules

- No Cortex source code, schemas, workers, UI, tests, or migrations are reused.
- Package identity: `com.kareem.awarex`.
- Kotlin + Jetpack Compose.
- Evidence is authoritative; AI is an optional reasoning accelerator.
- No feature is considered complete without an end-to-end acceptance test.

## Milestone 0

The first vertical slice is intentionally narrow:

1. Capture an observation.
2. Persist immutable evidence.
3. Extract a commitment/open loop deterministically.
4. Track whether it is still waiting or satisfied.
5. Surface a useful card in **Now** with its supporting evidence.

The app must remain useful even when no AI provider is configured.
