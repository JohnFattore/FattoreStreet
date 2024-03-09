import { useQuote, useCompanyProfile2, useFinancialsReported } from "./helperFunctions";

export default function WatchListRow({ ticker, index, setMessage, setTickers }) {
    const quote = useQuote(ticker, setMessage)
    const marketCap = useCompanyProfile2(ticker, setMessage)
    const netIncome = useFinancialsReported(ticker, setMessage)

    var color = "red";
    if (quote.percentChange > 0) {
        color = "green";
    }

    return (
        <tr key={index}>
            <td role="ticker">{ticker}</td>
            <td>${quote.price.toLocaleString(undefined, {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            })}</td>
            <td role="percentChange" style={{ color: color }}>{quote.percentChange.toLocaleString(undefined, {
                minimumFractionDigits: 3,
                maximumFractionDigits: 3
            })}%</td>
            <td role="marketCap">${marketCap} million</td>
            <td role="marketCap">${netIncome} million</td>
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