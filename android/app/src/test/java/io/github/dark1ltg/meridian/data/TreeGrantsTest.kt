package io.github.dark1ltg.meridian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TreeGrantsTest {
    @Test
    fun folderKeyPrefersFilesystemPath() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertEquals("/storage/emulated/0/Music", TreeGrants.folderKeyForTree(uri))
    }

    @Test
    fun folderKeyFallsBackToUri() {
        val uri = "content://com.example.provider/tree/abc"
        assertEquals(uri, TreeGrants.folderKeyForTree(uri))
    }
}
