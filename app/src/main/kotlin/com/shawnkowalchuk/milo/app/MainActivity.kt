package com.shawnkowalchuk.milo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * The only activity. It hosts the Compose UI and nothing else: no logic, no system calls.
 * Anything that talks to Android (Bluetooth, location, notifications) belongs in `platform/`
 * and is reached through a ViewModel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15 and later force apps to draw edge to edge; the phone this runs on
        // (Android 14) does not. Opting in here gives both the same layout, so what is tested on
        // the phone today does not shift after a system update.
        enableEdgeToEdge()

        setContent {
            MiloApp()
        }
    }
}
