# Decisions

Each entry records what was decided, why, and what it ruled out. Reversing one of these
should require reading it first.

---

## DEC-0001 - Offline first, local only

**Decision.** All data lives in a local Room database and local preferences. There is no
account, no server, no sync, no analytics transmission.

**Why.** The product is training, not data collection. A cognitive test that phones home
is a cognitive test whose measurements cannot be trusted. Offline also means the app
works on a train, which is where people have five spare minutes.

**Ruled out.** Cloud backup, cross-device sync, leaderboards, remote configuration.

---

## DEC-0002 - Multi-module Gradle project

**Decision.** Eight core modules and eleven feature modules rather than one module with
packages.

**Why.** The boundary that matters is "what can be tested without Android". Splitting
`core:presentation`, `core:domain` and `core:games` into pure JVM modules makes that
boundary a compiler error rather than a convention, and it is the reason 321 tests run
in seconds on a plain JVM.

**Cost.** More build files, and every new dependency has to be declared where it is
used. That cost has already paid for itself: the dependency cycle described in
DEC-0008 was only visible because modules are compiled separately.

---

## DEC-0003 - Repository streams are structural, not `Flow`

**Decision.** Repositories expose `Stream<T>` - a `fun interface` with a single
`suspend fun collect(collector: (T) -> Unit)` - rather than `kotlinx.coroutines.flow.Flow<T>`.

**Why.** `Flow` is itself a functional interface with the same single abstract method,
so any `Flow` satisfies this interface with no adapter. The domain therefore stays free
of a coroutines dependency in its public surface while still accepting the real Flows
the data layer returns.

**Consequence found the hard way.** A stream backed by Room never completes, so
`collect` does not return. Four screens used it as if it were a one-shot read and hung
before their first frame. `Stream.first(waitMillis)` is now the only supported snapshot
read, and it is bounded: an empty stream returns null instead of blocking forever.

---

## DEC-0004 - A JUnit shim in the verification script

**Decision.** `scripts/verify/TestRunner.kt` provides the small slice of JUnit the tests
use (`@Test`, `assertEquals`, `assertTrue`, ...) and runs them by reflection, rather
than depending on the real JUnit jars.

**Why.** The offline environment has no artifact repository beyond what the Kotlin
compiler ships with. The shim is ~80 lines and is only used by `verify_domain.sh`; the
Gradle build uses real JUnit.

**Risk.** The shim is not JUnit. A test that relies on JUnit features outside the shim
(`@Before`, parameterized runners, rules) silently would not run here. The shim is kept
deliberately small so that gap is obvious.

---

## DEC-0005 - Argon2id deferred, PBKDF2 at 210k iterations

**Decision.** Vault key derivation uses PBKDF2-HMAC-SHA256 with 210,000 iterations.
The format *accepts* Argon2id envelopes and reports that they were opened through the
PBKDF2 fallback.

**Why.** A JVM Argon2id implementation is a large amount of unreviewed native-adjacent
code. PBKDF2 at 210k iterations is a defensible floor, is available in the platform, and
the format is versioned so a migration is a new envelope version, not a break.

**Open.** Argon2id should be added when a reviewed dependency is available. The import
path already tells the user when a file used it.

---

## DEC-0006 - Cues are synthesized, not bundled as audio files

**Decision.** The nine cues are generated at runtime by `Synth`.

**Why.** Nine short files would be ~200 KB of assets, need licences, need density
variants, and would be tuned by ear in one environment and shipped to thousands. A
synthesised cue is data: frequency, envelope, duration - all testable and all reviewable
in a diff.

**Compromise.** A synthesised cue sounds like a synthesised cue. That is acceptable for
training signals and was rejected for anything musical, of which there is none.

---

## DEC-0007 - Themes change colour, not layout

**Decision.** A theme may change surfaces, accent, contrast and typography metrics. It
may not change spacing, radius, component structure or motion.

