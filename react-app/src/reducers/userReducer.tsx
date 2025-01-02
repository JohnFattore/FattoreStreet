import { createSlice } from '@reduxjs/toolkit';
import { login, refreshLogin } from '../components/axiosFunctions';

interface UserSlice {
  loading: boolean;
  username: string,
  access: string,
  refresh: string,
  error: string;
}

const initialState: UserSlice = {
  loading: false,
  username: '',
  access: '',
  refresh: '',
  error: '', 
};

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {
    logout: (state) => {
      state.username = '';
      state.access = '';
      state.refresh = '';
      state.error = '';
      window.location.reload(); // Reload the page
    },
    clearErrors: (state) => {
      state.error = '';
    },
  },
    extraReducers: (builder) => {
      builder
        .addCase(login.pending, (state) => {
          state.loading = true;
        })
        .addCase(login.fulfilled, (state, action) => {
          state.loading = false;
          state.username = action.payload.username;
          state.access = action.payload.access;
          state.refresh = action.payload.refresh;
          state.error = ''
        })
        .addCase(login.rejected, (state, action) => {
          state.loading = false;  // Set loading to false if the call failed
          state.error = (action.payload as string) || action.error.message || 'An error occurred';  // Set the error message
          state.refresh = ''
        })
        .addCase(refreshLogin.pending, (state) => {
          state.loading = true;
        })
        .addCase(refreshLogin.fulfilled, (state, action) => {
          state.loading = false;
          state.access = action.payload.access;
        })
        .addCase(refreshLogin.rejected, (state, action) => {
          state.loading = false;  // Set loading to false if the call failed
          state.error = (action.payload as string) || action.error.message || 'An error occurred';  // Set the error message
          state.refresh = ''
        })
    }
});

export const { logout, clearErrors } = userSlice.actions; // Export the logout action
export default userSlice.reducer; 