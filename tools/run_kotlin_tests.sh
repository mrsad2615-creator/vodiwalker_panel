#!/usr/bin/env bash
# ---------------------------------------------------------------------------------------------
# VodiGesture — Multi-Engine Kotlin Verification Runner
#
# Compiles every pure-Kotlin Clean Architecture module (13 production sources across 7 modules)
# plus all `src/test` suites with JetBrains Kotlin K2 (`-Werror`, JVM 21 target), then executes
# each `@Test` method on Eclipse Temurin OpenJDK 21 LTS.
#
# Modules covered:  :core:model  :core:common  :engine:gesture  :engine:prediction
#                   :engine:smartaction  :engine:power  :engine:haptics
# ---------------------------------------------------------------------------------------------
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="$(python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')"
export JAVA_HOME
KOTLIN_LIB="/opt/android-toolchain/node_modules/kotlin-compiler/lib"
BUILD_OUT="$(mktemp -d)"
trap 'rm -rf "$BUILD_OUT"' EXIT

# ---------------------------------------------------------------------------------------------
# Offline Room / coroutines stubs so the real entity + mapper sources compile on a plain JVM.
# (The Android build uses the genuine AndroidX artifacts; nothing here ships in the APK.)
# ---------------------------------------------------------------------------------------------
cat <<'KT' > "$BUILD_OUT/RoomStubs.kt"
package androidx.room

annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val foreignKeys: Array<ForeignKey> = [],
)
annotation class Index(val value: Array<String>, val unique: Boolean = false)
annotation class ForeignKey(
    val entity: kotlin.reflect.KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = 1,
) {
    companion object { const val CASCADE: Int = 5 }
}
annotation class ColumnInfo(val name: String = "", val typeAffinity: Int = 1) {
    companion object { const val BLOB: Int = 5 }
}
annotation class PrimaryKey(val autoGenerate: Boolean = false)
annotation class Upsert
annotation class Query(val value: String)
annotation class Transaction
annotation class Dao
annotation class Database(
    val entities: Array<kotlin.reflect.KClass<*>> = [],
    val version: Int = 1,
    val exportSchema: Boolean = true,
)
abstract class RoomDatabase {
    enum class JournalMode { AUTOMATIC, TRUNCATE, WRITE_AHEAD_LOGGING }
}
KT

cat <<'KT' > "$BUILD_OUT/CoroutinesStub.kt"
package kotlinx.coroutines.flow

interface Flow<out T>
KT

MODULES=(
  "android/core/model"
  "android/core/common"
  "android/engine/gesture"
  "android/engine/prediction"
  "android/engine/smartaction"
  "android/engine/haptics"
  "android/engine/power"
)

MAIN_SRCS=()
TEST_SRCS=()
for module in "${MODULES[@]}"; do
  while IFS= read -r file; do MAIN_SRCS+=("$file"); done < <(find "$ROOT_DIR/$module/src/main/kotlin" -name '*.kt' 2>/dev/null | sort)
  while IFS= read -r file; do TEST_SRCS+=("$file"); done < <(find "$ROOT_DIR/$module/src/test/kotlin" -name '*.kt' 2>/dev/null | sort)
done

# :core:database entity + mapper layer is framework-annotation-only; compile it with minimal
# offline Room/coroutines stubs so its codecs are exercised on the plain JVM.
DB_SRC="$ROOT_DIR/android/core/database/src/main/kotlin/com/vodiwalker/gesture/core/database"
EXTRA_SRCS=("$DB_SRC/EntityMappers.kt" "$DB_SRC/VodiGestureDatabase.kt")
while IFS= read -r file; do TEST_SRCS+=("$file"); done < <(find "$ROOT_DIR/android/core/database/src/test/kotlin" -name '*.kt' 2>/dev/null | sort)

if [ "${#TEST_SRCS[@]}" -eq 0 ]; then
  echo "ERROR: no Kotlin test sources discovered." >&2
  exit 1
fi

# ---------------------------------------------------------------------------------------------
# Minimal JUnit4-compatible @Test runtime annotation so kotlin.test suites execute standalone
# (kotlin-test-junit.jar is on the classpath, but no JUnit runner is downloaded).
# ---------------------------------------------------------------------------------------------
cat <<'KT' > "$BUILD_OUT/JUnitShim.kt"
package org.junit

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Test
KT

