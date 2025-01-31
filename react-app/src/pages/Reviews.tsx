import RestaurantRecommendTable from '../components/RestaurantRecommendTable';
import { getRestaurantRecommendations } from '../components/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { Alert, Button } from 'react-bootstrap';
import ReviewMap from '../components/ReviewMap'
import { useEffect } from 'react';
import { getReviews } from '../components/axiosFunctions';
import LoginForm from '../components/LoginForm';

export default function Reviews() {
    const dispatch = useDispatch<AppDispatch>();
    const { restaurants, loading } = useSelector((state: RootState) => state.restaurantRecommend);
    const { access } = useSelector((state: RootState) => state.user);


    useEffect(() => {
        dispatch(getReviews())
    }, []);

    if (loading) return <Alert>Loading Restaurant Recommendations</Alert>;

    const renderRecommendations = () => {

        if (!access) {
            return <>
            <Alert>Login to see reviews and get recommendations</Alert>
            <LoginForm/>;
            </>
        }

        else if (restaurants.length === 0) {
            return (
                <Button onClick={() => dispatch(getRestaurantRecommendations())}>
                    Click for Recommendations
                </Button>
            );
        }

        else if (loading) {
            return <Alert>Loading Restaurant Recommendations</Alert>;
        }

        return <RestaurantRecommendTable setRestaurant={console.log} />;
    };


    return (
        <>
            {renderRecommendations()}
            <ReviewMap />
            <p>Data provided by Yelp and is only a subset of all restaurant... All Nashville restaurants seem to be here.</p>
        </>
    )
}