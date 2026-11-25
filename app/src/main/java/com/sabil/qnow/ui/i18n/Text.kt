package com.sabil.qnow.ui.i18n

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.sabil.qnow.data.local.AppLanguage

/** The language the customer picked in Profile → Language. */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.EN }

/** The text in the chosen language, or the English text when there's no translation yet. */
fun translate(text: String, lang: AppLanguage): String {
    if (lang == AppLanguage.EN || text.isBlank()) return text
    fun find(s: String) = Translations.table[s]?.get(lang) ?: CoreTranslations.table[lang]?.get(s)
    find(text)?.let { return it }
    // Section titles are shown in CAPITALS: match them case-insensitively.
    if (text.any { it.isLetter() } && text == text.uppercase()) {
        val key = upperIndex[text] ?: return text
        return find(key) ?: text
    }
    return text
}

private val upperIndex: Map<String, String> by lazy {
    (Translations.table.keys + CoreTranslations.table.values.flatMap { it.keys }).associateBy { it.uppercase() }
}

/** Translate a piece of text for the current screen. */
@Composable
@ReadOnlyComposable
fun tr(text: String): String = translate(text, LocalLanguage.current)

/**
 * Drop-in replacement for Material's Text: every on-screen string goes through [tr], so the
 * whole app follows the chosen language without changing each screen.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    onTextLayout = onTextLayout, style = style,
)

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
)
