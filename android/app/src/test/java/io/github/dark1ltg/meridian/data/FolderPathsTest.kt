package io.github.dark1ltg.meridian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderPathsTest {
    @Test
    fun primaryMusicTree() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        assertEquals("/storage/emulated/0/Music", FolderPaths.fromTreeUri(uri))
    }

    @Test
    fun nestedDownload() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload%2FAlbums"
        assertEquals("/storage/emulated/0/Download/Albums", FolderPaths.fromTreeUri(uri))
    }

    @Test
    fun sdCardVolume() {
        assertEquals(
            "/storage/1A2B-3C4D/Music",
            FolderPaths.fromDocumentId("1A2B-3C4D:Music"),
        )
    }

    @Test
    fun alreadyAPath() {
        assertEquals("/storage/emulated/0/Music", FolderPaths.fromTreeUri("/storage/emulated/0/Music"))
    }

    @Test
    fun garbage() {
        assertNull(FolderPaths.fromDocumentId("nope"))
    }
}
