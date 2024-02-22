import WatchListForm from '../components/WatchListForm'
import WatchListTable from '../components/WatchListTable';
import WatchListClear from '../components/WatchListClear';
import React from 'react';


export default function WatchList() {
    // assets is changed in useEffect, so another variable is needed to not cause the useEffect function to loop infinitly
    const [change, setChange] = React.useState(false)
    return (
        <>
            <WatchListTable change={change} setChange={setChange}/>
            <WatchListForm setChange={setChange}/>
            <WatchListClear setChange={setChange}/>
        </>

    );
}