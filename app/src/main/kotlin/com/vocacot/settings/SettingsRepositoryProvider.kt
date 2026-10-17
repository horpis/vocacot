// SettingsRepositoryProvider.kt
package com.vocacot

import android.content.Context
import com.vocacot.settings.SettingsRepository

object SettingsRepositoryProvider {
    private var instance: SettingsRepository? = null

    fun get(context: Context): SettingsRepository =
        instance ?: synchronized(this) {
            instance ?: SettingsRepository(context.applicationContext).also { instance = it }
        }
}
