import { combineReducers } from '@reduxjs/toolkit';
import userReducer from './userReducer';
import restaurantReducer from './restaurantReducer';
import watchListReducer from './watchListReducer';
import reviewReducer from './reviewReducer'
import locationReducer from './locationReducer'
import restaurantRecommendReducer from './restaurantRecommendReducer'
import chatbotReducer from './chatbotReducer'
import adminSuccessBarReducer from './adminSuccessBarReducer'
import { djangoApi, springbootApi } from "../functions/api";

const rootReducer = combineReducers({
    restaurants: restaurantReducer,
    reviews: reviewReducer,
    restaurantRecommend: restaurantRecommendReducer,
    watchList: watchListReducer,
    user: userReducer,
    location: locationReducer,
    chatbot: chatbotReducer,
    adminSuccessBar: adminSuccessBarReducer,
    [djangoApi.reducerPath]: djangoApi.reducer,
    [springbootApi.reducerPath]: springbootApi.reducer,
});

export type RootState = ReturnType<typeof rootReducer>;
export default rootReducer;