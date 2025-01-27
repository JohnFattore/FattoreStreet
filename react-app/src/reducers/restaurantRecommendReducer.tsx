import { createSlice } from '@reduxjs/toolkit';
import { getRestaurantRecommendations } from '../components/axiosFunctions';
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

const restaurantRecommendSlice = createSlice({
  name: 'restaurantRecommend',
  initialState,
  reducers: {
    setRestaurantRecommendSort: (state, action) => {
      const { sortColumn, sortDirection } = action.payload;
      state.sort = { sortColumn, sortDirection };
      if (sortColumn) {
        state.restaurants = sortRestaurant(state.restaurants, sortColumn, sortDirection);
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(getRestaurantRecommendations.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(getRestaurantRecommendations.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        state.restaurants = action.payload;  // Set the fetched data
      })
      .addCase(getRestaurantRecommendations.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        state.error = action.error.message || 'An error occurred';;  // Set the error message
      });
  },
});

export const { setRestaurantRecommendSort } = restaurantRecommendSlice.actions;
export default restaurantRecommendSlice.reducer;