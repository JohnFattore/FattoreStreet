import DjangoTable from '../components/DjangoTable';
import { IMessage, IRestaurant } from '../interfaces';
import { useState, useEffect, useReducer } from 'react';
import { getRestaurants } from '../components/axiosFunctions';
import { Alert } from 'react-bootstrap';
import { handleError, setAlertVarient } from '../components/helperFunctions';

function restaurantReducer(restaurants, action) {
    switch (action.type) {
        case 'add': {
            return [...restaurants, action.restaurant];
        }
        case 'delete': {
            return restaurants.filter(e => e !== action.restaurant)
        }
        case 'refresh': {
            return [...restaurants]
        }
    }
}

export default function Restaurants() {
    const [message, setMessage] = useState<IMessage>({ text: "", type: "" })
    const [restaurants, dispatch] = useReducer(restaurantReducer, []);

    let data: IRestaurant[] = []
    useEffect(() => {
        if (restaurants.length == 0) {
            getRestaurants()
                .then((response) => {
                    for (let i = 0; i < response.data.length; i++) {
                        data.push({
                            name: response.data[i].name,
                            address: response.data[i].address,
                            phone_number: response.data[i].phone_number,
                            website: response.data[i].website,
                            id: response.data[i].id
                        })
                    }
                    for (let i = 0; i < data.length; i++) {
                        dispatch({ type: "add", restaurant: data[i] })
                    }
                }).catch((error) => {
                    handleError(error, setMessage)
                })
        }
    }, []);

    const fields = {
        name: { name: "Name", type: "text" },
        address: { name: "Address", type: "text" },
        phone_number: { name: "Phone Number", type: "text" },
        website: { name: "Website", type: "text" },
    }

    return (
        <>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
            <DjangoTable setMessage={setMessage} models={restaurants} dispatch={dispatch} fields={fields} />
        </>
    )
}