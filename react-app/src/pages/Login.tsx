import LoginForm from '../components/LoginForm';
import { useState } from 'react';
import Alert from 'react-bootstrap/Alert';

export default function Login() {
    const [error, setError] = useState("noError")
    const [success, setSuccess] = useState("noSuccess")
    return (
        <>
            <h3>Login to your Fattore Account</h3>
            <LoginForm setError={setError} setSuccess={setSuccess}/>
            {error != "noError" && <Alert variant='danger' dismissible transition>{error} </Alert>}
            {success != "noSuccess" && <Alert dismissible transition>{success} </Alert>}
        </>
    );
}