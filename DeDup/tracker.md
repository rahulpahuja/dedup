# DeDup — Internal Tracker

Ticket IDs: `DEDUP-<n>`, permanent once assigned (never renumbered/reused).
Status legend: `[ ]` open · `[~]` in progress · `[x]` done.

Reference a ticket in its commit subject, e.g. `[DEDUP-1] Guard NativeLib load failure`,
same convention `PLAN.md` uses with `[epic-X/story-N]`.

---

## Open / In Progress

### DEDUP-1 — Guard against `NativeLib` load failure poisoning the class
**Status:** [~] in progress (uncommitted)
**File:** `app/src/main/java/com/rp/dedup/core/security/NativeLib.kt`
**Problem:** `System.loadLibrary("dedup_native")` ran directly in the companion object's
`init` block. A load failure on any device throws out of the class initializer, which
permanently poisons the class — every later `NativeLib()` access then throws
`NoClassDefFoundError` instead of the original error, bypassing callers that only
caught `UnsatisfiedLinkError`.
**Fix:** Wrap the load in a `try/catch (Throwable)`, expose `NativeLib.isAvailable: Boolean`,
report failures to `FirebaseCrashlytics`.
**Depends on:** none — blocks DEDUP-2, DEDUP-3.

### DEDUP-2 — Skip Facebook SDK init when native lib is unavailable
**Status:** [~] in progress (uncommitted)
**File:** `app/src/main/java/com/rp/dedup/core/app/DeDupApp.kt`
**Problem:** `DeDupApp.onCreate()` called `NativeLib().getFacebookClientToken()` unconditionally;
relied only on catching `UnsatisfiedLinkError` around it.
**Fix:** Gate the whole Facebook SDK init block behind `NativeLib.isAvailable`.
**Depends on:** DEDUP-1.

### DEDUP-3 — Fail gracefully in Google sign-in when native lib is unavailable
**Status:** [~] in progress (uncommitted)
**File:** `app/src/main/java/com/rp/dedup/core/firebase/auth/FirebaseAuthManager.kt`
**Problem:** Google sign-in read the server client ID via `NativeLib().getGoogleWebClientId()`
with no guard — a load failure would crash instead of failing gracefully.
**Fix:** Check `NativeLib.isAvailable` first; log + show the user a toast
("Google sign-in isn't available on this device right now.") and return `null` instead of crashing.
**Depends on:** DEDUP-1.

### DEDUP-4 — Default DOCUMENT search to `application/*` mime filter
**Status:** [~] in progress (uncommitted)
**File:** `feature/voice-storage/src/main/java/com/rp/dedup/feature/voicestorage/data/repository/LocalStorageRepository.kt`
**Problem:** Queries for `MediaType.DOCUMENT` were silently dropped whenever the caller
didn't supply a `mimeTypeFilter`, since querying `Files` unfiltered would return
images/videos/audio already covered by the other branches.
**Fix:** When `DOCUMENT` is requested with no `mimeTypeFilter`, default to the
`application/%` wildcard (covers PDFs, ZIPs, APKs, Office docs) instead of skipping the query.
**Depends on:** none — blocks DEDUP-5.

