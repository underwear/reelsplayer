# ReelsPlayer

Offline video player for your downloaded Reels, Shorts, and TikToks — swipe through them like you're still on the feed.

Pick a folder, get the full-screen vertical swipe experience. No internet, no accounts, no tracking. Just your videos.

## Features

- **Folder picker** — point at any folder on your device, plays all videos inside
- **Vertical swipe** — exactly like Reels/Shorts/TikTok
- **Tap to pause/play**
- **Long press right side → 2x speed** — hold to speed up, release to go back to normal
- **Progress bar** — thin minimal bar at the bottom, like the real thing
- **Auto-loop** — each video loops until you swipe
- **Fully offline** — zero network, zero permissions beyond storage access

## Download

Grab the latest APK from [Releases](../../releases).

## Requirements

- Android 8.0+ (API 26)

## Building from source

```bash
git clone https://github.com/underwear/reelsplayer.git
cd reelsplayer
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## License

MIT