**Why.** A theme that can change layout is a theme that can break a training surface,
and a broken stimulus surface produces wrong measurements. Constraining themes to
colour keeps every theme measurably identical.

---

## DEC-0008 - Screens depend on `AppServices`, not on the container

**Decision.** Feature modules take `com.neurox.presentation.di.AppServices`, an interface
implemented by `AppContainer` in `:app`.

**Why.** With `:app` depending on every feature, no feature may depend on `:app`. Every
route previously imported `AppContainer`, which is a cycle. The previous build check
compiled modules in isolation and classified the resulting unresolved symbol as a
"platform gap", so the cycle was invisible for several sessions.

**Consequence.** Vault crypto and the backup coordinator are deliberately *not* on
`AppServices`: they are IO/Android concerns that only two screens need, so those screens
take them as explicit parameters and their dependency list is visible in the signature.

---

## DEC-0009 - Compilation against hand-written API stubs replaces the filtered audit

**Decision.** `scripts/compile_android.sh` compiles every Android module against
`tooling/stubs/` and requires zero diagnostics. The previous `audit_android_layers.sh`
was deleted.

**Why.** The audit grepped compiler output and discarded every error class that a
missing platform symbol *could* cause - including `argument type mismatch` and
`overload resolution ambiguity`, which is precisely where our bugs were. It reported
"0 real errors" on code that does not compile, repeatedly, across sessions. Filtering
error text is not verification.

**Trade-off accepted.** The stubs are hand-written and can disagree with the real
libraries. That is a known, bounded, documented risk (`docs/TEST_STATUS.md`) and it is
smaller than the risk of a check that cannot fail.

---

## DEC-0010 - `R` is generated for the offline gate

**Decision.** `tooling/gen_r.py` parses each module's `res/` and its Gradle `namespace`
and emits an `R` class for the compile gate.

**Why.** Without it, `R.font.vazirmatn_regular` cannot resolve offline, and a typo in a
resource name - the single most common Android mistake - is invisible to every check in
the repository. Generating `R` from the actual resource tree turns that into a compile
error.

**Limitation.** It checks existence, not types or values. Resource linking is still
Gradle's job.

---

## DEC-0011 - The loopback-free wording of the timing note

**Decision.** Every place that reports a response time states that the value is an
estimate, that it has been corrected for measured input and display latency, and what
the size of the uncertainty is.

**Why.** A reaction time measured through a touch digitizer and a compositor is not a
laboratory measurement. Presenting it as one would be a fabricated medical-adjacent
claim, which DEC-0012 forbids.

---

## DEC-0012 - No medical claims, no diagnosis, no "brain age"

**Decision.** The app reports task performance only. No claimed transfer to real-world
cognition, no IQ, no brain age, no clinical category.

**Why.** The evidence for transfer from trained tasks is weak at best. An app that
claims otherwise is lying to its users, and the string catalogues are auditable for it:
the phrasing is "your accuracy on this exercise today", never "your cognitive ability".

**Enforcement.** The catalogue is the surface where such a claim would appear, and it
has a parity and key-contract test. It does not police tone; a reviewer must.

---

## DEC-0013 - Persian digits for social numbers, Western digits for measurements

**Decision.** Level, streak, XP and dates use Persian digits in Persian. Milliseconds,
WPM and percentages use Western digits in both languages.

**Why.** A Persian reader expects ۱۲ for a level. But a stopwatch that reads ۰٫۴۱۲
seconds is harder to compare against a threshold than 0.412, and the measurement must be
unambiguous. The split is applied in one place (`Strings.decimal` vs the measurement
formatters) rather than per screen.

---

## DEC-0014 - The app shell refuses to guess its start screen

**Decision.** `AppUiState.initial()` has `loaded = false` and does not guess whether
onboarding is needed; the splash stays up until storage answers.

**Why.** Guessing "needs onboarding" flashes the onboarding screen at an existing user;
guessing the opposite flashes a home screen with no profile. Neither is acceptable at
cold start, and a branded splash for 200 ms is cheaper than either.
