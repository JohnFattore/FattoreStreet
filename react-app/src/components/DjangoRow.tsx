

// some things should move up to the table level and be passed down
export default function DjangoRow({ model, setMessage, dispatch, fields, axiosFunctions }) {

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
            result.push(<td style={ fields["style"] }>{"$" + Number(properties[item]).toFixed(2)}</td>)
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
            result.push(<td>{"$" + Number(properties[item]).toFixed(2) + " Billion"}</td>)
        }
        else if (fields[item].type == "hidden") {
            continue
        }
        else
            result.push(<td>{properties[item]}</td>)
    }

    if (axiosFunctions['delete'] != null) {
        result.push(<td onClick={() => {
            axiosFunctions['delete'](model.id).then(() => {
                dispatch({ type: "delete", asset: model });
                setMessage({ text: model.ticker + " deleted", type: "success" })
            })
                .catch(() => {
                    setMessage({ text: "There was a problem deleting the asset", type: "error" })
                })
        }}>Delete</td>)
    }

    if (axiosFunctions["watchlist"] != null) {
        result.push(
            <td role="delete" onClick={() => {
                let tickersDB = localStorage.getItem("tickers");
                let tickers: string[] = JSON.parse(tickersDB as string);
                tickers = tickers.filter(e => e !== model['ticker']); // will remove ticker from list
                // setting tickers so display refreshes
                let tickerModels: any[] = []
                for (const i in tickers) {
                    tickerModels.push({"ticker": tickers[i]})
                }
                axiosFunctions["watchlist"](tickerModels)
                tickersDB = JSON.stringify(tickers);
                localStorage.setItem("tickers", tickersDB);
                setMessage({ text: model['ticker'] + " deleted from watchlist", type: "success" });
            }}>delete</td>
        )
    }


    return (
        <tr key={model.id}>
            {result}
        </tr>)
}