### DEDUP-5 — Include DOCUMENT in "largest files" voice query
**Status:** [~] in progress (uncommitted)
**File:** `feature/voice-storage/src/main/java/com/rp/dedup/feature/voicestorage/presentation/ChatViewModel.kt`
**Problem:** The "largest files" voice/chat query only searched `IMAGE` + `VIDEO`, so a
user asking "show my largest files" would never see large documents.
**Fix:** Add `MediaType.DOCUMENT` to the `mediaTypes` set for the largest-files query path.
**Depends on:** DEDUP-4 (documents only surface correctly once DEDUP-4's default filter lands).

---

## Epic: Migrate to Clean Architecture + Hilt DI

**Context:** Raised as a query — the app already follows MVVM (ViewModel + Repository), but
has no domain layer (business logic lives inside ViewModels/Repositories directly) and no DI
framework. Every ViewModel wires its own dependencies via a hand-rolled
`class Factory(private val context: Context) : ViewModelProvider.Factory` companion object that
manually calls `AppDatabase.getDatabase(context)` and `new`s up repositories/managers inline
(see e.g. `TrashViewModel.Factory`, repeated with variations across all 21 ViewModels in
`app/src/main/java/com/rp/dedup/core/viewmodels/`). `PLAN.md` (Epic B risks) independently
flagged the same gap: *"App has no Hilt/DI framework — must use the skill's
framework-agnostic service-locator pattern."*

**Target architecture (per module, `app` and `feature:voice-storage`):**
```
data/          — DAOs, ContentResolver/MediaStore access, repository IMPLEMENTATIONS
domain/
  model/       — plain Kotlin domain models (not Room entities / DTOs)
  repository/  — repository INTERFACES (domain owns the contract, data implements it)
  usecase/     — one class per business operation, orchestration logic pulled out of ViewModels
presentation/  — ViewModels (`@HiltViewModel`), Compose screens (unchanged)
```
`feature:voice-storage` already has this package split (`data/`, `domain/`, `presentation/`);
`app` currently has a flat `core/repository/` + `core/viewmodels/` + `screens/` layout and needs
the domain layer introduced.

**DI framework:** Hilt (`com.google.dagger:hilt-android`), not Koin/manual DI — reuses the KSP
plugin already configured for Room/AppFunctions, and `androidx.hilt:hilt-work` gives first-class
`@HiltWorker` support for `ScanWorker`/`ImageIndexWorker`, which a manual/Koin approach would
still need extra wiring for.

**Non-goals:** No behavior change to any screen/feature during migration — this is a structural
refactor only, verified by existing tests continuing to pass. No new features bundled in.

**Risks:** Large surface area (21 ViewModels, 18 repositories, 2 modules, several WorkManager
workers, the `DeDupAppFunctionService` service-locator from Epic B). Must land incrementally
(one story = one buildable, testable commit) rather than a single sweeping change, per repo's
existing epic/story convention in `PLAN.md`.

### DEDUP-6 — Define Clean Architecture package convention (no code yet)
**Status:** [ ] open
**Scope:** Documentation/decision-only story. Write the target package layout above into
`ARCHITECTURE.md` (new file) as the source of truth for DEDUP-7 through DEDUP-14: what belongs
in `domain/model` vs. reusing Room entities, naming convention for use cases
(`<Verb><Noun>UseCase`, single public `suspend operator fun invoke(...)`), rule that
`presentation/` may only depend on `domain/`, never on `data/` directly.
**Depends on:** none — blocks everything below.

### DEDUP-7 — Add Hilt to the build: base wiring, no migration yet
**Status:** [ ] open
**Files:** `gradle/libs.versions.toml` (add `hilt`, `hilt-work`, `hilt-compiler` versions/libs),
`build.gradle.kts` (root — add Hilt Gradle plugin), `app/build.gradle.kts` and
`feature/voice-storage/build.gradle.kts` (apply Hilt plugin + KSP compiler dep),
`app/src/main/java/com/rp/dedup/core/app/DeDupApp.kt` (add `@HiltAndroidApp`),
`app/src/main/java/com/rp/dedup/MainActivity.kt` (add `@AndroidEntryPoint`).
**AC:** `./gradlew :app:assembleDevDebug` succeeds with Hilt wired in and no existing code
touched/migrated yet — this story only proves the toolchain, same pattern as Epic B/Story-1
(`B1 — Wire AppFunctions build dependencies`) in `PLAN.md`.
**Depends on:** DEDUP-6.

### DEDUP-8 — `app` module: extract repository interfaces into `domain/repository`
**Status:** [ ] open
**Files:** all of `app/src/main/java/com/rp/dedup/core/repository/` — move existing
`IContactScannerRepository`, `IFileScannerRepository`, `IImageScannerRepository`,
`IScanHistoryRepository`, `IScannedImageRepository`, `IScannedVideoRepository`,
`ITrashRepository`, `IVideoScannerRepository` to `domain/repository/`; **write new interfaces**
for the 3 repositories that don't have one yet — `MemoriesRepository`,
`SemanticDuplicateRepository`, `StorageForecastingRepository`. Implementations move to
`data/repository/` and implement the domain interface.
**AC:** every repository is consumed by its interface, not its concrete class, everywhere
(ViewModels, Workers, AppFunctions service). No behavior change — pure move + interface
extraction.
**Test:** existing repository unit tests still pass unmodified (only import paths change).
**Depends on:** DEDUP-6.

### DEDUP-9 — Bind repositories via Hilt modules
**Status:** [ ] open
**Files:** new `app/src/main/java/com/rp/dedup/core/di/RepositoryModule.kt`
(`@Module @InstallIn(SingletonComponent::class)`, `@Binds` each interface → impl, or `@Provides`
where the impl needs `AppDatabase`/DAO construction), new `DatabaseModule.kt` providing
`AppDatabase` + individual DAOs as singletons (replacing scattered
`AppDatabase.getDatabase(context)` calls across every `Factory`).
**AC:** `AppDatabase.getDatabase(context)` is called from exactly one place (the Hilt module);
all repositories are injectable.
**Depends on:** DEDUP-7, DEDUP-8.

### DEDUP-10 — Introduce use-case layer for ViewModel business logic
**Status:** [ ] open
**Files:** new `app/src/main/java/com/rp/dedup/core/domain/usecase/` — start with the
highest-value extractions: `FindDuplicateGroupsUseCase` (wraps
`SemanticDuplicateRepository.findDuplicateGroups()`, used by both `ScanWorker` and
`SemanticScannerViewModel` — currently duplicated call sites), `EvaluateLowStorageUseCase`
(the D1 forecast/threshold condition currently inline in `ScanWorker`), `GroupMemoriesUseCase`
(wraps `MemoriesRepository` grouping logic used by `MemoriesViewModel`).
**AC:** each use case is a single-responsibility class with one public
`suspend operator fun invoke(...)`, unit-testable independent of Android framework classes;
ViewModels/Workers call the use case instead of the repository directly for these three flows.
**Test:** unit tests for each use case, replacing/supplementing the equivalent logic
previously tested at the ViewModel/Worker level.
**Depends on:** DEDUP-8, DEDUP-9.

### DEDUP-11 — Migrate ViewModels off manual `Factory` to `@HiltViewModel`
**Status:** [ ] open
**Scope:** all 21 ViewModels in `app/src/main/java/com/rp/dedup/core/viewmodels/`
(`BigFileMapViewModel`, `CleanupViewModel`, `ContactScannerViewModel`, `DashboardViewModel`,
`EmptyFolderViewModel`, `FileBrowserViewModel`, `FileScannerViewModel`,
`ImageCompressionViewModel`, `ImageSearchViewModel`, `MemoriesViewModel`,
`ScanHistoryViewModel`, `ScannerViewModel`, `SemanticScannerViewModel`, `SettingsViewModel`,
`SmartJunkViewModel`, `SocialMediaCleanerViewModel`, `StorageHealthViewModel`,
`ThemeViewModel`, `TrashViewModel`, `UserProfileViewModel`, `VideoScannerViewModel`,
`WhatsAppCleanerViewModel`) plus every Compose call site currently using
`viewModel(factory = SomeViewModel.Factory(context))`, switched to `hiltViewModel()`.
**AC:** each ViewModel gets `@HiltViewModel` + `@Inject constructor(...)`, its `Factory`
companion object is deleted; Compose screens call `hiltViewModel()` with no manual factory arg.
Migrate incrementally, one ViewModel (or tightly-coupled cluster, e.g. the 3 Epic-D/forecast
ViewModels together) per commit — do not attempt all 21 in one change.
**Test:** existing ViewModel tests continue to pass with constructor injection swapped in for
manual instantiation (e.g. `mockk` fakes passed directly to the constructor instead of via a
fake `Factory`).
**Depends on:** DEDUP-9, DEDUP-10 (for the 3 ViewModels touched by DEDUP-10's use cases).

### DEDUP-12 — Migrate `ScanWorker`/`ImageIndexWorker` to `HiltWorker`
**Status:** [ ] open
**Files:** `app/src/main/java/com/rp/dedup/core/workers/ScanWorker.kt`,
`ImageIndexWorker.kt` (or wherever it lives under `core/workers/`/`core/work/`),
`DeDupApp.kt` (implement `Configuration.Provider`, supply `HiltWorkerFactory` via
`@Inject lateinit var workerFactory: HiltWorkerFactory`), `AndroidManifest.xml` (remove the
default `WorkManagerInitializer` provider per Hilt's on-demand-initialization docs).
**AC:** both workers use `@HiltWorker` + `@AssistedInject constructor(@Assisted context,
@Assisted params, ...deps)`; manual dependency construction inside `doWork()` is gone.
**Test:** existing `WorkManagerTestInitHelper`-based tests (per `PLAN.md` A3) updated to inject
the Hilt worker factory instead of the default one.
**Depends on:** DEDUP-9, DEDUP-10 (workers consume `FindDuplicateGroupsUseCase` /
`EvaluateLowStorageUseCase`).

### DEDUP-13 — Migrate `feature:voice-storage` to Clean Architecture + Hilt
**Status:** [ ] open
**Files:** new `domain/repository/IVoiceStorageRepository.kt` (interface extracted from
`LocalStorageRepository`, which moves under `data/repository/` and implements it); new
`domain/usecase/SearchStorageUseCase.kt` wrapping `LocalStorageRepository.queryFiles()` +
`VoiceQueryParser`; `ChatViewModel.kt` → `@HiltViewModel` + `@Inject constructor`, drop its
existing `Factory(context)`; new `feature/voice-storage/.../di/VoiceStorageModule.kt`
(`@Module @InstallIn(SingletonComponent::class)`) binding the repository — needs
`@InstallIn` scoped so `:app` can consume it across the module boundary (feature module depends
on `hilt-android` too, not just the annotations).
**AC:** `ChatViewModel` no longer directly `new`s `LocalStorageRepository(context)`; DEDUP-4/
DEDUP-5 fixes (already in this repository, currently uncommitted) carry through unaffected —
this story only changes how the repository is constructed and wired, not its query logic.
**Depends on:** DEDUP-6, DEDUP-7.

### DEDUP-14 — Migrate `DeDupAppFunctionService` off the service-locator pattern
**Status:** [ ] open
**File:** `app/src/main/java/com/rp/dedup/core/appfunctions/DeDupAppFunctionService.kt`
(from `PLAN.md` Epic B — currently reaches `LocalStorageRepository` via
`applicationContext`-based service-locator because, at the time B was built, "app has no
Hilt/DI framework").
**Problem:** `AppFunctionService` is a system-instantiated Android service; Hilt can't
constructor-inject it the way it does ViewModels/Workers.
**Fix:** Use Hilt's `@EntryPoint` pattern — define an `@EntryPoint @InstallIn(SingletonComponent::class)
interface VoiceStorageEntryPoint { fun searchStorageUseCase(): SearchStorageUseCase }`, resolve it
via `EntryPointAccessors.fromApplication(applicationContext, VoiceStorageEntryPoint::class.java)`
inside the service instead of manually constructing `LocalStorageRepository`.
**Depends on:** DEDUP-13.

### DEDUP-15 — Delete dead manual-DI code + full regression pass
**Status:** [ ] open
**Scope:** cleanup + verification story, last in the epic. Delete every now-unused
`Factory(context)` companion object confirmed dead by DEDUP-11/12/13; grep the codebase for any
remaining direct `AppDatabase.getDatabase(` / `SomeRepository(context)` construction outside
Hilt modules and fix stragglers; run the full unit test suite + `./gradlew build`; update
`PLAN.md`/`tracker.md` cross-references and `ARCHITECTURE.md` (from DEDUP-6) if the design
shifted during implementation.
**AC:** zero remaining manual `ViewModelProvider.Factory` implementations in the codebase
(`grep -rl "ViewModelProvider.Factory"` returns empty); build + full test suite green.
**Depends on:** DEDUP-11, DEDUP-12, DEDUP-13, DEDUP-14.

---

## Progress

- [~] DEDUP-1 — Guard against `NativeLib` load failure poisoning the class
- [~] DEDUP-2 — Skip Facebook SDK init when native lib is unavailable
- [~] DEDUP-3 — Fail gracefully in Google sign-in when native lib is unavailable
- [~] DEDUP-4 — Default DOCUMENT search to `application/*` mime filter
- [~] DEDUP-5 — Include DOCUMENT in "largest files" voice query
- [ ] DEDUP-6 — Define Clean Architecture package convention (`ARCHITECTURE.md`)
- [ ] DEDUP-7 — Add Hilt to the build: base wiring, no migration yet
- [ ] DEDUP-8 — `app` module: extract repository interfaces into `domain/repository`
- [ ] DEDUP-9 — Bind repositories via Hilt modules
- [ ] DEDUP-10 — Introduce use-case layer for ViewModel business logic
- [ ] DEDUP-11 — Migrate ViewModels off manual `Factory` to `@HiltViewModel`
- [ ] DEDUP-12 — Migrate `ScanWorker`/`ImageIndexWorker` to `HiltWorker`
- [ ] DEDUP-13 — Migrate `feature:voice-storage` to Clean Architecture + Hilt
- [ ] DEDUP-14 — Migrate `DeDupAppFunctionService` off the service-locator pattern
- [ ] DEDUP-15 — Delete dead manual-DI code + full regression pass
