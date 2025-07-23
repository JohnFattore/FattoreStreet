import { IQuote, IWatchListItem } from "../interfaces";
import { useState, useEffect } from "react";
import { getQuote, getCompanyProfile2, getFinancials } from "./axiosFunctions";

export function useWatchListData (ticker: string) {
    const quoteResponse = useCachedData(ticker, getQuote, 10000);
    var quote = {
      c: 0,
      dp: 0,
    };
    if (quoteResponse) {
      quote = quoteResponse.data;
    }
  
    const companyProfileResponse = useCachedData(
      ticker,
      getCompanyProfile2,
      30000
    );
    var companyProfile = {
      marketCapitalization: 0,
    };
    if (companyProfileResponse) {
      companyProfile = companyProfileResponse.data;
    }
  
    let incomeTTM = 0;
    let revenueTTM = 0;
    let name = ""
    const financialsResponse = useCachedData(ticker, getFinancials, 30000);
    if (financialsResponse) {
      incomeTTM = financialsResponse.data["net_income"];
      revenueTTM = financialsResponse.data["total_revenue"];
      name = financialsResponse.data["name"]
    }
  
    const watchListItem: IWatchListItem = {
      ticker: ticker,
      name: name,
      price: quote.c,
      percentChange: quote.dp / 100,
      marketCap: companyProfile.marketCapitalization / 1000,
      trailingPERatio: (1000000 * companyProfile.marketCapitalization) / incomeTTM,
      incomeTTM: incomeTTM,
      revenueTTM: revenueTTM,
      netMarginTTM: incomeTTM / revenueTTM
    };

    return watchListItem;
}

export function useCachedData<T>(
  key: string,
  fetcher: (key: string) => Promise<T>,
  ttlMs: number
): T | null {
  const [data, setData] = useState<T | null>(null);
  const type = fetcher.name
  useEffect(() => {
    const now = Date.now();
    const cached = localStorage.getItem(type + key);

    if (cached) {
      try {
        const [timestamp, value]: [number, T] = JSON.parse(cached);
        if (now - timestamp < ttlMs) {
          setData(value);
          return;
        }
      } catch (e) {
        console.warn("Invalid cache format for", type + key);
        localStorage.removeItem(type + key);
      }
    }

    fetcher(key)
      .then((result) => {
        setData(result);
        localStorage.setItem(type + key, JSON.stringify([now, result]));
      })
      .catch((err) => {
        console.error("Fetch error for", type + key, err);
      });
  }, [key, fetcher, ttlMs]);

  return data;
}


