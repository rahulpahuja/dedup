# DeDup — Feature Plan (Epics A–D)

Status legend: `[ ]` not started · `[~]` in progress · `[x]` done

Scope: items 2, 3, 5, 6 from the prior feature brainstorm, mapped 1:1 to epics A–D below.

---

## Problem statements & success criteria

**Item 2 → Epic A — Finish background auto-scan**
Problem: `ScanWorker` is a dead stub (`doWork()` just returns `Result.success()`), so duplicates/junk silently accumulate until a user remembers to open the app and scan manually.
Success criteria: A periodic background job actually scans, computes reclaimable space, and — when it crosses a threshold — notifies the user, with a way to turn it off.

**Item 3 → Epic B — Expose voice-storage as AppFunctions**
Problem: The NL query engine already built in `feature:voice-storage` (`VoiceQueryParser` + `LocalStorageRepository`) only works inside the in-app chat screen; it's invisible to Assistant/system agents.
Success criteria: At least two read-only storage queries ("find large files", "find old photos") are registered as Android AppFunctions, discoverable and invokable outside the app via the system, backed by the existing repository/parser (no duplicated logic).

**Item 5 → Epic C — "Storage Memories" resurfacing**
Problem: Photo embeddings are indexed (`ImageEmbeddingDao`/`ImageIndexWorker`) but never resurfaced — there's no retention loop, no "look what you have" moment.
Success criteria: A new in-app Memories screen groups previously-indexed photos by capture date ("on this day", "N years ago") and is reachable from the dashboard.

**Item 6 → Epic D — Storage forecast → proactive push**
Problem: `StorageForecastingRepository` already predicts days-until-full but it's only visible if a user opens the dashboard and looks at the card.
Success criteria: When the forecast crosses a low-storage threshold, the app proactively fires a local notification (deduped, at most once/day), and the in-app card shows the same numbers.

---

## Epic A — Finish background auto-scan (`ScanWorker`)

**Goal:** Turn `ScanWorker` from a no-op stub into a real periodic job that finds duplicates/junk and notifies the user, following the existing `ImageIndexWorker` scheduling pattern and `AppNotificationManager` notification pattern.

**Non-goals:** User-configurable scan frequency; scanning junk/WhatsApp/social categories beyond duplicates in this pass; redesigning the notification tap-through UX beyond deep-linking to an existing screen.

**Acceptance criteria:**
- `ScanWorker.doWork()` runs a real duplicate scan and computes reclaimable bytes.
- A notification fires only when reclaimable bytes exceed a sane threshold, and only if notification permission is granted.
- The job is scheduled periodically at app start (`ExistingPeriodicWorkPolicy.KEEP`), battery-friendly constraints.
- Users can disable it from Settings; disabling cancels the scheduled work.

**Risks/dependencies:** `SemanticDuplicateRepository.findDuplicateGroups()` requires the embedding index to be populated first (depends on `ImageIndexWorker` having run) — a scan on a fresh install may find nothing, which is expected, not a bug. Epic D (Story D1) extends this same worker, so Epic A must land first.

### Stories

**A1 — Implement real duplicate scan in `ScanWorker.doWork()`**
- Outcome: worker computes duplicate groups and total reclaimable bytes instead of doing nothing.
- Files: `app/src/main/java/com/rp/dedup/core/workers/ScanWorker.kt`
- AC: calls `SemanticDuplicateRepository.findDuplicateGroups()`, sums size of all-but-one file per group, returns `Result.success()` with output data (`groupCount`, `reclaimableBytes`); returns `Result.retry()` on transient failure.
- Test: unit test mocking the repository with a fixed set of duplicate groups, asserting the computed reclaimable-bytes value and `Result` type.

