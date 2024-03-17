import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { getQuote, postAsset } from './axiosFunctions';

interface IFormInput {
    ticker: string,
    shares: number,
    costBasis: number,
    buyDate: string
}

export default function AssetForm({ setMessage, dispatch }) {
    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        ticker: yup.string().required().uppercase(),
        shares: yup.number().required().positive(),
        costBasis: yup.number().required().positive(),
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
                    setMessage({text: "Invalid Ticker", type: "error"})
                else {
                    postAsset({
                        ticker: data.ticker,
                        shares: data.shares,
                        costbasis: data.costBasis,
                        buy: data.buyDate,
                        // just a place holder
                        id: 1
                    })
                        .then((response) => {
                            setMessage({
                                text: data.shares + " shares of " + data.ticker + " bought for " + data.costBasis + " each on " + data.buyDate,
                                type: "success"
                            })
                            const asset = {
                                ticker: data.ticker,
                                shares: data.shares,
                                costbasis: data.costBasis,
                                buy: data.buyDate,
                                id: response.data.id
                            }
                            dispatch({type: "add", asset: asset})
                        })
                        .catch(() => {
                            setMessage({ text: "There was a problem with the asset purchase", type: "error" })
                        });
                }
            });

    }
    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("ticker", {
                        required: true
                    })} placeholder='Ticker' />
                    {errors.ticker && <Alert variant="danger" role="tickerError">Error: Ticker text field is required</Alert>}

                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("shares", {
                        required: true
                    })} placeholder='Shares' />
                    {errors.shares && <Alert variant="danger" role="sharesError">Error: Shares number field is required</Alert>}
                </Col>
            </Row>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("costBasis", {
                        required: true
                    })} placeholder='Cost Basis' />
                    {errors.costBasis && <Alert variant="danger" role="costBasisError">Error: Cost Basis number field is required</Alert>}
                </Col>
                <Col sm={3}>
                    <Form.Control
                        type="date"
                        size="lg"
                        {...register("buyDate", {
                            required: true
                        })} placeholder='Buy Date' />
                    {errors.buyDate && <Alert variant="danger" role="buyDateError">Error: Buy date field is required</Alert>}
                </Col>
            </Row>
            <Button type="submit">Add to Portfolio</Button>
        </Form>
    );
}