# DeDup — Product Requirements Document

**Product:** DeDup  
**Platform:** Android (Kotlin / Jetpack Compose)  
**Author:** Rahul Pahuja  
**Status:** Active Development  
**Last Updated:** 2026-06-30

---

## 1. Product Vision

DeDup is an AI-powered, privacy-first storage optimizer for Android. It identifies and removes duplicate files, junk media, and redundant data entirely on-device — no cloud upload, no data exposure — to help users reclaim storage and keep their device running cleanly.

---

## 2. Target Users

- Android users with 16 GB–256 GB devices running low on storage
- Heavy WhatsApp / social media users accumulating duplicate media
- Users with large photo libraries (1 000+ images)
- Power users who want control over what is deleted

---

## 3. Features

### 3.1 Duplicate Detection

| Feature | Description | Status |
|---|---|---|
| **Image Deduplication** | Two-layer grouping: CRC32 exact hash (first 64 KB) + perceptual dHash (9×8 → 64-bit, Hamming distance ≤ configurable threshold). Catches resized, recompressed, and slightly edited copies. | Shipped |
| **EXIF Rotation Correction** | Reads EXIF orientation before hashing so rotated copies of the same photo are correctly grouped as duplicates. | Shipped |
| **Near-Duplicate Threshold** | User-configurable Hamming distance slider (0–20 bits). Lower = strict (only very similar); higher = relaxed (catches more variants). | Shipped |
| **Best Shot Selection** | ML Kit face/quality scoring ranks images within each duplicate group; highest-quality image is highlighted as the recommended keep. | Shipped |
| **Video Deduplication** | Frame-hash fingerprinting groups visually identical or near-identical videos. Incremental resume: already-scanned videos are persisted and skipped on re-scan. | Shipped |
| **File Deduplication** | Size + name/checksum grouping across all file types (APKs, PDFs, ZIPs, archives, documents). Deep-scan mode computes full checksum for disambiguation. | Shipped |

---

### 3.2 Smart Junk Cleaning

| Feature | Description | Status |
|---|---|---|
| **AI Smart Junk Scanner** | On-device ML Kit image labelling classifies recent photos into: Screenshots, Memes & Graphics, Receipts & Docs, Blurry Shots. One-tap auto-clear per category. | Shipped |
| **WhatsApp Cleaner** | Scans received images, videos, documents, sent media, and statuses in WhatsApp folders. SHA-256 checksums identify exact cross-folder duplicates. Batch delete with confirmation. | Shipped |
| **Social Media Cleaner** | Detects media saved from Instagram, Telegram, Snapchat, Facebook, Twitter/X via MediaStore path matching. Supports bulk review and deletion. | Shipped |
| **Cache Cleaner** | Clears app caches across installed apps to recover temporary storage. Animated progress with before/after size delta. | Shipped |
| **Empty Folder Remover** | Scans the file system for empty directories left behind after file deletion. Batch-selects and deletes with a single tap. | Shipped |
| **Large File Finder** | Surfaces files above user-selected thresholds (>50 MB / >100 MB / >200 MB). Organized into: Unused Video Assets, Obsolete Archives, Large App Downloads, Old Downloads. List and grid view modes. | Shipped |
| **Old Downloads Cleanup** | Highlights files in the Downloads folder that are older than 3 months for review and removal. | Shipped |

---

### 3.3 File Management

| Feature | Description | Status |
|---|---|---|
| **File Browser** | Full recursive file browser with sort modes (name, date, size, type). Direct delete or share from any file. | Shipped |
| **PDF Scanner** | Scans for duplicate PDF files by size + name. Supports bulk selection and MediaStore-backed deletion. | Shipped |
| **APK Scanner** | Scans for duplicate or redundant APK files. Useful after sideloading multiple app versions. | Shipped |
| **File Cleanup Screen** | Hub screen combining category shortcuts (PDFs, APKs) with the Large File Finder section. | Shipped |

---

### 3.4 Storage Analysis & Insights

| Feature | Description | Status |
|---|---|---|
| **Dashboard** | At-a-glance storage ring: used / free / total. Per-category media counts (images, videos, audio, docs, APKs). Reclaimable bytes estimate. | Shipped |
| **Storage Savings Calculator** | Converts reclaimable bytes to estimated monetary savings in the user's selected local currency (47 currencies supported). | Shipped |
| **Big File Map (Treemap)** | Interactive treemap visualization of the file system. Drill into folders to spot space hogs. Tap to navigate or delete. | Shipped |
| **Scan History** | Timestamped log of every scan run. Shows: type, duration, files scanned, duplicates found, reclaimable bytes, status (completed / cancelled). | Shipped |
| **Activity Log** | Per-operation audit trail of every file deleted or moved within the app, with date and size delta. | Shipped |
| **Storage Widget** | Home-screen Glance widget showing real-time used / free storage without opening the app. | Shipped |
| **Quick Scan Grid** | Dashboard shortcut grid: Images, Videos, Documents, APKs, Browse Files, Scan History, Smart Cleanup. | Shipped |

