# Verification, Delivery & Honest Environment Limits

## Executed in the sandbox

- `cd mesad-studio && npm test` — **11/11 tests pass** across 2 files: seven time-engine tests plus four studio interaction tests for integrated navigation, alarm creation/repeat controls, IANA city persistence, and browser interactions.
- `cd mesad-studio && npm run build` — **passes**: strict TypeScript build and production Vite bundle. Current Three.js bundle is large (~808 kB uncompressed); split points/lazy loading are an optimization opportunity, not a correctness failure.
- `python3 -m venv .venv && .venv/bin/pip install pillow pytest` — succeeded. The icon suite generator completed; PNG and SVG outputs are checked in under `mesad-assets/icons/`.
- Three.js renderer uses generated 3D meshes and real-time lighting/material response. The Android view uses GLES 3 context, triangle geometry, depth buffer and shader lighting. The Performance Mode selects a 2D Compose Canvas renderer.

## Android build boundary

This sandbox does not include a JDK, Kotlin compiler, Gradle, Android SDK, or ADB. Outbound access to Google's SDK download host is blocked by the sandbox allowlist. The Android source and GitHub Actions build workflow are provided, but **the native APK has not been compiled here**. Use the `MESAD verification` GitHub workflow (standard Ubuntu/JDK 17/SDK 36 environment) or build in Android Studio to confirm dependency resolution, Kotlin/manifest/resource correctness, emulator behavior, exact-alarm permission handling, notification behavior, boot rescheduling, and frame-time/battery targets. Do not treat source-level inspection as equivalent to device QA.

## Product implementation caveats to check in CI/device QA

- Android exact alarms use `SCHEDULE_EXACT_ALARM` and a permission-aware inexact fallback; OS policy and permission dialogs must be validated on Android 12–16 hardware.
- The brand icon's vector adaptive layers are authoritative; raster previews are design exports. Verify adaptive masks/monochrome on OEM launchers.
- The native 3D view is procedural OpenGL ES geometry with a metallic lighting shader, while Filament dependencies are included for future renderer substitution. The standalone WebGL studio uses Three.js physical materials. The native renderer has not yet been profiled against 60fps/battery acceptance targets.
- Android UI currently stores alarm/city/timer records in Room and user preferences in SharedPreferences. The web studio uses browser localStorage. No account/network service is required.
- Native reminders are local list items, not scheduled notification reminders; the web simulator provides local reminder management. Native reminder notification scheduling remains a follow-up product requirement.
