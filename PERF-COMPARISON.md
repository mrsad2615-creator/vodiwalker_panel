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
| frames | 522 | 509 | -2.5% worse |
| mean frame (ms) | 28 | 31 | +10.7% inside the spread |
| p50 frame (ms) | 20 | 20 | +0.0% inside the spread |
| p90 frame (ms) | 36 | 38 | +5.6% inside the spread |
| p95 frame (ms) | 37 | 50 | +35.1% worse |
| p99 frame (ms) | 132 | 151 | +14.4% worse |
| worst frame (ms) | 150 | 230 | +53.3% worse |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 19 | 20 | +5.3% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 22 | 33 | +50.0% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 36 | +0.0% inside the spread |
| scroll | scroll · p95 (ms) | 50 | 53 | +6.0% inside the spread |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 36 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 37 | 150 | +305.4% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 19 | 20 | +5.3% inside the spread |
| navigate | navigate · p95 (ms) | 34 | 36 | +5.9% inside the spread |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 6027 | 7715 | +28.0% worse |
| CPU load (%) | 66 | 83 | +25.8% worse |
| PSS at the end (kB) | 169269 | 167821 | -0.9% inside the spread |
| PSS growth (kB) | -939 | 108 | -111.5% inside the spread |
| Java heap in use (kB) | 8743 | 7694 | -12.0% inside the spread |
| native heap (kB) | 19019 | 18817 | -1.1% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 20–35 | 20–36 |
| p95 frame (ms) | 36–37 | 50–67 |
| worst frame (ms) | 136–180 | 201–367 |
| CPU load (%) | 64–69 | 77–86 |
| PSS at the end (kB) | 166430–172374 | 167140–168921 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
