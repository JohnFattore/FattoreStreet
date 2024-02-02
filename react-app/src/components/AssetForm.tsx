import axios from 'axios';
import { Form, Button, Col, Row } from 'react-bootstrap';
import { useContext } from 'react';
import { ENVContext } from './ENVContext';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';

interface IFormInput {
    ticker: string,
    shares: number,
    costBasis: number,
    buyDate: string
}

function AssetForm({ setChange }) {
    const ENV = useContext(ENVContext);

    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        ticker: yup.string().required().uppercase(),
        shares: yup.number().required().positive(),
        costBasis: yup.number().required().positive(),
        buyDate: yup.string().required()
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, watch, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    console.log(watch("ticker"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        axios.get(ENV.finnhubURL.concat("quote/"), {
            params: {
                symbol: data.ticker,
                token: ENV.finnhubKey
            }
        }).then((response) => {
            // a valid ticker wont return null values
            if (response.data.d == null)
                alert("Ticker isnt valid");
            else {
                axios.post(ENV.djangoURL.concat("assets/"), {
                    ticker: data.ticker,
                    shares: data.shares,
                    costbasis: data.costBasis,
                    buy: data.buyDate,
                    // 1 is a placeholder, this is actually set on the back end using the User object returned by the request
                    user: 1
                }, {
                    headers: {
                        'Authorization': ' Bearer '.concat(sessionStorage.getItem('token') as string),
                    }
                }
                ).then(() => {
                    setChange(true)
                    alert(data.shares + " of " + data.ticker + " bought for " + data.costBasis + " each on " + data.buyDate);
                }
                ).catch(() => {
                    alert("There was an error with the purchase")
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
                    {errors.ticker && <p>This field is required</p>}

                </Col>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("shares", {
                        required: true
                    })} placeholder='Shares' />
                    {errors.shares && <p>This field is required</p>}
                </Col>
            </Row>
            <Row>
                <Col sm={3}>
                    <Form.Control size="lg" {...register("costBasis", {
                        required: true
                    })} placeholder='Cost Basis' />
                    {errors.costBasis && <p>This field is required</p>}
                </Col>
                <Col sm={3}>
                    <Form.Control
                        type="date"
                        size="lg"
                        {...register("buyDate", {
                            required: true
                        })} placeholder='Buy Date' />
                    {errors.buyDate && <p>This field is required</p>}
                </Col>
            </Row>

            <Button type="submit">Add to Portfolio</Button>
        </Form>
    );
}

export default AssetForm;