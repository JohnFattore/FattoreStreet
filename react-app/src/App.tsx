import { Routes, Route } from "react-router-dom";
import TopNavigation from "./components/TopNavigation";
import Home from "./pages/Home";
import Portfolio from "./pages/Portfolio";
import Watchlist from "./pages/WatchList";
import Entertainment from "./pages/Entertainment";
import ErrorPage from "./components/ErrorPage";
import Restaurants from "./pages/Restaurants";
import Visualizer from "./pages/Visualizer";
import EconomicIndicators from "./pages/EconomicIndicators";
import axios from "axios";
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "./main";
import { djangoApi } from "./functions/api";
import { logout } from "./reducers/userReducer";
import { useEffect } from "react";
import Chatbot from "./pages/Chatbot";
import AssetView from "./pages/AssetView";
import AccountView from "./pages/AccountView";
import Admin from "./pages/Admin";
import SECData from "./pages/SECData";
import IexPricesView from "./pages/IexPricesView";
import Indexes from "./pages/Indexes";
import User from "./pages/User";
import AdminSuccessBar from "./pages/AdminSuccessBar";
import Blog from "./pages/Blog";
import BlogPost from "./pages/BlogPost";

// react router for all our routes
export default function App() {
  const { refresh, darkMode } = useSelector((state: RootState) => state.user);
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    document.body.classList.toggle("dark-mode", darkMode);
  }, [darkMode]);

  useEffect(() => {
    const interceptorId = axios.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config;
        const detail = error.response?.data?.detail ?? "";
        // Matches SimpleJWT messages like "Token is invalid or expired",
        // "Token is expired", "token_not_valid", etc.
        const isTokenError = /token.*(invalid|expired)/i.test(detail);
        if (
          error.response?.status === 401 &&
          !originalRequest._retry &&
          refresh &&
          !isTokenError
        ) {
          originalRequest._retry = true;
          const refreshRequest = dispatch(
            djangoApi.endpoints.refreshLogin.initiate(refresh)
          );
          try {
            const result = await refreshRequest.unwrap();
            originalRequest.headers["Authorization"] = `Bearer ${result.access}`;
            return axios(originalRequest);
          } finally {
            refreshRequest.reset();
          }
        }
        else if (error.response?.status === 401 && isTokenError) {
          dispatch(logout());
        }

        return Promise.reject(error);
      }
    );
    return () => axios.interceptors.response.eject(interceptorId);
  }, [refresh, dispatch]);

  return (
    <div className="app-container">
      <TopNavigation />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/restaurants" element={<Restaurants />} />
        <Route
          path="/portfolio"
          element={<Portfolio />}
          errorElement={<ErrorPage />}
        />
        <Route path="/watchlist" element={<Watchlist />} />
        <Route path="/entertainment" element={<Entertainment />} />
        <Route path="/chatbot" element={<Chatbot />} />
        <Route path="/asset/:ticker" element={<AssetView />} />
        <Route path="/account/:id" element={<AccountView />} />
        <Route path="/visualizer" element={<Visualizer />} />
        <Route path="/economic-indicators" element={<EconomicIndicators />} />
        <Route path="/indexes" element={<Indexes />} />
        <Route path="/sec-edgar/:ticker" element={<SECData />} />
        <Route path="/iex-prices/:ticker" element={<IexPricesView />} />
        <Route path="/react-admin/success-bar" element={<AdminSuccessBar />} />
        <Route path="/blog" element={<Blog />} />
        <Route path="/blog/:slug" element={<BlogPost />} />
        <Route path="/user" element={<User />} />
        <Route path="/react-admin" element={<Admin />} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
      <p>
        This website is purely for entertainment. All investments carry risk.
        Consult a professional.
      </p>
      <p>Index Funds are Awesome!!</p>
    </div>
  );
}
