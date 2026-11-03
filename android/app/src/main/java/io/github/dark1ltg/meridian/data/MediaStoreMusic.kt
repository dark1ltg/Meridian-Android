package io.github.dark1ltg.meridian.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

data class MediaStoreTrack(
    /** Filesystem path if readable; otherwise a content://media URI. */
    val playPath: String,
    val title: String,
    /** MediaStore DURATION in ms when known (0 if missing). */
    val durationMs: Long = 0L,
)

/** MediaStore audio → folders for os.walk and/or content URI tracks. */
object MediaStoreMusic {
    fun directories(context: Context): List<String> {
        return collapseParents(filePaths(context))
    }

    /**
     * Every music row as something Meridian can play. Prefer a readable file
     * path (so scan/Listen see real tags); fall back to the MediaStore URI
     * so scoped-storage phones still get a library after Allow.
     */
    fun tracks(context: Context, max: Int = SafIngest.MAX_FILES): List<MediaStoreTrack> {
        val uri = collection()
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
        ).apply {
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
        }
        val out = ArrayList<MediaStoreTrack>()
        try {
            context.contentResolver.query(
                uri,
                projection.toTypedArray(),
                "${MediaStore.Audio.Media.IS_MUSIC}!=0",
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val volCol = cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext() && out.size < max) {
                    val id = cursor.getLong(idCol)
                    val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val rel = if (relCol >= 0) cursor.getString(relCol) else null
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val title = if (titleCol >= 0) cursor.getString(titleCol) else null
                    val volume = if (volCol >= 0) cursor.getString(volCol) else null
                    val durationMs = if (durCol >= 0) cursor.getLong(durCol).coerceAtLeast(0L) else 0L
                    val guessed = resolvePath(data, volume, rel, name)
                    val file = guessed?.let { File(it) }
                    val playPath = if (file != null && file.isFile && file.canRead()) {
                        file.absolutePath
                    } else {
                        ContentUris.withAppendedId(uri, id).toString()
                    }
                    val label = (title ?: name ?: SafIngest.displayTitle(File(playPath).name))
                        .ifBlank { "Track" }
                    out += MediaStoreTrack(playPath, SafIngest.displayTitle(label), durationMs)
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        }
        return out
    }

    fun filePaths(context: Context): List<String> {
        return tracks(context).map { it.playPath }.filter { !it.startsWith("content:") }
    }

    private fun collection() = if (Build.VERSION.SDK_INT >= 29) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    /** DATA is often null on API 29+; rebuild from volume + relative path. */
    fun resolvePath(
        data: String?,
        volume: String?,
        relativePath: String?,
        displayName: String?,
    ): String? {
        if (!data.isNullOrBlank()) return data
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val rel = (relativePath ?: "").trim().trimStart('/').trimEnd('/')
        val base = volumeBase(volume)
        return if (rel.isEmpty()) "$base/$name" else "$base/$rel/$name"
    }

    fun volumeBase(volume: String?): String {
        val v = volume?.trim().orEmpty()
        return if (v.isEmpty() || v == "external_primary" || v == "external") {
            "/storage/emulated/0"
        } else {
            "/storage/$v"
        }
    }

    fun collapseParents(filePaths: List<String>): List<String> {
        val dirs = filePaths.mapNotNull { path ->
            File(path).parentFile?.let { parent ->
                if (parent.isDirectory || parent.absolutePath.startsWith("/storage/")) {
                    parent.absolutePath
                } else {
                    null
                }
            }
        }.distinct()
        return dropNested(dirs)
    }

    fun dropNested(directories: List<String>): List<String> {
        val sorted = directories.map { it.trimEnd('/') }.distinct().sortedBy { it.length }
        val kept = mutableListOf<String>()
        for (dir in sorted) {
            if (kept.none { dir == it || dir.startsWith("$it/") }) {
                kept += dir
            }
        }
        return kept
    }
}
