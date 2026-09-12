package io.github.dark1ltg.meridian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreMusicTest {
    @Test
    fun dropNestedKeepsParentsOnly() {
        val kept = MediaStoreMusic.dropNested(
            listOf(
                "/storage/emulated/0/Music",
                "/storage/emulated/0/Music/Album",
                "/storage/emulated/0/Download",
            ),
        )
        assertEquals(listOf("/storage/emulated/0/Download", "/storage/emulated/0/Music"), kept.sorted())
    }

    @Test
    fun collapseParentsFromFiles() {
        val dirs = MediaStoreMusic.collapseParents(
            listOf(
                "/tmp/does-not-exist/track.mp3",
            ),
        )
        assertTrue(dirs.isEmpty())
    }

    @Test
    fun resolvePathPrefersData() {
        assertEquals(
            "/sdcard/a.mp3",
            MediaStoreMusic.resolvePath("/sdcard/a.mp3", "external_primary", "Music/", "b.mp3"),
        )
    }

    @Test
    fun resolvePathFromRelativeOnPrimary() {
        assertEquals(
            "/storage/emulated/0/Music/Album/song.flac",
            MediaStoreMusic.resolvePath(null, "external_primary", "Music/Album/", "song.flac"),
        )
    }

    @Test
    fun resolvePathFromSdVolume() {
        assertEquals(
            "/storage/1A2B-3C4D/Music/x.mp3",
            MediaStoreMusic.resolvePath(null, "1A2B-3C4D", "Music/", "x.mp3"),
        )
    }
}

class SafIngestTest {
    @Test
    fun treeKeyIsStable() {
        val a = SafIngest.treeKey("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        val b = SafIngest.treeKey("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        assertEquals(a, b)
        assertEquals(16, a.length)
    }

    @Test
    fun displayTitleStripsExtension() {
        assertEquals("Glow", SafIngest.displayTitle("Glow.flac"))
        assertEquals("track", SafIngest.displayTitle("track"))
    }

    @Test
    fun maxFilesMatchesWalkBudget() {
        assertEquals(8_000, SafIngest.MAX_FILES)
    }
}
