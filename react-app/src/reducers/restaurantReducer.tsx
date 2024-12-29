import { createSlice } from '@reduxjs/toolkit';
import { getRestaurants } from '../components/axiosFunctions';
import { IRestaurant } from '../interfaces';

interface RestaurantState {
  loading: boolean;
  restaurants: IRestaurant[];
  error: string;
}

const initialState: RestaurantState = {
  loading: false,
  restaurants: [],
  error: '',
};

const restaurantSlice = createSlice({
  name: 'restaurant',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(getRestaurants.pending, (state) => {
        state.loading = true;  // Set loading to true when the async call is pending
      })
      .addCase(getRestaurants.fulfilled, (state, action) => {
        state.loading = false;  // Set loading to false when the async call is fulfilled
        // this has to be more specific, 
        state.restaurants = action.payload;  // Set the fetched data
      })
      .addCase(getRestaurants.rejected, (state, action) => {
        state.loading = false;  // Set loading to false if the call failed
        state.error = action.error.message || 'An error occurred';;  // Set the error message
      });
  },
});
export default restaurantSlice.reducer;