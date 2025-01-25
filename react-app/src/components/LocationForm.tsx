import { Form, Button, Col, Row } from 'react-bootstrap';
import Alert from 'react-bootstrap/Alert';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from '@hookform/resolvers/yup';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { setLocation } from '../reducers/locationReducer';
import { getRestaurants } from './axiosFunctions';

const STATE_CHOICES = [
    { value: "AZ", label: "Arizona" },
    { value: "CA", label: "California" },
    { value: "CO", label: "Colorado" },
    { value: "DE", label: "Delaware" },
    { value: "FL", label: "Florida" },
    { value: "HI", label: "Hawaii" },
    { value: "ID", label: "Idaho" },
    { value: "IL", label: "Illinois" },
    { value: "IN", label: "Indiana" },
    { value: "LA", label: "Louisiana" },
    { value: "MO", label: "Missouri" },
    { value: "MT", label: "Montana" },
    { value: "NV", label: "Nevada" },
    { value: "NJ", label: "New Jersey" },
    { value: "NC", label: "North Carolina" },
    { value: "PA", label: "Pennsylvania" },
    { value: "TN", label: "Tennessee" },
];

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
                    <Form.Select
                        size="lg"
                        {...register("state", { required: true })}
                        aria-label="Rating"
                    >
                        <option value="" disabled>
                            Select a rating
                        </option>
                        {STATE_CHOICES.map(({ value, label }) => (
                            <option key={value} value={value}>
                                {label}
                            </option>
                        ))}
                        {errors.state && <Alert variant="danger">Error: State field is required</Alert>}
                    </Form.Select>
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