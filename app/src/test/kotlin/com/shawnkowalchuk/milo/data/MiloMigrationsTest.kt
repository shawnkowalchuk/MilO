package com.shawnkowalchuk.milo.data

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migrations of the main database, checked against the schema files Room exports to
 * `app/schemas` (the tests run with the `app` folder as their working directory).
 *
 * No database is opened here: SQLite is not available to a plain JVM test. What is checked is
 * that the statements a step runs are exactly the difference between the two exported schemas,
 * and that they only ever add. That the step really runs on a database with rows in it was
 * shown on an emulator (docs/FINDINGS_LOG.md) and is a check of the device checklist.
 */
class MiloMigrationsTest {
    private val schemas = File("schemas/com.shawnkowalchuk.milo.data.MiloDatabase")

    /** One table of an exported schema: the SQL that adds each column, and that makes each index. */
    private class Table(val columns: Map<String, String>, val indices: Set<String>)

    private fun tables(version: Int): Map<String, Table> {
        val file = File(schemas, "$version.json")
        assertTrue("Room has not exported ${file.path}", file.isFile)
        val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database")
        val entities = database.jsonObject.getValue("entities").jsonArray.map { it.jsonObject }
        return entities.associate { entity ->
            val name = entity.text("tableName")
            val fields = entity.getValue("fields").jsonArray.map { it.jsonObject }
            val indices = entity["indices"]?.jsonArray.orEmpty().map { it.jsonObject }
            name to
                Table(
                    columns = fields.associate { it.text("columnName") to addColumnSql(name, it) },
                    indices =
                        indices
                            .map { it.text("createSql").replace("\${TABLE_NAME}", name) }
                            .map(::normalised)
                            .toSet(),
                )
        }
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    /** The statement that adds a column as the schema file declares it. */
    private fun addColumnSql(table: String, field: JsonObject): String {
        val notNull = if (field["notNull"]?.jsonPrimitive?.boolean == true) " NOT NULL" else ""
        val declared = field["defaultValue"]?.jsonPrimitive?.content
        val default = declared?.let { " DEFAULT $it" }.orEmpty()
        val column = "${field.text("columnName")} ${field.text("affinity")}"
        return normalised("ALTER TABLE $table ADD COLUMN $column$notNull$default")
    }

    /** Without the quoting and spacing that differ between Room's SQL and hand-written SQL. */
    private fun normalised(sql: String): String =
        sql.replace("`", "").replace(Regex("\\s+"), " ").trim()

    /** The statements [step] runs, in order. */
    private fun statementsOf(step: Migration): List<String> {
        val recorder = RecordingConnection()
        runTest { step.migrate(recorder) }
        return recorder.statements.map(::normalised)
    }

    @Test
    fun `there is one step for every version the database has had`() {
        val current = schemas.listFiles { file -> file.extension == "json" }.orEmpty().size

        assertEquals("One schema file per version, and no gaps", current, 3)
        // In order and without a gap, so a database that is two versions behind is taken
        // through both steps, one after the other.
        assertEquals(
            (1 until current).map { it to it + 1 },
            MILO_MIGRATIONS.map { it.startVersion to it.endVersion },
        )
    }

    @Test
    fun `every step makes exactly what its version has more than the one before`() {
        for (step in MILO_MIGRATIONS) {
            val name = "The step from ${step.startVersion} to ${step.endVersion}"
            val before = tables(step.startVersion)
            val after = tables(step.endVersion)
            assertEquals("$name adds or drops a table", before.keys, after.keys)
            val expected =
                after.flatMap { (table, now) ->
                    val old = before.getValue(table)
                    assertTrue(
                        "$name: a column of $table is gone or was changed",
                        now.columns.entries.containsAll(old.columns.entries),
                    )
                    assertTrue(
                        "$name: an index of $table is gone",
                        now.indices.containsAll(old.indices),
                    )
                    (now.columns.values - old.columns.values.toSet()) + (now.indices - old.indices)
                }

            assertEquals(name, expected.toSet(), statementsOf(step).toSet())
            assertEquals("$name runs a statement twice", expected.size, statementsOf(step).size)
        }
    }

    @Test
    fun `every step only adds`() {
        for (statement in MILO_MIGRATIONS.flatMap(::statementsOf)) {
            // No UPDATE, no DELETE, no DROP: a step never rewrites or removes a stored value.
            val adds =
                statement.startsWith("ALTER TABLE trips ADD COLUMN ") ||
                    statement.startsWith("CREATE INDEX IF NOT EXISTS ")
            assertTrue("Not an addition: $statement", adds)
        }
    }

    @Test
    fun `a new column that may not be empty has a default for the rows already stored`() {
        for (step in MILO_MIGRATIONS) {
            val added = statementsOf(step).filter { it.contains(" ADD COLUMN ") }

            assertEquals("Columns added from version ${step.startVersion}", 4, added.size)
            for (statement in added.filter { it.contains("NOT NULL") }) {
                // Without a default SQLite refuses the statement on a table that has rows.
                assertTrue(statement, statement.contains(" DEFAULT "))
            }
        }
    }

    @Test
    fun `the step from 2 to 3 leaves every stored trip unsorted, and sorts none itself`() {
        val statements = statementsOf(MIGRATION_2_3)

        // Empty on every stored row, which is how "not sorted yet" is stored. The catch-up at
        // the same process start sorts them; the step itself cannot read the schedule.
        assertTrue("ALTER TABLE trips ADD COLUMN category TEXT" in statements)
        // And no stored trip is set by hand, past the schedule or ignored.
        for (flag in listOf("categorySetByHand", "ranPastSchedule", "ignoredOutsideSchedule")) {
            assertTrue(
                flag,
                "ALTER TABLE trips ADD COLUMN $flag INTEGER NOT NULL DEFAULT 0" in statements,
            )
        }
        assertEquals(4, statements.size)
    }
}

/** Stands in for the database connection Room hands a migration: it only notes the SQL. */
private class RecordingConnection : SQLiteConnection {
    val statements = mutableListOf<String>()

    override fun prepare(sql: String): SQLiteStatement {
        statements += sql
        return NoRows
    }

    override fun close() = Unit
}

/** A statement that returns no rows, which is all a migration's own statements ever do. */
private object NoRows : SQLiteStatement {
    override fun step(): Boolean = false

    override fun reset() = Unit

    override fun clearBindings() = Unit

    override fun close() = Unit

    override fun getColumnCount(): Int = 0

    override fun bindBlob(index: Int, value: ByteArray) = notUsed()

    override fun bindDouble(index: Int, value: Double) = notUsed()

    override fun bindLong(index: Int, value: Long) = notUsed()

    override fun bindText(index: Int, value: String) = notUsed()

    override fun bindNull(index: Int) = notUsed()

    override fun getBlob(index: Int): ByteArray = notUsed()

    override fun getDouble(index: Int): Double = notUsed()

    override fun getLong(index: Int): Long = notUsed()

    override fun getText(index: Int): String = notUsed()

    override fun isNull(index: Int): Boolean = notUsed()

    override fun getColumnName(index: Int): String = notUsed()

    override fun getColumnType(index: Int): Int = notUsed()

    private fun notUsed(): Nothing = error("A migration statement binds and reads nothing")
}
