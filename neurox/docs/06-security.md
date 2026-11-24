# Security specification and limitations

## Current protections

* The application requests no internet permission.
* Android app sandbox storage holds Room and DataStore files.
* Platform automatic backup is disabled.
* Cleartext network traffic is disabled.
* History and mutation APIs require a profile identifier.
* Deleting a profile cascades to its related rows.
* No backup password or static encryption key is stored in source.
* Entered typing strings do not enter session history.

## Portable backup envelope, version 1

`NXVB` (4 bytes) | version (1 byte) | PBKDF2 iterations (4 bytes, big-endian) | random salt (16 bytes) | random nonce (12 bytes) | AES-GCM ciphertext and tag.

Use PBKDF2-HMAC-SHA256 with 310000 iterations to derive a 256-bit key. Use AES-256-GCM with a 128-bit authentication tag. Authenticate the complete header as additional data. Use SecureRandom for a fresh salt and nonce on every export. Reject derivation costs outside 200000–600000. Limit plaintext to 4 MiB. Require 10–1024 password characters. Clear temporary key bytes and the PBE password specification. The caller owns and must clear its password character array.

PBKDF2 is the portability alternative for this milestone. This choice is not a claim of a completed independent cryptographic review. The portable cipher is tested on the sandbox JVM. Test its provider behavior and derivation latency on supported Android versions.

## Not yet implemented

The vault has no Android export/import UI. It has no staged database restore, payload allowlist, selective export, backup scheduling, SAF URI handling or Keystore integration. Do not expose a restore action until all these safety gates pass:

1. Read the file with a size limit.
2. Authenticate and decrypt the complete envelope.
3. Validate the payload version, schema, references and bounds.
4. Load the data into temporary storage.
5. Check referential integrity and allowed profile scope.
6. Commit all changes with a transaction or reviewed safe replacement.
7. Preserve the original database if any step fails.
8. Remove temporary plaintext files.
9. Report success only after commit.

A forgotten backup password can make an encrypted backup unrecoverable. No password recovery service is planned.

PIN protection, Keystore device-bound keys, retry throttling and screen capture policy need a separate threat-model review. The database is not encrypted at rest beyond the Android sandbox/device protections. A person who can use this unlocked app can switch between unprotected profiles. Do not advertise these profiles as a private vault.
