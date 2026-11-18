# AegisGuard — 100% Offline Personal, Family & Community Security Ecosystem

AegisGuard (`ir.aegis.security`) transforms the baseline alert concept into a complete **100% offline-first Android security, preparedness, and crisis management suite** built with **Kotlin, Jetpack Compose (Obsidian Glass Design System), Room SQLite, DataStore, WorkManager, and Clean Architecture** across **17 Gradle modules**.

## Zero-Network Guarantee
- The merged Android manifest declares **zero `INTERNET`**, **zero `ACCESS_NETWORK_STATE`**, and **zero `ACCESS_WIFI_STATE`** permissions.
- Every core capability operates reliably in **Airplane Mode** without GPS, Bluetooth, Wi-Fi, or mobile signal.
- Optional hardware sensors (GPS, Camera torch/scanner, Microphone, BiometricPrompt) degrade gracefully when unavailable or denied.
- No analytics SDKs, cloud backends, or background telemetry exist anywhere in the codebase.

## Visual Identity
- **Obsidian Glass**: Deep layered canvas with diagonal specular borders and refractive cyan/teal/violet/crimson accents.
- **Adaptive Light & Dark + Bilingual EN/FA (LTR/RTL)**: Instant switching from the dashboard header.
- **Hybrid Glass + 3D**: Interactive 3D perspective tilt cards, animated 3D gyroscope readiness shield, and hybrid 2D/3D projected offline vector maps.

## 10 Modules
1. **Personal Security Center (`:feature:personal`)**: Security Profile, Emergency Identity Card (ICE) with deterministic visual code, AES-256-GCM Encrypted Vault, Trusted Contacts with quick dialer, Personal Safety Plans, WorkManager Security Reminders, and optionally encrypted Private Incident Journal.
2. **Advanced Crisis Management (`:feature:crisis`)**: Incident registration with live offline AI classification & duplicate detection, category/priority/status workflows, timeline events, SHA-256-verified evidence attachments, interactive checklists, search/archive filtering, and offline PDF report export.
3. **Offline Map System (`:feature:maps`)**: 2D orthographic & 3D perspective vector map engine (`Projection3D`), bundled offline regional packages, custom markers, saved evacuation routes, layer & category filters, Haversine distance/bearing ruler, live/fallback GPS + DMS/UTM coordinates, and compass.
4. **Emergency Response Center (`:feature:emergency`)**: SOS Morse Torch & 2.6 kHz AudioTrack beacon, quick native emergency dialer, high-visibility ICE summary, coordinate readout, SMS/clipboard Emergency Message Generator, reviewed offline First-Aid & Disaster Guides, and Go-Bag Equipment Checklists.
5. **Local AI Intelligence (`:feature:ai`)**: Offline BM25 knowledge search, deterministic assistant with explicit safety banners, incident classifier, Jaccard+Haversine duplicate detector, extractive summarizer, dynamic checklist generator, statistical risk insights, and optional user-imported local `.gguf`/`.tflite` model pack manager.
6. **Family Safety (`:feature:family`)**: Multi-member profiles & medical ICE cards, live check-in status, Family Emergency Plan, Primary/Backup Meeting Point Planner, family checklists, and optional explicit-consent offline JSON sharing with SHA-256 verification.
7. **Preparedness Academy (`:feature:academy`)**: 6 offline domains (First Aid, Fire Safety, Earthquake, Flood, Travel Safety, Digital Safety), structured lessons, graded quizzes, interactive field scenarios, and persistent Room progress & badges.
8. **Advanced Analytics (`:feature:analytics`)**: Custom 7D/30D/All-Time & category filters, time-series bar chart, category distribution breakdown, 6×6 spatial incident heatmap, executive summary, and offline PDF/CSV export.
9. **Privacy & Security (`:feature:privacy`)**: PBKDF2-HMAC-SHA256 PIN lock + BiometricPrompt integration, `FLAG_SECURE` screenshot shield, lock-screen notification redaction, AES-256-GCM encrypted `.aegisbak` full snapshot export/restore, local activity audit trail, panic wipe, and permission inspector.
10. **Advanced Utilities (`:feature:utilities`)**: 12 real offline tools — Hardware Flashlight/Strobe + White Screen Fallback, Sensor Compass, GPS Telemetry, M4A Voice Notes Recorder/Player, High-Contrast PDF Document Scanner, Offline Notes, Emergency Inventory Tracker, Ration Resource Calculator, Dual Gregorian/Jalali Calendar, Quick Reminders, Countdown Timer, and Multi-Domain Unit Converter.

## Building & Testing
```bash
cd aegis-security
gradle testDebugUnitTest lintDebug assembleDebug assembleRelease
gradle :core:database:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```
