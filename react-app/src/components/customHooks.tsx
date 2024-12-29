import { IQuote } from "../interfaces"
import { useState, useEffect } from "react"
import { getQuote, getCompanyProfile2 } from "./axiosFunctions"

// could generalize these functions, a custom hook factory. They all just save to broswer database
export function useQuote(ticker) {
    const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0, });
    const d = new Date();
    useEffect(() => {
        let storedQuote = localStorage.getItem(ticker);
        if (storedQuote != null) {
            // quoteTime is a list [ price, percent change, time stamp ]
            let quoteTime = JSON.parse(storedQuote);
            // if ticker in localstorage and timestamp is less than 100 sec ago
            if ((d.getTime() - quoteTime[2]) < 100000) {
                setQuote({ price: quoteTime[0], percentChange: quoteTime[1] })
            }
            else {
                getQuote(ticker).then((response) => {
                    setQuote({ price: response.data.c, percentChange: response.data.dp });
                    localStorage.setItem(ticker, JSON.stringify([response.data.c, response.data.dp, d.getTime()]));
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    console.log("Spike Handle this Error")
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
                console.log("Spike Handle this Error")
            });
        }
    }, []);
    return quote
}

export function useCompanyProfile2(ticker) {
    const [marketCap, setMarketCap] = useState("");
    const d = new Date();
    useEffect(() => {
        let storedMarketCap = localStorage.getItem("marketcap".concat(ticker));
        if (storedMarketCap != null) {
            // marketCapTime is a list [ time stamp, market cap ]
            let marketCapTime = JSON.parse(storedMarketCap);
            // if ticker in localstorage and timestamp is less than 5 min ago
            if ((d.getTime() - marketCapTime[0]) < 100000) {
                setMarketCap(marketCapTime[1])
            }
            else {
                getCompanyProfile2(ticker).then((response) => {
                    if (response.data.marketCapitalization != null) {
                        setMarketCap(String(response.data.marketCapitalization / 1000));
                        localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), response.data.marketCapitalization / 1000]));
                    }
                    else {
                        setMarketCap("ETF");
                        localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), "ETF"]));
                    }
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    console.log("Spike Handle this Error")
                });
            }
        }
        // if no stock info saved, go fetch it
        else {
            getCompanyProfile2(ticker).then((response) => {
                if (response.data.marketCapitalization != null) {
                    setMarketCap(String(response.data.marketCapitalization / 1000));
                    localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), response.data.marketCapitalization / 1000]));
                }
                else {
                    setMarketCap("ETF");
                    localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), "ETF"]));
                }
            }).catch(() => {
                console.log("Spike Handle this Error")
            });
        }
    }, []);
    return marketCap
}