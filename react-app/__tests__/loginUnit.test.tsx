// @vitest-environment jsdom

import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React, { useContext } from 'react';
import LoginForm from '../src/components/LoginForm'
import { login } from '../src/components/AxiosFunctions';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

vi.mock('../src/components/AxiosFunctions', () => ({
    __esModule: true,
    login: vi.fn(() => new Promise((resolve) => resolve({ data: { access: "maxwellKEY", refresh: "spikeKEY" } }))),
}));

// useNavigate is utlizied in the E2E Tests
vi.mock("react-router-dom", () => ({
    __esModule: true,
    useNavigate: vi.fn(() => console.log),
}));

test('Login form Test Render', () => {
    render(<LoginForm setError={console.log} setSuccess={console.log}/>);
});

test('Login form Test password blank', async () => {
    render(<LoginForm setError={console.log} setSuccess={console.log}/>);
    fireEvent.input(screen.getByPlaceholderText("Username"), { target: { value: "maxwell", }, })
    fireEvent.submit(screen.getByRole("button"));
    await waitFor(() => {
        expect(screen.queryByRole("usernameError")?.textContent).not.toBeDefined();
        expect(screen.queryByRole("passwordError")?.textContent).to.include("Error");
    })
});

test('Login form Test full successful submit', async () => {
    render(<LoginForm setError={console.log} setSuccess={console.log}/>);
    fireEvent.input(screen.getByPlaceholderText("Username"), { target: { value: "maxwell", }, })
    fireEvent.input(screen.getByPlaceholderText("Password"), { target: { value: "secret password", }, })
    fireEvent.submit(screen.getByRole("button"));
    await waitFor(() => {
        expect(screen.queryByRole("usernameError")?.textContent).not.toBeDefined();
        expect(screen.queryByRole("passwordError")?.textContent).not.toBeDefined();
        expect(login).toHaveBeenCalledWith("maxwell", "secret password");
    });
});