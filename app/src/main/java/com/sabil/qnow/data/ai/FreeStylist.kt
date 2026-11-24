package com.sabil.qnow.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.sabil.qnow.domain.ai.StyleOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class NoFaceException : IllegalStateException("No face found. Use a clear, front-facing photo with your whole head visible.")

/**
 * Free, private style preview that runs entirely on the phone (nothing is uploaded):
 *  - Google MediaPipe's hair model finds exactly which pixels are hair,
 *  - Google ML Kit finds the face outline, eyebrows, nose and lips,
 *  - then hair is recoloured, cut to a new shape (hair outside it is replaced with skin or
 *    background, missing hair is painted in the person's own hair colour), and beards are
 *    removed or drawn.
 * It's a realistic colour preview and a good shape guide for cuts; not a photo-real AI edit.
 */
@Singleton
class FreeStylist @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val segmenter by lazy {
        ImageSegmenter.createFromOptions(
            context,
            ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hair_segmenter.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build(),
        )
    }

    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build(),
        )
    }

    suspend fun preview(photo: Bitmap, cut: StyleOption?, colour: StyleOption?): Bitmap = withContext(Dispatchers.Default) {
        val src = photo.copy(Bitmap.Config.ARGB_8888, true)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val hair = hairMask(src)

        var hairAlpha = hair
        val isBeard = cut?.id in BEARDS
        if (cut != null) {
            val face = faceDetector.process(InputImage.fromBitmap(src, 0)).await()
                .maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: throw NoFaceException()
            val g = Geometry.of(face, w, h)
            val skin = skinTone(px, w, g)
            val base = hairColour(px, hair)
            if (isBeard) {
                applyBeard(px, w, h, g, cut.id, skin, base)
            } else {
                hairAlpha = applyCut(px, w, h, hair, g, specFor(cut.id), skin, base)
            }
        }
        colour?.let { recolour(px, w, h, hairAlpha, it.id) }

        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ detection

    private fun hairMask(bmp: Bitmap, using: ImageSegmenter = segmenter): FloatArray {
        val result = using.segment(BitmapImageBuilder(bmp).build())
        val masks = result.confidenceMasks().orElse(null) ?: return FloatArray(bmp.width * bmp.height)
        val m = masks[if (masks.size > 1) 1 else 0]
        val buf = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val mw = m.width
        val mh = m.height
        val out = FloatArray(bmp.width * bmp.height)
        for (y in 0 until bmp.height) {
            val sy = min(mh - 1, y * mh / bmp.height)
            for (x in 0 until bmp.width) {
                val sx = min(mw - 1, x * mw / bmp.width)
                out[y * bmp.width + x] = buf.get(sy * mw + sx)
            }
        }
        return out
    }

    /** Head landmarks in image pixels. */
    private class Geometry(
        val cx: Float, val faceW: Float, val faceH: Float,
        val brow: Float, val chin: Float, val headTop: Float,
        val faceOutline: List<PointF>, val noseBottom: Float, val mouthTop: Float, val mouthBottom: Float,
        val mouthLeft: Float, val mouthRight: Float, val lipPoly: List<PointF>,
    ) {
        val headW get() = faceW * 1.12f
        companion object {
            fun of(f: Face, w: Int, h: Int): Geometry {
                val box = f.boundingBox
                fun pts(type: Int) = f.getContour(type)?.points.orEmpty()
                val browY = (pts(FaceContour.LEFT_EYEBROW_TOP) + pts(FaceContour.RIGHT_EYEBROW_TOP)).map { it.y }.average()
                    .takeIf { !it.isNaN() }?.toFloat() ?: (box.top + box.height() * 0.3f)
                val outline = pts(FaceContour.FACE).ifEmpty {
                    (0 until 36).map { i ->
                        val a = i / 36.0 * 2 * Math.PI
                        PointF(box.exactCenterX() + box.width() / 2f * cos(a).toFloat(), box.exactCenterY() + box.height() / 2f * sin(a).toFloat())
                    }
                }
                val upperLip = pts(FaceContour.UPPER_LIP_TOP)
                val lowerLip = pts(FaceContour.LOWER_LIP_BOTTOM)
                val nose = pts(FaceContour.NOSE_BOTTOM)
                val chinY = outline.maxOf { it.y }
                val faceH = chinY - browY
                return Geometry(
                    cx = box.exactCenterX(), faceW = box.width().toFloat(), faceH = faceH * 1.35f,
                    brow = browY, chin = chinY, headTop = browY - (chinY - browY) * 0.62f,
                    faceOutline = outline,
                    noseBottom = nose.map { it.y }.average().takeIf { !it.isNaN() }?.toFloat() ?: (browY + faceH * 0.45f),
                    mouthTop = upperLip.minOfOrNull { it.y } ?: (browY + faceH * 0.6f),
                    mouthBottom = lowerLip.maxOfOrNull { it.y } ?: (browY + faceH * 0.75f),
                    mouthLeft = upperLip.minOfOrNull { it.x } ?: (box.exactCenterX() - box.width() * 0.2f),
                    mouthRight = upperLip.maxOfOrNull { it.x } ?: (box.exactCenterX() + box.width() * 0.2f),
                    lipPoly = upperLip + lowerLip.reversed(),
                )
            }
        }
    }

    // ------------------------------------------------------------------ colours

    private fun skinTone(px: IntArray, w: Int, g: Geometry): Int {
        // Cheeks: beside the nose, between the eyes and the mouth.
        var r = 0L; var gg = 0L; var b = 0L; var n = 0
        val y0 = ((g.brow + g.noseBottom) / 2).toInt()
        val y1 = g.noseBottom.toInt()
        for (side in listOf(-1, 1)) {
            val x0 = (g.cx + side * g.faceW * 0.22f).toInt()
            for (y in y0..y1) for (x in x0 - 6..x0 + 6) {
                val i = y * w + x
                if (i !in px.indices || x !in 0 until w) continue
                val c = px[i]; r += (c shr 16) and 255; gg += (c shr 8) and 255; b += c and 255; n++
            }
        }
        if (n == 0) return 0xFFC69C7A.toInt()
        return rgb((r / n).toInt(), (gg / n).toInt(), (b / n).toInt())
    }

    private fun hairColour(px: IntArray, hair: FloatArray): Int {
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (i in px.indices) if (hair[i] > 0.75f) {
            val c = px[i]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
        }
        if (n < 200) return 0xFF2E221C.toInt()
        return rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    // ------------------------------------------------------------------ haircuts

    enum class Texture { STUBBLE, SHORT, STRAIGHT, WAVY, CURLY, BRAIDS }

    /** Shape of a cut, in units of face height: how high it rises, how far it falls, etc. */
    private data class CutSpec(
        val lift: Float,           // above the skull top
        val sides: Float,          // how far down the sides reach below the eyebrows
        val length: Float = 0f,    // long hair below the chin (0 = none)
        val volume: Float = 1f,    // width multiplier for long/round styles
        val fringe: Float = 0f,    // how far a fringe covers the forehead (0..0.25)
        val sideAlpha: Float = 1f, // density on the sides
        val fade: Boolean = false,
        val narrowTop: Boolean = false,
        val bun: Boolean = false,
        val texture: Texture = Texture.SHORT,
    )

    private fun specFor(id: String): CutSpec = when (id) {
        "buzz" -> CutSpec(0.01f, 0.18f, texture = Texture.STUBBLE, sideAlpha = 0.85f)
        "crew", "kids_crew", "ivy" -> CutSpec(0.06f, 0.2f, fringe = 0.04f, sideAlpha = 0.75f)
        "caesar", "french_crop", "textured_fringe" -> CutSpec(0.07f, 0.2f, fringe = 0.16f, sideAlpha = 0.6f, fade = id == "french_crop")
        "low_fade", "kids_fade" -> CutSpec(0.09f, 0.26f, fringe = 0.03f, fade = true)
        "mid_fade", "taper" -> CutSpec(0.09f, 0.22f, fringe = 0.03f, fade = true, sideAlpha = 0.8f)
        "high_fade" -> CutSpec(0.08f, 0.14f, fade = true, sideAlpha = 0.5f)
        "undercut", "kids_undercut" -> CutSpec(0.15f, 0.12f, fringe = 0.06f, sideAlpha = 0.25f, texture = Texture.STRAIGHT)
        "slick_back", "side_part" -> CutSpec(0.11f, 0.2f, sideAlpha = 0.7f, texture = Texture.STRAIGHT)
        "pompadour", "quiff" -> CutSpec(0.26f, 0.18f, sideAlpha = 0.6f, texture = Texture.STRAIGHT)
        "faux_hawk", "spiky" -> CutSpec(0.2f, 0.16f, sideAlpha = 0.4f, narrowTop = id == "faux_hawk")
        "mohawk" -> CutSpec(0.28f, 0.1f, sideAlpha = 0f, narrowTop = true, texture = Texture.STRAIGHT)
        "curly_top" -> CutSpec(0.18f, 0.2f, fringe = 0.08f, fade = true, texture = Texture.CURLY)
        "modern_mullet" -> CutSpec(0.1f, 0.25f, length = 0.25f, volume = 0.9f, fringe = 0.06f)
        "afro" -> CutSpec(0.45f, 0.65f, volume = 1.55f, texture = Texture.CURLY)
        "cornrows" -> CutSpec(0.02f, 0.2f, texture = Texture.BRAIDS)
        "dreadlocks" -> CutSpec(0.12f, 0.4f, length = 0.6f, volume = 1.2f, texture = Texture.BRAIDS)
        "long_layers_m" -> CutSpec(0.1f, 0.4f, length = 0.45f, volume = 1.05f, texture = Texture.WAVY)
        "man_bun", "bun", "high_pony" -> CutSpec(0.04f, 0.2f, bun = true, sideAlpha = 0.9f, texture = Texture.STRAIGHT)
        "pixie", "bixie" -> CutSpec(0.09f, 0.3f, fringe = 0.14f, sideAlpha = 0.9f)
        "bob", "kids_bob", "asym_bob" -> CutSpec(0.1f, 0.55f, length = 0.02f, volume = 1.1f, fringe = 0.05f, texture = Texture.STRAIGHT)
        "curly_bob" -> CutSpec(0.14f, 0.55f, length = 0.05f, volume = 1.25f, texture = Texture.CURLY)
        "lob" -> CutSpec(0.1f, 0.6f, length = 0.3f, volume = 1.1f, texture = Texture.STRAIGHT)
        "blunt_bangs" -> CutSpec(0.1f, 0.6f, length = 0.8f, volume = 1.1f, fringe = 0.24f, texture = Texture.STRAIGHT)
        "curtain_bangs" -> CutSpec(0.1f, 0.6f, length = 0.8f, volume = 1.1f, fringe = 0.12f, texture = Texture.STRAIGHT)
        "sleek_straight" -> CutSpec(0.06f, 0.6f, length = 0.9f, volume = 1.0f, texture = Texture.STRAIGHT)
        "beach_waves", "long_layers", "butterfly", "feathered" -> CutSpec(0.12f, 0.6f, length = 0.85f, volume = 1.2f, texture = Texture.WAVY)
        "shag", "wolf" -> CutSpec(0.16f, 0.55f, length = 0.5f, volume = 1.25f, fringe = 0.1f, texture = Texture.WAVY)
        "braid_crown", "french_braid", "kids_braids", "pigtails" -> CutSpec(0.04f, 0.22f, length = if (id == "braid_crown") 0f else 0.6f, volume = 0.75f, texture = Texture.BRAIDS)
        else -> CutSpec(0.08f, 0.2f)
    }

    /** Returns the new hair mask (used afterwards for colour). */
    private fun applyCut(px: IntArray, w: Int, h: Int, hair: FloatArray, g: Geometry, s: CutSpec, skin: Int, base: Int): FloatArray {
        val target = shapeMask(w, h, g, s)
        val plate = cleanPlate(px, w, h, hair)
        val source = HairSource.from(px, w, h, hair, g.brow)
        val hairline = g.faceOutline.minOf { it.y }
        val faceBox = RectF(g.cx - g.faceW * 0.55f, hairline, g.cx + g.faceW * 0.55f, g.chin)
        val out = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val t = target[i]
            val m = hair[i]
            var c = px[i]
            // 1) Remove hair that falls outside the new cut.
            if (m > 0.35f && t < 0.99f) {
                val fill = if (faceBox.contains(x.toFloat(), y.toFloat()) || insideHead(x, y, g)) skin else plate[i]
                c = lerp(c, fill, m * (1f - t))
            }
            // 2) Paint hair into the new shape (keeps real hair where it already is).
            if (t > 0.01f) {
                val tex = source?.takeIf { s.texture != Texture.STUBBLE }?.sample(x, y, s.texture)
                    ?.let { shadeTop(it, y, g) } ?: hairTexture(x, y, base, skin, s.texture, g)
                val keepReal = if (s.texture == Texture.STUBBLE) 0f else m * 0.9f
                c = lerp(c, tex, t * (1f - keepReal))
            }
            px[i] = c
            out[i] = max(t, if (t > 0.01f) m * t else 0f)
        }
        return out
    }

    private fun insideHead(x: Int, y: Int, g: Geometry): Boolean {
        val rx = g.headW / 2f
        val ry = (g.chin - g.headTop) / 2f
        val dx = (x - g.cx) / rx
        val dy = (y - (g.headTop + ry)) / ry
        return dx * dx + dy * dy <= 1f
    }

    /** Soft-edged alpha mask of where the new cut has hair. */
    private fun shapeMask(w: Int, h: Int, g: Geometry, s: CutSpec): FloatArray {
        val scale = 4
        val bw = max(1, w / scale)
        val bh = max(1, h / scale)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val cv = Canvas(bmp)
        cv.scale(1f / scale, 1f / scale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val fh = g.faceH
        val half = g.headW / 2f * (if (s.volume > 1.3f) s.volume else 1f)
        val top = g.headTop - s.lift * fh
        val sideBottom = g.brow + s.sides * fh
        val hairline = g.brow - fh * (0.12f - s.fringe)

        // Cap over the skull (or a narrow strip for mohawk-style cuts).
        val capHalf = if (s.narrowTop) g.headW * 0.16f else half * 1.04f
        val cap = Path().apply {
            moveTo(g.cx - capHalf, sideBottom)
            cubicTo(g.cx - capHalf, top, g.cx + capHalf, top, g.cx + capHalf, sideBottom)
            close()
        }
        p.alpha = 255
        cv.drawPath(cap, p)

        // Sides and long hair beside the face.
        if (s.sideAlpha > 0f) {
            p.alpha = (255 * s.sideAlpha).toInt()
            val bottom = if (s.length > 0f) g.chin + s.length * fh else sideBottom
            val outer = half * max(1f, s.volume)
            for (sign in listOf(-1f, 1f)) {
                val path = Path().apply {
                    moveTo(g.cx + sign * g.faceW * 0.42f, hairline)
                    lineTo(g.cx + sign * outer, hairline)
                    cubicTo(g.cx + sign * outer * 1.05f, (hairline + bottom) / 2, g.cx + sign * outer, bottom, g.cx + sign * outer * 0.85f, bottom)
                    lineTo(g.cx + sign * g.faceW * 0.46f, bottom)
                    close()
                }
                cv.drawPath(path, p)
            }
        }
        // Bun or ponytail on top.
        if (s.bun) {
            p.alpha = 255
            cv.drawOval(RectF(g.cx - fh * 0.16f, top - fh * 0.3f, g.cx + fh * 0.16f, top + fh * 0.02f), p)
        }
        // Keep the face clear, following the real forehead line (no straight edges).
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        if (g.faceOutline.size > 2) {
            val face = Path().apply {
                g.faceOutline.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
                close()
            }
            cv.drawPath(face, clear)
        }
        // Fringe: strands falling over the forehead with an uneven, natural edge.
        if (s.fringe > 0f) {
            p.alpha = 255
            val foreheadTop = g.faceOutline.minOf { it.y }
            val fringeBottom = foreheadTop + s.fringe * fh * 1.6f
            val left = g.cx - g.faceW * 0.4f
            val right = g.cx + g.faceW * 0.4f
            val fringe = Path().apply {
                moveTo(left, foreheadTop - fh * 0.02f)
                lineTo(right, foreheadTop - fh * 0.02f)
                val steps = 14
                for (k in steps downTo 0) {
                    val x = left + (right - left) * k / steps
                    val dip = if (k % 2 == 0) 1f else 0.82f
                    lineTo(x, foreheadTop + (fringeBottom - foreheadTop) * dip)
                }
                close()
            }
            cv.drawPath(fringe, p)
        }

        // Feather the edges, then read back at full size.
        val soft = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(bmp, max(1, bw / 3), max(1, bh / 3), true), bw, bh, true)
        val big = Bitmap.createScaledBitmap(soft, w, h, true)
        val a = ByteArray(w * h)
        val buf = java.nio.ByteBuffer.wrap(a)
        big.copyPixelsToBuffer(buf)
        val out = FloatArray(w * h)
        val fadeStart = g.brow + s.sides * fh * 0.15f
        for (y in 0 until h) for (x in 0 until w) {
            var v = (a[y * w + x].toInt() and 255) / 255f
            if (s.fade && abs(x - g.cx) > g.faceW * 0.32f && y > fadeStart) {
                v *= (1f - (y - fadeStart) / max(1f, sideBottom - fadeStart)).coerceIn(0.05f, 1f)
            }
            out[y * w + x] = v
        }
        return out
    }

    /** Fills hair pixels with an estimate of what's behind them (blurred surroundings). */
    private fun cleanPlate(px: IntArray, w: Int, h: Int, hair: FloatArray): IntArray {
        val s = 6
        val sw = max(1, w / s)
        val sh = max(1, h / s)
        val r = FloatArray(sw * sh); val gg = FloatArray(sw * sh); val b = FloatArray(sw * sh)
        val known = BooleanArray(sw * sh)
        for (y in 0 until sh) for (x in 0 until sw) {
            val i = (y * s) * w + (x * s)
            val k = y * sw + x
            val c = px[i]
            r[k] = ((c shr 16) and 255).toFloat(); gg[k] = ((c shr 8) and 255).toFloat(); b[k] = (c and 255).toFloat()
            known[k] = hair[i] < 0.25f
        }
        // Grow known colours into the hair area, ring by ring.
        repeat(80) {
            var changed = false
            val nk = known.copyOf()
            for (y in 0 until sh) for (x in 0 until sw) {
                val k = y * sw + x
                if (known[k]) continue
                var cr = 0f; var cg = 0f; var cb = 0f; var n = 0
                for ((dx, dy) in NEIGHBOURS) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until sw || ny !in 0 until sh) continue
                    val q = ny * sw + nx
                    if (known[q]) { cr += r[q]; cg += gg[q]; cb += b[q]; n++ }
                }
                if (n > 0) { r[k] = cr / n; gg[k] = cg / n; b[k] = cb / n; nk[k] = true; changed = true }
            }
            System.arraycopy(nk, 0, known, 0, known.size)
            if (!changed) return@repeat
        }
        val small = IntArray(sw * sh) { rgb(r[it].toInt(), gg[it].toInt(), b[it].toInt()) }
        val blurred = Bitmap.createScaledBitmap(
            Bitmap.createScaledBitmap(Bitmap.createBitmap(small, sw, sh, Bitmap.Config.ARGB_8888), max(1, sw / 2), max(1, sh / 2), true), w, h, true,
        )
        return IntArray(w * h).also { blurred.getPixels(it, 0, w, 0, 0, w, h) }
    }

    private fun shadeTop(c: Int, y: Int, g: Geometry): Int {
        val light = (1.08f - (y - g.headTop) / max(1f, g.chin - g.headTop) * 0.2f).coerceIn(0.85f, 1.1f)
        return scale(c, light)
    }

    /**
     * Real hair texture taken from the person's own photo: the densest patch of hair is tiled
     * (mirrored so there are no seams) wherever new hair is needed.
     */
    class HairSource internal constructor(private val px: IntArray, private val w: Int, private val l: Int, private val t: Int, private val sw: Int, private val sh: Int) {
        fun sample(x: Int, y: Int, texture: Texture): Int {
            // Straight styles stretch the texture along the strands; curly ones keep it as is.
            val yy = if (texture == Texture.STRAIGHT) y / 3 else y
            return px[(t + mirror(yy, sh)) * w + (l + mirror(x, sw))]
        }

        companion object {
            private fun mirror(v: Int, n: Int): Int { val m = Math.floorMod(v, 2 * n); return if (m < n) m else 2 * n - 1 - m }

            fun from(px: IntArray, w: Int, h: Int, hair: FloatArray, brow: Float): HairSource? {
                // Find the box of confident hair above the eyebrows, then shrink to its dense core.
                var l = w; var r = 0; var t = h; var b = 0
                for (y in 0 until min(h, brow.toInt())) for (x in 0 until w) if (hair[y * w + x] > 0.9f) {
                    l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
                }
                if (r - l < 20 || b - t < 20) return null
                val cl = l + (r - l) / 4; val cr = r - (r - l) / 4
                val ct = t + (b - t) / 4; val cb = b - (b - t) / 6
                if (cr - cl < 12 || cb - ct < 12) return null
                return HairSource(px.copyOf(), w, cl, ct, cr - cl, cb - ct)
            }
        }
    }

    /** Procedural hair strands in the person's own colour, with light from above. */
    private fun hairTexture(x: Int, y: Int, base: Int, skin: Int, t: Texture, g: Geometry): Int {
        val fx = x.toFloat(); val fy = y.toFloat()
        val shade = when (t) {
            Texture.STUBBLE -> {
                val dots = noise(fx * 0.9f, fy * 0.9f)
                return lerp(skin, base, 0.22f + 0.3f * dots)
            }
            Texture.SHORT -> 0.7f + 0.45f * noise(fx * 0.35f, fy * 0.08f) + 0.15f * noise(fx * 1.2f, fy * 1.2f)
            Texture.STRAIGHT -> 0.65f + 0.55f * noise(fx * 0.45f, fy * 0.03f)
            Texture.WAVY -> 0.65f + 0.55f * noise(fx * 0.3f + 6f * sin(fy * 0.05f), fy * 0.04f)
            Texture.CURLY -> 0.6f + 0.6f * noise(fx * 0.18f + 3f * sin(fy * 0.25f), fy * 0.18f + 3f * cos(fx * 0.25f))
            Texture.BRAIDS -> 0.6f + 0.5f * (0.5f + 0.5f * sin(fx * 0.35f + fy * 0.12f)) * noise(fx * 0.1f, fy * 0.3f)
        }
        // Brighter near the crown, darker lower down.
        val light = (1.15f - (fy - g.headTop) / max(1f, g.chin - g.headTop) * 0.35f).coerceIn(0.7f, 1.15f)
        return scale(base, (shade * light).coerceIn(0.35f, 1.6f))
    }

    // ------------------------------------------------------------------ beards

    private fun applyBeard(px: IntArray, w: Int, h: Int, g: Geometry, id: String, skin: Int, hairBase: Int) {
        val beardColour = scale(hairBase, 0.85f)
        val region = beardRegion(w, h, g, full = true, chinOnly = false, moustache = true, handlebar = false)
        // Start clean: fade existing dark facial hair towards skin.
        val skinL = luma(skin)
        for (i in px.indices) {
            val a = region[i]
            if (a <= 0f) continue
            val c = px[i]
            val darkness = ((skinL - luma(c)) / max(1f, skinL)).coerceIn(0f, 1f)
            px[i] = lerp(c, lerp(skin, c, 0.18f), a * (darkness * 1.6f).coerceIn(0f, 1f))
        }
        if (id == "clean") return
        val shape = when (id) {
            "stubble" -> region
            "short_boxed", "full_beard" -> region
            "goatee" -> beardRegion(w, h, g, full = false, chinOnly = true, moustache = true, handlebar = false)
            "van_dyke" -> beardRegion(w, h, g, full = false, chinOnly = true, moustache = true, handlebar = false, separate = true)
            "handlebar" -> beardRegion(w, h, g, full = false, chinOnly = false, moustache = true, handlebar = true)
            else -> region
        }
        val density = when (id) { "stubble" -> 0.45f; "short_boxed" -> 0.8f; "full_beard" -> 0.95f; else -> 0.9f }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = shape[i]
            if (a <= 0f) continue
            val n = noise(x * 0.8f, y * 0.8f)
            val tex = if (id == "stubble") lerp(skin, beardColour, 0.3f + 0.5f * n) else scale(beardColour, 0.7f + 0.6f * noise(x * 0.3f, y * 0.12f))
            px[i] = lerp(px[i], tex, a * density * (if (id == "stubble") (0.5f + 0.5f * n) else 1f))
        }
    }

    private fun beardRegion(w: Int, h: Int, g: Geometry, full: Boolean, chinOnly: Boolean, moustache: Boolean, handlebar: Boolean, separate: Boolean = false): FloatArray {
        val scale = 2
        val bw = max(1, w / scale); val bh = max(1, h / scale)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val cv = Canvas(bmp).apply { scale(1f / scale, 1f / scale) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val mouthW = g.mouthRight - g.mouthLeft
        if (full) {
            val jaw = g.faceOutline.filter { it.y > g.noseBottom - g.faceH * 0.05f }.sortedBy { it.x }
            if (jaw.size > 2) {
                val path = Path().apply {
                    moveTo(jaw.first().x, jaw.first().y)
                    // Along the jaw (outline points are sorted left→right, take the lower ones).
                    g.faceOutline.filter { it.y > g.noseBottom }.sortedBy { atan(it.x - g.cx, it.y - g.noseBottom) }.forEach { lineTo(it.x, it.y + g.faceH * 0.03f) }
                    lineTo(jaw.last().x, jaw.last().y)
                    // Up to the cheeks, then back across under the nose.
                    lineTo(g.cx + g.faceW * 0.3f, g.noseBottom + g.faceH * 0.02f)
                    lineTo(g.cx - g.faceW * 0.3f, g.noseBottom + g.faceH * 0.02f)
                    close()
                }
                cv.drawPath(path, p)
            }
        }
        if (chinOnly) {
            val top = if (separate) g.mouthBottom + g.faceH * 0.03f else g.mouthTop
            cv.drawOval(RectF(g.cx - mouthW * 0.55f, top, g.cx + mouthW * 0.55f, g.chin + g.faceH * 0.04f), p)
            if (!separate) {
                cv.drawRect(g.mouthLeft - mouthW * 0.08f, g.mouthTop, g.mouthLeft + mouthW * 0.05f, g.mouthBottom + g.faceH * 0.05f, p)
                cv.drawRect(g.mouthRight - mouthW * 0.05f, g.mouthTop, g.mouthRight + mouthW * 0.08f, g.mouthBottom + g.faceH * 0.05f, p)
            }
        }
        if (moustache) {
            val top = g.noseBottom + (g.mouthTop - g.noseBottom) * 0.15f
            cv.drawRoundRect(RectF(g.mouthLeft - mouthW * 0.1f, top, g.mouthRight + mouthW * 0.1f, g.mouthTop + g.faceH * 0.01f), mouthW * 0.2f, mouthW * 0.2f, p)
            if (handlebar) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = (g.mouthTop - top) * 0.7f
                p.strokeCap = Paint.Cap.ROUND
                for (sign in listOf(-1f, 1f)) {
                    val ex = if (sign < 0) g.mouthLeft else g.mouthRight
                    val curl = Path().apply {
                        moveTo(ex, g.mouthTop)
                        quadTo(ex + sign * mouthW * 0.45f, g.mouthTop + g.faceH * 0.02f, ex + sign * mouthW * 0.55f, g.mouthTop - g.faceH * 0.07f)
                    }
                    cv.drawPath(curl, p)
                }
                p.style = Paint.Style.FILL
            }
        }
        // Never cover the lips.
        if (g.lipPoly.size > 2) {
            val lips = Path().apply { g.lipPoly.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }; close() }
            cv.drawPath(lips, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
        }
        val soft = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(bmp, max(1, bw / 2), max(1, bh / 2), true), w, h, true)
        val a = ByteArray(w * h)
        soft.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(a))
        return FloatArray(w * h) { (a[it].toInt() and 255) / 255f }
    }

    private fun atan(dx: Float, dy: Float) = kotlin.math.atan2(dy.toDouble(), dx.toDouble())

    // ------------------------------------------------------------------ hair colour

    internal fun recolour(px: IntArray, w: Int, h: Int, mask: FloatArray, id: String) {
        var sumL = 0f; var n = 0; var top = h; var bottom = 0
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (mask[i] > 0.5f) { sumL += luma(px[i]); n++; top = min(top, y); bottom = max(bottom, y) }
        }
        if (n == 0) return
        val meanL = sumL / n / 255f
        val target = COLOURS[id] ?: return
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = mask[i]
            if (a < 0.05f) continue
            val along = (y - top).toFloat() / max(1, bottom - top)
            val goal = when (id) {
                // Lighter towards the ends; roots stay natural.
                "ombre", "balayage" -> if (along < 0.35f) null else target
                // Fine streaks.
                "highlights" -> if (noise(x * 0.12f, y * 0.01f) > 0.62f) target else null
                else -> target
            } ?: continue
            val strength = when (id) {
                "ombre" -> ((along - 0.35f) / 0.65f).coerceIn(0f, 1f)
                "balayage" -> ((along - 0.35f) / 0.65f).coerceIn(0f, 1f) * (0.6f + 0.4f * noise(x * 0.08f, y * 0.02f))
                else -> 1f
            }
            val c = px[i]
            val l = luma(c) / 255f
            // Softer edges (a * a) so the colour fades out where hair meets skin or background.
            px[i] = lerp(c, dyeLike(c, l, meanL, toHsl(goal)), (a * a * 0.9f * strength).coerceIn(0f, 1f))
        }
    }

    // ------------------------------------------------------------------ Live mirror

    /** A second model instance used only by the camera thread. */
    private val liveSegmenter by lazy {
        ImageSegmenter.createFromOptions(
            context,
            ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("hair_segmenter.tflite").build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputConfidenceMasks(true)
                .setOutputCategoryMask(false)
                .build(),
        )
    }
    private var lastLiveMask: FloatArray? = null

    /**
     * Recolours the hair in one camera frame (call from a single background thread).
     * The mask is blended with the previous frame's so the colour doesn't flicker.
     */
    fun liveFrame(frame: Bitmap, colourId: String?): Bitmap {
        val w = frame.width
        val h = frame.height
        val px = IntArray(w * h).also { frame.getPixels(it, 0, w, 0, 0, w, h) }
        if (colourId != null) {
            val mask = hairMask(frame, liveSegmenter)
            lastLiveMask?.takeIf { it.size == mask.size }?.let { prev ->
                for (i in mask.indices) mask[i] = mask[i] * 0.6f + prev[i] * 0.4f
            }
            lastLiveMask = mask
            recolour(px, w, h, mask, colourId)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    // ------------------------------------------------------------------ Look Studio (manual editing)

    enum class Tool { DYE, TRIM, GROW, SHAVE, BEARD, RESTORE }

    /** Prepares a photo for hand editing: finds the hair, the face and what's behind the hair. */
    suspend fun startSession(photo: Bitmap): LookSession = withContext(Dispatchers.Default) {
        val src = photo.copy(Bitmap.Config.ARGB_8888, true)
        val w = src.width
        val h = src.height
        val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val hair = hairMask(src)
        val face = runCatching {
            faceDetector.process(InputImage.fromBitmap(src, 0)).await().maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        }.getOrNull()
        val g = face?.let { Geometry.of(it, w, h) }
        val skin = if (g != null) skinTone(px, w, g) else 0xFFC69C7A.toInt()
        LookSession(
            w = w, h = h, original = px, hair = hair, plate = cleanPlate(px, w, h, hair), skin = skin,
            hairBase = hairColour(px, hair), source = HairSource.from(px, w, h, hair, g?.brow ?: (h * 0.4f)),
            isHead = { x, y -> g != null && (insideHead(x, y, g) || (y < g.chin && abs(x - g.cx) < g.faceW * 0.55f)) },
            stylist = this@FreeStylist,
        )
    }

    /**
     * One photo being edited by hand. Every brush stroke blends softly towards a target:
     * background/skin (Trim), the person's own hair texture (Grow), skin (Shave), stubble (Beard),
     * a dye colour (Dye) or the original photo (Restore). Up to 15 steps can be undone.
     */
    class LookSession internal constructor(
        val w: Int,
        val h: Int,
        private val original: IntArray,
        private val hair: FloatArray,
        private val plate: IntArray,
        private val skin: Int,
        private val hairBase: Int,
        private val source: HairSource?,
        private val isHead: (Int, Int) -> Boolean,
        private val stylist: FreeStylist,
    ) {
        val pixels: IntArray = original.copyOf()
        val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
        private val history = ArrayDeque<IntArray>()
        private val meanHairL: Float = run {
            var s = 0f; var n = 0
            for (i in original.indices) if (hair[i] > 0.6f) { s += luma(original[i]); n++ }
            if (n == 0) 0.25f else s / n / 255f
        }
        val canUndo get() = history.isNotEmpty()

        fun beginStroke() {
            history.addLast(pixels.copyOf())
            if (history.size > 15) history.removeFirst()
        }

        fun undo() {
            val prev = history.removeLastOrNull() ?: return
            System.arraycopy(prev, 0, pixels, 0, pixels.size)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        fun reset() {
            beginStroke()
            System.arraycopy(original, 0, pixels, 0, pixels.size)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        /** Recolours all detected hair at once. */
        fun dyeAllHair(colourId: String) {
            beginStroke()
            stylist.recolour(pixels, w, h, hair, colourId)
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        /** One soft, round dab of a tool at (cx, cy) with the given radius, in photo pixels. */
        fun stamp(tool: Tool, cx: Float, cy: Float, radius: Float, dyeColour: Int?) {
            val r = max(2f, radius)
            val x0 = max(0, (cx - r).toInt()); val x1 = min(w - 1, (cx + r).toInt())
            val y0 = max(0, (cy - r).toInt()); val y1 = min(h - 1, (cy + r).toInt())
            if (x0 > x1 || y0 > y1) return
            val goalHsl = dyeColour?.let { toHsl(it) }
            for (y in y0..y1) for (x in x0..x1) {
                val dx = x - cx; val dy = y - cy
                val d = kotlin.math.sqrt(dx * dx + dy * dy) / r
                if (d >= 1f) continue
                val fall = (1f - d * d) * 0.45f   // soft edge; repeated dabs build up
                val i = y * w + x
                val c = pixels[i]
                pixels[i] = when (tool) {
                    Tool.TRIM -> lerp(c, if (isHead(x, y)) shadeSkin(x, y) else plate[i], fall)
                    Tool.GROW -> lerp(c, source?.sample(x, y, Texture.CURLY) ?: hairBase, fall)
                    Tool.SHAVE -> lerp(c, lerp(shadeSkin(x, y), original[i], 0.12f), fall)
                    Tool.BEARD -> lerp(c, lerp(skin, scale(hairBase, 0.8f), 0.35f + 0.55f * noise(x * 0.8f, y * 0.8f)), fall * 0.8f)
                    Tool.RESTORE -> lerp(c, original[i], fall)
                    Tool.DYE -> {
                        val hsl = goalHsl ?: return
                        val l = luma(c) / 255f
                        // Only hair takes the dye; skin, clothes and background stay as they are.
                        val onHair = ((hair[i] - 0.2f) / 0.5f).coerceIn(0f, 1f)
                        lerp(c, dyeLike(c, l, meanHairL, hsl), fall * onHair)
                    }
                }
            }
            val bw = x1 - x0 + 1
            bitmap.setPixels(pixels, y0 * w + x0, w, x0, y0, bw, y1 - y0 + 1)
        }

        private fun shadeSkin(x: Int, y: Int) = scale(skin, 0.94f + 0.08f * noise(x * 0.05f, y * 0.05f))
    }


    // ------------------------------------------------------------------ small helpers

    companion object {
        /** Dye swatches for the Look Studio palette (same ids as the style catalogue). */
        fun dye(id: String): Int? = COLOURS[id]

        private val BEARDS = setOf("clean", "stubble", "short_boxed", "full_beard", "goatee", "van_dyke", "handlebar")
        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to 1, -1 to 1, 1 to -1)
        private val COLOURS = mapOf(
            "black" to 0xFF141110.toInt(), "dark_brown" to 0xFF3B2418.toInt(), "chestnut" to 0xFF6B3A1E.toInt(),
            "burgundy" to 0xFF5A1428.toInt(), "copper" to 0xFFB4501E.toInt(), "henna" to 0xFF9A3A16.toInt(),
            "honey" to 0xFFC9933F.toInt(), "platinum" to 0xFFE8E2D0.toInt(), "ash_grey" to 0xFF9A9A98.toInt(),
            "silver" to 0xFFC8CCD0.toInt(), "highlights" to 0xFFC08A4A.toInt(), "balayage" to 0xFFC9933F.toInt(),
            "ombre" to 0xFFE2C990.toInt(), "pastel_pink" to 0xFFF2A7C3.toInt(), "blue" to 0xFF1F4FD1.toInt(),
            "purple" to 0xFF5B2A8C.toInt(),
        )

        /**
         * Colours one hair pixel the way real dye behaves: the hair keeps its own texture and
         * shadows, darker strands pick up only a hint of colour, and the colour shows most in
         * the lighter, shiny strands. Lightening shades (blonde, platinum) lift the whole lock.
         */
        fun dyeLike(c: Int, l: Float, meanL: Float, goal: FloatArray): Int {
            val h = goal[0]; val s = goal[1]; val tl = goal[2]
            val newL = if (tl > meanL + 0.08f) {
                (l + (tl - meanL) * 0.85f).coerceIn(0.02f, 0.97f)                // lighten, keep texture
            } else {
                // Coloured dyes (burgundy, copper…) lift very dark hair a touch so the shade shows.
                val lift = if (s > 0.3f) 0.07f else 0f
                (l * 0.75f + (l * tl / max(0.05f, meanL)).coerceIn(0f, 1f) * 0.25f + lift).coerceIn(0.02f, 0.97f)
            }
            // Darker pixels show less colour, brighter ones more, but never none at all.
            val showing = 0.3f + 0.7f * ((newL - 0.04f) / 0.35f).coerceIn(0f, 1f)
            val dyed = fromHsl(h, (s * 0.85f * showing).coerceIn(0f, 1f), newL)
            // Keep a little of the original grain so it doesn't look painted on.
            return lerp(dyed, c, 0.12f)
        }

        fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        fun luma(c: Int) = 0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)
        fun scale(c: Int, f: Float) = rgb((((c shr 16) and 255) * f).toInt(), (((c shr 8) and 255) * f).toInt(), ((c and 255) * f).toInt())
        fun lerp(a: Int, b: Int, t: Float): Int {
            val u = t.coerceIn(0f, 1f)
            fun ch(s: Int) = (((a shr s) and 255) + (((b shr s) and 255) - ((a shr s) and 255)) * u).toInt()
            return rgb(ch(16), ch(8), ch(0))
        }

        /** Smooth value noise in 0..1. */
        fun noise(x: Float, y: Float): Float {
            val xi = floor(x).toInt(); val yi = floor(y).toInt()
            val xf = x - xi; val yf = y - yi
            fun hash(a: Int, b: Int): Float {
                var n = a * 374761393 + b * 668265263
                n = (n xor (n shr 13)) * 1274126177
                return ((n xor (n shr 16)) and 0x7fffffff) / 2147483647f
            }
            val u = xf * xf * (3 - 2 * xf); val v = yf * yf * (3 - 2 * yf)
            val a = hash(xi, yi) + (hash(xi + 1, yi) - hash(xi, yi)) * u
            val b = hash(xi, yi + 1) + (hash(xi + 1, yi + 1) - hash(xi, yi + 1)) * u
            return a + (b - a) * v
        }

        fun toHsl(c: Int): FloatArray {
            val r = ((c shr 16) and 255) / 255f; val g = ((c shr 8) and 255) / 255f; val b = (c and 255) / 255f
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val l = (mx + mn) / 2
            if (mx == mn) return floatArrayOf(0f, 0f, l)
            val d = mx - mn
            val s = if (l > 0.5f) d / (2 - mx - mn) else d / (mx + mn)
            val h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            } / 6f
            return floatArrayOf(h, s, l)
        }

        fun fromHsl(h: Float, s: Float, l: Float): Int {
            if (s == 0f) return rgb((l * 255).toInt(), (l * 255).toInt(), (l * 255).toInt())
            val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
            val p = 2 * l - q
            fun hue(t0: Float): Float {
                var t = t0
                if (t < 0) t += 1f
                if (t > 1) t -= 1f
                return when {
                    t < 1f / 6 -> p + (q - p) * 6 * t
                    t < 1f / 2 -> q
                    t < 2f / 3 -> p + (q - p) * (2f / 3 - t) * 6
                    else -> p
                }
            }
            return rgb((hue(h + 1f / 3) * 255).toInt(), (hue(h) * 255).toInt(), (hue(h - 1f / 3) * 255).toInt())
        }
    }
}
