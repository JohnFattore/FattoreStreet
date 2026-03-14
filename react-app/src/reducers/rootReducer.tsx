import { combineReducers } from '@reduxjs/toolkit';
import userReducer from './userReducer';
import restaurantReducer from './restaurantReducer';
import watchListReducer from './watchListReducer';
import reviewReducer from './reviewReducer'
import locationReducer from './locationReducer'
import restaurantRecommendReducer from './restaurantRecommendReducer'
import chatbotReducer from './chatbotReducer'
import adminSuccessBarReducer from './adminSuccessBarReducer'
import { api } from '../functions/api' // adjust path as needed

const rootReducer = combineReducers({
    restaurants: restaurantReducer,
    reviews: reviewReducer,
    restaurantRecommend: restaurantRecommendReducer,
    watchList: watchListReducer,
    user: userReducer,
    location: locationReducer,
    chatbot: chatbotReducer,
    adminSuccessBar: adminSuccessBarReducer,
    [api.reducerPath]: api.reducer, // <- add this line
});

export type RootState = ReturnType<typeof rootReducer>;  // Type the state of the Redux store
export default rootReducer;