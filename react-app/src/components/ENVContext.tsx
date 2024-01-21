import { createContext } from "react";

// make environment variables
export const ENVContext = createContext({    
    djangoURL: import.meta.env.VITE_APP_DJANGO_URL,
    finnhubURL: import.meta.env.VITE_APP_FINNHUB_URL,
    finnhubKey: import.meta.env.VITE_APP_FINNHUB_KEY
});