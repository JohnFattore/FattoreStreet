import DjangoTable from '../components/DjangoTable';
import Spike from '../components/SpikeImg';
import { IAlbum } from '../interfaces';

export default function Entertainment() {

    // this could be saved in database if desired
    const albums: IAlbum[] = [
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

    const fields = {
        name: {name: "Name"},
        artist: {name: "Artist"},
        year: {name: "Year"}
    }

    return (
        <>
            <h1>10 10/10 Albums</h1>
            <DjangoTable setMessage={console.log} models={albums} dispatch={console.log} fields={fields} axiosFunctions={{}}/>
            <Spike />
        </>
    );
}