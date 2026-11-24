package com.neurox.domain

/** Durable checkpoints are append-only. Out-of-order snapshots are safe no-ops. */
object CheckpointProgress {
    fun accept(previous: Checkpoint, next: Checkpoint): Boolean {
        require(previous.config == next.config) { "Session configuration cannot change after start" }
        if (next.attempts.size < previous.attempts.size ||
            (next.attempts.size == previous.attempts.size && next.elapsedMs < previous.elapsedMs)) return false
        require(next.elapsedMs >= previous.elapsedMs) { "Active time cannot decrease when attempts are added" }
        require(next.attempts.take(previous.attempts.size) == previous.attempts) { "Recorded attempts cannot change" }
        return true
    }
}
