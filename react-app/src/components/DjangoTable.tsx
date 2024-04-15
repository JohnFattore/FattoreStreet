import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';

export default function DjangoTable({ models, dispatch, setMessage, fields, axiosFunctions }) {

    let headers: String[] = []
    for (const i in fields) {
        if (fields[i].type != 'hidden')
            headers.push(fields[i].name)
    }

    for (const i in axiosFunctions)
        headers.push(i)

    if (models.length == 0) {
        return (<h3 role="noModels">There are no models for this week</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    {headers.map((property) => (
                        <th>{property}</th>
                    ))}
                </tr>
            </thead>
            <tbody>
                {models.map((model) => (
                    <DjangoRow model={model} setMessage={setMessage} dispatch={dispatch} fields={fields} axiosFunctions={axiosFunctions} />
                ))}
            </tbody>
        </Table>
    );
}