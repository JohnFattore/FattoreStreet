// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import { getOptions } from '../src/components/AxiosFunctions';

//afterEach(() => { cleanup() });

it('getOptions Test', () => {
    return getOptions()
        .then((response) => {
            expect(response.data[0].ticker).to.include("AAPL")
        })
});