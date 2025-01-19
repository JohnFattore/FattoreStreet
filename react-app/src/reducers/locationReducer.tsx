import { createSlice } from '@reduxjs/toolkit';

interface locationSlice {
  loading: boolean;
  state: string,
  city: string,
  error: string;
}

const initialState: locationSlice = {
  loading: false,
  state: 'TN',
  city: 'Nashville',
  error: '', 
};

const locationSlice = createSlice({
  name: 'location',
  initialState,
  reducers: {
    setLocation: (state, action) => {
      state.state = action.payload.state;
      state.city = action.payload.city;
      state.error = '';
      state.loading = false;
    },
  },
});

export const { setLocation } = locationSlice.actions; // Export the logout action
export default locationSlice.reducer; 