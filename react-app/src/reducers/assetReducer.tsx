import { createSlice } from '@reduxjs/toolkit';
import { deleteAsset, getAssets, postAsset } from '../components/axiosFunctions';
import { IAsset } from '../interfaces';

interface RestaurantState {
  loading: boolean;
  assets: IAsset[];
  error: string;
}

const initialState: RestaurantState = {
  loading: false,
  assets: [],
  error: '',
};

const assetSlice = createSlice({
  name: 'restaurant',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(getAssets.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(getAssets.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        // this has to be more specific, 
        state.assets = action.payload;  // Set the fetched data
        state.error = ''
      })
      .addCase(getAssets.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        state.error = action.error.message || 'An error occurred';  // Set the error message
      })
      .addCase(postAsset.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(postAsset.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        // this has to be more specific, 
        let asset = action.payload
        state.assets.push({
            ticker: asset.ticker,
            shares: asset.shares,
            costbasis: asset.costbasis,
            buy: asset.buy,
            SnP500Price: 200, // probably fetch this, this is the historical date price getSnPPrice(asset.buy), or just fetch entire asset
            id: asset.id
        })  // Set the fetched data
      })
      .addCase(postAsset.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        state.error = action.error.message || 'An error occurred';  // Set the error message
      })      
      .addCase(deleteAsset.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(deleteAsset.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        state.assets = state.assets.filter(asset => asset.id !== action.payload.id);  // Remove the deleted asset
    })
      .addCase(deleteAsset.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        // tranlateError could go here!
        state.error = action.error.message || 'An error occurred';  // Set the error message
      });
  },
});

export default assetSlice.reducer;