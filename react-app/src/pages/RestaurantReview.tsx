import ReviewForm from '../components/ReviewForm';
import RestaurantTable from '../components/RestaurantTable';

export default function RestaurantReview() {

    return (
        <>
            <ReviewForm setMessage={console.log}/>
            <RestaurantTable />
        </>
    )
}