# Product requirements and scope ledger

The supplied NEUROX master specification remains the product scope. This ledger does not approve omitted functionality. A source implementation is not a completed feature until its acceptance gates pass.

| Requirement group | This milestone | Remaining acceptance work |
|---|---|---|
| Native offline Android | Kotlin, Compose, Room, DataStore source; no network permission | APK build, airplane-mode/device checks |
| Visual identity | Geometric emblem, vector app/notification assets, SVG light/dark/mono/splash variants | Adaptive launcher integration, cinematic skip-once introduction, visual device review |
| Design system | Semantic tokens, distinct light/dark palettes, local Persian/Latin font, consistent glyphs | Full adaptive/tablet layouts, contrast audit of every state |
| Color Studio | 11 presets plus custom hex, two variants, preview, apply, save, edit/rename, delete, defaults | Android persistence and accessibility verification |
| Navigation/home | Six main destinations, native bottom navigation, real aggregates, empty state, interrupted session actions | Deep links, contextual motion, missions, recommendations on home, full restoration matrix |
| VISION | Focus Lock, seeded Visual Search and Pattern Match core/UI source | Five remaining exercises and full parameter controls |
| REFLEX | Quick Tap, balanced Go / No-Go and Shape Reaction; inhibition separated from response timing | Six remaining exercises; frame/input latency instrumentation |
| READ | Comprehension, timed Phrase Reading and RSVP Reader, three original bilingual passages, separate reading/question time | Six remaining exercises, Reading Studio, safe import, position/history controls |
| TYPE | Precision, per-word Word Sprint and Code Typing, Unicode-aware comparison, documented WPM, aggregated weak keys | Five remaining exercises, rhythm, layout support matrix, targeted weak-key training |
| Adaptive intelligence | Conservative multi-session rules, reasons, accept/reject log, disable setting | Goal-aware duration/weak-skill/recovery planning; more statistics |
| Planner | Tested overlap/rest-period rules | Planner UI, persistent schedules and plan history |
| QUEST | XP, derived level, streak; no duplicate completed-session XP | Module levels, achievements, missions, challenges, milestones, reward paths |
| Library | Original/My Exercises/Favorites areas, search, module filter, profile favorites, finite builder schema, preview, real unscored test run, save/edit/duplicate/delete | Full stimulus geometry, fixed-duration sessions, custom scoring, device workflow acceptance |
| SENSE | No playback is exposed | Local audio, independent module controls, focus/silence/resource handling, haptics |
| BASELINE | Per-session baseline flag; separate history and no XP | Guided four-module assessment, practice rounds, summary and plan |
| IDENTITY | Guest, personal profiles, separate preferences/history/themes/favorites, delete confirmation | Avatar, PIN, Keystore, adversarial profile access tests |
| Analytics | Date/module/baseline filters, session data, per-module accuracy, weak-key totals | Full daily/monthly/trend charts, records, achievement/adaptation histories, scalable queries |
| Vault | Tested AES-GCM/PBKDF2 primitive and bounded envelope | SAF integration, authenticated payload schema, staged restore, safe commit, selective/scheduled backup, Keystore |
| Performance/motion | No continuous idle ticker; no decorative animation; text scale setting | Three performance modes, interruptible motion, frame/memory/battery/device measurements |
| Error/recovery | Explicit errors, save retry, incomplete session restore, no fake completion | Fault injection and full interruption/device matrix |
| Accessibility | RTL/LTR, bundled font, labels, native controls, text scaling | TalkBack/Switch Access, large fonts, keyboard and contrast tests on device |
| QA and release | Core/static tests, Android test sources, unrun CI workflow | Build/lint/emulator/device suites, signed release, screenshots, APK |

## Exercise completion ledger

Implemented source paths (native build/device acceptance still pending): VISION / Focus Lock, Visual Search and Pattern Match; REFLEX / Quick Tap, Go / No-Go and Shape Reaction; READ / Reading Comprehension, Phrase Reading and RSVP Reader; TYPE / Key Precision, Word Sprint and Code Typing.

Required next:

* VISION: Peripheral Scan, Multi Target Tracking, Visual Memory, Target Identification, Spatial Attention.
* REFLEX: Color Reaction, Direction Match, Dual Focus, Sequence Memory, Inhibition Test, Multi-Stimulus Reaction.
* READ: Word Flash, Guided Reading, Visual Scanning, Peripheral Reading, Recall Challenge, Focus Reading.
* TYPE: Sentence Flow, Timed Challenge, Weak Keys, Rhythm Training, Custom Text.

Do not show a runnable button for one of these exercises until its engine and input handling exist. Do not mark a whole system complete when only a subset of its exercises passes core tests.
