# Maintenance guide

## Before a change

1. Read the scope ledger and known limitations.
2. Run the core tests.
3. Run the Kotlin syntax scan.
4. Keep the Python panel unchanged unless a separate task requires a change.

## After a change

1. Add regression tests for the changed behavior.
2. Run `scripts/test-core.sh`.
3. Run `scripts/check-kotlin-syntax.sh`.
4. Build and lint the Android app in an SDK-capable environment.
5. Run Room and Compose instrumented tests.
6. Update the test report with actual results.
7. Update the scope ledger and release notes.

## Data changes

Generate and commit the Room schema after the first successful native build. Before a later schema change, increment the database version and add a non-destructive migration. Test migration using exported historical schemas. Keep checkpoint versions separate from database versions and vault-envelope versions. Reject unknown payload versions until a migration exists.

Do not log passwords, entered typing content, decrypted backups or private profile data. Keep runtime files, tool installations, SDKs, generated JARs and APK build outputs outside Git. Keep release signing material outside source control.

## Dependency baseline

The project pins Kotlin/Compose plugin 2.2.21, AGP 8.13.1, Gradle 8.13, KSP 2.2.21-2.0.4, Compose BOM 2025.10.01, Activity 1.11.0, Lifecycle 2.9.4, Room 2.8.3, DataStore 1.1.7 and Coroutines 1.10.2. These are pinned intended-compatible versions, not a claim that they are the newest versions. Android dependency resolution is not verified in this sandbox. Check official release notes, repository resolution and security advisories before release. Update a compatible group, then run the full regression matrix.

The sandbox Java runtime package uses Java 17.0.9 for isolated core tests. It is not an Android release toolchain recommendation. Use a maintained full JDK 17 in CI and local Android development.

## Build integrity

The Gradle wrapper scripts and JAR came from the Gradle v8.13.0 repository through the GitHub contents API. The wrapper JAR SHA-256 is `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`. Keep TLS validation enabled. Add official distribution checksum and dependency verification after successful resolution. Do not treat an unrun workflow as a passing check.

The standalone syntax utility uses a compiler API scheduled for change after Kotlin 2.2. Keep its compiler pinned until the utility is updated. It reports grammar errors only.
