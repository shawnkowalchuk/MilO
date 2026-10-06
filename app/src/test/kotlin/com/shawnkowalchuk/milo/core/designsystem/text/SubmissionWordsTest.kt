package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which sentence says whether a month's report was sent, on Trips and on Report alike. */
class SubmissionWordsTest {
    @Test
    fun `a month nothing was sent for is not submitted, whatever else is passed`() {
        assertEquals(R.string.report_not_submitted, submissionWordsRes(false, 0, false))
        assertEquals(R.string.report_not_submitted, submissionWordsRes(false, 3, true))
    }

    @Test
    fun `a month sent once says when`() {
        assertEquals(R.string.report_submitted_on, submissionWordsRes(true, 0, sentAgain = false))
    }

    @Test
    fun `a month sent again names its newest revision as well`() {
        assertEquals(R.string.report_submitted_revised, submissionWordsRes(true, 1, true))
        assertEquals(R.string.report_submitted_revised, submissionWordsRes(true, 4, true))
    }

    @Test
    fun `a month whose first report was removed says that a revision is what marks it`() {
        // Only revision 1 is left in the list: nothing was "sent again" after it.
        val words = submissionWordsRes(submitted = true, revisions = 1, sentAgain = false)

        assertEquals(R.string.report_submitted_by_revision, words)
    }
}
