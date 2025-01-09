import { Form, Button, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { getSnP500Price } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';

interface IFormInput {
    date: string,
}

export default function AssetForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { loading } = useSelector((state: RootState) => state.assets);

    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        date: yup.string().required()
    });

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    //console.log(watch("ticker"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(
            getSnP500Price(data.date));
    }

    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Form.Control 
                type="date"
                size="lg" 
                {...register("date", {
                    required: true
                })} placeholder='Date' />
                {errors.date && <Alert variant="danger">Error: Date field is required</Alert>}
            </Row>
            <Button type="submit" disabled={loading}>Get S&P 500 Price</Button>
        </Form>
    );
}