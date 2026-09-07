# 🥏 Scroll-Tron

**Scroll-Tron** is a fast-paced, neon-infused arcade game inspired by classic light cycle battles, but with an unconventional twist: you steer entirely using your **mouse scroll wheel**. Survive as long as possible, outmaneuver your opponents, and don't hit the walls!

Built with **Kotlin Multiplatform** and **Compose Multiplatform**, targeting both **Web (Wasm & JS)** and **Desktop (JVM)**.

---

## 🕹️ Play Online

Play Scroll-Tron directly in your browser without installing anything:

### 👉 [**tobias-franke.github.io/Scroll-Tron**](https://tobias-franke.github.io/Scroll-Tron/)

---

## ✨ Features

- **Unique Control Scheme**: Steer your cycle with mouse wheel flicks. Features realistic angular velocity and momentum decay for precision maneuvering.
- **P2P Multiplayer (Web)**: Host or join games with up to 4 players using decentralized Peer-to-Peer WebRTC networking powered by [PeerJS](https://peerjs.com/).
- **Intelligent AI Bots**: Add 1 to 3 autonomous bot players in multiplayer sessions for solo practice or filling empty slots. Bots use real-time raycast feelers with 2D DDA spatial acceleration, danger avoidance, and opponent trajectory prediction.
- **Desktop Bot Battles**: Play multiplayer locally on Desktop against AI bots.
- **Cyberpunk Visuals**: High-contrast neon color palette, animated grid scanlines, and glowing light trails.
- **De-Rez Crash FX**: Collisions trigger dramatic multi-ring shockwaves and sparkling particle explosions with smooth 60 FPS animations.
- **Transparent Cyber HUD**: Floating transparent glass HUD displays live scores and record high scores without obscuring trails beneath it.
- **Streamlined Lobby**: 4-character room codes with 1-click clipboard copy, keyboard paste (`Ctrl+V` / `Cmd+V`), dedicated paste buttons, and interactive player slots.
- **In-Game Diagnostics**: Press `F3` to toggle a real-time performance HUD showing FPS, TPS (ticks per second), and velocity metrics.
- **Secret Surprises**: Keep an eye out for easter eggs during singleplayer runs!

---

## 🎮 Controls

| Action | Control |
| :--- | :--- |
| **Steer Cycle** | **Mouse Scroll Wheel** (Scroll Up / Scroll Down) |
| **Restart / Rematch** | Press `R` or Click "RESTART" / "REMATCH" |
| **Main Menu / Back** | Press `Escape` |
| **Toggle Debug Overlay** | Press `F3` (FPS, TPS, Velocity) |
| **Copy Room Code** | Click the room code in the Host Lobby |
| **Paste Room Code** | Press `Ctrl+V` / `Cmd+V` or Click "PASTE" in Join Lobby |

---

## 🕹️ Game Modes

### Singleplayer (Survival)
Navigate an increasingly dense field of your own light trail. Each death cycles your trail hue (Cyan, Pink, Lime, Orange) while tracking your current score and all-time record.

### Multiplayer (Web & Desktop)
- **Web (P2P Online)**:
  1. Click **MULTIPLAYER** → **HOST GAME**. You will receive a 4-letter room code (click to copy).
  2. Friends enter the code on the **JOIN GAME** screen (or paste from clipboard) to connect.
  3. Supports up to 4 players total (1 Host + up to 3 Guests/Bots).
  4. Hosts can click empty player slots (`+`) to add AI bots, or click existing bots to remove them.
- **Desktop (JVM)**:
  - Host a local session and add 1–3 AI bots for instant practice and bot battles directly on desktop.

---

## 🛠️ Build and Run

### Prerequisites
- **JDK 17** or higher
- **Node.js** (for Wasm linkage verification)
- **Gradle** (provided via `./gradlew`)

### Desktop (JVM)
Run the desktop application locally:
```shell
./gradlew :composeApp:run
```

Package native desktop distributions (DMG / MSI / DEB):
```shell
./gradlew :composeApp:packageDmg     # macOS
./gradlew :composeApp:packageMsi     # Windows
./gradlew :composeApp:packageDeb     # Linux
```

### Web Application (Wasm or JS)
**WASM Target (Recommended):**
```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**JavaScript Target:**
```shell
./gradlew :composeApp:jsBrowserDevelopmentRun
```

**Build Production Web Distribution:**
```shell
./gradlew :composeApp:wasmJsBrowserDistribution
```

---

## 🧪 Testing & Verification

Run the full verification suite (including common unit tests and Wasm linkage verification):
```shell
./gradlew check
```

Run target-specific tests:
```shell
./gradlew :composeApp:jvmTest       # JVM tests
./gradlew :composeApp:wasmJsTest    # Wasm/JS tests
./gradlew :composeApp:jsTest        # JS tests
```

---

## 🧰 Tech Stack

- **UI & Graphics**: [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) & Skia
- **Language**: Kotlin Multiplatform (targeting JVM, Wasm, JS)
- **Networking**: [PeerJS](https://peerjs.com/) (WebRTC DataChannels)
- **Typography**: [Orbitron](https://fonts.google.com/specimen/Orbitron)

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for the full license text.
