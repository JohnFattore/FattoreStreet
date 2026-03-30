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
  errors: unknown[];
};

function parseNumericString(s: string): number | null {
  const stripped = s.replace(/[,$%\s]/g, "");
  if (stripped === "" || isNaN(Number(stripped))) return null;
  return Number(stripped);
}

export function SortableTable<T extends object>({
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
      const key = sortConfig.key as keyof T;
      const aValue = a[key];
      const bValue = b[key];
      if (aValue == null || aValue === "") return 1;
      if (bValue == null || bValue === "") return -1;

      if (typeof aValue === "number" && typeof bValue === "number") {
        return sortConfig.direction === "asc"
          ? aValue - bValue
          : bValue - aValue;
      }
      if (typeof aValue === "string") {
        const aNum = parseNumericString(aValue);
        const bStr = typeof bValue === "string" ? bValue : String(bValue);
        const bNum = parseNumericString(bStr);
        if (aNum !== null && bNum !== null) {
          return sortConfig.direction === "asc"
            ? aNum - bNum
            : bNum - aNum;
        }
        return sortConfig.direction === "asc"
          ? aValue.localeCompare(bStr)
          : bStr.localeCompare(aValue);
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
                  role={sortable ? "button" : undefined}
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
                  <td key={j}>
                    {render
                      ? render(row)
                      : String(
                          (row as Record<string, unknown>)[sortKey as string] ?? ""
                        )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </Table>
      }
    />
  );
}
