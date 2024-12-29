import Table from 'react-bootstrap/Table';
import { formatString } from './helperFunctions';
import { useSelector } from "react-redux";
import { RootState } from "../main";
import { Alert } from 'react-bootstrap';

// function is for simple calculations, function2 is for more complex operations
function RestaurantRow({ restaurant, fields }) {

    let attributes: any[] = [
        restaurant.name,
        restaurant.address,
        restaurant.phone_number,
        restaurant.website,
    ];

    let tableData: JSX.Element[] = [];

    for (let i = 0; i < attributes.length; i++) {
        tableData.push(<td key={i}>{formatString(attributes[i], fields[i]["type"])}</td>)
    }

    return (
        <tr key={restaurant.id}>
            {tableData}
        </tr>)
}

export default function RestaurantTable() {
    const { restaurants, loading, error } = useSelector((state: RootState) => state.restaurants);
    if (loading) return <Alert>Loading Restaurants</Alert>;
    if (error) return <Alert variant="danger">Error: {error}</Alert>;
    const fields = [
        { name: "Restaurant", type: "text" },
        { name: "Address", type: "text" },
        { name: "Phone Number", type: "text" },
        { name: "Website", type: "text"}
    ]
    
    let headers: JSX.Element[] = []
    for (let i = 0; i < fields.length; i++) {
        headers.push(<th key={i}>{fields[i].name}</th>)
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
                    <RestaurantRow key={index} restaurant={restaurant} fields={fields} />
                ))}
            </tbody>
        </Table>
    );
}