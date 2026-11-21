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

- versions: 1.1.0-debug, 1.4.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 523 | 413.5 | -20.9% worse |
| mean frame (ms) | 34.5 | 62 | +79.7% worse |
| p50 frame (ms) | 35 | 63 | +80.0% worse |
| p90 frame (ms) | 48 | 68 | +41.7% worse |
| p95 frame (ms) | 50 | 84 | +68.0% worse |
| p99 frame (ms) | 150.5 | 193 | +28.2% inside the spread |
| worst frame (ms) | 167 | 235.5 | +41.0% worse |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 36 | 67 | +86.1% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 50 | 69 | +38.0% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 35.5 | 67 | +88.7% worse |
| scroll | scroll · p95 (ms) | 60.5 | 84 | +38.8% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 63 | +75.0% worse |
| touch | touch (the object under the finger) · p95 (ms) | 39 | 77.5 | +98.7% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 20 | 52.5 | +162.5% worse |
| navigate | navigate · p95 (ms) | 37 | 68 | +83.8% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7798.5 | 9218 | +18.2% worse |
| CPU load (%) | 84.5 | 97 | +14.8% worse |
| PSS at the end (kB) | 171056.5 | 169632 | -0.8% inside the spread |
| PSS growth (kB) | 2225 | -2880 | -229.4% inside the spread |
| Java heap in use (kB) | 9199 | 6842.5 | -25.6% inside the spread |
| native heap (kB) | 19461.5 | 19299 | -0.8% inside the spread |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 25–36 | 52–67 |
| p95 frame (ms) | 50–67 | 72–84 |
| worst frame (ms) | 136–233 | 234–250 |
| CPU load (%) | 80–88 | 95–100 |
| PSS at the end (kB) | 168269–171251 | 168700–172746 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
