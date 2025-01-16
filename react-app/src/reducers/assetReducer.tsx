import { createSlice } from '@reduxjs/toolkit';
import { deleteAsset, getAssets, postAsset } from '../components/axiosFunctions';
import { IAsset } from '../interfaces';

function sortAssets(assets: IAsset[], sortColumn: keyof IAsset, sortDirection: 'asc' | 'desc'): IAsset[] {
  return [...assets].sort((a, b) => {
    const aValue = a[sortColumn];
    const bValue = b[sortColumn];

    if (aValue == null || bValue == null) return 0;

    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
    return 0;
  });
}

interface AssetState {
  loading: boolean;
  assets: IAsset[];
  error: string;
  sort: {
    sortColumn: keyof IAsset | null;
    sortDirection: 'asc' | 'desc'
  }
}

const initialState: AssetState = {
  loading: false,
  assets: [],
  error: '',
  sort: {
    sortColumn: null,
    sortDirection: 'asc'
  }
};

const assetSlice = createSlice({
  name: 'asset',
  initialState,
  reducers: {
    errorAssets: (state, action) => {
      state.error = action.payload;
    },
    setAssetSort: (state, action) => {
      const { sortColumn, sortDirection } = action.payload;
      state.sort = { sortColumn, sortDirection };
      if (sortColumn) {
        state.assets = sortAssets(state.assets, sortColumn, sortDirection);
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getAssets.pending, (state) => {
        state.loading = true; 
      })
      .addCase(getAssets.fulfilled, (state, action) => {
        state.loading = false;
        state.assets = action.payload;
        state.error = ''
      })
      .addCase(getAssets.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })
      .addCase(postAsset.pending, (state) => {
        state.loading = true;
      })
      .addCase(postAsset.fulfilled, (state, action) => {
        state.loading = false;
        let asset = action.payload
        state.assets.push({
            ticker: asset.ticker,
            shares: asset.shares,
            costBasis: asset.costBasis,
            buyDate: asset.buyDate,
            totalCostBasis: asset.totalCostBasis,
            currentPrice: asset.currentPrice, 
            percentChange: asset.percentChange,
            SnP500Price: asset.SnP500Price,
            SnP500PercentChange: asset.SnP500PercentChange,
            id: asset.id
        })
        state.error = '';
      })
      .addCase(postAsset.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })      
      .addCase(deleteAsset.pending, (state) => {
        state.loading = true; 
      })
      .addCase(deleteAsset.fulfilled, (state, action) => {
        state.loading = false;
        state.assets = state.assets.filter(asset => asset.id !== action.payload.id); 
        state.error = '';
      })
      .addCase(deleteAsset.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      });
  },
});

export const { errorAssets, setAssetSort } = assetSlice.actions;
export default assetSlice.reducer;