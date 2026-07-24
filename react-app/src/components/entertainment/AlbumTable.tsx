import Table from "react-bootstrap/Table";
import { formatString } from "../../functions/helperFunctions";
import { IAlbum } from "../../interfaces";

interface Field {
  name: string;
  type: string;
}

function AlbumRow({ album, fields }: { album: IAlbum; fields: Field[] }) {
  const attributes: (string | number)[] = [
    album.name,
    album.artist,
    album.year,
  ];

  const tableData: JSX.Element[] = [];

  for (let i = 0; i < attributes.length; i++) {
    tableData.push(
      <td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>,
    );
  }

  return <tr key={album.name}>{tableData}</tr>;
}

export default function AlbumTable({ albums }: { albums: IAlbum[] }) {
  const fields = [
    { name: "Name", type: "text" },
    { name: "Artist", type: "text" },
    { name: "Year", type: "text" },
  ];

  const headers: JSX.Element[] = [];
  for (let i = 0; i < fields.length; i++) {
    headers.push(<th key={i}>{fields[i].name}</th>);
  }

  return (
    <Table>
      <thead>
        <tr>{headers}</tr>
      </thead>
      <tbody>
        {albums.map((album: IAlbum) => (
          <AlbumRow key={album.name} album={album} fields={fields} />
        ))}
      </tbody>
    </Table>
  );
}
