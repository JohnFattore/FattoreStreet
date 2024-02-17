// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import Lucy from '../src/components/Lucy'
import getToy from '../src/components/getToy';
import React from 'react';

//import { getOptions } from '../src/components/AxiosFunctions';

afterEach(() => { cleanup() });

vi.mock('../src/components/getToy', () => ({
    __esModule: true,
    default: vi.fn(() => "Mocked Toy"),
}));

it('Lucy Test', () => {
    render(<Lucy />);
    expect(screen.queryByRole('toy')?.textContent).to.include("Mocked Toy")
});

it('Get Toy Test', () => {
    const toy = getToy();
    expect(toy).toBe("Mocked Toy");
});