// could generalize these functions, a custom hook factory. They all just save to broswer database
export function useQuote(ticker) {
  const [quote, setQuote] = useState<IQuote>({ price: 0, percentChange: 0 });
  const d = new Date();
  useEffect(() => {
    let storedQuote = localStorage.getItem(ticker);
    if (storedQuote != null) {
      // quoteTime is a list [ price, percent change, time stamp ]
      let quoteTime = JSON.parse(storedQuote);
      // if ticker in localstorage and timestamp is less than 100 sec ago
      if (d.getTime() - quoteTime[2] < 100000) {
        setQuote({ price: quoteTime[0], percentChange: quoteTime[1] });
      } else {
        getQuote(ticker)
          .then((response) => {
            setQuote({
              price: response.data.c,
              percentChange: response.data.dp,
            });
            localStorage.setItem(
              ticker,
              JSON.stringify([response.data.c, response.data.dp, d.getTime()])
            );
            // store in storage with ticker, stock data, and a time stamp
          })
          .catch(() => {
            console.log("Spike Handle this Error");
          });
      }
    }
    // if no stock info saved, go fetch it
    else {
      getQuote(ticker)
        .then((response) => {
          setQuote({ price: response.data.c, percentChange: response.data.dp });
          localStorage.setItem(
            ticker,
            JSON.stringify([response.data.c, response.data.dp, d.getTime()])
          );
          // store in storage with ticker, stock data, and a time stamp
        })
        .catch(() => {
          console.log("Spike Handle this Error");
        });
    }
  }, []);
  return quote;
}
/*
export function useCompanyProfile2(ticker: string) {
  const [marketCap, setMarketCap] = useState("");
  const d = new Date();
  useEffect(() => {
    let storedMarketCap = localStorage.getItem("marketcap".concat(ticker));
    if (storedMarketCap != null) {
      // marketCapTime is a list [ time stamp, market cap ]
      let marketCapTime = JSON.parse(storedMarketCap);
      // if ticker in localstorage and timestamp is less than 5 min ago
      if (d.getTime() - marketCapTime[0] < 100000) {
        setMarketCap(marketCapTime[1]);
      } else {
        getCompanyProfile2(ticker)
          .then((response) => {
            if (response.data.marketCapitalization != null) {
              setMarketCap(String(response.data.marketCapitalization / 1000));
              localStorage.setItem(
                "marketcap".concat(ticker),
                JSON.stringify([
                  d.getTime(),
                  response.data.marketCapitalization / 1000,
                ])
              );
            } else {
              setMarketCap("ETF");
              localStorage.setItem(
                "marketcap".concat(ticker),
                JSON.stringify([d.getTime(), "ETF"])
              );
            }
            // store in storage with ticker, stock data, and a time stamp
          })
          .catch(() => {
            console.log("Spike Handle this Error");
          });
      }
    }
    // if no stock info saved, go fetch it
    else {
      getCompanyProfile2(ticker)
        .then((response) => {
          if (response.data.marketCapitalization != null) {
            setMarketCap(String(response.data.marketCapitalization / 1000));
            localStorage.setItem(
              "marketcap".concat(ticker),
              JSON.stringify([
                d.getTime(),
                response.data.marketCapitalization / 1000,
              ])
            );
          } else {
            setMarketCap("ETF");
            localStorage.setItem(
              "marketcap".concat(ticker),
              JSON.stringify([d.getTime(), "ETF"])
            );
          }
        })
        .catch(() => {
          console.log("Spike Handle this Error");
        });
    }
  }, []);
  return marketCap;
}

export function useCompanyFinancials(ticker) {
  const [financials, setFinancials] = useState("");
  const d = new Date();
  useEffect(() => {
    let storedFinancials = localStorage.getItem("financials".concat(ticker));
    if (storedFinancials != null) {
      // financialsCapTime is a list [ time stamp, market cap ]
      let financialsCapTime = JSON.parse(storedFinancials);
      // if ticker in localstorage and timestamp is less than 5 min ago
      if (d.getTime() - financialsCapTime[0] < 100000) {
        setFinancials(financialsCapTime[1]);
      } else {
        getCompanyProfile2(ticker)
          .then((response) => {
            if (response.data.financials != null) {
              setFinancials(String(response.data.financials / 1000));
              localStorage.setItem(
                "financials".concat(ticker),
                JSON.stringify([d.getTime(), response.data.financials / 1000])
              );
            } else {
              setFinancials("ETF");
              localStorage.setItem(
                "financials".concat(ticker),
                JSON.stringify([d.getTime(), "ETF"])
              );
            }
            // store in storage with ticker, stock data, and a time stamp
          })
          .catch(() => {
            console.log("Spike Handle this Error");
          });
      }
    }
    // if no stock info saved, go fetch it
    else {
      getCompanyProfile2(ticker)
        .then((response) => {
          if (response.data.financials != null) {
            setFinancials(String(response.data.financials / 1000));
            localStorage.setItem(
              "financials".concat(ticker),
              JSON.stringify([d.getTime(), response.data.financials / 1000])
            );
          } else {
            setFinancials("ETF");
            localStorage.setItem(
              "financials".concat(ticker),
              JSON.stringify([d.getTime(), "ETF"])
            );
          }
        })
        .catch(() => {
          console.log("Spike Handle this Error");
        });
    }
  }, []);
  return financials;
}
*/