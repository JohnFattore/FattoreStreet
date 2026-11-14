// @vitest-environment jsdom
import { useState } from "react";
import { screen, waitFor, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom";
import { http } from "msw";
import { SecuritySearch } from "../src/components/SecuritySearch";
import { NavbarSearch } from "../src/components/NavbarSearch";
import type { ISecuritySearchResult } from "../src/interfaces";
import { renderWithProviders, renderWithRoute } from "./testutils";
import { server } from "./mocks/server";

function Harness({
  onSelect,
  includeFunds,
}: {
  onSelect: (result: ISecuritySearchResult) => void;
  includeFunds?: boolean;
}) {
  const [value, setValue] = useState("");
  return (
    <SecuritySearch
      value={value}
      onChange={setValue}
      onSelect={onSelect}
      ariaLabel="Search"
      includeFunds={includeFunds}
    />
  );
}

describe("SecuritySearch", () => {
  it("suggests matches by name with the ticker beside each", async () => {
    renderWithProviders(<Harness onSelect={vi.fn()} />);

    await userEvent.type(screen.getByRole("combobox"), "apple");

    const list = await screen.findByRole("listbox");
    const options = within(list).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual([
      "Apple Inc. (AAPL)",
      "Apple Hospitality REIT, Inc. (APLE)",
    ]);
    expect(screen.getByRole("combobox")).toHaveAttribute(
      "aria-expanded",
      "true",
    );
  });

  it("picks the first match on Enter", async () => {
    const onSelect = vi.fn();
    renderWithProviders(<Harness onSelect={onSelect} />);

    await userEvent.type(screen.getByRole("combobox"), "apple");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{Enter}");

    expect(onSelect).toHaveBeenCalledWith({
      ticker: "AAPL",
      name: "Apple Inc.",
      isFund: false,
    });
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("moves through matches with the arrow keys", async () => {
    const onSelect = vi.fn();
    renderWithProviders(<Harness onSelect={onSelect} />);

    await userEvent.type(screen.getByRole("combobox"), "apple");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}");

    const options = screen.getAllByRole("option");
    expect(options[1]).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("combobox")).toHaveAttribute(
      "aria-activedescendant",
      options[1].id,
    );

    await userEvent.keyboard("{Enter}");
    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ ticker: "APLE" }),
    );
  });

  it("forgets the highlighted match when focus leaves the box", async () => {
    const onSelect = vi.fn();
    renderWithProviders(
      <>
        <Harness onSelect={onSelect} />
        <button>Elsewhere</button>
      </>,
    );

    await userEvent.type(screen.getByRole("combobox"), "apple");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{ArrowDown}{ArrowDown}");
    await userEvent.tab();
    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole("combobox"));
    expect(screen.getByRole("combobox")).not.toHaveAttribute(
      "aria-activedescendant",
    );
    await userEvent.keyboard("{Enter}");

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ ticker: "AAPL" }),
    );
  });

  it("picks a match on click", async () => {
    const onSelect = vi.fn();
    renderWithProviders(<Harness onSelect={onSelect} />);

    await userEvent.type(screen.getByRole("combobox"), "micro");
    await userEvent.click(await screen.findByText("MICROSOFT CORP (MSFT)"));

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ ticker: "MSFT" }),
    );
  });

  it("closes the list on Escape", async () => {
    renderWithProviders(<Harness onSelect={vi.fn()} />);

    await userEvent.type(screen.getByRole("combobox"), "apple");
    await screen.findByRole("listbox");
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("listbox")).not.toBeInTheDocument();
  });

  it("shows a fund with no name by its ticker and marks it as a fund", async () => {
    renderWithProviders(<Harness onSelect={vi.fn()} />);

    await userEvent.type(screen.getByRole("combobox"), "vti");

    const option = await screen.findByRole("option");
    expect(option).toHaveTextContent(/^VTIFund$/);
  });

  it("asks for companies only when funds are excluded", async () => {
    let includeFundsParam: string | null = null;
    server.use(
      http.get(
        import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("listings/search"),
        ({ request }) => {
          includeFundsParam = new URL(request.url).searchParams.get(
            "includeFunds",
          );
          return Response.json([], { status: 200 });
        },
      ),
    );
    renderWithProviders(<Harness onSelect={vi.fn()} includeFunds={false} />);

    await userEvent.type(screen.getByRole("combobox"), "spy");

    await waitFor(() => expect(includeFundsParam).toBe("false"));
  });
});

describe("NavbarSearch", () => {
  const extraRoutes = [
    { path: "/asset/:ticker", element: <p>Security page</p> },
  ];

  it("opens the picked security's page and clears the box", async () => {
    renderWithRoute(<NavbarSearch />, {
      path: "/",
      initialEntry: "/",
      extraRoutes,
    });

    await userEvent.type(screen.getByRole("combobox"), "micro");
    await userEvent.click(await screen.findByText("MICROSOFT CORP (MSFT)"));

    expect(await screen.findByText("Security page")).toBeInTheDocument();
  });

  it("opens a typed ticker on Enter when nothing matches", async () => {
    server.use(
      http.get(
        import.meta.env.VITE_APP_SPRINGBOOT_URL.concat("listings/search"),
        () => Response.json([], { status: 200 }),
      ),
    );
    renderWithRoute(<NavbarSearch />, {
      path: "/",
      initialEntry: "/",
      extraRoutes,
    });

    await userEvent.type(screen.getByRole("combobox"), "zzzz{Enter}");

    expect(await screen.findByText("Security page")).toBeInTheDocument();
  });
});
