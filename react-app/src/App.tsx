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
import { useSelector, useDispatch } from "react-redux";
import { AppDispatch, RootState } from "./main";
import { djangoApi } from "./functions/api";
import { getSessionExpiryMs, hasActiveSession } from "./functions/auth";
import { logout } from "./reducers/userReducer";
import { useEffect } from "react";
import Chatbot from "./pages/Chatbot";
import AssetView from "./pages/AssetView";
import AccountView from "./pages/AccountView";
import SECData from "./pages/SECData";
import IexPricesView from "./pages/IexPricesView";
import Indexes from "./pages/Indexes";
import User from "./pages/User";
import AdminSuccessBar from "./pages/AdminSuccessBar";
import { AdminDashboard } from "./pages/AdminDashboard";
import { AdminRoute } from "./components/AdminRoute";
import Blog from "./pages/Blog";
import BlogPost from "./pages/BlogPost";
import LlmNotebook from "./pages/LlmNotebook";
import { CompanyComparison } from "./pages/CompanyComparison";

const MAX_TIMEOUT_MS = 2_147_483_647;

// react router for all our routes
export default function App() {
  const { access, refresh, darkMode } = useSelector(
    (state: RootState) => state.user,
  );
  const dispatch = useDispatch<AppDispatch>();

  useEffect(() => {
    document.body.classList.toggle("dark-mode", darkMode);
  }, [darkMode]);

  useEffect(() => {
    if (!access && !refresh) return;

    const clearExpiredSession = () => {
      if (!hasActiveSession({ access, refresh })) {
        dispatch(logout());
        dispatch(djangoApi.util.resetApiState());
        return true;
      }
      return false;
    };

    if (clearExpiredSession()) return;

    const expiryMs = getSessionExpiryMs({ access, refresh });
    let timerId: ReturnType<typeof setTimeout> | undefined;

    const scheduleExpiryCheck = () => {
      if (expiryMs === null) return;
      const remainingMs = expiryMs - Date.now();
      if (remainingMs <= 0) {
        clearExpiredSession();
        return;
      }
      timerId = setTimeout(
        scheduleExpiryCheck,
        Math.min(remainingMs, MAX_TIMEOUT_MS),
      );
    };

    scheduleExpiryCheck();

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        clearExpiredSession();
      }
    };

    window.addEventListener("focus", clearExpiredSession);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      if (timerId !== undefined) clearTimeout(timerId);
      window.removeEventListener("focus", clearExpiredSession);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [access, refresh, dispatch]);

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
        <Route path="/compare" element={<CompanyComparison />} />
        <Route path="/iex-prices/:ticker" element={<IexPricesView />} />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <AdminDashboard />
            </AdminRoute>
          }
        />
        <Route
          path="/admin/success-bar"
          element={
            <AdminRoute>
              <AdminSuccessBar />
            </AdminRoute>
          }
        />
        <Route path="/blog" element={<Blog />} />
        {/* Static segment, so it out-ranks /blog/:slug in the router */}
        <Route path="/blog/llm-notebook" element={<LlmNotebook />} />
        <Route path="/blog/:slug" element={<BlogPost />} />
        <Route path="/user" element={<User />} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
      <p className="small text-muted">
        Fattore Street is not a registered investment adviser, broker-dealer, or
        tax professional. Content on this site is general educational
        information, not personalized advice or a recommendation to buy or sell
        any security. All investing involves risk, including loss of principal.
      </p>
      <p>Index Funds are Awesome!!</p>
    </div>
  );
}
