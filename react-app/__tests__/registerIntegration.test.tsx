// @vitest-environment jsdom

import { render, screen, cleanup, waitFor, fireEvent } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from 'react';
import Register from '../src/pages/Register'
import { postUser, login } from '../src/components/axiosFunctions';
import { useNavigate } from 'react-router-dom';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

vi.mock('../src/components/axiosFunctions', () => ({
    __esModule: true,
    login: vi.fn(() => new Promise((resolve) => resolve({ data: { access: "maxwellKEY", refresh: "spikeKEY" } }))),
    postUser: vi.fn(() => new Promise((resolve) => resolve({ status: 201 }))),
}));

vi.mock("react-router-dom", () => ({
    __esModule: true,
    useNavigate: vi.fn(() => console.log),
}));

test('Register Page Test Render', () => {
    render(<Register />);
});

test('Register Page Test blank submit', async () => {
    render(<Register />);
    fireEvent.submit(screen.getByRole("button"));
    await waitFor(() => {
        expect(screen.queryByRole("usernameError")?.textContent).toBeDefined();
        expect(screen.queryByRole("passwordError")?.textContent).toBeDefined();
        expect(screen.queryByRole("emailError")?.textContent).toBeDefined();
    });
});

test('Register Form Test successful submit', async () => {
    render(<Register />);
    fireEvent.input(screen.getByPlaceholderText("Username"), { target: { value: "maxwell", }, });
    fireEvent.input(screen.getByPlaceholderText("Password"), { target: { value: "maxwell", }, });
    fireEvent.input(screen.getByPlaceholderText("Email"), { target: { value: "mail@mail.com", }, });
    fireEvent.submit(screen.getByRole("button"));
    await waitFor(() => {
        expect(login).toHaveBeenCalledWith("maxwell", "maxwell");
        expect(postUser).toHaveBeenCalledWith("maxwell", "maxwell", "mail@mail.com");
        expect(useNavigate).toHaveBeenCalled();
        expect(screen.queryByRole("usernameError")?.textContent).not.toBeDefined();
        expect(screen.queryByRole("passwordError")?.textContent).not.toBeDefined();
        expect(screen.queryByRole("emailError")?.textContent).not.toBeDefined();
    });   
});