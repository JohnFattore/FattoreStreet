import { configureStore } from "@reduxjs/toolkit";
import rootReducer from './reducers/rootReducer';
import { persistReducer, Storage, PersistedState } from 'redux-persist';
import { djangoApi, springbootApi } from "./functions/api";

// Vite 8 production builds mis-resolve the CJS default export of
// redux-persist/lib/storage (the import becomes the module namespace, so
// storage.getItem is missing and the app crashes on boot). Define the
// localStorage engine directly instead of importing it.
const storage: Storage = {
    getItem: (key) => Promise.resolve(localStorage.getItem(key)),
    setItem: (key, value) => Promise.resolve(localStorage.setItem(key, value)),
    removeItem: (key) => Promise.resolve(localStorage.removeItem(key)),
};

const persistConfig = {
    key: 'root', // The key to store the data in storage
    storage, // The storage to use (localStorage or sessionStorage)
    whitelist: ['user', 'location', 'adminSuccessBar'], // Persist selected reducers
    version: 1,
    // v0 → v1: the user slice dropped its loading/error fields when auth moved
    // to RTK Query mutations; strip them from persisted state
    migrate: (state: PersistedState) => {
        const persisted = state as (PersistedState & { user?: Record<string, unknown> }) | undefined;
        if (persisted?.user) {
            delete persisted.user.loading;
            delete persisted.user.error;
        }
        return Promise.resolve(state);
    },
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = configureStore({
    reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false, // redux-persist needs this
    }).concat(djangoApi.middleware, springbootApi.middleware),
});