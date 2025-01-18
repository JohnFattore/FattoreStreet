import Table from 'react-bootstrap/Table';
import { formatString } from './helperFunctions';
import { useSelector, useDispatch } from "react-redux";
import { RootState, AppDispatch } from "../main";
import { Alert } from 'react-bootstrap';
import { setRestaurantSort } from '../reducers/restaurantReducer';
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

]

// function is for simple calculations, function2 is for more complex operations
function RestaurantRow({ restaurant }) {
    let tableData: JSX.Element[] = [];

    for (let i = 0; i < fields.length; i++) {
        tableData.push(<td key={i}>{formatString(restaurant[fields[i]["field"]], fields[i]["type"])}</td>)
    }

    return (
        <tr key={restaurant.id}>
            {tableData}
        </tr>)
}

export default function RestaurantTable() {
    const { restaurants, loading, error, sort } = useSelector((state: RootState) => state.restaurants);
    const dispatch = useDispatch<AppDispatch>();
    if (loading) return <Alert>Loading Restaurants</Alert>;
    if (error) return <Alert variant="danger">Error: {error}</Alert>;

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

    if (restaurants.length == 0) {
        return (<h3 role="noModels">No Data</h3>)
    }

    return (
        <Table>
            <thead>
                <tr>
                    {headers}
                </tr>
            </thead>
            <tbody>
                {restaurants.map((restaurant, index) => (
                    <RestaurantRow key={index} restaurant={restaurant} />
                ))}
            </tbody>
        </Table>
    );
}