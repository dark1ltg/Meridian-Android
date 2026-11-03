package io.github.dark1ltg.meridian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class EngineSnapshotPlayingTest {
    private fun row(id: Int, title: String = "t$id") = TrackRow(
        id = id,
        path = "/music/$id.mp3",
        title = title,
        artist = "a",
        album = "al",
        durationMs = 180_000L,
        valence = 0.5f,
        energy = 0.5f,
        confidence = 1f,
        lowTrust = false,
        pinned = false,
        loved = false,
        quadrant = "now",
    )

    @Test
    fun withPlayingTrackUpdatesCurrentAndQueueIndex() {
        val a = row(1, "Motor Head")
        val b = row(2, "Next")
        val snap = emptySnapshot().copy(queue = listOf(a, b), queueIndex = 0, current = a)
        val next = snap.withPlayingTrack(b)
        assertEquals(2, next.current?.id)
        assertEquals("Next", next.current?.title)
        assertEquals(1, next.queueIndex)
        assertSame(a, next.queue[0])
    }

    @Test
    fun withPlayingTrackKeepsIndexWhenNotInQueue() {
        val a = row(1)
        val orphan = row(9, "Orphan")
        val snap = emptySnapshot().copy(queue = listOf(a), queueIndex = 0, current = a)
        val next = snap.withPlayingTrack(orphan)
        assertEquals(9, next.current?.id)
        assertEquals(0, next.queueIndex)
    }
}
