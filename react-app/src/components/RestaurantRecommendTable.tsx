import Table from 'react-bootstrap/Table';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { Alert } from 'react-bootstrap';
import { setRestaurantRecommendSort } from '../reducers/restaurantRecommendReducer';
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
    //{ name: "Create Review", type: "text", field: "createReview" },
    //{ name: "Yelp ID", type: "text", field: "yelp_id" },
]

export default function RestaurantRecommendTable({ setRestaurant }) {
    const { restaurants, loading, error, sort } = useSelector((state: RootState) => state.restaurantRecommend);
    const dispatch = useDispatch<AppDispatch>();

    const handleSort = (sortColumn: string) => {
        const sortDirection =
            sort.sortColumn === sortColumn && sort.sortDirection === 'asc'
                ? 'desc'
                : 'asc';
        dispatch(setRestaurantRecommendSort({ sortColumn, sortDirection }));
    };


    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i} onClick={() => handleSort(fields[i]["field"])}>{fields[i].name}</th>)
    }

    if (loading) return <Alert>Loading Restaurants</Alert>;
    if (error) return <Alert variant="danger">Error: {error}</Alert>;
    if (restaurants.length == 0) {
        return (<h3>No Data</h3>)
    }

    return (
        <>
        <h3>Recommended Restaurants</h3>
            <Table>
                <thead>
                    <tr>
                        {headers}
                    </tr>
                </thead>
                <tbody>
                    {restaurants.map((restaurant, index) => (
                        <RestaurantRow key={index} fields={fields} restaurant={restaurant} setRestaurant={setRestaurant} />
                    ))}
                </tbody>
            </Table>
        </>

    );
}