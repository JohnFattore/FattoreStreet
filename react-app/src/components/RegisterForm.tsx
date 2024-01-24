import axios from 'axios';
import { Form, Button, Col, Row } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "react-router-dom";

interface IFormInput {
    username: string,
    password: string,    
    email: string

}

function RegisterForm() {

    const ENV = useContext(ENVContext);
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
        axios.post(ENV.djangoURL.concat("users/"), {
            username: data.username,
            password: data.password,
            email: data.email,
        }).then(() => {
            axios.post(ENV.djangoURL.concat("token/"), {
                username: data.username,
                password: data.password,
            }).then((response) => {
                sessionStorage.setItem("token", response.data.access);
                sessionStorage.setItem("refresh", response.data.refresh);
                alert("Welcome ".concat(data.username, "!!!"));
                navigate("/portfolio");
            });
        }).catch(() => {
            alert("Error Registering, the username is probably already taken");
        });
    }

    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("username", {
                        required: true
                    })} placeholder='Username' />
                    {errors.username && <p>This field is required</p>}

                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("password", {
                        required: true
                    })} placeholder='Password' />
                    {errors.password && <p>This field is required</p>}
                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("email", {
                        required: true
                    })} placeholder='Email' />
                    {errors.email && <p>This field is required</p>}
                </Col>
            </Row>

            <Button type="submit">Register</Button>
        </Form>
    );
}

export default RegisterForm;