package com.sabil.qnow.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The free AI said no: busy, or this phone's free daily limit is used up. */
class FreeAiBusyException : IOException("Today's free AI looks on this internet connection are used up. They refill over the next few hours. Colours still work any time.")

/**
 * Free realistic hair looks with FLUX.1 Kontext, the open image-editing model, through its public
 * Hugging Face demo (no account, no key, no cost). Hugging Face gives each phone a small amount of
 * free AI time per day, so a few looks a day per customer. The photo is sent to Hugging Face only
 * when the customer taps a style, after they agree.
 */
@Singleton
class FreeAiStylist @Inject constructor() {

    suspend fun restyle(photo: Bitmap, instruction: String): Bitmap = withContext(Dispatchers.IO) {
        val path = upload(photo)
        val eventId = call(path, instruction)
        val url = awaitResult(eventId)
        download(url)
    }

    /** Short, direct instructions work best with Kontext. */
    fun instruction(changes: List<String>): String = buildString {
        changes.forEach { append(it.trim()).append(' ') }
        append("Keep the exact same person, face, identity, expression, skin tone, pose, clothes, lighting and background. Photorealistic.")
    }

    private fun upload(photo: Bitmap): String {
        val jpeg = ByteArrayOutputStream().also { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val boundary = "qnow" + UUID.randomUUID().toString().replace("-", "")
        val conn = open("$SPACE/gradio_api/upload", "POST").apply {
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        conn.outputStream.use { out ->
            out.write("--$boundary\r\nContent-Disposition: form-data; name=\"files\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n".toByteArray())
            out.write(jpeg)
            out.write("\r\n--$boundary--\r\n".toByteArray())
        }
        return JSONArray(readBody(conn)).getString(0)
    }

    private fun call(path: String, instruction: String): String {
        val image = JSONObject().put("path", path).put("meta", JSONObject().put("_type", "gradio.FileData"))
        // input_image, prompt, seed, randomize_seed, guidance_scale, steps
        val body = JSONObject().put("data", JSONArray().put(image).put(instruction).put(0).put(true).put(2.5).put(STEPS))
        val conn = open("$SPACE/gradio_api/call/infer", "POST").apply { setRequestProperty("Content-Type", "application/json") }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        return JSONObject(readBody(conn)).getString("event_id")
    }

    /** Reads the server-sent events until the picture is ready (or the AI refuses). */
    private fun awaitResult(eventId: String): String {
        val conn = open("$SPACE/gradio_api/call/infer/$eventId", "GET").apply { readTimeout = 240_000 }
        if (conn.responseCode !in 200..299) throw FreeAiBusyException()
        conn.inputStream.bufferedReader().use { reader ->
            var event = ""
            while (true) {
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") && event == "complete" -> {
                        val first = JSONArray(line.removePrefix("data:").trim()).getJSONObject(0)
                        return first.optString("url").ifBlank { "$SPACE/gradio_api/file=" + first.getString("path") }
                    }
                    line.startsWith("data:") && event == "error" -> throw FreeAiBusyException()
                }
            }
        }
        throw FreeAiBusyException()
    }

    private fun download(url: String): Bitmap {
        val conn = open(url, "GET")
        val bytes = conn.inputStream.use { it.readBytes() }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw AiNoImageException()
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = method == "POST"
            setRequestProperty("User-Agent", "QNow-Android")
        }

    private fun readBody(conn: HttpURLConnection): String {
        if (conn.responseCode == 429 || conn.responseCode == 503) throw FreeAiBusyException()
        if (conn.responseCode !in 200..299) throw IOException("Free AI error ${conn.responseCode}")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        private const val SPACE = "https://black-forest-labs-flux-1-kontext-dev.hf.space"
        private const val STEPS = 14 // fewer steps = faster and uses less of the free daily time, still realistic
    }
}
