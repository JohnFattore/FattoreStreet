import { Form, Button, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postChatbot } from './axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';

interface IFormInput {
    message: string
}

export default function ChatbotForm() {
    const dispatch = useDispatch<AppDispatch>();
    const { loading, error } = useSelector((state: RootState) => state.chatbot);

    const schema = yup.object().shape({
        message: yup.string().required()
    });

    //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
    const { register, handleSubmit, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })

    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(postChatbot(data.message))
    }

    return (
        <>
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col sm={9}>
                        <Form.Control size="lg" {...register("message", {
                            required: true
                        })} placeholder='message' />
                        {errors.message && <Alert variant='danger'>This field is required</Alert>}

                    </Col>
                </Row>
                <Button type="submit" disabled={loading}>Ask Chatbot</Button>
            </Form>
            {error && <Alert variant="danger">{error}</Alert>}
        </>
    );
}