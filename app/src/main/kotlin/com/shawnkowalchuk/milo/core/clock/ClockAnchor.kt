package com.shawnkowalchuk.milo.core.clock

/**
 * A moment MilO goes by: [wallMs] was the time of day when the clock that runs from boot read
 * [elapsedMs], during the boot numbered [bootCount]. From it, the time of any later moment of
 * the same boot is worked out without asking the phone's clock.
 *
 * @param onProbationSinceMs null for an anchor that is proven. Otherwise the anchor was taken
 * from the phone's clock with nothing to check it against (the first start after an install or
 * an update, the first MilO process after a restart of the phone, an anchor file that could not
 * be read), and this is what the clock that runs from boot read then. Such an anchor is on
 * probation ([PROBATION_MS]): the phone's date may have been set ahead at that very moment.
 * The mark is stored with the anchor, so that a later process of the same boot knows.
 */
data class ClockAnchor(
    val bootCount: Int,
    val wallMs: Long,
    val elapsedMs: Long,
    val onProbationSinceMs: Long?,
)
