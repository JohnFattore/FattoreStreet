import axios from 'axios';
import * as formik from 'formik';
import * as Yup from 'yup';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
// import Django URL Context
import { useContext } from 'react';
import { ENVContext } from '../ENVContext';

function LoginForm() {
  // Django RESTFUL API base url
  const ENV = useContext(ENVContext);
  const { Formik } = formik;
  const schema = Yup.object().shape({
    username: Yup.string()
      .required('Required'),
    password: Yup.string()
      .required('Required'),
  });

  return (
    <Formik
      initialValues={{ username: '', password: '' }}
      validationSchema={schema}
      // on submit, post to server with values in the form
      onSubmit={(values) => {
        axios.post(ENV.djangoURL.concat("token/"), {
          username: values.username,
          password: values.password,
        }).then((response) => {
          sessionStorage.setItem("token", response.data.access);
          sessionStorage.setItem("refresh", response.data.refresh);
        });
        // doesnt work, want to clear form after post
        //Formik.resetForm();
      }}
    >
      {({ handleSubmit, handleChange, values, touched, errors }) => (
        <Form noValidate onSubmit={handleSubmit}>
          <Form.Group as={Col} md="4" controlId="validationFormik01">
            <Form.Label>Username / Email</Form.Label>
            <Form.Control
              type="text"
              name="username"
              placeholder="Username"
              value={values.username}
              onChange={handleChange}
              isValid={touched.username && !errors.username}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.username && errors.username ? (
              <Form.Label>{errors.username}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik02">
            <Form.Label>Password</Form.Label>
            <Form.Control
              type="text"
              name="password"
              placeholder="Password"
              value={values.password}
              onChange={handleChange}
              isValid={touched.password && !errors.password}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.password && errors.password ? (
              <Form.Label>{errors.password}</Form.Label>
            ) : null}
          </Form.Group>

          <Button type="submit">Login</Button>
        </Form>
      )}
    </Formik>
  );
}

export default LoginForm;