import { createSlice } from '@reduxjs/toolkit';
import { deleteAsset, getAssets, postAsset, sellAsset } from '../components/axiosFunctions';
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
  asset: IAsset | null;
  error: string;
  sort: {
    sortColumn: keyof IAsset | null;
    sortDirection: 'asc' | 'desc'
  }
}

const initialState: AssetState = {
  loading: false,
  assets: [],
  asset: null,
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
    setAsset: (state, action) => {
      state.asset = action.payload;
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
        state.assets.push(asset)
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
      })
      .addCase(sellAsset.pending, (state) => {
        state.loading = true; 
      })
      .addCase(sellAsset.fulfilled, (state, action) => {
        state.loading = false;
        state.assets = state.assets.map(asset =>
          asset.id === action.payload.id ? action.payload : asset
        );

        state.asset = action.payload;
        state.error = '';
      })
      .addCase(sellAsset.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })
  },
});

export const { errorAssets, setAssetSort, setAsset } = assetSlice.actions;
export default assetSlice.reducer;