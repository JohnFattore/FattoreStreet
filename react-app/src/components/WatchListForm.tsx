import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { getQuote } from './AxiosFunctions';
import { Alert } from 'react-bootstrap';

interface IFormInput {
  ticker: string
}

export default function WatchListForm({ setMessage, setTickers }) {
  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase()
  });

  //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
  const { register, handleSubmit, reset, formState: { errors }, } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  })
  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    getQuote(data.ticker)
      .then((response) => {
        // a valid ticker wont return null values
        if (response.data.d == null)
          setMessage({text: "Ticker isnt valid", type: "error"});
        else {
          let tickersDB = localStorage.getItem("tickers");
          let allTickers: string[] = JSON.parse(tickersDB as string);
          // not let duplicates to be added to list
          if (allTickers.includes(data.ticker)) {
            setMessage({text: "Ticker already in watchlist", type: "error"})
          }
          else {
            allTickers.push(data.ticker);
            setTickers(allTickers);
            tickersDB = JSON.stringify(allTickers);
            localStorage.setItem("tickers", tickersDB);
            setMessage({ text: data.ticker + " added to watchlist", type: "success"});
            reset();
          }
        }
      });
  }

  return (
    <Form onSubmit={handleSubmit(onSubmit)}>
      <Form.Label>Ticker</Form.Label>
      <Form.Control {...register("ticker")} placeholder='Enter Ticker Here' />
      {errors.ticker && <Alert variant="danger" role="tickerError">Error: Ticker text field is required</Alert>}
      <Button type="submit" >Add to Watchlist</Button>
    </Form>
  );
}