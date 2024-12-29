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
import RestaurantReview from "./pages/RestaurantReview";

// react router for all our routes
export default function App() {

  return (
    <>
      <TopNavigation />
      <Routes >
        <Route path="/" element={<Home />} />
        <Route path="/restaurants" element={<Restaurants />} />
        <Route path="/portfolio" element={<Portfolio />} errorElement={<ErrorPage />} />
        <Route path="/watchlist" element={<Watchlist />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/education" element={<Education />} />
        <Route path="/outliers" element={<Outliers />} />
        <Route path="/entertainment" element={<Entertainment />} />
        <Route path="/review" element={<RestaurantReview />} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
      <p>This website is purely for entertainment. All investments carry risk. Consult a professional.</p>
      <p>Passive Investing is Awesome!!</p>
    </>
  );
}