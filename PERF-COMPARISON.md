# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 5 runs
- redesign: 6 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.7.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 518 | 509 | -1.7% inside the spread |
| mean frame (ms) | 34 | 40.5 | +19.1% worse |
| p50 frame (ms) | 36 | 35.5 | -1.4% inside the spread |
| p90 frame (ms) | 38 | 50 | +31.6% inside the spread |
| p95 frame (ms) | 50 | 51.5 | +3.0% inside the spread |
| p99 frame (ms) | 133 | 150 | +12.8% inside the spread |
| worst frame (ms) | 150 | 169 | +12.7% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 36 | 35 | -2.8% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 49 | 50 | +2.0% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 36 | 43.5 | +20.8% inside the spread |
| scroll | scroll · p95 (ms) | 52 | 145.5 | +179.8% inside the spread |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 20 | 38.5 | +92.5% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 42 | 50 | +19.0% inside the spread |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 20 | 33.5 | +67.5% worse |
| navigate | navigate · p95 (ms) | 31 | 50 | +61.3% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 6949 | 8210.5 | +18.2% worse |
| CPU load (%) | 77 | 91 | +18.2% worse |
| PSS at the end (kB) | 170679 | 168943 | -1.0% inside the spread |
| PSS growth (kB) | -4870 | -8280 | +70.0% inside the spread |
| Java heap in use (kB) | 8866 | 7550 | -14.8% inside the spread |
| native heap (kB) | 19308 | 18794 | -2.7% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 20–36 | 33–47 |
| p95 frame (ms) | 36–67 | 50–53 |
| worst frame (ms) | 134–187 | 150–186 |
| CPU load (%) | 70–77 | 88–94 |
| PSS at the end (kB) | 169308–172289 | 168357–170346 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
