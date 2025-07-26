import { ListGroup } from "react-bootstrap";
import { formatString } from "./helperFunctions";
import { useWatchListData } from "./customHooks";


export default function StockFinancials({ticker}) {
    let financials = useWatchListData(ticker)
  return (
    <>
    <ListGroup>
        <ListGroup.Item>{"Market Capitalization: " + formatString(financials.marketCap, "marketCap")}</ListGroup.Item>
        <ListGroup.Item>{"Income TTM: " + formatString(financials.incomeTTM, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Revenue TTM: " + formatString(financials.revenueTTM, "money")}</ListGroup.Item>
        <ListGroup.Item>{"Net Margin TTM: " + formatString(financials.netMarginTTM, "percent")}</ListGroup.Item>
    </ListGroup>
    </>
  );
}
