import { render } from '@testing-library/react';
import { Provider } from "react-redux";
import { MemoryRouter } from 'react-router-dom';
import { configureStore } from "@reduxjs/toolkit";
import rootReducer from '../src/reducers/rootReducer';
import { api } from '../src/functions/api';

export function createTestStore(preloadedState = {}) {
    return configureStore({
        reducer: rootReducer,
        preloadedState,
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware({
                serializableCheck: false,
            }).concat(api.middleware),
    });
}

export function renderWithProviders(ui: React.ReactNode, { preloadedState = {}, store = createTestStore(preloadedState) } = {}) {
    return {
        ...render(
            <Provider store={store}>
                <MemoryRouter>
                    {ui}
                </MemoryRouter>
            </Provider>
        ),
        store,
    };
}
