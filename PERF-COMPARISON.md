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

- versions: 1.1.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 519 | 512 | -1.3% inside the spread |
| mean frame (ms) | 38 | 37 | -2.6% inside the spread |
| p50 frame (ms) | 36 | 35 | -2.8% inside the spread |
| p90 frame (ms) | 50 | 50 | +0.0% inside the spread |
| p95 frame (ms) | 51 | 52 | +2.0% inside the spread |
| p99 frame (ms) | 151 | 127 | -15.9% better |
| worst frame (ms) | 184 | 201 | +9.2% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 37 | 36 | -2.7% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 51 | 51 | +0.0% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 37 | 37 | +0.0% inside the spread |
| scroll | scroll · p95 (ms) | 67 | 52 | -22.4% inside the spread |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 36 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 48 | 100 | +108.3% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 21 | 32 | +52.4% inside the spread |
| navigate | navigate · p95 (ms) | 50 | 50 | +0.0% inside the spread |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7750 | 8194 | +5.7% worse |
| CPU load (%) | 85 | 89 | +4.7% worse |
| PSS at the end (kB) | 169497 | 171209 | +1.0% inside the spread |
| PSS growth (kB) | -6990 | -3283 | -53.0% inside the spread |
| Java heap in use (kB) | 8937 | 9973 | +11.6% inside the spread |
| native heap (kB) | 19098 | 20386 | +6.7% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 22–36 | 35–46 |
| p95 frame (ms) | 50–55 | 51–67 |
| worst frame (ms) | 167–200 | 167–267 |
| CPU load (%) | 79–87 | 88–91 |
| PSS at the end (kB) | 167873–172995 | 168356–172853 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
