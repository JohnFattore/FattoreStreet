import { Form, Button, Alert } from 'react-bootstrap';
import { useForm /*, SubmitHandler*/ } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useSelector } from "react-redux";
import { RootState } from '../main';
import { translateError, getApiErrorMessages } from '../functions/helperFunctions';
import { usePostUserMutation, useLoginMutation } from '../functions/api';

interface IFormInput {
    username: string,
    password: string,
    password2: string,
    email: string
}

export default function RegisterForm() {
    const { access } = useSelector((state: RootState) => state.user);
    const [postUser, { error: registerError, isLoading: isRegistering }] = usePostUserMutation();
    const [login, { error: loginError, isLoading: isLoggingIn }] = useLoginMutation();

    const schema = yup.object().shape({
        username: yup.string().required(),
        password: yup.string().required(),
        password2: yup.string().oneOf([yup.ref("password")], "Passwords must match").required(),
        email: yup.string().email().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })

    const onSubmit = async (data: IFormInput) => {
        try {
            await postUser({ username: data.username, password: data.password, email: data.email }).unwrap();
            await login({ username: data.username, password: data.password }).unwrap();
        } catch {
            // errors surface through the mutation hooks
        }
    }

    const error = registerError ?? loginError;
    const isLoading = isRegistering || isLoggingIn;

    if (access) {
        return null;
    }

    return (
        <div className="register-form-container">
            {error ? <Alert variant="danger">{translateError(getApiErrorMessages(error)[0])}</Alert> : null}
            {isLoading && <Alert variant="info">Loading...</Alert>}
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Form.Group>
                    <Form.Label>Username</Form.Label>
                    <Form.Control
                        {...register("username", { required: true })}
                        placeholder='Enter username'
                    />
                    {errors.username && <Alert variant='danger' role="usernameError">Username is required</Alert>}
                </Form.Group>

                <Form.Group>
                    <Form.Label>Email</Form.Label>
                    <Form.Control
                        {...register("email", { required: true })}
                        placeholder='Enter email'
                    />
                    {errors.email && <Alert variant='danger' role="emailError">Valid email is required</Alert>}
                </Form.Group>

                <Form.Group>
                    <Form.Label>Password</Form.Label>
                    <Form.Control
                        type="password"
                        {...register("password", { required: true })}
                        placeholder='Enter password'
                    />
                    {errors.password && <Alert variant='danger' role="passwordError">Password is required</Alert>}
                </Form.Group>

                <Form.Group>
                    <Form.Label>Confirm Password</Form.Label>
                    <Form.Control
                        type="password"
                        {...register("password2", { required: true })}
                        placeholder='Confirm password'
                    />
                    {errors.password2 && <Alert variant='danger' role="password2Error">{errors.password2.message}</Alert>}
                </Form.Group>

                <div>
                    <Button variant="success" type="submit" disabled={isLoading}>
                        {isLoading ? 'Creating User...' : 'Register User'}
                    </Button>
                </div>
            </Form>
        </div>
    );
}
