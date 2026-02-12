import { formatString } from "../../functions/helperFunctions";
import { Button } from "react-bootstrap";

export default function RestaurantRow({ fields, restaurant, setRestaurant }) {
    let tableData: JSX.Element[] = [];
    for (let i = 0; i < fields.length; i++) {
        if (fields[i]["field"] == "createReview") {
            tableData.push(
                <td key={i}>
                    <Button
                        variant="primary"
                        size="sm"
                        onClick={() => setRestaurant(restaurant)}
                    >
                        Create Review
                    </Button>
                </td>
            )
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