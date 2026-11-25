// NEUROX offline compile stubs - Room annotations, RoomDatabase and the builder.
//
// The annotations are real enough to catch a missing/extra parameter. The DAO SQL is
// NOT checked here and cannot be without the Room compiler; that limitation is stated
// in docs/TEST_STATUS.md.
package androidx.room

import android.content.Context

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Entity(
    val tableName: String = "",
    val indices: Array<Index> = [],
    val foreignKeys: Array<ForeignKey> = [],
    val primaryKeys: Array<String> = [],
    val inheritSuperIndices: Boolean = false,
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Index(val value: Array<String>, val unique: Boolean = false)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ForeignKey(
    val entity: kotlin.reflect.KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = NO_ACTION,
    val onUpdate: Int = NO_ACTION,
    val deferred: Boolean = false,
) {
    companion object {
        const val NO_ACTION = 1
        const val RESTRICT = 2
        const val SET_NULL = 3
        const val SET_DEFAULT = 4
        const val CASCADE = 5
    }
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class ColumnInfo(val name: String = "", val defaultValue: String = "undefined")

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class Embedded(val prefix: String = "")

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class TypeConverters(vararg val value: kotlin.reflect.KClass<*>)

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.BINARY)
annotation class TypeConverter

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Ignore

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Delete

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Upsert

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Dao

object OnConflictStrategy {
    const val REPLACE = 1
    const val ROLLBACK = 2
    const val ABORT = 3
    const val FAIL = 4
    const val IGNORE = 5
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Database(
    val entities: Array<kotlin.reflect.KClass<*>> = [],
    val version: Int = 1,
    val exportSchema: Boolean = true,
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Transaction

/** JournalMode is an enum in the real library. */
enum class JournalMode { AUTOMATIC, TRUNCATE, WRITE_AHEAD_LOGGING }

abstract class RoomDatabase {

    abstract fun <T : Any> dao(daoClass: Class<T>): T
    abstract fun clearAllTables()
    open fun close() {}

    /**
     * The builder. Only the methods this app calls are declared; the generic parameter
     * mirrors the real one so `RoomDatabase.Builder<NeuroxDatabase>` compiles.
     */
    abstract class Builder<T : RoomDatabase> {
        abstract fun addMigrations(vararg migrations: androidx.room.migration.Migration): Builder<T>
        abstract fun addCallback(callback: Callback): Builder<T>
        abstract fun setJournalMode(mode: JournalMode): Builder<T>
        abstract fun fallbackToDestructiveMigration(): Builder<T>
        abstract fun allowMainThreadQueries(): Builder<T>
        abstract fun build(): T
    }

    /** Callbacks receive a database handle; onOpen runs every time the file is opened. */
    abstract class Callback {
        open fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
        open fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
        open fun onDestructiveMigration(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
    }
}

object Room {
    @JvmStatic
    fun <T : RoomDatabase> databaseBuilder(context: Context, klass: Class<T>, name: String): RoomDatabase.Builder<T> =
        error("stub")

    @JvmStatic
    fun <T : RoomDatabase> inMemoryDatabaseBuilder(context: Context, klass: Class<T>): RoomDatabase.Builder<T> =
        error("stub")
}
