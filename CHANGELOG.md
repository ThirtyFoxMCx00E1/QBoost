# Changelog

## v9.4 (versionCode 21)

**Package name changed to `com.qboost.hub`** (was `com.aistudio.qboost.gmpk`). Heads up: Android treats a
different package name as a different app entirely, so this build will install *alongside* any existing
install rather than updating it — uninstall the old one first, or you'll end up with two separate copies
(and the old one's local settings/library won't carry over, since that data is tied to its own package).

**Splash screen: transparent logo, one consistent look, no more placeholder chime**
- The rocket mark is now a transparent PNG with no filled icon backdrop (previously it was the full app
  icon, blue rounded square included) — both the OS's own cold-start frame and the app's animated splash
  now show this same glyph, so the two hand off as one continuous splash instead of two different-looking
  screens in a row.
- Removed `splash_screen.mp3` (and the code that played it) — it was a placeholder chime, never real sound
  design, and this app doesn't need one.

**Lobby theme replaced, and it no longer loops seamlessly on purpose**
- Swapped in the new ~60-minute `lobby_theme.m4a`. The looping mechanism changed to match it: previously
  two `MediaPlayer`s handed off to each other for a gapless loop; now there's a single player, and after
  the track finishes there's an intentional 8-second silence before it starts again from the top.

## v9.2 (versionCode 20)

**Removed: Hardware Architecture Compatibility banner** (Super Base) — gone entirely.

**Removed: HUD access / refresh-rate / Turbo status chips** from the top bar — the HUD permission prompt
is still reachable from the Super Base "Fix 'Denied Access'" banner, just not duplicated up top anymore.

**Trailer video: no more glitching from the native player's own controls** — a transparent layer now sits
over the video/gallery area (but behind the back button and Video/Gallery pills, which stay tappable) and
absorbs taps aimed at the native prev/pause/forward controls, which were visibly glitching the details
screen when pressed.

**Removed: beat-reactive library tile animation** — the selected game tile no longer pulses/scales with
the lobby music's beat. Tiles are static now regardless of music playback.

**Fixed: "Add Game" could create duplicates** — tapping an already-added app did nothing to stop it from
being added a second time (the "ADDED" badge was purely visual). Rows for already-added apps are no longer
clickable, and `GameRepository.addGame()` itself now refuses to add a package that's already in the
library as a second safeguard. Also removed the "All Installed Apps" tab entirely — the picker now only
ever lists real games (never Play Store, YouTube, or other non-game apps), matching what "Games" already
filtered to.

**Added: controller and network status icons** in the top bar, next to search/battery — a controller icon
(green dot only appears once a real USB/OTG or Bluetooth controller is detected, no dot otherwise) and a
Wi-Fi icon that dims when the device has no working internet connection and returns to full white once
back online.

## v9.1 (versionCode 19)

**Cold-start splash: no more system icon flash before it**
- On some devices (ColorOS/Realme in particular, but this is really an unconfigured Android-12+ launch
  path) the OS was drawing its own default cold-start frame — the bare launcher icon centered on a plain
  background with the status bar showing — *before* our Activity or its custom SplashScreen.kt ever got to
  draw anything, since nothing had told the platform to hand that moment over to us. Added
  `androidx.core:core-splashscreen`, a `Theme.App.Starting` (`Theme.SplashScreen` parent) on `MainActivity`
  using the same grey and rocket icon as SplashScreen.kt, and `installSplashScreen()` in `onCreate()`. Now
  that system frame is ours too, on the same grey, so the hand-off into the animated splash is seamless
  instead of a visible swap.

**Gamepad LB/RB now reach every tab, not just Super Base**
- LB/RB (and the on-screen LB/RB badges) always jumped straight to Library / Super Base respectively —
  never wired up to reach the new Favorites tab at all. Replaced with proper cycling through
  Library → Super Base → Favorites (and back) in tab order, so RB actually gets you there now.

