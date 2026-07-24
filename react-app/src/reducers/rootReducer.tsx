import { combineReducers } from "@reduxjs/toolkit";
import userReducer from "./userReducer";
import watchListReducer from "./watchListReducer";
import locationReducer from "./locationReducer";
import adminSuccessBarReducer from "./adminSuccessBarReducer";
import { djangoApi, springbootApi } from "../functions/api";

const rootReducer = combineReducers({
  watchList: watchListReducer,
  user: userReducer,
  location: locationReducer,
  adminSuccessBar: adminSuccessBarReducer,
  [djangoApi.reducerPath]: djangoApi.reducer,
  [springbootApi.reducerPath]: springbootApi.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;
export default rootReducer;
