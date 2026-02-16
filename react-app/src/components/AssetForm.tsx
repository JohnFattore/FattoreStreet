import { Form, Col, Row, Button, Modal } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "./LoginForm";
import { usePostNewAssetMutation, useGetAccountsQuery, useLazyGetAssetInfosQuery } from "../functions/api";
import { getApiErrorMessages, formatString } from "../functions/helperFunctions";
import LoadingButton from "./LoadingButton";
import { useEffect, useState } from "react";

interface IFormInput {
  ticker: string;
  shares: number;
  buyDate: string;
  accountId: number;
}

interface AssetFormProps {
  defaultAccountId?: number;
  defaultTicker?: string;
}

export default function AssetForm({ defaultAccountId, defaultTicker }: AssetFormProps) {
  const [postNewAsset, { error, isLoading }] = usePostNewAssetMutation();
  const { access } = useSelector((state: RootState) => state.user);
  const { data: accounts } = useGetAccountsQuery(undefined, { skip: !access });
  const [show, setShow] = useState(false);
  const [success, setSuccess] = useState<string | null>(null);
  const [isProcessing, setIsProcessing] = useState(false);
  const [triggerAssetInfo] = useLazyGetAssetInfosQuery();

  const handleClose = () => {
    setShow(false);
    setSuccess(null);
    reset();
  };
  const handleShow = () => setShow(true);

  const schema = yup.object().shape({
    ticker: yup.string().required().uppercase(),
    shares: yup.number().required().positive(),
    buyDate: yup.string().required(),
    accountId: yup.number().required("Account is required").transform((value, originalValue) => originalValue === "" ? undefined : value),
  });
  const {
    register,
    handleSubmit,
    setValue,
    reset,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
    defaultValues: {
      accountId: defaultAccountId,
      ticker: defaultTicker
    }
  });

  // Update form values if defaults change
  useEffect(() => {
    if (defaultAccountId) {
      setValue("accountId", defaultAccountId);
    }
    if (defaultTicker) {
      setValue("ticker", defaultTicker);
    }
  }, [defaultAccountId, defaultTicker, setValue]);

  //console.log(watch("ticker"))
  const onSubmit: SubmitHandler<IFormInput> = async (data) => {
    setIsProcessing(true);
    try {
      const result: any = await postNewAsset({
        ticker: data.ticker,
        shares: data.shares,
        buy_date: data.buyDate,
        account_id: Number(data.accountId),
      });

      if ('data' in result) {
        const assetData = result.data;
        const infoResult = await triggerAssetInfo([data.ticker]);
        const info = infoResult.data ? infoResult.data[data.ticker] : null;
        const companyName = info?.shortName || data.ticker;

        const message = `Successfully added ${companyName} (${data.ticker}) - ${data.shares} shares at ${formatString(assetData.buy_price, "money")} on ${data.buyDate}`;
        setSuccess(message);
        reset();
      }
    } finally {
      setIsProcessing(false);
    }
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
      <Button variant="primary" onClick={handleShow} className="mb-3">
        Add Asset
      </Button>

      <Modal show={show} onHide={handleClose} dialogClassName="asset-form-modal">
        <Modal.Header closeButton>
          <Modal.Title>Add Asset</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {success && <Alert variant="success">{success}</Alert>}
          {error ? (
            <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
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
                  disabled={!!defaultTicker}
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
                  <option value="">Select Account</option>
                  {accounts?.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))}
                </Form.Select>
                {errors.accountId && (
                  <Alert variant="danger" role="accountIdError">
                    Error: An account must be selected
                  </Alert>
                )}
              </Col>
            </Row>
            <div>
              <Button variant="secondary" onClick={handleClose}>
                Close
              </Button>
              <LoadingButton label={"Add to Portfolio"} loading={isLoading || isProcessing} />
            </div>
          </Form>
        </Modal.Body>
      </Modal>
    </>
  );
}
