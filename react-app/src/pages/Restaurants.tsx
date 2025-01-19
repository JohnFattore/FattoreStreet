import RestaurantTable from '../components/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants } from '../components/axiosFunctions';
import { useDispatch } from "react-redux";
import { AppDispatch } from "../main";
import ReviewForm from '../components/ReviewForm';
import { useState } from 'react';
import { IRestaurant } from '../interfaces';
import { Col, Row } from 'react-bootstrap';
import LocationForm from '../components/LocationForm'

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();

    useEffect(() => {
        dispatch(getRestaurants());
    }, [dispatch]);

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

    return (
        <>
            <Row>
                <Col>
                    <ReviewForm restaurant={restaurant} />
                </Col>
                <Col>
                    <LocationForm />
                </Col>
            </Row>
            <RestaurantTable setRestaurant={setRestaurant} />
        </>
    )
}