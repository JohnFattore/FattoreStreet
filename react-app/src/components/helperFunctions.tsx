import { IMessage, IQuote } from "../interfaces"
import { useState, useEffect } from "react"
import { getQuote } from "./AxiosFunctions"

export function setAlertVarient(message: IMessage) {
    if (message.type == "error")
        return "danger"
    else if (message.type == "success")
        return "success"
    else
        return ""
}

export function useQuote(ticker, setMessage) {
    const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0, });
    const d = new Date();
    useEffect(() => {
        let storedQuote = localStorage.getItem(ticker);
        if (storedQuote != null) {
            // quoteTime is a list [ price, percent change, time stamp ]
            let quoteTime = JSON.parse(storedQuote);
            // if ticker in localstorage and timestamp is less than 5 min ago
            if ((d.getTime() - quoteTime[2]) < 100000) {
                setQuote({ price: quoteTime[0], percentChange: quoteTime[1] })
            }
            else {
                getQuote(ticker).then((response) => {
                    setQuote({ price: response.data.c, percentChange: response.data.dp });
                    localStorage.setItem(ticker, JSON.stringify([response.data.c, response.data.dp, d.getTime()]));
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    setMessage({ text: "We are experincing are issue getting some asset data", type: "error" })
                });
            }
        }
        // if no stock info saved, go fetch it
        else {
            getQuote(ticker).then((response) => {
                setQuote({ price: response.data.c, percentChange: response.data.dp });
                localStorage.setItem(ticker, JSON.stringify([response.data.c, response.data.dp, d.getTime()]));
                // store in storage with ticker, stock data, and a time stamp
            }).catch(() => {
                setMessage({ text: "We are experincing are issue getting some asset data", type: "error" })
            });
        }
    }, []);
    return quote
}