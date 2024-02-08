// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import { expect, test, vi, afterEach } from 'vitest'
import React, { useContext } from 'react';
import Login from '../src/pages/Login'
import LoginForm from '../src/components/LoginForm'
import { ENVContext } from '../src/components/ENVContext';
import { BrowserRouter } from "react-router-dom";

afterEach(() => { document.body.innerHTML = ''; });
const ENV = {
    finnhubURL: "https://finnhub.io/api/v1/",
    djangoURL: "http://127.0.0.1:8000/portfolio/api/",
    finnhubKey: "ckivfdpr01qlj9q7a2rgckivfdpr01qlj9q7a2s0"
};

test('Login Page Test', () => {
    render(
        <BrowserRouter>
            <ENVContext.Provider value={ENV}>
                <Login />
            </ENVContext.Provider>
        </BrowserRouter>
    );
});

test('Login Form Test', () => {
    render(
        <BrowserRouter>
            <ENVContext.Provider value={ENV}>
                <LoginForm />
            </ENVContext.Provider>
        </BrowserRouter>
    );
});