import ReactDOM from 'react-dom/client';
import App from "./App";
import {
    BrowserRouter,
} from "react-router-dom";
// router is on the outside of app so that useNavigate work in the App componment
const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement)
root.render(
    <BrowserRouter>
        <App />
    </BrowserRouter>

);