import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';
import { useState, useReducer } from 'react';

function rowReducer(rows, action) {
    switch (action.type) {
        case 'add': {
            return [...rows, action.row];
        }
        case 'delete': {
            return rows.filter(e => e !== action.row)
        }
        case 'refresh': {
            return [...rows]
        }
    }
}

export default function DjangoTable({ models, dispatch, setMessage, fields }) {

    // create list of header names, enable table sorting
    const [counter, setCounter] = useState(1)
    let headers: any[] = []
    for (const field in fields) {
        if (fields[field].type != 'hidden') {
            headers.push(<th onClick={() => {
                setCounter(counter * -1)
                if (fields[field].type == "text") {
                    models.sort((a, b) => {
                        if (b[field] > a[field])
                            return (counter * 1)
                        else if (b[field] < a[field])
                            return (counter * -1)
                        else
                            return 0
                    })
                }
                else {
                    models.sort((a, b) => counter * (b[field] - a[field]))
                }
                dispatch({ type: "refresh" });
            }}>{fields[field].name}</th>);
        }
    }

    // react state, list of display model fields
    const [rows, dispatchRow] = useReducer(rowReducer, []);
    // loop throug all models
    for (let i = 0; i < models.length; i++) {
        let row = {};
        // for each model, calculate each field
        for (const item in fields) {
            const field = fields[item]

            if (field.function != null) {
                // create function parameter array
                const functionInput: any[] = []
                for (const j in field.parameters) {
                    const parameter = field.parameters[j]
    
                    // Resolve parameter: from model, properties, or raw value
                    if (parameter in models[i])
                        functionInput.push(models[i][parameter])
                    else if (parameter in fields)
                        functionInput.push(row[parameter])
                    else
                        functionInput.push(parameter)
                }
    
                // call function, get field value
                if (field.item != null)
                    row[item] = (field.function(...functionInput)[field.item])
                else
                    row[item] = (field.function(...functionInput))
            }
    
            // if no function, field is a django model field
            else {
                row[item] = models[i][item]
            }            
        }
        dispatchRow({type: "add", row: row})
    }

    console.log(rows)

    if (models.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }
    
// hard to not feel like models and rows is too similar
    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {rows.map((row) => (
                    <DjangoRow row={row} setMessage={setMessage} dispatch={dispatch} fields={fields} dispatchRow={dispatchRow}/>
                ))}
            </tbody>
        </Table>
    );
}