package io.github.dark1ltg.meridian.ui

import io.github.dark1ltg.meridian.data.JobProgress
import io.github.dark1ltg.meridian.data.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTest {
    @Test
    fun clockBandPhraseUsesSpokenTime() {
        assertEquals("morning", clockBandPhrase("Dawn"))
        assertEquals("daytime", clockBandPhrase("Day"))
        assertEquals("evening", clockBandPhrase("Dusk"))
        assertEquals("night", clockBandPhrase("Night"))
        assertEquals("this hour", clockBandPhrase("  "))
    }

    @Test
    fun queueSourceLineNamesAimAndBand() {
        assertEquals(
            "From where you aimed · evening",
            queueSourceLine(queueStale = false, bandLabel = "Dusk"),
        )
        assertEquals(
            "Lens moved · Renew or Play Context to sync",
            queueSourceLine(queueStale = true, bandLabel = "Night"),
        )
    }

    @Test
    fun emptyMatrixCopyExplainsEachBand() {
        assertEquals("Nothing parked yet", emptyMatrixCopy("SHELF"))
        assertEquals("Nothing closest yet", emptyMatrixCopy("NOW"))
        assertEquals("Nothing to sit with yet", emptyMatrixCopy("DEEP"))
        assertEquals("Nothing filling yet", emptyMatrixCopy("FILL"))
    }

    @Test
    fun jobChipLabelIsCompactCount() {
        val listen = JobProgress(kind = "listen", current = 5742, total = 7174, running = true)
        assertEquals("Listening 5742 / 7174", jobChipLabel(listen))
        assertEquals("Scanning", jobChipLabel(JobProgress(kind = "scan", running = true)))
        assertEquals("Rescanning 3 / 10", jobChipLabel(JobProgress(kind = "rescan", current = 3, total = 10)))
    }

    @Test
    fun whyCardsUsePlainEnglishNotRawMetrics() {
        val track = row(
            quadrant = "now",
            fit = 0.91f,
            valence = 0.49f,
            energy = 0.49f,
            confidence = 0.30f,
            lowTrust = true,
            playCount = 0,
            confidenceNote = "tag:game · weak placement evidence",
        )
        val cards = whyCards(track, bandLabel = "Dusk", mode = "wander")
        assertEquals("Closest to where you aimed (91% fit)", cards[0].title)
        assertEquals("Has not been played yet — Meridian is trying it fresh", cards[1].title)
        assertEquals(
            "Picked for evening Wander. Sounds calm and a bit dark. Game-soundtrack tag is only a weak hint.",
            cards[2].title,
        )
        cards.forEach { card ->
            assertFalse(card.title.contains("valence", ignoreCase = true))
            assertFalse(card.title.contains("confidence 0."))
            assertFalse("raw percent importance leaked", card.title.contains("importance", ignoreCase = true))
        }
    }

    @Test
    fun whyCardsLovedAndClearListen() {
        val track = row(
            quadrant = "now",
            fit = 0.82f,
            valence = 0.62f,
            energy = 0.48f,
            confidence = 0.81f,
            loved = true,
            playCount = 6,
        )
        val cards = whyCards(track, bandLabel = "Night", mode = "wander")
        assertEquals("Closest to where you aimed (82% fit)", cards[0].title)
        assertEquals("You loved this, so it ranks higher here", cards[1].title)
        assertEquals("Picked for night Wander. Sounds bright and calm.", cards[2].title)
        assertTrue(cards[2].title.startsWith("Picked for"))
    }

    @Test
    fun whyFitCardCoversMatrixBands() {
        assertEquals(
            "Worth sitting with — just outside the lens",
            whyFitCard(row(quadrant = "deep")),
        )
        assertTrue(whyFitCard(row(quadrant = "fill", fit = 0.7f)).startsWith("Keeps the room going"))
        assertEquals(
            "Parked for later — outside where you aimed",
            whyFitCard(row(quadrant = "shelf")),
        )
    }

    private fun row(
        quadrant: String = "now",
        fit: Float = 0.5f,
        valence: Float = 0.5f,
        energy: Float = 0.5f,
        confidence: Float = 0.8f,
        lowTrust: Boolean = false,
        loved: Boolean = false,
        pinned: Boolean = false,
        playCount: Int = 0,
        skipCount: Int = 0,
        confidenceNote: String = "",
        why: String = "",
    ) = TrackRow(
        id = 1,
        path = "",
        title = "t",
        artist = "a",
        album = "",
        durationMs = 1_000,
        valence = valence,
        energy = energy,
        confidence = confidence,
        lowTrust = lowTrust,
        pinned = pinned,
        loved = loved,
        quadrant = quadrant,
        fit = fit,
        playCount = playCount,
        skipCount = skipCount,
        confidenceNote = confidenceNote,
        why = why,
    )
}
