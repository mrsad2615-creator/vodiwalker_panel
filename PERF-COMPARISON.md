# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 5 runs
- redesign: 5 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.2.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 522 | 444 | -14.9% worse |
| mean frame (ms) | 33 | 57 | +72.7% worse |
| p50 frame (ms) | 34 | 50 | +47.1% worse |
| p90 frame (ms) | 50 | 67 | +34.0% worse |
| p95 frame (ms) | 50 | 83 | +66.0% worse |
| p99 frame (ms) | 150 | 167 | +11.3% worse |
| worst frame (ms) | 167 | 286 | +71.3% worse |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 31 | 50 | +61.3% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 50 | 67 | +34.0% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 52 | +44.4% inside the spread |
| scroll | scroll · p95 (ms) | 67 | 68 | +1.5% inside the spread |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 55 | +52.8% worse |
| touch | touch (the object under the finger) · p95 (ms) | 39 | 167 | +328.2% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 20 | 50 | +150.0% worse |
| navigate | navigate · p95 (ms) | 50 | 69 | +38.0% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7434 | 8860 | +19.2% worse |
| CPU load (%) | 82 | 94 | +14.6% worse |
| PSS at the end (kB) | 170150 | 170599 | +0.3% inside the spread |
| PSS growth (kB) | 2264 | 828 | -63.4% inside the spread |
| Java heap in use (kB) | 9296 | 9801 | +5.4% inside the spread |
| native heap (kB) | 19540 | 20476 | +4.8% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 21–36 | 50–51 |
| p95 frame (ms) | 50–52 | 67–85 |
| worst frame (ms) | 153–201 | 250–367 |
| CPU load (%) | 76–84 | 93–96 |
| PSS at the end (kB) | 169382–171593 | 167690–172274 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
