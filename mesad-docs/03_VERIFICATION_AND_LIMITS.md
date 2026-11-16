# Verification, Delivery & Honest Environment Limits

## Executed in the sandbox

- `cd mesad-studio && npm test` — **11/11 tests pass** across 2 files: seven time-engine tests plus four studio interaction tests for integrated navigation, alarm creation/repeat controls, IANA city persistence, and browser interactions.
- `cd mesad-studio && npm run build` — **passes**: strict TypeScript build and production Vite bundle. Current Three.js bundle is large (~808 kB uncompressed); split points/lazy loading are an optimization opportunity, not a correctness failure.
- `python3 -m venv .venv && .venv/bin/pip install pillow pytest` — succeeded. The icon suite generator completed; PNG and SVG outputs are checked in under `mesad-assets/icons/`.
- Three.js renderer uses generated 3D meshes and real-time lighting/material response. The Android view uses GLES 3 context, triangle geometry, depth buffer and shader lighting. The Performance Mode selects a 2D Compose Canvas renderer.

## Android build boundary and verified artifact

The sandbox does not include a JDK, Kotlin compiler, Gradle, Android SDK, or ADB, and outbound access to Google's SDK download host is blocked. However, GitHub Actions run [43939238578](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/43939238578) on commit `497b412` passed `testDebugUnitTest` and `assembleDebug`, then uploaded the `mesad-debug-apk` artifact. The separate WebGL studio tests and production build also passed. The APK can be downloaded from the run's **Artifacts** section; see `mesad/README.md` for installation steps. The artifact expires after 14 days.

This CI result confirms compilation and JVM unit tests, not emulator or physical-device behavior. Exact-alarm permission handling, notification behavior, boot rescheduling, adaptive launcher icon appearance, and frame-time/battery targets still need device verification. Do not treat a successful build as device QA.

## Product implementation caveats to check in CI/device QA

- Android exact alarms use `SCHEDULE_EXACT_ALARM` and a permission-aware inexact fallback; OS policy and permission dialogs must be validated on Android 12–16 hardware.
- The brand icon's vector adaptive layers are authoritative; raster previews are design exports. Verify adaptive masks/monochrome on OEM launchers.
- The native 3D view is procedural OpenGL ES geometry with a metallic lighting shader. Filament is not included in the Android build. The standalone WebGL studio uses Three.js physical materials. The native renderer has not yet been profiled against 60fps/battery acceptance targets.
- Android UI currently stores alarm/city/timer records in Room and user preferences in SharedPreferences. The web studio uses browser localStorage. No account/network service is required.
- Native reminders are local list items, not scheduled notification reminders; the web simulator provides local reminder management. Native reminder notification scheduling remains a follow-up product requirement.
