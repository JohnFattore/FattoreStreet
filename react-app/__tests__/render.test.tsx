// @vitest-environment jsdom
import { render, screen, cleanup, waitFor, fireEvent, getByPlaceholderText } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React from "react";
import { Provider } from "react-redux";
import '@testing-library/jest-dom';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import Portfolio from '../src/pages/Portfolio';
import Chatbot from '../src/pages/Chatbot';
import Restaurants from '../src/pages/Restaurants';
import Reviews from '../src/pages/Reviews';
import { getAssets, login } from '../src/components/axiosFunctions';
import { act } from 'react';
import { store } from '../src/store';

afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    localStorage.clear();
});

export function renderWrapped(ui) {
    return render(
        <Provider store={store}>
            <MemoryRouter>
                {ui}
            </MemoryRouter >
        </Provider>
    );
}

test('Portfolio Test', async () => {
    renderWrapped(<Portfolio />);
    const usernameInput = screen.getByPlaceholderText('Username');
    const passwordInput = screen.getByPlaceholderText('Password');
    const submitButton = screen.getByRole('button', { name: /login/i });
  
    await userEvent.type(usernameInput, 'testUser');
    await userEvent.type(passwordInput, 'testPass');
  
    // Submit the form
    await act(async () => {
        await userEvent.click(submitButton);
        await store.dispatch(login({ username: 'testUser', password: 'testPass' }));
        await store.dispatch(getAssets());
    });
    console.log(store.getState());
    screen.debug()
  });

test('Chatbot Test', async () => {
    renderWrapped(<Chatbot />);
});

test('Restaurants Test', async () => {
    renderWrapped(<Restaurants />);
});

test('Reviews Test', async () => {
    renderWrapped(<Reviews />);
});