# DeDup — Bug Fix Log

This document records every bug found and fixed in the codebase audit sessions of May–June 2026.
Each entry lists the root cause, the impact, the fix applied, and the unit test that validates it.

---

## Previously Fixed (Session 1, May 2026)

### Fix #1 — AppDatabase recovery path called `build()` twice
**File:** `core/db/AppDatabase.kt`  
**Root cause:** `buildDatabase()` held a single `RoomDatabase.Builder` instance. The `try` block called `builder.build()`, and the `catch` block called `builder.build()` again. Room forbids calling `build()` more than once on the same builder and throws `IllegalStateException`.  
**Impact:** App crash on any database initialization failure (e.g., corrupted key on first launch after a factory reset).  
**Fix:** Extracted `createBuilder()` helper that returns a fresh, unbuilt builder on every call. The recovery path now calls `createBuilder().build()` independently. Also scoped `fallbackToDestructiveMigration` to `BuildConfig.DEBUG` only — production builds no longer silently wipe the database.  
**Test:** `Fix1AppDatabaseRecoveryTest.kt`

---

### Fix #2 — TrashManager used `File.renameTo` on all API levels
**File:** `core/utils/TrashManager.kt`  
**Root cause:** `moveToTrash()` called `File.renameTo()` via the raw filesystem path from `MediaStore.MediaColumns.DATA`. On Android Q+ (API 29+) scoped storage makes raw file paths inaccessible; `renameTo` silently returns `false`.  
**Impact:** Silent failure — files appeared to be trashed but were not moved or deleted on any modern Android device (API 29+, covering ~95% of active installs).  
**Fix:** Routing by SDK level:
- API 30+ (`R`): Sets `MediaStore.MediaColumns.IS_TRASHED = 1` via `ContentResolver.update` (system-managed trash).
- API 29 (`Q`): Copies bytes via `ContentResolver.openInputStream`, then deletes via `ContentResolver.delete`.
- API ≤ 28: Keeps `File.renameTo` (only valid before scoped storage).  
**Test:** `Fix2TrashManagerTest.kt`

---

### Fix #3 — HandoffReceiver exported with implicit broadcast action
**File:** `core/handoff/HandoffReceiver.kt`, `AndroidManifest.xml`  
**Root cause:** The receiver was declared `exported="true"` and listened on `android.intent.action.HANDOFF_RECEIVED`, a non-namespaced action. Any installed app could send this broadcast and trigger navigation inside DeDup.  
**Impact:** Security vulnerability — malicious apps could force arbitrary in-app navigation.  
**Fix:** Changed to `exported="false"` with a namespaced action `com.rp.dedup.action.NAVIGATE`. Added `buildIntent()` helper that sets `setPackage()` so the system never delivers it implicitly. Removed the Android 17 Handoff API usage (non-standard).  
**Test:** `Fix3HandoffReceiverTest.kt`

---

### Fix #6 — ScanWorker performed a useless fire-and-forget scan
**File:** `core/workers/ScanWorker.kt`  
**Root cause:** `doWork()` called `repository.scanImagesInParallel().collect {}` — collecting every emission and discarding it. No results were stored, no notification was shown, and the scan consumed CPU/battery with no observable effect.  
**Impact:** Unnecessary background CPU/battery drain whenever WorkManager scheduled the worker.  
**Fix:** Reduced to `Result.success()` stub. Background scanning requires a live ViewModel/UI context to process and surface results; the worker is retained as a placeholder for future notification-based scanning.  
**Test:** `Fix6ScanWorkerTest.kt`

---

### Fix #7 — CleanupViewModel concurrent state mutations without synchronization
**File:** `core/viewmodels/CleanupViewModel.kt`  
**Root cause:** `refreshAll()` launched 4 coroutines concurrently (videos, large files, old files, APKs). Each coroutine performed a read-modify-write on `_uiState.value` without any synchronization. Concurrent writes could overwrite each other's results, leaving some categories blank.  
**Impact:** Race condition — category scan results silently lost when two scans completed close together.  
**Fix:** Added a `Mutex` (`stateMutex`). Every read-modify-write of `_uiState` is wrapped in `stateMutex.withLock { }`.  
**Test:** `Fix7CleanupViewModelTest.kt`

