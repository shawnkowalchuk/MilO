package com.shawnkowalchuk.milo.feature.setup

import androidx.lifecycle.ViewModel
import com.shawnkowalchuk.milo.data.settings.ConfirmedStep
import com.shawnkowalchuk.milo.platform.system.PermissionAsk
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.system.SetupFix
import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SystemScreen
import com.shawnkowalchuk.milo.platform.system.SystemScreens
import com.shawnkowalchuk.milo.platform.system.androidDidNotAsk
import kotlinx.coroutines.flow.StateFlow

/**
 * The Setup screen's link to the checklist. It keeps no copy of the rows: they are the shared
 * [SetupChecklist]'s, which the home screen's warning reads too.
 */
class SetupViewModel(private val checklist: SetupChecklist, private val screens: SystemScreens) :
    ViewModel() {
    /** Null until the phone has been read for the first time. */
    val rows: StateFlow<List<SetupRow>?> = checklist.rows

    /** The permission dialog that is open, if one is. Its answer needs to know what was asked. */
    private var pendingAsk: PermissionAsk? = null

    /**
     * Shawn changes settings outside MilO and comes back, and Android reports none of it, so
     * every state is read again each time the screen comes to the front.
     */
    fun onCameToFront() {
        checklist.refresh()
    }

    fun onOpenScreen(screen: SystemScreen) {
        screens.open(screen)
    }

    fun onConfirm(step: ConfirmedStep) {
        checklist.setConfirmed(step, confirmed = true)
    }

    fun onTakeBack(step: ConfirmedStep) {
        checklist.setConfirmed(step, confirmed = false)
    }

    /** The screen is about to show Android's permission dialog for [fix]. */
    fun onAsking(fix: SetupFix.AskPermission, couldExplainBefore: Boolean) {
        pendingAsk = PermissionAsk(fix, couldExplainBefore)
    }

    /**
     * Android's dialog has answered. If it never appeared, the settings page that holds the
     * permission is opened in its place, so the button always leads somewhere.
     */
    fun onPermissionAnswer(granted: Boolean, canExplainAfter: Boolean) {
        val ask = pendingAsk ?: return
        pendingAsk = null
        if (androidDidNotAsk(granted, ask.couldExplainBefore, canExplainAfter)) {
            screens.open(ask.fix.ifNotAsked)
        }
        checklist.refresh()
    }
}
