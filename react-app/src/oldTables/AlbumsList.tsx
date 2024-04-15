import { Table } from "react-bootstrap";
import AlbumRow from "./AlbumRow";

interface IAlbum {
    name: string;
    artist: string;
    year: number;
}

const listAlbums: IAlbum[] = [
    {
        name: "Flower Boy",
        artist: "Tyler, The Creator",
        year: 2017,
    },
    {
        name: "2014 Forest Hill Drive",
        artist: "J Cole",
        year: 2014,
    },
    {
        name: "Nevermind",
        artist: "Nirvana",
        year: 1991,
    },
    {
        name: "Songs in the Key of Life",
        artist: "Stevie Wonder",
        year: 1974,
    },
    {
        name: "My Beautiful Dark Twisted Fantasy",
        artist: "Kanye West",
        year: 2010,
    },
    {
        name: "The Blueprint",
        artist: "JAY-Z",
        year: 2001,
    },
    {
        name: "Discovery",
        artist: "Daft Punk",
        year: 2000,
    },
    {
        name: "Currents",
        artist: "Tame Impala",
        year: 2015,
    },
    {
        name: "The Miseducation of Lauryn Hill",
        artist: "Lauryn Hill",
        year: 1998,
    },
    {
        name: "Rumours",
        artist: "Fleetwood Mac",
        year: 1977,
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