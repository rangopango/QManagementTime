package com.sabil.qnow.ui.customer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.sabil.qnow.domain.ai.HairStyles
import com.sabil.qnow.domain.ai.StyleOption
import java.io.File

/**
 * "Try with Gemini": hands the customer's photo and a ready-made instruction to the Gemini app
 * on their own phone, so the realistic preview uses THEIR free Gemini allowance and QNow pays
 * nothing. The instruction is also copied, in case Gemini doesn't pick up the text.
 */
object GeminiHandoff {
    private const val GEMINI_APP = "com.google.android.apps.bard"

    enum class Result { OPENED_GEMINI, NEEDS_GEMINI_APP }

    fun instruction(style: StyleOption, group: String): String {
        val isColour = group == HairStyles.colours.id
        return HairStyles.prompt(if (isColour) null else style, if (isColour) style else null)
    }

    /** Copies the instruction and opens Gemini with the photo attached. */
    fun open(context: Context, photo: Bitmap, style: StyleOption, group: String): Result =
        openText(context, photo, instruction(style, group))

    /** Opens Gemini with the photo and any ready-made instruction (also copied to paste). */
    fun openText(context: Context, photo: Bitmap, text: String): Result {
        copy(context, text)
        val uri = shareablePhoto(context, photo)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newRawUri("photo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            setPackage(GEMINI_APP)
        }
        return try {
            context.startActivity(intent)
            Result.OPENED_GEMINI
        } catch (_: ActivityNotFoundException) {
            Result.NEEDS_GEMINI_APP
        } catch (_: SecurityException) {
            Result.NEEDS_GEMINI_APP
        }
    }

    fun installGemini(context: Context) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$GEMINI_APP")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$GEMINI_APP")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Without the app: save the photo to the gallery (Pictures/QNow) and open Gemini in the
     * browser, where the customer attaches it and pastes the copied instruction.
     */
    fun openInBrowser(context: Context, photo: Bitmap, style: StyleOption, group: String): Boolean =
        openInBrowserText(context, photo, instruction(style, group))

    fun openInBrowserText(context: Context, photo: Bitmap, text: String): Boolean {
        copy(context, text)
        val saved = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "QNow-for-Gemini-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QNow")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }.getOrDefault(false)
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return saved
    }

    private fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Hairstyle instruction", text))
    }

    private fun shareablePhoto(context: Context, photo: Bitmap): Uri {
        val dir = File(context.cacheDir, "ai").apply { mkdirs() }
        val file = File(dir, "for-gemini.jpg")
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}
