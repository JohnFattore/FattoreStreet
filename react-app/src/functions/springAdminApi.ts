import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { store } from '../store';
import { refreshLogin } from './axiosFunctions';

type ConfigWithRetry = InternalAxiosRequestConfig & { _springAdminRetry?: boolean };

/**
 * Ensures path concatenation works: `base + "admin/..."` must not produce `...8080admin`.
 */
export function normalizeSpringBootBaseUrl(raw: string | undefined): string {
    const trimmed = (raw ?? '').trim();
    if (!trimmed) {
        return 'http://127.0.0.1:8080/';
    }
    return trimmed.endsWith('/') ? trimmed : `${trimmed}/`;
}

/**
 * Axios instance for Spring Boot admin and sec-api calls. Attaches the current Django JWT from Redux
 * on each request (so the header is always fresh) and retries once after `refreshLogin` on 401.
 */
export const springAdminApi = axios.create({
    baseURL: normalizeSpringBootBaseUrl(import.meta.env.VITE_APP_SPRINGBOOT_URL),
});

springAdminApi.interceptors.request.use((config) => {
    const access = store.getState().user.access?.trim();
    if (access) {
        config.headers.set('Authorization', `Bearer ${access}`);
    }
    return config;
});

springAdminApi.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
        const original = error.config as ConfigWithRetry | undefined;
        if (
            !original ||
            original._springAdminRetry ||
            error.response?.status !== 401
        ) {
            return Promise.reject(error);
        }
        original._springAdminRetry = true;
        try {
            await store.dispatch(refreshLogin()).unwrap();
        } catch {
            return Promise.reject(error);
        }
        return springAdminApi.request(original);
    },
);
