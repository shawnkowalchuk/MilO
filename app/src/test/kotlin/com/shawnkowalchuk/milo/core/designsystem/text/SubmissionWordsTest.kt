package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which sentence says whether a month's report was sent, on Trips and on Report alike. */
class SubmissionWordsTest {
    @Test
    fun `a month nothing was sent for is not submitted, whatever else is passed`() {
        assertEquals(R.string.report_not_submitted, submissionWordsRes(submitted = false, 0))
        assertEquals(R.string.report_not_submitted, submissionWordsRes(submitted = false, 3))
    }

    @Test
    fun `a month sent once says when`() {
        assertEquals(R.string.report_submitted_on, submissionWordsRes(submitted = true, 0))
    }

    @Test
    fun `a month sent again names its newest revision as well`() {
        assertEquals(R.string.report_submitted_revised, submissionWordsRes(submitted = true, 1))
        assertEquals(R.string.report_submitted_revised, submissionWordsRes(submitted = true, 4))
    }
}
