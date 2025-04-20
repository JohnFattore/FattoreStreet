import Table from 'react-bootstrap/Table';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { Alert } from 'react-bootstrap';
import { setRestaurantSort } from '../reducers/restaurantReducer';
import RestaurantSearchBar from './RestaurantSearchBar';
import { useState } from 'react';
import RestaurantRow from './RestaurantRow';

const fields = [
    { name: "Restaurant", type: "text", field: "name" },
    { name: "Address", type: "text", field: "address" },
    { name: "State", type: "text", field: "state" },
    { name: "City", type: "text", field: "city" },
    //{ name: "Latitude", type: "text", field: "latitude" },
    //{ name: "Longitude", type: "text", field: "longitude" },
    //{ name: "Categories", type: "text", field: "categories" },
    { name: "Stars", type: "number", field: "stars" },
    { name: "Review Count", type: "text", field: "review_count" },
    { name: "Create Review", type: "text", field: "createReview" },
    //{ name: "Yelp ID", type: "text", field: "yelp_id" },
]

export default function RestaurantTable({ setRestaurant }) {
    const { restaurants, loading, error, sort } = useSelector((state: RootState) => state.restaurants);
    const dispatch = useDispatch<AppDispatch>();
    const [search, setSearch] = useState('')

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setRestaurantSort({ sortColumn, sortDirection }));
    };

    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
    }

    const filteredRestaurants = restaurants.filter(restaurant =>
        restaurant.name.toLowerCase().includes(search.toLowerCase())
    );

    const renderRestaurants = () => {

        if (loading) return (<Alert>Loading Restaurants</Alert>)

        if (error) return (<Alert variant="danger">Error: {error}</Alert>);

        if (filteredRestaurants.length == 0) return (<h3 role="noModels">No Data</h3>)

        return <>
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {filteredRestaurants.map((restaurant, index) => (
                        <RestaurantRow key={index} fields={fields} restaurant={restaurant} setRestaurant={setRestaurant} />
                    ))}
                </tbody>
            </Table>
        </>;
    };

    return (
        <>
            {<RestaurantSearchBar setSearch={setSearch} />}
            {renderRestaurants()}
        </>
    );
}