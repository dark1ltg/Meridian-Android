package io.github.dark1ltg.meridian.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Unique existing directories that hold MediaStore audio. */
object MediaStoreMusic {
    fun directories(context: Context): List<String> {
        return collapseParents(filePaths(context))
    }

    fun filePaths(context: Context): List<String> {
        val uri = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf<String>().apply {
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= 29) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                add(MediaStore.Audio.Media.VOLUME_NAME)
            }
        }
        val out = ArrayList<String>()
        context.contentResolver.query(
            uri,
            projection.toTypedArray(),
            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
            null,
            null,
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val relCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val volCol = cursor.getColumnIndex(MediaStore.Audio.Media.VOLUME_NAME)
            while (cursor.moveToNext()) {
                val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                val rel = if (relCol >= 0) cursor.getString(relCol) else null
                val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                val volume = if (volCol >= 0) cursor.getString(volCol) else null
                val path = resolvePath(data, volume, rel, name) ?: continue
                out += path
            }
        }
        return out
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
