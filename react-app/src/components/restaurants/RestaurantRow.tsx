import { formatString } from "../../functions/helperFunctions";
import { Button } from "react-bootstrap";
import { IRestaurant } from "../../interfaces";

interface Field { name: string; type: string; field: string; }

export default function RestaurantRow({ fields, restaurant, setRestaurant }: { fields: Field[]; restaurant: IRestaurant; setRestaurant: (r: IRestaurant) => void }) {
    const tableData: JSX.Element[] = [];
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
            tableData.push(<td key={i}>{formatString((restaurant as unknown as Record<string, string | number>)[fields[i]["field"]], fields[i]["type"])}</td>)
        }
    }

    return (
        <tr key={restaurant.id}>
            {tableData}
        </tr>)
}