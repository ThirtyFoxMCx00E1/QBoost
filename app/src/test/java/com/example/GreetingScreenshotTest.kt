package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.GameItem
import com.example.model.PerformanceStats
import com.example.ui.QboostGameSpaceScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun qboost_lobby_screenshot() {
    val sampleGame = GameItem(
        id = "wuthering",
        name = "Wuthering Waves",
        initials = "WW"
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true, dynamicColor = false) {
        QboostGameSpaceScreen(
            games = listOf(sampleGame),
            selectedGame = sampleGame,
            stats = PerformanceStats(),
            isMusicMuted = false,
            isVibrationEnabled = true,
            isTurboFanActive = true,
            beatIntensity = 0.5f,
            installedApps = emptyList(),
            onSelectGame = {},
            onStartGame = {},
            onAddGame = { _, _ -> },
            onToggleMuteMusic = {},
            onToggleVibration = {},
            onToggleTurboFan = {},
            onClearProcesses = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/qboost_lobby.png")
  }
}

