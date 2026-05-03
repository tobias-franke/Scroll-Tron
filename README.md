# 🚀 Scroll-Tron


**Scroll-Tron** is a fast-paced, neon-infused arcade game inspired by the classic Tron light cycles, but with a twist: you steer using your **mouse scroll wheel**. Survive as long as possible, outmaneuver your opponents, and don't hit the walls!

---

## 🕹️ Play it here

You can play Scroll-Tron directly in your browser here:

### 👉 [**tobias-franke.github.io/Scroll-Tron**](https://tobias-franke.github.io/Scroll-Tron/)

---

## ✨ Features

- **Unique Control Scheme**: Steer your light trail using the mouse scroll wheel for a unique challenge.
- **Multiplayer Mode**: Host or join games with up to 4 players using Peer-to-Peer networking.
- **Neon Aesthetic**: Retro-futuristic visuals with glowing trails and high-contrast colors.
- **Cross-Platform**: Built with Compose Multiplatform, targeting JVM (Desktop) and Wasm/JS (Web).
- **Easter Eggs**: Discover hidden surprises as you play!

---

## 🕹️ Controls

| Action | Control |
| :--- | :--- |
| **Steer Left/Right** | Scroll Up / Scroll Down |
| **Restart Game** | Press `R` or Click "RESTART" |
| **Exit to Menu** | Press `Escape` |

---

## 🌐 Multiplayer

Scroll-Tron features a robust P2P multiplayer mode powered by [PeerJS](https://peerjs.com/).

1. **Host a Game**: Click on "MULTIPLAYER" and then "HOST". You will receive a 4-letter room code.
2. **Join a Game**: Enter the room code provided by the host and click "JOIN".
3. **Player Limit**: Supports up to 4 players (1 Host + 3 Guests).

*Note: Multiplayer is currently optimized for the Web version (WasmJS/JS).*

---

## 🛠️ Build and Run

### Prerequisites
- JDK 17 or higher
- Gradle (provided via `./gradlew`)

### Desktop (JVM)
```shell
./gradlew :composeApp:run
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

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for the full license text.