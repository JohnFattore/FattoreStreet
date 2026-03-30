import { Form, Button } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from "./LoginForm";
import { usePatchAssetMutation } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { IAsset } from "../interfaces";

interface IFormInput {
  sellDate: string;
}

// yup default .date() format does not work with DRF asset api endpoint
const sellDateSchema = yup.object().shape({
  sellDate: yup.string().required(),
});

export default function AssetSellForm({ asset }: { asset: IAsset | undefined }) {
  const [patchAsset, { error, isLoading }] = usePatchAssetMutation();
  const { access } = useSelector((state: RootState) => state.user);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(sellDateSchema),
  });

  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    if (!asset) return;
    patchAsset({ id: asset.id, sell_date: data.sellDate });
  };

  if (!asset) {
    return null;
  }

  if (!access) {
    return (
      <>
        <Alert>Login to see portfolio</Alert>
        <LoginForm />
      </>
    );
  }

  return (
    <Form onSubmit={handleSubmit(onSubmit)}>
      {error ? (
        <Alert variant="danger">{getApiErrorMessages(error)}</Alert>
      ) : null}
      <Form.Control
        type="date"
        size="lg"
        {...register("sellDate", {
          required: true,
        })}
        placeholder="Sell Date"
      />
      {errors.sellDate && (
        <Alert variant="danger">Error: Sell date field is required</Alert>
      )}
      <Button
        type="submit"
        disabled={isLoading}
      >{`Sell ${asset.ticker}`}</Button>
    </Form>
  );
}
