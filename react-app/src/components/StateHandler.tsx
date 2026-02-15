import { Spinner, Alert } from "react-bootstrap";
import { getApiErrorMessages } from "../functions/helperFunctions";

type CardProps = {
  isLoading?: boolean;
  errors?: unknown[],
  content: React.ReactNode;
};

export default function StateHandler({ isLoading = false, errors = [], content }: CardProps) {
  if (isLoading) {
    return (
      <Spinner animation="border" />
    )
  }
  for (const error of errors) {
    if (error) {
      return (<Alert variant="danger">{getApiErrorMessages(error)}</Alert>)
    }
  }

  return content
}
