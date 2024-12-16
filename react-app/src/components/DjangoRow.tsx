
function numberWithCommas(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

// function is for simple calculations, function2 is for more complex operations
export default function DjangoRow({ row, setMessage, dispatch, fields, dispatchRow }) {
/*
    // handle fields
    let properties = {}
    for (const item in fields) {
        const field = fields[item]

        if (field.function != null) {
            // create function parameter array
            const functionInput: any[] = []
            for (const j in field.parameters) {
                const parameter = field.parameters[j]

                // Resolve parameter: from model, properties, or raw value
                if (parameter in model)
                    functionInput.push(model[parameter])
                else if (parameter in fields)
                    functionInput.push(properties[parameter])
                else
                    functionInput.push(parameter)
            }

            // call function, get field value
            if (field.item != null)
                properties[item] = (field.function(...functionInput)[field.item])
            else
                properties[item] = (field.function(...functionInput))
        }

        // if no function, field is a django model field
        else {
            properties[item] = model[item]
        }
    }
*/
    // turn properties into list, formatting occurs
    // turn object rows into list of table data
    let result: any[] = []
    for (const item in row) {
        // this should totally be a switch case
        if (fields[item].type == "money") {
            result.push(<td style={fields["style"]}>{"$" + (numberWithCommas(Number(row[item]).toFixed(2)))}</td>)
        }

        else if (fields[item].type == "amount") {
            result.push(<td>{numberWithCommas(Number(row[item]).toFixed(2))}</td>)
        }
        else if (fields[item].type == "percent") {
            var strColor = "red";
            if (Number(row[item]) > 0) {
                strColor = "green";
            }
            result.push(<td style={{ color: strColor }}>{Number(row[item]).toFixed(2) + "%"}</td>)
        }
        else if (fields[item].type == "marketCap") {
            if (row[item] == "ETF") {
                result.push(<td style={fields["style"]}>{row[item]}</td>)
            }
            else {
                result.push(<td>{"$" + Number(row[item]).toFixed(2) + " Billion"}</td>)
            }
        }
        else if (fields[item].type == "hidden") {
            continue
        }

        else if (fields[item].type == "watchlistDelete") {
            result.push(
                <td role="delete" onClick={() => {
                    let tickersDB = localStorage.getItem("tickers");
                    let tickers: string[] = JSON.parse(tickersDB as string);
                    tickers = tickers.filter(e => e !== row['ticker']); // will remove ticker from list
                    // setting tickers so display refreshes
                    let tickerModels: any[] = []
                    for (const i in tickers) {
                        tickerModels.push({ "ticker": tickers[i] })
                    }
                    fields[item].function2(tickerModels)
                    tickersDB = JSON.stringify(tickers);
                    localStorage.setItem("tickers", tickersDB);
                    dispatchRow({type: "delete", row: row})
                    setMessage({ text: row['ticker'] + " deleted from watchlist", type: "success" });
                }}>delete</td>
            )
        }

        // not actually generalized, only works for assets
        else if (fields[item].type == "delete") {
            result.push(<td onClick={() => {
                fields[item].function2(row.id).then(() => {
                    dispatch({ type: "refresh", asset: row });
                    dispatchRow({type: "delete", row: row})
                    setMessage({ text: row.ticker + " deleted", type: "success" })
                })
                    .catch(() => {
                        setMessage({ text: "There was a problem deleting the asset", type: "error" })
                    })
            }}>Delete</td>)
        }

        else
            result.push(<td>{row[item]}</td>)
    }

    return (
        <tr key={row.id}>
            {result}
        </tr>)
}