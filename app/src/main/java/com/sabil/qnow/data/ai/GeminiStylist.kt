package com.sabil.qnow.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

class AiNoImageException : IllegalStateException("The AI didn't return a picture. Try a clearer photo or another style.")

/**
 * Restyles hair on a customer's photo with Google's Gemini image model, through Firebase AI
 * Logic (no API key inside the app; usage is billed to the QNow Firebase project).
 * The photo is sent to Google only when the customer taps Generate, and QNow doesn't store it.
 */
@Singleton
class GeminiStylist @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.TEXT, ResponseModality.IMAGE)
            },
        )
    }

    /** Loads the photo at a size the model handles well (and that uploads quickly). */
    suspend fun load(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val s = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    suspend fun restyle(photo: Bitmap, prompt: String): Bitmap {
        val response = model.generateContent(content {
            image(photo)
            text(prompt)
        })
        return response.candidates.firstOrNull()?.content?.parts
            ?.filterIsInstance<ImagePart>()?.firstOrNull()?.image
            ?: throw AiNoImageException()
    }

    companion object {
        const val MODEL = "gemini-3.1-flash-image"
        private const val MAX_SIDE = 1024
    }
}
