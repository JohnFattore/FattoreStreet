import { useMemo } from "react";
import { Spinner, Card } from "react-bootstrap";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
} from "recharts";

const formatDate = (date: string) =>
  new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

const formatValue = (value: number) =>
  new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);

/**
 * Single-line usage:  <GenericLineChart data={[...]} label="Title" strokeColor="#007bff" />
 * Multi-line overlay: <GenericLineChart data={mergedData} label="Title" lines={[{ dataKey: "a", color: "#007bff", name: "A" }, ...]} />
 */
export default function GenericLineChart({ data, label, description, latestReading, strokeColor = "#8884d8", height = 300, lines }: {
  data: object[] | null | undefined;
  label: string;
  description: string;
  latestReading?: string;
  strokeColor?: string;
  height?: number;
  lines?: { dataKey: string; color: string; name: string }[];
}) {
  const autoLatest = useMemo(() => {
    if (latestReading !== undefined || !data || data.length === 0) return null;
    const last = data[data.length - 1] as Record<string, unknown>;
    const date = last.date as string | undefined;
    if (!date) return null;

    if (lines) {
      const parts = lines
        .map((l) => {
          const v = last[l.dataKey];
          return v != null ? `${l.name}: ${formatValue(Number(v))}` : null;
        })
        .filter(Boolean);
      return parts.length > 0 ? `${parts.join(" · ")} (${formatDate(date)})` : null;
    }

    const v = last.value;
    return v != null ? `${formatValue(Number(v))} (${formatDate(date)})` : null;
  }, [data, lines, latestReading]);
  if (!data) {
    return (
      <Card>
        <Card.Body style={{ minHeight: height + 100 }}>
          <Card.Title>{label}</Card.Title>
          <Spinner animation="border" variant="primary" />
        </Card.Body>
      </Card>
    );
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title>
          {label}
        </Card.Title>
        <div style={{ width: "100%", height }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#999', fontSize: 12 }}
                minTickGap={30}
              />
              <YAxis
                domain={["auto", "auto"]}
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#999', fontSize: 12 }}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: '10px',
                  border: 'none',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                }}
              />
              {lines ? (
                <>
                  <Legend />
                  {lines.map((line) => (
                    <Line
                      key={line.dataKey}
                      type="monotone"
                      dataKey={line.dataKey}
                      name={line.name}
                      stroke={line.color}
                      strokeWidth={3}
                      dot={false}
                      activeDot={{ r: 6, strokeWidth: 0 }}
                    />
                  ))}
                </>
              ) : (
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={strokeColor}
                  strokeWidth={3}
                  dot={false}
                  activeDot={{ r: 6, strokeWidth: 0 }}
                />
              )}
            </LineChart>
          </ResponsiveContainer>
        </div>
        {description && (
          <div>
            <p style={{ fontSize: '0.9rem', lineHeight: '1.6' }}>
              {description}
            </p>
          </div>
        )}
        {(latestReading || autoLatest) && (
          <p className="text-muted small mb-0">
            <strong>Latest reading:</strong> {latestReading ?? autoLatest}
          </p>
        )}
      </Card.Body>
    </Card>
  );
}
