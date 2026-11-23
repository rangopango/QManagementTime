package com.sabil.qnow.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Shrinks photos so they fit in Firestore documents (no paid file storage). Re-encoding also
 * drops EXIF data, so a shop photo never leaks the GPS position or device it was taken on.
 */
@Singleton
class ImageCompressor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** Full photo: about 1080 px, usually 80-200 KB. */
    suspend fun compress(uri: Uri, maxSide: Int = PHOTO_SIDE, quality: Int = 72): ByteArray =
        encode(ImageDecoder.createSource(context.contentResolver, uri), maxSide, quality)

    /** Re-compress bytes, e.g. a list thumbnail from a saved photo. */
    suspend fun compress(bytes: ByteArray, maxSide: Int, quality: Int = 70): ByteArray =
        encode(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), maxSide, quality)

    private suspend fun encode(source: ImageDecoder.Source, maxSide: Int, quality: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val scale = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            var q = quality
            var out: ByteArray
            // Step quality down until the photo fits comfortably under Firestore's 1 MB document limit.
            do {
                out = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, q, stream)
                    stream.toByteArray()
                }
                q -= 10
            } while (out.size > MAX_BYTES && q >= 30)
            bitmap.recycle()
            out
        }

    companion object {
        const val PHOTO_SIDE = 1080
        const val THUMB_SIDE = 240
        const val MAX_BYTES = 350_000
    }
}
