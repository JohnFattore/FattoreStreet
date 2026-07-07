import { render } from '@testing-library/react';
import { Provider } from "react-redux";
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { configureStore } from "@reduxjs/toolkit";
import rootReducer from '../src/reducers/rootReducer';
import { djangoApi, springbootApi } from "../src/functions/api";

export function createTestStore(preloadedState = {}) {
    return configureStore({
        reducer: rootReducer,
        preloadedState,
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware({
                serializableCheck: false,
            }).concat(djangoApi.middleware, springbootApi.middleware),
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

export function renderWithRoute(
    ui: React.ReactElement,
    {
        path,
        initialEntry,
        preloadedState = {},
        extraRoutes = [],
    }: {
        path: string;
        initialEntry: string;
        preloadedState?: Record<string, unknown>;
        extraRoutes?: { path: string; element: React.ReactElement }[];
    }
) {
    const store = createTestStore(preloadedState);
    return {
        ...render(
            <Provider store={store}>
                <MemoryRouter initialEntries={[initialEntry]}>
                    <Routes>
                        <Route path={path} element={ui} />
                        {extraRoutes.map((route) => (
                            <Route key={route.path} path={route.path} element={route.element} />
                        ))}
                    </Routes>
                </MemoryRouter>
            </Provider>
        ),
        store,
    };
}
