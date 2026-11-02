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

## Phase 7 — Drag order and category headers ✅ (not committed yet)
- Open tasks are in the order the user drags them (handle ⠿ on each row); replaces the automatic sort
- New task → top of its category; changing category in the editor → top of the new one
- Home groups open tasks under a header per category (alphabetical, Uncategorized last); Done stays one section
- Dragging a task under another header moves it to that category
- "Move up/down" accessibility actions do the same as dragging
- Room v2: `position` column; auto-migration seeds it from the old sort, so existing tasks keep their order
- Library: `sh.calvin.reorderable` 2.4.3
- Tests: `ReorderTasks`, grouping, header crossing, v1 → v2 migration on a real v1 database

---
**Release 1** = everything above (Phases 0–7): `versionCode` 1, `versionName` 0.1.0, database v2.
Everything below goes into a later release.

---

## Phase 8 — Requested changes ✅ (not committed yet)
Done in this order: small visual changes first, sub tasks last because they touch everything.

### 8.1 Priority order and colors ✅
- Editor priority picker reads Minimal → Urgent, left to right (Urgent at the far right)
- Medium becomes cyan, light and dark:
  - Light: dot `#00ACC1`, chip `#E0F7FA`, text `#006064`
  - Dark: dot `#4DD0E1`, chip `#003C44`, text `#B2EBF2`
  - Check on the phone that cyan Medium and blue Low are easy to tell apart; nudge Low toward indigo if not
- History "Done by priority" bars stay vertical with Urgent on top (unchanged)
- Tests: screen test for the picker's left-to-right order

### 8.2 Adding several tasks at once ✅
- **Several at once**: a switch in the new-task editor turns the title into a multi-line box
  (instead of a separate sheet: it reuses the editor's pickers)
  - One task per non-blank line, trimmed; a paste of a list works the same way
  - Priority, category, and optional deadline chosen once, applied to every line; no description
  - Live count on the button: "Add 4"
  - The tasks keep the typed order and land at the top of their category
- **Save and add another** in the editor (new tasks only, not when editing)
  - Saves, then clears title, description and deadline; keeps priority and category
  - Snackbar "Task added" so it's clear the save happened
- Domain: `AddTasks` (validates every line first; adds all or none, in one transaction)
- Tests: `AddTasks` (blank lines skipped, order kept, all-or-nothing), editor "add another" flow

### 8.3 Sort menu on All tasks (and category lists) ✅
- A sort button in the list header opens a menu:
  - **Sort by**: My order (default) / Priority (Urgent first, then nearest deadline) / Deadline (nearest first, none last) / Date added (newest first)
  - **Group by category**: on/off (on = today's headers)
- Dragging only in "My order"; in other sorts the handle is hidden
- "My order" always has headers (drag order is kept per category); the grouping switch applies to the other sorts
- Remembered across launches (DataStore, like Appearance); one setting shared by all lists
- Done section unaffected (most recently done first)
- Sorting is applied in `ObserveTaskList`, so it's unit-tested without the UI
- Tests: each sort mode, grouping on/off, handle hidden outside My order, setting survives a restart

### 8.4 Sub tasks (full tasks under a parent) ✅
Built with the defaults below.
- **One level only**: a sub task can't have its own sub tasks
- **Same category as its parent**, always; changing the parent's category moves its sub tasks too
- Each sub task is a full task: own priority, deadline, reminder, done/archived state
- **Display**: indented under the parent, with a "2/5 done" count on the parent row; collapse/expand arrow
- **Adding**: "Add sub task" in the parent's editor (and Quick add can target a parent)
- **Order**: sub tasks are dragged within their parent; dragging the parent moves the whole block;
  other sorts sort sub tasks within their parent
- **Ticking the parent** with open sub tasks: asks "Also mark the 3 sub tasks done?" (Mark all done / Cancel).
  A done parent never has open sub tasks, so they always have somewhere to show.
  The notification's "Mark done" ticks the sub tasks too (it can't ask)
- **Unticking a sub task** of a done parent unticks the parent too (same rule)
- **Ticking the last open sub task**: the parent stays open (no automatic completion)
- **Restoring** a sub task from History restores its parent too
- **Archiving** a parent archives its done sub tasks with it; restoring brings them back
- **Deleting** a parent asks for confirmation and deletes its sub tasks
- **Stats** count every task, parent or sub task, as one
- **Lists screen counts**: sub tasks counted like any task
- Data: Room v3, `parent_id` column (foreign key to tasks, cascade delete), migration test v2 → v3
- Domain: `Task.parentId`; rules in `AddTask`/`UpdateTask`/`MarkTaskDone`/`ArchiveTask`/`DeleteTask`
  (parent must exist, be open, have no parent itself, and share the category)
- Tests: every rule above, reminders on sub tasks, migration, UI indent/collapse/drag

## Phase 9 — Multi-select ✅ (not committed yet)
- Long press a task to enter selection mode; tap then adds/removes tasks; ✕ or Back leaves
- Selection bar: "3 selected", Set priority, Move to category, Delete (asks first, says how many sub tasks go too)
- While selecting: no dragging, no swipe-to-archive, and the check selects instead of ticking
- Sub tasks: priority and delete apply; they never move on their own (a moved parent takes them along)
- Each bulk change is one write; a deleted or archived task drops out of the selection
- Domain: `DeleteTasks`, `SetTasksPriority`, `MoveTasksToCategory`; `TaskRepository.deleteAll` (one SQL statement)

## Phase 10 — Show the encouragement again ✅ (not committed yet)
- A heart button on Home's header (next to Appearance) brings the welcome screen back over the app
- "Let's go" or Back closes it as usual; the once-a-day showing is unchanged

## Phase 11 — Sort the History ✅ (not committed yet)
- A sort button on the Archived header: Recently archived (default), Recently done, Priority, Category (A–Z, Uncategorized last)
- Ties go to the most recently archived; the choice is remembered across launches (separate from the lists' sort)
- The stats above are unaffected

## Phase 12 — Sort the Done section ✅ (not committed yet)
- A sort icon on each list's Done header: Recently done (default), Priority, Category (Home only: A–Z, Uncategorized last)
- Independent of the open tasks' sort, but stored with it (`ListSort.doneOrder`) and shared by all lists
- Ties go to the most recently done; a done parent's sub tasks stay under it, most recently done first

## Review cleanups (later)
- Sub-task lookups (`subTasksOf`, `topPositionIn`, `ObserveSubTasks`) load the whole task table; use DAO queries on the `parent_id` index
- "Open first, then most recently done" sub-task ordering exists in both `ObserveTaskList` and `ObserveSubTasks`; share one helper
- `UpdateTask`: a sub task whose category disagrees with its parent's gets a top-level position (only possible with already-inconsistent data)

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