---

### 3.5 AI-Powered Search

| Feature | Description | Status |
|---|---|---|
| **Semantic Image Search** | Natural-language photo search using on-device text-image embedding model + cosine similarity (threshold 0.25, up to 200 results). | Shipped |
| **Keyword Fallback Search** | Falls back to filename / metadata keyword search when the embedding index is not yet ready. | Shipped |
| **Background Index Building** | `ImageIndexWorker` (WorkManager) builds the embedding vector index in the background, non-blocking. | Shipped |
| **Search Suggestions** | Pre-built suggestion chips (WhatsApp, screenshots, etc.) to guide first-time users. | Shipped |
| **In-Search Delete** | Delete a matched image directly from the search results sheet without leaving the flow. | Shipped |

---

### 3.6 Contact Deduplication

| Feature | Description | Status |
|---|---|---|
| **Duplicate Contact Detection** | Groups contacts by: identical name, identical phone number, or similar info (fuzzy match on email + company). | Shipped |
| **Merge Preview** | Shows primary record vs. duplicates side-by-side before committing a merge. User selects which phone/email entries to keep or discard. | Shipped |
| **Merge Execution** | Writes merged contact data via `ContentProviderOperation` batch, removes duplicates cleanly. | Shipped |
| **Mergeable Groups Counter** | Dashboard card shows the count of mergeable contact groups found. | Shipped |

---

### 3.7 Deep System Optimization

| Feature | Description | Status |
|---|---|---|
| **Deep System Optimization Hub** | Aggregated screen linking Cache Cleaner, Empty Folder Cleanup, and Social Media Cleaner in one guided flow. | Shipped |
| **AI Assistant Voice Storage** | Voice-command entry point for storage actions (navigation target, UI wired). | Shipped |

---

### 3.8 Privacy & Security

| Feature | Description | Status |
|---|---|---|
| **Local-First Processing** | All scanning, hashing, and ML inference runs entirely on-device. No media is uploaded to the cloud. | Shipped |
| **Encrypted Database** | Room database encrypted with SQLCipher. Key managed by `KeyManager` via Android Keystore. | Shipped |
| **Root Detection** | 6-check root detection (su binary, test keys, dangerous props, Magisk, build tags, file paths). Blocks app use on rooted devices and logs the detection event to Firebase Analytics. | Shipped |
| **Play Integrity API** | Verifies device integrity and app authenticity via Google Play Integrity. | Shipped |
| **Native Secret Storage** | Sensitive constants compiled into a native `.so` via CMake and loaded via JNI — not visible in the APK's DEX. | Shipped |
| **Deep Link Allowlist** | `MainActivity` validates incoming deep-link routes against an explicit allowlist. Unauthorized routes are silently rejected. | Shipped |
| **All Files Permission Gate** | Graceful permission rationale screen before requesting `MANAGE_EXTERNAL_STORAGE`; UI degrades gracefully on denial. | Shipped |
| **Guest Mode** | Users can explore the app without signing in. Delete and premium actions prompt sign-in via `GuestSignInDialog`. | Shipped |
| **Firebase Auth (Google Sign-In)** | Optional Google Sign-In via Firebase. Session persisted across restarts. | Shipped |

---

### 3.9 Personalisation & Settings

| Feature | Description | Status |
|---|---|---|
| **Theme** | Light / Dark / System-default toggle + 6 colour palettes (Ocean, Midnight, Forest, Sunset, Rose, Mono). | Shipped |
| **In-App Language Switching** | `LocaleManager` switches locale without app restart. Supports system default + all device-supported locales. | Shipped |
| **Currency Picker** | 47 currencies; user picks one for the savings calculator. Persisted via DataStore. | Shipped |
| **Image Similarity Threshold** | 0–20 bit Hamming distance slider in Settings. Applied to all image scans. | Shipped |
| **Excluded Folders** | User-defined folder exclusion list. Paths are skipped during all scans. | Shipped |
| **Auto-Scan on Startup** | Toggle in Settings. Triggers a background `ScanWorker` automatically on app launch. | Shipped |
| **Feedback Dialog** | In-app text (+ voice input button) feedback submission backed by Firebase Firestore. | Shipped |
| **Feature Request Dialog** | Separate in-app feature request form, also backed by Firestore. | Shipped |

