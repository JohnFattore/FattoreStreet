import { Button } from 'react-bootstrap';
import { IRestaurant } from '../../interfaces';

export const restaurantColumns = (setRestaurant: (r: IRestaurant) => void) => [
    { label: "Restaurant", sortKey: "name" },
    { label: "Address", sortKey: "address" },
    { label: "State", sortKey: "state" },
    { label: "City", sortKey: "city" },
    { label: "Stars", sortKey: "stars" },
    { label: "Review Count", sortKey: "review_count" },
    {
        label: "Create Review",
        sortKey: "createReview",
        sortable: false,
        render: (restaurant: IRestaurant) => (
            <Button
                variant="primary"
                size="sm"
                onClick={() => setRestaurant(restaurant)}
            >
                Create Review
            </Button>
        ),
    },
];
