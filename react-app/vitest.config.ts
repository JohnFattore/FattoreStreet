import { defineConfig } from "vitest/config";

export default defineConfig({
  define: {
    "import.meta.env.VITE_APP_DJANGO_USERS_URL": JSON.stringify("http://127.0.0.1:8000/users/api/"),
    "import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL": JSON.stringify("http://127.0.0.1:8000/portfolio/api/"),
    "import.meta.env.VITE_APP_DJANGO_RESTAURANTS_URL": JSON.stringify("http://127.0.0.1:8000/restaurants/api/"),
    "import.meta.env.VITE_APP_DJANGO_CHATBOT_URL": JSON.stringify("http://127.0.0.1:8000/chatbot/api/"),
    "import.meta.env.VITE_APP_SPRINGBOOT_URL": JSON.stringify("http://127.0.0.1:8080/"),
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./__tests__/setupTests.ts"],
  },
});