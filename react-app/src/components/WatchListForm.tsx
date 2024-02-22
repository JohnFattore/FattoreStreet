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

export default function WatchListForm({ setChange }) {
  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase()
  });

  //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
  const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  })
  // on submit, add ticker if proper
  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    getQuote(data.ticker)
      .then((response) => {
        // a valid ticker wont return null values
        if (response.data.d == null)
          alert("Ticker isnt valid");
        else {
          let tickersDB = localStorage.getItem("tickers");
          let tickers: string[] = JSON.parse(tickersDB as string);
          // not let duplicates to be added to list
          if (tickers.includes(data.ticker)) {
            alert("Ticker already in watchlist")
          }
          // TODO: clean up tickers.txt, loop through it 
          // for (let i = 0; ticker.txt.length > i; i++) { if (ticker in tickers.txt == ticker submitted by form){ do the regular ticker adding logic }  }
          else {
            tickers.push(data.ticker);
            tickersDB = JSON.stringify(tickers);
            localStorage.setItem("tickers", tickersDB)
            setChange(true)
          }
        }
      });

  }

  //console.log(watch("ticker"));

  return (
    <Form onSubmit={handleSubmit(onSubmit)}>
      <Form.Label>Ticker</Form.Label>
      <Form.Control {...register("ticker")} placeholder='Enter Ticker Here' />
      <Alert variant='danger'>{errors.ticker?.message}</Alert>
      <Button type="submit" >Add to Watchlist</Button>
    </Form>
  );
}
//,{ required: true }