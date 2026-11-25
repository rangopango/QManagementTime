package com.sabil.qnow.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { SYSTEM("System default"), LIGHT("Light"), DARK("Dark") }

/** App languages. [nativeName] is shown in the picker so people can find their own language. */
enum class AppLanguage(val nativeName: String, val englishName: String, val rtl: Boolean = false) {
    EN("English", "English"),
    // India's 22 official (8th Schedule) languages.
    AS("অসমীয়া", "Assamese"), BN("বাংলা", "Bengali"), BRX("बर'", "Bodo"), DOI("डोगरी", "Dogri"),
    GU("ગુજરાતી", "Gujarati"), HI("हिन्दी", "Hindi"), KN("ಕನ್ನಡ", "Kannada"), KS("کٲشُر", "Kashmiri", rtl = true),
    KOK("कोंकणी", "Konkani"), MAI("मैथिली", "Maithili"), ML("മലയാളം", "Malayalam"), MNI("ꯃꯩꯇꯩꯂꯣꯟ", "Manipuri"),
    MR("मराठी", "Marathi"), NE("नेपाली", "Nepali"), OR("ଓଡ଼ିଆ", "Odia"), PA("ਪੰਜਾਬੀ", "Punjabi"),
    SA("संस्कृतम्", "Sanskrit"), SAT("ᱥᱟᱱᱛᱟᱲᱤ", "Santali"), SD("سنڌي", "Sindhi", rtl = true), TA("தமிழ்", "Tamil"),
    TE("తెలుగు", "Telugu"), UR("اردو", "Urdu", rtl = true),
}

private val Context.settingsStore by preferencesDataStore(name = "qnow_settings")

/** Non-sensitive app preferences (appearance). Sensitive values go in [SecureStore]. */
@Singleton
class AppSettings @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val themeKey = stringPreferencesKey("theme")

    val theme: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.SYSTEM
    }

    private val languageKey = stringPreferencesKey("language")

    val language: Flow<AppLanguage> = context.settingsStore.data.map { prefs ->
        AppLanguage.entries.firstOrNull { it.name == prefs[languageKey] } ?: AppLanguage.EN
    }

    suspend fun setLanguage(lang: AppLanguage) {
        context.settingsStore.edit { it[languageKey] = lang.name }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsStore.edit { it[themeKey] = mode.name }
    }
}
