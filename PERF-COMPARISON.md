# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 6 runs
- redesign: 5 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.6.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 521 | 511 | -1.9% worse |
| mean frame (ms) | 34.5 | 30 | -13.0% inside the spread |
| p50 frame (ms) | 36 | 20 | -44.4% inside the spread |
| p90 frame (ms) | 37.5 | 37 | -1.3% inside the spread |
| p95 frame (ms) | 50 | 50 | +0.0% inside the spread |
| p99 frame (ms) | 136.5 | 150 | +9.9% inside the spread |
| worst frame (ms) | 150 | 200 | +33.3% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 36 | 20 | -44.4% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 50 | 33 | -34.0% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 36 | +0.0% inside the spread |
| scroll | scroll · p95 (ms) | 51 | 150 | +194.1% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 36 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 37.5 | 40 | +6.7% inside the spread |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 20 | 20 | +0.0% inside the spread |
| navigate | navigate · p95 (ms) | 36.5 | 36 | -1.4% inside the spread |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7325 | 7409 | +1.1% inside the spread |
| CPU load (%) | 80.5 | 81 | +0.6% inside the spread |
| PSS at the end (kB) | 170726.5 | 168857 | -1.1% inside the spread |
| PSS growth (kB) | 2153 | 196 | -90.9% inside the spread |
| Java heap in use (kB) | 8819 | 7721 | -12.5% inside the spread |
| native heap (kB) | 19254 | 18642 | -3.2% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 34–36 | 20–36 |
| p95 frame (ms) | 50–52 | 50–50 |
| worst frame (ms) | 133–197 | 151–234 |
| CPU load (%) | 71–83 | 81–82 |
| PSS at the end (kB) | 168049–172193 | 166878–171108 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
