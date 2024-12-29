import ReactDOM from 'react-dom/client';
import App from "./App";
import {
    BrowserRouter,
} from "react-router-dom";
import { Provider } from "react-redux";
import { configureStore } from "@reduxjs/toolkit";
import restaurantsReducer from "./reducers/restaurantReducer";
import assetReducer from "./reducers/assetReducer";
import watchListReducer from "./reducers/watchListReducer";

const store = configureStore({
    reducer: {
      // Add your reducers here
      restaurants: restaurantsReducer,
      assets: assetReducer,
      watchList: watchListReducer,
    },
  });

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;


// router is on the outside of app so that useNavigate work in the App componment
const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement)
root.render(
    <Provider store={store}>
        <BrowserRouter>
            <App/>
        </BrowserRouter>
    </Provider>
);