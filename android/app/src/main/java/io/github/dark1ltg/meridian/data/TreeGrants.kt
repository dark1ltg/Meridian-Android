package io.github.dark1ltg.meridian.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

/**
 * Maps library folder keys → persistable SAF tree URIs so Remove can drop the
 * grant, and Add can keep using DocumentFile after a restart.
 */
object TreeGrants {
    private const val PREFS = "meridian_tree_grants"
    private const val KEY = "map"

    fun remember(context: Context, folderKey: String, treeUri: String) {
        val key = folderKey.trim()
        val uri = treeUri.trim()
        if (key.isEmpty() || uri.isEmpty()) return
        val map = load(context)
        map[key] = uri
        save(context, map)
    }

    fun uriFor(context: Context, folderKey: String): String? =
        load(context)[folderKey.trim()]

    fun forget(context: Context, folderKey: String) {
        val map = load(context)
        if (map.remove(folderKey.trim()) != null) {
            save(context, map)
        }
    }

    fun release(context: Context, folderKey: String) {
        val uri = uriFor(context, folderKey) ?: folderKey.takeIf { it.startsWith("content:") }
        forget(context, folderKey)
        if (uri.isNullOrBlank()) return
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalArgumentException) {
        }
    }

    fun folderKeyForTree(treeUri: String): String {
        val path = FolderPaths.fromTreeUri(treeUri)
        if (!path.isNullOrBlank()) return path
        return treeUri.trim()
    }

    private fun load(context: Context): MutableMap<String, String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val out = linkedMapOf<String, String>()
            obj.keys().forEach { k -> out[k] = obj.optString(k, "") }
            out.filterValues { it.isNotBlank() }.toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun save(context: Context, map: Map<String, String>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, obj.toString())
            .apply()
    }
}
