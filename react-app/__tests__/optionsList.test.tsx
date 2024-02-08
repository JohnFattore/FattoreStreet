// @vitest-environment jsdom
import { render, screen, cleanup, waitFor } from '@testing-library/react';
import { expect, test, it, vi, afterEach } from 'vitest'
import React from 'react';
import OptionsList from '../src/components/OptionsList'

afterEach(() => { cleanup() });

it('OptionsList Test', async () => {
    render(<OptionsList />);
    await waitFor(() => { expect(screen.debug()) })
    expect(screen.queryByRole('ticker'))
    //textContains('ticker', 'AAPL')
    //textContains('sunday', '2024-2-11')
});