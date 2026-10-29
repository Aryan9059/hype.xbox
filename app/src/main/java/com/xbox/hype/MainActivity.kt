package com.xbox.hype

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.xbox.hype.controller.ControllerState
import com.xbox.hype.net.ConnectionStatus
import com.xbox.hype.net.ControllerClient
import com.xbox.hype.ui.ConnectScreen
import com.xbox.hype.ui.ControllerScreen
import com.xbox.hype.ui.RevealHost
import com.xbox.hype.ui.theme.HypexboxTheme

class MainActivity : ComponentActivity() {
    private lateinit var client: ControllerClient
    private lateinit var pad: ControllerState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        client = ControllerClient(lifecycleScope, ::rumble)
        pad = ControllerState { client.update(it) }
        val prefs = getSharedPreferences("hype", Context.MODE_PRIVATE)

        setContent {
            HypexboxTheme(darkTheme = true, dynamicColor = false) {
                val status by client.status.collectAsState()
                val discovered by client.discovered.collectAsState()
                val connected = status as? ConnectionStatus.Connected
                RevealHost(
                    host = connected?.host,
                    connect = {
                        ConnectScreen(
                            discovered = discovered,
                            lastHost = remember { prefs.getString("host", "") ?: "" },
                            onConnect = { host ->
                                prefs.edit().putString("host", host).apply()
                                client.connect(host)
                            },
                            onRescan = client::startDiscovery,
                        )
                    },
                    controller = { host ->
                        ControllerScreen(pad, host) {
                            pad.reset()
                            client.startDiscovery()
                        }
                    },
                )
            }
        }
        client.startDiscovery()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        pad.reset()
    }

    override fun onDestroy() {
        client.stop()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Draw into the notch area instead of leaving a black bar (landscape included)
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun rumble(large: Int, small: Int) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else
            @Suppress("DEPRECATION") getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val strength = maxOf(large, small)
        if (strength == 0) vibrator.cancel()
        else vibrator.vibrate(VibrationEffect.createOneShot(120, strength.coerceIn(1, 255)))
    }
}
