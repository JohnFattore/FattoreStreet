import Button from 'react-bootstrap/Button';
import Form from 'react-bootstrap/Form';
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { getQuote } from './axiosFunctions';
import { Alert } from 'react-bootstrap';
import { addTicker, errorTicker } from '../reducers/watchListReducer';
import { RootState, AppDispatch } from '../main';
import { useSelector, useDispatch } from 'react-redux';

interface IFormInput {
  ticker: string
}

export default function WatchListForm() {
  const { tickers } = useSelector((state: RootState) => state.watchList);
  const dispatch = useDispatch<AppDispatch>();

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
          dispatch(errorTicker("Couldn't retrieve data for ticker"));
        else {
          // not let duplicates to be added to list
          if (tickers.includes(data.ticker)) {
            dispatch(errorTicker("Ticker already on watchlist"));
          }
          else {
            dispatch(addTicker(data.ticker));
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