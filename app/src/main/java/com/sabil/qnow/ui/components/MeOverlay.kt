package com.sabil.qnow.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.location.Location
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * "You are here" on the map: the customer's profile picture (or a person figure when there's no
 * picture) in a pin with a soft pulsing ring and a "You" tag. It's kept as the top overlay so
 * nearby shop pins never hide it.
 */
class MeOverlay(context: Context, map: MapView, private val brand: Int) :
    MyLocationNewOverlay(GpsMyLocationProvider(context), map) {

    private val density = context.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val point = android.graphics.Point()
    private var photo: Bitmap? = null
    private val view = map

    /** Use the customer's own picture inside the pin. */
    fun setPhoto(bitmap: Bitmap?) {
        photo = bitmap
        view.postInvalidate()
    }

    override fun drawMyLocation(canvas: Canvas, pj: Projection, lastFix: Location) {
        pj.toPixels(GeoPoint(lastFix.latitude, lastFix.longitude), point)
        val x = point.x.toFloat()
        val y = point.y.toFloat()
        val r = 22f * density            // avatar radius
        val tip = 12f * density          // pointer below the avatar
        val cy = y - r - tip             // avatar centre sits above the exact spot

        // Pulsing ring on the ground at the exact location (1.6 s loop).
        val phase = (SystemClock.uptimeMillis() % 1600L) / 1600f
        paint.style = Paint.Style.FILL
        paint.color = brand
        paint.alpha = ((1f - phase) * 90).toInt()
        canvas.drawCircle(x, y, (8f + 26f * phase) * density, paint)
        paint.alpha = 255
        canvas.drawCircle(x, y, 5f * density, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(x, y, 2.2f * density, paint)

        // Gentle bob so it feels alive.
        val bob = sin(phase * 2 * PI).toFloat() * 1.5f * density
        val c = cy + bob

        // Pin body: white outline, brand ring, pointer.
        val pin = Path().apply {
            addCircle(x, c, r + 4f * density, Path.Direction.CW)
            moveTo(x - 9f * density, c + r)
            lineTo(x, y - 3f * density + bob)
            lineTo(x + 9f * density, c + r)
            close()
        }
        paint.color = android.graphics.Color.WHITE
        paint.setShadowLayer(6f * density, 0f, 2f * density, 0x55000000)
        canvas.drawPath(pin, paint)
        paint.clearShadowLayer()
        paint.color = brand
        canvas.drawCircle(x, c, r + 1.5f * density, paint)

        val p = photo
        if (p != null) {
            // Profile picture, centre-cropped into the circle.
            val shader = BitmapShader(p, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val scale = (2 * r) / min(p.width, p.height).toFloat()
            shader.setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate(x - p.width * scale / 2, c - p.height * scale / 2)
            })
            photoPaint.shader = shader
            canvas.drawCircle(x, c, r - 1f * density, photoPaint)
        } else {
            // Person figure: head + shoulders.
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(x, c - r * 0.28f, r * 0.30f, paint)
            canvas.save()
            canvas.clipPath(Path().apply { addCircle(x, c, r - 1f * density, Path.Direction.CW) })
            canvas.drawOval(RectF(x - r * 0.62f, c + r * 0.12f, x + r * 0.62f, c + r * 1.2f), paint)
            canvas.restore()
        }

        // "You" tag above the pin.
        paint.textSize = 11f * density
        paint.typeface = Typeface.DEFAULT_BOLD
        val label = "You"
        val w = paint.measureText(label) + 12f * density
        val top = c - r - 4f * density - 20f * density
        paint.color = brand
        canvas.drawRoundRect(RectF(x - w / 2, top, x + w / 2, top + 17f * density), 9f * density, 9f * density, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(label, x, top + 12.5f * density, paint)
        paint.textAlign = Paint.Align.LEFT

        // Keep the pulse moving (about 25 frames a second, only while the map is on screen).
        view.postInvalidateDelayed(40)
    }
}
