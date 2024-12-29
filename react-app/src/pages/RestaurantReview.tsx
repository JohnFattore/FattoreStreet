import ReviewForm from '../components/ReviewForm';
import { IMessage } from '../interfaces';
import { useState } from 'react';
import { Alert } from 'react-bootstrap';
import { setAlertVarient } from '../components/helperFunctions';
import RestaurantTable from '../components/RestaurantTable';
import { useSelector } from "react-redux";

export default function RestaurantReview() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const restaurants = useSelector((state: any) => state.restaurants);

    return (
        <>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <ReviewForm setMessage={setMessage}/>
            <RestaurantTable restaurants={restaurants} dispatch={console.log} setMessage={setMessage}/>
        </>
    )
}