import { createSlice } from '@reduxjs/toolkit';
import { getSnP500Price } from '../components/axiosFunctions';
import { ISnP500Price } from '../interfaces';

interface SnP500State {
  loading: boolean;
  snp500Prices: ISnP500Price[];
  error: string;
}

const initialState: SnP500State = {
  loading: false,
  snp500Prices: [],
  error: '',
};

const snp500Slice = createSlice({
  name: 'snp500',
  initialState,
  reducers: {},
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
            price: snp500.price,
            dividends: snp500.dividends,
            reinvestShares: snp500.reinvestShares,
            id: snp500.id
        })        
        state.error = ''
      })
      .addCase(getSnP500Price.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'An error occurred';
      })
  },
});

export default snp500Slice.reducer;