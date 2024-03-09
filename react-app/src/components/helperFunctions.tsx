import { IMessage, IOption, IQuote, ISelection } from "../interfaces"
import { useState, useEffect } from "react"
import { getQuote, getCompanyProfile2, getFinancialsReported } from "./AxiosFunctions"

export function setAlertVarient(message: IMessage) {
    if (message.type == "error")
        return "danger"
    else if (message.type == "success")
        return "success"
    else
        return ""
}

export function selectedOption(option: IOption, selections: ISelection[]) {
    const selectionsFiltered: ISelection[] = selections.filter((selection: ISelection) => selection.option == option.id)
    if (selectionsFiltered.length != 0)
        return ""//"green"
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

export function useCompanyProfile2(ticker, setMessage) {
    const [marketCap, setMarketCap] = useState(0);
    const d = new Date();
    useEffect(() => {
        let storedMarketCap = localStorage.getItem("marketcap".concat(ticker));
        if (storedMarketCap != null) {
            // marketCapTime is a list [ time stamp, market cap ]
            let marketCapTime = JSON.parse(storedMarketCap);
            // if ticker in localstorage and timestamp is less than 5 min ago
            if ((d.getTime() - marketCapTime[0]) < 500000) {
                setMarketCap(marketCapTime[1])
            }
            else {
                getCompanyProfile2(ticker).then((response) => {
                    setMarketCap(response.data.marketCapitalization);
                    localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), response.data.marketCapitalization]));
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    setMessage({ text: "We are experincing are issue getting market cap data", type: "error" })
                });
            }
        }
        // if no stock info saved, go fetch it
        else {
            getCompanyProfile2(ticker).then((response) => {
                setMarketCap(response.data.marketCapitalization);
                localStorage.setItem("marketcap".concat(ticker), JSON.stringify([d.getTime(), response.data.marketCapitalization]));
            }).catch(() => {
                setMessage({ text: "We are experincing are issue getting market cap data", type: "error" })
            });
        }
    }, []);
    return marketCap
}

export function useFinancialsReported(ticker, setMessage) {
    const [netIncome, setNetIncome] = useState(0);
    const d = new Date();
    useEffect(() => {
        let storedNetIncome = localStorage.getItem("netIncome".concat(ticker));
        if (storedNetIncome != null) {
            // netIncomeTime is a list [ time stamp, market cap ]
            let netIncomeTime = JSON.parse(storedNetIncome);
            // if ticker in localstorage and timestamp is less than a bunch of seconds
            if ((d.getTime() - netIncomeTime[0]) < 5000000000) {
                setNetIncome(netIncomeTime[1])
            }
            else {
                getFinancialsReported(ticker).then((response) => {
                    const responseNetIncome = response.data.data[0].report.cf.find(obj => {
                        return obj.concept == 'us-gaap_NetIncomeLoss'
                    }).value
                    setNetIncome(responseNetIncome);
                    localStorage.setItem("netIncome".concat(ticker), JSON.stringify([d.getTime(), responseNetIncome]));
                    // store in storage with ticker, stock data, and a time stamp
                }).catch(() => {
                    setMessage({ text: "We are experincing are issue getting net income data, maxwell", type: "error" })
                });
            }
        }
        // if no stock info saved, go fetch it
        else {
            getFinancialsReported(ticker).then((response) => {
                /*
                const responseNetIncome = response.data.data[0].report.cf.find(obj => {
                    return obj.concept == 'us-gaap_NetIncomeLoss'
                }).value
                setNetIncome(responseNetIncome);
                console.log(responseNetIncome)
                localStorage.setItem("netIncome".concat(ticker), JSON.stringify([d.getTime(), responseNetIncome]));
                */
               console.log(response.data.data)
            }).catch(() => {
                setMessage({ text: "We are experincing are issue getting net income data, django", type: "error" })
            });
        }
    }, []);
    return netIncome
}