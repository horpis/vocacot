package com.vocacot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // BroadcastReceiver синхронный — читаем настройку блокирующе, это допустимо здесь
        val autostart = runBlocking {
            SettingsRepositoryProvider.get(context).settings.first().autostartOnBoot
        }

        if (autostart) {
            context.startForegroundService(Intent(context, MonitoringService::class.java))
        }
    }
}
