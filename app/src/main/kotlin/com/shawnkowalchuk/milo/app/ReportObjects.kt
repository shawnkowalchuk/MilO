package com.shawnkowalchuk.milo.app

import android.content.Context
import com.shawnkowalchuk.milo.platform.reminder.AlarmManagerReminderAlarm
import com.shawnkowalchuk.milo.platform.reminder.MonthlyReminder
import com.shawnkowalchuk.milo.platform.reminder.ReminderNotification
import com.shawnkowalchuk.milo.platform.report.ReportDocuments
import com.shawnkowalchuk.milo.platform.report.ReportHandOff
import com.shawnkowalchuk.milo.platform.report.ReportTexts
import com.shawnkowalchuk.milo.platform.report.buildReportDocuments
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * The long-lived objects around the report for the accountant: its words, its two files, the
 * hand-over to another app, and the monthly reminder to send it.
 *
 * It is a part of the [AppContainer], which creates it once and through which everything
 * reaches it (`container.reports`). It is a class of its own only because the container is at
 * its size limit (ENGINEERING_STANDARDS section 3); the rules are the container's: each object
 * is made lazily, on first use, and handed what it needs through its constructor.
 *
 * @param container where the storage these objects read is made.
 */
class ReportObjects(private val appContext: Context, private val container: AppContainer) {
    /** The words of the report for the accountant, and how this phone writes dates and times. */
    val texts: ReportTexts by lazy { ReportTexts(appContext) }

    /**
     * Makes the PDF and the CSV of a report, in the app's cache. Nothing here sends anything:
     * the files are handed to another app by [handOff], when Shawn asks for it.
     */
    val documents: ReportDocuments by lazy { buildReportDocuments(appContext, texts) }

    /**
     * Builds the requests that hand a file to the email app, a viewer or the share sheet: a
     * report's PDF or CSV, and the event log as a text file.
     */
    val handOff: ReportHandOff by lazy { ReportHandOff(appContext) }

    /**
     * The monthly reminder that last month's report has not been sent. It is handed the
     * settings, the list of sent reports and a way to read last month's trips, and nothing of
     * the trip engine: it cannot start, end or change a trip.
     */
    val reminder: MonthlyReminder by lazy {
        // Both are made on first use, which is on a background thread, the first time the
        // reminder looks: building the reminder at process start then asks nothing of Android
        // on the main thread, where a trip trigger may be waiting. MilO's one activity is
        // named here and handed in, so that the reminder's package does not import `app/`.
        val notification by lazy {
            ReminderNotification(appContext, opens = MainActivity::class.java)
        }
        val alarm by lazy { AlarmManagerReminderAlarm(appContext) }
        MonthlyReminder(
            alarm = { atMs -> alarm.setFor(atMs) },
            show = { month -> notification.show(month) },
            withdraw = { notification.cancel() },
            settings = container.settingsStore,
            sentReports = container.sentReportRepository::currentSent,
            tripsStartedBetween = { fromMs, untilMs ->
                container.tripRepository.observeTripsStartedBetween(fromMs, untilMs).first()
            },
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            clock = System::currentTimeMillis,
            zone = ZoneId::systemDefault,
            scope = container.applicationScope,
        )
    }
}
