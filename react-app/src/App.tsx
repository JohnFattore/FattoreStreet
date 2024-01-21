import {
  Routes,
  Route,
  BrowserRouter,
} from "react-router-dom";
import TopNavigation from "./components/TopNavigation";
import Home from "./pages/Home";
import Portfolio from "./pages/Portfolio"
import Allocation from "./pages/Allocation";
import Transaction from "./pages/Transaction";
import Watchlist from "./pages/WatchList";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Philosophy from "./pages/Philosophy";
import Entertainment from "./pages/Entertainment";
import Footer from "./components/Footer";
import axios from "axios";
import { useContext } from "react";
import { ENVContext } from "./components/ENVContext"
//import { redirect } from "react-router-dom";

// react router for all our routes
export default function App() {

  const ENV = useContext(ENVContext);

  // axios interceptor that handles refreshing JWT
  // first function handles successes, second handles errors
  axios.interceptors.response.use(function (response) {
    return response;
  }, function (error) {
    //console.log("HTTP Error Code: ", error.response.data.code)
    // handle refreshing expired access codes automatically for user
    if (error.response.data.code === "token_not_valid") {
      axios.post(ENV.djangoURL.concat("token/refresh/"), {
        refresh: sessionStorage.getItem("refresh"),
      }).then((response) => {
        sessionStorage.setItem("token", response.data.access);
      });
      //return axios_instance(config);
      //return redirect("/login");
    }
    /*
    if (error.response.status === 400) {
      //return redirect("/login");
      alert("Please Login")
    }
    */

    return Promise.reject(error);
  });

  return (
    <ENVContext.Provider value={ENV}>
      <BrowserRouter>
        <TopNavigation />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/portfolio" element={<Portfolio />} />
          <Route path="/transaction" element={<Transaction />} />
          <Route path="/watchlist" element={<Watchlist />} />
          <Route path="/login" element={<Login />} />
          <Route path="/allocation" element={<Allocation />} />
          <Route path="/register" element={<Register />} />
          <Route path="/philosophy" element={<Philosophy />} />
          <Route path="/entertainment" element={<Entertainment />} />
        </Routes>
        <Footer />
      </BrowserRouter>
    </ENVContext.Provider>
  );
}