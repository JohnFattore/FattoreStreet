//import React from 'react';
import axios from 'axios';
import * as formik from 'formik';
import * as Yup from 'yup';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
// import Django URL Context
import { useContext } from 'react';
import { ENVContext } from '../ENVContext';

function AssetPost() {

  const ENV = useContext(ENVContext);

  const { Formik } = formik;

  const schema = Yup.object().shape({
    ticker: Yup.string()
      .max(15, 'Must be 5 characters or less')
      .required('Required'),
    shares: Yup.number()
      .required('Required'),
    costBasis: Yup.string()
      .required('Required'),
    buyDate: Yup.date()
      .required('Required'),/*
    account: Yup.string()
      .required('Required'), */
  });

  // formik provides some state handling capabilities i.e. no need for useState
  // formik also includes error handling
  // react bootstrap form, button, col, row utilized for styling, which the formik form lacks
  // yup handles form validation parameters
  return (
    <Formik
      initialValues={{ ticker: '', shares: '', costBasis: '', buyDate: '', account: '' }}
      validationSchema={schema}
      // on submit, post to server with values in the form
      onSubmit={(values) => {
        axios.post(ENV.djangoURL.concat("assets/"), {
          ticker_string: values.ticker,
          shares_number: values.shares,
          costbasis_number: values.costBasis,
          buy_date: values.buyDate,
          account_string: 'roth_ira',
          // Set on the back end
          user: 1
        },{
        headers: {
          'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string),
      }}
        );
        // doesnt work, want to clear form after post
        //Formik.resetForm();
      }}
    >
      {({ handleSubmit, handleChange, values, touched, errors }) => (
        <Form noValidate onSubmit={handleSubmit}>
          <Form.Group as={Col} md="4" controlId="validationFormik01">
            <Form.Label>Ticker</Form.Label>
            <Form.Control
              type="text"
              name="ticker"
              placeholder="Ticker"
              value={values.ticker}
              onChange={handleChange}
              isValid={touched.ticker && !errors.ticker}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.ticker && errors.ticker ? (
              <Form.Label>{errors.ticker}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik02">
            <Form.Label>Number of Shares</Form.Label>
            <Form.Control
              type="text"
              name="shares"
              placeholder="Shares"
              value={values.shares}
              onChange={handleChange}
              isValid={touched.shares && !errors.shares}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.shares && errors.shares ? (
              <Form.Label>{errors.shares}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik03">
            <Form.Label>Price per Share</Form.Label>
            <Form.Control
              type="text"
              name="costBasis"
              placeholder="Cost Basis"
              value={values.costBasis}
              onChange={handleChange}
              isValid={touched.costBasis && !errors.costBasis}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.costBasis && errors.costBasis ? (
              <Form.Label>{errors.costBasis}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik04">
            <Form.Label>Buy Date</Form.Label>
            <Form.Control
              type="date"
              name="buyDate"
              placeholder="Buy Date"
              value={values.buyDate}
              onChange={handleChange}
              isValid={touched.buyDate && !errors.buyDate}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.buyDate && errors.buyDate ? (
              <Form.Label>{errors.buyDate}</Form.Label>
            ) : null}
          </Form.Group>

          <Button type="submit">Submit Purchase</Button>
        </Form>
      )}
    </Formik>
  );
}

export default AssetPost;