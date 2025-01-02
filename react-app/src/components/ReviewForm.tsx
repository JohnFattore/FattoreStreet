import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postReview } from './axiosFunctions';

interface IFormInput {
    restaurant: number,
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

export default function ReviewForm({ setMessage }) {
    const schema = yup.object().shape({
        restaurant: yup.number().required(),
        rating: yup.number().required(),
        comment: yup.string().required(),
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })
    //console.log(watch("username"))
    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        postReview(1, data.rating, data.comment)
            .then(() => {
                setMessage({ text: "Review Submitted", type: "success" });
            }).catch(() => {
                setMessage({ text: "Error submitting review", type: "error" });
            });
    }

    return (
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
                <Col sm={3}>
                    <Form.Control size="lg" {...register("comment", {
                        required: true
                    })} placeholder='Comment' />
                    {errors.comment && <Alert variant='danger' role="commentError">This field is required</Alert>}

                </Col>
            </Row>
            <Button type="submit">Submit Review</Button>
        </Form>
    );
}