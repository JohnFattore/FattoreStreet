import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';

export default function DjangoTable({ models, dispatch, setMessage, extraFields, excludeFields }) {

    const model = models[0]

    let properties: String[] = []
    for (const property in model) {
        if (!(excludeFields.includes(property)))
            properties.push(property)
    }

    for (const index in extraFields) {
        properties.push(extraFields[index].field)
        //properties.push(extraFields[index].function(extraFields[index].field))
    }


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
                    <DjangoRow model={model} setMessage={setMessage} dispatch={dispatch} extraFields={extraFields} excludeFields={excludeFields} />
                ))}
            </tbody>
        </Table>
    );
}