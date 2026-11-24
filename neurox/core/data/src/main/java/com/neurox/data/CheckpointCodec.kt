package com.neurox.data

import com.neurox.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Versioned checkpoint. No user-entered typing text is persisted. */
object CheckpointCodec {
    fun encode(value: Checkpoint): String = JSONObject().apply {
        put("version", 3)
        put("goal", value.config.targetAccuracyPercent)
        put("errorAllowance", value.config.errorAllowance)
        put("practice", value.config.practice)
        put("customId", value.config.customExerciseId ?: JSONObject.NULL)
        put("customName", value.config.customExerciseName ?: JSONObject.NULL)
        put("stimulusSeed", value.config.stimulusSeed)
        put("exercise", value.config.exercise.name)
        put("language", value.config.language.name)
        put("difficulty", value.config.difficulty)
        put("rounds", value.config.rounds)
        put("limit", value.config.responseLimitMs)
        put("baseline", value.config.baseline)
        put("elapsed", value.elapsedMs)
        put("completed", value.completed)
        put("attempts", JSONArray().apply {
            value.attempts.forEach { a -> put(JSONObject().apply {
                put("outcome", a.outcome.name)
                put("response", a.responseMs ?: JSONObject.NULL)
                put("correctChars", a.correctCharacters)
                put("incorrectChars", a.incorrectCharacters)
                put("words", a.wordsRead)
                put("readingMs", a.readingMs)
                put("weak", JSONObject(a.weakKeys))
            }) }
        })
    }.toString()

    fun decode(text: String): Checkpoint {
        require(text.length <= 128_000) { "Checkpoint is too large" }
        val root = JSONObject(text)
        val version = root.getInt("version")
        require(version in 1..3) { "Unsupported checkpoint version" }
        val config = TrainingConfig(Exercise.valueOf(root.getString("exercise")), Language.valueOf(root.getString("language")),
            root.getInt("difficulty"), root.getInt("rounds"), root.getLong("limit"), root.getBoolean("baseline"),
            if (version >= 2) root.getLong("stimulusSeed") else 0L,
            if (version >= 3) root.getInt("goal") else 80,
            if (version >= 3) root.getInt("errorAllowance") else 30,
            if (version >= 3) root.getBoolean("practice") else false,
            if (version >= 3 && !root.isNull("customId")) root.getString("customId") else null,
            if (version >= 3 && !root.isNull("customName")) root.getString("customName") else null)
        require(config.validate().isEmpty())
        val array = root.getJSONArray("attempts")
        require(array.length() <= config.rounds)
        val attempts = (0 until array.length()).map { i ->
            val a = array.getJSONObject(i)
            val weak = a.getJSONObject("weak")
            require(weak.length() <= 256)
            val keys = weak.keys().asSequence().associateWith { weak.getInt(it) }
            Attempt(Outcome.valueOf(a.getString("outcome")), if (a.isNull("response")) null else a.getLong("response"),
                a.getInt("correctChars"), a.getInt("incorrectChars"), a.getInt("words"), a.getLong("readingMs"), keys)
        }
        return Checkpoint(config, attempts, root.getLong("elapsed"), root.getBoolean("completed"))
    }
}
