import { createAction } from "@reduxjs/toolkit";

// Defined outside userReducer.tsx so baseQuery.ts and djangoApi.ts can dispatch
// auth updates without creating a circular import with userReducer.tsx.
export const logout = createAction("user/logout");
export const setAccessToken = createAction<string>("user/setAccessToken");
