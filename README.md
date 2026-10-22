# 🐒 Jungle Run

Endless-runner Android game. Momo the monkey jungle mein bhaag raha hai aur **sher (lion) peeche pada hai**!
Har jaanwar ka apna khana, apna jungle, apna challenge aur apna peecha karne wala shikari hai.

| Animal | Food | Biome / challenge | Chased by | Ability | Unlock |
|---|---|---|---|---|---|
| 🐒 Momo the Monkey | 🍌 Banana | Jungle — falling coconuts | 🦁 Lion | Banana Frenzy (obstacles → bananas), double jump | Free |
| 🐰 Bunny Bolt | 🥕 Carrot | Meadow — burrow holes | 🦊 Fox | Moon Jump | 2,500 coins |
| 🐼 Pao the Panda | 🎋 Bamboo | Bamboo grove — low bars | 🐯 Tiger | Panda Roll (invincible) | 5,000 coins |
| 🦁 Leo the Lion | 🍖 Meat | Savanna — warthog stampede | 🦏 Rhino | King's Roar (clears screen) | 8,000 coins |
| 🐻 Bruno Bear | 🍯 Honey | Honey woods — bee swarms | 🐝 Bees | Honey Magnet | 12,000 coins |
| 🐘 Ellie Elephant | 🥜 Peanut | Croc river — wide rivers | 🐊 Crocodile | Trunk Smash | 80 gems |
| 🐆 Chase the Cheetah | 🍖 Meat | Savanna | Hyena pack | Turbo Dash | 150 gems |
| 🦒 Gigi Giraffe | 🌿 Leaves | Savanna | 🦁 Lion | Long Neck (auto-collect) | 200 gems |
| ✨ Golden Momo | 🍌 Banana | Jungle | 🦁 Lion | 2x coins forever | In-app purchase |

## Features

**Gameplay**
- Side-scrolling endless runner: tap = jump, swipe down = slide (and fast-fall), ability button bottom-right.
- Chaser mechanic (Temple Run style): hit one obstacle → the predator catches up; hit another while it's close → caught. Fall in a river/pit → splash.
- 7 hazard types (logs/rocks, tall walls, overhead branches, pits/rivers, falling coconuts, charging warthogs/boulders, bee swarms/birds), mixed per biome.
- Power-ups: magnet, shield, 2x coins, mystery box. Golden food, gems.
- Difficulty ramps with distance; speed is a function of distance so the generator can always leave a fair gap (verified with a bot that survives 16 km on every seed).
- 100% procedural art & synthesized audio — no image/sound assets needed.

**Multiplayer (3 modes)**
1. **Same device (2 players)** — split screen, top half vs bottom half, multi-touch.
2. **Wi-Fi room (local)** — "Create room" shows a 4-digit code; nearby phones auto-discover it via UDP broadcast; game link over TCP. Works on a hotspot with no internet.
3. **Online** — Quick Match or private 5-letter room code, via the small relay server in [`server/`](server/).

Both racers get the **same seed** (same course). Positions stream ~12×/s and the rival is drawn as a translucent ghost. Highest score wins.

**Monetisation (industry standard)**
- **Google AdMob**: anchored adaptive banner, interstitial (paced: every 3rd game over, ≥90 s, never in first runs), rewarded, rewarded interstitial, app-open (on resume from background), native ad on the results screen.
- **Meta Audience Network**: direct fallback (banner, interstitial, rewarded) when AdMob has no fill **and** the AdMob → Meta mediation adapter.
- **UMP consent** (GDPR/US states) before any ad request, privacy-options entry in Settings.
- Rewarded placements: revive, double coins, daily reward x2, free coins/gems.
- **Google Play Billing v7**: coin packs, gem packs (consumables), Remove Ads, Starter Pack, Golden Momo (non-consumables), Jungle VIP monthly subscription; pending purchases, acknowledgement, restore.

**Retention / economy**
- Coins & gems, animal unlocks, 4 upgrade tracks (magnet, shield, 2x coins, ability).
- 3 rotating missions → score multiplier (up to 30x), mission skip for gems.
- Daily challenge (run as a specific animal and eat its food), 7-day daily reward streak, achievements, top-10 scores.
- Revive with ad or escalating gem cost.

**Engineering**
- Kotlin, Jetpack Compose UI, SurfaceView game loop (fixed 120 Hz simulation, multi-touch).
- Firebase Analytics + Crashlytics wired in, auto-enabled when `app/google-services.json` exists.
- R8 minify + resource shrinking, release signing from `keystore.properties` or env vars.
- Unit tests for determinism, missions, revive and save-file round trip. GitHub Actions CI builds the APK and tests the server.

## Build & run

Requirements: Android Studio (Ladybug or newer) / JDK 17, Android SDK 35.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest
```

Online multiplayer locally:

```bash
cd server && npm install && npm start   # ws://localhost:8080
```

The debug app connects to `ws://10.0.2.2:8080` (the emulator's alias for your computer). For a real phone or production, deploy the server (Dockerfile included, e.g. Render/Fly.io/Cloud Run) behind TLS and build with
`-PONLINE_SERVER_URL=wss://your-server.example.com`.

## Before publishing — replace test IDs

All ad IDs default to **Google/Meta test IDs**. Pass real ones as Gradle properties (`~/.gradle/gradle.properties`) or CI env vars:

```
ADMOB_APP_ID=ca-app-pub-XXXX~YYYY
ADMOB_BANNER_ID=...            ADMOB_INTERSTITIAL_ID=...
ADMOB_REWARDED_ID=...          ADMOB_REWARDED_INTERSTITIAL_ID=...
ADMOB_APP_OPEN_ID=...          ADMOB_NATIVE_ID=...
META_BANNER_ID=...             META_INTERSTITIAL_ID=...   META_REWARDED_ID=...
ONLINE_SERVER_URL=wss://...
```

Also:
1. **AdMob console** → add Meta Audience Network as a mediation source (bidding) for the same ad units.
2. **Play Console → Monetize → Products**: create in-app products `coins_small`, `coins_medium`, `coins_large`, `gems_small`, `gems_medium`, `gems_large`, `starter_pack`, `remove_ads`, `animal_golden_monkey` and subscription `vip_monthly` (see `billing/Products.kt`).
3. Verify purchase tokens server-side (Play Developer API) before granting high-value items.
4. Add `app/google-services.json` from Firebase to enable Analytics/Crashlytics.
5. Release signing: create `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) — it's git-ignored.
6. Replace `PRIVACY_POLICY_URL` in `SettingsScreen.kt` and add an `app-ads.txt` on your developer website.
7. If you target children under 13 (Designed for Families), Meta Audience Network is **not** allowed and AdMob must be configured as child-directed.

## Project layout

```
app/src/main/java/com/junglerun/game/
  core/       Animals, foods, biomes, chasers, abilities, deterministic RNG
  game/       GameWorld (physics/rules), WorldGen, GameRenderer, AnimalPainter, GameView loop, GameController
  data/       Save game (PlayerRepository), missions, achievements, daily rewards, economy
  net/        RoomSession protocol, LAN host/guest/discovery, Online WebSocket transport
  ads/        AdsManager (AdMob + Meta waterfall), AppOpenAdManager, UMP ConsentManager
  billing/    Play Billing manager + product catalogue
  audio/      Synthesised SFX + music, haptics
  ui/         Compose screens: Home, Animals, Shop, Missions, Multiplayer, Lobby, Game, Settings
server/       Node.js WebSocket relay (rooms + quick match) with smoke test
```
