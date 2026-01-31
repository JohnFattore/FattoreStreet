import { Form, Col, Row, Alert } from 'react-bootstrap';
import { useForm, SubmitHandler } from 'react-hook-form';
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { postChatbot } from '../functions/axiosFunctions';
import { useDispatch, useSelector } from "react-redux";
import { AppDispatch, RootState } from '../main';
import LoadingButton from './LoadingButton';

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
    const { register, handleSubmit, reset, formState: { errors }, } = useForm<IFormInput>({
        resolver: yupResolver(schema)
    })

    const onSubmit: SubmitHandler<IFormInput> = (data) => {
        dispatch(postChatbot(data.message))
        reset();
    }

    return (
        <>
            <Form onSubmit={handleSubmit(onSubmit)}>
                <Row>
                    <Col sm={9}>
                        <Form.Control size="lg" {...register("message", {
                            required: true
                        })} placeholder='Start by typing here...' />
                        {errors.message && <Alert variant='danger'>This field is required</Alert>}

                    </Col>
                </Row>
                <LoadingButton label={"Ask Chatbot"} loading={loading} />
            </Form>
            {error && <Alert variant="danger">{error}</Alert>}
        </>
    );
}