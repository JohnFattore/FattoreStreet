//import React from 'react';
import axios from 'axios';
import * as formik from 'formik';
import * as Yup from 'yup';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';
// import Django URL Context
import { useContext } from 'react';
import { strDjangoURLContext } from '../App';

function AssetPost() {

  const strDjangoURL = useContext(strDjangoURLContext);

  const { Formik } = formik;

  const schema = Yup.object().shape({
    strTicker: Yup.string()
      .max(15, 'Must be 5 characters or less')
      .required('Required'),
    numShares: Yup.number()
      .required('Required'),
    numCostBasis: Yup.string()
      .required('Required'),
    dtmBuy: Yup.date()
      .required('Required'),/*
    strAccount: Yup.string()
      .required('Required'), */
  });

  // formik provides some state handling capabilities i.e. no need for useState
  // formik also includes error handling
  // react bootstrap form, button, col, row utilized for styling, which the formik form lacks
  // yup handles form validation parameters
  return (
    <Formik
      initialValues={{ strTicker: '', numShares: '', numCostBasis: '', dtmBuy: '', strAccount: '' }}
      validationSchema={schema}
      // on submit, post to server with values in the form
      onSubmit={(values) => {
        axios.post(strDjangoURL.concat("assets/"), {
          ticker_string: values.strTicker,
          shares_number: values.numShares,
          costbasis_number: values.numCostBasis,
          buy_date: values.dtmBuy,
          account_string: 'roth_ira',
          user_key: 1
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
              name="strTicker"
              placeholder="Ticker"
              value={values.strTicker}
              onChange={handleChange}
              isValid={touched.strTicker && !errors.strTicker}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.strTicker && errors.strTicker ? (
              <Form.Label>{errors.strTicker}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik02">
            <Form.Label>Number of Shares</Form.Label>
            <Form.Control
              type="text"
              name="numShares"
              placeholder="Shares"
              value={values.numShares}
              onChange={handleChange}
              isValid={touched.numShares && !errors.numShares}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.numShares && errors.numShares ? (
              <Form.Label>{errors.numShares}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik03">
            <Form.Label>Price per Share</Form.Label>
            <Form.Control
              type="text"
              name="numCostBasis"
              placeholder="Cost Basis"
              value={values.numCostBasis}
              onChange={handleChange}
              isValid={touched.numCostBasis && !errors.numCostBasis}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.numCostBasis && errors.numCostBasis ? (
              <Form.Label>{errors.numCostBasis}</Form.Label>
            ) : null}
          </Form.Group>

          <Form.Group as={Col} md="4" controlId="validationFormik04">
            <Form.Label>Buy Date</Form.Label>
            <Form.Control
              type="date"
              name="dtmBuy"
              placeholder="Buy Date"
              value={values.dtmBuy}
              onChange={handleChange}
              isValid={touched.dtmBuy && !errors.dtmBuy}
            />
            <Form.Control.Feedback>Looks good!</Form.Control.Feedback>
            {touched.dtmBuy && errors.dtmBuy ? (
              <Form.Label>{errors.dtmBuy}</Form.Label>
            ) : null}
          </Form.Group>

          <Button type="submit">Submit Purchase</Button>
        </Form>
      )}
    </Formik>
  );
}

export default AssetPost;