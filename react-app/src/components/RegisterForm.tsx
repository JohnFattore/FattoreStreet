import axios from 'axios';
import * as formik from 'formik';
import * as Yup from 'yup';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
// import Django URL Context
import { useContext } from 'react';
import { strDjangoURLContext } from '../App';

function RegisterForm() {

  const strDjangoURL = useContext(strDjangoURLContext);

  const { Formik } = formik;
  const schema = Yup.object().shape({
    strUsername: Yup.string()
      .required('Required'),
    strPassword: Yup.string()
      .required('Required'),
  });

  return (
    <Formik
      initialValues={{ strUsername: '', strPassword: '' }}
      validationSchema={schema}
      // on submit, post to server with values in the form
      onSubmit={(values) => {
        axios.post(strDjangoURL.concat("assets/"), {
          username: values.strUsername,
          password: values.strPassword,
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
              name="strUsername"
              placeholder="Username"
              value={values.strUsername}
              onChange={handleChange}
              isValid={touched.strUsername && !errors.strUsername}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.strUsername && errors.strUsername ? (
              <Form.Label>{errors.strUsername}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik02">
            <Form.Label>Password</Form.Label>
            <Form.Control
              type="text"
              name="strPassword"
              placeholder="Password"
              value={values.strPassword}
              onChange={handleChange}
              isValid={touched.strPassword && !errors.strPassword}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.strPassword && errors.strPassword ? (
              <Form.Label>{errors.strPassword}</Form.Label>
            ) : null}
          </Form.Group>

          <Button type="submit">Login</Button>
        </Form>
      )}
    </Formik>
  );
}

export default RegisterForm;