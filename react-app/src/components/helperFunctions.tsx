import { IMessage, IOption, ISelection } from "../interfaces"

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

export function setAlertVarient(message: IMessage) {
    if (message.type == "error")
        return "danger"
    else if (message.type == "success")
        return "success"
    else if (message.type == "loading")
        return "primary"
    else
        return ""
}

export function selectedOption(option: IOption, selections: ISelection[]) {
    const selectionsFiltered: ISelection[] = selections.filter((selection: ISelection) => selection.option == option.id)
    if (selectionsFiltered.length != 0)
        return "green"
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


// I imagine one handleResponse functions that can be used for all axios functions

export function handleError(error, setMessage) {
    if (error.response.statusText == 'Unauthorized') {
        if (error.response.data.detail == 'Given token not valid for any token type') {
            setMessage({ text: "Please Login", type: "error" })
        }
        else if (error.response.data.detail == 'No active account found with the given credentials') {
            setMessage({ text: "Wrong Email/Password", type: "error" })
        }
        else {
            setMessage({ text: "Please Login", type: "error" })
        }
    }
    else if (error.response.statusText == 'Bad Request') {
        setMessage({ text: String(error.response.data.buy[0]), type: "error" })
    }

    else if (error.response.data.code == 'token_not_valid') {
        // could redirect to a login page
        setMessage({ text: "Please Login", type: "error" })
    }

    else {
        setMessage({ text: "Error", type: "error" })
    }
    // could have single setMessage at end
}

export function translateError(error: string) {
    if (error == 'Request failed with status code 401') {
        return 'Please Login'
    }
    else {
        return error
    }
}