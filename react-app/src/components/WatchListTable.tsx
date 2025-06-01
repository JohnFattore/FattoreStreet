import Table from "react-bootstrap/Table";
import { Alert } from "react-bootstrap";
import { useCachedData } from "./customHooks";
import { formatString } from "./helperFunctions";
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { removeTicker } from "../reducers/watchListReducer";
import { getCompanyProfile2, /*getFinancials,*/ getQuote } from "./axiosFunctions";

function WatchListRow({ ticker, fields }) {
  const dispatch = useDispatch<AppDispatch>();

  const quoteResponse = useCachedData(ticker, getQuote, 10000);
  var quote = {
    c: 0,
    dp: 0
  };
  if (quoteResponse) {
    quote = quoteResponse.data;
  }

  const companyProfileResponse = useCachedData(ticker, getCompanyProfile2, 30000);
  var companyProfile = {
    marketCapitalization: 0,
  };
  if (companyProfileResponse) {
    companyProfile = companyProfileResponse.data;
  }
/*
  const financialsResponse = useCachedData(ticker, getFinancials, 30000);
  var financials = {
    marketCapitalization: 0,
  };
  if (financialsResponse) {
    financials = financialsResponse.data;
    if (financials["data"][0]) {
      let cf = financials["data"][0]["report"]["cf"]
      //console.log(cf)
      console.log(Object.values(cf).find(item => (item as { concept: string }).concept === "us-gaap_NetIncomeLoss"));
    }
  }
*/
  let attributes: any[] = [
    ticker,
    quote.c,
    quote.dp / 100,
    companyProfile.marketCapitalization / 1000,
    "delete",
  ];

  let tableData: JSX.Element[] = [];

  for (let i = 0; i < attributes.length; i++) {
    if (fields[i]["type"] == "delete") {
      tableData.push(
        <td onClick={() => dispatch(removeTicker(ticker))}>
          {formatString(attributes[i], fields[i]["type"])}
        </td>
      );
    } else {
      tableData.push(<td>{formatString(attributes[i], fields[i]["type"])}</td>);
    }
  }

  return <tr>{tableData}</tr>;
}

export default function WatchListTable() {
  const { tickers, loading } = useSelector(
    (state: RootState) => state.watchList
  );

  const fields = [
    { name: "Ticker", type: "text" },
    { name: "Price", type: "money" },
    { name: "Percent Change", type: "percent" },
    { name: "Market Cap", type: "marketCap" },
    //{ name: "PE Ratio", type: "text" },
    { name: "Delete", type: "delete" },
  ];

  let headers: JSX.Element[] = [];
  for (let i = 0; i < fields.length; i++) {
    headers.push(<th key={i}>{fields[i].name}</th>);
  }

  if (loading) return <Alert>Loading WatchList</Alert>;

  if (tickers.length == 0) {
    return <h3 role="noModels">No Data</h3>;
  }

  return (
    <Table>
      <thead>
        <tr>{headers}</tr>
      </thead>
      <tbody>
        {tickers.map((ticker) => (
          <WatchListRow key={ticker} ticker={ticker} fields={fields} />
        ))}
      </tbody>
    </Table>
  );
}
