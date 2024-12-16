import {
  Routes,
  Route
} from "react-router-dom";
import TopNavigation from "./components/TopNavigation";
import Home from "./pages/Home";
import Portfolio from "./pages/Portfolio"
import Allocation from "./pages/Allocation";
import Watchlist from "./pages/WatchList";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Education from "./pages/Education";
import Entertainment from "./pages/Entertainment";
import ErrorPage from "./components/ErrorPage";
import Outliers from "./pages/Outliers";
import Restaurants from "./pages/Restaurants";
import RestaurantReview from "./pages/RestaurantReview";
//import axios from "axios";

// react router for all our routes
export default function App() {
  /*
  const navigate = useNavigate();
  // axios interceptor that handles refreshing JWT
  // first function handles successes, second handles errors
  axios.interceptors.response.use(function (response) {
    return response;
  }, function (error) {
    //console.log("HTTP Error Code: ", error.response.data.code)
    // handle refreshing expired access codes automatically for user
    if (error.response.data.code === "token_not_valid") {
      axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("token/refresh/"), {
        refresh: sessionStorage.getItem("refresh"),
      }).then((response) => {
        sessionStorage.setItem("token", response.data.access);
      }).catch(() => {
        alert("Please Login");
        navigate("/login");
      });
      //return axios.request(error.config); // retries the original request
    }

    return Promise.reject(error);
  });
  */



  /*
  // Add a request interceptor
  axios.interceptors.request.use(function (config) {
    // Do something before request is sent
    return config;
  }, function (error) {
    // Do something with request error
    return Promise.reject(error);
  });

  // Add a response interceptor
  axios.interceptors.response.use(function (response) {
    // Any status code that lie within the range of 2xx cause this function to trigger
    // Do something with response data
    return response;
  }, function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error

    // handle refreshing expired access codes automatically for user
    if (error.response.data.code === "token_not_valid") {
      // refresh token
      axios.post(import.meta.env.VITE_APP_DJANGO_PORTFOLIO_URL.concat("token/refresh/"), {
        refresh: sessionStorage.getItem("refresh"),
      }).then((response) => {
        sessionStorage.setItem("token", response.data.access);
        // fix and retry original call and resolve promise with this response
        error.config.headers.Authorization = ' Bearer '.concat(response.data.access)
        console.log(error.config.headers)
        return error.config;
      }).catch(() => {
        alert("Please Login");
        //navigate("/login");
      });
      //return axios.request(error.config); // retries the original request
    }
    return Promise.reject(error);
  });
*/
  // errorElement doesnt always work, needs refactoring
  return (
    <>
      <TopNavigation />
      <Routes >
        <Route path="/" element={<Home />} />
        <Route path="/restaurants" element={<Restaurants />} />
        <Route path="/portfolio" element={<Portfolio />} errorElement={<ErrorPage />} />
        <Route path="/watchlist" element={<Watchlist />} />
        <Route path="/login" element={<Login />} />
        <Route path="/allocation" element={<Allocation />} />
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