package com.example.ui

import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.MediaController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.details.GameDetails
import com.example.i18n.tr
import com.example.model.GameItem
import com.example.ui.components.qClickable
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.QboostBlue
import com.example.ui.theme.QboostBlueDark
import com.example.ui.theme.QboostBlueGlow
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * "View details" for one game, laid out like the GameHub game page: trailer / gallery on one side,
 * title + Google Play rating + genres + developer info + requirements + introduction on the other,
 * and one big button at the bottom.
 */
@Composable
fun GameDetailsScreen(
    game: GameItem,
    details: GameDetails,
    isLoading: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onTrailerAudioDuckChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) } // 0 = Video, 1 = Gallery
    val art = LibraryArt.forGame(game)

    BackHandler(onBack = onBack)

    // However this screen goes away (back press, tab switch, leaving entirely), never leave the lobby
    // theme ducked once nothing is actually playing the trailer's audio anymore.
    DisposableEffect(Unit) {
        onDispose { onTrailerAudioDuckChange(false) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDarkBg)
    ) {
        if (art != null) {
            Image(
                painter = painterResource(id = art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(26.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC070A12), Color(0xE6070A12), Color(0xFF070A12))
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)
            ) {
                MediaPanel(
                    details = details,
                    tab = tab,
                    onTabChange = { tab = it },
                    onBack = onBack,
                    onTrailerPlayingChanged = onTrailerAudioDuckChange,
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(18.dp))
                InfoPanel(
                    game = game,
                    details = details,
                    isLoading = isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
            DetailsBottomBar(game = game, onBack = onBack, onStart = onStart)
        }
    }
}

// ============================================================================================
//  Video / Gallery
// ============================================================================================

@Composable
private fun MediaPanel(
    details: GameDetails,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
    onTrailerPlayingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0A0F1C))
    ) {
        if (tab == 0) {
            if (details.trailerUrl.isNotBlank()) {
                key(details.trailerUrl) {
                    TrailerPlayer(
                        url = details.trailerUrl,
                        onPlayingChanged = onTrailerPlayingChanged,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                EmptyMedia(tr("details_no_trailer"))
            }
        } else {
            if (details.gallery.isNotEmpty()) {
                GalleryRow(urls = details.gallery, modifier = Modifier.fillMaxSize())
            } else {
                EmptyMedia(tr("details_no_gallery"))
            }
        }

        // Absorbs taps meant for the native player's own prev/pause/forward controls, which visibly
        // glitch the details screen when pressed. The back button and tab pills below are declared
        // after this, so they stay on top of it and remain tappable as normal.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .qClickable(onClick = onBack)
                .testTag("details_back_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x99000000))
                .padding(3.dp)
        ) {
            TabPill(text = tr("details_video"), selected = tab == 0, tag = "details_tab_video") { onTabChange(0) }
            TabPill(text = tr("details_gallery"), selected = tab == 1, tag = "details_tab_gallery") { onTabChange(1) }
        }
    }
}

@Composable
private fun TabPill(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color(0xFF10141C) else Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .qClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag(tag)
    )
}

@Composable
private fun EmptyMedia(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = TextGray, fontSize = 13.sp)
    }
}

/**
 * Plays a direct video link (`.mp4` / `.webm` / `.m3u8` — for example a Cloudinary-hosted trailer) with
 * the built-in native player.
 *
 * Trailers are no longer fetched from, or played through, YouTube at all: no Google Play page scrape and
 * no YouTube search fallback for the URL (see [com.example.details.GameDetailsRepository]), and no in-app
 * YouTube embed here.
 *
 * v8.10 switched this player to a direct-file-only [android.widget.VideoView] to get away from the
 * YouTube `WebView` embed's playback problems, but that just traded one black-screen-with-audio bug for
 * another: `VideoView` renders through a `SurfaceView`, which is composited as its own hardware overlay
 * *outside* the normal View/Compose drawing pipeline. This screen's media panel is inside a
 * `Modifier.clip(RoundedCornerShape(...))`, and a clipped (or blurred, or alpha-faded) parent forces
 * Compose to draw that subtree into an offscreen layer — a `SurfaceView`'s overlay doesn't composite into
 * that layer correctly, so the picture can end up invisible while its audio (decoded and output
 * independently of the visible surface) keeps playing right on schedule. `TextureView` doesn't have this
 * problem: it draws its frames as a normal View, so it composites correctly no matter what clipping,
 * blur, or alpha is applied around it. [MediaPlayer] is driven directly here (rather than `VideoView`,
 * which only exposes a `SurfaceView`) so the picture can be pointed at that `TextureView`'s surface.
 */
