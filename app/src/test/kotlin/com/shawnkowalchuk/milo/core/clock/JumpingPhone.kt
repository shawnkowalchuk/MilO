package com.shawnkowalchuk.milo.core.clock

/** What the owner's date trick adds to the phone's clock: one day. */
const val DAY_MS = 24L * 60L * 60L * 1000L

/** How long before a scene begins the phone of these tests was switched on. */
private const val BOOTED_BEFORE_MS = 1_000_000_000L

/**
 * A phone whose date can be set by hand, for tests: the two clocks a real one has, and MilO's
 * clock built on them.
 *
 * - **Time since boot** only moves when a test moves it ([advance]), and never jumps.
 * - **The phone's wall clock** is the true time plus whatever the date was set by ([setAhead],
 *   [setBack], [setBy]). The true time is where the scene began plus the time that has passed.
 * - **[clock]** is the real `TrustedClock` on those two. A test hands `{ phone.clock.now() }`
 *   to the class under test, as the app's wiring hands it `AppContainer.clock`.
 *
 * @param startMs the true time at which the scene begins.
 * @param bootCount the number of the phone's present boot, or null for a phone that keeps none.
 * @param miloHasRun true for a phone on which MilO has been running since it was switched on,
 * which is the usual case: the first clock of the scene finds a proven anchor that an earlier
 * process left. False for the very first MilO process of this boot (or the first after the
 * update that brought the clock): it finds no anchor and takes the phone's clock, on probation.
 */
class JumpingPhone(startMs: Long, var bootCount: Int? = 7, miloHasRun: Boolean = true) {
    /** Time since boot, in milliseconds. */
    var elapsedMs = BOOTED_BEFORE_MS
        private set

    /** The true time at the moment the phone was switched on. */
    private var bootedAtMs = startMs - BOOTED_BEFORE_MS

    /** How far the phone's clock is from the true time: positive for ahead. */
    var aheadMs = 0L
        private set

    /** Every anchor MilO's clock has stored, oldest first: the anchor file, in effect. */
    val stored = mutableListOf<ClockAnchor>()

    init {
        val boot = bootCount
        if (miloHasRun && boot != null) {
            stored += ClockAnchor(boot, startMs, BOOTED_BEFORE_MS, onProbationSinceMs = null)
        }
    }

    /** Everything MilO's clock has told about the phone's, oldest first. */
    val news = mutableListOf<ClockNews>()

    /** MilO's clock in the process that is running now. [newProcess] replaces it. */
    var clock: TrustedClock = build()
        private set

    /** The time it really is, whatever the phone's clock says. */
    val trueNowMs: Long get() = bootedAtMs + elapsedMs

    /** What the phone's own clock reads. */
    val phoneNowMs: Long get() = trueNowMs + aheadMs

    /** Time passes. */
    fun advance(ms: Long) {
        require(ms >= 0) { "Time since boot does not go back: $ms ms" }
        elapsedMs += ms
    }

    /** Time passes until the true time is [trueMs]. */
    fun advanceTo(trueMs: Long) = advance(trueMs - trueNowMs)

    /** Settings, "Set date": the phone's clock is now one day ahead. */
    fun setAhead() = setBy(DAY_MS)

    /** Automatic time is switched on again: the network puts the clock right. */
    fun setBack() = setBy(0)

    /** The phone's clock is set to stand [offsetMs] from the true time. */
    fun setBy(offsetMs: Long) {
        aheadMs = offsetMs
    }

    /**
     * MilO's process ends and a new one starts, on the same boot: the new clock is handed the
     * anchor the old one stored last, as `miloClock` reads it from the file.
     */
    fun newProcess() {
        clock = build()
    }

    /**
     * MilO's process starts with no anchor to go by, on the same boot: the first start after
     * the update that brought the clock, or an anchor file that is gone or cannot be read.
     */
    fun newProcessWithoutAnchor() {
        stored.clear()
        clock = build()
    }

    /** The phone is switched off and on: a new boot, and time since boot starts again. */
    fun reboot(elapsedAfterMs: Long = 30_000L) {
        bootedAtMs = trueNowMs - elapsedAfterMs
        elapsedMs = elapsedAfterMs
        bootCount = bootCount?.plus(1)
        clock = build()
    }

    private fun build(): TrustedClock = TrustedClock(
        phoneNow = { phoneNowMs },
        elapsedNow = { elapsedMs },
        bootCount = bootCount,
        stored = stored.lastOrNull(),
        save = { stored += it },
    ).also { it.tellNewsTo { told -> news += told } }
}
