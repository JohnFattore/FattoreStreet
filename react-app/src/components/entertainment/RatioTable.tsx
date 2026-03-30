import Table from 'react-bootstrap/Table';
import { formatString } from '../../functions/helperFunctions';
import { IRatio } from '../../interfaces';

interface Field { name: string; type: string; }

function RatioRow({ ratio, fields }: { ratio: IRatio; fields: Field[] }) {

    const attributes: string[] = [
        ratio.name,
        ratio.formula,
        ratio.description
    ];

    const tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={ratio.name}>
            {tableData}
        </tr>)
}

export default function RatioTable({ratios}: {ratios: IRatio[]}) {

    const fields = [
        {name: "Name", type: "text"},
        {name: "Artist", type: "text"},
        {name: "Year", type: "text"}
    ]
    
    const headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
    }


    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {ratios.map((ratio: IRatio) => (
                    <RatioRow key={ratio.name} ratio={ratio} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}