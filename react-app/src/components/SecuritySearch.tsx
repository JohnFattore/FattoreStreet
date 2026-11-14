import { useId, useState, type KeyboardEvent } from "react";
import Form from "react-bootstrap/Form";
import ListGroup from "react-bootstrap/ListGroup";
import Badge from "react-bootstrap/Badge";
import { useSearchListingsQuery } from "../functions/api";
import { useDebouncedValue } from "../functions/customHooks";
import type { ISecuritySearchResult } from "../interfaces";

const DEBOUNCE_MS = 200;

type SecuritySearchProps = {
  value: string;
  onChange: (value: string) => void;
  onSelect: (result: ISecuritySearchResult) => void;
  ariaLabel: string;
  placeholder?: string;
  // false limits matches to operating companies, for pages that can't load a fund
  includeFunds?: boolean;
  limit?: number;
};

function securityLabel({ name, ticker }: ISecuritySearchResult) {
  return name ? `${name} (${ticker})` : ticker;
}

// A text box that suggests companies and funds by name or ticker as the user
// types (ARIA combobox pattern). Arrow keys move through the matches, Enter or
// a click picks one, Escape closes the list. When no match is highlighted,
// Enter falls through to the enclosing form, so a typed ticker still submits.
export function SecuritySearch({
  value,
  onChange,
  onSelect,
  ariaLabel,
  placeholder = "Search company or ticker",
  includeFunds = true,
  limit,
}: SecuritySearchProps) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const query = value.trim();
  const debouncedQuery = useDebouncedValue(query, DEBOUNCE_MS);
  // currentData, not data: data would keep showing the previous query's
  // matches while the next one loads, and Enter could pick a stale match
  const { currentData } = useSearchListingsQuery(
    { q: debouncedQuery, limit, includeFunds },
    { skip: !debouncedQuery },
  );
  // Matches are only offered once they belong to what is in the box now
  const results = debouncedQuery === query ? (currentData ?? []) : [];
  const showList = open && results.length > 0;

  function pick(result: ISecuritySearchResult) {
    setOpen(false);
    setActiveIndex(-1);
    onSelect(result);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" && results.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((i) => (i + 1) % results.length);
    } else if (event.key === "ArrowUp" && results.length > 0) {
      event.preventDefault();
      setOpen(true);
      setActiveIndex((i) => (i <= 0 ? results.length - 1 : i - 1));
    } else if (event.key === "Enter" && showList) {
      // The best match is first (an exact ticker always is), so Enter
      // without arrowing picks it
      event.preventDefault();
      pick(results[Math.max(activeIndex, 0)]);
    } else if (event.key === "Escape") {
      setOpen(false);
      setActiveIndex(-1);
    }
  }

  const activeId =
    showList && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined;

  return (
    <div className="position-relative">
      <Form.Control
        type="search"
        role="combobox"
        autoComplete="off"
        aria-label={ariaLabel}
        aria-autocomplete="list"
        aria-expanded={showList}
        aria-controls={listId}
        aria-activedescendant={activeId}
        placeholder={placeholder}
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
          setOpen(true);
          setActiveIndex(-1);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => {
          // Forget the highlight too: refocusing reopens the list, and a
          // stale highlight would make Enter pick a match the user can't see
          // they chose
          setOpen(false);
          setActiveIndex(-1);
        }}
        onKeyDown={handleKeyDown}
      />
      {showList && (
        <ListGroup
          id={listId}
          role="listbox"
          aria-label={`${ariaLabel} suggestions`}
          className="position-absolute w-100 shadow z-3"
        >
          {results.map((result, index) => (
            <ListGroup.Item
              key={result.ticker}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === activeIndex}
              active={index === activeIndex}
              action
              as="div"
              // mousedown would blur the input and close the list before
              // the click lands
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => pick(result)}
            >
              {securityLabel(result)}
              {result.isFund && (
                <Badge bg="secondary" className="ms-2">
                  Fund
                </Badge>
              )}
            </ListGroup.Item>
          ))}
        </ListGroup>
      )}
    </div>
  );
}
