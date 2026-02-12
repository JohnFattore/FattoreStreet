import RestaurantTable from '../components/restaurants/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants, getReviews, getRestaurantRecommendations } from '../functions/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import ReviewForm from '../components/restaurants/ReviewForm';
import { useState } from 'react';
import { IRestaurant } from '../interfaces';
import { Alert, Button } from 'react-bootstrap';
import LoginRequired from '../components/LoginRequired';
import ReviewTable from '../components/restaurants/ReviewTable';
import ReviewMap from '../components/restaurants/ReviewMap'
import RestaurantRecommendTable from '../components/restaurants/RestaurantRecommendTable'

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

    const [showReviewModal, setShowReviewModal] = useState(false);
    const [showMap, setShowMap] = useState(false);

    // Function to handle opening the review modal
    const handleOpenReviewModal = (selectedRestaurant: IRestaurant) => {
        setRestaurant(selectedRestaurant);
        setShowReviewModal(true);
    };

    const renderRecommendations = () => {

        if (restaurants.length === 0 && !loading) {
            return (
                <Button onClick={() => dispatch(getRestaurantRecommendations())}>
                    Click for Recommendations
                </Button>
            );
        }

        if (loading) return <Alert>Loading Restaurant Recommendations</Alert>;

        return <RestaurantRecommendTable setRestaurant={handleOpenReviewModal} />
    };

    if (!access) {
        return (
            <>
                <LoginRequired
                    title="Nashville Restaurant Explorer"
                    message="Join our community to see personal reviews and get AI-powered recommendations."
                    buttonText="Sign In to Unlock Full Access"
                />
                <RestaurantTable setRestaurant={handleOpenReviewModal} />
                <p className="text-muted text-center mt-3">Data provided by Yelp</p>
            </>
        );
    }

    return (
        <>
            <h1>Nashville Restaurants</h1>
            <ReviewTable />
            <Button onClick={() => setShowMap(prev => !prev)}> {showMap ? 'Hide Map' : 'Show Map'} </Button>
            {showMap && <ReviewMap />}
            {renderRecommendations()}
            <ReviewForm
                restaurant={restaurant}
                show={showReviewModal}
                onHide={() => setShowReviewModal(false)}
            />
            <RestaurantTable setRestaurant={handleOpenReviewModal} />
            <p>Data provided by Yelp</p>
        </>
    )
}