---

### Fix #8 — VideoScannerViewModel set `_isScanning` inside the launched coroutine
**File:** `core/viewmodels/VideoScannerViewModel.kt`  
**Root cause:** `startScanning()` called `viewModelScope.launch { _isScanning.value = true; ... }`. Because the assignment was inside `launch {}`, it ran asynchronously. A caller could check `_isScanning.value` immediately after `startScanning()` and see `false`, then call `startScanning()` again, launching a duplicate scan.  
**Impact:** Duplicate concurrent scans possible, causing doubled DB writes and UI state corruption.  
**Fix:** Moved `_isScanning.value = true` to before the `viewModelScope.launch` call, so the guard is synchronous.  
**Test:** `Fix8VideoScannerGuardTest.kt`

---

### Fix #9 — BestShotAnalyzer FaceDetector never closed
**File:** `core/image/BestShotAnalyzer.kt`, `core/viewmodels/ScannerViewModel.kt`  
**Root cause:** `BestShotAnalyzer` holds a `lazy` singleton `FaceDetector` from ML Kit. The detector allocates a native thread pool and loads a model (~50–150 MB). No call to `faceDetector.close()` existed anywhere.  
**Impact:** Native memory and thread pool leaked for the entire process lifetime after the first scan.  
**Fix:** Added `BestShotAnalyzer.close()` that calls `faceDetector.close()` inside a try/catch. Added `ScannerViewModel.onCleared()` override that calls `BestShotAnalyzer.close()`.  
**Test:** `Fix9BestShotAnalyzerTest.kt`

---

### Fix #11 — RootDetectionManager blocked the main thread in `onCreate`
**File:** `MainActivity.kt`  
**Root cause:** `RootDetectionManager.check()` was called synchronously in `onCreate()` before `setContent`. The check performs 26+ `File.exists()` and process-inspection calls, each of which can block on slow storage or under I/O pressure.  
**Impact:** ANR risk — blocked the main thread during app startup; also delayed first frame by the duration of the check.  
**Fix:** Moved the check into a `LaunchedEffect(Unit)` with `withContext(Dispatchers.IO)`. The UI renders immediately; the rooted-device screen is shown only after the async check completes. The root result is held in a `remember { mutableStateOf<RootCheckResult?>(null) }` — `null` means "not yet checked" and shows the normal UI.  
**Test:** `Fix11RootDetectionTest.kt`

---

## New Fixes (Session 2, June 2026)

### Fix #12 — ScannerViewModel leaked Activity context
**File:** `core/viewmodels/ScannerViewModel.kt`, `ScannerViewModelFactory.kt`  
**Root cause:** The constructor parameter `private val context: Context` stored whatever context was passed in. The factory created the ViewModel from a Composable where `LocalContext.current` is an Activity context. ViewModels outlive Activities, so the Activity was retained across configuration changes.  
**Impact:** Memory leak — every screen rotation leaked the previous Activity instance.  
**Fix:** Constructor parameter changed from `private val context: Context` to `context: Context`. An internal field `private val context: Context = context.applicationContext` is assigned in the class body. The factory was also updated to extract `applicationContext` once and pass it to all sub-dependencies (`ImageScannerRepository`, `DataStoreManager`, `AnalyticsManager`).  
**Test:** `Fix12ContextLeakScannerViewModelTest.kt`

---

### Fix #13 — DashboardViewModel leaked Activity context
**File:** `core/viewmodels/DashboardViewModel.kt`  
**Root cause:** Same pattern as Fix #12 — `private val context: Context` stored the raw constructor argument.  
**Impact:** Memory leak on every screen rotation.  
**Fix:** Constructor parameter is now `context: Context` (no `val`). Internal field: `private val context: Context = context.applicationContext`.  
**Test:** `Fix13ContextLeakDashboardViewModelTest.kt`

