import Table from 'react-bootstrap/Table';
import { formatString } from './helperFunctions';

// function is for simple calculations, function2 is for more complex operations
function AlbumRow({ album, fields }) {

    let attributes: any[] = [
        album.name,
        album.artist,
        album.year
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={album.name}>
            {tableData}
        </tr>)
}

export default function AlbumTable({albums}) {

    const fields = [
        {name: "Name", type: "text"},
        {name: "Artist", type: "text"},
        {name: "Year", type: "text"}
    ]
    
    let headers: JSX.Element[] = []
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
                {albums.map((album) => (
                    <AlbumRow key={album.name} album={album} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}