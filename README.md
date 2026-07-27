# Dedup

AI-powered duplicate cleaner for Android.

Dedup helps users identify and remove duplicate and unnecessary files using intelligent media analysis while keeping user data private and processed locally whenever possible.

---

## Screenshots


| Media Analysis | Storage Insights | Cleanup Results |
|---|---|---|
| ![Dash](screenshots/1.png) | ![Files](screenshots/2.png) | ![Settings](screenshots/3.png) | ![Scan](screenshots/4.png) | ![Nav](screenshots/5.png) |

> Create a `screenshots` folder in the repository root and place your Play Store images inside it.

---

## Overview

Dedup helps clean:

- Duplicate photos
- Repeated videos
- Shared memes
- Old APK files
- Re-downloaded documents
- WhatsApp media clutter

It scans device storage and intelligently groups duplicate or similar files to recover storage safely and efficiently.

---

## Features

### Duplicate Detection

| Feature | Detail |
|---|---|
| Image Deduplication | Two-layer grouping: CRC32 exact hash (first 64 KB) + perceptual dHash (9×8 → 64-bit, Hamming distance ≤ 3) |
| Video Deduplication | Frame-hash fingerprinting to group visually identical or near-identical videos |
| File Deduplication | Byte-level duplicate detection across all file types (APKs, PDFs, documents, archives) |
| Near-Duplicate Detection | Configurable similarity threshold — catches resized, recompressed, or slightly edited copies |
| Best Shot Selection | ML Kit face detection scores each image in a duplicate group; highest-quality image recommended for keeping |
| EXIF Correction | Reads EXIF orientation before hashing — rotated copies of the same photo are correctly detected as duplicates |

---

### Smart Junk Cleaning

| Feature | Detail |
|---|---|
| AI Junk Scanner | On-device ML Kit image labelling categorises recent photos into: Screenshots, Memes & Graphics, Receipts & Docs, Blurry Shots |
| WhatsApp Cleaner | Scans received images, videos, documents, sent media, and statuses; SHA-256 checksums identify exact cross-folder duplicates |
| Social Media Cleaner | Finds media saved from Instagram, Telegram, Snapchat, Facebook, Twitter/X and similar apps via MediaStore path detection |
| Cache Cleaner | Clears app caches to recover space without deleting user data |
| Empty Folder Cleanup | Detects and removes empty directories left behind after file deletion |
| Download Folder Cleanup | Surfaces large or redundant files in the Downloads folder for review |

---

### Storage Analysis

| Feature | Detail |
|---|---|
| Dashboard | At-a-glance storage summary: used / free / total, per-category media counts (images, videos, audio, docs, APKs) |
| Big File Map | Interactive treemap visualisation of the file system — drill into folders, spot space hogs instantly |
| File Browser | Full recursive file browser with sort modes; direct delete or share from any file |
| File Scanner | Scans and lists all file types with size, date, and path; supports bulk selection and deletion |
| Scan History | Timestamped log of every scan run with before/after storage deltas |
| Activity Log | Per-operation audit trail of every file deleted or moved within the app |
| Storage Widget | Home-screen Glance widget showing real-time used/free storage at a glance |
| Savings Calculator | Estimates the monetary value of recovered storage in the user's local currency |

---

### Deep System Optimisation

| Feature | Detail |
|---|---|
| Deep System Optimisation Screen | Aggregated hub linking Cache Cleaner, Empty Folder Cleanup, and Social Media Cleaner in one flow |
| Contact Deduplication | Detects duplicate or mergeable contacts; previews merge before applying |
| Excluded Folders | User-defined folder exclusion list — chosen paths are skipped during all scans |

---

### AI-Powered Search

| Feature | Detail |
|---|---|
| Semantic Image Search | Natural-language photo search using on-device embedding model + cosine similarity (threshold 0.25, up to 200 results) |
| Fallback Keyword Search | Falls back to filename/metadata keyword search when embedding index is not yet built |
| Background Indexing | `ImageIndexWorker` (WorkManager) builds the embedding index in the background without blocking the UI |
| Gemini Classifier | Gemini-based image classifier for richer content understanding |

---

### Privacy & Security

| Feature | Detail |
|---|---|
| Local-First Processing | All scanning, hashing, and ML inference runs on-device — no media is uploaded to the cloud |
| Encrypted Database | Room database encrypted with SQLCipher; key managed by `KeyManager` via Android Keystore |
| Root Detection | Detects rooted devices and warns the user |
| Network Security | `NetworkSecurityManager` enforces certificate pinning / secure-channel policies |
| Firebase Auth | Optional Google Sign-In via Firebase — guest mode supported with no account required |
| All Files Permission | Graceful permission gate for `MANAGE_EXTERNAL_STORAGE`; explains rationale before requesting |

---

### Personalisation & Settings

| Feature | Detail |
|---|---|
| Theme | Light / Dark / System-default; additional colour palettes (Solar theme + custom `AppPalette`) |
| Language | In-app locale switching via `LocaleManager`; no app restart required |
| Currency | Configurable currency code for the savings calculator |
| Similarity Threshold | Slider (0–64 bits Hamming distance) to tune how aggressively near-duplicates are grouped |
| Auto-Scan on Startup | Toggle to trigger a background scan automatically when the app launches |
| Feedback & Feature Requests | In-app submission dialogs backed by Firebase Firestore |

---

### Platform & Infrastructure

| Feature | Detail |
|---|---|
| Background Scanning | `ScanWorker` (WorkManager) runs scans off the main thread with progress reporting |
| Push Notifications | Firebase Cloud Messaging for scan-complete and storage-warning alerts |
| Storage Level Receiver | System broadcast receiver triggers a notification when device storage drops below a threshold |
| Scan Handoff | `HandoffReceiver` supports inter-process / widget-triggered scan handoff |
| Bubble Launcher | Floating bubble UI for quick access without leaving the current app |
| Report Generator | Exports a scan summary report for sharing or logging |
| Trash Manager | Soft-delete with a recoverable trash bin before permanent deletion |

---

## Platform Support

| Current | Planned |
|---|---|
| Android | iOS |
|  | Desktop platforms |
|  | Cloud integrations |

---

## Technology Stack

| Layer | Technologies |
|---|---|
| Language | Kotlin |
| Framework | Android SDK |
| Architecture | Jetpack Components |
| Concurrency | Coroutines |
| Storage | Room Database |
| Media Access | MediaStore APIs |

---

## Installation

This repository is private and proprietary.

Access is currently limited to authorized testers and collaborators only.

---

## Early Access Testing

For becoming a part of the early testing group, please fill out the registration form shared by the repository owner.

---

## Project Status

Dedup is under active development. Features, APIs, and internal implementations may change during the preview phase.

---

## Intellectual Property Notice

Copyright © 2026 Rahul Pahuja. All rights reserved.

This repository, source code, architecture, assets, branding, algorithms, and associated materials are proprietary intellectual property owned by Rahul Pahuja.

Unauthorized copying, modification, redistribution, sublicensing, resale, reverse engineering, public distribution, or commercial usage of this software, in whole or in part, is strictly prohibited without explicit written permission from the owner.

Access to this repository does not grant permission to reproduce, replicate, or create derivative works from the software.

---

## Contributing

Contributions, suggestions, and issue reports are welcome.

By contributing to this repository, you agree that all contributions become part of the proprietary project owned by the repository owner.

---

## Author

| Name | Role |
|---|---|
| Rahul Pahuja | Staff Software Engineer · Mobile Architect · Founder, Mobile1X |

---

## Contact

For licensing, collaboration, partnership, or commercial inquiries, please contact the repository owner directly.
