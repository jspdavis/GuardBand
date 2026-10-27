package com.example.guardband.ui.bandviewer

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * Where the turntable's frames live and which files count as frames.
 *
 * **The folder is `app/src/main/assets/band_turntable/`**, files named
 * `band_turn_000.webp`, `band_turn_001.webp`, ... in turning order. Assets
 * rather than `res/drawable-nodpi`: an asset folder can be listed at run
 * time, so dropping in 36 or 72 frames needs no code change and no constant,
 * and the files are used byte for byte (no density scaling, no `R` ids, no
 * resource shrinker to drop "unused" frames). docs/band-viewer.md has the
 * full frame spec.
 */
internal object TurntableFrames {

    /** The asset folder the frames are read from. */
    const val FOLDER = "band_turntable"

    private val NAME = Regex("""band_turn_(\d{3})\.webp""")

    /**
     * The frame files among [names], in turning order (by their number, so
     * `band_turn_010` follows `band_turn_009`). Anything else in the folder,
     * a README or a stray PNG, is ignored. A gap in the numbering is not an
     * error: the frames are played in order, and the count is how many there
     * are.
     */
    fun select(names: List<String>): List<String> =
        names.mapNotNull { name -> NAME.matchEntire(name)?.let { it.groupValues[1].toInt() to name } }
            .sortedBy { it.first }
            .map { it.second }
}

/**
 * Frames the viewer can show. [decode] runs off the main thread and may
 * return null (a missing or broken file); the viewer then shows the
 * placeholder for that frame, never an error.
 */
internal interface FrameSource {
    val count: Int
    fun decode(index: Int): Bitmap?
}

/** [TurntableFrames.FOLDER] in the app's assets. Listed once, decoded one frame at a time. */
internal class AssetFrameSource(private val assets: AssetManager) : FrameSource {

    private val files: List<String> = try {
        TurntableFrames.select(assets.list(TurntableFrames.FOLDER)?.toList().orEmpty())
    } catch (e: IOException) {
        emptyList()
    }

    override val count: Int get() = files.size

    override fun decode(index: Int): Bitmap? {
        val file = files.getOrNull(index) ?: return null
        return try {
            assets.open("${TurntableFrames.FOLDER}/$file").use { BitmapFactory.decodeStream(it) }
        } catch (e: IOException) {
            null
        } catch (e: OutOfMemoryError) {
            // One frame too big must not take the app down; it shows the placeholder.
            null
        }
    }
}
