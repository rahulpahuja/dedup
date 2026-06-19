# DeDup Screen-by-Screen Automation Test Cases

This document details the test scenarios, assertions, and automation strategies for each screen of the DeDup Android application. These tests can be implemented using **Jetpack Compose Testing APIs** (for UI and state checks) and **UI Automator** (for system-level interactions like permission dialogs and notifications).

---

## 1. Splash Screen (`SplashScreen.kt`)
**Objective**: Validate app initialization, root detection, and initial routing.

* **Test Case 1.1: Standard App Launch & Auto-Login Handoff**
  * *Steps*:
    1. Clear app data and launch the app.
    2. Wait for the splash screen animation to complete.
  * *Assertions*:
    * Verify that the database initializes successfully.
    * Verify that if the user is already authenticated (or guest mode is active), the app navigates directly to `DashboardScreen`.
    * Verify that if the user is unauthenticated, the app navigates to `LoginScreen`.

* **Test Case 1.2: Rooted Device Guard Trigger**
  * *Steps*:
    1. Emulate a rooted environment (e.g., mock `RootDetectionManager.check()` to return `RootCheckResult.Rooted`).
    2. Launch the app.
  * *Assertions*:
    * Verify that a warning banner/dialog displays indicating the device is rooted.
    * Verify that the user can either acknowledge the warning to proceed or exit the app safely.

---

## 2. Login Screen (`LoginScreen.kt`)
**Objective**: Verify authentication flows and Guest mode fallback.

* **Test Case 2.1: Google Sign-In Integration**
  * *Steps*:
    1. Click the Google Sign-In button.
    2. Click on the mocked account selection in the system Google Play Services dialog (using UI Automator).
  * *Assertions*:
    * Verify that `FirebaseAuthManager` receives the token and authenticates.
    * Verify that the UI transitions to the `DashboardScreen` upon success.

* **Test Case 2.2: Guest Mode Bypass**
  * *Steps*:
    1. Click the "Continue as Guest" button.
  * *Assertions*:
    * Verify that the user is marked as a Guest in the database/data store.
    * Verify that the app transitions to `DashboardScreen` without requiring network authentication.

---

## 3. Dashboard Screen (`DashboardScreen.kt`)
**Objective**: Validate overall storage summaries, counts, and screen navigation.

* **Test Case 3.1: Storage Stats Display**
  * *Steps*:
    1. Populate the mock media database/MediaStore with predefined files (e.g., 5GB Used, 10GB Free).
    2. Navigate to `DashboardScreen`.
  * *Assertions*:
    * Verify that the Circular Storage Gauge accurately displays "Used: 5.0 GB" and "Free: 10.0 GB".
    * Verify that category cards show exact counts: Images (X), Videos (Y), Documents (Z).

* **Test Case 3.2: Screen Navigation Hub**
  * *Steps*:
    1. Click on individual category cards or quick tools (e.g., "Deep Clean", "Big Files").
  * *Assertions*:
    * Verify navigation to:
      * "Images" $\rightarrow$ `ImageScannerScreen`
      * "Videos" $\rightarrow$ `VideoScannerScreen`
      * "Deep System Optimization" $\rightarrow$ `DeepSystemOptimizationScreen`
      * "Big File Map" $\rightarrow$ `BigFileMapScreen`

---

## 4. App Drawer (`AppDrawer.kt`)
**Objective**: Verify lateral navigation menu items.

* **Test Case 4.1: Drawer Opening & Profile Rendering**
  * *Steps*:
    1. Click the hamburger icon on the Dashboard header.
  * *Assertions*:
    * Verify that the drawer slides out.
    * Verify that user details (email/avatar for logged-in users, or "Guest User" label) display correctly.

* **Test Case 4.2: Drawer Links Navigation**
  * *Steps*:
    1. Open the drawer and click "Activity Log".
  * *Assertions*:
    * Verify that the app navigates to `ActivityLogScreen`.
    * Verify that the drawer closes upon selection.

---

## 5. Image & Video Scanners (`ImageScannerScreen.kt`, `VideoScannerScreen.kt`)
**Objective**: Validate permission requests, scanning states, and user cancel commands.

