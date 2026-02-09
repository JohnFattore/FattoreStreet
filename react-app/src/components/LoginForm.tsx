import { Form, Button, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import { getReviews, login } from '../functions/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from '../functions/helperFunctions';
import { clearReviewErrors } from '../reducers/reviewReducer';

interface IFormInput {
    username: string,
    password: string
}

export default function LoginForm() {
    const { access, loading, error } = useSelector((state: RootState) => state.user);
    const dispatch = useDispatch<AppDispatch>();

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>()
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(login({ username: data.username, password: data.password }))
            .then(() => {
                dispatch(getReviews())
                dispatch(clearReviewErrors())
            })
    }

    if (access) {
        return null;
    }

    return (
        <div className="login-form-container">
            {error && <Alert variant="danger">{translateError(error)}</Alert>}
            {loading && <Alert variant="info">Login Loading...</Alert>}
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Form.Group className="mb-3">
                    <Form.Label>Username</Form.Label>
                    <Form.Control
                        {...register("username", { required: true })}
                        placeholder='Enter username'
                    />
                    {errors.username && <Alert variant="danger" className="mt-2" role="usernameError">Username is required</Alert>}
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Password</Form.Label>
                    <Form.Control
                        type="password"
                        {...register("password", { required: true })}
                        placeholder='Enter password'
                    />
                    {errors.password && <Alert variant="danger" className="mt-2" role="passwordError">Password is required</Alert>}
                </Form.Group>

                <div className="d-grid gap-2">
                    <Button variant="primary" type="submit" disabled={loading}>
                        {loading ? 'Logging in...' : 'Login'}
                    </Button>
                </div>
            </Form>
        </div>
    );
}