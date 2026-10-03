package com.shawnkowalchuk.milo.data.eventlog

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One line of the event log: the record that explains a missed trip after the fact. MilO has no
 * crash-reporting service, so this table is also where crashes and system kills end up.
 *
 * @param atMs wall-clock time of the event itself. A crash or a process kill is written at the
 * next start but dated when it happened, which is why the log is ordered by this and not by [id].
 * @param message one short line.
 * @param detail anything longer, such as a stack trace. Null when the message says it all.
 */
@Entity(tableName = "event_log", indices = [Index("atMs")])
data class EventLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val category: EventCategory,
    val message: String,
    val detail: String? = null,
)

/**
 * What an event is about. These are the kinds of evidence ADR-002 asks for from day one. Only
 * [PROCESS], [CRASH] and [ERROR] are written so far; the others are written by the trigger,
 * service and Android Auto code as it is built.
 *
 * Stored by name, so a constant can be added freely but never renamed without a migration.
 */
enum class EventCategory {
    /** A process start, and the reason the system recorded for the previous process ending. */
    PROCESS,

    /** An uncaught exception. */
    CRASH,

    /** A failure that was caught: the app carried on, but something did not get done. */
    ERROR,

    /** A trip trigger firing: its source, the state before and after, whether recording started. */
    TRIGGER,

    /** The trip service starting, stopping or failing to start. */
    SERVICE,

    /** The grace timer starting, being cancelled or running out. */
    GRACE,

    /** Android Auto connecting or disconnecting. */
    ANDROID_AUTO,

    /** A trip starting, finishing or being discarded. */
    TRIP,
}
