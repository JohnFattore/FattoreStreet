import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "react-router-dom";
import { postUser, login } from './axiosFunctions';

interface IFormInput {
    username: string,
    password: string,
    email: string

}

export default function RegisterForm({ setMessage }) {
    const navigate = useNavigate();

    const schema = yup.object().shape({
        username: yup.string().required(),
        password: yup.string().required(),
        email: yup.string().email().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })
    //console.log(watch("username"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        postUser(data.username, data.password, data.email)
            .then(() => {
                login(data.username, data.password)
                    .then(() => {
                        navigate("/wallstreet");
                    });
            }).catch(() => {
                setMessage({text: "Error Registering, the username is probably already taken", type: "error"});
            });
    }

    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("username", {
                        required: true
                    })} placeholder='Username' />
                    {errors.username && <Alert variant='danger' role="usernameError">This field is required</Alert>}

                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("password", {
                        required: true
                    })} placeholder='Password' />
                    {errors.password && <Alert variant='danger' role="passwordError">This field is required</Alert>}
                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("email", {
                        required: true
                    })} placeholder='Email' />
                    {errors.email && <Alert variant='danger' role="emailError">This field is required</Alert>}
                </Col>
            </Row>

            <Button type="submit">Register</Button>
        </Form>
    );
}