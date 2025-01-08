import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm /*, SubmitHandler*/ } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postUser, login } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from './helperFunctions';

interface IFormInput {
    username: string,
    password: string,
    email: string
}

export default function RegisterForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { username, access, loading, error } = useSelector((state: RootState) => state.user);

    const schema = yup.object().shape({
        username: yup.string().required(),
        password: yup.string().required(),
        email: yup.string().email().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })

    const onSubmit = (data: IFormInput) => {
        dispatch(postUser(data)).unwrap()
            .then(() => {
                dispatch(login({ username: data.username, password: data.password }))
            })
    }

    if (access) {
        return <Alert variant="success">Welcome, {username}!</Alert>;
    }

    return (<>
        {error && <Alert variant="danger">{translateError(error)}</Alert>}
        {loading && <Alert>Loading...</Alert>}
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("username", {
                        required: true
                    })} placeholder='Username' />
                    {errors.username && <Alert variant='danger' role="usernameError">This field is required</Alert>}

                </Col>
                <Col sm={3}>
                    <Form.Control type="password" size="lg" {...register("password", {
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

            <Button type="submit" disabled={loading}>Register</Button>
        </Form>
    </>
    );
}