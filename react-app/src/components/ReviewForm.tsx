import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postReview } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import { useState } from 'react';

interface IFormInput {
    rating: number,
    comment: string
}

const RATING_CHOICES = [
    { value: 1, label: "1 - Poor" },
    { value: 1.5, label: "1.5 - Subpar" },
    { value: 2, label: "2 - Fair" },
    { value: 2.5, label: "2.5 - Almost Good" },
    { value: 3, label: "3 - Good" },
    { value: 3.5, label: "3.5 - Decent" },
    { value: 4, label: "4 - Very Good" },
    { value: 4.5, label: "4.5 - Excellent-ish" },
    { value: 5, label: "5 - Excellent" },
];

export default function ReviewForm({ restaurant }) {
    const dispatch = useDispatch<AppDispatch>();
    const { loading, error } = useSelector((state: RootState) => state.reviews);
    const [submission, setSubmission] = useState()

    const schema = yup.object().shape({
        rating: yup.number().required(),
        comment: yup.string().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(postReview({
            restaurant: restaurant.id,
            name: '',
            user: 1,
            rating: data.rating,
            comment: data.comment,
            longitude: 1,
            latitude: 1,
            id: 1
        }))
        setSubmission(restaurant.name)
    }

    return (
        <>
            <h3>{restaurant.id == 0 ? "Select Restaurant to Review" : "Review for " + restaurant.name}</h3>
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col sm={3}>
                        <Form.Select
                            size="lg"
                            {...register("rating", { required: true })}
                            aria-label="Rating"
                        >
                            <option value="" disabled>
                                Select a rating
                            </option>
                            {RATING_CHOICES.map(({ value, label }) => (
                                <option key={value} value={value}>
                                    {label}
                                </option>
                            ))}
                        </Form.Select>
                        {errors.comment && <Alert variant='danger' role="commentError">This field is required</Alert>}
                    </Col>
                    <Col sm={9}>
                        <Form.Control size="lg" {...register("comment", {
                            required: true
                        })} placeholder='Comment' />
                        {errors.comment && <Alert variant='danger' role="commentError">This field is required</Alert>}

                    </Col>
                </Row>
                <Button type="submit" disabled={restaurant.id == 0 || loading}>Submit Review</Button>
            </Form>
            {error && <Alert variant="danger">{error}</Alert>}
            {submission && error != 'You have already reviewed this restaurant.' && <Alert>{"Review for ".concat(submission, " submitted")}</Alert>}
        </>

    );
}