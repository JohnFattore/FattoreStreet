import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';

export default function DjangoTable({ models, dispatch, setMessage, fields }) {

    let properties: String[] = []
    for (const i in fields)
        properties.push(fields[i].name)

    return (
        <Table>
            <thead>
                <tr>
                    {properties.map((property) => (
                        <th>{property}</th>
                    ))}
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