import { Form, Button, Alert } from 'react-bootstrap';
import { useForm /*, SubmitHandler*/ } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postUser, login } from '../functions/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { translateError } from '../functions/helperFunctions';
import { setUserError } from '../reducers/userReducer';

interface IFormInput {
    username: string,
    password: string,
    password2: string,
    email: string
}

export default function RegisterForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { access, loading, error } = useSelector((state: RootState) => state.user);

    const schema = yup.object().shape({
        username: yup.string().required(),
        password: yup.string().required(),
        password2: yup.string().required(),
        email: yup.string().email().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })

    const onSubmit = (data: IFormInput) => {
        if (data.password != data.password2)
            dispatch(setUserError("Passwords must match"))
        else {
            dispatch(postUser(data)).unwrap()
                .then(() => {
                    dispatch(login({ username: data.username, password: data.password }))
                })
        }
    }

    if (access) {
        return null;
    }

    return (
        <div className="register-form-container">
            {error && <Alert variant="danger">{translateError(error)}</Alert>}
            {loading && <Alert variant="info">Loading...</Alert>}
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Form.Group className="mb-3">
                    <Form.Label>Username</Form.Label>
                    <Form.Control
                        {...register("username", { required: true })}
                        placeholder='Enter username'
                    />
                    {errors.username && <Alert variant='danger' className="mt-2" role="usernameError">Username is required</Alert>}
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Email</Form.Label>
                    <Form.Control
                        {...register("email", { required: true })}
                        placeholder='Enter email'
                    />
                    {errors.email && <Alert variant='danger' className="mt-2" role="emailError">Valid email is required</Alert>}
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Password</Form.Label>
                    <Form.Control
                        type="password"
                        {...register("password", { required: true })}
                        placeholder='Enter password'
                    />
                    {errors.password && <Alert variant='danger' className="mt-2" role="passwordError">Password is required</Alert>}
                </Form.Group>

                <Form.Group className="mb-3">
                    <Form.Label>Confirm Password</Form.Label>
                    <Form.Control
                        type="password"
                        {...register("password2", { required: true })}
                        placeholder='Confirm password'
                    />
                </Form.Group>

                <div className="d-grid gap-2">
                    <Button variant="success" type="submit" disabled={loading}>
                        {loading ? 'Creating User...' : 'Register User'}
                    </Button>
                </div>
            </Form>
        </div>
    );
}