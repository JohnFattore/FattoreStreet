import { Button, Spinner } from "react-bootstrap";
export default function LoadingButton({
  label,
  loading,
}: {
  label: string;
  loading: boolean;
}) {
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
          />
          Loading...
        </>
      ) : (
        label
      )}
    </Button>
  );
}
