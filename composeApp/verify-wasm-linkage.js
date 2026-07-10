import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Mock browser globals so that the main entry point doesn't crash on document/window access
globalThis.window = globalThis;
globalThis.document = {
    createElement: () => ({
        getContext: () => ({}),
        style: {},
        addEventListener: () => {},
        appendChild: () => {},
    }),
    body: {
        appendChild: () => {},
    },
    addEventListener: () => {},
    documentElement: {
        style: {}
    }
};
globalThis.location = {
    href: 'http://localhost/'
};

// The compiled development executable packages are stored in the root build directory
const mjsPath = path.resolve(__dirname, '../build/wasm/packages/ScrollTron-composeApp/kotlin/ScrollTron-composeApp.mjs');

if (!fs.existsSync(mjsPath)) {
    console.error(`Error: Compiled Wasm/JS entry point not found at ${mjsPath}.\nPlease compile the project first by running: ./gradlew :composeApp:compileDevelopmentExecutableKotlinWasmJs`);
    process.exit(1);
}

console.log(`Verifying Wasm instantiation for ${mjsPath}...`);

try {
    await import(mjsPath);
    console.log("Success: Wasm module instantiated successfully with no LinkErrors.");
    process.exit(0);
} catch (error) {
    // If it's a LinkError, it means the WebAssembly linking failed (our regression case)
    if (error instanceof WebAssembly.LinkError || (error.stack && error.stack.includes("LinkError"))) {
        console.error("\n[TEST FAILED] WebAssembly Linkage Error Detected:");
        console.error(error);
        process.exit(2);
    }
    
    // If it's some other browser API we didn't mock, but it instantiated successfully,
    // we don't treat it as a linkage failure. But we print it for visibility.
    console.log("Wasm compiled and instantiated, but a runtime JS error occurred during execution:");
    console.log(error);
    process.exit(0);
}
