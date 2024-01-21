import axios from 'axios';
import { Form, Button, Col, Row } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';

interface IFormInput {
    username: string,
    password: string
}

function LoginForm() {
    // Django RESTFUL API base url
    const ENV = useContext(ENVContext);
    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>()
    //console.log(watch("username"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        // on submit, post to server with values in the form
        axios.post(ENV.djangoURL.concat("token/"), {
            username: data.username,
            password: data.password,
        }).then((response) => {
            sessionStorage.setItem("token", response.data.access);
            sessionStorage.setItem("refresh", response.data.refresh);
            alert("Welcome ".concat(data.username, "!!!"));
        }).catch(() => {
            alert("Wrong Username / Password");
        });
        // doesnt work, want to clear form after post
        //Formik.resetForm();
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
            </Row>

            <Button type="submit" >Login</Button>
        </Form>
    );
}

export default LoginForm;