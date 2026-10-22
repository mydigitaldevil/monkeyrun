package com.junglerun.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.junglerun.game.ads.ConsentManager
import com.junglerun.game.ui.JungleRunNavHost
import com.junglerun.game.ui.theme.JungleTheme

class MainActivity : ComponentActivity() {
    lateinit var consent: ConsentManager
        private set

    private val container get() = (application as JungleRunApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        consent = ConsentManager(this)
        container.ads.start(this, consent)
        setContent {
            JungleTheme {
                JungleRunNavHost(container = container, activity = this)
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        container.sound.resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        container.sound.pauseMusic()
    }
}
