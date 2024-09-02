
function numberWithCommas(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

// some things should move up to the table level and be passed down
export default function DjangoRow({ model, setMessage, dispatch, fields }) {

    // handle fields
    let properties = {}
    for (const item in fields) {
        const field = fields[item]
        if (field.function != null) {
            // create function parameter array
            const functionInput: any[] = []
            for (const j in field.parameters) {
                var parameter = field.parameters[j]
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

        else if (field.type == 'axios') {
            properties[item] = 'delete'
        }

        // if no function, field is a django model field
        else {
            properties[item] = model[item]
        }
    }

    // turn properties into list, formatting occurs
    let result: any[] = []
    for (const item in properties) {
        // this should totally be a switch case
        if (fields[item].type == "money") {
            result.push(<td style={fields["style"]}>{"$" + (numberWithCommas(Number(properties[item]).toFixed(2)))}</td>)
        }

        else if (fields[item].type == "amount") {
            result.push(<td>{Number(properties[item]).toFixed(2)}</td>)
        }
        else if (fields[item].type == "percent") {
            var strColor = "red";
            if (Number(properties[item]) > 0) {
                strColor = "green";
            }
            result.push(<td style={{ color: strColor }}>{Number(properties[item]).toFixed(2) + "%"}</td>)
        }
        else if (fields[item].type == "marketCap") {
            if (properties[item] == "ETF") {
                result.push(<td style={fields["style"]}>{properties[item]}</td>)
            }
            else {
                result.push(<td>{"$" + Number(properties[item]).toFixed(2) + " Billion"}</td>)
            }
        }
        else if (fields[item].type == "hidden") {
            continue
        }
        else if (fields[item].type == "axiosWL") {
            result.push(
                <td role="delete" onClick={() => {
                    let tickersDB = localStorage.getItem("tickers");
                    let tickers: string[] = JSON.parse(tickersDB as string);
                    tickers = tickers.filter(e => e !== model['ticker']); // will remove ticker from list
                    // setting tickers so display refreshes
                    let tickerModels: any[] = []
                    for (const i in tickers) {
                        tickerModels.push({ "ticker": tickers[i] })
                    }
                    fields[item].function2(tickerModels)
                    tickersDB = JSON.stringify(tickers);
                    localStorage.setItem("tickers", tickersDB);
                    setMessage({ text: model['ticker'] + " deleted from watchlist", type: "success" });
                }}>delete</td>
            )
        }
        else if (fields[item].type == "delete") {
            result.push(<td onClick={() => {
                fields[item].function2(model.id).then(() => {
                    dispatch({ type: "delete", asset: model });
                    setMessage({ text: model.ticker + " deleted", type: "success" })
                })
                    .catch(() => {
                        setMessage({ text: "There was a problem deleting the asset", type: "error" })
                    })
            }}>Delete</td>)
        }

        else
            result.push(<td>{properties[item]}</td>)
    }

    return (
        <tr key={model.id}>
            {result}
        </tr>)
}