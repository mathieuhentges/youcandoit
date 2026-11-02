# Development plan

Each phase ends with something working and tested before the next builds on it.

## Phase 0 — Domain module ✅
Model, first use cases, JVM unit tests. Pure Kotlin.

## Phase 1 — Complete the domain ✅
- Task: `UpdateTask`, `DeleteTask`
- Category: `RenameCategory`, `DeleteCategory`
- Progress: `ObserveProgressStats` (last 7 days, last 30 days, all time; by priority and category)
- Reminders: pure `ReminderPolicy`, `ReminderScheduler` port, `ReminderSync`, `RescheduleAllReminders`

## Phase 2 — App skeleton ✅
- `:app` module: Compose, Hilt, Navigation Compose, minSdk 26
- Hilt module providing `Clock.systemDefaultZone()` and `UuidIdGenerator`
- Material 3 theme, light and dark
- Optional: GitHub Actions running `./gradlew test lint`

## Phase 3 — Data layer (Room) ✅
- `TaskEntity`, `CategoryEntity`, DAOs returning `Flow`
- Converters: `Instant` as epoch millis, priority as level, reminder as type + offset
- Mappers and repository implementations
- Schema export on from day one
- Tests: mappers; DAO/repository tests on in-memory Room via Robolectric

## Phase 3.5 — Done/Archived lifecycle ✅
- Task status becomes Open → Done → Archived (replaces Open/Closed)
  - Done: stays in its list, checked, at the bottom; can be unticked; reminder cancelled
  - Archived: moves to History, read-only until restored
  - Progress stats count from the time a task was marked done
- Archive by an "Archive done" button per list, or by swiping a single done task
- Domain, Room mapping and tests updated accordingly (schema still v1, no migration)

## Phase 4 — Screens ✅
One ViewModel per screen exposing a single `StateFlow<UiState>`, tested with fakes and Turbine.
Navigation: bottom bar with Home, Lists and History. The app opens on Home, which is the All tasks view.
1. Home: All tasks across every category (the default screen)
2. Lists: categories with counts; tap one to see its tasks; create, rename, delete categories
3. Task list (Home or one category): open tasks sorted, overdue highlight, Done section at the bottom with "Archive done" and swipe to archive
4. Add/edit task: title, description, priority, category (create inline), deadline, reminder
5. History: archived tasks, progress stats, restore

Priority shown as color + label.
- Compose UI tests (add → done → archive → history) moved to Phase 6, alongside on-device checks.

## Phase 5 — Notifications ✅
- `AlarmManager` implementation of `ReminderScheduler`, receiver with a "Mark done" action
- `POST_NOTIFICATIONS` requested on first reminder (Android 13+)
- Exact-alarm permission requested once, inexact fallback if refused
- Reschedule on boot, app update, clock/time zone change, exact-alarm permission change, every task change, and on each app start (covers force-stop)
- Tapping a notification opens the task (deep link todo://task/{id})

## Phase 6 — Polish and release ✅
- App name "You Can Do It!" (launcher label "Can Do It"), package `dev.mathieu.youcandoit`
- Adaptive icon (concept D "Checklist") with themed monochrome version; 512px Play icon
- Appearance setting: Follow phone / Light / Dark (DataStore), on the Home header; no white flash in dark mode
- Target/compile SDK 36 (Play requirement since 2026-08-31), AGP 8.13
- Release build: R8 + resource shrinking, signing from git-ignored keystore.properties
- Compose UI tests on Robolectric: screen tests + the add → done → archive → history flow
- Release guide (claude/RELEASE.md), Play listing text, data safety answers and privacy policy (claude/store/)
- Auto Backup: on by default (allowBackup), which includes the database and settings

## Code review fixes ✅
- Re-registering reminders (app start, reboot…) only schedules; it can no longer dismiss a reminder being shown
- Editing a task doesn't dismiss its already-fired reminder; ticking/deleting/removing the reminder still does
- A "before" reminder that's already too late falls back to the deadline, with a note in the editor
- Done tasks have an "Archive" accessibility action (swipe isn't usable with TalkBack)
- Category names compare case-insensitively with accents too (É = é); locale-aware alphabetical order
- Double-tapping Create in category dialogs no longer reopens the dialog with an error
- Overdue state, counts, "last 7 days" and Today/Tomorrow labels refresh every minute
- Editing a task due in the repeated DST hour keeps its exact deadline
- Locale-aware date formats; deep-link constant moved out of the UI package

## Ideas for later
- Undo after archiving or deleting
- Custom reminder offsets ("Custom…" chip)
- Library upgrades: Kotlin 2.2 + KSP2, Room 2.7, Robolectric 4.16, newer Compose BOM
- French translation
- Home-screen widget
- Search across tasks

## Agreed defaults
- Deleting a category moves its tasks to Uncategorized
- Deadline is date + time (23:59 if only a date is picked)
- Archived tasks are read-only until restored; done tasks can simply be unticked
- Deleting a task is permanent; done → archive is the normal path
