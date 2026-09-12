package io.github.dark1ltg.meridian.data

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Map an SAF document tree URI onto a real filesystem path Python can walk. */
object FolderPaths {
    fun fromTreeUri(uri: String): String? {
        val raw = uri.trim()
        if (raw.startsWith("/") && !raw.startsWith("//")) return raw
        val marker = "/tree/"
        val idx = raw.indexOf(marker)
        val encoded = if (idx >= 0) raw.substring(idx + marker.length) else raw
        val doc = URLDecoder.decode(encoded.substringBefore('?'), StandardCharsets.UTF_8.name())
        return fromDocumentId(doc)
    }

    fun fromDocumentId(docId: String): String? {
        val id = docId.trim()
        if (id.isEmpty()) return null
        if (id.startsWith("/")) return id
        val colon = id.indexOf(':')
        if (colon < 0) return null
        val volume = id.substring(0, colon)
        val rel = id.substring(colon + 1).trimStart('/')
        val base = if (volume == "primary" || volume.isEmpty()) {
            "/storage/emulated/0"
        } else {
            "/storage/$volume"
        }
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    fun displayName(path: String): String {
        val trimmed = path.trimEnd('/')
        return trimmed.substringAfterLast('/').ifBlank { trimmed }
    }
}
