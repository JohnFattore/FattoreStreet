import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { getQuote, postAsset } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { errorAssets } from '../reducers/assetReducer';
import LoginForm from './LoginForm';

interface IFormInput {
    ticker: string,
    shares: number,
    buyDate: string
}

export default function AssetForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { loading } = useSelector((state: RootState) => state.assets);
    const { access } = useSelector((state: RootState) => state.user)

    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        ticker: yup.string().required().uppercase(),
        shares: yup.number().required().positive(),
        buyDate: yup.string().required()
    });

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    //console.log(watch("ticker"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        getQuote(data.ticker)
            .then((response) => {
                // a valid ticker wont return null values
                if (response.data.d == null)
                    dispatch(errorAssets("Invalid Ticker"))
                else {
                    dispatch(
                        postAsset({
                            // 1s are placeholders, nice to include those so IAsset interface can be used
                            ticker: data.ticker,
                            shares: data.shares,
                            buyDate: data.buyDate,
                            costBasis: 1,
                            totalCostBasis: 1,
                            currentPrice: 1,
                            percentChange: 1,
                            SnP500Price: 1,
                            SnP500PercentChange: 1,
                            id: 1
                        }));
                }
            });
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
        <>
            <h3>Add Assets</h3>
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col>
                        <Form.Control size="lg" {...register("ticker", {
                            required: true
                        })} placeholder='Ticker' />
                        {errors.ticker && <Alert variant="danger" role="tickerError">Error: Ticker text field is required</Alert>}

                    </Col>
                    <Col>
                        <Form.Control size="lg" {...register("shares", {
                            required: true
                        })} placeholder='Shares' />
                        {errors.shares && <Alert variant="danger" role="sharesError">Error: Shares number field is required</Alert>}
                    </Col>
                </Row>
                <Row>
                    <Col>
                        <Form.Control
                            type="date"
                            size="lg"
                            {...register("buyDate", {
                                required: true
                            })} placeholder='Buy Date' />
                        {errors.buyDate && <Alert variant="danger" role="buyDateError">Error: Buy date field is required</Alert>}
                    </Col>
                </Row>
                <Button type="submit" disabled={loading}>Add to Portfolio</Button>
            </Form>
        </>
    );
}