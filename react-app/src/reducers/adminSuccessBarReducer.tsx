import { createSlice, PayloadAction } from "@reduxjs/toolkit";

interface AdminSuccessBarState {
  tickers: string[];
  error: string;
}

const initialState: AdminSuccessBarState = {
  tickers: [],
  error: "",
};

const normalizeTicker = (value: string): string =>
  value.trim().toUpperCase();

const adminSuccessBarSlice = createSlice({
  name: "adminSuccessBar",
  initialState,
  reducers: {
    addTicker: (state, action: PayloadAction<string>) => {
      const ticker = normalizeTicker(action.payload);
      if (!ticker) {
        state.error = "Ticker is required.";
        return;
      }
      if (!/^[A-Z][A-Z0-9.\-]*$/.test(ticker)) {
        state.error = "Invalid ticker format.";
        return;
      }
      if (state.tickers.includes(ticker)) {
        state.error = `${ticker} is already in the list.`;
        return;
      }
      state.tickers.push(ticker);
      state.error = "";
    },
    removeTicker: (state, action: PayloadAction<string>) => {
      const ticker = normalizeTicker(action.payload);
      state.tickers = state.tickers.filter((item) => item !== ticker);
      if (state.error) {
        state.error = "";
      }
    },
    clearError: (state) => {
      state.error = "";
    },
    setValidationError: (state, action: PayloadAction<string>) => {
      state.error = action.payload;
    },
  },
});

export const { addTicker, removeTicker, clearError, setValidationError } =
  adminSuccessBarSlice.actions;

export default adminSuccessBarSlice.reducer;
