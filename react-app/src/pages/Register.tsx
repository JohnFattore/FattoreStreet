import RegisterForm from '../components/RegisterForm';
import { useState } from 'react';
import { IMessage } from '../interfaces';
import Alert from 'react-bootstrap/Alert';
import { setAlertVarient } from '../components/helperFunctions';

export default function Register() {
    const [ message, setMessage ] = useState<IMessage>({text: "", type: ""})
    return (
        <>
            <h3>Register for a Fattore Account</h3>
            <RegisterForm setMessage={setMessage}/>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}