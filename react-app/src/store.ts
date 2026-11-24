import { configureStore } from "@reduxjs/toolkit";
import rootReducer from "./reducers/rootReducer";
import { persistReducer, Storage, PersistedState } from "redux-persist";
import { djangoApi, springbootApi } from "./functions/api";
import { hasActiveSession } from "./functions/auth";

// Vite 8 production builds mis-resolve the CJS default export of
// redux-persist/lib/storage (the import becomes the module namespace, so
// storage.getItem is missing and the app crashes on boot). Define the
// localStorage engine directly instead of importing it.
const storage: Storage = {
  getItem: (key) => Promise.resolve(localStorage.getItem(key)),
  setItem: (key, value) => Promise.resolve(localStorage.setItem(key, value)),
  removeItem: (key) => Promise.resolve(localStorage.removeItem(key)),
};

/**
 * Cleans up persisted state on every boot before Redux rehydrates:
 * - v0 → v1: drops legacy loading/error fields removed when auth moved to RTK Query
 * - clears expired JWT sessions so the UI never boots into a false "logged in" state
 */
export function sanitizePersistedState(state: PersistedState): PersistedState {
  const persisted = state as
    | (PersistedState & {
        user?: Record<string, unknown>;
      })
    | undefined;
  if (!persisted?.user) {
    return state;
  }

  const { loading: _loading, error: _error, ...restUser } = persisted.user;
  void _loading;
  void _error;

  const access = typeof restUser.access === "string" ? restUser.access : "";
  const refresh = typeof restUser.refresh === "string" ? restUser.refresh : "";
  const sanitizedUser =
    (access || refresh) && !hasActiveSession({ access, refresh })
      ? {
          ...restUser,
          username: "",
          access: "",
          refresh: "",
        }
      : restUser;

  return {
    ...persisted,
    user: sanitizedUser,
  } as PersistedState;
}

const persistConfig = {
  key: "root", // The key to store the data in storage
  storage, // The storage to use (localStorage or sessionStorage)
  whitelist: ["user", "location", "adminSuccessBar"], // Persist selected reducers
  version: 1,
  migrate: (state: PersistedState) =>
    Promise.resolve(sanitizePersistedState(state)),
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = configureStore({
  reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false, // redux-persist needs this
    }).concat(djangoApi.middleware, springbootApi.middleware),
});
