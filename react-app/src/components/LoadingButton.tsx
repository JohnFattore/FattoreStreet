import { Button, Spinner } from "react-bootstrap";
export default function LoadingButton({label, loading}) {
  return (
    <Button type="submit" disabled={loading}>
      {loading ? (
        <>
          <Spinner
            as="span"
            animation="grow"
            size="sm"
            role="status"
            aria-hidden="true"
            className="me-2"
          />
          Loading...
        </>
      ) : (
        label
      )}
    </Button>
  );
}
