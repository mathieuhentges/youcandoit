# You Can Do It!

Android to-do app: Kotlin, Jetpack Compose, Room, Hilt, DataStore. Local-only, no backend.
Package: `dev.mathieu.youcandoit` (permanent; the display name can change freely).

## Modules
- `:domain` — pure Kotlin: models, use cases, rules. JUnit 5 tests; shares its in-memory fakes via test fixtures.
- `:app` — Android: Room data layer, reminders (AlarmManager + notifications), Hilt wiring, Compose UI.
  JUnit 4 + Robolectric tests, including Compose UI tests.

## Getting started
Step-by-step guide: [claude/RUN_LOCALLY.md](claude/RUN_LOCALLY.md). In short, with Android Studio Narwhal 3 or newer:

```
./gradlew test          # all tests, no device needed
./gradlew installDebug  # install on a connected phone or emulator
```

The Gradle wrapper (8.13) is included. The Room schema is exported to `app/schemas/` on build — commit it.

## Docs
- [claude/RUN_LOCALLY.md](claude/RUN_LOCALLY.md) — run the app and tests on your machine
- [claude/PLAN.md](claude/PLAN.md) — development plan
- [claude/RELEASE.md](claude/RELEASE.md) — signing, building and publishing to the Play internal testing track
- [claude/store/](claude/store/) — Play listing text, privacy policy, icon
- [claude/designs/](claude/designs/) — screen and icon mockups