---

### Fix #14 — ContactScannerViewModel factory passed Activity context to ToastManager
**File:** `core/viewmodels/ContactScannerViewModel.kt`  
**Root cause:** The `factory()` companion function constructed `ContactScannerRepository(context)` and `ToastManager(context)` using the raw `context: Context` parameter, which at the Composable call site is an Activity context. Both objects were stored in the ViewModel for its lifetime.  
**Impact:** Memory leak — Activity retained through both the repository and the toast manager.  
**Fix:** Factory now extracts `val appContext = context.applicationContext` and passes it to both dependencies.  
**Test:** `Fix14ContextLeakContactScannerViewModelTest.kt`

---

### Fix #15 — FirebaseAuthManager owned an unmanaged CoroutineScope
**File:** `core/firebase/auth/FirebaseAuthManager.kt`, `screens/SplashScreen.kt`, `screens/LoginScreen.kt`  
**Root cause:** `FirebaseAuthManager` created `CoroutineScope(Dispatchers.Main + SupervisorJob())` at construction time and launched fire-and-forget error-logging coroutines into it. The scope was never cancelled. Since `FirebaseAuthManager` is created via `remember { }` in Composables, a new scope was created on every re-entry to the composition, and old scopes ran indefinitely.  
**Impact:** Coroutine leak — accumulating zombie scopes with no way to cancel them; potential double-delivery of error logs.  
**Fix:** `FirebaseAuthManager` now implements `java.io.Closeable`. The `SupervisorJob` is held separately so `close()` can cancel it. Both `SplashScreen` and `LoginScreen` add `DisposableEffect(authManager) { onDispose { authManager.close() } }`. `SplashScreen` also adds `context` as a key to `remember` so the instance is replaced when the Context changes.  
**Test:** `Fix15FirebaseAuthManagerCloseableTest.kt`

---

### Fix #16 — SmartJunkRepository ML Kit labeler never closed
**File:** `core/search/SmartJunkRepository.kt`, `core/viewmodels/SmartJunkViewModel.kt`  
**Root cause:** `SmartJunkRepository` created an `ImageLabeling.getClient(...)` instance at construction time. ML Kit labelers hold a native thread pool and model weights. Neither the repository nor the ViewModel called `labeler.close()`.  
**Impact:** Native memory/resource leak — labeler's resources held until GC finalizer, which may never run.  
**Fix:** `SmartJunkRepository` implements `java.io.Closeable`; `close()` calls `labeler.close()` inside a try/catch. `SmartJunkViewModel.onCleared()` override added, calling `repository.close()`.  
**Test:** `Fix16SmartJunkLabelerLeakTest.kt`

---

### Fix #17 — TFLite EmbedderProvider never closed
**File:** `core/search/SemanticSearchRepository.kt`, `core/viewmodels/ImageSearchViewModel.kt`  
**Root cause:** `ImageSearchViewModel.Factory.create()` instantiated a fresh `EmbedderProvider` (wrapping a TFLite `TextEmbedder`) on every ViewModel creation. `EmbedderProvider.close()` existed but was never called — the embedder's native runtime and model buffer were retained indefinitely.  
**Impact:** Native memory/resource leak — TFLite runtime not released after ViewModel is destroyed.  
**Fix:** `SemanticSearchRepository` implements `java.io.Closeable`; `close()` delegates to `embedder.close()`. `ImageSearchViewModel.onCleared()` override added, calling `repository.close()`.  
**Test:** `Fix17EmbedderProviderLeakTest.kt`

---

