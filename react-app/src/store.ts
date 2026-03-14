import { configureStore } from "@reduxjs/toolkit";
import rootReducer from './reducers/rootReducer';
import { persistReducer } from 'redux-persist';
import storage from 'redux-persist/lib/storage';
import { api } from './functions/api'

const persistConfig = {
    key: 'root', // The key to store the data in storage
    storage, // The storage to use (localStorage or sessionStorage)
    whitelist: ['user', 'location', 'adminSuccessBar'], // Persist selected reducers
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = configureStore({
    reducer: persistedReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      serializableCheck: false, // redux-persist needs this
    }).concat(api.middleware),
});