**A2 — Fire "duplicates found" notification from scan results**
- Outcome: user gets a notification when the scan finds meaningful reclaimable space; no notification when it doesn't.
- Files: `ScanWorker.kt` (call `AppNotificationManager`), notification copy.
- AC: notification fires only when `reclaimableBytes` > threshold; respects `hasNotificationPermission()`; tapping deep-links to the Dashboard (`ROUTE_DASHBOARD`) — not `results_media`/duplicates screen directly, since that route is deliberately excluded from `MainActivity`'s deep-link allowlist (requires prior scan context; expanding a security allowlist is out of scope for this story).
- Test: unit test asserting the notification manager is/isn't invoked for above/below-threshold and permission-denied cases.

**A3 — Schedule `ScanWorker` as periodic work at app start**
- Outcome: the worker actually runs on a schedule instead of sitting dead code.
- Files: `ScanWorker.kt` (companion `enqueuePeriodic(context)`), `app/src/main/java/com/rp/dedup/core/app/DeDupApp.kt`.
- AC: `PeriodicWorkRequestBuilder` (24h interval), constraints `setRequiresBatteryNotLow(true)`, `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)`, called once from `DeDupApp.onCreate()`.
- Test: Robolectric test using `WorkManagerTestInitHelper` asserting the unique periodic work is enqueued with expected constraints/tag.

**A4 — Settings toggle to enable/disable background auto-scan**
- Outcome: user can turn the feature off; matches existing settings patterns (theme/language toggles).
- Files: existing settings preferences repo (DataStore), Settings screen composable, `ScanWorker` enqueue/cancel call sites.
- AC: preference defaults to on; toggling off calls `WorkManager.cancelUniqueWork`; toggling on re-enqueues; persisted across restarts.
- Test: unit test for preference read/write; test that the toggle handler calls enqueue/cancel appropriately.

---

## Epic B — Expose voice-storage as AppFunctions

**Goal:** Register read-only storage queries as Android AppFunctions (`androidx.appfunctions`, service-entry-point architecture) backed directly by the existing `LocalStorageRepository`/`VoiceQueryParser` in `feature:voice-storage` — no reimplementation.

