package com.shawnkowalchuk.milo.platform.car

import android.annotation.SuppressLint
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.data.eventlog.EventCategory

/**
 * The entry point of the Android Auto screen. Android Auto binds this service when MilO is
 * opened on the car's display, and it hands each visit a [MiloCarSession].
 *
 * It runs in MilO's one process, on purpose: the trip service that records with GPS is there
 * too, and the screen reads the same trip controller the phone's screens read. Being shown on a
 * car does not count as being in the foreground for location, so nothing here records anything.
 *
 * Android creates this class itself, so it fetches the shared objects from the application, like
 * the trip service does (ARCHITECTURE section 3).
 */
class MiloCarAppService : CarAppService() {
    private val container get() = (application as MiloApplication).container

    override fun onCreate() {
        super.onCreate()
        // Written before the host has been checked. This line with no "session created" after
        // it means the host was turned away below, or gave up.
        note("Android Auto screen: car app service created, a host is connecting")
    }

    /**
     * Which apps may show MilO's screen and press its button. The service is exported with no
     * permission (Android Auto could not bind it otherwise), so this check is all that stands
     * between any app on the phone and Start trip / End trip.
     *
     * The list is the one the library ships: Android Auto and Google's Automotive host, each by
     * the fingerprint of its signing certificate. It is used in every build, the debug build
     * included. Google's documentation suggests letting every host in while developing; that
     * build is the one on the phone, holding the real trips.
     */
    // Lint calls the list a private resource of the library. Naming it is the use Google's guide
    // gives, and a copy kept here would go stale when Android Auto's certificate changes.
    @SuppressLint("PrivateResource")
    override fun createHostValidator(): HostValidator = HostValidator
        .Builder(applicationContext)
        .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
        .build()

    override fun onCreateSession(sessionInfo: SessionInfo): Session = MiloCarSession(
        controller = container.tripController,
        trips = container.tripRepository,
        checklist = container.setupChecklist,
        shownUnit = container.shownUnit.unit,
        clock = container.clock,
    )

    override fun onDestroy() {
        note("Android Auto screen: car app service destroyed")
        super.onDestroy()
    }

    /** Through the trip controller, so the lines are in order with everything else in the log. */
    private fun note(message: String) {
        container.tripController.note(EventCategory.ANDROID_AUTO, message)
    }
}
