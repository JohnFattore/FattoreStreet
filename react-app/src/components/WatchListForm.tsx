import Form from "react-bootstrap/Form";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { getQuote } from "../functions/axiosFunctions";
import { Alert } from "react-bootstrap";
import { addTicker, clearWatchlistError, errorTicker } from "../reducers/watchListReducer";
import { RootState, AppDispatch } from "../main";
import { useSelector, useDispatch } from "react-redux";
import LoadingButton from "./LoadingButton";
import { getErrorMessages, useIsEndpointLoading } from "../functions/helperFunctions";

interface IFormInput {
  ticker: string;
}

export default function WatchListForm() {
  const { tickers, loading } = useSelector(
    (state: RootState) => state.watchList
  );
  const dispatch = useDispatch<AppDispatch>();
  const isLoading = useIsEndpointLoading("getAssetInfos")

  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase(),
  });

  //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  });
  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    if (tickers.includes(data.ticker)) {
      dispatch(errorTicker("Ticker already on watchlist"));
      return
    } 
      getQuote(data.ticker).then((response) => {
        // a valid ticker wont return null values
        if (response.data.price == null)
          dispatch(errorTicker("Couldn't retrieve data for ticker"));
        else {
          dispatch(addTicker(data.ticker));
          dispatch(clearWatchlistError());
          reset();
        }
      }).catch((error) => {
        dispatch(errorTicker(getErrorMessages(error.response.data)))
      });
  };

  return (
    <>
      <Form onSubmit={handleSubmit(onSubmit)}>
      <Form.Label>Ticker</Form.Label>
      <Form.Control {...register("ticker")} placeholder="Enter Ticker Here" />
      {errors.ticker && (
        <Alert variant="danger" role="tickerError">
          Error: Ticker text field is required
        </Alert>
      )}
    <LoadingButton label={"Add to Watchlist"} loading={loading || isLoading}/>
    </Form>
    </>

  );
}
