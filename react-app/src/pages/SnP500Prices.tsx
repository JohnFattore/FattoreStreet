import SnP500Table from '../components/SnP500Table';
import SnP500Form from '../components/SnP500Form'
import YahooFinanceBanner from '../components/YahooFinanceBanner';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { useEffect } from 'react';
import { getSnP500Price } from '../components/axiosFunctions';

export default function SnP500Prices() {
    const dispatch = useDispatch<AppDispatch>();
    const { snp500Prices } = useSelector((state: RootState) => state.snp500Prices);

    
    useEffect(() => {
        if (snp500Prices.length == 0) {
            dispatch(getSnP500Price('2020-01-02'));
        }
    }, [dispatch])


    return (
        <>
            <SnP500Form />
            <SnP500Table />
            <YahooFinanceBanner />
        </>
    )
}