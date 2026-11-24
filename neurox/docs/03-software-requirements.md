# Software requirements — current contracts

| ID | Requirement | Verification |
|---|---|---|
| S-001 | Reject difficulty outside 1–5 and rounds outside 1–30. Keep comprehension difficulty at 1. Phrase Reading uses levels 1–5 for presentation pace, not text complexity. | Core tests |
| S-002 | Use monotonic time for active duration and response latency. | Fake-clock tests; device latency tests pending |
| S-003 | Record early reaction taps without a response-time value. | Core tests |
| S-004 | Record timed-out targets as missed responses. Do not apply an expired tap to a new round. | Core tests |
| S-005 | Exclude paused time. Restart an unfinished round after resume. | Core tests; lifecycle tests pending |
| S-006 | Use NFC-normalized Unicode code points for typing comparison. Do not reverse Persian text. | Core tests |
| S-007 | Calculate typing WPM as correct code points / 5 / active typing minutes. Count missing and extra characters as errors. | Core tests |
| S-008 | Calculate reading WPM from words / reading minutes. Exclude question time. Show comprehension separately. | Core tests |
| S-009 | Require full completion, at least one timed response and at least 1000 ms active time for session validity. | Core tests |
| S-010 | Give no XP to baselines, aborted or invalid sessions. | Core tests; Room tests pending |
| S-011 | Change suggested difficulty by at most one level. Require five accurate, stable sessions for an increase. Require three repeated low-accuracy sessions for a decrease. | Core tests |
| S-012 | Scope every history, favorite and theme query to a profile ID. | Static checks; integration tests pending |
| S-013 | Commit the final row before showing a saved-result screen. Make completion idempotent. | Source inspection; integration tests pending |
| S-014 | Reject unsupported or oversized backup envelopes before key derivation where possible. Authenticate the header and payload. | Crypto tests |
| S-015 | Do not request network permission or store entered typing text. | Static/core checks; device privacy tests pending |
| S-016 | Adjust custom accent contrast to at least 4.5:1 against the card surface. | Color math tests; complete UI contrast audit pending |

| S-017 | Generate exactly one upright T target and 3–15 distractors. Recreate the board from the saved seed. | Core tests |
| S-018 | Use a shuffled GO/NO-GO plan with at least one GO; include a NO-GO when rounds >= 2. Record correct withholding with no reaction latency. | Core tests |
| S-019 | Do not let a waiting tap activate its own GO. Reject callbacks from old input revisions. | Core tests |
| S-020 | Show every three-word phrase for its minimum display interval. Do not skip phrases after a delayed tick. Calculate WPM from actual display time. | Core tests; physical presentation timing pending |
| S-021 | Complete a sprint round only after every word is submitted. Limit each word input to 64 UTF-16 code units. Aggregate correct characters, errors and weak keys. | Core tests |
| S-022 | Read checkpoint versions 1 and 2. Write version 2 with the stimulus seed. | Android codec tests supplied, not run |

The response clock starts when the engine publishes a stimulus. It is not a hardware timestamp for first pixel emission. Screen refresh, scheduling, touch hardware and assistive technology affect observed values. Do not describe the results as laboratory measurements or diagnoses.

The current typing method is positional comparison, not edit-distance alignment. An inserted or deleted character can cause later positions to count as errors. Explain this method in the user guide. Improve alignment only with versioned metric definitions and regression tests.