* **Test Case 5.1: Permission Gate Handling**
  * *Steps*:
    1. Revoke media permissions (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`).
    2. Click the "Scan" button.
  * *Assertions*:
    * Verify that the custom explanation dialog (`PermissionGate`) appears explaining why permission is needed.
    * Verify that agreeing to the gate launches the system permission request dialog.

* **Test Case 5.2: Scanning Progress & Interruption**
  * *Steps*:
    1. Grant permissions and click "Scan".
    2. Click the "Cancel Scan" button while the scan is at 50% progress.
  * *Assertions*:
    * Verify that the progress bar/indicator displays real-time updates.
    * Verify that the scan halts immediately, releases system resources (TFLite embedder, Face Detector), and does not modify the database.

---

## 6. Deduplication Results Screen (`DeduplicationScreen.kt` & `DuplicateClustersScreen.kt`)
**Objective**: Test duplicate grouping, EXIF handling, and smart auto-selection.

* **Test Case 6.1: Duplicate Grouping & Auto-Selection**
  * *Steps*:
    1. Inject duplicate image clusters into the mock database (e.g., Cluster A: 3 identical images, Cluster B: 2 near-duplicates).
    2. Open the deduplication results.
  * *Assertions*:
    * Verify that files are correctly grouped under their respective cluster headers.
    * Verify that the "Best Shot" (highest resolution, open eyes/smile detected via ML Kit) is marked as "Keep" and remains unchecked.
    * Verify that duplicate versions are auto-selected for deletion.

* **Test Case 6.2: Manual Override & Bulk Deletion**
  * *Steps*:
    1. Uncheck an auto-selected duplicate file and check a "Keep" photo.
    2. Verify the action bar updates: "Delete X files (Y MB)".
    3. Click "Delete Selected".
  * *Assertions*:
    * Verify that a warning confirmation dialog appears.
    * Click "Confirm" and verify that selected files are deleted or moved to Trash.
    * Verify that the deleted files no longer appear in the cluster list.

---

## 7. Smart Junk Cleaner Screen (`SmartJunkScreen.kt`)
**Objective**: Test AI-categorized junk filters.

* **Test Case 7.1: Image Category Filtering**
  * *Steps*:
    1. Inject images representing: 2 screenshots, 3 receipts (with text), and 1 blurry photo.
    2. Trigger the Smart Junk scan.
  * *Assertions*:
    * Verify that the category counts accurately reflect: Screenshots (2), Receipts (3), Blurry (1).
    * Verify that clicking on "Screenshots" filters and shows only the 2 screenshot images.

* **Test Case 7.2: Fast Bulk Clean**
  * *Steps*:
    1. Select all items in "Receipts" and click "Clean".
  * *Assertions*:
    * Verify that the files are removed.
    * Verify that the Wellness score or storage savings counter updates immediately.

---

## 8. WhatsApp & Social Media Cleaner Screen (`WhatsAppCleanerScreen.kt`, `SocialMediaCleanerScreen.kt`)
**Objective**: Verify sandbox folder scanning and cross-folder deduplication.

* **Test Case 8.1: WhatsApp Media Isolation**
  * *Steps*:
    1. Create mock directory structures matching WhatsApp's standard hierarchy (e.g., `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent`).
    2. Place unique and duplicate files there.
    3. Launch the WhatsApp cleaner.
  * *Assertions*:
    * Verify that the app scans "Sent" and "Received" folders separately.
    * Verify that exact duplicates across folders are identified using SHA-256 hashes.
    * Verify that system files outside these paths are ignored.

---

## 9. Big File Map (`BigFileMapScreen.kt`)
**Objective**: Verify file system tree walking and interactive treemap layout.

* **Test Case 9.1: Treemap Hierarchical Drill-Down**
  * *Steps*:
    1. Construct a mock directory tree: `Root/` $\rightarrow$ `Movies/` (5GB) and `Documents/` (500MB).
    2. Load `BigFileMapScreen`.
  * *Assertions*:
    * Verify that `Movies/` is rendered as the largest tile.
    * Click on the `Movies/` tile. Verify that the screen drills down to show child files/folders within `Movies/`.
    * Click the "Up" arrow navigation. Verify that the screen returns to the root node.

---

## 10. File Browser Screen (`FileBrowserScreen.kt`)
**Objective**: Ensure basic file management functionalities work.

* **Test Case 10.1: Sorting & Quick Share**
  * *Steps*:
    1. Open `FileBrowserScreen` under a directory with files of different sizes and dates.
    2. Toggle Sort options: "Sort by Size (Large to Small)".
  * *Assertions*:
    * Verify that the list updates with the largest files at the top.
    * Long press a file to trigger the contextual menu. Click "Share". Verify that the system Share sheet opens (using UI Automator).

---

## 11. Deep System Optimization Screen (`DeepSystemOptimizationScreen.kt`)
**Objective**: Ensure the health audit system triggers cleanups.

* **Test Case 11.1: Aggregated Scan & One-Tap Clean**
  * *Steps*:
    1. Launch the Deep System Optimization tool.
  * *Assertions*:
    * Verify that it triggers parallel scans for: App Caches, Empty Folders, and Social Media clutter.
    * Verify that after the scan completes, a "Clean Now" button appears.
    * Verify that clicking "Clean Now" triggers cleanup for all selected sections sequentially.

---

## 12. Settings Screen (`SettingsScreen.kt`)
**Objective**: Verify runtime config changes and local persistence.

* **Test Case 12.1: Instant Theme & Language Switching**
  * *Steps*:
    1. Navigate to Settings.
    2. Select "Dark Theme".
    3. Select "Spanish (Español)" as the app language.
  * *Assertions*:
    * Verify that the theme swaps to Dark Mode instantly (Compose themes reflect changes).
    * Verify that screen labels translate to Spanish (e.g., "Settings" becomes "Configuración") without requiring an app restart.

* **Test Case 12.2: Similarity Threshold Persist**
  * *Steps*:
    1. Drag the "Deduplication Similarity Slider" to "Hamming Distance: 5".
    2. Relaunch the app.
  * *Assertions*:
    * Verify that the slider value remains persisted at 5 in `DataStoreManager`.

---

## 13. Scan History & Activity Logs (`ScanHistoryScreen.kt`, `ActivityLogScreen.kt`)
**Objective**: Verify historical audit logging.

* **Test Case 13.1: Chronological Log Inspection**
  * *Steps*:
    1. Execute 2 mock scan-and-delete operations.
    2. Navigate to `ScanHistoryScreen`.
  * *Assertions*:
    * Verify that the logs show both events with timestamps, file counts, and storage space reclaimed (in MB/GB).
    * Verify that deleting a log row clears it from the UI and AppDatabase.