@Composable
private fun TrailerPlayer(
    url: String,
    onPlayingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isReady by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(false) }

    // If nothing has rendered after a while, stop making the person stare at a black box: show the fallback.
    LaunchedEffect(url) {
        delay(9000)
        if (!isReady) failed = true
    }

    // Covers every way this player can stop making sound: paused, ended, torn down for a tab switch,
    // a different game's trailer replacing this one, or the whole details screen going away.
    DisposableEffect(url) {
        onDispose { onPlayingChanged(false) }
    }

    Box(modifier = modifier.background(Color.Black)) {
        if (url.isNotBlank()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val root = FrameLayout(ctx)
                    root.setBackgroundColor(android.graphics.Color.BLACK)

                    var player: MediaPlayer? = null
                    var controller: MediaController? = null

                    val textureView = TextureView(ctx)
                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val mp = MediaPlayer()
                                player = mp
                                mp.setSurface(Surface(surface))
                                mp.setDataSource(ctx, Uri.parse(url))
                                mp.setOnPreparedListener { prepared ->
                                    isReady = true
                                    prepared.start()
                                    onPlayingChanged(true)
                                    controller?.setMediaPlayer(MediaPlayerControlAdapter(prepared))
                                }
                                mp.setOnErrorListener { _, _, _ ->
                                    failed = true
                                    true // we show our own fallback instead of the system error dialog
                                }
                                mp.prepareAsync()
                            } catch (_: Exception) {
                                failed = true
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            player?.let {
                                try {
                                    it.stop()
                                } catch (_: Exception) {
                                }
                                it.release()
                            }
                            player = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                    root.addView(
                        textureView,
                        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    )

                    val mediaController = MediaController(ctx)
                    mediaController.setAnchorView(root)
                    controller = mediaController
                    textureView.setOnClickListener {
                        if (mediaController.isShowing) mediaController.hide() else mediaController.show()
                    }

                    root
                },
                onRelease = { root ->
                    for (i in 0 until root.childCount) {
                        (root.getChildAt(i) as? TextureView)?.surfaceTextureListener = null
                    }
                }
            )
        } else {
            DisposableEffect(Unit) {
                failed = true
                onDispose {}
            }
        }

        if (!isReady && !failed) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
            )
        }

        if (failed) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = tr("details_video_failed"), color = TextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                WatchExternallyButton(url = url)
            }
        } else if (url.isNotBlank()) {
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) {
                WatchExternallyButton(url = url)
            }
        }
    }
}

/** Lets the framework [MediaController] widget (built for [android.widget.VideoView]) drive a raw [MediaPlayer] instead. */
private class MediaPlayerControlAdapter(private val player: MediaPlayer) : MediaController.MediaPlayerControl {
    override fun start() = player.start()
    override fun pause() = player.pause()
    override fun getDuration(): Int = try { player.duration } catch (_: Exception) { 0 }
    override fun getCurrentPosition(): Int = try { player.currentPosition } catch (_: Exception) { 0 }
    override fun seekTo(pos: Int) {
        try {
            player.seekTo(pos)
        } catch (_: Exception) {
        }
    }
    override fun isPlaying(): Boolean = try { player.isPlaying } catch (_: Exception) { false }
    override fun getBufferPercentage(): Int = 0
    override fun canPause(): Boolean = true
    override fun canSeekBackward(): Boolean = true
    override fun canSeekForward(): Boolean = true
    override fun getAudioSessionId(): Int = try { player.audioSessionId } catch (_: Exception) { 0 }
}

@Composable
private fun WatchExternallyButton(url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC000000))
            .qClickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                }
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = tr("details_open_externally"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GalleryRow(urls: List<String>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 52.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(urls) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillParentMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
            )
        }
    }
}

// ============================================================================================
//  Text info
// ============================================================================================

@Composable
private fun InfoPanel(
    game: GameItem,
    details: GameDetails,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = details.title.ifBlank { game.name },
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (details.summary.isNotBlank()) {
                    Text(
                        text = details.summary,
                        color = TextGray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            RatingBlock(details)
        }

        if (isLoading) {
            Text(
                text = tr("details_loading"),
                color = QboostBlueGlow,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (details.genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                details.genres.forEach { genre ->
                    Text(
                        text = genre,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x33FFFFFF))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(tr("details_developer"), details.developer, Modifier.weight(1f))
            InfoCell(tr("details_release"), details.releaseDate, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            InfoCell(tr("details_age"), details.ageRating, Modifier.weight(1f))
            InfoCell(tr("language"), details.languages, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))
        RequirementsCard(details)

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = tr("details_intro"),
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = details.description.ifBlank { "--" },
            color = TextGray,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RatingBlock(details: GameDetails) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (details.rating >= 0f) String.format(Locale.US, "%.1f", details.rating) else "--",
            color = TextWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = tr("details_rating"), color = TextGray, fontSize = 11.sp)
        if (details.ratingCount >= 0L) {
            Text(text = "(${formatCount(details.ratingCount)})", color = TextGray, fontSize = 10.sp)
        }
        Text(text = "Google Play", color = QboostBlueGlow, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatCount(count: Long): String = when {
    count >= 1_000_000_000L -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
    count >= 1_000_000L -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
    count >= 1_000L -> String.format(Locale.US, "%.1fK", count / 1_000.0)
    else -> count.toString()
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Text(
        text = "$label: ${value.ifBlank { "--" }}",
        color = TextGray,
        fontSize = 12.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(end = 8.dp)
    )
}

@Composable
private fun RequirementsCard(details: GameDetails) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x26FFFFFF))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = tr("details_requirements"),
            color = TextWhite,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        RequirementRow(tr("details_engine"), details.engine)
        RequirementRow(
            tr("android"),
            if (details.minAndroid.isNotBlank()) tr("details_and_up", details.minAndroid) else ""
        )
        RequirementRow(tr("details_app_size"), details.appSize)
        RequirementRow(tr("details_arch"), details.architecture)
    }
}

@Composable
private fun RequirementRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(text = label, color = TextGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            text = value.ifBlank { "--" },
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================================
//  Bottom bar
// ============================================================================================

@Composable
private fun DetailsBottomBar(game: GameItem, onBack: () -> Unit, onStart: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FFFFFF))
                .qClickable(onClick = onBack)
                .testTag("details_grid_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = TextWhite,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Brush.horizontalGradient(listOf(QboostBlueDark, QboostBlue)))
                .qClickable(onClick = onStart)
                .testTag("details_start_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    game.isInstalled -> tr("start_game")
                    game.packageName.isNotBlank() -> tr("details_get_on_play")
                    else -> tr("install_play")
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
