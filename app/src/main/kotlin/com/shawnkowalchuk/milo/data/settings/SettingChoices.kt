package com.shawnkowalchuk.milo.data.settings

// The values the Settings screen offers for the two trip settings and for the reminder's day,
// and the arithmetic of stepping through them. Pure, so the ranges are tested without a phone. The settings store
// itself accepts any value that is not negative; these are what Shawn can choose.

/**
 * A setting that is chosen in even steps between two limits. The unit is the stored one
 * (seconds, metres); what the screen prints is worked out from it when it is shown.
 *
 * @param min the smallest value offered.
 * @param max the largest value offered.
 * @param step the distance between two neighbouring values. [min] and [max] are both on the
 * grid, so stepping from one always reaches the other exactly.
 */
data class SteppedChoice(val min: Int, val max: Int, val step: Int) {
    init {
        require(step > 0) { "A step must be positive, but was $step" }
        require(min <= max) { "The range ends ($max) before it starts ($min)" }
        require((max - min) % step == 0) { "The range $min..$max is not a whole number of steps" }
    }

    /**
     * The next value up from [value]: the nearest one on the grid above it, and never past
     * [max]. A stored value that is not on the grid (written by an older version, or by hand)
     * is brought onto it by the first press.
     */
    fun stepUp(value: Int): Int = when {
        value >= max -> max
        value < min -> min
        else -> min + ((value - min) / step + 1) * step
    }

    /** The next value down from [value], on the same terms, and never below [min]. */
    fun stepDown(value: Int): Int = when {
        value <= min -> min

        value > max -> max

        // Rounded up to the grid first, so that a value between two steps goes to the lower.
        else -> min + ((value - min + step - 1) / step - 1) * step
    }

    /** Whether there is a larger value to choose. */
    fun canStepUp(value: Int): Boolean = value < max

    /** Whether there is a smaller value to choose. */
    fun canStepDown(value: Int): Boolean = value > min
}

/**
 * The grace period, in seconds: half a minute to ten minutes, in half minutes. Under half a
 * minute a truck that drops Bluetooth for a moment would end the trip; over ten, two separate
 * drives start to run into one.
 */
val GRACE_PERIOD_CHOICE = SteppedChoice(min = 30, max = 600, step = 30)

/**
 * The minimum trip distance, in metres: 0.1 km to 2 km, in tenths of a kilometre. There is no
 * zero: with no minimum, a start that never left the yard would be a counted trip.
 */
val MINIMUM_TRIP_DISTANCE_CHOICE = SteppedChoice(min = 100, max = 2000, step = 100)

/**
 * The reminder's day of the month: the 1st to the 31st, a day at a time. A month that is
 * shorter than the day chosen uses its own last day, so the 29th to the 31st are offered too.
 */
val REMINDER_DAY_CHOICE =
    SteppedChoice(min = REMINDER_DAYS.first, max = REMINDER_DAYS.last, step = 1)
