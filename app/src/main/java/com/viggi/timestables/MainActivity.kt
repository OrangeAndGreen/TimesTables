package com.viggi.timestables

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.viggi.timestables.ui.DrillScreen
import com.viggi.timestables.ui.HistoryScreen
import com.viggi.timestables.ui.ResultScreen
import com.viggi.timestables.ui.SessionDetailScreen
import com.viggi.timestables.ui.SetupScreen
import com.viggi.timestables.ui.TimesTablesTheme

class MainActivity : ComponentActivity() {
    private val vm: DrillViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimesTablesTheme {
                App(vm)
            }
        }
    }

    /** Leaving the app mid-drive pauses the session, so the clocks don't run on without her. */
    override fun onStop() {
        super.onStop()
        if (vm.screen.value == Screen.Drilling) vm.pause()
    }

    @Composable
    private fun App(vm: DrillViewModel) {
        val screen by vm.screen.collectAsState()

        // The screen stays on through a session: nobody in the car should have to unlock the phone.
        DisposableEffect(screen == Screen.Drilling) {
            if (screen == Screen.Drilling) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        when (val s = screen) {
            Screen.Setup -> SetupScreen(vm)
            Screen.Drilling -> {
                // Back pauses rather than throwing the session away.
                BackHandler { vm.pause() }
                DrillScreen(vm)
            }
            is Screen.Result -> {
                BackHandler { vm.show(Screen.Setup) }
                ResultScreen(s, onDone = { vm.show(Screen.Setup) }, onAgain = vm::start)
            }
            Screen.History -> {
                BackHandler { vm.show(Screen.Setup) }
                HistoryScreen(vm)
            }
            is Screen.Detail -> {
                BackHandler { vm.show(Screen.History) }
                SessionDetailScreen(vm, s.id)
            }
        }
    }
}
