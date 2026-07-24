import Form from "react-bootstrap/Form";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { Alert } from "react-bootstrap";
import {
  addTicker,
  clearWatchlistError,
  errorTicker,
} from "../reducers/watchListReducer";
import { RootState, AppDispatch } from "../main";
import { useSelector, useDispatch } from "react-redux";
import LoadingButton from "./LoadingButton";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { useLazyGetQuoteQuery } from "../functions/api";

interface IFormInput {
  ticker: string;
}

type WatchListFormProps = {
  assetInfosLoading: boolean;
};

export default function WatchListForm({
  assetInfosLoading,
}: WatchListFormProps) {
  const { tickers, loading } = useSelector(
    (state: RootState) => state.watchList,
  );
  const dispatch = useDispatch<AppDispatch>();
  const [getQuote] = useLazyGetQuoteQuery();

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
      return;
    }
    getQuote(data.ticker)
      .unwrap()
      .then((quote) => {
        // a valid ticker wont return null values
        if (quote.price == null)
          dispatch(errorTicker("Couldn't retrieve data for ticker"));
        else {
          dispatch(addTicker(data.ticker));
          dispatch(clearWatchlistError());
          reset();
        }
      })
      .catch((error) => {
        dispatch(errorTicker(getApiErrorMessages(error)));
      });
  };

  return (
    <>
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Form.Control {...register("ticker")} placeholder="Enter Ticker Here" />
        {errors.ticker && (
          <Alert variant="danger" role="tickerError">
            Error: Ticker text field is required
          </Alert>
        )}
        <LoadingButton
          label={"Add to Watchlist"}
          loading={loading || assetInfosLoading}
        />
      </Form>
    </>
  );
}
