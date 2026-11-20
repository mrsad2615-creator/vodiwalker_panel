# MRSAD — performance comparison

Both rows were measured by the same benchmark, on the same emulator,
driven by the same script. The frame numbers come from the platform's
own `FrameMetrics`, and the CPU and memory numbers from the process
itself.

- baseline: `1.1.0-debug` · cpu 79% · pss 171304 kB
- redesign: `1.1.0-debug` · cpu 72% · pss 170283 kB

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 521 | 512 | -1.7% worse |
| mean frame (ms) | 27 | 25 | -7.4% better |
| p50 frame (ms) | 20 | 20 | +0.0% same |
| p90 frame (ms) | 36 | 36 | +0.0% same |
| p95 frame (ms) | 39 | 36 | -7.7% better |
| p99 frame (ms) | 133 | 100 | -24.8% better |
| worst frame (ms) | 150 | 284 | +89.3% worse |
| jank (%) | 100 | 100 | +0.0% same |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 20 | 19 | -5.0% better |
| idle | idle (the object turns on its own) · p95 (ms) | 39 | 26 | -33.3% better |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% same |
| scroll | scroll · p50 (ms) | 22 | 35 | +59.1% worse |
| scroll | scroll · p95 (ms) | 45 | 38 | -15.6% better |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% same |
| touch | touch (the object under the finger) · p50 (ms) | 20 | 19 | -5.0% better |
| touch | touch (the object under the finger) · p95 (ms) | 38 | 97 | +155.3% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% same |
| navigate | navigate · p50 (ms) | 20 | 19 | -5.0% better |
| navigate | navigate · p95 (ms) | 37 | 21 | -43.2% better |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% same |

> Every frame of **both** revisions is late against the frame
> budget of the emulator. A software-rendered x86_64 emulator does
> not draw in 16.67 ms, so the jank column sits at its ceiling for
> both and cannot separate them: the percentile columns carry the
> comparison, and the screenshot rows show what was on the screen.

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7235 | 6533 | -9.7% better |
| CPU load (%) | 79 | 72 | -8.9% better |
| PSS at the end (kB) | 171304 | 170283 | -0.6% same |
| Java heap in use (kB) | 9092 | 8961 | -1.4% better |
| native heap (kB) | 19555 | 19424 | -0.7% same |

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
