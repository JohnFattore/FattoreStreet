
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
import { createContext, useContext } from "react";
//import { redirect } from "react-router-dom";

  // Django base URL, served to components as React context
export const strDjangoURLContext = createContext("http://localhost:1337/portfolio/api/");


// react router for all our routes
export default function App() {

  const strDjangoURL = useContext(strDjangoURLContext);

  // axios interceptor that handles refreshing JWT
  axios.interceptors.response.use(function (response) {
    return response;
  }, function (error) {
    console.log(error.response.status)
    // if error 401 unauthorized, try refreshing token
    // set retry flag so that if error occurs again, an infinity loop isnt created
    if (error.response.status === 401 && !error.config._retry) {
      error.config._retry = true;
      // refresh token post
      axios.post(strDjangoURL.concat("token/refresh/"), {
        refresh: sessionStorage.getItem("refresh"),
      }).then((response) => {
        sessionStorage.setItem("token", response.data.access);
      });
      //return axios_instance(config);
      //return redirect("/login");
    }
    if (error.response.status === 400) {
      //return redirect("/login");
      alert("Please Login")
    }

    return Promise.reject(error);
  });

  return (
    <strDjangoURLContext.Provider value={"http://localhost:8000/portfolio/api/"}>
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
    </strDjangoURLContext.Provider>

  );
}