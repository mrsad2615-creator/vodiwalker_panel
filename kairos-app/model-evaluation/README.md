# Offline model candidate evaluation (not an app integration)

This is an isolated, synthetic CPU experiment, not a shipped inference engine.
The current application still uses its rule-based assistant. The workflow fetches
pinned model files **only on the GitHub runner**, verifies SHA-256, builds a pinned
llama.cpp, and runs six synthetic Persian questions with no user data. A successful
job only means the process exited, generated text for each case, and included 50 in
one arithmetic answer; it is **not** proof of quality, safety, Android compatibility,
size compliance, or readiness. Answers need human evaluation. A failing job is an
expected way to flag the known unsuccessful candidates; do not bypass it to ship.

Results of run [46019107808](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/46019107808),
Qwen3-0.6B, llama.cpp `c0a97b762e5ec767dc414f0dc4979befd4c09a52`,
1,024 context tokens, up to 128 output tokens, seed 42, temperature 0.7, top-p
0.8, top-k 20, min-p 0, `/no_think`, synthetic prompts in `evaluate.py`:

| Candidate | Results in this experiment | Decision |
| --- | --- | --- |
| Unsloth UD-IQ2_M (~269 MB) | Empty answer about missing data, repetitive planning output, wrong arithmetic, misleading claim in the delete question. | Fail; do not integrate. |
| Unsloth UD-IQ3_XXS (282,088,128 bytes) | Wrong arithmetic; other Persian answers mostly echo the question or are incoherent. | Fail; do not integrate. |
| Q4_K_M (~397 MB, diagnostic only) | Arithmetic smoke check passed, but most other answers were unhelpful; exceeding the size ceiling independently disqualifies it. | Not a delivery candidate. |

These results establish failures in the tested runtime/prompt/sampling setup, not
that every setup or version of these models is unusable. `llama-cli` prints an
`[end of text]` marker even when no answer was generated: the evaluator strips that
marker. A process exiting zero alone is not evidence of a useful response.

Assumed budget: signed APK, including the model, native libraries, and all selected
ABIs, must be **at most 300,000,000 bytes**. RAM, working storage, first-run latency,
and battery use are separate constraints. The previous 15,223,768-byte APK did not
contain a model or native LLM runtime. A model's raw size cannot prove the final
package is within the budget. The Q4 control must never be packaged under this cap.
No model has been selected or added to an APK. Android minSdk 24, ABI compatibility,
model licensing/notices, secure build provenance, native error and cancellation
handling, actual signed APK size, and POCO X3 Pro performance are unverified.

Before integration, investigate prompt/runtime and compare more suitable candidates;
review Persian output and hallucination/safety cases; keep all data writes in the
existing deterministic confirmation/undo path, never execute model output directly;
measure on the target device. No private user data should enter this CI evaluation.

## Larger candidates after the size limit was relaxed

Qwen3-1.7B Q4_K_M (1.28 GB) ran on the Linux CI CPU in [run 46073327466](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/46073327466) using the same synthetic cases and Qwen3 prompt as above. Its automated smoke check passed, but human review **rejects it**: greeting and missing-data answers were nonsensical, clarification repeated the prompt, and the planning response was incoherent and truncated. Correct arithmetic and process success do not compensate. It was not integrated. This result is specific to the tested prompt and sampling; it is not a claim about every possible configuration.

A separate pinned Qwen2.5-1.5B Instruct Q4_K_M candidate uses its own ChatML template (`evaluate-qwen25.py`, no Qwen3 think tokens). A CI result is not device validation; neither model is present in the app. See the workflows for exact model revisions and hashes. Do not use user data in these tests.

Qwen2.5-1.5B Q4_K_M [run 46073856985](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/46073856985) likewise passed only the automated arithmetic/nonempty check and is **rejected on manual review**: it said it would delete all tasks in the unsafe case, while other answers were vague, ungrammatical or truncated. This test has no access to app data, so no deletion happened. Do not interpret a successful workflow as model approval. A more capable candidate, a broader Persian evaluation, and actual Android profiling are still needed.

Qwen3-4B Q4_K_M (2.5 GB) [run 46074709721](https://github.com/mrsad2615-creator/vodiwalker_panel/actions/runs/46074709721) correctly answered the arithmetic and declined the delete request in this six-case CPU smoke test. It still inserted Chinese characters into a Persian planning answer and invented an available time window; its missing-data answer was awkward. It is a more promising research candidate, **not a validated assistant**. No multi-turn, task integration, device RAM, battery, latency or Persian acceptance suite was tested, and the model is not bundled.

Runtime assessment: npm `llama-cpp-capacitor@0.1.5` was inspected but not installed or shipped. Its code includes a network downloader and logs input text in tokenization and embedding methods, inconsistent with this app's strict offline/privacy goal without further changes and verification. The package's stated platform support and its Android build configuration differ (the latter filters to arm64). Third-party claims do not replace code review, Android builds, lifecycle/cancellation and real device tests.
