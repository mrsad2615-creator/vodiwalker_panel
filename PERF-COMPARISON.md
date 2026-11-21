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

- versions: 1.1.0-debug, 1.7.0-debug

## Frames, whole session

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| frames | 523 | 452 | -13.6% worse |
| mean frame (ms) | 29.5 | 53 | +79.7% worse |
| p50 frame (ms) | 21 | 50 | +138.1% worse |
| p90 frame (ms) | 37 | 67 | +81.1% worse |
| p95 frame (ms) | 50 | 67.5 | +35.0% worse |
| p99 frame (ms) | 129.5 | 150 | +15.8% worse |
| worst frame (ms) | 150 | 183.5 | +22.3% inside the spread |
| jank (%) | 100 | 100 | +0.0% inside the spread |

## Frames, per phase

| phase | metric | baseline | redesign | change |
| --- | --- | --- | --- | --- |
| idle | idle (the object turns on its own) · p50 (ms) | 23 | 50 | +117.4% worse |
| idle | idle (the object turns on its own) · p95 (ms) | 47.5 | 67 | +41.1% worse |
| idle | idle (the object turns on its own) · jank (%) | 100 | 100 | +0.0% inside the spread |
| scroll | scroll · p50 (ms) | 27 | 51 | +88.9% worse |
| scroll | scroll · p95 (ms) | 50 | 133.5 | +167.0% worse |
| scroll | scroll · jank (%) | 100 | 100 | +0.0% inside the spread |
| touch | touch (the object under the finger) · p50 (ms) | 36 | 50 | +38.9% worse |
| touch | touch (the object under the finger) · p95 (ms) | 37.5 | 67 | +78.7% worse |
| touch | touch (the object under the finger) · jank (%) | 100 | 100 | +0.0% inside the spread |
| navigate | navigate · p50 (ms) | 20 | 50 | +150.0% worse |
| navigate | navigate · p95 (ms) | 36 | 67 | +86.1% worse |
| navigate | navigate · jank (%) | 100 | 100 | +0.0% inside the spread |

## The process

| metric | baseline | redesign | change |
| --- | --- | --- | --- |
| CPU time (ms) | 7119.5 | 8991 | +26.3% worse |
| CPU load (%) | 78.5 | 98 | +24.8% worse |
| PSS at the end (kB) | 170870 | 171859 | +0.6% same |
| PSS growth (kB) | 495 | -592 | -219.6% inside the spread |
| Java heap in use (kB) | 8829 | 9678 | +9.6% worse |
| native heap (kB) | 19239 | 19774 | +2.8% worse |

## The spread of the runs

The lowest and the highest value of every run of each side. A
difference that is smaller than this is not a difference.

| metric | baseline | redesign |
| --- | --- | --- |
| p50 frame (ms) | 20–36 | 50–50 |
| p95 frame (ms) | 33–50 | 67–83 |
| worst frame (ms) | 136–186 | 167–233 |
| CPU load (%) | 69–79 | 96–98 |
| PSS at the end (kB) | 167777–171143 | 171287–172481 |

## The frame budget

A frame longer than 17 ms missed its deadline on the 59 Hz display of the emulator and is counted as jank.
