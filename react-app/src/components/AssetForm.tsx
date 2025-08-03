import { Form, Button, Col, Row, Spinner } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "./LoginForm";
import { usePostNewAssetMutation } from "../functions/api";
import { getErrorMessages } from "../functions/helperFunctions";

interface IFormInput {
  ticker: string;
  shares: number;
  buyDate: string;
}

export default function AssetForm() {
  const [postNewAsset, { error, isLoading }] = usePostNewAssetMutation();
  const { access } = useSelector((state: RootState) => state.user);

  // yup default .date() format does not work with DRF asset api endpoint
  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase(),
    shares: yup.number().required().positive(),
    buyDate: yup.string().required(),
  });
  console.log(error);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  });
  //console.log(watch("ticker"))
  const onSubmit: SubmitHandler<IFormInput> = async (data) => {
    await postNewAsset({
      ticker: data.ticker,
      shares: data.shares,
      buy_date: data.buyDate,
    });
  };

  if (!access) {
    return (
      <>
        <Alert>Login to see portfolio</Alert>
        <LoginForm />
      </>
    );
  }

  return (
    <>
      <h3>Add Assets</h3>
      {error ? (
        <Alert variant="danger">{getErrorMessages(error["data"])}</Alert>
      ) : null}
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Row>
          <Col>
            <Form.Control
              size="lg"
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
          </Col>
          <Col>
            <Form.Control
              size="lg"
              {...register("shares", {
                required: true,
              })}
              placeholder="Shares"
            />
            {errors.shares && (
              <Alert variant="danger" role="sharesError">
                Error: Shares number field is required
              </Alert>
            )}
          </Col>
        </Row>
        <Row>
          <Col>
            <Form.Control
              type="date"
              size="lg"
              {...register("buyDate", {
                required: true,
              })}
              placeholder="Buy Date"
            />
            {errors.buyDate && (
              <Alert variant="danger" role="buyDateError">
                Error: Buy date field is required
              </Alert>
            )}
          </Col>
        </Row>
        <Button type="submit" disabled={isLoading}>
          {isLoading ? (
            <>
              <Spinner
                as="span"
                animation="grow"
                size="sm"
                role="status"
                aria-hidden="true"
                className="me-2"
              />
              Loading...
            </>
          ) : (
            "Add to Portfolio"
          )}
        </Button>
      </Form>
    </>
  );
}
