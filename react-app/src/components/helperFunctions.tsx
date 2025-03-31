import { IQuote } from "../interfaces";
import { getQuote } from "./axiosFunctions";

export function formatString(value: string | number, type: string): string {
    switch (type) {
        case "text":
            return String(value);

        case "money":
            // Format as USD currency
            return new Intl.NumberFormat("en-US", {
                style: "currency",
                currency: "USD",
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
            }).format(Number(value));

        case "amount":
            // Format like money, but without the currency symbol
            return new Intl.NumberFormat("en-US", {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
            }).format(Number(value));

        case "percent":
            // Format as a percentage
            return new Intl.NumberFormat("en-US", {
                style: "percent",
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
            }).format(Number(value)); // Divide by 100 for percent formatting
        case "marketCap":
            if (value == "ETF") {
                return "ETF"
            }
            else {
                return new Intl.NumberFormat("en-US", {
                    style: "currency",
                    currency: "USD",
                    minimumFractionDigits: 2,
                }).format(Number(value)) + " Billion";
        }
        default:
            return String(value); // Fallback: return the value as-is
    }
}

// this weeks sunday, sunday last past, is 0
export function getSunday(week: number) {
    function addDays(date, days) {
        var result = new Date(date);
        result.setDate(result.getDate() + days);
        return result;
    }

    function formatDate(date) {
        var dd = String(date.getDate()).padStart(2, '0');
        var mm = String(date.getMonth() + 1).padStart(2, '0'); //January is 0!
        var yyyy = date.getFullYear();
        return yyyy + "-" + mm + "-" + dd;
    }

    var date = new Date();
    var UTC = new Date(date.getTime() + (date.getTimezoneOffset() * 60 * 1000))
    var offsetHours = Number(import.meta.env.VITE_APP_CUTOVER_ISOWEEKDAY * 24) + Number(import.meta.env.VITE_APP_CUTOVER_HOUR)
    var cutoff = new Date(UTC.getTime() - (offsetHours * 60 * 60 * 1000))
    var thisSunday = addDays(cutoff, -cutoff.getDay() % 7)
    var sunday = addDays(thisSunday, (7 * week))
    return formatDate(sunday)
}


// phase this out, good error message come from the server, set with axiosFunctions and the reducers
export function translateError(error: string) {
    if (error == 'Request failed with status code 401')
        return 'Please Login'
    if (error == 'Request failed with status code 500')
        return 'Server Error'
    if (error == 'Token is invalid or expired')
        return 'Login Expired'
    else {
        return error
    }
}


export async function fetchQuote(ticker: string): Promise<IQuote> {
    const storedQuote = localStorage.getItem(ticker);
    const currentTime = Date.now();
    if (storedQuote) {
        const [price, percentChange, timestamp] = JSON.parse(storedQuote);
        const isRecent = (currentTime - timestamp) < 100000; // 100 seconds

        if (isRecent) {
            return { price, percentChange };
        }
    }

    // Fetch new quote if not in localStorage or outdated
    try {
        const response = await getQuote(ticker);
        const { c: price, dp: percentChange } = response.data;
        localStorage.setItem(ticker, JSON.stringify([price, percentChange, currentTime]));
        return { price, percentChange };
    } catch (error) {
        console.error(`Error fetching quote for ${ticker}:`, error);
        throw new Error('Failed to fetch quote');
    }
}