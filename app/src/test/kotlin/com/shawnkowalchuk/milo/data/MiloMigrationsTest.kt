package com.shawnkowalchuk.milo.data

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

    /** The statements [MIGRATION_1_2] runs, in order. */
    private fun statementsOfStepOneToTwo(): List<String> {
        val recorder = RecordingConnection()
        runTest { MIGRATION_1_2.migrate(recorder) }
        return recorder.statements.map(::normalised)
    }

    @Test
    fun `there is one step for every version the database has had`() {
        val current = schemas.listFiles { file -> file.extension == "json" }.orEmpty().size

        assertEquals("One schema file per version, and no gaps", current, 2)
        assertEquals(
            (1 until current).map { it to it + 1 },
            MILO_MIGRATIONS.map { it.startVersion to it.endVersion },
        )
    }

    @Test
    fun `the step from 1 to 2 makes exactly what version 2 has more than version 1`() {
        val before = tables(1)
        val after = tables(2)
        assertEquals("No table is added or dropped by this step", before.keys, after.keys)
        val expected =
            after.flatMap { (name, table) ->
                val old = before.getValue(name)
                assertTrue(
                    "A column of $name is gone or was changed",
                    table.columns.entries.containsAll(old.columns.entries),
                )
                assertTrue("An index of $name is gone", table.indices.containsAll(old.indices))
                (table.columns.values - old.columns.values.toSet()) + (table.indices - old.indices)
            }

        assertEquals(expected.toSet(), statementsOfStepOneToTwo().toSet())
        assertEquals("No statement runs twice", expected.size, statementsOfStepOneToTwo().size)
    }

    @Test
    fun `the step from 1 to 2 only adds`() {
        for (statement in statementsOfStepOneToTwo()) {
            val adds =
                statement.startsWith("ALTER TABLE trips ADD COLUMN ") ||
                    statement.startsWith("CREATE INDEX IF NOT EXISTS ")
            assertTrue("Not an addition: $statement", adds)
        }
    }

    @Test
    fun `a new column that may not be empty has a default for the rows already stored`() {
        val added = statementsOfStepOneToTwo().filter { it.contains(" ADD COLUMN ") }

        assertEquals(4, added.size)
        for (statement in added.filter { it.contains("NOT NULL") }) {
            // Without a default SQLite refuses the statement on a table that has rows.
            assertTrue(statement, statement.contains(" DEFAULT "))
        }
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
