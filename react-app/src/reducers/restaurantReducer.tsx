import { createSlice } from '@reduxjs/toolkit';
import { getRestaurants } from '../components/axiosFunctions';
import { IRestaurant } from '../interfaces';

function sortRestaurant(restaurants: IRestaurant[], sortColumn: keyof IRestaurant, sortDirection: 'asc' | 'desc'): IRestaurant[] {
  return [...restaurants].sort((a, b) => {
    const aValue = a[sortColumn];
    const bValue = b[sortColumn];

    if (aValue == null || bValue == null) return 0;

    if (aValue < bValue) return sortDirection === 'asc' ? -1 : 1;
    if (aValue > bValue) return sortDirection === 'asc' ? 1 : -1;
    return 0;
  });
}

interface RestaurantState {
  loading: boolean;
  restaurants: IRestaurant[];
  error: string;
  sort: {
    sortColumn: keyof IRestaurant | null;
    sortDirection: 'asc' | 'desc'
  }
}

const initialState: RestaurantState = {
  loading: false,
  restaurants: [],
  error: '',
  sort: {
    sortColumn: null,
    sortDirection: 'asc'
  }
};

const restaurantSlice = createSlice({
  name: 'restaurant',
  initialState,
  reducers: {
    setRestaurantSort: (state, action) => {
      const { sortColumn, sortDirection } = action.payload;
      state.sort = { sortColumn, sortDirection };
      if (sortColumn) {
        state.restaurants = sortRestaurant(state.restaurants, sortColumn, sortDirection);
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getRestaurants.pending, (state) => {
        state.loading = true; 
      })
      .addCase(getRestaurants.fulfilled, (state, action) => {
        state.loading = false; 
        state.restaurants = action.payload;
        state.error = '';
      })
      .addCase(getRestaurants.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'An error occurred';
      });
  },
});

export const { setRestaurantSort } = restaurantSlice.actions;
export default restaurantSlice.reducer;