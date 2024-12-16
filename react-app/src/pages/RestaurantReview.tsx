import ReviewForm from '../components/ReviewForm';
import { IMessage } from '../interfaces';
import { useState } from 'react';
import { Alert } from 'react-bootstrap';
import { setAlertVarient } from '../components/helperFunctions';

export default function RestaurantReview() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })

    return (
        <>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <ReviewForm setMessage={setMessage}/>
        </>
    )
}