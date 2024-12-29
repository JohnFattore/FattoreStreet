import { useDispatch } from 'react-redux';
import { AppDispatch } from '../main';
import WatchListTable from '../components/WatchListTable';
import { useEffect } from 'react';
import { loadTickers } from '../reducers/watchListReducer';
import BenchmarkTable from '../components/BenchmarkTable';
import WatchListForm from '../components/WatchListForm';

export default function WatchList() {

    const dispatch = useDispatch<AppDispatch>();

    useEffect(() => {
        dispatch(loadTickers());
    }, [dispatch]);    

    return (
        <>
            <h3>Common Benchmarks</h3>
            <BenchmarkTable />
            <h3>Watchlist</h3>
            <WatchListTable />
            <WatchListForm />
        </>
    );
}