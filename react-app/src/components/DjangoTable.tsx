import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';
import { useState } from 'react';

export default function DjangoTable({ models, dispatch, setMessage, fields }) {

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

    if (models.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {models.map((model) => (
                    <DjangoRow model={model} setMessage={setMessage} dispatch={dispatch} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}