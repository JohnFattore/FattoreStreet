import { Form, Col, Row, Alert } from "react-bootstrap";
import { useForm, SubmitHandler } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { getApiErrorMessages } from "../functions/helperFunctions";
import { usePostChatbotMutation } from "../functions/api";
import LoadingButton from "./LoadingButton";

interface IFormInput {
  message: string;
}

export default function ChatbotForm() {
  const [postChatbot, { error, isLoading }] = usePostChatbotMutation();

  const schema = yup.object().shape({
    message: yup.string().required(),
  });

  //useForm is fantastic for handling form state, functions such as onSubmit/onChange/onBlur, validation, and even flexibility for other UI libraries (using Controller)
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<IFormInput>({
    resolver: yupResolver(schema),
  });

  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    postChatbot(data.message);
    reset();
  };

  return (
    <>
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Row>
          <Col sm={9}>
            <Form.Control
              size="lg"
              {...register("message", {
                required: true,
              })}
              placeholder="Start by typing here..."
            />
            {errors.message && (
              <Alert variant="danger">This field is required</Alert>
            )}
          </Col>
        </Row>
        <LoadingButton label={"Ask Chatbot"} loading={isLoading} />
      </Form>
      {error ? (
        <Alert variant="danger">{getApiErrorMessages(error)[0]}</Alert>
      ) : null}
    </>
  );
}
