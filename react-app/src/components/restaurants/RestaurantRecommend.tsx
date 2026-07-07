import RestaurantRecommendTable from './RestaurantRecommendTable';
import { useLazyGetRestaurantRecommendationsQuery } from '../../functions/api';
import { Button } from 'react-bootstrap';
import { IRestaurant } from '../../interfaces';

export default function RestaurantRecommend({ setRestaurant }: { setRestaurant: (r: IRestaurant) => void }) {
    const [getRecommendations, { data: restaurants, isLoading, error }] =
        useLazyGetRestaurantRecommendationsQuery();

    if (!restaurants && !isLoading && !error) {
        return (
            <Button onClick={() => getRecommendations()}>
                Click for Recommendations
            </Button>
        )
    }

    return (
        <>
            <RestaurantRecommendTable
                restaurants={restaurants ?? []}
                isLoading={isLoading}
                errors={[error]}
                setRestaurant={setRestaurant}
            />
            <p>Data provided by Yelp and is only a subset of all restaurant... All Nashville restaurants seem to be here.</p>
        </>
    )
}
