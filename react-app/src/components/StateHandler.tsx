import { Spinner, Alert } from "react-bootstrap";
import { getErrorMessages } from "../functions/helperFunctions";

type CardProps = {
  isLoading?: boolean;
  errors?: any[],
  content: React.ReactNode;
};

// pass/check for access, pass in boolean stating if to show if no access... maybe
export default function StateHandler({ isLoading = false, errors = [], content }: CardProps) {
  if (isLoading) {
    return (
      <Spinner animation="border" />
    )
  }
  for (const error of errors) {
    if (error) {
      return (<Alert variant="danger">{getErrorMessages(error["data"])}</Alert>)
    }
  }

  return content
}
