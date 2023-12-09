import { Table } from "react-bootstrap";
import AlbumRow from "./AlbumRow";

interface IAlbum {
    strName: string;
    strArtist: string;
    numYear: number;
}

const listAlbums: IAlbum[] = [
    {
        strName: "2014 Forest Hill Drive",
        strArtist: "J Cole",
        numYear: 2014,
    },
    {
        strName: "Nevermind",
        strArtist: "Nirvana",
        numYear: 1991,
    },
    {
        strName: "Songs in the Key of Life",
        strArtist: "Stevie Wonder",
        numYear: 1974,//?
    },
    {
        strName: "My Beautiful Dark Twisted Fantasy",
        strArtist: "Kanye West",
        numYear: 2010,
    },
    {
        strName: "The Blueprint",
        strArtist: "JAY-Z",
        numYear: 2001,
    },
    {
        strName: "Discovery",
        strArtist: "Daft Punk",
        numYear: 2000,
    },
    {
        strName: "Currents",
        strArtist: "Tame Impala",
        numYear: 2015,
    }
]

function AlbumList() {
    return (

        <Table>
            <thead>
                <tr>
                    <th scope="col">Album Name</th>
                    <th scope="col">Artist</th>
                    <th scope="col">Year</th>
                </tr>
            </thead>
            <tbody>
            {listAlbums.map(album => (
          <AlbumRow album={album} />
        ))} 
            </tbody>
        </Table>
    );
}

export default AlbumList;