import { createSlice } from '@reduxjs/toolkit';

interface UserSlice {
  loading: boolean;
  user: string,
  token: string,
  error: string;
}

const initialState: UserSlice = {
  loading: false,
  user: '',
  token: '',
  error: '',
};

const userSlice = createSlice({
  name: 'user',
  initialState,
  reducers: {},
});

export default userSlice.reducer;