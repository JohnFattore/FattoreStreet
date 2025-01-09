import { createSlice } from '@reduxjs/toolkit';

interface WatchListState {
  loading: boolean;
  tickers: string[];
  error: string;
}

const initialState: WatchListState = {
  loading: false,
  tickers: [],
  error: '',
};

// no loading stuff... idk maybe not an issue
const watchListSlice = createSlice({
  name: 'watchList',
  initialState,
  reducers: {    
    loadTickers: (state) => {
      if (localStorage.getItem("tickers") == null) {
        let tickers: string[] = (["VTI", "SPY"]);
        localStorage.setItem("tickers", (JSON.stringify(tickers)));
    };
      const tickers = JSON.parse(localStorage.getItem("tickers") as string) as string[]
      state.tickers = tickers
    },
    addTicker: (state, action) => {
      const updatedTickers = [...state.tickers, action.payload]; // Create a new array with the new ticker added
      state.tickers = updatedTickers;
      localStorage.setItem("tickers", (JSON.stringify(state.tickers)));
    },
    removeTicker: (state, action) => {
      const updatedTickers = state.tickers.filter((ticker) => ticker !== action.payload);
      state.tickers = updatedTickers;
      localStorage.setItem("tickers", (JSON.stringify(state.tickers)));
    },
},
});
export const { loadTickers, addTicker, removeTicker } = watchListSlice.actions;
export default watchListSlice.reducer;