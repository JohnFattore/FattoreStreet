import {
  Routes,
  Route
} from "react-router-dom";
import TopNavigation from "./components/TopNavigation";
import Home from "./pages/Home";
import Portfolio from "./pages/Portfolio"
import Watchlist from "./pages/WatchList";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Education from "./pages/Education";
import Entertainment from "./pages/Entertainment";
import ErrorPage from "./components/ErrorPage";
import Outliers from "./pages/Outliers";
import Restaurants from "./pages/Restaurants";
import SnP500Prices from "./pages/SnP500Prices"
import axios from "axios";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from './main';
import { refreshLogin } from "./components/axiosFunctions";
import { logout, clearErrors } from "./reducers/userReducer";
import { useEffect } from "react";

// react router for all our routes
export default function App() {

  const { refresh } = useSelector((state: RootState) => state.user)
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    // Clear errors when the app initializes
    dispatch(clearErrors());
  }, [dispatch]);

  // Response Interceptor
  axios.interceptors.response.use(
    (response) => {
      return response; // Return the response if successful
    },
    async (error) => {
      const originalRequest = error.config; // Capture the original request
      if (error.response?.status === 401 && refresh && error.response.data.detail != 'Token is invalid or expired') {
        originalRequest._retry = true; // Prevent infinite retries
        const result = await dispatch(refreshLogin()).unwrap();
        originalRequest.headers['Authorization'] = `Bearer ${result.access}`;
        return axios(originalRequest);
      }

      else if (error.response?.status === 401 && error.response.data.detail === 'Token is invalid or expired') {
        dispatch(logout());
        console.log('Token is not valid');
      }

      return Promise.reject(error); // Handle response errors
    }
  );


  return (
    <div className="app-container">
      <TopNavigation />
      <Routes >
        <Route path="/" element={<Home />} />
        <Route path="/restaurants" element={<Restaurants />} />
        <Route path="/portfolio" element={<Portfolio />} errorElement={<ErrorPage />} />
        <Route path="/snp500Prices" element={<SnP500Prices />} />
        <Route path="/watchlist" element={<Watchlist />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/education" element={<Education />} />
        <Route path="/outliers" element={<Outliers />} />
        <Route path="/entertainment" element={<Entertainment />} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
      <p>This website is purely for entertainment. All investments carry risk. Consult a professional.</p>
      <p>Index Funds are Awesome!!</p>
    </div>

  );
}