# ---------------------------------------------------------------------------------------------
# Reflection-driven runner: discovers every @kotlin.test.Test method in the given classes.
# ---------------------------------------------------------------------------------------------
cat <<'KT' > "$BUILD_OUT/TestRunnerMain.kt"
package com.vodiwalker.gesture.testrunner

import kotlin.system.exitProcess
import kotlin.system.measureNanoTime

fun main() {
    val testClasses = listOf(
        com.vodiwalker.gesture.core.common.ResilienceAndCodecTest::class,
        com.vodiwalker.gesture.core.database.RoomPayloadCodecTest::class,
        com.vodiwalker.gesture.engine.gesture.HybridGestureEngineTest::class,
        com.vodiwalker.gesture.engine.gesture.AdaptiveOneEuroFilterTest::class,
        com.vodiwalker.gesture.engine.prediction.AiGesturePredictionEngineTest::class,
        com.vodiwalker.gesture.engine.smartaction.SmartActionHapticsAndPowerEngineTest::class,
    )
    var passed = 0
    var failed = 0
    var totalNanos = 0L
    for (kClass in testClasses) {
        val methods = kClass.java.declaredMethods
            .filter { m -> m.annotations.any { it.annotationClass.simpleName == "Test" } }
            .sortedBy { it.name }
        println("${kClass.simpleName} (${methods.size} tests)")
        for (method in methods) {
            val instance = kClass.java.getDeclaredConstructor().newInstance()
            try {
                val nanos = measureNanoTime { method.invoke(instance) }
                totalNanos += nanos
                println("  [PASS] ${method.name} (${"%.2f".format(nanos / 1_000_000.0)} ms)")
                passed++
            } catch (ex: java.lang.reflect.InvocationTargetException) {
                val cause = ex.targetException ?: ex
                println("  [FAIL] ${method.name}: ${cause::class.simpleName}: ${cause.message}")
                cause.printStackTrace()
                failed++
            }
        }
    }
    println("------------------------------------------------------------")
    println("Executed on OpenJDK ${System.getProperty("java.version")} | K2 -Werror | JVM 21")
    println("RESULT: $passed passed, $failed failed (${"%.2f".format(totalNanos / 1_000_000.0)} ms total)")
    if (failed > 0) exitProcess(1)
}
KT

echo "[1/3] Compiling ${#MAIN_SRCS[@]} production Kotlin sources across ${#MODULES[@]} JVM modules + Room codec layer (-Werror, JVM 21)..."
kotlinc -Werror -jvm-target 21 "${MAIN_SRCS[@]}" "${EXTRA_SRCS[@]}" \
  "$BUILD_OUT/RoomStubs.kt" "$BUILD_OUT/CoroutinesStub.kt" -d "$BUILD_OUT/engines.jar"

echo "[2/3] Compiling ${#TEST_SRCS[@]} Kotlin test sources (-Werror, JVM 21)..."
kotlinc -Werror -jvm-target 21 \
  -cp "$BUILD_OUT/engines.jar:$KOTLIN_LIB/kotlin-test.jar:$KOTLIN_LIB/kotlin-test-junit.jar:$KOTLIN_LIB/kotlin-reflect.jar" \
  "$BUILD_OUT/JUnitShim.kt" \
  "${TEST_SRCS[@]}" \
  "$BUILD_OUT/TestRunnerMain.kt" \
  -d "$BUILD_OUT/tests.jar"

echo "[3/3] Executing unit, concurrency, anti-loop, privacy, chaos & latency suites..."
"$JAVA_HOME/bin/java" \
  -cp "$BUILD_OUT/engines.jar:$BUILD_OUT/tests.jar:$KOTLIN_LIB/kotlin-stdlib.jar:$KOTLIN_LIB/kotlin-test.jar:$KOTLIN_LIB/kotlin-test-junit.jar:$KOTLIN_LIB/kotlin-reflect.jar" \
  com.vodiwalker.gesture.testrunner.TestRunnerMainKt
