import { Form, Col, Row } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "./LoginForm";
import { usePostNewAssetMutation, useGetAccountsQuery } from "../functions/api";
import { getErrorMessages } from "../functions/helperFunctions";
import LoadingButton from "./LoadingButton";
import { useEffect } from "react";

interface IFormInput {
  ticker: string;
  shares: number;
  buyDate: string;
  accountId?: number | null;
}

interface AssetFormProps {
  defaultAccountId?: number;
}

export default function AssetForm({ defaultAccountId }: AssetFormProps) {
  const [postNewAsset, { error, isLoading }] = usePostNewAssetMutation();
  const { access } = useSelector((state: RootState) => state.user);
  const { data: accounts } = useGetAccountsQuery(undefined, { skip: !access });

  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase(),
    shares: yup.number().required().positive(),
    buyDate: yup.string().required(),
    accountId: yup.number().optional().nullable().transform((value, originalValue) => originalValue === "" ? null : value),
  });
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
    defaultValues: {
      accountId: defaultAccountId
    }
  });

  // Update form value if defaultAccountId changes
  useEffect(() => {
    if (defaultAccountId) {
      setValue("accountId", defaultAccountId);
    }
  }, [defaultAccountId, setValue]);

  //console.log(watch("ticker"))
  const onSubmit: SubmitHandler<IFormInput> = async (data) => {
    await postNewAsset({
      ticker: data.ticker,
      shares: data.shares,
      buy_date: data.buyDate,
      account_id: data.accountId ? Number(data.accountId) : null,
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
          <Col>
            <Form.Select
              size="lg"
              {...register("accountId")}
              disabled={!!defaultAccountId}
            >
              <option value="">Select Account (Optional)</option>
              {accounts?.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </Form.Select>
          </Col>
        </Row>
        <LoadingButton label={"Add to Portfolio"} loading={isLoading} />
      </Form>
    </>
  );
}
