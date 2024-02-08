import { screen } from '@testing-library/react';
import { expect} from 'vitest'

export default function textContains(role, text) {
    return expect(screen.queryByRole(role)?.textContent).to.include(text);
}