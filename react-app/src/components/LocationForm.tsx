import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { setLocation } from '../reducers/locationReducer';
import { getRestaurants } from './axiosFunctions';

interface IFormInput {
    state: string,
    city: string,
}

export default function LocationForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { loading } = useSelector((state: RootState) => state.restaurants);

    // yup default .date() format does not work with DRF asset api endpoint
    const schema = yup.object().shape({
        state: yup.string().required().uppercase(),
        city: yup.string().required(),
    });

    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema),
    })
    //console.log(watch("ticker"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(setLocation({ state: data.state, city: data.city }))
        dispatch(getRestaurants())
    }

    return (
        <Form onSubmit={handleSubmit(onSubmit)}>
            <Row>
                <Col>
                    <Form.Control size="lg" {...register("state", {
                        required: true
                    })} placeholder='State' />
                    {errors.state && <Alert variant="danger">Error: State field is required</Alert>}

                </Col>
                <Col>
                    <Form.Control size="lg" {...register("city", {
                        required: true
                    })} placeholder='City' />
                    {errors.city && <Alert variant="danger">Error: City field is required</Alert>}
                </Col>
            </Row>
            <Button type="submit" disabled={loading}>Update Location</Button>
        </Form>
    );
}