---

### 3.10 Platform & Background Infrastructure

| Feature | Description | Status |
|---|---|---|
| **Background Scan Worker** | `ScanWorker` (WorkManager) runs image/file scans off the main thread with incremental progress reporting. | Shipped |
| **Push Notifications** | Firebase Cloud Messaging for scan-complete and storage-warning alerts. | Shipped |
| **Storage Level Receiver** | System broadcast receiver triggers a local notification when device storage drops below a threshold. | Shipped |
| **Scan Handoff Receiver** | `HandoffReceiver` supports inter-process / widget-triggered scan handoff. | Shipped |
| **Onboarding Tutorial** | `IntroShowcase`-based tooltip walkthrough for first-time users. Covers: AI Search, Storage Overview, Quick Scan, Optimization Tips. Persisted via DataStore so it shows only once. | Shipped |
| **App Drawer Navigation** | Side drawer with links to all major sections; shows user profile avatar (photo or initial) and theme switcher. | Shipped |
| **Glass Morphism Navigation Bar** | Bottom nav bar with animated shimmer sweep, specular border highlight, spring-animated pill indicator, and glow effect around selected icon. | Shipped |
| **Share App Card** | Dashboard card to share the app's Play Store link via the system share sheet. | Shipped |
| **About Screen** | App version, build number, developer name, website link, project site link. | Shipped |
| **Privacy Policy Screen** | In-app WebView rendering the privacy policy. | Shipped |

---

## 4. Navigation Structure

```
Splash
└── Login (optional — guest mode available)
    └── Dashboard
        ├── Image Scanner
        │   └── Duplicate Clusters (results)
        ├── Video Scanner
        ├── File Cleanup
        │   └── File Scanner (PDF / APK)
        ├── Settings
        ├── Activity Log
        ├── Scan History
        ├── File Browser
        ├── Cache Cleaner
        ├── Smart Junk (AI)
        ├── Deep System Optimization
        │   ├── WhatsApp Cleaner
        │   ├── Social Media Cleaner
        │   └── Empty Folder Remover
        ├── Big File Map (Treemap)
        ├── Contact Deduplication
        ├── Voice/AI Storage Assistant
        ├── About
        └── Privacy Policy
```

---

## 5. Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + StateFlow + Repository pattern |
| Async | Coroutines + Flow |
| DI | Manual (companion-object Factory pattern) |
| Storage | Room (SQLCipher encrypted) + DataStore Preferences |
| Media Access | MediaStore API |
| Auth | Firebase Authentication (Google Sign-In) |
| Backend | Firebase Firestore (feedback), Firebase Analytics, FCM |
| ML | ML Kit (image labeling, face detection), Gemini classifier |
| Security | Android Keystore, Play Integrity API, SQLCipher, JNI native secrets |
| Background | WorkManager |
| Image Loading | Coil |
| Navigation | Jetpack Navigation Compose |

---

## 6. Non-Functional Requirements

| Requirement | Target |
|---|---|
| Scan performance | Image scan: ≥ 8 parallel coroutines; video scan: buffered channel with checkpoint every 5 videos |
| Privacy | Zero media bytes leave the device during scanning or deduplication |
| Database security | AES-256 via SQLCipher; key never stored in plaintext |
| Cold start | App cold start < 2 s on mid-range device |
| Permission model | Degrade gracefully on permission denial; never crash |
| Root detection | Block app on positive detection; log to Analytics |
| Localization | All user-visible strings in `strings.xml`; in-app locale switching |

---

---

## 8. Product Roadmap

### Now — Active (v1.x)
All features listed in sections 3.1–3.10 are shipped or in active development.

---

### Q3 2026 — Storage Safety & Engagement

| Feature | Description | Priority |
|---|---|---|
| **Recoverable Trash (30-day bin)** | Files "deleted" through DeDup are moved to a private `.dedup_trash` directory. A dedicated Trash screen lets users restore or permanently delete within 30 days. Auto-expiry on app open. | P0 |
| **Storage Health Score** | A 0–100 gamified score on the dashboard computed from: free-space ratio, scan recency, reclaimable bytes, and app cache size. Score history tracked so users see improvement over time. | P0 |
| **Google Photos Backup Check** | Before deleting a photo, query `MediaStore` / Photos content provider to detect backup status. Surface "Backed up — safe to delete" vs. "No backup — delete with caution" UI. | P1 |
| **Cleanup Campaign (Goal Mode)** | "Free 1 GB" / "Free 5 GB" modes build an ordered risk-ranked deletion plan (caches → empty folders → exact duplicates → near-duplicates) and walk users through it step-by-step. | P1 |

