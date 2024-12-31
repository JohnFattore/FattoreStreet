import { combineReducers } from '@reduxjs/toolkit';
import userReducer from './userReducer';
import assetReducer from './assetReducer';
import restaurantReducer from './restaurantReducer';
import watchListReducer from './watchListReducer';

const rootReducer = combineReducers({
    restaurants: restaurantReducer,
    assets: assetReducer,
    watchList: watchListReducer,
    user: userReducer,
});

export type RootState = ReturnType<typeof rootReducer>;  // Type the state of the Redux store
export default rootReducer;