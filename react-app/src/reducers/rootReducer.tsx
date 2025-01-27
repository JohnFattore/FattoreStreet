import { combineReducers } from '@reduxjs/toolkit';
import userReducer from './userReducer';
import assetReducer from './assetReducer';
import restaurantReducer from './restaurantReducer';
import watchListReducer from './watchListReducer';
import snp500Reducer from './snp500Reducer';
import reviewReducer from './reviewReducer'
import locationReducer from './locationReducer'
import restaurantRecommendReducer from './restaurantRecommendReducer'

const rootReducer = combineReducers({
    restaurants: restaurantReducer,
    reviews: reviewReducer,
    restaurantRecommend: restaurantRecommendReducer,
    assets: assetReducer,
    watchList: watchListReducer,
    user: userReducer,
    snp500Prices: snp500Reducer,
    location: locationReducer,
});

export type RootState = ReturnType<typeof rootReducer>;  // Type the state of the Redux store
export default rootReducer;