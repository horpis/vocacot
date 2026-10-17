package com.vocacot.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("app_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val UI_LANGUAGE = stringPreferencesKey("ui_language")
        val RECOGNITION_LANGUAGE = stringPreferencesKey("recognition_language")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val MASTER_PHRASE_ENABLED = booleanPreferencesKey("master_phrase_enabled")
        val MASTER_PHRASE = stringPreferencesKey("master_phrase")
        val AUTOSTART_ON_BOOT = booleanPreferencesKey("autostart_on_boot")
        val SOUND_ON_TRIGGER = booleanPreferencesKey("sound_on_trigger")
        val PREFER_OFFLINE = booleanPreferencesKey("prefer_offline")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        AppSettings(
            uiLanguage = prefs[Keys.UI_LANGUAGE]
                ?.let { tag -> UiLanguage.entries.firstOrNull { it.tag == tag } }
                ?: UiLanguage.SYSTEM,
            recognitionLanguage = prefs[Keys.RECOGNITION_LANGUAGE]
                ?.let { tag -> RecognitionLanguage.entries.firstOrNull { it.tag == tag } }
                ?: RecognitionLanguage.RUSSIAN,
            monitoringEnabled = prefs[Keys.MONITORING_ENABLED] ?: false,
            masterPhraseEnabled = prefs[Keys.MASTER_PHRASE_ENABLED] ?: false,
            masterPhrase = prefs[Keys.MASTER_PHRASE] ?: "",
            autostartOnBoot = prefs[Keys.AUTOSTART_ON_BOOT] ?: false,
            soundOnTrigger = prefs[Keys.SOUND_ON_TRIGGER] ?: true,
            preferOffline = prefs[Keys.PREFER_OFFLINE] ?: false
        )
    }

    suspend fun setUiLanguage(lang: UiLanguage) {
        context.settingsStore.edit { it[Keys.UI_LANGUAGE] = lang.tag }
    }

    suspend fun setRecognitionLanguage(lang: RecognitionLanguage) {
        context.settingsStore.edit { it[Keys.RECOGNITION_LANGUAGE] = lang.tag }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.MONITORING_ENABLED] = enabled }
    }

    suspend fun setMasterPhraseEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.MASTER_PHRASE_ENABLED] = enabled }
    }

    suspend fun setMasterPhrase(phrase: String) {
        context.settingsStore.edit { it[Keys.MASTER_PHRASE] = phrase }
    }

    suspend fun setAutostartOnBoot(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.AUTOSTART_ON_BOOT] = enabled }
    }

    suspend fun setSoundOnTrigger(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.SOUND_ON_TRIGGER] = enabled }
    }

    suspend fun setPreferOffline(enabled: Boolean) {
        context.settingsStore.edit { it[Keys.PREFER_OFFLINE] = enabled }
    }
}
