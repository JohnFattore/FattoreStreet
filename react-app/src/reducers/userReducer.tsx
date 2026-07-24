import { createSlice } from "@reduxjs/toolkit";
import { djangoApi } from "../functions/api/djangoApi";

interface UserSlice {
  username: string;
  access: string;
  refresh: string;
  darkMode: boolean;
}

const initialState: UserSlice = {
  username: "",
  access: "",
  refresh: "",
  darkMode: false,
};

const userSlice = createSlice({
  name: "user",
  initialState,
  reducers: {
    logout: (state) => {
      state.username = "";
      state.access = "";
      state.refresh = "";
    },
    setUserDarkMode: (state, action) => {
      state.darkMode = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addMatcher(djangoApi.endpoints.login.matchFulfilled, (state, action) => {
        state.username = action.payload.username;
        state.access = action.payload.access;
        state.refresh = action.payload.refresh;
      })
      .addMatcher(djangoApi.endpoints.login.matchRejected, (state) => {
        state.refresh = "";
      })
      .addMatcher(
        djangoApi.endpoints.refreshLogin.matchFulfilled,
        (state, action) => {
          state.access = action.payload.access;
        },
      )
      .addMatcher(djangoApi.endpoints.refreshLogin.matchRejected, (state) => {
        state.access = "";
        state.refresh = "";
      });
  },
});

export const { logout, setUserDarkMode } = userSlice.actions;
export default userSlice.reducer;