---

### Q4 2026 — Intelligence & Monetization

| Feature | Description | Priority |
|---|---|---|
| **AI Best-of-Burst Curation** | When scanner identifies a burst group (≥3 near-identical photos within 3 seconds), auto-score each on sharpness, exposure, and face quality. Surface "Keep Best Shot" one-tap card. | P0 |
| **Predictive Storage Alerts** | On-device linear regression on 30 days of DataStore-persisted storage deltas. Push "You'll hit critical storage in ~14 days" notification with a one-tap deep-link to clean. | P0 |
| **Duplicate Source Attribution** | Post-scan chart showing which sources generated duplicates: WhatsApp received, Camera burst, Telegram, Downloads. Turns raw numbers into personalized recommendations. | P1 |
| **Freemium Monetization Gates** | Free tier: up to 500-image scan, manual cache clear, basic stats. Pro tier ($2.99/month or $14.99/year): unlimited scans, scheduled cleanup, video compression, trash bin, health score history, priority background scan. One-time $29.99 lifetime option. | P0 |
| **Shareable Cleanup Report** | One-tap shareable card — "I freed 3.2 GB with DeDup" — showing before/after storage ring, items cleaned, and Play Store QR code. Organic viral loop. | P2 |

---

### Q1 2027 — Compression & Advanced Optimization

| Feature | Description | Priority |
|---|---|---|
| **Video Transcoding** | On-device `MediaCodec` + `MediaMuxer` pipeline. Compress 4K → 1080p or 1080p → 720p with a quality slider. Preview size saving before committing. | P0 |
| **Image Recompression** | Batch re-compress JPEG/PNG/WebP via `Bitmap.compress(WEBP_LOSSY, 85)`. Typically 40–60% size reduction with imperceptible quality loss. | P1 |
| **Smart Format Conversion** | HEIC → JPEG (compatibility) and JPEG/PNG → WebP (size). Before/after size preview per file. | P2 |
| **Semantic Duplicate Detection** | Lightweight on-device vision embedding (MobileNetV3 / EfficientNet-Lite) via ONNX Runtime to catch semantic duplicates — same scene from slightly different angles, memes cropped differently. Repurposes existing `ImageSearchRepository` embedding infrastructure. | P0 |
| **SD Card / USB OTG Offload** | "Move large files to SD card" flow using `DocumentsContract`. Scan files above user threshold, batch-move to SD with a progress bar. | P1 |

---

### Q2 2027 — Platform & Ecosystem

| Feature | Description | Priority |
|---|---|---|
| **Quick Settings Tile** | `TileService` in the notification shade: current storage %, one-tap "Scan now" that triggers `ScanWorker`. Top-of-mind retention hook. | P0 |
| **Wear OS Companion** | Wear OS tile / complication: storage % ring + one-tap "Quick Clean" that triggers `ScanWorker` remotely. Targets Pixel Watch and Galaxy Watch users. | P1 |
| **Open Scan Audit Log** | Every file read/moved/deleted creates a signed log entry. Export as JSON. Differentiator: "DeDup shows exactly what it did and why." Targets privacy-conscious users and editorial features. | P1 |
| **Privacy Dashboard** | Screen showing: files scanned this session, files deleted, data sent to internet (always 0 bytes). Powerful trust signal in a category where users are skeptical. | P1 |
| **Batch Rename on Merge** | When merging duplicates, rename surviving file to canonical pattern (`YYYY-MM-DD_###` or original camera name). Especially valuable for WhatsApp hash-named files. | P2 |

---

### Future (Unscheduled)

| Feature | Description |
|---|---|
| **Cross-Device Dedup (P2P WiFi Direct)** | Detect photos/videos duplicated across family devices over local WiFi. No cloud transfer. Very high technical complexity. |
| **NAS / Network Drive Scanning** | Scan network-attached storage via SMB/SFTP. Target power users with home NAS setups. |
| **iOS App** | Native Swift / SwiftUI implementation with feature parity. Separate codebase. |
| **Android Files App Integration** | Register `DocumentsProvider` so DeDup appears as a source in the system Files app — platform-level trust signal. |
| **AR Storage Visualization** | ARCore-based visualization of storage as physical objects in space. Experimental / marketing-led. |

---

## 7. Out of Scope (Current Version)

- iOS support
- Cloud backup / sync
- Desktop platforms
- Automated scheduled scans (auto-scan is on-demand at launch only)
- Direct sharing to cloud storage (Drive, Dropbox, etc.)
