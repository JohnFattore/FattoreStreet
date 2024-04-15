import { useQuote, useCompanyProfile2 } from "../components/customHooks";

export default function WatchListRow({ ticker, setMessage, setTickers }) {
    const quote = useQuote(ticker, setMessage)
    const marketCap = useCompanyProfile2(ticker, setMessage)
    //const netIncome = useFinancialsReported(ticker, setMessage)

    var color = "red";
    if (quote.percentChange > 0) {
        color = "green";
    }

    //console.log(marketCap)

    return (
        <tr>
            <td role="ticker">{ticker}</td>
            <td>${quote.price.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td role="percentChange" style={{ color: color }}>{quote.percentChange.toLocaleString(undefined, {
                minimumFractionDigits: 3,
                maximumFractionDigits: 3
            })}%</td>
            <td role="marketCap">${marketCap.toFixed(2)} billion</td>
            <td role="marketCap">${"PE Ratio"} million</td>
            <td role="delete" onClick={() => {
                let tickersDB = localStorage.getItem("tickers");
                let tickers: string[] = JSON.parse(tickersDB as string);
                tickers = tickers.filter(e => e !== ticker); // will remove ticker from list
                // setting tickers so display refreshes
                setTickers(tickers)
                tickersDB = JSON.stringify(tickers);
                localStorage.setItem("tickers", tickersDB);
                setMessage({ text: ticker + " deleted from watchlist", type: "success" });
            }}>delete</td>
        </tr>)
}