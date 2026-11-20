# MRSAD — performance comparison

Both rows were measured by the same benchmark, on the same emulator,
driven by the same script. The frame numbers come from the platform's
own `FrameMetrics`, and the CPU and memory numbers from the process
itself.

- baseline: `1.1.0-debug` · cpu 58% · pss 167890 kB
- redesign: `1.1.0-debug` · cpu 78% · pss 170762 kB

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 520 | 519 | -0.2% same |
| mean frame (ms) | 25 | 31 | +24.0% worse |
| p50 frame (ms) | 20 | 21 | +5.0% worse |
| p90 frame (ms) | 36 | 50 | +38.9% worse |
| p95 frame (ms) | 36 | 51 | +41.7% worse |
| p99 frame (ms) | 111 | 148 | +33.3% worse |
| worst frame (ms) | 136 | 300 | +120.6% worse |
| jank (%) | 100 | 100 | +0.0% same |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 19 | 20 | +5.3% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 20 | 40 | +100.0% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% same |
| scroll | scroll · p50 (ms) | 35 | 37 | +5.7% worse |
| scroll | scroll · p95 (ms) | 37 | 58 | +56.8% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% same |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 21 | -41.7% better |
| touch | touch (the object under the finger) · p95 (ms) | 37 | 145 | +291.9% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% same |
| navigate | navigate · p50 (ms) | 19 | 20 | +5.3% worse |
| navigate | navigate · p95 (ms) | 35 | 50 | +42.9% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% same |

> Every frame of **both** revisions is late against the frame
> budget of the emulator. A software-rendered x86_64 emulator does
> not draw in 16.67 ms, so the jank column sits at its ceiling for
> both and cannot separate them: the percentile columns carry the
> comparison, and the screenshot rows show what was on the screen.

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 5184 | 7231 | +39.5% worse |
| CPU load (%) | 58 | 78 | +34.5% worse |
| PSS at the end (kB) | 167890 | 170762 | +1.7% worse |
| Java heap in use (kB) | 6373 | 9703 | +52.3% worse |
| native heap (kB) | 17848 | 19012 | +6.5% worse |

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
