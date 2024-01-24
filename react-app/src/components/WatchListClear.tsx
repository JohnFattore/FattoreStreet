import { Button } from "react-bootstrap";

function WatchListClear({setChange}) {

    return (
        <>
            <h3>Clear WatchList</h3>
            <Button onClick={() => {
                localStorage.removeItem("tickers");
                setChange(true)
                alert("Watchlist Cleared");
              }}>Clear</Button>
        </>
    )
}

export default WatchListClear;