import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import { getAssets, getReviews, login } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from './helperFunctions';

interface IFormInput {
    username: string,
    password: string
}

export default function LoginForm() {
    const { username, access, loading, error } = useSelector((state: RootState) => state.user);
    const dispatch = useDispatch<AppDispatch>();

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>()
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(login({username: data.username, password: data.password}))
        .then(() => {
            dispatch(getAssets())
            dispatch(getReviews())
        })
    }

    if (access) {
        return <Alert variant="success">Welcome, {username}!</Alert>;
    }

    return (
        <>
            {error && <Alert variant="danger">{translateError(error)}</Alert>}
            {loading && <Alert>Login Loading...</Alert>}
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
                <Button type="submit" disabled={loading}>Login</Button>
            </Form>
        </>
    );
}