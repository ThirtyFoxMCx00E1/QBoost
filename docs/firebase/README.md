# Connecting Qboost to Firebase (game trailers, gallery, extra details)

The "View details" screen reads your own data from a **Firebase Realtime Database** through its plain web
address (no Firebase SDK, no `google-services.json` needed). Everything is optional: a game with no Firebase
entry still shows its Google Play rating, description, genre, developer and age rating.

## 1. Create the database
1. Firebase console -> your project -> **Build -> Realtime Database -> Create database**.
2. **Rules** tab, paste this (anyone can read game info, nobody can write):
   ```json
   { "rules": { "games": { ".read": true, ".write": false } } }
   ```

## 2. Tell the app where it is
Edit `app/src/main/res/raw/firebase_config.json`:
```json
{ "databaseUrl": "https://YOUR-PROJECT-default-rtdb.firebaseio.com" }
```
(the address is shown at the top of the Realtime Database page, and as `firebase_url` inside `google-services.json`).

## 3. Add your games
Realtime Database -> the three dots -> **Import JSON** -> pick `games.sample.json` from this folder, then fill it in.
Each game lives at `games/<key>`. The key is the game id: `minecraft`, `genshin`, `pubgm`, `coc`, `codm`, `dmc`,
`dolphin`, `worms4`, `wuthering`, `gta_sa`, `sky`, `roblox`, `blood_strike`, `asphalt8`, `getting_over_it`,
`human_fall_flat`, `oceanhorn`, `dysmantle`, `little_nightmares`.
Games you add yourself use `pkg_` + the package name with dots turned into underscores (`pkg_com_example_game`).

| Field | Meaning |
| --- | --- |
| `trailer` | A direct video link (`.mp4`, `.webm`, `.m3u8` — for example a Cloudinary or Firebase Storage download URL). YouTube links are no longer supported here; see below |
| `gallery` | List of image links (screenshots). Firebase turns lists into `{ "0": "...", "1": "..." }`, both work |
| `title`, `summary`, `description` | Override the Google Play text |
| `genres` | List of strings, for example `["Action", "Adventure"]` |
| `developer`, `releaseDate`, `ageRating` | Override / fill the values |
| `languages` | Text (`"English, Spanish"`) or a list |
| `engine`, `minAndroid` | Only needed for games that are not installed (installed games are read from the phone) |

Blank fields are ignored. The star **rating always comes from Google Play**, never from Firebase.

## Where the trailer comes from if you leave it blank

**Your Firebase `trailer` field is the only source now** — if it's blank, "View details" shows no trailer.
Trailers are no longer fetched from YouTube in any way: not from the Google Play page's promo video, and
not from a YouTube search fallback. Both of those routes used to hand back a `youtube.com` link, which then
played through an in-app YouTube embed; that embed was the source of a recurring bug where the trailer's
audio would play over a black screen. Direct video files through the native player don't have that problem,
so that's the only path left.

Upload your trailer somewhere that gives you a direct file URL — Cloudinary, Firebase Storage, or any other
host works — and put that link in `trailer`. A Cloudinary "shared collection" page (`collection.cloudinary.com/...`)
is just a browsing UI for people, not a playable link itself: open the asset inside it and copy its actual
delivery URL, which looks like `https://res.cloudinary.com/<cloud_name>/video/upload/.../<name>.mp4` (every
game already in `games.sample.json` below uses exactly this format).

`games.sample.json` in this folder already has a Cloudinary-hosted trailer link and developer name for every
library game except `coc` (Clash of Clans) — import it as-is, then set `coc`'s `trailer` to your own
Cloudinary URL and add any gallery links or overrides on top.

Data is cached on the phone for 12 hours.
