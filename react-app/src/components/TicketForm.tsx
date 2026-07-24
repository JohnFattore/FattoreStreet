import { Alert, Button, Form } from "react-bootstrap";
import { SubmitHandler, useForm } from "react-hook-form";
import * as yup from "yup";
import { yupResolver } from "@hookform/resolvers/yup";
import { usePostTicketMutation } from "../functions/api";
import { getApiErrorMessages } from "../functions/helperFunctions";
import LoadingButton from "./LoadingButton";

interface ITicketFormInput {
  title: string;
  description: string;
}

const schema = yup.object({
  title: yup
    .string()
    .trim()
    .required("Title is required")
    .max(255, "Title must be 255 characters or less"),
  description: yup.string().trim().required("Description is required"),
});

export default function TicketForm() {
  const [postTicket, { error, isLoading }] = usePostTicketMutation();
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitSuccessful },
  } = useForm<ITicketFormInput>({
    resolver: yupResolver(schema),
    defaultValues: {
      title: "",
      description: "",
    },
  });

  const onSubmit: SubmitHandler<ITicketFormInput> = async (data) => {
    await postTicket(data).unwrap();
    reset();
  };

  return (
    <div>
      <h5>Submit Product Feedback</h5>
      <p className="text-muted mb-3">
        Share bugs, feature requests, or workflow issues with the team.
      </p>
      {isSubmitSuccessful && !error ? (
        <Alert variant="success">Thanks, your ticket was submitted.</Alert>
      ) : null}
      {error ? (
        <Alert variant="danger">{getApiErrorMessages(error).join(" ")}</Alert>
      ) : null}
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Form.Group className="mb-3" controlId="ticketTitle">
          <Form.Label>Title</Form.Label>
          <Form.Control
            type="text"
            placeholder="Short summary"
            {...register("title")}
          />
          {errors.title ? (
            <Alert variant="danger" className="mt-2 mb-0">
              {errors.title.message}
            </Alert>
          ) : null}
        </Form.Group>

        <Form.Group className="mb-3" controlId="ticketDescription">
          <Form.Label>Description</Form.Label>
          <Form.Control
            as="textarea"
            rows={4}
            placeholder="What happened, what you expected, and any helpful context"
            {...register("description")}
          />
          {errors.description ? (
            <Alert variant="danger" className="mt-2 mb-0">
              {errors.description.message}
            </Alert>
          ) : null}
        </Form.Group>

        <div className="d-flex gap-2">
          <Button
            variant="secondary"
            type="button"
            onClick={() => reset()}
            disabled={isLoading}
          >
            Reset
          </Button>
          <LoadingButton label="Submit Ticket" loading={isLoading} />
        </div>
      </Form>
    </div>
  );
}
