/*
import { describe, it, expect } from 'vitest';
import { getFinancials, login, getAssets, postAsset, deleteAsset, sellAsset } from '../src/functions/axiosFunctions';
import { configureStore } from '@reduxjs/toolkit';
import userReducer from '../src/reducers/userReducer';
import assetReducer from '../src/reducers/assetReducer'
import { IAsset } from '../src/interfaces';

describe('getFinancials for stock test', () => {
  it('returns real user data from the API', async () => {
    const financials = await getFinancials("V");
    expect(financials).toHaveProperty('data');
  });
});

describe('getFinancials for ETF test', () => {
  it('returns real user data from the API', async () => {
    const financials = await getFinancials("VTI");
    expect(financials).toHaveProperty('data');
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
      login({ username: 'jimmy', password: 'jimmy' })
    );
    expect(result.type).toBe('users/login/fulfilled');
    expect(result.payload).toHaveProperty('username', 'jimmy');
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
      login({ username: 'jimmy', password: 'jimmy' })
    );

    const assetsResponse = await store.dispatch(getAssets())
    const assets = assetsResponse.payload as IAsset[];
    expect(assetsResponse.type).toBe('assets/getAssets/fulfilled');
    expect(Array.isArray(assets)).toBe(true);
    expect(assets.length).toBeGreaterThan(0); 
  });
});

describe('postAsset / sellAsset / deleteAsset thunk test', () => {
  it('should add and remove asset', async () => {
    const store = configureStore({
      reducer: {
        user: userReducer,
        asset: assetReducer,
      },
    });

    await store.dispatch(
      login({ username: 'jimmy', password: 'jimmy' })
    );

    const postAssetResponse = await store.dispatch(postAsset({
                            ticker: "AAPL",
                            shares: 1.5,
                            buyDate: '2024-10-14'
                        }))
    expect(postAssetResponse.type).toBe('assets/postAsset/fulfilled');
    expect(postAssetResponse.payload).toHaveProperty("ticker")

    const sellAssetResponse = await store.dispatch(sellAsset({id: postAssetResponse.payload.id, sellDate: '2024-10-21'}))
    expect(sellAssetResponse.type).toBe('assets/sellAsset/fulfilled');
    expect(sellAssetResponse.payload).toHaveProperty("id")                     

    const deleteAssetResponse = await store.dispatch(deleteAsset(postAssetResponse.payload.id))
    expect(deleteAssetResponse.type).toBe('assets/deleteAsset/fulfilled');
    expect(deleteAssetResponse.payload).toHaveProperty("id")
  });
});
*/