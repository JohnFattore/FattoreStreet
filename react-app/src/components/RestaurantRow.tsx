import { formatString } from "./helperFunctions";

export default function RestaurantRow({ fields, restaurant, setRestaurant }) {
    let tableData: JSX.Element[] = [];
    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["field"] == "createReview") {
            tableData.push(<td key={i} onClick={() => setRestaurant(restaurant)}>{fields[i]["name"]}</td>)
        }
        else {
            tableData.push(<td key={i}>{formatString(restaurant[fields[i]["field"]], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr key={restaurant.id}>
            {tableData}
        </tr>)
}