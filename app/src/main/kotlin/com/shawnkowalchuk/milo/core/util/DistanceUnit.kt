package com.shawnkowalchuk.milo.core.util

import java.math.BigDecimal

/**
 * The unit a distance is shown in: Shawn's choice in Settings (2026-10-07, "add the ability in
 * settings to change the units from km to mile"). Kilometres until he picks miles.
 *
 * **It is only ever about showing.** Every distance is stored in metres, whatever is picked,
 * and a trip is not touched when the choice changes: switching back and forth loses nothing.
 * The figure for a trip is worked out from its stored metres each time, in the unit that is
 * asked for (`tenthsOf`); a figure that was already rounded in one unit is never converted
 * into the other.
 *
 * @param metresPerUnit how many metres one of it is. A mile is 1 609.344 m exactly, by its
 * definition.
 */
enum class DistanceUnit(val metresPerUnit: BigDecimal) {
    KILOMETRES(BigDecimal("1000")),
    MILES(BigDecimal("1609.344")),
    ;

    /** How many metres a tenth of it is: what a printed figure counts in. */
    val metresPerTenth: BigDecimal get() = metresPerUnit.movePointLeft(1)
}
