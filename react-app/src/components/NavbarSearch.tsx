import { useState, type FormEvent } from "react";
import Form from "react-bootstrap/Form";
import { useNavigate } from "react-router-dom";
import { SecuritySearch } from "./SecuritySearch";

// Site-wide search in the top bar: picking a match, or pressing Enter on a
// typed ticker with no match highlighted, opens that security's page
export function NavbarSearch() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  function open(ticker: string) {
    setQuery("");
    navigate(`/asset/${ticker}`);
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const ticker = query.trim().toUpperCase();
    if (ticker) {
      open(ticker);
    }
  }

  return (
    <Form role="search" onSubmit={handleSubmit}>
      <SecuritySearch
        value={query}
        onChange={setQuery}
        onSelect={(result) => open(result.ticker)}
        ariaLabel="Search securities"
      />
    </Form>
  );
}
