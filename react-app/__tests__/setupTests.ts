import { cleanup } from '@testing-library/react';
import { afterEach, beforeAll, afterAll, vi } from "vitest";
import { worker } from './mocks/browser';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
    worker.resetHandlers()
});


// This will start the server before the tests run
beforeAll(() => worker.start());

// This will close the server after the tests finish
afterAll(() => worker.stop());