import RestaurantTable from '../components/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants } from '../components/axiosFunctions';
import { useDispatch } from "react-redux";
import { AppDispatch } from "../main";

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();

    useEffect(() => {
        dispatch(getRestaurants());
    }, [dispatch]);
    
    return (
        <>
            <RestaurantTable />
        </>
    )
}