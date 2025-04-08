import { configureStore } from "@reduxjs/toolkit";
import rootReducer from './reducers/rootReducer';
import { persistReducer } from 'redux-persist';
import storage from 'redux-persist/lib/storage';

const persistConfig = {
    key: 'root', // The key to store the data in storage
    storage, // The storage to use (localStorage or sessionStorage)
    whitelist: ['user', 'snp500Prices', 'location'], // Only persist the user reducer
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = configureStore({
    reducer: persistedReducer,
});