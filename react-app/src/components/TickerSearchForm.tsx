import { useState, type FormEvent } from "react";
import { Form } from "react-bootstrap";
import Alert from "react-bootstrap/Alert";
import { useNavigate } from "react-router-dom";
import LoadingButton from "./LoadingButton";
import { SecuritySearch } from "./SecuritySearch";

export default function TickerSearchForm() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [showError, setShowError] = useState(false);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ticker = query.trim().toUpperCase();
    if (!ticker) {
      setShowError(true);
      return;
    }
    navigate(`/asset/${ticker}`);
  }

  return (
    <>
      <h3>Search Ticker</h3>
      <Form onSubmit={handleSubmit}>
        <SecuritySearch
          value={query}
          onChange={(value) => {
            setQuery(value);
            setShowError(false);
          }}
          onSelect={(result) => navigate(`/asset/${result.ticker}`)}
          ariaLabel="Ticker"
          placeholder="Ticker or company name"
        />
        {showError && (
          <Alert variant="danger" role="tickerError">
            Error: Ticker text field is required
          </Alert>
        )}
        <LoadingButton label={"Search Ticker"} loading={false} />
      </Form>
    </>
  );
}
