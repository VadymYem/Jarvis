package top.authorche.agent.voice

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class PhoneFinderController(context: Context) {
    private val appContext = context.applicationContext

    fun start() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, PhoneFinderService::class.java)
                .setAction(PhoneFinderService.ACTION_START),
        )
    }

    fun stop() {
        appContext.stopService(Intent(appContext, PhoneFinderService::class.java))
    }
}
