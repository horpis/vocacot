package com.vocacot.settings

enum class UiLanguage(val tag: String, val displayName: String) {
    SYSTEM("system", "Системный"),
    RUSSIAN("ru", "Русский"),
    ENGLISH("en", "English")
}

enum class RecognitionLanguage(val tag: String, val displayName: String) {
    RUSSIAN("ru-RU", "Русский"),
    ENGLISH("en-US", "English")
}

data class AppSettings(
    val uiLanguage: UiLanguage = UiLanguage.SYSTEM,        // язык интерфейса приложения
    val recognitionLanguage: RecognitionLanguage = RecognitionLanguage.RUSSIAN, // язык распознавания
    val monitoringEnabled: Boolean = false,                // фоновое прослушивание
    val masterPhraseEnabled: Boolean = false,              // мастер-фраза
    val masterPhrase: String = "",                         // текст мастер-фразы
    val autostartOnBoot: Boolean = false,                  // автозапуск при включении устройства
    val soundOnTrigger: Boolean = true,
    val preferOffline: Boolean = false
)
