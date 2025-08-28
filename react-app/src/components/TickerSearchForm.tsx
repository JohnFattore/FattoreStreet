import { Form } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useNavigate } from "react-router-dom";
import LoadingButton from "./LoadingButton";

interface IFormInput {
  ticker: string;
}

export default function TickerSearchForm() {
  const navigate = useNavigate();

  // yup default .date() format does not work with DRF asset api endpoint
  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase(),
  });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  });
  const onSubmit: SubmitHandler<IFormInput> = async (data) => {
    navigate(`/asset/${data.ticker}`);
  };

  return (
    <>
      <h3>Search Ticker</h3>
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Form.Control
          {...register("ticker", {
            required: true,
          })}
          placeholder="Ticker"
        />
        {errors.ticker && (
          <Alert variant="danger" role="tickerError">
            Error: Ticker text field is required
          </Alert>
        )}
        <LoadingButton label={"Search Ticker"} loading={false} />
      </Form>
    </>
  );
}
