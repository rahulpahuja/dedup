package com.rp.dedup.core.i18n

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {

    fun applyLocale(languageCode: String) {
        val appLocale: LocaleListCompat = if (languageCode == "system" || languageCode.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getSupportedLanguages(): List<LanguageOption> {
        return listOf(
            LanguageOption("System Default", "system"),
            LanguageOption("English", "en"),
            LanguageOption("Hindi (हिन्दी)", "hi")
        )
    }
}

data class LanguageOption(val name: String, val code: String)
