import * as formik from 'formik';
import * as Yup from 'yup';
import Button from 'react-bootstrap/Button';
import Col from 'react-bootstrap/Col';
import Form from 'react-bootstrap/Form';

// Django RESTFUL API asset base url


function LoginForm() {
  const { Formik } = formik;
  const schema = Yup.object().shape({
    strTicker: Yup.string()
      .required('Required'),
  });

  return (
    <Formik
      initialValues={{ strTicker: '' }}
      validationSchema={schema}
      // on submit, post to server with values in the form
      onSubmit={(values) => {
        let strDBTickers = localStorage.getItem("listTickers");
        let listTickers: string[] = JSON.parse(strDBTickers as string);
        listTickers.push(values.strTicker);
        strDBTickers = JSON.stringify(listTickers);
        localStorage.setItem("listTickers", strDBTickers)
      }}
    >
      {({ handleSubmit, handleChange, values, touched, errors }) => (
        <Form noValidate onSubmit={handleSubmit}>
          <Form.Group as={Col} md="4" controlId="validationFormik01">
            <Form.Label>Add Ticker to Watchlist</Form.Label>
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
          <Button type="submit">Add to WatchList</Button>
        </Form>
      )}
    </Formik>
  );
}

export default LoginForm;