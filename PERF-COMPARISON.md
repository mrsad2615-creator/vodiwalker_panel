# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 6 runs
- redesign: 6 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.9.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 528.5 | 510 | -3.5% worse |
| mean frame (ms) | 28 | 42 | +50.0% worse |
| p50 frame (ms) | 20.5 | 37 | +80.5% inside the spread |
| p90 frame (ms) | 36.5 | 50 | +37.0% worse |
| p95 frame (ms) | 37 | 52 | +40.5% inside the spread |
| p99 frame (ms) | 133 | 143.5 | +7.9% inside the spread |
| worst frame (ms) | 150 | 167 | +11.3% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 20 | 45 | +125.0% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 35 | 50 | +42.9% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 50 | +38.9% worse |
| scroll | scroll · p95 (ms) | 50 | 133 | +166.0% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 47 | +30.6% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 37 | 50 | +35.1% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 19 | 33 | +73.7% worse |
| navigate | navigate · p95 (ms) | 36 | 50 | +38.9% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 6464.5 | 7843.5 | +21.3% worse |
| CPU load (%) | 70.5 | 87.5 | +24.1% worse |
| PSS at the end (kB) | 168887.5 | 167563 | -0.8% inside the spread |
| PSS growth (kB) | -2238 | -149 | -93.3% inside the spread |
| Java heap in use (kB) | 7330.5 | 6135 | -16.3% inside the spread |
| native heap (kB) | 18624 | 18736.5 | +0.6% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 20–36 | 33–45 |
| p95 frame (ms) | 37–50 | 50–66 |
| worst frame (ms) | 136–166 | 166–184 |
| CPU load (%) | 67–77 | 85–88 |
| PSS at the end (kB) | 167225–171491 | 167297–169596 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
