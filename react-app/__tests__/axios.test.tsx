import { describe, it, expect } from 'vitest';
import { getFinancials, login, getAssets } from '../src/components/axiosFunctions';
import { configureStore } from '@reduxjs/toolkit';
import userReducer from '../src/reducers/userReducer';
import assetReducer from '../src/reducers/assetReducer'
import { IAsset } from '../src/interfaces';

describe('getFinancials test', () => {
  it('returns real user data from the API', async () => {
    const financials = await getFinancials("V");
    console.log(financials)
    //expect(financials).toHaveProperty('data');
  });
});

describe('login thunk test', () => {
  it('should log in and return user data', async () => {
    const store = configureStore({
      reducer: {
        user: userReducer,
      },
    });

    const result = await store.dispatch(
      login({ username: 'maxwell', password: 'maxwell' })
    );
    expect(result.type).toBe('users/login/fulfilled');
    expect(result.payload).toHaveProperty('username', 'maxwell');
    expect(result.payload).toHaveProperty('access');
    expect(result.payload).toHaveProperty('refresh');
  });
});

describe('getAssets thunk test', () => {
  it('should return users assets', async () => {
    const store = configureStore({
      reducer: {
        user: userReducer,
        asset: assetReducer,
      },
    });

    await store.dispatch(
      login({ username: 'maxwell', password: 'maxwell' })
    );

    const assetsResults = await store.dispatch(getAssets())
    const assets = assetsResults.payload as IAsset[];
    expect(assetsResults.type).toBe('assets/getAssets/fulfilled');
    expect(Array.isArray(assets)).toBe(true);
    expect(assets.length).toBeGreaterThan(0); 
  });
});