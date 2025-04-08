import { cleanup } from '@testing-library/react';
import { afterEach, beforeAll, afterAll, vi } from "vitest";
import { server } from './mocks/server';

// This will start the server before the tests run
beforeAll(() => server.listen());

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
    server.resetHandlers()
});


// This will close the server after the tests finish
afterAll(() => server.close());