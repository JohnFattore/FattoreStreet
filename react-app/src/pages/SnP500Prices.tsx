import SnP500Table from '../components/SnP500Table';
import SnP500Form from '../components/SnP500Form'
import YahooFinanceBanner from '../components/YahooFinanceBanner';
import { useDispatch } from "react-redux";
import { AppDispatch } from '../main';
import { useEffect } from 'react';
import { getSnP500Price } from '../components/axiosFunctions';

export default function SnP500Prices() {
    const dispatch = useDispatch<AppDispatch>();
    //const { assets, loading, error } = useSelector((state: RootState) => state.assets);

    useEffect(() => {
        dispatch(getSnP500Price('2020-01-02'));
    }, [dispatch])

    return (
        <>
            <SnP500Form />
            <SnP500Table />
            <YahooFinanceBanner />
        </>
    )
}