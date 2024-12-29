import { createSlice } from '@reduxjs/toolkit';
import { login } from '../components/axiosFunctions';

interface UserSlice {
  loading: boolean;
  username: string,
  token: string,
  error: string;
}

const initialState: UserSlice = {
  loading: false,
  username: '',
  token: '',
  error: '',
};

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {},
    extraReducers: (builder) => {
      builder
        .addCase(login.pending, (state) => {
          state.loading = true;  // Set loading to true when the async call is pending
        })
        .addCase(login.fulfilled, (state, action) => {
          state.loading = false;  // Set loading to false when the async call is fulfilled
          state.username = action.payload.username;
          state.token = action.payload.token;
        })
        .addCase(login.rejected, (state, action) => {
          state.loading = false;  // Set loading to false if the call failed
          state.error = action.error.message || 'An error occurred';  // Set the error message
        })
    }
});

export default userSlice.reducer;