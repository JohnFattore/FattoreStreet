import { Button } from "react-bootstrap";

function WatchListClear({setChange}) {

    return (
        <>
            <h1>Clear WatchList</h1>
            <Button onClick={() => {
                localStorage.removeItem("tickers");
                setChange(true)
                alert("Watchlist Cleared");
              }}>Clear</Button>
        </>
    )
}

export default WatchListClear;