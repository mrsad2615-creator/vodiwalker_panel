# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 4 runs
- redesign: 6 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.5.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 517.5 | 510 | -1.4% inside the spread |
| mean frame (ms) | 24 | 43 | +79.2% worse |
| p50 frame (ms) | 19 | 37.5 | +97.4% inside the spread |
| p90 frame (ms) | 35 | 51.5 | +47.1% worse |
| p95 frame (ms) | 35.5 | 60 | +69.0% worse |
| p99 frame (ms) | 76.5 | 101 | +32.0% worse |
| worst frame (ms) | 115 | 117 | +1.7% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 18.5 | 35.5 | +91.9% inside the spread |
| idle | idle (the object turns on its own) · p95 (ms) | 20 | 51.5 | +157.5% inside the spread |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 35 | 49 | +40.0% worse |
| scroll | scroll · p95 (ms) | 36 | 67 | +86.1% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 35 | 50 | +42.9% inside the spread |
| touch | touch (the object under the finger) · p95 (ms) | 36 | 67 | +86.1% inside the spread |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 18.5 | 40.5 | +118.9% inside the spread |
| navigate | navigate · p95 (ms) | 20 | 67 | +235.0% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 4334.5 | 8014.5 | +84.9% worse |
| CPU load (%) | 49 | 88 | +79.6% worse |
| PSS at the end (kB) | 169764.5 | 173225 | +2.0% inside the spread |
| PSS growth (kB) | -3801 | 5271 | -238.7% inside the spread |
| Java heap in use (kB) | 7853.5 | 9422 | +20.0% inside the spread |
| native heap (kB) | 18819 | 16273.5 | -13.5% better |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 19–35 | 35–49 |
| p95 frame (ms) | 35–36 | 52–67 |
| worst frame (ms) | 109–116 | 101–134 |
| CPU load (%) | 49–51 | 85–93 |
| PSS at the end (kB) | 169709–170832 | 169643–177048 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
