import React, { useState, useMemo } from "react";
import { Table, Spinner } from "react-bootstrap";
import StateHandler from "./StateHandler";

type Column<T> = {
  label: string;
  sortKey: keyof T | string;
  render?: (row: T) => React.ReactNode;
  sortable?: boolean;
};

type Props<T> = {
  data: T[];
  columns: Column<T>[];
  initialSortKey?: keyof T | string;
  initialSortDirection?: "asc" | "desc";
  isLoading: boolean;
  errors: any[];
};

export function SortableTable<T extends Record<string, any>>({
  data,
  columns,
  initialSortKey,
  initialSortDirection = "asc",
  isLoading,
  errors,
}: Props<T>) {
  const [sortConfig, setSortConfig] = useState<{
    key: keyof T | string;
    direction: "asc" | "desc";
  }>({
    key: initialSortKey || (columns[0]?.sortKey ?? ""),
    direction: initialSortDirection,
  });

  const onSort = (key: keyof T | string) => {
    setSortConfig((prev) => ({
      key,
      direction: prev.key === key && prev.direction === "asc" ? "desc" : "asc",
    }));
  };

  const renderSortArrow = (key: keyof T | string) => {
    if (sortConfig.key !== key) return null;
    return sortConfig.direction === "asc" ? " ▲" : " ▼";
  };

  const sortedData = useMemo(() => {
    if (!data) return [];
    return [...data].sort((a, b) => {
      const aValue = a[sortConfig.key];
      const bValue = b[sortConfig.key];
      if (aValue == null) return 1;
      if (bValue == null) return -1;

      if (typeof aValue === "string") {
        return sortConfig.direction === "asc"
          ? aValue.localeCompare(bValue)
          : bValue.localeCompare(aValue);
      }
      if (typeof aValue === "number") {
        return sortConfig.direction === "asc"
          ? aValue - bValue
          : bValue - aValue;
      }
      return 0;
    });
  }, [data, sortConfig]);

  if (!data) {
    return <Spinner animation="border" />;
  }

  return (
    <StateHandler
      isLoading={isLoading}
      errors={errors}
      content={
        <Table>
          <thead>
            <tr>
              {columns.map(({ label, sortKey, sortable = true }) => (
                <th
                  key={label}
                  onClick={() => (sortable ? onSort(sortKey) : undefined)}
                  style={{ cursor: sortable ? "pointer" : "default" }}
                >
                  {label}
                  {sortable && renderSortArrow(sortKey)}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {sortedData.map((row, i) => (
              <tr key={i}>
                {columns.map(({ sortKey, render }, j) => (
                  <td key={j}>{render ? render(row) : row[sortKey]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </Table>
      }
    />
  );
}
