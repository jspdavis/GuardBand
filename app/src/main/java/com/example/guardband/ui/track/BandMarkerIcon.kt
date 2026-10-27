package com.example.guardband.ui.track

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleOwner
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.example.guardband.R
import com.example.guardband.ui.avatar.AvatarPhoto
import kotlin.math.max

/**
 * Draws the band's marker: a round face (the photo, or initials on brand
 * green) in a white ring with a soft shadow, and a short pointer underneath
 * whose tip is the band's position. See `docs/osmdroid.md` §5.4.
 *
 * Geometry, in dp: a 44 dp face inside a 3 dp ring (50 dp across), a pointer
 * 8 dp tall and 14 dp wide below it, and 4 dp of room on every side for the
 * shadow. The bitmap is 58 × 66 dp and the tip sits at its horizontal centre,
 * 62 dp down: give the Marker [anchorU], [anchorV], which are computed from
 * the rounded pixel size so the tip lands on the position to the pixel.
 *
 * **[bind] draws only when the face changes.** The initials version is drawn
 * at once, on the calling thread (one small bitmap), and handed to [onIcon],
 * so the marker is never blank and nothing waits on the network. If the face
 * has a photo, Coil loads it off the main thread and [Composer.photo] runs
 * there too, as a Coil [Transformation]; the composed marker lands in Coil's
 * memory cache keyed by the link, so a re-created Track view gets it back
 * without decoding or drawing again. A failed or offline load keeps the
 * initials. Nothing here recycles a bitmap: Coil owns the photo ones, and an
 * initials one lives as long as the drawable that holds it.
 *
 * The link is never logged, and nothing is stored beyond Coil's own caches,
 * the same ones the Device tab's avatar already uses.
 *
 * One per Track view. Call [release] from `onDestroyView`.
 */
internal class BandMarkerIcon(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onIcon: (Drawable) -> Unit,
) {

    private val composer = Composer(context)

    /** Where the pointer's tip is in the icon; the Marker's anchor. */
    val anchorU: Float get() = composer.anchorU
    val anchorV: Float get() = composer.anchorV

    private var face: BandMarkerFace? = null
    private var request: Disposable? = null

    fun bind(next: BandMarkerFace) {
        if (next == face) return
        face = next
        request?.dispose()
        request = null

        onIcon(drawable(composer.initials(next.initials)))

        val url = next.photoUrl ?: return
        request = context.imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(url)
                .size(AvatarPhoto.SIZE_PX)
                .allowHardware(false)
                .transformations(composer)
                .lifecycle(lifecycleOwner)
                .target(onSuccess = { result ->
                    // Only the face that asked; a newer bind disposed the rest.
                    if (face == next) onIcon(result)
                })
                .build()
        )
    }

    fun release() {
        request?.dispose()
        request = null
        face = null
    }

    private fun drawable(bitmap: Bitmap): Drawable = BitmapDrawable(context.resources, bitmap)

    /** Draws both versions of the marker. Also Coil's [Transformation] for the photo. */
    private class Composer(context: Context) : Transformation {

        private val density = context.resources.displayMetrics.density
        private fun px(dp: Float) = dp * density

        private val pad = px(PAD_DP)
        private val ringRadius = px(FACE_DP / 2 + RING_DP)
        private val faceRadius = px(FACE_DP / 2)
        private val cx = pad + ringRadius
        private val cy = pad + ringRadius
        private val tipY = cy + ringRadius + px(POINTER_DP)
        private val width = (2 * (pad + ringRadius)).toInt()
        private val height = (tipY + pad).toInt()

        /** The tip as fractions of the bitmap, from the rounded pixel size, not the dp. */
        val anchorU = cx / width
        val anchorV = tipY / height

        private val brandGreen = ContextCompat.getColor(context, R.color.brand_green)
        private val typeface = ResourcesCompat.getFont(context, R.font.poppins_semibold)
        private val glyph = ContextCompat.getDrawable(context, R.drawable.ic_profile)?.mutate()

        /** Density-specific: a marker drawn for one screen is the wrong size on another. */
        override val cacheKey = "GuardBandMarker-v1-${context.resources.displayMetrics.densityDpi}"

        override suspend fun transform(input: Bitmap, size: Size): Bitmap = photo(input)

        fun initials(text: String): Bitmap = compose { canvas ->
            canvas.drawCircle(cx, cy, faceRadius, fill(brandGreen))
            if (text.isEmpty()) {
                drawGlyph(canvas)
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textAlign = Paint.Align.CENTER
                    typeface = this@Composer.typeface
                    textSize = px(if (text.length > 2) 14f else 17f)
                }
                val baseline = cy - (paint.descent() + paint.ascent()) / 2
                canvas.drawText(text, cx, baseline, paint)
            }
        }

        /** [source] centre-cropped into the face circle. Runs on Coil's thread. */
        fun photo(source: Bitmap): Bitmap = compose { canvas ->
            val diameter = 2 * faceRadius
            val scale = diameter / minOf(source.width, source.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    cx - source.width * scale / 2,
                    cy - source.height * scale / 2
                )
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    .apply { setLocalMatrix(matrix) }
            }
            canvas.drawCircle(cx, cy, faceRadius, paint)
        }

        /** The ring, pointer and shadow, then [face] on top. */
        private fun compose(face: (Canvas) -> Unit): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val halfBase = px(POINTER_BASE_DP / 2)
            // The pointer starts inside the ring, so ring and pointer are one shape.
            val shoulderY = cy + ringRadius - px(RING_DP)
            val outline = Path().apply {
                addCircle(cx, cy, ringRadius, Path.Direction.CW)
                moveTo(cx - halfBase, shoulderY)
                lineTo(cx + halfBase, shoulderY)
                lineTo(cx, tipY)
                close()
            }
            val ring = fill(Color.WHITE).apply {
                setShadowLayer(px(SHADOW_RADIUS_DP), 0f, px(SHADOW_DY_DP), SHADOW_COLOR)
            }
            canvas.drawPath(outline, ring)

            face(canvas)
            return bitmap
        }

        private fun drawGlyph(canvas: Canvas) {
            val drawable = glyph ?: return
            val half = max(faceRadius * 0.55f, 1f)
            drawable.setTint(Color.WHITE)
            drawable.setBounds(
                (cx - half).toInt(), (cy - half).toInt(),
                (cx + half).toInt(), (cy + half).toInt()
            )
            drawable.draw(canvas)
        }

        private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
    }

    private companion object {
        const val FACE_DP = 44f
        const val RING_DP = 3f
        const val POINTER_DP = 8f
        const val POINTER_BASE_DP = 14f
        const val PAD_DP = 4f
        const val SHADOW_RADIUS_DP = 3f
        const val SHADOW_DY_DP = 1f
        const val SHADOW_COLOR = 0x55000000
    }
}
