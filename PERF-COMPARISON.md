# MRSAD — performance comparison

Both sides were measured by the same benchmark, on the same emulator,
driven by the same script, several times each. The frame numbers come
from the platform's own `FrameMetrics`, and the CPU and memory numbers
from the process itself.

- baseline: 3 runs
- redesign: 6 runs

The number of each column is the middle of the runs. The verdict says
whether the difference is larger than the spread of the runs; a
difference *inside the spread* is the machine talking, not the
application.

- versions: 1.1.0-debug, 1.3.1-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 524 | 451 | -13.9% worse |
| mean frame (ms) | 39 | 58 | +48.7% worse |
| p50 frame (ms) | 36 | 51 | +41.7% worse |
| p90 frame (ms) | 51 | 67 | +31.4% worse |
| p95 frame (ms) | 53 | 68.5 | +29.2% worse |
| p99 frame (ms) | 133 | 231.5 | +74.1% worse |
| worst frame (ms) | 163 | 267 | +63.8% worse |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 35 | 50 | +42.9% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 58 | 67 | +15.5% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 45 | 67 | +48.9% worse |
| scroll | scroll · p95 (ms) | 67 | 162.5 | +142.5% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 37 | 53 | +43.2% worse |
| touch | touch (the object under the finger) · p95 (ms) | 50 | 69 | +38.0% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 22 | 50 | +127.3% worse |
| navigate | navigate · p95 (ms) | 50 | 67 | +34.0% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 8021 | 9476 | +18.1% worse |
| CPU load (%) | 86 | 96 | +11.6% worse |
| PSS at the end (kB) | 169830 | 168906 | -0.5% inside the spread |
| PSS growth (kB) | -2473 | -857 | -65.3% inside the spread |
| Java heap in use (kB) | 8014 | 7211.5 | -10.0% inside the spread |
| native heap (kB) | 19011 | 18919.5 | -0.5% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 31–37 | 50–57 |
| p95 frame (ms) | 50–59 | 68–83 |
| worst frame (ms) | 151–167 | 262–268 |
| CPU load (%) | 81–89 | 94–98 |
| PSS at the end (kB) | 167891–171877 | 167727–171520 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
