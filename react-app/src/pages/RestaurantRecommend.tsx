import RestaurantRecommendTable from '../components/RestaurantRecommendTable';
import { useEffect } from 'react';
import { getRestaurantRecommendations } from '../components/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();

    useEffect(() => {
        dispatch(getRestaurantRecommendations());
    }, []);


    return (
        <>
            <RestaurantRecommendTable setRestaurant={console.log} />
            <p>Data provided by Yelp and is only a subset of all restaurant... All Nashville restaurants seem to be here.</p>
        </>
    )
}