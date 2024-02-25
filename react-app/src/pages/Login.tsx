import LoginForm from '../components/LoginForm';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';
import { IMessage } from '../interfaces';
import { setAlertVarient } from '../components/helperFunctions';

export default function Login() {
    const [ message, setMessage ] = useState<IMessage>({text: "", type: ""})
    return (
        <>
            <h3>Login to your Fattore Account</h3>
            <LoginForm setMessage={setMessage}/>
            {message.type != "" && <Alert variant={setAlertVarient(message)} transition role="message">{message.text} </Alert>}
        </>
    );
}