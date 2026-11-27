# Technology decisions — initial record

This is a shortlist and risk record, not a completed dependency audit. No new runtime or library was added this cycle. Package ranges and resolved versions live in `kairos-app/package.json` and `package-lock.json`.

## TD-01 — Existing local store: Dexie/IndexedDB

- **Status:** retained for current functionality, not a final decision for native widget writes.
- **Reason:** existing migrations, local operation and transaction support; replacing a working database now would put data at risk.
- **Official reference:** https://dexie.org/docs/API-Reference — read-write transactions are atomic; do not wait for unrelated asynchronous APIs inside them.
- **Alternative:** native database shared between app and widget process could give native components one authoritative store, but migration, Android API 24 support, licensing and compatibility need proof. No technology selected or migrated yet.
- **Test/fallback:** fake-indexeddb tests exercise current transactions; Android WebView/process-death behavior remains untested. Never pretend a native widget can directly update Dexie when the WebView is absent.

## TD-02 — Native widgets

- **Status:** retain current AppWidgetProvider/RemoteViews while auditing; not a new ecosystem implementation.
- **Official references:** https://developer.android.com/develop/ui/views/appwidgets/advanced and https://developer.android.com/design/ui/mobile/guides/widgets/widget_quality_guide . Periodic updatePeriodMillis does not support intervals below 30 minutes; interactions may trigger updates but need safe data access.
- **Alternative:** Glance; its Kotlin/Compose integration and compatibility with this Java/Capacitor project must be evaluated before adoption.
- **Fallback:** show honest stale/error state or open the app; do not fake successful writes. Launcher testing is pending.

## TD-03 — Local inference candidates

- **Status:** RESEARCHING; no runtime or model selected, installed or benchmarked.
- **Alternatives:** LiteRT-LM, llama.cpp, ONNX Runtime. Compare Android API/ABI compatibility, model format, maintenance, licences, APK/native library size, CPU fallback, thermal/RAM constraints and tool calling.
- **Official reference:** https://ai.google.dev/edge/litert-lm/overview describes supported backends and examples on named devices. Those benchmarks do not establish POCO X3 Pro compatibility, Persian quality or Adreno 640 support.
- **Fallback:** retain clearly labelled rule-based local assistant; do not label it as an LLM. Model acquisition requires explicit consent and separate storage; inference must operate offline. This project currently removes INTERNET permission, so an in-app download would require an explicit policy change or external file import.
- **Pending:** review exact runtime/model licences and version compatibility, run in project, test CPU and GPU and benchmark actual target hardware. No cloud inference.

## TD-04 — Android system backups

- **Status:** unresolved policy, no manifest change in this cycle.
- **Reference:** https://developer.android.com/privacy-and-security/risks/backup-best-practices and https://developer.android.com/identity/data/autobackup explain separate Android 11-and-earlier and Android 12+ rules and manufacturer-specific transfer limitations.
- **Trade-off:** disabling backups can reduce exposure but may remove a user's recovery route; preserve current behavior until an explicit migration and recovery strategy exists. Existing app backup is incomplete.
- **Validation:** Android API-specific cloud and device-to-device tests still required; no claim of actual transfer or leak.