**Non-goals:** Exposing delete/destructive actions via AppFunctions in this pass (the skill's security constraint explicitly disallows irreversible actions without a confirmation step — deferred to a future epic with a confirmation trampoline). Full MCP-description polish beyond the KDoc-refinement story.

**Acceptance criteria:**
- `:app` builds with `androidx.appfunctions` + KSP compiler wired in.
- At least two functions (`findLargeFiles`, `findOldPhotos` or similar) are registered, callable via ADB per the skill's testing step, and return real data from `LocalStorageRepository`.
- KDoc on all new `@AppFunction`/`@AppFunctionSerializable` members follows the skill's inline-KDoc, agent-oriented convention.

**Risks/dependencies:** App has **no Hilt/DI framework** — must use the skill's framework-agnostic service-locator pattern (`applicationContext` access), not the Hilt example. `feature:voice-storage` has `minSdk 24` but AppFunctions need API 36+, so all new code needs `@RequiresApi(36)` guards; confirm `LocalStorageRepository`/`VoiceQueryParser` are public (not `internal`) before B2 — if internal, first sub-step of B2 is to widen visibility.

### Stories

**B1 — Wire AppFunctions build dependencies into `:app`**
- Outcome: KSP + `androidx.appfunctions` configured and proven with a placeholder function; nothing user-facing yet.
- Files: `gradle/libs.versions.toml` (new version/library entries), `app/build.gradle.kts` (KSP plugin + deps), a minimal placeholder `@AppFunctionServiceEntryPoint` class to prove the toolchain.
- AC: `./gradlew :app:assembleDevDebug` succeeds and KSP generates the expected XML/service metadata for the placeholder.
- Test: build passes; no unit test (pure config) — verified by the build itself.

**B2 — Implement `findLargeFiles` AppFunction**
- Outcome: first real, read-only capability — find files above a size threshold, sorted by size descending — invokable outside the app.
- Files: new `app/src/main/java/com/rp/dedup/core/appfunctions/DeDupAppFunctionService.kt` (abstract class extends `AppFunctionService`, `@AppFunctionServiceEntryPoint`, service-locator style reaching `LocalStorageRepository` via `applicationContext`), `@AppFunctionSerializable` result data class, `res/xml/app_metadata.xml`, manifest service registration.
- AC: `@RequiresApi(36)`, `suspend`, runs on `Dispatchers.IO`, maps params → `FilterConfig` → `LocalStorageRepository.queryFiles()`, throws `AppFunctionInvalidArgumentException` on bad input.
- Test: extract the param→`FilterConfig` mapping and result-mapping into small pure functions and unit-test those directly (the `AppFunctionService` shell itself isn't unit-testable); manual ADB invocation documented in the commit for verification.

**B3 — Implement `findOldPhotos` / `getStorageSummary` AppFunctions**
- Outcome: second/third read-only capability, reusing `VoiceQueryParser`'s date-filter logic.
- Files: same service file, extended.
- AC/Test: same pattern as B2.

**B4 — KDoc refinement pass for agent consumption**
- Outcome: all new `@AppFunction`/`@AppFunctionSerializable` members have agent-optimized inline KDoc (operational patterns + constraints), per the skill's Step 3.
- Files: `DeDupAppFunctionService.kt` (docs only), `app_metadata.xml` description.
- AC: inline per-property KDoc (no class-level `@param`); manually verified via the skill's ADB listing command that descriptions surface correctly.
- Test: none automated (docs); manual ADB verification step recorded in the commit message.

---

## Epic C — "Storage Memories" resurfacing

**Goal:** Reuse the already-computed image index (`ImageEmbeddingDao`) to resurface photos grouped by capture date, as a new dashboard-reachable screen.

**Non-goals:** Push notifications for memories (could reuse Epic A/D's notification infra later, but out of scope here); modifying the `image_embeddings` Room schema.

**Acceptance criteria:**
- A `MemoriesRepository` groups indexed photo URIs by MediaStore `DATE_TAKEN` into "on this day" / "N years ago" buckets without any DB migration (reads `DATE_TAKEN` live via `ContentResolver`, not stored).
- A dashboard entry point navigates to a `MemoriesScreen` showing the groups with thumbnails, and a sane empty state.

**Risks/dependencies:** `ImageEmbeddingEntity` has no capture-date column (`indexedAt` only reflects indexing time) — resolved by joining against MediaStore at read time instead of a schema migration, to avoid touching the encrypted (SQLCipher) DB schema.

### Stories

**C1 — `MemoriesRepository`: group indexed photos by capture date**
- Outcome: pure logic that turns a list of indexed URIs into date-based memory groups.
- Files: new `app/src/main/java/com/rp/dedup/core/repository/MemoriesRepository.kt`.
- AC: takes `List<Uri>` + a reference "now", queries `DATE_TAKEN` per URI via `ContentResolver`, groups into "on this day" (same month/day, earlier year) and "N years ago"; skips URIs with missing `DATE_TAKEN` instead of crashing.
- Test: Robolectric test (matching `FileScannerRepositoryTest` conventions) seeding MediaStore rows with known `DATE_TAKEN` values and asserting correct grouping for a fixed clock.

**C2 — `MemoriesViewModel`**
- Outcome: exposes memory groups as UI state, following existing ViewModel conventions.
- Files: new `app/src/main/java/com/rp/dedup/core/viewmodels/MemoriesViewModel.kt`.
- AC: mirrors `ImageSearchViewModel`'s `Factory(context)` pattern; exposes `StateFlow<List<MemoryGroup>>` + loading/empty state.
- Test: ViewModel test using `MainDispatcherRule` + `mockk(relaxed = true)` repository, asserting state transitions (loading → populated / empty).

**C3 — `MemoriesScreen` + nav entry from dashboard**
- Outcome: user-visible screen, reachable from the dashboard.
- Files: new `app/src/main/java/com/rp/dedup/screens/memories/MemoriesScreen.kt`, `AppNavigation.kt` (new `Screen` object + route + `composable` block, gated with `PermissionGate`/`PermissionManager.IMAGE` per existing pattern), dashboard screen (new entry card).
- AC: shows grouped memories with Coil thumbnails; empty state when no memories exist; reachable via a dashboard tap.
- Test: unit test for any pure date-label formatting helper used by the screen; manual verification via the `run` skill / emulator screenshot (no existing Compose UI test harness confirmed in-repo to extend).

---

## Epic D — Storage forecast → proactive low-storage push

**Goal:** Turn the existing (already-computed, already-in-app-only) `StorageForecastingRepository` prediction into a proactive local notification, reusing Epic A's periodic worker rather than adding a second background job.

**Non-goals:** User-configurable thresholds; FCM/remote push (local notification only, via `AppNotificationManager`, same as Epic A).

**Acceptance criteria:**
- The periodic worker from Epic A also records a storage snapshot and checks the forecast each run.
- A "storage running low" notification fires at most once per day when `daysRemaining` crosses a threshold with acceptable confidence.
- The in-app dashboard card shows the same numbers that triggered the notification (no discrepancy).

**Risks/dependencies:** Depends on Epic A landing first (extends `ScanWorker` rather than duplicating a periodic job, per "no speculative abstraction" / reuse-existing-patterns rule).

### Stories

**D1 — Extend `ScanWorker` to record forecast snapshot + evaluate low-storage condition**
- Outcome: each periodic run also feeds `StorageForecastingRepository` and computes whether a warning should fire.
- Files: `ScanWorker.kt` (inject `StorageForecastingRepository`, call `recordSnapshotIfNecessary(freeBytes)`, read `forecast` once, evaluate `daysRemaining <= threshold && confidence` is acceptable).
- AC: pure boolean/condition computation, testable independent of notification side-effects.
- Test: unit test with a mocked repository covering boundary values (`daysRemaining == threshold`, low-confidence excluded, etc.).

**D2 — Fire deduped "storage running low" notification**
- Outcome: user gets warned before running out of space, without being spammed.
- Files: `ScanWorker.kt`, a small DataStore-backed "last low-storage notification date" flag (existing preferences pattern).
- AC: notification fires at most once per calendar day even if the worker runs more than once; tapping deep-links to the dashboard.
- Test: unit test asserting notification fired/not-fired based on the last-notified timestamp and the D1 condition.

**D3 — Align in-app forecast card with notification data**
- Outcome: the dashboard shows the same "N days remaining" figure used to trigger the push, closing the loop.
- Files: `app/src/main/java/com/rp/dedup/screens/dashboard/components/StorageHealthScoreCard.kt` (or a small addition near it), the dashboard ViewModel that feeds it (wire in `StorageForecastingRepository.forecast`).
- AC: card reflects live forecast values from the same repository/flow used by D1.
- Test: ViewModel/Compose state test verifying the card's view-state reflects forecast values from a mocked flow.

---

## Execution order

Epics may interleave, but within each epic stories are strictly sequential (each depends on the previous file/class existing). Suggested global order: **A1 → A2 → A3 → A4**, then **D1 → D2 → D3** (needs A's worker), interleaved with **B1 → B2 → B3 → B4** and **C1 → C2 → C3** (both independent of A/D).

## Progress

- [x] A1 — Implement real duplicate scan in `ScanWorker.doWork()`
- [x] A2 — Fire "duplicates found" notification from scan results
- [x] A3 — Schedule `ScanWorker` as periodic work at app start
- [x] A4 — Settings toggle to enable/disable background auto-scan
- [x] B1 — Wire AppFunctions build dependencies into `:app`
- [x] B2 — Implement `findLargeFiles` AppFunction
- [ ] B3 — Implement `findOldPhotos` / `getStorageSummary` AppFunctions
- [ ] B4 — KDoc refinement pass for agent consumption
- [ ] C1 — `MemoriesRepository`: group indexed photos by capture date
- [ ] C2 — `MemoriesViewModel`
- [ ] C3 — `MemoriesScreen` + nav entry from dashboard
- [ ] D1 — Extend `ScanWorker` to record forecast snapshot + evaluate low-storage condition
- [ ] D2 — Fire deduped "storage running low" notification
- [ ] D3 — Align in-app forecast card with notification data
