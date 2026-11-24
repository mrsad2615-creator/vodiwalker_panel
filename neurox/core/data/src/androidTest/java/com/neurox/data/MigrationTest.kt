package com.neurox.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neurox.domain.Exercise
import com.neurox.domain.ExerciseBlueprint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @Test fun additiveMigrationValidatesAndPreservesLegacyRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets.open("schema-v1.sql").bufferedReader().use { it.readText() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { legacy ->
            fixture.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n").split(';').filter { it.isNotBlank() }.forEach { legacy.execSQL(it) }
            legacy.execSQL("INSERT INTO profiles VALUES ('guest', 'Original guest', 1, 'FA', 'DARK', 4292131202, 4285946408, 1, 0, 1.0)")
            legacy.execSQL("INSERT INTO favorites VALUES ('guest', 'FOCUS_LOCK')")
            legacy.execSQL("INSERT INTO saved_themes VALUES ('theme-original', 'guest', 'Keep me', 4292131202, 4285946408)")
            legacy.version = 1
        }
        val migrated = Room.databaseBuilder(context, NeuroxDatabase::class.java, name).addMigrations(DatabaseMigrations.MIGRATION_1_2).build()
        try {
            // Opening the database forces Room to validate every migrated table and index.
            assertEquals(2, migrated.openHelper.writableDatabase.version)
            assertEquals("Original guest", migrated.dao().profile("guest")?.name)
            assertEquals("FOCUS_LOCK", migrated.dao().favorites("guest").first().single().exercise)
            assertEquals("Keep me", migrated.dao().themes("guest").first().single().name)
            val repository = NeuroxRepository(context, migrated)
            repository.saveCustomExercise("guest", ExerciseBlueprint("New exercise", Exercise.PATTERN_MATCH))
            assertEquals(1, repository.customExercises("guest").first().size)
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
