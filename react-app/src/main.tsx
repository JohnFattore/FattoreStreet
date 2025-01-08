import ReactDOM from 'react-dom/client';
import App from "./App";
import {
    BrowserRouter,
} from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import rootReducer from './reducers/rootReducer';
import { persistStore, persistReducer } from 'redux-persist';
import { PersistGate } from 'redux-persist/integration/react';
import storage from 'redux-persist/lib/storage'; // Default storage (localStorage)

const persistConfig = {
    key: 'root', // The key to store the data in storage
    storage, // The storage to use (localStorage or sessionStorage)
    whitelist: ['user'], // Only persist the user reducer
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = configureStore({
    reducer: persistedReducer,
});

export const persistor = persistStore(store);

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

// router is on the outside of app so that useNavigate work in the App componment
const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement)
root.render(
    <Provider store={store}>
        <PersistGate loading={null} persistor={persistor}>
            <BrowserRouter>
                <App />
            </BrowserRouter>
        </PersistGate >
    </Provider>
);