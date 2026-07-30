import { defineConfig } from "vitest/config";

// Node >= 25 ships a stub localStorage global that shadows jsdom's Storage;
// disable it so tests get jsdom's working localStorage. Workers inherit this env.
// Guarded because older Node (e.g. 18) rejects the flag in NODE_OPTIONS.
//
// `in` rather than `typeof globalThis.localStorage !== "undefined"`: on Node 26
// the global is an accessor that, without --localstorage-file, warns
// ("localStorage is not available because --localstorage-file was not provided")
// and evaluates to undefined. A typeof check therefore reads "undefined" on the
// exact versions that need the flag, so the guard never fired and the stub
// shadowed jsdom anyway -- every localStorage test failed with "Cannot read
// properties of undefined". `in` sees the property without invoking the getter.
if ("localStorage" in globalThis) {
  process.env.NODE_OPTIONS = [
    process.env.NODE_OPTIONS,
    "--no-experimental-webstorage",
  ]
    .filter(Boolean)
    .join(" ");
}

export default defineConfig({
  define: {
    "import.meta.env.VITE_APP_DJANGO_URL": JSON.stringify(
      "http://127.0.0.1:8000/",
    ),
    "import.meta.env.VITE_APP_SPRINGBOOT_URL": JSON.stringify(
      "http://127.0.0.1:8080/",
    ),
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./__tests__/setupTests.ts"],
  },
});
