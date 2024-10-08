import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
//import { useNavigate } from "react-router-dom";
import { login } from './axiosFunctions';
import { handleError } from './helperFunctions';

interface IFormInput {
    username: string,
    password: string
}

export default function LoginForm({ setMessage }) {
    //const navigate = useNavigate();
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>()
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        // on submit, post to server with values in the form
        setMessage({ text: "Loading", type: "loading" })
        login(data.username, data.password)
            .then(() => {
                setMessage({ text: "Welcome ".concat(data.username, "!!!"), type: "success" });
                //navigate("/portfolio");
                // handle this more elegantly please
                window.location.reload();
            })
            .catch((error) => {
                handleError(error, setMessage)
            });
    }
    return (
        <>
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col>
                        <Form.Control size="lg" {...register("username", {
                            required: true
                        })} placeholder='Username' />
                        {errors.username && <Alert variant="danger" role="usernameError">Error: Username text field is required</Alert>}

                    </Col>
                    <Col>
                        <Form.Control type="password" size="lg" {...register("password", {
                            required: true
                        })} placeholder='Password' />
                        {errors.password && <Alert variant="danger" role="passwordError">Error: password, This field is required</Alert>}
                    </Col>
                </Row>
                <Button type="submit" >Login</Button>
            </Form>
        </>

    );
}