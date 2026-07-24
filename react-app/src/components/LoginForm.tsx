import { Form, Button, Alert } from "react-bootstrap";
import { useForm, SubmitHandler } from "react-hook-form";
import { useSelector } from "react-redux";
import { RootState } from "../main";
import {
  translateError,
  getApiErrorMessages,
} from "../functions/helperFunctions";
import { useLoginMutation } from "../functions/api";

interface IFormInput {
  username: string;
  password: string;
}

export default function LoginForm() {
  const { access } = useSelector((state: RootState) => state.user);
  const [login, { error, isLoading }] = useLoginMutation();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<IFormInput>();
  const onSubmit: SubmitHandler<IFormInput> = (data) => {
    login({ username: data.username, password: data.password });
  };

  if (access) {
    return null;
  }

  return (
    <div className="login-form-container">
      {error ? (
        <Alert variant="danger">
          {translateError(getApiErrorMessages(error)[0])}
        </Alert>
      ) : null}
      {isLoading && <Alert variant="info">Login Loading...</Alert>}
      <Form onSubmit={handleSubmit(onSubmit)}>
        <Form.Group>
          <Form.Label>Username</Form.Label>
          <Form.Control
            {...register("username", { required: true })}
            placeholder="Enter username"
          />
          {errors.username && (
            <Alert variant="danger" role="usernameError">
              Username is required
            </Alert>
          )}
        </Form.Group>

        <Form.Group>
          <Form.Label>Password</Form.Label>
          <Form.Control
            type="password"
            {...register("password", { required: true })}
            placeholder="Enter password"
          />
          {errors.password && (
            <Alert variant="danger" role="passwordError">
              Password is required
            </Alert>
          )}
        </Form.Group>

        <div>
          <Button variant="primary" type="submit" disabled={isLoading}>
            {isLoading ? "Logging in..." : "Login"}
          </Button>
        </div>
      </Form>
    </div>
  );
}