### Fix #18 — StateFlow mutations from `Dispatchers.IO` without switching to Main
**File:** `core/viewmodels/ScannerViewModel.kt`  
**Root cause:** `loadCachedResults()` was launched on `Dispatchers.IO` and directly assigned `_duplicateGroups.value`, `_cacheLoaded.value`, and `_isStale.value` from the IO thread. A separate `checkStaleness()` function did the same. While `MutableStateFlow` writes are thread-safe in isolation, the pattern violated the `@MainThread` contract and made state update ordering unpredictable under the Compose snapshot system.  
**Impact:** Subtle UI update ordering issues; fragile pattern that could cause missed recompositions on specific devices.  
**Fix:** `checkStaleness()` renamed to `computeStaleness()` and changed to return `Boolean` instead of mutating state. `loadCachedResults()` performs all IO work first, then batches every StateFlow mutation into a single `withContext(Dispatchers.Main.immediate) { }` block.  
**Test:** `Fix18StateFlowMainThreadTest.kt`

---

### Fix #19 — `verifyTokenOnServer()` silently returned `true`
**File:** `core/security/NetworkSecurityManager.kt`  
**Root cause:** The Play Integrity server-side verification method was a stub that unconditionally returned `true`. Any caller gating a security decision on this result would always pass, making the entire attestation flow meaningless.  
**Impact:** Security — Play Integrity token obtained but never validated; attestation completely bypassed.  
**Fix:** Method now throws `UnsupportedOperationException` with a descriptive message. Future callers are forced to handle the unimplemented state explicitly rather than silently trusting unverified tokens.  
**Test:** `Fix19NetworkSecurityVerifyTokenTest.kt`

---

