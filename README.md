# Scroll-Tron

Scroll-Tron is a fast-paced, neon-infused arcade game built using [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform). Steer your light trail using your mouse's scroll wheel and try to survive as long as possible without hitting the edge or your own trail!

## Features
- **Scroll to Steer**: Unique control scheme utilizing the mouse scroll wheel.
- **Neon Aesthetic**: Retro-futuristic glowing visuals.
- **Cross-Platform**: Playable on the web (WASM/JS) and desktop (JVM).
- **Easter Eggs**: Discover hidden surprises as you play!

## Controls
- **Scroll Up/Down**: Steer left/right
- **R / Click Restart**: Restart after a crash
- **Escape**: Exit game

## How to Build and Run

### Desktop (JVM)
Run the development version of the desktop application using Gradle:
```shell
./gradlew :composeApp:run
```

### Web Application (Wasm or JS)
Run the web application locally. You can target either WebAssembly (faster, for modern browsers) or JavaScript (for older browsers).

**WASM Target:**
```shell
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

**JavaScript Target:**
```shell
./gradlew :composeApp:jsBrowserDevelopmentRun
```

After starting the server, open your web browser to the provided local URL to play the game!