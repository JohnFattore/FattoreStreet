import { createSlice } from '@reduxjs/toolkit';
import { getSnP500Price } from '../components/axiosFunctions';
import { ISnP500Price } from '../interfaces';

function sortSnP500(assets: ISnP500Price[], sortColumn: keyof ISnP500Price, sortDirection: 'asc' | 'desc'): ISnP500Price[] {
  return [...assets].sort((a, b) => {
    const aValue = a[sortColumn];
    const bValue = b[sortColumn];

    if (aValue == null || bValue == null) return 0;

    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
    return 0;
  });
}

interface SnP500State {
  loading: boolean;
  snp500Prices: ISnP500Price[];
  error: string;
  sort: {
    sortColumn: keyof ISnP500Price | null;
    sortDirection: 'asc' | 'desc'
  } 
}

const initialState: SnP500State = {
  loading: false,
  snp500Prices: [],
  error: '',
  sort: {
    sortColumn: null,
    sortDirection: 'asc'
  }
};

const snp500Slice = createSlice({
  name: 'snp500',
  initialState,
  reducers: {
    removeSnP500: (state, action) => {
      state.snp500Prices = state.snp500Prices.filter((snp500) => snp500.id !== action.payload);
    },
    errorSnP500: (state, action) => {
      state.error = action.payload;
    },
    setSnP500Sort: (state, action) => {
      const { sortColumn, sortDirection } = action.payload;
      state.sort = { sortColumn, sortDirection };
      if (sortColumn) {
        state.snp500Prices = sortSnP500(state.snp500Prices, sortColumn, sortDirection);
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getSnP500Price.pending, (state) => {
        state.loading = true;
      })
      .addCase(getSnP500Price.fulfilled, (state, action) => {
        state.loading = false;
        let snp500 = action.payload
        state.snp500Prices.push({
          date: snp500.date,
          costBasis: snp500.costBasis,
          currentPrice: snp500.currentPrice,
          percentChange: snp500.percentChange,
          id: snp500.id
        })
        state.error = ''
      })
      .addCase(getSnP500Price.rejected, (state, action) => {
        state.loading = false;
        state.error = (action.payload as string) || action.error.message || 'An error occurred';
      })
  },
});

export const { removeSnP500, errorSnP500, setSnP500Sort } = snp500Slice.actions;
export default snp500Slice.reducer;