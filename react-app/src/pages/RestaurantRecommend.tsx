import RestaurantRecommendTable from '../components/RestaurantRecommendTable';
import { getRestaurantRecommendations } from '../components/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from "../main";
import { Alert, Button } from 'react-bootstrap';

export default function Restaurants() {
    const dispatch = useDispatch<AppDispatch>();
    const { restaurants, loading } = useSelector((state: RootState) => state.restaurantRecommend);

    /*
    useEffect(() => {
        dispatch(getRestaurantRecommendations());
    }, []);
*/
    if (restaurants.length == 0 && !loading) {
        return (
            <Button onClick={() => dispatch(getRestaurantRecommendations())}>
                Click for Recommendations
            </Button>
        )
    }

    if (loading) return <Alert>Loading Restaurant Recommendations</Alert>;

    return (
        <>
            <RestaurantRecommendTable setRestaurant={console.log} />
            <p>Data provided by Yelp and is only a subset of all restaurant... All Nashville restaurants seem to be here.</p>
        </>
    )
}