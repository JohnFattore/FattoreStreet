import { defineConfig } from "vitest/config";

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