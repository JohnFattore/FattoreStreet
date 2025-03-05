import { createSlice } from '@reduxjs/toolkit';
import { login, postUser, refreshLogin } from '../components/axiosFunctions';

interface UserSlice {
  loading: boolean;
  username: string,
  access: string,
  refresh: string,
  error: string,
  darkMode: boolean
}

const initialState: UserSlice = {
  loading: false,
  username: '',
  access: '',
  refresh: '',
  error: '', 
  darkMode: false,
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
      state.loading = false;
    },
    clearErrors: (state) => {
      state.error = '';
    },
    setUserError: (state, action) => {
      state.error = action.payload;
    },
    setUserDarkMode: (state, action) => {
      state.darkMode = action.payload;
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
          state.access = ''
          state.refresh = ''
        })
        .addCase(postUser.pending, (state) => {
          state.loading = true;
        })
        .addCase(postUser.fulfilled, (state) => {
          state.loading = false;
          state.error = ''
        })
        .addCase(postUser.rejected, (state, action) => {
          state.loading = false;  // Set loading to false if the call failed
          state.error = (action.payload as string) || action.error.message || 'An error occurred';  // Set the error message
        })
    }
});

export const { logout, clearErrors, setUserError, setUserDarkMode } = userSlice.actions; // Export the logout action
export default userSlice.reducer; 