**Lobby music no longer starts under the splash**
- `QboostViewModel.init` started `lobby_theme.m4a` immediately, and merely touching the ViewModel (which
  `Activity.onResume()` does right away, regardless of the splash's 9-second hold) was enough to kick it
  off — so the music was already playing underneath the splash animation instead of waiting for it. Moved
  the start into a new guarded `startLobbyMusic()`, called only once from the splash's `onFinished`, so the
  lobby theme now begins right as the games menu fades in, not before.

## v9.0 (versionCode 18)

**New app icon**
- Replaced the launcher icon everywhere: legacy icons regenerated at every density (mdpi–xxxhdpi, regular
  and round), and the adaptive icon (Android 8+) background/foreground updated to match the new artwork's
  blue gradient. Old `qboost_logo.jpg` removed.

**Animated splash screen**
- New `SplashScreen.kt`: on launch, the rocket mark does a quick spring pop (scale + spin), settles back to
  rest, then the QBOOST wordmark slides in right beside it — logo and wordmark kept close together as one
  lockup rather than spread across the screen. Plays `res/raw/splash_screen.mp3` once on entry. Holds for
  9 seconds total, then cross-fades into the games menu.
  - **`splash_screen.mp3` is a placeholder** — a small synthesized two-note chime, generated so the app has
    a real file to build and play (an `R.raw` reference to a missing file won't compile). Swap it for real
    sound design whenever it's ready; nothing else about the screen needs to change.
  - Background is a neutral grey (`#1C1C1E`), set as `Theme.MyApplication`'s `android:windowBackground` too,
    so the system's own pre-draw frame, the splash, and the fade into the menu are all the same grey with no
    color flash at any handoff.

**Favorites**
- Games can now be favorited/unfavorited from the "..." menu; favorited games get a small heart badge on
  their tile. New **Favorites** tab next to Super Base at the top shows just the favorited games.
  `GameItem.isFavorite` persists in the same per-game prefs storage as everything else.

**Library button/menu cleanup**
- The "..." menu previously had both "View details" *and* a duplicate "Saturation" entry, while a separate
  "Saturation" button also sat next to Start Game — a leftover duplicate. Swapped: **View details** is now
  the button next to Start Game, and the "..." menu has a single **Saturation** entry (plus the new
  favorite toggle), Add game, and Remove from library.

## v8.11 (versionCode 17)

**Trailer: fixed for real — the "audio but no video" bug was never YouTube's fault**
- v8.10 removed the YouTube embed to fix trailers playing audio over a black screen, but the same symptom
  came right back on plain direct video files too. The real cause: `VideoView` (used in v8.10) renders
  through a `SurfaceView`, which is composited as its own hardware overlay *outside* Compose's normal
  drawing pipeline. The media panel here sits inside a clipped, rounded container
  (`Modifier.clip(RoundedCornerShape(...))`), and a clipped/blurred/faded parent forces Compose to draw
  that subtree into an offscreen layer — a `SurfaceView`'s overlay doesn't composite into that layer
  correctly, so the picture can disappear entirely while its audio (decoded independently of the surface)
  keeps playing right on schedule.
- Replaced `VideoView` with a `TextureView` driven directly by `MediaPlayer`. `TextureView` draws its
  frames as an ordinary View, so it composites correctly no matter what clipping/blur/alpha surrounds it —
  this is the standard fix for video-inside-Compose black-screen bugs. Play/pause/seek controls
  (tap to show/hide) are preserved via a small adapter handing the raw `MediaPlayer` to the same
  `MediaController` widget `VideoView` used internally.

## v8.10 (versionCode 16)

**Trailer: YouTube playback removed, "audio but no video" fixed**
- The in-app YouTube embed (a `WebView` playing YouTube's page, with the video hopefully promoted to a
  native view for hardware playback) is gone. That hand-off was the cause of the trailer sometimes playing
  its audio over a black or blank box — "ready" fired as soon as the page loaded, whether or not the
  promoted video view had actually appeared. The trailer now only ever plays through Android's native
  `VideoView`, the same reliable path direct video files already used.
- Trailers are also no longer *fetched* from YouTube at all: removed the Google Play page's promo-video
  scrape and the "search YouTube for `<game> official trailer`" last-resort fallback. The Firebase `trailer`
  field is now the only source — see `docs/firebase/README.md`.
- `docs/firebase/games.sample.json`: `coc` (Clash of Clans) was the last game still pointing at a
  `youtube.com` link; cleared to blank pending a direct (e.g. Cloudinary) trailer URL, since a YouTube link
  no longer plays here.

## v8.9 (versionCode 15)

**Trailer hosting moved off YouTube entirely**
- `docs/firebase/games.sample.json` now points each game's `trailer` field at a direct `.mp4` URL
  (hosted at github.com/ThirtyFoxMCx00E1/Trailers) instead of a YouTube link. The app already had a native
  player path for direct video URLs, so this sidesteps the YouTube/WebView issues below entirely — no
  `enablejsapi`, no embed page, no verification checks to fail. Update the matching `trailer` values in the
  real Firestore data to switch each game over. One game (`coc` / Clash of Clans) has no hosted trailer yet.

**Removed: automatic fullscreen on entering the details screen**
- The trailer no longer jumps to fullscreen by itself the moment it starts playing. Entering "View details"
  now always shows the normal side-by-side layout; fullscreen (if any) has to be started manually. The
  lobby-music ducking is unaffected — it still ducks while the trailer is loaded/playing either way.

**Fixed for real this time: trailer showing "Video unavailable, error 152-4"**
- The first fix attempt (moving to a statically-declared iframe) turned out not to be the actual cause —
  the same error still showed up on a second, unrelated trailer afterward. The real trigger is `enablejsapi`
  itself: YouTube now requires embedding Android apps to prove their authenticity before it'll let a page
  control the player via JS (Android's WebView Media Integrity API, rolled out for YouTube embeds in 2024),
  and a page loaded the way this app loads its embed page doesn't carry that verification.
- Fixed by dropping `enablejsapi` and the whole IFrame Player API script entirely, back to the plain embed
  v8.8 used successfully. Trade-off: forced-1080p playback and exact play/pause detection both needed
  `enablejsapi` to work, so they're gone — the trailer now just plays at whatever quality YouTube picks, and
  the lobby-music ducking / auto-fullscreen trigger off the player finishing loading rather than a confirmed
  "now playing" event. Reliable playback over the extra precision.

**Trailer: fullscreen and no more fighting the lobby music**
- The trailer now goes fullscreen the moment it starts playing, the same as tapping YouTube's own
  fullscreen button — nobody has to find and tap it themselves. Tapping back (or the system back
  gesture) once returns to the normal details layout; a second time leaves the details screen.
- The lobby theme now automatically ducks to 15% volume while the trailer is loaded/playing, and comes
  straight back to normal once the trailer is closed or the details screen is left. See the fix note
  above for why this uses "finished loading" rather than a precise play/pause event.

**Fixed a build warning**
- The "Open video" / "Watch on YouTube" button used the deprecated `Icons.Filled.OpenInNew`; switched to
  the current `Icons.AutoMirrored.Filled.OpenInNew`.

## v8.8 (versionCode 14)

**Trailer playback: fixed the black screen**
- The embedded YouTube player now hands the video off to a proper native view when the WebView promotes it
  for hardware playback (`onShowCustomView`/`onHideCustomView`) — the most common reason an embedded
  YouTube video stays black while technically "playing". A loading spinner shows while it connects.
- There's always a small **"Watch on YouTube" / "Open video"** button over the player now, so there is a
  guaranteed way to see the trailer even on a device where the inline player still fails. If nothing has
  loaded after 9 seconds, that button is shown front and center instead of a blank box.
- Same fix applies to direct (non-YouTube) video links: playback errors now show the same fallback instead
  of a silent black screen.

**Release date, language, and the game introduction**
- The "About this game" text is now read in full from the store page, instead of Play's short one-line
  tagline (that's why the introduction looked far shorter than GameHub's).
- Release date extraction is more tolerant of Play's page markup, with a second attempt at a machine-readable
  date if the first doesn't match.
- Language now has a best-effort reader too, but Play's current page design often doesn't publish a
  supported-language list at all — when that's the case it honestly stays "--"; set the `languages` field in
  Firebase for guaranteed results.
- All of this reads Google's live page, which they can change at any time, so results can vary game to game
  and may need occasional fixes.

**Lobby music**
- Replaced `lobby_theme.m4a` with the new ~1h 22m track.
- Looping is now gapless: instead of one player seeking back to the start (which is what caused the ~9s
  pause between loops), Qboost now keeps two players and uses Android's `MediaPlayer.setNextMediaPlayer` —
  the API built for exactly this — so the second one is already fully loaded and ready the instant the
  first ends.

## v8.7 (versionCode 13)

**Trailers: fixed "not found on Play Store"**
- Broadened the Google Play trailer reader: Play's page has used several different ways to embed the promo
  video over the years, and the old code only recognized one of them, which is why it usually came up empty.
- New automatic fallback: if neither your Firebase entry nor the Play page has a trailer, Qboost now searches
  YouTube for "<game name> official trailer" and uses the top result. This is best-effort (there's no free
  YouTube search API) and can occasionally pick the wrong video; a Firebase `trailer` field always wins.
- `docs/firebase/games.sample.json` now ships with a **real, verified trailer link and developer name for
  every library game** (checked in September 2026) — import it to fix "no trailer" immediately.
- X (Twitter) and other social apps are not fetched automatically: as of 2026 their search only works through
  a paid API or while logged in, so there's no free, reliable way to do this. A link from X (or anywhere else)
  still works if you paste it into the Firebase `trailer` field.

## v8.6 (versionCode 12)

**View details (like the GameHub game page)**
- New **View details** item at the top of the `...` menu of a game. It opens a page with a **video / gallery**
  switch, the title, the **Google Play rating**, genres, developer, release date, age rating, language, a
  **Requirements** card (game engine, minimum Android, app size, 32/64-bit) and the game introduction.
- Where the data comes from (nothing is invented; missing values show `--`):
  - **Firebase Realtime Database** (your own data): trailer, gallery, and any text you want to set. Read through its
    web address, so no Firebase SDK or `google-services.json` is needed. Setup: `docs/firebase/README.md`.
  - **Google Play page** of the game: rating (the rating is *only* ever taken from here), description, genre,
    developer, age rating, screenshots, promo video, release date, minimum Android. Google has no official API for
    this, so it is read from the public page and may stop working if Google changes it.
  - **The phone** (installed games only): the real game engine (Unity, Unreal, Godot ...), minimum Android, app size
    and 32/64-bit, read from the installed app.
- Videos play in the built-in player (direct links) or an embedded YouTube player. Details are cached for 12 hours.

**Notifications (Settings)**
- New **Notifications** category like GameHub: a *Push notifications* switch that mirrors the real Android setting
  (tap it to allow, or to open Qboost's notification settings) and *Update alerts*: Qboost checks GitHub at most every
  12 hours and shows a notification when a newer version exists.

**Games**
- DYSMANTLE (`com.the10tons.dysmantle`) and Little Nightmares (`com.playdigious.littlenightmare`) now have their
  Google Play packages; existing tiles are updated once.

## v8.2 (versionCode 11)

_Build fix: `ScalerService` now handles a null result from `MediaProjectionManager.getMediaProjection` (compile error on newer SDKs)._

**Floating apps (multitasking over the game)**
- Tap YouTube, Spotify, Discord, Facebook, WhatsApp, TikTok, Instagram, Messenger, Telegram, X, Twitch, Reddit,
  Netflix or Browser in the panel dock to open it in a small window over the game. Drag by the title bar, resize
  with the corner grip, `Aa` turns the keyboard on, `-` minimizes to a tab, `x` closes. Several can be open at once.
- Android does not let an app put another app's own screen in a window, so these windows show the web version of
  each service (log in once inside the window). **Hold** a dock icon to open the real app instead (in a window too on
  phones with freeform windows enabled).

**Upscaler + Frame gen (experimental, like Lossless Scaling on PC)**
- New Upscaler and Frame gen tiles in the panel. They capture the game screen, process it on the GPU and show the
  result over the game; touches go straight through to the game.
  - Upscaler: contrast adaptive sharpening (AMD FidelityFX CAS). Strength: Settings > Display.
  - Frame gen: an in-between frame after every captured frame, from block-matching motion estimation.
- Needs **Android 14+**: pick "A single app" and choose your game in Android's capture question. It adds a little
  delay and GPU load, and stops when the game leaves the screen or you stop it in the notification.

**Other**
- The panel is more minimal: the tools are now Saturation, Upscaler, Frame gen, Haptics, Wi-Fi, Display, Qboost,
  Stop HUD. The Touch boost and Cooling fan tiles (which did nothing) are gone.
- The app follows the phone when you flip between the two landscape positions (`sensorLandscape`).
- PC-only games (Valorant, Dota 2, Undertale, Ratchet & Clank, World of Warcraft, Marvel Heroes Omega, Little
  Nightmares 2) are replaced by mobile games with their own art: Blood Strike, Asphalt 8: Airborne, Getting Over
  It, Human: Fall Flat, Oceanhorn, DYSMANTLE and Little Nightmares. Existing libraries are updated once.
- Tiles without an app (DYSMANTLE, Little Nightmares) link themselves to the installed app with the same name, and
  "installed" now updates when you install or remove a game.

## v8.0 (versionCode 10)

**In-game panel, redesigned (like the "New Game Space" reference)**
- Two pages: **Gaming tools** (time, game name, battery, FPS gauge with CPU and Memory bars, Battery /
  Balanced / Performance switch, Memory optimization, System monitor, and a 4 x 3 grid of tools) and **Games**
  (your installed library games, tap to launch).
- **App dock** on the left with the real icons of WhatsApp, TikTok, Spotify, YouTube, Instagram, Facebook,
  Discord, Messenger, Telegram, Snapchat, X, Netflix, Twitch, Reddit and Chrome (only the installed ones show).
  The YouTube PIP button and the floating browser are gone.
- No emoji anywhere in the panel: gradients and plain text only.
- The edge handle is **invisible by default (0% opacity)** but still opens the panel when you tap or swipe
  where it is. Panel opacity, handle opacity and the dock can be changed in Settings. Tapping the game
  outside the panel closes it.
- Real numbers only (FPS / CPU / GPU / RAM / temperature), same as v5.0.

**Game Space menu**
- **LB / RB / Y Search / Menu / A hints only appear when a game controller is connected** (USB / OTG or
  Bluetooth), or when forced in Settings > General > Controller hints. When connected they work: LB / RB
  switch tabs, D-pad or left stick move between games, A launches, Y searches, Start opens the menu, B goes back.
- New **Settings** button next to Search. Settings screen (GameHub / PC Engine style): General (language,
  controller), Audio & feedback, In-game panel, Display, About (app information, check for updates, GitHub).
- **13 languages:** English, Spanish, Japanese, French, Arabic (right-to-left), Korean, Chinese (Singapore),
  Portuguese (Brazil), German, Russian, Hindi, Indonesian and Turkish. Translations live in
  `res/raw/i18n.tsv`. The lobby, Settings and in-game panel are translated; the long help dialogs stay in English.
- The focused game tile has an **animated white outline** (a bright comet travelling around the tile).
- Update check compares the `versionName` on the main branch of the GitHub repo with the installed one.

## v7.0 (versionCode 9)
- **More library art:** Call of Duty Mobile, Devil May Cry, Dolphin and Worms 4 now have cover art, and
  GTA San Andreas, Sky, Roblox and Little Nightmares 2 have new tiles (`res/drawable-nodpi/lib_*`).
- **FF7EC is replaced by Little Nightmares 2** in the same spot (existing libraries are updated once on
  first launch). Tiles that are not linked to an installed app show a download badge.
- **New lobby music:** `res/raw/lobby_theme.m4a` (24:00) plays in full and then loops. The pulse on the
  selected tile now follows the real loudness of the track (`res/raw/lobby_theme_env.bin`, one value
  every 50 ms) instead of a fixed 124 BPM timer. The old `lobby_music.wav` was removed.
- **Bottom toggles** (music / haptics / fan / click sound) are icon-only: no circle or background, so
  they can no longer overlap. Blue = on, gray = off.
- **Saturation button** shows just the word "Saturation" (no palette icon, no percentage).
- Game Space always opens with the selected game scrolled into view.

## v6.0.1 (versionCode 8)
- Fixed the GitHub Actions build failure after upgrading to v6.0: the old `RedMagicGameSpaceScreen.kt` and
  `RedMagicSpeakerCore.kt` were still in the repo and no longer compiled (removed colors and the Coil
  library). Both files are now empty placeholders, so copying this project over an older checkout is
  enough. You can still delete them, plus `app/src/main/assets/qboost_bg.gif` (unused).

## v6.0 (versionCode 7)

**Game Space menu redesign (GameHub style)**
- Removed the animated GIF background (and the 15 MB `qboost_bg.gif`). The background is now the
  selected game's library art, blurred and darkened, and it cross-fades when you pick another game.
- New library row: every game is an art tile (`res/drawable-nodpi/lib_*`). Tap a tile to focus it, tap the
  focused tile ("A Launch Game") to start it. Games without art use their real app icon.
- Library games added (once, on first launch of v6.0): Minecraft, Genshin Impact, PUBG Mobile, Clash of
  Clans, Valorant, Dota 2, Undertale, Ratchet & Clank, World of Warcraft, Marvel Heroes Omega
  (Wuthering Waves already existed). Games that are not linked to an installed app show a download badge.
- New top bar: menu, LB / RB tab badges, `Library` and `Super Base` tabs, search, HUD / Hz / TURBO status,
  battery and a live clock. The profile icon is gone.
- New info block under the row (name, mode, install state) with `Start Game`, `Saturation` and a `...` menu
  (Saturation, Add game, Remove from library). Bottom bar keeps the music / haptics / fan / click-sound
  toggles and has `Y Search` and `Menu` buttons.
- No outlines anywhere in the app UI (tiles, chips, buttons, dialogs, text fields, in-game panel).
- Whole UI recolored to blue (`QboostBlue*` in `ui/theme/Color.kt`; the in-game panel too).
- `RedMagicGameSpaceScreen` is now `QboostGameSpaceScreen`; the speaker-core graphic was removed.

**Upgrading from v5.0:** `RedMagicGameSpaceScreen.kt`, `RedMagicSpeakerCore.kt` and `assets/qboost_bg.gif` are
no longer used (see v6.0.1 for how they are handled).

## v5.0 (versionCode 6)

**Game Space**
- Fixed the *Add Game to Game Space* dialog in landscape: it is now a two-pane layout, so the
  **Add to Game Space** button is always visible (app list on the left, name + button on the right).
- App is locked to landscape (`android:screenOrientation="landscape"`), no more sensor rotation.
- Header renamed from REDMAGIC to **QBOOST**; leftover Red Magic wording removed (badge is now `TURBO 5.0`).
- Background replaced with the animated `assets/qboost_bg.gif` (Coil GIF decoder). The old
  `red_magic_bg.jpg` was removed. A light dark scrim keeps the text readable (`BACKGROUND_SCRIM_ALPHA`
  in `RedMagicGameSpaceScreen.kt`).
- New **click sound** button (bottom right) and `res/raw/click.ogg` played on UI buttons in Game Space and
  in the in-game HUD. Drop your own file at `app/src/main/res/raw/click.ogg` to replace the sound.
- New **Saturation** button + dialog (slider 0-200%, presets, save per game).

**In-game HUD**
- New **System Monitor** button: floating, draggable overlay with FPS, CPU, GPU, RAM, MEM (swap/zRAM)
  and TEMP.
- Saturation slider with presets, reset, and a copyable ADB command.
- Real RAM purge: reports the memory that was actually freed.

**Real data (nothing simulated any more)**
- RAM, swap, battery, temperature, refresh rate: read from the system.
- CPU load: `/proc/stat` when allowed, otherwise estimated from real per-core clock speeds.
- GPU load/clock: Adreno (kgsl), Mali and MediaTek nodes when readable; GPU/Vulkan names from the driver.
- FPS: display `measured_fps` node when readable, otherwise the panel refresh rate labelled as such.
- Anything the ROM blocks is shown as `N/A` / `--` instead of a made-up number.
- If Qboost has been granted root, blocked files are read through root as a fallback.

**Saturation**
- Real, system-wide saturation through SurfaceFlinger (needs root, or one ADB command).
  Reset to 100% when the HUD stops.

## v4.0
- Initial Game Space / floating cockpit release.
