import Table from 'react-bootstrap/Table';
import DjangoRow from './DjangoRow';

export default function DjangoTable({ models, dispatch, setMessage, fields }) {

    // messed this one up changing last one to an object, should have some standard object
    //for (const i in axiosFunctions)
    //    headers.push(i)

    let headers: any[] = []
    for (const field in fields) {
        if (fields[field].type != 'hidden') {
            headers.push(<th onClick={() => {
                models.sort((a, b) => b[field] - a[field])
                dispatch({ type: "refresh" });
            }}>{fields[field].name}</th>);
        }

    }

    if (models.length == 0) {
        return (<h3 role="noModels">Please Login</h3>)
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