import { defineConfig } from "vitest/config";

// Node >= 25 ships a stub localStorage global that shadows jsdom's Storage;
// disable it so tests get jsdom's working localStorage. Workers inherit this env.
// Guarded because older Node (e.g. 20 in CI) rejects the flag in NODE_OPTIONS.
if (typeof globalThis.localStorage !== "undefined") {
  process.env.NODE_OPTIONS = [process.env.NODE_OPTIONS, "--no-experimental-webstorage"]
    .filter(Boolean)
    .join(" ");
}

export default defineConfig({
  define: {
    "import.meta.env.VITE_APP_DJANGO_URL": JSON.stringify("http://127.0.0.1:8000/"),
    "import.meta.env.VITE_APP_SPRINGBOOT_URL": JSON.stringify("http://127.0.0.1:8080/"),
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./__tests__/setupTests.ts"],
  },
});