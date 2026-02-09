import RestaurantTable from '../components/restaurants/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants, getReviews, getRestaurantRecommendations } from '../functions/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import ReviewForm from '../components/restaurants/ReviewForm';
import { useState } from 'react';
import { IRestaurant } from '../interfaces';
import { Alert, Button, Col, Container, Row } from 'react-bootstrap';
import LoginModal from '../components/LoginModal';
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

    const [showLogin, setShowLogin] = useState(false);

    if (!access) {
        return (
            <Container className="mt-4">
                <Row className="mb-4">
                    <Col md={12} className="text-center">
                        <Alert variant="info" className="py-5 shadow-sm">
                            <h2 className="mb-4">Nashville Restaurant Explorer</h2>
                            <p className="lead mb-4">Join our community to see personal reviews and get AI-powered recommendations.</p>
                            <Button variant="primary" size="lg" onClick={() => setShowLogin(true)}>
                                Sign In to Unlock Full Access
                            </Button>
                        </Alert>
                    </Col>
                </Row>
                <LoginModal show={showLogin} onHide={() => setShowLogin(false)} />
                <RestaurantTable setRestaurant={setRestaurant} />
                <p className="text-muted text-center mt-3">Data provided by Yelp</p>
            </Container>
        );
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