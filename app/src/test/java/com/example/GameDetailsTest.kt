package com.example

import com.example.details.GameDetails
import com.example.details.GameDetailsRepository
import com.example.model.GameItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameDetailsTest {

    @Test
    fun firstSourceWinsAndGapsAreFilled() {
        val firebase = GameDetails(title = "A", developer = "", trailerUrl = "video", fromFirebase = true)
        val play = GameDetails(
            title = "B",
            developer = "Dev",
            rating = 4.5f,
            gallery = listOf("x"),
            fromPlay = true
        )
        val merged = firebase.withFallback(play)
        assertEquals("A", merged.title)
        assertEquals("Dev", merged.developer)
        assertEquals("video", merged.trailerUrl)
        assertEquals(4.5f, merged.rating, 0.001f)
        assertEquals(listOf("x"), merged.gallery)
        assertTrue(merged.fromPlay && merged.fromFirebase)
    }

    @Test
    fun unknownValuesStayUnknown() {
        val details = GameDetails(title = "Only a title")
        assertEquals(-1f, details.rating, 0.001f)
        assertTrue(details.genres.isEmpty())
        assertEquals("", details.developer)
    }

    @Test
    fun firebaseKeyIsTheGameIdOrThePackage() {
        assertEquals("minecraft", GameDetailsRepository.keyFor(GameItem(id = "minecraft", name = "Minecraft")))
        val custom = GameItem(
            id = "123e4567-e89b-12d3-a456-426614174000",
            name = "My game",
            packageName = "com.example.game"
        )
        assertEquals("pkg_com_example_game", GameDetailsRepository.keyFor(custom))
    }
}
