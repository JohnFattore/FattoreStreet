import RestaurantTable from '../components/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants, getReviews, getRestaurantRecommendations } from '../components/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import ReviewForm from '../components/ReviewForm';
import { useState } from 'react';
import { IRestaurant } from '../interfaces';
import { Button, Alert } from 'react-bootstrap';
import ReviewTable from '../components/ReviewTable';
import ReviewMap from '../components/ReviewMap'
import RestaurantRecommendTable from '../components/RestaurantRecommendTable'
import LoginForm from '../components/LoginForm';

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();
    const { access } = useSelector((state: RootState) => state.user);
    const { restaurants, loading } = useSelector((state: RootState) => state.restaurantRecommend);

    useEffect(() => {
        dispatch(getRestaurants());
        dispatch(getReviews())
    }, []);

    const [restaurant, setRestaurant] = useState<IRestaurant>({
        yelp_id: '',
        name: '',
        address: '',
        state: '',
        city: '',
        latitude: 1,
        longitude: 1,
        categories: '',
        stars: '',
        review_count: 0,
        id: 0
    });

    const [showMap, setShowMap] = useState(false);

    const renderRecommendations = () => {

        if (restaurants.length === 0 && !loading) {
            return (
                <Button onClick={() => dispatch(getRestaurantRecommendations())}>
                    Click for Recommendations
                </Button>
            );
        }

        if (loading) return <Alert>Loading Restaurant Recommendations</Alert>;

        return <RestaurantRecommendTable setRestaurant={setRestaurant} />
    };

    if (!access) {
        return <>
        <Alert>Login to see reviews and get recommendations</Alert>
        <LoginForm/>
        <RestaurantTable setRestaurant={setRestaurant} />
        <p>Data provided by Yelp</p>
        </>
    }

    return (
        <>
        <h1>Nashville Restaurants</h1>
            <ReviewTable />
            <Button onClick={() => setShowMap(prev => !prev)}> {showMap ? 'Hide Map' : 'Show Map'} </Button>
            {showMap && <ReviewMap />}
            {renderRecommendations()}
            <ReviewForm restaurant={restaurant} />
            <RestaurantTable setRestaurant={setRestaurant} />
            <p>Data provided by Yelp</p>
        </>
    )
}