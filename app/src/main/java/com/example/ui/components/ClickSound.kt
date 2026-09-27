package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import com.example.audio.UiSounds

/** Same as [clickable], but plays Qboost's click sound (res/raw/click.ogg) first. */
fun Modifier.qClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled) {
        UiSounds.play()
        onClick()
    }

/** Wraps a button's onClick so the click sound plays before the action runs. */
fun clickSound(action: () -> Unit): () -> Unit = {
    UiSounds.play()
    action()
}
