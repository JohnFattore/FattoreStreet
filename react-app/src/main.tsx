import ReactDOM from 'react-dom/client';
import App from "./App";
import {
    BrowserRouter,
} from "react-router-dom";
import { Provider } from "react-redux";
import { persistStore } from 'redux-persist';
import { PersistGate } from 'redux-persist/integration/react';
import { store } from './store';

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