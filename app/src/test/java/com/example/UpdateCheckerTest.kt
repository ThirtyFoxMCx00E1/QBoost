package com.example

import com.example.net.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun newerVersionsAreDetected() {
        assertTrue(UpdateChecker.isNewer("8.0", "7.0"))
        assertTrue(UpdateChecker.isNewer("8.0.1", "8.0"))
        assertTrue(UpdateChecker.isNewer("10.0", "9.9.9"))
    }

    @Test
    fun sameOrOlderVersionsAreNotUpdates() {
        assertFalse(UpdateChecker.isNewer("8.0", "8.0"))
        assertFalse(UpdateChecker.isNewer("8.0", "8.0.0"))
        assertFalse(UpdateChecker.isNewer("7.9", "8.0"))
    }
}
