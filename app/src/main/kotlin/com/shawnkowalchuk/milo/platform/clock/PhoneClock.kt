package com.shawnkowalchuk.milo.platform.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.shawnkowalchuk.milo.core.clock.ClockAnchor
import com.shawnkowalchuk.milo.core.clock.TrustedClock
import com.shawnkowalchuk.milo.data.clock.ClockAnchorStore
import com.shawnkowalchuk.milo.data.clock.buildClockAnchorStore
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.crash.buildCrashFileStore
import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// The Android side of MilO's clock (ADR-005): the two clocks of the phone, the number of this
// boot, and the file the anchor is kept in. The rule itself is `core/clock/TrustedClock`.
//
// THIS FILE IS THE ONE PLACE IN MILO THAT READS THE PHONE'S WALL CLOCK. Everything else is
// handed `TrustedClock.now` as its `clock`, and a unit test reads the source tree to keep it so
// (`WallClockReadersTest`).

private val processClockLock = Any()
private var processClock: TrustedClock? = null

/**
 * MilO's clock: one for the whole process, made the first time it is asked for.
 *
 * `MiloApplication` asks first, before anything else is built, so that the anchor is read
 * before the first reading of the time. Android's backup agent asks for itself: its process has
 * no `MiloApplication`.
 */
fun miloClock(context: Context): TrustedClock = synchronized(processClockLock) {
    processClock ?: buildMiloClock(context.applicationContext).also { processClock = it }
}

private fun buildMiloClock(appContext: Context): TrustedClock {
    val anchors = buildClockAnchorStore(appContext)
    // One thread, so that anchors reach the file in the order they were made. A daemon: a
    // write that is waiting must not keep a process alive.
    val writer =
        Executors.newSingleThreadExecutor { work ->
            Thread(work, "milo-clock-anchor").apply { isDaemon = true }
        }
    val storing = AnchorStoring(anchors, buildCrashFileStore(appContext), writer)
    return TrustedClock(
        phoneNow = System::currentTimeMillis,
        elapsedNow = SystemClock::elapsedRealtime,
        bootCount = bootCountOf(appContext),
        stored = anchors.read(),
        save = storing::store,
    )
}

/**
 * The number Android counts the phone's boots with, or null if this phone does not keep it.
 * The clock that runs from boot starts at nought with each boot, so an anchor is only good for
 * the boot it was made in, and this number is how the next process knows.
 */
private fun bootCountOf(appContext: Context): Int? = try {
    Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
} catch (missing: Settings.SettingNotFoundException) {
    // Without it no anchor is stored or used, and the phone's clock is taken as it is at every
    // process start, on probation: a process that starts while the date is set ahead goes by
    // that date until the phone's clock comes back.
    null
}

/**
 * Writes the clock's anchor on a thread of its own: the clock asks for it in the middle of a
 * reading, which can be on the main thread.
 *
 * An anchor that cannot be written is not worth a process, and the trip service runs in this
 * one: the failure goes to a crash file, once per process, and reaches the event log at the
 * next start, the way out the backup agent has too. It is dated with the anchor's own time,
 * which is MilO's time at the moment the anchor was made. Until an anchor can be written
 * again, a MilO that Android starts while the phone's date is set ahead goes by that date
 * until the phone's clock comes back.
 */
internal class AnchorStoring(
    private val anchors: ClockAnchorStore,
    private val crashFiles: CrashFileStore,
    private val writer: Executor,
) {
    private val failureWritten = AtomicBoolean(false)

    fun store(anchor: ClockAnchor) {
        writer.execute {
            try {
                anchors.write(anchor)
            } catch (notWritten: IOException) {
                writeDown(notWritten, anchor.wallMs)
            }
        }
    }

    private fun writeDown(notWritten: IOException, atMs: Long) {
        if (!failureWritten.compareAndSet(false, true)) return
        val lost =
            IllegalStateException(
                "MilO's clock could not store its anchor. A MilO started while the phone's " +
                    "date is set ahead will go by that date until the phone's clock comes back.",
                notWritten,
            )
        try {
            crashFiles.write(CrashRecord.from(atMs, Thread.currentThread().name, lost))
        } catch (alsoNotWritten: IOException) {
            // Storage refuses both files, so there is nowhere to say it now. The next anchor
            // that cannot be written tries the crash file again.
            notWritten.addSuppressed(alsoNotWritten)
            failureWritten.set(false)
        }
    }
}
