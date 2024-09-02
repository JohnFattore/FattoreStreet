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
  // errorElement doesnt always work, needs refactoring
  return (
    <>
      <TopNavigation />
      <Routes >
        <Route path="/" element={<Home />} />
        <Route path="/portfolio" element={<Portfolio />} errorElement={<ErrorPage />} />
        <Route path="/watchlist" element={<Watchlist />} />
        <Route path="/login" element={<Login />} />
        <Route path="/allocation" element={<Allocation />} />
        <Route path="/register" element={<Register />} />
        <Route path="/education" element={<Education />} />
        {/*        
        <Route path="/wallstreet" element={<WallStreet />}  errorElement={<ErrorPage />}/>
        <Route path="/results" element={<Results />}  errorElement={<ErrorPage />}/>
      */
        }
        <Route path="/outliers" element={<Outliers />} />
        <Route path="/entertainment" element={<Entertainment />} />
        <Route path="*" element={<ErrorPage />} />
      </Routes>
      <p>This website is purely for entertainment. All investments carry risk. Consult a professional.</p>
      <p>Passive Investing is Awesome!!</p>
    </>
  );
}