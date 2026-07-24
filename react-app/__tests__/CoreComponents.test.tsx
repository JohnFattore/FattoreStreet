// @vitest-environment jsdom
import React from "react";
import { screen } from "@testing-library/react";
import { expect, describe, it } from "vitest";
import "@testing-library/jest-dom";
import userEvent from "@testing-library/user-event";
import { SortableTable } from "../src/components/SortableTable";
import StateHandler from "../src/components/StateHandler";
import { renderWithProviders } from "./testutils";

const sampleData = [
  { name: "Apple", price: 150, ticker: "AAPL" },
  { name: "Microsoft", price: 400, ticker: "MSFT" },
  { name: "Google", price: 170, ticker: "GOOG" },
];

const columns = [
  {
    label: "Name",
    sortKey: "name" as const,
    render: (row: (typeof sampleData)[0]) => row.name,
  },
  {
    label: "Price",
    sortKey: "price" as const,
    render: (row: (typeof sampleData)[0]) => `$${row.price}`,
  },
  {
    label: "Ticker",
    sortKey: "ticker" as const,
    render: (row: (typeof sampleData)[0]) => row.ticker,
  },
];

describe("SortableTable", () => {
  it("renders column headers and data rows", () => {
    renderWithProviders(
      <SortableTable
        data={sampleData}
        columns={columns}
        isLoading={false}
        errors={[]}
      />,
    );

    expect(screen.getByText(/^Name/)).toBeInTheDocument();
    expect(screen.getByText(/^Price/)).toBeInTheDocument();
    expect(screen.getByText(/^Ticker/)).toBeInTheDocument();

    expect(screen.getByText("Apple")).toBeInTheDocument();
    expect(screen.getByText("Microsoft")).toBeInTheDocument();
    expect(screen.getByText("Google")).toBeInTheDocument();
  });

  it("sorts ascending then descending on header click", async () => {
    const { container } = renderWithProviders(
      <SortableTable
        data={sampleData}
        columns={columns}
        initialSortKey="name"
        initialSortDirection="asc"
        isLoading={false}
        errors={[]}
      />,
    );

    let rows = container.querySelectorAll("tbody tr");
    expect(rows[0]).toHaveTextContent("Apple");
    expect(rows[1]).toHaveTextContent("Google");
    expect(rows[2]).toHaveTextContent("Microsoft");

    await userEvent.click(screen.getByText(/^Price/));
    rows = container.querySelectorAll("tbody tr");
    expect(rows[0]).toHaveTextContent("Apple");
    expect(rows[1]).toHaveTextContent("Google");
    expect(rows[2]).toHaveTextContent("Microsoft");

    await userEvent.click(screen.getByText(/^Price/));
    rows = container.querySelectorAll("tbody tr");
    expect(rows[0]).toHaveTextContent("Microsoft");
    expect(rows[1]).toHaveTextContent("Google");
    expect(rows[2]).toHaveTextContent("Apple");
  });

  it("handles empty data", () => {
    renderWithProviders(
      <SortableTable
        data={[]}
        columns={columns}
        isLoading={false}
        errors={[]}
      />,
    );

    expect(screen.getByText(/^Name/)).toBeInTheDocument();
    expect(screen.queryByText("Apple")).not.toBeInTheDocument();
  });

  it("shows spinner when loading", () => {
    const { container } = renderWithProviders(
      <SortableTable
        data={[]}
        columns={columns}
        isLoading={true}
        errors={[]}
      />,
    );

    expect(container.querySelector(".spinner-border")).toBeTruthy();
  });
});

describe("StateHandler", () => {
  it("renders content when not loading and no errors", () => {
    renderWithProviders(<StateHandler content={<p>Hello World</p>} />);

    expect(screen.getByText("Hello World")).toBeInTheDocument();
  });

  it("renders spinner when loading", () => {
    const { container } = renderWithProviders(
      <StateHandler isLoading={true} content={<p>Hidden</p>} />,
    );

    expect(container.querySelector(".spinner-border")).toBeTruthy();
    expect(screen.queryByText("Hidden")).not.toBeInTheDocument();
  });

  it("renders error alert when errors exist", () => {
    const error = { status: 500, data: "Server error" };
    renderWithProviders(
      <StateHandler errors={[error]} content={<p>Hidden</p>} />,
    );

    expect(screen.queryByText("Hidden")).not.toBeInTheDocument();
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });
});
