import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { sellAsset } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import LoginForm from './LoginForm';

interface IFormInput {
    sellDate: string
}

export default function AssetSellForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { asset, loading } = useSelector((state: RootState) => state.assets);
    const { access } = useSelector((state: RootState) => state.user)

    if (!asset) {
        return (null)
    }

    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        sellDate: yup.string().required()
    });

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(sellAsset({ id: asset.id, sellDate: data.sellDate }))
    }

    if (!access) {
        return (
            <>
                <Alert>Login to see portfolio</Alert>
                <LoginForm />
            </>
        )
    }

    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col>
                    <Form.Control
                        type="date"
                        size="lg"
                        {...register("sellDate", {
                            required: true
                        })} placeholder='Sell Date' />
                    {errors.sellDate && <Alert variant="danger">Error: Sell date field is required</Alert>}
                </Col>
            </Row>
            <Button type="submit" disabled={loading}>{`Sell ${asset.ticker}`}</Button>
        </Form>
    );
}