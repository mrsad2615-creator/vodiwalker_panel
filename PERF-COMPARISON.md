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

- versions: 1.1.0-debug, 1.2.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 522.5 | 461 | -11.8% worse |
| mean frame (ms) | 29 | 54.5 | +87.9% worse |
| p50 frame (ms) | 20 | 50 | +150.0% worse |
| p90 frame (ms) | 36.5 | 67 | +83.6% worse |
| p95 frame (ms) | 44.5 | 69 | +55.1% worse |
| p99 frame (ms) | 127 | 150 | +18.1% worse |
| worst frame (ms) | 159.5 | 202 | +26.6% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 20 | 50 | +150.0% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 31.5 | 67 | +112.7% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 50 | +38.9% worse |
| scroll | scroll · p95 (ms) | 50 | 69 | +38.0% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 51 | +41.7% worse |
| touch | touch (the object under the finger) · p95 (ms) | 37 | 134 | +262.2% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 19 | 50.5 | +165.8% worse |
| navigate | navigate · p95 (ms) | 35.5 | 68 | +91.5% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 6680 | 8814.5 | +32.0% worse |
| CPU load (%) | 74 | 95 | +28.4% worse |
| PSS at the end (kB) | 170357 | 169241 | -0.7% inside the spread |
| PSS growth (kB) | -4049.5 | -9973 | +146.3% inside the spread |
| Java heap in use (kB) | 8707 | 7280.5 | -16.4% inside the spread |
| native heap (kB) | 19189 | 19107 | -0.4% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 19–21 | 50–51 |
| p95 frame (ms) | 33–50 | 68–83 |
| worst frame (ms) | 150–201 | 201–335 |
| CPU load (%) | 64–75 | 92–98 |
| PSS at the end (kB) | 167610–172437 | 167955–170502 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