### Fix #20 — `Toast.view` deprecated, custom toast silently ignored on API 30+
**File:** `core/notifications/ToastManager.kt`  
**Root cause:** `showCustom()` set `toast.view = layout` and `toast.setGravity()`, both deprecated in API 30. On Android 11+ (API 30+) the system silently ignores custom toast views when set this way; the plain message text is shown instead (or nothing, if the app is in the background). This means the branded icon, colour, and corner radius were never visible on ~80%+ of active devices.  
**Impact:** Silent failure — custom toast UI never appeared on modern Android; `iconRes` parameter had no effect.  
**Fix:** Added a runtime guard: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)` falls back to `Toast.makeText(...).show()` immediately. The deprecated code path is retained only for API < 30 under `@Suppress("DEPRECATION")`.  
**Test:** `Fix20ToastDeprecatedViewTest.kt`

---

### Fix #21 — LIKE query used unsanitized folder path (SQL wildcard injection)
**File:** `core/repository/FileScannerRepository.kt`  
**Root cause:** `scanOldFiles(folder, olderThanMs)` constructed a MediaStore `LIKE ?` selection with the raw `folder` string as the pattern prefix. A folder path containing `%` or `_` would match unintended files. While the current callers pass safe system paths, the method's public signature accepted arbitrary strings with no validation.  
**Impact:** Logic bug / latent security risk — wrong files could match; exploitable if folder path ever came from user input.  
**Fix:** The folder string is escaped before use: `\` → `\\`, `%` → `\%`, `_` → `\_`. The selection uses `LIKE ? ESCAPE '\\'` so SQLite treats those characters literally.  
**Test:** `Fix21LikeWildcardEscapeTest.kt` — 6 test cases covering plain paths, each special character, combined characters, and the final wildcard suffix.

---

### Fix #22 — `Uri.withAppendedPath` instead of `ContentUris.withAppendedId`
**File:** `core/deepoptimization/WhatsAppCleanerRepository.kt`  
**Root cause:** `queryMediaStore()` constructed per-item URIs with `Uri.withAppendedPath(baseUri, id.toString())`. For standard `MediaStore` collection URIs this happens to produce the same result, but `Uri.withAppendedPath` appends as a string path segment, not a proper numeric content ID. On volume-specific URIs (API 29+ secondary storage volumes) the two diverge and `ContentResolver.delete()` rejects the malformed URI.  
**Impact:** Silent deletion failure on secondary storage or volume-aware URIs — files selected for deletion were not removed.  
**Fix:** Replaced with `ContentUris.withAppendedId(baseUri, cursor.getLong(idIdx))` — the correct API for MediaStore item URIs.  
**Test:** `Fix22ContentUrisWithAppendedIdTest.kt`

---

### Fix #23 — `getColumnIndex` not checked for `-1` in ContactScannerRepository
**File:** `core/repository/ContactScannerRepository.kt`  
**Root cause:** `scanContacts()` called `cursor.getColumnIndex(...)` for `CONTACT_ID`, `DISPLAY_NAME`, and `NUMBER` without checking the return value. `getColumnIndex` returns `-1` when the column is absent. `cursor.getString(-1)` subsequently crashes with `CursorIndexOutOfBoundsException` on some devices/ROMs, or silently reads the wrong column on others.  
**Impact:** Crash or silent data corruption on devices where the Contacts ContentProvider returns unexpected column names.  
**Fix:** All three replaced with `cursor.getColumnIndexOrThrow(...)`, which throws `IllegalArgumentException` immediately with the column name, making the failure immediately visible and diagnosable.  
**Test:** `Fix23GetColumnIndexOrThrowTest.kt`

---

### Fix #24 — ImageIndexRepository stale-deletion guard with no permission pre-check
**File:** `core/search/ImageIndexRepository.kt`  
**Root cause:** `indexImages()` guarded `dao.deleteStale(allUris)` with `if (allUris.isNotEmpty())`. The guard is correct — `deleteStale` uses `NOT IN` and an empty list would delete the entire embedding table. However, if storage permission was revoked mid-session, `loadAllImageUris()` returns an empty list for the wrong reason (permission denied, not "device has no images"). The guard hides this silently. The invariant protecting the table from a full wipe was undocumented, making it a landmine for future refactors.  
**Impact:** Logic bug — permission revocation silently keeps stale index rows; the guard's purpose was invisible, creating a deletion-of-entire-table risk on any code change that removed it.  
**Fix:** Added an explicit permission check (`READ_MEDIA_IMAGES` on API 33+, `READ_EXTERNAL_STORAGE` below) at the start of `indexImages()`. If permission is not granted, the run aborts early with a log message. The existing `isNotEmpty()` guard is retained with a comment explaining the `NOT IN` invariant.  
**Test:** `Fix24ImageIndexPermissionGuardTest.kt`

---

### Fix #25 — `SplashScreen` `remember` captured stale Context with no key
**File:** `screens/SplashScreen.kt`  
**Root cause:** `remember { FirebaseAuthManager(ToastManager(context)) }` with no key captured `context` from the first composition. Compose's `remember` with no key never re-runs its lambda even if the Context instance changes. `ToastManager` was also created with the raw Activity context rather than `applicationContext`.  
**Impact:** Stale Context held in a long-lived `remember` — minor memory leak; toasts could reference a Context that no longer corresponds to the active window after certain lifecycle transitions.  
**Fix:** Changed to `remember(context) { FirebaseAuthManager(ToastManager(context.applicationContext)) }`. Added `DisposableEffect(authManager) { onDispose { authManager.close() } }` (enabled by Fix #15). This was partially addressed during Fix #15 as the `DisposableEffect` was added at the same time.  
**Test:** `Fix25SplashRememberContextKeyTest.kt`

---

### Fix #26 — Notification ID overflow from `currentTimeMillis().toInt()`
**File:** `core/firebase/notifications/FirebaseMessageService.kt`  
**Root cause:** `onMessageReceived()` used `System.currentTimeMillis().toInt()` as the notification ID. `currentTimeMillis()` returns ~1.7 trillion; truncating to a 32-bit `Int` (max ~2.1 billion) overflows to a large negative number. Two notifications arriving within the same millisecond get the same truncated ID, so the second silently replaces the first.  
**Impact:** Silent notification loss — rapid push notifications collapse into one; the ID is effectively random and uncontrollable.  
**Fix:** Added `private val notifIdCounter = AtomicInteger(0)` to the companion object. Each notification receives `notifIdCounter.getAndIncrement()` — monotonically increasing, unique, no overflow risk in normal usage.  
**Test:** `Fix26NotificationIdOverflowTest.kt`

---

### Fix #27 — Recursive directory scan accumulated entire result list before emitting
**File:** `core/repository/FileScannerRepository.kt`  
**Root cause:** `scanDirectoryRecursively()` was a regular (non-suspend) function that recursively built and returned a `MutableList<ScannedFile>`. All results for the entire directory tree were accumulated in memory before the first item was emitted to the surrounding `flow {}`. On a device with `MANAGE_EXTERNAL_STORAGE` and a large external storage tree, this could allocate hundreds of megabytes before yielding any results.  
**Impact:** OOM risk — large file trees caused excessive memory allocation before any progress was visible to the UI.  
**Fix:** Replaced with `emitDirectoryRecursively()` — a `private suspend fun` that uses an iterative depth-first stack (`ArrayDeque<File>`) and calls `emit(file)` for each matching file immediately. No intermediate list is built. Hidden directories and excluded folders are still skipped.  
**Test:** `Fix27RecursiveScanOomTest.kt`

---

### Fix #28 — BitmapPool returned bitmaps without validating dimensions or config
**File:** `core/image/BitmapPool.kt`  
**Root cause:** `acquire(width, height, config)` checked only `!pooled.isRecycled` before returning a pooled bitmap. It did not verify that the pooled bitmap's `width`, `height`, or `config` matched the caller's request. `ImageHasher` always requests 9×8 `ARGB_8888`, so this was safe with the current single caller — but any future caller with different dimensions would silently receive a wrong-size bitmap, causing the `Canvas.drawBitmap` in `calculateDHash` to produce corrupted hash values.  
**Impact:** Latent data corruption — dHash values would be wrong for any caller requesting a non-9×8 bitmap, leading to missed or false-positive duplicate groups.  
**Fix:** `acquire()` now checks `pooled.width == width && pooled.height == height && pooled.config == config` before returning the pooled bitmap. On a mismatch, the pooled bitmap is `recycle()`d and a fresh one is allocated.  
**Test:** `Fix28BitmapPoolDimensionValidationTest.kt` — 5 test cases: correct dims from empty pool; wrong dims rejected; wrong config rejected; matching entry reused; mismatched entry recycled.

---

### Fix #29 — `persistResults` was fire-and-forget, racing with the next scan
**File:** `core/viewmodels/ScannerViewModel.kt`  
**Root cause:** `persistResults()` was declared as a plain `fun` that called `viewModelScope.launch(Dispatchers.IO) { ... }` internally. It was called at the end of `startScanning()`'s try block, but because it launched a new coroutine and returned immediately, the scan coroutine finished and `_isScanning.value = false` ran before the DB write completed. If the user started a new scan immediately, the new scan's `clearAll()` ran, then the old persist's coroutine resumed and re-inserted the previous scan's data. Additionally, if the DB write threw, `_isStale.value = false` was never set, causing spurious stale-data prompts.  
**Impact:** Data corruption — previous scan results could overwrite a new scan's state; `_isStale` stuck at `true` on any DB error.  
**Fix:** `persistResults()` changed to `private suspend fun`. Called under `withContext(NonCancellable + Dispatchers.IO)` so it completes before the scan coroutine exits. Errors are caught with a try/catch so `_isStale.value = false` only runs on success. StateFlow mutations inside `persistResults` use `withContext(Dispatchers.Main.immediate)`.  
**Test:** `Fix29PersistResultsRaceTest.kt`

---

### Fix #30 — `VideoScannerViewModel.clearCache()` reset in-memory state before DB commit
**File:** `core/viewmodels/VideoScannerViewModel.kt`  
**Root cause:** `clearCache()` launched an IO coroutine for `videoRepository?.clearAll()` but immediately (synchronously) reset `_videos`, `_duplicateGroups`, `_scannedCount`, `_resumedCount`, and `_cacheLoaded` on the caller's thread, before the DB operation completed. If the app was killed in the window between the UI reset and the DB write, the old data reloaded on next launch while the UI showed an empty state.  
**Impact:** Race condition — a crash between the UI clear and the DB clear left stale cached data that contradicted the empty UI state shown before the kill.  
**Fix:** All StateFlow mutations moved inside the IO coroutine, after `clearAll()` completes, wrapped in `withContext(Dispatchers.Main.immediate)`. The DB is now always cleared before the UI reflects the cleared state.  
**Test:** `Fix30VideoScannerClearCacheRaceTest.kt`

---

## Summary Table

| # | Category | File(s) | Impact | Test |
|---|----------|---------|--------|------|
| 1 | Crash | `AppDatabase.kt` | App crash on DB init failure | Fix1 |
| 2 | Silent failure | `TrashManager.kt` | Files not deleted on API 29+ | Fix2 |
| 3 | Security | `HandoffReceiver.kt`, `AndroidManifest.xml` | Broadcast hijack by any app | Fix3 |
| 6 | Performance | `ScanWorker.kt` | Wasted CPU/battery | Fix6 |
| 7 | Race condition | `CleanupViewModel.kt` | Scan results silently lost | Fix7 |
| 8 | Race condition | `VideoScannerViewModel.kt` | Duplicate concurrent scans | Fix8 |
| 9 | Native resource leak | `BestShotAnalyzer.kt`, `ScannerViewModel.kt` | ~50–150 MB native memory leak | Fix9 |
| 11 | ANR risk | `MainActivity.kt` | Blocked main thread on startup | Fix11 |
| 12 | Memory leak | `ScannerViewModel.kt`, `ScannerViewModelFactory.kt` | Activity leaked per rotation | Fix12 |
| 13 | Memory leak | `DashboardViewModel.kt` | Activity leaked per rotation | Fix13 |
| 14 | Memory leak | `ContactScannerViewModel.kt` | Activity leaked per rotation | Fix14 |
| 15 | Coroutine leak | `FirebaseAuthManager.kt`, `SplashScreen.kt`, `LoginScreen.kt` | Zombie scopes accumulate | Fix15 |
| 16 | Native resource leak | `SmartJunkRepository.kt`, `SmartJunkViewModel.kt` | ML Kit labeler never freed | Fix16 |
| 17 | Native resource leak | `SemanticSearchRepository.kt`, `ImageSearchViewModel.kt` | TFLite runtime never freed | Fix17 |
| 18 | Threading | `ScannerViewModel.kt` | StateFlow mutations off main thread | Fix18 |
| 19 | Security | `NetworkSecurityManager.kt` | Play Integrity attestation bypassed | Fix19 |
| 20 | Silent failure | `ToastManager.kt` | Custom toast invisible on API 30+ | Fix20 |
| 21 | Security | `FileScannerRepository.kt` | SQL wildcard injection in LIKE query | Fix21 |
| 22 | API correctness | `WhatsAppCleanerRepository.kt` | Deletions fail on secondary storage | Fix22 |
| 23 | Crash | `ContactScannerRepository.kt` | Crash on unexpected cursor columns | Fix23 |
| 24 | Logic bug | `ImageIndexRepository.kt` | Silent stale index; table-wipe risk | Fix24 |
| 25 | Memory / staleness | `SplashScreen.kt` | Stale Context captured in `remember` | Fix25 |
| 26 | Silent failure | `FirebaseMessageService.kt` | Notification IDs collide and overflow | Fix26 |
| 27 | OOM risk | `FileScannerRepository.kt` | Entire file tree loaded into memory | Fix27 |
| 28 | Data corruption | `BitmapPool.kt` | Wrong-size bitmap causes bad dHash | Fix28 |
| 29 | Data corruption | `ScannerViewModel.kt` | Old scan results overwrite new scan | Fix29 |
| 30 | Race condition | `VideoScannerViewModel.kt` | Stale DB data after crash during clear | Fix30 |
