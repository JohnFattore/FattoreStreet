import RestaurantTable from '../components/RestaurantTable';
import { useEffect } from 'react';
import { getRestaurants, getReviews } from '../components/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import ReviewForm from '../components/ReviewForm';
import { useState } from 'react';
import { IRestaurant } from '../interfaces';
import { Col, Row } from 'react-bootstrap';
import LocationForm from '../components/LocationForm'
import ReviewTable from '../components/ReviewTable';

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();
    const { state, city } = useSelector((state: RootState) => state.location);
    const { access } = useSelector((state: RootState) => state.user);

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

    return (
        <>
            <ReviewTable />
            <Row>
                <Col md={4}>
                    <h3>{"Selected Location: ".concat(state, " ", city)}</h3>
                    <LocationForm />
                </Col>
                {access && <Col md={8}>
                    <ReviewForm restaurant={restaurant} />
                </Col>}
            </Row>
            <RestaurantTable setRestaurant={setRestaurant} />
            <p>Data provided by Yelp and is only a subset of all restaurant... All Nashville restaurants seem to be here.</p>
        </>
    )
}