package io.github.dark1ltg.meridian.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

data class SafTrack(
    val uri: String,
    val title: String,
)

data class SafIngestResult(
    val folders: List<String>,
    val uriTracks: List<SafTrack>,
)

/**
 * Turn an SAF tree into folders Python can walk, or content URIs when the
 * document has no readable `/storage/…` path (USB/SD without a mount).
 */
object SafIngest {
    const val MAX_FILES = 8_000
    private val audioExt = setOf("mp3", "flac", "ogg", "opus", "m4a", "wav", "aac", "wma", "aiff")

    fun ingest(context: Context, tree: Uri, maxFiles: Int = MAX_FILES): SafIngestResult {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return SafIngestResult(emptyList(), emptyList())
        val readable = LinkedHashSet<String>()
        val uriTracks = ArrayList<SafTrack>()
        var seen = 0
        walk(root, "") { doc, _ ->
            if (seen >= maxFiles) return@walk
            val name = doc.name ?: return@walk
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in audioExt && doc.type?.startsWith("audio/") != true) return@walk
            seen += 1
            val guessed = guessPath(doc)
            if (guessed != null) {
                File(guessed).parentFile?.absolutePath?.let { readable += it }
            } else {
                uriTracks += SafTrack(doc.uri.toString(), displayTitle(name))
            }
        }
        return SafIngestResult(
            folders = MediaStoreMusic.dropNested(readable.toList()),
            uriTracks = uriTracks.distinctBy { it.uri },
        )
    }

    fun displayTitle(name: String): String {
        val stem = name.substringBeforeLast('.', missingDelimiterValue = name).trim()
        return stem.ifBlank { name }
    }

    fun guessPath(doc: DocumentFile): String? {
        val id = android.provider.DocumentsContract.getDocumentId(doc.uri) ?: return null
        val guessed = FolderPaths.fromDocumentId(id) ?: FolderPaths.fromTreeUri(doc.uri.toString())
        return guessed?.takeIf { File(it).canRead() }
    }

    fun treeKey(uri: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val hex = md.digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return hex.take(16)
    }

    private fun walk(dir: DocumentFile, rel: String, visit: (DocumentFile, String) -> Unit) {
        dir.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            val next = if (rel.isEmpty()) name else "$rel/$name"
            if (child.isDirectory) {
                walk(child, next, visit)
            } else {
                visit(child, next)
            }
        }
    }
}
